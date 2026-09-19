package de.aetherion.hub.command;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.island.FaweIslandPaste;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /aetherpaste <farming|fishing> [x y z] [world]}
 * {@code /aetherpaste clear <farming|fishing>}
 * {@code /aetherpaste <farming|fishing> clear}
 * {@code /aetherpaste clear <x1 y1 z1 x2 y2 z2> [world]}
 */
public final class AetherPasteCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "aetherion.paste";

    private static final List<String> ISLANDS = List.of("farming", "fishing");
    private static final List<String> FIRST = List.of("farming", "fishing", "clear");

    private final AetherionHub plugin;

    public AetherPasteCommand(AetherionHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cYou cannot use this command.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        if ("clear".equalsIgnoreCase(args[0])) {
            return handleClear(sender, args);
        }

        IslandPreset preset = IslandPreset.from(args[0]);
        if (preset == null) {
            sender.sendMessage("§cUnknown island §f" + args[0] + "§c. Use §ffarming §cor §ffishing§c.");
            usage(sender);
            return true;
        }
        if (args.length == 2 && "clear".equalsIgnoreCase(args[1])) {
            return clearIsland(sender, preset);
        }
        if (args.length != 1 && args.length != 4 && args.length != 5) {
            usage(sender);
            return true;
        }
        return pasteIsland(sender, preset, args);
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length == 2) {
            IslandPreset preset = IslandPreset.from(args[1]);
            if (preset == null) {
                sender.sendMessage("§cUnknown island §f" + args[1] + "§c. Use §ffarming §cor §ffishing§c.");
                usage(sender);
                return true;
            }
            return clearIsland(sender, preset);
        }
        if (args.length == 7 || args.length == 8) {
            return clearBox(sender, args);
        }
        usage(sender);
        return true;
    }

    private boolean pasteIsland(CommandSender sender, IslandPreset preset, String[] args) {
        int x = preset.x(plugin);
        int y = preset.y(plugin);
        int z = preset.z(plugin);
        String worldName = preset.world(plugin);
        if (args.length >= 4) {
            try {
                x = Integer.parseInt(args[1]);
                y = Integer.parseInt(args[2]);
                z = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cCoordinates must be integers: §f<x> <y> <z>");
                return true;
            }
        }
        if (args.length == 5) {
            worldName = args[4];
        }

        if (!requireFawe(sender)) {
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: §f" + worldName);
            return true;
        }

        String schemName = preset.schematic(plugin);
        File schem = FaweIslandPaste.resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            sender.sendMessage("§cSchematic not found: §f" + schemName);
            for (String path : FaweIslandPaste.schematicSearchHint(plugin, schemName)) {
                sender.sendMessage("§7Tried §f" + path);
            }
            return true;
        }

        if (!FaweIslandPaste.tryBegin()) {
            sender.sendMessage("§cA paste/clear is already running. Wait for it to finish.");
            return true;
        }

        boolean ignoreAir = preset.ignoreAir(plugin);
        int rotateY = preset.rotateY(plugin);
        sender.sendMessage("§eStarting async FAWE paste §f" + preset.id
                + " §eat §f" + x + " " + y + " " + z
                + " §7" + world.getName()
                + " §e(schem=§f" + schem.getName()
                + "§e ignoreAir=" + ignoreAir
                + " rotateY=" + rotateY + "§e).");
        plugin.getLogger().info("aetherpaste start " + preset.id
                + " schem=" + schem.getName()
                + " world=" + world.getName()
                + " origin=" + x + "," + y + "," + z
                + " ignoreAir=" + ignoreAir
                + " rotateY=" + rotateY
                + " file=" + schem.getAbsolutePath());

        final int px = x;
        final int py = y;
        final int pz = z;
        return schedule(sender, () -> FaweIslandPaste.paste(
                plugin, sender, preset.id, schem, world, px, py, pz, ignoreAir, rotateY
        ));
    }

    private boolean clearIsland(CommandSender sender, IslandPreset preset) {
        if (!requireFawe(sender)) {
            return true;
        }

        FaweIslandPaste.Footprint footprint = FaweIslandPaste.loadLastFootprint(plugin, preset.id);
        String source = "last paste";
        if (footprint == null) {
            try {
                footprint = fallbackFootprint(preset);
                source = "schem + last/default origin";
            } catch (Exception exception) {
                sender.sendMessage("§cNo last footprint for §f" + preset.id + "§c, and could not derive one from the schematic.");
                sender.sendMessage("§7" + exception.getMessage());
                sender.sendMessage("§7Or clear a box: §f/aetherpaste clear <x1 y1 z1 x2 y2 z2> [world]");
                return true;
            }
        }

        World world = Bukkit.getWorld(footprint.world());
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: §f" + footprint.world());
            return true;
        }
        if (!FaweIslandPaste.tryBegin()) {
            sender.sendMessage("§cA paste/clear is already running. Wait for it to finish.");
            return true;
        }

        sender.sendMessage("§eStarting async FAWE clear §f" + preset.id
                + " §e(" + source + ") §f"
                + footprint.minX() + " " + footprint.minY() + " " + footprint.minZ()
                + " §7→ §f"
                + footprint.maxX() + " " + footprint.maxY() + " " + footprint.maxZ()
                + " §7" + world.getName() + "§e.");
        plugin.getLogger().info("aetherpaste clear " + preset.id
                + " source=" + source
                + " world=" + world.getName()
                + " box=" + footprint.minX() + "," + footprint.minY() + "," + footprint.minZ()
                + ".." + footprint.maxX() + "," + footprint.maxY() + "," + footprint.maxZ());

        FaweIslandPaste.Footprint box = footprint;
        return schedule(sender, () -> FaweIslandPaste.clearBox(
                plugin, sender, preset.id, world,
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()
        ));
    }

    private FaweIslandPaste.Footprint fallbackFootprint(IslandPreset preset) throws Exception {
        FaweIslandPaste.LastOrigin last = FaweIslandPaste.loadLastOrigin(plugin, preset.id);
        int x = last != null ? last.x() : preset.x(plugin);
        int y = last != null ? last.y() : preset.y(plugin);
        int z = last != null ? last.z() : preset.z(plugin);
        String worldName = last != null ? last.world() : preset.world(plugin);
        File schem = FaweIslandPaste.resolveSchematic(plugin, preset.schematic(plugin));
        if (schem == null || !schem.isFile()) {
            throw new IllegalStateException("Schematic not found: " + preset.schematic(plugin));
        }
        return FaweIslandPaste.footprintFromSchem(schem, x, y, z, preset.rotateY(plugin), worldName);
    }

    private boolean clearBox(CommandSender sender, String[] args) {
        int x1;
        int y1;
        int z1;
        int x2;
        int y2;
        int z2;
        try {
            x1 = Integer.parseInt(args[1]);
            y1 = Integer.parseInt(args[2]);
            z1 = Integer.parseInt(args[3]);
            x2 = Integer.parseInt(args[4]);
            y2 = Integer.parseInt(args[5]);
            z2 = Integer.parseInt(args[6]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cBox coordinates must be integers: §f<x1 y1 z1 x2 y2 z2>");
            return true;
        }
        String worldName = args.length == 8 ? args[7] : "world";
        if (!requireFawe(sender)) {
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: §f" + worldName);
            return true;
        }
        if (!FaweIslandPaste.tryBegin()) {
            sender.sendMessage("§cA paste/clear is already running. Wait for it to finish.");
            return true;
        }

        String label = x1 + "," + y1 + "," + z1 + ".." + x2 + "," + y2 + "," + z2;
        sender.sendMessage("§eStarting async FAWE clear box §f"
                + x1 + " " + y1 + " " + z1 + " §7→ §f"
                + x2 + " " + y2 + " " + z2 + " §7" + world.getName() + "§e.");
        return schedule(sender, () -> FaweIslandPaste.clearBox(
                plugin, sender, label, world, x1, y1, z1, x2, y2, z2
        ));
    }

    private boolean requireFawe(CommandSender sender) {
        if (FaweIslandPaste.fawePresent()) {
            return true;
        }
        sender.sendMessage("§cFastAsyncWorldEdit is required for /aetherpaste (async queue).");
        return false;
    }

    private boolean schedule(CommandSender sender, Runnable task) {
        try {
            FaweIslandPaste.runAsync(plugin, task);
        } catch (RuntimeException exception) {
            FaweIslandPaste.finish();
            plugin.getLogger().log(Level.SEVERE, "aetherpaste failed to schedule", exception);
            sender.sendMessage("§cCould not start async FAWE job: " + exception.getMessage());
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("§6/aetherpaste <farming|fishing> [x y z] [world]");
        sender.sendMessage("§6/aetherpaste clear <farming|fishing>");
        sender.sendMessage("§6/aetherpaste <farming|fishing> clear");
        sender.sendMessage("§6/aetherpaste clear <x1 y1 z1 x2 y2 z2> [world]");
        sender.sendMessage("§7farming §8→ §fFarming-Eldervale-Island.schem §7at §f-600 90 427 §7in §fworld");
        sender.sendMessage("§7fishing §8→ §fFishing-Eldervale-Island.schem §7at §f-585 90 -649 §7in §fworld");
        sender.sendMessage("§7Clear uses the last pasted AABB (saved in Hub config). FAWE async queue. §8aetherion.paste");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(FIRST, args[0]);
        }
        if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
            return filter(ISLANDS, args[1]);
        }
        if (args.length == 2 && IslandPreset.from(args[0]) != null) {
            return filter(List.of("clear"), args[1]);
        }
        if (args.length == 5 && IslandPreset.from(args[0]) != null) {
            return filter(worldNames(), args[4]);
        }
        if (args.length == 8 && "clear".equalsIgnoreCase(args[0])) {
            return filter(worldNames(), args[7]);
        }
        return List.of();
    }

    private static List<String> worldNames() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private enum IslandPreset {
        FARMING("farming", "Farming-Eldervale-Island.schem", "world", -600, 90, 427),
        FISHING("fishing", "Fishing-Eldervale-Island.schem", "world", -585, 90, -649);

        private final String id;
        private final String schematic;
        private final String world;
        private final int x;
        private final int y;
        private final int z;

        IslandPreset(String id, String schematic, String world, int x, int y, int z) {
            this.id = id;
            this.schematic = schematic;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static IslandPreset from(String raw) {
            String key = raw.toLowerCase(Locale.ROOT);
            for (IslandPreset preset : values()) {
                if (preset.id.equals(key)) {
                    return preset;
                }
            }
            return null;
        }

        ConfigurationSection section(AetherionHub plugin) {
            return plugin.getConfig().getConfigurationSection("aether-paste." + id);
        }

        String schematic(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getString("schematic", schematic) : schematic;
        }

        String world(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getString("world", world) : world;
        }

        int x(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getInt("paste.x", x) : x;
        }

        int y(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getInt("paste.y", y) : y;
        }

        int z(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getInt("paste.z", z) : z;
        }

        boolean ignoreAir(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section == null || section.getBoolean("ignore-air", true);
        }

        int rotateY(AetherionHub plugin) {
            ConfigurationSection section = section(plugin);
            return section != null ? section.getInt("rotate-y", 0) : 0;
        }
    }
}
