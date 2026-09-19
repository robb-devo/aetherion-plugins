package de.aetherion.items.listener;

import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


/*
 * =========================================================
 * AETHERION ITEM PROTECTION LISTENER
 * =========================================================
 *
 * Verhindert, dass Aetherion-Items vom Spieler
 * aus dem Inventar gedroppt werden können.
 *
 * Normale Minecraft-Items bleiben vollständig
 * normal droppbar.
 *
 */
public class ItemProtectionListener implements Listener {


    /*
     * =========================================================
     * ITEM MANAGER
     * =========================================================
     */

    private final ItemManager itemManager;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public ItemProtectionListener(
            ItemManager itemManager
    ) {

        this.itemManager = itemManager;

    }


    /*
     * =========================================================
     * ITEM DROP
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onItemDrop(
            PlayerDropItemEvent event
    ) {

        ItemStack item =
                event.getItemDrop()
                        .getItemStack();


        /*
         * =====================================================
         * KEIN ITEM
         * =====================================================
         */

        if (
                item == null
                        || item.getType().isAir()
        ) {

            return;

        }


        /*
         * =====================================================
         * MUSS AETHERION ITEM SEIN
         * =====================================================
         */

        if (!itemManager.isAetherionItem(item)) {
            return;
        }

        event.setCancelled(true);

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String itemId = itemManager.getItemId(item);
        if (itemId == null) {
            return;
        }
        if (itemId.startsWith("compressed_")
                || itemId.startsWith("compacted_")
                || itemManager.isWand(item)
                || AccessoryItems.isCharm(itemId)) {
            event.setCancelled(true);
            if (itemId.startsWith("compressed_") || itemId.startsWith("compacted_")) {
                event.getPlayer().sendMessage("§cCompressed and compacted items cannot be placed.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCharmUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!AccessoryItems.isCharm(itemManager.getItemId(stack))) {
            return;
        }
        Material type = stack.getType();
        if (!type.isBlock() && type != Material.WHEAT_SEEDS && !type.name().endsWith("_SEEDS")
                && type != Material.COCOA_BEANS && type != Material.SWEET_BERRIES) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

}