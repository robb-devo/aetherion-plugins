package de.aetherion.items.progress;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Short on-screen unlock feedback. Chat alone gets ignored.
 */
public final class UnlockToast {

    private UnlockToast() {
    }

    public static void show(Player player, String feature, String subtitle) {
        if (player == null || feature == null || feature.isBlank()) {
            return;
        }
        String clean = feature.startsWith("§") ? feature : "§f" + feature;
        String sub = subtitle == null || subtitle.isBlank()
                ? "§7Aetherion Manager"
                : (subtitle.startsWith("§") ? subtitle : "§7" + subtitle);

        player.sendTitle("§a§lUNLOCKED", clean, 8, 55, 16);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Unlocked " + strip(feature) + " — " + strip(sub)
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f, 1.4f);
        worldNoticed(player);
        player.sendMessage("§a✦ Unlocked " + clean + "§a. §8" + strip(sub));
    }

    /**
     * Same "the world noticed you" beat as a new-area discover (Hub), in unlock green:
     * a ring slides out from your feet, a few motes lift, a second chime answers the first.
     * Player-only particles, no entities.
     */
    private static void worldNoticed(Player player) {
        Location feet = player.getLocation().add(0.0, 0.15, 0.0);
        for (int i = 0; i < 14; i++) {
            double a = i * (Math.PI * 2.0 / 14.0);
            // count 0 = offsets become a velocity: sparks slide outward along the ground.
            player.spawnParticle(Particle.FIREWORK, feet, 0, Math.cos(a), 0.02, Math.sin(a), 0.12);
        }
        player.spawnParticle(Particle.HAPPY_VILLAGER, feet.clone().add(0.0, 0.9, 0.0), 5, 0.4, 0.4, 0.4, 0.0);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.5f);
                }
            }, 6L);
        }
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }
}
