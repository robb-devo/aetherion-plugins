package de.aetherion.dungeons.instance;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import de.aetherion.core.world.VoidChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Reuses one persistent Endless XL base world so WorldEdit paste happens at most once
 * (until the schematic changes). Enter/leave only recycle entities + door.
 */
final class DungeonWarmPool {

    /** Survives purge/restart — schematic lives here permanently. */
    public static final String ENDLESS_BASE = InstanceManager.WORLD_PREFIX + "xl_base";
    /** Throne of Ashes Floor 3 — full world copy, no WorldEdit paste. */
    public static final String ASHES_BASE = InstanceManager.WORLD_PREFIX + "f3_ashes";

    private final JavaPlugin plugin;
    private final AtomicBoolean endlessBuilding = new AtomicBoolean(false);
    private final AtomicBoolean voidBuilding = new AtomicBoolean(false);
    private final AtomicBoolean ashesBuilding = new AtomicBoolean(false);
    private final AtomicBoolean floorOneBuilding = new AtomicBoolean(false);
    private final Map<String, WarmEndless> claimedEndless = new ConcurrentHashMap<>();
    private final Map<String, WarmAshes> claimedAshes = new ConcurrentHashMap<>();

    private volatile WarmEndless warmEndless;
    private volatile WarmAshes warmAshes;
    private volatile World warmVoid;
    /** Pre-built Floor 1 worlds, oldest first; instances.prebuilt-floor1 of them (default 2). */
    private final java.util.concurrent.ConcurrentLinkedDeque<WarmFloorOne> readyFloorOne =
            new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** Parallel runs (A-12): one ready copy of each base for the next party while the base is in use. */
    private volatile WarmEndless spareEndless;
    private volatile WarmAshes spareAshes;
    private final AtomicBoolean endlessCopying = new AtomicBoolean(false);
    private final AtomicBoolean ashesCopying = new AtomicBoolean(false);
    private final Set<String> copying = ConcurrentHashMap.newKeySet();
    /** World name of the Floor 1 being pre-built, so the orphan sweep leaves it alone. */
    private volatile String floorOneInProgress;

