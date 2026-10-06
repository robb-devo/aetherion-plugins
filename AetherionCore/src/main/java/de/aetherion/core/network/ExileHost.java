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
    private final Set<UUID> arriving = ConcurrentHashMap.newKeySet();

    private final String worldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;
    private final Set<String> keepWorlds;
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
            tuneWorld(world);
            arrival.purgeRigs(world);
            world.setSpawnLocation(spawnLocation());
        }
        retireForeignWorlds();
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> arrive(player), 8L);
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
        player.teleport(spawn);
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        // First visit → ferry cutscene; returnees land quietly.
        arrival.playIfFirst(player);
    }

    /** Called by {@link ExileArrival} when the cutscene ends or is skipped. */
    void finishArrival(Player player, boolean fromCutscene) {
        if (player == null) {
            return;
        }
        arriving.remove(player.getUniqueId());
        Location spawn = spawnLocation();
        if (spawn != null && player.isOnline()) {
            if (!fromCutscene || player.getWorld() != spawn.getWorld()
                    || player.getLocation().distanceSquared(spawn) > 25.0) {
                player.teleport(spawn);
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
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
