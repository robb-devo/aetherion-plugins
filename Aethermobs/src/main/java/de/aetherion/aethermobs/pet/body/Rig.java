package de.aetherion.aethermobs.pet.body;

import de.aetherion.core.AetherKeys;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A pet body: a small bone tree whose parts are {@link Display} entities riding one invisible anchor.
 *
 * <p>The anchor carries position (one teleport per move, smoothed by {@code teleport_duration}); every part
 * carries its full pose as a transformation matrix, heading included, so turning and walking cost one
 * metadata write per changed part per keyframe. Keyframes are sparse (gait extremes) and the client
 * interpolates between them. Parts are hidden by default and shown only to players close enough to care,
 * wild rigs fall asleep (parts removed) when nobody is near, and a global per-tick budget caps the writes.
 */
public final class Rig {

    /** Part detail tier: core reads from afar, detail only up close. */
    public static final int CORE = 0;
    public static final int DETAIL = 1;
    /** Rarity extra: only built for rare+ instances (decided by the catalog). */
    public static final int EXTRA = 2;

    static final int SHOW_DISTANCE = 44;
    static final int HIDE_DISTANCE = 52;
    static final int DRAGON_SHOW_DISTANCE = 64;
    static final int DRAGON_HIDE_DISTANCE = 72;
    /** Wild rigs with no viewer this close for a while drop their parts. */
    static final int SLEEP_DISTANCE = 48;
    static final int NEAR_DISTANCE = 18;

    public static final class Bone {
        final String name;
        final int parent;
        final Vector3f pivot;
        final Quaternionf rest;
        /** Animated, set by the gait each keyframe. */
        public final Quaternionf rot = new Quaternionf();
        public final Vector3f off = new Vector3f();
        /** Scales only the parts attached to this bone (breathing, puffing, squash). */
        public final Vector3f scale = new Vector3f(1f, 1f, 1f);
        final Quaternionf worldRot = new Quaternionf();
        final Vector3f worldPos = new Vector3f();

        Bone(String name, int parent, Vector3f pivot, Quaternionf rest) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rest = rest;
        }

