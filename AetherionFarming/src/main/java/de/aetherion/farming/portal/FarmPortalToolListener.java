package de.aetherion.farming.portal;

import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class FarmPortalToolListener implements Listener {

    private final FarmPortalService portals;

    public FarmPortalToolListener(FarmPortalService portals) {
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!portals.isHubPortalTool(item)) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDev only.");
            return;
        }
        event.setCancelled(true);
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (player.isSneaking()) {
            portals.clearHubPortal();
            player.sendMessage("§eHub farm portal cleared.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
            return;
        }
        String message = portals.placeHubPortal(player, clicked);
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.9f, 1.2f);
    }
}
