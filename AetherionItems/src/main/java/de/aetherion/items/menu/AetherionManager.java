package de.aetherion.items.menu;

import de.aetherion.items.codex.BestiaryGUI;
import de.aetherion.items.codex.CollectionGUI;
import de.aetherion.items.codex.DungeonJournalGUI;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.recipe.GUI.RecipeBookGUI;
import de.aetherion.items.storage.StorageInventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public class AetherionManager {

    private final AetherionManagerGUI gui;
    private final StatsOverviewGUI statsGUI;
    private final RecipeBookGUI recipeBookGUI;
    private final StorageInventory storageInventory;
    private final BestiaryGUI bestiaryGUI;
    private final CollectionGUI collectionGUI;
    private final DungeonJournalGUI journalGUI;

    private Consumer<Player> petMenuOpener;

    public AetherionManager(
            ItemManager itemManager,
            RecipeBookGUI recipeBookGUI,
            StorageInventory storageInventory,
            BestiaryGUI bestiaryGUI,
            CollectionGUI collectionGUI,
            DungeonJournalGUI journalGUI
    ) {
        this.recipeBookGUI = recipeBookGUI;
        this.storageInventory = storageInventory;
        this.bestiaryGUI = bestiaryGUI;
        this.collectionGUI = collectionGUI;
        this.journalGUI = journalGUI;
        this.gui = new AetherionManagerGUI(this, storageInventory);
        this.statsGUI = new StatsOverviewGUI(itemManager);
    }

    public void open(Player player) {
        if (player != null && tryOpenDungeonMap(player)) {
            return;
        }
        gui.open(player);
        noteManagerOpened(player);
    }

    private static void noteManagerOpened(Player player) {
        de.aetherion.items.util.QuestProgressHook.noteUsed(player, "AETHERION_MANAGER");
    }

    private boolean tryOpenDungeonMap(Player player) {
        if (player.getWorld() == null || !player.getWorld().getName().startsWith("aedun_")) {
            return false;
        }
        var plugin = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            plugin.getClass().getMethod("openDungeonMap", Player.class).invoke(plugin, player);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public AetherionManagerGUI getGui() {
        return gui;
    }

    public StatsOverviewGUI getStatsGUI() {
        return statsGUI;
    }

    public RecipeBookGUI getRecipeBookGUI() {
        return recipeBookGUI;
    }

    public StorageInventory getStorageInventory() {
        return storageInventory;
    }

    public BestiaryGUI getBestiaryGUI() {
        return bestiaryGUI;
    }

    public CollectionGUI getCollectionGUI() {
        return collectionGUI;
    }

    public DungeonJournalGUI getJournalGUI() {
        return journalGUI;
    }

    public void setPetMenuOpener(Consumer<Player> petMenuOpener) {
        this.petMenuOpener = petMenuOpener;
    }

    public boolean hasPetMenu() {
        return petMenuOpener != null || Bukkit.getPluginManager().isPluginEnabled("AetherMobs");
    }

    public void openPets(Player player) {
        if (player == null) {
            return;
        }
        notePetsOpened(player);
        if (petMenuOpener != null) {
            petMenuOpener.accept(player);
            return;
        }
        if (openPetsFromPlugin(player)) {
            return;
        }
        player.performCommand("pets");
    }

    private static void notePetsOpened(Player player) {
        de.aetherion.items.util.QuestProgressHook.noteUsed(player, "AETHERION_PET_MENU");
    }

    private static boolean openPetsFromPlugin(Player player) {
        var plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Object menu = plugin.getClass().getMethod("getPetMenu").invoke(plugin);
            if (menu == null) {
                return false;
            }
            menu.getClass().getMethod("open", Player.class).invoke(menu, player);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
