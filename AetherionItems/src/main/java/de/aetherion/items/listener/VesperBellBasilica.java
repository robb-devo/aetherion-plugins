package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vesper Bell (vesper_bell) — Basilica of the Last Hymn.
 *
 * <p>One toll and a cathedral builds itself around the caster. The floor lays itself in a spiral of
 * turning flagstones, eight fluted columns ratchet up out of the ground, pointed arches close between
 * them, gold ribs race each other to a keystone and a stained-glass oculus blooms overhead like a
 * flower. Light pours through it and paints ruby and cobalt on the floor — and the painted light turns
 * with the window.
 *
 * <p>Four bells toll the hymn. Every toll the floor breathes, allies inside are healed and consecrated
 * (+35% damage), enemies are nailed by stakes of light. Then the hymn stops mid-swing. Gold threads bind
 * every enemy to the window and the whole basilica lifts off its floor and ascends, turning, taking them
 * with it. It hangs in silence, turns to glass from the top down, and shatters in slow motion — casting
 * the bound back to the earth. The floor folds itself away. One distant bell.
 */
public final class VesperBellBasilica {

    /* ---------------------------------------------------------------- timeline (ticks) */
    private static final int T_FLOOR = 2;
    private static final int T_PILLARS = 16;
    private static final int PILLAR_STAGGER = 2;
    private static final int PILLAR_RISE = 19;
    private static final int ARCH_GROW = 10;
    private static final int T_KEYSTONE = 64;
    private static final int T_BLOOM = 67;
    private static final int T_LIGHT = 72;
    private static final int LIGHT_FADE = 12;
    private static final int T_HYMN = 80;
    private static final int[] TOLLS = {86, 102, 118, 134};
    private static final int T_STILL = 148;
    private static final int T_RAPTURE = 158;
    private static final int T_HANG = 184;
    private static final int T_GLASS = 192;
    private static final int T_SHATTER = 197;
    private static final int T_FOLD = 203;
    private static final int T_ECHO = 232;
    private static final int TOTAL = 242;

    /* ---------------------------------------------------------------- architecture */
    private static final double R_PILLAR = 7.4;
    private static final float H_CAP = 6.1f;
    private static final float H_APEX = 13.4f;
    private static final float ARCH_RISE = 2.6f;
    private static final double R_OCULUS = 3.0;
    private static final double R_ZONE = 9.6;
    private static final float RAPTURE_LIFT = 15f;
    private static final float RAPTURE_TURN = 55f;
    private static final int PILLARS = 8;
    private static final int RIB_SEGS = 6;
    private static final float RIB_BOW = 1.25f;
    private static final int PETALS = 8;
    private static final int RIM_SEGS = 12;
    private static final double[][] BANDS = {{1.3, 3.7}, {3.7, 6.2}, {6.2, 8.8}};
    private static final int[] BAND_SEGS = {10, 16, 22};
    private static final double[] INLAY_R = {3.7, 8.8};
    private static final int[] INLAY_SEGS = {12, 20};
    private static final int MAX_LIVE = 380;
    private static final double AMP = 1.35;

    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final Color WARM = Color.fromRGB(255, 236, 184);
    private static final Color RUBY = Color.fromRGB(255, 58, 84);
    private static final Color COBALT = Color.fromRGB(64, 116, 255);
    private static final Color IVORY = Color.fromRGB(255, 250, 238);
    private static final Color WHITE = Color.fromRGB(255, 255, 255);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final float HALF_PI = (float) (Math.PI / 2);

    private static final Material[] CHAPEL_GLASS = {
            Material.WHITE_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.WHITE_STAINED_GLASS
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    /** Consecrated players → server tick their +35% damage expires. */
    private static final Map<UUID, Long> CONSECRATED = new ConcurrentHashMap<>();
    /** Enemies we switched gravity off for — restored on shatter, abort and shutdown. */
    private static final Set<LivingEntity> SUSPENDED = ConcurrentHashMap.newKeySet();
    private static Listener amplifier;

    private final JavaPlugin plugin;
    private final Player caster;
    private final World world;
    private final double damage;
    private final Runnable done;

    private Location anchor;
    private double face;
    private boolean crowded;
    private int t;
    private boolean released;

    private final List<Tile> tiles = new ArrayList<>();
    private final List<Piece> inlay = new ArrayList<>();
    private final List<double[]> inlayMeta = new ArrayList<>();
    private final List<Piece> medallion = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    private final List<Rib> ribs = new ArrayList<>();
    private final List<Piece> rim = new ArrayList<>();
    private final List<Piece> boss = new ArrayList<>();
    private final List<Piece> petals = new ArrayList<>();
    private final List<Piece> spokes = new ArrayList<>();
    private final List<Hanger> bells = new ArrayList<>();
    private final List<Hanger> lanterns = new ArrayList<>();
    /** Everything that ascends, turns to glass and shatters. */
    private final List<Piece> arch = new ArrayList<>();
    private final List<Quad> rays = new ArrayList<>();
    private final List<Quad> shafts = new ArrayList<>();
    private final List<Quad> pools = new ArrayList<>();
    private final List<Stake> stakes = new ArrayList<>();
    private final List<Bound> bound = new ArrayList<>();
    private final List<Fallen> fallen = new ArrayList<>();
    private final List<Integer> waves = new ArrayList<>();

    private float windowSpin;
    private float medallionSpin;
    private float shaftSpin;
    private float lightPulse;
    private float lift;
    private float turn;

    // ------------------------------------------------------------------ lifecycle

    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
        for (LivingEntity living : SUSPENDED) {
            if (living != null && living.isValid()) {
                living.setGravity(true);
            }
        }
        SUSPENDED.clear();
        CONSECRATED.clear();
    }

    static void cast(JavaPlugin plugin, Player player, double damage, Runnable done) {
        ensureAmplifier(plugin);
        new VesperBellBasilica(plugin, player, damage, done).start();
    }

