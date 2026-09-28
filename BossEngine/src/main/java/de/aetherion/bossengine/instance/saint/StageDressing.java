package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-fight set dressing on top of the block stage: the cloud ceiling the Hand hides behind,
 * follow-spots that pick out Seraphine and her next victim (and carry real moving light),
 * a skull audience that turns its heads to watch, and the theatre's private night sky.
 */
final class StageDressing {

    static final float CLOUD_Y = 31f;
    /** Flip if the skulls render facing away from their target (item-model orientation). */
    private static final float SKULL_YAW_OFFSET = SaintMath.PI;

    private final SaintFx fx;
    private final SaintStage stage;

    /* clouds */
    private final List<BlockDisplay> clouds = new ArrayList<>();
    private final List<float[]> cloudSpec = new ArrayList<>();
    private float cloudDrift;
    private float cloudOpen;
    private float cloudOpenTarget;

    /* spots */
    final Spot key;
    final Spot hunter;
    final Spot third;

    /* audience */
    private final List<ItemDisplay> skulls = new ArrayList<>();
    private final List<Vector3f> skullSeats = new ArrayList<>();
    private final List<float[]> skullFall = new ArrayList<>();
    private Vector3f gaze = new Vector3f();
    private boolean headsFallen;

    /* sky */
    private final Set<UUID> timed = new HashSet<>();
    private float time = 13000f;
    private float timeTarget = 13000f;
    private float timeRate = 60f;

    private int clock;

    StageDressing(SaintFx fx, SaintStage stage) {
        this.fx = fx;
        this.stage = stage;
        this.key = new Spot("key", new Vector3f(0f, 5.5f, 29.5f), Color.fromRGB(255, 232, 170));
        this.hunter = new Spot("hunter", new Vector3f(-11f, 14.5f, -16.8f), Color.fromRGB(255, 120, 120));
        this.third = new Spot("third", new Vector3f(11f, 14.5f, -16.8f), Color.fromRGB(255, 232, 170));
    }

    void spawn() {
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = i * 2.39996;
            float r = (float) (4 + (i * 7.3 % 20));
            float x = (float) Math.sin(a) * r;
            float z = (float) Math.cos(a) * r;
            float y = CLOUD_Y + (i % 3) * 1.1f;
            float sx = 9f + (i * 5.1f % 7f);
            float sz = 7f + (i * 3.7f % 6f);
            cloudSpec.add(new float[]{x, y, z, sx, sz, (float) a});
            Material m = i % 3 == 0 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
            clouds.add(fx.block(m, null, 7));
        }
        pushClouds(0);

        key.spawn();
        hunter.spawn();
        third.spawn();

