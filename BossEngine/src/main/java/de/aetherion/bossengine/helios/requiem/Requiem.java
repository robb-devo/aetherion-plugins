package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Lang;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

/**
 * THE REQUIEM (6 %). Everything stops. Three seconds of real silence while Helios rises over the pit
 * and the singularity falls still. Then the last movement: forty-eight beats at 120 BPM in D minor
 * (i – VI – III – VII), and every attack of the fight is an instrument in it.
 *
 * <pre>
 *   bars  1-2   plasma rings on every beat, jump / duck / jump / duck        (the kick)
 *   bars  3-4   plasma on 1 and 3, flares counted in to land on 2 and 4     (the snare)
 *   bars  5-6   portal chain: opens on 5.1, fires on 6.1; plasma on 1s
 *   bars  7-8   the seismic bomb: armed across bar 7, silence on 8.3, boom on 8.4
 *   bars  9-10  the icosahedron closes around the party; solar wind on 10.1
 *   bars 11-12  meteors on every half beat; plasma low / high on 12.1 / 12.3
 *   12.4        cut. One chord. The heart comes down: the window.
 * </pre>
 *
 * During the chart Helios cannot be hurt. The window lasts {@code helios.requiem.window-seconds} and
 * damage to the heart is multiplied. If the heart survives it, the last eight bars play again.
 */
final class Requiem {

    enum Stage { SILENCE, CHART, WINDOW }

    private static final int SILENCE_TICKS = 60;
    private static final int BEATS = 48;
    /** D minor progression per bar: i, VI, III, VII (note block semitones from F#). */
    private static final int[][] CHORDS = {{8, 11, 15}, {4, 8, 11}, {11, 15, 18}, {6, 10, 13}};
    private static final int[] ROOTS = {8, 4, 11, 6};

    private final HeliosScript h;
    private final HeliosEncounter enc;
    private Stage stage = Stage.SILENCE;
    private int t;
    private int beat = -1;
    private int startBeat;
    private int windowLeft;
    private int rounds;

    Requiem(HeliosScript h) {
        this.h = h;
        this.enc = h.encounter();
    }

    Stage stage() {
        return stage;
    }

    boolean exposed() {
        return stage == Stage.WINDOW;
    }

    void begin() {
        stage = Stage.SILENCE;
        t = 0;
        h.stopAttacks();
        enc.score().silence(SILENCE_TICKS);
        enc.tempo().bpm(enc.config().bpm("requiem", 120), 0);
        enc.sky().darken(true);
        for (Player p : enc.audience()) {
            p.sendActionBar(TextUtil.component(Lang.pick(p, "&f&oStille.", "&f&oSilence.")));
        }
    }

    /** @return true while the Requiem owns the fight */
    boolean tick() {
        t++;
        switch (stage) {
            case SILENCE -> {
                h.glideTo(new Vector3f(0f, 10f, 0f), 0.08f);
                if (t == SILENCE_TICKS - 10) {
                    enc.score().play(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f, true);
                }
                if (t >= SILENCE_TICKS) {
                    stage = Stage.CHART;
                    startBeat = enc.tempo().beat() + 1;
                    enc.score().unmute();
                    enc.camera().title("", "&fRequiem", 5, 30, 10);
                }
            }
            case CHART -> chart();
            case WINDOW -> window();
        }
        return true;
    }

