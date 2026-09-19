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

public final class FarmingHoeProgress {

    public static final double FORTUNE_PER_LEVEL = 1.0;
    public static final double HARVEST_PER_LEVEL = 2.0;

    private FarmingHoeProgress() {
    }

    public static boolean isHoe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = itemId(item);
        return id != null && id.toLowerCase(Locale.ROOT).startsWith("farming_hoe");
    }

    public static boolean hasProgress(ItemStack item) {
        return isHoe(item) && (level(item) > 1 || xp(item) > 0);
    }

    public static int maxLevel(ItemStack item) {
        return SkillToolCaps.maxLevel(itemId(item));
    }

    public static void init(ItemStack item) {
        if (!isHoe(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (!meta.getPersistentDataContainer().has(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(ItemKeys.farmingHoeXp(), PersistentDataType.INTEGER, 0);
        }
        int cap = maxLevel(item);
        int level = Math.min(cap, levelFrom(meta, cap));
        meta.getPersistentDataContainer().set(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER, level);
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
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, Math.min(cap, value));
    }

    private static int xpFrom(ItemMeta meta) {
        Integer value = meta.getPersistentDataContainer().get(ItemKeys.farmingHoeXp(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public static int xpToNext(int level, int maxLevel) {
        return SkillToolCaps.xpToNext(level, maxLevel);
    }

    public static void copy(ItemStack from, ItemStack to) {
        if (!isHoe(from) || !isHoe(to) || to.getItemMeta() == null) {
            return;
        }
        ItemMeta meta = to.getItemMeta();
        int cap = maxLevel(to);
        int copiedLevel = Math.min(cap, level(from));
        int copiedXp = xp(from);
        meta.getPersistentDataContainer().set(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER, copiedLevel);
        meta.getPersistentDataContainer().set(ItemKeys.farmingHoeXp(), PersistentDataType.INTEGER, copiedXp);
        meta.setLore(withProgress(meta.getLore(), copiedLevel, copiedXp, cap));
        ItemPresentation.polish(meta);
        to.setItemMeta(meta);
    }

    public static void grant(Player player, ItemStack item, ItemManager items, int amount) {
        if (player == null || items == null || amount <= 0 || !isHoe(item)) {
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
        double harvestGain = 0;
        while (xp >= needed && needed > 0 && level < cap) {
            xp -= needed;
            level++;
            leveled = true;
            double mult = SkillToolCaps.gainMult(level);
            fortuneGain += FORTUNE_PER_LEVEL * mult;
            harvestGain += HARVEST_PER_LEVEL * mult;
            needed = xpToNext(level, cap);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (leveled) {
            ItemStats stats = items.getItemStats(item);
            stats.setFortune(stats.getFortune() + fortuneGain);
            stats.setHarvestSpread(stats.getHarvestSpread() + harvestGain);
            items.saveItemStats(meta, stats);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            ItemLore.updateDisplayedStats(lore, stats, items.getProfile(item));
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(ItemKeys.farmingHoeLevel(), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(ItemKeys.farmingHoeXp(), PersistentDataType.INTEGER, level >= cap ? 0 : xp);
        meta.setLore(withProgress(meta.getLore(), level, level >= cap ? 0 : xp, cap));
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        if (leveled) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
            player.sendMessage("§aThe Furrow Ledger clocked in. §fHoe Lv. " + level + "§8/§7" + cap + "§a.");
        }
    }

    public static void refreshLore(ItemStack item) {
        if (!isHoe(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        int cap = maxLevel(item);
        meta.setLore(withProgress(meta.getLore(), level(item), xp(item), cap));
        item.setItemMeta(meta);
    }

    private static List<String> withProgress(List<String> lore, int level, int xp, int maxLevel) {
        List<String> next = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        next.removeIf(FarmingHoeProgress::isProgressLine);
        int insert = LoreLayout.indexBeforeValue(next);
        List<String> block = new ArrayList<>();
        block.add("§7Hoe Level: §f" + level + "§8/§7" + maxLevel);
        if (level >= maxLevel) {
            block.add("§6MAX. The field has nothing left to prove.");
        } else {
            int needed = xpToNext(level, maxLevel);
            int filled = needed <= 0 ? 10 : Math.min(10, (xp * 10) / needed);
            block.add("§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                    + " §7" + xp + "§8/§7" + needed);
        }
        block.add("§8+" + (int) FORTUNE_PER_LEVEL + " Fortune, +" + (int) HARVEST_PER_LEVEL
                + " Harvest per level §8(more after Lv.60).");
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
        return line.contains("Hoe Level:")
                || line.contains("The field has nothing left to prove.")
                || (line.contains("█") && (line.contains("/") || line.contains("MAX")))
                || line.contains("Harvest per level");
    }

    private static String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }
}
