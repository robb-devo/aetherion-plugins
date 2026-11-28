package de.aetherion.tutorial.isle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Old Clanger's body: a hermit crab that wears the harbour bell. Every part is a block display that one player
 * sees (the fight is theirs alone); none of them ever teleports, a part is posed through its transformation around
 * one anchor (the middle of the arena), so the client glides every change.
 *
 * <p>Local space: +Z is where he looks, +X his left, +Y up, the origin on the sand under his middle.
 */
final class ClangerRig {

    static final String TAG = "ae_clanger";

    /** Everything the body can be asked to do, as numbers. The fight sets them, {@link #pose} draws them. */
    static final class Pose {
        double x;
        double y;
        double z;
        /** Minecraft yaw he faces (degrees). */
        float yaw;
        /** 0..1: legs, claws and eyes pulled into the bell. */
        double tuck;
        /** Bell (and body) lifted off the sand, in blocks. */
        double lift;
        /** Leaning back, degrees (reeling from a parry: his belly shows). */
        double tilt;
        /** Side wobble, degrees. */
        double wobble;
        /** Spin of the bell around its own axis, degrees (slam). */
        double spin;
        /** The big claw: 0 = at rest, 1 = reared back high. */
        double rear;
        /** The big claw: 0 = at rest, 1 = thrust far forward. */
        double thrust;
        /** How far the thrust goes, in blocks from his middle (it stops short of whoever it is aimed at). */
        double reach = 4.15;
        /** Both claws up in the air, 0..1 (he gives up). */
        double surrender;
        /** Walk cycle angle (radians) and how much of it shows (0 = standing). */
        double stride;
        double walk;
        /** Eyes: 1 = normal, bigger = startled, 0.1 = shut. */
        double eyes = 1.0;
        /** Pupils circle (radians); 0 with {@link #dizzy} false. */
        double dizzyAngle;
        boolean dizzy;
        /** Where he looks, relative to straight ahead: -1..1 left/right. */
        double glance;
        /** 0..1: a hit just rang the bell (it swells for a blink). */
        double ring;
        /** Whole body scale (he shrinks to nothing when he leaves). */
        double scale = 1.0;
    }

    private static final Quaternionf IDENTITY = new Quaternionf();

    private final Plugin plugin;
    private final Player viewer;
    private final Location anchor;
    private final NamespacedKey key;
    private final List<Part> all = new ArrayList<>();

    /**
     * The bell's outline from the lip up: {height above the lip's foot, height of the ring, radius}. The wide rings
     * are true octagons (four crossed slabs each), the small ones near the top two crossed squares.
     */
    private static final double[][] BELL = {
            {0.00, 0.22, 1.56},
            {0.22, 0.34, 1.43},
            {0.56, 0.60, 1.29},
            {1.16, 0.60, 1.19},
            {1.76, 0.50, 1.08},
            {2.26, 0.36, 0.90},
            {2.62, 0.26, 0.62},
            {2.88, 0.28, 0.30}
    };
    /** Rings drawn as full octagons (the rest: two squares). */
    private static final int ROUND_RINGS = 5;
    private static final double BELL_TOP = 3.16;

    private final Part[][] rings = new Part[BELL.length][];
    private final Part loop;
    private final Part[] barnacles = new Part[3];
    private final Part body;
    private final Part face;
    private final Part[] stalk = new Part[2];
    private final Part[] eye = new Part[2];
    private final Part[] pupil = new Part[2];
    private final Part[] upperArm = new Part[2];
    private final Part[] foreArm = new Part[2];
    private final Part[] jawLow = new Part[2];
    private final Part[] jawHigh = new Part[2];
    private final Part[] thigh = new Part[6];
    private final Part[] shin = new Part[6];

