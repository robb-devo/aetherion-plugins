package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CONSTELLATION: Helios's signature and the fight's business card. It draws a constellation of portals
 * in the sky, threads its light through them one star at a time, and then the light comes down and
 * <b>hunts</b>.
 *
 * <pre>
 *   SUMMON         each portal ignites on its own half-beat (a rising arpeggio): a point of light, then
 *                  its rim is drawn round, its face spins in, three shards take orbit
 *   CONSTELLATION  pairs are colour-coded (cyan, magenta) and linked by a thread whose light-points flow
 *                  from ENTRY to EXIT; the whole beam path is traced the same way; amber lanes with
 *                  arrows show where the floor beams come down and in which direction they rake
 *   CHARGE         the heart flares; the feed beam swells; everything spins up
 *   RELAY          the light hops through the chain one segment per half-beat, each hop a step higher
 *                  in pitch, the portal it enters pulsing; you can hear it coming
 *   HUNT           the floor beam locks onto a player and chases them across the arena (it has weight:
 *                  it speeds up over the hunt but turns late, so a sprint and a hard turn shake it). Every
 *                  two beats it picks its next prey (a ping and a hairline tell who). It scorches, cracks
 *                  and melts the floor behind it. Prism side beams rake their lanes like walls, then fade.
 *   TEAR           the scar it left glows white for a beat, then erupts along its whole length, oldest
 *                  end first: pillars of light, and every third point tears a hole through the floor
 *                  (it grows back a few seconds later)
 *   IMPLODE        overload flash, the beams snap off, every portal collapses on a falling chord
 * </pre>
 *
 * Geometry is physically consistent (each follow-up entry sits on the previous exit's ray, facing back
 * along it), so the chain can be traced by eye. Beams only hurt from the moment their segment ignites.
 * The Requiem uses the short form (no hunt, no tear) so its music stays on time.
 */
final class PortalBeams extends Attack {

    private static final Color WHITE = Color.fromRGB(245, 245, 255);
    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color[] PAIR = {Color.fromRGB(80, 220, 255), Color.fromRGB(255, 90, 220)};
    private static final Material[] PAIR_GLASS = {Material.CYAN_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};
    private static final Material[] PAIR_SOLID = {Material.LIGHT_BLUE_CONCRETE, Material.MAGENTA_CONCRETE};

    /** One portal: rim, face, orbiting shards, its birth point. */
    private final class Portal {
        final Vector3f c;
        final Quaternionf q;
        final int pair;
        final Shapes.Ring rim;
        final Shapes.Ring inner;
        final Shapes.Disc face;
        final List<BlockDisplay> shards = new ArrayList<>();
        final BlockDisplay spark;
        int born = -1;
        float pulse;
        /** Accumulated spin (tick * rate made every rim jump when the rate changed at charge / implode). */
        float spin;

        Portal(Vector3f c, Vector3f normal, int pair) {
            this.c = c;
            this.q = HMath.alignY(normal);
            this.pair = pair;
            Material glass = pair < 0 ? Material.WHITE_STAINED_GLASS : PAIR_GLASS[pair];
            Color color = pair < 0 ? WHITE : PAIR[pair];
            this.rim = new Shapes.Ring(stage, g, stage.budget().scaled(16, 10), pair < 0 ? Material.WHITE_CONCRETE : PAIR_SOLID[pair], color, 15, true);
            this.inner = new Shapes.Ring(stage, g, stage.budget().scaled(10, 6), Material.WHITE_CONCRETE, WHITE, 15, true);
            this.face = new Shapes.Disc(stage, g, glass, null, 15);
            int n = stage.budget().scaled(3, 1);
            for (int i = 0; i < n; i++) {
                BlockDisplay d = g.block((pair < 0 ? Material.WHITE_CONCRETE : PAIR_SOLID[pair]).createBlockData(), color, 15, false);
                if (d != null) {
                    shards.add(d);
                }
            }
            this.spark = g.block(Material.WHITE_CONCRETE.createBlockData(), WHITE, 15, true);
            rim.hide(c, 0);
            inner.hide(c, 0);
            face.hide(c, 0);
            for (BlockDisplay d : shards) {
                stage.push(d, HeliosStage.gone(c), 0);
            }
            stage.push(spark, HeliosStage.gone(c), 0);
        }

        Vector3f normal() {
            return new Quaternionf(q).transform(new Vector3f(0f, 1f, 0f));
        }

