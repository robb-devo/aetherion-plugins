package de.aetherion.foraging;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.BarSync;
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
 * The fell bar owns the top of the screen while a fell is being aimed. Holds a named QuestBars
 * lease so a fishing bar or farm event can't hand the screen back mid-aim.
 */
final class ForagingHud {

    static final String LEASE = "fell";

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    /** Live aim: green when the lane is clear and on the gold ring, yellow when clear, red when it would hit. */
    void aiming(Player player, FellAim aim, int streak) {
        boolean german = AetherServices.german(player);
        boolean clear = aim.laneClear();
        boolean ring = clear && aim.onMark();
        paint(
                player,
                ForagingStrike.aimTitle(german, clear, ring, aim.slipping(), streak),
                aim.timeLeft(),
                !clear ? BarColor.RED : ring ? BarColor.GREEN : BarColor.YELLOW
        );
    }

    /** Tutorial: the Forager is aiming — same bar, driven by the demo. */
    void demo(Player player, boolean onRing) {
        boolean german = AetherServices.german(player);
        paint(player, ForagingStrike.aimTitle(german, true, onRing, false, 0), 1.0d,
                onRing ? BarColor.GREEN : BarColor.YELLOW);
    }

    /** One reused BossBar per player — look preview, never a second bar. */
    void preview(Player player, int streak) {
        paint(
                player,
                ForagingStrike.previewTitle(AetherServices.german(player), streak),
                0.0d,
                BarColor.WHITE
        );
    }

    void previewCooling(Player player, long secondsLeft) {
        paint(
                player,
                ForagingStrike.coolingTitle(AetherServices.german(player), secondsLeft),
                0.0d,
                BarColor.RED
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
            BossBar created = Bukkit.createBossBar("", color, BarStyle.SEGMENTED_20);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            QuestBars.suppress(player, LEASE);
            bar.addPlayer(player);
        }
        // Gather frame + axe; per tick, so only what changed goes out.
        BarSync.title(bar, BarChrome.legacy(BarChrome.Family.GATHER, BarChrome.Icon.AXE, title));
        BarSync.progress(bar, progress);
        BarSync.color(bar, color);
        BarSync.visible(bar, true);
    }
}
