package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.helios.encounter.Participants;
import de.aetherion.bossengine.helios.world.ArenaSlots;
import de.aetherion.bossengine.helios.world.HeliosWorld;
import de.aetherion.bossengine.instance.BossScripts;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
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
 * Bootstrap and registry for The Hush. Lives inside BossEngine (it uses the engine's HP pool, damage
 * tracker, loot tables and combat listener, and the slot journal, void generator and bars that Helios
 * already has) but owns its world, its instances and its own tick.
 *
 * <p>The world is only created the first time somebody enters, so a backend where nobody fights the
 * Hush never carries it.
 */
public final class HushModule {

    /** Scoreboard tag on every entity the encounter spawns (displays, echo hitboxes). */
    static final String TAG = "aetherion_hush";

    private final BossEngine plugin;
    private HushConfig config = HushConfig.defaults();
    private World world;
    private ArenaSlots slots;
    private final List<HushEncounter> encounters = new ArrayList<>();
    private final Set<UUID> internalTeleport = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final List<Location> entryBells = new ArrayList<>();
    private BukkitTask task;

    public HushModule(BossEngine plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        File file = new File(plugin.getDataFolder(), "hush.yml");
        if (!file.exists()) {
            plugin.saveResource("hush.yml", false);
        }
        config = HushConfig.load(file);
        HushParty.keys(plugin);
        BossScripts.register(HushEncounter.BOSS_ID, instance -> new HushScript(this, instance));
        slots = new ArenaSlots(new File(plugin.getDataFolder(), "hush/state.yml"), plugin.getLogger());
        readEntryBells();

        plugin.getServer().getPluginManager().registerEvents(new HushGuard(this), plugin);
        HushCommand command = new HushCommand(this);
        PluginCommand cmd = plugin.getCommand("hush");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        if (!config.enabled()) {
            plugin.getLogger().info("[Hush] Disabled in hush.yml.");
            return;
        }
        world = Bukkit.getWorld(config.worldName());
        recover();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("[Hush] Ready: " + config.maxInstances() + " belfry slot(s), world '"
                + config.worldName() + "' " + (world == null ? "is created on first entry." : "loaded."));
    }

    /** A crash left slots dirty: turn them back into air and free them. */
    private void recover() {
        List<ArenaSlots.Slot> dirty = slots.load();
        if (!dirty.isEmpty() && ensureWorld() != null) {
            for (ArenaSlots.Slot s : dirty) {
                int cx = s.index() * config.slotSpacing();
                plugin.getLogger().warning("[Hush] Slot " + s.index() + " was " + s.state() + " at shutdown: clearing it.");
                HushArena.clear(plugin, world, cx, config.arenaY(), 0);
                sweep(world, cx);
                slots.set(s.index(), ArenaSlots.State.FREE);
            }
        }
        // Anyone already online who is still marked (a /reload) goes home now.
        for (Player p : Bukkit.getOnlinePlayers()) {
            HushGuard.recoverPlayer(this, p);
        }
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (HushEncounter e : new ArrayList<>(encounters)) {
            e.close(false, "disable");
        }
        encounters.clear();
        BossScripts.unregister(HushEncounter.BOSS_ID);
    }

    public void reload() {
        config = HushConfig.load(new File(plugin.getDataFolder(), "hush.yml"));
        readEntryBells();
    }

    private void readEntryBells() {
        entryBells.clear();
        for (String raw : config.entryBells()) {
            Location l = Participants.parse(raw);
            if (l != null) {
                entryBells.add(l);
            } else {
                plugin.getLogger().warning("[Hush] entry.bells: cannot read '" + raw + "' (world,x,y,z; is the world loaded here?).");
            }
        }
    }

