package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.ItemStats;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * REV2: Wave 3 — raid uniques sit at T5 × ~1.15, not a second endgame.

 * Existing items migrate on the next inventory refresh.
 */
public final class BossGearBalance {

    public static final int REV = 2;

    /** Bridged Axe base life steal (core skillEffect multiplies on top). */
    public static final double BRIDGED_LIFESTEAL = 0.05d;

    private BossGearBalance() {
    }

    public static void stamp(ItemMeta meta) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.bossGearRev(), PersistentDataType.INTEGER, REV);
        }
    }

    public static boolean migrate(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null || meta == null) {
            return false;
        }
        if ("ashen_katana".equalsIgnoreCase(itemId) && legacyAshenKatana(stats)) {
            copyCombat(stats, base(itemId));
            stamp(meta);
            return true;
        }
        Integer revision = meta.getPersistentDataContainer().get(ItemKeys.bossGearRev(), PersistentDataType.INTEGER);
        if (revision != null && revision >= REV) {
            return false;
        }
        ItemStats fresh = base(itemId);
        if (fresh == null) {
            return false;
        }
        copyCombat(stats, fresh);
        if ("bridged_axe".equalsIgnoreCase(itemId)) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.lifesteal(),
                    PersistentDataType.DOUBLE,
                    BRIDGED_LIFESTEAL
            );
        }
        stamp(meta);
        return true;
    }

    public static ItemStats base(String itemId) {
        if (itemId == null) {
            return null;
        }
        return switch (itemId.toLowerCase()) {
            case "warped_blade" -> weapon(48, 8, 9, 62);
            case "gravwell_cleaver" -> weapon(80, 8, 13, 96);
            case "ashen_katana" -> weapon(90, 9, 80, 140);
            case "bridged_axe" -> weapon(80, 12, 13, 90);
            case "skuldugery_shortbow" -> weapon(72, 14, 12, 88);
            case "aetherblade" -> weapon(100, 16, 16, 115);
            case "staff_of_technical_difficulties" -> weapon(72, 10, 12, 80);
            case "aetherion_void_stick" -> {
                ItemStats stats = new ItemStats();
                stats.setDamage(72);
                yield stats;
            }
            case "squids_boot" -> {
                ItemStats stats = new ItemStats();
                stats.setDefense(22);
                stats.setHealth(34);
                stats.setSpeed(8);
                yield stats;
            }
            case "aetherion_helmet" -> aetherion(42, 58, 14, 14, 8, 50, 3);
            case "aetherion_chestplate" -> aetherion(70, 96, 26, 22, 16, 110, 4);
            case "aetherion_leggings" -> aetherion(56, 72, 18, 18, 10, 68, 3);
            case "aetherion_boots" -> aetherion(42, 50, 12, 12, 8, 50, 6);
            case "pickaxe_core_of_the_burrower" -> {
                ItemStats stats = new ItemStats();
                stats.setFortune(128);
                stats.setMiningPower(96);
                stats.setSpread(8);
                stats.setDefense(14);
                yield stats;
            }
            case "insolvent_ledger" -> {
                ItemStats stats = new ItemStats();
                stats.setDamage(18);
                yield stats;
            }
            case "hollow_longbow" -> weapon(88, 12, 12, 85);
            case "ironhide_helmet" -> tank(20, 36);
            case "ironhide_chestplate" -> tank(38, 56);
            case "ironhide_leggings" -> tank(30, 44);
            case "ironhide_boots" -> tank(20, 34);
            default -> null;
        };
    }

    private static ItemStats weapon(double damage, double attackSpread, double critChance, double critDamage) {
        ItemStats stats = new ItemStats();
        stats.setDamage(damage);
        stats.setAttackSpread(attackSpread);
        stats.setCritChance(critChance);
        stats.setCritDamage(critDamage);
        return stats;
    }

    private static ItemStats tank(double defense, double health) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        return stats;
    }

    private static ItemStats aetherion(
            double defense,
            double health,
            double damage,
            double attackSpread,
            double critChance,
            double critDamage,
            double speed
    ) {
        ItemStats stats = new ItemStats();
        stats.setDefense(defense);
        stats.setHealth(health);
        stats.setDamage(damage);
        stats.setAttackSpread(attackSpread);
        stats.setCritChance(critChance);
        stats.setCritDamage(critDamage);
        stats.setSpeed(speed);
        return stats;
    }

    /** Pre-polish Dev Menu katana: 68 damage, 9 spread, 15% crit, 108% crit damage. */
    private static boolean legacyAshenKatana(ItemStats stats) {
        return near(stats.getDamage(), 68)
                && near(stats.getAttackSpread(), 9)
                && near(stats.getCritChance(), 15)
                && near(stats.getCritDamage(), 108);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.05;
    }

    private static void copyCombat(ItemStats target, ItemStats base) {
        target.setDamage(base.getDamage());
        target.setDefense(base.getDefense());
        target.setHealth(base.getHealth());
        target.setAttackSpread(base.getAttackSpread());
        target.setCritChance(base.getCritChance());
        target.setCritDamage(base.getCritDamage());
        target.setSpeed(base.getSpeed());
        target.setFortune(base.getFortune());
        target.setMiningPower(base.getMiningPower());
        target.setSpread(base.getSpread());
    }
}
