package de.aetherion.items.dungeon;

import de.aetherion.items.model.ItemStats;

import org.bukkit.Color;
import org.bukkit.Material;

public enum DungeonCalling {

    TANK(
            "tank",
            "Tank",
            "§b",
            "Bulwark",
            Material.IRON_CHESTPLATE,
            Color.fromRGB(72, 88, 108)
    ),
    ASSASSIN(
            "assassin",
            "Assassin",
            "§5",
            "Shade",
            Material.NETHERITE_SWORD,
            Color.fromRGB(28, 12, 42)
    ),
    SOLDIER(
            "soldier",
            "Soldier",
            "§c",
            "Vanguard",
            Material.IRON_AXE,
            Color.fromRGB(148, 42, 38)
    ),
    HEALER(
            "healer",
            "Healer",
            "§e",
            "Sanctuary",
            Material.GOLDEN_APPLE,
            Color.fromRGB(236, 214, 128)
    ),
    SHAMAN(
            "shaman",
            "Shaman",
            "§3",
            "Spiritbind",
            Material.TOTEM_OF_UNDYING,
            Color.fromRGB(28, 118, 112)
    );

    private final String id;
    private final String display;
    private final String color;
    private final String setName;
    private final Material icon;
    private final Color leather;

    DungeonCalling(
            String id,
            String display,
            String color,
            String setName,
            Material icon,
            Color leather
    ) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.setName = setName;
        this.icon = icon;
        this.leather = leather;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    public String setName() {
        return setName;
    }

    public Material icon() {
        return icon;
    }

    public Color leatherColor() {
        return usesLeather() ? leather : null;
    }

    public boolean usesLeather() {
        return this == ASSASSIN || this == SHAMAN;
    }

    public String itemId(DungeonPiece piece) {
        return itemId(DungeonGearTier.T1, piece);
    }

    public String itemId(DungeonGearTier tier, DungeonPiece piece) {
        if (tier == null || tier == DungeonGearTier.T1) {
            return "dungeon_" + id + "_" + piece.id();
        }
        return "dungeon_" + tier.key() + "_" + id + "_" + piece.id();
    }

    public String displayName(DungeonPiece piece) {
        return displayName(DungeonGearTier.T1, piece);
    }

    public String displayName(DungeonGearTier tier, DungeonPiece piece) {
        String base = color + setName + " " + piece.display();
        if (tier == null || tier == DungeonGearTier.T1) {
            return base;
        }
        return base + " " + tier.roman();
    }

    public Material material(DungeonPiece piece) {
        return switch (this) {
            case TANK -> piece.iron();
            case SOLDIER -> piece.chainmail();
            case HEALER -> piece.gold();
            case ASSASSIN, SHAMAN -> piece.leather();
        };
    }

    public int model(DungeonPiece piece) {
        return model(DungeonGearTier.T1, piece);
    }

    public int model(DungeonGearTier tier, DungeonPiece piece) {
        int base = 2710 + ordinal() * 10 + piece.ordinal() + 1;
        if (tier == null || tier == DungeonGearTier.T1) {
            return base;
        }
        return base + tier.floor() * 40;
    }

    public ItemStats stats(DungeonPiece piece, DungeonGearTier tier) {
        ItemStats base = stats(piece);
        if (tier == null || tier == DungeonGearTier.T1) {
            return base;
        }
        return DungeonGearTier.scale(base, tier.statMultiplier());
    }

