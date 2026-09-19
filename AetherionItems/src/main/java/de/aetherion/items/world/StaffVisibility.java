package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffVisibility {

    private StaffVisibility() {
    }

    public static boolean isStaff(Player player) {
        return player != null && (player.isOp() || player.hasPermission("aetherion.dev"));
    }

    public static void apply(JavaPlugin plugin, Entity entity) {
        if (plugin == null || entity == null || !entity.isValid()) {
            return;
        }
        entity.setVisibleByDefault(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(plugin, player, entity);
        }
    }

    public static void apply(JavaPlugin plugin, Player player, Entity entity) {
        if (plugin == null || player == null || entity == null || !entity.isValid()) {
            return;
        }
        entity.setVisibleByDefault(false);
        if (isStaff(player)) {
            player.showEntity(plugin, entity);
        } else {
            player.hideEntity(plugin, entity);
        }
    }
}
