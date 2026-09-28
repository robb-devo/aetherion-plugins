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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * SOLAR FLARES. For every player a flare is torn out of Helios's rings and lobbed high. On the floor
 * under its landing point a marker appears: an outer ring (the blast) and an inner disc that fills
 * toward the ring; when it touches the ring, the flare lands, on the beat. The impact leaves the floor
 * molten for a few seconds (real magma: don't stand on it).
 */
final class SolarFlares extends Attack {

    private static final Color FLARE = Color.fromRGB(255, 150, 40);
    private static final float RADIUS = 3f;

    private final HeliosScript h;
    private final int beats;
    private final List<Flare> flares = new ArrayList<>();
    private int land;

    private final class Flare {
        final Vector3f from;
        final Vector3f to;
        final BlockDisplay body;
        final BlockDisplay glow;
        final Shapes.Ring rim;
        final Shapes.Disc fill;
        final Shapes.Line trail;
        boolean landed;

        Flare(Vector3f from, Vector3f to) {
            this.from = from;
            this.to = to;
            this.body = g.block(Material.SHROOMLIGHT.createBlockData(), FLARE, 15, true);
            this.glow = g.block(Material.ORANGE_STAINED_GLASS.createBlockData(), null, 15, true);
            this.rim = new Shapes.Ring(stage, g, 16, Material.RED_STAINED_GLASS, FLARE, 15, true);
            this.fill = new Shapes.Disc(stage, g, Material.ORANGE_STAINED_GLASS, null, 15);
            this.trail = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, FLARE);
        }
    }

    /** @param beats beats from launch to impact (the count-in) */
    SolarFlares(HeliosScript h, int beats) {
        super(h);
        this.h = h;
        this.beats = Math.max(2, beats);
    }

    @Override
    public String id() {
        return "flares";
    }

    @Override
    public Family family() {
        return Family.SKY;
    }

    @Override
    public void start() {
        land = enc.tempo().ticksUntil(beats - 1);
        int i = 0;
        for (Player p : enc.fighters()) {
            Vector3f to = stage.feet(p);
            // Lead a moving target a little, then snap to floor level.
            org.bukkit.util.Vector v = p.getVelocity();
            to.add((float) v.getX() * 8f, 0f, (float) v.getZ() * 8f);
            to.y = 0.02f;
            Vector3f from = h.rig().bladePoint(i % 4);
            flares.add(new Flare(from, to));
            i++;
        }
        enc.score().play(Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        enc.score().play(Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.5f);
        for (Flare f : flares) {
            f.rim.flat(f.to, RADIUS, 0.12f, 0.04f, 0f, 4);
            f.fill.set(f.to, 0.1f, 0.03f, 0f, 0);
            f.fill.set(f.to, RADIUS * 0.95f, 0.03f, 0f, land);
        }
    }

    @Override
    protected boolean tick() {
        float f = HMath.window(t, 0, land);
        for (Flare fl : flares) {
            if (fl.landed) {
                continue;
            }
            Vector3f mid = new Vector3f(fl.from).add(fl.to).mul(0.5f).add(0f, 16f, 0f);
            Vector3f at = HMath.bezier(fl.from, mid, fl.to, HMath.inQuad(f), new Vector3f());
            Vector3f back = HMath.bezier(fl.from, mid, fl.to, HMath.inQuad(Math.max(0f, f - 0.08f)), new Vector3f());
            Quaternionf rot = new Quaternionf().rotateXYZ(t * 0.3f, t * 0.2f, 0f);
            stage.push(fl.body, HeliosStage.cube(at, 0.7f, rot), 1);
            stage.push(fl.glow, HeliosStage.cube(at, 1.1f + 0.2f * enc.star().beat(), new Quaternionf(rot).invert()), 1);
            fl.trail.set(back, at, 0.25f, 1);
            if (t % 4 == 0) {
                enc.score().at(at, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.4f);
            }
        }
        if (t == land) {
            for (Flare fl : flares) {
                impact(fl);
            }
        }
        return t > land + 8;
    }

    private void impact(Flare fl) {
        fl.landed = true;
        stage.push(fl.body, HeliosStage.gone(fl.to), 2);
        stage.push(fl.glow, HeliosStage.cube(fl.to, 4f, new Quaternionf()), 3);
        fl.trail.hide(fl.to, 2);
        fl.rim.flat(fl.to, RADIUS + 1.5f, 0.3f, 0.3f, 0f, 3);
        fl.fill.hide(fl.to, 6);
        enc.score().at(fl.to, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
        enc.score().at(fl.to, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        enc.camera().shakeFrom(fl.to, 8f, 5);
        stage.particle(org.bukkit.Particle.LAVA, new Vector3f(fl.to).add(0f, 0.5f, 0f), 8, 0.8, 0.1);
        double power = h.power("flares", 65);
        for (Player p : enc.fighters()) {
            Vector3f pf = stage.feet(p);
            if (HMath.horizontal(new Vector3f(pf).sub(fl.to)) < RADIUS && pf.y < 3f) {
                enc.hit(p, power, "flare", 10, h.away(fl.to, pf, 0.6));
            }
        }
        // Molten patch (real magma for a few seconds; the arena restores it).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    h.moltenAt((int) Math.floor(fl.to.x) + dx, (int) Math.floor(fl.to.z) + dz, 20 * 5);
                }
            }
        }
    }

    @Override
    protected void cleanup() {
    }
}
