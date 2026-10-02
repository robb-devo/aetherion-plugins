package de.aetherion.foraging.isle;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexCatalog;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.economy.IsleHeartwood;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin bridge to the skill spine, coins, Codex, heartwoods and inventory helpers in AetherionItems.
 * Safe when Items is missing, mid-reload, or an older build without the Eldervale foraging skills —
 * calls just stop crediting.
 *
 * <p>Foraging flags are looked up by name so this jar never hard-links to an enum constant the live
 * Items jar might not have yet (same rule as Mining's {@code MineSkills}).
 */
public final class ForageBridge {

    public static final String GROVE_BORN = "GROVE_BORN";
    public static final String SAP_SENSE = "SAP_SENSE";
    public static final String STEADY_HANDS = "STEADY_HANDS";
    public static final String DEADFALL_DANCER = "DEADFALL_DANCER";
    public static final String BOARD_RATES = "BOARD_RATES";
    public static final String HEART_HUNTER = "HEART_HUNTER";

    private static final Map<String, AetherSkill.Flag> FLAGS = new ConcurrentHashMap<>();
    private static final AetherSkill.Flag MISSING = AetherSkill.Flag.NONE;

    private ForageBridge() {
    }

    /** Bonus Foraging XP (finds, orders, discoveries, events). */
    public static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FORAGING, amount);
            }
        } catch (LinkageError ignored) {
        }
    }

    /** Best Foraging skill level (1 when Items is offline). */
    public static int level(Player player) {
        try {
            SkillService skills = skills();
            return skills == null || player == null ? 1 : Math.max(1, skills.highestLevel(player, AetherSkill.Category.FORAGING));
        } catch (LinkageError ignored) {
            return 1;
        }
    }

    /** Equipped Foraging flag, by name ({@link #SAP_SENSE}, …). */
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
            return skills == null ? 0.0d : Math.max(0.0d, skills.multiplier(player, flag(flagName)));
        } catch (LinkageError ignored) {
            return 0.0d;
        }
    }

    /** True when the live Items jar knows the Eldervale foraging skills at all. */
    public static boolean skillsInstalled() {
        return flag(GROVE_BORN) != null;
    }

    public static void coins(Player player, long amount) {
        CoinService coins = coinService();
        if (coins != null && player != null && amount > 0L) {
            coins.add(player, amount);
        }
    }

    public static boolean takeCoins(Player player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        CoinService coins = coinService();
        return coins != null && player != null && coins.take(player, amount);
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

    public static void give(Player player, ItemStack item, Location at) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        try {
            InventoryDrops.give(player, item, at);
        } catch (LinkageError ignored) {
            give(player, item);
        }
    }

    /**
     * Files one broken log with the Codex. Foraging cancels the break event (it pays wood itself) and
     * the Codex listener skips cancelled events, so without this no chopped log was ever counted.
     */
    public static void codexWood(Player player, Material material) {
        CodexService codex = codex();
        if (codex == null || player == null || material == null) {
            return;
        }
        try {
            String id = CodexCatalog.resolveBlock(material);
            if (id != null) {
                codex.addBlock(player, id);
            }
        } catch (LinkageError ignored) {
        }
    }

    /** Lifetime Codex count for a block family id ({@code oak}, {@code dark_oak}, …). */
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

    /** Codex top foragers for a wood: "1. Name — 12,345". Empty when Codex is offline. */
    public static List<String> codexTop(String codexId, int limit) {
        CodexService codex = codex();
        if (codex == null || codexId == null) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (CodexService.Rank rank : codex.topBlocks(codexId)) {
                if (out.size() >= limit) {
                    break;
                }
                out.add("§e" + rank.place() + ". §f" + rank.name() + " §8— §7" + ForageText.coins(rank.amount()));
            }
            return out;
        } catch (LinkageError ignored) {
            return List.of();
        }
    }

    /** Items registry id of a stack ({@code isle_heartwood_oak}, {@code forage_find_frostcone}, …) or null. */
    public static String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        try {
            return stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    public static boolean isHeartwood(ItemStack stack) {
        String id = itemId(stack);
        return id != null && id.startsWith("isle_heartwood_");
    }

    /** A heartwood of the given wood ({@code oak}, {@code cherry}, …), or null when Items can't make one. */
    public static ItemStack heartwood(String woodKey) {
        try {
            for (IsleHeartwood wood : IsleHeartwood.values()) {
                if (wood.woodKey().equalsIgnoreCase(woodKey)) {
                    return wood.create();
                }
            }
        } catch (LinkageError ignored) {
        }
        return null;
    }

    /** Heartwood for a log drop (typed wood), or null. */
    public static ItemStack heartwoodFor(Material drop) {
        try {
            IsleHeartwood wood = IsleHeartwood.fromWoodDrop(drop);
            return wood == null ? null : wood.create();
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /** Display name for a heartwood id, or the id prettified. */
    public static String heartwoodName(String woodKey) {
        try {
            for (IsleHeartwood wood : IsleHeartwood.values()) {
                if (wood.woodKey().equalsIgnoreCase(woodKey)) {
                    return wood.coloredName();
                }
            }
        } catch (LinkageError ignored) {
        }
        return "§d" + ForageText.pretty(woodKey) + " Heartwood";
    }

    /** Rarity-framed tooltip + Items registry id, same polish as heartwoods. Quietly skipped without Items. */
    public static void polish(org.bukkit.inventory.meta.ItemMeta meta, String itemId, String rarityName) {
        if (meta == null) {
            return;
        }
        try {
            meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId);
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getItemManager() != null) {
                de.aetherion.items.model.Rarity rarity;
                try {
                    rarity = de.aetherion.items.model.Rarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    rarity = de.aetherion.items.model.Rarity.UNCOMMON;
                }
                items.getItemManager().applyItemData(meta, rarity, new de.aetherion.items.model.ItemStats());
            }
            de.aetherion.items.item.ItemPresentation.polish(meta);
        } catch (LinkageError ignored) {
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
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null ? null : items.getSkills();
        } catch (LinkageError ignored) {
            return null;
        }
    }
}