    DungeonWarmPool(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    record WarmEndless(World world, EndlessSchemBuilder.PasteResult paste, EndlessEncounter.Prep prep) {
    }

    record WarmAshes(World world, AshesEncounter.Prep prep) {
    }

    /** A Floor 1 built ahead of time: its own void world, the plan and the party spawn. Chunks are pinned. */
    record WarmFloorOne(World world, FloorOnePool.Plan plan, Location spawn) {
    }

    void start() {
        boolean skipWarmOnHub = "hub".equalsIgnoreCase(plugin.getConfig().getString("role", "hub"))
                && plugin.getConfig().getBoolean("remote-transfer.enabled", false)
                && plugin.getConfig().getBoolean("remote-transfer.skip-warm-on-hub",
                        plugin.getConfig().getBoolean("remote-transfer.skip-floor2-warm-on-hub", true));
        if (skipWarmOnHub) {
            plugin.getLogger().info("Skipping dungeon warm pools on hub (all floors remote → mmo-d).");
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmVoid, 100L);
        // Prefer loading an existing base (fast). First-time paste is delayed further.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmEndless, 140L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmAshes, 180L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmFloorOne, 220L);
    }

    void ensureAll() {
        ensureWarmVoid();
        ensureWarmEndless();
        ensureWarmAshes();
        ensureWarmFloorOne();
        ensureSpareEndless();
        ensureSpareAshes();
    }

    void shutdown() {
        WarmEndless endless = warmEndless;
        warmEndless = null;
        if (endless != null && endless.world() != null) {
            parkWorld(endless.world());
            Bukkit.unloadWorld(endless.world(), true);
        }
        for (WarmEndless claimed : List.copyOf(claimedEndless.values())) {
            if (claimed.world() == null) {
                continue;
            }
            if (ENDLESS_BASE.equals(claimed.world().getName())) {
                parkWorld(claimed.world());
                Bukkit.unloadWorld(claimed.world(), true);
            } else {
                unloadDiscard(claimed.world());
            }
        }
        claimedEndless.clear();
        WarmEndless spare = spareEndless;
        spareEndless = null;
        if (spare != null && spare.world() != null) {
            unloadDiscard(spare.world());
        }
        WarmAshes ashes = warmAshes;
        warmAshes = null;
        if (ashes != null && ashes.world() != null) {
            parkWorld(ashes.world());
            Bukkit.unloadWorld(ashes.world(), true);
        }
        for (WarmAshes claimed : List.copyOf(claimedAshes.values())) {
            if (claimed.world() == null) {
                continue;
            }
            if (ASHES_BASE.equals(claimed.world().getName())) {
                parkWorld(claimed.world());
                Bukkit.unloadWorld(claimed.world(), true);
            } else {
                unloadDiscard(claimed.world());
            }
        }
        claimedAshes.clear();
        WarmAshes spareThrone = spareAshes;
        spareAshes = null;
        if (spareThrone != null && spareThrone.world() != null) {
            unloadDiscard(spareThrone.world());
        }
        World voidWorld = warmVoid;
        warmVoid = null;
        if (voidWorld != null) {
            unloadDiscard(voidWorld);
        }
        for (WarmFloorOne floorOne = readyFloorOne.pollFirst(); floorOne != null; floorOne = readyFloorOne.pollFirst()) {
            if (floorOne.world() != null) {
                FloorOnePool.forget(floorOne.world().getName());
                DungeonChunks.release(plugin, floorOne.world());
                unloadDiscard(floorOne.world());
            }
        }
        String building = floorOneInProgress;
        floorOneInProgress = null;
        World half = building == null ? null : Bukkit.getWorld(building);
        if (half != null) {
            unloadDiscard(half);
        }
    }

    boolean isPoolWorld(String worldName) {
        if (worldName == null) {
            return false;
        }
        if (ENDLESS_BASE.equals(worldName) || ASHES_BASE.equals(worldName)) {
            return true;
        }
        WarmEndless endless = warmEndless;
        if (endless != null && endless.world() != null && worldName.equals(endless.world().getName())) {
            return true;
        }
        WarmAshes ashes = warmAshes;
        if (ashes != null && ashes.world() != null && worldName.equals(ashes.world().getName())) {
            return true;
        }
        for (WarmFloorOne floorOne : readyFloorOne) {
            if (floorOne.world() != null && worldName.equals(floorOne.world().getName())) {
                return true;
            }
        }
        if (worldName.equals(floorOneInProgress)) {
            return true;
        }
        if (copying.contains(worldName) || claimedEndless.containsKey(worldName) || claimedAshes.containsKey(worldName)) {
            return true;
        }
        WarmEndless spare = spareEndless;
        if (spare != null && spare.world() != null && worldName.equals(spare.world().getName())) {
            return true;
        }
        WarmAshes spareThrone = spareAshes;
        if (spareThrone != null && spareThrone.world() != null && worldName.equals(spareThrone.world().getName())) {
            return true;
        }
        World voidWorld = warmVoid;
        return voidWorld != null && worldName.equals(voidWorld.getName());
    }

    /** A pre-built Floor 1, or null when none is ready (the caller then builds one on the spot). */
    WarmFloorOne takeFloorOne() {
        WarmFloorOne ready = readyFloorOne.pollFirst();
        if (ready != null) {
            plugin.getLogger().info("Claimed pre-built Floor 1: " + ready.world().getName());
            // The next one is built a little later, in small steps, while this party plays.
            plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmFloorOne, 20L * 20L);
        }
        return ready;
    }

