package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static de.aetherion.bossengine.instance.worldeater.WeMath.PI;

/**
 * NIHIL'S BODY. A serpent whose head is the void and whose body is every world it ever ate.
 *
 * <p>The head is a sculk skull: dark, speckled like a night sky, with a hinged jaw, fangs of
 * pointed dripstone, amethyst horns, crying-obsidian gills and three pairs of ender eyes. A fourth
 * eye on the brow opens only when it wants you to know it is looking.
 *
 * <p>Behind a short neck of void, each vertebra is a chunk-slab of a different world, cut the way
 * the game cuts chunks: a biome's surface block over its strata, with that world's plant or tree
 * on top. Plains, desert, snow, mushroom fields, badlands, crimson and warped forests, basalt, the
 * End, ocean, jungle, cherry grove, the deep dark, swamp, taiga, ice spikes. You can read what it
 * ate by looking at it, and you can watch it lose them.
 *
 * <p>The body follows the head along a {@link SpineTrail}. In the final phase it can also be laid
 * along a rounded square (the edge of the world) and swallow itself from the tail.
 */
final class Serpent {

    static final float NECK_GAP = 3.8f;
    static final float GAP = 0.24f;

    /** One eaten world: what its slab is made of and what grows on it. */
    record World(String name, Material top, Material under, Material feature,
                 float fw, float fh, boolean snowy, Color tint) {
    }

    static final World VOID_NECK = new World("void", Material.SCULK, Material.OBSIDIAN, Material.AMETHYST_CLUSTER,
            0.42f, 0.75f, false, Color.fromRGB(150, 60, 255));

    static final World[] WORLDS = {
            new World("plains", Material.GRASS_BLOCK, Material.DIRT, Material.OAK_LEAVES, 0.55f, 0.5f, false, Color.fromRGB(110, 200, 80)),
            new World("desert", Material.SAND, Material.SANDSTONE, Material.CACTUS, 0.24f, 0.6f, false, Color.fromRGB(230, 210, 140)),
            new World("snowy plains", Material.GRASS_BLOCK, Material.DIRT, Material.SPRUCE_LEAVES, 0.45f, 0.6f, true, Color.fromRGB(240, 250, 255)),
            new World("mushroom fields", Material.MYCELIUM, Material.DIRT, Material.RED_MUSHROOM_BLOCK, 0.55f, 0.28f, false, Color.fromRGB(200, 60, 60)),
            new World("badlands", Material.RED_SAND, Material.ORANGE_TERRACOTTA, Material.DEAD_BUSH, 0.55f, 0.55f, false, Color.fromRGB(210, 110, 40)),
            new World("crimson forest", Material.CRIMSON_NYLIUM, Material.NETHERRACK, Material.CRIMSON_FUNGUS, 0.6f, 0.6f, false, Color.fromRGB(200, 30, 40)),
            new World("warped forest", Material.WARPED_NYLIUM, Material.NETHERRACK, Material.WARPED_WART_BLOCK, 0.36f, 0.36f, false, Color.fromRGB(30, 180, 170)),
            new World("basalt deltas", Material.BASALT, Material.BLACKSTONE, Material.MAGMA_BLOCK, 0.3f, 0.3f, false, Color.fromRGB(255, 120, 30)),
            new World("the end", Material.END_STONE, Material.END_STONE_BRICKS, Material.CHORUS_FLOWER, 0.4f, 0.4f, false, Color.fromRGB(230, 220, 160)),
            new World("ocean", Material.PRISMARINE, Material.DARK_PRISMARINE, Material.BRAIN_CORAL, 0.55f, 0.55f, false, Color.fromRGB(60, 170, 190)),
            new World("jungle", Material.MOSS_BLOCK, Material.DIRT, Material.JUNGLE_LEAVES, 0.55f, 0.55f, false, Color.fromRGB(60, 160, 40)),
            new World("cherry grove", Material.GRASS_BLOCK, Material.DIRT, Material.CHERRY_LEAVES, 0.6f, 0.5f, false, Color.fromRGB(255, 170, 210)),
            new World("deep dark", Material.SCULK, Material.DEEPSLATE, Material.SCULK_SHRIEKER, 0.45f, 0.45f, false, Color.fromRGB(20, 90, 110)),
            new World("swamp", Material.MUD, Material.PACKED_MUD, Material.LILY_PAD, 0.62f, 0.06f, false, Color.fromRGB(80, 110, 60)),
            new World("taiga", Material.PODZOL, Material.COARSE_DIRT, Material.SWEET_BERRY_BUSH, 0.5f, 0.5f, false, Color.fromRGB(120, 90, 50)),
            new World("ice spikes", Material.SNOW_BLOCK, Material.PACKED_ICE, Material.BLUE_ICE, 0.26f, 0.75f, false, Color.fromRGB(150, 200, 255)),
    };

