package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exile backend host (MMO-C): apply transfer snapshots, park players on
 * {@code aetherion_exile}, first-join ferry cutscene, unload foreign Multiverse worlds.
 *
 * <p>Only arms when this Velocity backend is the exile server — zero cost on Hub/MMO-R.
 */
public final class ExileHost implements Listener {

    private final AetherionCore plugin;
    private final ExileLink link;
    private final TransferSnapshotStore snapshots;
    private final ExileArrival arrival;
    private de.aetherion.core.exile.ExileDoors doors;
    private de.aetherion.core.exile.ExileLift lift;
    private final Set<UUID> arriving = ConcurrentHashMap.newKeySet();

    private final String worldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;
    private final Set<String> keepWorlds;
    /** Boss worlds that live on this backend next to the island (instances / sites). Never retired. */
    private static final Set<String> BOSS_WORLDS = Set.of("helios_requiem", "world_eater", "ashen_void");
    private final List<String> retireWorlds;

    public ExileHost(AetherionCore plugin, ExileLink link, TransferSnapshotStore snapshots) {
        this.plugin = plugin;
        this.link = link;
        this.snapshots = snapshots;
        var cfg = plugin.getConfig();
        this.worldName = cfg.getString("exile.world", "aetherion_exile");
        this.spawnX = cfg.getDouble("exile.spawn.x", 162.5);
        this.spawnY = cfg.getDouble("exile.spawn.y", 73.0);
        this.spawnZ = cfg.getDouble("exile.spawn.z", 6.5);
        this.spawnYaw = (float) cfg.getDouble("exile.spawn.yaw", 90.0);
        this.spawnPitch = (float) cfg.getDouble("exile.spawn.pitch", -8.0);
        this.keepWorlds = new HashSet<>();
        for (String name : cfg.getStringList("exile.keep-worlds")) {
            if (name != null && !name.isBlank()) {
                keepWorlds.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (keepWorlds.isEmpty()) {
            keepWorlds.add(worldName.toLowerCase(Locale.ROOT));
        }
        keepWorlds.addAll(BOSS_WORLDS);
        this.retireWorlds = cfg.getStringList("exile.retire-worlds");
        this.arrival = new ExileArrival(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::bootstrap, 40L);
        plugin.getLogger().info("ExileHost armed · world=" + worldName
                + " spawn=" + spawnX + "," + spawnY + "," + spawnZ
                + " keep=" + keepWorlds);
    }

    public ExileArrival arrival() {
        return arrival;
    }

    public ExileLink link() {
        return link;
    }

    /** Gates, boss rituals and the sealed castle (mmo-c only, null elsewhere). */
    public de.aetherion.core.exile.ExileDoors doors() {
        return doors;
    }

    public void shutdown() {
        arrival.shutdown();
        if (doors != null) {
            doors.shutdown();
        }
        if (lift != null) {
            lift.shutdown();
        }
    }

    /** The exile island world when it is loaded here, else null. */
    public World world() {
        return exileWorld();
    }

    World exileWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        // Multiverse sometimes uses a minecraft: prefix in config only — try bare name.
        for (World loaded : Bukkit.getWorlds()) {
            if (loaded.getName().equalsIgnoreCase(worldName)) {
                return loaded;
            }
        }
        return null;
    }

    Location spawnLocation() {
        World world = exileWorld();
        if (world == null) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    private void bootstrap() {
        if (!link.isExileServer()) {
            plugin.getLogger().info("ExileHost idle — this backend is " + plugin.link().serverName()
                    + ", not " + link.exileServer());
            return;
        }
        World world = exileWorld();
        if (world == null) {
            plugin.getLogger().warning("Exile world '" + worldName + "' is not loaded. Load it with Multiverse.");
        } else {
            if (!(world.getGenerator() instanceof de.aetherion.core.world.VoidChunkGenerator)) {
                plugin.getLogger().warning("[ExileVoid] " + worldName + " generates terrain for new chunks ("
                        + (world.getGenerator() == null ? "vanilla" : world.getGenerator().getClass().getSimpleName())
                        + "). Set worlds." + worldName + ".generator: AetherionCore in bukkit.yml and no generator "
                        + "for it in Multiverse.");
            } else {
                plugin.getLogger().info("[ExileVoid] " + worldName + " uses the void generator.");
            }
            tuneWorld(world);
            arrival.purgeRigs(world);
            world.setSpawnLocation(spawnLocation());
            de.aetherion.core.exile.ExileGateCut.ensure(plugin, world);
            doors = new de.aetherion.core.exile.ExileDoors(plugin);
            doors.build(world);
            lift = new de.aetherion.core.exile.ExileLift(plugin);
            lift.arm(world);
            loadFinaleWorld();
        }
        retireForeignWorlds();
    }

    /** Aetherion's Ashen Void (behind the Great Doors) lives here when its folder does; void generator, no Multiverse. */
    private void loadFinaleWorld() {
        String name = "ashen_void";
        if (Bukkit.getWorld(name) != null || !new java.io.File(Bukkit.getWorldContainer(), name).isDirectory()) {
            return;
        }
        World finale = new org.bukkit.WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new de.aetherion.core.world.VoidChunkGenerator())
                .generateStructures(false)
                .createWorld();
        if (finale != null) {
            finale.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            finale.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            finale.setGameRule(GameRule.KEEP_INVENTORY, true);
            plugin.getLogger().info("[ExileFinale] " + name + " loaded for the Great Doors.");
        }
    }

    private void tuneWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        try {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        } catch (Throwable ignored) {
        }
        world.setTime(12400L);
        world.setStorm(false);
        world.setThundering(false);
        unpeaceful(plugin, world);
    }

