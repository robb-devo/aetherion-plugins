package de.aetherion.fishing;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class CastSession {

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

    CastSession(UUID hookId, LureSchool school) {
        this.hookId = hookId;
        this.school = school;
    }

    void beginApproach(int approachLimit) {
        this.phase = Phase.APPROACH;
        this.approachTicks = 0;
        this.approachLimit = Math.max(16, approachLimit);
    }

    void beginStrike(double catchStat, int strikeLimit) {
        this.phase = Phase.STRIKE;
        this.strikeTicks = 0;
        this.strikeLimit = strikeLimit;
        this.zoneSize = StrikeBar.zoneSize(catchStat);
        this.zoneStart = StrikeBar.randomZoneStart(zoneSize, ThreadLocalRandom.current());
        this.marker = 0;
        this.direction = 1;
        this.enteredZone = false;
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
}
