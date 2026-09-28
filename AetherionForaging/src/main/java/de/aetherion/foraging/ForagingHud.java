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

/**
 * The fell bar owns the top of the screen while a chop is live. Holds a named QuestBars
 * lease so a fishing bar or farm event can't hand the screen back mid-swing.
 */
final class ForagingHud {

    static final String LEASE = "fell";

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    /** Tutorial / legacy shape — no streak, no ready tell. */
    void striking(Player player, int marker, int zoneStart, int zoneSize, boolean hot) {
        striking(player, marker, zoneStart, zoneSize, hot, false, 0);
    }

    void striking(Player player, int marker, int zoneStart, int zoneSize, boolean hot, boolean ready, int streak) {
        paint(
                player,
                ForagingStrike.title(marker, zoneStart, zoneSize, hot, ready, streak),
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
        if (bar != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                bar.removePlayer(player);
            }
            bar.removeAll();
            bar.setVisible(false);
        }
        // Only our lease — never un-hides the quest bar over someone else's HUD.
        QuestBars.release(playerId, LEASE);
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
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            QuestBars.suppress(player, LEASE);
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0d, Math.min(1.0d, progress)));
        bar.setColor(color);
        bar.setVisible(true);
    }
}
