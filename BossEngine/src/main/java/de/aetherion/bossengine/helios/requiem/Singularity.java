package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE SINGULARITY (below 25 %). Helios's heart falls into itself over the pit.
 *
 * <pre>
 *   core      three black cubes with a white glow outline: the photon sphere, a black ball ringed in light
 *   horizon   a red ring on the floor at the event horizon's radius; it shrinks as Helios weakens
 *   disk      star debris and arena rubble on a tilted accretion disk; inner rocks orbit faster, glow
 *             white-hot and are stretched along their orbit; every rock spirals in and is swallowed
 *   lens      the far side of the disk, bent up over the top and under the bottom of the hole
 *             (two arcs that always face the party): gravitational lensing, the image you know
 * </pre>
 *
 * <b>Gravity</b> pulls everyone toward the hole with a swirl (sneaking resists), stronger the closer
 * you are. Inside the horizon you burn; touch the core and you are crushed and spat out.
 * <b>Time dilation</b>: the closer you are, the lower every sound is pitched and the slower your sky
 * moves, and your vision narrows. Every few seconds it tears one floating island off the Course and
 * feeds it to the disk (a few always remain).
 */
final class Singularity {

    private static final Color RIM = Color.fromRGB(235, 240, 255);
    private static final Color HOT = Color.fromRGB(255, 140, 40);
    public static final Vector3f CENTER = new Vector3f(0f, 3.2f, 0f);

    private final HeliosScript h;
    private final HeliosEncounter enc;
    private final HeliosStage stage;
    private final HeliosStage.Group g;
    private final BlockDisplay[] core = new BlockDisplay[3];
    private final Shapes.Ring floorMark;
    private final Shapes.Ring[] lens = new Shapes.Ring[2];
    private final List<Rock> disk = new ArrayList<>();
    private float horizon;
    private float horizonTarget;
    private float strength = 1f;
    private int t;
    private boolean quiet;
    private int nextIsland;
    private final List<Tear> tears = new ArrayList<>();

    private static final class Rock {
        BlockDisplay d;
        float r;
        float theta;
        float y;
        float size;
    }

    private static final class Tear {
        Arena.Replica replica;
        Vector3f pivot;
        int t;
    }

    Singularity(HeliosScript h) {
        this.h = h;
        this.enc = h.encounter();
        this.stage = enc.stage();
        this.g = stage.group();
        for (int i = 0; i < core.length; i++) {
            core[i] = g.block(Material.BLACK_CONCRETE.createBlockData(), RIM, 0, true);
        }
        floorMark = new Shapes.Ring(stage, g, stage.budget().scaled(28, 16), Material.RED_STAINED_GLASS, HOT, 15, true);
        for (int i = 0; i < lens.length; i++) {
            lens[i] = new Shapes.Ring(stage, g, stage.budget().scaled(18, 10), Material.ORANGE_STAINED_GLASS, HOT, 15, true);
        }
        horizon = 0.3f;
        horizonTarget = (float) enc.config().d("helios.singularity.horizon-start", 7.5);
        // The star's belt goes first, then rubble to fill the disk.
        DyingStar.Taken taken;
        while ((taken = enc.star().take()) != null) {
            addRock(taken.display(), taken.size());
        }
        int want = stage.budget().scaled(28, 14);
        Material[] rubble = {Material.DEEPSLATE_TILES, Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.POLISHED_DEEPSLATE};
        for (int i = disk.size(); i < want; i++) {
            BlockDisplay d = g.block(rubble[i % rubble.length].createBlockData(), null, 12, false);
            if (d != null) {
                addRock(d, 0.5f + HMath.hash(i, 3) * 0.7f);
            }
        }
        nextIsland = 20 * 8;
    }

    private void addRock(BlockDisplay d, float size) {
        Rock r = new Rock();
        r.d = d;
        int i = disk.size();
        r.r = horizon + 3f + HMath.hash(i, 17) * 13f;
        r.theta = HMath.hash(i, 29) * HMath.TAU;
        r.y = (HMath.hash(i, 31) - 0.5f) * 0.8f;
        r.size = size;
        g.displays().add(d);
        disk.add(r);
    }

    float horizon() {
        return horizon;
    }

    /** 0 .. 1 of the singularity's life: the horizon shrinks as Helios weakens. */
    void progress(float f) {
        float start = (float) enc.config().d("helios.singularity.horizon-start", 7.5);
        float end = (float) enc.config().d("helios.singularity.horizon-end", 3.0);
        horizonTarget = HMath.lerp(start, end, HMath.clamp01(f));
    }

    /** The Requiem's silence: everything stops pulling and turning. */
    void quiet(boolean on) {
        quiet = on;
    }

    void strength(float s) {
        strength = s;
    }