        void pose(int tick, float size, float spinRate, int interp) {
            if (born < 0 || tick < born) {
                return;
            }
            int age = tick - born;
            float draw = HMath.outCubic(HMath.window(age, 0, 12));
            float r = 1.8f * size * (1f + 0.25f * pulse) * (0.2f + 0.8f * draw);
            spin += 0.24f * spinRate;
            rim.arc(c, q, r, 0.16f + 0.1f * pulse, 0.16f, spin, spin + HMath.TAU * draw, interp);
            inner.pose(c, q, r * 0.72f, 0.06f, 0.06f, -spin * 1.6f, 0f, 0f, interp);
            face.set(c, r * 0.68f * draw, 0.03f, q, -spin * 0.8f, interp);
            for (int i = 0; i < shards.size(); i++) {
                float a = spin * 1.5f + i * HMath.TAU / shards.size();
                Vector3f at = new Quaternionf(q).transform(new Vector3f((float) Math.cos(a) * r * 1.35f, 0f, (float) Math.sin(a) * r * 1.35f)).add(c);
                stage.push(shards.get(i), HeliosStage.cube(at, 0.22f * draw, new Quaternionf().rotateXYZ(a, a * 0.7f, 0f)), interp);
            }
            float sparkSize = age < 6 ? 0.9f * (1f - age / 6f) + 0.05f : 0.001f;
            stage.push(spark, HeliosStage.cube(c, sparkSize, new Quaternionf().rotateY(a0(age))), interp);
            pulse *= 0.8f;
        }

        void implode(int interp) {
            rim.pose(c, q, 0.05f, 0.05f, 0.05f, 0f, 0f, 0f, interp);
            inner.hide(c, interp);
            face.set(c, 0.01f, 0.01f, q, 0f, interp);
            for (BlockDisplay d : shards) {
                stage.push(d, HeliosStage.gone(c), interp);
            }
        }

        private float a0(int age) {
            return age * 0.4f;
        }
    }

    /** A line of light-points flowing from {@code a} to {@code b}: direction at a glance. */
    private final class Flow {
        final Vector3f a;
        final Vector3f b;
        final Shapes.Line line;
        final List<BlockDisplay> dots = new ArrayList<>();
        boolean on;

        Flow(Vector3f a, Vector3f b, Material lineMat, Color color, Material dotMat, int dots) {
            this.a = a;
            this.b = b;
            this.line = new Shapes.Line(stage, g, lineMat, color);
            line.hide(a, 0);
            for (int i = 0; i < dots; i++) {
                BlockDisplay d = g.block(dotMat.createBlockData(), color, 15, false);
                if (d != null) {
                    stage.push(d, HeliosStage.gone(a), 0);
                    this.dots.add(d);
                }
            }
        }

        void show(float width, int interp) {
            on = true;
            line.set(a, b, width, interp);
        }

        void tick(int tick) {
            if (!on || tick % 2 != 0) {
                return;
            }
            float len = a.distance(b);
            for (int i = 0; i < dots.size(); i++) {
                float f = ((tick * 0.9f / Math.max(4f, len)) + i / (float) dots.size()) % 1f;
                Vector3f at = new Vector3f(a).lerp(b, f);
                stage.push(dots.get(i), HeliosStage.cube(at, 0.2f, new Quaternionf()), f < 0.05f ? 0 : HeliosStage.SMOOTH_2);
            }
        }

        void hide(int interp) {
            on = false;
            line.hide(b, interp);
            for (BlockDisplay d : dots) {
                stage.push(d, HeliosStage.gone(b), interp);
            }
        }
    }

    /** A segment of the beam path, igniting at {@code igniteAt}. */
    private static final class Segment {
        final Vector3f a;
        final Vector3f b;
        final boolean floor;
        final Vector3f laneA;
        final Vector3f laneB;
        Shapes.Beam beam;
        int igniteAt;
        Portal enters;

        Segment(Vector3f a, Vector3f b, boolean floor, Vector3f laneA, Vector3f laneB) {
            this.a = a;
            this.b = b;
            this.floor = floor;
            this.laneA = laneA;
            this.laneB = laneB;
        }
    }

    private final HeliosScript h;
    private final int count;
    private final List<Portal> portals = new ArrayList<>();
    private final List<Flow> threads = new ArrayList<>();
    private final List<Flow> paths = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();
    private final List<Shapes.Line> lanes = new ArrayList<>();
    private final List<Shapes.Line> arrows = new ArrayList<>();
    private final List<Shapes.Ring> scorch = new ArrayList<>();
    private Shapes.Beam feed;

    private int summon;
    private int constellation;
    private int charge;
    private int relayStart;
    private int relayStep;
    private int sweepStart;
    private int sweep;
    private int implodeAt;
    private int end;