    private static final int NECK_SEGMENTS = 3;

    final class Vertebra {
        final World world;
        final float size;
        BlockDisplay top;
        BlockDisplay under;
        BlockDisplay feature;
        float offset;
        float targetOffset;
        boolean hidden;
        boolean rising;
        float riseScale = 1f;
        /** Ring mode: stretches the slab to fill its slot on the edge of the world. */
        float ringMul = 1f;
        final Vector3f center = new Vector3f();
        final Quaternionf rot = new Quaternionf();
        Transformation sentTop;
        Transformation sentUnder;
        Transformation sentFeature;

        Vertebra(World world, float size) {
            this.world = world;
            this.size = size;
        }

        float length() {
            return size * 0.92f;
        }

        BlockData topData() {
            BlockData topData = world.top().createBlockData();
            if (world.snowy() && topData instanceof Snowable s) {
                s.setSnowy(true);
            }
            return topData;
        }

        void spawn() {
            top = fx.block(topData(), null, bright);
            under = fx.block(world.under(), null, bright);
            if (world.feature() != null) {
                feature = fx.block(world.feature(), null, bright);
            }
            sentTop = null;
            sentUnder = null;
            sentFeature = null;
        }

        boolean intact() {
            return top != null && top.isValid() && under != null && under.isValid()
                    && (world.feature() == null || feature != null && feature.isValid());
        }

        void remove() {
            fx.kill(top);
            fx.kill(under);
            fx.kill(feature);
            top = null;
            under = null;
            feature = null;
        }

        void render(int interp) {
            float s = size * riseScale * ringMul;
            float h = s * 0.8f;
            float l = length() * riseScale * ringMul;
            if (hidden) {
                Transformation gone = WeFx.gone(center);
                sentTop = push(top, gone, sentTop, interp);
                sentUnder = push(under, gone, sentUnder, interp);
                sentFeature = push(feature, gone, sentFeature, interp);
                return;
            }
            Vector3f topC = rot.transform(new Vector3f(0f, h * 0.29f, 0f)).add(center);
            Vector3f underC = rot.transform(new Vector3f(0f, -h * 0.21f, 0f)).add(center);
            sentTop = push(top, WeFx.box(topC, new Vector3f(s, h * 0.42f, l), rot), sentTop, interp);
            sentUnder = push(under, WeFx.box(underC, new Vector3f(s * 0.92f, h * 0.58f, l * 0.92f), rot), sentUnder, interp);
            if (feature != null) {
                float fw = s * world.fw();
                float fh = s * world.fh();
                Vector3f fc = rot.transform(new Vector3f(0f, h * 0.5f + fh * 0.5f - 0.04f, 0f)).add(center);
                sentFeature = push(feature, WeFx.box(fc, new Vector3f(fw, fh, fw), rot), sentFeature, interp);
            }
        }
    }

    enum Mode { TRAIL, RING }

    final WeFx fx;
    final WeRig head = new WeRig();
    final WeRig.Bone neck;
    final WeRig.Bone skull;
    final WeRig.Bone jaw;
    final WeRig.Bone crest;
    final List<Vertebra> body = new ArrayList<>();
    final List<Vertebra> rising = new ArrayList<>();
    final SpineTrail trail = new SpineTrail(6000, 260f);

    final Vector3f pos = new Vector3f();
    final Vector3f fwd = new Vector3f(0f, 0f, 1f);
    final Vector3f up = new Vector3f(0f, 1f, 0f);
    private final Quaternionf lookRot = new Quaternionf();
    private final Quaternionf lookGoal = new Quaternionf();
    private float lookEase = 0.2f;
    private float jawGoal;
    private float jawSnap;
    private int bright = 15;
    private boolean spawned;
    private boolean headHidden;
    private boolean bodyHidden;
    private float speed;
    /** Righting: how strongly "up" is pulled back toward the sky each tick. */
    float righting = 0.05f;

    /* ring (ouroboros) */
    Mode mode = Mode.TRAIL;
    private float ringBlend;
    private float ringBlendGoal;
    /** Ring mode: arc length each visible vertebra fills (0 = lay them at their trail offsets). */
    private float ringSlot;
    private final RingPath ringPath = new RingPath();

