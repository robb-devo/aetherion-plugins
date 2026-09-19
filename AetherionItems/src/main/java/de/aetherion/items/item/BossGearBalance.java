package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.ItemStats;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * REV1: rebase overworld boss weapons/armor/accessories onto the REV5 combat curve.
 * Existing items migrate on the next inventory refresh.
 */
public final class BossGearBalance {

    public static final int REV = 1;

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
            case "warped_blade" -> weapon(52, 8, 9, 60);
            case "gravwell_cleaver" -> weapon(115, 4, 13, 110);
            case "bridged_axe" -> weapon(90, 14, 13, 90);
            case "skuldugery_shortbow" -> weapon(88, 16, 12, 85);
            case "aetherblade" -> weapon(155, 18, 20, 140);
            case "staff_of_technical_difficulties" -> weapon(105, 10, 12, 72);
            case "aetherion_void_stick" -> {
                ItemStats stats = new ItemStats();
                stats.setDamage(100);
                yield stats;
            }
            case "squids_boot" -> {
                ItemStats stats = new ItemStats();
                stats.setDefense(20);
                stats.setHealth(24);
                stats.setSpeed(6);
                yield stats;
            }
            case "aetherion_helmet" -> aetherion(70, 110, 22, 22, 6, 42, 2);
            case "aetherion_chestplate" -> aetherion(140, 180, 42, 35, 12, 70, 3);
            case "aetherion_leggings" -> aetherion(105, 145, 32, 28, 8, 55, 3);
            case "aetherion_boots" -> aetherion(70, 110, 22, 20, 6, 42, 6);
            case "pickaxe_core_of_the_burrower" -> {
                ItemStats stats = new ItemStats();
                stats.setFortune(62);
                stats.setMiningPower(28);
                stats.setDefense(14);
                yield stats;
            }
            case "insolvent_ledger" -> {
                ItemStats stats = new ItemStats();
                stats.setDamage(20);
                yield stats;
            }
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
    }
}
