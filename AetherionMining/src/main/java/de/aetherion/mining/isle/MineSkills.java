package de.aetherion.mining.isle;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin bridge to the skill spine, coins, Codex and inventory helpers in AetherionItems.
 * Safe when Items is missing, mid-reload, or an older build without the Eldervale mining
 * skills — calls just stop crediting.
 *
 * <p>Mining flags are looked up by name so a Mining jar never hard-links to an enum constant
 * the live Items jar might not have yet (same rule as Farming's {@code FarmingSkills}).
 */
public final class MineSkills {

    public static final String BEDROCK_BORN = "BEDROCK_BORN";
    public static final String WORK_SONG = "WORK_SONG";
    public static final String GEODE_NOSE = "GEODE_NOSE";
    public static final String UNION_CARD = "UNION_CARD";
    public static final String SEAM_READER = "SEAM_READER";
    public static final String DEPTH_GAUGE = "DEPTH_GAUGE";
    public static final String CAVE_SENSE = "CAVE_SENSE";

    private static final Map<String, AetherSkill.Flag> FLAGS = new ConcurrentHashMap<>();
    private static final AetherSkill.Flag MISSING = AetherSkill.Flag.NONE;

    private MineSkills() {
    }

    /** Bonus Mining XP (rhythm, finds, orders, discoveries). */
    public static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.MINING, amount);
            }
        } catch (LinkageError ignored) {
        }
    }

    /** Focus Mining skill + level bar, or {@code null} with none equipped. */
    public static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.MINING);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /** Best Mining skill level (1 when Items is offline). */
    public static int level(Player player) {
        try {
            SkillService skills = skills();
            return skills == null || player == null ? 1 : skills.highestLevel(player, AetherSkill.Category.MINING);
        } catch (LinkageError ignored) {
            return 1;
        }
    }

    /** 0–5: one step per rarity tier (every 20 levels) of the best Mining skill. */
    public static int boostTier(Player player) {
        try {
            return SkillProgression.rarityTier(level(player));
        } catch (LinkageError ignored) {
            return 0;
        }
    }

    /** Equipped Mining flag, by name ({@link #WORK_SONG}, …). */
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

    /** Hand an item over (inventory first, overflow at the feet) through Items' drop helper. */
    public static void give(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        try {
            InventoryDrops.give(player, item);
        } catch (LinkageError ignored) {
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /** Lifetime Codex count for a block family id ({@code diamond}, {@code coal}, …). */
    public static long codexBlocks(Player player, String codexId) {
        CodexService codex = codex();
        if (codex == null || player == null || codexId == null) {
            return 0L;
        }
        try {
            return codex.blocks(player, codexId);
        } catch (LinkageError ignored) {
            return 0L;
        }
    }

    /** Codex top miners for a block family: "1. Name — 12,345". Empty when Codex is offline. */
    public static List<String> codexTop(String codexId, int limit) {
        CodexService codex = codex();
        if (codex == null || codexId == null) {
            return List.of();
        }
        try {
            List<CodexService.Rank> ranks = codex.topBlocks(codexId);
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (CodexService.Rank rank : ranks) {
                if (out.size() >= limit) {
                    break;
                }
                out.add("§e" + rank.place() + ". §f" + rank.name() + " §8— §7" + MineText.coins(rank.amount()));
            }
            return out;
        } catch (LinkageError ignored) {
            return List.of();
        }
    }

    /** Spawns an Ore Troll through Items (Troll Run). Returns false when Items can't. */
    public static boolean spawnTroll(Player aggro, org.bukkit.Location at) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getOreTrollListener() == null) {
                return false;
            }
            return items.getOreTrollListener().spawn(at, aggro) != null;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    /** Same as {@link #spawnTroll} but hands back the troll so callers can track it. */
    public static org.bukkit.entity.Entity spawnTrollEntity(Player aggro, org.bukkit.Location at) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getOreTrollListener() == null) {
                return null;
            }
            Object spawned = items.getOreTrollListener().spawn(at, aggro);
            return spawned instanceof org.bukkit.entity.Entity entity ? entity : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    public static boolean isTroll(org.bukkit.entity.Entity entity) {
        try {
            return de.aetherion.items.mining.OreTrollListener.isOreTroll(entity);
        } catch (LinkageError ignored) {
            return false;
        }
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

    private static CodexService codex() {
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null ? null : items.getCodex();
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