    /**
     * The island's bosses (the Hollow Sun, Seraphine) are hostile mobs: in peaceful the server discards their body
     * the tick it spawns ({@code cause=DESPAWN}) and a ritual answers "ask staff". The island has no mob spawning,
     * so a real difficulty only reaches the bosses. {@code exile.difficulty} (default easy, as the main world).
     */
    public static void unpeaceful(AetherionCore plugin, World world) {
        if (world == null || world.getDifficulty() != org.bukkit.Difficulty.PEACEFUL) {
            return;
        }
        org.bukkit.Difficulty want;
        try {
            want = org.bukkit.Difficulty.valueOf(
                    plugin.getConfig().getString("exile.difficulty", "easy").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            want = org.bukkit.Difficulty.EASY;
        }
        if (want == org.bukkit.Difficulty.PEACEFUL) {
            want = org.bukkit.Difficulty.EASY;
        }
        world.setDifficulty(want);
        plugin.getLogger().warning("[Exile] " + world.getName() + " was peaceful: boss bodies would vanish on spawn. Now "
                + want.name().toLowerCase(Locale.ROOT) + ".");
    }

    /**
     * Unload every loaded world that isn't exile. Folder deletion / Multiverse
     * cleanup is an ops step so we never wipe map art from a hot enable.
     */
    private void retireForeignWorlds() {
        if (!plugin.getConfig().getBoolean("exile.unload-foreign-worlds", true)) {
            return;
        }
        Location safe = spawnLocation();
        for (World world : List.copyOf(Bukkit.getWorlds())) {
            String name = world.getName().toLowerCase(Locale.ROOT);
            if (keepWorlds.contains(name)) {
                continue;
            }
            if (retireWorlds != null && !retireWorlds.isEmpty()) {
                boolean listed = false;
                for (String raw : retireWorlds) {
                    if (raw != null && name.equals(raw.trim().toLowerCase(Locale.ROOT))) {
                        listed = true;
                        break;
                    }
                }
                if (!listed) {
                    plugin.getLogger().info("ExileHost: unloading unexpected world '" + world.getName() + "'");
                }
            }
            if (safe != null) {
                for (Player player : List.copyOf(world.getPlayers())) {
                    player.teleport(safe);
                }
            }
            boolean ok = Bukkit.unloadWorld(world, true);
            plugin.getLogger().info("ExileHost: unload " + world.getName() + " → " + (ok ? "ok" : "failed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!link.isExileServer()) {
            return;
        }
        Player player = event.getPlayer();
        arriving.add(player.getUniqueId());
        // Read the route now: the router drops it a few ticks after join.
        boolean byFerry = cameByFerry(player);
        if (arrival.wantsRide(player, byFerry)) {
            // Open in the Veil at once, before the island can flash up; the ride waits for the character.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    arrival.begin(player);
                }
            }, 1L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> arrive(player), 8L);
        settleLater(player);
    }

    /**
     * The character can land after {@link #arrive} ran (late file, outbox recovery) and bring the game mode the
     * player had when the ferry left: spectator, because the departure shot films them. Once the transport is done
     * and no crossing is playing, nobody stays a spectator over the void: adventure on the shore.
     */
    private void settleLater(Player player) {
        UUID id = player.getUniqueId();
        long deadline = System.currentTimeMillis() + 20_000L;
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                NetworkRouter router = plugin.router();
                de.aetherion.core.api.CharacterTransport transport = router == null ? null : router.transport();
                boolean landing = (transport != null && transport.isArriving(id)) || arrival.isPlaying(player);
                if (landing && System.currentTimeMillis() < deadline) {
                    return;
                }
                cancel();
                if (arrival.isPlaying(player) || player.getGameMode() != GameMode.SPECTATOR
                        || player.getSpectatorTarget() != null) {
                    return;
                }
                Location spawn = spawnLocation();
                World world = exileWorld();
                if (spawn != null && (world == null || !player.getWorld().equals(world)
                        || player.getLocation().getY() < world.getMinHeight() + 8)) {
                    player.teleport(spawn);
                }
                player.setGameMode(GameMode.ADVENTURE);
                plugin.getLogger().info("[Exile] " + player.getName() + " arrived as a spectator after the crossing"
                        + " — set to adventure on the shore.");
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    private boolean cameByFerry(Player player) {
        try {
            NetworkRouter router = plugin.router();
            if (router == null) {
                return false;
            }
            RouteBook.Route route = router.routes().pendingFor(player.getUniqueId(), router.here());
            return route != null && !ServerNames.isExile(route.from());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void arrive(Player player) {
        if (player == null || !player.isOnline() || !link.isExileServer()) {
            if (player != null) {
                arriving.remove(player.getUniqueId());
            }
            return;
        }
        TransferSnapshotStore.ApplyResult result = snapshots == null
                ? TransferSnapshotStore.ApplyResult.none()
                : snapshots.applyDetailed(player);
        if (result.applied()) {
            plugin.getLogger().info("Exile arrival applied snapshot for " + player.getName()
                    + " warp=" + result.pendingWarp());
        }
        Location spawn = spawnLocation();
        if (spawn == null) {
            arriving.remove(player.getUniqueId());
            player.sendMessage("§cExile world missing — ask staff.");
            return;
        }
        if (arrival.isPlaying(player)) {
            // The crossing owns the player; it lands them itself.
            return;
        }
        player.teleport(spawn);
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        arriving.remove(player.getUniqueId());
    }

    /**
     * Called by {@link ExileArrival} when the crossing ends or is skipped. {@code landed} is where the ride put
     * the player (on the docked barge); null = the configured quay spawn.
     */
    void finishArrival(Player player, boolean fromCutscene, Location landed) {
        if (player == null) {
            return;
        }
        arriving.remove(player.getUniqueId());
        Location target = landed != null ? landed : spawnLocation();
        if (target != null && player.isOnline()) {
            if (!fromCutscene || player.getWorld() != target.getWorld()
                    || player.getLocation().distanceSquared(target) > 4.0) {
                player.teleport(target);
            }
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.SURVIVAL) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!link.isExileServer()) {
            return;
        }
        Location spawn = spawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
