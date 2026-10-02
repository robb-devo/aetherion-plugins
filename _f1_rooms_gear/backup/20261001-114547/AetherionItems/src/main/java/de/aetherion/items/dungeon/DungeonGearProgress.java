package de.aetherion.items.dungeon;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.Rarity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonGearProgress {

    public static final int LEVELS_PER_CORE = 25;
    public static final int MAX_LEVEL = 100;
    private static final int BAR_WIDTH = 8;

    private DungeonGearProgress() {
    }

    public static boolean canLevel(ItemStack item, ItemManager items) {
        if (item == null || items == null) {
            return false;
        }
        String id = items.getItemId(item);
        if (DungeonArmor.isNativeDungeonGear(item, items) || DungeonCore.isAetherionEndgame(id)) {
            return true;
        }
        return DungeonCore.canInfuse(id) && DungeonCore.tier(item) > 0;
    }

    public static int level(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer stored = meta.getPersistentDataContainer().get(ItemKeys.dungeonGearLevel(), PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, stored));
    }

    public static int maxLevel(ItemStack item) {
        return Math.min(MAX_LEVEL, LEVELS_PER_CORE * (1 + Math.max(0, DungeonCore.tier(item))));
    }

    public static int xp(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer stored = meta.getPersistentDataContainer().get(ItemKeys.dungeonGearXp(), PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, stored);
    }

    public static int xpToNext(int level) {
        return xpToNext(level, LEVELS_PER_CORE);
    }

    public static int xpToNext(int level, ItemStack item) {
        return xpToNext(level, maxLevel(item));
    }

    public static int xpToNext(int level, int maxLevel) {
        int cap = Math.max(LEVELS_PER_CORE, maxLevel);
        if (level >= cap) {
            return 0;
        }
        int core = Math.max(0, (cap / LEVELS_PER_CORE) - 1);
        int n = level + 1;
        return 35 + n * (12 + core * 10) + (n * n * (1 + core));
    }

    public static double dungeonLevelMultiplier(int level) {
        return 1.0d + (0.01d * Math.max(0, level));
    }

    public static int dungeonLevelPercent(int level) {
        return (int) Math.round((dungeonLevelMultiplier(level) - 1.0d) * 100.0d);
    }

    /**
     * Dungeon-only combat mult on top of displayed base (+ rarity applied separately).
     * Cores + gear level only — no flat dungeon ×3.20.
     */
    public static double dungeonBonusMultiplier(ItemStack item, ItemManager items, int level) {
        double mult = 1.0d;
        if (item == null || items == null) {
            return dungeonLevelMultiplier(level);
        }
        String id = items.getItemId(item);
        int core = DungeonCore.tier(item);
        if (core > 0 && DungeonCore.canInfuse(id)) {
            mult *= DungeonCore.dungeonStatMultiplier(core);
        }
        if (canLevel(item, items)) {
            mult *= dungeonLevelMultiplier(level);
        }
        return mult;
    }

    /** @deprecated use {@link #dungeonBonusMultiplier} */
    @Deprecated
    public static double dungeonMultiplier(ItemStack item, ItemManager items, int level) {
        return dungeonBonusMultiplier(item, items, level);
    }

    /** Percent stronger in dungeons from cores + gear level (matches combat bonus). */
    public static int combinedDungeonPercent(ItemStack item, ItemManager items, int level) {
        return (int) Math.round((dungeonBonusMultiplier(item, items, level) - 1.0d) * 100.0d);
    }

    public static double wornLevelMultiplier(Player player, ItemManager items) {
        if (player == null || items == null) {
            return 1.0d;
        }
        ItemStack[] slots = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
        double sum = 0.0d;
        int count = 0;
        for (ItemStack slot : slots) {
            if (slot == null || slot.getType().isAir() || !canLevel(slot, items)) {
                continue;
            }
            sum += dungeonLevelMultiplier(level(slot));
            count++;
        }
        return count <= 0 ? 1.0d : sum / count;
    }

    public static int killXp(LivingEntity victim, boolean boss) {
        if (victim == null) {
            return 0;
        }
        double hp = Math.max(1.0, victim.getMaxHealth());
        int amount = (int) Math.round(hp / 85.0d);
        if (boss) {
            amount = Math.max(90, amount + 80);
        }
        return Math.max(8, Math.min(boss ? 160 : 42, amount));
    }

    public static boolean grant(ItemStack item, ItemManager items, int amount) {
        if (amount <= 0 || !canLevel(item, items)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        ensure(meta);
        int cap = maxLevel(item);
        int level = levelFrom(meta);
        if (level >= cap) {
            return false;
        }
        int current = xpFrom(meta) + amount;
        boolean leveled = false;
        while (level < cap && current >= xpToNext(level, item)) {
            current -= xpToNext(level, item);
            level++;
            leveled = true;
        }
        meta.getPersistentDataContainer().set(ItemKeys.dungeonGearLevel(), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(ItemKeys.dungeonGearXp(), PersistentDataType.INTEGER, level >= cap ? 0 : current);
        patchLore(meta, item, items, level, current);
        item.setItemMeta(meta);
        return leveled;
    }

    public static void ensure(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        if (!meta.getPersistentDataContainer().has(ItemKeys.dungeonGearLevel(), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(ItemKeys.dungeonGearLevel(), PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(ItemKeys.dungeonGearXp(), PersistentDataType.INTEGER, 0);
        }
    }

    public static void ensure(ItemStack item, ItemManager items) {
        if (!canLevel(item, items)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        ensure(meta);
        patchLore(meta, item, items, levelFrom(meta), xpFrom(meta));
        item.setItemMeta(meta);
    }

    public static void grantToLoadout(Player player, ItemManager items, int amount) {
        if (player == null || items == null || amount <= 0) {
            return;
        }
        var inventory = player.getInventory();
        ItemStack[] slots = {
                inventory.getItemInMainHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };
        boolean anyLevel = false;
        int highest = 0;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            boolean leveled = grant(stack, items, amount);
            highest = Math.max(highest, level(stack));
            anyLevel |= leveled;
            switch (i) {
                case 0 -> inventory.setItemInMainHand(stack);
                case 1 -> inventory.setHelmet(stack);
                case 2 -> inventory.setChestplate(stack);
                case 3 -> inventory.setLeggings(stack);
                case 4 -> inventory.setBoots(stack);
                default -> {
                }
            }
        }
        if (anyLevel) {
            int cap = LEVELS_PER_CORE;
            for (ItemStack slot : slots) {
                if (slot != null && canLevel(slot, items)) {
                    cap = Math.max(cap, maxLevel(slot));
                }
            }
            player.sendMessage("§5Dungeon gear reached level §f" + highest + "§5/§f" + cap + "§5.");
        }
    }

    public static List<String> previewLines(int level, int currentXp, double baseMultiplier) {
        return previewLines(level, currentXp, baseMultiplier, LEVELS_PER_CORE, null);
    }

    public static List<String> previewLines(int level, int currentXp, double baseMultiplier, int cap, Rarity rarity) {
        int percent = (int) Math.round((Math.max(1.0d, baseMultiplier) * dungeonLevelMultiplier(level) - 1.0d) * 100.0d);
        return displayLines(level, currentXp, percent, Math.max(LEVELS_PER_CORE, cap), rarity);
    }

    public static List<String> levelLines(int level, int currentXp) {
        return previewLines(level, currentXp, 1.0d);
    }

    private static void patchLore(ItemMeta meta, ItemStack item, ItemManager items, int level, int currentXp) {
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        List<String> next = new ArrayList<>();
        boolean wrote = false;
        for (String line : lore) {
            if (isProgressLore(line)) {
                if (!wrote) {
                    next.addAll(displayLines(
                            level,
                            currentXp,
                            combinedDungeonPercent(item, items, level),
                            maxLevel(item),
                            items == null ? null : items.getRarity(item)
                    ));
                    wrote = true;
                }
                continue;
            }
            next.add(line);
        }
        if (!wrote) {
            next.add("");
            next.addAll(displayLines(
                    level,
                    currentXp,
                    combinedDungeonPercent(item, items, level),
                    maxLevel(item),
                    items == null ? null : items.getRarity(item)
            ));
        }
        meta.setLore(next);
    }

    private static List<String> displayLines(int level, int currentXp, int dungeonPercent, int cap, Rarity rarity) {
        int safeCap = Math.max(LEVELS_PER_CORE, Math.min(MAX_LEVEL, cap));
        List<String> lines = new ArrayList<>();
        lines.add("§5Dungeon: §f+" + Math.max(0, dungeonPercent) + "% stats");
        int rarityPercent = DungeonCore.rarityStatPercent(rarity);
        if (rarityPercent > 0) {
            lines.add("§7Rarity bonus: §f+" + rarityPercent + "% stats");
        }
        lines.add("§8Level cap §f" + safeCap + " §8(+25 per core)");
        lines.add(levelBarLine(level, currentXp, safeCap));
        return lines;
    }

    public static String levelBarLine(int level, int currentXp, int cap) {
        int safeCap = Math.max(LEVELS_PER_CORE, Math.min(MAX_LEVEL, cap));
        if (level >= safeCap) {
            return "§5Lv §f" + safeCap + "§8/" + safeCap + " " + bar(BAR_WIDTH, BAR_WIDTH) + " §8max";
        }
        int needed = Math.max(1, xpToNext(level, safeCap));
        int filled = (int) Math.round(BAR_WIDTH * Math.min(1.0d, currentXp / (double) needed));
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        return "§5Lv §f" + level + "§8/" + safeCap + " " + bar(filled, BAR_WIDTH)
                + " §f" + currentXp + "§8/" + needed;
    }

    private static String bar(int filled, int width) {
        int safeFilled = Math.max(0, Math.min(width, filled));
        return "§5" + "■".repeat(safeFilled) + "§8" + "□".repeat(Math.max(0, width - safeFilled));
    }

    private static boolean isProgressLore(String line) {
        if (line == null) {
            return false;
        }
        String plain = line.replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();
        if (plain.isEmpty()) {
            return false;
        }
        if (plain.contains("dungeon-bound") || plain.contains("dungeon weapon")) {
            return true;
        }
        if (plain.contains("from gear level") || plain.contains("gear level:") || plain.contains("levels in dungeons")) {
            return true;
        }
        if (plain.contains("rarity bonus") || plain.contains("level cap")) {
            return true;
        }
        if (plain.contains("in dungeons:") && plain.contains("%")) {
            return true;
        }
        if (plain.startsWith("dungeon:") && plain.contains("%")) {
            return true;
        }
        if (plain.contains("xp:") && plain.contains("/")) {
            return true;
        }
        if (plain.startsWith("lv ") || plain.startsWith("lv")) {
            return plain.contains("/") || plain.contains("■") || plain.contains("□");
        }
        return isBarOnly(plain);
    }

    private static boolean isBarOnly(String plain) {
        String stripped = plain.replace("■", "").replace("□", "").replace("█", "").replace("░", "").replace(" ", "");
        return stripped.isEmpty() && (plain.contains("■") || plain.contains("□") || plain.contains("█") || plain.contains("░"));
    }

    private static int levelFrom(ItemMeta meta) {
        Integer stored = meta.getPersistentDataContainer().get(ItemKeys.dungeonGearLevel(), PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, stored));
    }

    private static int xpFrom(ItemMeta meta) {
        Integer stored = meta.getPersistentDataContainer().get(ItemKeys.dungeonGearXp(), PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, stored);
    }
}
