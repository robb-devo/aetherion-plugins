package de.aetherion.gnomon.fx;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compact note-block melody player. Events come from a {@code .nbm} resource
 * ({@code tick instrument note velocity}). Used as GNOMON's fight underscore —
 * looped and private to the fight audience so it never bleeds into the overworld.
 *
 * <p>By default each fighter hears the bed at their own feet ({@link #followPlayers}),
 * so the whole dial stays audible. Optionally the origin + a large volume can be used
 * instead (MC hear-distance ≈ volume × 16 blocks).
 */
public final class NoteMelody {

    public record Note(int tick, Sound sound, int note, float velocity) {
    }

    private final List<Note> notes;
    private final Supplier<Collection<Player>> audience;
    private final Supplier<Location> origin;
    private boolean playing;
    private boolean loop = true;
    /** When true, each audience member hears notes at their own location (full arena). */
    private boolean followPlayers = true;
    private int cursor;
    private int clock;
    private float volume = 0.75f;

    private NoteMelody(List<Note> notes, Supplier<Collection<Player>> audience, Supplier<Location> origin) {
        this.notes = notes;
        this.audience = audience;
        this.origin = origin;
    }

    public static NoteMelody load(InputStream in, Supplier<Collection<Player>> audience, Supplier<Location> origin, Logger log) {
        List<Note> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] p = line.split("\\s+");
                if (p.length < 3) {
                    continue;
                }
                int tick = Integer.parseInt(p[0]);
                Sound sound = instrument(p[1]);
                if (sound == null) {
                    continue;
                }
                int note = Math.max(0, Math.min(24, Integer.parseInt(p[2])));
                float vel = p.length >= 4 ? Math.max(1, Math.min(15, Integer.parseInt(p[3]))) / 15f : 0.8f;
                out.add(new Note(tick, sound, note, vel));
            }
        } catch (IOException | NumberFormatException ex) {
            if (log != null) {
                log.log(Level.WARNING, "[Gnomon] Failed to load note melody", ex);
            }
            return new NoteMelody(List.of(), audience, origin);
        }
        return new NoteMelody(List.copyOf(out), audience, origin);
    }

    public void volume(float v) {
        this.volume = Math.max(0f, Math.min(4f, v));
    }

    public void loop(boolean loop) {
        this.loop = loop;
    }

    /** Prefer player-local playback so the bed covers the whole dial. */
    public void followPlayers(boolean follow) {
        this.followPlayers = follow;
    }

    public void play() {
        if (notes.isEmpty()) {
            return;
        }
        playing = true;
        cursor = 0;
        clock = 0;
    }

    public void stop() {
        playing = false;
        for (Player p : audience.get()) {
            if (p != null && p.isOnline()) {
                p.stopSound(SoundCategory.RECORDS);
            }
        }
    }

    public boolean playing() {
        return playing;
    }

    public int size() {
        return notes.size();
    }

    public void tick() {
        if (!playing || notes.isEmpty()) {
            return;
        }
        Location at = origin.get();
        while (cursor < notes.size() && notes.get(cursor).tick() <= clock) {
            Note n = notes.get(cursor++);
            float pitch = clampPitch((float) Math.pow(2.0, (n.note() - 12) / 12.0));
            float vol = volume * n.velocity();
            for (Player p : audience.get()) {
                if (p == null || !p.isOnline()) {
                    continue;
                }
                if (followPlayers) {
                    // No distance falloff across the dial — everyone in the fight hears the bed.
                    p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, vol, pitch);
                } else if (at != null && at.getWorld() != null) {
                    p.playSound(at, n.sound(), SoundCategory.RECORDS, vol, pitch);
                } else {
                    p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, vol, pitch);
                }
            }
        }
        clock++;
        if (cursor >= notes.size()) {
            if (loop) {
                cursor = 0;
                clock = 0;
            } else {
                playing = false;
            }
        }
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }

    private static Sound instrument(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "harp" -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case "bass" -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case "bit" -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case "pling" -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case "chime" -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case "bell" -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case "guitar" -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case "xylophone", "xylo" -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case "iron", "iron_xylophone" -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case "flute" -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case "banjo" -> Sound.BLOCK_NOTE_BLOCK_BANJO;
            case "didgeridoo", "didge" -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case "cow_bell", "cowbell" -> Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case "hat" -> Sound.BLOCK_NOTE_BLOCK_HAT;
            case "snare" -> Sound.BLOCK_NOTE_BLOCK_SNARE;
            case "basedrum", "drum" -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            default -> null;
        };
    }
}
