package de.aetherion.items.util;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class GuiItems {

    private GuiItems() {
    }

    public static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            markGui(meta);
            hideVanilla(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            markGui(meta);
            hideVanilla(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isGui(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.guiDisplay(), PersistentDataType.BYTE);
    }

    private static void markGui(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(ItemKeys.guiDisplay(), PersistentDataType.BYTE, (byte) 1);
    }

    public static void hideVanilla(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            ItemKeys.key("gui_hidden"),
                            0.0,
                            AttributeModifier.Operation.ADD_NUMBER
                    )
            );
        } catch (IllegalArgumentException ignored) {
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        try {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (NoSuchFieldError ignored) {
        }
    }
}
