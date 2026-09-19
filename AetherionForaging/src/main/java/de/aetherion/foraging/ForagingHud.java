package de.aetherion.foraging;

import de.aetherion.core.api.QuestBars;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ForagingHud {

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    void striking(Player player, int marker, int zoneStart, int zoneSize, boolean hot) {
        paint(
                player,
                ForagingStrike.title(marker, zoneStart, zoneSize, hot),
                ForagingStrike.progress(marker),
                hot ? BarColor.GREEN : BarColor.YELLOW
        );
    }

    void hide(Player player) {
        if (player != null) {
            hide(player.getUniqueId());
        }
    }

    void hide(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BossBar bar = bars.remove(playerId);
        if (bar == null) {
            QuestBars.unsuppress(playerId);
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            bar.removePlayer(player);
            QuestBars.unsuppress(player);
        } else {
            QuestBars.unsuppress(playerId);
        }
        bar.removeAll();
        bar.setVisible(false);
    }

    void hideAll() {
        for (UUID id : bars.keySet().toArray(UUID[]::new)) {
            hide(id);
        }
    }

    private void paint(Player player, String title, double progress, BarColor color) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar(title, color, BarStyle.SEGMENTED_20);
            created.setVisible(true);
            QuestBars.suppress(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
            QuestBars.suppress(player);
        }
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0d, Math.min(1.0d, progress)));
        bar.setColor(color);
        bar.setVisible(true);
    }
}
