package de.aetherion.bossengine.helios.core;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Sound design for the whole encounter: layering, pitch curves, true silence and time dilation.
 *
 * <ul>
 *   <li><b>Layering.</b> Big moments are several vanilla sounds at once, each pitched into its own
 *       register ({@link #chord}), instead of one loud sample.</li>
 *   <li><b>Pitch curves.</b> {@link #sweep} re-triggers a short sound on a curve, which reads as a
 *       rising or falling tone (charge-ups, the seismic whine, the gravity drone).</li>
 *   <li><b>Silence.</b> {@link #silence} stops every sound the players hear and mutes the score, so
 *       only forced cues (a single heartbeat) break it.</li>
 *   <li><b>Dilation.</b> {@link #dilation} lowers the pitch per listener (the singularity bends time
 *       for whoever stands near it) and the stage's slow motion lowers it for everyone.</li>
 *   <li><b>Resource pack.</b> Any vanilla key can be swapped for a custom one via {@code overrides}
 *       (config {@code sounds.overrides}), e.g. {@code entity.warden.heartbeat: helios:heartbeat}.</li>
 * </ul>
 */
public final class Score {

    private final HeliosStage stage;
    private final Map<String, String> overrides;
    private final float master;
    private final List<Voice> voices = new ArrayList<>();
    private int clock;
    private int muteUntil;
    private ToDoubleFunction<Player> dilation = p -> 1.0;

    public Score(HeliosStage stage, Map<String, String> overrides, float master) {
        this.stage = stage;
        this.overrides = overrides == null ? Map.of() : overrides;
        this.master = master <= 0 ? 1f : master;
    }

    public void dilation(ToDoubleFunction<Player> dilation) {
        this.dilation = dilation == null ? p -> 1.0 : dilation;
    }

    public void tick() {
        clock++;
        for (Iterator<Voice> it = voices.iterator(); it.hasNext(); ) {
            Voice v = it.next();
            if (v.tick()) {
                it.remove();
            }
        }
    }

    public boolean muted() {
        return clock < muteUntil;
    }

    /** Stops everything every listener hears and keeps the score quiet for {@code ticks}. */
    public void silence(int ticks) {
        voices.clear();
        muteUntil = clock + Math.max(0, ticks);
        for (Player p : stage.audience()) {
            p.stopAllSounds();
        }
    }

    public void unmute() {
        muteUntil = clock;
    }

    public void stopVoices() {
        voices.clear();
    }

    /** Semitone offset → Minecraft pitch (0 = 1.0). */
    public static float semi(int semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    /* ------------------------------------------------------------------ one-shots */

    /** Heard identically by every listener, at their ear. */
    public void play(Sound sound, float volume, float pitch) {
        play(sound, volume, pitch, false);
    }

    public void play(Sound sound, float volume, float pitch, boolean force) {
        if (!force && muted()) {
            return;
        }
        String key = key(sound);
        for (Player p : stage.audience()) {
            p.playSound(p.getLocation(), key, SoundCategory.HOSTILE, volume * master, pitch(p, pitch));
        }
    }

    /** Positional: comes from a point on the stage. */
    public void at(Vector3fc where, Sound sound, float volume, float pitch) {
        at(where, sound, volume, pitch, false);
    }

    public void at(Vector3fc where, Sound sound, float volume, float pitch, boolean force) {
        if (!force && muted()) {
            return;
        }
        String key = key(sound);
        Location src = stage.at(where);
        for (Player p : stage.audience()) {
            p.playSound(src, key, SoundCategory.HOSTILE, volume * master, pitch(p, pitch));
        }
    }

    /**
     * Directional even when the source is huge or far: played on each listener's side of the source,
     * at most {@code 8} blocks from their ear, so it stays loud and still comes from the right side.
     */
    public void from(Vector3fc where, Sound sound, float volume, float pitch) {
        if (muted()) {
            return;
        }
        String key = key(sound);
        Location src = stage.at(where);
        for (Player p : stage.audience()) {
            Location ear = p.getEyeLocation();
            Vector to = src.toVector().subtract(ear.toVector());
            double d = to.length();
            Location play = d > 8.0 ? ear.clone().add(to.multiply(8.0 / d)) : src;
            p.playSound(play, key, SoundCategory.HOSTILE, volume * master, pitch(p, pitch));
        }
    }

    /** Only one player hears it (their own warnings). */
    public void to(Player p, Sound sound, float volume, float pitch) {
        if (p != null && !muted()) {
            p.playSound(p.getLocation(), key(sound), SoundCategory.HOSTILE, volume * master, pitch(p, pitch));
        }
    }

    /** Several pitches of one sound at once. */
    public void chord(Sound sound, float volume, float... pitches) {
        for (float pitch : pitches) {
            play(sound, volume, pitch);
        }
    }

    public void chordAt(Vector3fc where, Sound sound, float volume, float... pitches) {
        for (float pitch : pitches) {
            at(where, sound, volume, pitch);
        }
    }

    /* ------------------------------------------------------------------ curves */

    /**
     * Re-triggers {@code sound} every {@code every} ticks for {@code ticks}, gliding the pitch from
     * {@code p0} to {@code p1} and the volume from {@code v0} to {@code v1}. Where null, it is heard
     * at every listener's ear; otherwise it comes from {@code where}.
     */
    public Voice sweep(Vector3fc where, Sound sound, float v0, float v1, float p0, float p1, int ticks, int every) {
        Voice v = new Voice(where, sound, v0, v1, p0, p1, Math.max(1, ticks), Math.max(1, every));
        voices.add(v);
        return v;
    }

    public Voice sweep(Sound sound, float volume, float p0, float p1, int ticks, int every) {
        return sweep(null, sound, volume, volume, p0, p1, ticks, every);
    }

    public final class Voice {
        private final Vector3fc where;
        private final Sound sound;
        private final float v0;
        private final float v1;
        private final float p0;
        private final float p1;
        private final int length;
        private final int every;
        private int t;
        private boolean cancelled;

        Voice(Vector3fc where, Sound sound, float v0, float v1, float p0, float p1, int length, int every) {
            this.where = where;
            this.sound = sound;
            this.v0 = v0;
            this.v1 = v1;
            this.p0 = p0;
            this.p1 = p1;
            this.length = length;
            this.every = every;
        }

        public void cancel() {
            cancelled = true;
        }

        boolean tick() {
            if (cancelled) {
                return true;
            }
            if (t % every == 0) {
                float f = t / (float) length;
                float vol = HMath.lerp(v0, v1, f);
                float pitch = HMath.lerp(p0, p1, f);
                if (where == null) {
                    play(sound, vol, pitch);
                } else {
                    at(where, sound, vol, pitch);
                }
            }
            t++;
            return t >= length;
        }
    }

    /* ------------------------------------------------------------------ helpers */

    private String key(Sound sound) {
        String vanilla = sound.getKey().getKey();
        String custom = overrides.get(vanilla);
        return custom == null || custom.isBlank() ? sound.getKey().toString() : custom;
    }

    private float pitch(Player p, float pitch) {
        double scaled = pitch * dilation.applyAsDouble(p);
        float slow = stage.timeScale();
        if (slow < 0.999f) {
            scaled *= 0.55 + 0.45 * slow;
        }
        return (float) HMath.clamp(scaled, 0.5, 2.0);
    }
}
