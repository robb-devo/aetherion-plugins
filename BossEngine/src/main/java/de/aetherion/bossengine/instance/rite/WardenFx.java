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
        static final int LAYERS = 7;
        static final int PANELS = 7;
        static final int DEBRIS = 14;
        final Location anchor;
        final List<BlockDisplay> panels = new ArrayList<>();
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
            double turn = t * (0.18 + b * 0.32);
            for (int i = 0; i < LAYERS; i++) {
                double y = 0.3 + i * 1.35 * b;
                double r = (1.3 + i * 0.62) * (0.4 + 0.6 * b);
                float w = (float) (Math.PI * 2 * r / PANELS * 0.82);
                for (int j = 0; j < PANELS; j++) {
                    double a = j * Math.PI * 2 / PANELS + i * 0.45 + turn * (1.0 + i * 0.12);
                    Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r));
                    Quaternionf q = Stage.yaw(-a - Math.PI / 2).rotateX(-0.43f).rotateZ(0.35f);
                    Stage.push(panels.get(i * PANELS + j), Stage.cube(c, q, w, (float) (1.5 * b), 0.06f), 2);
                }
            }
            for (int k = 0; k < debris.size(); k++) {
                double h = (phase[k] * 9.0 + t * 0.12) % 9.0 * b;
                double r = (1.5 + h * 0.46) * (0.5 + 0.5 * b);
                double a = phase[k] * Math.PI * 2 + t * 0.35 + h * 0.3;
                float s = (float) (0.32 + (k % 3) * 0.12);
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) (0.4 + h), (float) (Math.sin(a) * r));
                Quaternionf q = new Quaternionf().rotateXYZ((float) (t * 0.3 + k), (float) (t * 0.21), (float) (k * 0.7));
                Stage.push(debris.get(k), Stage.cube(c, q, s, s, s), 2);
            }
            for (int k = 0; k < blades.size(); k++) {
                boolean azure = k < 4;
                int trail = k % 4;
                double a = spin * (azure ? 1 : 1) + (azure ? 0 : Math.PI) - trail * 0.2;
                double y = azure ? 5.2 : 4.2;
                double r = 5.4 * b;
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r));
                Quaternionf q = Stage.yaw(-a);
                float len = (float) ((2.4 - trail * 0.35) * b);
                float thick = trail == 0 ? 0.16f : 0.1f;
                Stage.push(blades.get(k), Stage.cube(c, q, thick, thick, len), 1);
            }
            Location floor = Stage.ground(feet);
            if (t % 4 == 0) {
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double r = 1.4 + 2.4 * b;
                Stage.fx(floor.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), Particle.DUST_PILLAR, 2, 0.2, 0.05, 0.2, 0, Stage.floor(floor));
            }
            if (t % 14 == 0) {
                Stage.sound(floor, Sound.ITEM_ELYTRA_FLYING, 0.5f + (float) b * 0.6f, 0.6f);
            }
            return false;
        }

        private void spawn() {
            for (int i = 0; i < LAYERS; i++) {
                for (int j = 0; j < PANELS; j++) {
                    Material glass;
                    if (i >= LAYERS - 2 && j % 3 == 0) {
                        glass = j % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS;
                    } else {
                        glass = (i + j) % 3 == 0 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
                    }
                    panels.add(stage.block(anchor, glass, null, null, 13));
                }
            }
            Material[] junk = {Material.COBBLESTONE, Material.GRAVEL, Material.IRON_BARS, Material.DIRT,
                    Material.MOSSY_COBBLESTONE, Material.OAK_PLANKS};
            BlockData under = Stage.floor(anchor);
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

        /** The funnel bursts: sheets fly outward and thin out, the debris is thrown clear. */
        void release() {
            released = true;
            Vector3f base = Stage.offset(anchor, Stage.ground(feet));
            for (int i = 0; i < panels.size(); i++) {
                int layer = i / PANELS;
                double a = (i % PANELS) * Math.PI * 2 / PANELS + layer * 0.45 + spin;
                double r = (1.3 + layer * 0.62) * 2.2;
                Vector3f c = new Vector3f(base).add((float) (Math.cos(a) * r), (float) (0.3 + layer * 1.6), (float) (Math.sin(a) * r));
                Stage.push(panels.get(i), Stage.cube(c, Stage.yaw(-a).rotateX(-0.9f), 0.05f, 0.05f, 0.05f), 9);
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int k = 0; k < debris.size(); k++) {
                BlockDisplay d = debris.get(k);
                double a = rng.nextDouble(Math.PI * 2);
                Vector3f from = new Vector3f(base).add((float) (Math.cos(a) * 2), (float) rng.nextDouble(1, 6), (float) (Math.sin(a) * 2));
                Stage.Toss toss = stage.toss(d, true, 0.4f, from, Stage.v(Math.cos(a) * 0.45, 0.3, Math.sin(a) * 0.45));
                toss.floor = base.y;
                toss.rest = 16;
            }
            debris.clear();
            for (BlockDisplay d : blades) {
                Stage.push(d, Stage.gone(base), 4);
            }
            stage.play(t -> {
                if (t >= 10) {
                    for (BlockDisplay d : panels) {
                        stage.remove(d);
                    }
                    for (BlockDisplay d : blades) {
                        stage.remove(d);
                    }
                    panels.clear();
                    blades.clear();
                    dead = true;
                    return true;
                }
                return false;
            });
            Location floor = Stage.ground(feet);
            Stage.fx(floor.clone().add(0, 0.3, 0), Particle.DUST_PILLAR, 16, 3.0, 0.1, 3.0, 0, Stage.floor(floor));
            Stage.sound(floor, Sound.ENTITY_BREEZE_WIND_BURST, 1.4f, 0.6f);
        }

        @Override
        public void end() {
            for (BlockDisplay d : panels) {
                stage.remove(d);
            }
            for (BlockDisplay d : debris) {
                stage.remove(d);
            }
            for (BlockDisplay d : blades) {
                stage.remove(d);
            }
            panels.clear();
            debris.clear();
            blades.clear();
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
                Stage.fx(floor.clone().add(0, 0.3, 0), Particle.DUST_PILLAR, 14, 1.8, 0.1, 1.8, 0, Stage.floor(floor));
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
        Stage.fx(floor.clone().add(0, 0.3, 0), Particle.DUST_PILLAR, 14, radius * 0.5, 0.1, radius * 0.5, 0, Stage.floor(floor));
    }

    private static Matrix4f crack(Quaternionf q, float len, float width) {
        return new Matrix4f().translation(0f, 0.025f, 0f).rotate(q).scale(width, 0.02f, len).translate(-0.5f, -0.5f, 0f);
    }

    /* ================================================================== judgment pillar */

    public void judgment(Location focus, double radius) {
        Location floor = Stage.ground(focus);
        BlockDisplay shell = stage.block(floor, Material.WHITE_STAINED_GLASS, Stage.post(Stage.v(0, 0, 0), new Quaternionf(), 0.2f, 26f, 0.2f), AZURE, 15);
        BlockDisplay core = stage.block(floor, Material.WHITE_CONCRETE, Stage.post(Stage.v(0, 0, 0), new Quaternionf(), 0.08f, 26f, 0.08f), WHITE, 15);
        stage.play(t -> {
            if (t == 1) {
                Stage.push(shell, Stage.post(Stage.v(0, 0, 0), Stage.yaw(0.6), 2.8f, 26f, 2.8f), 3);
                Stage.push(core, Stage.post(Stage.v(0, 0, 0), Stage.yaw(0.2), 1.0f, 26f, 1.0f), 3);
            } else if (t == 6) {
                Stage.glow(shell, CRIMSON);
                Stage.push(shell, Stage.post(Stage.v(0, 0, 0), Stage.yaw(1.4), 0.05f, 26f, 0.05f), 10);
                Stage.push(core, Stage.post(Stage.v(0, 0, 0), Stage.yaw(1.0), 0.02f, 26f, 0.02f), 8);
            } else if (t >= 17) {
                stage.remove(shell);
                stage.remove(core);
                return true;
            }
            return false;
        });
        shockwave(floor, radius + 3.0, 8, CRIMSON_TONE);
        smash(floor, radius);
        Stage.fx(floor.clone().add(0, 1.0, 0), Particle.FLASH, 1, 0, 0);
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
        Stage.fx(floor.clone().add(0, 0.3, 0), Particle.DUST_PILLAR, 12, 1.2, 0.1, 1.2, 0, Stage.floor(floor));
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
