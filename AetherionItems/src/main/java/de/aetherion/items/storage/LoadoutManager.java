package de.aetherion.items.storage;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

public class LoadoutManager {

    /*
     * =========================================================
     * LOADOUT CONFIGURATION
     * =========================================================
     *
     * Aktuell:
     *
     * 1 Seite
     * 6 Loadouts pro Seite
     *
     * Später können weitere Seiten ergänzt werden.
     */

    public static final int LOADOUTS_PER_PAGE = 6;

    public static final int MAX_PAGES = 1;

    public static final int ARMOR_SLOTS = 4;


    /*
     * =========================================================
     * ARMOR SLOT ORDER
     * =========================================================
     *
     * 0 = Helmet
     * 1 = Chestplate
     * 2 = Leggings
     * 3 = Boots
     */

    private final JavaPlugin plugin;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public LoadoutManager(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        createLoadoutFolder();
    }


    /*
     * =========================================================
     * CREATE LOADOUT FOLDER
     * =========================================================
     */

    private void createLoadoutFolder() {

        File folder =
                new File(
                        plugin.getDataFolder(),
                        "loadouts"
                );


        if (
                !folder.exists()
        ) {

            if (
                    !folder.mkdirs()
            ) {

                plugin.getLogger().warning(
                        "Could not create loadout folder."
                );
            }
        }
    }


    /*
     * =========================================================
     * GET LOADOUT
     * =========================================================
     *
     * Die YAML-Datei ist die einzige Wahrheit.
     *
     * Bei jedem Aufruf wird der aktuelle Stand des Spielers
     * direkt aus seiner Datei gelesen.
     *
     * Dadurch kann niemals ein alter Memory-Cache angezeigt
     * werden.
     */

    public ItemStack[] getLoadout(
            Player player,
            int page,
            int loadout
    ) {

        if (
                !isValidPosition(
                        page,
                        loadout
                )
        ) {

            return createEmptyArmor();
        }


        File file =
                getPlayerFile(
                        player
                );


        if (
                !file.exists()
        ) {

            return createEmptyArmor();
        }


        YamlConfiguration config =
                YamlConfiguration
                        .loadConfiguration(
                                file
                        );


        ItemStack[] armor =
                createEmptyArmor();


        /*
         * =====================================================
         * DIESES EINE LOADOUT LADEN
         * =====================================================
         */

        for (
                int slot = 0;
                slot < ARMOR_SLOTS;
                slot++
        ) {

            String path =
                    getPath(
                            page,
                            loadout,
                            slot
                    );


            armor[slot] =
                    cloneOrNull(
                            readItem(
                                    config,
                                    path
                            )
                    );
        }


        return armor;
    }


    /*
     * =========================================================
     * SAVE LOADOUT
     * =========================================================
     *
     * Nur das angegebene Loadout wird verändert.
     *
     * Alle anderen fünf Loadouts bleiben erhalten.
     *
     * Der komplette aktuelle Spielerstand wird anschließend
     * persistent gespeichert.
     */

    public void saveLoadout(
            Player player,
            int page,
            int loadout,
            ItemStack[] armor
    ) {

        if (
                !isValidPosition(
                        page,
                        loadout
                )
        ) {

            return;
        }


        File file =
                getPlayerFile(
                        player
                );


        File parent =
                file.getParentFile();


        if (
                !parent.exists()
        ) {

            if (
                    !parent.mkdirs()
            ) {

                plugin.getLogger().warning(
                        "Could not create loadout directory."
                );

                return;
            }
        }


        /*
         * =====================================================
         * AKTUELLEN SPIELERSTAND LADEN
         * =====================================================
         *
         * Wir laden zuerst die vorhandene Datei.
         *
         * Dadurch werden beim Speichern eines Loadouts die
         * anderen Loadouts NICHT überschrieben.
         */

        YamlConfiguration config;

        if (
                file.exists()
        ) {

            config =
                    YamlConfiguration
                            .loadConfiguration(
                                    file
                            );

        } else {

            config =
                    new YamlConfiguration();
        }

        migrateLegacyStacks(config);


        /*
         * =====================================================
         * DIESES LOADOUT SCHREIBEN
         * =====================================================
         */

        for (
                int slot = 0;
                slot < ARMOR_SLOTS;
                slot++
        ) {

            String path =
                    getPath(
                            page,
                            loadout,
                            slot
                    );


            ItemStack item =
                    null;


            if (
                    armor != null
                            && slot < armor.length
            ) {

                item =
                        armor[slot];
            }


            writeItem(
                    config,
                    path,
                    item
            );
        }


        /*
         * =====================================================
         * PERSISTENT SPEICHERN
         * =====================================================
         */

        saveConfig(
                config,
                file
        );
    }


    /*
     * =========================================================
     * CLEAR LOADOUT
     * =========================================================
     */

    public void clearLoadout(
            Player player,
            int page,
            int loadout
    ) {

        saveLoadout(
                player,
                page,
                loadout,
                null
        );
    }


    /*
     * =========================================================
     * HAS LOADOUT
     * =========================================================
     */

