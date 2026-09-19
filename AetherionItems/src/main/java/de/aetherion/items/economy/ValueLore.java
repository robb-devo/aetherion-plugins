package de.aetherion.items.economy;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ValueLore {

    private ValueLore() {
    }

    public static void apply(ItemStack stack, ItemValueService values) {
        if (stack == null || stack.getType().isAir() || values == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        int index = valueLineIndex(lore);
        if (!values.showsListedValue(stack)) {
            if (index < 0) {
                return;
            }
            lore.remove(index);
            if (index > 0 && index - 1 < lore.size() && lore.get(index - 1).isBlank()) {
                lore.remove(index - 1);
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
            return;
        }
        long unit = values.unitValue(stack);
        if (unit <= 0L) {
            return;
        }
        String line = values.canSell(stack)
                ? "§8Value: §6" + format(unit) + " coins §7each"
                : "§8AH value: §6" + format(unit) + " coins";
        if (index >= 0) {
            if (lore.get(index).contains("Trade value:")) {
                return;
            }
            lore.set(index, line);
        } else {
            if (!lore.isEmpty() && !lore.get(lore.size() - 1).isBlank()) {
                lore.add("");
            }
            lore.add(line);
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    private static int valueLineIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String existing = lore.get(i);
            if (existing.contains("Value:") || existing.contains("AH value:") || existing.contains("Trade value:")) {
                return i;
            }
        }
        return -1;
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }
}
