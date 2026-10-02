package de.aetherion.items.menu.dev;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search catalog + pristine icon cache for the DEV menu.
 *
 * <p>The catalog is harvested by rendering every shelf (see {@link DevMenu#harvestIndex}), so
 * anything a page can show is findable — no second registry to keep in sync. Icons are cached
 * undecorated per action: favorites / recents / search results render a decorated clone, and a
 * click on a decorated clone gives the pristine item, never the one with the extra lore.
 */
final class DevIndex {

    record Entry(String action, ItemStack icon, String name, String path, String haystack) {
        boolean page() {
            return action.startsWith("page:");
        }
    }

    private static final long TTL_MILLIS = 10 * 60 * 1000L;
    private static final Map<String, ItemStack> ICONS = new ConcurrentHashMap<>();
    private static volatile List<Entry> catalog = List.of();
    private static volatile long builtAt;

    private DevIndex() {
    }

    /** Navigation / picker / per-target actions: never indexed, pinned or recorded. */
    static boolean isChrome(String action) {
        if (action == null || action.isBlank()) {
            return true;
        }
        return action.equals("close") || action.equals("back") || action.equals("root") || action.equals("noop")
                || action.equals("search") || action.equals("give-page") || action.equals("recents:clear")
                || action.equals("lab-info") || action.equals("lab-well") || action.equals("rank-sync")
                || action.equals("confirm-yes") || action.equals("confirm-no")
                || action.startsWith("pageidx:") || action.startsWith("searchidx:")
                || action.startsWith("wipe-") || action.startsWith("rank-player:") || action.startsWith("rank-set:")
                || action.startsWith("shard-player:") || action.startsWith("shard-add:") || action.startsWith("lab-apply:")
                || action.startsWith("testbot:count:") || action.startsWith("testbot:view:");
    }

    /** Pinnable to the dashboard: anything real that is not a danger-gated action. */
    static boolean isPinnable(String action) {
        return !isChrome(action) && !action.startsWith("confirm:") && !action.startsWith("testbot:");
    }

    /** Gives and teleports land in ⟲ Recent; page hops don't (they would drown the row). */
    static boolean isRecentable(String action) {
        if (action == null) {
            return false;
        }
        return action.startsWith("item:") || action.startsWith("set:") || action.startsWith("give:")
                || action.startsWith("testgear:") || action.startsWith("ambient:") || action.startsWith("boss:")
                || action.startsWith("npc:") || action.startsWith("sphere:") || action.startsWith("pet:")
                || action.startsWith("farmtool:") || action.startsWith("loadout:") || action.equals("test:goto")
                || action.startsWith("farmisle:tp:") || action.startsWith("fishisle:tp:")
                || action.startsWith("mineisle:tp:")
                || action.startsWith("test:spawn:");
    }

    static void remember(String action, ItemStack item) {
        if (item == null || isChrome(action) || DevItems.viaOf(item) != null) {
            return;
        }
        ICONS.put(action, item.clone());
    }

    static void rememberIfAbsent(String action, ItemStack item) {
        if (item == null || isChrome(action) || DevItems.viaOf(item) != null) {
            return;
        }
        ICONS.putIfAbsent(action, item.clone());
    }

    static ItemStack icon(String action) {
        ItemStack icon = action == null ? null : ICONS.get(action);
        return icon == null ? null : icon.clone();
    }

    static int size() {
        return catalog.size();
    }

    static long ageSeconds() {
        return builtAt == 0L ? -1L : (System.currentTimeMillis() - builtAt) / 1000L;
    }

    static void invalidate() {
        builtAt = 0L;
        catalog = List.of();
    }

    static List<Entry> catalog(DevMenu menu, Player player) {
        List<Entry> current = catalog;
        if (current.isEmpty() || System.currentTimeMillis() - builtAt > TTL_MILLIS) {
            current = List.copyOf(menu.harvestIndex(player));
            catalog = current;
            builtAt = System.currentTimeMillis();
        }
        return current;
    }

    private static volatile long lastWarm;

    /**
     * Warm the icon cache if a pinned / recent action has never been drawn this run. Throttled:
     * a pin whose plugin is offline must not trigger a full re-harvest on every dashboard open.
     */
    static void ensureIcons(DevMenu menu, Player player, List<String> actions) {
        for (String action : actions) {
            if (!ICONS.containsKey(action)) {
                if (System.currentTimeMillis() - lastWarm < 60_000L) {
                    return;
                }
                lastWarm = System.currentTimeMillis();
                builtAt = 0L;
                catalog(menu, player);
                return;
            }
        }
    }

    static Entry entry(String action, ItemStack icon, String path) {
        String name = plainName(icon);
        StringBuilder hay = new StringBuilder(name.toLowerCase(Locale.ROOT)).append(' ')
                .append(action.toLowerCase(Locale.ROOT).replace('_', ' ').replace(':', ' ')).append(' ')
                .append(path.toLowerCase(Locale.ROOT));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                hay.append(' ').append(ChatColor.stripColor(line).toLowerCase(Locale.ROOT));
            }
        }
        return new Entry(action, icon.clone(), name, path, hay.toString());
    }

    /** Every token must hit; name hits outrank id hits outrank path / lore hits. */
    static List<Entry> search(DevMenu menu, Player player, String query) {
        String[] tokens = query == null ? new String[0] : query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Scored> hits = new ArrayList<>();
        for (Entry entry : catalog(menu, player)) {
            int score = 0;
            boolean all = true;
            String name = entry.name().toLowerCase(Locale.ROOT);
            String id = entry.action().toLowerCase(Locale.ROOT).replace('_', ' ');
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                if (name.startsWith(token) || name.contains(" " + token)) {
                    score += 14;
                } else if (name.contains(token)) {
                    score += 10;
                } else if (id.contains(token)) {
                    score += 5;
                } else if (entry.haystack().contains(token)) {
                    score += 1;
                } else {
                    all = false;
                    break;
                }
            }
            if (all && score > 0) {
                hits.add(new Scored(entry, score + (entry.page() ? 2 : 0)));
            }
        }
        hits.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparingInt(s -> s.entry().name().length()));
        List<Entry> out = new ArrayList<>(hits.size());
        hits.forEach(hit -> out.add(hit.entry()));
        return out;
    }

    static String plainName(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName()).trim();
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private record Scored(Entry entry, int score) {
    }
}
