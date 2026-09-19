package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.storage.LoadoutGUI;
import de.aetherion.items.storage.LoadoutManager;
import de.aetherion.items.util.InventoryDrops;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoadoutListener implements Listener {

    private final LoadoutGUI loadoutGUI;

    private final LoadoutManager loadoutManager;


    /*
     * =========================================================
     * LOADOUT SLOTS
     * =========================================================
     *
     * Pro Loadout:
     *
     * 0 = Helmet
     * 1 = Chestplate
     * 2 = Leggings
     * 3 = Boots
     * 4 = Equip
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

    private static final int[] CLEAR_SLOTS = {
            1, 2, 3, 5, 6, 7
    };


    /*
     * =========================================================
     * EDIT TARGET
     * =========================================================
     *
     * Welches Loadout wird aktuell bearbeitet?
     *
     * Das hat NICHTS damit zu tun, welches Loadout der Spieler
     * gerade trägt.
     */

    private final Map<UUID, Integer> editTarget =
            new HashMap<>();


    /*
     * =========================================================
     * ACTIVE LOADOUT
     * =========================================================
     *
     * Welches gespeicherte Loadout trägt der Spieler gerade?
     *
     * null = kein gespeichertes Loadout aktiv.
     *
     * Dieses Feld wird ausschließlich für den Loadout-Swap
     * verwendet.
     */

    private final Map<UUID, Integer> activeLoadouts =
            new HashMap<>();

    private final Set<UUID> applyingLoadout = new HashSet<>();

    private final Set<UUID> restoreOnRespawn = new HashSet<>();


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public LoadoutListener() {

        this.loadoutGUI =
                new LoadoutGUI();

        this.loadoutManager =
                new LoadoutManager(
                        AetherionItems.getInstance()
                );
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    restoreActiveLoadout(online);
                }
            });
        }
    }

    public void openLoadoutMenu(Player player) {
        loadoutGUI.openLoadouts(
                player,
                loadoutManager,
                activeOf(player)
        );
    }


    /*
     * =========================================================
     * INVENTORY CLICK
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (
                !event.getView()
                        .getTitle()
                        .equals(
                                LoadoutGUI.LOADOUT_TITLE
                        )
        ) {

            return;
        }


        if (
                !(event.getWhoClicked()
                        instanceof Player)
        ) {

            event.setCancelled(true);

            return;
        }


        Player player =
                (Player)
                        event.getWhoClicked();


        int topSize =
                event.getView()
                        .getTopInventory()
                        .getSize();


        int rawSlot =
                event.getRawSlot();


        /*
         * =====================================================
         * SPIELERINVENTAR
         * =====================================================
         */

        if (
                rawSlot >= topSize
        ) {

            handlePlayerInventoryClick(
                    event,
                    player
            );

            return;
        }


        /*
         * =====================================================
         * GUI IMMER BLOCKIEREN
         * =====================================================
         */

        event.setCancelled(true);


        if (rawSlot == de.aetherion.items.util.ManagerNav.SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }


        /*
         * Welches Loadout gehört zu diesem Slot?
         */

        int loadout =
                getLoadoutForSlot(
                        rawSlot
                );


        if (
                loadout == -1
        ) {

            return;
        }

        if (isClearSlot(rawSlot)) {
            clearLoadoutSlot(player, loadout);
            return;
        }

        ItemStack[] savedLoadout = loadoutManager.getLoadout(player, 1, loadout);
        ItemStack[] wornArmor = player.getInventory().getArmorContents();
        // Empty slot while armored:
        // - first ever save (no filled loadouts yet) → park into this slot
        // - otherwise → go naked (armor stays in the previous filled set — never duplicate)
        if (!hasAnyArmor(savedLoadout) && hasAnyArmor(wornArmor)) {
            // First-ever park only — never copy an already-saved set into another slot.
            if (!hasAnyFilledLoadout(player) && loadoutManager.canCaptureWornSet(player)) {
                saveCurrentArmorToLoadout(player, loadout, wornArmor);
                loadoutManager.markWornCaptureUsed(player);
                rememberActive(player, loadout);
                refreshLoadout(player);
                player.sendMessage(
                        ChatColor.GREEN
                                + "Loadout "
                                + loadout
                                + " is now the set you're wearing."
                );
                return;
            }
            stripToEmptyLoadout(player, loadout, wornArmor);
            return;
        }


        /*
         * =====================================================
         * EQUIP BUTTON
         * =====================================================
         *
         * Der Equip-Button wird ausschließlich zum Ausrüsten
         * verwendet.
         *
         * Er verändert NICHT das Edit-Ziel.
         */

        if (
                rawSlot
                        == LOADOUT_SLOTS[loadout - 1][4]
        ) {

            if (isEquippedLoadout(player, loadout)) {
                player.sendMessage(
                        ChatColor.YELLOW
                                + "This loadout is already equipped."
                );
                return;
            }

            equipLoadout(
                    player,
                    loadout
            );

            return;
        }


        /*
         * =====================================================
         * ARMOR SLOT
         * =====================================================
         */

        int armorSlot =
                getArmorSlot(
                        rawSlot,
                        loadout
                );


        if (
                armorSlot < 0
                        || armorSlot >= 4
        ) {

            return;
        }


        if (isEquippedLoadout(player, loadout)) {
            player.sendMessage(
                    ChatColor.RED
                            + "You cannot edit a loadout while it is equipped."
            );
            return;
        }


        /*
         * Dieses Loadout wird jetzt bearbeitet.
         */

        editTarget.put(
                player.getUniqueId(),
                loadout
        );


        handleArmorSlotClick(
                event,
                player,
                loadout,
                armorSlot
        );
    }


    /*
     * =========================================================
     * PLAYER INVENTORY CLICK
     * =========================================================
     *
     * Nur Shift + Linksklick auf Armor speichert die Armor
     * in das aktuell ausgewählte Loadout.
     *
     * Normale Klicks werden komplett blockiert.
     */

    private void handlePlayerInventoryClick(
            InventoryClickEvent event,
            Player player
    ) {

        event.setCancelled(true);


        /*
         * Nur Shift + Linksklick.
         */

        if (
                event.getClick()
                        != ClickType.SHIFT_LEFT
        ) {

            return;
        }


        ItemStack item =
                event.getCurrentItem();


        if (
                item == null
                        || item.getType().isAir()
        ) {

            return;
        }


        /*
         * Nur Armor akzeptieren.
         */

        int armorSlot =
                getArmorSlotForItem(
                        item
                );


        if (
                armorSlot == -1
        ) {

            return;
        }


        /*
         * Aktuelles Edit-Ziel holen.
         */

        int loadout =
                getEditTarget(
                        player
                );

        if (isEquippedLoadout(player, loadout)) {
            player.sendMessage(
                    ChatColor.RED
                            + "You cannot edit a loadout while it is equipped."
            );
            return;
        }


        /*
         * Genau dieses Loadout laden.
         */

        ItemStack[] armor =
                loadoutManager.getLoadout(
                        player,
                        1,
                        loadout
                );


        /*
         * Slot bereits belegt?
         */

        if (
                armor[armorSlot] != null
                        && !armor[armorSlot]
                        .getType()
                        .isAir()
        ) {

            player.sendMessage(
                    ChatColor.RED
                            + "That armor slot is already occupied."
            );

            return;
        }


        /*
         * Item ins Loadout speichern.
         */

        armor[armorSlot] =
                item.clone();


        loadoutManager.saveLoadout(
                player,
                1,
                loadout,
                armor
        );


        /*
         * Original aus dem Spielerinventar entfernen.
         */

        ItemStack clicked =
                event.getCurrentItem();


        if (
                clicked != null
                        && !clicked.getType().isAir()
        ) {

            if (
                    clicked.getAmount() > 1
            ) {

                clicked.setAmount(
                        clicked.getAmount() - 1
                );

            } else {

                event.getClickedInventory()
                        .setItem(
                                event.getSlot(),
                                null
                        );
            }
        }


        /*
         * GUI aktualisieren.
         */

        refreshLoadout(
                player
        );
    }


    /*
     * =========================================================
     * GET EDIT TARGET
     * =========================================================
     */

    private int getEditTarget(
            Player player
    ) {

        Integer target =
                editTarget.get(
                        player.getUniqueId()
                );


        if (
                target == null
                        || target < 1
                        || target > LoadoutManager.LOADOUTS_PER_PAGE
        ) {

            return 1;
        }


        return target;
    }


    /*
     * =========================================================
     * ARMOR SLOT CLICK
     * =========================================================
     *
     * Armor-Slots sind ausschließlich zum Bearbeiten da.
     *
     * Cursor mit Armor:
     * -> speichern
     *
     * Leerer Cursor auf gespeicherter Armor:
     * -> entfernen
     */

    private void handleArmorSlotClick(
            InventoryClickEvent event,
            Player player,
            int loadout,
            int armorSlot
    ) {

        ItemStack cursor =
                event.getCursor();


        ItemStack current =
                event.getCurrentItem();


        /*
         * =====================================================
         * ITEM EINLEGEN
         * =====================================================
         */

        if (
                cursor != null
                        && !cursor.getType().isAir()
        ) {

            /*
             * Nur passende Armor akzeptieren.
             */

            if (
                    !isCorrectArmor(
                            cursor,
                            armorSlot
                    )
            ) {

                player.sendMessage(
                        ChatColor.RED
                                + "Only the correct armor piece "
                                + "can be placed here."
                );

                return;
            }


            /*
             * Bereits gespeichertes Item nicht überschreiben.
             */

            if (
                    current != null
                            && !current.getType().isAir()
                            && !isPlaceholder(
                            current
                    )
            ) {

                player.sendMessage(
                        ChatColor.RED
                                + "That armor slot is already occupied."
                );

                return;
            }


            /*
             * Armor speichern.
             */

            saveArmorPiece(
                    player,
                    loadout,
                    armorSlot,
                    cursor
            );


            /*
             * Cursor leeren.
             */

            event.setCursor(
                    null
            );


            refreshLoadout(
                    player
            );


            return;
        }


        /*
         * =====================================================
         * ITEM ENTFERNEN
         * =====================================================
         */

        if (
                current != null
                        && !current.getType().isAir()
                        && !isPlaceholder(
                        current
                )
        ) {

            ItemStack removed =
                    current.clone();


            /*
             * Erst versuchen, das Item ins Inventar zu legen.
             */

            HashMap<Integer, ItemStack> leftovers =
                    player.getInventory()
                            .addItem(
                                    removed
                            );


            /*
             * Kein Platz -> Loadout unverändert lassen.
             */

            if (
                    !leftovers.isEmpty()
            ) {

                player.sendMessage(
                        ChatColor.RED
                                + "Your inventory is full."
                );

                return;
            }


            /*
             * Jetzt wirklich aus dem Loadout löschen.
             */

            saveArmorPiece(
                    player,
                    loadout,
                    armorSlot,
                    null
            );


            refreshLoadout(
                    player
            );
        }
    }


    /*
     * =========================================================
     * SAVE ARMOR PIECE
     * =========================================================
     */

    private void saveArmorPiece(
            Player player,
            int loadout,
            int armorSlot,
            ItemStack item
    ) {

        ItemStack[] armor =
                loadoutManager.getLoadout(
                        player,
                        1,
                        loadout
                );


        if (
                item == null
        ) {

            armor[armorSlot] =
                    null;

        } else {

            armor[armorSlot] =
                    item.clone();
        }


        loadoutManager.saveLoadout(
                player,
                1,
                loadout,
                armor
        );
    }


    /*
     * =========================================================
     * EQUIP LOADOUT
     * =========================================================
     *
     * DAS ist der eigentliche Loadout-Swap.
     *
     * Wenn bereits ein Loadout aktiv ist:
     *
     *     aktuelle Armor
     *             ↓
     *     zurück in aktives Loadout speichern
     *             ↓
     *     Ziel-Loadout laden
     *             ↓
     *     direkt auf Spieler setzen
     *
     * Es wird beim Loadout -> Loadout Wechsel KEIN Item
     * ins Spielerinventar gelegt.
     */

    private void equipLoadout(
            Player player,
            int loadout
    ) {

        /*
         * =====================================================
         * ZIEL-LOADOUT LADEN
         * =====================================================
         */

        ItemStack[] targetSaved =
                loadoutManager.getLoadout(
                        player,
                        1,
                        loadout
                );


        /*
         * Leeres Loadout?
         */

        if (
                !hasAnyArmor(
                        targetSaved
                )
        ) {

            ItemStack[] worn = player.getInventory().getArmorContents();
            if (tryCaptureEmptyLoadout(player, loadout, targetSaved, worn)) {
                return;
            }

            // Empty + already naked: just select the clear slot.
            // Strip-to-naked is shift-click only (see handleLoadoutClick), so armor never vanishes.
            rememberActive(player, loadout);
            refreshWornStats(player);
            player.sendMessage(ChatColor.GRAY + "Empty loadout selected.");
            refreshLoadout(player);
            return;
        }


        /*
         * =====================================================
         * AKTUELLE ARMOR
         * =====================================================
         */

        ItemStack[] currentArmor =
                player.getInventory()
                        .getArmorContents();


        /*
         * =====================================================
         * AKTIVES LOADOUT
         * =====================================================
         */

        Integer activeLoadout = resolveActiveLoadout(player, currentArmor, targetSaved, loadout);

        if (sameArmorSet(currentArmor, targetSaved)) {
            rememberActive(player, loadout);
            player.sendMessage(
                    ChatColor.YELLOW
                            + "This loadout is already equipped."
            );
            return;
        }

        if (
                activeLoadout != null
                        && activeLoadout >= 1
                        && activeLoadout <= LoadoutManager.LOADOUTS_PER_PAGE
        ) {

            saveCurrentArmorToLoadout(
                    player,
                    activeLoadout,
                    currentArmor
            );

            targetSaved =
                    loadoutManager.getLoadout(
                            player,
                            1,
                            loadout
                    );
        } else {
            List<ItemStack> toDump = uniqueWornArmor(player, currentArmor);
            if (countFreeStorageSlots(player) < toDump.size()) {
                player.sendMessage(
                        ChatColor.RED
                                + "You need more inventory space "
                                + "to equip this loadout."
                );
                return;
            }
            for (ItemStack item : toDump) {
                HashMap<Integer, ItemStack> leftovers =
                        player.getInventory().addItem(item.clone());
                if (!leftovers.isEmpty()) {
                    for (ItemStack leftover : leftovers.values()) {
                        if (leftover != null) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                        }
                    }
                }
            }
        }


        /*
         * =====================================================
         * ZIELARMOR ERSTELLEN
         * =====================================================
         *
         * Loadout:
         *
         * 0 = Helmet
         * 1 = Chestplate
         * 2 = Leggings
         * 3 = Boots
         *
         * Bukkit:
         *
         * 0 = Boots
         * 1 = Leggings
         * 2 = Chestplate
         * 3 = Helmet
         */

        ItemStack[] targetArmor =
                new ItemStack[4];


        targetArmor[0] =
                cloneOrNull(
                        targetSaved[3]
                );


        targetArmor[1] =
                cloneOrNull(
                        targetSaved[2]
                );


        targetArmor[2] =
                cloneOrNull(
                        targetSaved[1]
                );


        targetArmor[3] =
                cloneOrNull(
                        targetSaved[0]
                );


        /*
         * =====================================================
         * DIREKT ANZIEHEN
         * =====================================================
         *
         * Kein addItem().
         *
         * Kein isSimilar().
         *
         * Kein NBT-Vergleich.
         *
         * Kein Entfernen des gespeicherten Loadouts.
         */

        applyingLoadout.add(player.getUniqueId());

        try {
            player.getInventory()
                    .setArmorContents(
                            targetArmor
                    );
        } finally {
            applyingLoadout.remove(player.getUniqueId());
        }
        refreshWornStats(player);


        /*
         * =====================================================
         * NEUES AKTIVES LOADOUT
         * =====================================================
         */

        rememberActive(player, loadout);


        /*
         * =====================================================
         * GUI SCHLIESSEN
         * =====================================================
         */

        player.sendMessage(
                ChatColor.GREEN
                        + "Loadout "
                        + loadout
                        + " equipped!"
        );


        player.closeInventory();
    }


    /*
     * =========================================================
     * SAVE CURRENT ARMOR TO ACTIVE LOADOUT
     * =========================================================
     *
     * Speichert exakt den Zustand, der gerade auf dem Spieler
     * liegt.
     *
     * Dadurch werden Änderungen an der tatsächlichen Armor
     * übernommen:
     *
     * - Haltbarkeit
     * - Enchantments
     * - Booster
     * - NBT
     * - sonstige Item-Daten
     */

    private void captureWornArmor(Player player, int loadout, ItemStack[] wornArmor) {
        if (player == null || wornArmor == null || !hasAnyArmor(wornArmor)) {
            return;
        }
        // Only write into the clicked slot — never clone the set into another loadout.
        saveCurrentArmorToLoadout(player, loadout, wornArmor);
        loadoutManager.markWornCaptureUsed(player);
        rememberActive(player, loadout);
        refreshLoadout(player);
        player.sendMessage(
                ChatColor.GREEN
                        + "Loadout "
                        + loadout
                        + " is now the set you're wearing."
        );
    }

    private boolean tryCaptureEmptyLoadout(
            Player player,
            int loadout,
            ItemStack[] savedLoadout,
            ItemStack[] wornArmor
    ) {
        if (hasAnyArmor(savedLoadout) || !hasAnyArmor(wornArmor)) {
            return false;
        }
        // Same rules as armor-slot click: first save only. Later empty Equip = go naked.
        if (!hasAnyFilledLoadout(player) && loadoutManager.canCaptureWornSet(player)) {
            captureWornArmor(player, loadout, wornArmor);
            return true;
        }
        stripToEmptyLoadout(player, loadout, wornArmor);
        return true;
    }

    private boolean hasAnyFilledLoadout(Player player) {
        if (player == null) {
            return false;
        }
        for (int index = 1; index <= LoadoutManager.LOADOUTS_PER_PAGE; index++) {
            if (hasAnyArmor(loadoutManager.getLoadout(player, 1, index))) {
                return true;
            }
        }
        return false;
    }

    private void stripToEmptyLoadout(Player player, int loadout, ItemStack[] wornArmor) {
        if (player == null || !hasAnyArmor(wornArmor)) {
            rememberActive(player, loadout);
            refreshLoadout(player);
            return;
        }
        Integer previous = activeOf(player);
        if (previous == null || previous < 1 || previous > LoadoutManager.LOADOUTS_PER_PAGE || previous == loadout) {
            List<Integer> matches = matchingLoadouts(player, wornArmor);
            previous = matches.isEmpty() ? null : matches.get(0);
        }
        if (previous == null || previous < 1 || previous > LoadoutManager.LOADOUTS_PER_PAGE || previous == loadout) {
            // No safe previous set — refuse rather than delete gear.
            player.sendMessage(
                    ChatColor.RED
                            + "Save this armor into a loadout first (click an empty slot)."
            );
            return;
        }
        saveCurrentArmorToLoadout(player, previous, wornArmor);
        UUID id = player.getUniqueId();
        applyingLoadout.add(id);
        try {
            player.getInventory().setArmorContents(new ItemStack[4]);
        } finally {
            applyingLoadout.remove(id);
        }
        rememberActive(player, loadout);
        refreshWornStats(player);
        player.sendMessage(ChatColor.GRAY + "Empty loadout. You're clear.");
        refreshLoadout(player);
    }

    private void saveCurrentArmorToLoadout(
            Player player,
            int loadout,
            ItemStack[] currentArmor
    ) {

        ItemStack[] savedArmor =
                new ItemStack[4];


        /*
         * Bukkit:
         *
         * 0 = Boots
         * 1 = Leggings
         * 2 = Chestplate
         * 3 = Helmet
         *
         * Loadout:
         *
         * 0 = Helmet
         * 1 = Chestplate
         * 2 = Leggings
         * 3 = Boots
         */

        savedArmor[0] =
                cloneOrNull(
                        currentArmor[3]
                );


        savedArmor[1] =
                cloneOrNull(
                        currentArmor[2]
                );


        savedArmor[2] =
                cloneOrNull(
                        currentArmor[1]
                );


        savedArmor[3] =
                cloneOrNull(
                        currentArmor[0]
                );


        /*
         * Aktuellen Zustand persistent speichern.
         */

        loadoutManager.saveLoadout(
                player,
                1,
                loadout,
                savedArmor
        );
    }


    /*
     * =========================================================
     * PLAYER QUIT
     * =========================================================
     *
     * Falls ein Spieler während eines aktiven Loadouts seine
     * Armor verändert hat, speichern wir den aktuellen Zustand
     * beim Verlassen.
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(
                AetherionItems.getInstance(),
                () -> restoreActiveLoadout(player)
        );
    }

    /**
     * After a Velocity transfer snapshot is applied: drop stale active/applying
     * state so inventory from the other backend wins over local loadout memory.
     */
    public void resetRuntimeAfterNetworkSync(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        activeLoadouts.remove(id);
        applyingLoadout.remove(id);
        editTarget.remove(id);
        // Keep YAML active slot, but do not re-equip — transferred inventory is source of truth.
    }

    /**
     * Drop in-memory loadout maps/sets for this player after a network yaml import
     * (same fields the old reflection walk cleared).
     */
    public void invalidateCachesAfterNetworkImport(UUID id) {
        if (id == null) {
            return;
        }
        editTarget.remove(id);
        activeLoadouts.remove(id);
        applyingLoadout.remove(id);
        restoreOnRespawn.remove(id);
    }


    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        Player player =
                event.getPlayer();


        Integer activeLoadout =
                activeOf(player);


        if (
                activeLoadout != null
                        && activeLoadout >= 1
                        && activeLoadout <= LoadoutManager.LOADOUTS_PER_PAGE
        ) {

            saveCurrentArmorToLoadout(
                    player,
                    activeLoadout,
                    player.getInventory()
                            .getArmorContents()
            );
            loadoutManager.setActiveLoadout(player, activeLoadout);
        }


        /*
         * Maps sauber halten. Active id stays in the YAML
         * so a rejoin does not treat worn loadout armor as
         * normal inventory gear.
         */

        activeLoadouts.remove(
                player.getUniqueId()
        );

        editTarget.remove(
                player.getUniqueId()
        );
        applyingLoadout.remove(player.getUniqueId());
    }


    /*
     * =========================================================
     * REFRESH LOADOUT
     * =========================================================
     */

    private void refreshLoadout(
            Player player
    ) {

        loadoutGUI.openLoadouts(
                player,
                loadoutManager,
                activeOf(player)
        );
    }


    /*
     * =========================================================
     * INVENTORY DRAG
     * =========================================================
     *
     * Kein Dragging innerhalb des Loadout-Fensters.
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        if (
                !event.getView()
                        .getTitle()
                        .equals(
                                LoadoutGUI.LOADOUT_TITLE
                        )
        ) {

            return;
        }


        event.setCancelled(true);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (applyingLoadout.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        Player player = event.getPlayer();
        Integer activeLoadout = activeOf(player);
        if (activeLoadout == null
                || activeLoadout < 1
                || activeLoadout > LoadoutManager.LOADOUTS_PER_PAGE) {
            return;
        }

        // Locked while a filled loadout is active — restore next tick if they strip/swap pieces.
        Bukkit.getScheduler().runTask(
                AetherionItems.getInstance(),
                () -> enforceActiveLoadoutArmor(player, activeLoadout)
        );
    }


    private void enforceActiveLoadoutArmor(Player player, int activeLoadout) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (applyingLoadout.contains(player.getUniqueId())) {
            return;
        }
        ItemStack[] saved = loadoutManager.getLoadout(player, 1, activeLoadout);
        if (!hasAnyArmor(saved)) {
            // Empty active loadout = naked is allowed.
            return;
        }
        ItemStack[] worn = player.getInventory().getArmorContents();
        if (sameArmorSet(worn, saved)) {
            return;
        }

        // Reclaim pieces that moved to the bag before restoring — otherwise lock = free dupe.
        reclaimUnequippedPieces(player, worn, saved);

        ItemStack[] armor = new ItemStack[4];
        armor[0] = cloneOrNull(saved[3]);
        armor[1] = cloneOrNull(saved[2]);
        armor[2] = cloneOrNull(saved[1]);
        armor[3] = cloneOrNull(saved[0]);
        UUID id = player.getUniqueId();
        applyingLoadout.add(id);
        try {
            player.getInventory().setArmorContents(armor);
        } finally {
            applyingLoadout.remove(id);
        }
        player.sendMessage(
                ChatColor.RED
                        + "That set is locked to a loadout. Switch sets — or pick an empty slot to go naked."
        );
        refreshWornStats(player);
    }

    /**
     * When lock restores YAML armor, remove the matching unequipped copies from inventory/cursor
     * so the player does not keep a bag copy + worn copy.
     */
    private void reclaimUnequippedPieces(Player player, ItemStack[] wornBukkit, ItemStack[] savedLoadout) {
        if (player == null || savedLoadout == null) {
            return;
        }
        // saved: 0 helm, 1 chest, 2 legs, 3 boots — bukkit worn: 0 boots, 1 legs, 2 chest, 3 helm
        ItemStack[] expectedWorn = new ItemStack[] {
                savedLoadout[3],
                savedLoadout[2],
                savedLoadout[1],
                savedLoadout[0]
        };
        for (int i = 0; i < 4; i++) {
            ItemStack expected = expectedWorn[i];
            if (expected == null || expected.getType().isAir()) {
                continue;
            }
            ItemStack current = wornBukkit == null || i >= wornBukkit.length ? null : wornBukkit[i];
            if (samePiece(current, expected)) {
                continue;
            }
            removeOneMatching(player, expected);
        }
    }

    private void removeOneMatching(Player player, ItemStack match) {
        if (player == null || match == null || match.getType().isAir()) {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (samePiece(cursor, match)) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) {
            return;
        }
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!samePiece(stack, match)) {
                continue;
            }
            // Armor slots 36–39 are the body — leave those for setArmorContents.
            if (i >= 36 && i <= 39) {
                continue;
            }
            player.getInventory().setItem(i, null);
            return;
        }
    }


    private void rememberActive(Player player, int loadout) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (loadout < 1 || loadout > LoadoutManager.LOADOUTS_PER_PAGE) {
            activeLoadouts.remove(id);
            loadoutManager.setActiveLoadout(player, 0);
            return;
        }
        activeLoadouts.put(id, loadout);
        loadoutManager.setActiveLoadout(player, loadout);
    }


    private Integer activeOf(Player player) {
        if (player == null) {
            return null;
        }
        return activeLoadouts.get(player.getUniqueId());
    }


    private void restoreActiveLoadout(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int stored = loadoutManager.getActiveLoadout(player);
        if (stored >= 1 && stored <= LoadoutManager.LOADOUTS_PER_PAGE) {
            activeLoadouts.put(player.getUniqueId(), stored);
            return;
        }
        int inferred = inferActiveLoadout(player);
        if (inferred >= 1) {
            rememberActive(player, inferred);
        }
    }


    private Integer resolveActiveLoadout(
            Player player,
            ItemStack[] worn,
            ItemStack[] targetSaved,
            int targetLoadout
    ) {
        Integer active = activeOf(player);
        if (active != null && active >= 1 && active <= LoadoutManager.LOADOUTS_PER_PAGE) {
            return active;
        }
        int inferred = inferActiveLoadout(player);
        if (inferred >= 1) {
            rememberActive(player, inferred);
            return inferred;
        }
        List<Integer> matches = matchingLoadouts(player, worn);
        if (matches.size() > 1) {
            int chosen = targetLoadout >= 1 && matches.contains(targetLoadout)
                    ? targetLoadout
                    : matches.get(0);
            // One physical set → one YAML slot. Saving into every match duplicated gear.
            saveCurrentArmorToLoadout(player, chosen, worn);
            rememberActive(player, chosen);
            return chosen;
        }
        if (targetSaved != null && targetLoadout >= 1 && sameArmorSet(worn, targetSaved)) {
            rememberActive(player, targetLoadout);
            return targetLoadout;
        }
        return null;
    }


    private int inferActiveLoadout(Player player) {
        List<Integer> matches = matchingLoadouts(player, player.getInventory().getArmorContents());
        return matches.size() == 1 ? matches.get(0) : 0;
    }


    private List<Integer> matchingLoadouts(Player player, ItemStack[] worn) {
        List<Integer> matches = new ArrayList<>();
        if (!hasAnyArmor(worn)) {
            return matches;
        }
        for (int loadout = 1; loadout <= LoadoutManager.LOADOUTS_PER_PAGE; loadout++) {
            if (sameArmorSet(worn, loadoutManager.getLoadout(player, 1, loadout))) {
                matches.add(loadout);
            }
        }
        return matches;
    }


    private List<ItemStack> uniqueWornArmor(Player player, ItemStack[] worn) {
        List<ItemStack> unique = new ArrayList<>();
        if (worn == null) {
            return unique;
        }
        for (int i = 0; i < Math.min(4, worn.length); i++) {
            ItemStack piece = worn[i];
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            if (storedInAnyLoadout(player, piece)) {
                continue;
            }
            unique.add(piece);
        }
        return unique;
    }


    private boolean storedInAnyLoadout(Player player, ItemStack piece) {
        if (piece == null || piece.getType().isAir()) {
            return false;
        }
        for (int loadout = 1; loadout <= LoadoutManager.LOADOUTS_PER_PAGE; loadout++) {
            ItemStack[] saved = loadoutManager.getLoadout(player, 1, loadout);
            if (saved == null) {
                continue;
            }
            for (ItemStack stored : saved) {
                if (stored == null || stored.getType().isAir()) {
                    continue;
                }
                if (samePiece(piece, stored)) {
                    return true;
                }
            }
        }
        return false;
    }


    private boolean sameArmorSet(ItemStack[] bukkitWorn, ItemStack[] loadoutArmor) {
        if (bukkitWorn == null || loadoutArmor == null || bukkitWorn.length < 4 || loadoutArmor.length < 4) {
            return false;
        }
        return samePiece(bukkitWorn[3], loadoutArmor[0])
                && samePiece(bukkitWorn[2], loadoutArmor[1])
                && samePiece(bukkitWorn[1], loadoutArmor[2])
                && samePiece(bukkitWorn[0], loadoutArmor[3]);
    }


    private boolean samePiece(ItemStack worn, ItemStack saved) {
        boolean wornEmpty = worn == null || worn.getType().isAir();
        boolean savedEmpty = saved == null || saved.getType().isAir();
        if (wornEmpty || savedEmpty) {
            return wornEmpty && savedEmpty;
        }
        var items = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getItemManager();
        if (items != null) {
            String wornId = items.getItemId(worn);
            String savedId = items.getItemId(saved);
            if (wornId != null || savedId != null) {
                return wornId != null && wornId.equals(savedId);
            }
        }
        return worn.getType() == saved.getType();
    }


    private boolean isEquippedLoadout(Player player, int loadout) {
        Integer active = activeOf(player);
        return active != null && active == loadout;
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] worn = player.getInventory().getArmorContents();
        Integer active = resolveActiveLoadout(player, worn, null, 0);
        if (active != null) {
            saveCurrentArmorToLoadout(player, active, worn);
        }
        if (event.getKeepInventory()) {
            return;
        }
        java.util.List<ItemStack> wornLeft = new java.util.ArrayList<>();
        for (ItemStack piece : worn) {
            if (piece != null && !piece.getType().isAir()) {
                wornLeft.add(piece.clone());
            }
        }
        event.getDrops().removeIf(drop -> {
            for (int index = 0; index < wornLeft.size(); index++) {
                if (samePiece(drop, wornLeft.get(index))) {
                    wornLeft.remove(index);
                    return true;
                }
            }
            return false;
        });
        if (active != null) {
            restoreOnRespawn.add(player.getUniqueId());
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!restoreOnRespawn.remove(player.getUniqueId())) {
            return;
        }
        Integer active = activeOf(player);
        if (active == null) {
            int stored = loadoutManager.getActiveLoadout(player);
            if (stored >= 1 && stored <= LoadoutManager.LOADOUTS_PER_PAGE) {
                active = stored;
            }
        }
        if (active == null) {
            return;
        }
        ItemStack[] saved = loadoutManager.getLoadout(player, 1, active);
        ItemStack[] armor = new ItemStack[4];
        armor[0] = cloneOrNull(saved[3]);
        armor[1] = cloneOrNull(saved[2]);
        armor[2] = cloneOrNull(saved[1]);
        armor[3] = cloneOrNull(saved[0]);
        UUID id = player.getUniqueId();
        applyingLoadout.add(id);
        try {
            player.getInventory().setArmorContents(armor);
        } finally {
            applyingLoadout.remove(id);
        }
        rememberActive(player, active);
        refreshWornStats(player);
    }

    private void refreshWornStats(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || player == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            HealthListener health = plugin.getHealthListener();
            if (health != null) {
                health.refreshHealth(player);
            }
        });
    }


    /*
     * =========================================================
     * GET LOADOUT FOR SLOT
     * =========================================================
     */

    private int getLoadoutForSlot(
            int slot
    ) {

        for (
                int loadoutIndex = 0;
                loadoutIndex < LOADOUT_SLOTS.length;
                loadoutIndex++
        ) {

            int[] slots =
                    LOADOUT_SLOTS[
                            loadoutIndex
                            ];


            for (
                    int guiSlot : slots
            ) {

                if (
                        guiSlot == slot
                ) {

                    return loadoutIndex + 1;
                }
            }

            if (loadoutIndex < CLEAR_SLOTS.length && CLEAR_SLOTS[loadoutIndex] == slot) {
                return loadoutIndex + 1;
            }
        }


        return -1;
    }

    private static boolean isClearSlot(int slot) {
        for (int clear : CLEAR_SLOTS) {
            if (clear == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dump a loadout's armor into the player inventory and free the slot.
     * If that set is currently worn, strip it too (no duplicate dump).
     */
    private void clearLoadoutSlot(Player player, int loadout) {
        if (loadout < 1 || loadout > LoadoutManager.LOADOUTS_PER_PAGE) {
            return;
        }
        ItemStack[] saved = loadoutManager.getLoadout(player, 1, loadout);
        boolean equipped = isEquippedLoadout(player, loadout);
        boolean hadSaved = hasAnyArmor(saved);
        boolean hadWorn = equipped && hasAnyArmor(player.getInventory().getArmorContents());

        if (!hadSaved && !hadWorn) {
            player.sendMessage(ChatColor.GRAY + "That loadout is already empty.");
            return;
        }

        // Prefer worn pieces when equipped — same physical set, avoid cloning into inv twice.
        ItemStack[] toGive = equipped
                ? cloneArmorContents(player.getInventory().getArmorContents())
                : cloneLoadoutArmor(saved);

        UUID id = player.getUniqueId();
        applyingLoadout.add(id);
        try {
            if (equipped) {
                player.getInventory().setArmorContents(new ItemStack[4]);
            }
            loadoutManager.saveLoadout(player, 1, loadout, new ItemStack[LoadoutManager.ARMOR_SLOTS]);
            if (equipped) {
                rememberActive(player, 0);
            }
        } finally {
            applyingLoadout.remove(id);
        }

        for (ItemStack piece : toGive) {
            if (piece != null && !piece.getType().isAir()) {
                InventoryDrops.give(player, piece);
            }
        }

        player.sendMessage(ChatColor.GREEN + "Set " + loadout + " cleared — armor is in your inventory.");
        refreshLoadout(player);
    }

    private static ItemStack[] cloneLoadoutArmor(ItemStack[] saved) {
        ItemStack[] out = new ItemStack[LoadoutManager.ARMOR_SLOTS];
        if (saved == null) {
            return out;
        }
        for (int i = 0; i < Math.min(saved.length, out.length); i++) {
            if (saved[i] != null && !saved[i].getType().isAir()) {
                out[i] = saved[i].clone();
            }
        }
        return out;
    }

    private static ItemStack[] cloneArmorContents(ItemStack[] worn) {
        // Bukkit: boots, legs, chest, helm — give as-is order doesn't matter for InventoryDrops.
        ItemStack[] out = new ItemStack[4];
        if (worn == null) {
            return out;
        }
        for (int i = 0; i < Math.min(worn.length, out.length); i++) {
            if (worn[i] != null && !worn[i].getType().isAir()) {
                out[i] = worn[i].clone();
            }
        }
        return out;
    }


    /*
     * =========================================================
     * GET ARMOR SLOT
     * =========================================================
     */

    private int getArmorSlot(
            int slot,
            int loadout
    ) {

        if (
                loadout < 1
                        || loadout > LOADOUT_SLOTS.length
        ) {

            return -1;
        }


        int[] slots =
                LOADOUT_SLOTS[
                        loadout - 1
                        ];


        for (
                int armorSlot = 0;
                armorSlot < slots.length;
                armorSlot++
        ) {

            if (
                    slots[armorSlot] == slot
            ) {

                return armorSlot;
            }
        }


        return -1;
    }


    /*
     * =========================================================
     * GET ARMOR SLOT FOR ITEM
     * =========================================================
     */

    private int getArmorSlotForItem(
            ItemStack item
    ) {

        if (
                item == null
                        || item.getType().isAir()
        ) {

            return -1;
        }


        Material type =
                item.getType();


        /*
         * Helmet
         */

        if (
                type.name()
                        .endsWith(
                                "_HELMET"
                        )
                        || type == Material.TURTLE_HELMET
        ) {

            return 0;
        }


        /*
         * Chestplate
         */

        if (
                type.name()
                        .endsWith(
                                "_CHESTPLATE"
                        )
        ) {

            return 1;
        }


        /*
         * Leggings
         */

        if (
                type.name()
                        .endsWith(
                                "_LEGGINGS"
                        )
        ) {

            return 2;
        }


        /*
         * Boots
         */

        if (
                type.name()
                        .endsWith(
                                "_BOOTS"
                        )
        ) {

            return 3;
        }


        return -1;
    }


    /*
     * =========================================================
     * CORRECT ARMOR
     * =========================================================
     */

    private boolean isCorrectArmor(
            ItemStack item,
            int armorSlot
    ) {

        return getArmorSlotForItem(
                item
        ) == armorSlot;
    }


    /*
     * =========================================================
     * PLACEHOLDER
     * =========================================================
     */

    private boolean isPlaceholder(
            ItemStack item
    ) {

        if (
                item == null
                        || item.getType().isAir()
        ) {

            return false;
        }


        if (
                !item.getType()
                        .name()
                        .endsWith(
                                "_STAINED_GLASS_PANE"
                        )
        ) {

            return false;
        }


        if (
                !item.hasItemMeta()
        ) {

            return false;
        }


        String name =
                item.getItemMeta()
                        .getDisplayName();


        if (
                name == null
        ) {

            return false;
        }


        return name.equals(" ")
                || name.equals(
                ChatColor.GRAY
                        + "Helmet"
        )
                || name.equals(
                ChatColor.GRAY
                        + "Chestplate"
        )
                || name.equals(
                ChatColor.GRAY
                        + "Leggings"
        )
                || name.equals(
                ChatColor.GRAY
                        + "Boots"
        );
    }


    /*
     * =========================================================
     * HAS ANY ARMOR
     * =========================================================
     */

    private boolean hasAnyArmor(
            ItemStack[] armor
    ) {

        if (
                armor == null
        ) {

            return false;
        }


        for (
                ItemStack item : armor
        ) {

            if (
                    item != null
                            && !item.getType().isAir()
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * =========================================================
     * COUNT ARMOR
     * =========================================================
     */

    private int countArmor(
            ItemStack[] armor
    ) {

        if (
                armor == null
        ) {

            return 0;
        }


        int count =
                0;


        for (
                ItemStack item : armor
        ) {

            if (
                    item != null
                            && !item.getType().isAir()
            ) {

                count++;
            }
        }


        return count;
    }


    /*
     * =========================================================
     * FREE STORAGE SLOTS
     * =========================================================
     */

    private int countFreeStorageSlots(
            Player player
    ) {

        int free =
                0;


        for (
                ItemStack item
                : player.getInventory()
                .getStorageContents()
        ) {

            if (
                    item == null
                            || item.getType().isAir()
            ) {

                free++;
            }
        }


        return free;
    }


    /*
     * =========================================================
     * CLONE OR NULL
     * =========================================================
     */

    private ItemStack cloneOrNull(
            ItemStack item
    ) {

        if (
                item == null
                        || item.getType().isAir()
        ) {

            return null;
        }


        return item.clone();
    }
}