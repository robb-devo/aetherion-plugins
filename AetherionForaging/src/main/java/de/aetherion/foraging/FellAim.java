package de.aetherion.foraging;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One live fell (A-51): replaces the old timing bar. The cutter looks where the tree should fall; a
 * line on the ground shows the lane (green clear, red where it would hang up), and a gold mark shows
 * the best spot. Click again to fell. Clear lane = clean fell, clear and on the mark = Perfect, hung
 * up on a trunk or a hill = rough. Run out of time and the tree twists its own way (rough).
 */
final class FellAim {

    private static final Particle.DustOptions CLEAR = new Particle.DustOptions(Color.fromRGB(110, 230, 90), 1.1f);
    private static final Particle.DustOptions BLOCKED = new Particle.DustOptions(Color.fromRGB(235, 60, 50), 1.1f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.4f);
    private static final Particle.DustOptions GOLD_HOT = new Particle.DustOptions(Color.fromRGB(255, 240, 120), 1.7f);

    private static final int MIN_AIM_TICKS = 6;

    final UUID playerId;
    final ForagingListener.TreeJob job;
    final Location base;
    final int reach;
    final Vector mark;
    /** Half-width of the Perfect cone, in degrees. */
    final double tolerance;
    private final int budgetTicks;
    private final Set<Long> own;
    private final Map<Integer, FellLane.Result> lanes = new HashMap<>();
    private Vector aim;
    private int ticks;
    private boolean resolved;
    private boolean chopped;
    private boolean clean;
    private boolean perfect;
    private boolean slipCued;
    private FellLane.Result last;

