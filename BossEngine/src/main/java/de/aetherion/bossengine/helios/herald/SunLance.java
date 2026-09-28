package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SUN LANCE (signature). He flies up under the star and plunges both hands into it; the star dims as
 * he draws its light out into a lance. While it charges, three pillars of blackstone rise out of the
 * rings: cover. A hairline searches the floor for the lance's first mark. Then it fires: a solid beam
 * that sweeps around the arena. Anyone the beam can see burns; the pillars stop it (real blocks, real
 * line of sight). Afterwards the pillars crack and crumble.
 */
final class SunLance extends Attack {

    private static final Color GOLD = Color.fromRGB(255, 196, 70);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);
    private static final Vector3f PERCH = new Vector3f(0f, 5.2f, 0f);
    private static final int PILLAR_H = 4;

    private final HeraldScript s;
    private int rise;
    private int charge;
    private int sweep;
    private float from;
    private float span;
    private Shapes.Beam lance;
    private Shapes.Line aim;
    private Shapes.Ring muzzle;
    private final List<int[]> pillars = new ArrayList<>();
    private final List<Arena.Replica> pillarRise = new ArrayList<>();
    private final Vector3f start = new Vector3f();

    SunLance(HeraldScript s) {
        super(s);
        this.s = s;
    }

    @Override
    public String id() {
        return "lance";
    }

    @Override
    public Family family() {
        return Family.BODY;
    }

    @Override
    public void start() {
        rise = 30;
        charge = enc.tempo().ticks(Math.max(3, s.cfgInt("lance.charge-beats", 6)));
        sweep = enc.tempo().ticks(Math.max(4, s.cfgInt("lance.sweep-beats", 10)));
        span = (float) Math.toRadians(s.cfgD("lance.degrees", 210)) * (ThreadLocalRandom.current().nextBoolean() ? 1f : -1f);
        Vector3f c = s.partyCentroid();
        from = HMath.angleOf(c.x, c.z) - span * 0.5f;
        start.set(s.rig().root);
        lance = new Shapes.Beam(stage, g, Material.WHITE_CONCRETE, Material.ORANGE_STAINED_GLASS, GOLD);
        aim = new Shapes.Line(stage, g, Material.WHITE_CONCRETE, WHITE);
        muzzle = new Shapes.Ring(stage, g, 10, Material.YELLOW_STAINED_GLASS, GOLD, 15, true);
        lance.hide(0);
        aim.hide(PERCH, 0);
        muzzle.hide(PERCH, 0);
        s.fast(true);
        s.rig().pose(HeraldRig.Pose.raise(), 0.2f);
        enc.score().play(Sound.ENTITY_PHANTOM_FLAP, 1f, 0.5f);
        placePillars();
    }

    /** Three cover pillars on the Course and Corona, spread around the sweep. */
    private void placePillars() {
        int n = 3;
        for (int i = 0; i < n; i++) {
            float a = from + span * (0.2f + 0.3f * i) + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f;
            float r = i % 2 == 0 ? ArenaLayout.ringMid(1) : 26f;
            int x = (int) Math.floor(Math.cos(a) * r);
            int z = (int) Math.floor(Math.sin(a) * r);
            pillars.add(new int[]{x, z});
            // A display pillar rises out of the floor; the real blocks lock in when it arrives.
            List<ArenaLayout.Cell> proxy = new ArrayList<>();
            BlockData data = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 0; y < PILLAR_H; y++) {
                        proxy.add(new ArenaLayout.Cell(x + dx, y, z + dz, y == PILLAR_H - 1 ? Material.GILDED_BLACKSTONE.createBlockData() : data, 1, 0, 0));
                    }
                }
            }
            Arena.Replica rep = enc.arena().replica(g, proxy, false);
            rep.pose(new Quaternionf(), new Vector3f(0f, -PILLAR_H - 1f, 0f), 1f, 0);
            pillarRise.add(rep);
            enc.score().at(new Vector3f(x, 0f, z), Sound.BLOCK_PISTON_EXTEND, 1f, 0.5f);
        }
    }

    @Override
    protected boolean tick() {
        HeraldRig rig = s.rig();
        DyingStar star = enc.star();
        int fire = rise + charge;
        int stop = fire + sweep;
        // Up to the perch under the star, hands into its light.
        if (t < rise) {
            float f = HMath.inOutCubic(HMath.window(t, 0, rise));
            rig.root.set(start).lerp(new Vector3f(PERCH).sub(0f, 3f, 0f), f);
            float up = t < rise / 2 ? 0f : 1f;
            for (Arena.Replica r : pillarRise) {
                r.pose(new Quaternionf(), new Vector3f(0f, (-PILLAR_H - 1f) * (1f - HMath.outCubic(HMath.window(t, 0, rise))), 0f), 1f, 2);
            }
            if (up > 0f && t == rise / 2) {
                enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
            }
        }
        if (t == rise) {
            lockPillars();
            star.pulse(0.1f);
            star.scale(0.8f, 0.01f);
            enc.score().sweep(PERCH, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.2f, 0.5f, 2f, charge, 4);
            enc.score().sweep(null, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1f, 0.8f, 1.6f, charge, enc.tempo().ticks(1));
        }
        if (t >= rise && t < fire) {
            float f = HMath.window(t, rise, fire);
            // The lance is drawn out of the star: a growing rod from his hands down to his chest.
            Vector3f hands = rig.handPoint(0).add(rig.handPoint(1)).mul(0.5f);
            lance.set(star.center(), hands, 0.1f + 0.4f * f, 2);
            // The aim hairline searches the floor for the first mark.
            float a = from + (float) Math.sin(t * 0.15f) * 0.12f;
            aim.set(new Vector3f(PERCH), HMath.ring(28f, a, 0.1f), 0.04f + 0.04f * f, 2);
            muzzle.pose(PERCH, HMath.alignY(new Vector3f(HMath.ring(28f, a, 0f)).sub(PERCH)), 0.4f + 0.8f * f, 0.06f, 0.06f, t * 0.3f, 0f, 0f, 2);
            rig.coreHeat(f);
            rig.faceToward(HMath.ring(28f, a, 0f), 0.2f);
        }
        if (t == fire) {
            aim.hide(PERCH, 2);
            enc.score().play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.7f);
            enc.score().play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
            enc.camera().shakeAll(10, 2);
            rig.pose(HeraldRig.Pose.point(-0.6f), 0.4f);
            star.scale(1f, 0.02f);
            star.pulse(0.35f);
        }
        if (t >= fire && t < stop) {
            float f = HMath.window(t, fire, stop);
            float a = from + span * HMath.inOutCubic(f);
            Vector3f dirEnd = HMath.ring(30f, a, -0.5f);
            Vector3f dir = new Vector3f(dirEnd).sub(PERCH).normalize();
            float len = reach(PERCH, dir, 40f);
            Vector3f end = new Vector3f(dir).mul(len).add(PERCH);
            lance.set(PERCH, end, 0.9f + 0.25f * enc.star().beat(), 1);
            muzzle.pose(PERCH, HMath.alignY(dir), 1.3f, 0.08f, 0.08f, t * 0.4f, 0f, 0f, 1);
            rig.faceToward(end, 0.5f);
            if (t % 4 == 0) {
                enc.score().at(end, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.6f);
                enc.score().play(Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
            }
            int bx = (int) Math.floor(end.x);
            int bz = (int) Math.floor(end.z);
            enc.arena().crackCell(bx, bz, 0.7f, 80);
            burn(end);
        }
        if (t == stop) {
            lance.thin(4);
            muzzle.hide(PERCH, 4);
            enc.score().play(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            rig.pose(HeraldRig.Pose.guard(), 0.15f);
        }
        if (t > stop && t < stop + 30) {
            float f = HMath.window(t, stop, stop + 30);
            rig.root.set(new Vector3f(PERCH).sub(0f, 3f, 0f)).lerp(new Vector3f(start.x, 0f, start.z), HMath.inOutCubic(f));
            for (int[] p : pillars) {
                for (int y = 0; y < PILLAR_H; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        enc.arena().cracks().set(enc.arena().cx() + p[0] + dx, enc.arena().cy() + y, enc.arena().cz() + p[1], f, 40);
                    }
                }
            }
        }
        if (t == stop + 30) {
            crumble();
            return true;
        }
        return false;
    }

    /** Beam length until it hits a block (a pillar) or runs out. */
    private float reach(Vector3f origin, Vector3f dir, float max) {
        RayTraceResult hit = stage.world().rayTraceBlocks(stage.at(origin),
                new org.bukkit.util.Vector(dir.x, dir.y, dir.z), max, org.bukkit.FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            return max;
        }
        return (float) hit.getHitPosition().distance(stage.at(origin).toVector());
    }

    private void burn(Vector3f end) {
        double power = s.power("lance", 32);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            if (HMath.bodyDistSq(f, 1.8f, PERCH, end) < 1.3f * 1.3f) {
                enc.hit(p, power, "lance", 5, null);
                p.setFireTicks(Math.max(p.getFireTicks(), 30));
            }
        }
    }

    private void lockPillars() {
        BlockData data = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
        BlockData top = Material.GILDED_BLACKSTONE.createBlockData();
        for (int[] p : pillars) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 0; y < PILLAR_H; y++) {
                        enc.arena().placeTemp(p[0] + dx, y, p[1] + dz, y == PILLAR_H - 1 ? top : data);
                    }
                }
            }
            enc.score().at(new Vector3f(p[0], 1f, p[1]), Sound.BLOCK_STONE_PLACE, 1f, 0.6f);
        }
        for (Arena.Replica r : pillarRise) {
            r.pose(new Quaternionf(), new Vector3f(), 0.001f, 0);
        }
    }

    private void crumble() {
        for (int[] p : pillars) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 0; y < PILLAR_H; y++) {
                        enc.arena().clearTemp(p[0] + dx, y, p[1] + dz);
                        enc.arena().cracks().clear(enc.arena().cx() + p[0] + dx, enc.arena().cy() + y, enc.arena().cz() + p[1] + dz);
                    }
                }
            }
            Vector3f at = new Vector3f(p[0] + 0.5f, 1.5f, p[1] + 0.5f);
            stage.blockDust(at, Material.POLISHED_BLACKSTONE_BRICKS, 30, 1.2);
            enc.score().at(at, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1f, 0.6f);
        }
        pillars.clear();
    }

    @Override
    protected void cleanup() {
        crumble();
        s.fast(false);
        s.rig().coreHeat(0.6f);
        enc.star().scale(1f, 0.02f);
        HeliosStage.brightness(null, 15);
    }

    @Override
    public int recovery() {
        return enc.tempo().ticks(3);
    }
}
