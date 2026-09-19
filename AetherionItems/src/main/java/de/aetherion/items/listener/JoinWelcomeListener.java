package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;

public final class JoinWelcomeListener implements Listener {

    private final AetherionItems plugin;

    public JoinWelcomeListener(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> show(player), 12L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                de.aetherion.items.progress.ProgressionUnlock.syncFromCompletedQuests(player);
            }
        }, 40L);
    }

    private void show(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.showTitle(Title.title(
                Component.text("AETHERION", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("welcome", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(350), Duration.ofMillis(2200), Duration.ofMillis(650))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.95f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.25f, 1.15f);
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1.1, 0),
                12,
                0.35,
                0.4,
                0.35,
                0.01
        );
    }
}
