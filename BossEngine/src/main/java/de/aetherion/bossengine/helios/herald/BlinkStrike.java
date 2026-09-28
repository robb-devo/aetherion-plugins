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

/**
 * BLINK-STRIKE. He winds up (a chime climbs), vanishes into a vertical line of light, and a second
 * line stands where he will land: right behind the target, on the far side. Three floor hairlines
 * fan out from it to show the cut. On the beat he is there and the cut lands (110° cone), leaving a
 * white-hot arc. Below half health he chains two or three.
 *
 * <p>Read: watch the landing line, step out of the fan (or through him). Sound alone works too: the
 * chime ticks from where he will appear.
 */
final class BlinkStrike extends Attack {

    private static final Color WHITE = Color.fromRGB(255, 244, 214);
    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final float REACH = 4.6f;
    private static final float HALF_CONE = 0.96f;

    private final HeraldScript s;
    private final int chains;
    private int chain;
    private int local;
    private int windup;
    private int mark;
    private int recover;
    private Player target;
    private final Vector3f landing = new Vector3f();
    private final Vector3f aim = new Vector3f();
    private Shapes.Line ghostLine;
    private Shapes.Line landLine;
    private final Shapes.Line[] fan = new Shapes.Line[3];
    private Shapes.Ring arc;
    private Shapes.Disc pad;

    BlinkStrike(HeraldScript s, int chains) {
        super(s);
        this.s = s;
        this.chains = Math.max(1, chains);
    }

    @Override
    public String id() {
        return "blink";
    }

    @Override
    public Family family() {
        return Family.BODY;
    }

    @Override
    public void start() {
        windup = enc.tempo().ticks(1.5);
        mark = enc.tempo().ticks(2);
        recover = enc.tempo().ticks(1.5);
        ghostLine = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
        landLine = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
        for (int i = 0; i < fan.length; i++) {
            fan[i] = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
        }
        arc = new Shapes.Ring(stage, g, 9, Material.WHITE_CONCRETE, WHITE, 15, true);
        pad = new Shapes.Disc(stage, g, Material.YELLOW_STAINED_GLASS, AMBER, 15);
        hideAll(0);
        s.fast(true);
        next();
    }

    private void next() {
        local = 0;
        target = s.pickTarget(chain == 0);
    }

