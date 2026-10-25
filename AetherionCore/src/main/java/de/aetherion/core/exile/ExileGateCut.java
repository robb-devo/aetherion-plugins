package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Iron Ring's gate (mmo-c, Runde 7 coordinates). The map was delivered with the gate buried: the crater's debris
 * stands as a heap against the bars (three of the grille's five rows hidden, the stand-outside point of the map's
 * own anchors under three blocks of rubble), so from the barracks yard it reads as a rubbish pile, not an entrance.
 *
 * <p>Once, on the exile host, this digs the approach out: an apron level with the gate's sill in front of the bars
 * and a stair up to the yard path, paved in the gate posts' sandstone. Only that box is touched and only debris
 * inside it goes (banners, signs, lanterns, the grille and its gold stay). Every block it changes is written to
 * {@code exile-crater-gate.yml} before the first one changes; {@code /exile gatecut undo} puts them back. The file
 * existing means it has run: it never runs on its own again, {@code /exile gatecut} runs it by hand.
 */
public final class ExileGateCut {

    static final int FLOOR_Y = 116;
    static final int TOP_Y = 127;
    /** {z, x1, x2, step}: the apron (step = the sill's floor) and the stair rows climbing south to the yard path. */
    static final int[][] ROWS = {
            {-71, 71, 75, 116}, {-70, 70, 76, 116}, {-69, 70, 76, 116},
            {-68, 71, 75, 117}, {-67, 71, 75, 118}, {-66, 71, 75, 119}, {-65, 71, 75, 120}
    };
    /** The gate's grille: if it is not here, this is not the map the box was measured on and nothing changes. */
    private static final int[] GRILLE = {71, 117, -72, 75, 121, -72};
    private static final String FILE = "exile-crater-gate.yml";

    private ExileGateCut() {
    }

    record Change(int x, int y, int z, String before, String after) {
    }

    /** Boot on the exile host: cut once unless {@code exile.crater-gate.cut} is false or it already ran. */
    public static void ensure(AetherionCore plugin, World world) {
        if (world == null || !plugin.getConfig().getBoolean("exile.crater-gate.cut", true)) {
            return;
        }
        if (new File(plugin.getDataFolder(), FILE).isFile()) {
            return;
        }
        run(plugin, null, world);
    }

    /** Cut now ({@code /exile gatecut}); refuses when the grille is not where the box was measured. */
    public static void run(AetherionCore plugin, CommandSender sender, World world) {
        if (world == null) {
            report(plugin, sender, "§c[GateCut] exile world not loaded. Nothing changed.");
            return;
        }
        if (!grilleInPlace(world)) {
            report(plugin, sender, "§c[GateCut] the Iron Ring's grille is not at " + GRILLE[0] + ".." + GRILLE[3] + " "
                    + GRILLE[1] + ".." + GRILLE[4] + " " + GRILLE[2] + " — not the map this was measured on. Nothing changed.");
            return;
        }
        List<Change> changes = plan(world);
        if (changes.isEmpty()) {
            report(plugin, sender, "§7[GateCut] the approach to the Iron Ring is already clear.");
            return;
        }
        File file = new File(plugin.getDataFolder(), FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("state", "cut");
        yaml.set("world", world.getName());
        List<String> lines = new ArrayList<>();
        for (Change c : changes) {
            lines.add(c.x() + " " + c.y() + " " + c.z() + " " + c.before());
        }
        yaml.set("before", lines);
        try {
            yaml.save(file);
        } catch (Exception ex) {
            report(plugin, sender, "§c[GateCut] backup could not be written (" + ex.getMessage() + "). Nothing changed.");
            return;
        }
        for (Change c : changes) {
            world.getBlockAt(c.x(), c.y(), c.z()).setBlockData(Bukkit.createBlockData(c.after()), false);
        }
        report(plugin, sender, "§a[GateCut] dug out the approach to the Iron Ring: " + changes.size()
                + " blocks (backup " + FILE + ", /exile gatecut undo puts them back).");
    }

    /** Puts back every block the cut changed and marks it undone (it will not cut again on its own). */
    public static void undo(AetherionCore plugin, CommandSender sender) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.isFile()) {
            report(plugin, sender, "§7[GateCut] nothing to undo (no " + FILE + ").");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!"cut".equals(yaml.getString("state"))) {
            report(plugin, sender, "§7[GateCut] already undone.");
            return;
        }
        World world = Bukkit.getWorld(yaml.getString("world", ""));
        if (world == null) {
            report(plugin, sender, "§c[GateCut] world '" + yaml.getString("world") + "' not loaded. Nothing changed.");
            return;
        }
        int n = 0;
        for (String line : yaml.getStringList("before")) {
            String[] p = line.split(" ", 4);
            if (p.length < 4) {
                continue;
            }
            try {
                world.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]))
                        .setBlockData(Bukkit.createBlockData(p[3]), false);
                n++;
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[GateCut] could not restore '" + line + "': " + ex.getMessage());
            }
        }
        yaml.set("state", "undone");
        try {
            yaml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("[GateCut] could not mark the backup undone: " + ex.getMessage());
        }
        report(plugin, sender, "§a[GateCut] put back " + n + " blocks at the Iron Ring.");
    }

    static boolean grilleInPlace(World world) {
        for (int x = GRILLE[0]; x <= GRILLE[3]; x++) {
            for (int y = GRILLE[1]; y <= GRILLE[4]; y++) {
                if (world.getBlockAt(x, y, GRILLE[2]).getType() != Material.IRON_BARS) {
                    return false;
                }
            }
        }
        return true;
    }

    /** What the box should become, block by block, against what stands there now. */
    static List<Change> plan(World world) {
        String floor = Material.SMOOTH_SANDSTONE.createBlockData().getAsString();
        String step = Bukkit.createBlockData("minecraft:smooth_sandstone_stairs[facing=south,half=bottom,shape=straight]").getAsString();
        String air = Material.AIR.createBlockData().getAsString();
        List<Change> out = new ArrayList<>();
        for (int[] row : ROWS) {
            int z = row[0];
            int top = row[3];
            boolean apron = top == FLOOR_Y;
            for (int x = row[1]; x <= row[2]; x++) {
                for (int y = FLOOR_Y; y <= TOP_Y; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (keeps(b)) {
                        continue;
                    }
                    String want;
                    if (y == top) {
                        want = apron ? floor : step;
                    } else if (y > top) {
                        want = air;
                    } else if (b.isPassable()) {
                        want = floor;
                    } else {
                        continue;
                    }
                    BlockData now = b.getBlockData();
                    if (!now.matches(Bukkit.createBlockData(want))) {
                        out.add(new Change(x, y, z, now.getAsString(), want));
                    }
                }
            }
        }
        return out;
    }

    private static boolean keeps(Block b) {
        Material m = b.getType();
        String key = m.getKey().getKey();
        return m == Material.IRON_BARS || m == Material.GOLD_BLOCK || key.endsWith("banner") || key.endsWith("sign")
                || key.endsWith("lantern") || b.getState() instanceof TileState;
    }

    private static void report(AetherionCore plugin, CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
        plugin.getLogger().info(line.replaceAll("§.", ""));
    }
}
