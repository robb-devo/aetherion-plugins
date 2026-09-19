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
 * Console/ops FAWE async paste of Eldervale island schematics.
 */
public final class AetherPasteCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "aetherion.paste";

    private static final List<String> ISLANDS = List.of("farming", "fishing");

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

        IslandPreset preset = IslandPreset.from(args[0]);
        if (preset == null) {
            sender.sendMessage("§cUnknown island §f" + args[0] + "§c. Use §ffarming §cor §ffishing§c.");
            usage(sender);
            return true;
        }
        if (args.length != 1 && args.length != 4 && args.length != 5) {
            usage(sender);
            return true;
        }

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

        if (!FaweIslandPaste.fawePresent()) {
            sender.sendMessage("§cFastAsyncWorldEdit is required for /aetherpaste (async queue).");
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
            sender.sendMessage("§cA paste is already running. Wait for it to finish.");
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
        try {
            FaweIslandPaste.runAsync(plugin, () -> FaweIslandPaste.paste(
                    plugin, sender, preset.id, schem, world, px, py, pz, ignoreAir, rotateY
            ));
        } catch (RuntimeException exception) {
            FaweIslandPaste.finish();
            plugin.getLogger().log(Level.SEVERE, "aetherpaste failed to schedule", exception);
            sender.sendMessage("§cCould not start async paste: " + exception.getMessage());
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("§6/aetherpaste <farming|fishing> [x y z] [world]");
        sender.sendMessage("§7farming §8→ §fFarming-Eldervale-Island.schem §7at §f-528 90 474 §7in §fworld");
        sender.sendMessage("§7fishing §8→ §fFishing-Eldervale-Island.schem §7at §f-585 90 -649 §7in §fworld");
        sender.sendMessage("§7Pasted via FastAsyncWorldEdit (async queue). Permission: §faetherion.paste");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ISLANDS, args[0]);
        }
        if (args.length == 5) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private enum IslandPreset {
        FARMING("farming", "Farming-Eldervale-Island.schem", "world", -528, 90, 474),
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
