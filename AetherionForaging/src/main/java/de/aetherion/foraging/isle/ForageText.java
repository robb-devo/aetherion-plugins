package de.aetherion.foraging.isle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/** Formatting shared by the Foraging Eldervale loops — legacy colour strings, like the rest of Aetherion. */
public final class ForageText {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private ForageText() {
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    private static final java.util.Map<java.util.UUID, Long> HOLD = new java.util.concurrent.ConcurrentHashMap<>();

    /** Action bar that the wayfinder leaves alone for a couple of seconds. */
    public static void bar(Player player, String text) {
        if (player != null) {
            player.sendActionBar(legacy(text));
            hold(player, 2500L);
        }
    }

    /** Keep low-priority action bars (the wayfinder) off this player's screen for {@code ms}. */
    public static void hold(Player player, long ms) {
        if (player != null) {
            HOLD.put(player.getUniqueId(), System.currentTimeMillis() + ms);
        }
    }

    public static boolean held(Player player) {
        Long until = player == null ? null : HOLD.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public static void forget(java.util.UUID id) {
        HOLD.remove(id);
    }

    /** Short card: fade in 8 ticks, hold {@code holdTicks}, fade out 16 ticks. */
    public static void card(Player player, String title, String subtitle, int holdTicks) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(legacy(title), legacy(subtitle), Title.Times.times(
                Duration.ofMillis(400), Duration.ofMillis(Math.max(10, holdTicks) * 50L), Duration.ofMillis(800))));
    }

    public static String coins(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static String grams(int grams) {
        return grams >= 1000 ? String.format(Locale.US, "%.2f kg", grams / 1000.0d) : grams + " g";
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

    /** "▲ 12" / "▼ 30" / "" for a wayfinder. The isle is tall — the height hint matters more than the arrow. */
    public static String vertical(Player player, Location target) {
        int dy = (int) Math.round(target.getY() - player.getLocation().getY());
        if (Math.abs(dy) < 3) {
            return "";
        }
        return dy > 0 ? " §a▲" + dy : " §c▼" + (-dy);
    }

    public static String pct(double fraction) {
        double value = fraction * 100.0d;
        return value == Math.floor(value) ? (long) value + "%" : String.format(Locale.US, "%.1f%%", value);
    }

    public static String plural(long amount, String noun) {
        return amount + " " + noun + (amount == 1 ? "" : "s");
    }

    /** {@code dark_oak} → {@code Dark Oak}. */
    public static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("[_ ]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
