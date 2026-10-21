package de.aetherion.items.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Custom pack icons for the Aetherion Manager buttons, picked by the button's material.
 *
 * <p>Each icon is a CustomModelData override in that material's item model (the pack's
 * {@code tools/gen_gui_icons.py} writes the art and {@code tools/gui_icons.json}). The values sit
 * above every item CMD in the pack (max 4206), so no real item on these materials changes look.
 * Fillers are {@link de.aetherion.items.util.GuiIcons#spacer()}.
 *
 * <p>A lockable tab that is not open yet keeps its own picture: grey with a padlock (locked, on grey
 * dye), in colour with a green arrow (opens next, on lime dye) or as a quiet silhouette (later, on a
 * light-grey pane).
 */
final class ManagerIcons {

    /** Lockable tabs by their open material, in the pack's TABS order: CMD = variant base + index. */
    private static final List<Material> TABS = List.of(
            Material.CHEST, Material.NETHERITE_SCRAP, Material.GOLD_BLOCK, Material.BONE, Material.IRON_PICKAXE,
            Material.LEAD, Material.WRITABLE_BOOK, Material.KNOWLEDGE_BOOK, Material.GRASS_BLOCK,
            Material.YELLOW_BANNER, Material.COMPASS, Material.CRAFTING_TABLE, Material.ANVIL);
    private static final int LOCKED = 7740;
    private static final int NEXT = 7760;
    private static final int LATER = 7780;

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

    /** {@code item} (grey dye) shows tab {@code tab} locked. */
    static ItemStack locked(ItemStack item, Material tab) {
        return variant(item, tab, LOCKED);
    }

    /** {@code item} (lime dye) shows tab {@code tab} as the next one to open. */
    static ItemStack next(ItemStack item, Material tab) {
        return variant(item, tab, NEXT);
    }

    /** {@code item} (light-grey pane) shows tab {@code tab} as far off. */
    static ItemStack later(ItemStack item, Material tab) {
        return variant(item, tab, LATER);
    }

    private static ItemStack variant(ItemStack item, Material tab, int base) {
        int index = TABS.indexOf(tab);
        if (item == null || index < 0) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(base + index);
            item.setItemMeta(meta);
        }
        return item;
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
}
