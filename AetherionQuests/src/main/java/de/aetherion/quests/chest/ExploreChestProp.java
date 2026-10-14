package de.aetherion.quests.chest;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One exploration chest as an authored prop instead of a vanilla block.
 * <p>
 * Built from Display entities around an invisible barrier (the barrier keeps the
 * click / break / explosion contract of {@link ExploreChestService}). Every part is a
 * unit cube placed with a matrix ({@code setTransformationMatrix}); the lid and
 * everything on it hang from one hinge at the back edge, so opening is a single
 * rotation. Each rarity has its own silhouette and its own idle:
 * <ul>
 *   <li><b>Rare</b> — harbour strongbox: spruce, verdigris copper bands, iron hasp.
 *       Breathes: the lid lifts a finger's width now and then.</li>
 *   <li><b>Epic</b> — warded coffer: dark oak, amethyst bands, a lit gem on the hasp,
 *       three shards orbiting slowly.</li>
 *   <li><b>Legendary</b> — gilded reliquary: blackstone on gold feet, gold corner posts,
 *       a small gold halo turning above the lid.</li>
 *   <li><b>Mythic</b> — veil casket: crying obsidian floating over a glowing plinth,
 *       bobbing, lid never quite shut, two motes circling.</li>
 * </ul>
 * All parts are non-persistent (a chunk unload drops them; the service rebuilds on load)
 * and tagged {@link #TAG} so a stray set can always be swept.
 */
final class ExploreChestProp {

    static final String TAG = "aether_explore_prop";

    /* Body proportions — wider than deep, like a real chest. */
    private static final float W = 0.86f;
    private static final float D = 0.70f;
    private static final float BH = 0.50f;
    private static final float LH = 0.22f;
    private static final float BAND = 0.24f;

    private static final float OPEN = (float) Math.toRadians(104.0);
    /** Lid tween step — small enough that the client's straight-line interpolation still reads as a hinge. */
    private static final float LID_STEP = (float) Math.toRadians(30.0);
    private static final int LID_STEP_TICKS = 2;

    private enum Group {
        /** Never moves (Mythic plinth). */
        STATIC,
        /** Chest body — follows the Mythic hover. */
        BODY,
        /** Hangs from the hinge. */
        LID
    }

    private static final class Part {
        private final Display display;
        private final Group group;
        private final Matrix4f local;

        private Part(Display display, Group group, Matrix4f local) {
            this.display = display;
            this.group = group;
            this.local = local;
        }
    }

    private final AetherionQuests plugin;
    private final ExploreChestKind kind;
    private final Location origin;
    private final float yaw;
    private final float base;
    private final List<Part> parts = new ArrayList<>();
    private final List<ItemDisplay> orbiters = new ArrayList<>();
    private final List<BlockDisplay> halo = new ArrayList<>();

    private float lid;
    private float restLid;
    private float hover;
    private boolean opened;
    private long age;
    private long nextBreath;
    private long breathUntil = -1L;
    private BukkitTask lidTask;

    ExploreChestProp(AetherionQuests plugin, ExploreChestKind kind, Location blockCorner, float yaw) {
        this.plugin = plugin;
        this.kind = kind;
        this.origin = blockCorner.clone().add(0.5, 0.0, 0.5);
        this.origin.setYaw(0f);
        this.origin.setPitch(0f);
        this.yaw = yaw;
        this.base = switch (kind) {
            case LEGENDARY -> 0.06f;
            case MYTHIC -> 0.2f;
            default -> 0.0f;
        };
        this.restLid = kind == ExploreChestKind.MYTHIC ? (float) Math.toRadians(7.0) : 0f;
        this.lid = restLid;
        this.nextBreath = ThreadLocalRandom.current().nextLong(60L, 200L);
    }

    /* =========================================================
     * BUILD
     * ========================================================= */

    void build() {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Palette p = Palette.of(kind);

        if (kind == ExploreChestKind.MYTHIC) {
            block(world, p.trim, Group.STATIC, new Matrix4f().translate(-0.49f, 0.0f, -0.49f).scale(0.98f, 0.06f, 0.98f), -1);
            block(world, p.glow, Group.STATIC, new Matrix4f().translate(-0.26f, 0.001f, -0.26f).scale(0.52f, 0.062f, 0.52f), 15);
        }
        if (kind == ExploreChestKind.LEGENDARY) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float cx = sx * (W / 2f - 0.07f);
                    float cz = sz * (D / 2f - 0.07f);
                    block(world, p.band, Group.BODY, new Matrix4f().translate(cx - 0.065f, 0.0f, cz - 0.065f)
                            .scale(0.13f, base, 0.13f), -1);
                }
            }
        }

        // Body, its front bands, and the "inside" light (hidden under the closed lid).
        block(world, p.body, Group.BODY, new Matrix4f().translate(-W / 2f, base, -D / 2f).scale(W, BH, D), -1);
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side * BAND;
            block(world, p.band, Group.BODY, new Matrix4f().translate(x0 - 0.045f, base, D / 2f).scale(0.09f, BH, 0.014f), -1);
        }
        block(world, p.glow, Group.BODY, new Matrix4f().translate(-W / 2f + 0.06f, base + BH + 0.002f, -D / 2f + 0.06f)
                .scale(W - 0.12f, 0.01f, D - 0.12f), 15);

        if (kind == ExploreChestKind.LEGENDARY) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    block(world, p.band, Group.BODY, new Matrix4f()
                            .translate(sx * W / 2f - 0.035f, base, sz * D / 2f - 0.035f)
                            .scale(0.07f, BH + 0.004f, 0.07f), -1);
                }
            }
        }

        // Lid + everything on it — hinge space: origin on the back top edge, +Z toward the front.
        block(world, p.lid, Group.LID, new Matrix4f().translate(-W / 2f - 0.01f, 0.0f, -0.005f).scale(W + 0.02f, LH, D + 0.025f), -1);
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side * BAND;
            block(world, p.band, Group.LID, new Matrix4f().translate(x0 - 0.045f, LH, -0.01f).scale(0.09f, 0.016f, D + 0.04f), -1);
            block(world, p.band, Group.LID, new Matrix4f().translate(x0 - 0.045f, -0.002f, D + 0.02f).scale(0.09f, LH + 0.018f, 0.014f), -1);
        }
        block(world, p.latch, Group.LID, new Matrix4f().translate(-0.06f, -0.15f, D + 0.02f).scale(0.12f, 0.19f, 0.04f), -1);
        if (kind == ExploreChestKind.EPIC || kind == ExploreChestKind.MYTHIC) {
            // A lit gem set into the hasp.
            block(world, p.gem, Group.LID, new Matrix4f()
                    .translate(0.0f, -0.065f, D + 0.062f)
                    .rotateZ((float) Math.toRadians(45.0))
                    .translate(-0.042f, -0.042f, 0.0f)
                    .scale(0.084f, 0.084f, 0.026f), 15);
        }

        if (kind == ExploreChestKind.EPIC) {
            for (int i = 0; i < 3; i++) {
                orbiters.add(item(world, new ItemStack(Material.AMETHYST_SHARD)));
            }
        } else if (kind == ExploreChestKind.MYTHIC) {
            for (int i = 0; i < 2; i++) {
                orbiters.add(item(world, new ItemStack(Material.END_ROD)));
            }
        } else if (kind == ExploreChestKind.LEGENDARY) {
            for (int k = 0; k < 6; k++) {
                BlockDisplay seg = world.spawn(origin, BlockDisplay.class, d -> {
                    setup(d);
                    d.setBlock(Material.GOLD_BLOCK.createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                });
                halo.add(seg);
            }
        }
        pose(0);
    }

    private void block(World world, BlockData data, Group group, Matrix4f local, int light) {
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, d -> {
            setup(d);
            d.setBlock(data);
            if (light >= 0) {
                d.setBrightness(new Display.Brightness(light, 15));
            }
            d.setTransformationMatrix(matrixFor(group, local));
        });
        parts.add(new Part(display, group, local));
    }

    private ItemDisplay item(World world, ItemStack stack) {
        return world.spawn(origin, ItemDisplay.class, d -> {
            setup(d);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
        });
    }

    private static void setup(Display d) {
        d.setPersistent(false);
        d.setInvulnerable(true);
        d.setGravity(false);
        d.setShadowRadius(0f);
        d.setViewRange(0.6f);
        d.addScoreboardTag(TAG);
    }

    /* =========================================================
     * MATRICES
     * ========================================================= */

    private Matrix4f frame(Group group) {
        Matrix4f m = new Matrix4f();
        if (group != Group.STATIC) {
            m.translate(0.0f, hover, 0.0f);
        }
        return m.rotateY(yaw);
    }

    private Matrix4f matrixFor(Group group, Matrix4f local) {
        Matrix4f m = frame(group);
        if (group == Group.LID) {
            // Hinge on the back top edge; negative X-rotation lifts the front.
            m.translate(0.0f, base + BH, -D / 2f).rotateX(-lid);
        }
        return m.mul(local);
    }

    /* =========================================================
     * ANIMATION
     * ========================================================= */

    /** Apply the current lid / hover / orbit state, interpolated over {@code ticks}. */
    private void pose(int ticks) {
        for (Part part : parts) {
            if (part.group == Group.STATIC || part.display == null || !part.display.isValid()) {
                continue;
            }
            if (part.group == Group.LID && lidTask != null) {
                // The lid tween owns these for now (its own, shorter interpolation).
                continue;
            }
            part.display.setInterpolationDelay(0);
            part.display.setInterpolationDuration(ticks);
            part.display.setTransformationMatrix(matrixFor(part.group, part.local));
        }
        poseOrbit(ticks);
    }

    private void poseLid(int ticks) {
        for (Part part : parts) {
            if (part.group != Group.LID || part.display == null || !part.display.isValid()) {
                continue;
            }
            part.display.setInterpolationDelay(0);
            part.display.setInterpolationDuration(ticks);
            part.display.setTransformationMatrix(matrixFor(Group.LID, part.local));
        }
    }

    private void poseOrbit(int ticks) {
        if (!orbiters.isEmpty()) {
            boolean epic = kind == ExploreChestKind.EPIC;
            float radius = epic ? 0.66f : 0.6f;
            float degPerTick = epic ? 2.2f : -3.0f;
            int n = orbiters.size();
            for (int i = 0; i < n; i++) {
                ItemDisplay orb = orbiters.get(i);
                if (orb == null || !orb.isValid()) {
                    continue;
                }
                float a = (float) Math.toRadians(age * degPerTick + i * (360.0 / n));
                float bob = (float) (0.05 * Math.sin(age * 0.07 + i * 2.1));
                float y = (epic ? base + 0.52f : base + 0.42f) + bob;
                Matrix4f m = new Matrix4f()
                        .translate(0.0f, epic ? 0.0f : hover, 0.0f)
                        .rotateY(a)
                        .translate(radius, y, 0.0f)
                        .rotateY((float) Math.toRadians(age * 6.0))
                        .rotateZ(epic ? (float) Math.toRadians(35.0) : 0.0f)
                        .scale(epic ? 0.3f : 0.24f);
                orb.setInterpolationDelay(0);
                orb.setInterpolationDuration(ticks);
                orb.setTransformationMatrix(m);
            }
        }
        if (!halo.isEmpty()) {
            float r = 0.3f;
            float apothem = (float) (r * Math.cos(Math.toRadians(30.0)));
            float t = 0.035f;
            float spin = (float) Math.toRadians(age * 1.5);
            float y = base + BH + LH + 0.3f + (float) (0.025 * Math.sin(age * 0.05));
            for (int k = 0; k < halo.size(); k++) {
                BlockDisplay seg = halo.get(k);
                if (seg == null || !seg.isValid()) {
                    continue;
                }
                Matrix4f m = new Matrix4f()
                        .translate(0.0f, y, 0.0f)
                        .rotateY(spin + (float) Math.toRadians(k * 60.0))
                        .translate(-r / 2f, -t / 2f, apothem - t / 2f)
                        .scale(r, t, t);
                seg.setInterpolationDelay(0);
                seg.setInterpolationDuration(ticks);
                seg.setTransformationMatrix(m);
            }
        }
    }

    /**
     * Idle life, called every {@code step} ticks while someone is near.
     * Also handles the chunk-safe case of parts having been dropped (returns false).
     */
    boolean idle(int step) {
        if (!valid()) {
            return false;
        }
        age += step;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean moved = false;
        if (kind == ExploreChestKind.MYTHIC) {
            hover = (float) (0.035 * Math.sin(age * (Math.PI * 2.0 / 90.0)));
            if (!opened) {
                // Never quite shut: the lid flutters between ajar and a little more ajar.
                lid = restLid + (float) Math.toRadians(3.0 * (0.5 + 0.5 * Math.sin(age * 0.09)));
            }
            moved = true;
            if (age % 20 < step && origin.getWorld() != null) {
                Location gap = light();
                origin.getWorld().spawnParticle(Particle.END_ROD, gap, 1, 0.18, 0.02, 0.12, 0.01);
            }
        } else if (!opened && lidTask == null) {
            if (breathUntil >= 0 && age >= breathUntil) {
                breathUntil = -1L;
                lid = restLid;
                poseLid(7);
            } else if (breathUntil < 0 && age >= nextBreath) {
                // The lid lifts a finger's width and settles — something in there shifted.
                lid = (float) Math.toRadians(kind == ExploreChestKind.RARE ? 6.0 : 4.0);
                breathUntil = age + 6;
                nextBreath = age + rng.nextLong(140L, 260L);
                poseLid(4);
                if (kind == ExploreChestKind.RARE && origin.getWorld() != null) {
                    origin.getWorld().playSound(origin, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.08f, 1.7f);
                }
            }
        }
        if (moved) {
            pose(step);
        } else {
            poseOrbit(step);
        }
        return true;
    }

    /** Someone the chest is ready for walked up: the hasp rattles once. */
    void notice() {
        if (opened || lidTask != null || kind == ExploreChestKind.MYTHIC || !valid()) {
            return;
        }
        lid = (float) Math.toRadians(3.0);
        poseLid(2);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!opened && lidTask == null && valid()) {
                lid = restLid;
                poseLid(3);
            }
        }, 3L);
    }

    /** Swing the lid open (a few short steps so it reads as a hinge), with sound and a spill of light. */
    void open() {
        if (!valid()) {
            return;
        }
        opened = true;
        breathUntil = -1L;
        tweenLid(OPEN);
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Location at = origin.clone().add(0.0, base + BH + hover, 0.0);
        switch (kind) {
            case RARE -> {
                world.playSound(at, Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.BLOCKS, 0.4f, 1.4f);
                world.playSound(at, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.55f, 1.1f);
            }
            case EPIC -> {
                world.playSound(at, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.55f, 0.9f);
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 1.0f);
            }
            case LEGENDARY -> {
                world.playSound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.BLOCKS, 0.6f, 1.0f);
                world.playSound(at, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.55f, 0.75f);
                world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 0.25f, 1.4f);
            }
            case MYTHIC -> {
                world.playSound(at, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 1.15f);
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.8f, 0.8f);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> spill(false), 4L);
    }

    /** The reward just landed on top — a second, brighter spill for the rarer crates. */
    void reveal() {
        spill(true);
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Location at = light().add(0.0, 0.5, 0.0);
        if (kind == ExploreChestKind.LEGENDARY) {
            world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.35f, 1.5f);
        } else if (kind == ExploreChestKind.MYTHIC) {
            world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.3f, 1.6f);
            world.spawnParticle(Particle.END_ROD, at, 12, 0.05, 0.6, 0.05, 0.04);
        }
    }

    void close() {
        if (!valid()) {
            opened = false;
            return;
        }
        opened = false;
        tweenLid(restLid);
        World world = origin.getWorld();
        if (world != null) {
            Sound sound = kind == ExploreChestKind.MYTHIC ? Sound.BLOCK_ENDER_CHEST_CLOSE : Sound.BLOCK_CHEST_CLOSE;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    world.playSound(origin, sound, SoundCategory.BLOCKS, 0.5f, 1.0f), 5L);
        }
    }

    private void tweenLid(float target) {
        if (lidTask != null) {
            lidTask.cancel();
            lidTask = null;
        }
        lidTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!valid()) {
                stopTween();
                return;
            }
            float diff = target - lid;
            if (Math.abs(diff) < 1.0e-3f) {
                stopTween();
                return;
            }
            lid += Math.signum(diff) * Math.min(Math.abs(diff), LID_STEP);
            poseLid(LID_STEP_TICKS);
        }, 1L, LID_STEP_TICKS);
    }

    private void stopTween() {
        if (lidTask != null) {
            lidTask.cancel();
            lidTask = null;
        }
    }

    private void spill(boolean bright) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Location at = light();
        Particle.DustOptions dust = new Particle.DustOptions(Palette.of(kind).tone, bright ? 1.1f : 0.8f);
        world.spawnParticle(Particle.DUST, at, bright ? 12 : 7, 0.25, 0.15, 0.18, 0.0, dust);
        Particle accent = switch (kind) {
            case RARE -> Particle.GLOW;
            case EPIC -> Particle.WITCH;
            case LEGENDARY -> Particle.WAX_ON;
            case MYTHIC -> Particle.END_ROD;
        };
        world.spawnParticle(accent, at, bright ? 6 : 3, 0.2, 0.2, 0.15, 0.02);
    }

    /** Top of the open chest (world space) — where the light comes out. */
    Location light() {
        return origin.clone().add(0.0, base + BH + hover + 0.08, 0.0);
    }

    Color tone() {
        return Palette.of(kind).tone;
    }

    ExploreChestKind kind() {
        return kind;
    }

    boolean valid() {
        if (parts.isEmpty()) {
            return false;
        }
        for (Part part : parts) {
            if (part.display == null || !part.display.isValid()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        stopTween();
        for (Part part : parts) {
            if (part.display != null && part.display.isValid()) {
                part.display.remove();
            }
        }
        parts.clear();
        for (ItemDisplay orb : orbiters) {
            if (orb != null && orb.isValid()) {
                orb.remove();
            }
        }
        orbiters.clear();
        for (BlockDisplay seg : halo) {
            if (seg != null && seg.isValid()) {
                seg.remove();
            }
        }
        halo.clear();
    }

    /* =========================================================
     * PALETTES
     * ========================================================= */

    private static final class Palette {
        private final BlockData body;
        private final BlockData lid;
        private final BlockData band;
        private final BlockData latch;
        private final BlockData gem;
        private final BlockData glow;
        private final BlockData trim;
        private final Color tone;

        private Palette(Material body, Material lid, Material band, Material latch, Material gem,
                        Material glow, Material trim, Color tone) {
            this.body = body.createBlockData();
            this.lid = lid.createBlockData();
            this.band = band.createBlockData();
            this.latch = latch.createBlockData();
            this.gem = gem.createBlockData();
            this.glow = glow.createBlockData();
            this.trim = trim.createBlockData();
            this.tone = tone;
        }

        private static final Palette RARE = new Palette(
                Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_WOOD, Material.WAXED_OXIDIZED_CUT_COPPER,
                Material.IRON_BLOCK, Material.PRISMARINE_BRICKS, Material.SEA_LANTERN, Material.SPRUCE_PLANKS,
                Color.fromRGB(96, 206, 226));
        private static final Palette EPIC = new Palette(
                Material.DARK_OAK_PLANKS, Material.STRIPPED_DARK_OAK_WOOD, Material.AMETHYST_BLOCK,
                Material.POLISHED_BLACKSTONE, Material.AMETHYST_BLOCK, Material.AMETHYST_BLOCK, Material.DARK_OAK_PLANKS,
                Color.fromRGB(168, 96, 226));
        private static final Palette LEGENDARY = new Palette(
                Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE, Material.GOLD_BLOCK,
                Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK, Material.SHROOMLIGHT, Material.GOLD_BLOCK,
                Color.fromRGB(255, 196, 64));
        private static final Palette MYTHIC = new Palette(
                Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.PURPUR_PILLAR,
                Material.AMETHYST_BLOCK, Material.PEARLESCENT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.OBSIDIAN,
                Color.fromRGB(255, 128, 232));

        static Palette of(ExploreChestKind kind) {
            return switch (kind) {
                case RARE -> RARE;
                case EPIC -> EPIC;
                case LEGENDARY -> LEGENDARY;
                case MYTHIC -> MYTHIC;
            };
        }
    }
}
