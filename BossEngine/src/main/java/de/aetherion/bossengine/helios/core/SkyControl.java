package de.aetherion.bossengine.helios.core;

import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Per-player sky: time of day eased toward a target, per-player weather (the arena biome has no
 * precipitation, so DOWNFALL only fades the sun, moon and stars out), and the boss bar sky flags
 * (darken / fog) that {@code HeliosBars} reads. The star's physical state drives it: dusk while it
 * burns, night when it tears, starless inside the singularity, dawn after the supernova.
 *
 * <p>{@link #freeze} bends time per listener: near the singularity the sky stops moving.
 */
public final class SkyControl {

    public static final float DUSK = 12_600f;
    public static final float NIGHT = 18_000f;
    public static final float NOON = 6_000f;
    public static final float DAWN = 23_200f;

    private final HeliosStage stage;
    private final Set<UUID> touched = new HashSet<>();
    private float time = DUSK;
    private float target = DUSK;
    private float rate = 20f;
    private WeatherType weather;
    private boolean darken;
    private boolean fog;
    private ToDoubleFunction<Player> freeze = p -> 1.0;
    private int clock;

    public SkyControl(HeliosStage stage) {
        this.stage = stage;
    }

    /** Ease toward {@code t} (Minecraft day time) at {@code rate} per tick, the short way round. */
    public void to(float t, float rate) {
        this.target = norm(t);
        this.rate = Math.max(1f, rate);
    }

    public void now(float t) {
        this.time = norm(t);
        this.target = this.time;
        apply(true);
    }

    public void weather(WeatherType type) {
        this.weather = type;
        apply(true);
    }

    public void darken(boolean on) {
        this.darken = on;
    }

    public void fog(boolean on) {
        this.fog = on;
    }

    public boolean darken() {
        return darken;
    }

    public boolean fog() {
        return fog;
    }

    public float time() {
        return time;
    }

    /** 1 = sky follows the fight, 0 = frozen for that player. */
    public void freeze(ToDoubleFunction<Player> freeze) {
        this.freeze = freeze == null ? p -> 1.0 : freeze;
    }

    public void tick() {
        clock++;
        if (time != target) {
            float d = target - time;
            if (d > 12_000f) {
                d -= 24_000f;
            } else if (d < -12_000f) {
                d += 24_000f;
            }
            time = Math.abs(d) <= rate ? target : norm(time + Math.signum(d) * rate);
        }
        apply(clock % 20 == 0);
    }

    private final java.util.Map<UUID, Long> sent = new java.util.HashMap<>();

    private void apply(boolean full) {
        for (Player p : stage.audience()) {
            double f = freeze.applyAsDouble(p);
            long t = (long) (f >= 0.999 ? time : lastFor(p, f));
            Long last = sent.get(p.getUniqueId());
            if (full || last == null || Math.abs(last - t) >= 2) {
                p.setPlayerTime(t, false);
                sent.put(p.getUniqueId(), t);
            }
            if (full || !touched.contains(p.getUniqueId())) {
                if (weather == null) {
                    p.resetPlayerWeather();
                } else {
                    p.setPlayerWeather(weather);
                }
            }
            touched.add(p.getUniqueId());
        }
    }

    /** Frozen listeners keep the time they had when they fell under the singularity's spell. */
    private final java.util.Map<UUID, Float> held = new java.util.HashMap<>();

    private float lastFor(Player p, double f) {
        Float h = held.get(p.getUniqueId());
        if (h == null) {
            h = time;
        }
        float next = (float) (h + (time - h) * Math.max(0.0, f) * 0.2);
        held.put(p.getUniqueId(), next);
        return next;
    }

    public void release(Player p) {
        if (p != null) {
            p.resetPlayerTime();
            p.resetPlayerWeather();
            touched.remove(p.getUniqueId());
            held.remove(p.getUniqueId());
            sent.remove(p.getUniqueId());
        }
    }

    public void releaseAll() {
        for (UUID id : new HashSet<>(touched)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.resetPlayerTime();
                p.resetPlayerWeather();
            }
        }
        touched.clear();
        held.clear();
        sent.clear();
    }

    private static float norm(float t) {
        float r = t % 24_000f;
        return r < 0 ? r + 24_000f : r;
    }
}
