package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PORTAL BEAMS. Helios opens three to five portals in the air and fires its light into the first one.
 *
 * <pre>
 *   Pairs:   an ENTRY (rim facing the light that will come in) and its EXIT share one colour
 *            (cyan pair, magenta pair). A thin thread of that colour links them: "in here, out there".
 *   Chain:   Helios → cyan in | cyan out → magenta in | magenta out → floor.
 *   Prism:   with an odd count the last beam hits a white prism that splits it into a fan of three.
 *   Floor:   the final beams rake across the floor; an amber lane on the ground shows exactly the
 *            stretch they will sweep.
 * </pre>
 *
 * Geometry: each exit is placed high on the rim, aimed at a player; a follow-up entry sits on the
 * previous exit's ray, facing back along it, so the chain is physically consistent (you can trace it
 * with your eyes). Tell: three beats of hairlines along the full path plus the lanes; fire on the beat.
 */
final class PortalBeams extends Attack {

    private static final Color WHITE = Color.fromRGB(245, 245, 255);
    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color[] PAIR = {Color.fromRGB(80, 220, 255), Color.fromRGB(255, 90, 220)};
    private static final Material[] PAIR_GLASS = {Material.CYAN_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};

    private static final class Portal {
        final Vector3f c;
        final Quaternionf q;
        final Shapes.Ring rim;
        final Shapes.Disc face;

        Portal(Vector3f c, Vector3f normal, Shapes.Ring rim, Shapes.Disc face) {
            this.c = c;
            this.q = HMath.alignY(normal);
            this.rim = rim;
            this.face = face;
        }

        Vector3f normal() {
            return new Quaternionf(q).transform(new Vector3f(0f, 1f, 0f));
        }
    }

    private final HeliosScript h;
    private final int count;
    private final List<Portal> portals = new ArrayList<>();
    private final List<Vector3f[]> segments = new ArrayList<>();
    private final List<Shapes.Beam> beams = new ArrayList<>();
    private final List<Shapes.Line> preview = new ArrayList<>();
    private final List<Shapes.Line> threads = new ArrayList<>();
    private final List<Shapes.Line> lanes = new ArrayList<>();
    /** Final floor beams: emitter, lane start, lane end. */
    private final List<Vector3f[]> finals = new ArrayList<>();
    private int tell;
    private int fire;
    private boolean prism;

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
        tell = enc.tempo().ticks(3);
        fire = enc.tempo().ticks(3);
        build();
        for (Portal p : portals) {
            p.rim.pose(p.c, p.q, 0.1f, 0.12f, 0.12f, 0f, 0f, 0f, 0);
            p.face.set(p.c, 0.1f, 0.03f, p.q, 0f, 0);
        }
        enc.score().play(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        h.rig().ringScale(1.25f);
    }

    /* ------------------------------------------------------------------ geometry */

