package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.farming.Crops;
import de.aetherion.items.item.FarmingHoeProgress;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Harvest = break extra neighbouring crops only.
 * Fortune on crops is applied once here: 100 Fortune → +1 item. Base yield is 1.
 */
public class CropHarvestListener implements Listener {

    public static final String CROP_BREAK_METADATA = "aetherion_crop_break";

    private static final BlockFace[] NEIGHBORS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
    };

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Set<String> harvesting = new HashSet<>();

    public CropHarvestListener(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropDrop(BlockDropItemEvent event) {
        if (!Crops.isCrop(event.getBlockState().getType())) {
            return;
        }
        Player player = event.getPlayer();
        double fortune = Math.max(0, equipmentStats.getStat(player, ItemCapability.FORTUNE));
        event.setCancelled(true);

        InventoryDrops.setDropAt(event.getBlock().getLocation());
        try {
            boolean paidPrimary = false;
            for (org.bukkit.entity.Item dropEntity : event.getItems()) {
                if (dropEntity == null) {
                    continue;
                }
                ItemStack stack = Crops.normalizeYield(dropEntity.getItemStack());
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                if (Crops.isPrimaryYield(stack.getType())) {
                    if (paidPrimary) {
                        continue;
                    }
                    paidPrimary = true;
                    giveFlatFortuned(player, stack, fortune);
                    continue;
                }
                // Rare extras (poisonous potato, …) — keep, no fortune stack abuse.
                InventoryDrops.give(player, stack);
            }
        } finally {
            InventoryDrops.clearDropAt();
        }
    }

    /** 100 Fortune → +1 extra crop. */
    private void giveFlatFortuned(Player player, ItemStack drop, double fortune) {
        int original = 1;
        double bonus = Math.max(0, fortune) / 100.0;
        int extra = (int) Math.floor(bonus);
        if (Math.random() < bonus - extra) {
            extra++;
        }
        int amount = Math.min(64, original + extra);
        for (int i = 0; i < amount; i++) {
            ItemStack one = drop.clone();
            one.setAmount(1);
            ItemStack compressed = ProgressionEffects.maybeCompress(player, itemManager, one);
            InventoryDrops.give(player, compressed != null ? compressed : one);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block source = event.getBlock();
        if (Crops.isDungeonWorld(source.getWorld()) || !Crops.isCrop(source.getType())) {
            return;
        }
        String playerId = player.getUniqueId().toString();
        if (harvesting.contains(playerId) || source.hasMetadata(CROP_BREAK_METADATA)) {
            return;
        }
        if (!Crops.isMature(source)) {
            return;
        }

        double harvest = equipmentStats.getStat(player, ItemCapability.HARVEST_SPREAD);
        if (harvest <= 0) {
            grantHoeXp(player, 4);
            return;
        }

        int guaranteed = (int) Math.floor(harvest / 100.0);
        double remainder = harvest - guaranteed * 100.0;
        int extra = guaranteed;
        if (remainder > 0 && Math.random() * 100.0 < remainder) {
            extra++;
        }
        if (extra <= 0) {
            grantHoeXp(player, 4);
            return;
        }

        Material type = source.getType();
        Set<Block> targets = findTargets(source, type, extra);
        if (targets.isEmpty()) {
            grantHoeXp(player, 4);
            return;
        }

        harvesting.add(playerId);
        try {
            int harvested = 1;
            for (Block target : targets) {
                if (target == null || target.getType() != type || !Crops.isMature(target)) {
                    continue;
                }
                target.setMetadata(CROP_BREAK_METADATA, new FixedMetadataValue(AetherionItems.getInstance(), true));
                try {
                    player.breakBlock(target);
                    harvested++;
                } finally {
                    target.removeMetadata(CROP_BREAK_METADATA, AetherionItems.getInstance());
                }
            }
            grantHoeXp(player, harvested * 4);
        } finally {
            harvesting.remove(playerId);
        }
    }

    private void grantHoeXp(Player player, int amount) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (FarmingHoeProgress.isHoe(tool)) {
            FarmingHoeProgress.grant(player, tool, itemManager, amount);
        }
        var plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getSkills() != null) {
            plugin.getSkills().grantFromFarm(player, amount);
        }
    }

    private static Set<Block> findTargets(Block source, Material type, int limit) {
        Set<Block> found = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        seen.add(key(source));
        queue.add(source);
        while (!queue.isEmpty() && found.size() < limit) {
            Block current = queue.poll();
            for (BlockFace face : NEIGHBORS) {
                if (found.size() >= limit) {
                    break;
                }
                Block next = current.getRelative(face);
                if (!seen.add(key(next))) {
                    continue;
                }
                if (next.getType() != type || !Crops.isMature(next)) {
                    continue;
                }
                found.add(next);
                queue.add(next);
            }
        }
        return found;
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
