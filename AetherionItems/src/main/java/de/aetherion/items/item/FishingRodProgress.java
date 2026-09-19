package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Fishing tool progression. Only rods level (armor/charms keep baked stats, no XP).
 * Caps: T1=50, T2=75, T3=100.
 */
public final class FishingRodProgress {

    public static final double ROD_SPEED_PER_LEVEL = 0.2;
    public static final double ROD_CATCH_PER_LEVEL = 0.5;
    public static final double ROD_FORTUNE_PER_LEVEL = 0.5;
    public static final double ARMOR_SPEED_PER_LEVEL = 0.1;
    public static final double ARMOR_CATCH_PER_LEVEL = 0.25;
    public static final double ARMOR_FORTUNE_PER_LEVEL = 0.25;
    public static final double CHARM_SPEED_PER_LEVEL = 0.15;
    public static final double CHARM_CATCH_PER_LEVEL = 0.4;
    public static final double CHARM_FORTUNE_PER_LEVEL = 0.4;

    private enum Kind {
        ROD,
        ARMOR,
        CHARM
    }

    private FishingRodProgress() {
    }

    public static boolean isRod(ItemStack item) {
        return isRodId(itemId(item));
    }

    public static boolean isArmor(ItemStack item) {
        return isArmorId(itemId(item));
    }

    public static boolean isCharm(ItemStack item) {
        return isCharmId(itemId(item));
    }

    /** Legacy: armor/charm may still carry frozen progress. Leveling is rod-only. */
    public static boolean isGear(ItemStack item) {
        return isRod(item) || isArmor(item) || isCharm(item);
    }

    public static boolean canLevel(ItemStack item) {
        return isRod(item);
    }

    public static boolean hasProgress(ItemStack item) {
        return canLevel(item) && (level(item) > 1 || xp(item) > 0);
    }

    public static int maxLevel(ItemStack item) {
        return SkillToolCaps.maxLevel(itemId(item));
    }

