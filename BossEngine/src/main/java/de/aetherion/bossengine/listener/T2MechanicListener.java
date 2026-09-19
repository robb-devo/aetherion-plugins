package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.manager.BossManager;
import de.aetherion.bossengine.skill.t2.T2Mechanics;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.GameMode;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public class T2MechanicListener implements Listener {

    private final BossManager bosses;
    private final BossKeys keys;

    public T2MechanicListener(BossManager bosses, BossKeys keys) {
        this.bosses = bosses;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        T2Mechanics.clearInvert(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        for (BossInstance instance : bosses.getOccupying()) {
            if (instance.clickFrostHearth(event.getPlayer(), event.getRightClicked())) {
                event.setCancelled(true);
                return;
            }
        }
        if (keys.isBeamFx(event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (T2Mechanics.clickProp(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        for (BossInstance instance : bosses.getOccupying()) {
            if (instance.clickFrostHearth(player, event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
        if (keys.isBeamFx(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (T2Mechanics.clickProp(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(EntityDamageEvent event) {
        BossInstance instance = bosses.getByEntity(event.getEntity()).orElse(null);
        if (instance == null || !instance.isOverheated()) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile projectile) {
            // Heat haze: ranged still works from farther out, but at reduced bite.
            double softened = Math.max(1.0, Math.max(event.getDamage(), event.getFinalDamage()) * 0.55);
            event.setDamage(softened);
            projectile.getWorld().playSound(projectile.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 0.45f, 1.35f);
            projectile.getWorld().spawnParticle(
                    org.bukkit.Particle.LAVA,
                    projectile.getLocation(),
                    4,
                    0.15,
                    0.15,
                    0.15,
                    0
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanWarp(EntityTeleportEvent event) {
        BossInstance instance = bosses.getByEntity(event.getEntity()).orElse(null);
        if (instance == null) {
            return;
        }
        String id = instance.getTemplate().getId();
        if ("lobby_cleaner".equalsIgnoreCase(id) && !instance.isInternalTeleport()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent event) {
        if (!(event.getEntity() instanceof Enderman enderman)) {
            return;
        }
        BossInstance instance = bosses.getByEntity(enderman).orElse(null);
        if (instance != null && "lobby_cleaner".equalsIgnoreCase(instance.getTemplate().getId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || BossHits.isApplying(player)) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (!keys.isMinion(source)) {
            return;
        }
        if (!source.getScoreboardTags().contains("debt_collector")
                && (source.getCustomName() == null || !source.getCustomName().contains("IOU"))) {
            return;
        }
        T2Mechanics.debtHit(player, source);
    }
}