    Serpent(WeFx fx, int worlds) {
        this.fx = fx;
        neck = head.bone(null, 0, 0, 0);
        skull = head.bone(neck, 0, 0.3f, 0.9f);
        jaw = head.bone(neck, 0, -0.4f, 0.6f);
        crest = head.bone(skull, 0, 1.6f, -0.9f);
        buildHead();
        for (int i = 0; i < NECK_SEGMENTS; i++) {
            body.add(new Vertebra(VOID_NECK, 3.5f - i * 0.08f));
        }
        for (int i = 0; i < worlds; i++) {
            float t = worlds <= 1 ? 0f : i / (float) (worlds - 1);
            float size = WeMath.lerp(3.2f, 1.05f, (float) Math.pow(t, 1.15));
            body.add(new Vertebra(WORLDS[i % WORLDS.length], size));
        }
        layoutOffsets(true);
    }

    /* ================================================================== the head */

    private void buildHead() {
        Material skin = Material.SCULK;
        BlockData fangDown = fang(BlockFace.DOWN);
        BlockData fangUp = fang(BlockFace.UP);
        Quaternionf none = new Quaternionf();

        // Neck collar.
        head.box(neck, skin, "head", 0, 0.1f, -0.75f, 3.2f, 2.6f, 2.2f);
        head.box(neck, Material.OBSIDIAN, "head", 0, 1.38f, -0.75f, 3.4f, 0.4f, 2.4f);
        head.box(neck, Material.PURPLE_STAINED_GLASS, "fin", -1.8f, 0.5f, -0.9f, 0.08f, 1.9f, 2.6f, new Quaternionf().rotateZ(0.4f));
        head.box(neck, Material.PURPLE_STAINED_GLASS, "fin", 1.8f, 0.5f, -0.9f, 0.08f, 1.9f, 2.6f, new Quaternionf().rotateZ(-0.4f));

        // Skull.
        head.box(skull, skin, "head", 0, 0.75f, 0.4f, 3.4f, 2.0f, 3.2f);
        head.box(skull, Material.OBSIDIAN, "head", 0, 0.7f, -1.3f, 3.0f, 1.6f, 0.4f);
        head.box(skull, skin, "head", 0, 0.35f, 3.3f, 2.5f, 1.2f, 3.0f);
        head.box(skull, Material.BLACK_CONCRETE, "head", 0, 0.3f, 4.95f, 2.2f, 1.0f, 0.5f);
        head.box(skull, Material.CRYING_OBSIDIAN, "glow", -0.6f, 0.75f, 5.0f, 0.35f, 0.2f, 0.3f);
        head.box(skull, Material.CRYING_OBSIDIAN, "glow", 0.6f, 0.75f, 5.0f, 0.35f, 0.2f, 0.3f);
        head.box(skull, Material.OBSIDIAN, "head", -1.35f, 1.8f, 1.0f, 1.1f, 0.45f, 2.8f, new Quaternionf().rotateZ(0.28f));
        head.box(skull, Material.OBSIDIAN, "head", 1.35f, 1.8f, 1.0f, 1.1f, 0.45f, 2.8f, new Quaternionf().rotateZ(-0.28f));
        head.box(skull, Material.CRYING_OBSIDIAN, "glow", -1.72f, 0.55f, 0.9f, 0.2f, 1.1f, 2.2f);
        head.box(skull, Material.CRYING_OBSIDIAN, "glow", 1.72f, 0.55f, 0.9f, 0.2f, 1.1f, 2.2f);
        head.box(skull, Material.CRYING_OBSIDIAN, "mouth", 0, -0.22f, 2.6f, 2.0f, 0.1f, 3.6f);
        // Horns and crest: amethyst, swept back.
        head.box(skull, Material.AMETHYST_CLUSTER, "horn", -1.25f, 2.1f, -0.4f, 1.3f, 2.6f, 1.3f,
                new Quaternionf().rotateZ(0.3f).rotateX(-1.1f));
        head.box(skull, Material.AMETHYST_CLUSTER, "horn", 1.25f, 2.1f, -0.4f, 1.3f, 2.6f, 1.3f,
                new Quaternionf().rotateZ(-0.3f).rotateX(-1.1f));
        head.box(crest, Material.AMETHYST_CLUSTER, "horn", 0, 0.4f, 0f, 0.9f, 1.8f, 0.9f, new Quaternionf().rotateX(-1.2f));
        head.box(crest, Material.AMETHYST_CLUSTER, "horn", 0, 0.0f, -0.9f, 0.7f, 1.4f, 0.7f, new Quaternionf().rotateX(-1.35f));
        // Upper fangs.
        float[] zs = {1.7f, 2.6f, 3.5f, 4.35f};
        for (float z : zs) {
            head.box(skull, fangDown, "fang", -1.0f, -0.72f, z, 0.45f, 1.0f, 0.45f, none);
            head.box(skull, fangDown, "fang", 1.0f, -0.72f, z, 0.45f, 1.0f, 0.45f, none);
        }
        head.box(skull, fangDown, "fang", -0.72f, -0.95f, 4.7f, 0.6f, 1.45f, 0.6f, none);
        head.box(skull, fangDown, "fang", 0.72f, -0.95f, 4.7f, 0.6f, 1.45f, 0.6f, none);
        // Ender eyes, three per side, and the brow eye that is closed until it stares.
        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        float[][] eyes = {{1.73f, 1.2f, 2.0f, 0.78f}, {1.7f, 1.32f, 1.1f, 0.62f}, {1.62f, 1.4f, 0.25f, 0.5f}};
        for (float[] e : eyes) {
            head.item(skull, eye, "eye", -e[0], e[1], e[2], e[3], new Quaternionf().rotateY(-PI / 2f));
            head.item(skull, eye, "eye", e[0], e[1], e[2], e[3], new Quaternionf().rotateY(PI / 2f));
        }
        head.item(skull, eye, "browEye", 0f, 1.72f, 1.95f, 1.1f, new Quaternionf().rotateX(-0.35f));

        // Jaw.
        head.box(jaw, skin, "head", 0, -0.35f, 2.3f, 2.6f, 0.8f, 4.6f);
        head.box(jaw, Material.BLACK_CONCRETE, "head", 0, -0.35f, 4.75f, 2.1f, 0.75f, 0.5f);
        head.box(jaw, Material.OBSIDIAN, "head", -1.4f, -0.1f, 0.1f, 0.5f, 0.9f, 0.9f);
        head.box(jaw, Material.OBSIDIAN, "head", 1.4f, -0.1f, 0.1f, 0.5f, 0.9f, 0.9f);
        head.box(jaw, Material.CRYING_OBSIDIAN, "mouth", 0, 0.08f, 2.4f, 1.9f, 0.1f, 3.8f);
        float[] lz = {1.3f, 2.2f, 3.1f, 3.95f};
        for (float z : lz) {
            head.box(jaw, fangUp, "fang", -0.95f, 0.45f, z, 0.42f, 0.9f, 0.42f, none);
            head.box(jaw, fangUp, "fang", 0.95f, 0.45f, z, 0.42f, 0.9f, 0.42f, none);
        }
        head.box(jaw, fangUp, "fang", -0.7f, 0.6f, 4.35f, 0.55f, 1.2f, 0.55f, none);
        head.box(jaw, fangUp, "fang", 0.7f, 0.6f, 4.35f, 0.55f, 1.2f, 0.55f, none);

        head.setGroupHidden("browEye", true);
        for (WeRig.Piece p : head.pieces()) {
            p.brightness = 15;
        }
        for (WeRig.Piece p : head.group("eye")) {
            p.glow = WeProps.VOID;
        }
        springs();
    }

