package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

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
 * CONSTELLATION: Helios's signature. It draws a constellation of portals in the sky and threads its
 * light through them, one star at a time, until it rakes the floor.
 *
 * <pre>
 *   SUMMON         each portal ignites on its own half-beat (a rising arpeggio): a point of light, then
 *                  its rim is drawn round, its face spins in, three shards take orbit
 *   CONSTELLATION  pairs are colour-coded (cyan, magenta) and linked by a thread whose light-points flow
 *                  from ENTRY to EXIT; the whole beam path is traced the same way; amber lanes with
 *                  arrows show where the floor beams will rake and in which direction
 *   CHARGE         the heart flares; the feed beam swells; everything spins up
 *   RELAY          the light hops through the chain one segment per half-beat, each hop a step higher
 *                  in pitch, the portal it enters pulsing; you can hear it coming
 *   SWEEP          the floor beams rake their lanes, leaving molten scars; the prism fans into three
 *   IMPLODE        overload flash, the beams snap off, every portal collapses on a falling chord
 * </pre>
 *
 * Geometry is physically consistent (each follow-up entry sits on the previous exit's ray, facing back
 * along it), so the chain can be traced by eye. Beams only hurt from the moment their segment ignites.
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
            float spin = tick * 0.12f * spinRate;
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
                stage.push(dots.get(i), HeliosStage.cube(at, 0.2f, new Quaternionf()), f < 0.05f ? 0 : 2);
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

    PortalBeams(HeliosScript h, int count) {
        super(h);
        this.h = h;
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
                p.pose(t, 1f + (t >= charge ? 0.15f : 0f), spinRate, 2);
            }
        }
        h.rig().lookAt(portals.get(0).c, 0.12f);

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
            int floorIdx = 0;
            for (Segment s : segments) {
                if (t < s.igniteAt) {
                    continue;
                }
                Vector3f to = s.b;
                if (s.floor) {
                    to = new Vector3f(s.laneA).lerp(s.laneB, sf);
                    scar(to, floorIdx++);
                }
                float grow = HMath.outCubic(HMath.window(t, s.igniteAt, s.igniteAt + 3));
                s.beam.set(s.a, to, 0.05f + (w - 0.05f) * grow, 1);
                hurt(s.a, to);
            }
            if (t % 20 == 0) {
                enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
            }
        }
        if (t == sweepStart) {
            enc.score().play(Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);
            enc.score().sweep(null, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.7f, 0.6f, 1.1f, sweep, 5);
        }

        // IMPLODE: overload flash, the beams snap, every portal collapses on a falling chord.
        if (t == implodeAt - 4) {
            for (Segment s : segments) {
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
