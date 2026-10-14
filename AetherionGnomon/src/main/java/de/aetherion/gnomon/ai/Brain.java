package de.aetherion.gnomon.ai;

import de.aetherion.gnomon.geom.Zone;
import de.aetherion.gnomon.math.Geo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Gnomon's mind. Bukkit-free on purpose: it can be driven by a simulation, and every decision is explainable.
 *
 * <pre>
 * observe() every 2 ticks ─▶ evidence (decaying) ─▶ evaluate() every 10 ticks: reads, posture, focus ─▶ tells
 * beginThreat/resolveThreat around every telegraph ─▶ dodge side + timing + exploit outcome (was it baited?)
 * decide() between moves ─▶ utility over authored moves: learned reads × weights + posture + situation − repetition
 * </pre>
 */
public final class Brain {

    /** What the heart looked like when a player hit it. */
    public enum Opening {
        NONE, TRUE, FALSE, STAGGER
    }

    /** Situation the encounter knows and the mind should weigh. */
    public record Context(double focusDistance, double closeFraction, double farFraction,
                          Predicate<MoveSpec> available) {
    }

    private static final class Threat {
        final String key;
        Zone zone;
        final long start;
        long impact;
        final Read exploit;
        final int sign;
        final UUID target;
        final double hubX;
        final double hubZ;
        final Map<UUID, Tracked> tracked = new HashMap<>();

        Threat(String key, Zone zone, long start, long impact, Read exploit, int sign, UUID target, double hubX, double hubZ) {
            this.key = key;
            this.zone = zone;
            this.start = start;
            this.impact = impact;
            this.exploit = exploit;
            this.sign = sign;
            this.target = target;
            this.hubX = hubX;
            this.hubZ = hubZ;
        }
    }

    private static final class Tracked {
        final double sx;
        final double sz;
        final boolean wasInside;
        long exitTick;

        Tracked(double sx, double sz, boolean wasInside) {
            this.sx = sx;
            this.sz = sz;
            this.wasInside = wasInside;
            this.exitTick = wasInside ? -1 : Long.MIN_VALUE;
        }
    }

    private final BrainConfig cfg;
    private final Random rng;
    private final Map<UUID, Profile> profiles = new LinkedHashMap<>();
    private final List<Tell> tells = new ArrayList<>();
    private final Map<String, Threat> threats = new HashMap<>();
    private final Deque<MoveSpec> recent = new ArrayDeque<>();
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final Set<String> announcedOutplays = new HashSet<>();

    private long tick;
    private int phase = 1;
    private long teachUntil;
    private Posture posture = Posture.OBSERVE;
    private long postureSince;
    private UUID focus;
    private long focusSince;
    private int movesSinceBreath;
    private double hubX;
    private double hubZ;
    private double arenaX;
    private double arenaZ;

    public Brain(BrainConfig cfg) {
        this.cfg = cfg;
        this.rng = cfg.seed() == 0L ? new Random() : new Random(cfg.seed());
    }

    public BrainConfig config() {
        return cfg;
    }

    /* ================================================================== lifecycle */

    public void start(long now, double arenaX, double arenaZ) {
        this.tick = now;
        this.arenaX = arenaX;
        this.arenaZ = arenaZ;
        this.teachUntil = now + Math.round(cfg.teachSeconds() * 20);
        this.posture = Posture.OBSERVE;
        this.postureSince = now;
    }

    /** Phase 2: no more teaching, memory gets sticky, the mood may flip at once. */
    public void setPhase(int phase) {
        this.phase = phase;
        if (phase >= 2) {
            teachUntil = Math.min(teachUntil, tick);
            postureSince = tick - Math.round(cfg.postureMinSeconds() * 20) - 1;
        }
    }

    public int phase() {
        return phase;
    }

    public boolean teaching() {
        return phase == 1 && tick < teachUntil;
    }

    public long tick() {
        return tick;
    }

