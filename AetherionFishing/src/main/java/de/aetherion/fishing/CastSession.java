package de.aetherion.fishing;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One live cast. Package code drives the phases; {@link CastHooks} may reshape the strike bar
 * (narrower window, faster marker, wider gold cell) and park per-cast data in {@link #tag()}.
 */
public final class CastSession {

    enum Phase {
        WAIT,
        APPROACH,
        STRIKE
    }

    final UUID hookId;
    final LureSchool school;
    Phase phase = Phase.WAIT;
    int ticks;
    int approachTicks;
    int approachLimit;
    int strikeTicks;
    int strikeLimit;
    int marker;
    int zoneStart;
    int zoneSize;
    int direction = 1;
    boolean enteredZone;
    boolean resolved;
    boolean schoolSpawned;
    /** True until the bobber has settled in water — no minigame on land/players. */
    boolean waitingForWater = true;
    int waitTotal;
    int waitLeft;
    int lastRemaining;
    /** Approach telegraph fired ("ready…") — plays its cue once. */
    boolean readyCued;
    /** HUD suffix from the hooks, refreshed while waiting. */
    String hudTag;
    /** Gold cells in the middle of the window (1 normally). */
    private int perfectWidth = 1;
    /** Marker moves 2 cells every 3 ticks instead of 1 every 2. */
    private boolean fastMarker;
    private Object tag;

    CastSession(UUID hookId, LureSchool school) {
        this.hookId = hookId;
        this.school = school;
    }

    void beginApproach(int approachLimit) {
        this.phase = Phase.APPROACH;
        this.approachTicks = 0;
        this.approachLimit = Math.max(16, approachLimit);
    }

    void beginStrike(double catchStat, int strikeLimit, int streak) {
        this.phase = Phase.STRIKE;
        this.strikeTicks = 0;
        this.strikeLimit = strikeLimit;
        this.zoneSize = StrikeBar.zoneSize(catchStat, streak);
        this.zoneStart = StrikeBar.randomZoneStart(zoneSize, ThreadLocalRandom.current());
        this.marker = 0;
        this.direction = 1;
        this.enteredZone = false;
    }

    /** True on the ticks the marker should step. */
    boolean markerDue() {
        return fastMarker ? strikeTicks % 3 != 2 : strikeTicks % 2 == 0;
    }

    void pulseMarker() {
        marker += direction;
        if (marker >= StrikeBar.SIZE - 1) {
            marker = StrikeBar.SIZE - 1;
            direction = -1;
        } else if (marker <= 0) {
            marker = 0;
            direction = 1;
        }
    }

    boolean inZone() {
        return StrikeBar.inZone(marker, zoneStart, zoneSize);
    }

    boolean onPerfect() {
        return phase == Phase.STRIKE && StrikeBar.onPerfect(marker, zoneStart, zoneSize, perfectWidth);
    }

    int perfectWidth() {
        return perfectWidth;
    }

    // ------------------------------------------------------------------ hooks API

    /** Current green window, in cells. */
    public int zoneSize() {
        return zoneSize;
    }

    /** Grow ({@code +}) or shrink ({@code -}) the green window; it stays at least two cells and on the bar. */
    public void resizeZone(int delta) {
        int size = Math.max(2, Math.min(StrikeBar.SIZE - 3, zoneSize + delta));
        if (size == zoneSize) {
            return;
        }
        zoneSize = size;
        zoneStart = StrikeBar.randomZoneStart(zoneSize, ThreadLocalRandom.current());
        perfectWidth = Math.min(perfectWidth, zoneSize);
    }

    /** Gold cells in the middle of the window (1–3). */
    public void perfectWidth(int width) {
        this.perfectWidth = Math.max(1, Math.min(Math.min(3, zoneSize), width));
    }

    /** A thrashing fish: the marker runs a third faster. */
    public void fastMarker(boolean fast) {
        this.fastMarker = fast;
    }

    public boolean fastMarker() {
        return fastMarker;
    }

    /** Hook-owned data for this cast (e.g. what is on the line). */
    public Object tag() {
        return tag;
    }

    public void tag(Object value) {
        this.tag = value;
    }
}
