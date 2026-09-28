package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * BLADE SWARM. The four wing-blades leave his back and fly a formation. Every formation draws its
 * path on the floor first (hairlines, one bar of warning), then commits on the beat.
 *
 * <ul>
 *   <li><b>Spiral</b>: they circle a target on a shrinking, sinking spiral, then plunge into the
 *       centre. Leave the ring before it closes; the white disc marks the plunge.</li>
 *   <li><b>Fan</b>: laid end to end they become one long spoke at knee height that sweeps half the
 *       arena around him. The floor hairline shows where it starts and which way it turns: jump it.</li>
 *   <li><b>Pincer</b>: two pairs stacked head-high rush in from both sides along one line and clash on
 *       the target. Step off the line (forward or back), not sideways.</li>
 * </ul>
 */
final class BladeSwarm extends Attack {

    enum Formation { SPIRAL, FAN, PINCER }

    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);

    private final HeraldScript s;
    private Formation formation;
    private Player target;
    private final Vector3f center = new Vector3f();
    private final Vector3f[] start = new Vector3f[4];
    private int tell;
    private int run;
    private Shapes.Ring guide;
    private Shapes.Disc plunge;
    private Shapes.Line[] lines;
    private float fanFrom;
    private float fanDir;
    private float lastFanAngle;
    private final Vector3f pincerAxis = new Vector3f();

    BladeSwarm(HeraldScript s, Formation formation) {
        super(s);
        this.s = s;
        this.formation = formation;
    }

    @Override
    public String id() {
        return "swarm";
    }

    @Override
    public Family family() {
        return Family.SWEEP;
    }

    @Override
    public void start() {
        if (formation == null) {
            Formation[] all = Formation.values();
            formation = all[ThreadLocalRandom.current().nextInt(all.length)];
        }
        target = s.pickTarget(formation == Formation.PINCER);
        tell = enc.tempo().ticks(formation == Formation.FAN ? 3 : 2);
        run = enc.tempo().ticks(formation == Formation.SPIRAL ? 5 : formation == Formation.FAN ? 3 : 1.5);
        HeraldRig rig = s.rig();
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = rig.blade(i);
            b.free = true;
            start[i] = new Vector3f(b.pos);
        }
        if (target != null) {
            center.set(stage.feet(target));
            center.y = 0f;
        }
        guide = new Shapes.Ring(stage, g, 20, Material.ORANGE_STAINED_GLASS, AMBER, 15, true);
        plunge = new Shapes.Disc(stage, g, Material.WHITE_STAINED_GLASS, WHITE, 15);
        lines = new Shapes.Line[4];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
            lines[i].hide(center, 0);
        }
        guide.hide(center, 0);
        plunge.hide(center, 0);
        enc.score().at(rig.chestPoint(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.4f);
        enc.score().at(rig.chestPoint(), Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.8f);
        switch (formation) {
            case SPIRAL -> tellSpiral();
            case FAN -> tellFan();
            case PINCER -> tellPincer();
        }
    }

    @Override
    protected boolean tick() {
        if (target == null) {
            return true;
        }
        boolean done = switch (formation) {
            case SPIRAL -> spiral();
            case FAN -> fan();
            case PINCER -> pincer();
        };
        s.rig().renderBladesOnly(1);
        return done;
    }

    /* ------------------------------------------------------------------ spiral */

    private void tellSpiral() {
        guide.flat(new Vector3f(center).add(0f, 0.05f, 0f), 0.5f, 0.04f, 0.02f, 0f, 0);
        guide.flat(new Vector3f(center).add(0f, 0.05f, 0f), 6f, 0.06f, 0.02f, 0f, tell);
        enc.score().sweep(new Vector3f(center).add(0f, 1f, 0f), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1f, 0.8f, 1.6f, tell, 4);
    }

    private boolean spiral() {
        int plungeAt = tell + run;
        int end = plungeAt + enc.tempo().ticks(1.5);
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = s.rig().blade(i);
            float a = i * HMath.HALF_PI + t * 0.16f;
            if (t < tell) {
                // Fly out to the ring, points down.
                float f = HMath.outCubic(HMath.window(t, 0, tell));
                Vector3f ring = new Vector3f(center).add(HMath.ring(6f, a, 3f));
                b.pos.set(start[i]).lerp(ring, f);
                b.rot.set(new Quaternionf().rotateY(-a).rotateX(HMath.PI * f));
                b.hot = false;
            } else if (t < plungeAt) {
                float f = HMath.window(t, tell, plungeAt);
                float r = HMath.lerp(6f, 1.3f, HMath.inOutCubic(f));
                float y = HMath.lerp(3f, 1.2f, f);
                b.pos.set(center).add(HMath.ring(r, a, y));
                b.rot.set(new Quaternionf().rotateY(-a - HMath.HALF_PI).rotateZ(HMath.HALF_PI * 0.85f));
                b.hot = true;
                cut(b, "swarm_spiral");
            } else if (t < plungeAt + 4) {
                float f = HMath.window(t, plungeAt, plungeAt + 4);
                b.pos.set(center).add(HMath.ring(1.3f * (1f - f), a, 1.2f + 2.4f * f));
                b.rot.set(new Quaternionf().rotateX(HMath.PI));
            } else if (t < plungeAt + 7) {
                float f = HMath.inQuad(HMath.window(t, plungeAt + 4, plungeAt + 7));
                b.pos.set(center).add(HMath.ring(0.35f, a, 3.6f - 3.4f * f));
            } else {
                returnHome(b, i, HMath.window(t, plungeAt + 10, end));
            }
        }
        if (t == tell) {
            guide.flat(new Vector3f(center).add(0f, 0.05f, 0f), 1.3f, 0.06f, 0.02f, 0f, run);
            enc.score().at(new Vector3f(center).add(0f, 1f, 0f), Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1.2f);
        }
        if (t == plungeAt - enc.tempo().ticks(1)) {
            plunge.set(new Vector3f(center).add(0f, 0.04f, 0f), 0.3f, 0.03f, 0f, 0);
            plunge.set(new Vector3f(center).add(0f, 0.04f, 0f), 2.6f, 0.03f, 0f, enc.tempo().ticks(1));
            enc.score().at(center, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
        }
        if (t == plungeAt + 7) {
            guide.hide(center, 4);
            plunge.hide(center, 6);
            enc.score().at(center, Sound.ITEM_TRIDENT_HIT_GROUND, 1.2f, 0.7f);
            enc.score().at(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1.2f);
            enc.camera().shakeFrom(center, 8f, 6);
            for (int i = -2; i <= 2; i++) {
                enc.arena().crackCell((int) Math.floor(center.x) + i, (int) Math.floor(center.z), 0.5f, 60);
                enc.arena().crackCell((int) Math.floor(center.x), (int) Math.floor(center.z) + i, 0.5f, 60);
            }
            for (Player p : enc.fighters()) {
                Vector3f f = stage.feet(p);
                if (f.distance(center) < 2.7f && f.y < 3f) {
                    enc.hit(p, s.power("swarm", 55) * 1.2, "swarm_plunge", 10, HeraldScript.away(center, f, 0.8));
                }
            }
        }
        return t >= end;
    }

    /* ------------------------------------------------------------------ fan */

    private void tellFan() {
        HeraldRig rig = s.rig();
        center.set(rig.root);
        center.y = 0f;
        Vector3f c = s.partyCentroid();
        float toParty = HMath.angleOf(c.x - center.x, c.z - center.z);
        fanDir = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
        fanFrom = toParty - fanDir * 1.75f;
        lastFanAngle = fanFrom;
        // The spoke's start line, and a short arrow of three hairlines showing the turn.
        lines[0].set(new Vector3f(center).add(0f, 0.05f, 0f), new Vector3f(center).add(HMath.ring(27f, fanFrom, 0.05f)), 0.05f, tell / 2);
        for (int i = 1; i < 4; i++) {
            float a = fanFrom + fanDir * 0.16f * i;
            lines[i].set(new Vector3f(center).add(HMath.ring(6f * i, a, 0.05f)), new Vector3f(center).add(HMath.ring(6f * i + 3f, a, 0.05f)), 0.04f, tell / 2);
        }
        enc.score().sweep(new Vector3f(center).add(0f, 1f, 0f), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1f, 0.7f, 1.4f, tell, enc.tempo().ticks(1));
        s.rig().pose(HeraldRig.Pose.point(-0.2f), 0.2f);
    }

    private boolean fan() {
        int end = tell + run + enc.tempo().ticks(1.5);
        float sweep = 3.5f;
        float angle;
        if (t < tell) {
            angle = fanFrom;
        } else {
            angle = fanFrom + fanDir * sweep * HMath.inOutCubic(HMath.window(t, tell, tell + run));
        }
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = s.rig().blade(i);
            if (t < tell) {
                float f = HMath.outCubic(HMath.window(t, 0, tell));
                Vector3f to = new Vector3f(center).add(HMath.ring(2f + i * 6.4f, angle, 0.55f));
                b.pos.set(start[i]).lerp(to, f);
                b.rot.set(spoke(angle));
                b.scale = HMath.lerp(0.85f, 2.8f, f);
            } else if (t < tell + run) {
                b.pos.set(center).add(HMath.ring(2f + i * 6.4f, angle, 0.55f));
                b.rot.set(spoke(angle));
                b.hot = true;
            } else {
                b.scale = HMath.lerp(2.8f, 0.85f, HMath.window(t, tell + run, end));
                returnHome(b, i, HMath.window(t, tell + run, end));
            }
        }
        if (t == tell) {
            for (Shapes.Line l : lines) {
                l.hide(center, 4);
            }
            enc.score().at(new Vector3f(center).add(0f, 1f, 0f), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.6f);
            enc.score().sweep(new Vector3f(center).add(0f, 1f, 0f), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.9f, 0.6f, 1.1f, run, 3);
        }
        if (t >= tell && t < tell + run) {
            // Swept test: anyone whose bearing the spoke passed this tick, and who is not in the air.
            float from = Math.min(lastFanAngle, angle);
            float to = Math.max(lastFanAngle, angle);
            for (Player p : enc.fighters()) {
                Vector3f f = stage.feet(p);
                float d = HMath.horizontal(new Vector3f(f).sub(center));
                if (d < 1.5f || d > 28f || f.y > 0.9f) {
                    continue;
                }
                float bearing = HMath.angleOf(f.x - center.x, f.z - center.z);
                if (sweptOver(from, to, bearing)) {
                    enc.hit(p, s.power("swarm", 55), "swarm_fan", 12, HeraldScript.away(center, f, 0.5));
                }
            }
        }
        lastFanAngle = angle;
        if (t == tell + run) {
            s.rig().pose(HeraldRig.Pose.guard(), 0.2f);
        }
        return t >= end;
    }

    /** Blade lying flat along the spoke direction, edge forward. */
    private static Quaternionf spoke(float angle) {
        return new Quaternionf().rotateY(-angle).rotateZ(-HMath.HALF_PI);
    }

    private static boolean sweptOver(float from, float to, float bearing) {
        // Normalise the bearing into the unwrapped [from, to] window.
        float b = bearing;
        while (b < from) {
            b += HMath.TAU;
        }
        while (b > from + HMath.TAU) {
            b -= HMath.TAU;
        }
        return b >= from && b <= to;
    }

    /* ------------------------------------------------------------------ pincer */

    private void tellPincer() {
        HeraldRig rig = s.rig();
        Vector3f toT = new Vector3f(center).sub(rig.root);
        toT.y = 0f;
        if (toT.lengthSquared() < 1e-3f) {
            toT.set(1f, 0f, 0f);
        }
        toT.normalize();
        pincerAxis.set(-toT.z, 0f, toT.x);
        Vector3f a = new Vector3f(center).add(new Vector3f(pincerAxis).mul(10f)).add(0f, 0.05f, 0f);
        Vector3f b = new Vector3f(center).sub(new Vector3f(pincerAxis).mul(10f)).add(0f, 0.05f, 0f);
        lines[0].set(a, b, 0.05f, tell / 2);
        lines[1].set(new Vector3f(center).add(0f, 0.05f, 0f), new Vector3f(center).add(0f, 2.2f, 0f), 0.03f, tell / 2);
        enc.score().at(a, Sound.BLOCK_CHAIN_HIT, 1f, 0.8f);
        enc.score().at(b, Sound.BLOCK_CHAIN_HIT, 1f, 0.8f);
        enc.score().sweep(new Vector3f(center).add(0f, 1f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.1f, 1f, 2f, tell, 3);
    }

    private boolean pincer() {
        int clash = tell + run;
        int end = clash + enc.tempo().ticks(2);
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = s.rig().blade(i);
            float side = i < 2 ? 1f : -1f;
            float height = i % 2 == 0 ? 0.5f : 1.5f;
            Vector3f far = new Vector3f(center).add(new Vector3f(pincerAxis).mul(side * 10f)).add(0f, height, 0f);
            Quaternionf rot = HMath.frame(new Vector3f(pincerAxis).mul(-side), new Vector3f(0f, 1f, 0f)).rotateX(HMath.HALF_PI);
            if (t < tell) {
                float f = HMath.outCubic(HMath.window(t, 0, tell));
                b.pos.set(start[i]).lerp(far, f);
                b.rot.set(rot);
            } else if (t < clash) {
                float f = HMath.inQuad(HMath.window(t, tell, clash));
                Vector3f near = new Vector3f(center).add(new Vector3f(pincerAxis).mul(side * 1.2f)).add(0f, height, 0f);
                b.pos.set(far).lerp(near, f);
                b.rot.set(rot);
                b.hot = true;
                cut(b, "swarm_pincer");
            } else {
                returnHome(b, i, HMath.window(t, clash + 6, end));
            }
        }
        if (t == tell) {
            enc.score().at(new Vector3f(center).add(0f, 1f, 0f), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.2f);
        }
        if (t == clash) {
            for (Shapes.Line l : lines) {
                l.hide(center, 4);
            }
            enc.score().at(center, Sound.BLOCK_ANVIL_LAND, 1f, 1.5f);
            enc.score().at(center, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.8f);
            stage.particle(org.bukkit.Particle.ELECTRIC_SPARK, new Vector3f(center).add(0f, 1f, 0f), 30, 0.4, 0.4);
            for (Player p : enc.fighters()) {
                Vector3f f = stage.feet(p);
                if (f.distance(center) < 1.8f && f.y < 2.5f) {
                    enc.hit(p, s.power("swarm", 55) * 1.3, "swarm_pincer", 10, HeraldScript.away(center, f, 0.6));
                }
            }
        }
        return t >= end;
    }

    /* ------------------------------------------------------------------ shared */

    private void cut(HeraldRig.Blade b, String key) {
        Vector3f tip = b.tip();
        for (Player p : enc.fighters()) {
            if (HMath.bodyDistSq(stage.feet(p), 1.8f, b.pos, tip) < 0.36f) {
                enc.hit(p, s.power("swarm", 55), key, 10, HeraldScript.away(b.pos, stage.feet(p), 0.5));
            }
        }
    }

    private void returnHome(HeraldRig.Blade b, int i, float f) {
        b.hot = false;
        if (f >= 1f) {
            b.free = false;
            return;
        }
        Vector3f home = new Vector3f(s.rig().chestPoint()).add(0f, 0.2f, 0f);
        b.pos.lerp(home, HMath.smooth(f) * 0.35f);
    }

    @Override
    protected void cleanup() {
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = s.rig().blade(i);
            b.free = false;
            b.hot = false;
            b.scale = 0.85f;
        }
    }
}
