package de.aetherion.dungeons.bridge;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;

import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class ItemLootBridge {

    private static final String[] BOSS_ITEMS = {
            "warped_blade",
            "aetherblade",
            "bridged_axe"
    };

    private static final String[] VESTIGE_PIECES = {
            "dungeon_vestige_helmet",
            "dungeon_vestige_chestplate",
            "dungeon_vestige_leggings",
            "dungeon_vestige_boots"
    };

    public static final double BOSS_ITEM_CHANCE = 0.08;
    public static final double DUNGEON_CORE_CHANCE = 0.10;
    public static final double VICTORY_CORE_CHANCE = 0.15;
    public static final double DUNGEON_ARMOR_CHANCE = 0.32;
    public static final double VICTORY_ARMOR_CHANCE = 0.40;
    public static final double WEAPON_SCHEMATIC_CHANCE = 0.18;
    public static final double VICTORY_SCHEMATIC_CHANCE = 0.28;

    private ItemLootBridge() {
    }

    public static ItemStack rollBossItem() {
        return rollBossItem(BOSS_ITEM_CHANCE);
    }

    public static ItemStack rollBossItem(double chance) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        String id = BOSS_ITEMS[ThreadLocalRandom.current().nextInt(BOSS_ITEMS.length)];
        return create(id);
    }

    public static final double AETHERION_VICTORY_CHANCE = 0.028;
    public static final double AETHERION_CACHE_CHANCE = 0.008;
    public static final double AETHERION_STICK_CHANCE = 0.009;

    public static ItemStack rollAetherionPiece(double chance) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        return create("random_aetherion_armor");
    }

    public static ItemStack rollVoidStick(double chance) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        return create("aetherion_void_stick");
    }

    public static ItemStack rollDungeonCore() {
        return rollDungeonCore(1, DUNGEON_CORE_CHANCE);
    }

    public static ItemStack rollDungeonCore(int floor) {
        return rollDungeonCore(floor, DUNGEON_CORE_CHANCE);
    }

    public static ItemStack rollDungeonCore(int floor, double chance) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        int tier = Math.max(1, Math.min(3, floor));
        return create(tier == 1 ? "dungeon_core" : "dungeon_core_" + tier);
    }

    public static ItemStack rollDungeonArmor() {
        return rollDungeonArmor(DUNGEON_ARMOR_CHANCE);
    }

    public static ItemStack rollDungeonArmor(double chance) {
        return rollDungeonArmor(chance, null);
    }

    /**
     * Prefer vestige slots the player has not received this dungeon run.
     * Once all four are collected, rolls freely again.
     */
    public static ItemStack rollDungeonArmor(double chance, java.util.Set<String> alreadyThisRun) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        return create(pickVestigeId(alreadyThisRun));
    }

    public static String pickVestigeId(java.util.Set<String> alreadyThisRun) {
        java.util.List<String> fresh = new java.util.ArrayList<>(4);
        for (String id : VESTIGE_PIECES) {
            if (alreadyThisRun == null || !alreadyThisRun.contains(id)) {
                fresh.add(id);
            }
        }
        if (fresh.isEmpty()) {
            return VESTIGE_PIECES[ThreadLocalRandom.current().nextInt(VESTIGE_PIECES.length)];
        }
        return fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));
    }

    public static String[] vestigePieceIds() {
        return VESTIGE_PIECES.clone();
    }

    public static String vestigeIdOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemFactoryAccess factory = AetherServices.items();
        if (factory == null) {
            return null;
        }
        String text = factory.itemId(item);
        if (text == null || text.isBlank()) {
            return null;
        }
        for (String piece : VESTIGE_PIECES) {
            if (piece.equals(text)) {
                return text;
            }
        }
        return null;
    }

    public static ItemStack rollWeaponSchematic() {
        return rollWeaponSchematic(WEAPON_SCHEMATIC_CHANCE, 1);
    }

    public static ItemStack rollWeaponSchematic(double chance) {
        return rollWeaponSchematic(chance, 1);
    }

    public static ItemStack rollWeaponSchematic(double chance, int floor) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return null;
        }
        return createDungeonWeaponSchematic(floor);
    }

    private static ItemStack createDungeonWeaponSchematic(int floor) {
        ItemFactoryAccess factory = AetherServices.items();
        if (factory == null) {
            return null;
        }
        ItemStack created = factory.dungeonWeaponSchematic(Math.max(1, floor));
        return created != null ? created : create("dungeon_weapon_schematic");
    }

    public static ItemStack rollCompressed() {
        return compressedItem(false);
    }

    public static ItemStack rollCompacted() {
        return compressedItem(true);
    }

    private static ItemStack compressedItem(boolean compacted) {
        ItemFactoryAccess factory = AetherServices.items();
        return factory == null ? null : factory.randomCompressed(compacted);
    }

    public static ItemStack create(String itemId) {
        ItemFactoryAccess factory = AetherServices.items();
        if (factory == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        return factory.create(itemId);
    }
}
