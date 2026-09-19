package de.aetherion.items.dungeon;

import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;

public enum DungeonGearTier {

    T1(1, "", "I", 1.00d, Rarity.RARE, Color.fromRGB(108, 110, 118), Material.LEATHER_CHESTPLATE),
    T2(2, "t2", "II", 1.60d, Rarity.EPIC, Color.fromRGB(92, 48, 128), Material.ENDER_EYE),
    T3(3, "t3", "III", 2.25d, Rarity.LEGENDARY, Color.fromRGB(168, 92, 28), Material.NETHER_STAR);

    private final int floor;
    private final String key;
    private final String roman;
    private final double statMultiplier;
    private final Rarity rarity;
    private final Color leather;
    private final Material relicIcon;

    DungeonGearTier(
            int floor,
            String key,
            String roman,
            double statMultiplier,
            Rarity rarity,
            Color leather,
            Material relicIcon
    ) {
        this.floor = floor;
        this.key = key;
        this.roman = roman;
        this.statMultiplier = statMultiplier;
        this.rarity = rarity;
        this.leather = leather;
        this.relicIcon = relicIcon;
    }

    public int floor() {
        return floor;
    }

    public String key() {
        return key;
    }

    public String roman() {
        return roman;
    }

    public double statMultiplier() {
        return statMultiplier;
    }

    public Rarity rarity() {
        return rarity;
    }

    public Color leather() {
        return leather;
    }

    public Material relicIcon() {
        return relicIcon;
    }

    public String rarityLine() {
        return switch (this) {
            case T3 -> "§7✦ §6LEGENDARY";
            case T2 -> "§7✦ §5EPIC";
            case T1 -> "§7✦ §bRARE";
        };
    }

    public DungeonCalling[] armorChoices() {
        return switch (this) {
            case T1 -> DungeonCalling.values();
            case T2 -> new DungeonCalling[]{DungeonCalling.TANK, DungeonCalling.SOLDIER, DungeonCalling.ASSASSIN};
            case T3 -> new DungeonCalling[]{DungeonCalling.ASSASSIN, DungeonCalling.HEALER, DungeonCalling.SHAMAN};
        };
    }

    public static DungeonGearTier fromFloor(int floor) {
        if (floor >= 3) {
            return T3;
        }
        if (floor >= 2) {
            return T2;
        }
        return T1;
    }

    public static DungeonGearTier fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.startsWith("dungeon_t3_") || id.startsWith("dungeon_relic_t3_")) {
            return T3;
        }
        if (id.startsWith("dungeon_t2_") || id.startsWith("dungeon_relic_t2_")) {
            return T2;
        }
        if (id.startsWith("dungeon_")) {
            return T1;
        }
        return null;
    }

    public static String strip(String itemId) {
        if (itemId == null) {
            return "";
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.startsWith("dungeon_t3_")) {
            return "dungeon_" + id.substring("dungeon_t3_".length());
        }
        if (id.startsWith("dungeon_t2_")) {
            return "dungeon_" + id.substring("dungeon_t2_".length());
        }
        return id;
    }

    public static ItemStats scale(ItemStats stats, double multiplier) {
        ItemStats scaled = new ItemStats();
        if (stats == null) {
            return scaled;
        }
        double mul = Math.max(1.0d, multiplier);
        scaled.setDamage(round(stats.getDamage() * mul));
        scaled.setDefense(round(stats.getDefense() * mul));
        scaled.setHealth(round(stats.getHealth() * mul));
        scaled.setAttackSpread(round(stats.getAttackSpread() * mul));
        scaled.setUndeadResist(round(stats.getUndeadResist() * mul));
        scaled.setCritChance(round(stats.getCritChance() * (1.0d + (mul - 1.0d) * 0.45d)));
        scaled.setCritDamage(round(stats.getCritDamage() * (1.0d + (mul - 1.0d) * 0.35d)));
        scaled.setSpeed(round(stats.getSpeed() * (1.0d + (mul - 1.0d) * 0.30d)));
        return scaled;
    }

    private static double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }
}
