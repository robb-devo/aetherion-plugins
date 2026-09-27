package de.aetherion.bossengine.instance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The sky over the forge answers to the star: dusk while it swells, a hard cut to night the tick it
 * collapses, one flash of noon when it goes supernova. Per-player time only, overworld skies only,
 * and every player it touched gets their own sky back on {@link #release()}.
 */
final class HollowSky {

    private static final double AUDIENCE = 72.0;

    private final Set<UUID> timed = new HashSet<>();
    private double time = -1;
    private double target = -1;
    private double rate;
    private int holdFor;
    private double after = -1;
    private boolean dirty;

    boolean isActive() {
        return time >= 0;
    }

    /** Glide the sky to {@code to} (0..24000) by the shorter way round. */
    void slide(World world, double to, double perTick) {
        if (time < 0) {
            time = world == null ? 6000 : world.getTime() % 24000;
        }
        target = wrap(to);
        rate = Math.max(1.0, perTick);
        holdFor = 0;
        dirty = true;
    }

    /** Snap the sky to {@code to} this tick. */
    void cut(double to) {
        time = wrap(to);
        target = time;
        holdFor = 0;
        dirty = true;
    }

    /** Snap to {@code to} for {@code ticks}, then snap to {@code then}. */
    void flash(double to, int ticks, double then) {
        cut(to);
        holdFor = Math.max(1, ticks);
        after = wrap(then);
    }

    void tick(Location center) {
        if (time < 0 || center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (holdFor > 0 && --holdFor == 0 && after >= 0) {
            time = after;
            target = after;
            after = -1;
            dirty = true;
        }
        boolean moving = time != target;
        if (moving) {
            double delta = shortest(time, target);
            time = Math.abs(delta) <= rate ? target : wrap(time + Math.signum(delta) * rate);
        }
        if (!moving && !dirty && Bukkit.getCurrentTick() % 20 != 0) {
            return;
        }
        dirty = false;
        Set<UUID> seen = new HashSet<>();
        double r2 = AUDIENCE * AUDIENCE;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= r2) {
                player.setPlayerTime((long) time, false);
                seen.add(player.getUniqueId());
            }
        }
        List<UUID> left = new ArrayList<>(timed);
        left.removeAll(seen);
        for (UUID id : left) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
            }
            timed.remove(id);
        }
        timed.addAll(seen);
    }

    /** Everyone gets the real sky back. Idempotent. */
    void release() {
        for (UUID id : timed) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
            }
        }
        timed.clear();
        time = -1;
        target = -1;
        after = -1;
        holdFor = 0;
    }

    private static double wrap(double t) {
        double w = t % 24000.0;
        return w < 0 ? w + 24000.0 : w;
    }

    private static double shortest(double from, double to) {
        double d = wrap(to - from);
        return d > 12000.0 ? d - 24000.0 : d;
    }
}
