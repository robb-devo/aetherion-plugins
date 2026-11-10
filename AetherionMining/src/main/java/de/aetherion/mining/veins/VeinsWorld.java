package de.aetherion.mining.veins;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.network.NetworkRouter;
import de.aetherion.core.network.RouteBook;
import de.aetherion.core.network.ServerNames;
import de.aetherion.mining.AetherionMining;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinsWorld {

    public static final String DEFAULT_NAME = "aether_veins";
    /**
     * Bump only when the procedural dig layout must wipe. Schematic mode ignores this —
     * the Amethyst Mines map is the hand-built {@code amethyst-mines.schem}, not Deep Mines.
     */
    public static final int LAYOUT = 5;
    public static final String DEPART_UP = "§e✦ §7The cage hauls you back up to daylight…";

    private final AetherionMining plugin;
    private final ConcurrentHashMap<UUID, Location> exits = new ConcurrentHashMap<>();
    private final File dataFile;
    private VeinsNpcs npcs;
    private long nextReset;
    private int generation;
    private int layout;
    private World world;
    private boolean digZonesReady;
    private boolean painting;
    private boolean schematicReady;
    /** The world on disk was created as the schematic's void host (not the retired Deep Mines dig world). */
    private boolean schematicHost;
    private Boolean hosted;
    private java.util.function.Supplier<Location> camp;

    public VeinsWorld(AetherionMining plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "veins.yml");
        loadData();
    }

    public void bind(VeinsNpcs npcs) {
        this.npcs = npcs;
    }

    /** The mine server's hub (Jawcamp) once it is built; leaving the Veins goes there instead of to the main world. */
    public void bindCamp(java.util.function.Supplier<Location> camp) {
        this.camp = camp;
    }

    public Location campSpot() {
        Location spot = camp == null ? null : camp.get();
        return spot != null && spot.getWorld() != null ? spot : null;
    }

    /**
     * Whether the Amethyst Mine runs on this backend. {@code veins.host: auto} (default) = only on the network's mine
     * server ({@code network.mines-server} in Core, mmo-m); every other backend sends /amethyst and the Foreman's
     * miners there. {@code local} = the old one-server behaviour (test boxes, no Core router).
     */
    public boolean hostedHere() {
        if (hosted == null) {
            NetworkRouter router = router();
            hosted = router == null || local()
                    || router.serverFor("mines").equalsIgnoreCase(router.here());
        }
        return hosted;
    }

    /** This backend is the mine server itself: the mine is here, daylight (the main world) is on another backend. */
    public boolean mineServer() {
        NetworkRouter router = router();
        return hostedHere() && !local() && router != null && !ServerNames.isMain(router.here());
    }

    private boolean local() {
        return "local".equalsIgnoreCase(plugin.getConfig().getString("veins.host", "auto"));
    }

    private boolean leavesByRoute() {
        NetworkRouter router = router();
        return mineServer() && router != null && router.canTravel();
    }

    private static NetworkRouter router() {
        AetherionCore core = AetherionCore.get();
        return core == null ? null : core.router();
    }

    /** Off the mine server: the character crosses to it, MinesHost lands it in place and {@code command} greets. */
    private boolean travelToMines(Player player, String command) {
        NetworkRouter router = router();
        if (router == null || !router.canTravel()) {
            player.sendMessage("§dThe mines §7are out of reach right now. Try again in a moment.");
            return false;
        }
        if (!router.travel(player, router.serverFor("mines"), RouteBook.Kind.COMMAND, command,
                NetworkRouter.DEPART_MINES, "mines")) {
            player.sendMessage("§7The mine cage is stuck. Try again in a moment.");
            return false;
        }
        return true;
    }

    /** Jawcamp, the camp in Ossarath's skull: across the network, or a walk on the mine server itself. */
    public boolean enterCamp(Player player) {
        if (player == null) {
            return false;
        }
        if (!hostedHere()) {
            return travelToMines(player, "jawcamp");
        }
        Location spot = campSpot();
        if (spot == null) {
            return enter(player);
        }
        if (!spot.getWorld().equals(player.getWorld()) || player.getLocation().distanceSquared(spot) > 64.0d) {
            player.teleport(spot);
            player.setFallDistance(0f);
        }
        player.sendMessage("§6Jawcamp §7· the camp in Ossarath's skull. §dThe Veins §7on the left, §4the Gullet §7at the back.");
        player.playSound(spot, org.bukkit.Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
        return true;
    }

    /** Back to the main world, where the player left it (Foreman Brann in Jawcamp). */
    public boolean exitToMain(Player player) {
        NetworkRouter router = router();
        if (player == null || router == null || !mineServer() || !router.canTravel()) {
            return false;
        }
        if (router.travel(player, router.serverFor("main"), RouteBook.Kind.NONE, "", DEPART_UP, "mines-exit")) {
            return true;
        }
        player.sendMessage("§7The cage up is stuck. Try again in a moment.");
        return false;
    }

    public String worldName() {
        return plugin.getConfig().getString("veins.world", DEFAULT_NAME);
    }

    public int radius() {
        return Math.max(16, plugin.getConfig().getInt("veins.radius", 250));
    }

    public int hubY() {
        return plugin.getConfig().getInt("veins.hub-y", 220);
    }

    public long resetMillis() {
        return Math.max(1L, plugin.getConfig().getLong("veins.reset-hours", 24)) * 60L * 60L * 1000L;
    }

    /**
     * When the next reset is due. {@code veins.reset-hour} (0-23, server time) pins it to that hour every day,
     * e.g. a quiet maintenance window; -1 keeps the rolling {@code veins.reset-hours} interval.
     */
    private long nextResetTime() {
        int hour = plugin.getConfig().getInt("veins.reset-hour", -1);
        if (hour < 0 || hour > 23) {
            return System.currentTimeMillis() + resetMillis();
        }
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toInstant().toEpochMilli();
    }

    /** {@code veins.daily-reset: false} turns the timed reset off (mmo-c only carries the jar for parity). */
    public boolean dailyResetEnabled() {
        return plugin.getConfig().getBoolean("veins.daily-reset", true);
    }

    /** True while the schematic is being pasted; entry stays closed until it finishes. */
    public boolean isRestoring() {
        return AmethystPaste.isBusy();
    }

    public boolean keepInventory() {
        return plugin.getConfig().getBoolean("veins.keep-inventory", true);
    }

    public World world() {
        return world;
    }

    public boolean isVeins(World check) {
        return check != null && check.getName().equalsIgnoreCase(worldName());
    }

    public boolean schematicMode() {
        return plugin.getConfig().getBoolean("veins.schematic-mode", true);
    }

    public boolean isSchematicReady() {
        return schematicReady;
    }

    public Location hubSpawn() {
        if (world == null) {
            return null;
        }
        // Prefer the placed Amethyst map spawn (schematic hub). hub-y 0,0 is the old deep-mines column.
        if (plugin.getConfig().contains("veins.spawn-x") || schematicMode()) {
            double x = plugin.getConfig().getDouble("veins.spawn-x", 8.5);
            double y = plugin.getConfig().getDouble("veins.spawn-y", 18.0);
            double z = plugin.getConfig().getDouble("veins.spawn-z", 8.5);
            float yaw = (float) plugin.getConfig().getDouble("veins.spawn-yaw", 0.0);
            float pitch = (float) plugin.getConfig().getDouble("veins.spawn-pitch", 0.0);
            return new Location(world, x, y, z, yaw, pitch);
        }
        return new Location(world, 0.5, hubY() + 1, 0.5, 0f, 0f);
    }

    public long nextResetAt() {
        // Off the mine server the local veins.yml is stale; the pinned reset hour is the same everywhere.
        return hostedHere() ? nextReset : nextResetTime();
    }

    /** Resets so far; bumps every daily reset (per-cycle tallies key off it). */
    public int generation() {
        return generation;
    }

    public boolean isDigZonesReady() {
        return digZonesReady;
    }

    public int digHalfExtent() {
        int configured = plugin.getConfig().getInt("veins.dig-half-extent", 0);
        if (configured > 0) {
            return Math.max(32, configured);
        }
        int outer = plugin.getConfig().getInt("veins.dig-outer-radius", 0);
        return Math.max(32, outer > 0 ? outer : Math.max(64, radius() - 32));
    }

    public int digDepth() {
        return Math.max(16, plugin.getConfig().getInt("veins.dig-depth", 48));
    }

    public int digHeight() {
        return Math.max(8, plugin.getConfig().getInt("veins.dig-height", 28));
    }

    public int digHubScan() {
        return Math.max(32, plugin.getConfig().getInt("veins.dig-hub-scan", 120));
    }

    public int digOuterRadius() {
        return digHalfExtent();
    }

    public long digSeed() {
        return plugin.getConfig().getLong("veins.dig-seed", 20260922L);
    }

    public int spawnProtectRadius() {
        return Math.max(8, plugin.getConfig().getInt("veins.spawn-protect-radius", 24));
    }

    /**
     * Live dig-volume paint (four zones). Kept from the richer live Mining jar — additive beside
     * chunk-gen geodes. Returns 1 if a paint job started, 0 if already ready / busy / unavailable.
     */
    public int ensureDigZones(CommandSender sender, boolean force) {
        if (schematicMode()) {
            if (sender != null) {
                sender.sendMessage("§7Schematic Amethyst Mines — dig paint disabled (Deep Mines retired).");
            }
            return 0;
        }
        World target = world != null && Bukkit.getWorld(world.getUID()) != null
                ? world
                : Bukkit.getWorld(worldName());
        if (target == null) {
            if (sender != null) {
                sender.sendMessage("§cAmethyst Area world / spawn unavailable.");
            }
            return 0;
        }
        world = target;
        Location spawn = hubSpawn();
        if (spawn == null) {
            if (sender != null) {
                sender.sendMessage("§cAmethyst Area world / spawn unavailable.");
            }
            return 0;
        }
        if (painting) {
            if (sender != null) {
                sender.sendMessage("§eDig paint already running…");
            }
            return 0;
        }
        if (!force && digZonesReady && layout >= LAYOUT) {
            return 0;
        }
        CommandSender out = sender != null ? sender : Bukkit.getConsoleSender();
        Location paintSpawn = spawn;
        painting = true;
        digZonesReady = false;
        if (force) {
            File stale = VeinsDigSnapshot.file(plugin.getDataFolder());
            if (stale.isFile() && !stale.delete()) {
                plugin.getLogger().warning("Could not delete stale dig snapshot before safe re-paint.");
            } else if (stale.isFile()) {
                out.sendMessage("§7Cleared stale dig snapshot (hub-safe re-paint).");
            }
        }
        VeinsDigZones.paintAsync(
                plugin,
                target,
                paintSpawn.getBlockX(),
                paintSpawn.getBlockY(),
                paintSpawn.getBlockZ(),
                digHalfExtent(),
                digDepth(),
                digHeight(),
                digHubScan(),
                digSeed(),
                out,
                plugin.getDataFolder(),
                () -> {
                    digZonesReady = true;
                    layout = LAYOUT;
                    painting = false;
                    VeinsGuard.protectSpawn(target, paintSpawn, spawnProtectRadius());
                    saveData();
                    scheduleSoftLight(out, paintSpawn);
                });
        return 1;
    }

    public void runSoftLight(CommandSender sender, World target, int centerX, int centerZ, int softRadius) {
        CommandSender out = sender != null ? sender : Bukkit.getConsoleSender();
        if (target == null) {
            out.sendMessage("§cNo world for softlight.");
            return;
        }
        try {
            Class<?> pass = Class.forName("de.aetherion.hub.util.SoftLightPass");
            pass.getMethod(
                            "run",
                            JavaPlugin.class,
                            CommandSender.class,
                            World.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class)
                    .invoke(
                            null,
                            softLightPlugin(),
                            out,
                            target,
                            centerX,
                            centerZ,
                            Math.max(64, softRadius),
                            plugin.getConfig().getInt("veins.softlight-min", 7),
                            plugin.getConfig().getInt("veins.softlight-step", 5),
                            plugin.getConfig().getInt("veins.softlight-level", 10));
        } catch (ReflectiveOperationException error) {
            out.sendMessage("§cSoftlight needs AetherionHub loaded (SoftLightPass).");
            plugin.getLogger().warning("SoftLightPass unavailable: " + error.getMessage());
        }
    }

    private void scheduleSoftLight(CommandSender sender, Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("veins.softlight-on-paint", false)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(
                (Plugin) plugin,
                () -> runSoftLight(sender, spawn.getWorld(), spawn.getBlockX(), spawn.getBlockZ(), digOuterRadius() + 16),
                40L);
    }

    private JavaPlugin softLightPlugin() {
        Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        return hub instanceof JavaPlugin javaPlugin ? javaPlugin : plugin;
    }

    public World ensureLoaded() {
        if (!hostedHere()) {
            return null;
        }
        if (schematicMode()) {
            return ensureSchematicWorld();
        }
        if (layout < LAYOUT) {
            rebuildNow();
            layout = LAYOUT;
            digZonesReady = false;
            if (nextReset <= 0L) {
                nextReset = nextResetTime();
            }
            saveData();
        }
        if (world != null && Bukkit.getWorld(world.getUID()) != null) {
            applyWorld(world);
            ensureDigZones(null, false);
            return world;
        }
        World existing = Bukkit.getWorld(worldName());
        if (existing != null) {
            world = existing;
            applyWorld(world);
            decorate();
            ensureDigZones(null, false);
            return world;
        }
        world = create();
        if (world != null) {
            decorate();
            ensureDigZones(null, false);
        }
        if (nextReset <= 0L) {
            nextReset = nextResetTime();
            saveData();
        }
        return world;
    }

    /**
     * Hand-built Amethyst Mines map. Never runs Deep Mines chunk gen / dig paint.
     * A pasted map on disk is loaded as it is, so a restart no longer wipes and re-pastes it.
     * Only the retired Deep Mines dig world (or no world at all) is wiped and pasted fresh.
     */
    private World ensureSchematicWorld() {
        if (world != null && Bukkit.getWorld(world.getUID()) != null) {
            applyWorld(world);
            return world;
        }
        World existing = Bukkit.getWorld(worldName());
        boolean onDisk = new File(Bukkit.getWorldContainer(), worldName()).isDirectory();
        if ((existing != null || onDisk) && schematicHost) {
            world = existing != null ? existing : createVoid();
            if (world != null) {
                applyWorld(world);
                if (!schematicReady) {
                    // A reset or first paste did not finish (crash, stop): restore in place, off the main thread.
                    plugin.getLogger().info("Amethyst Mines map incomplete — restoring from amethyst-mines.schem");
                    pasteSchematic(Bukkit.getConsoleSender(), true);
                }
            }
            return world;
        }
        // Retired dig world on disk, or first schematic install: wipe and paste the real map.
        plugin.getLogger().info("Amethyst Mines schematic mode — installing amethyst-mines.schem (Deep Mines retired)");
        rebuildNow();
        world = createVoid();
        if (world != null) {
            schematicHost = true;
            applyWorld(world);
            pasteSchematic(Bukkit.getConsoleSender(), false);
        }
        if (nextReset <= 0L) {
            nextReset = nextResetTime();
        }
        saveData();
        return world;
    }

    /** Admin paste ({@code /deepmines paste}): pastes with the configured {@code veins.ignore-air}. */
    public boolean pasteSchematic(CommandSender sender) {
        return pasteSchematic(sender, false);
    }

    /**
     * Starts the schematic paste. With FAWE it runs on the async queue and this returns right away;
     * the map counts as ready once the paste reports back on the main thread.
     *
     * @param restore paste air too, so the map comes back exactly as built
     * @return false when nothing was started (no world, schematic missing, or a paste already running)
     */
    public boolean pasteSchematic(CommandSender sender, boolean restore) {
        World target = world != null ? world : Bukkit.getWorld(worldName());
        if (target == null) {
            target = ensureLoaded();
        }
        if (target == null) {
            return false;
        }
        world = target;
        World pasted = target;
        return AmethystPaste.pasteAsync(plugin, pasted, sender, restore, ok -> {
            if (!ok) {
                return;
            }
            schematicReady = true;
            schematicHost = true;
            digZonesReady = true;
            layout = LAYOUT;
            Location spawn = hubSpawn();
            if (spawn != null && spawn.getWorld() != null) {
                spawn.getWorld().setSpawnLocation(spawn);
            }
            if (npcs != null) {
                npcs.spawnExit(pasted, (int) Math.floor(plugin.getConfig().getDouble("veins.spawn-y", 18.0)));
            }
            if (plugin.getIsle() != null) {
                plugin.getIsle().forgetPlaced(pasted);
            }
            saveData();
        });
    }

    public void rememberExit(Player player) {
        if (player == null || isVeins(player.getWorld())) {
            return;
        }
        exits.put(player.getUniqueId(), player.getLocation().clone());
        saveData();
    }

    public Location popExit(Player player) {
        if (player == null) {
            return overworldSpawn();
        }
        Location saved = exits.remove(player.getUniqueId());
        saveData();
        if (saved != null && saved.getWorld() != null && !isVeins(saved.getWorld())) {
            return saved;
        }
        return overworldSpawn();
    }

    public boolean enter(Player player) {
        if (player != null && !hostedHere()) {
            return travelToMines(player, "amethyst enter");
        }
        if (player != null && schematicMode() && isRestoring()) {
            player.sendMessage("§dThe Amethyst Mine §7is being restored. Try again in a moment.");
            return false;
        }
        World veins = ensureLoaded();
        if (player == null || veins == null) {
            return false;
        }
        Location spawn = hubSpawn();
        // Crossed from another backend: MinesHost already put them on the hub, so only the greeting is left.
        boolean arrived = isVeins(player.getWorld()) && player.getLocation().distanceSquared(spawn) < 64.0d;
        rememberExit(player);
        if (player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        if (!arrived) {
            player.teleport(spawn);
            player.setFallDistance(0f);
        }
        player.sendMessage("§dThe Amethyst Mine §8(The Veins) §7· mine anything but the hub. Corners have favourites; geodes grow deeper down.");
        player.playSound(spawn, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        return true;
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        Location spot = mineServer() ? campSpot() : null;
        if (spot != null) {
            player.teleport(spot);
            player.setFallDistance(0f);
            player.sendMessage("§7Back to §6Jawcamp§7.");
            player.playSound(spot, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
            return true;
        }
        if (leavesByRoute()) {
            // Back on the main world they stand where they left it: the Foreman at the mine road.
            NetworkRouter router = router();
            if (router.travel(player, router.serverFor("main"), RouteBook.Kind.NONE, "", DEPART_UP, "mines-exit")) {
                return true;
            }
            player.sendMessage("§7The cage up is stuck. Try again in a moment.");
            return false;
        }
        Location target = popExit(player);
        player.teleport(target);
        player.setFallDistance(0f);
        player.sendMessage("§7Back to daylight.");
        player.playSound(target, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
        return true;
    }

    public void tickReset() {
        if (!hostedHere() || !dailyResetEnabled() || isRestoring()) {
            return;
        }
        if (nextReset <= 0L) {
            nextReset = nextResetTime();
            saveData();
            return;
        }
        if (System.currentTimeMillis() < nextReset) {
            return;
        }
        reset("The Amethyst Mine closed for the night. Stone is new again, and so are the geodes.");
    }

    public void reset(String reason) {
        if (!hostedHere()) {
            plugin.getLogger().info("The Amethyst Mine runs on " + router().serverFor("mines") + ", reset it there.");
            return;
        }
        if (schematicMode()) {
            resetSchematic(reason);
            return;
        }
        World current = world != null ? world : Bukkit.getWorld(worldName());
        Location fallback = overworldSpawn();
        if (current != null) {
            for (Player occupant : new ArrayList<>(current.getPlayers())) {
                occupant.sendMessage("§8" + reason);
                occupant.teleport(fallback);
                occupant.setFallDistance(0f);
                exits.remove(occupant.getUniqueId());
            }
            current.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
            File folder = current.getWorldFolder();
            if (!current.getPlayers().isEmpty()) {
                plugin.getLogger().warning("The Veins still has players. Reset skipped.");
                return;
            }
            if (!Bukkit.unloadWorld(current, false)) {
                plugin.getLogger().warning("Could not unload The Veins.");
                return;
            }
            world = null;
            deleteLater(folder, 20L);
            deleteLater(folder, 80L);
        } else {
            deleteLater(new File(Bukkit.getWorldContainer(), worldName()), 20L);
        }
        generation++;
        nextReset = nextResetTime();
        saveData();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world = create();
            if (world != null) {
                decorate();
            }
        }, 100L);
        plugin.getLogger().info("The Veins reset. Generation " + generation + ".");
    }

    /**
     * Daily reset in schematic mode: players out, loose entities gone, then the schematic is pasted over the
     * live world in place. The world is no longer unloaded, deleted and recreated, and the paste runs on FAWE's
     * async queue, so the main thread never stalls on it (10-18 watchdog stop on mmo-r).
     */
    private void resetSchematic(String reason) {
        if (isRestoring()) {
            plugin.getLogger().warning("Amethyst Mines paste still running. Reset skipped.");
            return;
        }
        World current = world != null ? world : Bukkit.getWorld(worldName());
        Location fallback = overworldSpawn();
        if (current != null) {
            boolean up = leavesByRoute() || campSpot() != null;
            for (Player occupant : new ArrayList<>(current.getPlayers())) {
                occupant.sendMessage("§8" + reason);
                exits.remove(occupant.getUniqueId());
                // On the mine server daylight is the main world: they ride up, the hop freezes them meanwhile.
                if (up && leave(occupant)) {
                    continue;
                }
                occupant.teleport(fallback);
                occupant.setFallDistance(0f);
            }
            current.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
        }
        schematicReady = false;
        generation++;
        nextReset = nextResetTime();
        saveData();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World target = ensureSchematicWorld();
            // ensureSchematicWorld already started a paste when it had to load or build the world.
            if (target != null && !isRestoring() && !schematicReady) {
                pasteSchematic(Bukkit.getConsoleSender(), true);
            }
        }, 40L);
        plugin.getLogger().info("Amethyst Mines schematic reset. Generation " + generation + ".");
    }

    public void rescueIfBuried(Player player) {
        if (player == null || !isVeins(player.getWorld())) {
            return;
        }
        Location feet = player.getLocation();
        if (feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable()) {
            return;
        }
        Location spawn = hubSpawn();
        if (spawn != null) {
            player.teleport(spawn);
            player.setFallDistance(0f);
        }
    }

    private void decorate() {
        if (world == null || schematicMode()) {
            return;
        }
        VeinsHub.build(world, hubY());
        if (npcs != null) {
            npcs.spawnExit(world, hubY());
        }
    }

    private World create() {
        WorldCreator creator = new WorldCreator(worldName());
        creator.generator(new VeinsChunkGenerator(radius(), hubY()));
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        creator.seed(System.currentTimeMillis());
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().warning("Could not create The Veins.");
            return null;
        }
        applyWorld(created);
        return created;
    }

    /** Empty void host for the Amethyst Mines schematic — no Deep Mines stone cube. */
    private World createVoid() {
        WorldCreator creator = new WorldCreator(worldName());
        creator.generator(new ChunkGenerator() {
            @Override
            public boolean shouldGenerateNoise() {
                return false;
            }

            @Override
            public boolean shouldGenerateSurface() {
                return false;
            }

            @Override
            public boolean shouldGenerateCaves() {
                return false;
            }

            @Override
            public boolean shouldGenerateDecorations() {
                return false;
            }

            @Override
            public boolean shouldGenerateMobs() {
                return false;
            }

            @Override
            public boolean shouldGenerateStructures() {
                return false;
            }
        });
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        creator.seed(20260920L);
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().warning("Could not create Amethyst Mines void world.");
            return null;
        }
        return created;
    }

    private void rebuildNow() {
        World existing = world != null ? world : Bukkit.getWorld(worldName());
        Location fallback = overworldSpawn();
        File folder = existing != null
                ? existing.getWorldFolder()
                : new File(Bukkit.getWorldContainer(), worldName());
        if (existing != null) {
            for (Player occupant : new ArrayList<>(existing.getPlayers())) {
                occupant.sendMessage("§8The Veins is being rebuilt.");
                occupant.teleport(fallback);
                occupant.setFallDistance(0f);
            }
            existing.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
            if (!existing.getPlayers().isEmpty() || !Bukkit.unloadWorld(existing, false)) {
                plugin.getLogger().warning("Could not unload The Veins for rebuild.");
            }
        }
        world = null;
        deleteRecursively(folder);
        plugin.getLogger().info("The Veins layout " + LAYOUT + " — old world removed.");
    }

    private void applyWorld(World target) {
        target.setAutoSave(true);
        target.setKeepSpawnInMemory(true);
        target.setSpawnFlags(false, false);
        target.setDifficulty(Difficulty.PEACEFUL);
        target.setPVP(false);
        target.setTime(18000L);
        target.setStorm(false);
        target.setThundering(false);
        Location spawn;
        if (schematicMode() || plugin.getConfig().contains("veins.spawn-x")) {
            spawn = new Location(
                    target,
                    plugin.getConfig().getDouble("veins.spawn-x", 8.5),
                    plugin.getConfig().getDouble("veins.spawn-y", 18.0),
                    plugin.getConfig().getDouble("veins.spawn-z", 8.5),
                    (float) plugin.getConfig().getDouble("veins.spawn-yaw", 0.0),
                    (float) plugin.getConfig().getDouble("veins.spawn-pitch", 0.0)
            );
        } else {
            spawn = new Location(target, radius() + 8, hubY() + 1, 0);
        }
        target.setSpawnLocation(spawn);
        VeinsGuard.open(target);
        target.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        target.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        target.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        target.setGameRule(GameRule.MOB_GRIEFING, false);
        target.setGameRule(GameRule.DO_FIRE_TICK, false);
        target.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        target.setGameRule(GameRule.KEEP_INVENTORY, keepInventory());
        target.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        target.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        target.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        target.setGameRule(GameRule.DISABLE_RAIDS, true);
        var border = target.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(radius() * 2.0);
        border.setDamageBuffer(2.0);
        border.setWarningDistance(8);
    }

    private Location overworldSpawn() {
        if (Bukkit.getWorlds().isEmpty()) {
            return new Location(null, 0, 64, 0);
        }
        World main = Bukkit.getWorlds().get(0);
        if (isVeins(main) && Bukkit.getWorlds().size() > 1) {
            main = Bukkit.getWorlds().get(1);
        }
        return main.getSpawnLocation();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            nextReset = 0L;
            generation = 0;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        nextReset = yaml.getLong("next-reset", 0L);
        generation = yaml.getInt("generation", 0);
        layout = yaml.getInt("layout", 0);
        digZonesReady = yaml.getBoolean("dig-zones-ready", layout >= LAYOUT);
        schematicReady = yaml.getBoolean("schematic-ready", false);
        // Older veins.yml has no flag: a map that was pasted once sits in a void host.
        schematicHost = yaml.getBoolean("schematic-host", schematicReady);
        exits.clear();
        if (yaml.isConfigurationSection("exits")) {
            for (String key : yaml.getConfigurationSection("exits").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String worldName = yaml.getString("exits." + key + ".world");
                    World world = worldName == null ? null : Bukkit.getWorld(worldName);
                    if (world == null) {
                        continue;
                    }
                    exits.put(id, new Location(
                            world,
                            yaml.getDouble("exits." + key + ".x"),
                            yaml.getDouble("exits." + key + ".y"),
                            yaml.getDouble("exits." + key + ".z"),
                            (float) yaml.getDouble("exits." + key + ".yaw"),
                            (float) yaml.getDouble("exits." + key + ".pitch")
                    ));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveData() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-reset", nextReset);
        yaml.set("generation", generation);
        yaml.set("layout", layout);
        yaml.set("dig-zones-ready", digZonesReady);
        yaml.set("schematic-ready", schematicReady);
        yaml.set("schematic-host", schematicHost);
        exits.forEach((id, location) -> {
            if (location == null || location.getWorld() == null) {
                return;
            }
            String path = "exits." + id;
            yaml.set(path + ".world", location.getWorld().getName());
            yaml.set(path + ".x", location.getX());
            yaml.set(path + ".y", location.getY());
            yaml.set(path + ".z", location.getZ());
            yaml.set(path + ".yaw", location.getYaw());
            yaml.set(path + ".pitch", location.getPitch());
        });
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save veins.yml: " + exception.getMessage());
        }
    }

    private void deleteLater(File folder, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (folder != null && folder.exists() && !deleteRecursively(folder)) {
                plugin.getLogger().warning("Could not fully delete The Veins folder.");
            }
        }, delayTicks);
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
