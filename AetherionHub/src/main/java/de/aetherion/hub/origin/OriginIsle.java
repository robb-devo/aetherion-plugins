package de.aetherion.hub.origin;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Origin Isle: the showcase layer on the main island (OriginBuilds map, 1:1 in {@code world}).
 * One router owns every loop — districts and discovery, waystones, vistas, the seven bells, updrafts,
 * glides and skyways, ambience, island-wide moments, the Origin cast, softlight and the DEV hub.
 *
 * <p>Nothing here replaces an existing Hub system: spawns, pads, the prop wand, pastes and discover-unlocks
 * run exactly as before. Origin only listens (and adds two organic camps: Skyreach Summit and Whisperwood).
 */
public final class OriginIsle implements Listener {

    public enum Phase { DAWN, DAY, DUSK, NIGHT }

    private final AetherionHub plugin;
    private final HubService hub;
    private final OriginConfig config;
    private final OriginProfiles profiles;
    private final OriginCompass compass;
    private final OriginTraversal traversal;
    private final OriginFlight flight;
    private final OriginPads pads;
    private final OriginWaystones waystones;
    private final OriginVistas vistas;
    private final OriginBells bells;
    private final OriginAmbience ambience;
    private final OriginEvents events;
    private final OriginCast cast;
    private final OriginMenus menus;
    private final OriginSoftLight softlight;
    private final OriginDev dev;
    private final OriginFountain fountain;
    private BukkitTask task;
    private long tick;
    private boolean running;

    public OriginIsle(AetherionHub plugin, HubService hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.config = new OriginConfig(plugin);
        this.profiles = new OriginProfiles(plugin);
        this.compass = new OriginCompass(this);
        this.traversal = new OriginTraversal(this);
        this.flight = new OriginFlight(this);
        this.pads = new OriginPads(this);
        this.waystones = new OriginWaystones(this);
        this.vistas = new OriginVistas(this);
        this.bells = new OriginBells(this);
        this.ambience = new OriginAmbience(this);
        this.events = new OriginEvents(this);
        this.cast = new OriginCast(this);
        this.menus = new OriginMenus(this);
        this.softlight = new OriginSoftLight(this);
        this.dev = new OriginDev(this);
        this.fountain = new OriginFountain(this);
    }

    /** Called from {@code AetherionHub#onEnable}. Never throws: a broken Origin must not take the Hub down. */
    public void start() {
        try {
            String summary = config.load();
            OriginCommand command = new OriginCommand(this);
            PluginCommand origin = plugin.getCommand("origin");
            if (origin != null) {
                origin.setExecutor(command);
                origin.setTabCompleter(command);
            }
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(this, plugin);
            pm.registerEvents(traversal, plugin);
            pm.registerEvents(flight, plugin);
            pm.registerEvents(waystones, plugin);
            pm.registerEvents(vistas, plugin);
            pm.registerEvents(bells, plugin);
            pm.registerEvents(events, plugin);
            pm.registerEvents(cast, plugin);
            pm.registerEvents(menus, plugin);
            pm.registerEvents(fountain, plugin);
            OriginHooks.bind(this);
            if (!config.enabled()) {
                plugin.getLogger().info("Origin Isle: disabled in origin.yml (DEV and /origin dev still work).");
                return;
            }
            running = true;
            waystones.start();
            cast.start();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 20L, 1L);
            Bukkit.getScheduler().runTaskLater(plugin, this::seedSpawns, 40L);
            plugin.getLogger().info("Origin Isle: " + summary);
        } catch (Throwable throwable) {
            running = false;
            plugin.getLogger().severe("Origin Isle failed to start (Hub keeps running): " + throwable);
            throwable.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        try {
            flight.clear();
            traversal.clear();
            events.stopAll();
            vistas.clear();
            waystones.shutdown();
            cast.shutdown();
            softlight.cancel();
            profiles.saveAll();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Origin Isle stop: " + throwable);
        }
        OriginHooks.unbind(this);
    }

