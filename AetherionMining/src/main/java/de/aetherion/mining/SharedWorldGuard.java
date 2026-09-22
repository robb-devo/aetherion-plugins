package de.aetherion.mining;

import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared worlds: deny break/place by default.
 * Break allowlist = stone/ores, living trees (canopy), crops.
 * Place only on personal/guild/test islands (and Nether / Veins via their own rules).
 */
public final class SharedWorldGuard implements Listener {

    private static final Set<Material> SOFT_TERRAIN = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.DIRT_PATH,
            Material.FARMLAND,
            Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS,
            Material.CLAY,
            Material.SAND,
            Material.RED_SAND,
            Material.SUSPICIOUS_SAND,
            Material.GRAVEL,
            Material.SUSPICIOUS_GRAVEL,
            Material.SNOW,
            Material.SNOW_BLOCK,
            Material.POWDER_SNOW,
            Material.MOSS_BLOCK,
            Material.MOSS_CARPET
    );

    /** Farm crops — mirrored from AetherionFarming Crops.TYPES (no hard dep). */
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP,
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE
    );

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final int MAX_LOGS = 96;
    private static final int MAX_HORIZONTAL = 8;
    private static final int MAX_VERTICAL = 28;
    private static final int MAX_LEAF_RADIUS = 3;

    private final ConcurrentHashMap<UUID, Long> lastBreakHint = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastPlaceHint = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent event) {
        if (!deniesBreak(event.getPlayer(), event.getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (!deniesBreak(event.getPlayer(), event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        event.setDropItems(false);
        hintBreak(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (!deniesPlace(event.getPlayer(), event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        hintPlace(event.getPlayer());
    }

    private boolean deniesBreak(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (unrestricted(player)) {
            return false;
        }
        World world = block.getWorld();
        if (world == null || isBuildWorld(world)) {
            return false;
        }
        if (isDungeonWorld(world)) {
            return true;
        }
        if (isVeins(world)) {
            // Open dig outside spawn protect — VeinsListener owns hub protect.
            // Soft terrain is mineable here (Crystal Hollows specialty vs other mines).
            return false;
        }
        return !allowsGatherBreak(block);
    }

    private boolean deniesPlace(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (unrestricted(player)) {
            return false;
        }
        World world = block.getWorld();
        if (world == null || isBuildWorld(world)) {
            return false;
        }
        if (isDungeonWorld(world)) {
            return true;
        }
        // Veins hub protect + Nether place rules stay in their own listeners.
        if (isVeins(world) || world.getEnvironment() == World.Environment.NETHER) {
            return false;
        }
        return true;
    }

    private static boolean unrestricted(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private static boolean allowsGatherBreak(Block block) {
        Material type = block.getType();
        if (MiningBlocks.allows(type) || CROPS.contains(type)) {
            return true;
        }
        return isLog(type) && hasConnectedCanopy(block);
    }

    public static boolean isSoftTerrain(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (SOFT_TERRAIN.contains(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_CONCRETE_POWDER")
                || material == Material.SOUL_SAND
                || material == Material.SOUL_SOIL;
    }

    /** Islands / test sandboxes — free build, no gather seal-regen. */
    public static boolean isBuildWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_islands")
                || name.equals("aether_guilds")
                || name.equals("aether_test")
                || name.startsWith("aether_test_");
    }

    private static boolean isDungeonWorld(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }

    private static boolean isVeins(World world) {
        AetherionMining plugin = AetherionMining.getInstance();
        VeinsWorld veins = plugin == null ? null : plugin.getVeins();
        return veins != null && veins.isVeins(world);
    }

    private static boolean isLog(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LOGS.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_WOOD")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_STEM")
                || name.equals("MUSHROOM_STEM");
    }

    private static boolean isLeaf(Material material) {
        return material != null && material.name().endsWith("_LEAVES");
    }

    private static boolean isCanopy(Material material) {
        if (isLeaf(material)) {
            return true;
        }
        if (material == null) {
            return false;
        }
        return material == Material.NETHER_WART_BLOCK
                || material == Material.WARPED_WART_BLOCK
                || material == Material.SHROOMLIGHT
                || material == Material.AZALEA
                || material == Material.FLOWERING_AZALEA
                || material == Material.MANGROVE_ROOTS
                || material == Material.MUDDY_MANGROVE_ROOTS
                || material.name().endsWith("_SAPLING")
                || material == Material.VINE
                || material == Material.GLOW_LICHEN;
    }

    private static boolean hasConnectedCanopy(Block start) {
        List<Block> logs = collectLogs(start);
        return !logs.isEmpty() && !collectConnectedCanopy(logs).isEmpty();
    }

    private static List<Block> collectLogs(Block start) {
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(key(start));
        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            if (!isLog(current.getType()) || !inRange(start, current)) {
                continue;
            }
            found.add(current);
            for (BlockFace face : FACES) {
                offer(queue, seen, current.getRelative(face));
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    offer(queue, seen, current.getRelative(dx, 0, dz));
                    offer(queue, seen, current.getRelative(dx, 1, dz));
                }
            }
        }
        return found;
    }

    private static List<Block> collectConnectedCanopy(List<Block> logs) {
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        for (Block log : logs) {
            for (BlockFace face : FACES) {
                seedCanopy(queue, seen, found, log.getRelative(face));
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        seedCanopy(queue, seen, found, log.getRelative(dx, dy, dz));
                    }
                }
            }
        }
        while (!queue.isEmpty() && found.size() < 384) {
            Block current = queue.poll();
            for (BlockFace face : FACES) {
                Block next = current.getRelative(face);
                if (!isCanopy(next.getType()) || !nearAnyLog(logs, next)) {
                    continue;
                }
                if (seen.add(key(next))) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static void seedCanopy(Queue<Block> queue, Set<String> seen, List<Block> found, Block block) {
        if (!isCanopy(block.getType())) {
            return;
        }
        if (seen.add(key(block))) {
            found.add(block);
            queue.add(block);
        }
    }

    private static boolean nearAnyLog(List<Block> logs, Block canopy) {
        int horiz = MAX_LEAF_RADIUS + 2;
        int vert = MAX_LEAF_RADIUS + 4;
        for (Block anchor : logs) {
            int dx = Math.abs(canopy.getX() - anchor.getX());
            int dy = Math.abs(canopy.getY() - anchor.getY());
            int dz = Math.abs(canopy.getZ() - anchor.getZ());
            if (Math.max(dx, dz) <= horiz && dy <= vert) {
                return true;
            }
        }
        return false;
    }

    private static void offer(Queue<Block> queue, Set<String> seen, Block next) {
        if (seen.add(key(next))) {
            queue.add(next);
        }
    }

    private static boolean inRange(Block origin, Block current) {
        int dx = Math.abs(current.getX() - origin.getX());
        int dy = Math.abs(current.getY() - origin.getY());
        int dz = Math.abs(current.getZ() - origin.getZ());
        return Math.max(dx, dz) <= MAX_HORIZONTAL && dy <= MAX_VERTICAL;
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
    }

    private void hintBreak(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastBreakHint.get(player.getUniqueId());
        if (last != null && now - last < 2500L) {
            return;
        }
        lastBreakHint.put(player.getUniqueId(), now);
        player.sendMessage("§7Only stone, ores, trees, and crops.");
    }

    private void hintPlace(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastPlaceHint.get(player.getUniqueId());
        if (last != null && now - last < 2500L) {
            return;
        }
        lastPlaceHint.put(player.getUniqueId(), now);
        player.sendMessage("§7Build on your island or guild island.");
    }
}
