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

    /**
     * Vestige slots a player already covers with Floor-1 gear: a blank vestige or an attuned
     * Floor-I calling piece of that slot (worn, inventory, ender chest). Returned as vestige ids.
     */
    public static java.util.Set<String> ownedVestigeSlots(org.bukkit.entity.Player player) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (player == null) {
            return out;
        }
        ItemFactoryAccess factory = AetherServices.items();
        if (factory == null) {
            return out;
        }
        scanVestigeSlots(player.getInventory().getContents(), factory, out);
        scanVestigeSlots(player.getEnderChest().getContents(), factory, out);
        return out;
    }

    private static void scanVestigeSlots(ItemStack[] items, ItemFactoryAccess factory, java.util.Set<String> out) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String slot = vestigeSlotOf(factory.itemId(item));
            if (slot != null) {
                out.add(slot);
            }
        }
    }

    /** {@code dungeon_vestige_boots} / {@code dungeon_tank_boots} → {@code dungeon_vestige_boots}; T2/T3/relics/cores → null. */
    static String vestigeSlotOf(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        if (!id.startsWith("dungeon_")
                || id.startsWith("dungeon_relic_")
                || id.startsWith("dungeon_t2_")
                || id.startsWith("dungeon_t3_")
                || id.startsWith("dungeon_core")
                || id.startsWith("dungeon_weapon")) {
            return null;
        }
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            if (id.endsWith("_" + piece)) {
                return "dungeon_vestige_" + piece;
            }
        }
        return null;
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
        ItemStack out = created != null ? created : create("dungeon_weapon_schematic");
        if (floor <= 1) {
            rename(out, "§5Dungeon Weapon Schematic", "§5Armory Requisition §8· §dFloor I");
        }
        return out;
    }

    /** Floor I drops read as the Warden's Prison straight out of the chest (Items migrates the rest on refresh). */
    private static ItemStack prisonLook(String itemId, ItemStack item) {
        if (item == null || itemId == null) {
            return item;
        }
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        if (id.startsWith("dungeon_vestige_")) {
            String piece = id.substring("dungeon_vestige_".length());
            String display = piece.isEmpty() ? piece : Character.toUpperCase(piece.charAt(0)) + piece.substring(1);
            rename(item, "§7Dungeon Vestige " + display, "§7Shackled Vestige §8· §7" + display);
        }
        return item;
    }

    private static void rename(ItemStack item, String legacy, String wanted) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null && legacy.equals(meta.getDisplayName())) {
            meta.setDisplayName(wanted);
            item.setItemMeta(meta);
        }
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
        return prisonLook(itemId, factory.create(itemId));
    }
}