    private void build() {
        Vector3f heart = new Vector3f(h.rig().center);
        List<Player> f = enc.fighters();
        Vector3f t1 = f.isEmpty() ? new Vector3f(0f, 0f, 18f) : enc.stage().feet(f.get(ThreadLocalRandom.current().nextInt(f.size())));
        Vector3f t2 = f.size() < 2 ? new Vector3f(t1).mul(-1f) : enc.stage().feet(f.get(ThreadLocalRandom.current().nextInt(f.size())));
        t1.y = 0f;
        t2.y = 0f;
        prism = count % 2 == 1;
        int pairs = count >= 4 ? 2 : 1;

        // Entry 1: close to Helios, between it and the first target, facing the heart.
        float a1 = HMath.angleOf(t1.x, t1.z);
        Vector3f e1 = HMath.ring(6f, a1 + 0.9f, 1.5f).add(heart);
        addPortal(e1, new Vector3f(heart).sub(e1), 0);
        segments.add(new Vector3f[]{heart, e1});

        Vector3f exitPos = HMath.ring(23f, a1 + HMath.PI * 0.65f, 12f);
        Vector3f exitAim = pairs == 2 ? HMath.ring(20f, a1 + HMath.PI * 1.25f, 13f) : new Vector3f(t1);
        addPortal(exitPos, new Vector3f(exitAim).sub(exitPos), 0);
        link(e1, exitPos, 0);

        Vector3f emitter = exitPos;
        Vector3f emitDir = new Vector3f(exitAim).sub(exitPos).normalize();
        Vector3f target = t1;
        if (pairs == 2) {
            // Entry 2 sits on exit 1's ray, facing back along it.
            Vector3f e2 = new Vector3f(emitDir).mul(11f).add(exitPos);
            addPortal(e2, new Vector3f(emitDir).negate(), 1);
            segments.add(new Vector3f[]{exitPos, e2});
            float a2 = HMath.angleOf(t2.x, t2.z);
            Vector3f x2 = HMath.ring(24f, a2 + HMath.PI * 0.55f, 13f);
            addPortal(x2, new Vector3f(t2).sub(x2), 1);
            link(e2, x2, 1);
            emitter = x2;
            emitDir = new Vector3f(t2).sub(x2).normalize();
            target = t2;
        }
        if (prism) {
            // The prism floats on the last ray and splits it into a fan of three.
            Vector3f pr = new Vector3f(emitDir).mul(9f).add(emitter);
            addPrism(pr, emitDir);
            segments.add(new Vector3f[]{emitter, pr});
            Vector3f side = new Vector3f(-emitDir.z, 0f, emitDir.x).normalize();
            for (int k = -1; k <= 1; k++) {
                Vector3f aim = new Vector3f(target).add(new Vector3f(side).mul(k * 5f));
                addFinal(pr, aim, side);
            }
        } else {
            Vector3f side = new Vector3f(-emitDir.z, 0f, emitDir.x).normalize();
            addFinal(emitter, target, side);
        }
        int beamCount = segments.size() + finals.size();
        for (int i = 0; i < beamCount; i++) {
            beams.add(new Shapes.Beam(stage, g, Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, WHITE));
            Shapes.Line l = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
            preview.add(l);
        }
        for (Shapes.Beam b : beams) {
            b.hide(0);
        }
    }

    private void addPortal(Vector3f c, Vector3f normal, int pair) {
        Shapes.Ring rim = new Shapes.Ring(stage, g, stage.budget().scaled(14, 8), Material.WHITE_CONCRETE, PAIR[pair], 15, true);
        Shapes.Disc face = new Shapes.Disc(stage, g, PAIR_GLASS[pair], null, 15);
        portals.add(new Portal(c, normal.normalize(), rim, face));
    }

    private void addPrism(Vector3f c, Vector3f normal) {
        Shapes.Ring rim = new Shapes.Ring(stage, g, 8, Material.WHITE_CONCRETE, WHITE, 15, true);
        Shapes.Disc face = new Shapes.Disc(stage, g, Material.WHITE_STAINED_GLASS, WHITE, 15);
        portals.add(new Portal(c, new Vector3f(normal).negate(), rim, face));
    }

    /** The teleport link of a pair: a thread of the pair's colour from entry to exit. */
    private void link(Vector3f entry, Vector3f exit, int pair) {
        Shapes.Line l = new Shapes.Line(stage, g, PAIR_GLASS[pair], PAIR[pair]);
        l.hide(entry, 0);
        threads.add(l);
        threadEnds.add(new Vector3f[]{entry, exit});
    }

    private final List<Vector3f[]> threadEnds = new ArrayList<>();

    /** A floor beam that will sweep a 10-block lane across {@code aim}, perpendicular to its approach. */
    private void addFinal(Vector3f from, Vector3f aim, Vector3f side) {
        float dir = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
        Vector3f a = new Vector3f(aim).sub(new Vector3f(side).mul(5f * dir));
        Vector3f b = new Vector3f(aim).add(new Vector3f(side).mul(5f * dir));
        a.y = 0.05f;
        b.y = 0.05f;
        finals.add(new Vector3f[]{from, a, b});
        Shapes.Line lane = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
        lane.hide(a, 0);
        lanes.add(lane);
    }

    /* ------------------------------------------------------------------ timeline */

