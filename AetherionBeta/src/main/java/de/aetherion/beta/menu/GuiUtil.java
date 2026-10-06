package de.aetherion.beta.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class GuiUtil {

    private GuiUtil() {
    }

    public static ItemStack named(Material material, String name, String... lore) {
        return named(material, name, Arrays.asList(lore));
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler() {
        return named(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack accent() {
        return named(Material.PURPLE_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack ring() {
        return named(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void fill(org.bukkit.inventory.Inventory inventory) {
        ItemStack pane = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane.clone());
            }
        }
    }

    /**
     * Aetherion panel frame: purple beta bar on top, dark ring around, calm grey body
     * (same read as the Manager / Dev shelves).
     */
    public static void frame(org.bukkit.inventory.Inventory inventory) {
        ItemStack bar = accent();
        ItemStack edge = ring();
        ItemStack fill = filler();
        int size = inventory.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inventory.setItem(i, row == 0 ? bar.clone() : border ? edge.clone() : fill.clone());
        }
    }
}
