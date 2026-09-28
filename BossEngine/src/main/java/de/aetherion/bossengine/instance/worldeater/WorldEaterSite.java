package de.aetherion.bossengine.instance.worldeater;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossState;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * THE LAST SEED: the World Eater's dedicated void world and the encounter that runs through it.
 *
 * <pre>
 *   DORMANT   the Unbroken kneels as a statue before the sealed gate. Stars overhead.
 *   GUARDED   someone stepped onto the Threshold: the statue woke.
 *   OPEN      the Unbroken fell and tore the gate open. Daylight through the gate.
 *   FEAST     someone set foot on the Last Seed: the sky tore and Nihil came through.
 *   AFTERMATH the world was saved; the Bonus Chest is out.
 * </pre>
 *
 * The state lives in the world's persistent data, the layout lives in code ({@link SiteLayout}),
 * so a crash in any state recovers the same way: rebuild to the layout and go DORMANT.
 * Nothing here runs unless the world is loaded and has been built with {@code /boss worldeater build}.
 */
public final class WorldEaterSite {

    public static final String UNBROKEN_ID = "world_eater_unbroken";
    public static final String EATER_ID = "world_eater";
    public static final String DEFAULT_WORLD = "world_eater";
    public static final int LAYOUT_VERSION = 1;

    public enum State { DORMANT, GUARDED, OPEN, FEAST, AFTERMATH }

    private static WorldEaterSite site;

    private final BossEngine plugin;
    private final String worldName;
    private final NamespacedKey stateKey;
    private final NamespacedKey layoutKey;
    private BukkitTask task;
    private State state = State.DORMANT;
    private BossInstance guardian;
    private BossInstance eater;
    private SiteTerrain terrain;
    private SiteTerrain.Job job;
    private Runnable afterJob;
    private Senses senses;
    private Glimpse glimpse;
    private boolean glimpsed;
    private boolean recovered;
    private long clock;
    private int lonely;
    private int aftermathTicks = -1;
    private int spawnRetry;
    private final Map<UUID, Long> rescued = new HashMap<>();
    private final Set<UUID> told = new HashSet<>();

    private WorldEaterSite(BossEngine plugin) {
        this.plugin = plugin;
        this.worldName = plugin.getConfig().getString("world-eater.world", DEFAULT_WORLD);
        this.stateKey = new NamespacedKey(plugin, "world_eater_state");
        this.layoutKey = new NamespacedKey(plugin, "world_eater_layout");
    }

    /* ================================================================== lifecycle */

    public static WorldEaterSite start(BossEngine plugin) {
        if (site != null) {
            site.stopTask();
        }
        site = new WorldEaterSite(plugin);
        site.task = Bukkit.getScheduler().runTaskTimer(plugin, site::tick, 20L, 1L);
        if (plugin.getConfig().getBoolean("world-eater.auto-load", true)
                && new java.io.File(Bukkit.getWorldContainer(), site.worldName).isDirectory()) {
            Bukkit.getScheduler().runTask(plugin, () -> site.createWorld());
        }
        return site;
    }

    /** @return the running site, or null before BossEngine enabled it */
    public static WorldEaterSite get() {
        return site;
    }

