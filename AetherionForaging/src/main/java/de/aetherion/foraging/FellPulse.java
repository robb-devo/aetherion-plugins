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
    private boolean hit;

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
        hit = ForagingStrike.inZone(marker, zoneStart, zoneSize);
        return true;
    }

    boolean hot() {
        return ForagingStrike.inZone(marker, zoneStart, zoneSize);
    }

    boolean hit() {
        return hit;
    }
}
