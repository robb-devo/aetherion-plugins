package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;

/**
 * Hold-to-fire shortbows. Vanilla draw/ammo is cancelled; arrows are spawned
 * by this listener and tagged so {@link DamageListener} can apply Aetherion damage.
 */
public class ShortbowListener implements Listener, Runnable {

    private static final int HOLD_TIMEOUT_TICKS = 8;

    private static final int ENDERMAN_PIN_TICKS = 10;

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, FireState> firing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextShotTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pinnedEndermen = new ConcurrentHashMap<>();
    private final Set<UUID> forcingEndermanHit = ConcurrentHashMap.newKeySet();

    public ShortbowListener(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = itemIn(player, hand);

        if (!itemManager.isShortbow(item)) {
            return;
        }
        // Cascade Shortbow ulti: sneak+RC handled by TestGearListener
        String shortId = itemManager.getItemId(item);
        if (player.isSneaking() && "cascade_shortbow".equalsIgnoreCase(shortId)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }

        long tick = Bukkit.getCurrentTick();
        firing.put(player.getUniqueId(), new FireState(hand, tick));
        tryFire(player, item, tick);

        try {
            player.startUsingItem(hand);
        } catch (RuntimeException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVanillaShoot(EntityShootBowEvent event) {
        if (itemManager.isShortbow(event.getBow())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        if (itemManager.isShortbow(event.getItem())) {
            firing.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        firing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        firing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        firing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        firing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        firing.remove(id);
        nextShotTick.remove(id);
    }

    @Override
    public void run() {
        long tick = Bukkit.getCurrentTick();

        firing.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                return true;
            }

            FireState state = entry.getValue();
            ItemStack item = itemIn(player, state.hand);
            if (!itemManager.isShortbow(item)) {
                return true;
            }

            boolean using = player.hasActiveItem() && itemManager.isShortbow(player.getActiveItem());
            if (!using && tick - state.lastInputTick > HOLD_TIMEOUT_TICKS) {
                return true;
            }

            tryFire(player, item, tick);
            return false;
        });
        long now = tick;
        pinnedEndermen.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < now);
    }

    private void tryFire(Player player, ItemStack bow, long tick) {
        UUID id = player.getUniqueId();
        int interval = de.aetherion.items.dungeon.DungeonArmor.assassinShotInterval(
                player,
                itemManager,
                itemManager.getShortbowIntervalTicks(bow)
        );
        if (de.aetherion.items.item.AccessoryItems.holdingOffhand(player, "thermal_core")) {
            interval = Math.max(2, interval - 4);
        }
        Long next = nextShotTick.get(id);
        if (next != null && tick < next) {
            return;
        }
        if (itemManager.needsAmmo(bow) && !ArrowAmmo.consume(player)) {
            nextShotTick.put(id, tick + 12);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.65f);
            return;
        }

