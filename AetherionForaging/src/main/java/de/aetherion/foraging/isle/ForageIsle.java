package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.weather.IsleWeatherService;
import de.aetherion.foraging.weather.WeatherKind;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Foraging Eldervale: the forage destination. It owns every isle loop and routes each fell through all
 * of them, the way the Mining isle routes a strike:
 * <pre>
 *   fell ─ Fell Pulse (timing bar · streak · look-preview) — ForagingListener, unchanged at heart
 *        ─ Titan fells (two-stage chop on giants)          ─ district (baked grid + landmark boxes)
 *        ─ Grove Mastery (per wood, I–VII, permanent)      ─ Crown Finds (catch it as it falls)
 *        ─ District extras (weather-tilted)                ─ Widowmaker → Deadfall
 *        ─ Lumber Board orders · Warden Standing           ─ records (largest fell per forest)
 *        ─ Isle events (Golden Sap · Windfall · Blossom Storm · Bark Blight) · critters
 *   payout ─ ForagingListener asks back: CHOP window · marker speed · miss cooldown · wood cap ·
 *            heartwood chance · Codex filing
 *   around ─ updrafts · fall-catch · compass · the Grove cast · bench · ledger · journal
 * </pre>
 * Everything outside the isle footprint is left exactly as it was (harbour trees, personal islands).
 */
public final class ForageIsle implements Listener {

    private static volatile ForageIsle instance;

    private final AetherionForaging plugin;
    private final ForageConfig config;
    private final GroveMap map;
    private final ForageProfiles profiles;
    private final WardenStanding standing = new WardenStanding();
    private final GroveMastery mastery;
    private final CrownFinds finds;
    private final DistrictLoot loot;
    private final Widowmaker widowmaker;
    private final Woodwright woodwright;
    private final LumberBoard board;
    private final ForestLedger ledger;
    private final GroveEvents events;
    private final GroveCritters critters;
    private final Traversal traversal;
    private final GroveCompass compass;
    private final ForageCast cast;
    private final ForageMenus menus;
    private final ForageDev dev;

    private final List<Lure> lures = new ArrayList<>();
    private final List<Integer> tasks = new ArrayList<>();
    private List<Player> onIsleCache = List.of();
    private int onIsleTick = -1;

    private String worldName = "world";
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double minZ;
    private double maxZ;

    private record Lure(Location at, long until) {
    }

    public ForageIsle(AetherionForaging plugin) {
        this.plugin = plugin;
        ForageItems.init(plugin);
        migrateConfig();
        cacheFootprint();
        this.config = new ForageConfig(plugin);
        this.map = new GroveMap(plugin);
        this.profiles = new ForageProfiles(plugin);
        this.mastery = new GroveMastery(this);
        this.finds = new CrownFinds(this);
        this.loot = new DistrictLoot(this);
        this.widowmaker = new Widowmaker(this);
        this.woodwright = new Woodwright(this);
        this.board = new LumberBoard(this);
        this.ledger = new ForestLedger(this);
        this.events = new GroveEvents(this);
        this.critters = new GroveCritters(this);
        this.traversal = new Traversal(this);
        this.compass = new GroveCompass(this);
        this.cast = new ForageCast(this);
        this.menus = new ForageMenus(this);
        this.dev = new ForageDev(this);
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(this, plugin);
        pm.registerEvents(woodwright, plugin);
        pm.registerEvents(events, plugin);
        pm.registerEvents(critters, plugin);
        pm.registerEvents(traversal, plugin);
        pm.registerEvents(cast, plugin);
        pm.registerEvents(menus, plugin);
        PluginCommand grove = plugin.getCommand("grove");
        if (grove != null) {
            GroveCommand command = new GroveCommand(this);
            grove.setExecutor(command);
            grove.setTabCompleter(command);
        }
        var scheduler = Bukkit.getScheduler();
        tasks.add(scheduler.runTaskTimer(plugin, this::tick, 20L, 1L).getTaskId());
        tasks.add(scheduler.runTaskTimer(plugin, () -> profiles.saveAsync(false), 1200L, 1200L).getTaskId());
        scheduler.runTaskLater(plugin, cast::ensureAll, 80L);
        instance = this;
        plugin.getLogger().info("Foraging Eldervale: grid " + map.status() + " · " + config.landmarks().size() + " places · "
                + config.updrafts().size() + " updrafts · Items skills " + (ForageBridge.skillsInstalled() ? "found" : "not in this Items jar"));
    }

    public static ForageIsle get() {
        return instance;
    }

