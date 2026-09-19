package de.aetherion.farming;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;

import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.event.Event.Result.ALLOW;

public class FarmingListener implements Listener {

    private final Set<String> regenerating = ConcurrentHashMap.newKeySet();
    private final Set<String> freshMaturing = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardBreak(BreakBlockEvent event) {
        if (allowsCrop(event.getCause().getFirstBlock())) {
            event.setResult(ALLOW);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (allowsCrop(block)) {
                event.setResult(ALLOW);
                return;
            }
        }
    }

    /** WorldGuard build/block-place deny must not stop natural crop / cane growth. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardPlace(PlaceBlockEvent event) {
        // Only natural growth — players stay under block-place: deny.
        if (event.getCause().getFirstPlayer() != null) {
            return;
        }
        if (allowsGrowthMaterial(event.getCause().getFirstBlock())) {
            event.setResult(ALLOW);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (allowsGrowthMaterial(block)) {
                event.setResult(ALLOW);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block == null || Crops.isDungeonWorld(block.getWorld())) {
            return;
        }
        Material from = block.getType();
        Material to = event.getNewState() != null ? event.getNewState().getType() : null;
        if (Crops.allowsNaturalGrowth(from) || Crops.allowsNaturalGrowth(to)) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropSpread(BlockSpreadEvent event) {
        Block block = event.getBlock();
        if (block == null || Crops.isDungeonWorld(block.getWorld())) {
            return;
        }
        Material source = event.getSource() != null ? event.getSource().getType() : null;
        Material to = event.getNewState() != null ? event.getNewState().getType() : null;
        if (Crops.allowsNaturalGrowth(source) || Crops.allowsNaturalGrowth(to) || Crops.allowsNaturalGrowth(block.getType())) {
            event.setCancelled(false);
        }
    }

    /**
     * Admin-placed immature seeds snap to mature quickly so the MMO farm is harvestable.
     * Does not touch the normal harvest → replant → REGROW_TICKS cycle.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropPlace(BlockPlaceEvent event) {
        scheduleFreshMature(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (Crops.isDungeonWorld(event.getWorld())) {
            return;
        }
        ripenImmatureInChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block == null || Crops.isDungeonWorld(block.getWorld())) {
            return;
        }
        if (block.getType() != Material.FARMLAND) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysicalFarmland(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || Crops.isDungeonWorld(block.getWorld())) {
            return;
        }
        if (block.getType() == Material.FARMLAND || Crops.isCrop(block.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Crops.isCrop(block.getType())
                || Crops.isDungeonWorld(block.getWorld())
                || Crops.isBuildWorld(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        Material original = block.getType();
        if (original == Material.SUGAR_CANE && !canBreakSugarCane(player)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);

        BlockData snapshot = block.getBlockData().clone();
        boolean mature = Crops.isMature(block);
        boolean fruit = original == Material.MELON || original == Material.PUMPKIN || original == Material.SUGAR_CANE;

        // Always restore crops after a break (jump/physics edge cases included).
        String key = key(block);
        if (!regenerating.add(key)) {
            return;
        }
        freshMaturing.remove(key);

        Bukkit.getScheduler().runTask(AetherionFarming.getInstance(), () -> {
            if (!fruit) {
                replantSeedling(block, snapshot);
            }
            // Immature tramp leftovers also grow back to the prior age snapshot.
            long delay = mature ? Crops.REGROW_TICKS : Math.max(40L, Crops.REGROW_TICKS / 2);
            Bukkit.getScheduler().runTaskLater(
                    AetherionFarming.getInstance(),
                    () -> restore(block, original, snapshot, key, mature),
                    delay
            );
        });
    }

    /** Call once after enable so already-loaded farm chunks ripen fresh seedlings. */
    public void ripenLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            if (Crops.isDungeonWorld(world)) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                ripenImmatureInChunk(chunk);
            }
        }
    }

    private void ripenImmatureInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = world.getHighestBlockYAt(baseX + x, baseZ + z);
                int minY = world.getMinHeight();
                for (int dy = 0; dy <= 6; dy++) {
                    int y = top - dy;
                    if (y < minY || y > world.getMaxHeight()) {
                        continue;
                    }
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();
                    if (!Crops.isCrop(type) || type == Material.MELON || type == Material.PUMPKIN || type == Material.SUGAR_CANE) {
                        continue;
                    }
                    if (Crops.isMature(block)) {
                        continue;
                    }
                    scheduleFreshMature(block);
                }
            }
        }
    }

    private void scheduleFreshMature(Block block) {
        if (block == null || Crops.isDungeonWorld(block.getWorld()) || !Crops.isCrop(block.getType())) {
            return;
        }
        if (block.getType() == Material.MELON || block.getType() == Material.PUMPKIN || block.getType() == Material.SUGAR_CANE) {
            return;
        }
        if (Crops.isMature(block)) {
            return;
        }
        String key = key(block);
        if (regenerating.contains(key) || !freshMaturing.add(key)) {
            return;
        }
        Material type = block.getType();
        Bukkit.getScheduler().runTaskLater(AetherionFarming.getInstance(), () -> {
            freshMaturing.remove(key);
            if (regenerating.contains(key)) {
                return;
            }
            if (block.getType() != type) {
                return;
            }
            BlockData data = block.getBlockData();
            if (!(data instanceof Ageable ageable)) {
                return;
            }
            if (ageable.getAge() >= ageable.getMaximumAge()) {
                return;
            }
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable, false);
        }, Crops.FRESH_MATURE_TICKS);
    }

    private void replantSeedling(Block block, BlockData snapshot) {
        if (!Crops.canRestore(block, snapshot.getMaterial())) {
            return;
        }
        BlockData planted = snapshot.clone();
        if (planted instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
            return;
        }
        block.setBlockData(planted, false);
    }

    private void restore(Block block, Material original, BlockData snapshot, String key, boolean mature) {
        regenerating.remove(key);
        if (!Crops.canRestore(block, original)) {
            return;
        }
        BlockData grown = snapshot.clone();
        if (grown instanceof Ageable ageable) {
            if (mature) {
                ageable.setAge(ageable.getMaximumAge());
            }
            block.setBlockData(ageable, false);
            return;
        }
        // Melon / pumpkin / sugar cane: put the harvested block back.
        block.setBlockData(grown, false);
    }

    private static boolean canBreakSugarCane(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return true;
        }
        return isFarmingTool(hand);
    }

    private static boolean isFarmingTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(AetherKeys.ITEM_ID, PersistentDataType.STRING);
        return id != null && id.toLowerCase(Locale.ROOT).startsWith("farming_hoe");
    }

    private static boolean allowsCrop(Block block) {
        return block != null && Crops.isCrop(block.getType());
    }

    private static boolean allowsGrowthMaterial(Block block) {
        return block != null && Crops.allowsNaturalGrowth(block.getType());
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
