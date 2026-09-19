package de.aetherion.items.progress;

import org.bukkit.Sound;
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
        player.sendMessage("§a✦ Unlocked " + clean + "§a. §8" + strip(sub));
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }
}
