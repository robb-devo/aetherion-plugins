package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class T2UniqueListener implements Listener, Runnable {

    private static final int STAFF_WINDOW = 80;
    private static final NamespacedKey POOL = ItemKeys.key("tech_staff_pool");
    private static final NamespacedKey DUMPING = ItemKeys.key("tech_staff_dump");

    private final ItemManager items;
    private final Map<UUID, Long> staffUntil = new ConcurrentHashMap<>();

    public T2UniqueListener(ItemManager items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onStaff(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!id(player.getInventory().getItemInMainHand()).equals("staff_of_technical_difficulties")) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        long tick = Bukkit.getCurrentTick();
        Long until = staffUntil.get(player.getUniqueId());
        if (until != null && tick < until) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5Already buffering."));
            return;
        }
        staffUntil.put(player.getUniqueId(), tick + STAFF_WINDOW);
        player.getPersistentDataContainer().set(POOL, PersistentDataType.DOUBLE, 0.0d);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§5Damage is a ticket now. 4s."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        Bukkit.getScheduler().runTaskLater(AetherionItems.getInstance(), () -> dump(player), STAFF_WINDOW);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbsorb(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Byte dumping = player.getPersistentDataContainer().get(DUMPING, PersistentDataType.BYTE);
        if (dumping != null && dumping == (byte) 1) {
            return;
        }
        Long until = staffUntil.get(player.getUniqueId());
        if (until == null || Bukkit.getCurrentTick() >= until) {
            return;
        }
        if (!id(player.getInventory().getItemInMainHand()).equals("staff_of_technical_difficulties")) {
            return;
        }
        double incoming = Math.max(event.getFinalDamage(), event.getDamage());
        double bank = incoming * 0.65;
        double leftover = incoming * 0.35;
        Double pooled = player.getPersistentDataContainer().get(POOL, PersistentDataType.DOUBLE);
        player.getPersistentDataContainer().set(POOL, PersistentDataType.DOUBLE, (pooled == null ? 0.0 : pooled) + bank);
        event.setDamage(Math.max(0.2, leftover));
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.1, 0), 8, 0.25, 0.3, 0.25, 0.2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThermalHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }
        if (!AccessoryItems.holdingOffhand(player, "thermal_core")) {
            return;
        }
        if (player.getHealth() <= 2.0) {
            return;
        }
        player.getPersistentDataContainer().set(DUMPING, PersistentDataType.BYTE, (byte) 1);
        try {
            player.damage(1.15);
        } finally {
            player.getPersistentDataContainer().remove(DUMPING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLedgerKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !AccessoryItems.holdingOffhand(killer, "insolvent_ledger")) {
            return;
        }
        var coins = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getCoins();
        if (coins == null) {
            return;
        }
        long pay = Math.max(8L, Math.round(event.getEntity().getMaxHealth() * 0.18));
        coins.add(killer, pay);
        killer.sendMessage("§6+" + pay + " coins §8(foreclosure)");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String itemId = id(event.getItemInHand());
        if (itemId.equals("void_vacuum_charm") || itemId.equals("thermal_core")
                || itemId.equals("pickaxe_core_of_the_burrower") || itemId.equals("insolvent_ledger")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        staffUntil.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!AccessoryItems.holdingOffhand(player, "void_vacuum_charm")) {
                continue;
            }
            if (charmsDown(player)) {
                continue;
            }
            for (Entity entity : player.getNearbyEntities(10, 6, 10)) {
                if (!(entity instanceof Item drop) || drop.getPickupDelay() > 8) {
                    continue;
                }
                Vector to = player.getLocation().add(0, 0.4, 0).toVector().subtract(drop.getLocation().toVector());
                if (to.lengthSquared() < 0.04) {
                    continue;
                }
                drop.setVelocity(to.normalize().multiply(0.55));
            }
        }
    }

    private void dump(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        staffUntil.remove(player.getUniqueId());
        Double pooled = player.getPersistentDataContainer().get(POOL, PersistentDataType.DOUBLE);
        player.getPersistentDataContainer().remove(POOL);
        if (pooled == null || pooled < 1.0) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7Nothing in the buffer."));
            return;
        }
        final double dumpDamage = pooled;
        LivingEntity target = null;
        double best = 64.0;
        for (Entity entity : player.getNearbyEntities(8, 5, 8)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player || living.isDead()) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(player.getLocation());
            if (dist < best) {
                best = dist;
                target = living;
            }
        }
        if (target == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7Buffer expired. No target."));
            return;
        }
        final LivingEntity hit = target;
        player.getPersistentDataContainer().set(DUMPING, PersistentDataType.BYTE, (byte) 1);
        try {
            de.aetherion.items.combat.ScriptedHits.run(() -> hit.damage(dumpDamage, player));
        } finally {
            player.getPersistentDataContainer().remove(DUMPING);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§5Dumped §f" + (int) Math.round(dumpDamage) + " §5into the complaint."
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 1.15f);
        hit.getWorld().spawnParticle(Particle.WITCH, hit.getLocation().add(0, 1, 0), 28, 0.4, 0.6, 0.4, 0.05);
    }

    private static boolean charmsDown(Player player) {
        Long until = player.getPersistentDataContainer().get(ItemKeys.charmSuppressedUntil(), PersistentDataType.LONG);
        return until != null && System.currentTimeMillis() < until;
    }

    private String id(ItemStack item) {
        String value = items.getItemId(item);
        return value == null ? "" : value.toLowerCase();
    }
}
