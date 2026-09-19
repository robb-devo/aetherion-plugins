package de.aetherion.pit.listener;

import de.aetherion.pit.AetherionPit;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/** Hub QoL: no chests/shulkers, no durability, no hunger (eat still works). */
public final class HubRulesListener implements Listener {

    private static final Set<Material> BLOCKED_BLOCKS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.ENDER_CHEST, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.BREWING_STAND,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
    );

    private final AetherionPit plugin;

    public HubRulesListener(AetherionPit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.safeZone().isPitWorld(player.getWorld())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        // Block compass / travel gadgets / pearl teleports
        ItemStack hand = event.getItem();
        if (hand != null) {
            Material type = hand.getType();
            if (type == Material.COMPASS
                    || type == Material.ENDER_PEARL
                    || type == Material.CHORUS_FRUIT
                    || type == Material.RECOVERY_COMPASS) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getClickedBlock() != null && BLOCKED_BLOCKS.contains(event.getClickedBlock().getType())) {
            event.setCancelled(true);
            return;
        }
        // Shulker in hand
        if (hand != null && isShulker(hand.getType())) {
            event.setCancelled(true);
        }
    }

    /** Kill DeluxeHub double-jump / fly-toggle launch pads. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!plugin.safeZone().isPitWorld(player.getWorld())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.safeZone().isPitWorld(player.getWorld()) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        // Allow our own GUIs (custom chest inventories).
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof de.aetherion.pit.menu.PitShopGUI.Holder
                || holder instanceof de.aetherion.pit.menu.HubDevMenu.Holder) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CHEST
                || type == InventoryType.ENDER_CHEST
                || type == InventoryType.SHULKER_BOX
                || type == InventoryType.BARREL
                || type == InventoryType.HOPPER
                || type == InventoryType.DROPPER
                || type == InventoryType.DISPENSER
                || type == InventoryType.FURNACE
                || type == InventoryType.BLAST_FURNACE
                || type == InventoryType.SMOKER
                || type == InventoryType.BREWING
                || type == InventoryType.ANVIL
                || type == InventoryType.ENCHANTING
                || type == InventoryType.WORKBENCH) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (plugin.safeZone().isPitWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.safeZone().isPitWorld(player.getWorld())) {
            return;
        }
        // Keep hunger full; golden apples etc. still apply their effects via consume.
        event.setCancelled(true);
        if (player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    private static boolean isShulker(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }
}
