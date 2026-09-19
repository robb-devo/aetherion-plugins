package de.aetherion.beta.menu;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.beta.item.BetaBook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class LanguageMenu {

    public static final int SLOT_EN = 11;
    public static final int SLOT_DE = 15;

    private LanguageMenu() {
    }

    public static void open(AetherionBeta plugin, Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, Texts.langTitle());
        GuiUtil.frame(inventory);
        inventory.setItem(4, GuiUtil.named(
                Material.COMPASS,
                "§d✦ Language / Sprache",
                "§7Pick how the checklist speaks.",
                "§7Wähle die Sprache der Checkliste."
        ));
        inventory.setItem(SLOT_EN, GuiUtil.named(
                Material.WHITE_BANNER,
                "§fEnglish",
                "§7Checklist + book pages in English.",
                "§8Click to select"
        ));
        inventory.setItem(SLOT_DE, GuiUtil.named(
                Material.BLACK_BANNER,
                "§fDeutsch",
                "§7Checkliste + Buchseiten auf Deutsch.",
                "§8Klicken zum Auswählen"
        ));
        player.openInventory(inventory);
    }

    public static void handle(AetherionBeta plugin, Player player, int slot) {
        BetaLang lang;
        if (slot == SLOT_EN) {
            lang = BetaLang.EN;
        } else if (slot == SLOT_DE) {
            lang = BetaLang.DE;
        } else {
            return;
        }
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        data.setLang(lang);
        data.setName(player.getName());
        refreshBooks(plugin, player, lang);
        plugin.npcs().despawnFor(player);
        plugin.npcs().ensureFor(player);
        player.sendMessage(lang == BetaLang.DE
                ? "§aSprache: Deutsch"
                : "§aLanguage: English");
        ChecklistMenu.open(plugin, player);
    }

    private static void refreshBooks(AetherionBeta plugin, Player player, BetaLang lang) {
        BetaBook book = plugin.book();
        for (ItemStack item : player.getInventory().getContents()) {
            if (book.isBook(item)) {
                book.refreshDisplay(item, lang);
            }
        }
        if (book.isBook(player.getInventory().getItemInOffHand())) {
            book.refreshDisplay(player.getInventory().getItemInOffHand(), lang);
        }
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
