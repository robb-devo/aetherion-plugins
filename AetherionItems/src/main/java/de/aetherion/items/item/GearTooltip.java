package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.dungeon.DungeonArmor;
import de.aetherion.items.dungeon.DungeonGearProgress;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GearTooltip implements Listener {

    private static final char JOIN = '\u001e';

    private static GearTooltip instance;

    private final JavaPlugin plugin;
    private final ItemManager items;

    public GearTooltip(JavaPlugin plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static boolean expanding(Player player) {
        return false;
    }

    public static void restore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (restore(meta)) {
            item.setItemMeta(meta);
        }
    }

    public static boolean restore(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        String raw = meta.getPersistentDataContainer().get(ItemKeys.loreCache(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        List<String> cached = new ArrayList<>();
        for (String line : raw.split(String.valueOf(JOIN), -1)) {
            cached.add(line);
        }
        meta.setLore(cached);
        // the cache is always English: whatever language the item showed before, it is English now
        LoreLocale.setTag(meta, false);
        return true;
    }

    public static void finish(ItemStack item, ItemManager items, boolean expanded) {
        if (item == null || items == null || !items.isAetherionItem(item)) {
            return;
        }
        // English is canonical: lay the tooltip out in English, localize as the very last step.
        boolean german = LoreLocale.tagged(item);
        Boolean view = LoreLocale.scopedGerman() == null ? Boolean.valueOf(german) : null;
        LoreLocale.canonicalize(item);
        try (LoreLocale.Scope ignored = LoreLocale.scope(view, false)) {
            finishEnglish(item, items, expanded);
        }
    }

    private static void finishEnglish(ItemStack item, ItemManager items, boolean expanded) {
        DungeonGearProgress.ensure(item, items);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> full = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        full.removeIf(GearTooltip::isHintLine);
        DungeonArmor.applyDungeonStatPreview(full, item, items);
        String itemId = items.getItemId(item);
        full = LoreLayout.normalize(full, itemId, items.getRarity(item));
        store(meta, full);
        List<String> shown = shouldCompact(item, items, full) ? compact(item, items, full) : full;
        meta.setLore(LoreLocale.present(meta, itemId, shown));
        item.setItemMeta(meta);
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        items.refreshPlayerItems(player, false);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(player), 20L);
    }

    private static boolean shouldCompact(ItemStack item, ItemManager items, List<String> full) {
        if (items.getBoosterType(item) != null) {
            return false;
        }
        if (DungeonCore.isCore(item)) {
            return false;
        }
        return DungeonGearProgress.canLevel(item, items)
                || DungeonArmor.isAttuned(item, items)
                || DungeonCore.tier(item) > 0;
    }

    private static List<String> compact(ItemStack item, ItemManager items, List<String> full) {
        List<String> kept = new ArrayList<>();
        for (String line : full) {
            if (line == null || isHintLine(line) || isDungeonStatPreview(line) || isDungeonHudLine(line)) {
                continue;
            }
            kept.add(line.isBlank() ? "" : line);
        }
        kept.add("");
        String itemId = items.getItemId(item);
        boolean dungeonized = !DungeonCore.isCore(item)
                && (DungeonGearProgress.canLevel(item, items)
                || DungeonArmor.isAttuned(item, items)
                || DungeonCore.tier(item) > 0
                || DungeonCore.isAetherionEndgame(itemId));
        if (dungeonized) {
            int level = DungeonGearProgress.level(item);
            int cap = DungeonGearProgress.maxLevel(item);
            int percent = DungeonGearProgress.combinedDungeonPercent(item, items, level);
            int core = DungeonCore.tier(item);
            int rarityPercent = DungeonCore.rarityStatPercent(items.getRarity(item));
            kept.add("§5Dungeon: §f+" + Math.max(0, percent) + "% stats"
                    + (rarityPercent > 0 ? " §8· rarity §f+" + rarityPercent + "%" : ""));
            kept.add(DungeonGearProgress.levelBarLine(level, DungeonGearProgress.xp(item), cap));
            if (core > 0) {
                kept.add("§dInfused: Dungeon Core §f" + DungeonCore.roman(core) + "§8/" + DungeonCore.roman(DungeonCore.MAX_TIER));
            }
            if (DungeonCore.canInfuse(itemId) && (DungeonCore.isAetherionEndgame(itemId) || core == 0)) {
                kept.add(DungeonCore.coreHint(item, items));
            }
        } else if (DungeonCore.canInfuse(itemId)) {
            kept.add(DungeonCore.coreHint(item, items));
        }
        return LoreLayout.normalize(kept, itemId, items.getRarity(item));
    }

    private static List<String> spaced(List<String> lore) {
        List<String> out = new ArrayList<>();
        String lastKind = "";
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            if (line.isBlank()) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) {
                    out.add("");
                }
                lastKind = "blank";
                continue;
            }
            String kind = blockKind(line);
            if (!out.isEmpty() && !lastKind.isBlank() && !lastKind.equals("blank") && !kind.equals(lastKind)) {
                if (!out.get(out.size() - 1).isBlank()) {
                    out.add("");
                }
            }
            out.add(line);
            lastKind = kind;
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static boolean isDungeonStatPreview(String line) {
        return DungeonArmor.isStandaloneDungeonStatPreview(line);
    }

    private static boolean isHintLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return plain.contains("hold shift") || plain.contains("sneak in inventory");
    }

    private static boolean isDungeonHudLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return LoreLayout.isDungeonLine(line)
                || lower.contains("level bonus");
    }

    private static String blockKind(String line) {
        if (isStatLine(line) && !isZeroBooster(line)) {
            return "stat";
        }
        if (isAbilityLine(line)) {
            return "ability";
        }
        if (isJokeLine(line) || isFlavor(line)) {
            return "joke";
        }
        String plain = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        if (plain.startsWith("(+") && plain.endsWith(")")) {
            return "dungeon";
        }
        if (plain.startsWith("dungeon:")
                || plain.startsWith("lv ")
                || plain.startsWith("infused:")
                || plain.startsWith("dungeon core")
                || plain.startsWith("dungeonized")
                || plain.contains("level bonus")
                || plain.contains("rarity bonus")
                || plain.contains("level cap")
                || plain.contains("dungeon-bound")
                || (plain.contains("anvil +") && plain.contains("core"))) {
            return "dungeon";
        }
        if (plain.contains("hold shift") || plain.contains("sneak")) {
            return "hint";
        }
        if (plain.contains("booster") || isZeroBooster(line)) {
            return "booster";
        }
        return "other";
    }

    private static boolean isStatLine(String line) {
        String plain = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return plain.contains("damage:")
                || plain.contains("defense:")
                || plain.contains("health:")
                || plain.contains("spread:")
                || plain.contains("fortune:")
                || plain.contains("mining power:")
                || plain.contains("crit chance:")
                || plain.contains("crit damage:")
                || plain.contains("undead")
                || plain.contains("harvest:")
                || plain.contains("fish speed:")
                || plain.contains("fish catch:")
                || plain.contains("catch rate:")
                || plain.contains("fire rate:")
                || plain.contains("draw:")
                || (plain.contains("speed:") && !plain.contains("glowstone") && !plain.contains("fish"));
    }

    private static boolean isAbilityLine(String line) {
        String plain = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
        return plain.contains("right-click")
                || plain.contains("hold right")
                || plain.startsWith("forward.")
                || plain.contains("no arrows required")
                || plain.contains("warp step")
                || plain.contains("blink");
    }

    private static boolean isZeroBooster(String line) {
        String plain = ChatColor.stripColor(line).toLowerCase(Locale.ROOT).trim();
        if (!(plain.contains("redstone")
                || plain.contains("birch")
                || plain.contains("oak")
                || plain.contains("lapis")
                || plain.contains("emerald")
                || plain.contains("glowstone")
                || plain.contains("wheat")
                || plain.startsWith("coal ")
                || plain.startsWith("iron ")
                || plain.startsWith("gold ")
                || plain.startsWith("diamond ")
                || plain.startsWith("special boosters")
                || plain.startsWith("boosters:")
                || plain.startsWith("total:"))) {
            return false;
        }
        return plain.endsWith(": 0")
                || plain.endsWith(": +0")
                || plain.endsWith(": 0%")
                || plain.endsWith(": +0%")
                || plain.equals("special boosters:")
                || (plain.startsWith("total:") && (plain.endsWith("/0") || plain.contains(" 0/")));
    }

    private static boolean isJokeLine(String line) {
        if (line == null || isAbilityLine(line) || isStatLine(line) || isHintLine(line) || isDungeonStatPreview(line)) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        if (plain.length() < 8 || plain.contains(":")) {
            return false;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        if (lower.startsWith("dungeon")
                || lower.startsWith("infused")
                || lower.startsWith("lv ")
                || lower.contains("level bonus")
                || lower.contains("anvil +")
                || lower.startsWith("dungeonized")) {
            return false;
        }
        String raw = line.trim();
        return raw.startsWith("§7") || raw.startsWith("§5") || raw.startsWith("§d") || raw.startsWith("§6") || raw.startsWith("§8");
    }

    private static boolean isFlavor(String line) {
        if (line == null || !line.trim().startsWith("§7")) {
            return false;
        }
        String plain = ChatColor.stripColor(line).trim();
        return plain.length() >= 12 && !plain.contains(":");
    }

    private static void store(ItemMeta meta, List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            meta.getPersistentDataContainer().remove(ItemKeys.loreCache());
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lore.size(); i++) {
            if (i > 0) {
                builder.append(JOIN);
            }
            builder.append(lore.get(i) == null ? "" : lore.get(i));
        }
        meta.getPersistentDataContainer().set(ItemKeys.loreCache(), PersistentDataType.STRING, builder.toString());
    }
}
