package de.aetherion.bossengine.helios.core;

import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One fixed stage anchor (the arena center, floor surface) that every display of the encounter is
 * spawned on. Displays never teleport: all motion is transformation interpolation relative to the
 * anchor, so the whole show stays smooth on the client and one metadata packet moves one piece.
 *
 * <p>Displays are spawned through {@link Group}s. An attack owns a group and clears it when it ends,
 * the stage owns every group and clears them all on abort, so nothing can leak. Transforms are always
 * built from explicit translation / rotation / scale (never a raw matrix), so rotations cannot flip.
 *
 * <p>{@link #timeScale} is the encounter's slow motion: every interpolation is stretched by it.
 */
public final class HeliosStage {

    public static final float VIEW_RANGE = 4f;

    private final Location anchor;
    private final World world;
    private final BossKeys keys;
    private final UUID owner;
    private final DisplayBudget budget;
    private final List<Group> groups = new ArrayList<>();
    private final Group root;
    private Supplier<List<Player>> audience = List::of;
    private Supplier<List<Player>> fighters = List::of;
    private float timeScale = 1f;

    public HeliosStage(Location anchor, BossKeys keys, UUID owner, DisplayBudget budget) {
        this.anchor = anchor.clone();
        this.anchor.setYaw(0f);
        this.anchor.setPitch(0f);
        this.world = anchor.getWorld();
        this.keys = keys;
        this.owner = owner;
        this.budget = budget;
        this.root = group();
    }

    public HeliosStage audience(Supplier<List<Player>> audience, Supplier<List<Player>> fighters) {
        this.audience = audience == null ? List::of : audience;
        this.fighters = fighters == null ? List::of : fighters;
        return this;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public World world() {
        return world;
    }

    public DisplayBudget budget() {
        return budget;
    }

    public UUID owner() {
        return owner;
    }

    public Group root() {
        return root;
    }

    /** Everyone who watches (sounds, sky, titles): participants including echoes. */
    public List<Player> audience() {
        return audience.get();
    }

    /** Everyone the stage may hurt right now. */
    public List<Player> fighters() {
        return fighters.get();
    }

    public float timeScale() {
        return timeScale;
    }

    /** 1 = real time, 0.25 = four times slower. Affects interpolation and the score's pitch. */
    public void timeScale(float scale) {
        this.timeScale = HMath.clamp(scale, 0.05f, 1f);
    }

    /* ------------------------------------------------------------------ coordinates */

    public Location at(Vector3fc stage) {
        return new Location(world, anchor.getX() + stage.x(), anchor.getY() + stage.y(), anchor.getZ() + stage.z());
    }

    public Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    public Vector3f stage(Location loc) {
        return new Vector3f(
                (float) (loc.getX() - anchor.getX()),
                (float) (loc.getY() - anchor.getY()),
                (float) (loc.getZ() - anchor.getZ()));
    }

    public Vector3f feet(Player p) {
        return stage(p.getLocation());
    }

    /* ------------------------------------------------------------------ groups */

    public Group group() {
        Group g = new Group();
        groups.add(g);
        return g;
    }

    public void clear() {
        for (Group g : new ArrayList<>(groups)) {
            g.clear();
        }
        groups.clear();
        groups.add(root);
    }

    public int count() {
        int n = 0;
        for (Group g : groups) {
            n += g.size();
        }
        return n;
    }

    /** Drop references to displays that are already gone (chunk unload, external removal). */
    public void purge() {
        groups.removeIf(g -> g != root && g.closed);
        int live = 0;
        for (Group g : groups) {
            g.purge();
            live += g.size();
        }
        budget.resync(live);
    }

    /** A set of displays that live and die together (one attack, one prop, one body). */
    public final class Group {

        private final List<Display> list = new ArrayList<>();
        private boolean closed;

        public BlockDisplay block(Material material, Color glow, int brightness, boolean force) {
            return block(material.createBlockData(), glow, brightness, force);
        }

        public BlockDisplay block(Material material, int brightness) {
            return block(material.createBlockData(), null, brightness, true);
        }

        public BlockDisplay block(BlockData data, Color glow, int brightness, boolean force) {
            if (world == null || closed || (!force && !budget.allowSpawn())) {
                return null;
            }
            BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
                d.setBlock(data);
                prepare(d, glow, brightness);
            });
            track(display);
            return display;
        }

        public ItemDisplay item(ItemStack stack, int brightness, boolean force) {
            if (world == null || closed || (!force && !budget.allowSpawn())) {
                return null;
            }
            ItemDisplay display = world.spawn(anchor, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                prepare(d, null, brightness);
            });
            track(display);
            return display;
        }

        /** A billboarded label that sits at a stage point (not at the anchor: text must be culled by distance). */
        public TextDisplay text(Vector3fc at, String text, float scale, Color background) {
            if (world == null || closed) {
                return null;
            }
            TextDisplay display = world.spawn(at(at), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setLineWidth(260);
                d.setBackgroundColor(background == null ? Color.fromARGB(0, 0, 0, 0) : background);
                d.setViewRange(VIEW_RANGE);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
                d.text(TextUtil.component(text));
                if (keys != null) {
                    keys.tagBeamFx(d, owner);
                }
            });
            track(display);
            return display;
        }

        private void track(Display display) {
            list.add(display);
            budget.spawned();
        }

        public void kill(Display d) {
            if (d == null) {
                return;
            }
            if (list.remove(d)) {
                budget.removed();
            }
            if (d.isValid()) {
                d.remove();
            }
        }

        public void clear() {
            for (Display d : list) {
                if (d != null && d.isValid()) {
                    d.remove();
                }
                budget.removed();
            }
            list.clear();
            closed = true;
        }

        /** Clears the displays but keeps the group usable. */
        public void reset() {
            for (Display d : list) {
                if (d != null && d.isValid()) {
                    d.remove();
                }
                budget.removed();
            }
            list.clear();
        }

        void purge() {
            for (Iterator<Display> it = list.iterator(); it.hasNext(); ) {
                Display d = it.next();
                if (d == null || !d.isValid()) {
                    it.remove();
                }
            }
        }

        public int size() {
            return list.size();
        }

        public List<Display> displays() {
            return list;
        }
    }

    private void prepare(Display d, Color glow, int brightness) {
        d.setPersistent(false);
        d.setGravity(false);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(VIEW_RANGE);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(0);
        if (brightness >= 0) {
            int b = Math.max(0, Math.min(15, brightness));
            d.setBrightness(new Display.Brightness(b, b));
        }
        d.setTransformation(gone(new Vector3f()));
        if (glow != null) {
            d.setGlowing(true);
            d.setGlowColorOverride(glow);
        }
        if (keys != null) {
            keys.tagBeamFx(d, owner);
        }
    }

    /* ------------------------------------------------------------------ pushing */

    /** Pushes a pose; the interpolation is stretched by the stage's slow motion. */
    public void push(Display d, Transformation t, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        int scaled = timeScale >= 0.999f ? interp : Math.round(interp / timeScale);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, Math.min(200, scaled)));
        d.setTransformation(t);
        budget.pushed();
    }

    public static void glow(Display d, Color color) {
        if (d == null || !d.isValid()) {
            return;
        }
        if (color == null) {
            d.setGlowing(false);
            return;
        }
        d.setGlowColorOverride(color);
        d.setGlowing(true);
    }

    public static void brightness(Display d, int level) {
        if (d != null && d.isValid()) {
            int b = Math.max(0, Math.min(15, level));
            d.setBrightness(new Display.Brightness(b, b));
        }
    }

    public static void material(BlockDisplay d, Material material) {
        if (d != null && d.isValid() && d.getBlock().getMaterial() != material) {
            d.setBlock(material.createBlockData());
        }
    }

    /* ------------------------------------------------------------------ transforms */

    /** A box of {@code size} centered on {@code center}, rotated by {@code rot} about its center. */
    public static Transformation box(Vector3fc center, Vector3fc size, Quaternionf rot) {
        Vector3f corner = new Vector3f(size).mul(-0.5f);
        rot.transform(corner);
        return new Transformation(new Vector3f(center).add(corner), new Quaternionf(rot), new Vector3f(size), new Quaternionf());
    }

    public static Transformation box(Vector3fc center, Vector3fc size) {
        return new Transformation(new Vector3f(center).sub(new Vector3f(size).mul(0.5f)),
                new Quaternionf(), new Vector3f(size), new Quaternionf());
    }

    public static Transformation cube(Vector3fc center, float size, Quaternionf rot) {
        return box(center, new Vector3f(size), rot);
    }

    public static Transformation cube(Vector3fc center, float size) {
        return box(center, new Vector3f(size));
    }

    /** A flat slab lying on the XZ plane, yawed about Y. */
    public static Transformation flat(Vector3fc center, float sx, float sy, float sz, float yaw) {
        return box(center, new Vector3f(sx, sy, sz), new Quaternionf().rotateY(yaw));
    }

    /** A thin square rod from a to b. */
    public static Transformation beam(Vector3fc a, Vector3fc b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(new Vector3f(a));
        }
        Quaternionf rot = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / len, dir.y / len, dir.z / len);
        Vector3f corner = rot.transform(new Vector3f(-width * 0.5f, 0f, -width * 0.5f));
        return new Transformation(new Vector3f(a).add(corner), rot, new Vector3f(width, len, width), new Quaternionf());
    }

    /** A rod from a to b with a flat cross-section (width x depth), depth rolled toward {@code up}. */
    public static Transformation plank(Vector3fc a, Vector3fc b, float width, float depth, Vector3fc up) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-4f) {
            return gone(new Vector3f(a));
        }
        Quaternionf rot = HMath.frame(dir, up);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        return box(mid, new Vector3f(width, depth, len), rot);
    }

    /** Collapsed to nothing at a point (hidden but alive, ready to grow from there). */
    public static Transformation gone(Vector3fc at) {
        return new Transformation(new Vector3f(at), new Quaternionf(), new Vector3f(0.001f), new Quaternionf());
    }

    /* ------------------------------------------------------------------ particles (garnish only) */

    public void particle(Particle particle, Vector3fc at, int count, double spread, double speed) {
        if (world != null) {
            world.spawnParticle(particle, at(at), count, spread, spread, spread, speed, null, true);
        }
    }

    public void dust(Vector3fc at, Color color, float size, int count, double spread) {
        if (world != null) {
            world.spawnParticle(Particle.DUST, at(at), count, spread, spread, spread, 0,
                    new Particle.DustOptions(color, size), true);
        }
    }

    public void blockDust(Vector3fc at, Material material, int count, double spread) {
        if (world != null) {
            world.spawnParticle(Particle.BLOCK, at(at), count, spread, spread * 0.5, spread, 0,
                    material.createBlockData(), true);
        }
    }
}
