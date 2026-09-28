package de.aetherion.fishing;

import de.aetherion.core.api.QuestBars;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cast owns the top bar while it lives. Quest bar is held off with a named lease so a
 * fell bar or farm event on the same player can't hand the screen back early.
 */
final class FishingHud {

    static final String LEASE = "fishing";

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    void waiting(Player player, double progress, int remainingTicks, int streak, String tag) {
        paint(player, StrikeBar.waitTitle(remainingTicks, streak, tag), clamp(progress), BarColor.BLUE, BarStyle.SEGMENTED_20);
    }

    void approaching(Player player, int ticks, double closeness, boolean ready, int streak) {
        // Bar fills as the biter closes in; a small shimmer keeps it alive while it circles.
        double shimmer = Math.sin(ticks * 0.35d) * 0.04d;
        paint(
                player,
                StrikeBar.approachTitle(ready, streak),
                clamp(closeness + shimmer),
                ready ? BarColor.YELLOW : BarColor.BLUE,
                BarStyle.SOLID
        );
    }

    void striking(Player player, int marker, int zoneStart, int zoneSize, int perfectWidth, boolean hot, int streak) {
        paint(
                player,
                StrikeBar.strikeTitle(marker, zoneStart, zoneSize, perfectWidth, hot, streak),
                StrikeBar.strikeProgress(marker),
                hot ? BarColor.GREEN : BarColor.YELLOW,
                BarStyle.SEGMENTED_20
        );
    }

    /** Hide the bar and wipe any stale cast line from the action bar. */
    void hide(Player player) {
        if (player == null) {
            return;
        }
        hide(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    /** Hide the bar only — the caller is about to write its own result line. */
    void hideBarOnly(Player player) {
        if (player != null) {
            hide(player.getUniqueId());
        }
    }

    void hide(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BossBar bar = bars.remove(playerId);
        if (bar != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                bar.removePlayer(player);
            }
            bar.removeAll();
            bar.setVisible(false);
        }
        // Only releases our own lease — never un-hides the quest bar for someone else's HUD.
        QuestBars.release(playerId, LEASE);
    }

    void hideAll() {
        for (UUID id : bars.keySet().toArray(UUID[]::new)) {
            hide(id);
        }
        bars.clear();
    }

    private void paint(Player player, String title, double progress, BarColor color, BarStyle style) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar(title, color, style);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            QuestBars.suppress(player, LEASE);
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setProgress(clamp(progress));
        bar.setColor(color);
        bar.setStyle(style);
        bar.setVisible(true);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