    private void chart() {
        if (!enc.tempo().onBeat()) {
            return;
        }
        beat = enc.tempo().beat() - startBeat + (rounds > 0 ? 32 : 0);
        if (beat < 0) {
            return;
        }
        music(beat);
        int bar = beat / 4;
        int inBar = beat % 4;
        if (bar <= 1) {
            h.spawnHazard(new PlasmaRings(h, 1, beat % 2 == 0 ? PlasmaRings.Kind.LOW : PlasmaRings.Kind.HIGH));
        } else if (bar <= 3) {
            if (inBar == 0 || inBar == 2) {
                h.spawnHazard(new PlasmaRings(h, 1, inBar == 0 ? PlasmaRings.Kind.LOW : PlasmaRings.Kind.HIGH));
            } else if (inBar == 3 && bar == 2) {
                // Counted in two beats: they land on the 2 and the 4 of bar 4.
                h.spawnHazard(new SolarFlares(h, 3));
            }
        } else if (bar <= 5) {
            if (bar == 4 && inBar == 0) {
                h.spawnHazard(new PortalBeams(h, 4));
            }
            if (inBar == 0 && bar == 5) {
                h.spawnHazard(new PlasmaRings(h, 1, PlasmaRings.Kind.HIGH));
            }
        } else if (bar <= 7) {
            if (bar == 6 && inBar == 0) {
                h.spawnHazard(new SeismicBomb(h, null, 5));
            }
        } else if (bar <= 9) {
            if (bar == 8 && inBar == 0) {
                h.spawnHazard(new GeometryCage(h, GeometryCage.Kind.ICOSA, 6));
            }
            if (bar == 9 && inBar == 0) {
                h.spawnHazard(new SolarWind(h, 2));
            }
        } else if (bar <= 11) {
            if (bar == 10 && inBar == 0) {
                h.spawnHazard(new MeteorRain(h, 8, 0.5));
            }
            if (bar == 11 && (inBar == 0 || inBar == 2)) {
                h.spawnHazard(new PlasmaRings(h, 1, inBar == 0 ? PlasmaRings.Kind.LOW : PlasmaRings.Kind.HIGH));
            }
        }
        if (beat >= BEATS - 1) {
            openWindow();
        }
    }

    /** Kick on every beat, snare on 2 and 4, the bass and the chord on each bar. */
    private void music(int b) {
        Score s = enc.score();
        int bar = b / 4;
        int chord = bar % 4;
        s.play(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.7f);
        if (b % 2 == 1) {
            s.play(Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8f, 1f);
        }
        if (b % 4 == 0) {
            s.play(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, note(ROOTS[chord]));
            for (int n : CHORDS[chord]) {
                s.play(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.55f, note(n));
                s.play(Sound.BLOCK_NOTE_BLOCK_BELL, 0.35f, note(n));
            }
        } else if (b % 4 == 2) {
            s.play(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, note(ROOTS[chord] + 7));
        }
    }

    private static float note(int n) {
        int clamped = Math.max(0, Math.min(24, n));
        return Score.semi(clamped - 12);
    }

    private void openWindow() {
        stage = Stage.WINDOW;
        h.stopAttacks();
        windowLeft = Math.max(4, enc.config().i("helios.requiem.window-seconds", 12)) * 20;
        Score s = enc.score();
        s.silence(4);
        s.play(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, note(8), true);
        for (int n : CHORDS[0]) {
            s.play(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.9f, note(n), true);
            s.play(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, note(n + 12), true);
        }
        s.play(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f, true);
        enc.camera().flash(1, 6);
        h.exposeHeart(true);
        for (Player p : enc.audience()) {
            p.sendActionBar(TextUtil.component(Lang.pick(p, "&6&lDas Herz liegt offen!", "&6&lThe heart lies open!")));
        }
    }

    private void window() {
        windowLeft--;
        if (windowLeft % 20 == 0) {
            enc.score().play(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f + 0.3f * HMath.clamp01(1f - windowLeft / 240f));
        }
        if (windowLeft <= 0) {
            // It held. Once more, from bar 9.
            rounds++;
            h.exposeHeart(false);
            stage = Stage.CHART;
            startBeat = enc.tempo().beat() + 1;
            for (Player p : enc.audience()) {
                p.sendActionBar(TextUtil.component(Lang.pick(p, "&cDas Requiem beginnt von vorn…", "&cThe requiem plays again…")));
            }
        }
    }

    float windowProgress() {
        int total = Math.max(4, enc.config().i("helios.requiem.window-seconds", 12)) * 20;
        return stage == Stage.WINDOW ? windowLeft / (float) total : 0f;
    }
}
