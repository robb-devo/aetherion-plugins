package de.aetherion.items.listener;

import de.aetherion.items.economy.ItemValueService;
import de.aetherion.items.economy.ValueLore;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class PickupDropsListener implements Listener {

    private final ItemValueService values;

    public PickupDropsListener(ItemValueService values) {
        this.values = values;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        if (entity.hasMetadata("NPC")) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        for (ItemStack drop : drops) {
            InventoryDrops.give(killer, drop);
        }
        int exp = event.getDroppedExp();
        if (exp > 0) {
            killer.giveExp(exp);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ValueLore.apply(event.getItem().getItemStack(), values);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        stamp(event.getInventory());
        stamp(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        stamp(event.getPlayer().getInventory());
    }

    private void stamp(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            ValueLore.apply(item, values);
        }
    }
}