    /* ---- hunt + tear (the signature form; the Requiem plays the short form) */
    private static final int MAX_TRAIL = 16;
    private final boolean hunt;
    private int hunter = -1;
    private int huntEnd;
    private int tearAt;
    private int chainStep;
    private final Vector3f huntP = new Vector3f();
    private final Vector3f huntV = new Vector3f();
    private Player prey;
    private int preyIndex = -1;
    private int nextPrey;
    private int aimHideAt = -1;
    private Shapes.Line aimLine;
    private Shapes.Ring contact;
    private final List<Vector3f> trail = new ArrayList<>();
    private final List<Shapes.Line> fuses = new ArrayList<>();
    private final List<Arena.Replica> holes = new ArrayList<>();
    private final List<Integer> holeAt = new ArrayList<>();

    PortalBeams(HeliosScript h, int count) {
        this(h, count, false);
    }

    PortalBeams(HeliosScript h, int count, boolean hunt) {
        super(h);
        this.h = h;
        this.hunt = hunt;
        int min = enc.config().i("helios.portals.count-min", 3);
        int max = enc.config().i("helios.portals.count-max", 5);
        this.count = count > 0 ? count : min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
    }

    @Override
    public String id() {
        return "portals";
    }

    @Override
    public Family family() {
        return Family.SWEEP;
    }

