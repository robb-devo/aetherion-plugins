package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ForagingAxeProgress {

    public static final double FORTUNE_PER_LEVEL = 1.0;
    public static final double SPREAD_PER_LEVEL = 0.15;
    public static final double POWER_PER_LEVEL = 0.35;

    private ForagingAxeProgress() {
    }

    public static boolean isAxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = itemId(item);
        return id != null && id.toLowerCase(Locale.ROOT).startsWith("foraging_axe");
    }

    public static boolean hasProgress(ItemStack item) {
        return isAxe(item) && (level(item) > 1 || xp(item) > 0);
    }

    public static int maxLevel(ItemStack item) {
        return SkillToolCaps.maxLevel(itemId(item));
    }

    public static void init(ItemStack item) {
        if (!isAxe(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (!meta.getPersistentDataContainer().has(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(ItemKeys.foragingAxeXp(), PersistentDataType.INTEGER, 0);
        }
        int cap = maxLevel(item);
        int level = Math.min(cap, levelFrom(meta, cap));
        meta.getPersistentDataContainer().set(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER, level);
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

    private static int levelFrom(ItemMeta meta, int cap) {
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, Math.min(cap, value));
    }

    private static int xpFrom(ItemMeta meta) {
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.foragingAxeXp(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public static int xpToNext(int level, int maxLevel) {
        return SkillToolCaps.xpToNext(level, maxLevel);
    }

    public static void copy(ItemStack from, ItemStack to) {
        if (!isAxe(from) || !isAxe(to) || to.getItemMeta() == null) {
            return;
        }
        ItemMeta meta = to.getItemMeta();
        int cap = maxLevel(to);
        int copiedLevel = Math.min(cap, level(from));
        int copiedXp = xp(from);
        meta.getPersistentDataContainer().set(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER, copiedLevel);
        meta.getPersistentDataContainer().set(ItemKeys.foragingAxeXp(), PersistentDataType.INTEGER, copiedXp);
        meta.setLore(withProgress(meta.getLore(), copiedLevel, copiedXp, cap));
        ItemPresentation.polish(meta);
        to.setItemMeta(meta);
    }

    public static void grant(Player player, ItemStack item, ItemManager items, int amount) {
        if (player == null || items == null || amount <= 0 || !isAxe(item)) {
            return;
        }
        int cap = maxLevel(item);
        int level = level(item);
        if (level >= cap) {
            return;
        }
        int xp = xp(item) + amount;
        int needed = xpToNext(level, cap);
        boolean leveled = false;
        double fortuneGain = 0;
        double spreadGain = 0;
        double powerGain = 0;
        while (xp >= needed && needed > 0 && level < cap) {
            xp -= needed;
            level++;
            leveled = true;
            double mult = SkillToolCaps.gainMult(level);
            fortuneGain += FORTUNE_PER_LEVEL * mult;
            spreadGain += SPREAD_PER_LEVEL * mult;
            powerGain += POWER_PER_LEVEL * mult;
            needed = xpToNext(level, cap);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (leveled) {
            ItemStats stats = items.getItemStats(item);
            stats.setFortune(stats.getFortune() + fortuneGain);
            stats.setSpread(stats.getSpread() + spreadGain);
            stats.setMiningPower(stats.getMiningPower() + powerGain);
            items.saveItemStats(meta, stats);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            ItemLore.updateDisplayedStats(lore, stats, items.getProfile(item));
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(ItemKeys.foragingAxeLevel(), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(ItemKeys.foragingAxeXp(), PersistentDataType.INTEGER, level >= cap ? 0 : xp);
        meta.setLore(withProgress(meta.getLore(), level, level >= cap ? 0 : xp, cap));
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        if (leveled) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.35f);
            player.sendMessage("§aThe axe took a note. §fAxe Lv. " + level + "§8/§7" + cap + "§a.");
        }
    }

    public static void refreshLore(ItemStack item) {
        if (!isAxe(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        int cap = maxLevel(item);
        meta.setLore(withProgress(meta.getLore(), level(item), xp(item), cap));
        item.setItemMeta(meta);
    }

    private static List<String> withProgress(List<String> lore, int level, int xp, int maxLevel) {
        List<String> next = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        next.removeIf(ForagingAxeProgress::isProgressLine);
        int insert = LoreLayout.indexBeforeValue(next);
        List<String> block = new ArrayList<>();
        block.add("§7Axe Level: §f" + level + "§8/§7" + maxLevel);
        if (level >= maxLevel) {
            block.add("§6MAX. The forest has nothing left to prove.");
        } else {
            int needed = xpToNext(level, maxLevel);
            int filled = needed <= 0 ? 10 : Math.min(10, (xp * 10) / needed);
            block.add("§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                    + " §7" + xp + "§8/§7" + needed);
        }
        block.add("§8+" + FORTUNE_PER_LEVEL + " Fortune, +" + POWER_PER_LEVEL
                + " Power, +" + SPREAD_PER_LEVEL + " Spread per level §8(more after Lv.60).");
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
        return line.contains("Axe Level:")
                || line.contains("The forest has nothing left to prove.")
                || (line.contains("█") && (line.contains("/") || line.contains("MAX")))
                || line.contains("Spread per level");
    }

    private static String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }
}
