package de.aetherion.items.dungeon;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.ItemStats;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public enum DungeonWeaponKind {

    SWORD("sword", "Edge", Material.IRON_SWORD, Material.NETHERITE_SWORD),
    BOW("bow", "String", Material.BOW, Material.BOW),
    WAND("wand", "Wand", Material.BLAZE_ROD, Material.BLAZE_ROD),
    MACE("mace", "Maul", Material.IRON_AXE, Material.NETHERITE_AXE),
    STAFF("staff", "Staff", Material.BLAZE_ROD, Material.BLAZE_ROD);

    public static final String SCHEMATIC_ID = "dungeon_weapon_schematic";

    private final String id;
    private final String display;
    private final Material t2;
    private final Material t3;

    DungeonWeaponKind(String id, String display, Material t2, Material t3) {
        this.id = id;
        this.display = display;
        this.t2 = t2;
        this.t3 = t3;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public DungeonCalling calling() {
        return switch (this) {
            case SWORD -> DungeonCalling.SOLDIER;
            case BOW -> DungeonCalling.ASSASSIN;
            case WAND -> DungeonCalling.SHAMAN;
            case MACE -> DungeonCalling.TANK;
            case STAFF -> DungeonCalling.HEALER;
        };
    }

    public static DungeonWeaponKind forCalling(DungeonCalling calling) {
        if (calling == null) {
            return SWORD;
        }
        return switch (calling) {
            case SOLDIER -> SWORD;
            case ASSASSIN -> BOW;
            case SHAMAN -> WAND;
            case TANK -> MACE;
            case HEALER -> STAFF;
        };
    }

    public String itemId(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        if (safe == DungeonGearTier.T1) {
            return "dungeon_" + id;
        }
        return "dungeon_" + safe.key() + "_" + id;
    }

    public String displayName(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        String color = switch (this) {
            case SWORD -> "§c";
            case BOW -> "§5";
            case WAND -> "§3";
            case MACE -> "§b";
            case STAFF -> "§e";
        };
        String set = switch (this) {
            case SWORD -> "Vanguard";
            case BOW -> "Shade";
            case WAND -> "Spiritbind";
            case MACE -> "Bulwark";
            case STAFF -> "Sanctuary";
        };
        return color + set + " " + display + " " + safe.roman();
    }

    public Material material(DungeonGearTier tier) {
        return tier == DungeonGearTier.T3 ? t3 : t2;
    }

    public int model(DungeonGearTier tier) {
        int base = switch (this) {
            case SWORD -> 2810;
            case BOW -> 2820;
            case WAND -> 2830;
            case MACE -> 2840;
            case STAFF -> 2850;
        };
        int plus = switch (tier) {
            case T3 -> 3;
            case T2 -> 2;
            case T1 -> 1;
        };
        return base + plus;
    }

    public ItemStats stats(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        boolean t3 = safe == DungeonGearTier.T3;
        boolean t1 = safe == DungeonGearTier.T1;
        ItemStats stats = new ItemStats();
        switch (this) {
            case SWORD -> {
                stats.setDamage(t3 ? 95.0 : t1 ? 32.0 : 55.0);
                stats.setAttackSpread(t3 ? 12.0 : t1 ? 5.0 : 8.0);
                stats.setCritChance(t3 ? 14.0 : t1 ? 6.0 : 10.0);
                stats.setCritDamage(t3 ? 95.0 : t1 ? 45.0 : 70.0);
            }
            case BOW -> {
                stats.setDamage(t3 ? 78.0 : t1 ? 26.0 : 45.0);
                stats.setCritChance(t3 ? 18.0 : t1 ? 10.0 : 14.0);
                stats.setCritDamage(t3 ? 110.0 : t1 ? 55.0 : 80.0);
                stats.setSpeed(t3 ? 10.0 : t1 ? 4.0 : 7.0);
            }
            case WAND -> {
                stats.setDamage(t3 ? 85.0 : t1 ? 28.0 : 48.0);
                stats.setAttackSpread(t3 ? 16.0 : t1 ? 7.0 : 11.0);
                stats.setCritChance(t3 ? 12.0 : t1 ? 5.0 : 8.0);
                stats.setCritDamage(t3 ? 85.0 : t1 ? 42.0 : 65.0);
            }
            case MACE -> {
                stats.setDamage(t3 ? 105.0 : t1 ? 34.0 : 58.0);
                stats.setAttackSpread(t3 ? 8.0 : t1 ? 4.0 : 6.0);
                stats.setDefense(t3 ? 18.0 : t1 ? 8.0 : 12.0);
                stats.setCritChance(t3 ? 8.0 : t1 ? 4.0 : 6.0);
                stats.setCritDamage(t3 ? 65.0 : t1 ? 30.0 : 48.0);
            }
            case STAFF -> {
                stats.setDamage(t3 ? 72.0 : t1 ? 24.0 : 42.0);
                stats.setHealth(t3 ? 28.0 : t1 ? 12.0 : 18.0);
                stats.setCritChance(t3 ? 10.0 : t1 ? 5.0 : 7.0);
                stats.setCritDamage(t3 ? 70.0 : t1 ? 35.0 : 52.0);
            }
        }
        return stats;
    }

    public String flavor() {
        return switch (this) {
            case SWORD -> "A hallway argument, with better steel.";
            case BOW -> "The dungeon taught this bow to skip the draw.";
            case WAND -> "Point. The floor does the rest.";
            case MACE -> "You are the door. The door hits back.";
            case STAFF -> "Keep them alive. They will not thank you.";
        };
    }

    public static boolean isSchematic(String itemId) {
        return itemId != null && SCHEMATIC_ID.equalsIgnoreCase(itemId);
    }

    public static DungeonGearTier schematicTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return DungeonGearTier.T1;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return DungeonGearTier.T1;
        }
        Integer floor = meta.getPersistentDataContainer().get(
                ItemKeys.dungeonSchematicFloor(),
                PersistentDataType.INTEGER
        );
        return DungeonGearTier.fromFloor(floor == null ? 1 : floor);
    }

    public static DungeonWeaponKind fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        for (DungeonWeaponKind kind : values()) {
            for (DungeonGearTier tier : DungeonGearTier.values()) {
                if (id.equals(kind.itemId(tier))) {
                    return kind;
                }
            }
        }
        return null;
    }
}
