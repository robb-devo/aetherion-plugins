package de.aetherion.foraging;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One live fell (2026-11-27, Robb: "das Hingucken ist weird, eine Anzahl Schläge, eine Kerbe, easy to understand").
 * <ul>
 *   <li>A tree needs a number of hits on its trunk; the axe sets how many ({@link #hitsNeeded}). The hit that starts
 *   the fell counts.</li>
 *   <li>Every hit cuts the notch deeper: the stump block shows the break crack stage by stage (only for the cutter),
 *   chips fly, leaves drift from the crown. Nothing to explain.</li>
 *   <li>Now and then a spot on the trunk glows gold for a moment, on the side you are standing. Hit it while it
 *   glows and the hit counts twice; the last hit on the glow is a <b>clean cut</b> (the old Perfect). Missing it
 *   costs nothing.</li>
 *   <li>The tree falls away from you. If that lane is blocked it picks the nearest free side by itself; only when
 *   every side is blocked does it hang up (the old "rough fell").</li>
 *   <li>Stop hitting for a while and the fell just ends (no fell, no penalty); the crack fades.</li>
 * </ul>
 * Kept the class name and the read-outs the listener, the HUD and the isle hooks use.
 */
final class FellAim {

    private static final Particle.DustOptions GLOW = new Particle.DustOptions(Color.fromRGB(255, 205, 60), 1.0f);
    private static final Particle.DustOptions GLOW_CORE = new Particle.DustOptions(Color.fromRGB(255, 245, 170), 0.7f);
    /** Ticks between two hits that still count (a fast, honest swing rhythm; no auto-clicker). */
    private static final int MIN_HIT_GAP = 4;
    /** Degrees tried around "away from the cutter" when that lane is blocked. */
    private static final int[] TURNS = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};

    final UUID playerId;
    final ForagingListener.TreeJob job;
    final Location base;
    final int reach;
    final int hitsNeeded;
    private final Set<Long> own;
    private final int idleTicks;
    private final int glowTicks;
    private final int damageId;
    private final Location stump;
    private final Map<Integer, FellLane.Result> lanes = new HashMap<>();
    private final Vector aim;
    private final FellLane.Result last;
    private int hits;
    private int ticks;
    private int lastHitTick;
    private int glowUntil = -1;
    private int nextGlow;
    private Vector glowAt;
    private boolean lastSweet;
    private boolean resolved;
    private boolean clean;
    private boolean perfect;
    private boolean warned;

    /**
     * @param hitsNeeded hits this tree takes with the cutter's axe (at least 2)
     * @param idleTicks  no hit for this long and the fell ends quietly
     * @param glowTicks  how long the gold spot stays lit (skills, streak and isle marks make it longer)
     */
    FellAim(Player player, ForagingListener.TreeJob job, Location base, int reach, Set<Long> own,
            int idleTicks, int hitsNeeded, int glowTicks) {
        this.playerId = player.getUniqueId();
        this.job = job;
        this.base = base;
        this.reach = Math.max(4, reach);
        this.own = own;
        this.idleTicks = Math.max(60, idleTicks);
        this.hitsNeeded = Math.max(2, hitsNeeded);
        this.glowTicks = Math.max(8, glowTicks);
        this.damageId = (playerId.hashCode() * 31) ^ base.getBlockX() * 73 ^ base.getBlockZ() * 197 ^ 0x5EED;
        this.stump = new Location(base.getWorld(), base.getBlockX(), base.getBlockY(), base.getBlockZ());
        Vector away = base.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.04d) {
            away = player.getLocation().getDirection().setY(0);
        }
        away = away.lengthSquared() < 0.0001d ? new Vector(1, 0, 0) : away.normalize();
        Vector chosen = away;
        FellLane.Result chosenLane = null;
        for (int turn : TURNS) {
            Vector dir = rotate(away, Math.toRadians(turn));
            FellLane.Result lane = lane(dir);
            if (lane.clear()) {
                chosen = dir;
                chosenLane = lane;
                break;
            }
            if (chosenLane == null) {
                chosenLane = lane;
            }
        }
        this.aim = chosen;
        this.last = chosenLane == null ? lane(chosen) : chosenLane;
        // The hit that started the fell is the first cut.
        this.hits = 1;
        this.lastHitTick = 0;
        this.nextGlow = 6 + ThreadLocalRandom.current().nextInt(10);
        feedback(player, false);
    }

    /** One tick: the glowing spot comes and goes. True once the cutter has stopped hitting for too long. */
    boolean tick(Player player) {
        if (resolved) {
            return false;
        }
        ticks++;
        if (glowAt == null && ticks >= nextGlow) {
            startGlow(player);
        } else if (glowAt != null && ticks > glowUntil) {
            glowAt = null;
            nextGlow = ticks + 18 + ThreadLocalRandom.current().nextInt(18);
        }
        if (glowAt != null && ticks % 2 == 0) {
            drawGlow(player);
        }
        if (ticks % 40 == 0) {
            crack(player);
        }
        return ticks - lastHitTick >= idleTicks;
    }

    /** A swing on this tree's trunk. True when the notch is through and the tree falls. */
    boolean chop(Player player) {
        if (resolved || ticks - lastHitTick < MIN_HIT_GAP) {
            return false;
        }
        boolean sweet = glowAt != null && ticks <= glowUntil && aimsAtGlow(player);
        hits += sweet ? 2 : 1;
        lastHitTick = ticks;
        lastSweet = sweet;
        if (sweet) {
            glowAt = null;
            nextGlow = ticks + 14 + ThreadLocalRandom.current().nextInt(14);
        }
        if (hits >= hitsNeeded) {
            resolved = true;
            clean = last.clear();
            perfect = clean && sweet;
            clearCrack(player);
            return true;
        }
        feedback(player, sweet);
        return false;
    }

    /** The cutter walked off or switched trees: the crack fades. */
    void release(Player player) {
        clearCrack(player);
    }

    // ------------------------------------------------------------------ read-outs (listener, HUD, isle)

    /** The tree found a free lane (a blocked one hangs up: the old rough fell). */
    boolean hit() {
        return clean;
    }

    /** Last hit on the glowing spot: a clean cut. */
    boolean perfectHit() {
        return clean && perfect;
    }

    int hits() {
        return Math.min(hits, hitsNeeded);
    }

    /** The gold spot is lit right now. */
    boolean glowing() {
        return glowAt != null && ticks <= glowUntil;
    }

    boolean lastWasSweet() {
        return lastSweet;
    }

    boolean laneClear() {
        return last.clear();
    }

    FellLane.Result result() {
        return last;
    }

    Vector direction() {
        return aim.clone();
    }

    /** Share of the notch already cut, 0..1 (the HUD bar). */
    double progress() {
        return Math.min(1.0d, hits / (double) hitsNeeded);
    }

    String missReason() {
        return "hung up";
    }

    /** Where the crown comes to rest along the fall. */
    Location crownRest() {
        double along = Math.max(2.0d, reach - 2.0d);
        return base.clone().add(aim.getX() * along, 1.0d, aim.getZ() * along);
    }

    // ------------------------------------------------------------------ internals

    private FellLane.Result lane(Vector dir) {
        int bucket = (int) Math.round(Math.toDegrees(Math.atan2(dir.getZ(), dir.getX())) / 4.0d);
        return lanes.computeIfAbsent(bucket, ignored -> FellLane.sweep(base, dir, reach, own));
    }

    /** A spot on the trunk side facing the cutter, between knee and head height. */
    private void startGlow(Player player) {
        World world = base.getWorld();
        if (world == null || player.getWorld() != world) {
            return;
        }
        Vector centre = new Vector(stump.getBlockX() + 0.5d, 0.0d, stump.getBlockZ() + 0.5d);
        Vector toCutter = player.getLocation().toVector().setY(0).subtract(centre);
        if (toCutter.lengthSquared() < 0.0001d) {
            toCutter = aim.clone().multiply(-1);
        }
        toCutter.normalize();
        Vector side = new Vector(-toCutter.getZ(), 0.0d, toCutter.getX());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double height = 0.45d + random.nextDouble() * 1.25d;
        double across = (random.nextDouble() - 0.5d) * 0.6d;
        glowAt = centre.clone().add(toCutter.clone().multiply(0.53d)).add(side.multiply(across))
                .setY(stump.getBlockY() + height);
        glowUntil = ticks + glowTicks;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.8f);
    }

    private void drawGlow(Player player) {
        World world = base.getWorld();
        if (world == null || glowAt == null) {
            return;
        }
        Location at = glowAt.toLocation(world);
        double phase = ticks * 0.6d;
        for (int i = 0; i < 6; i++) {
            double a = phase + i * Math.PI / 3.0d;
            Vector offset = new Vector(Math.cos(a) * 0.16d, Math.sin(a) * 0.16d, 0.0d);
            player.spawnParticle(Particle.DUST, at.clone().add(offset), 1, 0.0d, 0.0d, 0.0d, 0.0d, GLOW);
        }
        player.spawnParticle(Particle.DUST, at, 2, 0.03d, 0.03d, 0.03d, 0.0d, GLOW_CORE);
    }

    /** The cutter's eye line passes close to the glowing spot (a swing at the right place). */
    private boolean aimsAtGlow(Player player) {
        World world = base.getWorld();
        if (world == null || player.getWorld() != world || glowAt == null) {
            return false;
        }
        Location eye = player.getEyeLocation();
        RayTraceResult ray = world.rayTraceBlocks(eye, eye.getDirection(), 6.0d, FluidCollisionMode.NEVER, true);
        if (ray == null || ray.getHitBlock() == null) {
            return false;
        }
        var block = ray.getHitBlock();
        if (!own.contains(FellLane.key(block.getX(), block.getY(), block.getZ()))) {
            return false;
        }
        return ray.getHitPosition().distanceSquared(glowAt) <= 0.55d * 0.55d;
    }

    /** Chips, a dull tock, a deeper crack, leaves from the crown; the last-but-one hit creaks. */
    private void feedback(Player player, boolean sweet) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        crack(player);
        Location chips = stump.clone().add(0.5d, 0.9d, 0.5d);
        BlockData wood = woodData();
        world.spawnParticle(Particle.BLOCK, chips, sweet ? 26 : 14, 0.3d, 0.35d, 0.3d, 0.0d, wood);
        world.playSound(chips, Sound.BLOCK_WOOD_HIT, 1.0f, 0.75f + ThreadLocalRandom.current().nextFloat() * 0.15f);
        world.playSound(chips, Sound.ITEM_AXE_STRIP, sweet ? 0.9f : 0.45f, sweet ? 1.25f : 0.8f);
        if (sweet) {
            world.spawnParticle(Particle.WAX_ON, chips, 8, 0.25d, 0.3d, 0.25d, 0.0d);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 1.6f);
        }
        Location crown = job.crownLocation();
        if (crown != null && crown.getWorld() == world) {
            world.spawnParticle(Particle.BLOCK, crown.clone().add(0.0d, 0.5d, 0.0d), 8, 1.4d, 0.5d, 1.4d, 0.0d, leafData());
        }
        if (!warned && hits >= hitsNeeded - 1) {
            warned = true;
            world.playSound(chips, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.35f, 0.55f);
        }
    }

    private void crack(Player player) {
        if (player == null || stump.getWorld() == null || player.getWorld() != stump.getWorld()) {
            return;
        }
        float progress = (float) Math.max(0.12d, Math.min(0.95d, hits / (double) hitsNeeded));
        try {
            player.sendBlockDamage(stump, progress, damageId);
        } catch (RuntimeException ignored) {
        }
    }

    private void clearCrack(Player player) {
        if (player == null || stump.getWorld() == null || player.getWorld() != stump.getWorld()) {
            return;
        }
        try {
            player.sendBlockDamage(stump, 0.0f, damageId);
        } catch (RuntimeException ignored) {
        }
    }

    private BlockData woodData() {
        Material family = job.family;
        return (family == null || !family.isBlock() ? Material.OAK_LOG : family).createBlockData();
    }

    private BlockData leafData() {
        Material family = job.family;
        Material leaves = Material.OAK_LEAVES;
        if (family != null) {
            Material match = Material.matchMaterial(family.name()
                    .replace("STRIPPED_", "")
                    .replace("_LOG", "_LEAVES")
                    .replace("_WOOD", "_LEAVES"));
            if (match != null && match.isBlock()) {
                leaves = match;
            }
        }
        return leaves.createBlockData();
    }

    private static Vector rotate(Vector dir, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(dir.getX() * cos - dir.getZ() * sin, 0.0d, dir.getX() * sin + dir.getZ() * cos).normalize();
    }
}
