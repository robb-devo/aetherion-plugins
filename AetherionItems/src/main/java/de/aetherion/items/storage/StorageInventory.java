package de.aetherion.items.storage;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StorageInventory {

    public static final int MAX_PAGES = 5;

    /*
     * Eine Storage-Seite ist eine komplette Doppelchest.
     *
     * 54 Slots insgesamt:
     *
     * 0 - 44  = echte Storage-Slots
     * 45 - 53 = GUI
     */

    public static final int PAGE_SIZE = 54;

    public static final int STORAGE_SIZE = 45;

    public static final int UI_START_SLOT = 45;


    private final JavaPlugin plugin;

    private final Map<UUID, ItemStack[][]> storageContents =
            new HashMap<>();

    private final Map<UUID, Integer> unlockedPages =
            new HashMap<>();

    private final Map<UUID, Boolean> hubAccess =
            new HashMap<>();


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public StorageInventory(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        createStorageFolder();
    }


    /*
     * =========================================================
     * STORAGE FOLDER
     * =========================================================
     */

    private void createStorageFolder() {

        File storageFolder =
                new File(
                        plugin.getDataFolder(),
                        "storage"
                );


        if (!storageFolder.exists()) {

            storageFolder.mkdirs();
        }
    }


    /*
     * =========================================================
     * CREATE PAGE INVENTORY
     * =========================================================
     */

    public Inventory createInventory(
            Player player,
            int page
    ) {

        UUID uuid =
                player.getUniqueId();


        loadStorage(
                uuid
        );


        if (
                page < 1
                        || page > MAX_PAGES
        ) {

            page = 1;
        }


        int unlocked =
                getUnlockedPages(
                        player
                );


        if (page > unlocked) {

            page = unlocked;
        }


        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        PAGE_SIZE,
                        StorageGUI.STORAGE_TITLE
                                + " - Page "
                                + page
                );


        ItemStack[][] pages =
                storageContents.get(
                        uuid
                );


        if (pages != null) {

            ItemStack[] contents =
                    pages[page - 1];


            if (contents != null) {

                /*
                 * Nur die echten Storage-Slots laden.
                 *
                 * Die letzten 9 Slots gehören der GUI.
                 */

                for (
                        int slot = 0;
                        slot < STORAGE_SIZE;
                        slot++
                ) {

                    ItemStack item =
                            contents[slot];


                    if (item != null) {

                        inventory.setItem(
                                slot,
                                item
                        );
                    }
                }
            }
        }


        return inventory;
    }


    /*
     * =========================================================
     * SAVE PAGE
     * =========================================================
     */

    public void saveInventory(
            Player player,
            Inventory inventory,
            int page
    ) {

        UUID uuid =
                player.getUniqueId();


        if (
                page < 1
                        || page > MAX_PAGES
        ) {

            return;
        }


        loadStorage(
                uuid
        );


        ItemStack[][] pages =
                storageContents.get(
                        uuid
                );


        if (pages == null) {

            pages =
                    createEmptyPages();

            storageContents.put(
                    uuid,
                    pages
            );
        }


        /*
         * Nur Slots 0-44 speichern.
         *
         * Die UI-Slots 45-53 werden niemals Teil
         * des eigentlichen Storage-Inhalts.
         */

        ItemStack[] contents =
                new ItemStack[
                        STORAGE_SIZE
                        ];


        for (
                int slot = 0;
                slot < STORAGE_SIZE;
                slot++
        ) {

            contents[slot] =
                    inventory.getItem(
                            slot
                    );
        }


        pages[page - 1] =
                contents;


        saveStorage(
                uuid
        );
    }


    /*
     * =========================================================
     * UNLOCKED PAGES
     * =========================================================
     */

    public int getUnlockedPages(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();


        loadStorage(
                uuid
        );


        return unlockedPages.getOrDefault(
                uuid,
                1
        );
    }


    public boolean hasHubAccess(Player player) {
        return player != null;
    }


    public void grantHubAccess(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        loadStorage(uuid);

        if (Boolean.TRUE.equals(hubAccess.get(uuid))) {
            return;
        }

        hubAccess.put(uuid, true);
        saveStorage(uuid);
    }


    /*
     * =========================================================
     * UNLOCK PAGE
     * =========================================================
     */

    public boolean unlockPage(
            Player player,
            int page
    ) {

        UUID uuid =
                player.getUniqueId();


        loadStorage(
                uuid
        );


        int current =
                getUnlockedPages(
                        player
                );


        if (
                page < 1
                        || page > MAX_PAGES
        ) {

            return false;
        }


        if (
                page != current + 1
        ) {

            return false;
        }


        unlockedPages.put(
                uuid,
                page
        );


        saveStorage(
                uuid
        );


        return true;
    }


    /*
     * =========================================================
     * CREATE EMPTY PAGES
     * =========================================================
     */

    private ItemStack[][] createEmptyPages() {

        return new ItemStack[
                MAX_PAGES
                ][
                STORAGE_SIZE
                ];
    }


    /*
     * =========================================================
     * LOAD STORAGE
     * =========================================================
     */

    private void loadStorage(
            UUID uuid
    ) {

        if (
                storageContents.containsKey(uuid)
                        && unlockedPages.containsKey(uuid)
                        && hubAccess.containsKey(uuid)
        ) {

            return;
        }


        File file =
                getStorageFile(
                        uuid
                );


        ItemStack[][] pages =
                createEmptyPages();


        int pagesUnlocked = 1;
        boolean access = false;


        if (file.exists()) {

            YamlConfiguration config =
                    YamlConfiguration
                            .loadConfiguration(
                                    file
                            );

            access = config.getBoolean("hub-access", true);

            pagesUnlocked =
                    config.getInt(
                            "unlocked-pages",
                            1
                    );


            if (
                    pagesUnlocked < 1
                            || pagesUnlocked > MAX_PAGES
            ) {

                pagesUnlocked = 1;
            }


            /*
             * =================================================
             * LOAD ALL PAGES
             * =================================================
             *
             * Prefer Base64 NBT bytes (same as Loadouts/Market).
             * Legacy Yaml ItemStack entries still load once, then
             * migrate on the next save.
             */

            boolean needsMigration = false;

            for (
                    int page = 1;
                    page <= MAX_PAGES;
                    page++
            ) {

                for (
                        int slot = 0;
                        slot < STORAGE_SIZE;
                        slot++
                ) {

                    String path = slotPath(page, slot);
                    ItemStack fromBytes = decode(config.getString(bytesPath(path)));
                    if (fromBytes != null) {
                        pages[page - 1][slot] = fromBytes;
                        continue;
                    }

                    ItemStack legacy = config.getItemStack(path);
                    if (legacy == null || legacy.getType().isAir()) {
                        continue;
                    }

                    pages[page - 1][slot] = legacy;
                    needsMigration = true;
                }
            }

            storageContents.put(uuid, pages);
            unlockedPages.put(uuid, pagesUnlocked);
            hubAccess.put(uuid, access);

            if (needsMigration) {
                saveStorage(uuid);
            }

            return;
        }


        storageContents.put(
                uuid,
                pages
        );


        unlockedPages.put(
                uuid,
                pagesUnlocked
        );

        hubAccess.put(
                uuid,
                access
        );
    }


    /*
     * =========================================================
     * SAVE STORAGE
     * =========================================================
     */

    private void saveStorage(
            UUID uuid
    ) {

        ItemStack[][] pages =
                storageContents.get(
                        uuid
                );


        int pagesUnlocked =
                unlockedPages.getOrDefault(
                        uuid,
                        1
                );


        if (pages == null) {

            pages =
                    createEmptyPages();
        }


        File file =
                getStorageFile(
                        uuid
                );


        YamlConfiguration config =
                new YamlConfiguration();


        config.set(
                "unlocked-pages",
                pagesUnlocked
        );

        config.set(
                "hub-access",
                hubAccess.getOrDefault(uuid, false)
        );


        /*
         * =====================================================
         * SAVE ALL STORAGE SLOTS (Base64 NBT bytes)
         * =====================================================
         */

        for (
                int page = 1;
                page <= MAX_PAGES;
                page++
        ) {

            for (
                    int slot = 0;
                    slot < STORAGE_SIZE;
                    slot++
            ) {

                writeItem(
                        config,
                        slotPath(page, slot),
                        pages[page - 1][slot]
                );
            }
        }


        /*
         * =====================================================
         * TEMPORARY FILE
         * =====================================================
         */

        File temporaryFile =
                new File(
                        file.getParentFile(),
                        file.getName()
                                + ".tmp"
                );


        try {
            // Never clobber a filled storage with an empty in-memory snapshot.
            if (file.exists() && file.length() > 64 && !hasStoredPages(config)) {
                plugin.getLogger().warning(
                        "Refusing to overwrite storage for " + uuid
                                + " with an empty snapshot (" + file.length() + " bytes on disk)."
                );
                return;
            }

            config.save(
                    temporaryFile
            );


            /*
             * Alte Datei entfernen und temporäre Datei
             * an ihre Stelle setzen.
             */

            if (
                    file.exists()
                            && !file.delete()
            ) {

                plugin.getLogger().warning(
                        "Could not replace storage file for "
                                + uuid
                );

                return;
            }


            if (
                    !temporaryFile.renameTo(file)
            ) {

                plugin.getLogger().warning(
                        "Could not finalize storage file for "
                                + uuid
                );
            }

        } catch (IOException exception) {

            plugin.getLogger().severe(
                    "Could not save storage for "
                            + uuid
                            + ": "
                            + exception.getMessage()
            );
        }
    }


    /*
     * =========================================================
     * STORAGE FILE
     * =========================================================
     */

    private File getStorageFile(
            UUID uuid
    ) {

        return new File(
                plugin.getDataFolder(),
                "storage"
                        + File.separator
                        + uuid
                        + ".yml"
        );
    }


    /*
     * =========================================================
     * ITEM SERIALIZATION (Base64 NBT — same as Loadouts)
     * =========================================================
     */

    private static String slotPath(int page, int slot) {
        return "pages." + page + "." + slot;
    }


    private static String bytesPath(String path) {
        return path + "-bytes";
    }


    private static String idPath(String path) {
        return path + "-id";
    }


    private static boolean hasStoredPages(YamlConfiguration config) {
        if (config == null) {
            return false;
        }
        if (config.contains("pages")) {
            return true;
        }
        for (String key : config.getKeys(true)) {
            if (key.startsWith("pages.") && (key.endsWith("-bytes") || key.endsWith("-id"))) {
                return true;
            }
        }
        return false;
    }


    private void writeItem(YamlConfiguration config, String path, ItemStack item) {
        config.set(path, null);
        config.set(bytesPath(path), null);
        config.set(idPath(path), null);
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemStack copy = item.clone();
        config.set(bytesPath(path), encode(copy));
        String itemId = itemIdOf(copy);
        if (itemId != null) {
            config.set(idPath(path), itemId);
        }
    }


    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }


    private static ItemStack decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException ignored) {
            return null;
        }
    }


    private static String itemIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.item(),
                PersistentDataType.STRING
        );
    }


    /*
     * =========================================================
     * CLEAR STORAGE
     * =========================================================
     */

    public void clearStorage(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();


        storageContents.remove(
                uuid
        );


        unlockedPages.remove(
                uuid
        );

        hubAccess.remove(
                uuid
        );


        File file =
                getStorageFile(
                        uuid
                );


        if (file.exists()) {

            if (!file.delete()) {

                plugin.getLogger().warning(
                        "Could not delete storage file for "
                                + uuid
                );
            }
        }
    }


    /*
     * =========================================================
     * SAVE ALL
     * =========================================================
     */

    public void saveAll() {

        for (
                UUID uuid : storageContents.keySet()
        ) {

            saveStorage(
                    uuid
            );
        }
    }
}