    private static BlockData fang(BlockFace direction) {
        BlockData d = Material.POINTED_DRIPSTONE.createBlockData();
        if (d instanceof PointedDripstone pd) {
            pd.setVerticalDirection(direction);
            pd.setThickness(PointedDripstone.Thickness.TIP);
        }
        return d;
    }

    void springs() {
        for (WeRig.Bone b : head.bones()) {
            b.spring(0.22f, 0.3f);
        }
        jaw.spring(0.3f, 0.34f);
        crest.spring(0.08f, 0.14f);
    }

    /* ================================================================== lifecycle */

    /** Spawns every display; the body is laid behind the head along {@code -forward}. */
    void spawn(Vector3f at, Vector3f forward, boolean laidOut) {
        pos.set(at);
        fwd.set(forward).normalize();
        up.set(0f, 1f, 0f);
        if (laidOut) {
            trail.seedStraight(pos, fwd, up, totalLength() + 6f, 0.6f);
        } else {
            trail.clear();
            trail.push(pos, up);
        }
        head.rootPos.set(pos);
        head.tilt.set(WeMath.frame(fwd, up));
        head.snapAll();
        head.spawn(fx);
        for (Vertebra v : body) {
            v.spawn();
        }
        spawned = true;
        render(0);
    }

    boolean spawned() {
        return spawned;
    }

