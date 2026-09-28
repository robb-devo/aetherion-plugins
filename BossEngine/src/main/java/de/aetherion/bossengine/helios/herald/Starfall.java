package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * STARFALL. The Herald's show-stopper: he crouches, leaps up into the star's light and hangs there,
 * hunting. An amber ring on the floor follows one player (a little slower than they can run) while a
 * white hairline drops from him onto it and a chime climbs. Half a beat before he falls the ring turns
 * white and locks. He dives onto it like a meteor: crater, cracks, and a flat shockwave that rolls out
 * across the floor (jump it). Then he is down on one knee for a beat. Below half health he dives twice,
 * enraged three times, each dive hunting someone else.
 *
 * <p>Read: keep moving while the ring hunts you, step out of the white ring when it locks, jump the wave.
 */
final class Starfall extends Attack {

    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);
    private static final float HEIGHT = 9f;
    private static final float CRATER = 3.2f;
    private static final float WAVE_MAX = 15f;

    private final HeraldScript s;
    private final int dives;
    private int dive;
    private int local;
    private Player target;
    private final Vector3f mark = new Vector3f();
    private final Vector3f lift = new Vector3f();
    private Shapes.Ring reticle;
    private Shapes.Ring inner;
    private Shapes.Line column;
    private Shapes.Ring wave;
    private Shapes.Disc crater;

    private int windup;
    private int rise;
    private int hunt;
    private int lock;
    private int fall;
    private int recover;
    private int waveLen;
    private int impactAt = -1;
    private final Vector3f impact = new Vector3f();

    Starfall(HeraldScript s, int dives) {
        super(s);
        this.s = s;
        this.dives = Math.max(1, dives);
    }

    @Override
    public String id() {
        return "starfall";
    }

    @Override
    public Family family() {
        return Family.BODY;
    }

    @Override
    public void start() {
        windup = 6;
        rise = Math.max(12, enc.tempo().ticks(1.25));
        hunt = enc.tempo().ticks(2);
        lock = Math.max(6, enc.tempo().ticks(0.5));
        fall = 4;
        recover = enc.tempo().ticks(1.25);
        waveLen = enc.tempo().ticks(1.5);
        reticle = new Shapes.Ring(stage, g, 22, Material.ORANGE_STAINED_GLASS, AMBER, 15, true);
        inner = new Shapes.Ring(stage, g, 12, Material.YELLOW_STAINED_GLASS, AMBER, 15, true);
        column = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
        wave = new Shapes.Ring(stage, g, stage.budget().scaled(40, 24), Material.WHITE_CONCRETE, WHITE, 15, true);
        crater = new Shapes.Disc(stage, g, Material.WHITE_STAINED_GLASS, WHITE, 15);
        Vector3f o = new Vector3f(s.rig().root);
        reticle.hide(o, 0);
        inner.hide(o, 0);
        column.hide(o, 0);
        wave.hide(o, 0);
        crater.hide(o, 0);
        s.fast(true);
        nextDive();
    }

    /** Each dive hunts the player farthest from him, never the same one twice in a row (if there is a choice). */
    private void nextDive() {
        local = 0;
        Player previous = target;
        java.util.List<Player> fighters = enc.fighters();
        target = null;
        float best = -1f;
        for (Player p : fighters) {
            if (p == previous && fighters.size() > 1) {
                continue;
            }
            float d = stage.feet(p).distanceSquared(s.rig().root);
            if (d > best) {
                best = d;
                target = p;
            }
        }
        if (target == null) {
            target = previous;
        }
        lift.set(s.rig().root);
    }

    @Override
    protected boolean tick() {
        HeraldRig rig = s.rig();
        int huntFrom = windup + rise;
        int lockAt = huntFrom + hunt;
        int fallAt = lockAt + lock;
        int hitAt = fallAt + fall;
        int end = hitAt + recover;
        tickWave();

        if (target != null && (!target.isValid() || target.isDead())) {
            target = s.pickTarget(false);
        }
        if (local == 0) {
            HeraldRig.Pose p = HeraldRig.Pose.guard();
            p.crouch = 0.9f;
            p.lean = 0.35f;
            p.hover = 0f;
            rig.pose(p, 0.35f);
            enc.score().at(rig.chestPoint(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
            enc.score().sweep(new Vector3f(rig.root).add(0f, 2f, 0f), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.1f, 0.6f, 1.4f, huntFrom, 3);
        }
        if (local == windup) {
            // The leap: up into the star's light.
            rig.pose(HeraldRig.Pose.raise(), 0.3f);
            rig.flare(15);
            rig.coreHeat(1f);
            enc.score().at(rig.root, Sound.ENTITY_BREEZE_JUMP, 1.2f, 0.6f);
            enc.score().at(rig.root, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);
            enc.camera().shakeFrom(rig.root, 10f, 4);
            stage.blockDust(new Vector3f(rig.root).add(0f, 0.2f, 0f), Material.POLISHED_BLACKSTONE, 18, 0.8);
        }
        if (local >= windup && local < huntFrom) {
            float f = HMath.outCubic(HMath.window(local, windup, huntFrom));
            Vector3f want = target == null ? new Vector3f(lift) : stage.feet(target);
            rig.root.x = HMath.lerp(lift.x, want.x, f * 0.6f);
            rig.root.z = HMath.lerp(lift.z, want.z, f * 0.6f);
            rig.root.y = HEIGHT * f;
            if (target != null) {
                rig.faceToward(stage.feet(target), 0.3f);
            }
        }
        if (local == huntFrom) {
            mark.set(target == null ? rig.root : stage.feet(target));
            mark.y = 0f;
            enc.score().at(new Vector3f(mark).add(0f, 1f, 0f), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
            if (target != null) {
                enc.score().to(target, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.7f);
                target.sendActionBar(TextUtil.component("&6&lThe Herald hunts you. &eKeep moving!"));
            }
            HeraldRig.Pose p = HeraldRig.Pose.point(-1.2f);
            p.hover = 0.2f;
            rig.pose(p, 0.25f);
        }
        if (local >= huntFrom && local < lockAt) {
            // The reticle hunts: a little slower than a walk, so you can drag it but not lose it.
            if (target != null) {
                Vector3f want = stage.feet(target);
                want.y = 0f;
                Vector3f step = new Vector3f(want).sub(mark);
                float d = step.length();
                float max = 0.2f;
                if (d > max) {
                    step.mul(max / d);
                }
                mark.add(step);
            }
            float f = HMath.window(local, huntFrom, lockAt);
            rig.root.x = HMath.lerp(rig.root.x, mark.x, 0.3f);
            rig.root.z = HMath.lerp(rig.root.z, mark.z, 0.3f);
            rig.root.y = HEIGHT + 0.25f * (float) Math.sin(local * 0.3f);
            float r = HMath.lerp(4.2f, CRATER + 0.2f, HMath.smooth(f));
            if (local % 2 == 0) {
                reticle.flat(new Vector3f(mark).add(0f, 0.06f, 0f), r, 0.16f, 0.04f, local * 0.05f, 2);
                inner.flat(new Vector3f(mark).add(0f, 0.07f, 0f), 0.8f + 0.5f * (1f - f), 0.08f, 0.04f, -local * 0.08f, 2);
                column.set(new Vector3f(mark).add(0f, 0.1f, 0f), new Vector3f(rig.root).add(0f, 1.2f, 0f), 0.03f + 0.04f * f, 2);
            }
            int step = Math.max(3, enc.tempo().ticks(0.5));
            if ((local - huntFrom) % step == 0) {
                int k = (local - huntFrom) / step;
                enc.score().at(new Vector3f(mark).add(0f, 1f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, Score.semi(k * 2));
            }
        }
        if (local == lockAt) {
            // Locked: the ring goes white and stops. Get out of it.
            if (!enc.arena().solidAt(mark.x, mark.z)) {
                mark.set(enc.arena().safeSpot(mark));
            }
            reticle.material(Material.WHITE_CONCRETE);
            reticle.glow(WHITE);
            crater.hide(new Vector3f(mark).add(0f, 0.05f, 0f), 0);
            wave.hide(new Vector3f(mark).add(0f, 0.3f, 0f), 0);
            reticle.flat(new Vector3f(mark).add(0f, 0.06f, 0f), CRATER, 0.22f, 0.05f, 0f, 2);
            inner.hide(mark, 3);
            column.set(new Vector3f(mark).add(0f, 0.1f, 0f), new Vector3f(mark).add(0f, HEIGHT + 1.2f, 0f), 0.14f, 3);
            enc.score().at(new Vector3f(mark).add(0f, 1f, 0f), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            enc.score().at(new Vector3f(mark).add(0f, 1f, 0f), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
            rig.root.x = mark.x;
            rig.root.z = mark.z;
            HeraldRig.Pose p = HeraldRig.Pose.slashWindup();
            p.lean = 0.9f;
            p.hover = 0.3f;
            rig.pose(p, 0.4f);
        }
        if (local >= fallAt && local < hitAt) {
            float f = HMath.inQuad(HMath.window(local, fallAt, hitAt));
            rig.root.set(mark.x, HEIGHT * (1f - f), mark.z);
            if (local == fallAt) {
                enc.score().at(new Vector3f(mark).add(0f, 4f, 0f), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.7f);
                rig.pose(HeraldRig.Pose.slash(), 0.6f);
            }
        }
        if (local == hitAt) {
            land(rig);
        }
        if (local == hitAt + 4) {
            reticle.hide(mark, 6);
            column.hide(mark, 4);
        }
        local++;
        if (local >= end) {
            dive++;
            if (dive >= dives) {
                // The last wave finishes rolling out before the move is over.
                return impactAt < 0 || t - impactAt >= waveLen;
            }
            reticle.material(Material.ORANGE_STAINED_GLASS);
            reticle.glow(AMBER);
            nextDive();
        }
        return false;
    }

    private void land(HeraldRig rig) {
        rig.root.set(mark.x, 0f, mark.z);
        HeraldRig.Pose p = HeraldRig.Pose.kneel();
        p.crouch = 0.85f;
        p.lean = 0.5f;
        rig.snap(p);
        rig.flare(15);
        impact.set(mark);
        impactAt = t;
        crater.set(new Vector3f(mark).add(0f, 0.05f, 0f), CRATER + 0.3f, 0.04f, 0.4f, 3);
        enc.score().at(mark, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.6f);
        enc.score().at(mark, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        enc.score().at(mark, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.9f);
        enc.score().at(mark, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.5f);
        enc.camera().shakeFrom(mark, 22f, 12);
        enc.camera().tint(Material.ORANGE_STAINED_GLASS, 1, 4);
        stage.blockDust(new Vector3f(mark).add(0f, 0.3f, 0f), Material.POLISHED_BLACKSTONE, 40, 1.6);
        stage.particle(org.bukkit.Particle.FLASH, new Vector3f(mark).add(0f, 0.5f, 0f), 1, 0, 0);
        int cx = (int) Math.floor(mark.x);
        int cz = (int) Math.floor(mark.z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 10) {
                    enc.arena().crackCell(cx + dx, cz + dz, 0.75f - 0.05f * (Math.abs(dx) + Math.abs(dz)), 140);
                }
            }
        }
        double power = s.power("starfall", 60);
        for (Player pl : enc.fighters()) {
            Vector3f f = stage.feet(pl);
            float d = HMath.horizontal(new Vector3f(f).sub(mark));
            if (d <= CRATER && f.y < 2.5f) {
                enc.hit(pl, power, "starfall", 10, HeraldScript.away(mark, f, 0.9));
            }
        }
    }

    /** The floor shockwave from the last impact: a flat white ring rolling outward. Jump it. */
    private void tickWave() {
        if (impactAt < 0) {
            return;
        }
        int age = t - impactAt;
        if (age == 8) {
            crater.hide(impact, 8);
        }
        if (age > waveLen + 2) {
            return;
        }
        float f = HMath.outQuad(HMath.window(age, 0, waveLen));
        float r = HMath.lerp(CRATER, WAVE_MAX, f);
        if (age >= waveLen) {
            wave.hide(impact, 3);
            return;
        }
        float tall = 0.6f * (1f - f) + 0.2f;
        wave.pose(new Vector3f(impact).add(0f, 0.3f, 0f), new Quaternionf(), r, 0.35f, tall, age * 0.04f, 0f, 0f, 1);
        double power = s.power("starfall.wave", 30);
        for (Player p : enc.fighters()) {
            Vector3f f0 = stage.feet(p);
            float d = HMath.horizontal(new Vector3f(f0).sub(impact));
            // Only once it has left the crater (whoever stood in the crater already took the dive).
            if (r > CRATER + 0.5f && Math.abs(d - r) < 0.9f && f0.y < 0.6f) {
                enc.hit(p, power, "starfall_wave", 12, HeraldScript.away(impact, f0, 0.6));
            }
        }
    }

    @Override
    protected void cleanup() {
        HeraldRig rig = s.rig();
        rig.root.y = 0f;
        rig.flare(15);
        s.fast(false);
    }

    @Override
    public int recovery() {
        return enc.tempo().ticks(1);
    }
}
