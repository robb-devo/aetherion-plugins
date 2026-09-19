package de.aetherion.items.storage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class StorageGUI {

    public static final String MAIN_MENU_TITLE =
            ChatColor.DARK_RED + "Aetherion";

    public static final String STORAGE_TITLE =
            "Aetherion Storage";


    /*
     * =========================================================
     * MAIN MENU
     * =========================================================
     */

    public void openMainMenu(
            Player player
    ) {

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        27,
                        MAIN_MENU_TITLE
                );


        inventory.setItem(
                11,
                createButton(
                        Material.CHEST,
                        ChatColor.GOLD + "Storage",
                        ChatColor.GRAY
                                + "Open your Aetherion Storage."
                )
        );


        inventory.setItem(
                15,
                createButton(
                        Material.ARMOR_STAND,
                        ChatColor.AQUA + "Loadouts",
                        ChatColor.GRAY
                                + "Manage your gear loadouts."
                )
        );


        inventory.setItem(
                22,
                createButton(
                        Material.BARRIER,
                        ChatColor.RED + "Close",
                        ChatColor.GRAY
                                + "Close this menu."
                )
        );


        player.openInventory(
                inventory
        );
    }


    /*
     * =========================================================
     * STORAGE
     * =========================================================
     *
     * 54 Slots = echte Doppelchest
     *
     * Slots 0-44:
     * echte Storage-Slots
     *
     * Slots 45-53:
     * UI / Navigation
     */

    public void openStorage(
            Player player,
            Inventory storageInventory,
            int page,
            int unlockedPages
    ) {

        if (
                page < 1
                        || page > StorageInventory.MAX_PAGES
        ) {

            page = 1;
        }


        if (
                page > unlockedPages
        ) {

            page = unlockedPages;
        }


        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        StorageInventory.PAGE_SIZE,
                        getStorageTitle(
                                page
                        )
                );


        /*
         * =====================================================
         * STORAGE ITEMS
         * =====================================================
         */

        for (
                int slot = 0;
                slot < StorageInventory.STORAGE_SIZE;
                slot++
        ) {

            ItemStack item =
                    storageInventory.getItem(
                            slot
                    );


            if (item != null) {

                inventory.setItem(
                        slot,
                        item
                );
            }
        }


        /*
         * =====================================================
         * LOWER UI ROW
         * =====================================================
         */

        for (
                int slot = StorageInventory.UI_START_SLOT;
                slot < StorageInventory.PAGE_SIZE;
                slot++
        ) {

            inventory.setItem(
                    slot,
                    createGlassPane()
            );
        }


        /*
         * =====================================================
         * BACK TO MANAGER
         * =====================================================
         */

        inventory.setItem(
                45,
                createButton(
                        Material.ARROW,
                        ChatColor.YELLOW + "Back",
                        ChatColor.GRAY + "Return to the Aetherion Manager."
                )
        );


        /*
         * =====================================================
         * PREVIOUS PAGE
         * =====================================================
         */

        if (page > 1) {

            inventory.setItem(
                    46,
                    createButton(
                            Material.ARROW,
                            ChatColor.YELLOW
                                    + "Previous Page",
                            ChatColor.GRAY
                                    + "Go to page "
                                    + (page - 1)
                                    + "."
                    )
            );

        } else {

            inventory.setItem(
                    46,
                    createButton(
                            Material.ARROW,
                            ChatColor.DARK_GRAY
                                    + "Previous Page",
                            ChatColor.GRAY
                                    + "You are on page 1."
                    )
            );
        }


        /*
         * =====================================================
         * PAGE INDICATOR
         * =====================================================
         */

        inventory.setItem(
                49,
                createButton(
                        Material.PAPER,
                        ChatColor.GOLD
                                + "Storage Page "
                                + page
                                + " / "
                                + StorageInventory.MAX_PAGES,
                        ChatColor.GRAY
                                + "Unlocked pages: "
                                + unlockedPages
                                + " / "
                                + StorageInventory.MAX_PAGES
                )
        );


        /*
         * =====================================================
         * NEXT PAGE / BUY
         * =====================================================
         */

        if (
                page < unlockedPages
        ) {

            inventory.setItem(
                    53,
                    createButton(
                            Material.ARROW,
                            ChatColor.YELLOW
                                    + "Next Page",
                            ChatColor.GRAY
                                    + "Go to page "
                                    + (page + 1)
                                    + "."
                    )
            );

        } else if (
                unlockedPages
                        < StorageInventory.MAX_PAGES
        ) {

            int nextPage =
                    unlockedPages + 1;

            int cost =
                    getPageCost(
                            nextPage
                    );


            inventory.setItem(
                    51,
                    createButton(
                            Material.CHEST,
                            ChatColor.GREEN
                                    + "Buy More Slots",
                            ChatColor.GRAY
                                    + "Unlock Storage Page "
                                    + nextPage
                                    + ".",
                            ChatColor.YELLOW
                                    + "Cost: "
                                    + cost
                                    + " Chests"
                    )
            );


            inventory.setItem(
                    53,
                    createButton(
                            Material.CHEST,
                            ChatColor.RED
                                    + "Page "
                                    + nextPage
                                    + " Locked",
                            ChatColor.GRAY
                                    + "Purchase the next page",
                            ChatColor.GRAY
                                    + "to unlock it."
                    )
            );

        } else {

            inventory.setItem(
                    51,
                    createButton(
                            Material.NETHER_STAR,
                            ChatColor.GREEN
                                    + "Maximum Storage",
                            ChatColor.GRAY
                                    + "All storage pages",
                            ChatColor.GRAY
                                    + "are unlocked."
                    )
            );


            inventory.setItem(
                    53,
                    createButton(
                            Material.ARROW,
                            ChatColor.DARK_GRAY
                                    + "No Next Page",
                            ChatColor.GRAY
                                    + "Maximum page reached."
                    )
            );
        }


        player.openInventory(
                inventory
        );
    }


    /*
     * =========================================================
     * STORAGE TITLE
     * =========================================================
     */

    public String getStorageTitle(
            int page
    ) {

        return STORAGE_TITLE
                + " - Page "
                + page;
    }


    /*
     * =========================================================
     * PAGE COST
     * =========================================================
     */

    public int getPageCost(
            int page
    ) {

        switch (page) {

            case 2:
                return 16;

            case 3:
                return 32;

            case 4:
                return 48;

            case 5:
                return 64;

            default:
                return 0;
        }
    }


    /*
     * =========================================================
     * GLASS PANE
     * =========================================================
     */

    private ItemStack createGlassPane() {

        ItemStack item =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE
                );


        ItemMeta meta =
                item.getItemMeta();


        if (meta == null) {

            return item;
        }


        meta.setDisplayName(
                " "
        );


        item.setItemMeta(
                meta
        );


        return item;
    }


    /*
     * =========================================================
     * BUTTON
     * =========================================================
     */

    private ItemStack createButton(
            Material material,
            String name,
            String... lore
    ) {

        ItemStack item =
                new ItemStack(
                        material
                );


        ItemMeta meta =
                item.getItemMeta();


        if (meta == null) {

            return item;
        }


        meta.setDisplayName(
                name
        );


        meta.setLore(
                List.of(
                        lore
                )
        );


        item.setItemMeta(
                meta
        );


        return item;
    }
}