    boolean intact() {
        if (!head.intact()) {
            return false;
        }
        for (Vertebra v : body) {
            if (!v.intact()) {
                return false;
            }
        }
        return true;
    }

    /** Respawns every display in place (chunk unload). */
    void repair() {
        head.remove(fx);
        head.spawn(fx);
        for (Vertebra v : body) {
            v.remove();
            v.spawn();
        }
        for (Vertebra v : rising) {
            v.remove();
            v.spawn();
        }
    }

    void remove() {
        head.remove(fx);
        for (Vertebra v : body) {
            v.remove();
        }
        for (Vertebra v : rising) {
            v.remove();
        }
        rising.clear();
        spawned = false;
    }

    /* ================================================================== motion */

    /**
     * Moves the head pivot to {@code to}. Forward follows the motion, "up" is carried along by
     * parallel transport and gently righted toward the sky.
     */
    void moveTo(Vector3f to) {
        Vector3f d = new Vector3f(to).sub(pos);
        speed = d.length();
        if (speed > 1e-4f) {
            Vector3f nf = new Vector3f(d).normalize();
            Quaternionf turn = new Quaternionf().rotationTo(fwd, nf);
            turn.transform(up);
            fwd.set(nf);
            orthoUp();
        }
        pos.set(to);
        trail.push(pos, up);
    }

    /** Turns in place (no travel): the body does not move. */
    void faceTo(Vector3f dir, float maxStep) {
        Vector3f want = new Vector3f(dir);
        if (want.lengthSquared() < 1e-6f) {
            return;
        }
        want.normalize();
        float angle = (float) Math.acos(WeMath.clamp(fwd.dot(want), -1f, 1f));
        if (angle < 1e-4f) {
            return;
        }
        float t = Math.min(1f, maxStep / angle);
        Quaternionf full = new Quaternionf().rotationTo(fwd, want);
        Quaternionf part = new Quaternionf().slerp(full, t);
        part.transform(fwd);
        part.transform(up);
        fwd.normalize();
        orthoUp();
    }

    private void orthoUp() {
        up.fma(-up.dot(fwd), fwd);
        if (up.lengthSquared() < 1e-6f) {
            up.set(0f, 1f, 0f).fma(-fwd.y, fwd);
            if (up.lengthSquared() < 1e-6f) {
                up.set(1f, 0f, 0f);
            }
        }
        up.normalize();
        if (righting > 0f) {
            Vector3f sky = new Vector3f(0f, 1f, 0f).fma(-fwd.y, fwd);
            if (sky.lengthSquared() > 0.02f) {
                sky.normalize();
                up.lerp(sky, righting).fma(-up.dot(fwd), fwd).normalize();
            }
        }
    }

    /** Slides back through its own history ({@code distance} blocks): the uncanny rewind. */
    void rewind(float distance) {
        float at = trail.newestS() - distance;
        Vector3f p = trail.retract(Math.max(trail.oldestS() + 1f, at));
        Vector3f u = new Vector3f();
        Vector3f tan = new Vector3f();
        trail.sample(trail.newestS(), new Vector3f(), u, tan);
        pos.set(p);
        fwd.set(tan);
        up.set(u);
        orthoUp();
    }

    float speed() {
        return speed;
    }

    /** Lets the head look toward a stage point (weight 0..1 of the way from the body line). */
    void look(Vector3f target, float weight, float ease) {
        Vector3f dir = new Vector3f(target).sub(pos);
        if (dir.lengthSquared() < 1e-4f || weight <= 0f) {
            lookGoal.identity();
        } else {
            Quaternionf body = WeMath.frame(fwd, up);
            Quaternionf want = WeMath.frame(dir, new Vector3f(0f, 1f, 0f));
            Quaternionf rel = new Quaternionf(body).conjugate().mul(want);
            lookGoal.identity().slerp(rel, WeMath.clamp01(weight));
        }
        lookEase = ease;
    }

    void lookForward() {
        lookGoal.identity();
    }

    /** Jaw opening 0 (closed) .. 1 (unhinged). */
    void jaw(float open) {
        jawGoal = WeMath.clamp(open, 0f, 1.25f);
    }

