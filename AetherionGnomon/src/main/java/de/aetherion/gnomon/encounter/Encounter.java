package de.aetherion.gnomon.encounter;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.gnomon.GnomonConfig;
import de.aetherion.gnomon.GnomonPlugin;
import de.aetherion.gnomon.Lang;
import de.aetherion.gnomon.ai.Brain;
import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.MoveSpec;
import de.aetherion.gnomon.ai.Moves;
import de.aetherion.gnomon.ai.Posture;
import de.aetherion.gnomon.ai.Profile;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.ai.Tell;
import de.aetherion.gnomon.attack.Breath;
import de.aetherion.gnomon.attack.FalseOpening;
import de.aetherion.gnomon.attack.Feint;
import de.aetherion.gnomon.attack.HourHand;
import de.aetherion.gnomon.attack.Interdict;
import de.aetherion.gnomon.attack.PlumbDrop;
import de.aetherion.gnomon.attack.Recital;
import de.aetherion.gnomon.attack.Stomp;
import de.aetherion.gnomon.attack.Stride;
import de.aetherion.gnomon.attack.SurveyLine;
import de.aetherion.gnomon.fx.Fx;
import de.aetherion.gnomon.fx.Ledger;
import de.aetherion.gnomon.fx.NoteMelody;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;
import de.aetherion.gnomon.math.Ease;
import de.aetherion.gnomon.math.Geo;
import de.aetherion.gnomon.render.Parts;
import de.aetherion.gnomon.rig.Tripod;
import de.aetherion.gnomon.world.TempBlocks;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One GNOMON fight. Owns the rig, the dial, the mind, every telegraph and every temporary change, and gives all of it
 * back on {@link #close(String)}. BossEngine owns the HP pool and loot (this is driven from {@code GnomonScript}).
 */
public final class Encounter {

    public enum State {
        INTRO, FIGHT, TRANSITION, DYING, DONE, CLOSED
    }

    public static final Color GOLD = Color.fromRGB(0xFF, 0xC8, 0x3C);

    private final GnomonPlugin plugin;
    private final GnomonConfig cfg;
    private final BossInstance instance;
    private final World world;
    private final Arena arena;
    private final Parts parts;
    private final Tripod rig;
    private final Shapes shapes;
    private final Ledger ledger;
    private final Fx fx;
    private final Party party;
    private final Brain brain;
    private final TempBlocks blocks;
    private final BlockData floorBlock;

    private final List<Hazard> hazards = new ArrayList<>();
    private final Map<String, Long> iframes = new HashMap<>();
    private final Map<UUID, Long> pathSeen = new HashMap<>();
    private final Map<UUID, Long> lastSeenInArena = new HashMap<>();
    private final NoteMelody music;

    private State state = State.INTRO;
    private int stateTick;
    private long clock;
    private int phase = 1;
    private Attack attack;
    private long nextMoveAt;
    private Decision lastDecision;
    private int threatSeq;
    private int errors;
    private long emptySince = -1;
    private boolean exploitActive;
    private long lookHoldUntil;
    private double deathQuality;

    // openings
    private Brain.Opening opening = Brain.Opening.NONE;
    private long openingStart;
    private long openingEnd;
    private UUID falseTarget;
    private boolean falseBit;
    private Shapes.Circle goldMark;

    public Encounter(GnomonPlugin plugin, GnomonConfig cfg, BossInstance instance, Location center) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.instance = instance;
        this.world = center.getWorld();
        double foundFloor = Arena.findFloor(center);
        final double floorY = Double.isNaN(foundFloor) ? center.getY() : foundFloor;
        double cx = center.getBlockX() + 0.5;
        double cz = center.getBlockZ() + 0.5;
        this.arena = new Arena(world, cx, cz, floorY, cfg.arenaRadius(), cfg.leaveRadius());
        Location anchor = new Location(world, cx, floorY, cz);
        this.floorBlock = world.getBlockAt(center.getBlockX(), (int) Math.floor(floorY) - 1, center.getBlockZ()).getBlockData();
        this.parts = new Parts(anchor, cfg.viewRange());
        this.shapes = new Shapes(parts, floorY);
        this.ledger = new Ledger(parts, floorY, cfg.i("ledger.max-segments", 160), cfg.i("ledger.per-player", 44));
        this.party = new Party(arena, cfg.seal(), cfg.night());
        this.fx = new Fx(world, party::audience);
        this.brain = new Brain(cfg.brain());
        this.blocks = new TempBlocks(new File(plugin.getDataFolder(), "journal.yml"), plugin.getLogger());
        this.arena.build(parts);
        this.rig = new Tripod(cfg.rig(), parts, floorY, cx, cz, -Math.PI / 2);
        this.music = loadMusic(plugin, cfg, party, () -> new Location(world, rig.hubX(), floorY + 4.0, rig.hubZ()));
        rig.onAutoPlant(this::footPlanted);
        rig.onStepStart(this::footLifted);
        party.onJoin(this::joined);
        party.onLeave((id, name) -> {
            brain.leave(id);
            ledger.lift(id);
            pathSeen.remove(id);
        });
        brain.start(0, cx, cz);
        brain.setHub(cx, cz);
        // Intro pose: folded under the floor, lens shut, no needle.
        rig.placeHub(cx, floorY - 1.0, cz);
        rig.heightTarget(-1.0);
        rig.lensScale(0);
        rig.needleLength(0);
        rig.gazeSize(0);
        rig.forcePose();
    }

    private static NoteMelody loadMusic(GnomonPlugin plugin, GnomonConfig cfg, Party party,
                                        java.util.function.Supplier<Location> origin) {
        NoteMelody empty = NoteMelody.load(InputStream.nullInputStream(), party::audience, origin, plugin.getLogger());
        if (!cfg.b("music.enabled", true)) {
            return empty;
        }
        String track = plugin.getConfig().getString("music.track", "stalemate");
        if (track == null || track.isBlank()) {
            track = "stalemate";
        }
        String resource = "melodies/" + track.trim().toLowerCase(Locale.ROOT) + ".nbm";
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("[Gnomon] Music track missing: " + resource);
                return empty;
            }
            NoteMelody m = NoteMelody.load(in, party::audience, origin, plugin.getLogger());
            m.volume((float) plugin.getConfig().getDouble("music.volume", 0.75));
            m.loop(plugin.getConfig().getBoolean("music.loop", true));
            // follow = each fighter hears at their feet (covers the whole dial). false = hub-anchored.
            m.followPlayers(plugin.getConfig().getBoolean("music.follow-players", true));
            if (m.size() > 0) {
                plugin.getLogger().info("[Gnomon] Music loaded: " + resource + " (" + m.size() + " notes)");
            }
            return m;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Gnomon] Music load failed", ex);
            return empty;
        }
    }

    /* ================================================================== accessors for moves */

    public GnomonPlugin plugin() {
        return plugin;
    }

    public GnomonConfig cfg() {
        return cfg;
    }

    public BossInstance instance() {
        return instance;
    }

    public World world() {
        return world;
    }

    public Arena arena() {
        return arena;
    }

    public Tripod rig() {
        return rig;
    }

    public Shapes shapes() {
        return shapes;
    }

    public Ledger ledger() {
        return ledger;
    }

    public Fx fx() {
        return fx;
    }

    public Party party() {
        return party;
    }

    public Brain brain() {
        return brain;
    }

    public TempBlocks blocks() {
        return blocks;
    }

    public long clock() {
        return clock;
    }

    public int phase() {
        return phase;
    }

    public double floorY() {
        return arena.floorY();
    }

    public BlockData floorBlock() {
        return floorBlock;
    }

    public State state() {
        return state;
    }

    public boolean dying() {
        return state == State.DYING || state == State.DONE;
    }

    public boolean blocksDamage() {
        return state != State.FIGHT;
    }

    public Player player(UUID id) {
        if (id == null) {
            return null;
        }
        Player p = Bukkit.getPlayer(id);
        return p != null && p.isOnline() && party.contains(p) && p.getWorld() == world ? p : null;
    }

    /** The player the lens is on (or the nearest member if the mind has no focus yet). */
    public Player focus() {
        Player p = player(brain.focus());
        if (p != null) {
            return p;
        }
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player m : party.online()) {
            double dd = Geo.dist(m.getLocation().getX(), m.getLocation().getZ(), rig.hubX(), rig.hubZ());
            if (dd < bestD) {
                bestD = dd;
                best = m;
            }
        }
        return best;
    }

    public static Vector3d head(Player p) {
        Location l = p.getEyeLocation();
        return new Vector3d(l.getX(), l.getY(), l.getZ());
    }

    public static Vector3d feet(Player p) {
        Location l = p.getLocation();
        return new Vector3d(l.getX(), l.getY(), l.getZ());
    }

    public String newThreatKey(String prefix) {
        return prefix + "#" + (++threatSeq);
    }

    /** Windup for the focus: posture and timing reads move it, the fairness floor holds it. */
    public int windup(int base) {
        int w = base + brain.windupAdjust(brain.focus());
        return Math.max(cfg.minTelegraph(), w);
    }

    public int commitTicks(int configured) {
        return Math.max(cfg.minCommit(), configured);
    }

    /**
     * Where the mind expects this player to be after reacting: their spot shifted by the habit it spends.
     * SIDE: sideways in their own frame (facing the hub). ORBIT: ahead along their circle around the hub.
     */
    public double[] predict(UUID id, Read exploit, int sign, double distance) {
        Profile p = brain.profile(id);
        Player pl = player(id);
        double x = pl != null ? pl.getLocation().getX() : (p == null ? arena.cx() : p.x());
        double z = pl != null ? pl.getLocation().getZ() : (p == null ? arena.cz() : p.z());
        if (exploit == Read.SIDE) {
            double fx = rig.hubX() - x;
            double fz = rig.hubZ() - z;
            double len = Math.sqrt(fx * fx + fz * fz);
            if (len > 1e-3) {
                fx /= len;
                fz /= len;
                // right-hand vector (-fz, fx)
                x += sign * distance * -fz;
                z += sign * distance * fx;
            }
        } else if (exploit == Read.ORBIT) {
            double r = Math.max(2.5, Geo.dist(x, z, rig.hubX(), rig.hubZ()));
            double a = Geo.angle(x - rig.hubX(), z - rig.hubZ()) + sign * distance / r;
            x = rig.hubX() + Math.cos(a) * r;
            z = rig.hubZ() + Math.sin(a) * r;
        }
        double[] c = arena.clamp(x, z, 1.0);
        return c;
    }

    /* ================================================================== lifecycle */

    private void joined(Player p) {
        brain.join(p.getUniqueId(), p.getName());
        if (state == State.FIGHT || state == State.TRANSITION) {
            fx.title(p, Lang.title(), Lang.subtitleIntro(p), 10, 40, 15);
            fx.soundIn(p, Sound.ITEM_SPYGLASS_USE, 1f, 0.8f);
        }
    }

    /** One engine tick. @return true once the death cinematic finished (engine pays loot, then despawns). */
    public boolean tick() {
        if (state == State.CLOSED) {
            return false;
        }
        clock++;
        stateTick++;
        try {
            if (clock % 5 == 1) {
                party.tick();
            }
            brain.setHub(rig.hubX(), rig.hubZ());
            brain.tick(clock);
            if ((state == State.FIGHT || state == State.TRANSITION) && clock % 2 == 0) {
                sample();
            }
            for (Tell tl : brain.drainTells()) {
                tell(tl);
            }
            fx.tick();
            ledger.tick();
            music.tick();
            boolean finished = false;
            switch (state) {
                case INTRO -> tickIntro();
                case FIGHT -> tickFight();
                case TRANSITION -> tickTransition();
                case DYING -> finished = tickDeath();
                default -> {
                }
            }
            tickHazards();
            tickOpening();
            refreshIris();
            rig.tick();
            placeHitbox();
            if (clock % 10 == 0) {
                plugin.debugHud(this);
            }
            if (finished) {
                state = State.DONE;
            }
            return finished;
        } catch (Throwable t) {
            errors++;
            plugin.getLogger().log(Level.SEVERE, "[Gnomon] Encounter tick failed (" + errors + "/5)", t);
            if (errors >= 5) {
                plugin.getLogger().severe("[Gnomon] Too many errors - ending the fight and cleaning up.");
                plugin.abort(this, "errors");
            }
            return false;
        }
    }

    private void sample() {
        for (Player p : party.online()) {
            Location l = p.getLocation();
            UUID id = p.getUniqueId();
            brain.observe(id, l.getX(), l.getY(), l.getZ(), l.getY() <= floorY() + 0.05);
            double max = p.getAttribute(maxHealthAttr()) == null ? 20 : p.getAttribute(maxHealthAttr()).getValue();
            brain.hp(id, p.getHealth() / Math.max(1, max));
            Profile prof = brain.profile(id);
            if (prof != null && !prof.path().isEmpty()) {
                Profile.PathPoint last = prof.path().peekLast();
                Long seen = pathSeen.get(id);
                if (seen == null || last.tick() > seen) {
                    pathSeen.put(id, last.tick());
                    ledger.point(id, last.x(), last.z());
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static org.bukkit.attribute.Attribute maxHealthAttr() {
        try {
            return org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException e) {
            return org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH");
        }
    }

    /* ================================================================== intro */

    private void tickIntro() {
        int t = stateTick;
        double cx = arena.cx();
        double cz = arena.cz();
        double fy = floorY();
        if (t == 1) {
            fx.sound(cx, fy, cz, Sound.BLOCK_BELL_RESONATE, 2.5f, 0.5f);
        }
        if (t <= 36 && t % 3 == 0) {
            int n = t / 3;
            arena.reveal(n, 6);
            fx.sound(cx, fy, cz, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.4f, (float) (0.5 + n * 0.12));
        }
        if (t == 40) {
            arena.revealAll(10);
            fx.sound(cx, fy, cz, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
        }
        if (t == 46) {
            fx.shakeAll(50, 3);
            fx.sound(cx, fy, cz, Sound.BLOCK_GRINDSTONE_USE, 2.5f, 0.5f);
            fx.sound(cx, fy, cz, Sound.AMBIENT_CAVE, 2f, 0.6f);
            rig.heightTarget(rig.spec().hubHeight());
            rig.pendulum().reel(rig.chainRest(), 0.12);
        }
        if (t > 46 && t < 130 && t % 12 == 0) {
            fx.sound(rig.hubX(), rig.hub().y, rig.hubZ(), Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.5f);
            fx.dust(cx, fy + 0.2, cz, floorBlock, 30, 2.5);
        }
        if (t >= 120 && t <= 140) {
            rig.needleLength(7.0 * Ease.outBack((t - 120) / 20.0));
            if (t == 120) {
                fx.sound(rig.hubX(), rig.hub().y + 4, rig.hubZ(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
            }
        }
        if (t >= 140 && t <= 150) {
            rig.lensScale(Ease.outBack((t - 140) / 10.0));
            rig.gazeSize(1.3 * Ease.smooth((t - 140) / 10.0));
            if (t == 140) {
                fx.sound(rig.lensPos().x, rig.lensPos().y, rig.lensPos().z, Sound.BLOCK_VAULT_OPEN_SHUTTER, 2f, 0.7f);
            }
        }
        int readStart = 156;
        int perPlayer = 14;
        if (t == readStart) {
            // Snapshot who it reads, so a leave mid-intro can never stall the timeline.
            introReaders.clear();
            for (Player p : party.online()) {
                if (introReaders.size() < 6) {
                    introReaders.add(p.getUniqueId());
                }
            }
        }
        int reads = introReaders.size();
        if (t >= readStart && t < readStart + reads * perPlayer && (t - readStart) % perPlayer == 0) {
            Player p = player(introReaders.get((t - readStart) / perPlayer));
            if (p != null) {
                rig.snapLook(head(p));
                rig.face(p.getLocation().getX(), p.getLocation().getZ());
                fx.soundIn(p, Sound.ITEM_SPYGLASS_USE, 1.2f, 0.9f);
            }
        }
        int end = readStart + reads * perPlayer + 10;
        if (t > readStart && t == end) {
            for (Player p : party.audience()) {
                fx.title(p, Lang.title(), Lang.subtitleIntro(p), 8, 40, 12);
            }
        }
        if (t > readStart && t >= end + 30) {
            for (Player p : party.audience()) {
                fx.subtitle(p, Lang.intro(p), 50);
            }
            beginFight();
        }
    }

    private final List<UUID> introReaders = new ArrayList<>();

    private void beginFight() {
        state = State.FIGHT;
        stateTick = 0;
        brain.start(clock, arena.cx(), arena.cz());
        int n = Math.max(1, party.size());
        double mul = Math.min(cfg.d("scaling.max-health-multiplier", 4.0),
                1.0 + cfg.d("scaling.per-extra-player", 0.55) * (n - 1));
        instance.applyCustomScale(n, mul, 1.0);
        nextMoveAt = clock + 30;
        rig.needleRest();
        music.play();
    }

    /* ================================================================== fight */

    private void tickFight() {
        BossPhase p = instance.getCurrentPhase();
        if (phase == 1 && p != null && "verdict".equalsIgnoreCase(p.getId())) {
            beginTransition();
            return;
        }
        if (party.size() == 0) {
            if (emptySince < 0) {
                emptySince = clock;
            }
            idle();
            if (clock - emptySince > 20L * cfg.i("arena.empty-reset-seconds", 45)) {
                plugin.getLogger().info("[Gnomon] Dial empty for too long - the Gnomon folds away (fight reset).");
                plugin.abort(this, "empty");
            }
            return;
        }
        emptySince = -1;
        if (attack != null) {
            boolean done = attack.tick();
            attack.t++;
            if (done) {
                endAttack();
                nextMoveAt = clock + cfg.gap(brain.posture());
            }
            return;
        }
        idle();
        if (clock >= nextMoveAt && (opening == Brain.Opening.NONE)) {
            decideAndStart();
        }
    }

    private void idle() {
        Player f = focus();
        if (f != null && clock >= lookHoldUntil) {
            rig.look(head(f));
            rig.face(f.getLocation().getX(), f.getLocation().getZ());
        }
        rig.beam(false);
        if (opening == Brain.Opening.NONE) {
            rig.gazeSize(1.3);
        }
        // Keep the body on the dial.
        double[] c = arena.clamp(rig.hubGoal().x, rig.hubGoal().z, 7);
        if (Geo.dist(c[0], c[1], rig.hubGoal().x, rig.hubGoal().z) > 0.1) {
            rig.moveTo(c[0], c[1]);
        }
    }

    private void decideAndStart() {
        Player f = focus();
        if (f == null) {
            return;
        }
        if (brain.focus() == null) {
            brain.forceFocus(f.getUniqueId());
        }
        double dist = Geo.dist(f.getLocation().getX(), f.getLocation().getZ(), rig.hubX(), rig.hubZ());
        int n = 0;
        int close = 0;
        int far = 0;
        for (Player m : party.online()) {
            n++;
            double dm = Geo.dist(m.getLocation().getX(), m.getLocation().getZ(), rig.hubX(), rig.hubZ());
            if (dm < 5.5) {
                close++;
            } else if (dm > 13) {
                far++;
            }
        }
        double closeF = n == 0 ? 0 : close / (double) n;
        double farF = n == 0 ? 0 : far / (double) n;
        Decision d = brain.decide(cfg.moves(), new Brain.Context(dist, closeF, farF, this::available));
        startDecision(d);
    }

    /** Starts a decision (also used by {@code /gnomon force}). */
    public void startDecision(Decision d) {
        if (attack != null) {
            endAttack();
        }
        Attack a = create(d);
        brain.commit(d);
        lastDecision = d;
        if (cfg.logDecisions()) {
            plugin.getLogger().info("[Gnomon] " + d.why());
        }
        attack = a;
        a.start();
    }

    private boolean available(MoveSpec spec) {
        switch (spec.id()) {
            case Moves.RECITAL -> {
                Profile p = brain.profile(brain.focus());
                return p != null && p.pathBetween(clock - 260, clock - 40).size() >= 5;
            }
            case Moves.INTERDICT -> {
                return blocks.size() < 240;
            }
            case Moves.PLUMB_DROP, Moves.FALSE_OPENING -> {
                return !rig.pendulum().pinned();
            }
            case Moves.HOUR_HAND -> {
                return arena.distance(rig.hubX(), rig.hubZ()) < arena.radius() - 5;
            }
            default -> {
                return true;
            }
        }
    }

    private Attack create(Decision d) {
        if (d.breath()) {
            return new Breath(this, d);
        }
        return switch (d.spec().id()) {
            case Moves.FEINT -> new Feint(this, d);
            case Moves.HOUR_HAND -> new HourHand(this, d);
            case Moves.PLUMB_DROP -> new PlumbDrop(this, d, false);
            case Moves.FALSE_OPENING -> new FalseOpening(this, d);
            case Moves.SURVEY_LINE -> new SurveyLine(this, d);
            case Moves.STRIDE -> new Stride(this, d);
            case Moves.INTERDICT -> new Interdict(this, d);
            case Moves.RECITAL -> new Recital(this, d);
            default -> new Stomp(this, d);
        };
    }

    /** Builds a decision for {@code /gnomon force <move>} (spends the focus's strongest exploitable read if any). */
    public Decision forced(String id) {
        UUID focus = brain.focus() != null ? brain.focus() : (focus() == null ? null : focus().getUniqueId());
        for (MoveSpec s : cfg.moves()) {
            if (s.id().equalsIgnoreCase(id)) {
                Read best = null;
                double conf = 0;
                for (Read r : Read.values()) {
                    if (s.readWeight(r) > 0 && brain.exploitable(focus, r) && brain.confidence(focus, r) > conf) {
                        best = r;
                        conf = brain.confidence(focus, r);
                    }
                }
                int sign = best == null ? 0 : brain.sign(focus, best);
                int sector = best == Read.CAMPER && brain.profile(focus) != null
                        ? brain.profile(focus).read(Read.CAMPER).sector() : -1;
                return new Decision(s, focus, best, sign, sector, 0, "forced " + s.id());
            }
        }
        if ("BREATH".equalsIgnoreCase(id)) {
            return new Decision(null, focus, null, 0, -1, 0, "forced breath");
        }
        return null;
    }

    private void endAttack() {
        if (attack != null) {
            Attack a = attack;
            attack = null;
            try {
                a.end();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "[Gnomon] move cleanup failed: " + a.id(), ex);
            }
        }
        exploitActive = false;
        rig.beam(false);
        rig.gait(true);
        rig.lean(0, 0);
        rig.crouch(0);
        if (state == State.FIGHT) {
            rig.rear(0);
        }
    }

    /* ================================================================== tells */

    private void tell(Tell t) {
        Player p = player(t.player());
        Vector3d lens = rig.lensPos();
        switch (t.kind()) {
            case NOTICED -> {
                if (p == null) {
                    return;
                }
                if (lensFree()) {
                    rig.snapLook(head(p));
                    lookHoldUntil = clock + 16;
                }
                fx.soundTo(p, lens.x, lens.y, lens.z, Sound.ITEM_SPYGLASS_USE, 1.4f, 0.8f);
                fx.sound(lens.x, lens.y, lens.z, Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.4f, 0.7f);
                fx.sound(lens.x, lens.y, lens.z, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.6f);
                ledger.flare(p.getUniqueId(), 50);
                if (t.firstTime()) {
                    fx.subtitle(p, Lang.noticed(p, t.read(), t.sign()), 50);
                }
            }
            case FORGOTTEN -> {
                if (p == null) {
                    return;
                }
                fx.soundTo(p, lens.x, lens.y, lens.z, Sound.ITEM_BOOK_PAGE_TURN, 1.2f, 1.3f);
                ledger.dim(p.getUniqueId());
                if (t.firstTime()) {
                    fx.subtitle(p, Lang.forgotten(p), 40);
                }
            }
            case OUTPLAYED -> {
                // The flinch: the eye jerks away, glass cracks, the heart sags. You earned this window.
                if (lensFree()) {
                    rig.snapLook(new Vector3d(lens.x, lens.y + 6, lens.z));
                    lookHoldUntil = clock + 12;
                }
                fx.sound(lens.x, lens.y, lens.z, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 2f, 0.6f);
                fx.sound(lens.x, lens.y, lens.z, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
                if (p != null && t.firstTime()) {
                    fx.subtitle(p, Lang.outplayed(p), 50);
                }
                stagger();
            }
            case POSTURE -> {
                rig.dip(0.3);
                Vector3d h = rig.hub();
                Sound s = switch (t.posture()) {
                    case OBSERVE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                    case PRESS -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
                    case DENY -> Sound.BLOCK_BEACON_POWER_SELECT;
                    case PREY -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
                };
                fx.sound(h.x, h.y, h.z, s, 2f, t.posture() == Posture.PREY ? 0.5f : 0.8f);
            }
            case FOCUS -> {
                if (p == null) {
                    return;
                }
                if (lensFree()) {
                    rig.snapLook(head(p));
                    lookHoldUntil = clock + 10;
                }
                fx.soundTo(p, lens.x, lens.y, lens.z, Sound.ITEM_SPYGLASS_USE, 1.2f, 1.0f);
                fx.actionbar(p, Lang.watching(p));
            }
        }
    }

    /**
     * The eye never lies: while a move is aiming, nothing else may move the lens. Tells then speak through sound,
     * the ledger and words only.
     */
    private boolean lensFree() {
        return attack == null || !attack.ownsLens();
    }

    /** Called by a move that spends a read: red iris, red gaze, a crimson mark where it expects you. */
    public void announceExploit(Decision d, double x, double z) {
        if (d == null || d.exploit() == null) {
            return;
        }
        exploitActive = true;
        Player p = player(d.target());
        Vector3d lens = rig.lensPos();
        if (p != null) {
            fx.soundTo(p, lens.x, lens.y, lens.z, Sound.ITEM_SPYGLASS_USE, 1.6f, 0.6f);
        }
        fx.sound(lens.x, lens.y, lens.z, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1.6f, 0.7f);
    }

    private void refreshIris() {
        if (state == State.DYING || state == State.DONE || state == State.CLOSED) {
            return;
        }
        Material iris;
        if (opening == Brain.Opening.TRUE || opening == Brain.Opening.STAGGER) {
            iris = Material.GRAY_CONCRETE;
        } else if (opening == Brain.Opening.FALSE || exploitActive) {
            iris = Material.RED_CONCRETE;
        } else {
            iris = switch (brain.posture()) {
                case OBSERVE -> Material.WHITE_CONCRETE;
                case PRESS -> Material.ORANGE_CONCRETE;
                case DENY -> Material.PURPLE_CONCRETE;
                case PREY -> Material.RED_CONCRETE;
            };
        }
        rig.iris(iris);
        rig.gazeColor(exploitActive || opening == Brain.Opening.FALSE ? Material.RED_STAINED_GLASS : Material.WHITE_STAINED_GLASS);
    }

    /* ================================================================== openings */

    public Brain.Opening opening() {
        return opening;
    }

    public double openingProgress() {
        if (opening == Brain.Opening.NONE) {
            return 0;
        }
        return Ease.clamp01((clock - openingStart) / (double) Math.max(1, openingEnd - openingStart));
    }

    /** The honest opening: heart embedded and glowing, lens dimmed and drooping. */
    public void openTrue(int ticks) {
        opening = Brain.Opening.TRUE;
        openingStart = clock;
        openingEnd = clock + ticks;
        rig.pendulum().pin(true);
        rig.bobGold(true, GOLD);
        rig.lensDim(true);
        Vector3d b = rig.bob();
        rig.look(new Vector3d(b.x, floorY(), b.z));
        lookHoldUntil = openingEnd;
        rig.gazeSize(0);
        goldMark = shapes.dot(b.x, b.z, 1.9, Shapes.Style.GOLD);
        fx.sound(b.x, b.y, b.z, Sound.BLOCK_BELL_RESONATE, 2f, 1.2f);
    }

    /** The lie: the same gold, but the lens stays bright and stares at {@code target}. */
    public void openFalse(UUID target, int ticks) {
        opening = Brain.Opening.FALSE;
        openingStart = clock;
        openingEnd = clock + ticks + 40;
        falseTarget = target;
        falseBit = false;
        rig.pendulum().pin(true);
        rig.bobGold(true, GOLD);
        Vector3d b = rig.bob();
        goldMark = shapes.dot(b.x, b.z, 1.9, Shapes.Style.GOLD);
        Player p = player(target);
        if (p != null) {
            rig.snapLook(head(p));
            lookHoldUntil = openingEnd;
        }
        fx.sound(b.x, b.y, b.z, Sound.BLOCK_BELL_RESONATE, 2f, 1.2f);
    }

    public boolean falseBit() {
        return falseBit;
    }

    public UUID falseTarget() {
        return falseTarget;
    }

    /** Out-thought: the heart sags into a short gold window. */
    public void stagger() {
        if (state != State.FIGHT || opening != Brain.Opening.NONE || (attack != null && attack.usesBob())) {
            return;
        }
        opening = Brain.Opening.STAGGER;
        openingStart = clock;
        int ticks = cfg.telegraph("stagger", 40);
        openingEnd = clock + ticks;
        Vector3d h = rig.hub();
        rig.crouch(1.6);
        rig.pendulum().drop(h.y - 0.8 - floorY() + 2, -0.2, () -> {
            Vector3d b = rig.bob();
            fx.sound(b.x, b.y, b.z, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
            fx.dust(b.x, floorY() + 0.1, b.z, floorBlock, 25, 0.8);
        });
        rig.bobGold(true, GOLD);
        rig.lensDim(true);
        nextMoveAt = Math.max(nextMoveAt, openingEnd + 8);
        fx.sound(h.x, h.y, h.z, Sound.BLOCK_BELL_RESONATE, 2f, 1.5f);
    }

    public void closeOpening() {
        if (opening == Brain.Opening.NONE) {
            return;
        }
        opening = Brain.Opening.NONE;
        falseTarget = null;
        rig.pendulum().pin(false);
        rig.pendulum().reel(rig.chainRest(), 0.16);
        rig.bobGold(false, null);
        rig.lensDim(false);
        rig.crouch(0);
        lookHoldUntil = 0;
        if (goldMark != null) {
            goldMark.remove();
            goldMark = null;
        }
        Vector3d b = rig.bob();
        fx.sound(b.x, b.y, b.z, Sound.ITEM_CROSSBOW_LOADING_END, 1.4f, 0.5f);
    }

    private void tickOpening() {
        if (opening == Brain.Opening.NONE) {
            return;
        }
        if (goldMark != null && opening != Brain.Opening.FALSE) {
            Vector3d b = rig.bob();
            goldMark.move(b.x, b.z, 2);
        }
        if (opening != Brain.Opening.FALSE && clock >= openingEnd) {
            closeOpening();
        }
        if (opening == Brain.Opening.FALSE && clock >= openingEnd) {
            closeOpening();
        }
    }

    /* ================================================================== damage both ways */

    /** A player hit the heart (via BossEngine). Returns the amount for the HP pool, or negative to cancel. */
    public double incoming(Player p, double amount) {
        if (state != State.FIGHT) {
            return -1;
        }
        // Only people on the dial fight it: no sniping the heart from outside the seal.
        if (!party.contains(p)) {
            return -1;
        }
        Vector3d bob = rig.bob();
        Location eye = p.getEyeLocation();
        double dist = new Vector3d(eye.getX(), eye.getY(), eye.getZ()).distance(bob);
        boolean melee = dist <= 5.5;
        double mult = 1.0;
        Brain.Opening o = opening;
        switch (o) {
            case TRUE -> mult = cfg.damage("opening-multiplier", 1.6);
            case STAGGER -> mult = cfg.damage("stagger-multiplier", 1.35);
            case FALSE -> {
                if (p.getUniqueId().equals(falseTarget)) {
                    falseBit = true;
                }
            }
            default -> {
            }
        }
        brain.onPlayerHit(p.getUniqueId(), melee, o, openingProgress(), amount * mult);
        // The heart answers the hit: recoil away from the striker.
        if (!rig.pendulum().pinned()) {
            Vector3d away = new Vector3d(bob).sub(eye.getX(), eye.getY(), eye.getZ());
            if (away.lengthSquared() > 1e-4) {
                away.normalize().mul(cfg.hitRecoil() * (melee ? 1.0 : 0.6));
                rig.pendulum().kick(away.x, Math.max(0, away.y) * 0.3, away.z);
            }
        }
        if (o == Brain.Opening.TRUE || o == Brain.Opening.STAGGER) {
            fx.sound(bob.x, bob.y, bob.z, Sound.BLOCK_BELL_USE, 1.1f, (float) (1.1 + Math.random() * 0.4));
            fx.particle(Particle.CRIT, bob.x, bob.y, bob.z, 10, 0.4, 0.3);
        } else {
            fx.sound(bob.x, bob.y, bob.z, Sound.BLOCK_CHAIN_HIT, 1.0f, 0.7f);
            fx.sound(bob.x, bob.y, bob.z, Sound.BLOCK_ANVIL_LAND, 0.2f, 1.9f);
        }
        return amount * mult;
    }

    /**
     * Applies a hit to every member inside {@code zone}. Ground waves can be jumped.
     *
     * @return players hit
     */
    public Set<UUID> strike(Zone zone, double power, String key, int iframeTicks, boolean groundWave, double knock) {
        Set<UUID> hit = new HashSet<>();
        for (Player p : party.online()) {
            Location l = p.getLocation();
            if (!zone.contains(l.getX(), l.getZ())) {
                continue;
            }
            if (Math.abs(l.getY() - floorY()) > 6) {
                continue;
            }
            if (groundWave && l.getY() > floorY() + cfg.jumpClearance()) {
                continue;
            }
            Vector kb = null;
            if (knock > 0) {
                double dx = l.getX() - zone.originX();
                double dz = l.getZ() - zone.originZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 0.2) {
                    double a = Math.random() * Math.PI * 2;
                    dx = Math.cos(a);
                    dz = Math.sin(a);
                    len = 1;
                }
                kb = new Vector(dx / len * knock, 0.35 + knock * 0.2, dz / len * knock);
            }
            if (hurt(p, power, key, iframeTicks, kb)) {
                hit.add(p.getUniqueId());
            }
        }
        return hit;
    }

    /** One hit on one player: Aetherion boss-damage formula, i-frames per move, tamed knockback, a camera kick. */
    public boolean hurt(Player p, double power, String key, int iframeTicks, Vector knock) {
        if (p == null || p.isDead() || !party.contains(p)) {
            return false;
        }
        GameMode m = p.getGameMode();
        if (m != GameMode.SURVIVAL && m != GameMode.ADVENTURE) {
            return false;
        }
        String k = p.getUniqueId() + ":" + key;
        Long until = iframes.get(k);
        if (until != null && clock < until) {
            return false;
        }
        iframes.put(k, clock + Math.max(1, iframeTicks));
        LivingEntity source = instance.getEntity();
        BossHits.hurt(p, source, instance.scaleDamage(power));
        if (knock != null) {
            double h = Math.min(0.95, Math.hypot(knock.getX(), knock.getZ()));
            double hx = knock.getX();
            double hz = knock.getZ();
            double len = Math.hypot(hx, hz);
            if (len > 1e-4) {
                hx = hx / len * h;
                hz = hz / len * h;
            }
            p.setVelocity(p.getVelocity().multiply(0.2).add(new Vector(hx, Math.min(0.5, knock.getY()), hz)));
        }
        fx.kick(p);
        return true;
    }

    /** Threat bookkeeping around a telegraph (the mind measures the dodge). */
    public void threat(String key, Zone zone, long impactTick, Decision d) {
        brain.beginThreat(key, zone, impactTick, d == null ? null : d.exploit(), d == null ? 0 : d.sign(),
                d == null || d.exploit() == null ? null : d.target());
    }

    public void moveThreat(String key, Zone zone, long impactTick) {
        brain.moveThreat(key, zone, impactTick);
    }

    public Set<UUID> resolve(String key, Set<UUID> hit) {
        return brain.resolveThreat(key, hit);
    }

    /** A heavy thing met the floor: sound layers, dust, cracks of light, shake, hit-stop when it connected. */
    public void impact(double x, double z, double strength, boolean connected) {
        double y = floorY();
        fx.sound(x, y, z, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, (float) (1.2 + strength), 0.6f);
        fx.sound(x, y, z, Sound.BLOCK_ANVIL_LAND, (float) (0.6 + strength * 0.5), 0.5f);
        if (strength > 0.8) {
            fx.sound(x, y, z, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
        }
        fx.dust(x, y + 0.15, z, floorBlock, (int) (30 + 50 * strength), 0.6 + strength);
        fx.ring(Particle.CLOUD, x, y + 0.1, z, 1.2 + strength * 1.5, 14);
        fx.shakeNear(x, z, 8 + strength * 10, (int) (8 + strength * 14));
        rig.dip(0.25 + strength * 0.35);
        if (connected) {
            rig.freeze(3);
        }
    }

    /** A gait step began: a small shadow marks where the foot will land (only while fighting). */
    private void footLifted(Vector3d to, int ticks) {
        if (state != State.FIGHT) {
            return;
        }
        Shapes.Circle s = shapes.dot(to.x, to.z, 1.5, Shapes.Style.FORMING);
        s.grow(0.1, 0);
        s.grow(1.0, Math.max(1, ticks));
        final int life = ticks + 3;
        addHazard(new Hazard() {
            private int t;

            @Override
            public boolean tick() {
                t++;
                if (t == Math.max(1, ticks - 4)) {
                    s.style(Shapes.Style.COMMITTED);
                }
                return t >= life;
            }

            @Override
            public void end() {
                s.remove();
            }
        });
    }

    private final Set<String> onceKeys = new HashSet<>();

    /** True the first time a key is seen in this fight (one-shot lines). */
    public boolean once(String key) {
        return onceKeys.add(key);
    }

    /** Every gait footstep: a little shadow beforehand (drawn by the step itself), a small hazard on landing. */
    private void footPlanted(Vector3d foot) {
        if (state != State.FIGHT) {
            fx.sound(foot.x, floorY(), foot.z, Sound.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.5f);
            return;
        }
        fx.sound(foot.x, floorY(), foot.z, Sound.ENTITY_IRON_GOLEM_STEP, 1.4f, 0.45f);
        fx.sound(foot.x, floorY(), foot.z, Sound.BLOCK_DEEPSLATE_STEP, 1.2f, 0.6f);
        fx.dust(foot.x, floorY() + 0.1, foot.z, floorBlock, 14, 0.4);
        fx.shakeNear(foot.x, foot.z, 5, 4);
        strike(new Zone.Circle(foot.x, foot.z, 1.5), cfg.damage("stride-foot", 30), "foot", 12, false, 0.5);
    }

    /* ================================================================== hazards */

    public void addHazard(Hazard h) {
        hazards.add(h);
    }

    private void tickHazards() {
        for (Iterator<Hazard> it = hazards.iterator(); it.hasNext(); ) {
            Hazard h = it.next();
            boolean done;
            try {
                done = h.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "[Gnomon] hazard failed", ex);
                done = true;
            }
            if (done) {
                h.end();
                it.remove();
            }
        }
    }

    private void endHazards() {
        for (Hazard h : new ArrayList<>(hazards)) {
            try {
                h.end();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "[Gnomon] hazard cleanup failed", ex);
            }
        }
        hazards.clear();
    }

    /* ================================================================== transition: the Ledger opens */

    private void beginTransition() {
        endAttack();
        closeOpening();
        endHazards();
        phase = 2;
        brain.setPhase(2);
        state = State.TRANSITION;
        stateTick = 0;
    }

    private void tickTransition() {
        int t = stateTick;
        Vector3d h = rig.hub();
        double cx = arena.cx();
        double cz = arena.cz();
        if (t == 1) {
            rig.rear(4.0);
            rig.look(new Vector3d(h.x, h.y + 10, h.z));
            lookHoldUntil = clock + 30;
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_BELL_RESONATE, 3f, 0.4f);
            fx.sound(h.x, h.y, h.z, Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.6f);
            for (Player p : party.audience()) {
                fx.subtitle(p, Lang.verdict(p), 60);
            }
        }
        if (t >= 12 && t <= 60 && t % 2 == 0) {
            int n = ledger.inkSome(6);
            if (n > 0) {
                fx.sound(h.x, h.y, h.z, Sound.ITEM_BOOK_PAGE_TURN, 1.6f, (float) (0.6 + Math.random() * 0.5));
            }
            if (t % 8 == 0) {
                fx.sound(h.x, h.y, h.z, Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.6f, 0.6f);
            }
        }
        if (t == 40) {
            rig.look(new Vector3d(cx, floorY(), cz));
        }
        if (t == 55) {
            arena.turn(Math.toRadians(30), 30);
            fx.sound(cx, floorY(), cz, Sound.BLOCK_GRINDSTONE_USE, 2.5f, 0.4f);
            fx.shakeAll(30, 4);
        }
        if (t == 65) {
            for (Player p : party.online()) {
                fx.darkness(p, 45);
            }
            rig.needleLength(9.5);
            fx.sound(h.x, h.y + 5, h.z, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        }
        if (t == 90) {
            rig.rear(1.0);
            for (int i = 0; i < 3; i++) {
                Vector3d f = rig.leg(i).foot();
                rig.holdLeg(i, new Vector3d(f.x, floorY() + 5.5, f.z));
            }
            rig.gait(false);
            fx.sound(h.x, h.y, h.z, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 2f, 0.4f);
        }
        if (t == 108) {
            double stance = rig.spec().stance() + 1.5;
            for (int i = 0; i < 3; i++) {
                double a = rig.yaw() + Math.toRadians(60 + 120 * i);
                Vector3d target = new Vector3d(rig.hubX() + Math.cos(a) * stance, floorY(), rig.hubZ() + Math.sin(a) * stance);
                final boolean first = i == 0;
                rig.slamLeg(i, target, 4, landed -> {
                    if (first) {
                        impact(rig.hubX(), rig.hubZ(), 1.4, false);
                        fx.ring(Particle.SONIC_BOOM, rig.hubX(), floorY() + 0.5, rig.hubZ(), 6, 10);
                        for (Player p : party.online()) {
                            Location l = p.getLocation();
                            double dx = l.getX() - rig.hubX();
                            double dz = l.getZ() - rig.hubZ();
                            double len = Math.max(0.5, Math.hypot(dx, dz));
                            if (len < 10) {
                                p.setVelocity(new Vector(dx / len * 0.7, 0.45, dz / len * 0.7));
                            }
                        }
                    }
                });
            }
        }
        if (t == 130) {
            rig.gait(true);
            rig.rear(0);
            state = State.FIGHT;
            stateTick = 0;
            nextMoveAt = clock + 10;
        }
    }

    /* ================================================================== death */

    /** HP reached zero. */
    public boolean beginDeath() {
        if (state == State.DYING || state == State.DONE || state == State.CLOSED) {
            return true;
        }
        music.stop();
        endAttack();
        closeOpening();
        endHazards();
        party.close();
        double sum = 0;
        int n = 0;
        for (Profile p : brain.present()) {
            sum += p.maxConfidence();
            n++;
        }
        deathQuality = n == 0 ? 0 : sum / n;
        state = State.DYING;
        stateTick = 0;
        return true;
    }

    private boolean tickDeath() {
        int t = stateTick;
        Vector3d h = rig.hub();
        if (t == 1) {
            rig.freeze(6);
            rig.lensCracked();
            rig.beam(false);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_GLASS_BREAK, 2.5f, 0.5f);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 2.5f, 0.5f);
            for (Player p : party.audience()) {
                fx.subtitle(p, deathQuality >= 0.45 ? Lang.deathKnown(p) : Lang.deathUnknown(p), 70);
            }
            rig.gait(false);
        }
        if (t == 12) {
            rig.limp(0, 3.5, 16);
            rig.rear(-3);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_CHAIN_BREAK, 2f, 0.5f);
        }
        if (t == 32) {
            rig.limp(1, 3.5, 16);
            rig.rear(-6);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.45f);
        }
        if (t == 52) {
            rig.limp(2, 3.5, 16);
            rig.heightTarget(3.2);
            fx.sound(h.x, h.y, h.z, Sound.BLOCK_ANVIL_LAND, 2f, 0.4f);
            fx.shakeAll(20, 3);
        }
        if (t == 64) {
            rig.needle(Math.toRadians(80), rig.yaw() + Math.PI * 0.7);
            fx.sound(h.x, h.y + 4, h.z, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
        }
        if (t == 74) {
            rig.pendulum().drop(h.y + 4 - floorY(), -0.3, () -> {
                Vector3d b = rig.bob();
                fx.sound(b.x, b.y, b.z, Sound.BLOCK_BELL_USE, 2.5f, 0.5f);
                fx.sound(b.x, b.y, b.z, Sound.BLOCK_BELL_RESONATE, 2.5f, 0.5f);
                impact(b.x, b.z, 1.0, false);
            });
        }
        if (t == 90) {
            ledger.ascend(60);
            arena.fade(60);
            fx.sound(arena.cx(), floorY(), arena.cz(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2.5f, 1.2f);
        }
        if (t > 90 && t < 150 && t % 6 == 0) {
            fx.sound(arena.cx(), floorY() + 3, arena.cz(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f,
                    (float) (0.8 + (t - 90) / 60.0));
        }
        if (t == 140) {
            rig.vanish(18);
        }
        return t >= 160;
    }

    /* ================================================================== hitbox */

    private void placeHitbox() {
        LivingEntity body = instance.getEntity();
        if (body == null || !body.isValid()) {
            return;
        }
        if (body instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (!body.isInvisible()) {
            body.setInvisible(true);
        }
        if (body.hasGravity()) {
            body.setGravity(false);
        }
        body.setFireTicks(0);
        body.setFallDistance(0f);
        boolean inv = blocksDamage();
        if (body.isInvulnerable() != inv) {
            body.setInvulnerable(inv);
        }
        // Keep projectile hits on the swinging heart (arrows need collidable=true).
        if (!body.isCollidable()) {
            body.setCollidable(true);
        }
        Vector3d b = rig.bob();
        Location at = new Location(world, b.x, b.y - 0.8, b.z);
        Location cur = body.getLocation();
        if (cur.getWorld() != world || cur.distanceSquared(at) > 0.0025) {
            instance.runInternalTeleport(() -> body.teleport(at));
        }
        body.setVelocity(new Vector());
    }

    /* ================================================================== close */

    /** Removes everything and gives every player their sky back. Idempotent. */
    public void close(String reason) {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        music.stop();
        endAttack();
        endHazards();
        try {
            shapes.clearAll();
            ledger.clear();
            parts.removeAll();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "[Gnomon] display cleanup failed", ex);
        }
        try {
            blocks.restoreAll();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "[Gnomon] block restore failed", ex);
        }
        party.releaseAll();
        plugin.getLogger().info("[Gnomon] Encounter closed (" + reason + ").");
    }

    /* ================================================================== debug */

    public String status() {
        Player f = focus();
        return String.format(Locale.ROOT, "%s phase=%d posture=%s focus=%s move=%s opening=%s parts=%d hazards=%d temp=%d ledger=%d",
                state, phase, brain.posture(), f == null ? "-" : f.getName(),
                attack == null ? "-" : attack.id(), opening, parts.count(), hazards.size(), blocks.size(), ledger.size());
    }

    public String hud() {
        Player f = focus();
        String reads = f == null ? "-" : brain.summary(f.getUniqueId());
        String last = lastDecision == null ? "-" : lastDecision.id()
                + (lastDecision.exploit() != null ? "§c[" + lastDecision.exploit() + "]" : "");
        return "§8[§f" + brain.posture() + "§8] §7" + (f == null ? "-" : f.getName()) + " §8| §f" + reads + " §8| §7" + last
                + (brain.teaching() ? " §8(teaching)" : "");
    }

    public List<String> dump() {
        List<String> out = new ArrayList<>();
        out.add("§f" + status());
        for (Profile p : brain.profiles()) {
            out.add("§7" + p.name() + (p.present() ? "" : " §8(gone)") + "§8: §f" + brain.summary(p.id()));
        }
        out.add("§8-- mind log --");
        for (String line : brain.log()) {
            out.add("§8" + line);
        }
        return out;
    }

    public boolean setHealthPercent(double percent) {
        if (state != State.FIGHT) {
            return false;
        }
        instance.setCombatHealthPercent(percent);
        return true;
    }

    /** Lets an admin cut the intro short. */
    public void skipIntro() {
        if (state == State.INTRO) {
            rig.placeHub(arena.cx(), floorY() + rig.spec().hubHeight(), arena.cz());
            rig.heightTarget(rig.spec().hubHeight());
            rig.lensScale(1);
            rig.gazeSize(1.3);
            rig.needleLength(7);
            rig.pendulum().reel(rig.chainRest(), 0.5);
            arena.revealAll(0);
            beginFight();
        }
    }
}
