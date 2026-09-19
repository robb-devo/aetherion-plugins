package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * T2 Colosseum arena — center marked in-world; bosses spawn one block below the pad.
 */
public final class ColosseumArena {

    public static final String WORLD = "world";
    /** Player standing pad (reported). */
    public static final double PAD_X = -362.5;
    public static final double PAD_Y = 66.0;
    public static final double PAD_Z = -110.5;
    /** Boss spawn = one block under the pad. */
    public static final double SPAWN_Y = 65.0;
    public static final int RADIUS = 45;

    private ColosseumArena() {
    }

    public static Location pad(World world) {
        return new Location(world, PAD_X, PAD_Y, PAD_Z);
    }

    public static Location bossSpawn(World world) {
        return new Location(world, PAD_X, SPAWN_Y, PAD_Z);
    }

    public static Location bossSpawn() {
        World world = Bukkit.getWorld(WORLD);
        return world == null ? null : bossSpawn(world);
    }

    public static boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!WORLD.equalsIgnoreCase(location.getWorld().getName())) {
            return false;
        }
        double dx = location.getX() - PAD_X;
        double dz = location.getZ() - PAD_Z;
        return dx * dx + dz * dz <= (double) RADIUS * RADIUS;
    }

    /** Ensure a Colosseum area marker exists for TAB / discover. */
    public static void ensureArea(JavaPlugin plugin, AreaService areas) {
        if (areas == null) {
            return;
        }
        for (AreaZone zone : areas.zonesOf(AreaType.COLOSSEUM)) {
            // Keep the fixed center — rewrite if someone placed a stray marker.
            if (Math.abs(zone.getX() - PAD_X) < 2.0
                    && Math.abs(zone.getZ() - PAD_Z) < 2.0
                    && zone.getRadius() == RADIUS) {
                return;
            }
            areas.clearType(AreaType.COLOSSEUM);
            break;
        }
        World world = Bukkit.getWorld(WORLD);
        if (world == null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> ensureArea(plugin, areas), 80L);
            return;
        }
        areas.place(bossSpawn(world), AreaType.COLOSSEUM, null, RADIUS);
        plugin.getLogger().info("Colosseum area ready @ "
                + PAD_X + "," + SPAWN_Y + "," + PAD_Z + " r=" + RADIUS);
    }
}
