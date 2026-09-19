package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.ItemStats;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Overwrites main-ladder bases when {@link BalanceTargets#LADDER_REV} bumps.
 * Booster extras are preserved via flat delta rebase.
 */
public final class StarterSetBalance {

    public static final int REV = BalanceTargets.LADDER_REV;

    private StarterSetBalance() {
    }

    /** Stamp factory-fresh ladder stats and mark current REV. */
    public static void ensureBase(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null) {
            return;
        }
        ItemStats base = currentBase(itemId);
        if (base == null) {
            return;
        }
        copyLadder(stats, base);
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.starterRev(), PersistentDataType.INTEGER, REV);
        }
    }

    public static boolean apply(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null || meta == null) {
            return false;
        }
        if (!isLadder(itemId)) {
            return false;
        }
        Integer revision = meta.getPersistentDataContainer().get(ItemKeys.starterRev(), PersistentDataType.INTEGER);
        if (revision != null && revision >= REV) {
            return false;
        }
        int fromRev = revision == null ? 0 : revision;
        ItemStats newBase = currentBase(itemId);
        ItemStats oldBase = resolveOldBase(itemId, fromRev, stats);
        if (oldBase == null || newBase == null) {
            return false;
        }
        rebaseFlat(stats, oldBase, newBase);
        meta.getPersistentDataContainer().set(ItemKeys.starterRev(), PersistentDataType.INTEGER, REV);
        return true;
    }

    /**
     * Pick the correct pre-image for rebase.
     * <p>REV0→curve: use legacy factory numbers so inflated PDC (e.g. sword +310) collapses to the curve.
     * Items that were wrongly stamped REV3/4 while still on legacy get healed the same way.
     */
    private static ItemStats resolveOldBase(String itemId, int fromRev, ItemStats actual) {
        ItemStats legacy = legacyBase(itemId);
        ItemStats curve = currentBase(itemId);
        if (legacy != null && looksStuckOnLegacy(actual, legacy, curve)) {
            return legacy;
        }
        if (fromRev < 3 && legacy != null) {
            return legacy;
        }
        return previousCurveBase(itemId, fromRev);
    }

    /** True when primary ladder stats still match the old factory table (bad first migrate). */
    private static boolean looksStuckOnLegacy(ItemStats actual, ItemStats legacy, ItemStats curve) {
        if (actual == null || legacy == null || curve == null) {
            return false;
        }
        if (legacy.getDamage() > 0 && curve.getDamage() > 0) {
            return near(actual.getDamage(), legacy.getDamage()) && actual.getDamage() > curve.getDamage() * 1.25;
        }
        if (legacy.getMiningPower() > 0 && curve.getMiningPower() > 0) {
            return near(actual.getMiningPower(), legacy.getMiningPower())
                    && actual.getMiningPower() > curve.getMiningPower() * 1.25;
        }
        if (legacy.getDefense() > 0 && curve.getDefense() > 0) {
            return near(actual.getDefense(), legacy.getDefense())
                    && actual.getDefense() > curve.getDefense() * 1.25;
        }
        return false;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= Math.max(1.0, b * 0.08);
    }

    private static void rebaseFlat(ItemStats actual, ItemStats oldBase, ItemStats newBase) {
        actual.setMiningPower(newBase.getMiningPower() + Math.max(0, actual.getMiningPower() - oldBase.getMiningPower()));
        actual.setFortune(newBase.getFortune() + Math.max(0, actual.getFortune() - oldBase.getFortune()));
        actual.setDamage(newBase.getDamage() + Math.max(0, actual.getDamage() - oldBase.getDamage()));
        actual.setDefense(newBase.getDefense() + Math.max(0, actual.getDefense() - oldBase.getDefense()));
        actual.setHealth(newBase.getHealth() + Math.max(0, actual.getHealth() - oldBase.getHealth()));
        actual.setSpread(newBase.getSpread() + Math.max(0, actual.getSpread() - oldBase.getSpread()));
        actual.setAttackSpread(newBase.getAttackSpread() + Math.max(0, actual.getAttackSpread() - oldBase.getAttackSpread()));
        actual.setCritChance(newBase.getCritChance() + Math.max(0, actual.getCritChance() - oldBase.getCritChance()));
        actual.setCritDamage(newBase.getCritDamage() + Math.max(0, actual.getCritDamage() - oldBase.getCritDamage()));
        actual.setHarvestSpread(newBase.getHarvestSpread() + Math.max(0, actual.getHarvestSpread() - oldBase.getHarvestSpread()));
        actual.setFishingSpeed(newBase.getFishingSpeed() + Math.max(0, actual.getFishingSpeed() - oldBase.getFishingSpeed()));
        actual.setFishingCatch(newBase.getFishingCatch() + Math.max(0, actual.getFishingCatch() - oldBase.getFishingCatch()));
        actual.setSpeed(newBase.getSpeed() + Math.max(0, actual.getSpeed() - oldBase.getSpeed()));
    }

    private static void copyLadder(ItemStats target, ItemStats base) {
        target.setMiningPower(base.getMiningPower());
        target.setFortune(base.getFortune());
        target.setDamage(base.getDamage());
        target.setDefense(base.getDefense());
        target.setHealth(base.getHealth());
        target.setSpread(base.getSpread());
        target.setAttackSpread(base.getAttackSpread());
        target.setCritChance(base.getCritChance());
        target.setCritDamage(base.getCritDamage());
        target.setHarvestSpread(base.getHarvestSpread());
        target.setFishingSpeed(base.getFishingSpeed());
        target.setFishingCatch(base.getFishingCatch());
        target.setSpeed(base.getSpeed());
    }

    private static boolean isLadder(String itemId) {
        return currentBase(itemId) != null;
    }

    private static int tier(String itemId) {
        return SkillToolCaps.tierFromId(itemId);
    }

    private static String baseId(String itemId) {
        return itemId.replaceAll("_\\d+$", "");
    }

    static ItemStats currentBase(String itemId) {
        if (itemId == null) {
            return null;
        }
        String id = itemId.toLowerCase();
        int t = tier(id);
        String base = baseId(id);

        ItemStats stats = new ItemStats();
        switch (base) {
            case "beginner_pickaxe" -> {
                pick(stats, 2, 6, 0);
                return stats;
            }
            case "simple_pickaxe" -> {
                pick(stats, 10, 16, 1);
                return stats;
            }
            case "combat_helmet" -> {
                combatPiece(stats,
                        BalanceTargets.at(BalanceTargets.COMBAT_HELM, t, 0),
                        BalanceTargets.at(BalanceTargets.COMBAT_HELM, t, 1),
                        BalanceTargets.at(BalanceTargets.COMBAT_HELM, t, 2),
                        BalanceTargets.at(BalanceTargets.COMBAT_HELM, t, 3), 0, 0);
                return stats;
            }
            case "combat_chestplate" -> {
                combatPiece(stats,
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 0),
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 1),
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 2),
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 3),
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 4),
                        BalanceTargets.at(BalanceTargets.COMBAT_CHEST, t, 5));
                return stats;
            }
            case "combat_leggings" -> {
                combatPiece(stats,
                        BalanceTargets.at(BalanceTargets.COMBAT_LEGS, t, 0),
                        BalanceTargets.at(BalanceTargets.COMBAT_LEGS, t, 1),
                        BalanceTargets.at(BalanceTargets.COMBAT_LEGS, t, 2),
                        BalanceTargets.at(BalanceTargets.COMBAT_LEGS, t, 3), 0, 0);
                return stats;
            }
            case "combat_boots" -> {
                combatPiece(stats,
                        BalanceTargets.at(BalanceTargets.COMBAT_BOOTS, t, 0),
                        BalanceTargets.at(BalanceTargets.COMBAT_BOOTS, t, 1),
                        BalanceTargets.at(BalanceTargets.COMBAT_BOOTS, t, 2),
                        BalanceTargets.at(BalanceTargets.COMBAT_BOOTS, t, 3), 0, 0);
                return stats;
            }
            case "combat_sword" -> {
                sword(stats,
                        BalanceTargets.at(BalanceTargets.COMBAT_SWORD, t, 0),
                        BalanceTargets.at(BalanceTargets.COMBAT_SWORD, t, 1),
                        BalanceTargets.at(BalanceTargets.COMBAT_SWORD, t, 2),
                        BalanceTargets.at(BalanceTargets.COMBAT_SWORD, t, 3));
                return stats;
            }
            case "mining_helmet" -> {
                minePiece(stats,
                        BalanceTargets.at(BalanceTargets.MINING_HELM, t, 0),
                        BalanceTargets.at(BalanceTargets.MINING_HELM, t, 1),
                        BalanceTargets.at(BalanceTargets.MINING_HELM, t, 2),
                        BalanceTargets.at(BalanceTargets.MINING_HELM, t, 3));
                return stats;
            }
            case "mining_chestplate" -> {
                minePiece(stats,
                        BalanceTargets.at(BalanceTargets.MINING_CHEST, t, 0),
                        BalanceTargets.at(BalanceTargets.MINING_CHEST, t, 1),
                        BalanceTargets.at(BalanceTargets.MINING_CHEST, t, 2),
                        BalanceTargets.at(BalanceTargets.MINING_CHEST, t, 3));
                return stats;
            }
            case "mining_leggings" -> {
                minePiece(stats,
                        BalanceTargets.at(BalanceTargets.MINING_LEGS, t, 0),
                        BalanceTargets.at(BalanceTargets.MINING_LEGS, t, 1),
                        BalanceTargets.at(BalanceTargets.MINING_LEGS, t, 2),
                        BalanceTargets.at(BalanceTargets.MINING_LEGS, t, 3));
                return stats;
            }
            case "mining_boots" -> {
                minePiece(stats,
                        BalanceTargets.at(BalanceTargets.MINING_BOOTS, t, 0),
                        BalanceTargets.at(BalanceTargets.MINING_BOOTS, t, 1),
                        BalanceTargets.at(BalanceTargets.MINING_BOOTS, t, 2),
                        BalanceTargets.at(BalanceTargets.MINING_BOOTS, t, 3));
                return stats;
            }
            case "mining_pickaxe" -> {
                pick(stats,
                        BalanceTargets.at(BalanceTargets.MINING_PICK, t, 0),
                        BalanceTargets.at(BalanceTargets.MINING_PICK, t, 1),
                        BalanceTargets.at(BalanceTargets.MINING_PICK, t, 2));
                return stats;
            }
            case "farming_helmet" -> {
                farmArmor(stats,
                        BalanceTargets.at(BalanceTargets.FARMING_HELM, t, 0),
                        BalanceTargets.at(BalanceTargets.FARMING_HELM, t, 1),
                        BalanceTargets.at(BalanceTargets.FARMING_HELM, t, 2), 0);
                return stats;
            }
            case "farming_chestplate" -> {
                farmArmor(stats,
                        BalanceTargets.at(BalanceTargets.FARMING_CHEST, t, 0),
                        BalanceTargets.at(BalanceTargets.FARMING_CHEST, t, 1),
                        BalanceTargets.at(BalanceTargets.FARMING_CHEST, t, 2), 0);
                return stats;
            }
            case "farming_leggings" -> {
                farmArmor(stats,
                        BalanceTargets.at(BalanceTargets.FARMING_LEGS, t, 0),
                        BalanceTargets.at(BalanceTargets.FARMING_LEGS, t, 1),
                        BalanceTargets.at(BalanceTargets.FARMING_LEGS, t, 2), 0);
                return stats;
            }
            case "farming_boots" -> {
                farmArmor(stats,
                        BalanceTargets.at(BalanceTargets.FARMING_BOOTS, t, 0),
                        BalanceTargets.at(BalanceTargets.FARMING_BOOTS, t, 1),
                        BalanceTargets.at(BalanceTargets.FARMING_BOOTS, t, 2),
                        BalanceTargets.at(BalanceTargets.FARMING_BOOTS, t, 3));
                return stats;
            }
            case "farming_hoe" -> {
                farmHoe(stats,
                        BalanceTargets.at(BalanceTargets.FARMING_HOE, t, 0),
                        BalanceTargets.at(BalanceTargets.FARMING_HOE, t, 1));
                return stats;
            }
            case "foraging_helmet" -> {
                forageArmor(stats,
                        BalanceTargets.at(BalanceTargets.FORAGING_HELM, t, 0),
                        BalanceTargets.at(BalanceTargets.FORAGING_HELM, t, 1),
                        BalanceTargets.at(BalanceTargets.FORAGING_HELM, t, 2), 0);
                return stats;
            }
            case "foraging_chestplate" -> {
                forageArmor(stats,
                        BalanceTargets.at(BalanceTargets.FORAGING_CHEST, t, 0),
                        BalanceTargets.at(BalanceTargets.FORAGING_CHEST, t, 1),
                        BalanceTargets.at(BalanceTargets.FORAGING_CHEST, t, 2), 0);
                return stats;
            }
            case "foraging_leggings" -> {
                forageArmor(stats,
                        BalanceTargets.at(BalanceTargets.FORAGING_LEGS, t, 0),
                        BalanceTargets.at(BalanceTargets.FORAGING_LEGS, t, 1),
                        BalanceTargets.at(BalanceTargets.FORAGING_LEGS, t, 2), 0);
                return stats;
            }
            case "foraging_boots" -> {
                forageArmor(stats,
                        BalanceTargets.at(BalanceTargets.FORAGING_BOOTS, t, 0),
                        BalanceTargets.at(BalanceTargets.FORAGING_BOOTS, t, 1),
                        BalanceTargets.at(BalanceTargets.FORAGING_BOOTS, t, 2),
                        BalanceTargets.at(BalanceTargets.FORAGING_BOOTS, t, 3));
                return stats;
            }
            case "foraging_axe" -> {
                forageAxe(stats,
                        BalanceTargets.at(BalanceTargets.FORAGING_AXE, t, 0),
                        BalanceTargets.at(BalanceTargets.FORAGING_AXE, t, 1),
                        BalanceTargets.at(BalanceTargets.FORAGING_AXE, t, 2));
                return stats;
            }
            case "fishing_helmet" -> {
                fishArmor(stats,
                        BalanceTargets.at(BalanceTargets.FISHING_HELM, t, 0),
                        BalanceTargets.at(BalanceTargets.FISHING_HELM, t, 1),
                        BalanceTargets.at(BalanceTargets.FISHING_HELM, t, 2),
                        BalanceTargets.at(BalanceTargets.FISHING_HELM, t, 3), 0);
                return stats;
            }
            case "fishing_chestplate" -> {
                fishArmor(stats,
                        BalanceTargets.at(BalanceTargets.FISHING_CHEST, t, 0),
                        BalanceTargets.at(BalanceTargets.FISHING_CHEST, t, 1),
                        BalanceTargets.at(BalanceTargets.FISHING_CHEST, t, 2),
                        BalanceTargets.at(BalanceTargets.FISHING_CHEST, t, 3), 0);
                return stats;
            }
            case "fishing_leggings" -> {
                fishArmor(stats,
                        BalanceTargets.at(BalanceTargets.FISHING_LEGS, t, 0),
                        BalanceTargets.at(BalanceTargets.FISHING_LEGS, t, 1),
                        BalanceTargets.at(BalanceTargets.FISHING_LEGS, t, 2),
                        BalanceTargets.at(BalanceTargets.FISHING_LEGS, t, 3), 0);
                return stats;
            }
            case "fishing_boots" -> {
                fishArmor(stats,
                        BalanceTargets.at(BalanceTargets.FISHING_BOOTS, t, 0),
                        BalanceTargets.at(BalanceTargets.FISHING_BOOTS, t, 1),
                        BalanceTargets.at(BalanceTargets.FISHING_BOOTS, t, 2),
                        BalanceTargets.at(BalanceTargets.FISHING_BOOTS, t, 3),
                        BalanceTargets.at(BalanceTargets.FISHING_BOOTS, t, 4));
                return stats;
            }
            case "fishing_rod" -> {
                fishRod(stats,
                        BalanceTargets.at(BalanceTargets.FISHING_ROD, t, 0),
                        BalanceTargets.at(BalanceTargets.FISHING_ROD, t, 1),
                        BalanceTargets.at(BalanceTargets.FISHING_ROD, t, 2));
                return stats;
            }
            default -> {
                return null;
            }
        }
    }

    /** Curve snapshot just before {@code fromRev} → current. */
    private static ItemStats previousCurveBase(String itemId, int fromRev) {
        ItemStats stats = currentBase(itemId);
        if (stats == null) {
            return null;
        }
        // REV3 had BalanceTargets without combat-armor Damage; REV4+ include it.
        if (fromRev < 4) {
            String base = baseId(itemId.toLowerCase());
            if (base.startsWith("combat_") && !base.contains("sword")) {
                stats.setDamage(0);
            }
        }
        return stats;
    }

    /**
     * Pre-BalanceTargets factory numbers (CustomItem hardcodes).
     * Used so a one-shot rebase can strip obsolete bases without wiping real booster extras.
     */
    static ItemStats legacyBase(String itemId) {
        if (itemId == null) {
            return null;
        }
        String id = itemId.toLowerCase();
        int t = tier(id);
        String base = baseId(id);
        ItemStats stats = new ItemStats();
        switch (base) {
            case "combat_sword" -> {
                sword(stats,
                        v(t, 20, 45, 90, 170, 310),
                        v(t, 6, 10, 15, 20, 28),
                        v(t, 5, 8, 12, 15, 18),
                        v(t, 40, 60, 85, 110, 140));
                return stats;
            }
            case "combat_helmet" -> {
                combatPiece(stats,
                        v(t, 6, 14, 28, 50, 90),
                        v(t, 10, 20, 36, 60, 100),
                        v(t, 3, 5, 8, 12, 18),
                        0, 0, 0);
                return stats;
            }
            case "combat_chestplate" -> {
                combatPiece(stats,
                        v(t, 12, 28, 52, 95, 170),
                        v(t, 16, 32, 55, 95, 160),
                        v(t, 4, 7, 12, 18, 26),
                        0,
                        v(t, 5, 7, 10, 13, 16),
                        v(t, 35, 50, 70, 95, 120));
                return stats;
            }
            case "combat_leggings" -> {
                combatPiece(stats,
                        v(t, 8, 18, 36, 65, 115),
                        v(t, 12, 24, 42, 70, 120),
                        v(t, 3, 5, 8, 12, 18),
                        0, 0, 0);
                return stats;
            }
            case "combat_boots" -> {
                combatPiece(stats,
                        v(t, 6, 12, 24, 45, 80),
                        v(t, 10, 18, 32, 55, 95),
                        v(t, 2, 4, 6, 9, 14),
                        0, 0, 0);
                return stats;
            }
            case "mining_pickaxe" -> {
                pick(stats,
                        v(t, 22, 42, 80, 150, 280),
                        v(t, 40, 75, 140, 260, 480),
                        v(t, 3, 6, 12, 20, 32));
                return stats;
            }
            case "mining_helmet" -> {
                minePiece(stats,
                        v(t, 4, 8, 14, 22, 35),
                        v(t, 10, 22, 42, 80, 150),
                        v(t, 18, 45, 85, 160, 300),
                        v(t, 2, 4, 8, 14, 24));
                return stats;
            }
            case "mining_chestplate" -> {
                minePiece(stats,
                        v(t, 8, 24, 40, 65, 100),
                        v(t, 14, 35, 60, 110, 200),
                        v(t, 24, 65, 120, 220, 420),
                        v(t, 3, 6, 12, 20, 34));
                return stats;
            }
            case "mining_leggings" -> {
                minePiece(stats,
                        v(t, 6, 16, 28, 45, 70),
                        v(t, 12, 28, 50, 90, 170),
                        v(t, 20, 55, 100, 180, 350),
                        v(t, 2, 5, 10, 16, 28));
                return stats;
            }
            case "mining_boots" -> {
                minePiece(stats,
                        v(t, 4, 8, 14, 22, 35),
                        v(t, 10, 22, 42, 80, 150),
                        v(t, 16, 45, 85, 160, 300),
                        v(t, 2, 4, 8, 14, 24));
                return stats;
            }
            default -> {
                return null;
            }
        }
    }

    private static double v(int tier, double a, double b, double c, double d, double e) {
        return switch (tier) {
            case 5 -> e;
            case 4 -> d;
            case 3 -> c;
            case 2 -> b;
            default -> a;
        };
    }

    private static void sword(ItemStats stats, double damage, double as, double cc, double cd) {
        stats.setDamage(damage);
        stats.setAttackSpread(as);
        stats.setCritChance(cc);
        stats.setCritDamage(cd);
    }

    private static void combatPiece(ItemStats stats, double def, double hp, double as, double damage, double cc, double cd) {
        stats.setDefense(def);
        stats.setHealth(hp);
        stats.setAttackSpread(as);
        stats.setDamage(damage);
        if (cc > 0) {
            stats.setCritChance(cc);
            stats.setCritDamage(cd);
        }
    }

    private static void minePiece(ItemStats stats, double def, double mp, double fort, double spread) {
        stats.setDefense(def);
        stats.setMiningPower(mp);
        stats.setFortune(fort);
        stats.setSpread(spread);
    }

    private static void pick(ItemStats stats, double mp, double fort, double spread) {
        stats.setMiningPower(mp);
        stats.setFortune(fort);
        stats.setSpread(spread);
    }

    private static void farmArmor(ItemStats stats, double def, double fort, double harvest, double speed) {
        stats.setDefense(def);
        stats.setFortune(fort);
        stats.setHarvestSpread(harvest);
        stats.setSpeed(speed);
    }

    private static void farmHoe(ItemStats stats, double fort, double harvest) {
        stats.setFortune(fort);
        stats.setHarvestSpread(harvest);
    }

    private static void forageArmor(ItemStats stats, double def, double fort, double spread, double speed) {
        stats.setDefense(def);
        stats.setFortune(fort);
        stats.setSpread(spread);
        stats.setSpeed(speed);
    }

    private static void forageAxe(ItemStats stats, double mp, double fort, double spread) {
        stats.setMiningPower(mp);
        stats.setFortune(fort);
        stats.setSpread(spread);
    }

    private static void fishArmor(ItemStats stats, double def, double fort, double fs, double fc, double speed) {
        stats.setDefense(def);
        stats.setFortune(fort);
        stats.setFishingSpeed(fs);
        stats.setFishingCatch(fc);
        stats.setSpeed(speed);
    }

    private static void fishRod(ItemStats stats, double fort, double fs, double fc) {
        stats.setFortune(fort);
        stats.setFishingSpeed(fs);
        stats.setFishingCatch(fc);
    }
}
