package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SEISMIC BOMB. A dark, heavy, metallic charge (a heavy core, banded with a pulsing amber ring and
 * four studs) sinks out of Helios on a long, falling whine and lands with a dead clank. It arms on
 * three pulses, each one deeper. A thin ring on the floor shows how far the blast will run.
 *
 * <p>Then the sound drops out: one full beat of total silence. Then the detonation, pitched far
 * down and stretched: a flat, white-hot shockwave races out along the floor and fissures tear open
 * behind it. The wave only hurts what touches the ground: <b>jump as it reaches you</b>. The silence is
 * the tell; the wave is fast.
 */
final class SeismicBomb extends Attack {

    private static final Color AMBER = Color.fromRGB(255, 150, 40);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);
    private static final float WAVE_SPEED = 1.1f;

    private final HeliosScript h;
    private final Vector3f target;
    private final int armBeats;
    private final Vector3f pos = new Vector3f();
    private final Vector3f from = new Vector3f();
    private BlockDisplay shell;
    private final BlockDisplay[] studs = new BlockDisplay[4];
    private Shapes.Ring band;
    private Shapes.Ring reach;
    private Shapes.Ring wave;
    private Shapes.Ring waveGlow;
    private int drop;
    private int armed;
    private int silentAt;
    private int boomAt;
    private float radius;
    private final List<Float> fissures = new ArrayList<>();

    SeismicBomb(HeliosScript h, Vector3f target, int armBeats) {
        super(h);
        this.h = h;
        this.target = target;
        this.armBeats = Math.max(2, armBeats);
    }

    @Override
    public String id() {
        return "seismic";
    }

    @Override
    public Family family() {
        return Family.GROUND;
    }

    @Override
    public void start() {
        radius = (float) enc.config().d("helios.seismic.radius", 20.0);
        from.set(h.rig().center).add(0f, -1.5f, 0f);
        if (target != null) {
            pos.set(target);
        } else {
            Vector3f c = h.partyCentroid();
            pos.set(c).add((ThreadLocalRandom.current().nextFloat() - 0.5f) * 6f, 0f, (ThreadLocalRandom.current().nextFloat() - 0.5f) * 6f);
            if (!enc.arena().solidAt(pos.x, pos.z)) {
                pos.set(enc.arena().safeSpot(pos));
            }
        }
        pos.y = 0f;
        drop = enc.tempo().ticks(2);
        armed = drop + enc.tempo().ticks(armBeats);
        silentAt = armed;
        boomAt = armed + enc.tempo().ticks(1);
        shell = g.block(Material.HEAVY_CORE.createBlockData(), null, 6, true);
        for (int i = 0; i < studs.length; i++) {
            studs[i] = g.block(Material.OCHRE_FROGLIGHT.createBlockData(), AMBER, 15, true);
        }
        band = new Shapes.Ring(stage, g, 12, Material.ORANGE_STAINED_GLASS, AMBER, 15, true);
        reach = new Shapes.Ring(stage, g, stage.budget().scaled(40, 24), Material.RED_STAINED_GLASS, null, 12, true);
        wave = new Shapes.Ring(stage, g, stage.budget().scaled(44, 26), Material.WHITE_CONCRETE, WHITE, 15, true);
        waveGlow = new Shapes.Ring(stage, g, stage.budget().scaled(44, 26), Material.WHITE_STAINED_GLASS, null, 15, true);
        reach.hide(pos, 0);
        wave.hide(pos, 0);
        waveGlow.hide(pos, 0);
        for (int i = 0; i < 7; i++) {
            fissures.add(HMath.hash(i, (int) (pos.x * 7)) * HMath.TAU);
        }
        // The long falling whine as it sinks out of Helios.
        enc.score().sweep(from, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.2f, 1.2f, 0.5f, drop, 3);
        enc.score().at(from, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.5f);
    }

    @Override
    protected boolean tick() {
        float pulse;
        Vector3f at;
        if (t < drop) {
            float f = HMath.window(t, 0, drop);
            at = new Vector3f(from).lerp(new Vector3f(pos).add(0f, 0.7f, 0f), HMath.inCubic(f));
            pulse = 0.3f;
        } else {
            at = new Vector3f(pos).add(0f, 0.7f, 0f);
            pulse = HMath.heartbeat(enc.tempo().phase());
        }
        if (t == drop) {
            enc.score().at(pos, Sound.BLOCK_HEAVY_CORE_PLACE, 1.4f, 0.5f);
            enc.score().at(pos, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            enc.camera().shakeFrom(pos, 10f, 4);
            reach.flat(new Vector3f(pos).add(0f, 0.04f, 0f), 1f, 0.12f, 0.03f, 0f, 0);
            reach.flat(new Vector3f(pos).add(0f, 0.04f, 0f), radius, 0.12f, 0.03f, 0f, enc.tempo().ticks(1));
        }
        if (t > drop && t < armed && enc.tempo().onBeat()) {
            // Each arming pulse is deeper than the last.
            float depth = HMath.window(t, drop, armed);
            enc.score().at(pos, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.9f - depth * 0.4f);
            enc.score().at(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
            enc.score().at(pos, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
        if (t < boomAt) {
            Quaternionf rot = new Quaternionf().rotateY(t * 0.08f).rotateX(0.2f);
            stage.push(shell, HeliosStage.cube(at, 1.35f, rot), 2);
            for (int i = 0; i < studs.length; i++) {
                Vector3f s = new Quaternionf(rot).transform(HMath.ring(0.72f, i * HMath.HALF_PI, 0f)).add(at);
                stage.push(studs[i], HeliosStage.cube(s, 0.22f + 0.12f * pulse, rot), 2);
            }
            band.pose(at, rot, 0.78f, 0.1f + 0.08f * pulse, 0.18f, 0f, 0f, 0f, 2);
            HeliosStage.brightness(shell, 4 + (int) (6 * pulse));
        }
        if (t == silentAt) {
            // The drop-out: one beat of nothing at all.
            enc.score().silence(boomAt - silentAt);
        }
        if (t == boomAt) {
            detonate();
        }
        if (t > boomAt) {
            float r = (t - boomAt) * WAVE_SPEED;
            float w = 0.35f + 0.15f * (1f - r / radius);
            wave.flat(new Vector3f(pos).add(0f, 0.25f, 0f), r, w, 0.35f, 0f, 1);
            waveGlow.flat(new Vector3f(pos).add(0f, 0.2f, 0f), r - 0.6f, w * 3f, 0.6f, 0f, 1);
            for (float a : fissures) {
                Vector3f tip = new Vector3f(pos).add(HMath.ring(Math.min(radius, r), a, 0f));
                enc.arena().crackLine(new Vector3f(pos).add(HMath.ring(Math.max(0f, r - 2f), a, 0f)), tip, 0.9f, 140);
            }
            shock(r);
            if (r >= radius) {
                wave.hide(pos, 3);
                waveGlow.hide(pos, 3);
                reach.hide(pos, 6);
                return true;
            }
        }
        return false;
    }

    private void detonate() {
        enc.score().unmute();
        stage.push(shell, HeliosStage.gone(new Vector3f(pos).add(0f, 0.7f, 0f)), 2);
        for (BlockDisplay s : studs) {
            stage.push(s, HeliosStage.gone(pos), 2);
        }
        band.hide(pos, 2);
        // Stretched and pitched far down: several layers of the same moment.
        enc.score().at(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        enc.score().at(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
        enc.score().at(pos, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.5f);
        enc.score().at(pos, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 0.5f);
        enc.score().sweep(pos, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 0.3f, 0.7f, 0.5f, 30, 6);
        enc.camera().shakeFrom(pos, radius + 6f, 18);
        enc.camera().tint(Material.ORANGE_STAINED_GLASS, 1, 4);
        stage.particle(org.bukkit.Particle.EXPLOSION_EMITTER, new Vector3f(pos).add(0f, 0.5f, 0f), 1, 0, 0);
    }

    private void shock(float r) {
        double power = h.power("seismic", 90);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            float d = HMath.horizontal(new Vector3f(f).sub(pos));
            if (Math.abs(d - r) > WAVE_SPEED + 0.4f) {
                continue;
            }
            // Only what touches the ground: a jump clears it.
            if (f.y > 0.45f) {
                continue;
            }
            Vector3f out = new Vector3f(f).sub(pos);
            out.y = 0f;
            if (out.lengthSquared() > 1e-4f) {
                out.normalize();
            }
            enc.hit(p, power * (1f - 0.4f * d / radius), "seismic", 10, new Vector(out.x * 0.5, 0.75, out.z * 0.5));
        }
    }
}
