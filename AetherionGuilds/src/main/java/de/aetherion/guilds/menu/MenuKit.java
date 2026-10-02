package de.aetherion.guilds.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shared bits for the island-highlight menus: one holder type, one click route, the usual item helpers. */
public final class MenuKit {

    private MenuKit() {
    }

    /** A menu that handles its own clicks. */
    public interface Menu {
        void click(Player player, Holder holder, int slot, ClickType click);
    }

    public static final class Holder implements InventoryHolder {
        private final Menu menu;
        private final Object context;
        private Inventory inventory;

        public Holder(Menu menu, Object context) {
            this.menu = menu;
            this.context = context;
        }

        public Menu menu() {
            return menu;
        }

        public Object context() {
            return context;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static Inventory create(Menu menu, Object context, int size, String title) {
        Holder holder = new Holder(menu, context);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, pane.clone());
        }
        return inventory;
    }

    /**
     * The hub look: a dark frame on the top and bottom rows, open space in between (only real choices get a
     * slot). Same holder/click route as {@link #create}.
     */
    public static Inventory framed(Menu menu, Object context, int size, String title) {
        Holder holder = new Holder(menu, context);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        ItemStack frame = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 9 && slot < size; slot++) {
            inventory.setItem(slot, frame.clone());
            inventory.setItem(size - 9 + slot, frame.clone());
        }
        return inventory;
    }

    /** A hub tile: glows (and gets a "next" marker) when it is the obvious next thing to do. */
    public static ItemStack tile(Material material, String name, List<String> lore, boolean next) {
        List<String> lines = new ArrayList<>(lore);
        ItemStack item = named(material, next ? name + " §6◆" : name, lines);
        return next ? glow(item) : item;
    }

    public static ItemStack named(Material material, String name, String... lore) {
        return named(material, name, List.of(lore));
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(new ArrayList<>(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack decorate(ItemStack base, String name, List<String> lore) {
        ItemStack item = base == null ? new ItemStack(Material.PAPER) : base.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