    public void join(UUID id, String name) {
        Profile p = profiles.get(id);
        if (p == null) {
            Profile created = new Profile(id, name);
            created.lastFocused = tick;
            profiles.put(id, created);
        } else {
            p.present = true;
        }
    }

    public void leave(UUID id) {
        Profile p = profiles.get(id);
        if (p != null) {
            p.present = false;
        }
        if (id.equals(focus)) {
            focus = null;
        }
    }

    public Profile profile(UUID id) {
        return id == null ? null : profiles.get(id);
    }

    public Collection<Profile> profiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public List<Profile> present() {
        List<Profile> out = new ArrayList<>();
        for (Profile p : profiles.values()) {
            if (p.present) {
                out.add(p);
            }
        }
        return out;
    }

    public Posture posture() {
        return posture;
    }

    public UUID focus() {
        return focus;
    }

    /** Drains the tells produced since the last call. */
    public List<Tell> drainTells() {
        if (tells.isEmpty()) {
            return List.of();
        }
        List<Tell> out = new ArrayList<>(tells);
        tells.clear();
        return out;
    }

    /* ================================================================== observation */

    public void tick(long now) {
        this.tick = now;
        if (now % 10 == 0) {
            evaluate();
        }
    }

    public void setHub(double x, double z) {
        hubX = x;
        hubZ = z;
    }

    public void hp(UUID id, double fraction) {
        Profile p = profiles.get(id);
        if (p != null) {
            p.hpFraction = Math.max(0, Math.min(1, fraction));
        }
    }

    /** One position sample (call every 2 ticks per participant). */
    public void observe(UUID id, double x, double y, double z, boolean grounded) {
        Profile p = profiles.get(id);
        if (p == null || !p.present) {
            return;
        }
        double hl = cfg.halfLifeTicks(phase);
        long dt = p.lastSample < 0 ? 0 : Math.min(10, tick - p.lastSample);
        double seconds = dt / 20.0;
        double r = Geo.dist(x, z, hubX, hubZ);
        double a = Geo.angle(x - hubX, z - hubZ);
        if (dt > 0) {
            // Orbit: tangential speed around the hub.
            if (!Double.isNaN(p.hubAngle) && r > 2.5 && r < 24) {
                double dA = Geo.wrap(a - p.hubAngle);
                double tangential = Math.abs(dA) * r / dt;
                if (tangential >= 0.05) {
                    (dA > 0 ? p.orbitCw : p.orbitCcw).add(seconds, tick, hl);
                }
            }
            // Range bands.
            if (r < cfg.closeRadius()) {
                p.timeClose.add(seconds, tick, hl);
            } else if (r > cfg.farRadius()) {
                p.timeFar.add(seconds, tick, hl);
            } else {
                p.timeMid.add(seconds, tick, hl);
            }
            // Dial slices (around the arena center, not the hub: "your corner" is a place).
            double ra = Geo.dist(x, z, arenaX, arenaZ);
            if (ra > 4.0) {
                double sa = Geo.angle(x - arenaX, z - arenaZ);
                int sector = sectorOf(sa);
                p.sectors[sector].add(seconds, tick, hl);
            }
        }
        p.x = x;
        p.y = y;
        p.z = z;
        p.grounded = grounded;
        p.hubAngle = a;
        p.lastSample = tick;

        // Route memory.
        if (tick - p.lastPathTick >= cfg.pathSampleTicks()) {
            boolean moved = Double.isNaN(p.lastPathX)
                    || Geo.dist(x, z, p.lastPathX, p.lastPathZ) >= cfg.pathMinStep();
            if (moved) {
                p.path.addLast(new Profile.PathPoint(x, z, tick));
                while (p.path.size() > 400) {
                    p.path.removeFirst();
                }
                p.lastPathX = x;
                p.lastPathZ = z;
            }
            p.lastPathTick = tick;
        }

        // Threat exits.
        for (Threat t : threats.values()) {
            Tracked tr = t.tracked.get(id);
            if (tr != null && tr.wasInside && tr.exitTick < 0 && !t.zone.contains(x, z)) {
                tr.exitTick = tick;
            }
        }
    }

