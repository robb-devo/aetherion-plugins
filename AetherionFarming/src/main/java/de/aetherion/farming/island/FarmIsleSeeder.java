package de.aetherion.farming.island;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.Crops;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fills the live Farm Isle footprint with sugar cane banks and mature crops on
 * otherwise empty farmland. Purely additive — existing cane, crops and player
 * rows are never overwritten, so a re-run is safe.
 *
 * Harvesting stays on the normal {@link de.aetherion.farming.FarmingListener}
 * regen path; this only places the blocks.
 */
public final class FarmIsleSeeder implements Runnable {

    private static final BlockFace[] SIDES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    /** How far below the surface a farmland row may hide (barns, terraces). */
    private static final int COLUMN_DEPTH = 5;

    private final AetherionFarming plugin;
    private final Deque<long[]> pending = new ArrayDeque<>();

    private BukkitTask task;
    private World world;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private int caneBudget;
    private int cropBudget;
    private int canePlanted;
    private int cropsPlanted;
    private int outstanding;
    private boolean running;

    public FarmIsleSeeder(AetherionFarming plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    /** Called once from onEnable when {@code farm-isle-cane.auto-seed} is on. */
    public void autoSeed() {
        if (!plugin.getConfig().getBoolean("farm-isle-cane.auto-seed", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("farm-isle-cane.seeded", false)) {
            return;
        }
        String status = start(false);
        plugin.getLogger().info(status.replace('§', '&'));
    }

    /**
     * Queue a seeding pass over the footprint.
     *
     * @param force run again even if the footprint was already seeded once
     */
    public String start(boolean force) {
        if (running) {
            return "§eFarm Isle seeding already running.";
        }
        if (!force && plugin.getConfig().getBoolean("farm-isle-cane.seeded", false)) {
            return "§eFarm Isle already seeded §8· §7use force to run again.";
        }
        FarmIsleZones.Footprint footprint = FarmIsleZones.footprint(plugin);
        if (footprint == null) {
            return "§cFarm Isle footprint disabled or its world is not loaded.";
        }
        world = footprint.world();
        minX = footprint.minX();
        maxX = footprint.maxX();
        minZ = footprint.minZ();
        maxZ = footprint.maxZ();
        caneBudget = Math.max(0, plugin.getConfig().getInt("farm-isle-cane.max-plants-per-run", 900));
        cropBudget = Math.max(0, plugin.getConfig().getInt("farm-isle-fields.max-plants-per-run", 600));
        canePlanted = 0;
        cropsPlanted = 0;
        outstanding = 0;

        pending.clear();
        for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++) {
                pending.add(new long[]{chunkX, chunkZ});
            }
        }
        if (pending.isEmpty()) {
            return "§cFarm Isle footprint is empty.";
        }
        running = true;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        return "§aFarm Isle seeding started §8· §f" + pending.size() + " §7chunks queued.";
    }

