package de.aetherion.foraging;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Where a tree can fall (A-51, Foraging's own mechanic). A trunk tips over its base towards a
 * direction; the lane is the quarter circle it sweeps on the way down. Another tree's trunk, a wall or
 * a hill in that sweep stops it early: a hang-up, which is a rough fell. Leaves of other trees, grass
 * and flowers don't count: the trunk crashes through them.
 */
final class FellLane {

    /** A hit at or past this angle counts as "resting on the slope", not a hang-up. */
    static final double REST_ANGLE = 75.0d;
    private static final int[] SWEEP_DEGREES = {10, 20, 30, 40, 50, 60, 70, 80, 90};
    private static final int DIRECTIONS = 24;

    /** How a fall in one direction ends. {@code hitAngle} 90 = lies flat, clear. */
    record Result(double hitAngle, Location hitAt) {
        boolean clear() {
            return hitAngle >= REST_ANGLE;
        }
    }

    private FellLane() {
    }

    /**
     * @param base  stump centre at the stump block's bottom (x+.5, y, z+.5)
     * @param dir   horizontal unit direction
     * @param reach trunk + crown height in blocks
     * @param own   block keys of this tree ({@link #key}) — never in its own way
     */
    static Result sweep(Location base, Vector dir, int reach, Set<Long> own) {
        World world = base.getWorld();
        if (world == null) {
            return new Result(90.0d, null);
        }
        for (int degrees : SWEEP_DEGREES) {
            double a = Math.toRadians(degrees);
            double sin = Math.sin(a);
            double cos = Math.cos(a);
            for (int h = 2; h <= reach; h++) {
                double x = base.getX() + dir.getX() * h * sin;
                double y = base.getY() + 0.5d + h * cos;
                double z = base.getZ() + dir.getZ() * h * sin;
                Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                if (blocks(block, own)) {
                    return new Result(Math.max(5.0d, degrees - 5.0d), block.getLocation().add(0.5d, 0.5d, 0.5d));
                }
            }
        }
        return new Result(90.0d, null);
    }

    /**
     * The gold mark's direction: a random clear lane, or the least blocked one when the tree stands in a
     * thicket. {@code avoid} (the cutter's own side) is used only when nothing else is clear.
     */
    static Vector markDirection(Location base, int reach, Set<Long> own, Vector avoid) {
        Vector best = null;
        double bestAngle = -1.0d;
        java.util.List<Vector> clear = new java.util.ArrayList<>();
        java.util.List<Vector> clearAway = new java.util.ArrayList<>();
        double offset = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d / DIRECTIONS);
        for (int i = 0; i < DIRECTIONS; i++) {
            double yaw = offset + i * Math.PI * 2.0d / DIRECTIONS;
            Vector dir = new Vector(Math.cos(yaw), 0.0d, Math.sin(yaw));
            Result result = sweep(base, dir, reach, own);
            if (result.clear()) {
                clear.add(dir);
                if (avoid == null || dir.dot(avoid) < 0.5d) {
                    clearAway.add(dir);
                }
            }
            if (result.hitAngle() > bestAngle) {
                bestAngle = result.hitAngle();
                best = dir;
            }
        }
        java.util.List<Vector> pool = !clearAway.isEmpty() ? clearAway : clear;
        if (!pool.isEmpty()) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).clone();
        }
        return best == null ? new Vector(1, 0, 0) : best.clone();
    }

    /** Angle between two horizontal unit directions, in degrees (0..180). */
    static double degreesBetween(Vector a, Vector b) {
        double dot = Math.max(-1.0d, Math.min(1.0d, a.getX() * b.getX() + a.getZ() * b.getZ()));
        return Math.toDegrees(Math.acos(dot));
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static boolean blocks(Block block, Set<Long> own) {
        if (own.contains(key(block.getX(), block.getY(), block.getZ()))) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) {
            return false;
        }
        String name = type.name();
        if (name.endsWith("_LEAVES") || name.equals("VINE") || name.endsWith("_WART_BLOCK")
                || name.equals("SHROOMLIGHT") || name.endsWith("AZALEA") || name.equals("MOSS_CARPET")
                || name.endsWith("_CARPET") || name.equals("SNOW") || name.equals("COBWEB")) {
            return false;
        }
        return type.isSolid();
    }
}