    public static int sectorOf(double mathAngle) {
        double a = mathAngle < 0 ? mathAngle + Geo.TAU : mathAngle;
        int s = (int) Math.floor(a / (Geo.TAU / Profile.SECTORS));
        return Math.max(0, Math.min(Profile.SECTORS - 1, s));
    }

    public static double sectorCenter(int sector) {
        return (sector + 0.5) * (Geo.TAU / Profile.SECTORS);
    }

    /** A player's hit landed on the heart. */
    public void onPlayerHit(UUID id, boolean melee, Opening opening, double openingProgress, double damage) {
        Profile p = profiles.get(id);
        if (p == null) {
            return;
        }
        double hl = cfg.halfLifeTicks(phase);
        p.damageDealt.add(damage, tick, hl);
        switch (opening) {
            case TRUE -> {
                if (openingProgress >= 0.66) {
                    p.greedHits.add(1, tick, hl);
                } else {
                    p.cleanHits.add(1, tick, hl);
                }
            }
            case FALSE -> p.greedHits.add(1.5, tick, hl);
            default -> {
            }
        }
    }

    /* ================================================================== threats */

    /**
     * A telegraph appeared. Players inside it (and the exploit target) are watched until it resolves.
     */
    public void beginThreat(String key, Zone zone, long impactTick, Read exploit, int sign, UUID exploitTarget) {
        Threat t = new Threat(key, zone, tick, Math.max(tick + 1, impactTick), exploit, sign, exploitTarget, hubX, hubZ);
        for (Profile p : profiles.values()) {
            if (!p.present || p.lastSample < 0) {
                continue;
            }
            boolean inside = zone.contains(p.x, p.z);
            boolean near = Geo.dist(p.x, p.z, zone.originX(), zone.originZ()) <= 4.0;
            if (inside || near || p.id.equals(exploitTarget)) {
                t.tracked.put(p.id, new Tracked(p.x, p.z, inside));
            }
        }
        threats.put(key, t);
    }

    /** The shape moved while it was still tracking (before commit). */
    public void moveThreat(String key, Zone zone, long impactTick) {
        Threat t = threats.get(key);
        if (t != null) {
            t.zone = zone;
            t.impact = Math.max(t.start + 1, impactTick);
        }
    }

    /**
     * The telegraph landed. Measures how each watched player dodged and whether an exploit paid.
     *
     * @return players who out-thought an exploit (the encounter staggers the Gnomon for them)
     */
    public Set<UUID> resolveThreat(String key, Set<UUID> hit) {
        Threat t = threats.remove(key);
        if (t == null) {
            return Set.of();
        }
        double hl = cfg.halfLifeTicks(phase);
        Set<UUID> outplayed = new HashSet<>();
        for (Map.Entry<UUID, Tracked> e : t.tracked.entrySet()) {
            Profile p = profiles.get(e.getKey());
            if (p == null || !p.present) {
                continue;
            }
            Tracked tr = e.getValue();
            boolean wasHit = hit.contains(p.id);
            double dx = p.x - tr.sx;
            double dz = p.z - tr.sz;
            double moved = Math.sqrt(dx * dx + dz * dz);
            // Left / right is the player's own frame: they face the Gnomon. (A dodge sideways from the hub is
            // also tangential to it, so SIDE and ORBIT correlate - SIDE only counts reactions under a threat.)
            double fx = t.hubX - tr.sx;
            double fz = t.hubZ - tr.sz;
            boolean framed = fx * fx + fz * fz >= 2.25;
            double lateral = framed ? Geo.lateral(fx, fz, dx, dz) : 0;
            boolean measured = framed && (tr.wasInside || p.id.equals(t.target));
            if (!wasHit && measured && moved >= 1.2 && Math.abs(lateral) >= 0.8) {
                (lateral < 0 ? p.dodgeLeft : p.dodgeRight).add(1, tick, hl);
            }
            if (!wasHit && tr.wasInside && tr.exitTick >= 0) {
                double frac = (tr.exitTick - t.start) / (double) Math.max(1, t.impact - t.start);
                if (frac < 0.45) {
                    p.early.add(1, tick, hl);
                } else if (frac > 0.78) {
                    p.late.add(1, tick, hl);
                } else {
                    p.onTime.add(1, tick, hl);
                }
            }
            if (wasHit) {
                p.hurtTaken.add(1, tick, hl);
            }
            if (t.exploit != null && p.id.equals(t.target)) {
                p.exploitTries.add(1, tick, hl);
                if (wasHit) {
                    p.exploitLanded.add(1, tick, hl);
                } else if (isOutplay(t, tr, p, lateral)) {
                    punishRead(p, t.exploit, t.sign);
                    outplayed.add(p.id);
                    String k = p.id + ":" + t.exploit;
                    tells.add(Tell.outplayed(p.id, t.exploit, t.sign, announcedOutplays.add(k)));
                    log("OUTPLAYED by " + p.name + " on " + t.exploit + sgn(t.exploit, t.sign));
                }
            }
        }
        return outplayed;
    }