    @Override
    public void run() {
        int perTick = Math.max(1, plugin.getConfig().getInt("farm-isle-cane.chunks-per-tick", 4));
        for (int i = 0; i < perTick; i++) {
            if (pending.isEmpty() || (caneBudget <= 0 && cropBudget <= 0)) {
                pending.clear();
                if (outstanding <= 0) {
                    finish();
                }
                return;
            }
            long[] coords = pending.poll();
            outstanding++;
            world.getChunkAtAsync((int) coords[0], (int) coords[1])
                    .thenAccept(chunk -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        seedChunk(chunk);
                        outstanding--;
                    }));
        }
    }

    public void shutdown() {
        pending.clear();
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void finish() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        plugin.getConfig().set("farm-isle-cane.seeded", true);
        plugin.saveConfig();
        plugin.getLogger().info("Farm Isle seeded cane=+" + canePlanted + " crops=+" + cropsPlanted);
    }

    private void seedChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int heightMin = Math.max(1, plugin.getConfig().getInt("farm-isle-cane.height-min", 3));
        int heightMax = Math.max(heightMin, plugin.getConfig().getInt("farm-isle-cane.height-max", 4));
        boolean allowDirt = plugin.getConfig().getBoolean("farm-isle-cane.allow-dirt", false);
        boolean fields = plugin.getConfig().getBoolean("farm-isle-fields.auto-seed", true);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Material> mix = cropMix();

        for (int dx = 0; dx < 16; dx++) {
            int x = baseX + dx;
            if (x < minX || x > maxX) {
                continue;
            }
            for (int dz = 0; dz < 16; dz++) {
                int z = baseZ + dz;
                if (z < minZ || z > maxZ) {
                    continue;
                }
                int top = world.getHighestBlockYAt(x, z);
                for (int dy = 0; dy <= COLUMN_DEPTH; dy++) {
                    int y = top - dy;
                    if (y <= world.getMinHeight()) {
                        break;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (caneBudget > 0 && isCaneSoil(type, allowDirt) && touchesWater(block)) {
                        canePlanted += plantCane(block, heightMin + rng.nextInt(heightMax - heightMin + 1));
                        break;
                    }
                    if (fields && cropBudget > 0 && type == Material.FARMLAND) {
                        if (plantCrop(block, mix.get(rng.nextInt(mix.size())))) {
                            cropsPlanted++;
                            cropBudget--;
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Stacks cane on the soil block; returns how many cane blocks were added. */
    private int plantCane(Block soil, int height) {
        Block base = soil.getRelative(BlockFace.UP);
        if (base.getType() == Material.SUGAR_CANE) {
            // Already a bank here — leave it and spend no budget.
            return 0;
        }
        if (!base.getType().isAir()) {
            return 0;
        }
        int placed = 0;
        for (int i = 0; i < height && caneBudget > 0; i++) {
            Block at = base.getRelative(0, i, 0);
            if (!at.getType().isAir()) {
                break;
            }
            at.setType(Material.SUGAR_CANE, false);
            caneBudget--;
            placed++;
        }
        return placed;
    }

    /** Only plants where the farmland is genuinely empty — never replaces a row. */
    private boolean plantCrop(Block farmland, Material crop) {
        Block above = farmland.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) {
            return false;
        }
        if (!(crop.createBlockData() instanceof Ageable ageable)) {
            return false;
        }
        ageable.setAge(ageable.getMaximumAge());
        above.setBlockData(ageable, false);
        return true;
    }

    /** Weighted crop mix from config, repeated per weight so a plain pick is enough. */
    private List<Material> cropMix() {
        List<Material> mix = new ArrayList<>();
        addMix(mix, Material.WHEAT, plugin.getConfig().getInt("farm-isle-fields.mix.wheat", 40));
        addMix(mix, Material.CARROTS, plugin.getConfig().getInt("farm-isle-fields.mix.carrot", 20));
        addMix(mix, Material.POTATOES, plugin.getConfig().getInt("farm-isle-fields.mix.potato", 20));
        addMix(mix, Material.BEETROOTS, plugin.getConfig().getInt("farm-isle-fields.mix.beetroot", 20));
        if (mix.isEmpty()) {
            mix.add(Material.WHEAT);
        }
        return mix;
    }

    private static void addMix(List<Material> mix, Material crop, int weight) {
        if (!Crops.isCrop(crop)) {
            return;
        }
        for (int i = 0; i < weight; i++) {
            mix.add(crop);
        }
    }

    private static boolean isCaneSoil(Material type, boolean allowDirt) {
        if (type == Material.SAND || type == Material.RED_SAND) {
            return true;
        }
        return allowDirt
                && (type == Material.DIRT || type == Material.GRASS_BLOCK
                || type == Material.COARSE_DIRT || type == Material.PODZOL
                || type == Material.MUD || type == Material.ROOTED_DIRT);
    }

    private static boolean touchesWater(Block block) {
        for (BlockFace side : SIDES) {
            Material type = block.getRelative(side).getType();
            if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                return true;
            }
        }
        return false;
    }
}
