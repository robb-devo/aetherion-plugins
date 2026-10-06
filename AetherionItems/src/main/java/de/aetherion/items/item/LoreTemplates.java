package de.aetherion.items.item;

import de.aetherion.items.AetherionItems;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Factory lore as the single source of truth. On refresh an owned item's lore is rebuilt from what
 * its factory writes today (stats, boosters and progress come from the item's own data), so items
 * that were shredded by an older layout heal on the next refresh.
 */
public final class LoreTemplates {

    private static final ThreadLocal<Boolean> CAPTURE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<String, List<String>> CACHE = new HashMap<>();
    private static final List<String> MISSING = List.of();

    private LoreTemplates() {
    }

    /** True while a factory runs only to capture its authored lore (presentation skips the layout pass). */
    public static boolean capturing() {
        return CAPTURE.get();
    }

    public static void clear() {
        CACHE.clear();
    }

    /** Authored factory lore for {@code itemId}, or null when no factory makes it. */
    public static List<String> authored(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String key = itemId.toLowerCase(java.util.Locale.ROOT);
        List<String> cached = CACHE.get(key);
        if (cached != null) {
            return cached == MISSING ? null : new ArrayList<>(cached);
        }
        List<String> lore = capture(key);
        CACHE.put(key, lore == null ? MISSING : List.copyOf(lore));
        return lore == null ? null : new ArrayList<>(lore);
    }

    private static List<String> capture(String itemId) {
        AetherionItems plugin = AetherionItems.getInstance();
        CustomItem factory = plugin == null ? null : plugin.getCustomItem();
        if (factory == null) {
            return null;
        }
        boolean previous = CAPTURE.get();
        CAPTURE.set(Boolean.TRUE);
        try (LoreLocale.Scope ignored = LoreLocale.english()) {
            ItemStack made = factory.createById(itemId);
            if (made == null || !made.hasItemMeta()) {
                return null;
            }
            ItemMeta meta = made.getItemMeta();
            List<String> lore = meta == null ? null : meta.getLore();
            return lore == null || lore.isEmpty() ? null : new ArrayList<>(lore);
        } catch (Throwable ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "No lore template for " + itemId, ex);
            }
            return null;
        } finally {
            CAPTURE.set(previous);
        }
    }

    /**
     * Template lore plus the per-item state lines the template cannot know (tool levels, value).
     * Stats, boosters, cores and dungeon levels are rewritten from item data afterwards.
     */
    public static List<String> rebuild(String itemId, List<String> current) {
        List<String> template = authored(itemId);
        if (template == null) {
            return current;
        }
        List<String> state = new ArrayList<>();
        boolean hasValue = false;
        boolean hasLevel = false;
        if (current != null) {
            for (String line : current) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (LoreLayout.isValueLine(line)) {
                    hasValue = true;
                    state.add(line);
                } else if (LoreLayout.isLevelLine(line)) {
                    hasLevel = true;
                    state.add(line);
                } else if (LoreLayout.isMarkLine(line)) {
                    state.add(line);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String line : template) {
            if (line != null && ((hasLevel && LoreLayout.isLevelLine(line)) || (hasValue && LoreLayout.isValueLine(line)))) {
                continue;
            }
            out.add(line);
        }
        if (!state.isEmpty()) {
            out.add("");
            out.addAll(state);
        }
        return out;
    }
}