    FellAim(Player player, ForagingListener.TreeJob job, Location base, int reach, Set<Long> own,
            int budgetTicks, double tolerance) {
        this.playerId = player.getUniqueId();
        this.job = job;
        this.base = base;
        this.reach = Math.max(4, reach);
        this.own = own;
        this.budgetTicks = Math.max(60, budgetTicks);
        this.tolerance = tolerance;
        Vector away = base.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.04d) {
            away = player.getLocation().getDirection().setY(0);
        }
        this.aim = away.lengthSquared() < 0.0001d ? new Vector(1, 0, 0) : away.normalize();
        this.mark = FellLane.markDirection(base, this.reach, own, this.aim.clone().multiply(-1));
        this.last = lane(this.aim);
    }

    /** One tick: follow the cutter's eyes. True once the time is up. */
    boolean tick(Player player) {
        if (resolved) {
            return true;
        }
        ticks++;
        if (ticks % 2 == 0) {
            Vector next = lookDirection(player);
            if (next != null) {
                aim = next;
                last = lane(aim);
            }
        }
        if (ticks % 3 == 0) {
            draw(player);
        }
        return ticks >= budgetTicks;
    }

    /** The cutter clicked: the tree goes where it points now. */
    boolean chop() {
        if (resolved || ticks < MIN_AIM_TICKS) {
            // The click that started the fell (or a double-click) never fells it blind.
            return false;
        }
        resolved = true;
        chopped = true;
        last = lane(aim);
        clean = last.clear();
        perfect = clean && onMark();
        return true;
    }

    /** Time ran out: the tree twists a little off the aim, never Perfect, never clean. */
    void slipAway() {
        if (resolved) {
            return;
        }
        resolved = true;
        double twist = Math.toRadians(25.0d + Math.random() * 30.0d) * (Math.random() < 0.5d ? -1.0d : 1.0d);
        aim = rotate(aim, twist);
        last = lane(aim);
        clean = false;
        perfect = false;
    }

    boolean hit() {
        return clean;
    }

    boolean perfectHit() {
        return clean && perfect;
    }

    boolean onMark() {
        return FellLane.degreesBetween(aim, mark) <= tolerance;
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

    double timeLeft() {
        return Math.max(0.0d, 1.0d - ticks / (double) budgetTicks);
    }

    boolean slipping() {
        return !resolved && ticks >= budgetTicks * 0.72d;
    }

    boolean slipEdge() {
        if (!slipCued && slipping()) {
            slipCued = true;
            return true;
        }
        return false;
    }

    String missReason() {
        return chopped ? "hung up" : "too slow";
    }

    /** Where the crown comes to rest along the current aim. */
    Location crownRest() {
        double along = Math.max(2.0d, reach - 2.0d);
        return base.clone().add(aim.getX() * along, 1.0d, aim.getZ() * along);
    }

    Location markSpot() {
        double along = Math.max(2.0d, reach * 0.75d);
        return ground(base.clone().add(mark.getX() * along, 0.0d, mark.getZ() * along));
    }

    // ------------------------------------------------------------------ internals

    private FellLane.Result lane(Vector dir) {
        int bucket = (int) Math.round(Math.toDegrees(Math.atan2(dir.getZ(), dir.getX())) / 4.0d);
        return lanes.computeIfAbsent(bucket, ignored -> FellLane.sweep(base, dir, reach, own));
    }

    /** Ground spot the cutter looks at, as a direction from the stump; null keeps the last aim. */
    private Vector lookDirection(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        if (world != base.getWorld()) {
            return null;
        }
        Vector point = null;
        RayTraceResult ray = world.rayTraceBlocks(eye, look, 28.0d, FluidCollisionMode.NEVER, true);
        if (ray != null && ray.getHitBlock() != null) {
            var hit = ray.getHitBlock();
            if (own.contains(FellLane.key(hit.getX(), hit.getY(), hit.getZ()))) {
                // Looking at the tree itself: it falls away from you.
                Vector away = base.toVector().subtract(player.getLocation().toVector()).setY(0);
                return away.lengthSquared() < 0.04d ? null : away.normalize();
            }
            point = ray.getHitPosition();
        }
        if (point == null) {
            point = eye.toVector().add(look.clone().multiply(16.0d));
        }
        Vector dir = point.subtract(base.toVector()).setY(0);
        if (dir.lengthSquared() < 1.44d) {
            return null;
        }
        return dir.normalize();
    }

    /** The lane on the ground, the crown's rest and the gold mark — for the cutter only. */
    private void draw(Player player) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        FellLane.Result lane = last;
        double stop = lane.clear() ? reach : Math.max(1.0d, Math.sin(Math.toRadians(lane.hitAngle())) * reach);
        for (double d = 1.0d; d <= reach; d += 0.75d) {
            Location at = ground(base.clone().add(aim.getX() * d, 0.0d, aim.getZ() * d)).add(0, 0.15, 0);
            player.spawnParticle(Particle.DUST, at, 1, 0.04, 0.0, 0.04, 0.0, d <= stop ? CLEAR : BLOCKED);
        }
        if (!lane.clear() && lane.hitAt() != null) {
            player.spawnParticle(Particle.DUST, lane.hitAt(), 6, 0.3, 0.3, 0.3, 0.0, BLOCKED);
        }
        boolean hot = lane.clear() && onMark();
        Location spot = markSpot().add(0, 0.12, 0);
        Particle.DustOptions gold = hot ? GOLD_HOT : GOLD;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0d + ticks * 0.08d;
            player.spawnParticle(Particle.DUST, spot.clone().add(Math.cos(a) * 1.1d, 0, Math.sin(a) * 1.1d),
                    1, 0, 0, 0, 0.0, gold);
        }
        player.spawnParticle(Particle.WAX_ON, spot.clone().add(0, 0.6, 0), hot ? 4 : 2, 0.25, 0.35, 0.25, 0.0);
        if (ticks % 9 == 0) {
            player.spawnParticle(Particle.END_ROD, spot.clone().add(0, 0.2, 0), 1, 0.05, 0.4, 0.05, 0.01);
        }
    }

    /** Snap a point to the first standable surface near the stump's height (keeps the line on the ground). */
    private Location ground(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int y = base.getBlockY();
        for (int dy = 2; dy >= -3; dy--) {
            var below = world.getBlockAt(x, y + dy - 1, z);
            var here = world.getBlockAt(x, y + dy, z);
            if (below.getType().isSolid() && !below.isPassable() && (here.isPassable() || here.getType().isAir())) {
                return new Location(world, at.getX(), y + dy, at.getZ());
            }
        }
        return new Location(world, at.getX(), y, at.getZ());
    }

    private static Vector rotate(Vector dir, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(dir.getX() * cos - dir.getZ() * sin, 0.0d, dir.getX() * sin + dir.getZ() * cos).normalize();
    }
}