    @Override
    protected boolean tick() {
        int end = tell + fire;
        if (t < tell) {
            float f = HMath.window(t, 0, tell / 2);
            float spin = t * 0.25f;
            for (int i = 0; i < portals.size(); i++) {
                Portal p = portals.get(i);
                float r = 1.7f * HMath.outBack(HMath.window(t, i * 3, i * 3 + 12));
                p.rim.pose(p.c, p.q, r, 0.14f, 0.14f, spin, 0f, 0f, 2);
                p.face.set(p.c, r * 0.9f, 0.03f, p.q, -spin, 2);
                if (t == i * 3) {
                    enc.score().at(p.c, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f + i * 0.12f);
                }
            }
            if (t == tell / 3) {
                // Threads and the full path appear together: read it now.
                for (int i = 0; i < threads.size(); i++) {
                    Vector3f[] e = threadEnds.get(i);
                    threads.get(i).set(e[0], e[1], 0.05f, 6);
                }
                int k = 0;
                for (Vector3f[] s : segments) {
                    preview.get(k++).set(s[0], s[1], 0.035f, 6);
                }
                for (Vector3f[] fl : finals) {
                    preview.get(k++).set(fl[0], fl[1], 0.035f, 6);
                }
                for (int i = 0; i < lanes.size(); i++) {
                    lanes.get(i).set(finals.get(i)[1], finals.get(i)[2], 0.12f, 6);
                }
                enc.score().play(Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.9f, 1.2f);
                enc.score().sweep(null, Sound.ENTITY_GUARDIAN_ATTACK, 0.6f, 1f, 0.6f, 1.9f, tell - t, 4);
            }
            h.rig().lookAt(portals.get(0).c, 0.1f);
            return false;
        }
        if (t == tell) {
            for (Shapes.Line l : preview) {
                l.hide(portals.get(0).c, 3);
            }
            enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
            for (Portal p : portals) {
                enc.score().at(p.c, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
            }
            enc.camera().shakeAll(6, 2);
        }
        if (t < end) {
            float f = HMath.window(t, tell, end);
            float w = 0.55f + 0.2f * enc.star().beat();
            int k = 0;
            for (Vector3f[] s : segments) {
                beams.get(k++).set(s[0], s[1], w, 1);
                hurt(s[0], s[1]);
            }
            for (Vector3f[] fl : finals) {
                Vector3f aim = new Vector3f(fl[1]).lerp(fl[2], HMath.inOutCubic(f));
                beams.get(k++).set(fl[0], aim, w, 1);
                hurt(fl[0], aim);
                enc.arena().crackCell((int) Math.floor(aim.x), (int) Math.floor(aim.z), 0.8f, 100);
                if (t % 5 == 0) {
                    enc.score().at(aim, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.7f);
                }
            }
            float spin = t * 0.35f;
            for (Portal p : portals) {
                p.rim.pose(p.c, p.q, 1.8f + 0.15f * enc.star().beat(), 0.16f, 0.16f, spin, 0f, 0f, 1);
            }
            if (t % 20 == 0) {
                enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
            }
            return false;
        }
        if (t == end) {
            for (Shapes.Beam b : beams) {
                b.thin(4);
            }
            for (Shapes.Line l : lanes) {
                l.hide(portals.get(0).c, 4);
            }
            for (Shapes.Line l : threads) {
                l.hide(portals.get(0).c, 4);
            }
            for (Portal p : portals) {
                p.rim.pose(p.c, p.q, 0.05f, 0.1f, 0.1f, 0f, 0f, 0f, 8);
                p.face.set(p.c, 0.05f, 0.02f, p.q, 0f, 8);
                enc.score().at(p.c, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.5f);
            }
            h.rig().ringScale(1f);
        }
        return t > end + 10;
    }

    private void hurt(Vector3f a, Vector3f b) {
        double power = h.power("portals", 45);
        for (Player p : enc.fighters()) {
            if (HMath.bodyDistSq(stage.feet(p), 1.8f, a, b) < 0.9f * 0.9f) {
                enc.hit(p, power, "portal", 8, null);
                p.setFireTicks(Math.max(p.getFireTicks(), 20));
            }
        }
    }
}
