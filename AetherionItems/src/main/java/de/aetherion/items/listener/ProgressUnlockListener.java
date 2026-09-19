package de.aetherion.items.listener;

import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.progress.UnlockToast;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * World crafting tables / anvils are redirects — real stations live in the Manager.
 * Spawn unlocker pickup still grants Spawns.
 */
public final class ProgressUnlockListener implements Listener {

    private final ProgressionService progress;

    public ProgressUnlockListener(ProgressionService progress) {
        this.progress = progress;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        Player player = event.getPlayer();

        if (type == Material.CRAFTING_TABLE) {
            event.setCancelled(true);
            if (!progress.craftingTable(player)) {
                player.sendMessage(progress.hint(ProgressionService.Flag.WORKBENCH));
            } else {
                player.sendMessage("§eCrafting runs through the §fAetherion Manager §e→ Crafting Table.");
            }
            return;
        }

        if (type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL) {
            event.setCancelled(true);
            if (!progress.anvil(player)) {
                player.sendMessage(progress.hint(ProgressionService.Flag.ANVIL));
            } else {
                player.sendMessage("§eAnvils run through the §fAetherion Manager §e→ Anvil.");
            }
            return;
        }

        if (isSpawnUnlocker(event.getItem()) && progress.unlock(player, ProgressionService.Flag.SPAWN_UNLOCKER)) {
            UnlockToast.show(player, "Spawns", "The map just got opinions");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isSpawnUnlocker(event.getItem().getItemStack())) {
            return;
        }
        if (progress.unlock(player, ProgressionService.Flag.SPAWN_UNLOCKER)) {
            UnlockToast.show(player, "Spawns", "The map just got opinions");
        }
    }

    private static boolean isSpawnUnlocker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        for (var key : meta.getPersistentDataContainer().getKeys()) {
            if ("unlock_spawn".equals(key.getKey()) && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                return true;
            }
        }
        return false;
    }
}