        List<Vector3f> seats = stage.seats();
        for (int i = 0; i < seats.size() && skulls.size() < 34; i += 2) {
            Vector3f seat = seats.get(i);
            skullSeats.add(seat);
            skulls.add(fx.item(new ItemStack(Material.SKELETON_SKULL), 9));
            skullFall.add(null);
        }
        lookAt(new Vector3f(0, 3, 0), 0);
    }

    boolean intact() {
        for (BlockDisplay d : clouds) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        return key.intact() && hunter.intact() && third.intact();
    }

    void tick() {
        clock++;
        cloudDrift += 0.0012f;
        cloudOpen += (cloudOpenTarget - cloudOpen) * 0.03f;
        if (clock % 20 == 0) {
            pushClouds(20);
        }
        if (Math.abs(time - timeTarget) > 1f) {
            time += Math.signum(timeTarget - time) * Math.min(timeRate, Math.abs(timeTarget - time));
        }
        if (clock % 5 == 0) {
            applyTime();
        }
    }

    /* ------------------------------------------------------------------ clouds */

    void parting(float open) {
        cloudOpenTarget = open;
    }

    /** Immediately push clouds toward their current open target over {@code ticks}. */
    void pushCloudsNow(int ticks) {
        cloudOpen = cloudOpenTarget;
        pushClouds(ticks);
    }

    private void pushClouds(int interp) {
        for (int i = 0; i < clouds.size(); i++) {
            float[] s = cloudSpec.get(i);
            float r = (float) Math.sqrt(s[0] * s[0] + s[2] * s[2]);
            float a = (float) Math.atan2(s[0], s[2]) + cloudDrift;
            float rr = r + cloudOpen * 26f;
            Vector3f c = new Vector3f((float) Math.sin(a) * rr, s[1] + cloudOpen * 3f, (float) Math.cos(a) * rr);
            SaintFx.push(clouds.get(i), SaintFx.flat(c, s[3], 0.7f, s[4], s[5] + cloudDrift), interp);
        }
    }

    /* ------------------------------------------------------------------ audience */

    void lookAt(Vector3f target, int interp) {
        gaze = new Vector3f(target);
        for (int i = 0; i < skulls.size(); i++) {
            if (skullFall.get(i) != null) {
                continue;
            }
            Vector3f seat = skullSeats.get(i);
            Vector3f head = new Vector3f(seat).add(0, 0.55f, 0);
            float yaw = SaintMath.yawToward(target.x - head.x, target.z - head.z) + SKULL_YAW_OFFSET;
            float dist = SaintMath.horizontal(target, head);
            float pitch = (float) Math.atan2(head.y - target.y, Math.max(1f, dist)) * 0.8f;
            Matrix4f m = new Matrix4f().translation(head).rotateY(yaw).rotateX(-pitch).scale(0.95f);
            SaintFx.push(skulls.get(i), m, interp);
        }
    }

    /** All heads snap to the same point at once, with one dry wooden clack. */
    void snapGaze(Vector3f target) {
        lookAt(target, 2);
        fx.score(Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON, 1f, 0.5f);
        fx.score(Sound.ENTITY_SKELETON_STEP, 0.8f, 0.6f);
    }

    /** Heads idly turn toward whatever interests them. */
    void drift(Vector3f saint, List<Player> players, int tick) {
        if (headsFallen || tick % 12 != 0) {
            return;
        }
        Vector3f target = saint;
        if (!players.isEmpty() && (tick / 60) % 3 == 2) {
            target = fx.stage(players.get((tick / 180) % players.size()).getLocation());
        }
        if (target.distanceSquared(gaze) > 0.5f) {
            lookAt(target, 12);
        }
    }

    /** Applause: dry, wooden, uneven. Intensity 0..1. */
    void applause(float intensity) {
        if (skullSeats.isEmpty()) {
            return;
        }
        int claps = Math.round(intensity * 5);
        for (int i = 0; i < claps; i++) {
            Vector3f seat = skullSeats.get((int) (Math.random() * skullSeats.size()));
            Sound s = Math.random() < 0.5 ? Sound.BLOCK_WOODEN_BUTTON_CLICK_ON : Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON;
            fx.sound(seat, s, 1.6f, 0.7f + (float) Math.random() * 0.7f);
        }
    }

    /** Every head nods on the same beat: the audience is enjoying itself. */
    void nod(float amount, int interp) {
        for (int i = 0; i < skulls.size(); i++) {
            if (skullFall.get(i) != null) {
                continue;
            }
            Vector3f seat = skullSeats.get(i);
            Vector3f head = new Vector3f(seat).add(0, 0.55f - amount * 0.1f, 0);
            float yaw = SaintMath.yawToward(gaze.x - head.x, gaze.z - head.z) + SKULL_YAW_OFFSET;
            Matrix4f m = new Matrix4f().translation(head).rotateY(yaw).rotateX(amount * 0.6f).scale(0.95f);
            SaintFx.push(skulls.get(i), m, interp);
        }
    }

    /** The play is over: every head floats back up onto its seat and looks at the stage. */
    void restoreHeads(Vector3f gazeAt, int interp) {
        headsFallen = false;
        for (int i = 0; i < skullFall.size(); i++) {
            skullFall.set(i, null);
        }
        lookAt(gazeAt, interp);
    }

    /** The audience loses its heads: every skull tips off its seat and tumbles. */
    void dropHeads() {
        headsFallen = true;
        for (int i = 0; i < skulls.size(); i++) {
            Vector3f seat = skullSeats.get(i);
            float side = (float) (Math.random() - 0.5) * 1.2f;
            skullFall.set(i, new float[]{seat.x + side, seat.y - 0.35f, seat.z + 0.8f, (float) Math.random() * 6f});
            Vector3f land = new Vector3f(seat.x + side, seat.y - 0.3f, seat.z + 0.6f);
            Matrix4f m = new Matrix4f().translation(land)
                    .rotateY((float) (Math.random() * SaintMath.TAU))
                    .rotateX(SaintMath.HALF_PI * (Math.random() < 0.5 ? 1 : -1))
                    .scale(0.95f);
            SaintFx.push(skulls.get(i), m, 8 + (int) (Math.random() * 10));
        }
        fx.score(Sound.ENTITY_SKELETON_DEATH, 0.8f, 0.5f);
        fx.score(Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.6f);
    }

    /* ------------------------------------------------------------------ sky */

    void sky(float target, float ratePerTick) {
        timeTarget = target;
        timeRate = Math.max(1f, ratePerTick);
    }

    void skyNow(float t) {
        time = t;
        timeTarget = t;
        applyTime();
    }

    private void applyTime() {
        Set<UUID> seen = new HashSet<>();
        for (Player p : fx.audience()) {
            p.setPlayerTime((long) time, false);
            seen.add(p.getUniqueId());
        }
        for (UUID id : new ArrayList<>(timed)) {
            if (!seen.contains(id)) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) {
                    p.resetPlayerTime();
                }
                timed.remove(id);
            }
        }
        timed.addAll(seen);
    }

    void clear() {
        for (BlockDisplay d : clouds) {
            SaintFx.kill(d);
        }
        clouds.clear();
        cloudSpec.clear();
        key.remove();
        hunter.remove();
        third.remove();
        for (ItemDisplay d : skulls) {
            SaintFx.kill(d);
        }
        skulls.clear();
        skullSeats.clear();
        skullFall.clear();
        for (UUID id : timed) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) {
                p.resetPlayerTime();
            }
        }
        timed.clear();
    }

    /* ------------------------------------------------------------------ spot */

    /** A theatre follow-spot: a translucent beam from a lamp to a pool of light on the boards. */
    final class Spot {
        final String slot;
        final Vector3f source;
        final Color tint;
        private BlockDisplay beam;
        private BlockDisplay poolA;
        private BlockDisplay poolB;
        private boolean on;
        private final Vector3f aim = new Vector3f();
        private float radius = 1.8f;

        Spot(String slot, Vector3f source, Color tint) {
            this.slot = slot;
            this.source = source;
            this.tint = tint;
        }

        void spawn() {
            beam = fx.block(Material.YELLOW_STAINED_GLASS, null, 15);
            poolA = fx.block(Material.YELLOW_STAINED_GLASS, null, 15);
            poolB = fx.block(Material.YELLOW_STAINED_GLASS, null, 15);
        }

        boolean intact() {
            return beam != null && beam.isValid() && poolA != null && poolA.isValid();
        }

        void set(boolean lit) {
            if (lit != on) {
                fx.sound(source, lit ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.4f, 0.55f);
                fx.sound(source, Sound.BLOCK_LEVER_CLICK, 1.2f, 0.5f);
            }
            on = lit;
            if (!lit) {
                SaintFx.push(beam, SaintFx.gone(source), 2);
                SaintFx.push(poolA, SaintFx.gone(aim), 2);
                SaintFx.push(poolB, SaintFx.gone(aim), 2);
                stage.light(slot, null, 0);
            }
        }

        void material(Material m) {
            for (BlockDisplay d : new BlockDisplay[]{beam, poolA, poolB}) {
                if (d != null && d.isValid()) {
                    d.setBlock(m.createBlockData());
                }
            }
        }

        void size(float r) {
            radius = r;
        }

        void aim(Vector3f floorPoint, int interp) {
            aim.set(floorPoint.x, 0.03f, floorPoint.z);
            if (!on) {
                return;
            }
            SaintFx.push(beam, SaintFx.beam(source, aim, 0.75f), interp);
            float d = radius * 2f;
            SaintFx.push(poolA, SaintFx.flat(new Vector3f(aim.x, 0.04f, aim.z), d, 0.02f, d, 0f), interp);
            SaintFx.push(poolB, SaintFx.flat(new Vector3f(aim.x, 0.045f, aim.z), d, 0.02f, d, SaintMath.PI / 4f), interp);
            if (clock % 4 == 0) {
                stage.light(slot, new Vector3f(aim.x, 1.2f, aim.z), 13);
            }
        }

        Vector3f aimPoint() {
            return new Vector3f(aim);
        }

        void remove() {
            SaintFx.kill(beam);
            SaintFx.kill(poolA);
            SaintFx.kill(poolB);
            stage.light(slot, null, 0);
        }
    }
}
