package de.aetherion.farming;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillService;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin bridge to the skill spine and coins in AetherionItems. Safe when Items is missing,
 * mid-reload, or an older build without the Eldervale skills — calls just stop crediting.
 *
 * <p>Farming skill flags are looked up by name so a Farming jar never hard-links to an enum
 * constant the live Items jar might not have yet.
 */
public final class FarmingSkills {

    public static final String SOIL_SENSE = "SOIL_SENSE";
    public static final String ROW_RHYTHM = "ROW_RHYTHM";
    public static final String BLUE_RIBBON = "BLUE_RIBBON";
    public static final String BIRD_LAW = "BIRD_LAW";
    public static final String MARKET_DAY = "MARKET_DAY";

    private static final Map<String, AetherSkill.Flag> FLAGS = new ConcurrentHashMap<>();
    private static final AetherSkill.Flag MISSING = AetherSkill.Flag.NONE;

    private FarmingSkills() {
    }

    /** Bonus Farming XP (minigames, orders, discoveries). */
    public static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FARMING, amount);
            }
        } catch (LinkageError ignored) {
        }
    }

    /** Focus Farming skill + level bar, or {@code null} with none equipped. */
    public static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.FARMING);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /** 0–5: one step per rarity tier (every 20 levels) of the best Farming skill. */
    public static int boostTier(Player player) {
        return SkillProgression.rarityTier(level(player));
    }

    /** Best Farming skill level (1 when Items is offline). */
    public static int level(Player player) {
        try {
            SkillService skills = skills();
            return skills == null || player == null ? 1 : skills.highestLevel(player, AetherSkill.Category.FARMING);
        } catch (LinkageError ignored) {
            return 1;
        }
    }

    /** Equipped Farming flag, by name ({@link #ROW_RHYTHM}, …). */
    public static boolean has(Player player, String flagName) {
        AetherSkill.Flag flag = flag(flagName);
        if (flag == null || player == null) {
            return false;
        }
        try {
            SkillService skills = skills();
            return skills != null && skills.hasFlag(player, flag);
        } catch (LinkageError ignored) {
            return false;
        }
    }

    /** Effect scale of the equipped skill carrying {@code flagName}; 0 when not equipped. */
    public static double scale(Player player, String flagName) {
        if (!has(player, flagName)) {
            return 0.0d;
        }
        try {
            SkillService skills = skills();
            return skills == null ? 0.0d : skills.multiplier(player, flag(flagName));
        } catch (LinkageError ignored) {
            return 0.0d;
        }
    }

    public static void coins(Player player, long amount) {
        CoinService coins = coinService();
        if (coins != null && player != null && amount > 0L) {
            coins.add(player, amount);
        }
    }

    public static boolean takeCoins(Player player, long amount) {
        CoinService coins = coinService();
        return coins != null && player != null && amount >= 0L && coins.take(player, amount);
    }

    public static long balance(Player player) {
        CoinService coins = coinService();
        return coins == null || player == null ? 0L : coins.get(player);
    }

    private static AetherSkill.Flag flag(String name) {
        if (name == null) {
            return null;
        }
        AetherSkill.Flag flag = FLAGS.computeIfAbsent(name.toUpperCase(Locale.ROOT), key -> {
            try {
                return AetherSkill.Flag.valueOf(key);
            } catch (IllegalArgumentException | LinkageError missing) {
                return MISSING;
            }
        });
        return flag == MISSING ? null : flag;
    }

    private static CoinService coinService() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null ? null : items.getCoins();
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
