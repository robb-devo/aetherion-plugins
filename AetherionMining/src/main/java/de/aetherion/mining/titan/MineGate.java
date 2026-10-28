package de.aetherion.mining.titan;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Mining-level doors (Items' skill level, read like the Veins Foreman does). Staff walk through. */
public final class MineGate {

    private MineGate() {
    }

    public static boolean reaches(Player player, int level) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("aetherion.mines.admin")) {
            return true;
        }
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return true;
        }
        try {
            Object skills = items.getClass().getMethod("getSkills").invoke(items);
            if (skills == null) {
                return true;
            }
            Object value = skills.getClass().getMethod("miningLevel", Player.class).invoke(skills, player);
            return value instanceof Number number && number.intValue() >= level;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}
