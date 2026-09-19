package de.aetherion.items.storage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LoadoutGUI {

    public static final String LOADOUT_TITLE =
            "Aetherion Loadouts";


    /*
     * =========================================================
     * LOADOUT LAYOUT
     * =========================================================
     *
     * 54 Slots = Doppelchest
     *
     * 6 Loadouts nebeneinander.
     *
     * Pro Loadout:
     *
     * 0 = Helmet
     * 1 = Chestplate
     * 2 = Leggings
     * 3 = Boots
     * 4 = Equip Button
     */

    private static final int[][] LOADOUT_SLOTS = {

            {
                    10,
                    19,
                    28,
                    37,
                    46
            },

            {
                    11,
                    20,
                    29,
                    38,
                    47
            },

            {
                    12,
                    21,
                    30,
                    39,
                    48
            },

            {
                    14,
                    23,
                    32,
                    41,
                    50
            },

            {
                    15,
                    24,
                    33,
                    42,
                    51
            },

            {
                    16,
                    25,
                    34,
                    43,
                    52
            }
    };

    /** One clear button above each loadout's helmet. */
    private static final int[] CLEAR_SLOTS = {
            1, 2, 3, 5, 6, 7
    };


    /*
     * =========================================================
     * GLASS COLORS
     * =========================================================
     */

    private static final Material[] GLASS_COLORS = {

            Material.BLUE_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE
    };


    private static final Material[] GLASS_BLOCK_COLORS = {

            Material.BLUE_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS
    };


    /*
     * =========================================================
     * OPEN LOADOUT MENU
     * =========================================================
     */

    public void openLoadouts(
            Player player,
            LoadoutManager loadoutManager
    ) {
        openLoadouts(player, loadoutManager, null);
    }

    public void openLoadouts(
            Player player,
            LoadoutManager loadoutManager,
            Integer activeLoadout
    ) {

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        54,
                        LOADOUT_TITLE
                );


        /*
         * =====================================================
         * BACKGROUND
         * =====================================================
         */

        for (
                int slot = 0;
                slot < inventory.getSize();
                slot++
        ) {

            inventory.setItem(
                    slot,
                    createNeutralGlass()
            );
        }


        /*
         * =====================================================
         * LOADOUTS 1 - 6
         * =====================================================
         *
         * Jedes Loadout wird komplett unabhängig aufgebaut.
         */

        // Only advertise first-ever park — later empty slots strip/select, never clone a set.
        boolean captureAvailable = hasWornArmor(player)
                && loadoutManager.canCaptureWornSet(player);

        for (
                int loadout = 1;
                loadout <= LoadoutManager.LOADOUTS_PER_PAGE;
                loadout++
        ) {

            createLoadout(
                    inventory,
                    player,
                    loadoutManager,
                    loadout,
                    activeLoadout != null && activeLoadout == loadout,
                    captureAvailable
            );
        }


        inventory.setItem(
                de.aetherion.items.util.ManagerNav.SLOT,
                createBackButton()
        );


        player.openInventory(
                inventory
        );
    }


    /*
     * =========================================================
     * CREATE LOADOUT
     * =========================================================
     */

    private void createLoadout(
            Inventory inventory,
            Player player,
            LoadoutManager loadoutManager,
            int loadout,
            boolean equipped,
            boolean captureAvailable
    ) {

        /*
         * Sicherheitscheck.
         */

        if (
                loadout < 1
                        || loadout > LOADOUT_SLOTS.length
        ) {

            return;
        }


        int index =
                loadout - 1;


        int[] slots =
                LOADOUT_SLOTS[index];


        Material paneMaterial =
                GLASS_COLORS[index];


        Material buttonMaterial =
                GLASS_BLOCK_COLORS[index];


        /*
         * =====================================================
         * NUR DIESES EINE LOADOUT LADEN
         * =====================================================
         *
         * Hier gibt es keinerlei gemeinsamen Zustand zwischen
         * Set 1, 2, 3, 4, 5 und 6.
         */

        ItemStack[] armor =
                loadoutManager.getLoadout(
                        player,
                        1,
                        loadout
                );

        boolean empty = true;
        if (armor != null) {
            for (ItemStack piece : armor) {
                if (piece != null && !piece.getType().isAir()) {
                    empty = false;
                    break;
                }
            }
        }
        boolean canCapture = empty && captureAvailable;


        /*
         * =====================================================
         * CLEAR (above helmet)
         * =====================================================
         */

        inventory.setItem(
                CLEAR_SLOTS[index],
                createClearButton(loadout, !empty)
        );


        /*
         * =====================================================
         * ARMOR SLOTS
         * =====================================================
         */

        for (
                int armorSlot = 0;
                armorSlot < LoadoutManager.ARMOR_SLOTS;
                armorSlot++
        ) {

            ItemStack item =
                    armor[armorSlot];


            /*
             * Gespeichertes Item anzeigen.
             */

            if (
                    item != null
                            && !item.getType().isAir()
            ) {

                inventory.setItem(
                        slots[armorSlot],
                        equipped ? withEquippedLore(item.clone()) : item.clone()
                );

            } else {

                /*
                 * Leerer Slot bekommt die farbige Glasscheibe
                 * des jeweiligen Loadouts.
                 */

                inventory.setItem(
                        slots[armorSlot],
                        createArmorPlaceholder(
                                paneMaterial,
                                getArmorName(
                                        armorSlot
                                ),
                                loadout,
                                equipped,
                                canCapture
                        )
                );
            }
        }


        /*
         * =====================================================
         * EQUIP BUTTON
         * =====================================================
         *
         * Dieser Button gehört eindeutig zu genau diesem
         * Loadout.
         */

        inventory.setItem(
                slots[4],
                createEquipButton(
                        buttonMaterial,
                        loadout,
                        equipped,
                        canCapture
                )
        );
    }


    /*
     * =========================================================
     * CLEAR BUTTON
     * =========================================================
     */

    private ItemStack createClearButton(int loadout, boolean hasArmor) {
        ItemStack item = new ItemStack(hasArmor ? Material.BARRIER : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (hasArmor) {
            meta.setDisplayName(ChatColor.RED + "Clear Set " + loadout);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Move this set into your inventory.",
                    ChatColor.DARK_GRAY + "Frees the slot so you can save again."
            ));
        } else {
            meta.setDisplayName(ChatColor.DARK_GRAY + "Clear Set " + loadout);
            meta.setLore(List.of(ChatColor.DARK_GRAY + "Nothing stored here."));
        }
        item.setItemMeta(meta);
        return item;
    }


    /*
     * =========================================================
     * ARMOR PLACEHOLDER
     * =========================================================
     */

    private ItemStack createArmorPlaceholder(
            Material material,
            String armorName,
            int loadout,
            boolean equipped,
            boolean canCapture
    ) {

        ItemStack item =
                new ItemStack(
                        material
                );


        ItemMeta meta =
                item.getItemMeta();


        if (
                meta == null
        ) {

            return item;
        }


        meta.setDisplayName(
                ChatColor.GRAY
                        + armorName
        );


        if (equipped) {
            meta.setLore(
                    List.of(
                            ChatColor.GREEN
                                    + "Currently equipped",
                            ChatColor.RED
                                    + "Editing locked"
                    )
            );
        } else {
            meta.setLore(
                    canCapture
                            ? List.of(
                                    ChatColor.YELLOW + "Empty",
                                    ChatColor.GRAY + "Click to save your worn set here",
                                    ChatColor.GRAY + "(first save only — no duplicates)."
                            )
                            : List.of(
                                    ChatColor.DARK_GRAY + "Empty",
                                    ChatColor.GRAY + "Click while armored to go clear.",
                                    ChatColor.GRAY + "Armor stays in your filled set."
                            )
            );
        }


        item.setItemMeta(
                meta
        );


        return item;
    }


    /*
     * =========================================================
     * EQUIP BUTTON
     * =========================================================
     */

    private ItemStack createEquipButton(
            Material material,
            int loadout,
            boolean equipped,
            boolean canCapture
    ) {

        ItemStack item =
                new ItemStack(
                        equipped ? Material.LIME_STAINED_GLASS : material
                );


        ItemMeta meta =
                item.getItemMeta();


        if (
                meta == null
        ) {

            return item;
        }


        if (equipped) {
            meta.setDisplayName(
                    ChatColor.GREEN
                            + "Set "
                            + loadout
                            + " equipped"
            );

            meta.setLore(
                    List.of(
                            ChatColor.GRAY
                                    + "This set is currently worn.",
                            ChatColor.RED
                                    + "Editing is locked.",
                            ChatColor.DARK_GRAY
                                    + "Equip another set to swap."
                    )
            );
        } else {
            meta.setDisplayName(
                    ChatColor.GREEN
                            + (canCapture ? "Save worn set as Set " : "Equip Set ")
                            + loadout
            );

            meta.setLore(
                    canCapture
                            ? List.of(
                                    ChatColor.GRAY + "This loadout is empty.",
                                    ChatColor.YELLOW + "Click to save the armor you are wearing."
                            )
                            : List.of(
                                    ChatColor.GRAY + "This loadout is empty.",
                                    ChatColor.YELLOW + "Click while armored to go clear.",
                                    ChatColor.GRAY + "Armor stays in your filled set."
                            )
            );
        }


        item.setItemMeta(
                meta
        );


        return item;
    }


    private ItemStack withEquippedLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GREEN + "Currently equipped");
        lore.add(ChatColor.RED + "Editing locked");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean hasWornArmor(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack[] worn = player.getInventory().getArmorContents();
        if (worn == null) {
            return false;
        }
        for (ItemStack piece : worn) {
            if (piece != null && !piece.getType().isAir()) {
                return true;
            }
        }
        return false;
    }


    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Back");
            meta.setLore(List.of(ChatColor.GRAY + "Return to the Aetherion Manager."));
            item.setItemMeta(meta);
        }

        return item;
    }


    /*
     * =========================================================
     * NEUTRAL GLASS
     * =========================================================
     */

    private ItemStack createNeutralGlass() {

        ItemStack item =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE
                );


        ItemMeta meta =
                item.getItemMeta();


        if (
                meta == null
        ) {

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
     * ARMOR NAMES
     * =========================================================
     */

    private String getArmorName(
            int slot
    ) {

        switch (slot) {

            case 0:
                return "Helmet";

            case 1:
                return "Chestplate";

            case 2:
                return "Leggings";

            case 3:
                return "Boots";

            default:
                return "Armor";
        }
    }
}