    void tick() {
        t++;
        horizon = HMath.lerp(horizon, horizonTarget, 0.03f);
        render();
        tickTears();
        if (quiet) {
            return;
        }
        gravity();
        if (t % 5 == 0) {
            senses();
        }
        if (enc.tempo().onDownbeat()) {
            // The haul: a deep pull you can hear and see coming every bar.
            enc.score().play(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            enc.score().at(CENTER, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 0.5f);
        }
        if (t % 40 == 0) {
            enc.score().at(CENTER, Sound.BLOCK_PORTAL_AMBIENT, 1.2f, 0.5f);
            enc.score().at(CENTER, Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.5f);
        }
        if (t >= nextIsland) {
            nextIsland = t + 20 * 14;
            tearIsland();
        }
    }

    private void render() {
        float spin = quiet ? 0f : t * 0.04f;
        int interp = 2;
        if (t % 2 != 0) {
            return;
        }
        float s = horizon * 0.62f;
        for (int i = 0; i < core.length; i++) {
            Quaternionf rot = new Quaternionf().rotateXYZ(spin * (1 + i), spin * 0.7f, spin * 0.3f * i);
            stage.push(core[i], HeliosStage.cube(CENTER, Math.max(0.2f, s), rot), interp);
        }
        boolean haul = !quiet && enc.tempo().beatInBar() < 2;
        floorMark.flat(new Vector3f(CENTER.x, 0.06f, CENTER.z), horizon + (haul ? 0.4f : 0f), haul ? 0.45f : 0.14f, 0.04f, -spin * 2f, interp);
        // Lensing arcs face the party: the far side of the disk, bent over the top and under the bottom.
        Vector3f c = h.partyCentroid();
        float view = HMath.angleOf(c.x - CENTER.x, c.z - CENTER.z);
        // Plane spanned by "up" and the axis across the line of sight, so the arcs face the viewers.
        Quaternionf plane = new Quaternionf().rotateY(-view + HMath.HALF_PI).rotateX(HMath.HALF_PI);
        lens[0].arc(CENTER, plane, horizon * 1.25f, 0.22f, 0.12f, HMath.PI + 0.25f, HMath.TAU - 0.25f, interp);
        lens[1].arc(CENTER, plane, horizon * 1.05f, 0.12f, 0.08f, 0.35f, HMath.PI - 0.35f, interp);
        Quaternionf tilt = new Quaternionf().rotateX(0.28f).rotateZ(0.12f * (float) Math.sin(t * 0.01f));
        for (Rock r : disk) {
            if (!quiet) {
                float w = HMath.orbitSpeed(r.r, 2.2f);
                r.theta += w * 2f;
                r.r -= 0.012f * (1f + 6f / Math.max(1f, r.r));
            }
            if (r.r < horizon + 0.4f) {
                // Swallowed: a flicker at the rim, then it comes back on the outside of the disk.
                enc.score().at(CENTER, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.4f, 1.8f);
                r.r = horizon + 12f + HMath.hash((int) r.theta, t) * 5f;
            }
            Vector3f local = new Vector3f((float) Math.cos(r.theta) * r.r, r.y, (float) Math.sin(r.theta) * r.r);
            Vector3f at = new Quaternionf(tilt).transform(local).add(CENTER);
            float heat = HMath.clamp01(1f - (r.r - horizon) / 10f);
            float stretch = 1f + 3.5f * heat;
            Quaternionf along = new Quaternionf(tilt).rotateY(-r.theta);
            Material m = heat > 0.7f ? Material.WHITE_CONCRETE : heat > 0.4f ? Material.SHROOMLIGHT : heat > 0.15f ? Material.MAGMA_BLOCK : null;
            if (m != null) {
                HeliosStage.material(r.d, m);
                HeliosStage.brightness(r.d, 15);
            }
            stage.push(r.d, HeliosStage.box(at, new Vector3f(r.size * 0.7f, r.size * 0.6f, r.size * stretch), along), interp);
        }
    }

    private void gravity() {
        // The hole breathes with the bar: it hauls on beats 1-2 and slackens on 3-4. Run on the slack.
        boolean haul = enc.tempo().beatInBar() < 2;
        double pull = enc.config().d("helios.singularity.pull", 0.045) * strength * (haul ? 1.0 : 0.3);
        double horizonPower = h.power("singularity.horizon", 30);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            Vector3f rel = new Vector3f(CENTER.x - f.x, 0f, CENTER.z - f.z);
            float dist = rel.length();
            if (dist < 0.01f) {
                continue;
            }
            rel.div(dist);
            double k = pull * HMath.clamp(20.0 / Math.max(1.0, dist), 0.35, 1.6);
            if (p.isSneaking()) {
                k *= 0.35;
            }
            Vector swirl = new Vector(-rel.z, 0, rel.x).multiply(k * 0.25);
            Vector v = p.getVelocity().add(new Vector(rel.x * k, 0, rel.z * k)).add(swirl);
            // Never faster inward than a sprint can beat: skilled play escapes.
            double inward = v.getX() * rel.x + v.getZ() * rel.z;
            double cap = enc.config().d("helios.singularity.max-inward", 0.24);
            if (inward > cap) {
                v.subtract(new Vector(rel.x, 0, rel.z).multiply(inward - cap));
            }
            p.setVelocity(v);
            if (dist < horizon) {
                enc.hit(p, horizonPower, "horizon", 16, null);
                if (dist < 1.6f) {
                    spitOut(p);
                }
            }
        }
    }

