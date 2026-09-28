package de.aetherion.foraging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Fell loop feedback. Beats mirror fishing on purpose:
 * telegraph (glow + knock) → ready (creak) → hit (Clean / Perfect) → tally after the fall.
 * Everything is small and local; the tree coming down is the spectacle.
 */
final class ForagingFx {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ForagingFx() {
    }

    /** Fell-mark spark while chopping — keep subtle. Teaching sparks live at the lumberjack. */
    static void spark(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Location center = at.clone().add(0.5, 0.35, 0.5);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 2, 0.16, 0.2, 0.16, 0.0);
        world.spawnParticle(Particle.COMPOSTER, center, 1, 0.12, 0.16, 0.12, 0.0);
    }

    static void start(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_HIT, 0.6f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.5f);
    }

    /** Telegraph: knock + a small glow on the marked trunk so the eye goes there, not the bar. */
    static void start(Player player, Location fellAt) {
        start(player);
        if (fellAt != null && fellAt.getWorld() != null) {
            Location center = fellAt.clone().add(0.5, 0.35, 0.5);
            player.spawnParticle(Particle.HAPPY_VILLAGER, center, 5, 0.22, 0.25, 0.22, 0.0);
            player.spawnParticle(Particle.COMPOSTER, center, 3, 0.18, 0.2, 0.18, 0.0);
        }
    }

    /** Ready tell — the trunk creaks just before the window. One per pass. */
    static void creak(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_HIT, 0.45f, 0.65f);
    }

    static void success(Player player, Location at) {
        player.sendActionBar(Component.text("Clean fell.", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.4f, 0.9f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.85f, 0.75f);
        if (at != null && at.getWorld() != null) {
            Location center = at.clone().add(0.5, 0.5, 0.5);
            at.getWorld().spawnParticle(Particle.CRIT, center, 14, 0.3, 0.35, 0.3, 0.1);
            at.getWorld().spawnParticle(Particle.BLOCK, center, 18, 0.35, 0.4, 0.35, 0.08,
                    Material.OAK_LOG.createBlockData());
        }
    }

    static void success(Player player, Location at, boolean perfect, int streak) {
        success(player, at);
        player.sendActionBar(LEGACY.deserialize(word(perfect) + ForagingStrike.streakTag(streak)));
        if (perfect) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
            player.playSound(player.getLocation(), Sound.ITEM_AXE_STRIP, 0.7f, 1.2f);
            if (at != null && at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.WAX_ON, at.clone().add(0.5, 0.8, 0.5), 10, 0.3, 0.4, 0.3, 0.0);
            }
        }
    }

    static String word(boolean perfect) {
        return perfect ? "§6Perfect fell." : "§aClean fell.";
    }

    static void miss(Player player) {
        player.sendActionBar(Component.text("Missed the fell.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.55f);
    }

    /** Readable fail: why, what it cost, and that other trees are fine. */
    static void miss(Player player, String reason, int lostStreak, long cooldownSeconds) {
        StringBuilder line = new StringBuilder("§cMissed the fell §8· §7").append(reason);
        if (lostStreak >= 2) {
            line.append(" §8· §7streak §e✦").append(lostStreak).append(" §7lost");
        }
        line.append(" §8· §7trunk cools ").append(cooldownSeconds).append('s');
        player.sendActionBar(LEGACY.deserialize(line.toString()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.55f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_HIT, 0.5f, 0.5f);
    }

    /** Reward beat once the canopy is down. */
    static void tally(Player player, String line) {
        player.sendActionBar(LEGACY.deserialize(line));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 0.9f);
    }
}
