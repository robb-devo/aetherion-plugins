package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.chest.ExploreChestKind;
import de.aetherion.quests.chest.ExploreChestService;

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

public final class ExploreChestListener implements Listener {

    private final ExploreChestService chests;

    public ExploreChestListener(ExploreChestService chests) {
        this.chests = chests;
    }

    public static NamespacedKey placerKey() {
        return new NamespacedKey(AetherionQuests.getInstance(), "explore_chest_placer");
    }

    public static ItemStack create(String kindId) {
        return create(ExploreChestKind.fromId(kindId));
    }

    public static ItemStack create(ExploreChestKind kind) {
        if (kind == null) {
            return null;
        }
        ItemStack item = new ItemStack(kind.block());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kind.chat() + kind.display());
            meta.setLore(List.of(
                    "§7DEV · exploration crate.",
                    "§7Each player, this crate, every §f12h§7.",
                    "§8Spin animation — no vanilla inventory.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + click the chest §7to remove."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(placerKey(), PersistentDataType.STRING, kind.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ExploreChestKind placerKind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return ExploreChestKind.fromId(
                item.getItemMeta().getPersistentDataContainer().get(placerKey(), PersistentDataType.STRING));
    }

    public static boolean isPlacer(ItemStack item) {
        return placerKind(item) != null;
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
        ExploreChestKind placer = placerKind(hand);
        if (placer != null && player.hasPermission("aetherionquests.admin")) {
            event.setCancelled(true);
            if (action != Action.RIGHT_CLICK_BLOCK || block == null) {
                return;
            }
            if (player.isSneaking() && chests.isExploreChest(block)) {
                chests.remove(player, block);
                return;
            }
            if (player.isSneaking()) {
                player.sendMessage("§cSneak-click the exploration chest to remove it.");
                return;
            }
            chests.place(player, block, event.getBlockFace(), placer);
            return;
        }
        if (block == null || !chests.isExploreChest(block)) {
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
        if (!chests.isExploreChest(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasPermission("aetherionquests.admin")
                && isPlacer(player.getInventory().getItemInMainHand())
                && player.isSneaking()) {
            chests.remove(player, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) {
            return;
        }
        if (chests.isExploreChest(state.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof BlockState state && chests.isExploreChest(state.getBlock())) {
            event.setCancelled(true);
        }
        if (event.getDestination().getHolder() instanceof BlockState state && chests.isExploreChest(state.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (chests.isExploreChest(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (chests.isExploreChest(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(chests::isExploreChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(chests::isExploreChest);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        chests.restoreChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
