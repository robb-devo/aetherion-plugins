package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.storage.AetherionStorage;
import de.aetherion.items.storage.LoadoutGUI;
import de.aetherion.items.storage.LoadoutManager;
import de.aetherion.items.storage.StorageGUI;
import de.aetherion.items.storage.StorageInventory;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class StorageListener implements Listener {

    private final AetherionStorage aetherionStorage;

    private final StorageGUI storageGUI;

    private final StorageInventory storageInventory;

    private final LoadoutGUI loadoutGUI;

    private final LoadoutManager loadoutManager;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public StorageListener(
            AetherionStorage aetherionStorage,
            StorageInventory storageInventory
    ) {

        this.aetherionStorage =
                aetherionStorage;

        this.storageGUI =
                new StorageGUI();

        this.storageInventory =
                storageInventory;

        this.loadoutGUI =
                new LoadoutGUI();

        this.loadoutManager =
                new LoadoutManager(
                        AetherionItems.getInstance()
                );
    }


    /*
     * =========================================================
     * STORAGE RECHTSKLICK
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onStorageInteract(
            PlayerInteractEvent event
    ) {

        Action action =
                event.getAction();

        if (
                action != Action.RIGHT_CLICK_AIR
                        && action != Action.RIGHT_CLICK_BLOCK
        ) {

            return;
        }


        ItemStack item =
                event.getItem();


        if (
                !aetherionStorage.isStorage(item)
        ) {

            return;
        }


        event.setCancelled(true);

        Player player = event.getPlayer();
        storageInventory.grantHubAccess(player);

        AetherionItems plugin = AetherionItems.getInstance();

        if (plugin.getManager() != null) {
            plugin.getManager().open(player);
        } else {
            storageGUI.openMainMenu(player);
        }
    }


    /*
     * =========================================================
     * STORAGE DARF NICHT PLATZIERT WERDEN
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onStoragePlace(
            BlockPlaceEvent event
    ) {

        ItemStack item =
                event.getItemInHand();


        if (
                !aetherionStorage.isStorage(item)
        ) {

            return;
        }


        event.setCancelled(true);
    }


    /*
     * =========================================================
     * STORAGE DARF NICHT WEGGEWORFEN WERDEN
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onStorageDrop(
            PlayerDropItemEvent event
    ) {

        ItemStack item =
                event.getItemDrop()
                        .getItemStack();


        if (
                !aetherionStorage.isStorage(item)
        ) {

            return;
        }


        event.setCancelled(true);
    }


    /*
     * =========================================================
     * INVENTORY CLICK
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        String title =
                event.getView()
                        .getTitle();


        /*
         * =====================================================
         * STORAGE
         * =====================================================
         */

        if (
                title.startsWith(
                        StorageGUI.STORAGE_TITLE
                )
        ) {

            handleStorageClick(
                    event
            );

            return;
        }


        /*
         * =====================================================
         * MAIN MENU
         * =====================================================
         */

        if (
                !title.equals(
                        StorageGUI.MAIN_MENU_TITLE
                )
        ) {

            return;
        }


        event.setCancelled(true);


        if (
                event.getRawSlot() < 0
                        || event.getRawSlot()
                        >= event.getView()
                        .getTopInventory()
                        .getSize()
        ) {

            return;
        }


        if (
                !(event.getWhoClicked()
                        instanceof Player)
        ) {

            return;
        }


        Player player =
                (Player)
                        event.getWhoClicked();


        switch (event.getRawSlot()) {

            /*
             * =================================================
             * STORAGE
             * =================================================
             */

            case 11:

                openStoragePage(
                        player,
                        1
                );

                break;


            /*
             * =================================================
             * LOADOUTS
             * =================================================
             */

            case 15:

                LoadoutListener loadoutListener =
                        AetherionItems.getInstance().getLoadoutListener();

                if (loadoutListener != null) {
                    loadoutListener.openLoadoutMenu(player);
                } else {
                    loadoutGUI.openLoadouts(
                            player,
                            loadoutManager
                    );
                }

                break;


            /*
             * =================================================
             * CLOSE
             * =================================================
             */

            case 22:

                player.closeInventory();

                break;


            default:

                break;
        }
    }


    /*
     * =========================================================
     * STORAGE CLICK HANDLER
     * =========================================================
     */

    private void handleStorageClick(
            InventoryClickEvent event
    ) {

        /*
         * Klick im Spielerinventar:
         *
         * Normale Interaktion bleibt erlaubt.
         */

        if (
                event.getRawSlot() < 0
                        || event.getRawSlot()
                        >= event.getView()
                        .getTopInventory()
                        .getSize()
        ) {

            return;
        }


        int slot =
                event.getRawSlot();


        /*
         * =====================================================
         * ECHTE STORAGE-SLOTS
         * =====================================================
         *
         * Slots 0-44 sind echter Storage.
         */

        if (
                slot < StorageInventory.UI_START_SLOT
        ) {

            /*
             * Aetherion Storage darf nicht in sich selbst
             * gelegt werden.
             */

            ItemStack cursor =
                    event.getCursor();


            ItemStack current =
                    event.getCurrentItem();


            if (
                    isStorageItem(
                            cursor
                    )
            ) {

                event.setCancelled(true);

                return;
            }


            if (
                    event.isShiftClick()
                            && isStorageItem(
                            current
                    )
            ) {

                event.setCancelled(true);

                return;
            }


            return;
        }


        /*
         * =====================================================
         * UI-BEREICH
         * =====================================================
         */

        event.setCancelled(true);


        if (
                !(event.getWhoClicked()
                        instanceof Player)
        ) {

            return;
        }


        Player player =
                (Player)
                        event.getWhoClicked();


        int currentPage =
                getCurrentPage(
                        event.getView()
                                .getTitle()
                );


        int unlockedPages =
                storageInventory.getUnlockedPages(
                        player
                );


        /*
         * =====================================================
         * BACK TO MANAGER
         * =====================================================
         */

        if (slot == 45) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }


        /*
         * =====================================================
         * PREVIOUS PAGE
         * =====================================================
         */

        if (slot == 46) {

            if (currentPage > 1) {

                saveCurrentPage(
                        player,
                        event.getView()
                                .getTopInventory(),
                        currentPage
                );


                openStoragePage(
                        player,
                        currentPage - 1
                );
            }

            return;
        }


        /*
         * =====================================================
         * BUY MORE SLOTS
         * =====================================================
         */

        if (slot == 51) {

            if (
                    unlockedPages
                            >= StorageInventory.MAX_PAGES
            ) {

                return;
            }


            int nextPage =
                    unlockedPages + 1;


            int cost =
                    storageGUI.getPageCost(
                            nextPage
                    );


            if (cost <= 0) {

                return;
            }


            if (
                    !hasEnoughChests(
                            player,
                            cost
                    )
            ) {

                player.sendMessage(
                        ChatColor.RED
                                + "You need "
                                + cost
                                + " Chests."
                );

                return;
            }


            boolean unlocked =
                    storageInventory.unlockPage(
                            player,
                            nextPage
                    );


            if (!unlocked) {

                return;
            }


            removeChests(
                    player,
                    cost
            );


            player.sendMessage(
                    ChatColor.GREEN
                            + "Storage Page "
                            + nextPage
                            + " unlocked!"
            );


            saveCurrentPage(
                    player,
                    event.getView()
                            .getTopInventory(),
                    currentPage
            );


            openStoragePage(
                    player,
                    nextPage
            );


            return;
        }


        /*
         * =====================================================
         * NEXT PAGE
         * =====================================================
         */

        if (slot == 53) {

            if (
                    currentPage < unlockedPages
            ) {

                saveCurrentPage(
                        player,
                        event.getView()
                                .getTopInventory(),
                        currentPage
                );


                openStoragePage(
                        player,
                        currentPage + 1
                );
            }

            return;
        }
    }


    /*
     * =========================================================
     * OPEN STORAGE PAGE
     * =========================================================
     */

    public void openStorage(Player player) {
        openStoragePage(player, 1);
    }

    private void openStoragePage(
            Player player,
            int page
    ) {

        int unlockedPages =
                storageInventory.getUnlockedPages(
                        player
                );


        if (
                page < 1
                        || page > unlockedPages
                        || page > StorageInventory.MAX_PAGES
        ) {

            return;
        }


        Inventory storageInventoryPage =
                storageInventory.createInventory(
                        player,
                        page
                );


        storageGUI.openStorage(
                player,
                storageInventoryPage,
                page,
                unlockedPages
        );
    }


    /*
     * =========================================================
     * SAVE CURRENT PAGE
     * =========================================================
     */

    private void saveCurrentPage(
            Player player,
            Inventory inventory,
            int page
    ) {

        storageInventory.saveInventory(
                player,
                inventory,
                page
        );
    }


    /*
     * =========================================================
     * GET CURRENT PAGE
     * =========================================================
     */

    private int getCurrentPage(
            String title
    ) {

        String prefix =
                StorageGUI.STORAGE_TITLE
                        + " - Page ";


        if (
                !title.startsWith(prefix)
        ) {

            return 1;
        }


        String pageString =
                title.substring(
                        prefix.length()
                );


        try {

            return Integer.parseInt(
                    pageString
            );

        } catch (
                NumberFormatException exception
        ) {

            return 1;
        }
    }


    /*
     * =========================================================
     * STORAGE ITEM CHECK
     * =========================================================
     */

    private boolean isStorageItem(
            ItemStack item
    ) {

        return item != null
                && !item.getType().isAir()
                && aetherionStorage.isStorage(
                item
        );
    }


    /*
     * =========================================================
     * CHECK CHESTS
     * =========================================================
     */

    private boolean hasEnoughChests(
            Player player,
            int amount
    ) {

        int total =
                0;


        for (
                ItemStack item
                : player.getInventory()
                .getContents()
        ) {

            if (
                    item == null
                            || item.getType()
                            != Material.CHEST
            ) {

                continue;
            }


            total +=
                    item.getAmount();


            if (
                    total >= amount
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * =========================================================
     * REMOVE CHESTS
     * =========================================================
     */

    private void removeChests(
            Player player,
            int amount
    ) {

        int remaining =
                amount;


        ItemStack[] contents =
                player.getInventory()
                        .getContents();


        for (
                int slot = 0;
                slot < contents.length;
                slot++
        ) {

            if (remaining <= 0) {

                break;
            }


            ItemStack item =
                    contents[slot];


            if (
                    item == null
                            || item.getType()
                            != Material.CHEST
            ) {

                continue;
            }


            int remove =
                    Math.min(
                            item.getAmount(),
                            remaining
                    );


            item.setAmount(
                    item.getAmount()
                            - remove
            );


            remaining -=
                    remove;


            if (
                    item.getAmount() <= 0
            ) {

                contents[slot] =
                        null;
            }
        }


        player.getInventory()
                .setContents(
                        contents
                );
    }


    /*
     * =========================================================
     * INVENTORY CLOSE
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onInventoryClose(
            InventoryCloseEvent event
    ) {

        String title =
                event.getView()
                        .getTitle();


        if (
                !title.startsWith(
                        StorageGUI.STORAGE_TITLE
                                + " - Page "
                )
        ) {

            return;
        }


        if (
                !(event.getPlayer()
                        instanceof Player)
        ) {

            return;
        }


        Player player =
                (Player)
                        event.getPlayer();


        int page =
                getCurrentPage(
                        title
                );


        saveCurrentPage(
                player,
                event.getInventory(),
                page
        );
    }


    /*
     * =========================================================
     * INVENTORY DRAG
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        String title =
                event.getView()
                        .getTitle();


        /*
         * =====================================================
         * MAIN MENU
         * =====================================================
         */

        if (
                title.equals(
                        StorageGUI.MAIN_MENU_TITLE
                )
        ) {

            event.setCancelled(true);

            return;
        }


        /*
         * =====================================================
         * STORAGE
         * =====================================================
         */

        if (
                !title.startsWith(
                        StorageGUI.STORAGE_TITLE
                )
        ) {

            return;
        }


        /*
         * Storage-Item darf nicht per Drag
         * in den Storage gelegt werden.
         */

        ItemStack cursor =
                event.getOldCursor();


        if (
                isStorageItem(
                        cursor
                )
        ) {

            event.setCancelled(true);

            return;
        }


        /*
         * Untere Reihe ist reine GUI.
         */

        for (
                int slot : event.getRawSlots()
        ) {

            if (
                    slot >= StorageInventory.UI_START_SLOT
                            && slot < StorageInventory.PAGE_SIZE
            ) {

                event.setCancelled(true);

                return;
            }
        }
    }
}