    public boolean hasLoadout(
            Player player,
            int page,
            int loadout
    ) {

        ItemStack[] armor =
                getLoadout(
                        player,
                        page,
                        loadout
                );


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


    public boolean canCaptureWornSet(Player player) {
        File file = getPlayerFile(player);
        if (!file.exists()) {
            return true;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getBoolean("worn-capture-used", false)) {
            return false;
        }
        for (int loadout = 1; loadout <= LOADOUTS_PER_PAGE; loadout++) {
            for (int slot = 0; slot < ARMOR_SLOTS; slot++) {
                ItemStack item = readItem(config, getPath(1, loadout, slot));
                if (item != null && !item.getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }


    public void markWornCaptureUsed(Player player) {
        File file = getPlayerFile(player);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create loadout directory.");
            return;
        }
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        if (config.getBoolean("worn-capture-used", false)) {
            return;
        }
        config.set("worn-capture-used", true);
        saveConfig(config, file);
    }


    public int getActiveLoadout(Player player) {
        File file = getPlayerFile(player);
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int active = config.getInt("active", 0);
        if (active < 1 || active > LOADOUTS_PER_PAGE) {
            return 0;
        }
        return active;
    }


    public void setActiveLoadout(Player player, int loadout) {
        File file = getPlayerFile(player);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create loadout directory.");
            return;
        }
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        if (loadout < 1 || loadout > LOADOUTS_PER_PAGE) {
            config.set("active", null);
        } else {
            config.set("active", loadout);
        }
        saveConfig(config, file);
    }


    /*
     * =========================================================
     * VALID POSITION
     * =========================================================
     */

    private boolean isValidPosition(
            int page,
            int loadout
    ) {

        return page >= 1
                && page <= MAX_PAGES
                && loadout >= 1
                && loadout <= LOADOUTS_PER_PAGE;
    }


    /*
     * =========================================================
     * CREATE EMPTY ARMOR
     * =========================================================
     */

    private ItemStack[] createEmptyArmor() {

        return new ItemStack[
                ARMOR_SLOTS
                ];
    }


    /*
     * =========================================================
     * SAVE CONFIG
     * =========================================================
     *
     * Atomisches Speichern über eine temporäre Datei.
     *
     * So bleibt die vorhandene Datei geschützt, falls beim
     * Schreiben etwas schiefgeht.
     */

    private void saveConfig(
            YamlConfiguration config,
            File file
    ) {

        File parent =
                file.getParentFile();


        File temporaryFile =
                new File(
                        parent,
                        file.getName()
                                + ".tmp"
                );


        try {

            /*
             * Erst temporär schreiben.
             */

            config.save(
                    temporaryFile
            );


            /*
             * Alte Datei entfernen.
             */

            if (
                    file.exists()
            ) {

                if (
                        !file.delete()
                ) {

                    plugin.getLogger().warning(
                            "Could not replace loadout file: "
                                    + file.getName()
                    );

                    return;
                }
            }


            /*
             * Temporäre Datei finalisieren.
             */

            if (
                    !temporaryFile.renameTo(
                            file
                    )
            ) {

                plugin.getLogger().warning(
                        "Could not finalize loadout file: "
                                + file.getName()
                );


                /*
                 * Falls möglich, temporäre Datei nicht
                 * liegen lassen.
                 */

                if (
                        temporaryFile.exists()
                ) {

                    temporaryFile.delete();
                }
            }

        } catch (
                IOException exception
        ) {

            plugin.getLogger().severe(
                    "Could not save loadout file "
                            + file.getName()
                            + ": "
                            + exception.getMessage()
            );


            if (
                    temporaryFile.exists()
            ) {

                temporaryFile.delete();
            }
        }
    }


    /*
     * =========================================================
     * CONFIG PATH
     * =========================================================
     */

    private String getPath(
            int page,
            int loadout,
            int slot
    ) {

        return "pages."
                + page
                + ".loadouts."
                + loadout
                + "."
                + slot;
    }


    private static String bytesPath(String path) {
        return path + "-bytes";
    }


    private static String idPath(String path) {
        return path + "-id";
    }


    private void migrateLegacyStacks(YamlConfiguration config) {
        if (config == null) {
            return;
        }
        for (int page = 1; page <= MAX_PAGES; page++) {
            for (int loadout = 1; loadout <= LOADOUTS_PER_PAGE; loadout++) {
                for (int slot = 0; slot < ARMOR_SLOTS; slot++) {
                    String path = getPath(page, loadout, slot);
                    if (config.getString(bytesPath(path)) != null) {
                        config.set(path, null);
                        continue;
                    }
                    ItemStack legacy = config.getItemStack(path);
                    if (legacy == null || legacy.getType().isAir()) {
                        continue;
                    }
                    writeItem(config, path, legacy);
                }
            }
        }
    }


    private ItemStack readItem(YamlConfiguration config, String path) {
        ItemStack fromBytes = decode(config.getString(bytesPath(path)));
        if (fromBytes != null) {
            return fromBytes;
        }
        return config.getItemStack(path);
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
     * PLAYER FILE
     * =========================================================
     */

    private File getPlayerFile(
            Player player
    ) {

        return new File(
                plugin.getDataFolder(),
                "loadouts"
                        + File.separator
                        + player.getUniqueId()
                        + ".yml"
        );
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