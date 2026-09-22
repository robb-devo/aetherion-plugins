package de.aetherion.mining.veins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * WorldGuard flags for {@code aether_veins}: open dig outside spawn, protect hub spawn.
 */
public final class VeinsGuard {

    public static final String SPAWN_REGION = "veins_spawn_protect";

    private VeinsGuard() {
    }

    public static void open(World world) {
        if (world == null) {
            return;
        }
        try {
            RegionManager manager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return;
            }
            ProtectedRegion global = manager.getRegion("__global__");
            if (global != null) {
                global.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
                global.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
                global.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                global.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Cuboid around hub spawn denying break/place for non-members.
     * Plugin {@link VeinsListener} is the source of truth; WG is belt-and-suspenders.
     */
    public static void protectSpawn(World world, Location spawn, int radius) {
        if (world == null || spawn == null) {
            return;
        }
        try {
            RegionManager manager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return;
            }
            int r = Math.max(4, radius);
            int yPad = Math.max(24, r);
            int sx = spawn.getBlockX();
            int sy = spawn.getBlockY();
            int sz = spawn.getBlockZ();
            BlockVector3 min = BlockVector3.at(sx - r, sy - yPad, sz - r);
            BlockVector3 max = BlockVector3.at(sx + r, sy + yPad, sz + r);
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(SPAWN_REGION, min, max);
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            region.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
            manager.addRegion(region);
        } catch (RuntimeException ignored) {
        }
    }
}
