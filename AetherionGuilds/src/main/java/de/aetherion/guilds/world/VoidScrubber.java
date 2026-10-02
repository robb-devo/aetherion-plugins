package de.aetherion.guilds.world;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cleans overworld terrain that got generated into the island worlds before they were locked to void (see
 * {@link VoidWorlds}). Each chunk is checked once, when it loads, and marked in its own data:
 * <ul>
 *   <li>columns on nobody's land (owned parcels + tier square, with a 2-block margin) are emptied top to bottom;</li>
 *   <li>on owned land only what hangs below every island ({@value #FLOOR_BELOW_SURFACE} below the surface) goes,
 *   so islands, bought land and anything built on them are never touched;</li>
 *   <li>stray mobs, drops, carts and frames from that terrain go with it (tamed, named or tagged ones stay).</li>
 * </ul>
 * Work is spread over ticks (config {@code void.scrub-blocks-per-tick}), no physics, nearest chunks first.
 */
public final class VoidScrubber implements Listener {

    /** Islands hang at most ~22 below the surface (y 64); natural terrain under that is always junk. */
    public static final int FLOOR_BELOW_SURFACE = 30;
    private static final byte VERSION = 1;

    private static final class Job {
        final World world;
        final int cx;
        final int cz;
        ChunkSnapshot snapshot;
        boolean[] protectedColumn;
        int section;
        int index;
        int cleared;
        boolean force;

        Job(World world, int cx, int cz, boolean force) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
            this.force = force;
        }
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final NamespacedKey checkedKey;
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private long chunksCleaned;
    private long blocksCleared;
    private long chunksChecked;

    public VoidScrubber(JavaPlugin plugin, HostService hosts) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.checkedKey = new NamespacedKey(plugin, "void_checked");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("void.scrub", true);
    }

    private int budget() {
        return Math.max(250, plugin.getConfig().getInt("void.scrub-blocks-per-tick", 3000));
    }

    // ------------------------------------------------------------------------------------------------
    // intake
    // ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!enabled() || !hosts.isIslandWorld(world)) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) {
            return;
        }
        if (event.isNewChunk() && VoidWorlds.isVoid(world)) {
            mark(chunk);
            return;
        }
        enqueue(world, chunk.getX(), chunk.getZ(), false);
    }

    /** Queue every loaded chunk of both island worlds. {@code force} re-checks chunks already marked clean. */
    public int sweep(boolean force) {
        int added = 0;
        for (World world : new World[]{hosts.personalService().world(), hosts.guildIslands().world()}) {
            if (world == null) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!force && chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) {
                    continue;
                }
                if (enqueue(world, chunk.getX(), chunk.getZ(), force)) {
                    added++;
                }
            }
        }
        return added;
    }

    private boolean enqueue(World world, int cx, int cz, boolean force) {
        String key = world.getName() + ":" + cx + ":" + cz;
        if (!queued.add(key)) {
            return false;
        }
        queue.addLast(new Job(world, cx, cz, force));
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // work (every tick)
    // ------------------------------------------------------------------------------------------------

    public void tick() {
        if (queue.isEmpty()) {
            return;
        }
        int clears = budget();
        int scans = clears * 12;
        while (!queue.isEmpty() && clears > 0 && scans > 0) {
            Job job = queue.peekFirst();
            if (!job.world.isChunkLoaded(job.cx, job.cz)) {
                // unloaded before we got to it: checked again next time it loads
                drop(job);
                continue;
            }
            Chunk chunk = job.world.getChunkAt(job.cx, job.cz);
            if (job.snapshot == null) {
                if (!job.force && chunk.getPersistentDataContainer().has(checkedKey, PersistentDataType.BYTE)) {
                    drop(job);
                    continue;
                }
                job.snapshot = chunk.getChunkSnapshot(false, false, false);
                job.protectedColumn = protectedColumns(job.world, job.cx, job.cz);
                scans -= 512;
            }
            int minY = job.world.getMinHeight();
            int sections = (job.world.getMaxHeight() - minY) >> 4;
            int floorY = HostService.SURFACE_Y - FLOOR_BELOW_SURFACE;
            while (job.section < sections && clears > 0 && scans > 0) {
                if (job.index == 0 && job.snapshot.isSectionEmpty(job.section)) {
                    job.section++;
                    scans--;
                    continue;
                }
                int baseY = minY + (job.section << 4);
                while (job.index < 4096 && clears > 0 && scans > 0) {
                    int i = job.index++;
                    scans--;
                    int x = i & 15;
                    int z = (i >> 4) & 15;
                    int y = baseY + (i >> 8);
                    if (job.protectedColumn[x + (z << 4)] && y >= floorY) {
                        continue;
                    }
                    Material type = job.snapshot.getBlockType(x, y, z);
                    if (type.isAir()) {
                        continue;
                    }
                    chunk.getBlock(x, y, z).setType(Material.AIR, false);
                    job.cleared++;
                    clears--;
                }
                if (job.index >= 4096) {
                    job.index = 0;
                    job.section++;
                }
            }
            if (job.section >= sections) {
                finish(job, chunk, floorY);
            }
        }
    }

    private void finish(Job job, Chunk chunk, int floorY) {
        drop(job);
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player || !natural(entity)) {
                continue;
            }
            Location at = entity.getLocation();
            int column = (at.getBlockX() & 15) + ((at.getBlockZ() & 15) << 4);
            if (job.protectedColumn[column] && at.getY() >= floorY) {
                continue;
            }
            entity.remove();
            removed++;
        }
        mark(chunk);
        chunksChecked++;
        if (job.cleared > 0 || removed > 0) {
            chunksCleaned++;
            blocksCleared += job.cleared;
            if (chunksCleaned <= 3 || chunksCleaned % 50 == 0) {
                plugin.getLogger().info("Void: cleaned stray terrain in " + job.world.getName() + " chunk " + job.cx
                        + "," + job.cz + " (" + job.cleared + " blocks" + (removed > 0 ? ", " + removed + " entities" : "")
                        + "). Chunks cleaned so far: " + chunksCleaned + ".");
            }
        }
    }

    private void drop(Job job) {
        queue.remove(job);
        queued.remove(job.world.getName() + ":" + job.cx + ":" + job.cz);
    }

    private void mark(Chunk chunk) {
        chunk.getPersistentDataContainer().set(checkedKey, PersistentDataType.BYTE, VERSION);
    }

    /** Things natural terrain brings along. Anything tamed, named, tagged or carrying plugin data stays. */
    private static boolean natural(Entity entity) {
        if (!(entity instanceof Mob || entity instanceof Item || entity instanceof ExperienceOrb
                || entity instanceof FallingBlock || entity instanceof Vehicle || entity instanceof Hanging)) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return entity.customName() == null && entity.getScoreboardTags().isEmpty()
                && entity.getPersistentDataContainer().getKeys().isEmpty();
    }

    // ------------------------------------------------------------------------------------------------
    // land
    // ------------------------------------------------------------------------------------------------

    /** 256 flags (x + z*16): true = on someone's land (or within 2 blocks of it). */
    private boolean[] protectedColumns(World world, int cx, int cz) {
        boolean[] out = new boolean[256];
        Map<Integer, IslandHost> byX = new HashMap<>();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        for (int x = 0; x < 16; x++) {
            int wx = baseX + x;
            IslandHost host = byX.computeIfAbsent(wx, ignored -> hosts.at(world, wx, baseZ));
            if (host == null) {
                continue;
            }
            for (int z = 0; z < 16; z++) {
                int wz = baseZ + z;
                out[x + (z << 4)] = owned(host, wx, wz);
            }
        }
        return out;
    }

    private boolean owned(IslandHost host, int x, int z) {
        return hosts.inBuildZone(host, x, z) || hosts.inBuildZone(host, x + 2, z) || hosts.inBuildZone(host, x - 2, z)
                || hosts.inBuildZone(host, x, z + 2) || hosts.inBuildZone(host, x, z - 2);
    }

    // ------------------------------------------------------------------------------------------------
    // admin
    // ------------------------------------------------------------------------------------------------

    public int pending() {
        return queue.size();
    }

    public String status() {
        World personal = hosts.personalService().world();
        World guild = hosts.guildIslands().world();
        return "§7islands world: " + (VoidWorlds.isVoid(personal) ? "§avoid" : "§cNOT void")
                + " §8| §7guild world: " + (VoidWorlds.isVoid(guild) ? "§avoid" : "§cNOT void")
                + " §8| §7queue §f" + queue.size() + " §8| §7checked §f" + chunksChecked
                + " §8| §7cleaned §f" + chunksCleaned + " §7chunks / §f" + blocksCleared + " §7blocks";
    }
}
