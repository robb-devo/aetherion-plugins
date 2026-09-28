package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LASER CAGE. The corona drops a lattice of light across the whole arena at knee height: two sets of
 * parallel beams crossing at right angles, strung between gold emitters on the rim. The lattice turns
 * slowly and draws together, so every cell of safety drifts and shrinks; walk with your cell, or jump
 * a beam as it passes.
 *
 * <p>Tell: two beats of hairlines (no damage) and a charging whine from the rim, then the lines swell
 * on the beat and go live. The cage breathes on the star's heartbeat (width pulses) so it reads alive.
 */
final class LaserCage extends Attack {

    private static final Color GOLD = Color.fromRGB(255, 196, 70);
    private static final float R = ArenaLayout.RADIUS + 0.5f;
    private static final float HEIGHT = 0.55f;
    private static final int MAX_LINES = 13;

    private final HeraldScript s;
    private final List<Shapes.Beam> beams = new ArrayList<>();
    private final List<BlockDisplay> emitters = new ArrayList<>();
    private int tell;
    private int live;
    private float spacing;
    private float minSpacing;
    private float angle;
    private float spin;
    private boolean armed;

    LaserCage(HeraldScript s) {
        super(s);
        this.s = s;
    }

    @Override
    public String id() {
        return "cage";
    }

    @Override
    public Family family() {
        return Family.GROUND;
    }

    @Override
    public void start() {
        tell = enc.tempo().ticks(2);
        live = enc.tempo().ticks(Math.max(6, s.cfgInt("cage.duration-beats", 18)));
        spacing = (float) s.cfgD("cage.spacing", 7.0);
        minSpacing = (float) s.cfgD("cage.min-spacing", 4.6);
        angle = ThreadLocalRandom.current().nextFloat() * HMath.PI;
        spin = (ThreadLocalRandom.current().nextBoolean() ? 1f : -1f) * 0.0055f;
        for (int i = 0; i < MAX_LINES * 2; i++) {
            beams.add(new Shapes.Beam(stage, g, Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, GOLD));
        }
        int em = stage.budget().scaled(16, 8);
        for (int i = 0; i < em; i++) {
            BlockDisplay d = g.block(Material.GOLD_BLOCK.createBlockData(), GOLD, 15, false);
            if (d != null) {
                emitters.add(d);
            }
        }
        enc.score().sweep(null, Sound.ENTITY_GUARDIAN_ATTACK, 0.7f, 1f, 0.6f, 1.8f, tell, 4);
        enc.score().play(Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.7f);
        enc.star().pulse(0.55f);
        render(0f, 1);
    }

    @Override
    protected boolean tick() {
        if (t == tell) {
            armed = true;
            enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            enc.score().play(Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5f, 2f);
        }
        if (t >= tell) {
            angle += spin;
            float f = HMath.window(t, tell, tell + live);
            spacing = HMath.lerp((float) s.cfgD("cage.spacing", 7.0), minSpacing, HMath.smooth(f));
        }
        if (t % 2 == 0) {
            float width = armed ? 0.22f + 0.1f * enc.star().beat() : 0.05f;
            render(width, 2);
        }
        if (armed) {
            if (t % 20 == 0) {
                enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.9f);
            }
            hurt();
        }
        if (t == tell + live) {
            for (Shapes.Beam b : beams) {
                b.thin(6);
            }
            enc.score().play(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
            enc.star().pulse(0.35f);
            armed = false;
        }
        return t >= tell + live + 8;
    }

    /** Offsets of the lines in one family for the current spacing, centered with a safe middle cell. */
    private List<Float> offsets() {
        List<Float> out = new ArrayList<>();
        for (int i = 0; out.size() < MAX_LINES; i++) {
            float d = (i + 0.5f) * spacing;
            if (d >= R - 0.5f) {
                break;
            }
            out.add(d);
            out.add(-d);
        }
        return out;
    }

    private void render(float width, int interp) {
        List<Float> offs = offsets();
        int idx = 0;
        for (int fam = 0; fam < 2; fam++) {
            float a = angle + fam * HMath.HALF_PI;
            Vector3f u = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
            Vector3f n = new Vector3f(-u.z, 0f, u.x);
            for (int k = 0; k < MAX_LINES; k++) {
                Shapes.Beam beam = beams.get(idx++);
                if (k >= offs.size()) {
                    beam.hide(interp);
                    continue;
                }
                float d = offs.get(k);
                float half = (float) Math.sqrt(Math.max(0f, R * R - d * d));
                Vector3f mid = new Vector3f(n).mul(d).add(0f, HEIGHT, 0f);
                Vector3f p0 = new Vector3f(mid).sub(new Vector3f(u).mul(half));
                Vector3f p1 = new Vector3f(mid).add(new Vector3f(u).mul(half));
                beam.set(p0, p1, Math.max(0.03f, width), interp);
            }
        }
        // Emitters sit on the rim and turn with the lattice.
        for (int i = 0; i < emitters.size(); i++) {
            float a = angle + i * HMath.TAU / emitters.size();
            stage.push(emitters.get(i), HeliosStage.cube(HMath.ring(R, a, HEIGHT), 0.6f), interp);
        }
    }

    private void hurt() {
        List<Float> offs = offsets();
        double power = s.power("cage", 35);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            if (f.y > HEIGHT + 0.45f || f.y < -1.5f) {
                continue;
            }
            for (int fam = 0; fam < 2; fam++) {
                float a = angle + fam * HMath.HALF_PI;
                float nx = -(float) Math.sin(a);
                float nz = (float) Math.cos(a);
                float along = f.x * nx + f.z * nz;
                for (float d : offs) {
                    float gap = along - d;
                    if (Math.abs(gap) < 0.42f) {
                        Vector knock = new Vector(nx * Math.signum(gap) * 0.45, 0.3, nz * Math.signum(gap) * 0.45);
                        enc.hit(p, power, "cage", 10, knock);
                        break;
                    }
                }
            }
        }
    }
}
