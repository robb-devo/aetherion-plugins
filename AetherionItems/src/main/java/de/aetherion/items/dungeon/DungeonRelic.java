package de.aetherion.items.dungeon;

import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonRelic {

    private DungeonRelic() {
    }

    public static boolean isUnidentified(String itemId) {
        return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith("dungeon_relic_");
    }

    public static boolean isWeaponRelic(String itemId) {
        return isUnidentified(itemId) && itemId.toLowerCase(Locale.ROOT).endsWith("_weapon");
    }

    public static DungeonGearTier tier(String itemId) {
        return DungeonGearTier.fromItemId(itemId);
    }

    public static DungeonPiece piece(String itemId) {
        if (!isUnidentified(itemId) || isWeaponRelic(itemId)) {
            return null;
        }
        return DungeonPiece.fromItemId(itemId);
    }

    public static String itemId(DungeonGearTier tier, DungeonPiece piece) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        return "dungeon_relic_" + safe.key() + "_" + piece.id();
    }

    public static String weaponId(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        return "dungeon_relic_" + safe.key() + "_weapon";
    }

    public static String displayName(DungeonGearTier tier, DungeonPiece piece) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        String slot = piece == null ? "Weapon" : piece.display();
        return safe.rarity() == Rarity.LEGENDARY
                ? "§6Unidentified " + slot + " " + safe.roman()
                : "§5Unidentified " + slot + " " + safe.roman();
    }

    public static ItemStats stats(DungeonGearTier tier, DungeonPiece piece) {
        ItemStats stats = new ItemStats();
        boolean t3 = tier == DungeonGearTier.T3;
        if (piece == null) {
            stats.setDamage(t3 ? 8.0 : 5.0);
            return stats;
        }
        switch (piece) {
            case HELMET -> {
                stats.setDefense(t3 ? 6.0 : 4.0);
                stats.setHealth(t3 ? 8.0 : 5.0);
            }
            case CHESTPLATE -> {
                stats.setDefense(t3 ? 9.0 : 6.0);
                stats.setHealth(t3 ? 12.0 : 8.0);
            }
            case LEGGINGS -> {
                stats.setDefense(t3 ? 7.0 : 5.0);
                stats.setHealth(t3 ? 10.0 : 6.0);
            }
            case BOOTS -> {
                stats.setDefense(t3 ? 6.0 : 4.0);
                stats.setHealth(t3 ? 8.0 : 5.0);
            }
        }
        return stats;
    }

    public static List<String> lore(DungeonGearTier tier, DungeonPiece piece, ItemStats stats) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        List<String> lore = new ArrayList<>();
        lore.add(safe.rarityLine());
        lore.add("");
        DungeonArmor.addRelicStatLines(piece == null ? weaponId(safe) : itemId(safe, piece), stats, lore);
        lore.add("");
        lore.add("§7Unidentified overworld " + (piece == null ? "weapon" : piece.display().toLowerCase(Locale.ROOT)) + ".");
        lore.add("§7Boss drop. Pick a set. Dungeons keep their Floor I vestige.");
        lore.add("");
        if (piece == null) {
            lore.add("§eRight-click §7to pick a shape.");
            lore.add("§8Sword · Bow · Wand · Maul · Staff");
        } else {
            lore.add("§eRight-click §7to pick a set.");
            StringBuilder sets = new StringBuilder("§8");
            DungeonCalling[] choices = safe.armorChoices();
            for (int i = 0; i < choices.length; i++) {
                if (i > 0) {
                    sets.append(" · ");
                }
                sets.append(choices[i].display());
            }
            lore.add(sets.toString());
        }
        lore.add("§8Not dungeon gear until a matching core.");
        lore.add("§8There is no undo.");
        return lore;
    }

    public static String randomId(DungeonGearTier tier) {
        return randomArmorId(tier);
    }

    public static String randomArmorId(DungeonGearTier tier) {
        DungeonGearTier safe = tier == null || tier == DungeonGearTier.T1 ? DungeonGearTier.T2 : tier;
        DungeonPiece[] pieces = DungeonPiece.values();
        return itemId(safe, pieces[ThreadLocalRandom.current().nextInt(pieces.length)]);
    }
}
