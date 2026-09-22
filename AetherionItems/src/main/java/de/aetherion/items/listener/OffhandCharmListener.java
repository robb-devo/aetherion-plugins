package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Locale;

/**
 * The off-hand accepts Charms only. Anything else is cancelled or moved back.
 */
public final class OffhandCharmListener implements Listener {

    private final AetherionItems plugin;
    private final ItemManager items;

    public OffhandCharmListener(AetherionItems plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    public static boolean isCharm(ItemManager items, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        if (items == null) {
            return false;
        }
        String id = items.getItemId(stack);
        if (id == null || id.isBlank()) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.startsWith("charm_") || lower.endsWith("_charm");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!isCharm(items, event.getOffHandItem())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§7Off-hand holds §dCharms §7only.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack moving = event.getCurrentItem();
            if (!isCharm(items, moving)) {
                event.setCancelled(true);
                player.sendMessage("§7Off-hand holds §dCharms §7only.");
            }
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == 40) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
                if (!isCharm(items, hotbar)) {
                    event.setCancelled(true);
                    player.sendMessage("§7Off-hand holds §dCharms §7only.");
                }
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isCharm(items, cursor)) {
                event.setCancelled(true);
                player.sendMessage("§7Off-hand holds §dCharms §7only.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> eject(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> eject(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> eject(player));
        }
    }

    private void eject(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isCharm(items, offhand)) {
            return;
        }
        player.getInventory().setItemInOffHand(null);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(offhand);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.sendMessage("§7Off-hand holds §dCharms §7only. Moved that back.");
    }
}