    /** A hard snap shut (bite): the jaw slams, the skull jolts. */
    void snap() {
        jawGoal = 0f;
        jawSnap = 1f;
        jaw.kick(-0.5f, 0, 0);
        skull.kick(0.18f, 0, 0);
    }

    void brow(boolean open) {
        head.setGroupHidden("browEye", !open);
    }

    void eyes(Color glow) {
        head.setGroupGlow("eye", glow);
        head.setGroupGlow("browEye", glow);
    }

    void hideHead(boolean hide) {
        headHidden = hide;
        for (WeRig.Piece p : head.pieces()) {
            p.hidden = hide || "browEye".equals(p.group) && p.hidden;
        }
        if (!hide) {
            head.setGroupHidden("browEye", true);
        }
    }

    void hideBody(boolean hide) {
        bodyHidden = hide;
    }

    void brightness(int level) {
        bright = Math.max(0, Math.min(15, level));
        for (WeRig.Piece p : head.pieces()) {
            if (!"eye".equals(p.group) && !"browEye".equals(p.group) && !"glow".equals(p.group)) {
                head.setBrightness(p, bright);
            }
        }
        for (Vertebra v : body) {
            WeFx.brightness(v.top, bright);
            WeFx.brightness(v.under, bright);
            WeFx.brightness(v.feature, bright);
        }
    }

    /* ================================================================== body layout */

    float totalLength() {
        float len = NECK_GAP;
        for (int i = 0; i < body.size(); i++) {
            len += (i == 0 ? body.get(i).length() * 0.5f : 0.5f * (body.get(i - 1).length() + body.get(i).length()) + GAP);
        }
        return len;
    }

    /** Arc offsets behind the head for the current chain. */
    private void layoutOffsets(boolean snap) {
        float at = NECK_GAP;
        for (int i = 0; i < body.size(); i++) {
            Vertebra v = body.get(i);
            if (i > 0) {
                at += 0.5f * (body.get(i - 1).length() + v.length()) + GAP;
            }
            v.targetOffset = at;
            if (snap) {
                v.offset = at;
            }
        }
    }

    int vertebrae() {
        return body.size();
    }

    int worldsLeft() {
        int n = 0;
        for (Vertebra v : body) {
            if (v.world != VOID_NECK) {
                n++;
            }
        }
        return n;
    }

    List<World> worlds() {
        List<World> out = new ArrayList<>();
        for (Vertebra v : body) {
            if (v.world != VOID_NECK) {
                out.add(v.world);
            }
        }
        return out;
    }

    Vector3f vertebraCenter(int i) {
        return new Vector3f(body.get(i).center);
    }

    float vertebraSize(int i) {
        return body.get(i).size;
    }

    boolean vertebraShown(int i) {
        return !body.get(i).hidden;
    }

    /**
     * Starts moving one eaten world up the body into the throat (a bulge that travels to the
     * mouth); the gap behind it closes. {@link #tickRising()} reports when it reaches the throat.
     *
     * @return the world being brought up, or null if there is none to give
     */
    World bringUp(int index) {
        if (index < NECK_SEGMENTS || index >= body.size()) {
            return null;
        }
        Vertebra v = body.remove(index);
        v.rising = true;
        rising.add(v);
        layoutOffsets(false);
        return v.world;
    }