    public ItemStats stats(DungeonPiece piece) {
        return switch (this) {
            case TANK -> switch (piece) {
                case HELMET -> stats(16, 18, 0, 0, 0, 0, 0, 6);
                case CHESTPLATE -> stats(27, 32, 0, 0, 0, 0, 0, 9);
                case LEGGINGS -> stats(21, 25, 0, 0, 0, 0, 0, 7);
                case BOOTS -> stats(16, 18, 0, 0, 0, 0, 0, 5);
            };
            case ASSASSIN -> switch (piece) {
                case HELMET -> stats(0, 0, 13, 0, 12, 32, 7, 0);
                case CHESTPLATE -> stats(0, 0, 21, 9, 16, 48, 8, 0);
                case LEGGINGS -> stats(0, 0, 15, 0, 12, 37, 7, 0);
                case BOOTS -> stats(0, 0, 12, 0, 9, 25, 16, 0);
            };
            case SOLDIER -> switch (piece) {
                case HELMET -> stats(12, 13, 6, 0, 5, 0, 0, 0);
                case CHESTPLATE -> stats(21, 22, 12, 12, 7, 0, 0, 0);
                case LEGGINGS -> stats(15, 16, 8, 0, 5, 0, 0, 0);
                case BOOTS -> stats(12, 13, 5, 0, 0, 0, 7, 0);
            };
            case HEALER -> switch (piece) {
                case HELMET -> stats(9, 28, 0, 0, 0, 0, 5, 0);
                case CHESTPLATE -> stats(17, 48, 0, 0, 0, 0, 6, 0);
                case LEGGINGS -> stats(13, 34, 0, 0, 0, 0, 5, 0);
                case BOOTS -> stats(9, 25, 0, 0, 0, 0, 12, 0);
            };
            case SHAMAN -> switch (piece) {
                case HELMET -> stats(9, 18, 0, 9, 5, 0, 6, 0);
                case CHESTPLATE -> stats(16, 28, 0, 18, 9, 0, 7, 0);
                case LEGGINGS -> stats(13, 21, 0, 12, 6, 0, 6, 0);
                case BOOTS -> stats(9, 18, 0, 9, 5, 0, 12, 0);
            };
        };
    }

    public String flavor() {
        return switch (this) {
            case TANK -> "You are the door. The door is you.";
            case ASSASSIN -> "Stab first. Deny involvement.";
            case SOLDIER -> "Orders were 'hit it until it stops.'";
            case HEALER -> "Keep them alive. They will not thank you.";
            case SHAMAN -> "You asked the dungeon. It answered with teeth.";
        };
    }

    public String specialty() {
        return switch (this) {
            case TANK -> "Holds the line. Undead resist. Extra bulk in dungeons.";
            case ASSASSIN -> "Bows: -8% draw per piece. Cooldowns -5% per piece. Crits included.";
            case SOLDIER -> "Damage, defense, attack spread. Dies second on purpose.";
            case HEALER -> "Pulses health to nearby players. Optimism, with armor.";
            case SHAMAN -> "Attack spread, speed, spirit shields. Weather included.";
        };
    }

    public String boosters() {
        return switch (this) {
            case TANK -> "Iron, Coal, Lapis";
            case ASSASSIN -> "Diamond, Gold, Birch, Oak, Glowstone, Redstone";
            case SOLDIER -> "Iron, Gold, Diamond, Redstone, Birch, Lapis";
            case HEALER -> "Iron, Coal, Lapis, Glowstone";
            case SHAMAN -> "Gold, Redstone, Lapis, Glowstone, Birch";
        };
    }

    public static DungeonCalling fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank() || DungeonRelic.isUnidentified(itemId)) {
            return null;
        }
        String id = DungeonGearTier.strip(itemId);
        for (DungeonCalling calling : values()) {
            if (id.startsWith("dungeon_" + calling.id + "_")) {
                return calling;
            }
        }
        return null;
    }

    private static ItemStats stats(
            double defense,
            double health,
            double damage,
            double attackSpread,
            double critChance,
            double critDamage,
            double speed,
            double undeadResist
    ) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        stats.setDamage(damage);
        stats.setAttackSpread(attackSpread);
        stats.setCritChance(critChance);
        stats.setCritDamage(critDamage);
        stats.setSpeed(speed);
        stats.setUndeadResist(undeadResist);
        return stats;
    }
}