    @Override
    protected boolean tick() {
        if (target == null || !target.isValid()) {
            return true;
        }
        HeraldRig rig = s.rig();
        int t0 = windup;
        int t1 = windup + mark;
        int t2 = t1 + 6;
        int end = t2 + recover;
        if (local == 0) {
            rig.pose(HeraldRig.Pose.slashWindup(), 0.3f);
            enc.score().sweep(new Vector3f(rig.root).add(0f, 2f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f, 0.9f, 2f, t1, 2);
        }
        if (local < t0) {
            rig.faceToward(stage.feet(target), 0.2f);
            // Shimmer: the visor flickers while he gathers.
            rig.flare(local % 2 == 0 ? 15 : 8);
        }
        if (local == t0) {
            lockLanding(rig);
            Vector3f old = new Vector3f(rig.root);
            ghostLine.set(old, new Vector3f(old).add(0f, 3.4f, 0f), 0.18f, 0);
            ghostLine.set(old, new Vector3f(old).add(0f, 3.4f, 0f), 0.01f, 8);
            rig.visible(false);
            enc.score().at(new Vector3f(old).add(0f, 1.5f, 0f), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.7f);
            landLine.set(landing, new Vector3f(landing).add(0f, 3.4f, 0f), 0.02f, 0);
            landLine.set(landing, new Vector3f(landing).add(0f, 3.4f, 0f), 0.08f, mark);
            pad.set(new Vector3f(landing).add(0f, 0.03f, 0f), 0.2f, 0.02f, 0f, 0);
            pad.set(new Vector3f(landing).add(0f, 0.03f, 0f), 0.7f, 0.02f, 0f, mark);
            float yaw = HMath.angleOf(aim.x - landing.x, aim.z - landing.z);
            for (int i = 0; i < fan.length; i++) {
                float a = yaw + (i - 1) * HALF_CONE;
                Vector3f tip = new Vector3f(landing).add(HMath.ring(REACH, a, 0.04f));
                fan[i].set(new Vector3f(landing).add(0f, 0.04f, 0f), tip, 0.04f, 4);
            }
        }
        if (local > t0 && local < t1 && (local - t0) % 3 == 0) {
            enc.score().at(new Vector3f(landing).add(0f, 1f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
        }
        if (local == t1) {
            strike(rig);
        }
        if (local == t1 + 3) {
            // Hitstop over: the arc flies out and dies.
            float yaw = HMath.angleOf(aim.x - landing.x, aim.z - landing.z);
            arc.arc(new Vector3f(landing).add(0f, 1.1f, 0f), new Quaternionf(), REACH + 1.4f, 0.05f, 0.05f,
                    yaw - HALF_CONE, yaw + HALF_CONE, 6);
            hideMarkers(4);
            rig.pose(HeraldRig.Pose.guard(), 0.2f);
        }
        if (local == t2) {
            arc.hide(new Vector3f(landing).add(0f, 1.1f, 0f), 4);
        }
        local++;
        if (local >= end) {
            chain++;
            if (chain >= chains) {
                return true;
            }
            next();
        }
        return false;
    }

    private void lockLanding(HeraldRig rig) {
        Vector3f tf = stage.feet(target);
        aim.set(tf);
        Vector3f dir = new Vector3f(tf).sub(rig.root);
        dir.y = 0f;
        if (dir.lengthSquared() < 1e-3f) {
            dir.set(0f, 0f, 1f);
        }
        dir.normalize();
        landing.set(tf).add(new Vector3f(dir).mul(2.4f));
        landing.y = 0f;
        if (!enc.arena().solidAt(landing.x, landing.z)) {
            // No floor behind them: he lands in front instead.
            landing.set(tf).sub(new Vector3f(dir).mul(2.4f));
            landing.y = 0f;
        }
        rig.root.set(landing);
        rig.face = (float) Math.atan2(aim.x - landing.x, aim.z - landing.z);
    }

    private void strike(HeraldRig rig) {
        rig.visible(true);
        rig.snap(HeraldRig.Pose.slash());
        rig.flare(15);
        for (int i = 0; i < 4; i++) {
            if (!rig.blade(i).free) {
                rig.blade(i).hot = true;
            }
        }
        float yaw = HMath.angleOf(aim.x - landing.x, aim.z - landing.z);
        arc.arc(new Vector3f(landing).add(0f, 1.1f, 0f), new Quaternionf(), REACH, 0.12f, 0.12f,
                yaw - HALF_CONE, yaw + HALF_CONE, 1);
        enc.score().at(landing, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        enc.score().at(landing, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.3f);
        enc.score().at(landing, Sound.ENTITY_BREEZE_WIND_BURST, 0.7f, 0.8f);
        stage.particle(org.bukkit.Particle.SWEEP_ATTACK, new Vector3f(landing).add(0f, 1.2f, 0f), 3, 0.8, 0);
        double power = s.power("blink", 70);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            Vector3f d = new Vector3f(f).sub(landing);
            float dy = d.y;
            d.y = 0f;
            float dist = d.length();
            if (dist > REACH || dy > 3f || dy < -1.5f) {
                continue;
            }
            float ang = Math.abs(HMath.wrap(HMath.angleOf(d.x, d.z) - yaw));
            if (dist < 1.2f || ang <= HALF_CONE) {
                enc.hit(p, power, "blink", 10, HeraldScript.away(landing, f, 0.9));
            }
        }
        for (int i = 0; i < 4; i++) {
            if (!rig.blade(i).free) {
                rig.blade(i).hot = false;
            }
        }
    }

    private void hideMarkers(int interp) {
        landLine.hide(landing, interp);
        pad.hide(landing, interp);
        for (Shapes.Line l : fan) {
            l.hide(landing, interp);
        }
        ghostLine.hide(landing, interp);
    }

    private void hideAll(int interp) {
        Vector3f o = new Vector3f();
        landLine.hide(o, interp);
        ghostLine.hide(o, interp);
        pad.hide(o, interp);
        for (Shapes.Line l : fan) {
            l.hide(o, interp);
        }
        arc.hide(o, interp);
    }

    @Override
    protected void cleanup() {
        s.rig().visible(true);
        s.fast(false);
        s.rig().flare(15);
    }

    @Override
    public int recovery() {
        return enc.tempo().ticks(1.5);
    }
}