    public static void init(ItemStack item) {
        if (!canLevel(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        ensureKeys(meta);
        int cap = maxLevel(item);
        int level = Math.min(cap, levelFrom(meta, cap));
        meta.getPersistentDataContainer().set(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER, level);
        meta.setLore(withProgress(meta.getLore(), Kind.ROD, level, xpFrom(meta), cap));
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
    }

    public static void ensureMeta(ItemMeta meta, boolean rod) {
        if (!rod || meta == null) {
            return;
        }
        ensureKeys(meta);
        int cap = 100;
        meta.setLore(withProgress(meta.getLore(), Kind.ROD, levelFrom(meta, cap), xpFrom(meta), cap));
    }

    public static void ensureCharmMeta(ItemMeta meta) {
        // Charms no longer level — leave existing meta alone.
    }

    public static int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        return levelFrom(item.getItemMeta(), maxLevel(item));
    }

    public static int xp(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        return xpFrom(item.getItemMeta());
    }

    public static int extraLevels(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        return Math.max(0, levelFrom(meta, 100) - 1);
    }

    public static double speedPerLevel(boolean rod) {
        return rod ? ROD_SPEED_PER_LEVEL : ARMOR_SPEED_PER_LEVEL;
    }

    public static double catchPerLevel(boolean rod) {
        return rod ? ROD_CATCH_PER_LEVEL : ARMOR_CATCH_PER_LEVEL;
    }

    public static double fortunePerLevel(boolean rod) {
        return rod ? ROD_FORTUNE_PER_LEVEL : ARMOR_FORTUNE_PER_LEVEL;
    }

    private static int levelFrom(ItemMeta meta, int cap) {
        if (meta == null) {
            return 1;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, Math.min(cap, value));
    }

    private static int xpFrom(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.fishingRodXp(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public static int xpToNext(int level, int maxLevel) {
        return SkillToolCaps.xpToNext(level, maxLevel);
    }

    public static void copy(ItemStack from, ItemStack to) {
        if (!canLevel(from) || !canLevel(to) || to.getItemMeta() == null) {
            return;
        }
        ItemMeta meta = to.getItemMeta();
        int cap = maxLevel(to);
        int copiedLevel = Math.min(cap, level(from));
        int copiedXp = xp(from);
        meta.getPersistentDataContainer().set(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER, copiedLevel);
        meta.getPersistentDataContainer().set(ItemKeys.fishingRodXp(), PersistentDataType.INTEGER, copiedXp);
        meta.setLore(withProgress(meta.getLore(), Kind.ROD, copiedLevel, copiedXp, cap));
        ItemPresentation.polish(meta);
        to.setItemMeta(meta);
    }

    public static void grantEquipped(Player player, ItemManager items, int amount) {
        if (player == null || items == null || amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int leveled = 0;
        leveled += grant(player, inventory.getItemInMainHand(), items, amount, inventory::setItemInMainHand);
        leveled += grant(player, inventory.getItemInOffHand(), items, amount, inventory::setItemInOffHand);
        if (leveled <= 0) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.35f);
        player.sendMessage("§bThe brine soaked in. §fRod leveled§b.");
    }

    public static void grant(Player player, ItemStack item, ItemManager items, int amount) {
        if (player != null) {
            grant(player, item, items, amount, player.getInventory()::setItemInMainHand);
        }
    }

    private static int grant(
            Player player,
            ItemStack item,
            ItemManager items,
            int amount,
            Consumer<ItemStack> writeBack
    ) {
        if (player == null || items == null || amount <= 0 || !canLevel(item)) {
            return 0;
        }
        int cap = maxLevel(item);
        int level = level(item);
        if (level >= cap) {
            return 0;
        }
        int start = level;
        int xp = xp(item) + amount;
        int needed = xpToNext(level, cap);
        double fortuneGain = 0;
        double catchGain = 0;
        double speedGain = 0;
        while (xp >= needed && needed > 0 && level < cap) {
            xp -= needed;
            level++;
            double mult = SkillToolCaps.gainMult(level);
            fortuneGain += ROD_FORTUNE_PER_LEVEL * mult;
            catchGain += ROD_CATCH_PER_LEVEL * mult;
            speedGain += ROD_SPEED_PER_LEVEL * mult;
            needed = xpToNext(level, cap);
        }
        int gained = level - start;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        if (gained > 0) {
            ItemStats stats = items.getItemStats(item);
            stats.setFortune(stats.getFortune() + fortuneGain);
            stats.setFishingCatch(stats.getFishingCatch() + catchGain);
            stats.setFishingSpeed(stats.getFishingSpeed() + speedGain);
            items.saveItemStats(meta, stats);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            ItemLore.updateDisplayedStats(lore, stats, items.getProfile(item));
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(ItemKeys.fishingRodXp(), PersistentDataType.INTEGER, level >= cap ? 0 : xp);
        meta.setLore(withProgress(meta.getLore(), Kind.ROD, level, level >= cap ? 0 : xp, cap));
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        if (writeBack != null) {
            writeBack.accept(item);
        }
        return gained > 0 ? 1 : 0;
    }

    public static void refreshLore(ItemStack item) {
        if (!canLevel(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        int cap = maxLevel(item);
        meta.setLore(withProgress(meta.getLore(), Kind.ROD, level(item), xp(item), cap));
        item.setItemMeta(meta);
    }

    private static void ensureKeys(ItemMeta meta) {
        if (!meta.getPersistentDataContainer().has(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(ItemKeys.fishingRodLevel(), PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(ItemKeys.fishingRodXp(), PersistentDataType.INTEGER, 0);
        }
    }

    private static List<String> withProgress(List<String> lore, Kind kind, int level, int xp, int maxLevel) {
        List<String> next = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        next.removeIf(FishingRodProgress::isProgressLine);
        if (kind != Kind.ROD) {
            return next;
        }
        int insert = LoreLayout.indexBeforeValue(next);
        List<String> block = new ArrayList<>();
        block.add("§7Rod Level: §f" + level + "§8/§7" + maxLevel);
        if (level >= maxLevel) {
            block.add("§6MAX. The ocean has nothing left to prove.");
        } else {
            int needed = xpToNext(level, maxLevel);
            int filled = needed <= 0 ? 10 : Math.min(10, (xp * 10) / needed);
            block.add("§b" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                    + " §7" + xp + "§8/§7" + needed);
        }
        block.add("§8+" + ROD_FORTUNE_PER_LEVEL + " Fortune, +" + ROD_CATCH_PER_LEVEL
                + " Fish Catch, +" + ROD_SPEED_PER_LEVEL + " Fish Speed per level §8(more after Lv.60).");
        if (insert < 0) {
            next.addAll(block);
        } else {
            next.addAll(insert, block);
        }
        return next;
    }

    private static boolean isProgressLine(String line) {
        if (line == null) {
            return false;
        }
        return line.contains("Rod Level:")
                || line.contains("Gear Level:")
                || line.contains("Charm Level:")
                || line.contains("The ocean has nothing left to prove.")
                || (line.contains("█") && (line.contains("/") || line.contains("MAX")))
                || line.contains("Fish Speed per level");
    }

    private static String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }

    static boolean isRodId(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).startsWith("fishing_rod");
    }

    static boolean isArmorId(String id) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.startsWith("fishing_helmet")
                || lower.startsWith("fishing_chestplate")
                || lower.startsWith("fishing_leggings")
                || lower.startsWith("fishing_boots");
    }

    static boolean isCharmId(String id) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.equals("charm_fishing")
                || lower.equals("charm_fishing_2")
                || lower.equals("charm_fishing_3");
    }
}