    @Override
    public void start() {
        int half = Math.max(3, enc.tempo().ticks(0.5));
        summon = half * (count + 1);
        constellation = summon + enc.tempo().ticks(2);
        charge = constellation + enc.tempo().ticks(1);
        relayStart = charge;
        relayStep = half;
        build();
        for (int i = 0; i < portals.size(); i++) {
            portals.get(i).born = i * half;
        }
        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).igniteAt = relayStart + i * relayStep;
        }
        int lastIgnite = relayStart + (segments.size() - 1) * relayStep;
        sweepStart = lastIgnite;
        sweep = enc.tempo().ticks(3);
        implodeAt = sweepStart + sweep;
        if (hunt && hunter >= 0) {
            huntEnd = sweepStart + enc.tempo().ticks(Math.max(4, enc.config().i("helios.portals.hunt-beats", 8)));
            tearAt = huntEnd + enc.tempo().ticks(1);
            chainStep = 2;
            implodeAt = Math.max(implodeAt, tearAt + MAX_TRAIL * chainStep + 8);
            aimLine = new Shapes.Line(stage, g, Material.MAGENTA_STAINED_GLASS, PAIR[1]);
            aimLine.hide(heart0(), 0);
            contact = new Shapes.Ring(stage, g, 14, Material.WHITE_CONCRETE, WHITE, 15, true);
            contact.hide(heart0(), 0);
        }
        end = implodeAt + enc.tempo().ticks(1.5);
        h.rig().ringScale(1.6f);
        enc.score().play(Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
    }

    /* ================================================================== geometry */

    private void build() {
        Vector3f heart = new Vector3f(h.rig().center);
        List<Player> f = enc.fighters();
        Vector3f t1 = f.isEmpty() ? new Vector3f(0f, 0f, 18f) : stage.feet(f.get(ThreadLocalRandom.current().nextInt(f.size())));
        Vector3f t2 = f.size() < 2 ? new Vector3f(t1).mul(-1f) : stage.feet(f.get(ThreadLocalRandom.current().nextInt(f.size())));
        t1.y = 0f;
        t2.y = 0f;
        boolean prism = count % 2 == 1;
        int pairs = count >= 4 ? 2 : 1;

        float a1 = HMath.angleOf(t1.x, t1.z);
        Vector3f e1 = HMath.ring(6f, a1 + 0.9f, 2.5f).add(heart);
        Portal pe1 = addPortal(e1, new Vector3f(heart).sub(e1), 0);
        feed = new Shapes.Beam(stage, g, Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, WHITE);
        feed.hide(0);
        addSegment(heart, e1, pe1, false, null, null);

        Vector3f x1 = HMath.ring(23f, a1 + HMath.PI * 0.65f, 13f);
        Vector3f aim1 = pairs == 2 ? HMath.ring(20f, a1 + HMath.PI * 1.25f, 14f) : new Vector3f(t1);
        addPortal(x1, new Vector3f(aim1).sub(x1), 0);
        threads.add(new Flow(e1, x1, PAIR_GLASS[0], PAIR[0], PAIR_SOLID[0], stage.budget().scaled(6, 3)));

        Vector3f emitter = x1;
        Vector3f dir = new Vector3f(aim1).sub(x1).normalize();
        Vector3f target = t1;
        if (pairs == 2) {
            Vector3f e2 = new Vector3f(dir).mul(11f).add(x1);
            Portal pe2 = addPortal(e2, new Vector3f(dir).negate(), 1);
            addSegment(x1, e2, pe2, false, null, null);
            float a2 = HMath.angleOf(t2.x, t2.z);
            Vector3f x2 = HMath.ring(24f, a2 + HMath.PI * 0.55f, 14f);
            addPortal(x2, new Vector3f(t2).sub(x2), 1);
            threads.add(new Flow(e2, x2, PAIR_GLASS[1], PAIR[1], PAIR_SOLID[1], stage.budget().scaled(6, 3)));
            emitter = x2;
            dir = new Vector3f(t2).sub(x2).normalize();
            target = t2;
        }
        Vector3f side = new Vector3f(-dir.z, 0f, dir.x).normalize();
        float sweepDir = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
        if (prism) {
            Vector3f pr = new Vector3f(dir).mul(9f).add(emitter);
            Portal pp = addPortal(pr, new Vector3f(dir).negate(), -1);
            addSegment(emitter, pr, pp, false, null, null);
            for (int k = -1; k <= 1; k++) {
                Vector3f aim = new Vector3f(target).add(new Vector3f(side).mul(k * 5.5f));
                addFloor(pr, aim, side, sweepDir);
            }
        } else {
            addFloor(emitter, target, side, sweepDir);
        }
        // The hunter: the only floor beam, or the middle one of the prism's three.
        int floors = 0;
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).floor) {
                if (!prism || floors == 1) {
                    hunter = i;
                }
                floors++;
            }
        }
        for (Segment s : segments) {
            s.beam = new Shapes.Beam(stage, g, Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, WHITE);
            s.beam.hide(0);
            Vector3f to = s.floor ? s.laneA : s.b;
            paths.add(new Flow(s.a, to, Material.WHITE_STAINED_GLASS, WHITE, Material.WHITE_CONCRETE, stage.budget().scaled(4, 2)));
        }
    }

    private Portal addPortal(Vector3f c, Vector3f normal, int pair) {
        Portal p = new Portal(c, normal.normalize(), pair);
        portals.add(p);
        return p;
    }

    private void addSegment(Vector3f a, Vector3f b, Portal enters, boolean floor, Vector3f laneA, Vector3f laneB) {
        Segment s = new Segment(a, b, floor, laneA, laneB);
        s.enters = enters;
        segments.add(s);
    }

    /** A floor beam raking an 11-block lane across {@code aim}, with an arrow showing which way. */
    private void addFloor(Vector3f from, Vector3f aim, Vector3f side, float dirSign) {
        Vector3f a = new Vector3f(aim).sub(new Vector3f(side).mul(5.5f * dirSign));
        Vector3f b = new Vector3f(aim).add(new Vector3f(side).mul(5.5f * dirSign));
        a.y = 0.05f;
        b.y = 0.05f;
        addSegment(from, b, null, true, a, b);
        Shapes.Line lane = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
        lane.hide(a, 0);
        lanes.add(lane);
        // Arrowhead at the far end: two short strokes.
        Vector3f back = new Vector3f(a).sub(b).normalize();
        Vector3f perp = new Vector3f(-back.z, 0f, back.x);
        for (int s = -1; s <= 1; s += 2) {
            Shapes.Line l = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
            l.hide(b, 0);
            arrows.add(l);
            arrowEnds.add(new Vector3f[]{new Vector3f(b), new Vector3f(b).add(new Vector3f(back).mul(1.4f)).add(new Vector3f(perp).mul(s * 0.9f))});
        }
        Shapes.Ring r = new Shapes.Ring(stage, g, 10, Material.ORANGE_STAINED_GLASS, AMBER, 15, false);
        r.hide(a, 0);
        scorch.add(r);
    }

    private final List<Vector3f[]> arrowEnds = new ArrayList<>();

    /* ================================================================== timeline */

    @Override
    protected boolean tick() {
        Vector3f heart = h.rig().center;
        // SUMMON: each portal on its own half-beat, a rising arpeggio.
        for (int i = 0; i < portals.size(); i++) {
            if (t == portals.get(i).born) {
                Portal p = portals.get(i);
                enc.score().at(p.c, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, Score.semi(i * 3 - 6));
                enc.score().at(p.c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, Score.semi(i * 3));
                p.pulse = 1f;
            }
        }
        float spinRate = t < charge ? 1f : t < implodeAt ? 2.2f : 3f;
        if (t % 2 == 0 && t < implodeAt) {
            for (Portal p : portals) {
                p.pose(t, 1f + (t >= charge ? 0.15f : 0f), spinRate, HeliosStage.SMOOTH_2);
            }
        }
        if (hunt && hunter >= 0 && t >= sweepStart && t < huntEnd) {
            h.rig().lookAt(huntP, 0.25f);
        } else {
            h.rig().lookAt(portals.get(0).c, 0.12f);
        }

        // CONSTELLATION: threads and paths flow; lanes and arrows lay down.
        if (t == summon) {
            for (Flow f : threads) {
                f.show(0.06f, 6);
            }
            for (Flow f : paths) {
                f.show(0.035f, 6);
            }
            for (int i = 0; i < lanes.size(); i++) {
                Segment s = floorSegment(i);
                lanes.get(i).set(s.laneA, s.laneB, 0.14f, 6);
            }
            for (int i = 0; i < arrows.size(); i++) {
                Vector3f[] e = arrowEnds.get(i);
                arrows.get(i).set(e[0], e[1], 0.14f, 6);
            }
            enc.score().play(Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1.2f);
            enc.score().play(Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
        }
        for (Flow f : threads) {
            f.tick(t);
        }
        for (Flow f : paths) {
            f.tick(t);
        }
        if (t > summon && t < constellation && (t - summon) % 6 == 0) {
            enc.score().play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f + 0.1f * ((t - summon) / 6 % 4));
        }

        // CHARGE: the heart flares, the feed swells.
        if (t == constellation) {
            h.rig().heartSize(1.5f);
            enc.score().sweep(heart, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.3f, 0.6f, 2f, charge - constellation, 3);
            enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        }
        if (t >= constellation && t < charge) {
            float f = HMath.window(t, constellation, charge);
            feed.set(heart, segments.get(0).b, 0.05f + 0.3f * f, 1);
        }

        // RELAY: the light hops through the chain, one segment per half-beat, climbing in pitch.
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (t == s.igniteAt) {
                paths.get(i).hide(3);
                enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, Score.semi(-6 + i * 3));
                enc.score().at(s.b, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.45f, 1.2f + i * 0.1f);
                enc.camera().shakeFrom(s.b, 18f, 4);
                if (s.enters != null) {
                    s.enters.pulse = 1.5f;
                }
                if (i == 0) {
                    feed.hide(2);
                    h.rig().heartSize(1f);
                }
            }
        }
        if (t >= relayStart && t < implodeAt) {
            float w = 0.6f + 0.2f * enc.star().beat();
            float sf = HMath.inOutCubic(HMath.window(t, sweepStart, sweepStart + sweep));
            boolean sidesDone = hunt && t >= sweepStart + sweep;
            int floorIdx = 0;
            for (int i = 0; i < segments.size(); i++) {
                Segment s = segments.get(i);
                if (t < s.igniteAt) {
                    if (s.floor) {
                        floorIdx++;
                    }
                    continue;
                }
                Vector3f to = s.b;
                float width = w;
                if (s.floor) {
                    if (hunt && i == hunter) {
                        if (t >= huntEnd) {
                            floorIdx++;
                            continue;
                        }
                        if (t == s.igniteAt) {
                            huntP.set(s.laneA);
                            huntV.set(0f);
                        }
                        if (t >= sweepStart) {
                            stepHunt();
                        }
                        to = new Vector3f(huntP);
                        // Escalates: wider and hotter the longer it hunts.
                        width = w + 0.45f * HMath.window(t, sweepStart, huntEnd);
                    } else if (sidesDone) {
                        if (t == sweepStart + sweep) {
                            s.beam.thin(3);
                        }
                        floorIdx++;
                        continue;
                    } else {
                        to = new Vector3f(s.laneA).lerp(s.laneB, sf);
                    }
                    scar(to, floorIdx++);
                }
                float grow = HMath.outCubic(HMath.window(t, s.igniteAt, s.igniteAt + 3));
                s.beam.set(s.a, to, 0.05f + (width - 0.05f) * grow, 1);
                hurt(s.a, to);
            }
            if (t % 20 == 0) {
                enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
            }
        }
        if (hunt && hunter >= 0) {
            tickHuntShow();
            tickTear();
        }
        animateHoles();
        if (t == sweepStart) {
            enc.score().play(Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);
            enc.score().sweep(null, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.7f, 0.6f, 1.1f, sweep, 5);
        }

        // IMPLODE: overload flash, the beams snap, every portal collapses on a falling chord.
        if (t == implodeAt - 4) {
            for (Segment s : segments) {
                if (hunt && s.floor) {
                    continue;
                }
                s.beam.set(s.a, s.floor ? s.laneB : s.b, 1.4f, 3);
            }
            enc.camera().flash(2, 6);
        }
        if (t == implodeAt) {
            for (Segment s : segments) {
                s.beam.thin(3);
            }
            for (Shapes.Line l : lanes) {
                l.hide(portals.get(0).c, 4);
            }
            for (Shapes.Line l : arrows) {
                l.hide(portals.get(0).c, 4);
            }
            for (Flow f : threads) {
                f.hide(4);
            }
            for (int i = 0; i < portals.size(); i++) {
                Portal p = portals.get(i);
                p.implode(6);
                enc.score().at(p.c, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, Score.semi(6 - i * 3));
            }
            enc.score().chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, Score.semi(0), Score.semi(-5), Score.semi(-12));
            h.rig().ringScale(1f);
        }
        if (t == implodeAt + 8) {
            for (Segment s : segments) {
                s.beam.hide(2);
            }
            for (Shapes.Ring r : scorch) {
                r.hide(portals.get(0).c, 4);
            }
        }
        return t >= end;
    }

    private Vector3f heart0() {
        return new Vector3f(h.rig().center);
    }

    /* ================================================================== hunt */

    /**
     * The hunting beam has weight: it steers toward its prey with limited acceleration and a top speed
     * that climbs over the hunt (from a walk to just under a sprint). Running straight away only buys
     * time; a sprint and a hard turn make it overshoot.
     */
    private void stepHunt() {
        if (t >= nextPrey || prey == null || !prey.isValid() || prey.isDead() || !enc.fighters().contains(prey)) {
            pickPrey();
        }
        float f = HMath.window(t, sweepStart, huntEnd);
        float vmax = (float) HMath.lerp((float) enc.config().d("helios.portals.hunt-speed-start", 0.16),
                (float) enc.config().d("helios.portals.hunt-speed-end", 0.27), HMath.smooth(f));
        float accel = (float) enc.config().d("helios.portals.hunt-accel", 0.024);
        Vector3f desired = new Vector3f();
        if (prey != null) {
            Vector3f to = stage.feet(prey);
            to.y = 0.05f;
            desired.set(to).sub(huntP);
            desired.y = 0f;
            float d = desired.length();
            if (d > 0.05f) {
                desired.mul(Math.min(vmax, d * 0.5f + 0.02f) / d);
            }
        }
        Vector3f dv = new Vector3f(desired).sub(huntV);
        float dl = dv.length();
        if (dl > accel) {
            dv.mul(accel / dl);
        }
        huntV.add(dv);
        huntP.add(huntV);
        huntP.y = 0.05f;
        float r = HMath.horizontal(huntP);
        if (r > 33f) {
            huntP.x *= 33f / r;
            huntP.z *= 33f / r;
        }
        // Scorched ground: remember the path for the tear.
        if (trail.isEmpty() || (trail.size() < MAX_TRAIL && trail.get(trail.size() - 1).distance(huntP) >= 1.7f)) {
            trail.add(new Vector3f(huntP.x, 0f, huntP.z));
        }
        // Standing in the touch-down is worse than grazing the beam.
        double power = h.power("portals.hunt", h.power("portals", 36));
        for (Player p : enc.fighters()) {
            Vector3f pf = stage.feet(p);
            if (HMath.horizontal(new Vector3f(pf).sub(huntP)) < 1.5f && pf.y < 2f) {
                // Same key as the beam itself: touching down on you never stacks with the beam's own hit.
                enc.hit(p, power, "portal", 10, null);
                p.setFireTicks(Math.max(p.getFireTicks(), 30));
            }
        }
    }

    /** Next prey: round the party, one after the other (solo: you, again). A ping and a hairline say who. */
    private void pickPrey() {
        List<Player> f = enc.fighters();
        nextPrey = t + enc.tempo().ticks(2);
        if (f.isEmpty()) {
            prey = null;
            return;
        }
        preyIndex = (preyIndex + 1) % f.size();
        prey = f.get(preyIndex);
        Vector3f at = stage.feet(prey);
        aimLine.set(new Vector3f(huntP).add(0f, 0.12f, 0f), new Vector3f(at.x, 0.12f, at.z), 0.07f, 0);
        aimHideAt = t + 10;
        enc.score().to(prey, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        enc.score().to(prey, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
        enc.score().at(huntP, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        prey.sendActionBar(TextUtil.component("&d&lThe light hunts you. &fSprint and turn!"));
    }

    /** The pomp around the hunt: the touch-down ring, the tempo of the chase, the whole chain surging. */
    private void tickHuntShow() {
        if (aimHideAt >= 0 && t >= aimHideAt) {
            aimLine.hide(huntP, 3);
            aimHideAt = -1;
        }
        if (t < sweepStart || t >= huntEnd) {
            if (t == huntEnd && contact != null) {
                contact.hide(huntP, 4);
            }
            return;
        }
        float f = HMath.window(t, sweepStart, huntEnd);
        float beat = enc.star().beat();
        contact.flat(new Vector3f(huntP).add(0f, 0.08f, 0f), 1.1f + 0.5f * beat + 0.4f * f, 0.16f, 0.06f, t * 0.3f, HeliosStage.SMOOTH_1);
        if (t == sweepStart) {
            h.rig().spin(1.8f);
            h.rig().ringScale(1.9f);
            enc.score().play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
            enc.score().at(huntP, Sound.ENTITY_GUARDIAN_ATTACK, 1.4f, 0.6f);
            enc.score().chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, Score.semi(-12), Score.semi(-5), Score.semi(0));
            enc.camera().flash(1, 4);
        }
        if (enc.tempo().onBeat()) {
            // Every beat the chain surges: portals pulse, the heart flares, the drone climbs.
            for (Portal p : portals) {
                p.pulse = 1.2f;
            }
            h.rig().heartSize(1.25f + 0.35f * f);
            enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 1f, 0.9f + 0.9f * f);
            enc.score().at(huntP, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f + 0.5f * f);
            enc.camera().shakeFrom(huntP, 12f, 5);
        }
        if (t == sweepStart + (huntEnd - sweepStart) / 2) {
            enc.score().at(huntP, Sound.ENTITY_GUARDIAN_ATTACK, 1.4f, 1.1f);
        }
    }

    /* ================================================================== tear */

    private void tickTear() {
        if (t == huntEnd) {
            // Overload at the last touch-down, then the scar lights up: one beat to get off it.
            Segment s = segments.get(hunter);
            s.beam.set(s.a, huntP, 1.5f, 2);
            enc.camera().flash(1, 4);
            enc.score().at(huntP, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            h.rig().spin(1f);
            h.rig().heartSize(1f);
            for (int i = 0; i < trail.size(); i++) {
                Vector3f p = trail.get(i);
                // Spawned small in place this tick; they grow next tick (a push in the spawn tick is not
                // interpolated, and growing from the stage origin would fly them in from the centre).
                Shapes.Line fuse = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
                fuse.set(p, new Vector3f(p).add(0f, 0.05f, 0f), 0.02f, 0);
                fuses.add(fuse);
                if (i > 0) {
                    enc.arena().crackLine(trail.get(i - 1), p, 0.95f, tearAt - huntEnd + 40);
                }
            }
            enc.score().sweep(null, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.2f, 0.7f, 1.8f, tearAt - huntEnd, 3);
            for (Player p : enc.fighters()) {
                p.sendActionBar(TextUtil.component("&f&lThe scar is about to erupt! &7Get off the light!"));
            }
        }
        if (t == huntEnd + 1) {
            for (int i = 0; i < fuses.size(); i++) {
                Vector3f p = trail.get(i);
                fuses.get(i).set(p, new Vector3f(p).add(0f, 0.9f, 0f), 0.14f, Math.max(4, tearAt - huntEnd - 3));
            }
        }
        if (t == huntEnd + 4) {
            segments.get(hunter).beam.thin(3);
        }
        if (t == huntEnd + 8) {
            segments.get(hunter).beam.hide(2);
        }
        if (t < tearAt) {
            return;
        }
        int k = (t - tearAt) / chainStep;
        if ((t - tearAt) % chainStep == 0 && k < trail.size()) {
            erupt(k);
        }
        // Each pillar collapses a few ticks after it went up.
        int done = (t - tearAt - 5) / chainStep;
        if ((t - tearAt - 5) >= 0 && (t - tearAt - 5) % chainStep == 0 && done < fuses.size()) {
            fuses.get(done).hide(trail.get(done), 4);
        }
        // The last pillar is down: implode right after (the timeline reserved room for a full trail).
        if (t - tearAt == trail.size() * chainStep + 8 && t + 4 < implodeAt) {
            implodeAt = t + 4;
            end = implodeAt + enc.tempo().ticks(1.5);
        }
    }

    private void erupt(int k) {
        Vector3f p = trail.get(k);
        if (k < fuses.size()) {
            fuses.get(k).set(p, new Vector3f(p).add(0f, 4.5f, 0f), 0.6f, 2);
        }
        enc.score().at(p, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f + 0.04f * k);
        enc.score().at(p, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
        stage.particle(org.bukkit.Particle.EXPLOSION, new Vector3f(p).add(0f, 0.6f, 0f), 1, 0.1, 0);
        enc.camera().shakeFrom(p, 9f, 4);
        int cx = (int) Math.floor(p.x);
        int cz = (int) Math.floor(p.z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                enc.arena().crackCell(cx + dx, cz + dz, 0.9f, 80);
            }
        }
        double power = h.power("portals.tear", 44);
        for (Player pl : enc.fighters()) {
            Vector3f f = stage.feet(pl);
            if (HMath.horizontal(new Vector3f(f).sub(p)) < 1.9f && f.y < 3f) {
                enc.hit(pl, power, "portal_tear", 20, new org.bukkit.util.Vector(0, 0.35, 0));
            }
        }
        if (k % 3 == 2) {
            tearHole(cx, cz);
        }
    }

    /** Tears a small plus-shaped hole straight through the floor (keel included). It grows back. */
    private void tearHole(int cx, int cz) {
        Arena arena = enc.arena();
        List<ArenaLayout.Cell> cells = new ArrayList<>();
        int[][] plus = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : plus) {
            int dx = cx + o[0];
            int dz = cz + o[1];
            ArenaLayout.Cell top = ArenaLayout.at(dx, -1, dz);
            if (top == null || !arena.solidAt(dx + 0.5f, dz + 0.5f)) {
                continue;
            }
            // Never the seams: the pit rim, and the Crown/Course seam (islands must stay islands).
            if (top.radius() < ArenaLayout.RING_IN[top.ring()] + 0.5f) {
                continue;
            }
            for (int dy = -1; dy >= -16; dy--) {
                ArenaLayout.Cell c = ArenaLayout.at(dx, dy, dz);
                if (c != null) {
                    cells.add(c);
                }
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        Arena.Replica rep = arena.replica(g, cells, true);
        rep.pose(new Quaternionf(), new Vector3f(), 1f, 0);
        rep.material(Material.MAGMA_BLOCK);
        rep.brightness(15);
        arena.revertTempsIn(cells);
        arena.remove(cells);
        holes.add(rep);
        holeAt.add(t);
        h.regrowLater(cells, 20 * enc.config().i("helios.portals.tear-regrow-seconds", 9));
        Vector3f at = new Vector3f(cx + 0.5f, 0f, cz + 0.5f);
        enc.score().at(at, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.2f, 0.6f);
    }

    private void animateHoles() {
        for (int i = 0; i < holes.size(); i++) {
            int age = t - holeAt.get(i);
            if (age > 42) {
                continue;
            }
            if (age == 42) {
                holes.get(i).pose(new Quaternionf(), new Vector3f(0f, -20f, 0f), 0.001f, 0);
                continue;
            }
            if (age % 2 == 0) {
                float f = HMath.window(age, 0, 40);
                Quaternionf tumble = new Quaternionf().rotateXYZ(f * 1.3f, f * 0.7f, f * 0.4f);
                holes.get(i).pose(tumble, new Vector3f(0f, -HMath.inQuad(f) * 18f, 0f), 1f - 0.5f * f, HeliosStage.SMOOTH_2);
            }
        }
    }

    @Override
    protected void cleanup() {
        h.rig().spin(1f);
        h.rig().heartSize(1f);
        h.rig().ringScale(1f);
    }

    private Segment floorSegment(int i) {
        int k = 0;
        for (Segment s : segments) {
            if (s.floor) {
                if (k == i) {
                    return s;
                }
                k++;
            }
        }
        return segments.get(segments.size() - 1);
    }

    /** Where a floor beam touches down: a flash ring, cracks and a molten trail. */
    private void scar(Vector3f at, int idx) {
        if (t % 4 == 0 && idx < scorch.size()) {
            Shapes.Ring r = scorch.get(idx);
            r.flat(new Vector3f(at).add(0f, 0.08f, 0f), 0.4f, 0.1f, 0.04f, 0f, 0);
            r.flat(new Vector3f(at).add(0f, 0.08f, 0f), 1.8f, 0.05f, 0.04f, t * 0.2f, 4);
        }
        enc.arena().crackCell((int) Math.floor(at.x), (int) Math.floor(at.z), 0.9f, 100);
        if (t % 3 == 0) {
            h.moltenAt((int) Math.floor(at.x), (int) Math.floor(at.z), 20 * 4);
        }
        if (t % 5 == 0) {
            enc.score().at(at, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.7f);
        }
    }

    private void hurt(Vector3f a, Vector3f b) {
        double power = h.power("portals", 36);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            float d2 = HMath.bodyDistSq(f, 1.8f, a, b);
            if (d2 < 0.8f * 0.8f) {
                enc.hit(p, power, "portal", 10, null);
                p.setFireTicks(Math.max(p.getFireTicks(), 20));
            } else if (d2 < 3.5f * 3.5f && t % 10 == 0) {
                // Standing close to the light: the screen edges burn, a warning, not damage.
                enc.camera().vignette(p, 0.35f);
                org.bukkit.Bukkit.getScheduler().runTaskLater(enc.module().plugin(), () -> enc.camera().vignette(p, 0f), 8L);
            }
        }
    }
}
