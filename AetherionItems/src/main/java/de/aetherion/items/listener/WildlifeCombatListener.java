package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.FenceService;
import de.aetherion.items.economy.GearTraderService;
import de.aetherion.items.economy.MarketService;
import de.aetherion.items.economy.TraderService;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * WorldGuard {@code build: deny} blocks hits on animals and some mobs.
 * For now every player may hit wildlife. NPCs, traders, pets and minions stay protected.
 */
public final class WildlifeCombatListener implements Listener {

    private static final NamespacedKey PET_KEY = AetherKeys.PET_ENTITY;
    private static final NamespacedKey QUEST_NPC = AetherKeys.QUEST_NPC;
    private static final NamespacedKey DUNGEON_NPC = AetherKeys.DUNGEON_NPC;
    private static final NamespacedKey BOSS_MINION = AetherKeys.BOSS_MINION;
    private static final NamespacedKey SET_MINION = AetherKeys.SET_MINION;

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = false
    )
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event);
        if (attacker == null) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!allowsPlayerHit(attacker, target)) {
            return;
        }
        if (target.isInvulnerable()) {
            target.setInvulnerable(false);
        }
        event.setCancelled(false);
    }

    public static boolean allowsPlayerHit(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return false;
        }
        return !isProtected(target);
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        Player owner = helperOwner(damager);
        if (owner != null) {
            return owner;
        }
        return null;
    }

    private static Player helperOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        var data = entity.getPersistentDataContainer();
        String minionOwner = data.get(
                AetherKeys.SET_MINION_OWNER,
                PersistentDataType.STRING
        );
        if (minionOwner != null && !minionOwner.isBlank()) {
            try {
                return org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(minionOwner));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isProtected(LivingEntity target) {
        if (target instanceof Player
                || target instanceof ArmorStand
                || target instanceof Villager
                || target instanceof WanderingTrader) {
            return true;
        }
        if (target.hasMetadata("NPC")) {
            return true;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return true;
        }
        if (TraderService.isTrader(target)
                || GearTraderService.isGearTrader(target)
                || FenceService.isFence(target)
                || MarketService.isMarketNpc(target)) {
            return true;
        }
        if (target.getScoreboardTags().contains("dungeon_keeper")) {
            return true;
        }
        var data = target.getPersistentDataContainer();
        return data.has(PET_KEY, PersistentDataType.BYTE)
                || data.has(QUEST_NPC, PersistentDataType.STRING)
                || data.has(DUNGEON_NPC, PersistentDataType.STRING)
                || data.has(BOSS_MINION, PersistentDataType.STRING)
                || data.has(SET_MINION, PersistentDataType.BYTE)
                || data.has(ItemKeys.traderNpc(), PersistentDataType.STRING)
                || data.has(ItemKeys.gearShop(), PersistentDataType.STRING)
                || data.has(ItemKeys.bazaarNpc(), PersistentDataType.STRING)
                || data.has(ItemKeys.auctionNpc(), PersistentDataType.STRING);
    }
}