    public void cancelThreat(String key) {
        threats.remove(key);
    }

    private boolean isOutplay(Threat t, Tracked tr, Profile p, double lateral) {
        return switch (t.exploit) {
            case SIDE -> Math.signum(lateral) == -t.sign && Math.abs(lateral) >= 1.0;
            case ORBIT -> {
                double a0 = Geo.angle(tr.sx - t.hubX, tr.sz - t.hubZ);
                double a1 = Geo.angle(p.x - t.hubX, p.z - t.hubZ);
                double dA = Geo.wrap(a1 - a0);
                double r = Geo.dist(p.x, p.z, t.hubX, t.hubZ);
                yield Math.signum(dA) == -t.sign && Math.abs(dA) * r >= 1.5;
            }
            default -> false;
        };
    }

    /** The heart faked an opening at {@code target}. Did they bite? */
    public boolean resolveFalseOpening(UUID target, boolean bit) {
        Profile p = profiles.get(target);
        if (p == null) {
            return false;
        }
        double hl = cfg.halfLifeTicks(phase);
        p.exploitTries.add(1, tick, hl);
        if (bit) {
            p.exploitLanded.add(1, tick, hl);
            return false;
        }
        punishRead(p, Read.GREEDY, 0);
        String k = p.id + ":" + Read.GREEDY;
        tells.add(Tell.outplayed(p.id, Read.GREEDY, 0, announcedOutplays.add(k)));
        log("OUTPLAYED by " + p.name + " on GREEDY");
        return true;
    }

    private void punishRead(Profile p, Read read, int sign) {
        double f = cfg.outplayedForgetting();
        switch (read) {
            case SIDE -> (sign < 0 ? p.dodgeLeft : p.dodgeRight).scale(f);
            case ORBIT -> (sign > 0 ? p.orbitCw : p.orbitCcw).scale(f);
            case GREEDY -> p.greedHits.scale(f);
            default -> {
            }
        }
    }

    /* ================================================================== evaluation */

    /** Recomputes reads, posture and focus. Called every 10 ticks by {@link #tick(long)}; public for tests. */
    public void evaluate() {
        double hl = cfg.halfLifeTicks(phase);
        for (Profile p : profiles.values()) {
            if (!p.present) {
                continue;
            }
            updateReads(p, hl);
        }
        updatePosture();
        updateFocus(hl);
    }

