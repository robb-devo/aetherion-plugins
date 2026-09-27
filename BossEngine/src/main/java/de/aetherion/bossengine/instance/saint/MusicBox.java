package de.aetherion.bossengine.instance.saint;

import org.bukkit.Sound;

/**
 * Seraphine's lullaby, played on a music box (note-block chime over a soft harp root).
 *
 * <p>The same eight bars recur through the fight: slow and sweet in the intro, spun fast
 * during the pirouette, detuned while she cuts her strings, and winding down (each note later
 * and flatter than the last) as she dies. The Severed Waltz lands its cuts on the downbeats,
 * so the melody itself teaches the combo's rhythm.
 */
final class MusicBox {

    /** Semitones on the note-block scale (0 = F#). -1 = rest. Three beats per bar. */
    static final int[] THEME = {
            22, 18, 15, 17, 14, 17,
            18, 15, 10, 12, -1, -1,
            22, 18, 15, 20, 17, 14,
            15, 13, 10, 10, -1, -1,
    };
    /** Harp root for each bar, played on beat one. */
    static final int[] ROOTS = {3, 10, 3, 10};

    private final SaintFx fx;
    private boolean playing;
    private int beat;
    private float clock;
    private float ticksPerBeat = 8f;
    private float pitchMul = 1f;
    private float volume = 0.9f;
    private boolean loop;
    /** Death: every beat is longer and flatter until the spring runs out. */
    private boolean windingDown;

    MusicBox(SaintFx fx) {
        this.fx = fx;
    }

    void play(float ticksPerBeat, float pitchMul, float volume, boolean loop) {
        this.playing = true;
        this.beat = 0;
        this.clock = 0f;
        this.ticksPerBeat = ticksPerBeat;
        this.pitchMul = pitchMul;
        this.volume = volume;
        this.loop = loop;
        this.windingDown = false;
    }

    void windDown() {
        windingDown = true;
        loop = false;
    }

    void stop() {
        playing = false;
    }

    boolean playing() {
        return playing;
    }

    int beat() {
        return beat;
    }

    void tick() {
        if (!playing) {
            return;
        }
        clock -= 1f;
        if (clock > 0f) {
            return;
        }
        if (beat >= THEME.length) {
            if (!loop) {
                playing = false;
                return;
            }
            beat = 0;
        }
        note(beat);
        beat++;
        clock += ticksPerBeat;
        if (windingDown) {
            ticksPerBeat *= 1.16f;
            pitchMul *= 0.985f;
            volume *= 0.95f;
            if (ticksPerBeat > 60f) {
                playing = false;
            }
        }
    }

    private void note(int index) {
        int n = THEME[index];
        if (index % 3 == 0) {
            int root = ROOTS[(index / 6) % ROOTS.length];
            fx.score(Sound.BLOCK_NOTE_BLOCK_HARP, volume * 0.45f, clampPitch(SaintMath.semitone(root) * pitchMul));
        }
        if (n < 0) {
            return;
        }
        float pitch = clampPitch(SaintMath.semitone(n) * pitchMul);
        fx.score(Sound.BLOCK_NOTE_BLOCK_CHIME, volume, pitch);
        fx.score(Sound.BLOCK_NOTE_BLOCK_BELL, volume * 0.18f, pitch);
    }

    /** A single theme note, for scripted beats outside the loop. */
    void single(int index, float pitchMul, float volume) {
        int n = THEME[Math.floorMod(index, THEME.length)];
        if (n < 0) {
            return;
        }
        float pitch = clampPitch(SaintMath.semitone(n) * pitchMul);
        fx.score(Sound.BLOCK_NOTE_BLOCK_CHIME, volume, pitch);
        fx.score(Sound.BLOCK_NOTE_BLOCK_BELL, volume * 0.2f, pitch);
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }
}
