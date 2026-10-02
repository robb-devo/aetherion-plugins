package de.aetherion.items.menu.dev;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.core.ItemKeys;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared DEV button builders. Every tagged stack is remembered in {@link DevIndex} so the
 * dashboard can show favorites / recents with their real icons; decorated clones carry
 * {@link #VIA} so a click resolves back to the pristine item.
 */
final class DevItems {

    /** Set on decorated clones (favorite / recent / search result). Value = where it was shown. */
    static final NamespacedKey VIA = AetherKeys.namespaced("aetherion", "dev_via");

    private DevItems() {
    }

    static ItemStack button(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            de.aetherion.items.util.GuiItems.hideVanilla(meta);
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        DevIndex.remember(action, item);
        return item;
    }

    /** A tagged placeholder that is never cached as the action's icon (offline / cold stubs). */
    static ItemStack stub(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack tag(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        DevIndex.remember(action, item);
        return item;
    }

    static String actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.devAction(), PersistentDataType.STRING);
    }

    static String viaOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(VIA, PersistentDataType.STRING);
    }

    /** Display-only clone with extra lore + the VIA marker. Never handed to the player. */
    static ItemStack decorate(ItemStack icon, String via, List<String> extraLore) {
        ItemStack clone = icon.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (lore.size() > 14) {
            int hidden = lore.size() - 12;
            lore = new ArrayList<>(lore.subList(0, 12));
            lore.add("§8… " + hidden + " more lines");
        }
        lore.addAll(extraLore);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(VIA, PersistentDataType.STRING, via);
        clone.setItemMeta(meta);
        return clone;
    }

    /** Subtle enchant glint for "active" tiles (current category, armed confirm). */
    static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack named(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
