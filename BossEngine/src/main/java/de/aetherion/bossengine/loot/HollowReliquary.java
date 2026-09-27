package de.aetherion.bossengine.loot;

import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hollow Reliquary: the loot cache the Hollow Sun leaves behind once its supernova has cleared.
 *
 * A last ember falls out of the sky onto the boss anchor, burns a solar sigil into the floor and
 * assembles a blackstone-and-gold reliquary layer by layer on a sun-spoked dais. Eight crown rays
 * and a gold gyroscope halo turn around a nether star; a gold loot beam holds the column together.
 * Right-clicking it spins the claimant's own share and flings the lid open to pay it out.
 *
 * <p>Boss fights are session-less, so unlike the dungeon Reliquaries this prop owns its own claim:
 * there is no chest block and no world edit at all. The {@link Interaction} hitbox is the contract,
 * the bundles come straight from {@link LootService#distribute}, and every display is non-persistent
 * so a restart or an unload can never leak one. If nobody claims in time the prop pays the remaining
 * shares out on the ground and dissolves, so loot is never lost behind the spectacle.
 */
public final class HollowReliquary {

    /** Ticks from the prop's first frame until the claim opens. */
    private static final int CLAIM_AT = 106;
    /** Ticks after the claim opens before unclaimed shares fall to the ground and the prop dissolves. */
    private static final int EXPIRE_TICKS = 6000;
    private static final double SPINNER_Y = 2.25;
    private static final double LABEL_Y = 3.55;
    private static final float SPINNER_SCALE = 1.3f;

    private static final String PROP_TAG = "aether_sun_reliquary";
    private static final String HITBOX_TAG = "aether_sun_reliquary_box";
    private static final NamespacedKey PROP_KEY = new NamespacedKey("bossengine", "sun_reliquary");

    private static final Color GOLD = Color.fromRGB(255, 196, 64);
    private static final Color SOLAR = Color.fromRGB(255, 236, 160);
    private static final Color EMBER = Color.fromRGB(255, 122, 32);
    private static final Color VIOLET = Color.fromRGB(150, 70, 255);
    private static final Color PHOTON = Color.fromRGB(236, 226, 255);

    private static final Map<UUID, Reliquary> PROPS = new LinkedHashMap<>();
    private static BukkitTask ticker;

    private HollowReliquary() {
    }

    // ------------------------------------------------------------------ public api

    /**
     * Drops a Hollow Reliquary on {@code anchor} holding one share per player in {@code bundles}.
     *
     * @return false when there was nothing to hold, in which case the caller keeps the normal payout
     */
    public static boolean place(Plugin plugin, Location anchor, String bossName, Map<UUID, List<ItemStack>> bundles) {
        if (plugin == null || !plugin.isEnabled() || anchor == null || anchor.getWorld() == null) {
            return false;
        }
        Map<UUID, List<ItemStack>> shares = new LinkedHashMap<>();
        if (bundles != null) {
            bundles.forEach((playerId, items) -> {
                List<ItemStack> copy = new ArrayList<>();
                for (ItemStack item : items) {
                    if (item != null && !item.getType().isAir()) {
                        copy.add(item.clone());
                    }
                }
                if (!copy.isEmpty()) {
                    shares.put(playerId, copy);
                }
            });
        }
        if (shares.isEmpty()) {
            return false;
        }
        Location base = ground(anchor);
        clearNear(base, 4.0);
        Reliquary prop = new Reliquary(base, facingYaw(base), bossName, shares);
        build(prop);
        prop.spawnAll();
        PROPS.put(prop.id, prop);
        ensureTicker(plugin);
        return true;
    }

    /** @return true when {@code clicked} is a Reliquary hitbox, claimed or not, so the click is consumed */
    public static boolean click(Player player, Entity clicked) {
        if (player == null || clicked == null || !(clicked instanceof Interaction)
                || !clicked.getScoreboardTags().contains(HITBOX_TAG)) {
            return false;
        }
        String owner = clicked.getPersistentDataContainer().get(PROP_KEY, PersistentDataType.STRING);
        Reliquary prop = owner == null ? null : PROPS.get(parse(owner));
        if (prop != null) {
            prop.tryClaim(player);
        }
        return true;
    }

    /** True for any display or hitbox belonging to a Reliquary, so combat listeners can leave it alone. */
    public static boolean isProp(Entity entity) {
        return entity != null
                && (entity.getScoreboardTags().contains(PROP_TAG) || entity.getScoreboardTags().contains(HITBOX_TAG));
    }

    /** Pays out every remaining share on the ground and removes every prop. Used on plugin disable. */
    public static void clearAll() {
        for (Reliquary prop : new ArrayList<>(PROPS.values())) {
            prop.spill();
            prop.removeAll();
        }
        PROPS.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** Drops any stale prop (and stray displays) around {@code at} before a new one lands. */
    public static void clearNear(Location at, double radius) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        Iterator<Reliquary> it = PROPS.values().iterator();
        while (it.hasNext()) {
            Reliquary prop = it.next();
            if (prop.near(at, radius)) {
                prop.spill();
                prop.removeAll();
                it.remove();
            }
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at.clone().add(0, 2.0, 0), radius, 6.0, radius)) {
            if (isProp(entity)) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------ ticker

    private static void ensureTicker(Plugin plugin) {
        if (ticker != null && !ticker.isCancelled()) {
            return;
        }
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (PROPS.isEmpty()) {
                    cancel();
                    ticker = null;
                    return;
                }
                Iterator<Reliquary> it = PROPS.values().iterator();
                while (it.hasNext()) {
                    Reliquary prop = it.next();
                    boolean keep;
                    try {
                        keep = prop.tick();
                    } catch (RuntimeException exception) {
                        keep = false;
                    }
                    if (!keep) {
                        prop.spill();
                        prop.removeAll();
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ geometry

    private enum Role { BODY, LID, GLOW, BEAM, ORBIT, HALO, CROWN, STAR }

    /*
     * Local space: origin sits on the floor at the anchor, front is +Z, and the entity yaw turns the
     * whole rig toward the nearest player. Every measurement below is in blocks.
     */
    private static void build(Reliquary p) {
        // Dais: a burnt disc with eight gold sun spokes and four ember coals.
        List<Part> dais = new ArrayList<>();
        dais.add(p.box(Role.BODY, Material.POLISHED_BLACKSTONE, -1.05, 0, -1.05, 1.05, 0.12, 1.05));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.08, 0.08, -1.08, 1.08, 0.14, -1.0));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.08, 0.08, 1.0, 1.08, 0.14, 1.08));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.08, 0.08, -1.0, -1.0, 0.14, 1.0));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, 1.0, 0.08, -1.0, 1.08, 0.14, 1.0));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                dais.add(p.box(Role.BODY, Material.MAGMA_BLOCK,
                        sx * 0.95 - 0.12, 0.0, sz * 0.95 - 0.12, sx * 0.95 + 0.12, 0.2, sz * 0.95 + 0.12));
            }
        }

        List<Part> spokes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            spokes.add(p.spoke(Role.GLOW, i % 2 == 0 ? Material.OCHRE_FROGLIGHT : Material.SHROOMLIGHT,
                    a, 0.78, 0.5, 0.14, 0.13));
        }

        List<Part> step = new ArrayList<>();
        step.add(p.box(Role.BODY, Material.GILDED_BLACKSTONE, -0.82, 0.12, -0.7, 0.82, 0.24, 0.7));

        // Body: a blackstone husk in a gold cage with ember panels.
        List<Part> body = new ArrayList<>();
        body.add(p.box(Role.BODY, Material.BLACKSTONE, -0.66, 0.24, -0.52, 0.66, 0.96, 0.52));

        List<Part> frame = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                frame.add(p.box(Role.BODY, Material.GOLD_BLOCK,
                        Math.min(sx * 0.61, sx * 0.7), 0.24, Math.min(sz * 0.47, sz * 0.56),
                        Math.max(sx * 0.61, sx * 0.7), 0.99, Math.max(sz * 0.47, sz * 0.56)));
            }
        }
        frame.add(p.box(Role.BODY, Material.GOLD_BLOCK, -0.675, 0.24, -0.535, 0.675, 0.3, 0.535));
        frame.add(p.box(Role.BODY, Material.GOLD_BLOCK, -0.675, 0.56, -0.535, 0.675, 0.62, 0.535));

        List<Part> panels = new ArrayList<>();
        panels.add(p.box(Role.BODY, Material.MAGMA_BLOCK, -0.44, 0.34, 0.52, 0.44, 0.9, 0.545));
        panels.add(p.box(Role.BODY, Material.MAGMA_BLOCK, -0.44, 0.34, -0.545, 0.44, 0.9, -0.52));
        panels.add(p.box(Role.BODY, Material.MAGMA_BLOCK, 0.66, 0.34, -0.34, 0.685, 0.9, 0.34));
        panels.add(p.box(Role.BODY, Material.MAGMA_BLOCK, -0.685, 0.34, -0.34, -0.66, 0.9, 0.34));
        panels.add(p.diamond(Role.GLOW, Material.SHROOMLIGHT, 0, 0.62, 0.55, 0.28, 0.03));
        panels.add(p.diamond(Role.GLOW, Material.OCHRE_FROGLIGHT, 0, 0.62, 0.575, 0.13, 0.025));

        List<Part> glow = new ArrayList<>();
        glow.add(p.box(Role.GLOW, Material.OCHRE_FROGLIGHT, -0.62, 0.91, -0.48, 0.62, 0.965, 0.48));
        glow.add(p.box(Role.GLOW, Material.OCHRE_FROGLIGHT, -0.672, 0.915, -0.532, 0.672, 0.955, 0.532));

        // Lid: hinges backwards on claim, flings wide open on the reveal.
        List<Part> lid = new ArrayList<>();
        lid.add(p.box(Role.LID, Material.BLACKSTONE, -0.68, 0.96, -0.54, 0.68, 1.12, 0.54));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.695, 1.09, -0.555, 0.695, 1.13, 0.555));
        lid.add(p.box(Role.LID, Material.GILDED_BLACKSTONE, -0.54, 1.12, -0.42, 0.54, 1.25, 0.42));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.4, 1.25, -0.28, 0.4, 1.33, 0.28));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.42, 1.33, -0.05, 0.42, 1.39, 0.05));
        lid.add(p.diamond(Role.LID, Material.GOLD_BLOCK, 0, 1.03, 0.55, 0.3, 0.04));
        lid.add(p.diamond(Role.LID, Material.SHROOMLIGHT, 0, 1.03, 0.575, 0.15, 0.03));

        // Ember obelisks at the dais corners.
        List<Part> pillars = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double cx = sx * 0.95;
                double cz = sz * 0.95;
                pillars.add(p.box(Role.GLOW, Material.SHROOMLIGHT, cx - 0.085, 0.2, cz - 0.085, cx + 0.085, 1.5, cz + 0.085));
                pillars.add(p.box(Role.GLOW, Material.OCHRE_FROGLIGHT, cx - 0.12, 1.5, cz - 0.12, cx + 0.12, 1.66, cz + 0.12));
            }
        }

        stage(dais, 20, 8, 0, -0.35, 0, 0.6);
        stage(spokes, 24, 8, 0, 0, 0, 0.02);
        stage(step, 28, 7, 0, -0.25, 0, 0.5);
        stage(body, 32, 7, 0, -0.4, 0, 0.3);
        stage(frame, 36, 6, 0, 0.3, 0, 0.2);
        stage(panels, 40, 6, 0, 0, 0, 0.1);
        stage(glow, 44, 5, 0, 0, 0, 0.1);
        stage(lid, 50, 8, 0, 0.95, 0, 0.5);
        for (Part pillar : pillars) {
            pillar.growUp(56, 8);
        }

        // Gyroscope halo around the loot: a gold vertical ring and a violet flat ring.
        int upright = 14;
        float radius = 0.62f;
        for (int i = 0; i < upright; i++) {
            double a = i * Math.PI * 2 / upright;
            Part seg = p.segmentVertical(Material.GOLD_BLOCK, a, radius, 0.06f, SPINNER_Y,
                    2 * (float) Math.PI * radius / upright * 1.05f);
            seg.appear(64 + i / 4, 6, 0, 0, 0, 0.05);
        }
        int flat = 12;
        for (int i = 0; i < flat; i++) {
            double a = i * Math.PI * 2 / flat;
            Part seg = p.segmentFlat(Material.PURPLE_STAINED_GLASS, a, 0.88f, 0.05f, SPINNER_Y,
                    2 * (float) Math.PI * 0.88f / flat * 1.05f);
            seg.appear(68 + i / 4, 6, 0, 0, 0, 0.05);
        }

        // Ember shards orbiting the halo.
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            Part shard = p.shard(Role.ORBIT, i % 2 == 0 ? Material.MAGMA_BLOCK : Material.AMETHYST_CLUSTER,
                    Math.cos(a) * 1.22, 0, Math.sin(a) * 1.22, 0.36, 1.5);
            shard.phase = i * 1.26f;
            shard.flyIn(74 + i, 9, 3.0, 1.6);
        }

        // Crown of eight rays, the Hollow Sun's own silhouette, splayed around the star.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vector3f dir = new Vector3f(
                    (float) (Math.cos(a) * Math.sin(1.12)),
                    (float) Math.cos(1.12),
                    (float) (Math.sin(a) * Math.sin(1.12)));
            Part ray = p.ray(i % 2 == 0 ? Material.GOLD_BLOCK : Material.OCHRE_FROGLIGHT,
                    dir, 0.46f, i % 2 == 0 ? 0.92f : 0.6f, 0.1f, SPINNER_Y + 0.35);
            ray.appear(84 + i / 2, 7, 0, 0, 0, 0.05);
        }

        Part star = p.star(new ItemStack(Material.NETHER_STAR), SPINNER_Y + 0.95, 0.55f);
        star.appear(94, 6, 0, 0, 0, 0.05);

        Part outer = p.beam(Material.YELLOW_STAINED_GLASS, 0.26, SPINNER_Y + 1.25, 13.0);
        Part inner = p.beam(Material.WHITE_STAINED_GLASS, 0.1, SPINNER_Y + 1.25, 13.0);
        outer.growUp(100, 6);
        inner.growUp(101, 6);
    }

    private static void stage(List<Part> parts, int at, int ticks, double dx, double dy, double dz, double scale) {
        for (Part part : parts) {
            part.appear(at, ticks, dx, dy, dz, scale);
        }
    }

    // ------------------------------------------------------------------ prop

    private static final class Reliquary {

        private final UUID id = UUID.randomUUID();
        private final UUID worldId;
        private final double baseX;
        private final double baseY;
        private final double baseZ;
        private final float yaw;
        private final String bossName;
        private final Map<UUID, List<ItemStack>> shares;
        private final Set<UUID> claimed = new HashSet<>();
        private final List<Part> parts = new ArrayList<>();
        private final List<Temp> temps = new ArrayList<>();
        private final List<Spark> sparks = new ArrayList<>();

        private UUID hitbox;
        private UUID spinner;
        private UUID label;
        private int age;
        private int missing;
        private boolean claimOpen;
        private boolean spent;
        private int spentAt = -1;
        private int dissolveAt = -1;
        private double lid;
        private double lidTarget;
        private double lidSpeed = 0.2;
        private boolean slamPending;

        private UUID spinFor;
        private List<ItemStack> spinPool = List.of();
        private ItemStack spinPrize;
        private int spinTick = -1;
        private int spinTotal;
        private int payAt = -1;
        private int idleStep;
        private double spinBoost;
        private float halo;
        private float orbit;
        private float crown;

        private Reliquary(Location base, float yaw, String bossName, Map<UUID, List<ItemStack>> shares) {
            this.worldId = base.getWorld().getUID();
            this.baseX = base.getX();
            this.baseY = base.getY();
            this.baseZ = base.getZ();
            this.yaw = yaw;
            this.bossName = bossName == null || bossName.isBlank() ? "&6&lThe Hollow Sun" : bossName;
            this.shares = shares;
        }

        private World world() {
            return Bukkit.getWorld(worldId);
        }

        private boolean near(Location at, double radius) {
            if (!worldId.equals(at.getWorld().getUID())) {
                return false;
            }
            double dx = at.getX() - baseX;
            double dz = at.getZ() - baseZ;
            return dx * dx + dz * dz <= radius * radius;
        }

        private Location at(double lx, double ly, double lz) {
            Vector3f rotated = new Quaternionf().rotateY((float) Math.toRadians(-yaw))
                    .transform(new Vector3f((float) lx, 0f, (float) lz));
            return new Location(world(), baseX + rotated.x, baseY + ly, baseZ + rotated.z);
        }

        // --- part factories

        private Part box(Role role, Material material, double x0, double y0, double z0, double x1, double y1, double z1) {
            Transformation home = new Transformation(
                    new Vector3f((float) x0, (float) y0, (float) z0),
                    new Quaternionf(),
                    new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0)),
                    new Quaternionf());
            return add(new Part(role, material, null, home, 0.0));
        }

        /** A square turned 45 degrees on the front face, centered at (cx, cy, cz). */
        private Part diamond(Role role, Material material, double cx, double cy, double cz, double size, double depth) {
            Quaternionf q = new Quaternionf().rotateZ((float) (Math.PI / 4));
            Vector3f scale = new Vector3f((float) size, (float) size, (float) depth);
            Vector3f half = q.transform(new Vector3f(scale).mul(0.5f));
            Vector3f t = new Vector3f((float) cx, (float) cy, (float) cz).sub(half);
            return add(new Part(role, material, null, new Transformation(t, q, scale, new Quaternionf()), 0.0));
        }

        /** A flat bar lying on the dais, pointing outward from the centre. */
        private Part spoke(Role role, Material material, double angle, double r0, double len, double thick, double y) {
            Quaternionf q = new Quaternionf().rotateY((float) -angle);
            Vector3f scale = new Vector3f((float) len, 0.05f, (float) thick);
            Vector3f t = new Vector3f((float) (Math.cos(angle) * r0), (float) y, (float) (Math.sin(angle) * r0))
                    .add(q.transform(new Vector3f(0f, 0f, (float) (-thick / 2.0))));
            return add(new Part(role, material, null, new Transformation(t, q, scale, new Quaternionf()), 0.0));
        }

        /** A tapered ray pointing along {@code dir}, starting {@code start} blocks from the orbit centre. */
        private Part ray(Material material, Vector3f dir, float start, float length, float width, double lift) {
            Vector3f axis = new Vector3f(dir);
            if (axis.lengthSquared() < 1.0E-6f) {
                axis.set(0, 1, 0);
            }
            axis.normalize();
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
            Vector3f t = q.transform(new Vector3f(-width / 2f, start, -width / 2f));
            return add(new Part(Role.CROWN, material, null,
                    new Transformation(t, q, new Vector3f(width, length, width), new Quaternionf()), lift));
        }

        /** Tilted cube around an orbit centre {@code lift} above the floor. */
        private Part shard(Role role, Material material, double cx, double cy, double cz, double size, double lift) {
            Quaternionf q = new Quaternionf().rotateY((float) Math.atan2(cx, cz)).rotateX(0.6f).rotateZ(0.6f);
            Vector3f scale = new Vector3f((float) size, (float) size, (float) size);
            Vector3f half = q.transform(new Vector3f(scale).mul(0.5f));
            Vector3f t = new Vector3f((float) cx, (float) cy, (float) cz).sub(half);
            return add(new Part(role, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        /** Tangent segment of a ring standing upright in the local XY plane. */
        private Part segmentVertical(Material material, double a, float r, float thick, double lift, float len) {
            Quaternionf q = new Quaternionf().rotateZ((float) (a + Math.PI / 2));
            Vector3f scale = new Vector3f(len, thick, thick);
            Vector3f center = new Vector3f((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0f);
            Vector3f t = center.sub(q.transform(new Vector3f(scale).mul(0.5f)));
            return add(new Part(Role.HALO, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        /** Tangent segment of a flat ring in the local XZ plane. */
        private Part segmentFlat(Material material, double a, float r, float thick, double lift, float len) {
            Quaternionf q = new Quaternionf().rotateY((float) -(a + Math.PI / 2));
            Vector3f scale = new Vector3f(len, thick, thick);
            Vector3f center = new Vector3f((float) (Math.cos(a) * r), 0f, (float) (Math.sin(a) * r));
            Vector3f t = center.sub(q.transform(new Vector3f(scale).mul(0.5f)));
            return add(new Part(Role.HALO, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        private Part beam(Material material, double width, double from, double height) {
            Transformation home = new Transformation(
                    new Vector3f((float) (-width / 2.0), (float) from, (float) (-width / 2.0)),
                    new Quaternionf(),
                    new Vector3f((float) width, (float) height, (float) width),
                    new Quaternionf());
            return add(new Part(Role.BEAM, material, null, home, 0.0));
        }

        private Part star(ItemStack item, double lift, float size) {
            Transformation home = new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(size, size, size), new Quaternionf());
            return add(new Part(Role.STAR, null, item, home, lift));
        }

        private Part add(Part part) {
            if (part.role != Role.BODY && part.role != Role.LID) {
                part.glow = true;
            }
            parts.add(part);
            return part;
        }

        // --- lifecycle

        private void spawnAll() {
            World world = world();
            if (world == null) {
                return;
            }
            for (Part part : parts) {
                Location spawnAt = new Location(world, baseX, baseY + part.lift, baseZ, yaw, 0f);
                Display display;
                if (part.item != null) {
                    display = world.spawn(spawnAt, ItemDisplay.class, spawned -> {
                        spawned.setItemStack(part.item);
                        spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        prepare(spawned, part);
                    });
                } else {
                    BlockData data = part.material.createBlockData();
                    display = world.spawn(spawnAt, BlockDisplay.class, spawned -> {
                        spawned.setBlock(data);
                        prepare(spawned, part);
                    });
                }
                part.id = display.getUniqueId();
                part.cached = display;
            }
        }

        private void prepare(Display display, Part part) {
            display.setTransformation(part.hidden);
            display.setInterpolationDuration(0);
            display.setTeleportDuration(part.role == Role.ORBIT || part.role == Role.STAR ? 2 : 0);
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setViewRange(2.0f);
            if (part.glow) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.addScoreboardTag(PROP_TAG);
            display.getPersistentDataContainer().set(PROP_KEY, PersistentDataType.STRING, id.toString());
        }

        private void openClaim() {
            World world = world();
            if (world == null) {
                return;
            }
            claimOpen = true;
            Interaction box = world.spawn(at(0, 0, 0), Interaction.class, spawned -> {
                spawned.setInteractionWidth(2.4f);
                spawned.setInteractionHeight(1.7f);
                spawned.setResponsive(true);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(HITBOX_TAG);
                spawned.getPersistentDataContainer().set(PROP_KEY, PersistentDataType.STRING, id.toString());
            });
            hitbox = box.getUniqueId();

            List<ItemStack> pool = showcase();
            ItemStack first = pool.isEmpty() ? new ItemStack(Material.NETHER_STAR) : pool.get(0);
            ItemDisplay spin = world.spawn(at(0, SPINNER_Y, 0), ItemDisplay.class, spawned -> {
                spawned.setItemStack(first);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setShadowRadius(0f);
                spawned.setViewRange(2.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.setTransformation(spinPose(0f, 0.55f * SPINNER_SCALE));
                spawned.addScoreboardTag(PROP_TAG);
                spawned.getPersistentDataContainer().set(PROP_KEY, PersistentDataType.STRING, id.toString());
            });
            spinner = spin.getUniqueId();

            TextDisplay text = world.spawn(at(0, LABEL_Y, 0), TextDisplay.class, spawned -> {
                spawned.text(labelText());
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setSeeThrough(false);
                spawned.setShadowed(true);
                spawned.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                spawned.setViewRange(2.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(PROP_TAG);
                spawned.getPersistentDataContainer().set(PROP_KEY, PersistentDataType.STRING, id.toString());
            });
            label = text.getUniqueId();
            announce();
        }

        private void removeAll() {
            for (Part part : parts) {
                Display display = part.resolve();
                if (display != null) {
                    display.remove();
                }
            }
            parts.clear();
            for (Temp temp : temps) {
                if (temp.display != null && temp.display.isValid()) {
                    temp.display.remove();
                }
            }
            temps.clear();
            for (Spark spark : sparks) {
                if (spark.display != null && spark.display.isValid()) {
                    spark.display.remove();
                }
            }
            sparks.clear();
            for (UUID entityId : new UUID[]{hitbox, spinner, label}) {
                if (entityId == null) {
                    continue;
                }
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                }
            }
            hitbox = null;
            spinner = null;
            label = null;
        }

        /** Any share still unclaimed falls on the dais, so the spectacle can never eat loot. */
        private void spill() {
            World world = world();
            if (world == null) {
                shares.clear();
                return;
            }
            Location drop = at(0, 0.6, 0);
            for (Map.Entry<UUID, List<ItemStack>> entry : shares.entrySet()) {
                if (claimed.contains(entry.getKey())) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                for (ItemStack item : entry.getValue()) {
                    if (player != null && player.isOnline()) {
                        LootService.giveLootItem(player, item.clone());
                    } else {
                        world.dropItemNaturally(drop, item.clone());
                    }
                }
                if (player != null && player.isOnline()) {
                    player.sendMessage(TextUtil.component("&6Hollow Reliquary &8» &7your share was handed over."));
                }
            }
            claimed.addAll(shares.keySet());
        }

        /** @return false when the prop is gone for good and should be disposed */
        private boolean tick() {
            World world = world();
            if (world == null || parts.isEmpty()) {
                return false;
            }
            int cx = (int) Math.floor(baseX) >> 4;
            int cz = (int) Math.floor(baseZ) >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                return true;
            }
            Chunk chunk = world.getChunkAt(cx, cz);
            if (!chunk.isEntitiesLoaded()) {
                return true;
            }
            if (parts.get(0).resolve() == null) {
                return ++missing < 40;
            }
            missing = 0;
            age++;

            for (Part part : parts) {
                if (!part.shown && age >= part.appearAt) {
                    part.shown = true;
                    ease(part.resolve(), pose(part), part.appearTicks);
                }
            }
            script(world);
            if (!claimOpen && age >= CLAIM_AT) {
                openClaim();
            }
            tickLid(world);
            tickSpin(world);
            tickTemps();
            tickSparks(world);
            if (spentAt >= 0 && age >= spentAt && !spent) {
                becomeSpent(world);
            }
            if (dissolveAt >= 0 && age >= dissolveAt) {
                dissolve(world);
                return false;
            }
            if (claimOpen && !spent && age >= CLAIM_AT + EXPIRE_TICKS) {
                shout("&6The Hollow Reliquary &7burned out. &8Its shares were handed over.");
                spill();
                spentAt = age;
                dissolveAt = age + 40;
            }
            spinBoost *= 0.9;
            if (!spent && playersNear(world, 56.0)) {
                idle(world);
            }
            return true;
        }

        private boolean playersNear(World world, double radius) {
            double r2 = radius * radius;
            for (Player player : world.getPlayers()) {
                Location loc = player.getLocation();
                double dx = loc.getX() - baseX;
                double dy = loc.getY() - baseY;
                double dz = loc.getZ() - baseZ;
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    return true;
                }
            }
            return false;
        }

        // --- arrival script

        /*
         * 1-6 a last ember falls out of the sky and lands on the anchor · 8-34 the solar sigil draws itself ·
         * 20-63 the reliquary assembles bottom-up, one chime per layer · 64-80 halo and ember shards ·
         * 84-92 the crown of rays · 94 the star ignites (title) · 100 the loot beam · 106 the claim opens ·
         * 110 the lid breathes.
         */
        private void script(World world) {
            Location base = at(0, 0.1, 0);
            if (age == 1) {
                world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
                world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.2f, 0.7f);
                world.playSound(base, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.6f);
                Temp outer = temp(world, Material.YELLOW_STAINED_GLASS, skyColumn(1.2f, 0.001f), 44);
                Temp inner = temp(world, Material.WHITE_STAINED_GLASS, skyColumn(0.5f, 0.001f), 44);
                outer.then(skyColumn(1.2f, 30f), 5);
                inner.then(skyColumn(0.5f, 30f), 5);
            }
            if (age == 6) {
                Location impact = at(0, 0.3, 0);
                world.spawnParticle(Particle.FLASH, impact, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.END_ROD, impact, 44, 0.3, 0.1, 0.3, 0.28);
                world.spawnParticle(Particle.LAVA, impact, 20, 0.6, 0.1, 0.6, 0);
                world.spawnParticle(Particle.BLOCK, impact, 44, 1.3, 0.05, 1.3, 0, floorData(world));
                world.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.9f, 1.2f);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                world.playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.3f, 0.7f);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + random.nextDouble(0.3);
                    Vector vel = new Vector(Math.cos(a) * random.nextDouble(0.16, 0.3),
                            random.nextDouble(0.28, 0.5), Math.sin(a) * random.nextDouble(0.16, 0.3));
                    spark(world, impact, i % 2 == 0 ? Material.MAGMA_BLOCK : Material.SHROOMLIGHT, vel,
                            (float) random.nextDouble(0.18, 0.3), 24 + random.nextInt(10));
                }
            }
            if (age == 8) {
                world.playSound(base, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.8f);
            }
            if (age >= 8 && age <= 110 && age % 2 == 0) {
                double fill = Math.min(1.0, (age - 8) / 26.0);
                ringParticles(world, at(0, 0.16, 0), 1.5, GOLD, 1.0f, fill);
                ringParticles(world, at(0, 0.16, 0), 2.15, EMBER, 0.85f, fill);
                if (age <= 40) {
                    ringParticles(world, at(0, 0.16, 0), 2.7, VIOLET, 0.7f, fill);
                    world.spawnParticle(Particle.ENCHANT, at(0, 0.7, 0), 10, 1.5, 0.3, 1.5, 0.4);
                }
            }
            if (age == 30) {
                for (Temp temp : temps) {
                    if (temp.sky) {
                        temp.then(skyColumn(0.001f, 30f), 10);
                    }
                }
            }
            int[] layers = {20, 24, 28, 32, 36, 40, 44, 50, 56, 64, 74, 84};
            for (int i = 0; i < layers.length; i++) {
                if (age == layers[i]) {
                    float pitch = 0.6f + i * 0.11f;
                    world.playSound(base, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, pitch);
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.85f, pitch);
                    world.spawnParticle(Particle.END_ROD, at(0, 0.3 + i * 0.12, 0), 6, 0.5, 0.1, 0.5, 0.03);
                }
            }
            if (age == 94) {
                Location star = at(0, SPINNER_Y + 0.95, 0);
                world.spawnParticle(Particle.FLASH, star, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, star, 46, 0.3, 0.3, 0.3, 0.45);
                world.spawnParticle(Particle.END_ROD, star, 24, 0.1, 0.1, 0.1, 0.16);
                world.spawnParticle(Particle.FIREWORK, star, 20, 0.2, 0.2, 0.2, 0.08);
                world.playSound(star, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.0f);
                world.playSound(star, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.3f);
                world.playSound(star, Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
                for (Player player : viewers(world, 48.0)) {
                    player.showTitle(Title.title(
                            TextUtil.component("&6&l✦ Hollow Reliquary ✦"),
                            TextUtil.component("&7Remnant of the Sun"),
                            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2200), Duration.ofMillis(700))));
                }
            }
            if (age == 100) {
                world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }
            if (age == 110 && !busy()) {
                setLid(-0.5, 0.12);
                world.playSound(at(0, 0.93, 0), Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.9f, 0.85f);
                world.spawnParticle(Particle.DUST, at(0, 1.1, 0), 14, 0.4, 0.1, 0.35, 0,
                        new Particle.DustOptions(GOLD, 1.0f));
            }
            if (age == 122 && !busy()) {
                setLid(0.0, 0.25);
                slamPending = true;
            }
        }

        private void idle(World world) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float boost = (float) (1.0 + 3.0 * spinBoost);
            if (age % 2 == 0) {
                halo = wrap(halo + 5.0f * boost);
                orbit = wrap(orbit - 3.2f * boost);
                crown = wrap(crown + 1.6f * boost);
                animateRing(Role.HALO, halo);
                animateRing(Role.CROWN, crown);
                animateOrbit(0.09);
                animateStar();
            }
            if (age % 5 == 0) {
                double h = SPINNER_Y + 1.3 + random.nextDouble(12.0);
                world.spawnParticle(Particle.DUST, at(0, h, 0), 1, 0.1, 0.2, 0.1, 0,
                        new Particle.DustOptions(random.nextBoolean() ? SOLAR : PHOTON, 1.0f));
            }
            if (age % 6 == 0) {
                int sx = random.nextBoolean() ? 1 : -1;
                int sz = random.nextBoolean() ? 1 : -1;
                world.spawnParticle(Particle.SMALL_FLAME, at(sx * 0.95, 1.7, sz * 0.95), 1, 0.02, 0.05, 0.02, 0.005);
            }
            if (age % 9 == 0) {
                Location mote = at(random.nextDouble(-0.6, 0.6), 0.95 + random.nextDouble(0.3), random.nextDouble(-0.6, 0.6));
                world.spawnParticle(Particle.DUST, mote, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(EMBER, 0.8f));
            }
            if (age % 11 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, at(0, 0.22, 0), 4, 1.0, 0.05, 1.0, 0.01);
            }
            if (claimOpen && !busy() && age % 8 == 0) {
                ItemDisplay spin = spinnerDisplay();
                List<ItemStack> pool = showcase();
                if (spin != null && !pool.isEmpty()) {
                    idleStep++;
                    spin.setItemStack(pool.get(idleStep % pool.size()));
                    spin.setTransformation(spinPose(idleStep * 18f, 0.55f * SPINNER_SCALE));
                }
            }
            if (age % 14 == 0) {
                ringParticles(world, at(0, 0.16, 0), 1.5, GOLD, 0.7f, 1.0);
            }
            if (age % 140 == 0) {
                world.playSound(at(0, 1, 0), Sound.BLOCK_BEACON_AMBIENT, 0.55f, 1.4f);
            }
        }

        private void animateRing(Role role, float angle) {
            for (Part part : parts) {
                if (part.role != role || !ready(part)) {
                    continue;
                }
                Display display = part.resolve();
                if (display != null) {
                    display.setRotation(wrap(yaw + angle), 0f);
                }
            }
        }

        private void animateOrbit(double bob) {
            for (Part part : parts) {
                if (part.role != Role.ORBIT || !ready(part)) {
                    continue;
                }
                Display display = part.resolve();
                if (display == null) {
                    continue;
                }
                display.setRotation(wrap(yaw + orbit), 0f);
                Vector3f t = new Vector3f(part.home.getTranslation())
                        .add(0f, (float) (Math.sin(age * 0.12 + part.phase) * bob), 0f);
                ease(display, new Transformation(t, part.home.getLeftRotation(), part.home.getScale(),
                        part.home.getRightRotation()), 2);
            }
        }

        private void animateStar() {
            for (Part part : parts) {
                if (part.role != Role.STAR || !ready(part)) {
                    continue;
                }
                Display display = part.resolve();
                if (display == null) {
                    continue;
                }
                display.setRotation(wrap(yaw - halo * 1.7f), 0f);
                Vector3f t = new Vector3f(0f, (float) (Math.sin(age * 0.08) * 0.07), 0f);
                ease(display, new Transformation(t, part.home.getLeftRotation(), part.home.getScale(),
                        part.home.getRightRotation()), 2);
            }
        }

        private boolean ready(Part part) {
            return part.shown && age >= part.appearAt + part.appearTicks;
        }

        // --- lid

        private void setLid(double target, double speed) {
            lidTarget = target;
            lidSpeed = speed;
        }

        private void tickLid(World world) {
            if (Math.abs(lid - lidTarget) < 1.0E-4) {
                return;
            }
            double before = lid;
            lid = lid < lidTarget ? Math.min(lidTarget, lid + lidSpeed) : Math.max(lidTarget, lid - lidSpeed);
            for (Part part : parts) {
                if (part.role == Role.LID && ready(part)) {
                    ease(part.resolve(), pose(part), 1);
                }
            }
            if (slamPending && before < 0 && lid >= 0) {
                slamPending = false;
                Location seam = at(0, 0.93, 0);
                world.playSound(seam, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 0.9f, 0.75f);
                world.spawnParticle(Particle.SMOKE, seam, 6, 0.45, 0.03, 0.4, 0.01);
            }
        }

        private Transformation pose(Part part) {
            if (part.role == Role.LID && Math.abs(lid) > 1.0E-4) {
                return hinged(part.home, new Vector3f(0f, 0.96f, -0.54f), lid);
            }
            if (spent && part.role == Role.ORBIT) {
                Vector3f t = new Vector3f(part.home.getTranslation()).add(0f, (float) (0.24 - part.lift), 0f);
                return new Transformation(t, part.home.getLeftRotation(), part.home.getScale(), part.home.getRightRotation());
            }
            return part.home;
        }

        // --- claim

        private boolean busy() {
            return spinTick >= 0 || payAt >= 0;
        }

        private void tryClaim(Player player) {
            World world = world();
            if (world == null) {
                return;
            }
            Location seam = at(0, 0.93, 0);
            if (!claimOpen || spent) {
                player.playSound(seam, Sound.BLOCK_CHEST_LOCKED, 0.4f, 1.4f);
                return;
            }
            if (busy()) {
                player.sendMessage(TextUtil.component("&7Wait for the spin."));
                return;
            }
            UUID playerId = player.getUniqueId();
            if (claimed.contains(playerId)) {
                player.sendMessage(TextUtil.component("&7You already took your share."));
                player.playSound(seam, Sound.BLOCK_CHEST_LOCKED, 0.4f, 1.4f);
                return;
            }
            List<ItemStack> share = shares.get(playerId);
            if (share == null || share.isEmpty()) {
                player.sendMessage(TextUtil.component("&7The Reliquary holds nothing for you."));
                player.playSound(seam, Sound.BLOCK_CHEST_LOCKED, 0.4f, 1.6f);
                return;
            }
            spinFor = playerId;
            spinPool = new ArrayList<>(share);
            if (spinPool.size() < 3) {
                spinPool.addAll(showcase());
            }
            spinPrize = best(share);
            spinTick = 0;
            spinTotal = 22 + ThreadLocalRandom.current().nextInt(6);
            setLid(-0.42, 0.1);
            world.playSound(seam, Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.9f, 0.8f);
            world.spawnParticle(Particle.DUST, seam, 10, 0.4, 0.05, 0.35, 0, new Particle.DustOptions(GOLD, 0.9f));
            setLabel(TextUtil.component("&7..."));
        }

        private void tickSpin(World world) {
            if (payAt >= 0) {
                if (age >= payAt) {
                    payAt = -1;
                    payOut(world);
                }
                return;
            }
            if (spinTick < 0) {
                return;
            }
            Player player = Bukkit.getPlayer(spinFor);
            ItemDisplay spin = spinnerDisplay();
            if (player == null || !player.isOnline() || spin == null) {
                spinTick = -1;
                spinFor = null;
                setLid(0.0, 0.3);
                slamPending = true;
                setLabel(labelText());
                return;
            }
            spinTick++;
            spinBoost = 1.0;
            if (spinTick < spinTotal) {
                if (!spinPool.isEmpty()) {
                    spin.setItemStack(spinPool.get(spinTick % spinPool.size()));
                }
                spin.setTransformation(spinPose(spinTick * 42f, 0.62f * SPINNER_SCALE));
                player.playSound(at(0, SPINNER_Y, 0), Sound.UI_BUTTON_CLICK, 0.25f, 1.4f + (spinTick % 5) * 0.08f);
                if (spinTick % 4 == 0) {
                    world.spawnParticle(Particle.ENCHANT, at(0, SPINNER_Y, 0), 8, 0.6, 0.6, 0.6, 0.6);
                }
                return;
            }
            spin.setItemStack(spinPrize == null ? new ItemStack(Material.NETHER_STAR) : spinPrize);
            spin.setTransformation(spinPose(0f, 0.85f * SPINNER_SCALE));
            reveal(world);
            spinTick = -1;
            payAt = age + 16;
        }

        private void reveal(World world) {
            setLid(-1.95, 0.38);
            Location core = at(0, SPINNER_Y, 0);
            Location seam = at(0, 1.03, 0);
            world.playSound(core, Sound.BLOCK_VAULT_EJECT_ITEM, 0.9f, 1.0f);
            world.playSound(core, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, core, 30, 0.3, 0.4, 0.3, 0.35);
            world.spawnParticle(Particle.END_ROD, core, 26, 0.2, 0.2, 0.2, 0.12);
            world.spawnParticle(Particle.DUST, seam, 24, 0.5, 0.25, 0.4, 0, new Particle.DustOptions(GOLD, 1.1f));
            world.spawnParticle(Particle.DUST, core, 14, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(EMBER, 1.0f));
            ringParticles(world, at(0, 0.18, 0), 1.6, SOLAR, 1.2f, 1.0);
            Temp flare = temp(world, Material.YELLOW_STAINED_GLASS, column(0.7f, 0.001f, (float) SPINNER_Y - 0.4f), 40);
            flare.sky = false;
            flare.then(column(0.7f, 24f, (float) SPINNER_Y - 0.4f), 5);
        }

        private void payOut(World world) {
            UUID playerId = spinFor;
            spinFor = null;
            List<ItemStack> share = playerId == null ? null : shares.get(playerId);
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            if (playerId != null) {
                claimed.add(playerId);
            }
            if (share != null && player != null && player.isOnline()) {
                for (ItemStack item : share) {
                    LootService.giveLootItem(player, item.clone());
                    player.sendMessage(TextUtil.component("&6Hollow Reliquary &8» &f" + nameOf(item)));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
            } else if (share != null) {
                Location drop = at(0, 0.6, 0);
                share.forEach(item -> world.dropItemNaturally(drop, item.clone()));
            }
            ItemDisplay spin = spinnerDisplay();
            if (spin != null) {
                spin.setTransformation(spinPose(0f, 0.55f * SPINNER_SCALE));
            }
            setLid(0.0, 0.3);
            slamPending = true;
            if (claimed.containsAll(shares.keySet())) {
                setLabel(TextUtil.component("&8Spent"));
                spentAt = age + 14;
                dissolveAt = age + 140;
            } else {
                setLabel(labelText());
            }
        }

        private int remaining() {
            int left = 0;
            for (UUID playerId : shares.keySet()) {
                if (!claimed.contains(playerId)) {
                    left++;
                }
            }
            return left;
        }

        private Component labelText() {
            int left = remaining();
            String sub = left == 1 ? "1 share left · right-click" : left + " shares left · right-click";
            return TextUtil.component("&6&l✦ Hollow Reliquary ✦").append(Component.newline())
                    .append(TextUtil.component("&7" + sub));
        }

        private void setLabel(Component text) {
            if (label == null) {
                return;
            }
            Entity entity = Bukkit.getEntity(label);
            if (entity instanceof TextDisplay display) {
                display.text(text);
            }
        }

        private ItemDisplay spinnerDisplay() {
            if (spinner == null) {
                return null;
            }
            Entity entity = Bukkit.getEntity(spinner);
            return entity instanceof ItemDisplay display && display.isValid() ? display : null;
        }

        private List<ItemStack> showcase() {
            List<ItemStack> pool = new ArrayList<>();
            Set<Material> seen = new HashSet<>();
            for (List<ItemStack> share : shares.values()) {
                for (ItemStack item : share) {
                    if (pool.size() < 8 && seen.add(item.getType())) {
                        pool.add(item.clone());
                    }
                }
            }
            if (pool.isEmpty()) {
                pool.add(new ItemStack(Material.NETHER_STAR));
            }
            return pool;
        }

        /** The share's headline item: a nether star (the killer bonus) wins, else the rarest-looking stack. */
        private static ItemStack best(List<ItemStack> share) {
            ItemStack best = null;
            int bestScore = Integer.MIN_VALUE;
            for (ItemStack item : share) {
                int score = item.getType() == Material.NETHER_STAR ? 100 : 0;
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    score += 40;
                }
                score -= item.getAmount();
                if (score > bestScore) {
                    bestScore = score;
                    best = item;
                }
            }
            return best;
        }

        private void announce() {
            World world = world();
            if (world == null) {
                return;
            }
            Set<UUID> told = new HashSet<>();
            for (UUID playerId : shares.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && told.add(playerId)) {
                    player.sendMessage(TextUtil.component(
                            "&6Hollow Reliquary &8» &7Your share waits at " + bossName + "&7's anchor. &eRight-click it."));
                }
            }
            for (Player player : viewers(world, 56.0)) {
                if (told.add(player.getUniqueId())) {
                    player.sendMessage(TextUtil.component("&8A reliquary fell where " + bossName + " &8stood."));
                }
            }
        }

        private void shout(String message) {
            World world = world();
            if (world == null) {
                return;
            }
            for (Player player : viewers(world, 56.0)) {
                player.sendMessage(TextUtil.component(message));
            }
        }

        private List<Player> viewers(World world, double radius) {
            List<Player> near = new ArrayList<>();
            double r2 = radius * radius;
            for (Player player : world.getPlayers()) {
                Location loc = player.getLocation();
                double dx = loc.getX() - baseX;
                double dy = loc.getY() - baseY;
                double dz = loc.getZ() - baseZ;
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    near.add(player);
                }
            }
            return near;
        }

        // --- spent / dissolve

        private void becomeSpent(World world) {
            spent = true;
            Location seam = at(0, 0.93, 0);
            world.playSound(seam, Sound.BLOCK_VAULT_DEACTIVATE, 0.9f, 0.8f);
            world.playSound(seam, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.7f);
            world.spawnParticle(Particle.SMOKE, seam, 14, 0.45, 0.05, 0.4, 0.01);
            Iterator<Part> it = parts.iterator();
            while (it.hasNext()) {
                Part part = it.next();
                Display display = part.resolve();
                switch (part.role) {
                    case GLOW -> {
                        if (display instanceof BlockDisplay block) {
                            block.setBlock(Material.COAL_BLOCK.createBlockData());
                            block.setBrightness(null);
                        }
                    }
                    case BEAM, STAR, CROWN -> {
                        if (display != null) {
                            display.remove();
                        }
                        it.remove();
                    }
                    case ORBIT -> {
                        if (display != null) {
                            ease(display, pose(part), 12);
                            display.setBrightness(null);
                        }
                    }
                    default -> {
                    }
                }
            }
            world.spawnParticle(Particle.SMOKE, at(0, SPINNER_Y + 1.0, 0), 12, 0.1, 0.8, 0.1, 0.01);
        }

        /** The husk crumbles to ash, so nothing of the prop is left standing in the arena. */
        private void dissolve(World world) {
            Location base = at(0, 0.5, 0);
            world.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 1.1f, 0.6f);
            world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.6f);
            world.spawnParticle(Particle.WHITE_ASH, base, 60, 1.1, 0.8, 1.1, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, base, 24, 0.9, 0.5, 0.9, 0.02);
            world.spawnParticle(Particle.BLOCK, base, 40, 1.0, 0.5, 1.0, 0, floorData(world));
        }

        // --- transient fx

        private Temp temp(World world, Material material, Transformation start, int ttl) {
            BlockDisplay display = world.spawn(at(0, 0, 0), BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setTransformation(start);
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setShadowRadius(0f);
                spawned.setViewRange(3.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(PROP_TAG);
            });
            Temp temp = new Temp(display, ttl);
            temps.add(temp);
            return temp;
        }

        private void tickTemps() {
            Iterator<Temp> it = temps.iterator();
            while (it.hasNext()) {
                Temp temp = it.next();
                temp.ttl--;
                if (temp.pending != null) {
                    // Applied a tick later so the client has seen the start pose before interpolating.
                    ease(temp.display, temp.pending, temp.pendingTicks);
                    temp.pending = null;
                }
                if (temp.ttl <= 0 || temp.display == null || !temp.display.isValid()) {
                    if (temp.display != null && temp.display.isValid()) {
                        temp.display.remove();
                    }
                    it.remove();
                }
            }
        }

        private void spark(World world, Location from, Material material, Vector vel, float size, int life) {
            BlockDisplay display = world.spawn(from, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setTeleportDuration(1);
                spawned.setInterpolationDuration(1);
                spawned.setTransformation(centered(new Quaternionf(), size));
                spawned.setShadowRadius(0f);
                spawned.setViewRange(2.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(PROP_TAG);
            });
            sparks.add(new Spark(display, from.clone(), vel.clone(), size, life));
        }

        private void tickSparks(World world) {
            Iterator<Spark> it = sparks.iterator();
            while (it.hasNext()) {
                Spark spark = it.next();
                spark.life--;
                if (spark.life <= 0 || spark.display == null || !spark.display.isValid()) {
                    if (spark.display != null && spark.display.isValid()) {
                        spark.display.remove();
                    }
                    it.remove();
                    continue;
                }
                spark.pos.add(spark.vel);
                spark.vel.setY(spark.vel.getY() - 0.045);
                spark.vel.multiply(0.97);
                if (spark.pos.getY() < baseY + 0.12) {
                    spark.pos.setY(baseY + 0.12);
                    spark.vel.setY(Math.abs(spark.vel.getY()) * 0.3);
                    spark.vel.multiply(0.55);
                }
                spark.spin += 0.34f;
                float size = spark.size * Math.min(1f, spark.life / 8f);
                spark.display.teleport(spark.pos);
                ease(spark.display, centered(new Quaternionf().rotateY(spark.spin).rotateX(spark.spin * 0.8f), size), 1);
                if (spark.life % 3 == 0) {
                    world.spawnParticle(Particle.SMALL_FLAME, spark.pos, 1, 0.03, 0.03, 0.03, 0.005);
                }
            }
        }

        private BlockData floorData(World world) {
            Block below = world.getBlockAt((int) Math.floor(baseX), (int) Math.floor(baseY) - 1, (int) Math.floor(baseZ));
            return below.getType().isSolid() ? below.getBlockData() : Material.DEEPSLATE_TILES.createBlockData();
        }
    }

    // ------------------------------------------------------------------ part / temp / spark

    private static final class Part {
        private final Role role;
        private final Material material;
        private final ItemStack item;
        private final Transformation home;
        private final double lift;
        private UUID id;
        private Display cached;
        private Transformation hidden;
        private int appearAt;
        private int appearTicks = 6;
        private boolean shown;
        private boolean glow;
        private float phase;

        private Part(Role role, Material material, ItemStack item, Transformation home, double lift) {
            this.role = role;
            this.material = material;
            this.item = item;
            this.home = home;
            this.lift = lift;
            this.hidden = shrink(home, 0.001, 0, 0, 0);
        }

        private Display resolve() {
            if (cached != null && cached.isValid()) {
                return cached;
            }
            if (id == null) {
                return null;
            }
            Entity entity = Bukkit.getEntity(id);
            cached = entity instanceof Display display ? display : null;
            return cached;
        }

        /** Hidden pose = home shrunk around its centre by {@code scale} and moved by (dx, dy, dz). */
        private void appear(int at, int ticks, double dx, double dy, double dz, double scale) {
            appearAt = at;
            appearTicks = Math.max(1, ticks);
            hidden = shrink(home, Math.max(0.001, scale), dx, dy, dz);
        }

        /** Starts far out along its own radial direction, then flies to its orbit slot. */
        private void flyIn(int at, int ticks, double distance, double up) {
            Vector3f t = home.getTranslation();
            double len = Math.sqrt(t.x * t.x + t.z * t.z);
            double ox = len < 1.0E-3 ? 0 : t.x / len * distance;
            double oz = len < 1.0E-3 ? 0 : t.z / len * distance;
            appear(at, ticks, ox, up, oz, 0.3);
        }

        /** Grows from its base upward. */
        private void growUp(int at, int ticks) {
            appearAt = at;
            appearTicks = Math.max(1, ticks);
            Vector3f s = home.getScale();
            hidden = new Transformation(new Vector3f(home.getTranslation()), home.getLeftRotation(),
                    new Vector3f(s.x, 0.001f, s.z), home.getRightRotation());
        }
    }

    private static final class Temp {
        private final BlockDisplay display;
        private int ttl;
        private boolean sky = true;
        private Transformation pending;
        private int pendingTicks;

        private Temp(BlockDisplay display, int ttl) {
            this.display = display;
            this.ttl = ttl;
        }

        private void then(Transformation next, int ticks) {
            pending = next;
            pendingTicks = Math.max(1, ticks);
        }
    }

    private static final class Spark {
        private final BlockDisplay display;
        private final Location pos;
        private final Vector vel;
        private final float size;
        private int life;
        private float spin;

        private Spark(BlockDisplay display, Location pos, Vector vel, float size, int life) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.size = size;
            this.life = life;
        }
    }

    // ------------------------------------------------------------------ math & fx helpers

    private static Transformation column(float width, float height, float from) {
        return new Transformation(new Vector3f(-width / 2f, from, -width / 2f), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf());
    }

    /** A column whose top stays 30 blocks up, so growing its height reads as a star falling in. */
    private static Transformation skyColumn(float width, float height) {
        return column(width, height, 30f - height);
    }

    private static Transformation centered(Quaternionf rotation, float size) {
        Vector3f offset = rotation.transform(new Vector3f(-size / 2f, -size / 2f, -size / 2f));
        return new Transformation(offset, new Quaternionf(rotation), new Vector3f(size, size, size), new Quaternionf());
    }

    private static Transformation spinPose(float yawDeg, float scale) {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new org.joml.AxisAngle4f((float) Math.toRadians(yawDeg), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new org.joml.AxisAngle4f());
    }

    private static Transformation shrink(Transformation home, double factor, double dx, double dy, double dz) {
        Quaternionf left = home.getLeftRotation();
        Vector3f scale = home.getScale();
        Vector3f center = new Vector3f(home.getTranslation())
                .add(new Quaternionf(left).transform(new Vector3f(scale).mul(0.5f)));
        Vector3f small = new Vector3f(scale).mul((float) factor);
        Vector3f t = center.sub(new Quaternionf(left).transform(new Vector3f(small).mul(0.5f)))
                .add((float) dx, (float) dy, (float) dz);
        return new Transformation(t, new Quaternionf(left), small, new Quaternionf(home.getRightRotation()));
    }

    /** Rotates a part about the lid hinge (local X axis). Negative angles open the lid backwards. */
    private static Transformation hinged(Transformation home, Vector3f hinge, double angle) {
        Quaternionf r = new Quaternionf().rotateX((float) angle);
        Vector3f rel = new Vector3f(home.getTranslation()).sub(hinge);
        Vector3f t = r.transform(rel).add(hinge);
        Quaternionf left = new Quaternionf(r).mul(home.getLeftRotation());
        return new Transformation(t, left, new Vector3f(home.getScale()), new Quaternionf(home.getRightRotation()));
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static void ringParticles(World world, Location center, double radius, Color color, float size, double fill) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(12, (int) Math.ceil(Math.PI * 2 * radius / 0.3));
        int drawn = (int) Math.ceil(points * Math.max(0.0, Math.min(1.0, fill)));
        for (int i = 0; i < drawn; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    /** Front faces the nearest player, snapped to the block grid. */
    private static float facingYaw(Location base) {
        World world = base.getWorld();
        Player best = null;
        double bestDist = 64.0 * 64.0;
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(base);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        if (best == null) {
            return 0f;
        }
        double dx = best.getLocation().getX() - base.getX();
        double dz = best.getLocation().getZ() - base.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return 0f;
        }
        return wrap(Math.round((float) Math.toDegrees(Math.atan2(-dx, dz)) / 90f) * 90f);
    }

    /** Snaps the anchor down onto the first solid floor so the dais never floats. */
    private static Location ground(Location anchor) {
        World world = anchor.getWorld();
        int x = anchor.getBlockX();
        int z = anchor.getBlockZ();
        int from = anchor.getBlockY() + 3;
        for (int y = from; y > from - 20; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !world.getBlockAt(x, y + 1, z).getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, anchor.getBlockY(), z + 0.5);
    }

    private static String nameOf(ItemStack item) {
        if (item == null) {
            return "nothing";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName() + (item.getAmount() > 1 ? " &7x" + item.getAmount() : "");
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                + (item.getAmount() > 1 ? " &7x" + item.getAmount() : "");
    }

    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
