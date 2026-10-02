package de.aetherion.farming.dev;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * DEV tool — plants a short sugar-cane strip. Place yourself on Farm Isle.
 */
public final class CanePatchTool implements Listener {

    public static final String TOOL_KEY = "cane_patch_tool";

    private final AetherionFarming plugin;
    private final NamespacedKey key;

    public CanePatchTool(AetherionFarming plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, TOOL_KEY);
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.SUGAR_CANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aCane Patch §8(DEV)");
            meta.setLore(List.of(
                    "§7Right-click ground → plant a cane row",
                    "§7facing you (length 8 · height 3).",
                    "§eSneak-click §7plants a wider 3-row patch.",
                    "",
                    "§8Admin tool — not consumed"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            return;
        }
        event.setCancelled(true);
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        BlockFace face = event.getBlockFace();
        Block start = face == BlockFace.UP ? clicked.getRelative(BlockFace.UP) : clicked.getRelative(face);
        BlockFace along = look(player);
        int rows = player.isSneaking() ? 3 : 1;
        int planted = plant(start, along, 8, 3, rows);
        player.sendMessage("§aCane patch §8· §f" + planted + " §7canes planted.");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_GRASS_PLACE, 0.7f, 1.1f);
    }

    private int plant(Block start, BlockFace along, int length, int height, int rows) {
        BlockFace right = rightOf(along);
        int count = 0;
        for (int row = 0; row < rows; row++) {
            int side = row - (rows / 2);
            for (int i = 0; i < length; i++) {
                Block base = start.getRelative(along, i).getRelative(right, side);
                Block ground = base.getRelative(BlockFace.DOWN);
                if (!isSoil(ground.getType()) && !isSoil(base.getType())) {
                    continue;
                }
                Block plantAt = isSoil(base.getType()) ? base.getRelative(BlockFace.UP) : base;
                for (int y = 0; y < height; y++) {
                    Block at = plantAt.getRelative(0, y, 0);
                    if (!at.getType().isAir() && at.getType() != Material.SUGAR_CANE) {
                        break;
                    }
                    at.setType(Material.SUGAR_CANE, false);
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isSoil(Material type) {
        return type == Material.DIRT
                || type == Material.GRASS_BLOCK
                || type == Material.SAND
                || type == Material.RED_SAND
                || type == Material.MUD
                || type == Material.FARMLAND
                || type == Material.PODZOL
                || type == Material.COARSE_DIRT;
    }

    private static BlockFace look(Player player) {
        float yaw = player.getLocation().getYaw();
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) {
            return BlockFace.SOUTH;
        }
        if (rot < 135) {
            return BlockFace.WEST;
        }
        if (rot < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private static BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }
}
