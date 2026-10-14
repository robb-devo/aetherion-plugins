package net.aetherion.samuraiboss;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Soft-read of Aetherion language preference ({@code /language de}). No Core = everyone English.
 */
public final class Lang {

    private Lang() {
    }

    public static boolean isGerman(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Plugin core = Bukkit.getPluginManager().getPlugin("AetherionCore");
            if (core == null || !core.isEnabled()) {
                return false;
            }
            Class<?> services = Class.forName("de.aetherion.core.api.AetherServices");
            Object access = services.getMethod("lang").invoke(null);
            if (access == null) {
                return false;
            }
            Object result = access.getClass().getMethod("german", Player.class).invoke(access, player);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
