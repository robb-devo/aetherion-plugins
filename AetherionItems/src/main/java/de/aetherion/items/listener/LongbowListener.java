package de.aetherion.items.listener;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;

/**
 * Slow-draw longbows. Vanilla shoot is cancelled; a charged arrow
 * fires on release and consumes ammo when the item asks for it.
 */
public final class LongbowListener implements Listener {

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> started = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> charged = new ConcurrentHashMap<>();

    public LongbowListener(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!itemManager.isLongbow(bow)) {
            return;
        }
        started.putIfAbsent(player.getUniqueId(), (long) Bukkit.getCurrentTick());
        charged.putIfAbsent(player.getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVanillaShoot(EntityShootBowEvent event) {
        if (itemManager.isLongbow(event.getBow())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        ItemStack bow = event.getItem();
        if (!itemManager.isLongbow(bow)) {
            return;
        }
        Long start = started.remove(player.getUniqueId());
        charged.remove(player.getUniqueId());
        if (start == null) {
            return;
        }
        long held = Math.max(0L, Bukkit.getCurrentTick() - start);
        if (held < 8L) {
            return;
        }
        int needed = de.aetherion.items.dungeon.DungeonArmor.assassinShotInterval(
                player,
                itemManager,
                itemManager.getLongbowChargeTicks(bow)
        );
        double force = Math.min(1.0d, held / (double) needed);
        fire(player, bow, force);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    public void tick() {
        long tick = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Long start = started.get(id);
            if (start == null) {
                continue;
            }
            ItemStack bow = player.getInventory().getItemInMainHand();
            if (!itemManager.isLongbow(bow) || !player.hasActiveItem()) {
                continue;
            }
            int needed = de.aetherion.items.dungeon.DungeonArmor.assassinShotInterval(
                player,
                itemManager,
                itemManager.getLongbowChargeTicks(bow)
        );
            if (tick - start >= needed && !Boolean.TRUE.equals(charged.get(id))) {
                charged.put(id, true);
                player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.7f, 0.7f);
                player.getWorld().spawnParticle(Particle.CRIT, player.getEyeLocation(), 6, 0.15, 0.15, 0.15, 0.02);
            }
        }
    }

    private void fire(Player player, ItemStack bow, double force) {
        if (itemManager.needsAmmo(bow) && !ArrowAmmo.consume(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            player.sendMessage("§7You need arrows.");
            return;
        }
        Vector velocity = player.getEyeLocation().getDirection().multiply(2.4 + force * 1.4);
        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        arrow.setCritical(force >= 0.95d);
        arrow.setShotFromCrossbow(false);
        arrow.setDamage(1.0);
        arrow.setPierceLevel(itemId(bow).contains("hollow") ? 1 : 0);

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 16.0;
        }
        damage *= 0.35 + force * 0.65;

        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        boolean crit = force >= 0.95d && critChance > 0.0 && Math.random() * 100.0 < critChance;
        if (crit) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            arrow.setCritical(true);
            de.aetherion.items.combat.DamageNumbers.tagCrit(arrow);
        }

        arrow.getPersistentDataContainer().set(ItemKeys.shortbow(), PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(ItemKeys.damage(), PersistentDataType.DOUBLE, damage);

        player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.75f + (float) force * 0.25f);
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                player.getEyeLocation().add(player.getEyeLocation().getDirection()),
                4,
                0.05,
                0.05,
                0.05,
                0.01
        );
    }

    private String itemId(ItemStack bow) {
        String id = itemManager.getItemId(bow);
        return id == null ? "" : id.toLowerCase();
    }

    private void clear(UUID id) {
        started.remove(id);
        charged.remove(id);
    }
}
