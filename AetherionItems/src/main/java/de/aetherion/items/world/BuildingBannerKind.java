package de.aetherion.items.world;

import org.bukkit.Material;

import java.util.Locale;

public enum BuildingBannerKind {
    CASINO("Casino", "* LUCK · GAMES · GLITTER *", Material.GOLD_INGOT),
    AUCTION_BAZAAR("Ah & Bz", "* TRADE · BIDS · DEALS *", Material.EMERALD),
    BANK("Bank", "* VAULT · COINS · TRUST *", Material.AMETHYST_SHARD);

    private final String title;
    private final String subtitle;
    private final Material icon;

    BuildingBannerKind(String title, String subtitle, Material icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return "§5§l" + title + " §8· §dBanner";
    }

    public static BuildingBannerKind fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