    /** Touching the core: crushed to 1 HP and thrown back out onto solid ground. */
    private void spitOut(Player p) {
        enc.hit(p, h.power("singularity.horizon", 30) * 1.5, "core", 40, null);
        Vector3f safe = enc.arena().safeSpot(HMath.ring(14f, HMath.angleOf(stage.feet(p).x, stage.feet(p).z) + HMath.PI, 0f));
        org.bukkit.Location to = stage.at(safe.x, 0.2f, safe.z);
        to.setYaw(p.getLocation().getYaw());
        enc.module().teleport(p, to);
        enc.camera().darkness(p, 30);
        enc.grace(p, 40);
        enc.score().to(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    /** Time bends near the hole: lower pitch, a slower sky, narrower vision. */
    private void senses() {
        for (Player p : enc.fighters()) {
            float dist = stage.feet(p).distance(CENTER);
            float near = HMath.clamp01(1f - dist / 16f);
            enc.camera().vignette(p, near * 0.45f);
            if (dist < 6f) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12, 0, false, false, false));
            }
        }
    }

    double dilation(Player p) {
        if (quiet) {
            return 0.6;
        }
        float dist = stage.feet(p).distance(CENTER);
        return 1.0 - 0.28 * HMath.clamp01(1f - dist / 22f);
    }

    double skyRate(Player p) {
        float dist = stage.feet(p).distance(CENTER);
        return HMath.clamp01(dist / 22f);
    }

    /** One of the Course's floating islands cracks and is torn into the disk (never the last few). */
    private void tearIsland() {
        Arena arena = enc.arena();
        List<Integer> intact = new ArrayList<>();
        for (int s = 0; s < ArenaLayout.SECTORS[1]; s++) {
            if (arena.state(1, s) == Arena.SectorState.INTACT) {
                intact.add(s);
            }
        }
        if (intact.size() <= 4) {
            return;
        }
        // Prefer an island nobody is standing on.
        int pick = intact.get(0);
        float best = -1f;
        for (int s : intact) {
            float a = ArenaLayout.sectorAngle(1, s);
            Vector3f mid = HMath.ring(ArenaLayout.ringMid(1), a, 0f);
            float nearest = Float.MAX_VALUE;
            for (Player p : enc.fighters()) {
                nearest = Math.min(nearest, HMath.horizontal(stage.feet(p).sub(mid)));
            }
            if (nearest > best) {
                best = nearest;
                pick = s;
            }
        }
        List<ArenaLayout.Cell> cells = arena.sectorCells(1, pick, false);
        arena.crackSector(1, pick, 1f, 40);
        Tear tear = new Tear();
        tear.replica = arena.replica(g, cells, true);
        tear.pivot = tear.replica.pivot();
        tears.add(tear);
        arena.remove(cells);
        arena.markSector(1, pick, Arena.SectorState.SHATTERED);
        tear.replica.pose(new Quaternionf(), new Vector3f(), 1f, 0);
        enc.score().at(tear.pivot, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.2f, 0.5f);
        enc.score().at(tear.pivot, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.6f);
    }

    private void tickTears() {
        for (int i = tears.size() - 1; i >= 0; i--) {
            Tear tear = tears.get(i);
            tear.t++;
            float f = HMath.window(tear.t, 0, 80);
            Vector3f toward = new Vector3f(CENTER).sub(tear.pivot).mul(HMath.inCubic(f));
            Quaternionf spin = new Quaternionf().rotateY(f * 2.5f).rotateX(f * 0.8f);
            tear.replica.pose(spin, toward, 1f - 0.9f * HMath.inCubic(f), 2);
            if (tear.t >= 80) {
                tear.replica.pose(spin, toward, 0.001f, 0);
                tears.remove(i);
            }
        }
    }

    void release() {
        for (Player p : enc.audience()) {
            enc.camera().vignette(p, 0f);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        enc.score().dilation(null);
        enc.sky().freeze(null);
    }

    /** Everything collapses into the core (death). */
    void collapse(float f) {
        horizonTarget = Math.max(0.1f, horizonTarget * (1f - f));
        for (Rock r : disk) {
            r.r = Math.max(0.2f, r.r * (1f - 0.08f * f));
        }
    }

    void clear() {
        release();
        g.clear();
    }
}