    private void tick() {
        for (HushEncounter e : new ArrayList<>(encounters)) {
            try {
                e.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Hush] Slot " + e.slot() + " crashed its tick; closing it.", ex);
                e.close(false, "error");
            }
        }
    }

    /* ================================================================== world */

    /** The encounter's own void world, created on first use. Nothing in any other world is ever edited. */
    World ensureWorld() {
        if (world != null) {
            return world;
        }
        String name = config.worldName();
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new HeliosWorld.Void())
                    .biomeProvider(new HeliosWorld.VoidBiomes())
                    .generateStructures(false)
                    .createWorld();
            if (w == null) {
                plugin.getLogger().severe("[Hush] Could not create world '" + name + "'.");
                return null;
            }
            plugin.getLogger().info("[Hush] Created encounter world '" + name + "'.");
        }
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.KEEP_INVENTORY, config.keepInventory());
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        w.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        w.setGameRule(GameRule.DO_TILE_DROPS, false);
        w.setDifficulty(Difficulty.HARD);
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(18_000L);
        w.setSpawnFlags(false, false);
        w.setSpawnLocation(new Location(w, 0.5, config.arenaY() + 1, 0.5));
        world = w;
        return w;
    }

    /** Removes every entity the encounter left in a slot (displays, echo hitboxes). */
    void sweep(World w, int cx) {
        if (w == null) {
            return;
        }
        Location centre = new Location(w, cx + 0.5, config.arenaY(), 0.5);
        for (Entity e : w.getNearbyEntities(centre, HushArena.BOX, 96, HushArena.BOX)) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
    }

    /* ================================================================== entry */

    /** Opens a belfry for {@code leader} and their party. @return a result key for the caller */
    public String enter(Player leader) {
        return enter(leader, false);
    }

    /** @param staff an admin sends this player in: the entry permission and the cooldown do not apply */
    String enter(Player leader, boolean staff) {
        if (!config.enabled() || task == null) {
            return "disabled";
        }
        String perm = config.entryPermission();
        if (!staff && perm != null && !perm.isBlank() && !leader.hasPermission(perm)) {
            return "permission";
        }
        if (encounterOf(leader) != null) {
            return "already";
        }
        Long until = cooldowns.get(leader.getUniqueId());
        if (!staff && until != null && until > System.currentTimeMillis()) {
            return "cooldown:" + ((until - System.currentTimeMillis()) / 1000L + 1);
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
            return "min-players:" + config.minPlayers();
        }
        while (group.size() > config.maxPlayers()) {
            group.remove(group.size() - 1);
        }
        if (slots.busy() >= config.maxInstances() || ensureWorld() == null) {
            return "full";
        }
        var opened = HushEncounter.open(this, group);
        if (opened.isEmpty()) {
            return "full";
        }
        encounters.add(opened.get());
        return "ok";
    }

    /** Is this block one of the bells in the normal worlds that lead into the belfry? */
    boolean isEntryBell(Block block) {
        for (Location l : entryBells) {
            if (l.getWorld() == block.getWorld() && l.getBlockX() == block.getX()
                    && l.getBlockY() == block.getY() && l.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    void markCooldown(Player p) {
        int s = config.cooldownSeconds();
        if (s > 0) {
            cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + s * 1000L);
        }
    }

    /* ================================================================== lookup */

    HushEncounter encounterAt(Location l) {
        for (HushEncounter e : encounters) {
            if (e.contains(l)) {
                return e;
            }
        }
        return null;
    }

    HushEncounter encounterOf(Player p) {
        for (HushEncounter e : encounters) {
            if (e.party().contains(p)) {
                return e;
            }
        }
        return null;
    }

    List<HushEncounter> encounters() {
        return encounters;
    }

    void forget(HushEncounter e) {
        encounters.remove(e);
    }

    /* ================================================================== teleports */

    /** Teleports that the encounter itself makes; the guard lets them through untouched. */
    void teleport(Player p, Location to) {
        internalTeleport.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            internalTeleport.remove(p.getUniqueId());
        }
    }

    boolean internalTeleport(Player p) {
        return internalTeleport.contains(p.getUniqueId());
    }

    Location fallbackReturn() {
        Location l = Participants.parse(config.fallbackReturn());
        if (l != null) {
            return l;
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation();
    }

    /* ================================================================== accessors */

    BossEngine plugin() {
        return plugin;
    }

    HushConfig config() {
        return config;
    }

    /** The Hush world, or null while nobody has entered since the server started. */
    World world() {
        return world;
    }

    ArenaSlots slots() {
        return slots;
    }
}