    ClangerRig(Plugin plugin, Player viewer, Location anchor) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.anchor = anchor.clone();
        this.anchor.setYaw(0f);
        this.anchor.setPitch(0f);
        this.key = new NamespacedKey(plugin, "clanger");
        Material bell = Material.GOLD_BLOCK;
        Material band = Material.YELLOW_TERRACOTTA;
        Material shell = Material.RED_MUSHROOM_BLOCK;
        Material dark = Material.NETHER_WART_BLOCK;
        for (int i = 0; i < BELL.length; i++) {
            // the lip, the shoulder and the crown are the darker bands of a cast bell
            Material material = i == 0 || i == 5 || i == BELL.length - 1 ? band : bell;
            rings[i] = new Part[i < ROUND_RINGS ? 4 : 2];
            for (int k = 0; k < rings[i].length; k++) {
                rings[i][k] = part(material);
            }
        }
        loop = part(Material.POLISHED_BLACKSTONE);
        barnacles[0] = part(Material.DEAD_BRAIN_CORAL_BLOCK);
        barnacles[1] = part(Material.CALCITE);
        barnacles[2] = part(Material.DEAD_BUBBLE_CORAL_BLOCK);
        body = part(dark);
        face = part(shell);
        for (int i = 0; i < 2; i++) {
            stalk[i] = part(shell);
            eye[i] = part(Material.WHITE_CONCRETE);
            pupil[i] = part(Material.BLACK_CONCRETE);
            upperArm[i] = part(dark);
            foreArm[i] = part(shell);
            jawLow[i] = part(shell);
            jawHigh[i] = part(Material.BONE_BLOCK);
        }
        for (int i = 0; i < 6; i++) {
            thigh[i] = part(shell);
            shin[i] = part(dark);
        }
    }

    private Part part(Material material) {
        World world = anchor.getWorld();
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(TAG);
            d.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            d.setBlock(material.createBlockData());
            d.setViewRange(3.0f);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setBillboard(Display.Billboard.FIXED);
            // parts are drawn far from where the entity stands: without a wide box the client culls them
            d.setDisplayWidth(64f);
            d.setDisplayHeight(40f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0f, 0f, 0f), new Quaternionf()));
            d.setInterpolationDuration(0);
            d.setInterpolationDelay(0);
            d.setTeleportDuration(0);
        });
        viewer.showEntity(plugin, display);
        Part part = new Part(display);
        all.add(part);
        return part;
    }

    void remove() {
        for (Part part : all) {
            part.remove();
        }
        all.clear();
    }

    boolean valid() {
        return !all.isEmpty() && all.get(0).display.isValid();
    }

    static boolean ours(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the pose; the client glides to it over {@code interp} ticks. */
    void pose(Pose p, int interp) {
        float yawRad = (float) Math.toRadians(p.yaw);
        Quaternionf root = new Quaternionf().rotationY(-yawRad)
                .rotateX((float) Math.toRadians(-p.tilt))
                .rotateZ((float) Math.toRadians(p.wobble));
        double s = Math.max(0.0, p.scale);
        double tuck = clamp(p.tuck);
        double out = 1.0 - tuck;
        // pulled in, every part sits inside the bell, wherever the bell is (it flies for the slam)
        double in = (p.lift + 0.4) * tuck;
        double bob = Math.sin(p.stride * 2.0) * 0.04 * p.walk;
        // the bell sits on the sand when he is tucked in, and rides on his back when he stands
        double bellY = lerp(0.72, 0.0, tuck) + p.lift + bob;
        Quaternionf bellRot = new Quaternionf(root).rotateY((float) Math.toRadians(p.spin));
        double swell = 1.0 + 0.06 * clamp(p.ring);
        Frame frame = new Frame(p, root, s, interp);

        for (int i = 0; i < BELL.length; i++) {
            double foot = BELL[i][0];
            double high = BELL[i][1];
            double r = BELL[i][2] * swell;
            Part[] ring = rings[i];
            for (int k = 0; k < ring.length; k++) {
                Quaternionf turn = new Quaternionf(bellRot).rotateY((float) (Math.PI / ring.length * k));
                // a hair's difference in height per slab: no two top faces lie in one plane (they would flicker)
                double h = high + k * 0.007;
                if (ring.length == 4) {
                    frame.box(ring[k], 0, bellY + foot + h * 0.5, 0, turn, 2.0 * r, h, 0.8284 * r);
                } else {
                    frame.box(ring[k], 0, bellY + foot + h * 0.5, 0, turn, 1.84 * r, h, 1.84 * r);
                }
            }
        }
        frame.box(loop, 0, bellY + BELL_TOP + 0.20, 0, bellRot, 0.62, 0.44, 0.16);
        frame.box(barnacles[0], onBell(1.36, 0.40, 0.38), bellY, bellRot, 0.34, 0.30, 0.34);
        frame.box(barnacles[1], onBell(-0.80, 1.30, -0.92), bellY, bellRot, 0.26, 0.26, 0.26);
        frame.box(barnacles[2], onBell(-1.40, 0.34, 0.22), bellY, bellRot, 0.30, 0.22, 0.30);

        // the crab under it
        double bodyY = 0.72 + p.lift + bob;
        frame.box(body, 0, bodyY * out + in, 0.15 * out, root, 2.0 * out + 0.1, 0.62 * out + 0.05, 2.1 * out + 0.1);
        frame.box(face, 0, (bodyY + 0.02) * out + in, 1.28 * out, root, 1.5 * out, 0.66 * out, 0.62 * out);

        // eyes on stalks, peering out from under the rim
        double eyeSize = 0.36 * out * Math.max(0.0, p.eyes);
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? 1.0 : -1.0;
            double bx = side * 0.38;
            double tx = side * 0.52 + p.glance * 0.10;
            double baseY = bodyY + 0.30;
            double topY = bodyY + 1.02 + (p.eyes > 1.0 ? (p.eyes - 1.0) * 0.25 : 0.0);
            frame.bone(stalk[i], bx * out, baseY * out + in, 1.42 * out, tx * out, topY * out + in, 1.72 * out, 0.13 * out);
            double ex = tx * out;
            double ey = (topY + 0.14) * out + in;
            double ez = 1.74 * out;
            frame.box(eye[i], ex, ey, ez, root, eyeSize, Math.max(0.03, eyeSize * (p.eyes < 0.5 ? 0.25 : 1.0)), eyeSize);
            double px = p.glance * 0.07;
            double py = 0.0;
            if (p.dizzy) {
                px = Math.cos(p.dizzyAngle + i * Math.PI) * 0.08;
                py = Math.sin(p.dizzyAngle + i * Math.PI) * 0.08;
            }
            double pupilSize = p.eyes < 0.5 ? 0.0 : 0.17 * out * Math.min(1.25, p.eyes);
            frame.box(pupil[i], ex + px, ey + py, ez + 0.13 * out, root, pupilSize, pupilSize, 0.12 * out);
        }

        // claws: index 1 (his right) is the big one
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? 1.0 : -1.0;
            double big = i == 1 ? 1.35 : 0.9;
            double rear = i == 1 ? clamp(p.rear) : 0.0;
            double thrust = i == 1 ? clamp(p.thrust) : 0.0;
            double up = clamp(p.surrender);
            double sx = side * 0.98;
            double sy = bodyY + 0.02;
            double sz = 1.05;
            // where the pincer is: at rest in front of him, reared high and back, thrust far ahead, or up in the air
            double wx = side * 1.25;
            double wy = bodyY + 0.02 + Math.sin(p.stride + i) * 0.03 * p.walk;
            double wz = 2.25;
            wx = lerp(wx, side * 1.75, rear);
            wy = lerp(wy, bodyY + 2.05, rear);
            wz = lerp(wz, 0.85, rear);
            wx = lerp(wx, side * 0.35, thrust);
            wy = lerp(wy, bodyY + 0.25, thrust);
            wz = lerp(wz, Math.max(2.6, Math.min(4.15, p.reach)), thrust);
            wx = lerp(wx, side * 1.55, up);
            wy = lerp(wy, bodyY + 2.6, up);
            wz = lerp(wz, 1.0, up);
            double ex = (sx + wx) * 0.5 + side * 0.42;
            double ey = (sy + wy) * 0.5 + 0.30 - 0.25 * thrust;
            double ez = (sz + wz) * 0.5 - 0.10;
            double thick = 0.26 * big * out;
            frame.bone(upperArm[i], sx * out, sy * out + in, sz * out, ex * out, ey * out + in, ez * out, thick);
            frame.bone(foreArm[i], ex * out, ey * out + in, ez * out, wx * out, wy * out + in, wz * out, thick * 1.1);
            // the pincer points on from the forearm; it gapes when reared, snaps shut on the thrust
            double dx = wx - ex;
            double dy = wy - ey;
            double dz = wz - ez;
            double len = Math.max(1.0e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
            dx /= len;
            dy /= len;
            dz /= len;
            double reach = 0.52 * big;
            double cx = wx + dx * reach;
            double cy = wy + dy * reach;
            double cz = wz + dz * reach;
            double gape = (0.10 + 0.34 * rear + 0.22 * up) * (1.0 - thrust) * big;
            Quaternionf along = new Quaternionf(root).mul(new Quaternionf().rotationTo(0f, 0f, 1f, (float) dx, (float) dy, (float) dz));
            frame.box(jawLow[i], cx * out, (cy - 0.13 * big - gape * 0.5) * out + in, cz * out, along,
                    0.52 * big * out, 0.30 * big * out, 1.05 * big * out);
            frame.box(jawHigh[i], cx * out, (cy + 0.15 * big + gape * 0.5) * out + in, (cz + 0.05) * out, along,
                    0.44 * big * out, 0.22 * big * out, 0.95 * big * out);
        }

        // six legs, three a side, stepping in two tripods
        for (int i = 0; i < 6; i++) {
            int row = i / 2;
            double side = i % 2 == 0 ? 1.0 : -1.0;
            double hz = 0.55 - row * 0.72;
            double phase = p.stride + ((row + (side > 0 ? 0 : 1)) % 2 == 0 ? 0.0 : Math.PI);
            double step = Math.max(0.0, Math.sin(phase)) * 0.34 * p.walk;
            double swing = Math.cos(phase) * 0.30 * p.walk;
            double hx = side * 1.0;
            double hy = bodyY - 0.05;
            double fx = side * (2.05 + 0.1 * row);
            double fy = step;
            double fz = hz * 1.55 + 0.15 + swing;
            if (p.tilt > 4.0) {
                // on his back: the legs paddle the air
                double kick = Math.sin(p.dizzyAngle * 1.7 + i) * 0.25;
                fx = side * (1.7 + kick * 0.3);
                fy = bodyY + 0.75 + kick;
                fz = hz * 1.3 + 0.2;
            }
            double kx = (hx + fx) * 0.5 + side * 0.22;
            double ky = Math.max(hy, fy) + 0.55;
            double kz = (hz + fz) * 0.5;
            double thick = 0.21 * out;
            frame.bone(thigh[i], hx * out, hy * out + in, hz * out, kx * out, ky * out + in, kz * out, thick);
            frame.bone(shin[i], kx * out, ky * out + in, kz * out, fx * out, fy * out + in, fz * out, thick * 0.85);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** A point on the bell, in the bell's own space (it spins with the bell): x, height above the rim's foot, z. */
    private static double[] onBell(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    /** One draw call's worth of shared numbers. */
    private final class Frame {
        private final Pose pose;
        private final Quaternionf root;
        private final double scale;
        private final int interp;

        Frame(Pose pose, Quaternionf root, double scale, int interp) {
            this.pose = pose;
            this.root = root;
            this.scale = scale;
            this.interp = interp;
        }

        /** A box centred on the local point, turned by {@code rot} (a world rotation). */
        void box(Part part, double lx, double ly, double lz, Quaternionf rot, double sx, double sy, double sz) {
            Vector3f local = new Vector3f((float) (lx * scale), (float) (ly * scale), (float) (lz * scale));
            root.transform(local);
            part.box(pose.x + local.x - anchor.getX(), pose.y + local.y - anchor.getY(), pose.z + local.z - anchor.getZ(),
                    rot, sx * scale, sy * scale, sz * scale, interp);
        }

        /** A box stuck on the bell: the point is in the bell's own space (it spins with it). */
        void box(Part part, double[] onBell, double bellY, Quaternionf bellRot, double sx, double sy, double sz) {
            Vector3f local = new Vector3f((float) (onBell[0] * scale), (float) (onBell[1] * scale), (float) (onBell[2] * scale));
            bellRot.transform(local);
            Vector3f lift = new Vector3f(0f, (float) (bellY * scale), 0f);
            root.transform(lift);
            part.box(pose.x + local.x + lift.x - anchor.getX(), pose.y + local.y + lift.y - anchor.getY(),
                    pose.z + local.z + lift.z - anchor.getZ(), bellRot, sx * scale, sy * scale, sz * scale, interp);
        }

        /** A beam between two local points. */
        void bone(Part part, double ax, double ay, double az, double bx, double by, double bz, double thickness) {
            Vector3f a = new Vector3f((float) (ax * scale), (float) (ay * scale), (float) (az * scale));
            Vector3f b = new Vector3f((float) (bx * scale), (float) (by * scale), (float) (bz * scale));
            root.transform(a);
            root.transform(b);
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float dz = b.z - a.z;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-3f || thickness * scale < 1.0e-3) {
                part.hide(interp);
                return;
            }
            Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, dx / len, dy / len, dz / len);
            part.box(pose.x + (a.x + b.x) * 0.5 - anchor.getX(), pose.y + (a.y + b.y) * 0.5 - anchor.getY(),
                    pose.z + (a.z + b.z) * 0.5 - anchor.getZ(), q, thickness * scale, len, thickness * scale, interp);
        }
    }

    /** One block display, posed through its transformation; unchanged poses cost nothing. */
    private static final class Part {
        private static final float EPS = 0.002f;
        private final BlockDisplay display;
        private final Vector3f lastT = new Vector3f(Float.NaN, 0f, 0f);
        private final Vector3f lastS = new Vector3f();
        private final Quaternionf lastR = new Quaternionf();

        Part(BlockDisplay display) {
            this.display = display;
        }

        /** A box of the given size centred on {@code (cx, cy, cz)} (relative to the anchor), turned by {@code rot}. */
        void box(double cx, double cy, double cz, Quaternionf rot, double sx, double sy, double sz, int interp) {
            Quaternionf q = rot == null ? IDENTITY : rot;
            Vector3f half = new Vector3f((float) (sx * 0.5), (float) (sy * 0.5), (float) (sz * 0.5));
            q.transform(half);
            apply(new Vector3f((float) cx - half.x, (float) cy - half.y, (float) cz - half.z), q,
                    new Vector3f((float) sx, (float) sy, (float) sz), interp);
        }

        void hide(int interp) {
            apply(lastT.isFinite() ? new Vector3f(lastT) : new Vector3f(), lastR, new Vector3f(0f, 0f, 0f), interp);
        }

        private void apply(Vector3f t, Quaternionf q, Vector3f s, int interp) {
            if (!display.isValid()) {
                return;
            }
            if (lastT.isFinite() && near(t, lastT) && near(s, lastS) && Math.abs(q.dot(lastR)) > 1f - 1.0e-6f) {
                return;
            }
            lastT.set(t);
            lastS.set(s);
            lastR.set(q);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(Math.max(0, interp));
            display.setTransformation(new Transformation(t, new Quaternionf(q), s, new Quaternionf()));
        }

        private static boolean near(Vector3f a, Vector3f b) {
            return Math.abs(a.x - b.x) < EPS && Math.abs(a.y - b.y) < EPS && Math.abs(a.z - b.z) < EPS;
        }

        void remove() {
            if (display.isValid()) {
                display.remove();
            }
        }
    }
}
