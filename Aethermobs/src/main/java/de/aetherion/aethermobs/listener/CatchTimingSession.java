package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.BarSync;
import de.aetherion.core.api.QuestBars;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Optional timing window during a sphere catch. Miss = no penalty (1.0x).
 */
final class CatchTimingSession {

    static final int BAR_SIZE = 21;
    static final int ZONE_START = 8;
    static final int ZONE_SIZE = 5;
    static final int DURATION_TICKS = 36;
    static final double PERFECT_MULT = 1.35d;

    final UUID playerId;
    final PetEntity pet;
    final double baseChance;
    final BossBar bar;

    int ticks;
    int marker;
    int direction = 1;
    boolean locked;
    boolean perfect;

    private boolean wasHot;
    private boolean justEntered;
    private boolean closed;

    CatchTimingSession(Player player, PetEntity pet, double baseChance) {
        this.playerId = player.getUniqueId();
        this.pet = pet;
        this.baseChance = baseChance;
        QuestBars.suppress(player);
        this.bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        BarSync.title(bar, title(false));
        this.bar.setProgress(1.0d);
        this.bar.addPlayer(player);
        this.bar.setVisible(true);
    }

    void pulse() {
        if (!locked) {
            marker += direction;
            if (marker >= BAR_SIZE - 1) {
                marker = BAR_SIZE - 1;
                direction = -1;
            } else if (marker <= 0) {
                marker = 0;
                direction = 1;
            }
        }
        ticks++;
        boolean hot = inZone();
        justEntered = hot && !wasHot && !locked;
        wasHot = hot;
        BarSync.color(bar, perfect ? BarColor.GREEN : (hot && !locked ? BarColor.YELLOW : BarColor.PURPLE));
        BarSync.title(bar, title(hot));
        BarSync.progress(bar, 1.0d - ticks / (double) DURATION_TICKS);
    }

    /** True on the tick the marker slides into the green — the listener ticks a cue. */
    boolean justEnteredZone() {
        return justEntered;
    }

    boolean tryLock() {
        if (locked) {
            return false;
        }
        locked = true;
        perfect = inZone();
        BarSync.color(bar, perfect ? BarColor.GREEN : BarColor.WHITE);
        BarSync.title(bar, title(perfect));
        return true;
    }

    double multiplier() {
        return perfect ? PERFECT_MULT : 1.0d;
    }

    double boostedChance() {
        return Math.max(0.0d, Math.min(100.0d, baseChance * multiplier()));
    }

    boolean done() {
        return ticks >= DURATION_TICKS;
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        bar.removeAll();
        bar.setVisible(false);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            bar.removePlayer(player);
            QuestBars.unsuppress(player);
        } else {
            QuestBars.unsuppress(playerId);
        }
    }

    private boolean inZone() {
        return marker >= ZONE_START && marker < ZONE_START + ZONE_SIZE;
    }

    /** Gather frame (same timing family as the cast and the fell) with a paw in the socket. */
    private String title(boolean hot) {
        return BarChrome.legacy(BarChrome.Family.GATHER, BarChrome.Icon.PAW, line(hot));
    }

    private String line(boolean hot) {
        StringBuilder builder = new StringBuilder();
        if (perfect) {
            builder.append("§a✦ Perfect  ");
        } else if (locked) {
            builder.append("§7Locked  ");
        } else {
            builder.append(hot ? "§e✦ Now  " : "§dHold it  ");
        }
        for (int i = 0; i < BAR_SIZE; i++) {
            boolean zone = i >= ZONE_START && i < ZONE_START + ZONE_SIZE;
            if (i == marker) {
                builder.append(perfect ? "§a┃" : (hot ? "§e┃" : "§f┃"));
            } else if (zone) {
                builder.append("§a▌");
            } else {
                builder.append("§8▌");
            }
        }
        return builder.toString();
    }
}
