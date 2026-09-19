package de.aetherion.items.storage;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class AetherionStorage {

    private final JavaPlugin plugin;

    private final NamespacedKey storageKey;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public AetherionStorage(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        this.storageKey =
                new NamespacedKey(
                        plugin,
                        "aetherion_storage"
                );
    }


    /*
     * =========================================================
     * CREATE STORAGE ITEM
     * =========================================================
     */

    public ItemStack createStorage() {

        ItemStack storage =
                new ItemStack(
                        Material.CHEST
                );


        ItemMeta meta =
                storage.getItemMeta();


        if (meta == null) {
            return storage;
        }


        meta.setDisplayName(
                ChatColor.RED
                        + "Aetherion Storage"
        );


        meta.setLore(
                java.util.List.of(
                        ChatColor.GRAY
                                + "A special storage",
                        ChatColor.GRAY
                                + "for your Aetherion items."
                )
        );


        meta.getPersistentDataContainer()
                .set(
                        storageKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );


        storage.setItemMeta(
                meta
        );


        return storage;
    }


    /*
     * =========================================================
     * CHECK STORAGE
     * =========================================================
     */

    public boolean isStorage(
            ItemStack item
    ) {

        if (item == null) {
            return false;
        }


        if (item.getType() != Material.CHEST) {
            return false;
        }


        ItemMeta meta =
                item.getItemMeta();


        if (meta == null) {
            return false;
        }


        return meta.getPersistentDataContainer()
                .has(
                        storageKey,
                        PersistentDataType.BYTE
                );
    }
}