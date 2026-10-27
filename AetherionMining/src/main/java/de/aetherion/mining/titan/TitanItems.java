package de.aetherion.mining.titan;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** What Ossarath's body gives: Titan Bone (ribs, vertebrae), Titan Marrow (the Marrowdeep), Heart Embers (Heartbeat). */
public final class TitanItems {

    public enum Kind {
        BONE("titan_bone", Material.BONE, "§fTitan Bone", "§7Petrified bone of Ossarath, the Isle-Eater.", false),
        MARROW("titan_marrow", Material.MAGMA_CREAM, "§6Titan Marrow", "§7Still warm. Found only in the Marrowdeep.", false),
        EMBER("heart_ember", Material.BLAZE_POWDER, "§cHeart Ember", "§7Shed by the stone heart when it beats.", true);

        final String id;
        final Material base;
        final String name;
        final String lore;
        final boolean glint;

        Kind(String id, Material base, String name, String lore, boolean glint) {
            this.id = id;
            this.base = base;
            this.name = name;
            this.lore = lore;
            this.glint = glint;
        }

        public String display() {
            return name;
        }
    }

    private static NamespacedKey key;

    private TitanItems() {
    }

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "titan_material");
    }

    public static ItemStack create(Kind kind, int amount) {
        ItemStack item = new ItemStack(kind.base, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kind.name);
        meta.setLore(List.of(kind.lore, "§8Heartforge material · Jawcamp"));
        if (kind.glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind.id);
        item.setItemMeta(meta);
        return item;
    }

    public static Kind kindOf(ItemStack item) {
        if (item == null || key == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    public static int count(Player player, Kind kind) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (kindOf(stack) == kind) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static void take(Player player, Kind kind, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (kindOf(contents[i]) != kind) {
                continue;
            }
            int use = Math.min(left, contents[i].getAmount());
            left -= use;
            contents[i].setAmount(contents[i].getAmount() - use);
            if (contents[i].getAmount() <= 0) {
                contents[i] = null;
            }
        }
        inventory.setStorageContents(contents);
    }

    /** Into the inventory, the rest at the feet. */
    public static void give(Player player, Kind kind, int amount) {
        player.getInventory().addItem(create(kind, amount)).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
