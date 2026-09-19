package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
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
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return null;
        }
        try {
            Object plugin = items.getClass().getMethod("getInstance").invoke(null);
            Object manager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
            Object id = manager.getClass().getMethod("getItemId", ItemStack.class).invoke(manager, item);
            if (!(id instanceof String text) || text.isBlank()) {
                return null;
            }
            for (String piece : VESTIGE_PIECES) {
                if (piece.equals(text)) {
                    return text;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
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
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return null;
        }
        try {
            Object plugin = items.getClass().getMethod("getInstance").invoke(null);
            Object manager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
            Class<?> managerClass = Class.forName("de.aetherion.items.manager.ItemManager");
            Class<?> customClass = Class.forName("de.aetherion.items.item.CustomItem");
            Object custom = customClass.getConstructor(managerClass).newInstance(manager);
            Object created = customClass.getMethod("createDungeonWeaponSchematic", int.class)
                    .invoke(custom, Math.max(1, floor));
            return created instanceof ItemStack stack ? stack : null;
        } catch (Exception exception) {
            items.getLogger().warning("Dungeon weapon schematic failed: " + exception.getMessage());
            return create("dungeon_weapon_schematic");
        }
    }

    public static ItemStack rollCompressed() {
        return compressedItem(false);
    }

    public static ItemStack rollCompacted() {
        return compressedItem(true);
    }

    private static ItemStack compressedItem(boolean compacted) {
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return null;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.items.economy.CompressedResource");
            Object[] values = (Object[]) type.getMethod("values").invoke(null);
            if (values == null || values.length == 0) {
                return null;
            }
            Object pick = values[ThreadLocalRandom.current().nextInt(values.length)];
            Object created = type.getMethod(compacted ? "compacted" : "compressed").invoke(pick);
            return created instanceof ItemStack stack ? stack : null;
        } catch (Exception exception) {
            items.getLogger().warning("Dungeon compressed loot failed: " + exception.getMessage());
            return null;
        }
    }

    public static ItemStack create(String itemId) {
        de.aetherion.core.api.ItemFactoryAccess factory = de.aetherion.core.api.AetherServices.items();
        if (factory != null && itemId != null && !itemId.isBlank()) {
            ItemStack created = factory.create(itemId);
            if (created != null) {
                return created;
            }
        }
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled() || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Object plugin = items.getClass().getMethod("getInstance").invoke(null);
            Object manager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
            Class<?> managerClass = Class.forName("de.aetherion.items.manager.ItemManager");
            Class<?> customClass = Class.forName("de.aetherion.items.item.CustomItem");
            Object custom = customClass.getConstructor(managerClass).newInstance(manager);
            Method method = customClass.getMethod(toCreateMethod(itemId));
            Object created = method.invoke(custom);
            return created instanceof ItemStack stack ? stack : null;
        } catch (Exception exception) {
            items.getLogger().warning("Dungeon loot roll failed for " + itemId + ": " + exception.getMessage());
            return null;
        }
    }

    private static String toCreateMethod(String itemId) {
        StringBuilder builder = new StringBuilder("create");
        for (String part : itemId.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.chars().allMatch(Character::isDigit)) {
                builder.append(part);
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