    private VesperBellBasilica(JavaPlugin plugin, Player caster, double damage, Runnable done) {
        this.plugin = plugin;
        this.caster = caster;
        this.world = caster.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        anchor = floorUnder(caster.getLocation());
        Vector look = caster.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        face = Math.atan2(look.getZ(), look.getX());
        crowded = LIVE.size() > MAX_LIVE - 300;

        world.playSound(anchor, Sound.BLOCK_BELL_USE, 2.2f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_BELL_RESONATE, 1.4f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.55f);
        world.playSound(anchor, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        caster.sendMessage("§6✦ Vesper Bell §7— the first toll.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || caster.getWorld() != world) {
                    clearAll();
                    cancel();
                    return;
                }
                boolean finished;
                try {
                    finished = step();
                } catch (Throwable error) {
                    plugin.getLogger().warning("[VesperBell] timeline aborted: " + error);
                    finished = true;
                }
                if (finished) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    private boolean step() {
        t++;
        if (t == 1) {
            stageFloor();
        }
        tickFloor();
        tickInlay();
        tickMedallion();

        if (t == T_PILLARS - 1) {
            stagePillars();
        }
        tickPillars();

        if (t == T_PILLARS + PILLAR_RISE - 4) {
            stageVault();
        }
        tickSpans();
        tickRibs();

        if (t == T_KEYSTONE - 2) {
            stageOculus();
        }
        if (t == T_KEYSTONE) {
            keystone();
        }
        tickOculus();
        tickBells();
        tickLanterns();

        if (t == T_LIGHT - 1) {
            stageLight();
        }
        tickLight();

        for (int k = 0; k < TOLLS.length; k++) {
            if (t == TOLLS[k]) {
                toll(k);
            }
        }
        tickStakes();
        hymnAmbience();

        if (t == T_STILL) {
            still();
        }
        if (t >= T_RAPTURE && t <= T_HANG) {
            rapture();
        }
        tickBound();
        if (t == T_HANG) {
            hang();
        }
        if (t >= T_GLASS && t < T_GLASS + 4) {
            glassSweep(t - T_GLASS);
        }
        if (t == T_SHATTER) {
            shatter();
        }
        if (t > T_SHATTER) {
            tickShards();
        }
        tickFallen();
        if (t >= T_FOLD) {
            fold();
        }
        if (t == T_ECHO) {
            echo();
        }
        return t >= TOTAL;
    }

    // ------------------------------------------------------------------ act I — the floor lays itself

    private void stageFloor() {
        for (int band = 0; band < BANDS.length; band++) {
            int segs = crowded ? BAND_SEGS[band] / 2 + 2 : BAND_SEGS[band];
            double r0 = BANDS[band][0];
            double r1 = BANDS[band][1];
            float width = (float) (2 * r1 * Math.sin(Math.PI / segs) * 1.03);
            for (int s = 0; s < segs; s++) {
                double a = face + Math.PI * 2 * s / segs + (band % 2) * Math.PI / segs;
                boolean dark = (s + band) % 2 == 0;
                int start = T_FLOOR + band * 6 + (int) Math.round(9.0 * s / segs);
                Tile tile = new Tile(a, (float) r0 + 0.03f, (float) r1 - 0.03f, width,
                        dark ? 0.012f : 0.017f, start, band, s, segs);
                Material mat = dark ? Material.POLISHED_DEEPSLATE : Material.CALCITE;
                tile.display = spawnBlock(mat.createBlockData(), tile.pose(HALF_PI, 0f, 0.001f), null);
                tiles.add(tile);
            }
        }

        for (int ring = 0; ring < INLAY_R.length; ring++) {
            int segs = crowded ? INLAY_SEGS[ring] / 2 + 2 : INLAY_SEGS[ring];
            for (int s = 0; s < segs; s++) {
                double a = face + Math.PI * 2 * s / segs;
                BlockDisplay display = spawnBlock(Material.GOLD_BLOCK.createBlockData(),
                        arcSeg(a, 0.3, 0.08f, 0.16f, 0.05f, 0.05f), GOLD);
                Piece piece = new Piece(display, null);
                inlay.add(piece);
                inlayMeta.add(new double[]{ring, a, segs});
            }
        }

        medallion.add(new Piece(spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, 0.05f, 0), null), null));
        medallion.add(new Piece(spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, 0.05f, 0), null), null));
        medallion.add(new Piece(spawnBlock(Material.CHISELED_QUARTZ_BLOCK.createBlockData(), tinyAt(0, 0.07f, 0), null), null));
    }

    private void tickFloor() {
        if (t > T_FOLD) {
            return;
        }
        boolean clack = false;
        for (Tile tile : tiles) {
            if (t == tile.start) {
                ease(tile.display, tile.pose(HALF_PI, 0f, 1f), 2);
            } else if (t == tile.start + 2) {
                ease(tile.display, tile.pose(0f, 0f, 1f), 5);
            } else if (t == tile.start + 7) {
                clack = true;
            }
        }
        if (clack) {
            float pitch = 0.6f + Math.min(1f, t / 30f) * 0.9f;
            world.playSound(anchor, Sound.BLOCK_DEEPSLATE_TILES_PLACE, 0.55f, pitch);
            world.playSound(anchor, Sound.BLOCK_CALCITE_PLACE, 0.35f, pitch * 1.1f);
        }
        if (t == T_FLOOR + 12 + 9 + 7) {
            world.playSound(anchor, Sound.BLOCK_LODESTONE_PLACE, 0.9f, 0.6f);
        }

        /* The floor breathes with each toll: a swell runs outward under the flagstones. */
        boolean active = false;
        for (int wave : waves) {
            if (t - wave >= 0 && t - wave <= 17) {
                active = true;
                break;
            }
        }
        if (!active) {
            return;
        }
        for (Tile tile : tiles) {
            if (t < tile.start + 8 || tile.foldAt >= 0) {
                continue;
            }
            float mid = (tile.r0 + tile.r1) * 0.5f;
            float swell = 0f;
            for (int wave : waves) {
                int dt = t - wave;
                if (dt < 0 || dt > 16) {
                    continue;
                }
                double front = dt * 0.72;
                double d = mid - front;
                swell += (float) (0.32 * Math.exp(-(d * d) / 0.9) * (1.0 - dt / 17.0));
            }
            ease(tile.display, tile.pose(0f, swell, 1f), 2);
        }
    }

    /** The first toll's shockwave draws the sanctum's gold borders outward from the caster. */
    private void tickInlay() {
        if (t > 18 || t >= T_FOLD) {
            return;
        }
        double k = easeOutCubic(Math.min(1.0, (t - 1) / 15.0));
        for (int i = 0; i < inlay.size(); i++) {
            double[] meta = inlayMeta.get(i);
            double target = INLAY_R[(int) meta[0]];
            double radius = 0.3 + (target - 0.3) * k;
            float len = (float) (Math.PI * 2 * radius / meta[2] * 1.06);
            pose(inlay.get(i), arcSeg(meta[1], radius, 0.08f, 0.16f, 0.05f, Math.max(0.05f, len)), 2);
        }
        if (t % 2 == 1) {
            drawRing(anchor, 0.3 + 8.5 * k, 48, new Particle.DustOptions(GOLD, 1.3f), t * 0.1);
        }
        if (t == 18) {
            for (Piece piece : inlay) {
                if (piece.display != null && piece.display.isValid()) {
                    piece.display.setGlowing(false);
                }
            }
        }
    }

    private void tickMedallion() {
        if (medallion.isEmpty() || t >= T_ECHO - 3) {
            return;
        }
        float spin = medallionSpin;
        if (t > T_LIGHT && t < T_SHATTER) {
            medallionSpin += 0.02f;
        }
        if (t == 1 || t > T_LIGHT && t < T_SHATTER && t % 2 == 0) {
            int ticks = t == 1 ? 4 : 2;
            pose(medallion.get(0), centered(new Vector3f(0, 0.05f, 0), new Quaternionf().rotateY(spin),
                    new Vector3f(2.2f, 0.07f, 2.2f)), ticks);
            pose(medallion.get(1), centered(new Vector3f(0, 0.05f, 0), new Quaternionf().rotateY(spin + 0.785f),
                    new Vector3f(2.2f, 0.07f, 2.2f)), ticks);
            pose(medallion.get(2), centered(new Vector3f(0, 0.075f, 0), new Quaternionf().rotateY(-spin * 1.5f + 0.39f),
                    new Vector3f(1.2f, 0.08f, 1.2f)), ticks);
        }
    }

    // ------------------------------------------------------------------ act II — the raising

    private void stagePillars() {
        float sunk = -(H_CAP + 0.4f);
        for (int i = 0; i < PILLARS; i++) {
            double a = pillarAngle(i);
            Column column = new Column(a, T_PILLARS + i * PILLAR_STAGGER, radial(a, R_PILLAR, 0), yawQ(a));
            column.add(Material.POLISHED_DEEPSLATE, 0.35f, 1.55f, 0.7f, 1.55f, Material.GRAY_STAINED_GLASS);
            for (int d = 0; d < 3; d++) {
                column.add(Material.QUARTZ_PILLAR, 0.7f + 1.55f * d + 0.775f, 0.95f, 1.55f, 0.95f,
                        CHAPEL_GLASS[(i + d) % CHAPEL_GLASS.length]);
            }
            column.add(Material.GOLD_BLOCK, 3.02f, 1.03f, 0.13f, 1.03f, Material.YELLOW_STAINED_GLASS);
            column.add(Material.CHISELED_QUARTZ_BLOCK, 5.625f, 1.3f, 0.55f, 1.3f, Material.WHITE_STAINED_GLASS);
            column.add(Material.GOLD_BLOCK, 6.0f, 1.46f, 0.2f, 1.46f, Material.YELLOW_STAINED_GLASS);
            column.pose(sunk, 0);
            columns.add(column);
        }
    }

    private void tickPillars() {
        float sunk = -(H_CAP + 0.4f);
        for (Column column : columns) {
            int lt = t - column.start;
            if (lt < 0 || lt > PILLAR_RISE) {
                continue;
            }
            double progress = ratchet(lt);
            column.pose((float) (sunk * (1.0 - progress)), 1);
            Location base = worldAt(column.base);
            if (lt == 0 || lt == 7 || lt == 14) {
                world.playSound(base, Sound.BLOCK_GRINDSTONE_USE, 0.55f, 0.45f);
                world.playSound(base, Sound.BLOCK_PISTON_EXTEND, 0.35f, 0.5f);
            }
            if (lt < 5 || lt >= 7 && lt < 12 || lt >= 14 && lt < 19) {
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.15, 0), 5, 0.6, 0.05, 0.6, 0,
                        Material.CALCITE.createBlockData());
            }
            if (lt == 5 || lt == 12) {
                world.playSound(base, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, 0.9f, 0.5f);
            }
            if (lt == PILLAR_RISE) {
                world.playSound(base, Sound.ITEM_MACE_SMASH_GROUND, 0.7f, 0.55f);
                world.playSound(base, Sound.BLOCK_ANVIL_LAND, 0.25f, 0.5f);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(0, 0.2, 0), 3, 0.7, 0.05, 0.7, 0.005);
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.2, 0), 18, 0.8, 0.1, 0.8, 0,
                        Material.CALCITE.createBlockData());
                hangLantern(column);
            }
        }
    }

    /** Stop-motion extrusion: three ratchet strokes with holds, the last one landing with a hair of overshoot. */
    private static double ratchet(int lt) {
        if (lt < 5) {
            return easeOutCubic(lt / 5.0) / 3.0;
        }
        if (lt < 7) {
            return 1.0 / 3.0;
        }
        if (lt < 12) {
            return 1.0 / 3.0 + easeOutCubic((lt - 7) / 5.0) / 3.0;
        }
        if (lt < 14) {
            return 2.0 / 3.0;
        }
        if (lt < 19) {
            return 2.0 / 3.0 + easeOutBack((lt - 14) / 5.0) / 3.0;
        }
        return 1.0;
    }

    private void hangLantern(Column column) {
        double a = column.angle;
        Vector3f hang = radial(a, R_PILLAR - 0.95, H_CAP - 0.55);
        BlockData data = Material.LANTERN.createBlockData("[hanging=true]");
        BlockDisplay display = spawnBlock(data, tinyAt(hang.x, hang.y, hang.z), null);
        Piece piece = new Piece(display, null);
        Hanger lantern = new Hanger(piece, hang, a, 0.85f, t);
        lanterns.add(lantern);
        arch.add(piece);
        world.playSound(worldAt(hang), Sound.BLOCK_LANTERN_PLACE, 0.6f, 0.8f);
    }

    private void tickLanterns() {
        if (t >= T_SHATTER) {
            return;
        }
        for (Hanger lantern : lanterns) {
            int age = t - lantern.born;
            float grow = (float) easeOutBack(Math.min(1.0, age / 5.0));
            float theta = 0.12f * (float) Math.sin(t * 0.14 + lantern.angle * 3.0);
            pose(lantern.piece, hanging(lantern.hang, lantern.angle, theta, lantern.scale * Math.max(0.001f, grow)), 2);
        }
    }

    private void stageVault() {
        if (!crowded) {
            for (int i = 0; i < PILLARS; i++) {
                int j = (i + 1) % PILLARS;
                int start = Math.max(pillarDone(i), pillarDone(j)) + 1;
                Vector3f a = radial(pillarAngle(i), R_PILLAR + 0.02, H_CAP - 0.04);
                Vector3f b = radial(pillarAngle(j), R_PILLAR + 0.02, H_CAP - 0.04);
                Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
                Vector3f out = new Vector3f(mid.x, 0, mid.z).normalize();
                Span span = new Span(a, b, out, start);
                for (int k = 0; k < 4; k++) {
                    BlockDisplay display = spawnBlock(Material.SMOOTH_QUARTZ.createBlockData(), tinyAt(a.x, a.y, a.z), null);
                    span.segs[k] = new Piece(display, CHAPEL_GLASS[(i + k) % CHAPEL_GLASS.length]);
                    arch.add(span.segs[k]);
                }
                spans.add(span);
            }
        }
        for (int i = 0; i < PILLARS; i++) {
            double a = pillarAngle(i);
            int start = T_PILLARS + i * PILLAR_STAGGER + PILLAR_RISE - 1;
            Rib rib = new Rib(a, start);
            Vector3f root = ribPoint(a, 0.0);
            for (int k = 0; k < RIB_SEGS; k++) {
                BlockDisplay display = spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(root.x, root.y, root.z), null);
                rib.segs[k] = new Piece(display, k % 2 == 0 ? Material.YELLOW_STAINED_GLASS : Material.ORANGE_STAINED_GLASS);
                arch.add(rib.segs[k]);
            }
            ribs.add(rib);
        }
    }

    private void tickSpans() {
        for (Span span : spans) {
            int lt = t - span.start;
            if (lt < 0 || lt > ARCH_GROW) {
                continue;
            }
            double g = easeInOutSine(lt / (double) ARCH_GROW);
            double leftTip = 0.5 * g;
            double rightTip = 1.0 - 0.5 * g;
            double[][] from = {{0.0, 0.25}, {0.25, 0.5}, {1.0, 0.75}, {0.75, 0.5}};
            for (int k = 0; k < 4; k++) {
                double s0 = from[k][0];
                double s1 = from[k][1];
                double end;
                if (k < 2) {
                    end = Math.min(s1, leftTip);
                    if (end <= s0 + 1.0E-3) {
                        continue;
                    }
                } else {
                    end = Math.max(s1, rightTip);
                    if (end >= s0 - 1.0E-3) {
                        continue;
                    }
                }
                Vector3f p0 = archPoint(span, s0);
                Vector3f p1 = archPoint(span, end);
                pose(span.segs[k], rod(p0, p1, span.out, 0.62f, 0.42f, 0.22f), 1);
            }
            if (lt == ARCH_GROW && !span.clicked) {
                span.clicked = true;
                Location top = worldAt(archPoint(span, 0.5));
                world.playSound(top, Sound.BLOCK_LODESTONE_PLACE, 0.7f, 1.5f);
                world.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.2f);
                world.spawnParticle(Particle.END_ROD, top, 6, 0.15, 0.15, 0.15, 0.03);
            }
        }
    }

    private void tickRibs() {
        for (int r = 0; r < ribs.size(); r++) {
            Rib rib = ribs.get(r);
            if (t < rib.start || t > T_KEYSTONE) {
                continue;
            }
            double tip = easeInOutSine((t - rib.start) / (double) Math.max(1, T_KEYSTONE - rib.start));
            Vector3f tangent = new Vector3f((float) -Math.sin(rib.angle), 0f, (float) Math.cos(rib.angle));
            for (int k = 0; k < RIB_SEGS; k++) {
                double s0 = k / (double) RIB_SEGS;
                double s1 = (k + 1) / (double) RIB_SEGS;
                if (tip <= s0 + 1.0E-3) {
                    continue;
                }
                Vector3f p0 = ribPoint(rib.angle, s0);
                Vector3f p1 = ribPoint(rib.angle, Math.min(s1, tip));
                pose(rib.segs[k], rod(p0, p1, tangent, 0.3f, 0.44f, 0.16f), 1);
            }
        }
        if (t > T_PILLARS + PILLAR_RISE && t < T_KEYSTONE && t % 3 == 0) {
            float k = (t - T_PILLARS - PILLAR_RISE) / (float) (T_KEYSTONE - T_PILLARS - PILLAR_RISE);
            Location high = anchor.clone().add(0, 4 + k * 8, 0);
            world.playSound(high, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.45f, 0.6f + k * 1.2f);
        }
    }

    private int pillarDone(int i) {
        return T_PILLARS + i * PILLAR_STAGGER + PILLAR_RISE;
    }

    private Vector3f archPoint(Span span, double s) {
        Vector3f p = new Vector3f(span.a).lerp(span.b, (float) s);
        p.y += ARCH_RISE * (float) (1.0 - Math.pow(Math.abs(2.0 * s - 1.0), 1.5));
        return p;
    }

    private static Vector3f ribPoint(double angle, double s) {
        double r = (R_PILLAR - 0.3) + ((R_OCULUS + 0.2) - (R_PILLAR - 0.3)) * s;
        double h0 = H_CAP + 0.05;
        double h1 = H_APEX - 0.2;
        double y = h0 + (h1 - h0) * Math.sin(s * RIB_BOW) / Math.sin(RIB_BOW);
        return radial(angle, r, y);
    }

    // ------------------------------------------------------------------ keystone + oculus

    private void stageOculus() {
        for (int s = 0; s < RIM_SEGS; s++) {
            Piece piece = new Piece(spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, H_APEX, 0), GOLD),
                    Material.YELLOW_STAINED_GLASS);
            rim.add(piece);
            arch.add(piece);
        }
        for (int k = 0; k < 2; k++) {
            Piece piece = new Piece(spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, H_APEX, 0), null),
                    Material.YELLOW_STAINED_GLASS);
            boss.add(piece);
            arch.add(piece);
        }
        for (int p = 0; p < PETALS; p++) {
            Material glass = p % 2 == 0 ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS;
            Color glow = p % 2 == 0 ? RUBY : COBALT;
            Piece piece = new Piece(spawnBlock(glass.createBlockData(), tinyAt(0, H_APEX, 0), glow), null);
            petals.add(piece);
            arch.add(piece);
        }
        for (int p = 0; p < PETALS; p++) {
            Piece piece = new Piece(spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, H_APEX, 0), null),
                    Material.YELLOW_STAINED_GLASS);
            spokes.add(piece);
            arch.add(piece);
        }
        for (int b = 0; b < 4; b++) {
            double a = pillarAngle(b * 2 + 1);
            Vector3f hang = ribPoint(a, 0.52);
            hang.y -= 0.32f;
            BlockData data = Material.BELL.createBlockData("[attachment=ceiling]");
            Piece piece = new Piece(spawnBlock(data, tinyAt(hang.x, hang.y, hang.z), null), null);
            bells.add(new Hanger(piece, hang, a, 1.45f, T_KEYSTONE + 4 + b * 2));
            arch.add(piece);
        }
    }

    private void keystone() {
        Location apex = anchor.clone().add(0, H_APEX, 0);
        world.playSound(apex, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.55f);
        world.playSound(apex, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.1f);
        world.playSound(apex, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.0f);
        world.playSound(apex, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);
        world.playSound(anchor, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
        world.spawnParticle(Particle.FLASH, apex, 1, 0, 0, 0, 0);
        burstAt(apex, Particle.END_ROD, 40, 0.35);
        world.spawnParticle(Particle.DUST, apex, 30, 1.2, 0.3, 1.2, 0, new Particle.DustOptions(GOLD, 1.6f));
        caster.sendActionBar(Component.text("§6✦ §eThe vault closes §6✦"));
    }

    private void tickOculus() {
        if (t < T_KEYSTONE || t > T_STILL) {
            return;
        }
        boolean spinning = t >= T_LIGHT && t < T_STILL;
        if (spinning) {
            windowSpin += 0.011f;
        }
        int ticks = t <= T_KEYSTONE + 3 ? 3 : 2;

        if (t <= T_KEYSTONE + 1 || spinning && t % 4 == 0) {
            for (int s = 0; s < rim.size(); s++) {
                double a = face + windowSpin * 0.5 + Math.PI * 2 * s / RIM_SEGS;
                float len = (float) (Math.PI * 2 * R_OCULUS / RIM_SEGS * 1.08);
                pose(rim.get(s), arcSeg(a, R_OCULUS, H_APEX - 0.1f, 0.42f, 0.34f, len), ticks);
            }
            for (int k = 0; k < boss.size(); k++) {
                pose(boss.get(k), centered(new Vector3f(0, H_APEX - 0.06f, 0),
                        new Quaternionf().rotateY(windowSpin + k * 0.785f), new Vector3f(1.5f, 0.34f, 1.5f)), ticks);
            }
        }
        if (t == T_KEYSTONE + 6) {
            for (Piece piece : rim) {
                if (piece.display != null && piece.display.isValid()) {
                    piece.display.setGlowing(false);
                }
            }
        }

        for (int p = 0; p < petals.size(); p++) {
            int lt = t - (T_BLOOM + p);
            if (lt < 0) {
                continue;
            }
            float tilt;
            float grow;
            if (lt <= 2) {
                tilt = -HALF_PI;
                grow = lt / 2f;
            } else if (lt <= 11) {
                tilt = (float) (-HALF_PI + (HALF_PI + 0.12f) * easeOutBack((lt - 2) / 9.0));
                grow = 1f;
            } else if (spinning) {
                tilt = 0.12f;
                grow = 1f;
            } else {
                continue;
            }
            double a = face + windowSpin + Math.PI * 2 * p / PETALS;
            pose(petals.get(p), petal(a, tilt, Math.max(0.001f, grow)), lt <= 11 ? 1 : 2);
            if (lt == 3) {
                world.playSound(anchor.clone().add(0, H_APEX, 0), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f,
                        0.8f + p * 0.12f);
            }
        }
        if (t == T_BLOOM + PETALS + 10) {
            Location apex = anchor.clone().add(0, H_APEX, 0);
            chord(apex, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.707f);
            world.playSound(apex, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
        }

        int spokeStart = T_BLOOM + 8;
        for (int p = 0; p < spokes.size(); p++) {
            int lt = t - spokeStart;
            if (lt < 0 || lt > 5 && !(spinning && t % 2 == 0)) {
                continue;
            }
            double a = face + windowSpin + Math.PI * 2 * (p + 0.5) / PETALS;
            double reach = 0.75 + (R_OCULUS - 0.75) * easeOutCubic(Math.min(1.0, lt / 5.0));
            Vector3f from = radial(a, 0.7, H_APEX - 0.06);
            Vector3f to = radial(a, reach, H_APEX - 0.06);
            Vector3f up = new Vector3f(0, 1, 0);
            pose(spokes.get(p), rod(from, to, up, 0.14f, 0.16f, 0.05f), 2);
        }
    }

    /** A lancet hinged at the boss: tilt −π/2 hangs closed, 0 lies open. */
    private static Transformation petal(double a, float tilt, float grow) {
        float width = 1.12f;
        Quaternionf q = new Quaternionf().rotateY((float) -a).rotateZ(tilt);
        Vector3f v = new Vector3f((float) -Math.sin(a), 0f, (float) Math.cos(a));
        Vector3f corner = radial(a, 0.72, H_APEX - 0.12).add(new Vector3f(v).mul(-width / 2f));
        return new Transformation(corner, q, new Vector3f(2.2f * grow, 0.08f, width), new Quaternionf());
    }

    private void tickBells() {
        if (t >= T_SHATTER) {
            return;
        }
        for (int b = 0; b < bells.size(); b++) {
            Hanger bell = bells.get(b);
            if (t < bell.born) {
                continue;
            }
            int age = t - bell.born;
            float grow = (float) easeOutBack(Math.min(1.0, age / 6.0));
            if (age == 1) {
                world.playSound(worldAt(bell.hang), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.2f + b * 0.15f);
            }
            if (t > T_STILL) {
                continue;
            }
            double swing = 0.0;
            if (t > T_HYMN - 12) {
                double amp = 0.46 * Math.min(1.0, (t - (T_HYMN - 12)) / 16.0);
                double omega = Math.PI * 2 / 32.0;
                swing = amp * Math.sin(omega * (t - TOLLS[b]) + Math.PI / 2);
            }
            bell.theta = (float) swing;
            pose(bell.piece, hanging(bell.hang, bell.angle, bell.theta, bell.scale * Math.max(0.001f, grow)), 2);
        }
    }

    // ------------------------------------------------------------------ light

    private void stageLight() {
        int rayCount = crowded ? 4 : 8;
        for (int k = 0; k < rayCount; k++) {
            rays.add(new Quad(WARM));
        }
        for (int k = 0; k < (crowded ? 2 : 3); k++) {
            shafts.add(new Quad(WHITE));
        }
        for (int p = 0; p < PETALS; p++) {
            pools.add(new Quad(p % 2 == 0 ? RUBY : COBALT));
        }
        pools.add(new Quad(GOLD));
        world.playSound(anchor.clone().add(0, H_APEX, 0), Sound.BLOCK_BEACON_AMBIENT, 1.4f, 1.2f);
        world.playSound(anchor, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.9f);
        caster.sendActionBar(Component.text("§6§l✦ BASILICA OF THE LAST HYMN ✦"));
        caster.sendMessage("§6✦ §eBasilica of the Last Hymn §7— allies within are §fconsecrated§7.");
    }

    private void tickLight() {
        if (rays.isEmpty() && pools.isEmpty() || t < T_LIGHT) {
            return;
        }
        double fadeIn = Math.min(1.0, (t - T_LIGHT) / (double) LIGHT_FADE);
        double fadeOut = 1.0;
        if (t >= T_SHATTER) {
            fadeOut = Math.max(0.0, 1.0 - (t - T_SHATTER) / 10.0);
        }
        double poolOut = t >= T_SHATTER ? Math.max(0.0, 1.0 - (t - T_SHATTER) / 22.0) : 1.0;
        boolean still = t >= T_STILL;
        lightPulse = Math.max(0f, lightPulse - 0.08f);
        double shimmer = 0.85 + 0.15 * Math.sin(t * 0.33);
        double boost = still ? 1.9 : 1.0 + lightPulse;

        if (t < T_STILL) {
            shaftSpin += 0.012f;
        }
        boolean repose = t <= T_LIGHT + 1 || t < T_STILL && t % 2 == 0;

        for (int k = 0; k < rays.size(); k++) {
            Quad ray = rays.get(k);
            if (repose) {
                double a = face + windowSpin + Math.PI * 2 * (k + 0.5) / rays.size();
                Vector3f top = radial(a, R_OCULUS * 0.85, H_APEX - 0.3);
                Vector3f bottom = radial(a, 4.7, 0.15);
                Vector3f dir = new Vector3f(top).sub(bottom);
                float len = dir.length();
                Vector3f tangent = new Vector3f((float) -Math.sin(a), 0f, (float) Math.cos(a));
                Vector3f center = new Vector3f(top).add(bottom).mul(0.5f);
                ray.pose(center, basis(tangent, dir), 3.3f, len, 2);
            }
            ray.alpha((int) (34 * fadeIn * fadeOut * shimmer * boost));
        }
        for (int k = 0; k < shafts.size(); k++) {
            Quad shaft = shafts.get(k);
            if (repose) {
                double a = shaftSpin + Math.PI * k / shafts.size();
                Vector3f across = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
                shaft.pose(new Vector3f(0, (H_APEX - 0.4f) / 2f, 0), basis(across, new Vector3f(0, 1, 0)),
                        3.0f, H_APEX - 0.4f, 2);
            }
            shaft.alpha((int) (24 * fadeIn * fadeOut * (0.8 + 0.2 * Math.sin(t * 0.21 + k)) * boost));
        }
        for (int p = 0; p < pools.size(); p++) {
            Quad pool = pools.get(p);
            boolean center = p == PETALS;
            if (repose) {
                if (center) {
                    Vector3f along = new Vector3f((float) Math.cos(windowSpin + 0.785f), 0f, (float) Math.sin(windowSpin + 0.785f));
                    pool.pose(new Vector3f(0, 0.11f, 0), flat(along), 1.9f, 1.9f, 2);
                } else {
                    double a = face + windowSpin + Math.PI * 2 * p / PETALS;
                    Vector3f along = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
                    pool.pose(radial(a, 2.75, 0.1), flat(along), 1.55f, 3.3f, 2);
                }
            }
            int base = center ? 84 : 66;
            pool.alpha((int) (base * fadeIn * poolOut * (0.9 + 0.1 * shimmer) * (still ? 1.6 : 1.0 + lightPulse * 0.6)));
        }

        if (t >= T_SHATTER + 11) {
            for (Quad ray : rays) {
                ray.remove();
            }
            rays.clear();
            for (Quad shaft : shafts) {
                shaft.remove();
            }
            shafts.clear();
        }
        if (t >= T_SHATTER + 23) {
            for (Quad pool : pools) {
                pool.remove();
            }
            pools.clear();
        }

        /* Dust in the light — accents only. */
        if (t < T_SHATTER && t % 2 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double r = random.nextDouble(0.3, 4.2);
            double a = random.nextDouble(Math.PI * 2);
            Location mote = anchor.clone().add(Math.cos(a) * r, random.nextDouble(0.5, 11.0), Math.sin(a) * r);
            world.spawnParticle(Particle.END_ROD, mote, 0, 0, -0.02, 0, 1.0);
            world.spawnParticle(Particle.WHITE_ASH, mote, 2, 1.2, 1.5, 1.2, 0.0);
        }
    }

    // ------------------------------------------------------------------ act III — the hymn

    private void toll(int k) {
        float[] pitch = {0.84f, 0.71f, 0.63f, 0.5f};
        Hanger bell = k < bells.size() ? bells.get(k) : null;
        Location at = bell != null ? worldAt(bell.hang) : anchor.clone().add(0, 9, 0);
        world.playSound(at, Sound.BLOCK_BELL_USE, 2.4f, pitch[k]);
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, 1.1f, pitch[k]);
        world.playSound(anchor, Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, pitch[k] * 2f);
        if (k == TOLLS.length - 1) {
            world.playSound(anchor, Sound.BLOCK_BELL_USE, 1.6f, 0.5f);
            world.playSound(anchor, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
        waves.add(t);
        lightPulse = 0.9f;
        world.spawnParticle(Particle.DUST, at, 16, 0.6, 0.4, 0.6, 0, new Particle.DustOptions(GOLD, 1.4f));
        bless(k);
        penance(k);
        caster.sendActionBar(Component.text("§6✦ §eToll " + roman(k + 1) + " §6✦ §7allies healed · consecrated"));
    }

    private void bless(int k) {
        long until = Bukkit.getCurrentTick() + (TOTAL - t) + 60L;
        for (Player ally : world.getPlayers()) {
            if (!inZone(ally.getLocation()) || ally.isDead()) {
                continue;
            }
            AttributeInstance maxAttr = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxAttr != null ? maxAttr.getValue() : 20.0;
            ally.setHealth(Math.min(max, ally.getHealth() + max * (k == TOLLS.length - 1 ? 0.3 : 0.14)));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 1, true, false, true));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, true));
            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, true));
            if (k == 0) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 9, 1, true, false, true));
                ally.sendActionBar(Component.text("§6✦ Consecrated §7— healed every toll · §e+35% damage"));
            }
            CONSECRATED.put(ally.getUniqueId(), until);
            Location feet = ally.getLocation();
            drawRing(feet, 0.7, 18, new Particle.DustOptions(GOLD, 1.0f), 0);
            world.spawnParticle(Particle.END_ROD, feet.clone().add(0, 0.3, 0), 5, 0.25, 0.1, 0.25, 0.04);
            ally.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f + k * 0.12f);
        }
    }

    private void penance(int k) {
        if (damage <= 0) {
            return;
        }
        List<LivingEntity> sinners = enemiesInZone();
        sinners.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(anchor)));
        int staked = 0;
        for (LivingEntity sinner : sinners) {
            double amount = damage * 0.12;
            ScriptedHits.run(() -> sinner.damage(amount, caster));
            sinner.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, false));
            if (staked < 6) {
                stakes.add(new Stake(sinner.getLocation(), t));
                staked++;
            }
        }
    }

    /** A rod of light driven down onto a penitent. */
    private final class Stake {
        final BlockDisplay display;
        final Location at;
        final int born;

        Stake(Location target, int born) {
            this.at = level(target.clone());
            this.born = born;
            this.display = spawnBlockAt(at, Material.END_ROD.createBlockData(),
                    new Transformation(new Vector3f(-0.7f, 12f, -0.7f), new Quaternionf(), new Vector3f(1.4f, 7f, 1.4f),
                            new Quaternionf()), WARM);
            ease(display, new Transformation(new Vector3f(-0.7f, 0f, -0.7f), new Quaternionf(),
                    new Vector3f(1.4f, 7f, 1.4f), new Quaternionf()), 3);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.9f, 1.6f);
        }
    }

    private void tickStakes() {
        for (int i = stakes.size() - 1; i >= 0; i--) {
            Stake stake = stakes.get(i);
            int age = t - stake.born;
            if (age == 3) {
                world.spawnParticle(Particle.END_ROD, stake.at.clone().add(0, 0.4, 0), 10, 0.2, 0.1, 0.2, 0.08);
                world.spawnParticle(Particle.DUST, stake.at.clone().add(0, 0.8, 0), 10, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(GOLD, 1.3f));
                world.playSound(stake.at, Sound.ITEM_TRIDENT_HIT_GROUND, 0.9f, 1.4f);
                ease(stake.display, new Transformation(new Vector3f(-0.1f, 0f, -0.1f), new Quaternionf(),
                        new Vector3f(0.2f, 7f, 0.2f), new Quaternionf()), 6);
            }
            if (age >= 10) {
                discard(stake.display);
                stakes.remove(i);
            }
        }
    }

    private void hymnAmbience() {
        if (t < T_HYMN || t >= T_STILL) {
            return;
        }
        if ((t - T_HYMN) % 20 == 0) {
            world.playSound(anchor, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.15f);
        }
        for (int toll : TOLLS) {
            if (t == toll + 8) {
                chord(anchor.clone().add(0, 6, 0), Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.35f, 0.595f);
            }
        }
    }

    // ------------------------------------------------------------------ act IV — the last hymn

    private void still() {
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(anchor) < 64 * 64) {
                near.stopAllSounds();
            }
        }
        Location apex = anchor.clone().add(0, H_APEX, 0);
        world.playSound(apex, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 2.0f);
        world.playSound(apex, Sound.BLOCK_BELL_RESONATE, 0.6f, 2.0f);
        caster.sendActionBar(Component.text("§f§o…"));
        caster.sendMessage("§6✦ §7The hymn stops mid-swing.");

        List<LivingEntity> chosen = enemiesInZone();
        chosen.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(anchor)));
        for (int i = 0; i < chosen.size() && i < 10; i++) {
            LivingEntity enemy = chosen.get(i);
            BlockDisplay thread = spawnBlock(Material.GOLD_BLOCK.createBlockData(), tinyAt(0, H_APEX, 0), GOLD);
            Vector3f offset = toLocal(enemy.getLocation());
            bound.add(new Bound(enemy, thread, offset));
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 9, true, false, false));
            world.playSound(enemy.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.6f);
        }
    }

    private void tickBound() {
        if (bound.isEmpty()) {
            return;
        }
        for (Bound tie : bound) {
            LivingEntity enemy = tie.enemy;
            if (!enemy.isValid() || enemy.isDead()) {
                discard(tie.thread);
                continue;
            }
            if (t < T_SHATTER) {
                int age = t - T_STILL;
                float g = (float) easeOutCubic(Math.min(1.0, age / 5.0));
                Vector3f apex = new Vector3f(0, H_APEX - 0.4f + lift, 0);
                Vector3f body = toLocal(enemy.getLocation()).add(0, (float) (enemy.getHeight() * 0.6), 0);
                Vector3f tip = new Vector3f(apex).lerp(body, g);
                ease(tie.thread, rod(apex, tip, new Vector3f(1, 0, 0), 0.07f, 0.07f, 0f), 1);
            }
            if (t >= T_RAPTURE && t < T_SHATTER) {
                double yaw = Math.toRadians(turn);
                Vector3f orbit = new Vector3f(tie.offset).rotateY((float) -yaw);
                Location target = anchor.clone().add(orbit.x, lift * 0.88 + 1.2, orbit.z);
                Vector pull = target.toVector().subtract(enemy.getLocation().toVector()).multiply(0.32);
                if (pull.length() > 1.4) {
                    pull.normalize().multiply(1.4);
                }
                enemy.setVelocity(pull);
                enemy.setFallDistance(0f);
            }
        }
    }

    private void rapture() {
        int span = T_HANG - T_RAPTURE;
        double k = (t - T_RAPTURE) / (double) span;
        lift = (float) (RAPTURE_LIFT * easeInOutSine(k));
        turn = (float) (RAPTURE_TURN * k * k);
        Location up = anchor.clone().add(0, lift, 0);
        up.setYaw(turn);
        up.setPitch(0f);
        for (Piece piece : arch) {
            if (piece.display != null && piece.display.isValid()) {
                piece.display.teleport(up);
            }
        }
        for (Quad quad : rays) {
            quad.teleport(up);
        }
        for (Quad quad : shafts) {
            quad.teleport(up);
        }

        if (t == T_RAPTURE) {
            for (Bound tie : bound) {
                if (tie.enemy.isValid()) {
                    tie.enemy.setGravity(false);
                    SUSPENDED.add(tie.enemy);
                }
            }
            for (Piece piece : inlay) {
                if (piece.display != null && piece.display.isValid()) {
                    piece.display.setGlowing(true);
                    piece.display.setGlowColorOverride(GOLD);
                }
            }
            world.playSound(anchor, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
            world.playSound(anchor, Sound.BLOCK_CONDUIT_ACTIVATE, 1.2f, 0.7f);
            world.playSound(anchor, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.5f);
            for (int i = 0; i < PILLARS; i++) {
                Location base = worldAt(radial(pillarAngle(i), R_PILLAR, 0.2));
                world.spawnParticle(Particle.CLOUD, base, 8, 0.6, 0.05, 0.6, 0.02);
                world.spawnParticle(Particle.BLOCK, base, 12, 0.7, 0.1, 0.7, 0, Material.CALCITE.createBlockData());
            }
            caster.sendActionBar(Component.text("§6✦ §eThe basilica ascends §6✦"));
        }
        /* A choir climbing a major scale as the building climbs. */
        float[] scale = {0.5f, 0.561f, 0.63f, 0.667f, 0.749f, 0.841f, 0.944f, 1.0f, 1.122f, 1.26f};
        int step = (t - T_RAPTURE) / 3;
        if ((t - T_RAPTURE) % 3 == 0 && step < scale.length) {
            Location voice = anchor.clone().add(0, lift + 7, 0);
            world.playSound(voice, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, scale[step]);
            world.playSound(voice, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, scale[step]);
        }
        if (t % 4 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(2.0, R_PILLAR + 1.0);
                world.spawnParticle(Particle.END_ROD,
                        anchor.clone().add(Math.cos(a) * r, lift + random.nextDouble(0.0, 6.0), Math.sin(a) * r),
                        0, 0, -1, 0, 0.12);
            }
        }
    }

    private void hang() {
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(anchor) < 64 * 64) {
                near.stopAllSounds();
            }
        }
        world.playSound(anchor.clone().add(0, lift + 8, 0), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.8f);
    }

    /** Top-down: every stone of the basilica becomes stained glass. */
    private void glassSweep(int stage) {
        float threshold = H_APEX + 0.5f - (stage + 1) * (H_APEX + 1.5f) / 4f;
        for (Piece piece : arch) {
            if (piece.glassed || piece.display == null || !piece.display.isValid()) {
                continue;
            }
            if (piece.center().y < threshold) {
                continue;
            }
            piece.glassed = true;
            if (piece.glass != null) {
                piece.display.setBlock(piece.glass.createBlockData());
            }
            piece.display.setGlowing(true);
            piece.display.setGlowColorOverride(glowFor(piece.glass));
        }
        float[] pitch = {1.9f, 1.6f, 1.3f, 1.0f};
        Location voice = anchor.clone().add(0, lift + H_APEX - stage * 3, 0);
        world.playSound(voice, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f, pitch[stage]);
        world.playSound(voice, Sound.BLOCK_GLASS_PLACE, 0.9f, pitch[stage]);
    }

    private void shatter() {
        Location up = anchor.clone().add(0, lift, 0);
        world.playSound(up, Sound.BLOCK_GLASS_BREAK, 2.2f, 0.5f);
        world.playSound(up, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.7f);
        world.playSound(up, Sound.BLOCK_GLASS_BREAK, 1.8f, 1.0f);
        world.playSound(up, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6f, 0.6f);
        world.playSound(up, Sound.ITEM_TOTEM_USE, 0.9f, 1.4f);
        world.playSound(up, Sound.BLOCK_BELL_USE, 1.4f, 0.5f);
        world.playSound(anchor, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
        world.spawnParticle(Particle.FLASH, up.clone().add(0, 8, 0), 3, 3, 3, 3, 0);
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(anchor) < 64 * 64) {
                Location eye = near.getEyeLocation();
                near.spawnParticle(Particle.FLASH, eye.clone().add(eye.getDirection().multiply(1.2)), 1, 0, 0, 0, 0);
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = Math.toRadians(turn);
        for (Piece piece : arch) {
            if (piece.display == null || !piece.display.isValid()) {
                piece.dead = true;
                continue;
            }
            Vector3f c = piece.center();
            Vector3f spun = new Vector3f(c).rotateY((float) -yaw);
            Vector3f out = new Vector3f(spun.x, 0f, spun.z);
            if (out.lengthSquared() < 0.01f) {
                out.set((float) random.nextDouble(-1, 1), 0f, (float) random.nextDouble(-1, 1));
            }
            out.normalize();
            piece.vel.set(out).mul((float) random.nextDouble(0.08, 0.2))
                    .add((float) random.nextDouble(-0.03, 0.03), (float) random.nextDouble(0.02, 0.1),
                            (float) random.nextDouble(-0.03, 0.03));
            piece.spinAxis.set((float) random.nextDouble(-1, 1), (float) random.nextDouble(-1, 1),
                    (float) random.nextDouble(-1, 1));
            if (piece.spinAxis.lengthSquared() < 1.0E-3f) {
                piece.spinAxis.set(0, 1, 0);
            }
            piece.spinAxis.normalize();
            piece.spinRate = (float) random.nextDouble(0.04, 0.15);
            piece.life = 26 + random.nextInt(16);
            piece.shardCenter = c;
            piece.shardRot = new Quaternionf(piece.rot);
            piece.shardSize = new Vector3f(piece.size);
        }

        for (Bound tie : bound) {
            discard(tie.thread);
            LivingEntity enemy = tie.enemy;
            if (!enemy.isValid() || enemy.isDead()) {
                continue;
            }
            enemy.setGravity(true);
            SUSPENDED.remove(enemy);
            if (damage > 0) {
                double amount = damage * 2.2;
                ScriptedHits.run(() -> enemy.damage(amount, caster));
            }
            enemy.setVelocity(new Vector(0, -3.2, 0));
            fallen.add(new Fallen(enemy, t + 5));
        }
        for (LivingEntity enemy : enemiesInZone()) {
            boolean wasBound = false;
            for (Bound tie : bound) {
                if (tie.enemy.equals(enemy)) {
                    wasBound = true;
                    break;
                }
            }
            if (wasBound || damage <= 0) {
                continue;
            }
            double amount = damage;
            ScriptedHits.run(() -> enemy.damage(amount, caster));
            Vector away = enemy.getLocation().toVector().subtract(anchor.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            enemy.setVelocity(away.normalize().multiply(1.1).setY(0.5));
        }
        caster.sendActionBar(Component.text("§6§l✦ THE LAST HYMN ✦"));
        caster.sendMessage("§6✦ §eThe basilica shatters §7— the bound are cast down.");
    }

    private void tickShards() {
        Location up = anchor.clone().add(0, lift, 0);
        up.setYaw(turn);
        up.setPitch(0f);
        double yaw = Math.toRadians(turn);
        for (int i = 0; i < arch.size(); i++) {
            Piece piece = arch.get(i);
            if (piece.dead || piece.shardCenter == null) {
                continue;
            }
            piece.age++;
            boolean slow = piece.age < 10;
            float pace = slow ? 0.45f : 1.0f;
            piece.drift.add(new Vector3f(piece.vel).mul(pace));
            piece.vel.y -= slow ? 0.006f : 0.03f;
            piece.spinAngle += piece.spinRate * pace;
            float shrink = 1f - 0.5f * Math.min(1f, piece.age / (float) piece.life);

            Vector3f worldCenter = new Vector3f(piece.shardCenter).rotateY((float) -yaw).add(piece.drift);
            boolean grounded = worldCenter.y + lift < 0.4f;
            if (piece.age >= piece.life || grounded) {
                Location at = anchor.clone().add(worldCenter.x, worldCenter.y + lift, worldCenter.z);
                world.spawnParticle(Particle.END_ROD, at, 2, 0.1, 0.1, 0.1, 0.02);
                if (piece.glass != null) {
                    world.spawnParticle(Particle.BLOCK, at, 4, 0.15, 0.15, 0.15, 0, piece.glass.createBlockData());
                }
                discard(piece.display);
                piece.dead = true;
                continue;
            }
            if ((piece.age + i) % 2 != 0) {
                continue;
            }
            Location to = up.clone().add(piece.drift.x, piece.drift.y, piece.drift.z);
            piece.display.teleport(to);
            Quaternionf q = new Quaternionf().rotateAxis(piece.spinAngle, piece.spinAxis).mul(piece.shardRot);
            ease(piece.display, centered(piece.shardCenter, q, new Vector3f(piece.shardSize).mul(shrink)), 2);
        }
        if (t == T_SHATTER + 6) {
            world.playSound(anchor, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.8f);
        }
        if (t % 3 == 0 && t < T_SHATTER + 30) {
            world.playSound(anchor.clone().add(0, Math.max(2, lift - (t - T_SHATTER) * 0.4), 0),
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f + ThreadLocalRandom.current().nextFloat() * 0.8f);
        }
    }

    private void tickFallen() {
        for (int i = fallen.size() - 1; i >= 0; i--) {
            Fallen down = fallen.get(i);
            if (t < down.landTick) {
                continue;
            }
            fallen.remove(i);
            if (!down.enemy.isValid()) {
                continue;
            }
            Location at = down.enemy.getLocation();
            world.spawnParticle(Particle.EXPLOSION, at, 2, 0.4, 0.1, 0.4, 0);
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), 26, 0.8, 0.1, 0.8, 0,
                    Material.CALCITE.createBlockData());
            world.spawnParticle(Particle.DUST, at.clone().add(0, 0.5, 0), 14, 0.6, 0.4, 0.6, 0,
                    new Particle.DustOptions(GOLD, 1.5f));
            world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
            world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.5f);
        }
    }

    /** Reverse spiral from the rim inward: every flagstone stands up and sinks into its own hinge. */
    private void fold() {
        for (Tile tile : tiles) {
            if (tile.gone) {
                continue;
            }
            if (tile.foldAt < 0) {
                int reverse = tile.segs - 1 - tile.index;
                tile.foldAt = T_FOLD + (BANDS.length - 1 - tile.band) * 5 + (int) Math.round(8.0 * reverse / tile.segs);
            }
            int lt = t - tile.foldAt;
            if (lt == 0) {
                ease(tile.display, tile.pose(HALF_PI, 0f, 1f), 4);
            } else if (lt == 4) {
                ease(tile.display, tile.pose(HALF_PI, 0f, 0.001f), 3);
                if (tile.index % 3 == 0) {
                    world.playSound(anchor, Sound.BLOCK_DEEPSLATE_TILES_BREAK, 0.4f, 0.8f);
                }
            } else if (lt >= 8) {
                discard(tile.display);
                tile.gone = true;
            }
        }
        int inlayStart = T_FOLD + 10;
        if (t >= inlayStart && !inlay.isEmpty()) {
            double k = Math.min(1.0, (t - inlayStart) / 14.0);
            double shrink = 1.0 - easeInOutSine(k);
            for (int i = 0; i < inlay.size(); i++) {
                double[] meta = inlayMeta.get(i);
                double radius = Math.max(0.05, INLAY_R[(int) meta[0]] * shrink);
                float len = (float) (Math.PI * 2 * radius / meta[2] * 1.06);
                pose(inlay.get(i), arcSeg(meta[1], radius, 0.08f, 0.16f, 0.05f, Math.max(0.02f, len)), 2);
            }
            if (k >= 1.0) {
                for (Piece piece : inlay) {
                    discard(piece.display);
                }
                inlay.clear();
                inlayMeta.clear();
            }
        }
        if (t == T_ECHO - 3) {
            for (Piece piece : medallion) {
                ease(piece.display, tinyAt(0, 0.06f, 0), 3);
            }
        }
    }

    private void echo() {
        for (Piece piece : medallion) {
            discard(piece.display);
        }
        medallion.clear();
        world.spawnParticle(Particle.END_ROD, anchor.clone().add(0, 0.2, 0), 12, 0.1, 0.05, 0.1, 0.05);
        world.playSound(anchor, Sound.BLOCK_BELL_USE, 0.45f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_BELL_RESONATE, 0.5f, 0.5f);
        caster.sendMessage("§8…amen.");
    }

    // ------------------------------------------------------------------ zone helpers

    private boolean inZone(Location at) {
        if (at.getWorld() != world) {
            return false;
        }
        double dx = at.getX() - anchor.getX();
        double dz = at.getZ() - anchor.getZ();
        double dy = at.getY() - anchor.getY();
        return dx * dx + dz * dz <= R_ZONE * R_ZONE && dy > -2.5 && dy < 9.0;
    }

    private List<LivingEntity> enemiesInZone() {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(anchor, R_ZONE, 9.0, R_ZONE)) {
            if (TestPrototypeAbilities.isCombatTarget(entity) && inZone(entity.getLocation())) {
                out.add((LivingEntity) entity);
            }
        }
        return out;
    }

    private static void ensureAmplifier(JavaPlugin plugin) {
        if (amplifier != null) {
            return;
        }
        amplifier = new Amplifier();
        plugin.getServer().getPluginManager().registerEvents(amplifier, plugin);
    }

    /** Consecrated hands hit harder while the hymn lasts. */
    private static final class Amplifier implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent event) {
            if (CONSECRATED.isEmpty() || ScriptedHits.isActive()) {
                return;
            }
            Player hitter = null;
            if (event.getDamager() instanceof Player player) {
                hitter = player;
            } else if (event.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                hitter = shooter;
            }
            if (hitter == null || event.getEntity() instanceof Player
                    || !(event.getEntity() instanceof LivingEntity victim)) {
                return;
            }
            Long until = CONSECRATED.get(hitter.getUniqueId());
            if (until == null) {
                return;
            }
            if (Bukkit.getCurrentTick() > until) {
                CONSECRATED.remove(hitter.getUniqueId());
                return;
            }
            event.setDamage(event.getDamage() * AMP);
            Location at = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
            victim.getWorld().spawnParticle(Particle.DUST, at, 6, 0.25, 0.3, 0.25, 0, new Particle.DustOptions(GOLD, 1.1f));
            victim.getWorld().spawnParticle(Particle.END_ROD, at, 2, 0.2, 0.25, 0.2, 0.03);
        }
    }

    // ------------------------------------------------------------------ pieces

    private static final class Piece {
        final BlockDisplay display;
        final Material glass;
        final Vector3f corner = new Vector3f();
        final Quaternionf rot = new Quaternionf();
        final Vector3f size = new Vector3f(0.001f, 0.001f, 0.001f);
        final Vector3f drift = new Vector3f();
        final Vector3f vel = new Vector3f();
        final Vector3f spinAxis = new Vector3f(0, 1, 0);
        Vector3f shardCenter;
        Quaternionf shardRot;
        Vector3f shardSize;
        float spinRate;
        float spinAngle;
        int life;
        int age;
        boolean glassed;
        boolean dead;

        Piece(BlockDisplay display, Material glass) {
            this.display = display;
            this.glass = glass;
        }

        void set(Transformation tf) {
            corner.set(tf.getTranslation());
            rot.set(tf.getLeftRotation());
            size.set(tf.getScale());
        }

        Vector3f center() {
            return new Quaternionf(rot).transform(new Vector3f(size).mul(0.5f)).add(corner);
        }
    }

    /** A flagstone hinged on its inner edge. */
    private static final class Tile {
        final double angle;
        final float r0;
        final float r1;
        final float width;
        final float y;
        final int start;
        final int band;
        final int index;
        final int segs;
        BlockDisplay display;
        int foldAt = -1;
        boolean gone;

        Tile(double angle, float r0, float r1, float width, float y, int start, int band, int index, int segs) {
            this.angle = angle;
            this.r0 = r0;
            this.r1 = r1;
            this.width = width;
            this.y = y;
            this.start = start;
            this.band = band;
            this.index = index;
            this.segs = segs;
        }

        /** tilt π/2 = standing on the hinge, 0 = lying flat. grow shrinks toward the hinge line. */
        Transformation pose(float tilt, float lift, float grow) {
            Quaternionf q = new Quaternionf().rotateY((float) -angle).rotateZ(tilt);
            Vector3f u = new Vector3f((float) Math.cos(angle), 0f, (float) Math.sin(angle));
            Vector3f v = new Vector3f((float) -Math.sin(angle), 0f, (float) Math.cos(angle));
            Vector3f corner = u.mul(r0).add(v.mul(-width / 2f)).add(0f, y + lift, 0f);
            float g = Math.max(0.001f, grow);
            return new Transformation(corner, q, new Vector3f((r1 - r0) * g, 0.06f * Math.max(0.02f, g), width),
                    new Quaternionf());
        }
    }

    private final class Column {
        final double angle;
        final int start;
        final Vector3f base;
        final Quaternionf yaw;
        final List<Piece> parts = new ArrayList<>();
        final List<float[]> dims = new ArrayList<>();

        Column(double angle, int start, Vector3f base, Quaternionf yaw) {
            this.angle = angle;
            this.start = start;
            this.base = base;
            this.yaw = yaw;
        }

        void add(Material material, float centerY, float sx, float sy, float sz, Material glass) {
            BlockDisplay display = spawnBlock(material.createBlockData(), tinyAt(base.x, -8f, base.z), null);
            Piece piece = new Piece(display, glass);
            parts.add(piece);
            dims.add(new float[]{centerY, sx, sy, sz});
            arch.add(piece);
        }

        void pose(float offset, int ticks) {
            for (int i = 0; i < parts.size(); i++) {
                float[] d = dims.get(i);
                VesperBellBasilica.this.pose(parts.get(i),
                        centered(new Vector3f(base.x, d[0] + offset, base.z), yaw, new Vector3f(d[1], d[2], d[3])), ticks);
            }
        }
    }

    private static final class Span {
        final Vector3f a;
        final Vector3f b;
        final Vector3f out;
        final int start;
        final Piece[] segs = new Piece[4];
        boolean clicked;

        Span(Vector3f a, Vector3f b, Vector3f out, int start) {
            this.a = a;
            this.b = b;
            this.out = out;
            this.start = start;
        }
    }

    private static final class Rib {
        final double angle;
        final int start;
        final Piece[] segs = new Piece[RIB_SEGS];

        Rib(double angle, int start) {
            this.angle = angle;
            this.start = start;
        }
    }

    /** Bells and lanterns: pivot at the top, swing about the tangent. */
    private static final class Hanger {
        final Piece piece;
        final Vector3f hang;
        final double angle;
        final float scale;
        final int born;
        float theta;

        Hanger(Piece piece, Vector3f hang, double angle, float scale, int born) {
            this.piece = piece;
            this.hang = hang;
            this.angle = angle;
            this.scale = scale;
            this.born = born;
        }
    }

    private static final class Bound {
        final LivingEntity enemy;
        final BlockDisplay thread;
        final Vector3f offset;

        Bound(LivingEntity enemy, BlockDisplay thread, Vector3f offset) {
            this.enemy = enemy;
            this.thread = thread;
            this.offset = offset;
        }
    }

    private static final class Fallen {
        final LivingEntity enemy;
        final int landTick;

        Fallen(LivingEntity enemy, int landTick) {
            this.enemy = enemy;
            this.landTick = landTick;
        }
    }

    /**
     * A translucent plane of colored light: two text-display backgrounds back to back,
     * so it reads from both sides however the client culls text backgrounds.
     */
    private final class Quad {
        final TextDisplay front;
        final TextDisplay back;
        final Color tint;
        int alpha = -1;

        Quad(Color tint) {
            this.tint = tint;
            this.front = spawnText(anchor);
            this.back = spawnText(anchor);
        }

        void pose(Vector3f center, Quaternionf rot, float w, float h, int ticks) {
            ease(front, quad(center, rot, w, h, false), ticks);
            ease(back, quad(center, rot, w, h, true), ticks);
        }

        void alpha(int value) {
            int a = Math.max(0, Math.min(255, value));
            if (a == alpha) {
                return;
            }
            alpha = a;
            Color color = a < 6 ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(a, tint.getRed(), tint.getGreen(), tint.getBlue());
            if (front != null && front.isValid()) {
                front.setBackgroundColor(color);
            }
            if (back != null && back.isValid()) {
                back.setBackgroundColor(color);
            }
        }

        void teleport(Location at) {
            if (front != null && front.isValid()) {
                front.teleport(at);
            }
            if (back != null && back.isValid()) {
                back.teleport(at);
            }
        }

        void remove() {
            discard(front);
            discard(back);
        }
    }

    // ------------------------------------------------------------------ geometry

    private double pillarAngle(int i) {
        return face + (i + 0.5) * Math.PI * 2 / PILLARS;
    }

    private static Vector3f radial(double angle, double r, double y) {
        return new Vector3f((float) (Math.cos(angle) * r), (float) y, (float) (Math.sin(angle) * r));
    }

    /** Local x → radial, local z → tangent. */
    private static Quaternionf yawQ(double angle) {
        return new Quaternionf().rotateY((float) -angle);
    }

    private Vector3f toLocal(Location at) {
        return new Vector3f((float) (at.getX() - anchor.getX()), (float) (at.getY() - anchor.getY()),
                (float) (at.getZ() - anchor.getZ()));
    }

    private Location worldAt(Vector3f local) {
        return anchor.clone().add(local.x, local.y, local.z);
    }

    private static Transformation hanging(Vector3f hang, double angle, float theta, float scale) {
        Quaternionf q = yawQ(angle).rotateZ(theta);
        Vector3f pivot = new Vector3f(0.5f * scale, scale, 0.5f * scale);
        Vector3f corner = new Vector3f(hang).sub(new Quaternionf(q).transform(pivot));
        return new Transformation(corner, q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static Transformation arcSeg(double angle, double radius, float y, float radialW, float thick, float len) {
        return centered(radial(angle, radius, y), yawQ(angle), new Vector3f(radialW, thick, len));
    }

    private static Transformation centered(Vector3f center, Quaternionf rot, Vector3f size) {
        Quaternionf q = new Quaternionf(rot);
        Vector3f s = new Vector3f(Math.max(0.001f, size.x), Math.max(0.001f, size.y), Math.max(0.001f, size.z));
        Vector3f corner = new Vector3f(center).sub(new Quaternionf(q).transform(new Vector3f(s).mul(0.5f)));
        return new Transformation(corner, q, s, new Quaternionf());
    }

    /** Box whose long (y) axis runs a→b; xHint fixes the roll. */
    private static Transformation rod(Vector3f a, Vector3f b, Vector3f xHint, float wx, float wz, float overlap) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3f) {
            return tinyAt(a.x, a.y, a.z);
        }
        Vector3f center = new Vector3f(a).add(b).mul(0.5f);
        return centered(center, basis(xHint, d), new Vector3f(wx, len + overlap, wz));
    }

    /** Rotation whose local y is {@code yAxis} and local x is {@code xAxis} made orthogonal to it. */
    private static Quaternionf basis(Vector3f xAxis, Vector3f yAxis) {
        Vector3f y = new Vector3f(yAxis).normalize();
        Vector3f x = new Vector3f(xAxis).sub(new Vector3f(y).mul(xAxis.dot(y)));
        if (x.lengthSquared() < 1.0E-6f) {
            x = Math.abs(y.y) < 0.9f ? new Vector3f(0, 1, 0).cross(y) : new Vector3f(1, 0, 0).cross(y);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    /** A floor-lying quad whose length runs along {@code along}; its front faces up. */
    private static Quaternionf flat(Vector3f along) {
        Vector3f y = new Vector3f(along.x, 0f, along.z).normalize();
        Vector3f x = new Vector3f(y).cross(0f, 1f, 0f);
        return basis(x, y);
    }

    /** Text-display background of " " spans 1×1 after scale (8, 4) and a (−0.1, −0.5) shift. */
    private static Transformation quad(Vector3f center, Quaternionf rot, float w, float h, boolean back) {
        Quaternionf q = back ? new Quaternionf(rot).rotateY((float) Math.PI) : new Quaternionf(rot);
        Vector3f shift = new Quaternionf(q).transform(new Vector3f(-0.1f * w, -0.5f * h, 0f));
        Vector3f translation = new Vector3f(center).add(shift);
        if (back) {
            translation.sub(new Quaternionf(rot).transform(new Vector3f(0f, 0f, 0.004f)));
        }
        return new Transformation(translation, q, new Vector3f(8f * Math.max(0.001f, w), 4f * Math.max(0.001f, h), 1f),
                new Quaternionf());
    }

    private static Transformation tinyAt(float x, float y, float z) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f),
                new Quaternionf());
    }

    private static Color glowFor(Material glass) {
        if (glass == null) {
            return GOLD;
        }
        return switch (glass) {
            case LIGHT_BLUE_STAINED_GLASS, BLUE_STAINED_GLASS -> COBALT;
            case YELLOW_STAINED_GLASS, ORANGE_STAINED_GLASS -> GOLD;
            case MAGENTA_STAINED_GLASS, PINK_STAINED_GLASS, RED_STAINED_GLASS -> RUBY;
            default -> IVORY;
        };
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    // ------------------------------------------------------------------ easing / fx helpers

    private static double easeOutCubic(double x) {
        x = Math.max(0, Math.min(1, x));
        return 1.0 - Math.pow(1.0 - x, 3);
    }

    private static double easeOutBack(double x) {
        x = Math.max(0, Math.min(1, x));
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private static double easeInOutSine(double x) {
        x = Math.max(0, Math.min(1, x));
        return -(Math.cos(Math.PI * x) - 1) / 2;
    }

    private void chord(Location at, Sound sound, float volume, float root) {
        world.playSound(at, sound, volume, root);
        world.playSound(at, sound, volume * 0.85f, root * 1.26f);
        world.playSound(at, sound, volume * 0.8f, root * 1.498f);
    }

    private void drawRing(Location center, double radius, int points, Particle.DustOptions dust, double phase) {
        for (int i = 0; i < points; i++) {
            double a = phase + Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void burstAt(Location at, Particle particle, int count, double speed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2 * Math.PI * u;
            double phi = Math.acos(2 * v - 1);
            world.spawnParticle(particle, at, 0, Math.sin(phi) * Math.cos(theta), Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta), speed);
        }
    }

    private Location floorUnder(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 0.6, 0), new Vector(0, -1, 0), 8.0,
                FluidCollisionMode.NEVER, true);
        Location out = hit != null && hit.getHitPosition() != null ? hit.getHitPosition().toLocation(world) : at.clone();
        return level(out);
    }

    private static Location level(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void pose(Piece piece, Transformation transformation, int ticks) {
        if (piece == null) {
            return;
        }
        piece.set(transformation);
        ease(piece.display, transformation, ticks);
    }

    private BlockDisplay spawnBlock(BlockData data, Transformation initial, Color glow) {
        return spawnBlockAt(anchor, data, initial, glow);
    }

    private static BlockDisplay spawnBlockAt(Location at, BlockData data, Transformation initial, Color glow) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(level(at), BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                spawned.setViewRange(1.5f);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(initial);
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static TextDisplay spawnText(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            TextDisplay display = world.spawn(level(at), TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.text(Component.text(" "));
                spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                spawned.setDefaultBackground(false);
                spawned.setShadowed(false);
                spawned.setSeeThrough(false);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setBrightness(LIT);
                spawned.setLineWidth(200);
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                spawned.setViewRange(1.5f);
                spawned.setTransformation(tinyAt(0, 0, 0));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearAll() {
        for (Tile tile : tiles) {
            discard(tile.display);
        }
        tiles.clear();
        for (Piece piece : inlay) {
            discard(piece.display);
        }
        inlay.clear();
        inlayMeta.clear();
        for (Piece piece : medallion) {
            discard(piece.display);
        }
        medallion.clear();
        for (Piece piece : arch) {
            discard(piece.display);
        }
        arch.clear();
        for (Quad quad : rays) {
            quad.remove();
        }
        rays.clear();
        for (Quad quad : shafts) {
            quad.remove();
        }
        shafts.clear();
        for (Quad quad : pools) {
            quad.remove();
        }
        pools.clear();
        for (Stake stake : stakes) {
            discard(stake.display);
        }
        stakes.clear();
        for (Bound tie : bound) {
            discard(tie.thread);
            if (tie.enemy.isValid()) {
                tie.enemy.setGravity(true);
            }
            SUSPENDED.remove(tie.enemy);
        }
        bound.clear();
        fallen.clear();
        release();
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }
}
