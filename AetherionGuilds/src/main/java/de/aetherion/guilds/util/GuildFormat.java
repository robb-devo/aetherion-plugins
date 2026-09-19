package de.aetherion.guilds.util;

import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.model.QuarryType;

import java.util.Locale;

public final class GuildFormat {

    private GuildFormat() {
    }

    public static String islandTitle(String guildName) {
        if (guildName == null || guildName.isBlank()) {
            return "Guild Island";
        }
        String name = guildName.trim();
        if (name.endsWith("s") || name.endsWith("S")) {
            return name + "' Island";
        }
        return name + "'s Island";
    }

    public static String shortName(QuarryType type) {
        if (type == null) {
            return "Quarry";
        }
        return switch (type) {
            case COBBLESTONE -> "Cobble";
            case RAW_IRON -> "Iron";
            case RAW_GOLD -> "Gold";
            case RAW_COPPER -> "Copper";
            case OAK_LOG -> "Oak";
            default -> type.productName();
        };
    }

    public static String compact(int value) {
        if (value >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (value >= 1_000) {
            return String.format(Locale.US, "%.1fk", value / 1_000d);
        }
        return Integer.toString(value);
    }

    public static String compact(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (value >= 1_000L) {
            return String.format(Locale.US, "%.1fk", value / 1_000d);
        }
        return Long.toString(value);
    }

    public static String storageLine(QuarryType type, int level, QuarryMinion.StorageView view) {
        String name = shortName(type);
        if (view == null || view.isEmpty()) {
            return "&7" + name + " &8Lv." + level + " &e0";
        }
        if (view.compacted() <= 0 && view.compressed() <= 0) {
            return "&7" + name + " &8Lv." + level
                    + " &e" + compact(view.raw()) + "&8/&7" + compact(view.cap());
        }
        StringBuilder line = new StringBuilder("&7").append(name).append(" &8Lv.").append(level);
        if (view.compacted() > 0) {
            line.append(" &b").append(compact(view.compacted())).append("K");
        }
        if (view.compressed() > 0) {
            line.append(" &a").append(compact(view.compressed())).append("C");
        }
        if (view.raw() > 0) {
            line.append(" &e").append(compact(view.raw()));
        }
        return line.toString();
    }

    public static String nametag(QuarryType type, int level, QuarryMinion.StorageView view) {
        return storageLine(type, level, view).replace('&', '§');
    }
}