    private void updateReads(Profile p, double hl) {
        // SIDE
        double left = p.dodgeLeft.get(tick, hl);
        double right = p.dodgeRight.get(tick, hl);
        double n = left + right;
        double bias = n > 1e-6 ? (right - left) / n : 0;
        set(p, Read.SIDE, Math.abs(bias) * countFactor(n, cfg.minDodges()), bias >= 0 ? 1 : -1, -1);

        // ORBIT
        double cw = p.orbitCw.get(tick, hl);
        double ccw = p.orbitCcw.get(tick, hl);
        double on = cw + ccw;
        double ob = on > 1e-6 ? (cw - ccw) / on : 0;
        set(p, Read.ORBIT, Math.abs(ob) * clamp01(on / cfg.minOrbitSeconds()), ob >= 0 ? 1 : -1, -1);

        // RANGE
        double close = p.timeClose.get(tick, hl);
        double mid = p.timeMid.get(tick, hl);
        double far = p.timeFar.get(tick, hl);
        double total = close + mid + far;
        double evidence = clamp01(total / cfg.minRangeSeconds());
        double fClose = total > 1e-6 ? close / total : 0;
        double fFar = total > 1e-6 ? far / total : 0;
        set(p, Read.HUGGER, clamp01((fClose - 0.3) / 0.4) * evidence, 0, -1);
        set(p, Read.KITER, clamp01((fFar - 0.3) / 0.4) * evidence, 0, -1);

        // GREEDY
        double greed = p.greedHits.get(tick, hl);
        double clean = p.cleanHits.get(tick, hl);
        double gn = greed + clean;
        double gb = gn > 1e-6 ? greed / gn : 0;
        set(p, Read.GREEDY, clamp01((gb - 0.2) / 0.5) * clamp01(gn / cfg.minOpeningHits()), 0, -1);

        // TIMING
        double early = p.early.get(tick, hl);
        double late = p.late.get(tick, hl);
        double onTime = p.onTime.get(tick, hl);
        double tn = early + late + onTime;
        double tb = tn > 1e-6 ? (late - early) / tn : 0;
        set(p, Read.TIMING, Math.abs(tb) * countFactor(tn, cfg.minDodges()), tb >= 0 ? 1 : -1, -1);

        // CAMPER
        double sum = 0;
        double max = 0;
        int best = -1;
        for (int i = 0; i < Profile.SECTORS; i++) {
            double v = p.sectors[i].get(tick, hl);
            sum += v;
            if (v > max) {
                max = v;
                best = i;
            }
        }
        double frac = sum > 1e-6 ? max / sum : 0;
        // Camping needs real time in the slice: nothing below 40% of min-sector-seconds, full trust at 100%.
        double campEvidence = clamp01((sum - 0.4 * cfg.minSectorSeconds()) / (0.6 * cfg.minSectorSeconds()));
        set(p, Read.CAMPER, clamp01((frac - 0.3) / 0.4) * campEvidence, 0, best);
    }

    private void set(Profile p, Read read, double confidence, int sign, int sector) {
        Profile.ReadState s = p.reads.get(read);
        s.confidence = confidence;
        if (read.signed()) {
            if (s.noticed && sign != s.sign && confidence >= cfg.forget()) {
                // Bias flipped without passing through "forgotten": treat as forgotten first.
                s.noticed = false;
                s.exploitable = false;
                tells.add(Tell.forgotten(p.id, read, !s.announcedForget));
                s.announcedForget = true;
            }
            s.sign = sign;
        }
        if (sector >= 0) {
            s.sector = sector;
        }
        if (!s.noticed && confidence >= cfg.notice()) {
            s.noticed = true;
            boolean first = s.announcedSigns.add(read.signed() ? sign : 0);
            tells.add(Tell.noticed(p.id, read, s.sign, first));
            log("NOTICED " + p.name + " " + read + sgn(read, s.sign) + " " + pct(confidence));
        } else if (s.noticed && confidence < cfg.forget()) {
            s.noticed = false;
            tells.add(Tell.forgotten(p.id, read, !s.announcedForget));
            s.announcedForget = true;
            log("FORGOT " + p.name + " " + read);
        }
        s.exploitable = s.noticed && confidence >= cfg.exploit() && !teaching();
    }

