package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.storage.BoosterSackMenu;
import de.aetherion.items.storage.SackInventory;
import de.aetherion.items.storage.SackItems;
import de.aetherion.items.storage.SackType;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SackListener implements Listener {

    private final SackInventory sacks;
    private final ItemManager items;

    public SackListener(AetherionItems plugin, SackInventory sacks) {
        this.sacks = sacks;
        this.items = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpen(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!SackItems.isSack(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        event.setCancelled(true);
        SackItems.ensureId(item);
        sacks.open(player, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (SackItems.isSack(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();

        if (top.getHolder() instanceof BoosterSackMenu.Holder boosterHolder) {
            event.setCancelled(true);
            boolean topClick = event.getClickedInventory() != null && event.getClickedInventory().equals(top);
            // Only handle top clicks; block shift/number into the menu.
            if (topClick || event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY) {
                if (topClick) {
                    sacks.boosters().handleClick(player, boosterHolder, event.getRawSlot(), true);
                }
            }
            return;
        }

        if (!(top.getHolder() instanceof SackInventory.Holder holder)) {
            return;
        }
        SackType type = holder.type();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        Inventory clicked = event.getClickedInventory();

        if (clicked != null && clicked.equals(top)) {
            if (isInsert(event.getAction()) && !type.accepts(cursor, items)) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbar = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                if (!type.accepts(hotbar, items) && hotbar != null && !hotbar.getType().isAir()) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (event.isShiftClick() && clicked != null && !clicked.equals(top)) {
            if (!type.accepts(current, items)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BoosterSackMenu.Holder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof SackInventory.Holder holder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        if (!holder.type().accepts(event.getOldCursor(), items)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BoosterSackMenu.Holder boosterHolder) {
            sacks.boosters().save(boosterHolder);
            return;
        }
        if (event.getInventory().getHolder() instanceof SackInventory.Holder holder) {
            sacks.save(holder);
        }
    }

    private static boolean isInsert(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR;
    }
}
