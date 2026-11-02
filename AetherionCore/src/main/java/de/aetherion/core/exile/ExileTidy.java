package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * {@code /exile tidy [apply]} on the exile host, a dry run unless {@code apply}. Two leftovers on the island world:
 * <ul>
 *   <li>The old block sword in the crater (x 93..99, y 99..109, z -112): it stood in the middle of the Hollow Sun's
 *   arena and its pommel was taken for the arena floor. {@link HollowSword} replaces it. Only blocks that still are
 *   the sword's are removed.</li>
 *   <li>The water curtain west of the island: a sheet of old ocean (x -160 and the rows z -190 / z 195, y -33..62)
 *   that the void trim left hanging in the void. Nothing else on the map has water below y 95, so every water
 *   block in that box goes.</li>
 * </ul>
 */
public final class ExileTidy {

    private static final int[] WATER_MIN = {-161, -64, -191};
    private static final int[] WATER_MAX = {-41, 62, 196};

    /** The Little Theatre's hall inside its walls, from the floor (y 142 stays) to just under the ceiling trim. */
    private static final int[] HALL_MIN = {-52, 143, 46};
    private static final int[] HALL_MAX = {5, 171, 92};
    /** Middle of Seraphine's stage in the hall (floor surface); ExileDoors spawns her here. */
    public static final int[] THEATRE_STAGE = {-24, 144, 66};

    private ExileTidy() {
    }

    private record Cell(int x, int y, int z, Material m) {
    }

    private static List<Cell> sword() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 99; y <= 104; y++) {
            cells.add(new Cell(95, y, -112, Material.IRON_BLOCK));
            cells.add(new Cell(96, y, -112, Material.SMOOTH_STONE));
            cells.add(new Cell(97, y, -112, Material.IRON_BLOCK));
        }
        cells.add(new Cell(93, 105, -112, Material.RAW_GOLD_BLOCK));
        for (int x = 94; x <= 98; x++) {
            cells.add(new Cell(x, 105, -112, Material.GOLD_BLOCK));
        }
        cells.add(new Cell(99, 105, -112, Material.RAW_GOLD_BLOCK));
        for (int y = 106; y <= 108; y++) {
            cells.add(new Cell(96, y, -112, Material.POLISHED_BLACKSTONE));
        }
        cells.add(new Cell(96, 109, -112, Material.GOLD_BLOCK));
        return cells;
    }

    public static void run(AetherionCore plugin, CommandSender sender, World world, boolean apply) {
        if (world == null) {
            tell(sender, "§cThe exile world is not loaded on this backend.");
            return;
        }
        int found = 0;
        for (Cell c : sword()) {
            Block b = world.getBlockAt(c.x(), c.y(), c.z());
            if (b.getType() != c.m()) {
                continue;
            }
            found++;
            if (apply) {
                b.setType(Material.AIR, false);
            }
        }
        final int swordBlocks = found;
        tell(sender, "§8[Tidy] §7old sword: §f" + swordBlocks + "§7/" + sword().size() + " blocks "
                + (apply ? "removed" : "still standing"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world)).maxBlocks(-1).build()) {
                try {
                    edit.setFastMode(true);
                } catch (Throwable ignored) {
                }
                CuboidRegion box = new CuboidRegion(BlockVector3.at(WATER_MIN[0], WATER_MIN[1], WATER_MIN[2]),
                        BlockVector3.at(WATER_MAX[0], WATER_MAX[1], WATER_MAX[2]));
                BlockTypeMask water = new BlockTypeMask(edit, BlockTypes.WATER);
                int count = apply ? edit.replaceBlocks(box, water, BlockTypes.AIR.getDefaultState())
                        : edit.countBlocks(box, water);
                String line = "[Tidy] water curtain: " + count + " block(s) " + (apply ? "removed" : "would go");
                plugin.getLogger().info(line + (apply ? " (sword " + swordBlocks + ")" : ""));
                Bukkit.getScheduler().runTask(plugin, () -> tell(sender, "§8" + line));
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[Tidy] water curtain failed", t);
                Bukkit.getScheduler().runTask(plugin, () -> tell(sender, "§c[Tidy] water curtain failed: §7" + t.getMessage()));
            }
        });
    }

    /**
     * {@code /exile theatre [apply]}: the Little Theatre becomes Seraphine's arena. The hall is emptied inside its
     * walls (Elin's old stage, the seat rows, the chandeliers; walls, floor and ceiling stay) and her own Gilded
     * Proscenium is built into it for good with {@code /boss stage build}: round stage just over the floor, the arch
     * and curtain against the north wall, the stalls and side boxes around it. Her fights then open on it.
     */
    public static void theatre(AetherionCore plugin, CommandSender sender, World world, boolean apply) {
        if (world == null) {
            tell(sender, "§cThe exile world is not loaded on this backend.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world)).maxBlocks(-1).build()) {
                try {
                    edit.setFastMode(true);
                } catch (Throwable ignored) {
                }
                CuboidRegion hall = new CuboidRegion(BlockVector3.at(HALL_MIN[0], HALL_MIN[1], HALL_MIN[2]),
                        BlockVector3.at(HALL_MAX[0], HALL_MAX[1], HALL_MAX[2]));
                int count = apply ? edit.setBlocks(hall, BlockTypes.AIR.getDefaultState())
                        : edit.countBlocks(hall, new com.sk89q.worldedit.function.mask.ExistingBlockMask(edit));
                String line = "[Tidy] theatre hall: " + count + " block(s) " + (apply ? "cleared" : "inside the walls would go");
                plugin.getLogger().info(line);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    tell(sender, "§8" + line);
                    if (!apply) {
                        tell(sender, "§8[Tidy] then: /boss stage build " + world.getName() + " " + THEATRE_STAGE[0] + " "
                                + THEATRE_STAGE[1] + " " + THEATRE_STAGE[2]);
                        return;
                    }
                    String cmd = "boss stage build " + world.getName() + " " + THEATRE_STAGE[0] + " " + THEATRE_STAGE[1]
                            + " " + THEATRE_STAGE[2];
                    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    plugin.getLogger().info("[Tidy] theatre stage: /" + cmd + (ok ? "" : " (BossEngine did not answer)"));
                    tell(sender, ok ? "§8[Tidy] Seraphine's stage built into the hall." : "§c[Tidy] /" + cmd + " failed.");
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[Tidy] theatre failed", t);
                Bukkit.getScheduler().runTask(plugin, () -> tell(sender, "§c[Tidy] theatre failed: §7" + t.getMessage()));
            }
        });
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
