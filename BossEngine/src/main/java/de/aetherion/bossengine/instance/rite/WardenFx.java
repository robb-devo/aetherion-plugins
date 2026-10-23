package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pathwarden's own pieces, built from displays: the blade cyclone (a real funnel of wind sheets with the floor
 * torn up into it and his two blades cutting the rim), the iron verdict cage that drops where a runner is going
 * to be, the cracked floor of his smash and the judgment pillar. Azure, crimson and iron; nothing from the rite
 * kits. Telegraphs are rings of glowing slabs ({@link #halo}), not vanilla particles.
 */
public final class WardenFx {

    private static final Color AZURE = Color.fromRGB(80, 180, 255);
    private static final Color CRIMSON = Color.fromRGB(220, 40, 55);
    private static final Color WHITE = Color.fromRGB(255, 245, 240);

    private final BossInstance instance;
    private final Stage stage;
    private Cyclone cyclone;
    private BlockDisplay lanceShell;
    private BlockDisplay lanceCore;
    private Location lanceAnchor;
    private BlockDisplay sky;
    private Location skyAnchor;
    private final Map<String, Halo> halos = new HashMap<>();

    public WardenFx(BossInstance instance) {
        this.instance = instance;
        this.stage = new Stage(instance);
    }

    public void tick() {
        stage.tick();
    }

    public void clear() {
        stage.clear();
        cyclone = null;
        lanceShell = null;
        lanceCore = null;
        lanceAnchor = null;
        sky = null;
        skyAnchor = null;
        halos.clear();
    }

    /* ================================================================== blade cyclone */

    /** The funnel forms around his feet; drive it each tick with {@link #cyclone(Location, double, float)}. */
    public void cycloneBegin(Location feet) {
        if (cyclone != null) {
            cyclone.dead = true;
        }
        cyclone = new Cyclone(feet);
        stage.play(cyclone);
    }

    /** {@code build} 0..1 while it winds up, 1 while it rages; {@code spinDeg} is his body spin. */
    public void cyclone(Location feet, double build, float spinDeg) {
        if (cyclone != null) {
            cyclone.feet = feet.clone();
            cyclone.build = build;
            cyclone.spin = Math.toRadians(spinDeg);
        }
    }

    public void cycloneRelease() {
        if (cyclone != null) {
            cyclone.release();
            cyclone = null;
        }
    }

    private final class Cyclone implements Stage.Anim {
        static final int LAYERS = 9;
        static final int PANELS = 8;
        static final int CAP = 10;
        static final int SKIRT = 12;
        static final int DEBRIS = 22;
        final Location anchor;
        final List<BlockDisplay> panels = new ArrayList<>();
        final List<BlockDisplay> cap = new ArrayList<>();
        final List<BlockDisplay> skirt = new ArrayList<>();
        final List<BlockDisplay> debris = new ArrayList<>();
        final List<BlockDisplay> blades = new ArrayList<>();
        final double[] phase = new double[DEBRIS];
        Location feet;
        double build;
        double spin;
        boolean dead;
        boolean released;

        Cyclone(Location feet) {
            this.anchor = Stage.ground(feet);
            this.anchor.setYaw(0f);
            this.anchor.setPitch(0f);
            this.feet = feet.clone();
        }

        /** Layer radius: the funnel flares from 1.1 at his feet to almost 8 at the top. */
        double radius(int layer) {
            return 1.1 + layer * 0.55 + 0.035 * layer * layer;
        }

        @Override
        public boolean tick(int t) {
            if (dead) {
                return true;
            }
            if (released) {
                return t > 200;
            }
            if (t == 1) {
                spawn();
                return false;
            }
            Vector3f base = Stage.offset(anchor, Stage.ground(feet));
            double b = Math.max(0.05, build);
            double turn = t * (0.2 + b * 0.38);
            double top = 0.3 + LAYERS * 1.45 * b;
            for (int i = 0; i < LAYERS; i++) {
                double y = 0.3 + i * 1.45 * b;
                double r = radius(i) * (0.4 + 0.6 * b);
                // the funnel's axis wanders, more the higher it gets
                double sx = Math.sin(t * 0.13 + i * 0.42) * 0.12 * i * b;
                double sz = Math.cos(t * 0.11 + i * 0.37) * 0.12 * i * b;
                float w = (float) (Math.PI * 2 * r / PANELS * 0.85);
                for (int j = 0; j < PANELS; j++) {
                    double a = j * Math.PI * 2 / PANELS + i * 0.45 + turn * (1.0 + i * 0.1);
                    Vector3f c = new Vector3f(base).add((float) (sx + Math.cos(a) * r), (float) y, (float) (sz + Math.sin(a) * r));
                    Quaternionf q = Stage.yaw(-a - Math.PI / 2).rotateX(-0.43f).rotateZ(0.35f);
                    Stage.push(panels.get(i * PANELS + j), Stage.cube(c, q, w, (float) (1.7 * b), 0.06f), 2);
                }
            }
            if (t % 2 == 0) {
                double r = 8.2 * b;
                float chord = (float) (Math.PI * 2 * r / CAP * 0.95);
                for (int k = 0; k < cap.size(); k++) {
                    double a = k * Math.PI * 2 / CAP + t * 0.05;
                    double y = top + 0.6 + Math.sin(t * 0.08 + k) * 0.35;
                    Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r));
                    Quaternionf q = Stage.yaw(-(a + Math.PI / 2)).rotateX((float) (0.18 * Math.sin(t * 0.06 + k)));
                    Stage.push(cap.get(k), Stage.cube(c, q, chord, (float) (0.8 * b), (float) (2.6 * b)), 3);
                }
            }
            for (int k = 0; k < skirt.size(); k++) {
                double r = (2.4 + (k % 3) * 1.5) * (0.5 + 0.5 * b);
                double a = k * Math.PI * 2 / SKIRT + t * (0.55 - (k % 3) * 0.1);
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) (0.2 + 0.15 * Math.sin(t * 0.4 + k)), (float) (Math.sin(a) * r));
                Quaternionf q = Stage.yaw(-a).rotateZ((float) (0.4 * Math.sin(t * 0.3 + k)));
                Stage.push(skirt.get(k), Stage.cube(c, q, 0.9f, 0.28f, 0.9f), 2);
            }
            for (int k = 0; k < debris.size(); k++) {
                double h = (phase[k] * 11.0 + t * 0.14) % 11.0 * b;
                double r = (1.4 + h * 0.55) * (0.5 + 0.5 * b);
                double a = phase[k] * Math.PI * 2 + t * 0.4 + h * 0.3;
                float s = (float) (0.35 + (k % 4) * 0.15);
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) (0.4 + h), (float) (Math.sin(a) * r));
                Quaternionf q = new Quaternionf().rotateXYZ((float) (t * 0.3 + k), (float) (t * 0.21), (float) (k * 0.7));
                Stage.push(debris.get(k), Stage.cube(c, q, s, s, s), 2);
            }
            for (int k = 0; k < blades.size(); k++) {
                boolean azure = k < 4;
                int trail = k % 4;
                double a = spin + (azure ? 0 : Math.PI) - trail * 0.2;
                double y = azure ? 5.2 : 4.2;
                double r = 5.4 * b;
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r));
                Quaternionf q = Stage.yaw(-a);
                float len = (float) ((2.4 - trail * 0.35) * b);
                float thick = trail == 0 ? 0.16f : 0.1f;
                Stage.push(blades.get(k), Stage.cube(c, q, thick, thick, len), 1);
            }
            Location floor = Stage.ground(feet);
            if (t % 10 == 0) {
                Stage.sound(floor, Sound.ITEM_ELYTRA_FLYING, 1.0f + (float) b * 1.5f, 0.55f);
            }
            if (t % 20 == 0 && b > 0.5) {
                Stage.sound(floor, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
            }
            return false;
        }

        private void spawn() {
            for (int i = 0; i < LAYERS; i++) {
                for (int j = 0; j < PANELS; j++) {
                    Material glass;
                    if (i >= LAYERS - 3 && j % 3 == 0) {
                        glass = j % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS;
                    } else if (i < 2) {
                        glass = (i + j) % 2 == 0 ? Material.GRAY_STAINED_GLASS : Material.LIGHT_GRAY_STAINED_GLASS;
                    } else {
                        glass = (i + j) % 3 == 0 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
                    }
                    panels.add(stage.block(anchor, glass, null, null, 13));
                }
            }
            for (int k = 0; k < CAP; k++) {
                cap.add(stage.block(anchor, k % 2 == 0 ? Material.GRAY_STAINED_GLASS : Material.LIGHT_GRAY_STAINED_GLASS,
                        null, null, -1));
            }
            BlockData under = Stage.floor(anchor);
            for (int k = 0; k < SKIRT; k++) {
                skirt.add(stage.block(anchor, k % 3 == 1 ? Material.COARSE_DIRT.createBlockData() : under, null, null, -1));
            }
            Material[] junk = {Material.COBBLESTONE, Material.GRAVEL, Material.IRON_BARS, Material.DIRT,
                    Material.MOSSY_COBBLESTONE, Material.OAK_PLANKS, Material.STONE_BRICKS};
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int k = 0; k < DEBRIS; k++) {
                phase[k] = rng.nextDouble();
                BlockData data = k % 4 == 0 ? under : junk[k % junk.length].createBlockData();
                debris.add(stage.block(anchor, data, null, null, -1));
            }
            for (int k = 0; k < 8; k++) {
                boolean azure = k < 4;
                Material m = k % 4 == 0
                        ? (azure ? Material.LIGHT_BLUE_CONCRETE : Material.RED_CONCRETE)
                        : (azure ? Material.LIGHT_BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS);
                blades.add(stage.block(anchor, m, null, k % 4 == 0 ? (azure ? AZURE : CRIMSON) : null, 15));
            }
        }

        /** The funnel bursts: sheets and the storm cap are blown outward, debris and floor are thrown clear. */
        void release() {
            released = true;
            Vector3f base = Stage.offset(anchor, Stage.ground(feet));
            for (int i = 0; i < panels.size(); i++) {
                int layer = i / PANELS;
                double a = (i % PANELS) * Math.PI * 2 / PANELS + layer * 0.45 + spin;
                double r = radius(layer) * 3.2;
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) (3.0 + layer * 2.0), (float) (Math.sin(a) * r));
                Stage.push(panels.get(i), Stage.cube(c, Stage.yaw(-a).rotateX(-0.9f), 0.05f, 0.05f, 0.05f), 12);
            }
            for (int k = 0; k < cap.size(); k++) {
                double a = k * Math.PI * 2 / CAP;
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * 15), 17f, (float) (Math.sin(a) * 15));
                Stage.push(cap.get(k), Stage.cube(c, Stage.yaw(-a), 0.1f, 0.1f, 0.1f), 14);
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            List<BlockDisplay> thrown = new ArrayList<>(debris);
            thrown.addAll(skirt);
            for (BlockDisplay d : thrown) {
                double a = rng.nextDouble(Math.PI * 2);
                Vector3f from = new Vector3f(base).add((float) (Math.cos(a) * 2), (float) rng.nextDouble(0.5, 6), (float) (Math.sin(a) * 2));
                double v = rng.nextDouble(0.55, 0.9);
                Stage.Toss toss = stage.toss(d, true, 0.45f, from, Stage.v(Math.cos(a) * v, rng.nextDouble(0.3, 0.6), Math.sin(a) * v));
                toss.floor = base.y;
                toss.rest = 16;
            }
            debris.clear();
            skirt.clear();
            for (BlockDisplay d : blades) {
                Stage.push(d, Stage.gone(base), 4);
            }
            stage.play(t -> {
                if (t >= 16) {
                    for (BlockDisplay d : panels) {
                        stage.remove(d);
                    }
                    for (BlockDisplay d : cap) {
                        stage.remove(d);
                    }
                    for (BlockDisplay d : blades) {
                        stage.remove(d);
                    }
                    panels.clear();
                    cap.clear();
                    blades.clear();
                    dead = true;
                    return true;
                }
                return false;
            });
            Location floor = Stage.ground(feet);
            shockwave(floor, 7.0, 6, CRIMSON_TONE);
            shockwave(floor, 11.0, 10, IRON_TONE);
            Stage.sound(floor, Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.5f);
            Stage.sound(floor, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
            Stage.sound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.5f, 0.55f);
        }

        @Override
        public void end() {
            for (List<BlockDisplay> group : List.of(panels, cap, skirt, debris, blades)) {
                for (BlockDisplay d : group) {
                    stage.remove(d);
                }
                group.clear();
            }
        }
    }

    /* ================================================================== iron verdict: the cage drop */

    /**
     * A cage of iron bars hangs over {@code spot} for {@code tell} ticks with its shadow growing under it,
     * flashes white, then drops; {@code onLand} runs on impact (damage stays with the director).
     */
    public void verdict(Location spot, int tell, Runnable onLand) {
        Location floor = Stage.ground(spot);
        stage.play(new Cage(floor, tell, onLand));
        Stage.sound(floor, Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.5f);
        Stage.sound(floor, Sound.BLOCK_IRON_DOOR_OPEN, 1.2f, 0.5f);
    }

    private final class Cage implements Stage.Anim {
        static final float SIZE = 3.4f;
        static final float HANG = 9.0f;
        final Location floor;
        final int tell;
        final Runnable onLand;
        final List<BlockDisplay> bars = new ArrayList<>();
        BlockDisplay shadow;
        BlockDisplay lid;

        Cage(Location floor, int tell, Runnable onLand) {
            this.floor = floor.clone();
            this.tell = tell;
            this.onLand = onLand;
        }

        @Override
        public boolean tick(int t) {
            if (t == 1) {
                shadow = stage.block(floor, Material.BLACK_CONCRETE, Stage.cube(Stage.v(0, 0.02, 0), new Quaternionf(), 0.1f, 0.02f, 0.1f), CRIMSON, 0);
                BlockData ew = Material.IRON_BARS.createBlockData("[east=true,west=true]");
                BlockData ns = Material.IRON_BARS.createBlockData("[north=true,south=true]");
                for (int side = 0; side < 4; side++) {
                    bars.add(stage.block(floor, side < 2 ? ew : ns, side(side, HANG + 6), null, 15));
                }
                lid = stage.block(floor, Material.IRON_TRAPDOOR, lidAt(HANG + 6), null, 15);
                return false;
            }
            if (t == 2) {
                Stage.push(shadow, Stage.cube(Stage.v(0, 0.02, 0), Stage.yaw(Math.PI / 4), SIZE * 1.2f, 0.02f, SIZE * 1.2f), tell - 2);
                for (int side = 0; side < 4; side++) {
                    Stage.push(bars.get(side), side(side, HANG), 6);
                }
                Stage.push(lid, lidAt(HANG), 6);
            }
            if (t == tell - 6) {
                for (BlockDisplay d : bars) {
                    Stage.glow(d, WHITE);
                }
                Stage.glow(shadow, WHITE);
                Stage.sound(floor, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.9f);
            }
            if (t == tell) {
                for (int side = 0; side < 4; side++) {
                    Stage.push(bars.get(side), side(side, 0), 3);
                }
                Stage.push(lid, lidAt(0), 3);
            }
            if (t == tell + 3) {
                for (BlockDisplay d : bars) {
                    Stage.glow(d, CRIMSON);
                }
                Stage.sound(floor, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                Stage.sound(floor, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.5f);
                Stage.sound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
                chips(floor, 8, 0.45, Stage.floor(floor));
                if (onLand != null) {
                    onLand.run();
                }
            }
            if (t == tell + 34) {
                for (int side = 0; side < 4; side++) {
                    Stage.push(bars.get(side), side(side, -3.6f), 12);
                }
                Stage.push(lid, lidAt(-3.6f), 12);
                Stage.push(shadow, Stage.gone(Stage.v(0, 0.02, 0)), 12);
            }
            return t >= tell + 48;
        }

        private Matrix4f side(int side, float lift) {
            float half = SIZE / 2f;
            Vector3f c = switch (side) {
                case 0 -> Stage.v(0, lift + 1.5, -half);
                case 1 -> Stage.v(0, lift + 1.5, half);
                case 2 -> Stage.v(-half, lift + 1.5, 0);
                default -> Stage.v(half, lift + 1.5, 0);
            };
            return side < 2
                    ? Stage.cube(c, new Quaternionf(), SIZE, 3.0f, 1.0f)
                    : Stage.cube(c, new Quaternionf(), 1.0f, 3.0f, SIZE);
        }

        private Matrix4f lidAt(float lift) {
            return Stage.cube(Stage.v(0, lift + 3.05, 0), new Quaternionf(), SIZE, 0.6f, SIZE);
        }

        @Override
        public void end() {
            for (BlockDisplay d : bars) {
                stage.remove(d);
            }
            bars.clear();
            stage.remove(shadow);
            stage.remove(lid);
        }
    }

    /* ================================================================== smash: the floor cracks */

    public void smash(Location at, double radius) {
        Location floor = Stage.ground(at);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + rng.nextDouble(-0.2, 0.2);
            float len = (float) (radius * rng.nextDouble(0.7, 1.05));
            Quaternionf q = Stage.yaw(-a + Math.PI / 2);
            Material m = i % 3 == 0 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            BlockDisplay crack = stage.block(floor, m, crack(q, 0.05f, 0.4f), i % 3 == 0 ? CRIMSON : null, i % 3 == 0 ? 12 : 0);
            stage.play(t -> {
                if (t == 1) {
                    Stage.push(crack, crack(q, len, 0.22f), 3);
                } else if (t == 26) {
                    Stage.push(crack, crack(q, len, 0.01f), 12);
                } else if (t >= 39) {
                    stage.remove(crack);
                    return true;
                }
                return false;
            });
        }
        chips(floor, 8, 0.5, Stage.floor(floor));
    }

    /** Floor chips (and a few iron ones) thrown out of an impact; displays, not particles. */
    private void chips(Location floor, int count, double speed, BlockData data) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        BlockData iron = Material.IRON_BLOCK.createBlockData();
        for (int i = 0; i < count; i++) {
            BlockDisplay d = stage.block(floor, i % 4 == 0 ? iron : data, null, null, -1);
            if (d == null) {
                continue;
            }
            double a = rng.nextDouble(Math.PI * 2);
            double v = speed * rng.nextDouble(0.6, 1.0);
            Stage.Toss toss = stage.toss(d, true, (float) rng.nextDouble(0.25, 0.55),
                    Stage.v(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6),
                    Stage.v(Math.cos(a) * v, rng.nextDouble(0.35, 0.7), Math.sin(a) * v));
            toss.floor = 0f;
            toss.rest = 14;
        }
    }

    private static Matrix4f crack(Quaternionf q, float len, float width) {
        return new Matrix4f().translation(0f, 0.025f, 0f).rotate(q).scale(width, 0.02f, len).translate(-0.5f, -0.5f, 0f);
    }

    /* ================================================================== judgment pillar */

    public void judgment(Location focus, double radius) {
        skyOff();
        Location floor = Stage.ground(focus);
        Vector3f o = Stage.v(0, 0, 0);
        BlockDisplay shell = stage.block(floor, Material.WHITE_STAINED_GLASS, Stage.post(o, new Quaternionf(), 0.3f, 40f, 0.3f), AZURE, 15);
        BlockDisplay core = stage.block(floor, Material.WHITE_CONCRETE, Stage.post(o, new Quaternionf(), 0.12f, 40f, 0.12f), WHITE, 15);
        // the bloom: three thin white squares turned 30 degrees apart read as a disc of light on the floor
        List<BlockDisplay> bloom = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            bloom.add(stage.block(floor, Material.WHITE_CONCRETE, Stage.cube(Stage.v(0, 0.06, 0), Stage.yaw(i * Math.PI / 6), 0.5f, 0.06f, 0.5f), WHITE, 15));
        }
        float wide = (float) (radius * 2.2);
        stage.play(t -> {
            if (t == 1) {
                Stage.push(shell, Stage.post(o, Stage.yaw(0.6), 4.6f, 40f, 4.6f), 2);
                Stage.push(core, Stage.post(o, Stage.yaw(0.2), 2.0f, 40f, 2.0f), 2);
                for (int i = 0; i < bloom.size(); i++) {
                    Stage.push(bloom.get(i), Stage.cube(Stage.v(0, 0.06, 0), Stage.yaw(i * Math.PI / 6 + 0.3), wide, 0.06f, wide), 3);
                }
            } else if (t == 5) {
                if (Stage.alive(shell)) {
                    shell.setBlock(Material.RED_STAINED_GLASS.createBlockData());
                }
                Stage.glow(shell, CRIMSON);
                Stage.push(shell, Stage.post(o, Stage.yaw(1.4), 3.2f, 40f, 3.2f), 4);
            } else if (t == 9) {
                Stage.push(shell, Stage.post(o, Stage.yaw(2.2), 0.05f, 40f, 0.05f), 10);
                Stage.push(core, Stage.post(o, Stage.yaw(1.0), 0.02f, 40f, 0.02f), 8);
                for (int i = 0; i < bloom.size(); i++) {
                    Stage.push(bloom.get(i), Stage.cube(Stage.v(0, 0.06, 0), Stage.yaw(i * Math.PI / 6 + 0.6), wide * 1.4f, 0.01f, wide * 1.4f), 10);
                }
            } else if (t >= 20) {
                stage.remove(shell);
                stage.remove(core);
                for (BlockDisplay d : bloom) {
                    stage.remove(d);
                }
                return true;
            }
            return false;
        });
        shockwave(floor, radius + 4.0, 7, CRIMSON_TONE);
        shockwave(floor, radius + 10.0, 14, IRON_TONE);
        smash(floor, radius);
        chips(floor, 12, 0.8, Stage.floor(floor));
        if (floor.getWorld() != null) {
            floor.getWorld().strikeLightningEffect(floor);
        }
        Stage.fx(floor.clone().add(0, 1.0, 0), Particle.FLASH, 1, 0, 0);
        Stage.sound(floor, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
        Stage.sound(floor, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.8f);
        Stage.sound(floor, Sound.ITEM_TRIDENT_THUNDER, 3.0f, 0.6f);
        Stage.sound(floor, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 3.0f, 0.5f);
    }

    /* ================================================================== sky charge: the beam from above */

    /** A white column falls onto the locked zone and thickens with {@code charge} 0..1; the judgment replaces it. */
    public void skyCharge(Location focus, double charge) {
        Location floor = Stage.ground(focus);
        if (!Stage.alive(sky) || skyAnchor == null || skyAnchor.getWorld() != floor.getWorld()
                || skyAnchor.distanceSquared(floor) > 64) {
            skyOff();
            skyAnchor = floor.clone();
            skyAnchor.setYaw(0f);
            skyAnchor.setPitch(0f);
            sky = stage.block(skyAnchor, Material.WHITE_STAINED_GLASS, Stage.gone(Stage.v(0, 40, 0)), WHITE, 15);
            if (sky == null) {
                return;
            }
        }
        float w = (float) (0.15 + 1.2 * charge * charge);
        Stage.push(sky, Stage.post(Stage.offset(skyAnchor, floor), Stage.yaw(charge * 3.0), w, 42f, w), 1);
    }

    public void skyOff() {
        stage.remove(sky);
        sky = null;
        skyAnchor = null;
    }

    /* ================================================================== dazed: stars over his helm */

    /** Five glowing shards circle above {@code head} for {@code ticks}: the opening after a big move. */
    public void dazed(Location head, int ticks) {
        Location anchor = head.clone();
        anchor.setYaw(0f);
        anchor.setPitch(0f);
        List<BlockDisplay> stars = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            Material m = k % 3 == 0 ? Material.WHITE_CONCRETE : k % 3 == 1 ? Material.LIGHT_BLUE_CONCRETE : Material.RED_CONCRETE;
            stars.add(stage.block(anchor, m, Stage.gone(Stage.v(0, 0, 0)), k % 3 == 2 ? CRIMSON : k % 3 == 1 ? AZURE : WHITE, 15));
        }
        stage.play(t -> {
            if (t > ticks) {
                for (BlockDisplay d : stars) {
                    stage.remove(d);
                }
                return true;
            }
            for (int k = 0; k < stars.size(); k++) {
                double a = t * 0.3 + k * Math.PI * 2 / stars.size();
                Vector3f c = Stage.v(Math.cos(a) * 1.8, 0.3 * Math.sin(t * 0.25 + k), Math.sin(a) * 1.8);
                Quaternionf q = new Quaternionf().rotateXYZ((float) (t * 0.4), (float) (k + t * 0.2), 0.78f);
                Stage.push(stars.get(k), Stage.cube(c, q, 0.4f, 0.4f, 0.4f), 1);
            }
            return false;
        });
        Stage.sound(head, Sound.BLOCK_BELL_RESONATE, 1.5f, 1.4f);
    }

    /* ================================================================== halos: telegraph rings */

    /** Tones for {@link #halo}: azure (tracking), crimson (danger), white (about to land), iron (the shock ring). */
    public static final int AZURE_TONE = 0;
    public static final int CRIMSON_TONE = 1;
    public static final int WHITE_TONE = 2;
    public static final int IRON_TONE = 3;

    /** Places (or moves) the named floor ring; call it every tick it should stay, {@link #haloOff} drops it. */
    public void halo(String name, Location centre, double radius, int tone) {
        Halo halo = halos.get(name);
        if (halo == null || halo.dead() || halo.anchor.getWorld() != centre.getWorld()
                || halo.anchor.distanceSquared(centre) > 256) {
            if (halo != null) {
                halo.remove();
            }
            halo = new Halo(centre);
            halos.put(name, halo);
        }
        halo.set(centre, radius, 0.22f, tone, 2);
    }

    public void haloOff(String name) {
        Halo halo = halos.remove(name);
        if (halo != null) {
            halo.remove();
        }
    }

    /**
     * A ring that runs out from {@code centre} to {@code maxRadius} in {@code ticks}; the radius at tick k is
     * {@code 1 + (maxRadius - 1) * k / ticks}, the front the director hits players on.
     */
    public void shockwave(Location centre, double maxRadius, int ticks, int tone) {
        Location floor = Stage.ground(centre);
        Halo ring = new Halo(floor);
        stage.play(t -> {
            if (t > ticks) {
                ring.remove();
                return true;
            }
            double r = 1.0 + (maxRadius - 1.0) * t / ticks;
            float height = (float) (0.9 * (1.0 - 0.6 * t / (double) ticks));
            ring.set(floor, r, height, tone, 1);
            return false;
        });
    }

    /** A ring of short glowing slabs on the floor. */
    private final class Halo {
        static final int SEGMENTS = 20;
        final Location anchor;
        final List<BlockDisplay> parts = new ArrayList<>();
        int tone = -1;
        double lastRadius = -1;
        float lastHeight;
        Vector3f lastCentre;

        Halo(Location centre) {
            this.anchor = centre.clone();
            this.anchor.setYaw(0f);
            this.anchor.setPitch(0f);
            for (int i = 0; i < SEGMENTS; i++) {
                BlockDisplay d = stage.block(anchor, Material.LIGHT_BLUE_CONCRETE, Stage.gone(Stage.v(0, 0.05, 0)), AZURE, 15);
                if (d != null) {
                    parts.add(d);
                }
            }
        }

        boolean dead() {
            return parts.isEmpty() || !Stage.alive(parts.get(0));
        }

        void set(Location centre, double radius, float height, int tone, int interp) {
            if (tone != this.tone) {
                this.tone = tone;
                Material m = switch (tone) {
                    case CRIMSON_TONE -> Material.RED_CONCRETE;
                    case WHITE_TONE -> Material.WHITE_CONCRETE;
                    case IRON_TONE -> Material.IRON_BLOCK;
                    default -> Material.LIGHT_BLUE_CONCRETE;
                };
                Color glow = tone == AZURE_TONE ? AZURE : tone == WHITE_TONE ? WHITE : CRIMSON;
                BlockData data = m.createBlockData();
                for (BlockDisplay d : parts) {
                    if (Stage.alive(d)) {
                        d.setBlock(data);
                        Stage.glow(d, glow);
                    }
                }
            }
            Vector3f c = Stage.offset(anchor, Stage.ground(centre));
            if (lastCentre != null && lastCentre.distanceSquared(c) < 1.0e-4 && Math.abs(lastRadius - radius) < 0.01
                    && Math.abs(lastHeight - height) < 0.01f) {
                return;
            }
            lastCentre = c;
            lastRadius = radius;
            lastHeight = height;
            float chord = (float) (Math.PI * 2 * radius / SEGMENTS * 0.92);
            for (int i = 0; i < parts.size(); i++) {
                double a = i * Math.PI * 2 / SEGMENTS;
                Vector3f p = new Vector3f(c).add((float) (Math.cos(a) * radius), 0.03f + height / 2f, (float) (Math.sin(a) * radius));
                Stage.push(parts.get(i), Stage.cube(p, Stage.yaw(-(a + Math.PI / 2)), chord, height, 0.14f), interp);
            }
        }

        void remove() {
            for (BlockDisplay d : parts) {
                stage.remove(d);
            }
            parts.clear();
        }
    }

    /* ================================================================== judgment lance */

    /**
     * The beam from his crown to the floor: a glass shaft with a white core, re-aimed every call. {@code hot}
     * turns it into the red firing beam. {@link #lanceOff()} takes it down.
     */
    public void lance(Location from, Location to, Color color, float width, boolean hot) {
        if (from.getWorld() == null || from.getWorld() != to.getWorld()) {
            return;
        }
        if (!Stage.alive(lanceShell) || lanceAnchor == null || lanceAnchor.getWorld() != from.getWorld()
                || lanceAnchor.distanceSquared(from) > 64) {
            lanceOff();
            lanceAnchor = from.clone();
            lanceAnchor.setYaw(0f);
            lanceAnchor.setPitch(0f);
            lanceShell = stage.block(lanceAnchor, Material.LIGHT_BLUE_STAINED_GLASS, null, color, 15);
            lanceCore = stage.block(lanceAnchor, Material.WHITE_CONCRETE, null, WHITE, 15);
            if (lanceShell == null || lanceCore == null) {
                lanceOff();
                return;
            }
        }
        Material shell = hot ? Material.RED_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
        if (lanceShell.getBlock().getMaterial() != shell) {
            lanceShell.setBlock(shell.createBlockData());
        }
        Vector3f a = Stage.offset(lanceAnchor, from);
        Vector3f b = Stage.offset(lanceAnchor, to);
        Stage.glow(lanceShell, color);
        Stage.push(lanceShell, Stage.beam(a, b, width), 2);
        Stage.push(lanceCore, Stage.beam(a, b, width * 0.3f), 2);
    }

    public void lanceOff() {
        stage.remove(lanceShell);
        stage.remove(lanceCore);
        lanceShell = null;
        lanceCore = null;
        lanceAnchor = null;
    }
}