    /** Plugin disable: stop ticking, let every player's sky go, drop the glimpse. */
    public static void stop() {
        if (site == null) {
            return;
        }
        site.stopTask();
        site = null;
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (glimpse != null) {
            glimpse.clear();
            glimpse = null;
        }
        if (senses != null) {
            senses.close();
            senses = null;
        }
        job = null;
        afterJob = null;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public boolean isSiteWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName);
    }

    public boolean built() {
        World w = world();
        if (w == null) {
            return false;
        }
        Integer v = w.getPersistentDataContainer().get(layoutKey, PersistentDataType.INTEGER);
        return v != null && v >= LAYOUT_VERSION;
    }

    public State state() {
        return state;
    }

    public SiteTerrain terrain() {
        World w = world();
        if (w == null) {
            return null;
        }
        if (terrain == null || terrain.world() != w) {
            terrain = new SiteTerrain(w);
        }
        return terrain;
    }

    /** The site's hold on every player's sky. Directors at the site borrow it. */
    public Senses senses() {
        World w = world();
        if (w == null) {
            return null;
        }
        if (senses == null || senses.closed()) {
            senses = new Senses(w, () -> w.getPlayers(), this::borderAudience);
        }
        return senses;
    }

    private List<Player> borderAudience() {
        World w = world();
        List<Player> out = new ArrayList<>();
        if (w == null) {
            return out;
        }
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - SiteLayout.CENTER_X;
            double dz = l.getZ() - SiteLayout.CENTER_Z;
            if (dx * dx + dz * dz < 72 * 72 && l.getY() > 40) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Is a boss spawned at {@code spawn} standing in this site (and so allowed to edit it)?
     *
     * @param island true for the World Eater (island center), false for the Unbroken (plaza)
     */
    public boolean atSite(Location spawn, boolean island) {
        if (spawn == null || !isSiteWorld(spawn.getWorld()) || !built()) {
            return false;
        }
        double ax = island ? SiteLayout.CENTER_X : SiteLayout.PLAZA_X;
        double az = island ? SiteLayout.CENTER_Z : SiteLayout.PLAZA_Z;
        double dx = spawn.getX() - ax;
        double dz = spawn.getZ() - az;
        return dx * dx + dz * dz <= (island ? 20 * 20 : 12 * 12) && Math.abs(spawn.getY() - (SiteLayout.FLOOR + 1)) < 8;
    }

    /* ================================================================== dev / admin */

    /** Creates (or loads) the void world. */
    public World createWorld() {
        World existing = world();
        if (existing != null) {
            applyRules(existing);
            return existing;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new WorldEaterVoid());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        World w = creator.createWorld();
        if (w == null) {
            plugin.getLogger().warning("World Eater: could not create world " + worldName);
            return null;
        }
        applyRules(w);
        plugin.getLogger().info("World Eater: world '" + worldName + "' ready.");
        return w;
    }

    private void applyRules(World w) {
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setTime(18000L);
        w.setStorm(false);
        w.setThundering(false);
        if (w.getDifficulty() == Difficulty.PEACEFUL) {
            // Hostile hitboxes (the World Eater's head) would be removed in peaceful.
            w.setDifficulty(Difficulty.NORMAL);
        }
        w.setSpawnLocation(SiteLayout.arrival(w));
    }

    /** Builds or repairs the whole site to the layout, spread over ticks. */
    public boolean build(CommandSender feedback) {
        World w = world();
        if (w == null) {
            return false;
        }
        despawnSiteBosses();
        clearGlimpse();
        runJob(terrain().restoreAll(changed -> {
            writeSigns();
            w.getPersistentDataContainer().set(layoutKey, PersistentDataType.INTEGER, LAYOUT_VERSION);
            setState(State.DORMANT);
            glimpsed = false;
            if (feedback != null) {
                feedback.sendMessage(TextUtil.component("&5World Eater &8» &aThe Last Seed is built. &7(" + changed + " blocks set)"));
            }
            plugin.getLogger().info("World Eater: site built (" + changed + " blocks set).");
        }), null);
        return true;
    }

    /** Kills the site's bosses, restores everything, and goes dormant. */
    public boolean reset(CommandSender feedback) {
        if (world() == null) {
            return false;
        }
        return build(feedback);
    }

    /** Dev shortcut: the gate is open without the Unbroken. */
    public boolean openGate() {
        World w = world();
        if (w == null || !built()) {
            return false;
        }
        terrain().clearCells(SiteLayout.get().doorCells());
        setState(State.OPEN);
        return true;
    }

    public Location arrival() {
        World w = world();
        return w == null ? null : SiteLayout.arrival(w);
    }

    public String status() {
        World w = world();
        if (w == null) {
            return "&7World &f" + worldName + " &7is not loaded. &8(/boss worldeater create)";
        }
        return "&7World &f" + worldName + " &8· &7built " + (built() ? "&ayes" : "&cno")
                + " &8· &7state &f" + state.name()
                + " &8· &7guardian " + alive(guardian)
                + " &8· &7eater " + alive(eater)
                + (job != null ? " &8· &erebuilding" : "");
    }

    private static String alive(BossInstance i) {
        return i != null && i.getState() == BossState.ALIVE ? "&aalive" : "&8-";
    }

    /* ================================================================== director hooks */

    /** The Unbroken woke at the site. */
    public void adoptGuardian(BossInstance instance) {
        guardian = instance;
        setState(State.GUARDED);
    }

    /** The Unbroken's death cinematic finished: the gate is open. */
    public void guardianFell() {
        guardian = null;
        setState(State.OPEN);
        lonely = 0;
    }

    /** Nihil arrived at the site. */
    public void adoptEater(BossInstance instance) {
        eater = instance;
        setState(State.FEAST);
    }

    /** Nihil's death cinematic finished: the world is saved. */
    public void worldSaved() {
        eater = null;
        setState(State.AFTERMATH);
        aftermathTicks = 20 * 60 * 5;
    }

    /** The Bonus Chest closed: the site resets shortly after. */
    public static void chestFinished() {
        if (site != null && site.state == State.AFTERMATH) {
            site.aftermathTicks = Math.min(site.aftermathTicks < 0 ? Integer.MAX_VALUE : site.aftermathTicks, 20 * 20);
        }
    }

    /** Puts the island back (a fight ended early, or a phase needs its ground). */
    public void restoreIsland(Runnable after) {
        if (world() == null) {
            return;
        }
        runJob(terrain().restoreBoxes(n -> {
            if (after != null) {
                after.run();
            }
        }, SiteLayout.ISLAND, SiteLayout.BRIDGE, SiteLayout.RIFT), null);
    }

    private void setState(State next) {
        state = next;
        World w = world();
        if (w != null) {
            w.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, next.name());
        }
        Senses s = senses();
        if (s == null) {
            return;
        }
        switch (next) {
            case DORMANT, GUARDED -> {
                s.skyNow(18000f);
                s.weather(WeatherType.CLEAR);
            }
            case OPEN, AFTERMATH -> {
                s.weather(WeatherType.CLEAR);
            }
            default -> {
            }
        }
    }

    private void runJob(SiteTerrain.Job next, Runnable after) {
        job = next;
        afterJob = after;
    }

    private void despawnSiteBosses() {
        for (BossInstance i : new ArrayList<>(plugin.getBossManager().getOccupying())) {
            String id = i.getTemplate() == null ? "" : i.getTemplate().getId();
            if ((UNBROKEN_ID.equalsIgnoreCase(id) || EATER_ID.equalsIgnoreCase(id))
                    && i.getSpawnLocation() != null && isSiteWorld(i.getSpawnLocation().getWorld())) {
                plugin.getBossManager().despawn(i, BossDespawnEvent.Reason.COMMAND, null);
            }
        }
        guardian = null;
        eater = null;
    }

    private void writeSigns() {
        World w = world();
        if (w == null) {
            return;
        }
        for (Map.Entry<Long, String[]> e : SiteLayout.get().signs().entrySet()) {
            int[] c = SiteLayout.unkey(e.getKey());
            Block b = w.getBlockAt(c[0], c[1], c[2]);
            if (!(b.getState() instanceof Sign sign)) {
                continue;
            }
            SignSide side = sign.getSide(Side.FRONT);
            String[] lines = e.getValue();
            for (int i = 0; i < 4; i++) {
                side.line(i, Component.text(i < lines.length ? lines[i] : ""));
            }
            side.setColor(DyeColor.PURPLE);
            side.setGlowingText(true);
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    /* ================================================================== tick */

    private void tick() {
        clock++;
        World w = world();
        if (w == null) {
            recovered = false;
            return;
        }
        if (job != null) {
            if (job.step(12000)) {
                job = null;
                if (afterJob != null) {
                    Runnable r = afterJob;
                    afterJob = null;
                    r.run();
                }
            }
            return;
        }
        if (!built()) {
            return;
        }
        if (!recovered) {
            recovered = true;
            recover(w);
            return;
        }
        if (senses != null) {
            senses.tick();
        }
        if (glimpse != null && !glimpse.tick()) {
            glimpse.clear();
            glimpse = null;
        }
        if (clock % 5 == 0) {
            rescue(w);
        }
        if (clock % 10 != 0) {
            return;
        }
        switch (state) {
            case DORMANT -> tickDormant(w);
            case GUARDED -> tickGuarded(w);
            case OPEN -> tickOpen(w);
            case FEAST -> tickFeast(w);
            case AFTERMATH -> tickAftermath(w);
            default -> {
            }
        }
        if (clock % 240 == 0) {
            ambience(w);
        }
    }

    /** First tick with the world loaded: whatever state a crash left, rebuild and go dormant. */
    private void recover(World w) {
        String raw = w.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
        State persisted = State.DORMANT;
        if (raw != null) {
            try {
                persisted = State.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                persisted = State.DORMANT;
            }
        }
        if (persisted != State.DORMANT || !SiteLayout.get().statueCells().isEmpty() && statueBroken(w)) {
            plugin.getLogger().info("World Eater: recovering site from state " + persisted + ".");
            runJob(terrain().restoreAll(n -> {
                writeSigns();
                setState(State.DORMANT);
            }), null);
        } else {
            setState(State.DORMANT);
        }
    }

    private boolean statueBroken(World w) {
        int missing = 0;
        for (int[] c : SiteLayout.get().statueCells()) {
            if (w.getBlockAt(c[0], c[1], c[2]).getType().isAir()) {
                missing++;
            }
        }
        return missing > 6;
    }

    private void tickDormant(World w) {
        for (Player p : w.getPlayers()) {
            if (!WeFx.vulnerable(p)) {
                continue;
            }
            Location l = p.getLocation();
            if (!glimpsed && l.getZ() > -99.5 && l.getZ() < -92 && l.getX() > 12 && l.getX() < 36 && Math.abs(l.getY() - 100) < 6) {
                playGlimpse(w);
            }
            if (SiteLayout.onPlaza(l.getX(), l.getZ()) && Math.abs(l.getY() - 100) < 6
                    && Math.hypot(l.getX() - SiteLayout.PLAZA_X, l.getZ() - SiteLayout.PLAZA_Z) < 13.5) {
                wakeGuardian(w, p);
                return;
            }
        }
    }

    private void wakeGuardian(World w, Player who) {
        // The statue's blocks go first so the body has room; the director shows the same statue as
        // displays in the same tick and breaks its crust away.
        terrain().clearCells(SiteLayout.get().statueCells());
        Optional<BossInstance> spawned = plugin.getBossManager().spawn(UNBROKEN_ID, SiteLayout.plazaAnchor(w), SpawnCause.API, who);
        if (spawned.isEmpty()) {
            terrain().restoreCells(SiteLayout.get().statueCells());
            return;
        }
        guardian = spawned.get();
        setState(State.GUARDED);
        lonely = 0;
    }

    private void tickGuarded(World w) {
        if (guardian == null || guardian.getState() != BossState.ALIVE) {
            // It left without falling (killed by command, despawned): the statue kneels again.
            guardian = null;
            terrain().restoreCells(SiteLayout.get().statueCells());
            setState(State.DORMANT);
            return;
        }
        if (!anyoneNear(w, SiteLayout.PLAZA_X, SiteLayout.PLAZA_Z, 64)) {
            if (++lonely >= 60) {
                plugin.getBossManager().despawn(guardian, BossDespawnEvent.Reason.COMMAND, null);
                lonely = 0;
            }
        } else {
            lonely = 0;
        }
    }

    private void tickOpen(World w) {
        if (!anyoneNear(w, 24, -10, 90)) {
            if (++lonely >= 120 && !someoneInTheWay(w)) {
                lonely = 0;
                build(null);
            }
            return;
        }
        lonely = 0;
        if (spawnRetry > 0) {
            spawnRetry--;
            return;
        }
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (WeFx.vulnerable(p) && SiteLayout.onIsland(l.getX(), l.getZ()) && l.getZ() > 3
                    && l.getY() > SiteLayout.FLOOR - 1 && l.getY() < SiteLayout.FLOOR + 14) {
                Optional<BossInstance> spawned = plugin.getBossManager().spawn(EATER_ID, SiteLayout.islandAnchor(w), SpawnCause.API, p);
                if (spawned.isPresent()) {
                    eater = spawned.get();
                    setState(State.FEAST);
                } else {
                    spawnRetry = 10;
                }
                return;
            }
        }
    }

    private void tickFeast(World w) {
        if (eater == null || eater.getState() != BossState.ALIVE) {
            eater = null;
            restoreIsland(null);
            setState(State.OPEN);
            spawnRetry = 12;
            return;
        }
        if (!anyoneNear(w, SiteLayout.CENTER_X, SiteLayout.CENTER_Z, 90)) {
            if (++lonely >= 90) {
                plugin.getBossManager().despawn(eater, BossDespawnEvent.Reason.COMMAND, null);
                lonely = 0;
            }
        } else {
            lonely = 0;
        }
    }

    private void tickAftermath(World w) {
        if (aftermathTicks > 0) {
            aftermathTicks -= 10;
        }
        boolean empty = !anyoneNear(w, 24, 0, 100);
        if ((aftermathTicks <= 0 || empty && aftermathTicks < 20 * 60 * 4) && !someoneInTheWay(w)) {
            aftermathTicks = -1;
            build(null);
        }
    }

    /** Someone stands where a rebuild would put the gate doors or the statue back. */
    private boolean someoneInTheWay(World w) {
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            boolean gate = l.getX() > 15 && l.getX() < 33 && l.getZ() > -15 && l.getZ() < -8 && l.getY() > 97 && l.getY() < 118;
            boolean statue = l.getX() > 13 && l.getX() < 35 && l.getZ() > -39 && l.getZ() < -17 && l.getY() > 97 && l.getY() < 113;
            if (gate || statue) {
                return true;
            }
        }
        return false;
    }

    private boolean anyoneNear(World w, double x, double z, double r) {
        for (Player p : w.getPlayers()) {
            if (!WeFx.vulnerable(p)) {
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - x;
            double dz = l.getZ() - z;
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    /* ================================================================== the void spits you back */

    private void rescue(World w) {
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (!WeFx.vulnerable(p)) {
                continue;
            }
            Location l = p.getLocation();
            if (l.getY() > 86) {
                continue;
            }
            Location to;
            int k = ThreadLocalRandom.current().nextInt(12);
            if (l.getZ() > -11 && state != State.DORMANT && state != State.GUARDED) {
                to = SiteLayout.islandSafe(w, k);
            } else if (l.getZ() > -47) {
                to = SiteLayout.plazaSafe(w);
            } else {
                to = SiteLayout.arrival(w);
            }
            to.setYaw(l.getYaw());
            p.teleport(to);
            p.setFallDistance(0f);
            p.setVelocity(new Vector(0, 0.35, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, false));
            boolean fight = state == State.GUARDED || state == State.FEAST;
            if (fight && WeFx.vulnerable(p)) {
                BossInstance source = state == State.FEAST ? eater : guardian;
                BossHits.hurt(p, source == null ? null : source.getEntity(), 30);
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.HOSTILE, 0.25f, 1.8f);
            w.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            Long last = rescued.put(p.getUniqueId(), now);
            if (told.add(p.getUniqueId()) || last == null || now - last > 60_000L) {
                p.sendMessage(TextUtil.component(state == State.FEAST
                        ? "&8&oThe void spits you back out. It is saving you for later."
                        : "&8&oThe void spits you back out. Nothing down there wants you. Not yet."));
            }
        }
    }

    /* ================================================================== the road */

    private void ambience(World w) {
        if (state != State.DORMANT && state != State.GUARDED) {
            return;
        }
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (l.getZ() < -44 && l.getZ() > -130) {
                // Something vast breathes far below the road.
                Location below = l.clone().add(ThreadLocalRandom.current().nextDouble(-20, 20), -40, ThreadLocalRandom.current().nextDouble(-20, 20));
                p.playSound(below, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.AMBIENT, 1.4f, 0.5f);
                p.playSound(below, Sound.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, 0.6f, 0.5f);
            }
        }
    }

    private void playGlimpse(World w) {
        glimpsed = true;
        clearGlimpse();
        glimpse = new Glimpse(w);
    }

    private void clearGlimpse() {
        if (glimpse != null) {
            glimpse.clear();
            glimpse = null;
        }
    }

    /**
     * Halfway down the road something passes beneath it. A glimpse of vertebrae made of whole
     * worlds, a skull that rises out of the dark and takes one bite out of the desert ahead, and
     * then it is gone. Nobody on the road is told what it was.
     */
    private final class Glimpse {
        private final WeFx fx;
        private final Serpent body;
        private final SplinePath path;
        private final WeProps props = new WeProps();
        private final float biteAt;
        private float at;
        private int age;
        private boolean bitten;

        Glimpse(World w) {
            Location anchor = new Location(w, 24.0, SiteLayout.FLOOR + 1.0, -80.0);
            fx = new WeFx(anchor, plugin.getKeys(), UUID.randomUUID()).audience(120.0);
            List<Vector3f> controls = new ArrayList<>();
            controls.add(stage(76, 68, -44));
            controls.add(stage(52, 76, -64));
            controls.add(stage(35, 88, -75));
            controls.add(stage(27.5f, 97.4f, -79.6f));
            controls.add(stage(19, 88, -87));
            controls.add(stage(4, 80, -99));
            controls.add(stage(-22, 70, -114));
            controls.add(stage(-50, 60, -128));
            path = SplinePath.through(controls, 28);
            biteAt = path.nearest(stage(27.5f, 97.4f, -79.6f));
            body = new Serpent(fx, 12);
            Vector3f start = path.start(new Vector3f());
            Vector3f dir = path.tangent(0f, new Vector3f());
            body.spawn(start, dir, true);
            fx.scoreFrom(start, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2f, 0.5f);
        }

        private Vector3f stage(float x, float y, float z) {
            return new Vector3f(x - 24.0f, y - (SiteLayout.FLOOR + 1), z + 80.0f);
        }

        /** @return false when finished */
        boolean tick() {
            age++;
            float speed = Math.abs(at - biteAt) < 10f ? 1.25f : 0.95f;
            at += speed;
            Vector3f p = path.at(at, new Vector3f());
            body.moveTo(p);
            float toBite = biteAt - at;
            if (toBite < 12f && toBite > 0f) {
                body.jaw(WeMath.smooth(1f - toBite / 12f) * 1.1f);
            } else if (toBite <= 0f && !bitten) {
                bite();
            }
            if (age % 20 == 0) {
                fx.sound(p, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 3f, 0.5f + ThreadLocalRandom.current().nextFloat() * 0.1f);
            }
            if (toBite < 24f && toBite > 6f && age % 2 == 0) {
                for (Player pl : fx.audience()) {
                    if (pl.getLocation().getZ() < -84 && pl.getLocation().getZ() > -110) {
                        Senses.pan(pl, fx.at(new Vector3f(3.5f, -2f, 0.4f)), 0.07f);
                    }
                }
            }
            body.render(2);
            props.tick();
            return at < path.length() + 4f;
        }

        private void bite() {
            bitten = true;
            body.snap();
            SiteTerrain t = terrain();
            if (t != null) {
                SiteLayout.Box b = SiteLayout.ROAD_BITE;
                for (int x = b.x0(); x <= b.x1(); x++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        for (int y = b.y1(); y >= b.y0(); y--) {
                            Material was = t.clear(x, y, z);
                            if (was != null && y >= SiteLayout.FLOOR - 2 && (x + y + z) % 2 == 0) {
                                Vector3f c = new Vector3f(x + 0.5f - 24f, y + 0.5f - (SiteLayout.FLOOR + 1), z + 0.5f + 80f);
                                glimpseDebris(c, was);
                            }
                        }
                    }
                }
            }
            Vector3f at = new Vector3f(3.5f, -2f, 0.4f);
            fx.sound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, 2.5f, 0.5f);
            fx.sound(at, Sound.BLOCK_SAND_BREAK, 2.5f, 0.5f);
            fx.sound(at, Sound.ENTITY_GENERIC_EAT, 2.5f, 0.5f);
            fx.sound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.5f);
            fx.blockDust(at, Material.SAND, 80, 1.6);
        }

        private void glimpseDebris(Vector3f c, Material m) {
            if (props.size() > 12 || !m.isBlock()) {
                return;
            }
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector3f push = new Vector3f((float) r.nextDouble(-2, 2), 0f, (float) r.nextDouble(-2, 2));
            props.add(new WeProps.Debris(fx, m, c, new Vector3f(c).add(push.x * 0.4f, 1.5f, push.z * 0.4f),
                    new Vector3f(c).add(push.x, -30f, push.z), 6, 22, 0, 0.6f));
        }

        void clear() {
            props.clear();
            body.remove();
            fx.clear();
        }
    }

    /** Listener hook: players leaving the world or the server let go of the site's sky. */
    public static void forget(Player player) {
        Senses.forget(player);
    }
}