    /**
     * Builds the next Floor 1 ahead of time: a void world, a planned room pool, its chunks loaded off the
     * server thread and pinned, then one build step per tick (a room template each). Entering Floor 1 then
     * costs a teleport instead of the 1.6 s one-piece build measured on 11-27.
     */
    private void ensureWarmFloorOne() {
        int wanted = Math.max(1, plugin.getConfig().getInt("instances.prebuilt-floor1", 2));
        if (readyFloorOne.size() >= wanted || !FloorOnePool.enabled(plugin) || !floorOneBuilding.compareAndSet(false, true)) {
            return;
        }
        String name = InstanceManager.WORLD_PREFIX + "warm_f1_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        floorOneInProgress = name;
        long started = System.currentTimeMillis();
        World world;
        FloorOnePool.Plan plan;
        try {
            world = DungeonRunMetrics.timed(null, "pre-build Floor 1 world", () -> InstanceManager.createSlimVoidWorld(name));
            plan = world == null ? null : FloorOnePool.plan(plugin, name.hashCode() ^ System.nanoTime());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Floor 1 pre-build could not start: " + exception.getMessage());
            world = Bukkit.getWorld(name);
            plan = null;
        }
        if (world == null || plan == null) {
            abandonFloorOne(name, world);
            return;
        }
        final World target = world;
        final FloorOnePool.Plan planned = plan;
        DungeonLayout.Room box = planned.layout().bounds();
        DungeonChunks.pinArea(plugin, target, box.minX() - 16, box.maxX() + 16, box.minZ() - 16, box.maxZ() + 16, () -> {
            Location[] spawn = new Location[1];
            List<Runnable> steps = LerfingTestBuilder.pooledSteps(plugin, target, planned, spawn);
            int[] next = {0};
            org.bukkit.scheduler.BukkitTask[] holder = new org.bukkit.scheduler.BukkitTask[1];
            holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!name.equals(floorOneInProgress) || Bukkit.getWorld(name) == null) {
                    holder[0].cancel();
                    floorOneBuilding.set(false);
                    return;
                }
                if (next[0] < steps.size()) {
                    try {
                        DungeonRunMetrics.time(null, "pre-build Floor 1 step", steps.get(next[0]++));
                    } catch (RuntimeException exception) {
                        holder[0].cancel();
                        plugin.getLogger().warning("Floor 1 pre-build failed at step " + next[0] + ": " + exception);
                        abandonFloorOne(name, target);
                    }
                    return;
                }
                holder[0].cancel();
                if (spawn[0] == null) {
                    abandonFloorOne(name, target);
                    return;
                }
                target.setSpawnLocation(spawn[0]);
                readyFloorOne.addLast(new WarmFloorOne(target, planned, spawn[0]));
                floorOneInProgress = null;
                floorOneBuilding.set(false);
                plugin.getLogger().info("Floor 1 pre-built in " + target.getName() + ": " + steps.size() + " steps, "
                        + target.getChunkCount() + " chunks, " + (System.currentTimeMillis() - started) + " ms in all ("
                        + readyFloorOne.size() + " ready)");
                // Room for another one (a second party right behind the first): build it a little later.
                plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmFloorOne, 100L);
            }, 1L, 1L);
        });
    }

    private int maxRuns() {
        return Math.max(1, plugin.getConfig().getInt("instances.max-runs", 3));
    }

    /** A second party for Floor 2 gets a copy of the base: copied off the server thread, loaded, reset. */
    private void ensureSpareEndless() {
        // Only while the base itself is in a run.
        if (warmEndless != null || spareEndless != null || maxRuns() <= 1 || claimedEndless.size() >= maxRuns()
                || !claimedEndless.containsKey(ENDLESS_BASE) || !endlessCopying.compareAndSet(false, true)) {
            return;
        }
        EndlessSchemBuilder.PasteResult paste = loadPasteMeta();
        EndlessEncounter.Prep prep = loadPrep();
        File source = new File(Bukkit.getWorldContainer(), ENDLESS_BASE);
        if (paste == null || prep == null || !source.isDirectory()) {
            endlessCopying.set(false);
            return;
        }
        copyBase(source, InstanceManager.WORLD_PREFIX + "xl_" + shortId(), true, world -> {
            EndlessEncounter.resetForReuse(plugin, world, prep);
            parkWorld(world);
            spareEndless = new WarmEndless(world, paste, prep);
        }, endlessCopying);
    }

    /** A second party for Floor 3 gets a copy of the Throne base. */
    private void ensureSpareAshes() {
        if (warmAshes != null || spareAshes != null || maxRuns() <= 1 || claimedAshes.size() >= maxRuns()
                || !claimedAshes.containsKey(ASHES_BASE) || !ashesCopying.compareAndSet(false, true)) {
            return;
        }
        AshesEncounter.Prep prep = loadAshesPrep();
        File source = new File(Bukkit.getWorldContainer(), ASHES_BASE);
        if (prep == null || !source.isDirectory()) {
            ashesCopying.set(false);
            return;
        }
        copyBase(source, InstanceManager.WORLD_PREFIX + "f3_" + shortId(), false, world -> {
            AshesEncounter.resetForReuse(plugin, world, prep);
            parkWorld(world);
            world.setSpawnLocation(new Location(world, AshesEncounter.SPAWN_X + 0.5, AshesEncounter.SPAWN_Y,
                    AshesEncounter.SPAWN_Z + 0.5));
            spareAshes = new WarmAshes(world, prep);
        }, ashesCopying);
    }

    /**
     * Copies a base world folder to {@code name} off the server thread (no lock, no uid: the copy gets its own),
     * then loads it on the server thread and hands it to {@code ready}. The base never saves during a run, so
     * its files are stable while it is in use; left-over mobs in the copy are swept when their chunks load.
     */
    private void copyBase(File source, String name, boolean voidGenerator, java.util.function.Consumer<World> ready,
                          AtomicBoolean flag) {
        copying.add(name);
        long started = System.currentTimeMillis();
        File target = new File(Bukkit.getWorldContainer(), name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean copied = copyFolder(source.toPath(), target.toPath());
            long copyMs = System.currentTimeMillis() - started;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!copied || !plugin.isEnabled()) {
                        deleteRecursively(target);
                        plugin.getLogger().warning("Could not copy " + source.getName() + " to " + name + ".");
                        return;
                    }
                    World world = DungeonRunMetrics.timed(null, "load base copy", () -> {
                        WorldCreator creator = new WorldCreator(name);
                        if (voidGenerator) {
                            creator.generator(VoidChunkGenerator.forDungeons());
                        }
                        creator.generateStructures(false);
                        creator.environment(World.Environment.NORMAL);
                        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);
                        World loaded = creator.createWorld();
                        if (loaded != null) {
                            InstanceManager.slimExistingWorld(loaded);
                        }
                        return loaded;
                    });
                    if (world == null) {
                        deleteRecursively(target);
                        plugin.getLogger().warning("Could not load the base copy " + name + ".");
                        return;
                    }
                    ready.accept(world);
                    plugin.getLogger().info("Base copy ready for a second party: " + name + " (copy " + copyMs
                            + " ms off-thread, " + (System.currentTimeMillis() - started) + " ms in all).");
                } finally {
                    copying.remove(name);
                    flag.set(false);
                }
            });
        });
    }

    private static boolean copyFolder(Path from, Path to) {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if (file.equals("session.lock") || file.equals("uid.dat")) {
                    continue;
                }
                Path into = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(into);
                } else {
                    Files.copy(path, into, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void abandonFloorOne(String name, World world) {
        if (name.equals(floorOneInProgress)) {
            floorOneInProgress = null;
        }
        if (world != null) {
            FloorOnePool.forget(world.getName());
            DungeonChunks.release(plugin, world);
            unloadDiscard(world);
        }
        floorOneBuilding.set(false);
    }

    boolean isReusableEndless(String worldName) {
        return ENDLESS_BASE.equals(worldName) || claimedEndless.containsKey(worldName);
    }

    World takeVoid() {
        World world = warmVoid;
        warmVoid = null;
        if (world != null) {
            plugin.getLogger().info("Claimed warm void dungeon world: " + world.getName());
            plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureWarmVoid, 40L);
        }
        return world;
    }

    WarmEndless takeEndless() {
        WarmEndless ready = warmEndless;
        warmEndless = null;
        if (ready == null) {
            ready = spareEndless;
            spareEndless = null;
        }
        if (ready != null) {
            claimedEndless.put(ready.world().getName(), ready);
            plugin.getLogger().info("Claimed Endless XL base: " + ready.world().getName());
            // Do NOT paste again — recycle after the run. The next party gets a copy.
            plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureSpareEndless, 40L);
        }
        return ready;
    }

    /** True while Floor 2 exists but every copy of it is in a run (the caller says "busy" instead of pasting). */
    boolean endlessBusy() {
        return warmEndless == null && spareEndless == null
                && (claimedEndless.containsKey(ENDLESS_BASE) || new File(Bukkit.getWorldContainer(), ENDLESS_BASE).isDirectory());
    }

    /** True while Floor 3 exists but every copy of it is in a run. */
    boolean ashesBusy() {
        return warmAshes == null && spareAshes == null
                && (claimedAshes.containsKey(ASHES_BASE) || Bukkit.getWorld(ASHES_BASE) != null);
    }

    /** After players leave: wipe mobs, restore gate, put base back in the pool. */
    boolean recycleEndless(World world) {
        if (world == null) {
            return false;
        }
        WarmEndless meta = claimedEndless.remove(world.getName());
        if (meta == null && ENDLESS_BASE.equals(world.getName()) && warmEndless == null) {
            // Recover meta from disk if claim tracking was lost.
            EndlessSchemBuilder.PasteResult paste = loadPasteMeta();
            EndlessEncounter.Prep prep = loadPrep();
            if (paste != null && prep != null) {
                meta = new WarmEndless(world, paste, prep);
            }
        }
        if (meta == null) {
            return false;
        }
        EndlessEncounter.resetForReuse(plugin, world, meta.prep());
        parkWorld(world);
        if (ENDLESS_BASE.equals(world.getName())) {
            warmEndless = new WarmEndless(world, meta.paste(), meta.prep());
            plugin.getLogger().info("Endless XL base returned to warm pool.");
        } else if (spareEndless == null) {
            spareEndless = new WarmEndless(world, meta.paste(), meta.prep());
            plugin.getLogger().info("Floor 2 copy " + world.getName() + " kept as the spare.");
        } else {
            unloadDiscard(world);
            plugin.getLogger().info("Floor 2 copy " + world.getName() + " thrown away (a spare is ready).");
        }
        return true;
    }

    WarmAshes takeAshes() {
        WarmAshes ready = warmAshes;
        warmAshes = null;
        if (ready == null) {
            ready = spareAshes;
            spareAshes = null;
        }
        if (ready != null) {
            claimedAshes.put(ready.world().getName(), ready);
            plugin.getLogger().info("Claimed Ashes Floor 3 base: " + ready.world().getName());
            plugin.getServer().getScheduler().runTaskLater(plugin, this::ensureSpareAshes, 40L);
        }
        return ready;
    }

    /** Cold start when warm slot was empty — still reusable base. */
    void claimColdAshes(World world, AshesEncounter.Prep prep) {
        if (world == null || prep == null) {
            return;
        }
        claimedAshes.put(world.getName(), new WarmAshes(world, prep));
        saveAshesPrep(prep);
    }

    boolean recycleAshes(World world) {
        if (world == null) {
            return false;
        }
        WarmAshes meta = claimedAshes.remove(world.getName());
        if (meta == null && ASHES_BASE.equals(world.getName()) && warmAshes == null) {
            AshesEncounter.Prep prep = loadAshesPrep();
            if (prep != null) {
                meta = new WarmAshes(world, prep);
            }
        }
        if (meta == null) {
            return false;
        }
        AshesEncounter.resetForReuse(plugin, world, meta.prep());
        parkWorld(world);
        world.setSpawnLocation(new Location(world, AshesEncounter.SPAWN_X + 0.5, AshesEncounter.SPAWN_Y,
                AshesEncounter.SPAWN_Z + 0.5));
        if (ASHES_BASE.equals(world.getName())) {
            warmAshes = new WarmAshes(world, meta.prep());
            plugin.getLogger().info("Ashes Floor 3 base returned to warm pool.");
        } else if (spareAshes == null) {
            spareAshes = new WarmAshes(world, meta.prep());
            plugin.getLogger().info("Floor 3 copy " + world.getName() + " kept as the spare.");
        } else {
            unloadDiscard(world);
            plugin.getLogger().info("Floor 3 copy " + world.getName() + " thrown away (a spare is ready).");
        }
        return true;
    }

    private void ensureWarmVoid() {
        if (warmVoid != null || !voidBuilding.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (warmVoid != null) {
                    return;
                }
                String name = InstanceManager.WORLD_PREFIX + "warm_void_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                World world = DungeonRunMetrics.timed(null, "pre-build next void world",
                        () -> InstanceManager.createSlimVoidWorld(name));
                if (world == null) {
                    plugin.getLogger().warning("Warm void world create failed.");
                    return;
                }
                warmVoid = world;
                plugin.getLogger().info("Warm void dungeon ready: " + name);
            } finally {
                voidBuilding.set(false);
            }
        });
    }

    private void ensureWarmEndless() {
        if (warmEndless != null || !endlessBuilding.compareAndSet(false, true)) {
            return;
        }
        if (claimedEndless.containsKey(ENDLESS_BASE)) {
            endlessBuilding.set(false);
            return; // currently in a run
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (warmEndless != null || claimedEndless.containsKey(ENDLESS_BASE)) {
                    return;
                }
                long started = System.currentTimeMillis();
                File folder = new File(Bukkit.getWorldContainer(), ENDLESS_BASE);
                EndlessEncounter.Prep prep = loadPrep();
                EndlessSchemBuilder.PasteResult paste = loadPasteMeta();
                boolean schemChanged = schemFingerprintChanged(prep != null);
                boolean prepStale = prep == null || loadPrepVersion() != EndlessEncounter.PREP_VERSION;

                if (folder.isDirectory() && paste != null && !schemChanged) {
                    World world = loadOrCreateBaseWorld();
                    if (world == null) {
                        plugin.getLogger().warning("Could not load Endless XL base world.");
                        return;
                    }
                    if (prepStale) {
                        plugin.getLogger().info("Endless prep outdated — rebuilding gate/spots on existing base...");
                        prep = EndlessEncounter.prepare(
                                plugin,
                                world,
                                paste.minX(),
                                paste.maxX(),
                                paste.minY(),
                                paste.maxY(),
                                paste.minZ(),
                                paste.maxZ()
                        );
                        savePrep(prep, paste);
                    }
                    parkWorld(world);
                    warmEndless = new WarmEndless(world, paste, prep);
                    plugin.getLogger().info("Endless XL base loaded (no paste) in "
                            + (System.currentTimeMillis() - started) + "ms — spots=" + prep.mobSpotCount());
                    return;
                }

                if (!EndlessSchemBuilder.worldEditPresent()) {
                    plugin.getLogger().warning("Endless XL base missing and WorldEdit is offline.");
                    return;
                }

                plugin.getLogger().info("Building Endless XL base (one-time WorldEdit paste)...");
                World world = loadOrCreateBaseWorld();
                if (world == null) {
                    plugin.getLogger().warning("Endless XL base create failed.");
                    return;
                }
                // Clear leftover entities from a half-built base.
                for (org.bukkit.entity.Entity entity : new ArrayList<>(world.getEntities())) {
                    if (!(entity instanceof Player)) {
                        entity.remove();
                    }
                }
                paste = EndlessSchemBuilder.paste(plugin, world);
                // Prep IMMEDIATELY while pasted chunks are still loaded (delay caused spots=1).
                prep = EndlessEncounter.prepare(
                        plugin,
                        world,
                        paste.minX(),
                        paste.maxX(),
                        paste.minY(),
                        paste.maxY(),
                        paste.minZ(),
                        paste.maxZ()
                );
                savePrep(prep, paste);
                saveSchemFingerprint();
                parkWorld(world);
                world.setAutoSave(true);
                world.save();
                world.setAutoSave(false);
                warmEndless = new WarmEndless(world, paste, prep);
                plugin.getLogger().info("Endless XL base ready in " + (System.currentTimeMillis() - started)
                        + "ms (spots=" + prep.mobSpotCount() + ", door=" + prep.doorBlockCount()
                        + "). Later restarts skip paste.");
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Endless XL base failed: " + exception.getMessage());
            } finally {
                endlessBuilding.set(false);
            }
        });
    }

    private World loadOrCreateBaseWorld() {
        World existing = Bukkit.getWorld(ENDLESS_BASE);
        if (existing != null) {
            InstanceManager.slimExistingWorld(existing);
            return existing;
        }
        File folder = new File(Bukkit.getWorldContainer(), ENDLESS_BASE);
        if (folder.isDirectory()) {
            WorldCreator creator = new WorldCreator(ENDLESS_BASE);
            creator.generator(VoidChunkGenerator.forDungeons());
            creator.generateStructures(false);
            creator.environment(World.Environment.NORMAL);
            creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);
            World world = creator.createWorld();
            if (world != null) {
                InstanceManager.slimExistingWorld(world);
            }
            return world;
        }
        return InstanceManager.createSlimVoidWorld(ENDLESS_BASE);
    }

    private File prepFile() {
        return new File(plugin.getDataFolder(), "structures/endless/xl-prep.yml");
    }

    private File fingerprintFile() {
        return new File(plugin.getDataFolder(), "structures/endless/xl-schem.fp");
    }

    private void saveSchemFingerprint() {
        File schem = new File(plugin.getDataFolder(), EndlessSchemBuilder.RESOURCE_PATH);
        try {
            String fp = schem.length() + ":" + schem.lastModified();
            fingerprintFile().getParentFile().mkdirs();
            java.nio.file.Files.writeString(fingerprintFile().toPath(), fp);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not write schem fingerprint: " + exception.getMessage());
        }
    }

    private boolean schemFingerprintChanged(boolean hadPrep) {
        if (!hadPrep) {
            return true;
        }
        File schem = new File(plugin.getDataFolder(), EndlessSchemBuilder.RESOURCE_PATH);
        File fp = fingerprintFile();
        if (!schem.isFile() || !fp.isFile()) {
            return true;
        }
        try {
            String expected = schem.length() + ":" + schem.lastModified();
            String actual = java.nio.file.Files.readString(fp.toPath()).trim();
            return !expected.equals(actual);
        } catch (Exception exception) {
            return true;
        }
    }

    private void savePrep(EndlessEncounter.Prep prep, EndlessSchemBuilder.PasteResult paste) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("prepVersion", EndlessEncounter.PREP_VERSION);
        yaml.set("minX", paste.minX());
        yaml.set("maxX", paste.maxX());
        yaml.set("minY", paste.minY());
        yaml.set("maxY", paste.maxY());
        yaml.set("minZ", paste.minZ());
        yaml.set("maxZ", paste.maxZ());
        yaml.set("bossX", prep.bossX());
        yaml.set("bossY", prep.bossY());
        yaml.set("bossZ", prep.bossZ());
        yaml.set("doorSlice", prep.doorSlice());
        yaml.set("alongZ", prep.alongZ());
        List<String> door = new ArrayList<>();
        for (int[] xyz : prep.doorBlockList()) {
            door.add(xyz[0] + "," + xyz[1] + "," + xyz[2]);
        }
        yaml.set("door", door);
        List<String> spots = new ArrayList<>();
        for (int[] xyz : prep.mobSpotList()) {
            spots.add(xyz[0] + "," + xyz[1] + "," + xyz[2]);
        }
        yaml.set("spots", spots);
        try {
            prepFile().getParentFile().mkdirs();
            de.aetherion.core.persist.AtomicYaml.save(yaml, prepFile());
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save endless prep: " + exception.getMessage());
        }
    }

    private int loadPrepVersion() {
        File file = prepFile();
        if (!file.isFile()) {
            return -1;
        }
        return YamlConfiguration.loadConfiguration(file).getInt("prepVersion", 0);
    }

    private EndlessEncounter.Prep loadPrep() {
        File file = prepFile();
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> doorRaw = yaml.getStringList("door");
        List<String> spotRaw = yaml.getStringList("spots");
        if (doorRaw.isEmpty() || spotRaw.isEmpty()) {
            return null;
        }
        List<int[]> door = parseCoords(doorRaw);
        List<int[]> spots = parseCoords(spotRaw);
        if (door.isEmpty() || spots.isEmpty()) {
            return null;
        }
        return new EndlessEncounter.Prep(
                door,
                spots,
                yaml.getInt("bossX"),
                yaml.getInt("bossY", EndlessEncounter.SPAWN_Y),
                yaml.getInt("bossZ"),
                yaml.getInt("doorSlice"),
                yaml.getBoolean("alongZ", true)
        );
    }

    private EndlessSchemBuilder.PasteResult loadPasteMeta() {
        File file = prepFile();
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("minX")) {
            return null;
        }
        int minX = yaml.getInt("minX");
        int maxX = yaml.getInt("maxX");
        int minZ = yaml.getInt("minZ");
        int maxZ = yaml.getInt("maxZ");
        return new EndlessSchemBuilder.PasteResult(
                DungeonLayout.schemShell(minX, maxX, minZ, maxZ),
                minX,
                maxX,
                yaml.getInt("minY"),
                yaml.getInt("maxY"),
                minZ,
                maxZ
        );
    }

    private void ensureWarmAshes() {
        if (warmAshes != null || !ashesBuilding.compareAndSet(false, true)) {
            return;
        }
        if (claimedAshes.containsKey(ASHES_BASE)) {
            ashesBuilding.set(false);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (warmAshes != null || claimedAshes.containsKey(ASHES_BASE)) {
                    return;
                }
                File folder = new File(Bukkit.getWorldContainer(), ASHES_BASE);
                if (!folder.isDirectory() || !(new File(folder, "level.dat").isFile())) {
                    plugin.getLogger().warning("Ashes Floor 3 base missing (" + ASHES_BASE
                            + "). Copy Throne of Ashes world folder there.");
                    return;
                }
                long started = System.currentTimeMillis();
                World world = loadAshesWorld();
                if (world == null) {
                    plugin.getLogger().warning("Could not load Ashes Floor 3 base world.");
                    return;
                }
                AshesEncounter.Prep prep = loadAshesPrep();
                boolean prepStale = prep == null || loadAshesPrepVersion() != AshesEncounter.PREP_VERSION;
                if (prepStale) {
                    plugin.getLogger().info("Ashes prep building gate/spots...");
                    prep = AshesEncounter.prepare(plugin, world);
                    saveAshesPrep(prep);
                }
                parkWorld(world);
                world.setSpawnLocation(new Location(
                        world,
                        AshesEncounter.SPAWN_X + 0.5,
                        AshesEncounter.SPAWN_Y,
                        AshesEncounter.SPAWN_Z + 0.5
                ));
                warmAshes = new WarmAshes(world, prep);
                plugin.getLogger().info("Ashes Floor 3 base ready in "
                        + (System.currentTimeMillis() - started) + "ms — spots="
                        + prep.mobSpots().size() + " door=" + prep.doorBlocks().size());
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Ashes Floor 3 base failed: " + exception.getMessage());
            } finally {
                ashesBuilding.set(false);
            }
        });
    }

    private World loadAshesWorld() {
        World existing = Bukkit.getWorld(ASHES_BASE);
        if (existing != null) {
            InstanceManager.slimExistingWorld(existing);
            return existing;
        }
        WorldCreator creator = new WorldCreator(ASHES_BASE);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);
        World world = creator.createWorld();
        if (world != null) {
            InstanceManager.slimExistingWorld(world);
        }
        return world;
    }

    private File ashesPrepFile() {
        return new File(plugin.getDataFolder(), "structures/ashes/f3-prep.yml");
    }

    private void saveAshesPrep(AshesEncounter.Prep prep) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("prepVersion", AshesEncounter.PREP_VERSION);
            List<String> door = new ArrayList<>();
            for (int[] xyz : prep.doorBlocks()) {
                door.add(xyz[0] + "," + xyz[1] + "," + xyz[2]);
            }
            List<String> spots = new ArrayList<>();
            for (int[] xyz : prep.mobSpots()) {
                spots.add(xyz[0] + "," + xyz[1] + "," + xyz[2]);
            }
            yaml.set("door", door);
            yaml.set("spots", spots);
            ashesPrepFile().getParentFile().mkdirs();
            de.aetherion.core.persist.AtomicYaml.save(yaml, ashesPrepFile());
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save ashes prep: " + exception.getMessage());
        }
    }

    private int loadAshesPrepVersion() {
        File file = ashesPrepFile();
        if (!file.isFile()) {
            return -1;
        }
        return YamlConfiguration.loadConfiguration(file).getInt("prepVersion", -1);
    }

    private AshesEncounter.Prep loadAshesPrep() {
        File file = ashesPrepFile();
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<int[]> door = parseCoords(yaml.getStringList("door"));
        List<int[]> spots = parseCoords(yaml.getStringList("spots"));
        if (door.isEmpty() || spots.isEmpty()) {
            return null;
        }
        return new AshesEncounter.Prep(door, spots);
    }

    private static List<int[]> parseCoords(List<String> raw) {
        List<int[]> out = new ArrayList<>();
        for (String line : raw) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                out.add(new int[]{
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())
                });
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static void parkWorld(World world) {
        if (world == null) {
            return;
        }
        world.setSpawnLocation(new Location(
                world,
                EndlessEncounter.SPAWN_X + 0.5,
                EndlessEncounter.SPAWN_Y,
                EndlessEncounter.SPAWN_Z + 0.5
        ));
        int keep = 3;
        int spawnCx = EndlessEncounter.SPAWN_X >> 4;
        int spawnCz = EndlessEncounter.SPAWN_Z >> 4;
        for (Chunk chunk : world.getLoadedChunks()) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            if (Math.abs(cx - spawnCx) <= keep && Math.abs(cz - spawnCz) <= keep) {
                continue;
            }
            chunk.unload(true);
        }
    }

    private static void unloadDiscard(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        if (ENDLESS_BASE.equals(name)) {
            parkWorld(world);
            Bukkit.unloadWorld(world, true);
            return;
        }
        world.setAutoSave(false);
        for (Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(false);
        }
        Bukkit.unloadWorld(world, false);
        deleteRecursively(new File(Bukkit.getWorldContainer(), name));
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File child : children) {
                ok &= deleteRecursively(child);
            }
        }
        return file.delete() && ok;
    }
}
