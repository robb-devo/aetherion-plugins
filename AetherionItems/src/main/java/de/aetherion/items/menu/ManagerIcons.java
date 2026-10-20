package de.aetherion.items.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.Map;

/**
 * Custom pack icons for the Aetherion Manager buttons, picked by the button's material.
 *
 * <p>Each icon is a CustomModelData override in that material's item model (the pack's
 * {@code tools/gen_gui_icons.py} writes the art and {@code tools/gui_icons.json}). The values sit
 * above every item CMD in the pack (max 4206), so no real item on these materials changes look.
 * Glass panes get {@link #FILLER}, an empty model, so the Manager chrome under them shows through.
 */
final class ManagerIcons {

    static final int FILLER = 7700;

    private static final Map<Material, Integer> BY_MATERIAL = new EnumMap<>(Material.class);

    static {
        BY_MATERIAL.put(Material.NETHER_STAR, 7701);
        BY_MATERIAL.put(Material.AMETHYST_SHARD, 7702);
        BY_MATERIAL.put(Material.CHEST, 7703);
        BY_MATERIAL.put(Material.NETHERITE_SCRAP, 7704);
        BY_MATERIAL.put(Material.GOLD_BLOCK, 7705);
        BY_MATERIAL.put(Material.BONE, 7706);
        BY_MATERIAL.put(Material.IRON_PICKAXE, 7707);
        BY_MATERIAL.put(Material.LEAD, 7708);
        BY_MATERIAL.put(Material.WRITABLE_BOOK, 7709);
        BY_MATERIAL.put(Material.KNOWLEDGE_BOOK, 7710);
        BY_MATERIAL.put(Material.GRASS_BLOCK, 7711);
        BY_MATERIAL.put(Material.YELLOW_BANNER, 7712);
        BY_MATERIAL.put(Material.EXPERIENCE_BOTTLE, 7713);
        BY_MATERIAL.put(Material.ENDER_CHEST, 7714);
        BY_MATERIAL.put(Material.ARMOR_STAND, 7715);
        BY_MATERIAL.put(Material.COMPASS, 7716);
        BY_MATERIAL.put(Material.CRAFTING_TABLE, 7717);
        BY_MATERIAL.put(Material.ANVIL, 7718);
        BY_MATERIAL.put(Material.COMMAND_BLOCK, 7719);
        BY_MATERIAL.put(Material.BARRIER, 7720);
        BY_MATERIAL.put(Material.GRAY_DYE, 7721);
        BY_MATERIAL.put(Material.LIME_DYE, 7722);
        BY_MATERIAL.put(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 7723);
    }

    private ManagerIcons() {
    }

    /** Puts the button icon for this item's material on it, if there is one. Returns the same stack. */
    static ItemStack apply(ItemStack item) {
        if (item == null) {
            return null;
        }
        Integer cmd = BY_MATERIAL.get(item.getType());
        if (cmd == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Turns a glass pane into an invisible, tooltip-less spacer. */
    static ItemStack filler(Material pane) {
        ItemStack item = new ItemStack(pane);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(FILLER);
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
