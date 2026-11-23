package de.aetherion.foraging;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The tree coming down (A-51). Every log and most of the crown become block displays pinned to the
 * stump's edge, then tip over towards the chosen direction like a real fell: slow at first, then
 * faster, a thud and a small bounce. A hung-up tree stops at the angle where it met the obstacle,
 * shudders and splinters. The world blocks are already gone; the displays are only the picture.
 * <pre>
 *   0 ─ displays in place, real blocks cleared
 *   2 ─ creak: 0°→12° (8t) · 10 ─ 12°→40° (5t) · 15 ─ 40°→70° (4t) · 19 ─ 70°→90° (3t)
 *  22 ─ thud (onLand) · bounce · 34 ─ shrink away · 44 ─ removed
 * </pre>
 */
final class TreeFall {

    static final String TAG = "ae_tree_fall";
    /** Ticks from the start until the trunk lies flat (callers time their payout to this). */
    static final int LAND_TICKS = 22;
    private static final int MAX_LEAVES = 150;

    /** One block of the falling picture: its offset from the pivot, and what it looks like. */
    record Piece(double dx, double dy, double dz, BlockData data, boolean log) {
    }

    private TreeFall() {
    }

    /**
     * @param pivot   bottom edge of the stump on the fall side
     * @param dir     horizontal unit direction of the fall
     * @param stopAt  final angle in degrees (90 = flat; less = hung up)
     * @param onLand  runs on the thud (pay the wood there)
     */
    static void play(Plugin plugin, Location pivot, Vector dir, double stopAt, List<Piece> pieces, Runnable onLand) {
        World world = pivot.getWorld();
        if (world == null || pieces.isEmpty()) {
            if (onLand != null) {
                onLand.run();
            }
            return;
        }
        Vector3f axis = new Vector3f((float) dir.getZ(), 0.0f, (float) -dir.getX()).normalize();
        List<Piece> shown = thin(pieces);
        double tallest = 2.0d;
        for (Piece piece : shown) {
            tallest = Math.max(tallest, Math.abs(piece.dy()) + 2.0d);
        }
        final float reach = (float) tallest;
        List<BlockDisplay> displays = new ArrayList<>(shown.size());
        float box = reach * 2.0f + 4.0f;
        for (Piece piece : shown) {
            try {
                BlockDisplay display = world.spawn(pivot, BlockDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setSilent(true);
                    d.addScoreboardTag(TAG);
                    d.setBlock(piece.data());
                    d.setDisplayWidth(box);
                    d.setDisplayHeight(box);
                    d.setViewRange(0.8f);
                    d.setTransformation(pose(piece, axis, 0.0f, 1.0f));
                });
                displays.add(display);
            } catch (RuntimeException | LinkageError failed) {
                break;
            }
        }
        double end = Math.max(5.0d, Math.min(90.0d, stopAt));
        boolean hungUp = end < FellLane.REST_ANGLE;
        double[][] steps = hungUp
                ? new double[][] {{2, 0.13 * end, 8}, {10, 0.45 * end, 5}, {15, end, 4}}
                : new double[][] {{2, 12, 8}, {10, 40, 5}, {15, 70, 4}, {19, 90, 3}};
        for (double[] step : steps) {
            float angle = (float) Math.toRadians(step[1]);
            int duration = (int) step[2];
            Bukkit.getScheduler().runTaskLater(plugin, () -> move(displays, shown, axis, angle, 1.0f, duration), (long) step[0]);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> world.playSound(pivot, Sound.BLOCK_WOOD_HIT,
                SoundCategory.BLOCKS, 0.9f, 0.55f), 3L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> world.playSound(pivot, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundCategory.BLOCKS, 0.7f, 0.5f), 11L);
        float flat = (float) Math.toRadians(end);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            thud(world, pivot, dir, reach, end, shown);
            if (hungUp) {
                // Shudder against the obstacle.
                move(displays, shown, axis, flat - 0.06f, 1.0f, 2);
            } else {
                move(displays, shown, axis, flat - 0.07f, 1.0f, 3);
            }
            if (onLand != null) {
                onLand.run();
            }
        }, LAND_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> move(displays, shown, axis, flat, 1.0f, 3), LAND_TICKS + 3L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            move(displays, shown, axis, flat, 0.15f, 9);
            dust(world, pivot, dir, reach, end, shown);
        }, LAND_TICKS + 12L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay display : displays) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }, LAND_TICKS + 22L);
    }

    /** Remove pictures a hard stop left behind. */
    static void sweepStrays(World world) {
        if (world == null) {
            return;
        }
        for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
            if (display.getScoreboardTags().contains(TAG)) {
                display.remove();
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private static List<Piece> thin(List<Piece> pieces) {
        List<Piece> logs = new ArrayList<>();
        List<Piece> leaves = new ArrayList<>();
        for (Piece piece : pieces) {
            (piece.log() ? logs : leaves).add(piece);
        }
        if (leaves.size() > MAX_LEAVES) {
            List<Piece> kept = new ArrayList<>(MAX_LEAVES);
            double step = leaves.size() / (double) MAX_LEAVES;
            for (double i = 0; i < leaves.size() && kept.size() < MAX_LEAVES; i += step) {
                kept.add(leaves.get((int) i));
            }
            leaves = kept;
        }
        logs.addAll(leaves);
        return logs;
    }

    private static void move(List<BlockDisplay> displays, List<Piece> pieces, Vector3f axis, float angle, float scale,
                             int duration) {
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay display = displays.get(i);
            if (!display.isValid()) {
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(pose(pieces.get(i), axis, angle, scale));
        }
    }

    /** Block at {@code piece} rotated about the pivot axis; {@code scale} shrinks it about its own centre. */
    private static Transformation pose(Piece piece, Vector3f axis, float angle, float scale) {
        Quaternionf q = new Quaternionf().rotateAxis(angle, axis.x, axis.y, axis.z);
        float inset = (1.0f - scale) * 0.5f;
        Vector3f offset = new Vector3f((float) piece.dx() + inset, (float) piece.dy() + inset, (float) piece.dz() + inset);
        Vector3f translation = q.transform(offset, new Vector3f());
        return new Transformation(translation, q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static void thud(World world, Location pivot, Vector dir, float reach, double end, List<Piece> pieces) {
        BlockData wood = pieces.get(0).data();
        double sin = Math.sin(Math.toRadians(end));
        double cos = Math.cos(Math.toRadians(end));
        for (double d = 1.5d; d < reach; d += 1.5d) {
            Location at = pivot.clone().add(dir.getX() * d * sin, 0.3d + d * cos, dir.getZ() * d * sin);
            world.spawnParticle(Particle.BLOCK, at, 6, 0.35, 0.2, 0.35, 0.0, wood);
            if (end >= FellLane.REST_ANGLE) {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.3, 0.05, 0.3, 0.005);
            }
        }
        world.playSound(pivot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.35f, 0.55f);
        world.playSound(pivot, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(pivot, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.6f, 0.6f);
    }

    private static void dust(World world, Location pivot, Vector dir, float reach, double end, List<Piece> pieces) {
        BlockData leaf = null;
        for (Piece piece : pieces) {
            if (!piece.log()) {
                leaf = piece.data();
                break;
            }
        }
        double sin = Math.sin(Math.toRadians(end));
        double cos = Math.cos(Math.toRadians(end));
        for (double d = reach * 0.5d; d < reach; d += 1.0d) {
            Location at = pivot.clone().add(dir.getX() * d * sin, 0.6d + d * cos, dir.getZ() * d * sin);
            if (leaf != null) {
                world.spawnParticle(Particle.BLOCK, at, 5, 0.8, 0.4, 0.8, 0.0, leaf);
            }
        }
        world.playSound(pivot, Sound.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 0.8f, 0.8f);
    }
}