    private void updatePosture() {
        Posture want;
        if (teaching()) {
            want = Posture.OBSERVE;
        } else {
            Profile f = profile(focus);
            want = Posture.PRESS;
            if (f != null && f.present) {
                double hl = cfg.halfLifeTicks(phase);
                double tries = f.exploitTries.get(tick, hl);
                double landed = f.exploitLanded.get(tick, hl);
                double success = tries < 1.5 ? 1.0 : landed / tries;
                Read prey = bestExploitable(f, true);
                Read zone = bestZoneRead(f);
                if (prey != null && (success >= 0.3 || phase >= 2)) {
                    want = Posture.PREY;
                } else if (zone != null) {
                    want = Posture.DENY;
                }
            } else if (partyOrbitShare() >= 0.5) {
                want = Posture.DENY;
            }
        }
        if (want != posture) {
            long held = tick - postureSince;
            boolean leavingTeach = posture == Posture.OBSERVE && !teaching();
            if (leavingTeach || held >= Math.round(cfg.postureMinSeconds() * 20)) {
                posture = want;
                postureSince = tick;
                tells.add(Tell.posture(want));
                log("POSTURE " + want);
            }
        }
    }

    private Read bestExploitable(Profile p, boolean baitableOnly) {
        Read best = null;
        double conf = 0;
        for (Map.Entry<Read, Profile.ReadState> e : p.reads.entrySet()) {
            Profile.ReadState s = e.getValue();
            if (!s.exploitable) {
                continue;
            }
            if (baitableOnly && !e.getKey().baitable() && e.getKey() != Read.TIMING) {
                continue;
            }
            if (s.confidence > conf) {
                conf = s.confidence;
                best = e.getKey();
            }
        }
        return best;
    }

    private Read bestZoneRead(Profile p) {
        Read best = null;
        double conf = 0;
        for (Read r : new Read[]{Read.ORBIT, Read.CAMPER, Read.KITER}) {
            Profile.ReadState s = p.reads.get(r);
            if (s.noticed && s.confidence > conf) {
                conf = s.confidence;
                best = r;
            }
        }
        return best;
    }

    private double partyOrbitShare() {
        int n = 0;
        int orbiting = 0;
        for (Profile p : profiles.values()) {
            if (!p.present) {
                continue;
            }
            n++;
            if (p.reads.get(Read.ORBIT).noticed) {
                orbiting++;
            }
        }
        return n == 0 ? 0 : orbiting / (double) n;
    }