        nextShotTick.put(id, tick + interval);
        launch(player, bow);
    }

    private void launch(Player player, ItemStack bow) {
        String itemId = itemManager.getItemId(bow);
        int volley = "skuldugery_shortbow".equalsIgnoreCase(itemId == null ? "" : itemId)
                ? DungeonCore.shortbowVolley(DungeonCore.tier(bow))
                : 1;
        volley = Math.max(1, volley);

        Vector base = player.getEyeLocation().getDirection();
        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 8.0;
        }
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        boolean crit = critChance > 0.0 && Math.random() * 100.0 < critChance;
        if (crit) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
        }

        for (int i = 0; i < volley; i++) {
            double offset = 0.0;
            if (volley == 2) {
                offset = i == 0 ? -0.08 : 0.08;
            } else if (volley >= 3) {
                offset = (i - 1) * 0.12;
            }
            spawnArrow(
                    player,
                    yawOffset(base, offset),
                    damage,
                    crit,
                    "skuldugery_shortbow".equalsIgnoreCase(itemId == null ? "" : itemId)
                            ? DungeonCore.tier(bow)
                            : 0
            );
        }

        player.getWorld().playSound(
                player.getEyeLocation(),
                Sound.ENTITY_ARROW_SHOOT,
                0.85f,
                1.45f
        );
        player.getWorld().spawnParticle(
                Particle.FLAME,
                player.getEyeLocation().add(player.getEyeLocation().getDirection()),
                3,
                0.05,
                0.05,
                0.05,
                0.01
        );
    }

    private void spawnArrow(Player player, Vector velocity, double damage, boolean crit, int coreTier) {
        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(player);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(crit);
        arrow.setShotFromCrossbow(false);
        arrow.setDamage(1.0);
        int pierce = DungeonCore.shortbowPierce(coreTier);
        arrow.setPierceLevel((byte) Math.max(0, Math.min(127, pierce)));
        if (crit) {
            de.aetherion.items.combat.DamageNumbers.tagCrit(arrow);
        }
        arrow.getPersistentDataContainer().set(
                ItemKeys.shortbow(),
                PersistentDataType.BYTE,
                (byte) 1
        );
        arrow.getPersistentDataContainer().set(
                ItemKeys.damage(),
                PersistentDataType.DOUBLE,
                damage
        );
        if (pierce > 0) {
            arrow.getPersistentDataContainer().set(
                    ItemKeys.shortbowPierce(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        }
        if (DungeonCore.shortbowHitsEndermen(coreTier) || skuldugeryBow(player)) {
            arrow.getPersistentDataContainer().set(
                    ItemKeys.shortbowEnderman(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        }
    }

    private boolean skuldugeryBow(Player player) {
        String id = itemManager.getItemId(player.getInventory().getItemInMainHand());
        return id != null && id.toLowerCase(java.util.Locale.ROOT).contains("skuldugery");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEndermanArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !isEndermanArrow(arrow)) {
            return;
        }
        if (!(event.getHitEntity() instanceof Enderman enderman)) {
            return;
        }
        pinEnderman(enderman);
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }
        Double stored = arrow.getPersistentDataContainer().get(ItemKeys.damage(), PersistentDataType.DOUBLE);
        double damage = stored == null || stored <= 0.0 ? 8.0 : stored;
        UUID id = enderman.getUniqueId();
        if (!forcingEndermanHit.add(id)) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        try {
            enderman.damage(damage, player);
        } finally {
            if (plugin != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> forcingEndermanHit.remove(id));
            } else {
                forcingEndermanHit.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent event) {
        if (event.getEntity() != null && blockedBySkuldugery(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Enderman enderman && blockedBySkuldugery(enderman)) {
            event.setCancelled(true);
        }
    }

    private void pinEnderman(Enderman enderman) {
        pinnedEndermen.put(enderman.getUniqueId(), (long) Bukkit.getCurrentTick() + ENDERMAN_PIN_TICKS);
    }

    private boolean blockedBySkuldugery(Enderman enderman) {
        Long until = pinnedEndermen.get(enderman.getUniqueId());
        if (until != null && Bukkit.getCurrentTick() <= until) {
            return true;
        }
        if (until != null) {
            pinnedEndermen.remove(enderman.getUniqueId());
        }
        for (Entity nearby : enderman.getNearbyEntities(24.0, 24.0, 24.0)) {
            if (nearby instanceof Arrow arrow && isEndermanArrow(arrow)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEndermanArrow(Arrow arrow) {
        Byte tagged = arrow.getPersistentDataContainer().get(
                ItemKeys.shortbowEnderman(),
                PersistentDataType.BYTE
        );
        return tagged != null && tagged == 1;
    }

    private static Vector yawOffset(Vector direction, double yaw) {
        Vector copy = direction.clone();
        if (Math.abs(yaw) < 0.0001) {
            return copy.normalize().multiply(3.15);
        }
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double x = copy.getX() * cos - copy.getZ() * sin;
        double z = copy.getX() * sin + copy.getZ() * cos;
        return new Vector(x, copy.getY(), z).normalize().multiply(3.15);
    }

    private static ItemStack itemIn(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    private record FireState(EquipmentSlot hand, long lastInputTick) {
    }
}
