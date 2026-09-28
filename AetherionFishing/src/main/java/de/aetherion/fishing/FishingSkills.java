package de.aetherion.fishing;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin bridge to the skill spine and coins in AetherionItems (softdepend). Every call is safe
 * when Items is missing or mid-reload — the loop just stops crediting skills.
 *
 * <p>Eldervale skill flags are looked up by name so a Fishing jar never hard-links to an enum
 * constant an older Items jar does not have yet.
 */
public final class FishingSkills {

    public static final String LAKE_SENSE = "LAKE_SENSE";
    public static final String STEADY_LINE = "STEADY_LINE";
    public static final String TALL_TALES = "TALL_TALES";
    public static final String TIDE_READER = "TIDE_READER";

    private static final Map<String, AetherSkill.Flag> FLAGS = new ConcurrentHashMap<>();
    private static final AetherSkill.Flag MISSING = AetherSkill.Flag.NONE;

    private FishingSkills() {
    }

    /** Bonus Fishing XP for timing / streaks / discoveries (base catch XP stays in Items). */
    public static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FISHING, amount);
            }
        } catch (LinkageError ignored) {
        }
    }

    /** Focus Fishing skill + level bar, or {@code null} with none equipped. */
    public static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.FISHING);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /** Best Fishing skill level (1 when Items is offline). */
    public static int level(Player player) {
        try {
            SkillService skills = skills();
            return skills == null || player == null ? 1 : skills.fishingLevel(player);
        } catch (LinkageError ignored) {
            return 1;
        }
    }

    /** Equipped Fishing flag, by name ({@link #STEADY_LINE}, …). */
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

    /**
     * 0–1 strength of an equipped flag: about a third at level 1, half by level 40, full by level 75.
     * 0 when the skill is not equipped.
     */
    public static double power(Player player, String flagName) {
        double scale = scale(player, flagName);
        return scale <= 0.0d ? 0.0d : Math.min(1.0d, scale / 3.0d);
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
