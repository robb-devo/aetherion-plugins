package de.aetherion.mining.veins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.World;

public final class VeinsGuard {

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
            if (global == null) {
                return;
            }
            global.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
            global.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
            global.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
            global.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
        } catch (RuntimeException ignored) {
        }
    }
}