    /** DEV reload: re-reads origin.yml and rebuilds the placed pieces. */
    public String reload() {
        String summary = config.load();
        waystones.shutdown();
        cast.shutdown();
        if (config.enabled()) {
            if (!running) {
                running = true;
                task = Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 20L, 1L);
            }
            waystones.start();
            cast.start();
        } else {
            running = false;
            if (task != null) {
                task.cancel();
                task = null;
            }
            // Switched off live: let go of riders and clear everything the loop would have tidied.
            flight.clear();
            traversal.clear();
            events.stopAll();
            vistas.clear();
            softlight.cancel();
        }
        return summary;
    }

    private void loop() {
        tick++;
        try {
            flight.tick();
            traversal.rideTick();
            if (tick % 2 == 0) {
                events.tick(tick);
            }
            if (tick % 4 != 0) {
                return;
            }
            List<Player> onIsle = onIsle();
            traversal.triggerTick(onIsle);
            flight.triggerTick(onIsle);
            if (tick % 10 == 0) {
                compass.tick(onIsle);
                waystones.tick(onIsle);
                vistas.tick(onIsle);
                traversal.safetyTick(onIsle);
                pads.tick(onIsle, tick);
            }
            if (tick % 20 == 0) {
                ambience.emitters(onIsle, tick);
                cast.tick(onIsle);
            } else if (tick % 4 == 0) {
                ambience.fastEmitters(onIsle, tick);
            }
            if (tick % 60 == 0) {
                ambience.soundscape(onIsle);
            }
            if (tick % 1200 == 0) {
                profiles.flush();
            }
        } catch (Throwable throwable) {
            if (tick % 200 == 0) {
                plugin.getLogger().warning("Origin loop: " + throwable);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    public List<Player> onIsle() {
        List<Player> out = new ArrayList<>();
        World world = config.world();
        if (world == null) {
            return out;
        }
        for (Player player : world.getPlayers()) {
            if (player.isOnline() && config.inFootprint(player.getLocation())) {
                out.add(player);
            }
        }
        return out;
    }

    public boolean onIsle(Player player) {
        return player != null && config.inFootprint(player.getLocation());
    }

    public Phase phase() {
        World world = config.world();
        long time = world == null ? 6000L : world.getTime() % 24000L;
        if (time >= 22800L || time < 450L) {
            return Phase.DAWN;
        }
        if (time < 11800L) {
            return Phase.DAY;
        }
        if (time < 13200L) {
            return Phase.DUSK;
        }
        return Phase.NIGHT;
    }

    public boolean night() {
        return phase() == Phase.NIGHT;
    }

    /**
     * Tutorial players (camp {@code quiet-until-spawn} not unlocked yet) get quiet discovery: no titles,
     * soft sounds, no tour offer — the Harbour Hour keeps the stage.
     */
    public boolean quiet(Player player) {
        String gate = config.quietUntil();
        if (gate.isBlank() || hub.spawn(gate) == null) {
            return false;
        }
        return !hub.isUnlocked(player, gate);
    }

    /** Coins through the Core wallet (Items). Silently skipped when Items is offline. */
    public long pay(Player player, String rewardKey, long fallback) {
        long amount = config.reward(rewardKey, fallback);
        if (amount <= 0L || player == null) {
            return 0L;
        }
        de.aetherion.core.api.CoinAccess coins = de.aetherion.core.api.AetherServices.coins();
        if (coins == null) {
            return 0L;
        }
        try {
            coins.add(player, amount);
        } catch (Throwable ignored) {
            return 0L;
        }
        OriginProfile profile = profiles.get(player);
        profile.coinsEarned += amount;
        profile.dirty = true;
        return amount;
    }

    public boolean takeCoins(Player player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        de.aetherion.core.api.CoinAccess coins = de.aetherion.core.api.AetherServices.coins();
        try {
            return coins != null && coins.take(player, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Location loc(double[] p) {
        return config.location(p);
    }

    int seedSpawns() {
        int seeded = 0;
        for (OriginConfig.SpawnSeed seed : config.spawnSeeds().values()) {
            HubSpawn spawn = hub.spawn(seed.id());
            if (spawn == null || spawn.hasLocation()) {
                continue;
            }
            Location at = config.location(seed.at());
            if (at != null) {
                hub.setLocation(seed.id(), at, false);
                plugin.getLogger().info("Origin: seeded camp '" + seed.id() + "' at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
                seeded++;
            }
        }
        return seeded;
    }

    // ------------------------------------------------------------------ player lifecycle

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.get(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        compass.forget(id);
        vistas.forget(id);
        waystones.forget(id);
        pads.forget(id);
        ambience.forget(id);
        cast.forget(id);
        events.forget(id);
        bells.forget(id);
        fountain.forget(id);
        OriginText.forget(id);
        profiles.unload(id);
    }

    /** Full wipe (DEV / Items Full Player Wipe through HubService#wipePlayer). */
    public void wipe(UUID id) {
        compass.forget(id);
        vistas.forget(id);
        waystones.forget(id);
        pads.forget(id);
        profiles.wipe(id);
    }

    // ------------------------------------------------------------------ accessors

    public AetherionHub plugin() {
        return plugin;
    }

    public HubService hub() {
        return hub;
    }

    public OriginConfig config() {
        return config;
    }

    public OriginProfiles profiles() {
        return profiles;
    }

    public OriginCompass compass() {
        return compass;
    }

    public OriginTraversal traversal() {
        return traversal;
    }

    public OriginFlight flight() {
        return flight;
    }

    public OriginPads pads() {
        return pads;
    }

    public OriginWaystones waystones() {
        return waystones;
    }

    public OriginVistas vistas() {
        return vistas;
    }

    public OriginBells bells() {
        return bells;
    }

    public OriginAmbience ambience() {
        return ambience;
    }

    public OriginEvents events() {
        return events;
    }

    public OriginCast cast() {
        return cast;
    }

    public OriginMenus menus() {
        return menus;
    }

    public OriginSoftLight softlight() {
        return softlight;
    }

    public OriginDev dev() {
        return dev;
    }

    public boolean running() {
        return running;
    }
}
