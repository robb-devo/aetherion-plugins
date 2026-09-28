package de.aetherion.bossengine.instance.worldeater;

import io.papermc.paper.entity.LookAnchor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The World Eater does not only destroy blocks. It eats the parts of reality every Minecraft player
 * takes for granted, one at a time, per player and entirely client-side:
 *
 * <ul>
 *   <li><b>the sky</b>: per-player time (the sun and moon) and per-player weather. The arena biome has
 *       no precipitation, so "rain" never falls here: it only fades the sun, moon and every star out of
 *       the sky over five seconds. That is how the stars go out.</li>
 *   <li><b>the edge of the world</b>: a virtual per-player world border. It renders as the vanilla
 *       border wall (red while shrinking, green while growing) and is solid to the client.</li>
 *   <li><b>the camera</b>: a gentle forced pan toward something the player must see.</li>
 * </ul>
 *
 * <p>Everything here is restored: on release, when a player leaves the audience, changes world,
 * quits, and on plugin disable ({@link #releaseEverything()}).
 */
public final class Senses {

    private static final Set<Senses> LIVE = new HashSet<>();

    private final World world;
    private final Supplier<List<Player>> skyAudience;
    private final Supplier<List<Player>> borderAudience;

    /* sky */
    private boolean timeOn;
    private float time = 18000f;
    private float timeTarget = 18000f;
    private float timeRate = 40f;
    private final Set<UUID> timed = new HashSet<>();

    private WeatherType weather;
    private final Set<UUID> weathered = new HashSet<>();

    /* edge of the world */
    private WorldBorder border;
    private final Set<UUID> bordered = new HashSet<>();
    private boolean borderOn;
    private double borderX;
    private double borderZ;
    private double borderSize = 64.0;
    private double borderGoal = 64.0;
    private long borderGoalAt;
    private long borderFrom;
    private double borderStart = 64.0;

    private int clock;
    private boolean released;

    public Senses(World world, Supplier<List<Player>> skyAudience, Supplier<List<Player>> borderAudience) {
        this.world = world;
        this.skyAudience = skyAudience;
        this.borderAudience = borderAudience;
        LIVE.add(this);
    }

    /* ================================================================== sky */

    /** Ease the sky toward {@code target} (Minecraft day time) at {@code rate} per tick. */
    public void sky(float target, float rate) {
        timeOn = true;
        timeTarget = target;
        timeRate = Math.max(1f, rate);
    }

    public void skyNow(float t) {
        timeOn = true;
        time = t;
        timeTarget = t;
        applyTime(currentSky());
    }

    public float skyTime() {
        return time;
    }

    public void skyOff() {
        timeOn = false;
        for (UUID id : new ArrayList<>(timed)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.resetPlayerTime();
            }
        }
        timed.clear();
    }

    /** DOWNFALL fades out the sun, moon and stars (no rain falls in the arena biome); CLEAR brings them back. */
    public void weather(WeatherType type) {
        weather = type;
        applyWeather(currentSky(), true);
    }

    public WeatherType weather() {
        return weather;
    }

    public void weatherOff() {
        weather = null;
        for (UUID id : new ArrayList<>(weathered)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.resetPlayerWeather();
            }
        }
        weathered.clear();
    }

    /* ================================================================== edge of the world */

    /** Shows the edge of the world: a square border centered on (x, z). */
    public void border(double x, double z, double size) {
        if (border == null) {
            border = Bukkit.createWorldBorder();
            border.setWarningTime(0);
            border.setDamageAmount(0.0);
            border.setDamageBuffer(0.0);
            border.setWarningDistance(1);
        }
        borderOn = true;
        borderX = x;
        borderZ = z;
        border.setCenter(x, z);
        border.setSize(Math.max(1.0, size));
        borderSize = Math.max(1.0, size);
        borderStart = borderSize;
        borderGoal = borderSize;
        borderFrom = System.currentTimeMillis();
        borderGoalAt = borderFrom;
        applyBorder(currentBorderAudience(), true);
    }

    /** Resize the edge of the world over {@code seconds} (client-smooth: red while shrinking, green while growing). */
    public void borderTo(double size, long seconds) {
        if (border == null || !borderOn) {
            return;
        }
        double now = borderSize();
        double goal = Math.max(1.0, size);
        long secs = Math.max(0L, seconds);
        border.setSize(goal, secs);
        borderStart = now;
        borderGoal = goal;
        borderFrom = System.currentTimeMillis();
        borderGoalAt = borderFrom + secs * 1000L;
    }

    /** The warning vignette: the screen reddens the closer a player stands to the edge. */
    public void borderWarning(int blocks) {
        if (border != null) {
            border.setWarningDistance(Math.max(0, blocks));
        }
    }

    /** Current modelled size (the client lerps the same way). */
    public double borderSize() {
        if (!borderOn) {
            return Double.MAX_VALUE;
        }
        long now = System.currentTimeMillis();
        if (now >= borderGoalAt || borderGoalAt <= borderFrom) {
            borderSize = borderGoal;
            return borderGoal;
        }
        double t = (now - borderFrom) / (double) (borderGoalAt - borderFrom);
        borderSize = borderStart + (borderGoal - borderStart) * t;
        return borderSize;
    }

    public double borderGoal() {
        return borderGoal;
    }

    public boolean borderShown() {
        return borderOn;
    }

    public double borderX() {
        return borderX;
    }

    public double borderZ() {
        return borderZ;
    }

    /** True when the location is inside the edge of the world (or no edge is shown). */
    public boolean inside(Location at) {
        if (!borderOn) {
            return true;
        }
        double half = borderSize() * 0.5;
        return Math.abs(at.getX() - borderX) <= half && Math.abs(at.getZ() - borderZ) <= half;
    }

    /** How far outside the edge a location is (0 when inside). */
    public double outside(Location at) {
        if (!borderOn) {
            return 0.0;
        }
        double half = borderSize() * 0.5;
        double dx = Math.abs(at.getX() - borderX) - half;
        double dz = Math.abs(at.getZ() - borderZ) - half;
        return Math.max(0.0, Math.max(dx, dz));
    }

    public void borderOff() {
        borderOn = false;
        for (UUID id : new ArrayList<>(bordered)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.setWorldBorder(null);
            }
        }
        bordered.clear();
    }

    /* ================================================================== camera */

    /**
     * Turns the player's view a little toward {@code target} (at most {@code maxStep} radians this
     * tick). Called every tick it produces a smooth, insistent pan.
     */
    public static void pan(Player player, Location target, float maxStep) {
        if (player == null || target == null || !player.isOnline() || player.getWorld() != target.getWorld()) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector want = target.toVector().subtract(eye.toVector());
        if (want.lengthSquared() < 1e-4) {
            return;
        }
        want.normalize();
        Vector have = eye.getDirection().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, have.dot(want)));
        double angle = Math.acos(dot);
        Vector look;
        if (angle <= maxStep || angle < 1e-3) {
            look = want;
        } else {
            double t = maxStep / angle;
            double sin = Math.sin(angle);
            if (sin < 1e-4) {
                look = want;
            } else {
                double a = Math.sin((1 - t) * angle) / sin;
                double b = Math.sin(t * angle) / sin;
                look = have.clone().multiply(a).add(want.clone().multiply(b)).normalize();
            }
        }
        Location point = eye.clone().add(look.multiply(12.0));
        player.lookAt(point.getX(), point.getY(), point.getZ(), LookAnchor.EYES);
    }

    /* ================================================================== tick */

    public void tick() {
        if (released) {
            return;
        }
        clock++;
        if (timeOn && Math.abs(time - timeTarget) > 0.5f) {
            time += Math.signum(timeTarget - time) * Math.min(timeRate, Math.abs(timeTarget - time));
        }
        if (clock % 5 == 0) {
            List<Player> sky = currentSky();
            if (timeOn) {
                applyTime(sky);
            }
            applyWeather(sky, false);
            applyBorder(currentBorderAudience(), false);
        }
    }

    private List<Player> currentSky() {
        List<Player> out = skyAudience == null ? List.of() : skyAudience.get();
        return out == null ? List.of() : out;
    }

    private List<Player> currentBorderAudience() {
        List<Player> out = borderAudience == null ? List.of() : borderAudience.get();
        return out == null ? List.of() : out;
    }

    private void applyTime(List<Player> audience) {
        Set<UUID> seen = new HashSet<>();
        long value = (long) Math.floorMod((long) time, 24000L * 1000L);
        for (Player p : audience) {
            p.setPlayerTime(value, false);
            seen.add(p.getUniqueId());
        }
        for (UUID id : new ArrayList<>(timed)) {
            if (!seen.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.resetPlayerTime();
                }
                timed.remove(id);
            }
        }
        timed.addAll(seen);
    }

    private void applyWeather(List<Player> audience, boolean force) {
        if (weather == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (Player p : audience) {
            seen.add(p.getUniqueId());
            if (force || !weathered.contains(p.getUniqueId()) || p.getPlayerWeather() != weather) {
                p.setPlayerWeather(weather);
            }
        }
        for (UUID id : new ArrayList<>(weathered)) {
            if (!seen.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.resetPlayerWeather();
                }
                weathered.remove(id);
            }
        }
        weathered.addAll(seen);
    }

    private void applyBorder(List<Player> audience, boolean force) {
        if (!borderOn || border == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (Player p : audience) {
            if (p.getWorld() != world) {
                continue;
            }
            seen.add(p.getUniqueId());
            if (force || !bordered.contains(p.getUniqueId()) || p.getWorldBorder() != border) {
                p.setWorldBorder(border);
            }
        }
        for (UUID id : new ArrayList<>(bordered)) {
            if (!seen.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.setWorldBorder(null);
                }
                bordered.remove(id);
            }
        }
        bordered.addAll(seen);
    }

    /* ================================================================== release */

    /** One player walks out of the scene (quit, world change, left the arena). */
    public void release(Player p) {
        if (p == null) {
            return;
        }
        UUID id = p.getUniqueId();
        if (timed.remove(id)) {
            p.resetPlayerTime();
        }
        if (weathered.remove(id)) {
            p.resetPlayerWeather();
        }
        if (bordered.remove(id) && p.isOnline()) {
            p.setWorldBorder(null);
        }
    }

    public void releaseAll() {
        skyOff();
        weatherOff();
        borderOff();
    }

    /** Final: restore everyone and forget this scene. Idempotent. */
    public void close() {
        releaseAll();
        released = true;
        LIVE.remove(this);
    }

    public boolean closed() {
        return released;
    }

    /** Listener hook: a player quit or changed world; every scene lets go of them. */
    public static void forget(Player p) {
        for (Senses s : new ArrayList<>(LIVE)) {
            s.release(p);
        }
    }

    /** Plugin disable. */
    public static void releaseEverything() {
        for (Senses s : new ArrayList<>(LIVE)) {
            s.close();
        }
        LIVE.clear();
    }
}
