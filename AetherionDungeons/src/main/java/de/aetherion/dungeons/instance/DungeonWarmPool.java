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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Map<String, WarmEndless> claimedEndless = new ConcurrentHashMap<>();
    private final Map<String, WarmAshes> claimedAshes = new ConcurrentHashMap<>();

    private volatile WarmEndless warmEndless;
    private volatile WarmAshes warmAshes;
    private volatile World warmVoid;

    DungeonWarmPool(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    record WarmEndless(World world, EndlessSchemBuilder.PasteResult paste, EndlessEncounter.Prep prep) {
    }

    record WarmAshes(World world, AshesEncounter.Prep prep) {
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
    }

    void ensureAll() {
        ensureWarmVoid();
        ensureWarmEndless();
        ensureWarmAshes();
    }

    void shutdown() {
        WarmEndless endless = warmEndless;
        warmEndless = null;
        if (endless != null && endless.world() != null) {
            parkWorld(endless.world());
            Bukkit.unloadWorld(endless.world(), true);
        }
        for (WarmEndless claimed : List.copyOf(claimedEndless.values())) {
            if (claimed.world() != null) {
                parkWorld(claimed.world());
                Bukkit.unloadWorld(claimed.world(), true);
            }
        }
        claimedEndless.clear();
        WarmAshes ashes = warmAshes;
        warmAshes = null;
        if (ashes != null && ashes.world() != null) {
            parkWorld(ashes.world());
            Bukkit.unloadWorld(ashes.world(), true);
        }
        for (WarmAshes claimed : List.copyOf(claimedAshes.values())) {
            if (claimed.world() != null) {
                parkWorld(claimed.world());
                Bukkit.unloadWorld(claimed.world(), true);
            }
        }
        claimedAshes.clear();
        World voidWorld = warmVoid;
        warmVoid = null;
        if (voidWorld != null) {
            unloadDiscard(voidWorld);
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
        World voidWorld = warmVoid;
        return voidWorld != null && worldName.equals(voidWorld.getName());
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
        if (ready != null) {
            claimedEndless.put(ready.world().getName(), ready);
            plugin.getLogger().info("Claimed Endless XL base: " + ready.world().getName());
            // Do NOT paste again — recycle after the run.
        }
        return ready;
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
        warmEndless = new WarmEndless(world, meta.paste(), meta.prep());
        plugin.getLogger().info("Endless XL base returned to warm pool.");
        return true;
    }

    WarmAshes takeAshes() {
        WarmAshes ready = warmAshes;
        warmAshes = null;
        if (ready != null) {
            claimedAshes.put(ready.world().getName(), ready);
            plugin.getLogger().info("Claimed Ashes Floor 3 base: " + ready.world().getName());
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
        warmAshes = new WarmAshes(world, meta.prep());
        plugin.getLogger().info("Ashes Floor 3 base returned to warm pool.");
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
                World world = InstanceManager.createSlimVoidWorld(name);
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
        if (!claimedEndless.isEmpty()) {
            endlessBuilding.set(false);
            return; // currently in a run
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (warmEndless != null || !claimedEndless.isEmpty()) {
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
            yaml.save(prepFile());
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
        if (!claimedAshes.isEmpty()) {
            ashesBuilding.set(false);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (warmAshes != null || !claimedAshes.isEmpty()) {
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
            yaml.save(ashesPrepFile());
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
