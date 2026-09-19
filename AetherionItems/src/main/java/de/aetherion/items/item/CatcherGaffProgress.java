package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

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
 * Off-hand Catcher Gaff progression. Levels from successful pet catches.
 * Caps: T1=50, T2=75, T3=100. Higher pet rarity = more XP.
 */
public final class CatcherGaffProgress {

    public static final double CATCH_PER_LEVEL = 0.35;
    public static final double SPEED_PER_LEVEL = 0.12;

    private CatcherGaffProgress() {
    }

    public static boolean isGaff(ItemStack item) {
        return isGaffId(itemId(item));
    }

    public static boolean canLevel(ItemStack item) {
        return isGaff(item);
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
        meta.getPersistentDataContainer().set(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER, level);
        meta.setLore(withProgress(meta.getLore(), level, xpFrom(meta), cap));
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
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

    public static int xpForCatch(Rarity rarity) {
        if (rarity == null) {
            return 6;
        }
        return switch (rarity) {
            case COMMON -> 6;
            case UNCOMMON -> 10;
            case RARE -> 16;
            case EPIC -> 26;
            case LEGENDARY -> 40;
            case MYTHIC -> 55;
            case AETHERED -> 80;
        };
    }

    public static void copy(ItemStack from, ItemStack to) {
        if (!canLevel(from) || !canLevel(to) || to.getItemMeta() == null) {
            return;
        }
        ItemMeta meta = to.getItemMeta();
        int cap = maxLevel(to);
        int copiedLevel = Math.min(cap, level(from));
        int copiedXp = xp(from);
        meta.getPersistentDataContainer().set(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER, copiedLevel);
        meta.getPersistentDataContainer().set(ItemKeys.catcherGaffXp(), PersistentDataType.INTEGER, copiedXp);
        meta.setLore(withProgress(meta.getLore(), copiedLevel, copiedXp, cap));
        ItemPresentation.polish(meta);
        to.setItemMeta(meta);
    }

    public static void grantEquipped(Player player, ItemManager items, int amount) {
        if (player == null || items == null || amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int leveled = 0;
        leveled += grant(player, inventory.getItemInOffHand(), items, amount, inventory::setItemInOffHand);
        leveled += grant(player, inventory.getItemInMainHand(), items, amount, inventory::setItemInMainHand);
        if (leveled <= 0) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.45f);
        player.sendMessage("§dThe snare learned something. §fGaff leveled§d.");
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
        double catchGain = 0;
        double speedGain = 0;
        while (xp >= needed && needed > 0 && level < cap) {
            xp -= needed;
            level++;
            double mult = SkillToolCaps.gainMult(level);
            catchGain += CATCH_PER_LEVEL * mult;
            speedGain += SPEED_PER_LEVEL * mult;
            needed = xpToNext(level, cap);
        }
        int gained = level - start;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        if (gained > 0) {
            ItemStats stats = items.getItemStats(item);
            stats.setCatchRate(stats.getCatchRate() + catchGain);
            stats.setSpeed(stats.getSpeed() + speedGain);
            items.saveItemStats(meta, stats);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            ItemLore.updateDisplayedStats(lore, stats, items.getProfile(item));
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(ItemKeys.catcherGaffXp(), PersistentDataType.INTEGER, level >= cap ? 0 : xp);
        meta.setLore(withProgress(meta.getLore(), level, level >= cap ? 0 : xp, cap));
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
        meta.setLore(withProgress(meta.getLore(), level(item), xp(item), cap));
        item.setItemMeta(meta);
    }

    public static int xpToNext(int level, int maxLevel) {
        return SkillToolCaps.xpToNext(level, maxLevel);
    }

    private static void ensureKeys(ItemMeta meta) {
        if (!meta.getPersistentDataContainer().has(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(ItemKeys.catcherGaffXp(), PersistentDataType.INTEGER, 0);
        }
    }

    private static int levelFrom(ItemMeta meta, int cap) {
        if (meta == null) {
            return 1;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.catcherGaffLevel(), PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, Math.min(cap, value));
    }

    private static int xpFrom(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.catcherGaffXp(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    private static List<String> withProgress(List<String> lore, int level, int xp, int maxLevel) {
        List<String> next = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        next.removeIf(CatcherGaffProgress::isProgressLine);
        int insert = LoreLayout.indexBeforeValue(next);
        List<String> block = new ArrayList<>();
        block.add("§7Gaff Level: §f" + level + "§8/§7" + maxLevel);
        if (level >= maxLevel) {
            block.add("§6MAX. The menagerie filed a cease-and-desist.");
        } else {
            int needed = xpToNext(level, maxLevel);
            int filled = needed <= 0 ? 10 : Math.min(10, (xp * 10) / needed);
            block.add("§d" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                    + " §7" + xp + "§8/§7" + needed);
        }
        block.add("§8+" + CATCH_PER_LEVEL + " Catch Rate, +" + SPEED_PER_LEVEL
                + " Speed per level §8(more after Lv.60).");
        block.add("§8Levels from catches. Rarer pets = more XP.");
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
        return line.contains("Gaff Level:")
                || line.contains("The menagerie filed a cease-and-desist.")
                || (line.contains("█") && (line.contains("/") || line.contains("MAX")))
                || line.contains("Speed per level")
                || line.contains("Levels from catches.");
    }

    private static String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }

    static boolean isGaffId(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).startsWith("catcher_gaff");
    }
}
