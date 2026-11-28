package de.aetherion.tutorial;

import de.aetherion.core.api.AetherServices;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Texts: English is the canon, German follows the player's own choice; legacy colour codes like everywhere else. */
public final class Txt {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private Txt() {
    }

    public static boolean german(Player player) {
        try {
            return player != null && AetherServices.german(player);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The line in the player's language, with {@code {player}} filled in. */
    public static String t(Player player, String en, String de) {
        String text = german(player) && de != null ? de : en;
        return player == null ? text : text.replace("{player}", player.getName());
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static void actionBar(Player player, String text) {
        if (player != null) {
            player.sendActionBar(legacy(text));
        }
    }

    /** Screen-relative arrow from where the player looks towards {@code target}. */
    public static String arrow(Player player, Location target) {
        Location from = player.getLocation();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double delta = ((targetYaw - from.getYaw()) % 360.0d + 540.0d) % 360.0d - 180.0d;
        int index = (int) Math.round(delta / 45.0d);
        return ARROWS[((index % 8) + 8) % 8];
    }
}
