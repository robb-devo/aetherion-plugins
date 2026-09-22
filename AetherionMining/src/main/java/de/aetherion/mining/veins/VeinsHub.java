package de.aetherion.mining.veins;

import org.bukkit.Location;

/**
 * Spawn-protect helpers for the Amethyst Area hub.
 * Does <strong>not</strong> paste or reshape the BreadBuilds center schematic.
 */
public final class VeinsHub {

    private VeinsHub() {
    }

    /**
     * True when the block is inside the spawn-protect cylinder around hub spawn.
     * Horizontal radius from config ({@code veins.spawn-protect-radius}, default 20);
     * vertical band keeps multi-level hub floors covered.
     */
    public static boolean protectedSpot(Location location, Location spawn, int radius) {
        if (location == null || spawn == null) {
            return false;
        }
        if (location.getWorld() == null || spawn.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getUID().equals(spawn.getWorld().getUID())) {
            return false;
        }
        int r = Math.max(4, radius);
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        if (dx * dx + dz * dz > (double) r * r) {
            return false;
        }
        double dy = Math.abs(location.getY() - spawn.getY());
        return dy <= Math.max(24.0, r);
    }

    /** @deprecated Use {@link #protectedSpot(Location, Location, int)} with live spawn. */
    @Deprecated
    public static boolean protectedSpot(Location location, int hubY) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Location spawn = new Location(location.getWorld(), 8.5, hubY, 8.5);
        return protectedSpot(location, spawn, 20);
    }
}