    /** One-time: hotspots / bait were placeholders; they're designed now (Golden Sap, Sap Lure). */
    private void migrateConfig() {
        var cfg = plugin.getConfig();
        if (!cfg.isSet("hotspots.designed")) {
            cfg.set("hotspots.enabled", true);
            cfg.set("bait.enabled", true);
            cfg.set("hotspots.designed", "grove-events-v1");
            plugin.saveConfig();
            plugin.getLogger().info("Foraging Eldervale: hotspots (Golden Sap) and bait (Sap Lure) switched on — set false to opt out.");
        }
    }

    private void cacheFootprint() {
        var cfg = plugin.getConfig();
        worldName = cfg.getString("forage-isle.world", "world");
        minX = cfg.getDouble("forage-isle.light.min-x", 500);
        maxX = cfg.getDouble("forage-isle.light.max-x", 1070);
        minY = cfg.getDouble("forage-isle.light.min-y", 50);
        maxY = cfg.getDouble("forage-isle.light.max-y", 220);
        minZ = cfg.getDouble("forage-isle.light.min-z", -500);
        maxZ = cfg.getDouble("forage-isle.light.max-z", 30);
    }

    public void reload() {
        cacheFootprint();
        config.reload();
        map.realign();
    }

    public void shutdown() {
        instance = null;
        for (int id : tasks) {
            Bukkit.getScheduler().cancelTask(id);
        }
        tasks.clear();
        finds.clear();
        critters.clear();
        events.shutdown();
        traversal.clear();
        cast.removeHolograms();
        ledger.removeBoard();
        profiles.saveNow();
    }

    // ------------------------------------------------------------------ the heartbeat

    private int tick;

    private void tick() {
        tick++;
        if (!config.enabled()) {
            return;
        }
        List<Player> onIsle = playersOnIsle();
        traversal.tick(onIsle);
        if (tick % 2 == 0) {
            finds.run();
            critters.tick();
        }
        if (tick % 10 == 0 && !onIsle.isEmpty()) {
            compass.tick(onIsle);
        }
        if (tick % 20 == 0) {
            events.tick();
            critters.ambient(onIsle);
            pruneLures();
        }
        if (tick % 100 == 0) {
            cast.tickHolograms();
        }
        if (tick % 600 == 0) {
            ledger.tickBoard();
        }
    }

    // ------------------------------------------------------------------ where

    public World isleWorld() {
        return Bukkit.getWorld(worldName);
    }

