package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class SquidsBootListener implements Runnable {

    private static final double RANGE = 4.5;
    private static final double PERCENT = 0.01;
    private static final double CAP = 50.0;

    private final ItemManager itemManager;

    public SquidsBootListener(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            ItemStack boots = player.getInventory().getBoots();
            if (!isSquidsBoot(boots)) {
                continue;
            }
            pulse(player, boots);
        }
    }

    private void pulse(Player player, ItemStack boots) {
        int tier = de.aetherion.items.item.DungeonCore.tier(boots);
        double range = de.aetherion.items.item.DungeonCore.squidRange(tier);
        double percent = de.aetherion.items.item.DungeonCore.squidPercent(tier);
        double cap = de.aetherion.items.item.DungeonCore.squidCap(tier);
        boolean hit = false;
        for (var entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living, player)) {
                continue;
            }
            double amount = Math.min(cap, Math.max(1.0, living.getHealth() * percent));
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(player)
                    .build();
            living.damage(amount, source);
            living.getWorld().spawnParticle(
                    Particle.SQUID_INK,
                    living.getLocation().add(0, living.getHeight() * 0.5, 0),
                    4,
                    0.2,
                    0.2,
                    0.2,
                    0.01
            );
            hit = true;
        }
        if (hit) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 0.35f, 1.35f);
        }
    }

    private boolean isValidTarget(LivingEntity target, Player player) {
        if (target == null || target.equals(player) || !target.isValid() || target.isDead()) {
            return false;
        }
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (!(target instanceof Enemy)) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !AetherEntities.isPet(target);
    }

    private boolean isSquidsBoot(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.LEATHER_BOOTS) {
            return false;
        }
        return "squids_boot".equalsIgnoreCase(itemManager.getItemId(item));
    }
}
