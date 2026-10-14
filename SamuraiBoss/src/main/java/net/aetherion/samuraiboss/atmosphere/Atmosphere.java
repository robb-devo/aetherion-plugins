package net.aetherion.samuraiboss.atmosphere;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.util.Rand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stimmung pro Spieler: Tageszeit & Wetter (nur clientseitig – die echte Welt bleibt
 * unverändert), roter Bildschirmrand, Dunkelheit, Zeitlupe, Kamerawackeln, Titel.
 * Alles wird beim Verlassen/Kampfende exakt zurückgesetzt.
 */
public final class Atmosphere {

    private static final class State {
        long timeFrom, timeTo;
        int timeStart, timeDur;
        boolean timeActive;
        WorldBorder border;
        float tintNow, tintTarget;
        int shakeTicks;
        float shakeStrength;
    }

    private final Settings s;
    private final Location center;
    private final Map<UUID, State> states = new HashMap<>();
    private int tick;
    // gewünschter Grundzustand (für neu hinzukommende Spieler)
    private long baseTime = -1;
    private WeatherType baseWeather = null;
    private float baseTint = 0f;

    public Atmosphere(Settings s, Location center) {
        this.s = s;
        this.center = center;
    }

    private State st(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new State());
    }

    // ------------------------------------------------------------------ Teilnehmer

    /** Ein Spieler betritt den Kampf: aktuellen Grundzustand anwenden. */
    public void join(Player p) {
        State st = st(p);
        if (s.sky && baseTime >= 0) {
            st.timeFrom = p.getPlayerTime() % 24000;
            st.timeTo = baseTime;
            st.timeStart = tick;
            st.timeDur = 40;
            st.timeActive = true;
        }
        if (s.sky && baseWeather != null) p.setPlayerWeather(baseWeather);
        st.tintTarget = baseTint;
    }

    /** Spieler verlässt den Kampf: alles zurücksetzen. */
    public void leave(Player p) {
        State st = states.remove(p.getUniqueId());
        restore(p, st);
    }

    private void restore(Player p, State st) {
        if (!p.isOnline()) return;
        p.resetPlayerTime();
        p.resetPlayerWeather();
        if (st != null && st.border != null) p.setWorldBorder(null);
        clearEffect(p, PotionEffectType.DARKNESS);
        clearEffect(p, PotionEffectType.BLINDNESS);
        clearEffect(p, PotionEffectType.SLOWNESS);
    }

    private static void clearEffect(Player p, PotionEffectType t) {
        PotionEffect e = p.getPotionEffect(t);
        // Nur kurze Effekte entfernen (unsere sind nie länger als 15 s)
        if (e != null && e.getDuration() <= 300 && !e.hasParticles()) p.removePotionEffect(t);
    }

    public void restoreAll(Collection<Player> players) {
        for (Player p : players) restore(p, states.remove(p.getUniqueId()));
        // Spieler, die nicht mehr in der Liste sind, aber Zustand haben
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p, states.get(id));
        }
        states.clear();
    }

    // ------------------------------------------------------------------ Himmel

    /** Weicher Übergang der Tageszeit für alle (Ticks). */
    public void timeTo(Collection<Player> players, long time, int ticks) {
        baseTime = time;
        if (!s.sky) return;
        for (Player p : players) {
            State st = st(p);
            st.timeFrom = p.getPlayerTime() % 24000;
            st.timeTo = time;
            st.timeStart = tick;
            st.timeDur = Math.max(1, ticks);
            st.timeActive = true;
        }
    }

    public void weather(Collection<Player> players, WeatherType w) {
        baseWeather = w;
        if (!s.sky) return;
        for (Player p : players) {
            if (w == null) p.resetPlayerWeather();
            else p.setPlayerWeather(w);
        }
    }

    /** Roter Rand: 0 = aus, 1 = stark. Läuft weich auf den Zielwert. */
    public void tint(float intensity) {
        baseTint = s.redTint ? intensity : 0f;
        for (State st : states.values()) st.tintTarget = baseTint;
    }

    // ------------------------------------------------------------------ Körper-Effekte

    public void darkness(Collection<Player> players, int ticks) {
        for (Player p : players) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, true, false, false));
    }

    public void blind(Player p, int ticks) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, true, false, false));
    }

    /** Zeitlupe: starke Verlangsamung (inkl. Zoom-Effekt des Sichtfelds). */
    public void slow(Collection<Player> players, int ticks, int amplifier) {
        for (Player p : players) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier, true, false, false));
    }

    /** Kamerawackeln über die (schadenlose) Treffer-Animation der Kamera. */
    public void shake(Collection<Player> players, float strength, int ticks) {
        if (!s.screenShake) return;
        for (Player p : players) {
            State st = st(p);
            st.shakeTicks = Math.max(st.shakeTicks, ticks);
            st.shakeStrength = Math.max(st.shakeStrength, strength);
        }
    }

    public void shakeNear(Collection<Player> players, Location at, float strength, int ticks, double radius) {
        List<Player> near = new ArrayList<>();
        for (Player p : players) if (p.getWorld() == at.getWorld() && p.getLocation().distanceSquared(at) < radius * radius) near.add(p);
        shake(near, strength, ticks);
    }

    // ------------------------------------------------------------------ Text

    public void title(Collection<Player> players, Component main, Component sub, int in, int stay, int out) {
        if (!s.titles) return;
        Title t = Title.title(main, sub, Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L)));
        for (Player p : players) p.showTitle(t);
    }

    public void actionBar(Collection<Player> players, Component c) {
        for (Player p : players) p.sendActionBar(c);
    }

    // ------------------------------------------------------------------ Tick

    public void tick(Collection<Player> players) {
        tick++;
        for (Player p : players) {
            State st = st(p);
            if (st.timeActive && tick % 2 == 0) {
                float u = Math.min(1f, (tick - st.timeStart) / (float) st.timeDur);
                float e = u * u * (3 - 2 * u);
                long from = st.timeFrom, to = st.timeTo;
                // kürzester Weg (vorwärts oder rückwärts) – nie einmal "um den ganzen Tag"
                long diff = ((to - from) % 24000 + 24000) % 24000;
                if (diff > 12000) diff -= 24000;
                long time = from + Math.round(diff * e);
                p.setPlayerTime(((time % 24000) + 24000) % 24000, false);
                if (u >= 1f) st.timeActive = false;
            }
            if (Math.abs(st.tintNow - st.tintTarget) > 0.01f && tick % 2 == 0) {
                st.tintNow += Math.signum(st.tintTarget - st.tintNow) * Math.min(0.04f, Math.abs(st.tintTarget - st.tintNow));
                applyTint(p, st);
            }
            if (st.shakeTicks > 0) {
                if (st.shakeTicks % 2 == 0) p.playHurtAnimation(Rand.f() * 360f);
                st.shakeTicks--;
                if (st.shakeTicks == 0) st.shakeStrength = 0;
            }
        }
    }

    private void applyTint(Player p, State st) {
        if (st.tintNow <= 0.01f) {
            if (st.border != null) {
                p.setWorldBorder(null);
                st.border = null;
            }
            return;
        }
        if (st.border == null) {
            st.border = Bukkit.createWorldBorder();
            st.border.setCenter(center);
            st.border.setSize(2000);
            st.border.setWarningTime(0);
            st.border.setDamageAmount(0);
            st.border.setDamageBuffer(1000);
            p.setWorldBorder(st.border);
        }
        // Abstand zum Rand ~1000 Blöcke. Der Client färbt den Rand mit Stärke 1 - Abstand/Warnabstand,
        // also Warnabstand = 1000 / (1 - Stärke).
        float strength = Math.min(0.88f, st.tintNow * 0.88f);
        int warn = (int) (1000 / (1 - strength));
        st.border.setWarningDistance(warn);
    }
}
