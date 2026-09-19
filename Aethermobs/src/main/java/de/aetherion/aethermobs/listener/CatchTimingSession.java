package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.pet.PetEntity;
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

    CatchTimingSession(Player player, PetEntity pet, double baseChance) {
        this.playerId = player.getUniqueId();
        this.pet = pet;
        this.baseChance = baseChance;
        QuestBars.suppress(player);
        this.bar = Bukkit.createBossBar(title(false), BarColor.PURPLE, BarStyle.SOLID);
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
        bar.setColor(perfect ? BarColor.GREEN : (hot && !locked ? BarColor.YELLOW : BarColor.PURPLE));
        bar.setTitle(title(hot));
        bar.setProgress(Math.max(0.0d, 1.0d - ticks / (double) DURATION_TICKS));
    }

    boolean tryLock() {
        if (locked) {
            return false;
        }
        locked = true;
        perfect = inZone();
        bar.setColor(perfect ? BarColor.GREEN : BarColor.WHITE);
        bar.setTitle(title(perfect));
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

    private String title(boolean hot) {
        StringBuilder builder = new StringBuilder();
        if (perfect) {
            builder.append("§a✦ Perfect catch window  ");
        } else if (locked) {
            builder.append("§7Timing locked  ");
        } else {
            builder.append(hot ? "§e✦ Click!  " : "§dCatch timing  ");
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
