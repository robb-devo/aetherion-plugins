package de.aetherion.bossengine.helios.requiem;

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

/**
 * SOLAR WIND. Helios inhales (its rings draw in, a rising whistle), then breathes out walls of wind
 * that roll to the edge and shove everyone outward. Four gold anchor pylons rise out of the Course
 * for the duration: stand in a pylon's lee (the side away from Helios) and the wind passes you by.
 * Sneaking halves the push. Once the Corona has burned away, the edge is the void.
 */
final class SolarWind extends Attack {

    private static final Color WIND = Color.fromRGB(235, 245, 255);
    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final float SPEED = 0.55f;

    private final HeliosScript h;
    private final int count;
    private final List<Float> waves = new ArrayList<>();
    private final List<Shapes.Ring> walls = new ArrayList<>();
    private final List<BlockDisplay> pylons = new ArrayList<>();
    private final List<Vector3f> pylonAt = new ArrayList<>();
    private final Vector3f origin = new Vector3f();
    private int inhale;
    private int launched;
    private int nextAt;

    SolarWind(HeliosScript h, int count) {
        super(h);
        this.h = h;
        this.count = Math.max(1, count);
    }

    @Override
    public String id() {
        return "wind";
    }

    @Override
    public Family family() {
        return Family.ARENA;
    }

    @Override
    public void start() {
        inhale = enc.tempo().ticks(3);
        origin.set(h.rig().center.x, 0f, h.rig().center.z);
        float base = HMath.hash((int) origin.x, (int) origin.z) * HMath.TAU;
        for (int i = 0; i < 4; i++) {
            Vector3f at = HMath.ring(ArenaLayout.ringMid(1), base + i * HMath.HALF_PI, 0f);
            pylonAt.add(at);
            BlockDisplay d = g.block(Material.GOLD_BLOCK.createBlockData(), GOLD, 15, true);
            pylons.add(d);
            stage.push(d, HeliosStage.box(new Vector3f(at).add(0f, -1f, 0f), new Vector3f(0.6f, 0.01f, 0.6f)), 0);
        }
        for (int i = 0; i < count; i++) {
            walls.add(new Shapes.Ring(stage, g, stage.budget().scaled(32, 18), Material.WHITE_STAINED_GLASS, WIND, 15, true));
        }
        h.rig().ringScale(0.6f);
        enc.score().sweep(null, Sound.ENTITY_BREEZE_INHALE, 0.9f, 1f, 0.6f, 1.4f, inhale, enc.tempo().ticks(1));
        enc.score().play(Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.6f);
    }

    @Override
    protected boolean tick() {
        if (t < inhale) {
            float f = HMath.outCubic(HMath.window(t, 0, inhale));
            for (int i = 0; i < pylons.size(); i++) {
                Vector3f at = pylonAt.get(i);
                stage.push(pylons.get(i), HeliosStage.box(new Vector3f(at).add(0f, 1.6f * f, 0f), new Vector3f(0.6f, 3.2f * f, 0.6f)), 2);
            }
            return false;
        }
        if (launched < count && t >= Math.max(inhale, nextAt)) {
            waves.add(1.5f);
            launched++;
            nextAt = t + enc.tempo().ticks(1.5);
            enc.score().play(Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.6f);
            enc.score().play(Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 0.5f);
            h.rig().ringScale(1.3f);
        }
        boolean alive = false;
        for (int i = 0; i < waves.size(); i++) {
            float r = waves.get(i);
            if (r < 0f) {
                continue;
            }
            alive = true;
            r += SPEED;
            waves.set(i, r);
            walls.get(i).pose(new Vector3f(origin).add(0f, 1.1f, 0f), new org.joml.Quaternionf(), r, 0.25f, 2.2f,
                    t * 0.05f, 0f, 0f, 1);
            push(r);
            if (r > 36f) {
                walls.get(i).hide(origin, 3);
                waves.set(i, -1f);
            }
        }
        if (launched >= count && !alive) {
            for (int i = 0; i < pylons.size(); i++) {
                stage.push(pylons.get(i), HeliosStage.box(new Vector3f(pylonAt.get(i)).add(0f, -0.5f, 0f), new Vector3f(0.6f, 0.01f, 0.6f)), 10);
            }
            h.rig().ringScale(1f);
            return t > nextAt + 12;
        }
        return false;
    }

    private void push(float r) {
        double strength = enc.config().d("helios.wind.push", 0.5);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            Vector3f out = new Vector3f(f).sub(origin);
            out.y = 0f;
            float d = out.length();
            if (Math.abs(d - r) > 0.7f || d < 0.5f) {
                continue;
            }
            out.div(d);
            if (sheltered(f, out)) {
                enc.score().to(p, Sound.BLOCK_WOOL_PLACE, 0.4f, 1.4f);
                continue;
            }
            double k = p.isSneaking() ? 0.35 : 1.0;
            Vector dir = new Vector(out.x, 0, out.z);
            if (!enc.groundAhead(p, dir)) {
                // Edge grip: at the brink the wind only staggers you.
                k *= 0.12;
            }
            Vector v = p.getVelocity().add(new Vector(out.x * strength * k, 0.12 * k, out.z * strength * k));
            p.setVelocity(v);
            enc.hit(p, h.power("wind", 16) * 0.5, "wind", 20, null);
        }
    }

    /** Behind a pylon (on the side away from Helios, within its lee) nothing reaches you. */
    private boolean sheltered(Vector3f f, Vector3f out) {
        for (Vector3f at : pylonAt) {
            Vector3f rel = new Vector3f(f).sub(at);
            rel.y = 0f;
            float along = rel.dot(out);
            float across = Math.abs(rel.x * -out.z + rel.z * out.x);
            if (along > 0f && along < 5f && across < 1.3f) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void cleanup() {
        h.rig().ringScale(1f);
    }
}