    /** Index of the world vertebra closest to the neck. */
    int firstWorld() {
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i).world != VOID_NECK) {
                return i;
            }
        }
        return -1;
    }

    /** @return worlds that just reached the mouth this tick (the caller spits them) */
    List<World> tickRising() {
        List<World> out = new ArrayList<>();
        for (int i = rising.size() - 1; i >= 0; i--) {
            Vertebra v = rising.get(i);
            v.offset = Math.max(0.6f, v.offset - 1.4f);
            v.riseScale = Math.min(1.35f, v.riseScale + 0.03f);
            if (v.offset <= 0.61f) {
                v.remove();
                rising.remove(i);
                out.add(v.world);
            }
        }
        return out;
    }

    /** Swallow from the tail: the last world vanishes into the mouth. @return false when none left */
    boolean swallowTail() {
        for (int i = body.size() - 1; i >= NECK_SEGMENTS; i--) {
            Vertebra v = body.get(i);
            if (!v.hidden) {
                v.hidden = true;
                return true;
            }
        }
        return false;
    }

    /** Spit the tail back out (the ring grows). @return false when nothing is swallowed */
    boolean regurgitateTail() {
        for (int i = NECK_SEGMENTS; i < body.size(); i++) {
            Vertebra v = body.get(i);
            if (v.hidden) {
                v.hidden = false;
                return true;
            }
        }
        return false;
    }

    /** Remove the last {@code n} vertebrae entirely (they are gone). */
    void dropTail(int n) {
        for (int k = 0; k < n && body.size() > NECK_SEGMENTS; k++) {
            Vertebra v = body.remove(body.size() - 1);
            v.remove();
        }
        layoutOffsets(false);
    }

    /**
     * A world it just ate joins the body right behind the neck, starting in the throat and
     * sliding down into place while the rest of the body makes room.
     */
    void grow(World w, float size) {
        Vertebra v = new Vertebra(w, size);
        int at = Math.min(NECK_SEGMENTS, body.size());
        v.offset = at > 0 ? body.get(at - 1).offset : NECK_GAP;
        v.riseScale = 0.4f;
        body.add(at, v);
        if (spawned) {
            v.spawn();
        }
        layoutOffsets(false);
    }

    /** Grown vertebrae swell to full size over a second (call every tick). */
    private void settleGrowth() {
        for (Vertebra v : body) {
            if (!v.rising && v.riseScale < 1f) {
                v.riseScale = Math.min(1f, v.riseScale + 0.03f);
            }
        }
    }

    /** Head size (1 = normal). Used when it eats itself. */
    void headScale(float s) {
        head.scale = Math.max(0.001f, s);
    }

    /** Repaints one vertebra's surface block ({@code null} puts back its own world). */
    void paintTop(int i, Material m) {
        if (i < 0 || i >= body.size()) {
            return;
        }
        Vertebra v = body.get(i);
        if (v.top == null || !v.top.isValid()) {
            return;
        }
        v.top.setBlock(m == null ? v.topData() : m.createBlockData());
    }

    /* ================================================================== ring (ouroboros) */

    /**
     * Lays the body along a rounded square of side {@code side} around {@code center} (the edge of
     * the world), head at corner {@code corner}, resting on {@code floor}. The change eases in.
     */
    void ring(Vector3f center, float side, float floor, int corner, float blendGoal) {
        mode = Mode.RING;
        ringPath.set(center.x, center.z, side, floor, corner);
        ringBlendGoal = WeMath.clamp01(blendGoal);
        ringSlot = 0f;
    }

    /**
     * Lays the whole visible body around the edge of the world, head at {@code corner}, each
     * vertebra stretched to an equal slot so the tail ends exactly in the mouth. From then on the
     * ring's size follows the body: see {@link #ringFit()}.
     */
    void ringStart(Vector3f center, float side, float floor, int corner) {
        mode = Mode.RING;
        ringPath.set(center.x, center.z, side, floor, corner);
        int n = Math.max(1, visibleCount());
        ringSlot = Math.max(1.2f, (ringPath.perimeter() - NECK_GAP) / n);
        ringBlendGoal = 1f;
    }

    /** Resizes the ring so its perimeter holds exactly the visible body. @return the new side */
    float ringFit() {
        if (ringSlot <= 0f) {
            return ringPath.side;
        }
        float per = NECK_GAP + visibleCount() * ringSlot;
        float r = 2.4f;
        ringPath.side = Math.max(13.4f, (per - 2f * PI * r) / 4f + 2f * r);
        return ringPath.side;
    }

    float ringSlot() {
        return ringSlot;
    }

    Vector3f ringCenter() {
        return new Vector3f(ringPath.cx, ringPath.floor, ringPath.cz);
    }

    int visibleCount() {
        int n = 0;
        for (Vertebra v : body) {
            if (!v.hidden) {
                n++;
            }
        }
        return n;
    }

    void ringSide(float side) {
        ringPath.side = side;
    }

    float ringSide() {
        return ringPath.side;
    }

    void trailMode() {
        ringBlendGoal = 0f;
    }

    float ringBlend() {
        return ringBlend;
    }

    float ringPerimeter() {
        return ringPath.perimeter();
    }

    /** Where the head rests in ring mode, and the way it faces (toward its own tail). */
    void ringHead(Vector3f outPos, Vector3f outFwd) {
        ringPath.at(0f, outPos, outFwd);
    }

    /* ================================================================== render */

    void render(int interp) {
        if (!spawned) {
            return;
        }
        ringBlend = WeMath.approach(ringBlend, ringBlendGoal, 0.05f);
        if (ringBlend <= 0f && ringBlendGoal <= 0f) {
            mode = Mode.TRAIL;
        }
        // Head.
        lookRot.slerp(lookGoal, lookEase);
        Quaternionf frame = WeMath.frame(fwd, up).mul(lookRot);
        head.rootPos.set(pos);
        head.yaw = 0f;
        head.tilt.set(frame);
        float open = jawGoal;
        jaw.target.x = open * 0.9f;
        skull.target.x = -open * 0.22f;
        if (jawSnap > 0f) {
            jawSnap *= 0.7f;
        }
        crest.target.x = -Math.min(0.6f, speed * 0.35f);
        settleGrowth();
        head.step(1f);
        head.render();

        // Body.
        float headS = trail.newestS();
        Vector3f p = new Vector3f();
        Vector3f u = new Vector3f();
        Vector3f tan = new Vector3f();
        Vector3f rp = new Vector3f();
        Vector3f rf = new Vector3f();
        int slot = 0;
        for (Vertebra v : body) {
            v.offset += (v.targetOffset - v.offset) * 0.18f;
            boolean ok = trail.sample(headS - v.offset, p, u, tan);
            v.ringMul = 1f;
            if (ringBlend > 0f) {
                float ru = ringSlot > 0f ? NECK_GAP + (slot + 0.5f) * ringSlot : v.offset;
                ringPath.at(ru, rp, rf);
                if (ringSlot > 0f) {
                    v.ringMul = WeMath.lerp(1f, ringSlot * 0.97f / Math.max(0.1f, v.length()), ringBlend);
                }
                rp.y += v.size * v.ringMul * 0.4f;
                if (!ok) {
                    p.set(rp);
                    tan.set(rf);
                    u.set(0f, 1f, 0f);
                    ok = true;
                }
                WeMath.lerp(p, rp, ringBlend, p);
                tan.lerp(rf, ringBlend);
                u.lerp(new Vector3f(0f, 1f, 0f), ringBlend);
                if (ringBlend >= 1f && ru > ringPerimeter() + 0.5f) {
                    ok = false;
                }
            }
            if (!v.hidden) {
                slot++;
            }
            boolean show = ok && !bodyHidden;
            v.center.set(p);
            v.rot.set(WeMath.frame(tan, u));
            boolean wasHidden = v.hidden;
            if (!show) {
                v.hidden = true;
            }
            v.render(interp);
            if (!show) {
                v.hidden = wasHidden;
            }
        }
        for (Vertebra v : rising) {
            trail.sample(headS - v.offset, p, u, tan);
            v.center.set(p);
            v.rot.set(WeMath.frame(tan, u));
            v.render(interp);
        }
    }

    private static Transformation push(BlockDisplay d, Transformation t, Transformation sent, int interp) {
        if (d == null || !d.isValid()) {
            return sent;
        }
        if (sent != null && sent.getTranslation().distanceSquared(t.getTranslation()) < 4e-6f
                && sent.getScale().distanceSquared(t.getScale()) < 4e-6f
                && Math.abs(sent.getLeftRotation().dot(t.getLeftRotation())) > 0.999995f) {
            return sent;
        }
        WeFx.push(d, t, interp);
        return t;
    }

    /* ================================================================== points */

    Vector3f skullCenter() {
        return head.point(skull, 0f, 0.6f, 2.0f);
    }

    Vector3f mouth() {
        return head.point(skull, 0f, -0.4f, 3.9f);
    }

    Vector3f throat() {
        return head.point(skull, 0f, -0.2f, 1.2f);
    }

    Vector3f snout() {
        return head.point(skull, 0f, 0.3f, 5.3f);
    }

    Vector3f browPoint() {
        return head.point(skull, 0f, 1.72f, 1.95f);
    }

    /** Does a stage point touch the body (not the head)? */
    boolean bodyTouches(Vector3f point, float pad) {
        for (Vertebra v : body) {
            if (v.hidden) {
                continue;
            }
            float r = v.size * 0.55f + pad;
            if (v.center.distanceSquared(point) < r * r) {
                return true;
            }
        }
        return false;
    }

    /** Nearest visible vertebra center to a point, or null. */
    Vector3f nearestBody(Vector3f point) {
        Vector3f best = null;
        float bestD = Float.MAX_VALUE;
        for (Vertebra v : body) {
            if (v.hidden) {
                continue;
            }
            float d = v.center.distanceSquared(point);
            if (d < bestD) {
                bestD = d;
                best = v.center;
            }
        }
        return best == null ? null : new Vector3f(best);
    }
}