    public boolean onIsle(Location at) {
        if (at == null || at.getWorld() == null || !at.getWorld().getName().equals(worldName)) {
            return false;
        }
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Inside the footprint columns at any height — edge rescue uses this. */
    public boolean inFootprintXZ(Location at) {
        if (at == null || at.getWorld() == null || !at.getWorld().getName().equals(worldName)) {
            return false;
        }
        return at.getX() >= minX && at.getX() <= maxX && at.getZ() >= minZ && at.getZ() <= maxZ;
    }

    /** Players on the isle this tick (cached per tick). */
    public List<Player> playersOnIsle() {
        int now = Bukkit.getCurrentTick();
        if (now == onIsleTick) {
            return onIsleCache;
        }
        World world = isleWorld();
        List<Player> out = new ArrayList<>();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                if (onIsle(player.getLocation())) {
                    out.add(player);
                }
            }
        }
        onIsleCache = out;
        onIsleTick = now;
        return out;
    }

    /** The smallest named place containing {@code at}, or null. */
    public Landmark landmark(Location at) {
        if (!onIsle(at)) {
            return null;
        }
        Landmark best = null;
        for (Landmark landmark : config.landmarks().values()) {
            if (landmark.contains(at.getX(), at.getY(), at.getZ()) && (best == null || landmark.volume() < best.volume())) {
                best = landmark;
            }
        }
        return best;
    }

    /** District at a location: a place's own forest first, then the baked grid. */
    public Grove grove(Location at) {
        if (!onIsle(at)) {
            return null;
        }
        Landmark landmark = landmark(at);
        if (landmark != null && landmark.grove() != null) {
            return landmark.grove();
        }
        return map.gridAt(at.getX(), at.getY(), at.getZ());
    }

    /** TAB / area label: "Blossom Pagoda" inside a place, else the forest, else null. */
    public String areaLabel(Location at) {
        if (!config.enabled()) {
            return null;
        }
        Landmark landmark = landmark(at);
        if (landmark != null) {
            return landmark.display();
        }
        Grove grove = grove(at);
        return grove == null ? null : grove.display();
    }

    public WeatherKind weatherKind(Player player) {
        IsleWeatherService weather = plugin.weather();
        if (weather == null || player == null) {
            return null;
        }
        try {
            return weather.current(player).kind();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** How the sky tilts Crown Finds in each forest. */
    double weatherFindFactor(Player player, Grove grove) {
        WeatherKind kind = weatherKind(player);
        if (kind == null || grove == null) {
            return 1.0d;
        }
        return switch (grove) {
            case FROSTPINE -> kind == WeatherKind.SNOW ? 1.5d : 1.0d;
            case GLOAMWOOD -> kind == WeatherKind.FOG ? 1.5d : 1.0d;
            case SUNSCAR -> kind == WeatherKind.CLEAR ? 1.25d : 1.0d;
            case BRINEFALL, CANOPY_CROWN -> kind == WeatherKind.RAIN || kind == WeatherKind.DRIZZLE ? 1.4d : 1.0d;
            case BLOSSOM -> kind == WeatherKind.WINDY ? 1.4d : 1.0d;
            case ELDERWOOD -> kind == WeatherKind.DRIZZLE ? 1.2d : 1.0d;
        };
    }

    public Location guideLocation() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("isle-guide.placed", false)) {
            Landmark landing = config.landmark("landing");
            return landing == null ? null : landing.anchor(isleWorld());
        }
        World world = Bukkit.getWorld(cfg.getString("isle-guide.world", worldName));
        return world == null ? null : new Location(world, cfg.getDouble("isle-guide.x"), cfg.getDouble("isle-guide.y"),
                cfg.getDouble("isle-guide.z"));
    }

    // ------------------------------------------------------------------ lures

    void lure(Location at, long ms) {
        lures.add(new Lure(at.clone(), System.currentTimeMillis() + ms));
    }

    boolean lured(Location at) {
        if (at == null || lures.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Lure lure : lures) {
            if (lure.until() > now && lure.at().getWorld() == at.getWorld() && lure.at().distanceSquared(at) <= 36.0d) {
                return true;
            }
        }
        return false;
    }

    private void pruneLures() {
        long now = System.currentTimeMillis();
        Iterator<Lure> it = lures.iterator();
        while (it.hasNext()) {
            Lure lure = it.next();
            if (lure.until() <= now) {
                it.remove();
            } else if (tick % 60 == 0 && lure.at().getWorld() != null) {
                lure.at().getWorld().spawnParticle(org.bukkit.Particle.DRIPPING_HONEY, lure.at(), 2, 0.25, 0.3, 0.25, 0.0);
            }
        }
    }

    /** A log with leaves close above it — good enough to hang a lure on. */
    boolean isLivingLog(Block block) {
        if (block == null || !Tag.LOGS.isTagged(block.getType())) {
            return false;
        }
        for (int dy = 1; dy <= 14; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Material type = block.getRelative(dx, dy, dz).getType();
                    if (Tag.LEAVES.isTagged(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ hooks the chop listener calls

    /** Extra CHOP window cells for this player on this wood (isle only). */
    public int zoneBonus(Player player, Material wood, Location at) {
        if (!onIsle(at)) {
            return 0;
        }
        ForageProfile profile = profiles.get(player);
        int bonus = Woodwright.zoneBonus(profile);
        if (ForageBridge.has(player, ForageBridge.STEADY_HANDS)) {
            bonus++;
        }
        if (GroveMastery.widerWindow(profile, Wood.of(wood))) {
            bonus++;
        }
        if (profile.frostlitUntil > System.currentTimeMillis()) {
            bonus++;
        }
        return Math.min(3, bonus);
    }

    /** Fell marker slow-down (Keen Edge). 1.0 off the isle. */
    public double strikeFactor(Player player, Location at) {
        return onIsle(at) ? Woodwright.strikeFactor(profiles.get(player)) : 1.0d;
    }

    public long missCooldown(Player player, Location at, long baseMs) {
        if (!onIsle(at)) {
            return baseMs;
        }
        double factor = Woodwright.missFactor(profiles.get(player));
        double steady = ForageBridge.scale(player, ForageBridge.STEADY_HANDS);
        factor *= 1.0d - 0.25d * Math.min(2.0d, steady);
        return Math.max(10_000L, Math.round(baseMs * factor));
    }

    /** Sure Grip V: keep the streak through this miss. */
    public boolean secondWind(Player player, Location at) {
        return onIsle(at) && woodwright.secondWind(player, profiles.get(player));
    }

    /** Final wood cap for a tree (base comes from skill level + Fortune in the listener). */
    public int woodCap(Player player, Material wood, Location at, int base, boolean titan) {
        if (!onIsle(at)) {
            return base;
        }
        ForageProfile profile = profiles.get(player);
        Wood w = Wood.of(wood);
        int cap = base + Woodwright.woodCapBonus(profile) + GroveMastery.woodCapBonus(profile, w)
                + ForestLedger.woodCapBonus(profile, w);
        double born = ForageBridge.scale(player, ForageBridge.GROVE_BORN);
        if (born > 0) {
            cap += (int) Math.round(Math.min(4.0d, 2.0d * born));
        }
        double factor = events.woodCapFactor(grove(at));
        if (titan) {
            factor *= 1.6d;
        }
        return Math.max(1, (int) Math.round(cap * factor));
    }

    public double heartwoodChance(Player player, Material drop, double base) {
        if (!onIsle(player.getLocation())) {
            return base;
        }
        ForageProfile profile = profiles.get(player);
        double chance = base + GroveMastery.heartwoodBonus(profile, Wood.of(drop)) + ForestLedger.collectorRank(profile) * 0.002d;
        chance *= 1.0d + 0.5d * ForageBridge.scale(player, ForageBridge.HEART_HUNTER);
        if (profile.incenseUntil > System.currentTimeMillis()) {
            chance *= 2.0d;
        }
        chance *= events.heartwoodFactor(grove(player.getLocation()));
        return Math.min(0.35d, chance);
    }

    /** Every log paid anywhere: the Codex finally hears chopped wood. */
    public void woodPaid(Player player, Material drop) {
        ForageBridge.codexWood(player, drop);
    }

    public boolean titan(Location at, int logs) {
        return onIsle(at) && logs >= Math.max(16, config.tuningInt("titan-logs", 40));
    }

    /** One clean isle fell: route it through every loop. */
    public void onFell(Player player, FellContext ctx) {
        if (player == null || ctx == null || !onIsle(ctx.anchor())) {
            return;
        }
        ForageProfile profile = profiles.get(player);
        Grove grove = grove(ctx.anchor());
        profile.totalFells++;
        if (ctx.perfect()) {
            profile.perfects++;
        }
        if (ctx.titan()) {
            profile.titans++;
            if (profile.flags.add("first_titan")) {
                ForageText.card(player, "§6Titan felled", "§7The island felt that one", 40);
            }
        }
        profile.dirty = true;
        mastery.count(player, profile, ctx.wood(), ctx.titan());
        finds.onFell(player, profile, ctx, grove);
        loot.onFell(player, ctx, grove);
        widowmaker.onFell(player, ctx);
        board.noteFell(player, profile, ctx, grove);
        critters.onFell(player, ctx, grove);
        int xp = events.bonusXp(grove);
        if (xp > 0) {
            ForageBridge.bonus(player, xp);
        }
        if (grove != null) {
            int best = profile.bestFell.getOrDefault(grove.id(), 0);
            if (ctx.logs() > best) {
                profile.bestFell.put(grove.id(), ctx.logs());
            }
            if (profiles.offerRecord("fell." + grove.id(), player, ctx.logs(), ctx.wood() == null ? "" : ctx.wood().display())
                    && ctx.logs() >= 20) {
                player.sendMessage("§6★ Isle record §8· §7largest fell in " + grove.colored() + " §8· §f" + ctx.logs() + " logs");
            }
        }
    }

    /** Called when the deadfall beat succeeds (Widowmaker dodged). */
    void deadfall(Player player) {
        board.noteDeadfall(player, profiles.get(player));
    }

    /** "Frostpine II ▮▮▯ 34/120" for the fell tally, or null off the isle. */
    public String masteryLine(Player player, Material wood, Location at) {
        Wood w = Wood.of(wood);
        if (w == null || !onIsle(at)) {
            return null;
        }
        return GroveMastery.progress(profiles.get(player), w);
    }

    // ------------------------------------------------------------------ access

    public AetherionForaging plugin() {
        return plugin;
    }

    public ForageConfig config() {
        return config;
    }

    public GroveMap map() {
        return map;
    }

    public ForageProfiles profiles() {
        return profiles;
    }

    public WardenStanding standing() {
        return standing;
    }

    public CrownFinds finds() {
        return finds;
    }

    public Woodwright woodwright() {
        return woodwright;
    }

    public LumberBoard board() {
        return board;
    }

    public ForestLedger ledger() {
        return ledger;
    }

    public GroveEvents events() {
        return events;
    }

    public GroveCritters critters() {
        return critters;
    }

    public Traversal traversal() {
        return traversal;
    }

    public GroveCompass compass() {
        return compass;
    }

    public ForageCast cast() {
        return cast;
    }

    public ForageMenus menus() {
        return menus;
    }

    public ForageDev dev() {
        return dev;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        compass.forget(id);
        critters.forget(id);
        cast.forget(id);
        woodwright.forget(id);
        ForageText.forget(id);
        profiles.saveAsync(false);
    }
}
