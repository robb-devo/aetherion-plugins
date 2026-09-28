package de.aetherion.farming.isle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Formatting shared by the Eldervale loops — legacy colour strings, like the rest of Aetherion. */
public final class IsleText {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private IsleText() {
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static void bar(Player player, String text) {
        if (player != null) {
            player.sendActionBar(legacy(text));
        }
    }

    public static String coins(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static String kg(double kg) {
        return String.format(Locale.US, "%.2f kg", kg);
    }

    public static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    /** {@code 4:05} / {@code 1:02:03}. */
    public static String clock(long seconds) {
        long s = Math.max(0L, seconds);
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** Ten-cell bar, filled share in {@code on}. */
    public static String cells(double fill, String on) {
        int filled = (int) Math.round(Math.max(0.0d, Math.min(1.0d, fill)) * 10.0d);
        return on + "▮".repeat(filled) + "§8" + "▯".repeat(10 - filled);
    }

    /** Screen-relative arrow from the player's facing towards {@code target}. */
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
