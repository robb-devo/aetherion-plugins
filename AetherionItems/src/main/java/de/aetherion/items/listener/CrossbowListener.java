package de.aetherion.items.listener;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;

public final class CrossbowListener implements Listener {

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;

    public CrossbowListener(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (!itemManager.isCustomCrossbow(bow)) {
            return;
        }
        event.setCancelled(true);
        if (event.getProjectile() != null) {
            event.getProjectile().remove();
        }
        clearCharged(bow);
        fire(player, bow, Math.max(0.35d, event.getForce()));
    }

    private void fire(Player player, ItemStack bow, double force) {
        Vector velocity = player.getEyeLocation().getDirection().multiply(2.8 + force * 1.2);
        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        arrow.setShotFromCrossbow(true);
        arrow.setCritical(force >= 0.9d);
        arrow.setDamage(1.0);

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 24.0;
        }
        damage *= 0.45 + force * 0.55;

        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        if (force >= 0.9d && critChance > 0.0 && Math.random() * 100.0 < critChance) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            arrow.setCritical(true);
            de.aetherion.items.combat.DamageNumbers.tagCrit(arrow);
        }

        arrow.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(ItemKeys.damage(), PersistentDataType.DOUBLE, damage);

        player.getWorld().playSound(player.getEyeLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 0.95f);
        player.getWorld().spawnParticle(
                Particle.CRIT,
                player.getEyeLocation().add(player.getEyeLocation().getDirection()),
                6,
                0.05,
                0.05,
                0.05,
                0.02
        );
    }

    private void clearCharged(ItemStack bow) {
        if (bow == null || !(bow.getItemMeta() instanceof CrossbowMeta meta)) {
            return;
        }
        meta.setChargedProjectiles(List.of());
        bow.setItemMeta(meta);
    }
}
