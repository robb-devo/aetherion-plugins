package de.aetherion.quests.editor.gui;

import de.aetherion.quests.lang.LangPack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class EditorItems {

    private EditorItems() {
    }

    public static void fill(Inventory inventory) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    /** Double-chest chrome: gray fill, dark header/footer. */
    public static void chrome(Inventory inventory) {
        fill(inventory);
        if (inventory.getSize() < 54) {
            return;
        }
        ItemStack dark = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, dark.clone());
            inventory.setItem(45 + i, dark.clone());
        }
    }

    public static ItemStack section(String name, String... lore) {
        return button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§l" + name, lore);
    }

    public static ItemStack saved(boolean dirty) {
        if (dirty) {
            return button(
                    Material.ORANGE_CONCRETE,
                    "§eUnsaved",
                    "§7Type in chat, then it saves.",
                    "§8or §fcancel"
            );
        }
        return button(
                Material.LIME_CONCRETE,
                "§aSaved",
                "§7editor-npcs.yml",
                "§7editor-quests.yml"
        );
    }

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    if (line != null) {
                        lines.add(line);
                    }
                }
                if (!lines.isEmpty()) {
                    meta.setLore(lines);
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack head(String owner, String name, String... lore) {
        ItemStack item = button(Material.PLAYER_HEAD, name, lore);
        if (item.getItemMeta() instanceof SkullMeta meta && owner != null && !owner.isBlank()) {
            try {
                meta.setOwner(owner);
            } catch (IllegalArgumentException ignored) {
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String title(Player player, String key, String english) {
        return LangPack.ui(player, key, english);
    }
}
