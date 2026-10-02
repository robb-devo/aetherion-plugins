package de.aetherion.hub.origin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Legacy-colour formatting for the Origin Isle loops (same conventions as the Eldervale isles). */
public final class OriginText {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final Map<UUID, Long> HOLD = new ConcurrentHashMap<>();

    private OriginText() {
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** Action bar that the wayfinder leaves alone for a couple of seconds. */
    public static void bar(Player player, String text) {
        if (player != null) {
            player.sendActionBar(legacy(text));
            hold(player, 2500L);
        }
    }

    public static void hold(Player player, long ms) {
        if (player != null) {
            HOLD.put(player.getUniqueId(), System.currentTimeMillis() + ms);
        }
    }

    public static boolean held(Player player) {
        Long until = player == null ? null : HOLD.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public static void forget(UUID id) {
        HOLD.remove(id);
    }

    /** Short card: fade 8 ticks, hold {@code holdTicks}, fade 16 ticks. */
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

    /** Compass word for a bearing from {@code from} to {@code to}: N, NE, … */
    public static String compass(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0d) % 360.0d;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(deg / 45.0d) % 8];
    }

    /** "▲ 12" / "▼ 30" / "" — the island is tall, so height matters as much as direction. */
    public static String vertical(Player player, Location target) {
        int dy = (int) Math.round(target.getY() - player.getLocation().getY());
        if (Math.abs(dy) < 3) {
            return "";
        }
        return dy > 0 ? " §a▲" + dy : " §c▼" + (-dy);
    }

    public static String cells(double fill, String on) {
        int filled = (int) Math.round(Math.max(0.0d, Math.min(1.0d, fill)) * 10.0d);
        return on + "▮".repeat(filled) + "§8" + "▯".repeat(10 - filled);
    }

    public static String distance(double blocks) {
        return blocks >= 1000.0d ? String.format(Locale.US, "%.1fkm", blocks / 1000.0d) : (int) Math.round(blocks) + "m";
    }

    public static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("[_ ]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