    private void updateFocus(double hl) {
        List<Profile> live = present();
        if (live.isEmpty()) {
            focus = null;
            return;
        }
        double totalDamage = 0;
        for (Profile p : live) {
            totalDamage += p.damageDealt.get(tick, hl);
        }
        Profile current = profile(focus);
        if (current != null && !current.present) {
            current = null;
            focus = null;
        }
        long age = tick - focusSince;
        double bestScore = Double.NEGATIVE_INFINITY;
        Profile best = null;
        double currentScore = Double.NEGATIVE_INFINITY;
        for (Profile p : live) {
            double share = totalDamage > 1e-6 ? p.damageDealt.get(tick, hl) / totalDamage : 0;
            double readable = p.maxConfidence();
            double proximity = 1.0 - Math.min(1.0, Geo.dist(p.x, p.z, hubX, hubZ) / 24.0);
            double execute = 0.25 * (1.0 - p.hpFraction);
            // Nobody gets ignored for long: pressure rotates through the party (readable, not random).
            double neglect = p == current ? 0
                    : Math.min(0.9, (tick - p.lastFocused) / (20.0 * cfg.focusMaxSeconds()) * 0.6);
            double score = share + 0.8 * readable + 0.5 * proximity + execute + neglect;
            if (p == current) {
                score += 0.25;
                if (age > Math.round(cfg.focusMaxSeconds() * 20) && live.size() > 1) {
                    score -= 0.9;
                }
                currentScore = score;
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (current != null) {
            current.lastFocused = tick;
        }
        if (current == null) {
            setFocus(best);
            return;
        }
        if (best != current && age >= Math.round(cfg.focusMinSeconds() * 20) && bestScore > currentScore + 0.15) {
            setFocus(best);
        }
    }

    private void setFocus(Profile p) {
        UUID next = p == null ? null : p.id;
        Profile old = profile(focus);
        if (old != null) {
            old.lastFocused = tick;
        }
        if (next != null && !next.equals(focus)) {
            focus = next;
            focusSince = tick;
            p.lastFocused = tick;
            tells.add(Tell.focus(next));
        } else if (next == null) {
            focus = null;
        }
    }

    /** Forces the lens onto someone (tests / admin). */
    public void forceFocus(UUID id) {
        Profile p = profiles.get(id);
        if (p != null && p.present) {
            setFocus(p);
        }
    }

    /* ================================================================== decisions */

    /**
     * Picks the next move. Scores are deterministic apart from a small Gumbel noise ({@code ai.temperature}).
     */
    public Decision decide(List<MoveSpec> moves, Context ctx) {
        Profile f = profile(focus);
        if (f == null || !f.present) {
            return new Decision(null, null, null, 0, -1, 0, "no focus");
        }
        int breathEvery = posture == Posture.OBSERVE ? Math.max(2, cfg.breathEvery() - 1) : cfg.breathEvery();
        if (breathEvery > 0 && movesSinceBreath >= breathEvery) {
            movesSinceBreath = 0;
            return new Decision(null, f.id, null, 0, -1, 0, "breath");
        }
        MoveSpec bestSpec = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Read bestExploit = null;
        List<String> lines = new ArrayList<>();
        MoveSpec last = recent.peekLast();
        for (MoveSpec spec : moves) {
            if (phase < spec.minPhase()) {
                continue;
            }
            Long ready = readyAt.get(spec.id());
            if (ready != null && ready > tick) {
                continue;
            }
            if (posture == Posture.OBSERVE && !spec.teaching()) {
                continue;
            }
            if (spec.requires() != null && !f.reads.get(spec.requires()).exploitable) {
                continue;
            }
            if (ctx.available() != null && !ctx.available().test(spec)) {
                continue;
            }
            double s = spec.base();
            Read exploit = null;
            double exploitContribution = 0;
            for (Read r : Read.values()) {
                double w = spec.readWeight(r);
                if (w == 0) {
                    continue;
                }
                Profile.ReadState st = f.reads.get(r);
                double c = st.noticed ? st.confidence : st.confidence * 0.3;
                if (posture == Posture.OBSERVE) {
                    c *= 0.35;
                }
                double contribution = w * c;
                s += contribution;
                if (st.exploitable && contribution > exploitContribution) {
                    exploitContribution = contribution;
                    exploit = r;
                }
            }
            s += spec.postureBias(posture);
            s += spec.closeCrowd() * ctx.closeFraction() + spec.farCrowd() * ctx.farFraction();
            double d = ctx.focusDistance();
            if (d < spec.minDistance()) {
                s -= 0.6 * (spec.minDistance() - d) / Math.max(1.0, spec.minDistance());
            } else if (d > spec.maxDistance()) {
                s -= 0.6 * (d - spec.maxDistance()) / 5.0;
            }
            int repeats = 0;
            for (MoveSpec m : recent) {
                if (m.id().equals(spec.id())) {
                    repeats++;
                }
            }
            s -= cfg.repeatPenalty() * repeats;
            if (last != null && last.family() == spec.family()) {
                s -= 0.15;
            }
            double noise = cfg.temperature() * gumbel();
            lines.add(String.format(Locale.ROOT, "%s=%.2f", spec.id(), s));
            s += noise;
            if (s > bestScore) {
                bestScore = s;
                bestSpec = spec;
                bestExploit = exploit;
            }
        }
        if (bestSpec == null) {
            return new Decision(null, f.id, null, 0, -1, 0, "nothing ready");
        }
        if (posture == Posture.OBSERVE) {
            bestExploit = null;
        }
        Read exploit = bestExploit;
        int sign = exploit == null ? 0 : f.reads.get(exploit).sign;
        int sector = exploit == Read.CAMPER ? f.reads.get(Read.CAMPER).sector : -1;
        String why = posture + " → " + bestSpec.id()
                + (exploit != null ? " [spends " + exploit + sgn(exploit, sign) + "]" : "")
                + " | " + String.join(" ", lines);
        return new Decision(bestSpec, f.id, exploit, sign, sector, bestScore, why);
    }

    /** The encounter actually started this move. */
    public void commit(Decision d) {
        if (d.spec() == null) {
            log("BREATH");
            return;
        }
        recent.addLast(d.spec());
        while (recent.size() > 4) {
            recent.removeFirst();
        }
        readyAt.put(d.spec().id(), tick + d.spec().cooldownTicks());
        movesSinceBreath++;
        log(d.why());
    }

    /* ================================================================== queries for moves */

    public boolean exploitable(UUID id, Read read) {
        Profile p = profile(id);
        return p != null && p.reads.get(read).exploitable;
    }

    public double confidence(UUID id, Read read) {
        Profile p = profile(id);
        return p == null ? 0 : p.reads.get(read).confidence;
    }

    public int sign(UUID id, Read read) {
        Profile p = profile(id);
        return p == null ? 0 : p.reads.get(read).sign;
    }

    /**
     * Windup change answering this player's dodge timing. Early dodgers get a held windup (it keeps tracking),
     * late dodgers a rushed one. The encounter clamps this to the fairness floors.
     */
    public int windupAdjust(UUID id) {
        Profile p = profile(id);
        if (p == null) {
            return posture == Posture.OBSERVE ? 6 : 0;
        }
        int base = posture == Posture.OBSERVE ? 6 : 0;
        Profile.ReadState s = p.reads.get(Read.TIMING);
        if (!s.exploitable) {
            return base;
        }
        return base + (s.sign < 0 ? 8 : -6);
    }

    /* ================================================================== debug */

    public List<String> log() {
        return new ArrayList<>(log);
    }

    private void log(String line) {
        log.addLast("[" + tick + "] " + line);
        while (log.size() > 12) {
            log.removeFirst();
        }
    }

    public String summary(UUID id) {
        Profile p = profile(id);
        if (p == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Read r : Read.values()) {
            Profile.ReadState s = p.reads.get(r);
            if (s.confidence < 0.05) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(r.name(), 0, Math.min(4, r.name().length()))
                    .append(sgn(r, s.sign))
                    .append(String.format(Locale.ROOT, "%.2f", s.confidence))
                    .append(s.exploitable ? "!" : s.noticed ? "*" : "");
        }
        return sb.isEmpty() ? "(nothing yet)" : sb.toString();
    }

    private static String sgn(Read r, int sign) {
        if (r == null || !r.signed()) {
            return "";
        }
        return switch (r) {
            case SIDE -> sign < 0 ? "<L" : ">R";
            case ORBIT -> sign > 0 ? "↻" : "↺";
            case TIMING -> sign < 0 ? "-early" : "+late";
            default -> "";
        };
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100);
    }

    private double gumbel() {
        double u = Math.max(1e-9, Math.min(1 - 1e-9, rng.nextDouble()));
        return -Math.log(-Math.log(u));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** 1 sample = 0, {@code min} samples = full trust. */
    private static double countFactor(double n, double min) {
        if (min <= 1) {
            return clamp01(n);
        }
        return clamp01((n - 1) / (min - 1));
    }
}
