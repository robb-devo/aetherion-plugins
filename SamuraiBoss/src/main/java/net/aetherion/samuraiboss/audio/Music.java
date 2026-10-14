package net.aetherion.samuraiboss.audio;

import net.aetherion.samuraiboss.Settings;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prozedurale Kampfmusik aus Notenblock-Klängen in der japanischen In-Tonleiter
 * (Miyako-bushi: E F A B C). Koto = Harfe, Shamisen = Banjo, Shakuhachi = Flöte,
 * Taiko = Bassdrum, Kane = Kuhglocke. Jede Phase hat ihr eigenes Stück, das Tempo steigt.
 *
 * <p>Notation pro Spur: "schritt:NOTE" in Sechzehnteln, z. B. "0:E4 3:A4 4:B4@0.5".
 * Tonhöhen relativ zu Fis4 (Notenblock-Tonhöhe 1.0), Bereich Fis3–Fis5.</p>
 */
public final class Music {

    public record N(int step, int semi, float vol) {
    }

    public record Track(String instrument, int length, float volume, N[] notes) {
    }

    public record Piece(String name, float bpm, boolean loop, int length, Track[] tracks) {
    }

    private static final Map<String, Integer> NOTE = new HashMap<>();

    static {
        String[] names = {"F#3", "G3", "G#3", "A3", "A#3", "B3", "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4",
                "A#4", "B4", "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5"};
        for (int i = 0; i < names.length; i++) NOTE.put(names[i], i - 12);
    }

