package de.aetherion.bossengine.helios;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.encounter.Participants;
import de.aetherion.bossengine.helios.herald.HeraldScript;
import de.aetherion.bossengine.helios.requiem.HeliosScript;
import de.aetherion.bossengine.helios.world.ArenaBuilder;
import de.aetherion.bossengine.helios.world.ArenaSlots;
import de.aetherion.bossengine.helios.world.HeliosWorld;
import de.aetherion.bossengine.instance.BossScripts;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bootstrap and registry for Helios Requiem. Lives inside BossEngine (it reuses the engine's HP pool,
 * damage tracker, loot tables and combat listener) but owns its world, its instances, its crash
 * journal and its own tick.
 */
public final class HeliosModule {

    private final BossEngine plugin;
    private HeliosConfig config = HeliosConfig.defaults();
    private World world;
    private ArenaSlots slots;
    private ArenaBuilder builder;
    private final List<HeliosEncounter> encounters = new ArrayList<>();
    private final Set<UUID> internalTeleport = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask task;

    public HeliosModule(BossEngine plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!new File(plugin.getDataFolder(), "helios.yml").exists()) {
            plugin.saveResource("helios.yml", false);
        }
        config = HeliosConfig.load(new File(plugin.getDataFolder(), "helios.yml"));
        Participants.keys(plugin);
        de.aetherion.bossengine.helios.reward.PendingRewards.init(plugin.getDataFolder(), plugin.getLogger());
        BossScripts.register(HeliosEncounter.HERALD_ID, instance -> new HeraldScript(this, instance));
        BossScripts.register(HeliosEncounter.HELIOS_ID, instance -> new HeliosScript(this, instance));
        builder = new ArenaBuilder(plugin, config.buildPerTick(), config.clearPerTick());
        slots = new ArenaSlots(new File(plugin.getDataFolder(), "helios/state.yml"), plugin.getLogger());

        plugin.getServer().getPluginManager().registerEvents(new HeliosGuard(this), plugin);
        HeliosCommand command = new HeliosCommand(this);
        PluginCommand cmd = plugin.getCommand("helios");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        if (!config.enabled()) {
            plugin.getLogger().info("[Helios] Disabled in helios.yml.");
            return;
        }
        world = HeliosWorld.ensure(config, plugin.getLogger());
        if (world == null) {
            return;
        }
        recover();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("[Helios] Ready: world '" + world.getName() + "', " + config.maxInstances() + " instance slot(s).");
    }

    /** A crash left slots dirty: clear them completely and free them. */
    private void recover() {
        List<ArenaSlots.Slot> dirty = slots.load();
        for (ArenaSlots.Slot s : dirty) {
            int idx = s.index();
            int cx = idx * config.slotSpacing();
            plugin.getLogger().warning("[Helios] Slot " + idx + " was " + s.state() + " at shutdown: clearing it.");
            slots.set(idx, ArenaSlots.State.RESTORING);
            builder.clear(world, cx, config.arenaY(), 0, () -> {
                ArenaBuilder.release(plugin, world, cx, 0);
                slots.set(idx, ArenaSlots.State.FREE);
            });
        }
        // Anyone already online who is still marked (a /reload) goes home now.
        for (Player p : Bukkit.getOnlinePlayers()) {
            HeliosGuard.recoverPlayer(this, p);
        }
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (HeliosEncounter e : new ArrayList<>(encounters)) {
            e.close(false, "disable");
        }
        encounters.clear();
        if (builder != null) {
            builder.flush();
        }
        BossScripts.unregister(HeliosEncounter.HERALD_ID);
        BossScripts.unregister(HeliosEncounter.HELIOS_ID);
    }

    public void reload() {
        config = HeliosConfig.load(new File(plugin.getDataFolder(), "helios.yml"));
    }

    private void tick() {
        builder.tick();
        for (HeliosEncounter e : new ArrayList<>(encounters)) {
            try {
                e.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Helios] Slot " + e.slot() + " crashed its tick; closing it.", ex);
                e.close(false, "error");
            }
        }
    }

    /* ================================================================== entry */

    /** Opens an instance for {@code leader} and their party. @return a message key for the caller */
    public String enter(Player leader) {
        if (world == null || !config.enabled()) {
            return "disabled";
        }
        String perm = config.entryPermission();
        if (perm != null && !perm.isBlank() && !leader.hasPermission(perm)) {
            return "permission";
        }
        if (encounterOf(leader) != null) {
            return "already";
        }
        Long until = cooldowns.get(leader.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            return "cooldown:" + ((until - System.currentTimeMillis()) / 1000L);
        }
        List<Player> group = new ArrayList<>();
        group.add(leader);
        var party = de.aetherion.core.api.AetherServices.party();
        if (party != null && party.inParty(leader)) {
            if (!party.isLeader(leader)) {
                return "not-leader";
            }
            double r2 = config.entryRadius() * config.entryRadius();
            for (Player m : party.onlineMembers(leader)) {
                if (m.equals(leader) || m.getWorld() != leader.getWorld()) {
                    continue;
                }
                if (m.getLocation().distanceSquared(leader.getLocation()) <= r2 && encounterOf(m) == null) {
                    group.add(m);
                }
            }
        }
        if (group.size() < config.minPlayers()) {
            return "min-players";
        }
        while (group.size() > config.maxPlayers()) {
            group.remove(group.size() - 1);
        }
        if (slots.busy() >= config.maxInstances()) {
            return "full";
        }
        var opened = HeliosEncounter.open(this, group);
        if (opened.isEmpty()) {
            return "full";
        }
        encounters.add(opened.get());
        return "ok";
    }

    public void markCooldown(Player p) {
        int s = config.cooldownSeconds();
        if (s > 0) {
            cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + s * 1000L);
        }
    }

    /* ================================================================== lookup */

    public HeliosEncounter encounterAt(Location l) {
        for (HeliosEncounter e : encounters) {
            if (e.contains(l)) {
                return e;
            }
        }
        return null;
    }

    public HeliosEncounter encounterOf(Player p) {
        for (HeliosEncounter e : encounters) {
            if (e.party().contains(p)) {
                return e;
            }
        }
        return null;
    }

    public List<HeliosEncounter> encounters() {
        return encounters;
    }

    public void forget(HeliosEncounter e) {
        encounters.remove(e);
    }

    /* ================================================================== teleports */

    /** Teleports that Helios itself makes; the guard lets them through untouched. */
    public void teleport(Player p, Location to) {
        internalTeleport.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            internalTeleport.remove(p.getUniqueId());
        }
    }

    public boolean internalTeleport(Player p) {
        return internalTeleport.contains(p.getUniqueId());
    }

    public Location fallbackReturn() {
        Location l = Participants.parse(config.fallbackReturn());
        if (l != null) {
            return l;
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation();
    }

    /* ================================================================== accessors */

    public BossEngine plugin() {
        return plugin;
    }

    public HeliosConfig config() {
        return config;
    }

    public World world() {
        return world;
    }

    public ArenaSlots slots() {
        return slots;
    }

    public ArenaBuilder builder() {
        return builder;
    }

    public BossKeys keys() {
        return plugin.getKeys();
    }
}
