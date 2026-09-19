package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.chest.MerchantChestService;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MerchantChestListener implements Listener {

    private final MerchantChestService chests;

    public MerchantChestListener(MerchantChestService chests) {
        this.chests = chests;
    }

    public static NamespacedKey placerKey() {
        return new NamespacedKey(AetherionQuests.getInstance(), "merchant_chest_placer");
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Merchant Sample Chest");
            meta.setLore(List.of(
                    "§7DEV · place beside the merchant.",
                    "§7First loot during §fOpen Up§7.",
                    "§7After turn-in: random booster every §f12h§7.",
                    "§8No chat needed — just open the chest.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + click the chest §7to remove."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(placerKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPlacer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(placerKey(), PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isPlacer(hand) && player.hasPermission("aetherionquests.admin")) {
            event.setCancelled(true);
            if (action != Action.RIGHT_CLICK_BLOCK || block == null) {
                return;
            }
            if (player.isSneaking() && chests.isMerchantChest(block)) {
                chests.remove(player, block);
                return;
            }
            if (player.isSneaking()) {
                player.sendMessage("§cSneak-click the sample chest to remove it.");
                return;
            }
            chests.place(player, block, event.getBlockFace());
            return;
        }
        if (block == null || !chests.isMerchantChest(block)) {
            return;
        }
        event.setCancelled(true);
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        chests.tryOpen(player, block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!chests.isMerchantChest(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasPermission("aetherionquests.admin") && isPlacer(player.getInventory().getItemInMainHand()) && player.isSneaking()) {
            chests.remove(player, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) {
            return;
        }
        if (chests.isMerchantChest(state.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof BlockState state && chests.isMerchantChest(state.getBlock())) {
            event.setCancelled(true);
        }
        if (event.getDestination().getHolder() instanceof BlockState state && chests.isMerchantChest(state.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (chests.isMerchantChest(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (chests.isMerchantChest(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(chests::isMerchantChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(chests::isMerchantChest);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        chests.restoreChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
