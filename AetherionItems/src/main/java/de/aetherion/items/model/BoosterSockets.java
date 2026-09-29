package de.aetherion.items.model;

import de.aetherion.items.core.BoosterLimits;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Fourteen booster sockets stored on the item. Stats apply only while a booster is socketed.
 * Older items that only have booster counts are read as filled sockets.
 */
public final class BoosterSockets {

    public static final int COUNT = BoosterLimits.MAX_TOTAL;

    private BoosterSockets() {
    }

    public static BoosterType[] read(ItemManager items, ItemStack item) {
        BoosterType[] slots = empty();
        if (item == null || items == null || !item.hasItemMeta()) {
            return slots;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.boosterSockets(),
                PersistentDataType.STRING
        );
        if (raw != null && !raw.isBlank()) {
            return decode(raw);
        }
        return fromCounts(items.getItemStats(item));
    }

    public static void write(ItemStack item, BoosterType[] slots) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                ItemKeys.boosterSockets(),
                PersistentDataType.STRING,
                encode(slots)
        );
        item.setItemMeta(meta);
    }

    /** Copy sockets onto a crafted or upgraded result. Counts/stats are merged separately. */
    public static void copyOnto(ItemManager items, ItemStack from, ItemStack to) {
        if (items == null || from == null || to == null || !items.isAetherionItem(from)) {
            return;
        }
        write(to, read(items, from));
    }

    public static int firstEmpty(BoosterType[] slots) {
        if (slots == null) {
            return -1;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public static int filled(BoosterType[] slots) {
        if (slots == null) {
            return 0;
        }
        int count = 0;
        for (BoosterType slot : slots) {
            if (slot != null) {
                count++;
            }
        }
        return count;
    }

    public static String encode(BoosterType[] slots) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) {
                builder.append(',');
            }
            BoosterType type = slots != null && i < slots.length ? slots[i] : null;
            builder.append(type == null ? "-" : type.name());
        }
        return builder.toString();
    }

    public static BoosterType[] decode(String raw) {
        BoosterType[] slots = empty();
        if (raw == null || raw.isBlank()) {
            return slots;
        }
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < COUNT && i < parts.length; i++) {
            String token = parts[i] == null ? "" : parts[i].trim();
            if (token.isEmpty() || token.equals("-") || token.equalsIgnoreCase("empty")) {
                continue;
            }
            try {
                slots[i] = BoosterType.valueOf(token.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                slots[i] = null;
            }
        }
        return slots;
    }

    public static BoosterType[] fromCounts(ItemStats stats) {
        BoosterType[] slots = empty();
        if (stats == null) {
            return slots;
        }
        int index = 0;
        for (BoosterType type : BoosterType.values()) {
            int count = stats.boosterCount(type);
            for (int n = 0; n < count && index < COUNT; n++) {
                slots[index++] = type;
            }
        }
        return slots;
    }

    public static BoosterType[] empty() {
        return new BoosterType[COUNT];
    }
}
