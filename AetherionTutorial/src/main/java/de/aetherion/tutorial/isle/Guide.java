package de.aetherion.tutorial.isle;

import de.aetherion.core.api.BarChrome;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

/**
 * What leads a player without a word being read: the bar at the top (what to do now, an arrow, the metres), a
 * golden glow over the spot or person the step is about, and a pillar of light when that is far away. Cards mark
 * the moments something was learnt.
 */
final class Guide {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 96), 1.25f);
    private static final Particle.DustOptions GOLD_SOFT = new Particle.DustOptions(Color.fromRGB(255, 232, 170), 0.9f);

    private Guide() {
    }

    // ------------------------------------------------------------------ the bar

    /**
     * @param objective what to do now, eight words at most (legacy colours allowed)
     * @param progress  0..1: how far the step is, or negative to show the way instead (closer = fuller)
     */
    static void bar(Player player, Session session, BarChrome.Icon icon, String objective, float progress,
                    Location target) {
        String text = "§e" + Txt.t(player, "Morningbell", "Morningbell") + " §8· §f" + objective;
        float fill = progress;
        BossBar.Color color = BossBar.Color.YELLOW;
        if (target != null && target.getWorld() == player.getWorld()) {
            double d = player.getLocation().distance(target);
            if (d > 5.0) {
                text += " §8· §e" + Txt.arrow(player, target) + " §7" + (int) d + "m";
            }
            if (progress < 0f) {
                fill = (float) Math.max(0.05d, Math.min(1.0d, 1.0d - d / 110.0d));
            }
        } else if (progress < 0f) {
            fill = 0.1f;
        }
        if (progress >= 0f) {
            color = BossBar.Color.GREEN;
            fill = Math.max(0.02f, Math.min(1.0f, progress));
        }
        Component title = Txt.legacy(BarChrome.legacy(BarChrome.Family.QUEST, icon == null ? BarChrome.Icon.QUEST : icon, text));
        BossBar bar = session.bar;
        if (bar == null) {
            bar = BossBar.bossBar(title, fill, color, BossBar.Overlay.PROGRESS);
            session.bar = bar;
            player.showBossBar(bar);
            return;
        }
        bar.name(title);
        bar.progress(fill);
        bar.color(color);
    }

    static void hide(Player player, Session session) {
        if (session.bar != null) {
            if (player != null) {
                player.hideBossBar(session.bar);
            }
            session.bar = null;
        }
    }

    // ------------------------------------------------------------------ light on the goal

    /** A golden shimmer over the spot (only this player sees it). {@code lift}: how far above the feet. */
    static void glow(Player player, Location at, double lift, long tick) {
        if (at == null || at.getWorld() != player.getWorld()) {
            return;
        }
        double d2 = at.distanceSquared(player.getLocation());
        Location top = at.clone().add(0.0, lift, 0.0);
        if (d2 < 36.0 * 36.0) {
            double turn = (tick % 40L) / 40.0d * Math.PI * 2.0d;
            for (int i = 0; i < 3; i++) {
                double a = turn + i * (Math.PI * 2.0d / 3.0d);
                player.spawnParticle(Particle.DUST, top.clone().add(Math.cos(a) * 0.45, 0.05 * i, Math.sin(a) * 0.45),
                        1, 0.0, 0.0, 0.0, 0.0, GOLD);
            }
            player.spawnParticle(Particle.DUST, top, 1, 0.12, 0.18, 0.12, 0.0, GOLD_SOFT);
        }
        if (d2 > 14.0 * 14.0) {
            // far away: a thin pillar of light that can be seen across the island
            for (int i = 0; i < 16; i++) {
                player.spawnParticle(Particle.DUST, top.getX(), top.getY() + 0.6 + i * 1.1, top.getZ(), 1,
                        0.03, 0.2, 0.03, 0.0, i % 2 == 0 ? GOLD : GOLD_SOFT, true);
            }
        }
    }

    // ------------------------------------------------------------------ cards

    /** A big card in the middle of the screen; a title where cards are off. */
    static void card(Player player, String header, String title, List<String> lines, List<ItemStack> icons,
                     String next, String accent) {
        try {
            if (de.aetherion.quests.api.Cards.big(player, header, title, lines, icons, next, accent, null)) {
                return;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Quests is not here: say it the old way
        }
        String sub = lines == null || lines.isEmpty() ? "" : lines.get(0);
        player.showTitle(Title.title(
                Component.text(strip(title), NamedTextColor.GOLD, TextDecoration.BOLD),
                Txt.legacy(sub),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(700))));
    }

    /** A small card at the top; an action-bar line where cards are off. */
    static void toast(Player player, String title, String detail, String next) {
        try {
            if (de.aetherion.quests.api.Cards.toast(player, title, detail, next)) {
                return;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Quests is not here
        }
        Txt.actionBar(player, "§a✔ §f" + title + (detail == null || detail.isBlank() ? "" : " §8· §7" + detail));
    }

    /** A small card at the top with a star instead of the tick: something seen, not something done. */
    static void note(Player player, String title, List<String> lines, String next) {
        try {
            if (de.aetherion.quests.api.Cards.note(player, "✦", title, lines, next)) {
                return;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Quests is not here
        }
        Txt.actionBar(player, "§e✦ §f" + title);
    }

    /** The place card: the island's name, big, once. */
    static void place(Player player, String title, String subtitle) {
        player.showTitle(Title.title(
                Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD),
                Txt.legacy(subtitle),
                Title.Times.times(Duration.ofMillis(900), Duration.ofMillis(3200), Duration.ofMillis(900))));
    }

    private static String strip(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("§.", "");
    }
}
