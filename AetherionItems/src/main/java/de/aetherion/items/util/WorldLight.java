package de.aetherion.items.util;

import org.bukkit.entity.Player;

public final class WorldLight {

    private WorldLight() {
    }

    public static boolean isDark(Player player) {
        if (player == null || player.getLocation().getWorld() == null) {
            return false;
        }
        byte light = player.getLocation().getBlock().getLightLevel();
        byte sky = player.getLocation().getBlock().getLightFromSky();
        long time = player.getWorld().getTime();
        boolean night = time >= 13000 && time <= 23000;
        return light <= 7 || sky <= 4 || night;
    }
}
