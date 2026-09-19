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

final class FishingHud {

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    void waiting(Player player, double progress, int remainingTicks) {
        paint(player, StrikeBar.waitTitle(remainingTicks), clamp(progress), BarColor.BLUE, BarStyle.SEGMENTED_20);
    }

    void approaching(Player player, int ticks) {
        double pulse = 0.46d + Math.sin(ticks * 0.22d) * 0.22d;
        paint(player, StrikeBar.approachTitle(), clamp(pulse), BarColor.BLUE, BarStyle.SOLID);
    }

    void striking(Player player, int marker, int zoneStart, int zoneSize, boolean hot) {
        paint(
                player,
                StrikeBar.strikeTitle(marker, zoneStart, zoneSize, hot),
                StrikeBar.strikeProgress(marker),
                hot ? BarColor.GREEN : BarColor.YELLOW,
                BarStyle.SEGMENTED_20
        );
    }

    void hide(Player player) {
        if (player == null) {
            return;
        }
        hide(player.getUniqueId());
        player.sendActionBar(Component.empty());
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
        QuestBars.unsuppress(playerId);
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
            QuestBars.suppress(player);
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