    public static Track t(String instrument, int length, float volume, String pattern) {
        List<N> notes = new ArrayList<>();
        for (String tok : pattern.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] a = tok.split(":");
            int step = Integer.parseInt(a[0]);
            String note = a[1];
            float vol = 1f;
            int at = note.indexOf('@');
            if (at >= 0) {
                vol = Float.parseFloat(note.substring(at + 1));
                note = note.substring(0, at);
            }
            Integer semi = NOTE.get(note);
            if (semi == null) throw new IllegalArgumentException("Unbekannte Note " + note);
            notes.add(new N(step, semi, vol));
        }
        return new Track(instrument, length, volume, notes.toArray(new N[0]));
    }

    // ================================================================== Stücke

    /** Phase 1 – 「静」 Stille: spärliche Koto, atmende Shakuhachi, Herzschlag-Taiko. */
    public static final Piece SEI = new Piece("sei", 72, true, 64, new Track[]{
            t("harp", 64, 0.55f, "0:E4 3:A4 4:B4 6:C5 8:B4 12:A4 16:F4 19:E4 20:F4 22:A4 24:E4 "
                    + "32:A3 34:C4 35:E4 36:A4 38:B4 40:C5 42:E5 44:C5 46:B4 48:A4 52:F4 56:E4"),
            t("bass", 64, 0.55f, "0:A3 16:B3 32:A3 48:C4"),
            t("flute", 64, 0.32f, "0:E5 2:E5@0.6 4:E5@0.35 6:E5@0.2 24:B4 26:B4@0.6 28:B4@0.35 40:C5 42:C5@0.6 44:B4@0.5 46:A4@0.4 48:A4@0.25"),
            t("basedrum", 64, 0.85f, "0:C4 3:C4@0.6 32:C4 35:C4@0.6"),
            t("bell", 64, 0.28f, "0:E5 40:A4@0.7"),
    });

    /** Phase 2 – 「嵐」 Sturm: treibende Taiko, Shamisen-Ostinato, Koto-Melodie. */
    public static final Piece ARASHI = new Piece("arashi", 116, true, 64, new Track[]{
            t("basedrum", 32, 1.0f, "0:C4 3:C4@0.6 6:C4@0.7 8:C4 12:C4@0.6 14:C4@0.7 16:C4 19:C4@0.6 22:C4@0.7 24:C4 26:C4@0.6 28:C4@0.7 30:C4@0.5"),
            t("hat", 32, 0.45f, "2:F#4 10:F#4 18:F#4 26:F#4"),
            t("snare", 32, 0.5f, "4:F#4 12:F#4 20:F#4 28:F#4@0.8 30:F#4@0.6"),
            t("banjo", 16, 0.42f, "0:E4 2:E4 4:A4 6:E4 8:B4 10:E4 12:A4 14:F4"),
            t("harp", 64, 0.55f, "0:B4 2:C5 4:B4 6:A4 8:F4 12:E4 16:A4 18:B4 20:C5 22:E5 24:F5 28:E5 "
                    + "32:C5 34:B4 36:A4 38:B4 40:C5 44:B4 48:A4 50:F4 52:E4 56:F4 60:E4"),
            t("didgeridoo", 16, 0.3f, "0:A3"),
    });

    /** Phase 3 – 「散華」: Taiko-Wirbel, Tempelglocken, Kane, aufsteigende Arpeggien. */
    public static final Piece SANGE = new Piece("sange", 138, true, 64, new Track[]{
            t("basedrum", 16, 1.0f, "0:C4 2:C4@0.55 4:C4@0.8 6:C4@0.55 8:C4 10:C4@0.55 11:C4@0.5 12:C4@0.85 14:C4@0.7 15:C4@0.6"),
            t("hat", 4, 0.35f, "1:F#4 3:F#4@0.7"),
            t("cow_bell", 32, 0.3f, "4:A4 12:A4 20:A4 28:B4"),
            t("bell", 64, 0.5f, "0:E5 4:F5 6:E5 8:C5 12:B4 16:A4 20:B4 22:C5 24:E5 28:F5 "
                    + "32:F5 34:E5 36:C5 40:B4 44:A4 48:B4 50:C5 52:B4 54:A4 56:F4 60:E4"),
            t("pling", 16, 0.28f, "0:A4 1:C5 2:E5 3:F5 4:E5 5:C5 6:A4 7:F4 8:E4 9:F4 10:A4 11:B4 12:C5 13:B4 14:A4 15:F4"),
            t("didgeridoo", 64, 0.42f, "0:A3 16:F#3 32:A3 48:B3"),
    });

    /** Tod – 「辞世」: absteigende Koto, Flöte, letzte Glocke. Läuft einmal. */
    public static final Piece JISEI = new Piece("jisei", 58, false, 72, new Track[]{
            t("harp", 72, 0.6f, "0:E5 4:C5 8:B4 12:A4 16:F4 20:E4 28:C4 32:B3 40:A3 56:E4@0.5 60:A3@0.5"),
            t("flute", 72, 0.3f, "0:A4 2:A4@0.5 4:A4@0.3 16:F4 18:F4@0.5 20:E4@0.4 32:E4 34:E4@0.5 36:E4@0.3"),
            t("bell", 72, 0.45f, "0:A4 32:E4 64:A3"),
    });

    /** Wiederaufbau – 「春」 Frühling: helle Yo-Pentatonik mit Glocken. */
    public static final Piece HARU = new Piece("haru", 92, true, 32, new Track[]{
            t("chime", 32, 0.32f, "0:F#4 2:G#4 4:B4 6:C#5 8:D#5 10:C#5 12:B4 14:G#4 16:F#4 18:B4 20:C#5 22:F#5 24:D#5 26:C#5 28:B4 30:G#4"),
            t("bell", 32, 0.4f, "0:F#5 16:C#5"),
            t("harp", 32, 0.45f, "0:F#3 8:C#4 16:B3 24:C#4"),
    });

    // ================================================================== Abspielen

    private final Settings settings;
    private Piece current;
    private double pos;
    private int lastStep = -1;
    private float gain = 1f;
    private float gainTarget = 1f;
    private float gainStep = 0.05f;

    public Music(Settings settings) {
        this.settings = settings;
    }

    public void play(Piece p) {
        if (p == current) return;
        current = p;
        pos = 0;
        lastStep = -1;
        gain = 1f;
        gainTarget = 1f;
    }

    /** Ausblenden über 'ticks' Ticks und dann stoppen. */
    public void stop(int ticks) {
        if (current == null) return;
        if (ticks <= 0) {
            current = null;
            return;
        }
        gainTarget = 0f;
        gainStep = 1f / ticks;
    }

    public Piece current() {
        return current;
    }

    public void tick(Collection<Player> listeners) {
        if (current == null || !settings.music) return;
        if (gain != gainTarget) {
            gain += Math.signum(gainTarget - gain) * Math.min(gainStep, Math.abs(gainTarget - gain));
            if (gain <= 0.001f && gainTarget == 0f) {
                current = null;
                return;
            }
        }
        pos += current.bpm * 4.0 / 1200.0;
        int step = (int) Math.floor(pos);
        if (!current.loop && step >= current.length) {
            current = null;
            return;
        }
        while (lastStep < step) {
            lastStep++;
            playStep(lastStep, listeners);
        }
    }

    private void playStep(int step, Collection<Player> listeners) {
        float master = settings.musicVolume * gain;
        if (master <= 0.001f) return;
        for (Track tr : current.tracks) {
            int s = step % tr.length;
            for (N n : tr.notes) {
                if (n.step != s) continue;
                float vol = tr.volume * n.vol * master;
                float pitch = (float) Math.pow(2.0, n.semi / 12.0);
                String key = soundKey(tr.instrument);
                for (Player p : listeners) p.playSound(p, key, SoundCategory.RECORDS, vol, pitch);
            }
        }
    }

    private String soundKey(String instrument) {
        if (settings.packEnabled && settings.customSounds) {
            if (instrument.equals("harp")) return "samurai:koto";
            if (instrument.equals("basedrum")) return "samurai:taiko";
            if (instrument.equals("flute")) return "samurai:shakuhachi";
        }
        return "block.note_block." + instrument;
    }
}
