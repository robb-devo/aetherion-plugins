package de.aetherion.items.listener;

import de.aetherion.items.combat.UndeadCombat;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HealerSetListener implements Runnable {

    private final ItemManager itemManager;

    public HealerSetListener(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 100L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!player.isOnline() || player.isDead()) {
                    continue;
                }
                if (!UndeadCombat.wearingFullSet(player, itemManager, "healer_")) {
                    continue;
                }
                for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof Player nearby && nearby.isOnline() && !nearby.isDead()) {
                        heal(nearby);
                    }
                }
                heal(player);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void heal(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        double max = player.getMaxHealth();
        double current = player.getHealth();
        if (current < max) {
            player.setHealth(Math.min(max, current + 4.0));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.01);
        }
    }
}
