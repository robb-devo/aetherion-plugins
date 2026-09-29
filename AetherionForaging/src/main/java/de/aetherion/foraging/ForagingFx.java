package de.aetherion.foraging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Particle-only forage FX. Never spawn TextDisplay / ItemDisplay / ArmorStand —
 * holograms live in {@link de.aetherion.foraging.npc.IsleGuideNpc} and are capped
 * by {@link de.aetherion.foraging.island.ForageDisplayGuard}.
 */
final class ForagingFx {

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

    static void success(Player player, Location at) {
        player.sendActionBar(Component.text("Clean fell.", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.4f, 0.9f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.85f, 0.75f);
        if (at != null && at.getWorld() != null) {
            Location center = at.clone().add(0.5, 0.5, 0.5);
            at.getWorld().spawnParticle(Particle.CRIT, center, 14, 0.3, 0.35, 0.3, 0.1);
            at.getWorld().spawnParticle(Particle.BLOCK, center, 18, 0.35, 0.4, 0.35, 0.08,
                    org.bukkit.Material.OAK_LOG.createBlockData());
        }
    }

    static void miss(Player player) {
        player.sendActionBar(Component.text("Missed the fell.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.55f);
    }
}