        public void reset() {
            rot.identity();
            off.set(0f, 0f, 0f);
            scale.set(1f, 1f, 1f);
        }
    }

    public static final class Part {
        final boolean block;
        final BlockData blockData;
        final ItemStack item;
        final int bone;
        final Vector3f center;
        final Vector3f size;
        final Quaternionf rot;
        final int tier;
        boolean bright;
        boolean shadow;
        float viewRange = 0.7f;
        /** Hidden parts stay spawned at zero scale (wings folded away, carry ball, bubble). */
        boolean hidden;
        Display entity;
        final Matrix4f last = new Matrix4f().zero();

        Part(boolean block, BlockData blockData, ItemStack item, int bone, Vector3f center, Vector3f size,
             Quaternionf rot, int tier) {
            this.block = block;
            this.blockData = blockData;
            this.item = item;
            this.bone = bone;
            this.center = center;
            this.size = size;
            this.rot = rot;
            this.tier = tier;
        }

        public Part bright() {
            this.bright = true;
            return this;
        }

        public Part shadow() {
            this.shadow = true;
            return this;
        }

        public Part view(float range) {
            this.viewRange = range;
            return this;
        }

        public Part hidden() {
            this.hidden = true;
            return this;
        }
    }

    final Skeleton skeleton;
    final Roles roles = new Roles();
    final List<Bone> bones = new ArrayList<>();
    final List<Part> parts = new ArrayList<>();
    final boolean dragon;

    /** Species x rarity x equipped size. */
    float size = 1f;
    /** Grow/shrink factor for materialize, capture and sleep wake. */
    float presence = 1f;
    /** Rig attitude, set by the gait (degrees). */
    float pitch;
    float roll;
    /** Vertical offset of the whole body from the anchor (bob, submerge, hop arc). */
    float lift;
    /** Half height of the main body box (squash pivots on the feet, not the middle). */
    float bodyHalf = 0.2f;
    /** Body heading in Minecraft yaw degrees. */
    float heading;
    /** Head look offset from the body heading (degrees, clamped by the gait). */
    float lookOffset;
    /** Nameplate height above the anchor at size 1. */
    float top = 1.0f;
    /** Body centre height above the anchor at size 1 (catch beam target, FX). */
    float middle = 0.4f;

    /** Capture override: every part collapses onto this rig-space point. */
    Vector3f collapseTo;

    private ItemDisplay anchor;
    private Plugin plugin;
    private boolean built;
    private boolean glowing;
    private Color glowColor;
    private final Set<UUID> viewers = new HashSet<>();
    private int viewerCheck;
    private double nearestSq = Double.MAX_VALUE;
    private int lonelyTicks;
    private boolean neverSleep;

    Rig(Skeleton skeleton, boolean dragon) {
        this.skeleton = skeleton;
        this.dragon = dragon;
        bones.add(new Bone("root", -1, new Vector3f(), new Quaternionf()));
    }

    /*
     * =========================================================
     * BUILD (catalog side)
     * =========================================================
     */

    public int bone(String name, int parent, float x, float y, float z) {
        bones.add(new Bone(name, parent, new Vector3f(x, y, z), new Quaternionf()));
        return bones.size() - 1;
    }

    public int bone(String name, int parent, float x, float y, float z, float pitchDeg, float yawDeg, float rollDeg) {
        bones.add(new Bone(name, parent, new Vector3f(x, y, z), euler(pitchDeg, yawDeg, rollDeg)));
        return bones.size() - 1;
    }

    /** A box centred at (cx,cy,cz) in bone space, size (sx,sy,sz), optional rotation about its centre. */
    public Part box(int bone, BlockData data, float cx, float cy, float cz, float sx, float sy, float sz,
                    float pitchDeg, float yawDeg, float rollDeg, int tier) {
        Part p = new Part(true, data, null, bone, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz),
                euler(pitchDeg, yawDeg, rollDeg), tier);
        // Template placeholders (zero-size slots) never become entities.
        if (sx >= 0.004f && sy >= 0.004f && sz >= 0.004f) {
            parts.add(p);
        }
        return p;
    }

    public Part box(int bone, BlockData data, float cx, float cy, float cz, float sx, float sy, float sz, int tier) {
        return box(bone, data, cx, cy, cz, sx, sy, sz, 0f, 0f, 0f, tier);
    }

    /** An item model centred at (cx,cy,cz); player heads are a 0.5 cube at scale 1. */
    public Part item(int bone, ItemStack item, float cx, float cy, float cz, float scale,
                     float pitchDeg, float yawDeg, float rollDeg, int tier) {
        Part p = new Part(false, null, item, bone, new Vector3f(cx, cy, cz), new Vector3f(scale, scale, scale),
                euler(pitchDeg, yawDeg, rollDeg), tier);
        if (scale >= 0.004f && item != null) {
            parts.add(p);
        }
        return p;
    }

    public Bone bone(int index) {
        return bones.get(index);
    }

    public Roles roles() {
        return roles;
    }

    public Skeleton skeleton() {
        return skeleton;
    }

    public boolean isDragon() {
        return dragon;
    }

    public int partCount() {
        return parts.size();
    }

    static Quaternionf euler(float pitchDeg, float yawDeg, float rollDeg) {
        Quaternionf q = new Quaternionf();
        if (yawDeg != 0f) {
            q.rotateY((float) Math.toRadians(yawDeg));
        }
        if (pitchDeg != 0f) {
            q.rotateX((float) Math.toRadians(-pitchDeg));
        }
        if (rollDeg != 0f) {
            q.rotateZ((float) Math.toRadians(rollDeg));
        }
        return q;
    }

    /*
     * =========================================================
     * LIFECYCLE
     * =========================================================
     */

    public void attach(Plugin plugin, ItemDisplay anchor, boolean neverSleep) {
        this.plugin = plugin;
        this.anchor = anchor;
        this.neverSleep = neverSleep;
        refreshViewers(true);
        build();
        wake = false;
    }

    public boolean isBuilt() {
        return built;
    }

    public ItemDisplay anchor() {
        return anchor;
    }

    private void build() {
        if (built || anchor == null || !anchor.isValid()) {
            return;
        }
        World world = anchor.getWorld();
        Location at = anchor.getLocation();
        at.setYaw(0f);
        at.setPitch(0f);
        computeBones();
        for (Part part : parts) {
            Matrix4f m = partMatrix(part);
            Display display;
            if (part.block) {
                display = world.spawn(at, BlockDisplay.class, d -> {
                    prepare(d, part);
                    d.setBlock(part.blockData);
                    d.setTransformationMatrix(m);
                });
            } else {
                display = world.spawn(at, ItemDisplay.class, d -> {
                    prepare(d, part);
                    d.setItemStack(part.item);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setTransformationMatrix(m);
                });
            }
            if (!anchor.addPassenger(display)) {
                // A part that cannot ride would float behind on its own: drop it instead.
                display.remove();
                continue;
            }
            part.entity = display;
            part.last.set(m);
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.showEntity(plugin, display);
                }
            }
        }
        built = true;
    }

    private void prepare(Display d, Part part) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.setSilent(true);
        d.setGravity(false);
        d.setInvulnerable(true);
        d.getPersistentDataContainer().set(AetherKeys.PET_ENTITY, PersistentDataType.BYTE, (byte) 1);
        d.setRotation(0f, 0f);
        d.setBillboard(Display.Billboard.FIXED);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(2);
        d.setViewRange(dragon ? Math.max(part.viewRange, 0.9f) : part.viewRange);
        if (part.bright) {
            d.setBrightness(new Display.Brightness(15, 15));
        }
        if (part.shadow) {
            d.setShadowRadius(Math.max(0.15f, 0.42f * size));
            d.setShadowStrength(0.55f);
        }
        if (glowing) {
            d.setGlowing(true);
            d.setGlowColorOverride(glowColor);
        }
    }

    /** Drop the parts but keep the rig definition (sleep / world hop). */
    public void teardown() {
        for (Part part : parts) {
            if (part.entity != null) {
                try {
                    part.entity.remove();
                } catch (Throwable ignored) {
                }
                part.entity = null;
            }
            part.last.zero();
        }
        built = false;
    }

    public void remove() {
        teardown();
        viewers.clear();
        anchor = null;
    }

    /** Move the anchor and everything riding it (one packet; the client glides). */
    public static boolean moveAnchor(Entity anchor, Location to) {
        if (anchor == null || to == null || to.getWorld() == null) {
            return false;
        }
        if (anchor.getPassengers().isEmpty()) {
            return anchor.teleport(to);
        }
        if (!to.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        return anchor.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /*
     * =========================================================
     * VIEWERS + SLEEP
     * =========================================================
     */

    /** Returns true while the rig should keep animating at all (somebody can see it). */
    boolean refreshViewers(boolean force) {
        if (anchor == null || !anchor.isValid()) {
            return false;
        }
        if (!force && --viewerCheck > 0) {
            return built;
        }
        viewerCheck = 10;
        Location at = anchor.getLocation();
        World world = at.getWorld();
        int show = dragon ? DRAGON_SHOW_DISTANCE : SHOW_DISTANCE;
        int hide = dragon ? DRAGON_HIDE_DISTANCE : HIDE_DISTANCE;
        double showSq = (double) show * show;
        double hideSq = (double) hide * hide;
        double nearest = Double.MAX_VALUE;
        Set<UUID> seen = new HashSet<>();
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(at);
            if (d < nearest) {
                nearest = d;
            }
            UUID id = player.getUniqueId();
            seen.add(id);
            boolean has = viewers.contains(id);
            if (!has && d <= showSq) {
                viewers.add(id);
                if (built) {
                    for (Part part : parts) {
                        if (part.entity != null) {
                            player.showEntity(plugin, part.entity);
                        }
                    }
                }
            } else if (has && d > hideSq) {
                viewers.remove(id);
                if (built) {
                    for (Part part : parts) {
                        if (part.entity != null) {
                            player.hideEntity(plugin, part.entity);
                        }
                    }
                }
            }
        }
        viewers.retainAll(seen);
        nearestSq = nearest;

        if (neverSleep) {
            if (!built) {
                build();
            }
            return true;
        }
        if (nearest > (double) SLEEP_DISTANCE * SLEEP_DISTANCE) {
            lonelyTicks += 10;
            if (built && lonelyTicks >= 60) {
                teardown();
            }
            return built;
        }
        lonelyTicks = 0;
        if (!built) {
            build();
            wake = true;
        }
        return true;
    }

    /** Set by {@link #refreshViewers} when a sleeping rig rebuilt; the owner grows it in. */
    boolean wake;

    public double nearestViewerSq() {
        return nearestSq;
    }

    boolean near() {
        return nearestSq <= (double) NEAR_DISTANCE * NEAR_DISTANCE;
    }

    /*
     * =========================================================
     * POSE -> DISPLAYS
     * =========================================================
     */

    private void computeBones() {
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            if (b.parent < 0) {
                b.worldRot.set(b.rest).mul(b.rot);
                b.worldPos.set(b.pivot).add(b.off);
                continue;
            }
            Bone p = bones.get(b.parent);
            Vector3f local = new Vector3f(b.pivot).add(b.off);
            p.worldRot.transform(local);
            b.worldPos.set(p.worldPos).add(local);
            b.worldRot.set(p.worldRot).mul(b.rest).mul(b.rot);
        }
    }

    private Matrix4f rootMatrix() {
        float k = size * presence;
        return new Matrix4f()
                .translate(0f, lift, 0f)
                .rotateY((float) Math.toRadians(-heading))
                .rotateX((float) Math.toRadians(-pitch))
                .rotateZ((float) Math.toRadians(roll))
                .scale(k);
    }

    private Matrix4f partMatrix(Part part) {
        if (part.hidden) {
            // Constant, so a hidden part (carry sphere, bubble) is never re-sent with the gait.
            return new Matrix4f().translate(0f, middle * size, 0f).scale(0.0001f);
        }
        Matrix4f m = rootMatrix();
        if (presence <= 0.002f) {
            return m.scale(0.0001f);
        }
        Bone b = bones.get(part.bone);
        if (collapseTo != null) {
            // Capture: every part slides into the sphere and shrinks to a point.
            return new Matrix4f().translate(collapseTo).scale(0.0001f);
        }
        m.translate(b.worldPos).rotate(b.worldRot);
        m.translate(part.center).rotate(part.rot);
        m.scale(part.size.x * b.scale.x, part.size.y * b.scale.y, part.size.z * b.scale.z);
        if (part.block) {
            m.translate(-0.5f, -0.5f, -0.5f);
        }
        return m;
    }

    /**
     * Push the current pose. Only parts whose matrix changed are written; returns how many were.
     *
     * @param detail false at mid range: detail parts keep their last pose
     */
    int apply(int interpolationTicks, boolean detail) {
        if (!built) {
            return 0;
        }
        computeBones();
        int sent = 0;
        for (Part part : parts) {
            Display d = part.entity;
            if (d == null || !d.isValid()) {
                continue;
            }
            if (!detail && part.tier != CORE && collapseTo == null && !part.hidden) {
                continue;
            }
            Matrix4f m = partMatrix(part);
            if (m.equals(part.last, 0.0015f)) {
                continue;
            }
            part.last.set(m);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(0, interpolationTicks));
            d.setTransformationMatrix(m);
            sent++;
        }
        return sent;
    }

    /** Glow outline on every part (catch beacon / Wild Sight). Idempotent. */
    public void setGlow(boolean on, Color color) {
        if (glowing == on && (color == null ? glowColor == null : color.equals(glowColor))) {
            return;
        }
        glowing = on;
        glowColor = color;
        for (Part part : parts) {
            if (part.entity != null && part.entity.isValid()) {
                part.entity.setGlowing(on);
                part.entity.setGlowColorOverride(on ? color : null);
            }
        }
    }

    /** World position of a rig-space point at the current pose (FX, beams, breath). */
    public Location worldPoint(int bone, float x, float y, float z) {
        if (anchor == null) {
            return null;
        }
        computeBones();
        Bone b = bones.get(Math.max(0, Math.min(bones.size() - 1, bone)));
        Vector3f v = new Vector3f(x, y, z);
        b.worldRot.transform(v);
        v.add(b.worldPos);
        Matrix4f root = rootMatrix();
        root.transformPosition(v);
        return anchor.getLocation().add(v.x, v.y, v.z);
    }

    /** Rest-pose bounds of the body in rig units (min x,y,z then max x,y,z). */
    public float[] restBounds() {
        for (Bone b : bones) {
            b.reset();
        }
        computeBones();
        float[] out = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f v = new Vector3f();
        for (Part part : parts) {
            if (part == roles.ball || part == roles.bubble) {
                continue;
            }
            Bone b = bones.get(part.bone);
            Matrix4f m = new Matrix4f().translate(b.worldPos).rotate(b.worldRot)
                    .translate(part.center).rotate(part.rot).scale(part.size);
            float lo = part.block ? 0f : -0.25f;
            float hi = part.block ? 1f : 0.25f;
            if (part.block) {
                m.translate(-0.5f, -0.5f, -0.5f);
            }
            for (int i = 0; i < 8; i++) {
                v.set((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? lo : hi, (i & 4) == 0 ? lo : hi);
                m.transformPosition(v);
                out[0] = Math.min(out[0], v.x);
                out[1] = Math.min(out[1], v.y);
                out[2] = Math.min(out[2], v.z);
                out[3] = Math.max(out[3], v.x);
                out[4] = Math.max(out[4], v.y);
                out[5] = Math.max(out[5], v.z);
            }
        }
        if (out[0] > out[3]) {
            return new float[]{-0.3f, -0.3f, -0.3f, 0.3f, 0.3f, 0.3f};
        }
        return out;
    }

    public float scaledTop() {
        return top * size + lift;
    }

    public float scaledMiddle() {
        return middle * size + lift;
    }

    public float size() {
        return size;
    }

    /** Roles the gaits animate by name. */
    public static final class Roles {
        public int body = -1;
        public int chest = -1;
        public int head = -1;
        public int jaw = -1;
        public int[] neck = new int[0];
        public int[] tail = new int[0];
        /** Quadrupeds FL, FR, BL, BR; bipeds L, R; spiders pairs front to back. */
        public int[] legs = new int[0];
        public int[] arms = new int[0];
        /** L, R, then outer L, outer R. */
        public int[] wings = new int[0];
        public int[] ears = new int[0];
        public int[] fins = new int[0];
        public int[] tentacles = new int[0];
        public int[] orbit = new int[0];
        public int[] segments = new int[0];
        public int[] spikes = new int[0];
        /** Carry sphere (equipped ground pets inside their ball) and the water bubble. */
        public Part ball;
        public Part bubble;
        public final List<Part> bodyParts = new ArrayList<>();
    }
}
