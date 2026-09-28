package de.aetherion.foraging;

import org.bukkit.entity.Player;

import java.util.UUID;

final class FellPulse {

    final UUID playerId;
    final ForagingListener.TreeJob job;
    final int zoneStart;
    final int zoneSize;
    private final int strikeTicks;
    int marker;
    private int direction = 1;
    private int ticks;
    private boolean resolved;
    private boolean chopped;
    private boolean hit;
    private boolean perfectHit;
    private boolean early;
    /** Ready tell already played on this pass toward the window. */
    private boolean readyCued;

    FellPulse(Player player, ForagingListener.TreeJob job, int strikeTicks, int zoneSize) {
        this.playerId = player.getUniqueId();
        this.job = job;
        this.strikeTicks = Math.max(40, strikeTicks);
        this.zoneSize = ForagingStrike.zoneSize(zoneSize);
        this.zoneStart = ForagingStrike.randomZoneStart(this.zoneSize);
    }

    boolean tick() {
        if (resolved) {
            return true;
        }
        ticks++;
        if (ticks % 2 == 0) {
            marker += direction;
            if (marker >= ForagingStrike.SIZE - 1) {
                marker = ForagingStrike.SIZE - 1;
                direction = -1;
            } else if (marker <= 0) {
                marker = 0;
                direction = 1;
            }
        }
        return ticks >= strikeTicks;
    }

    boolean chop() {
        if (resolved) {
            return false;
        }
        resolved = true;
        chopped = true;
        hit = ForagingStrike.inZone(marker, zoneStart, zoneSize);
        perfectHit = marker == ForagingStrike.perfectCell(zoneStart, zoneSize);
        // The marker bounces: "early" means it hadn't reached the window on its current pass.
        early = direction > 0 ? marker < zoneStart : marker >= zoneStart + zoneSize;
        return true;
    }

    boolean hot() {
        return ForagingStrike.inZone(marker, zoneStart, zoneSize);
    }

    /** Marker is within two cells of the window and heading into it — the creak tell. */
    boolean ready() {
        if (hot()) {
            return false;
        }
        int zoneEnd = zoneStart + zoneSize;
        if (direction > 0) {
            return marker >= zoneStart - 2 && marker < zoneStart;
        }
        return marker >= zoneEnd && marker <= zoneEnd + 1;
    }

    /** True once per pass, the tick the marker becomes {@link #ready()}. */
    boolean readyEdge() {
        boolean ready = ready();
        if (ready && !readyCued) {
            readyCued = true;
            return true;
        }
        if (!ready && !hot()) {
            readyCued = false;
        }
        return false;
    }

    boolean hit() {
        return hit;
    }

    boolean perfectHit() {
        return hit && perfectHit;
    }

    /** Why it missed, for the fail line: early, late, or never swung. */
    String missReason() {
        if (!chopped) {
            return "too slow";
        }
        return early ? "early" : "late";
    }
}
