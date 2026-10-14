package de.aetherion.gnomon.ai;

import java.util.UUID;

/**
 * Something the mind just did that the player must be able to see or hear. The encounter turns these into lens snaps,
 * sounds, trail flares and subtitles. A tell is never optional: adaptation without a tell is a hidden cheat.
 */
public record Tell(Kind kind, UUID player, Read read, int sign, Posture posture, boolean firstTime) {

    public enum Kind {
        /** A habit crossed the notice threshold. */
        NOTICED,
        /** A noticed habit faded because the player changed. */
        FORGOTTEN,
        /** An exploit whiffed because the player did the opposite. The Gnomon was baited. */
        OUTPLAYED,
        /** Mood change. */
        POSTURE,
        /** The lens chose a new target. */
        FOCUS
    }

    static Tell noticed(UUID p, Read r, int sign, boolean first) {
        return new Tell(Kind.NOTICED, p, r, sign, null, first);
    }

    static Tell forgotten(UUID p, Read r, boolean first) {
        return new Tell(Kind.FORGOTTEN, p, r, 0, null, first);
    }

    static Tell outplayed(UUID p, Read r, int sign, boolean first) {
        return new Tell(Kind.OUTPLAYED, p, r, sign, null, first);
    }

    static Tell posture(Posture posture) {
        return new Tell(Kind.POSTURE, null, null, 0, posture, false);
    }

    static Tell focus(UUID p) {
        return new Tell(Kind.FOCUS, p, null, 0, null, false);
    }
}
