package de.aetherion.beta.item;

import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Texts;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BetaBook {

    public static final String PDC_VALUE = "checklist";

    private final NamespacedKey key;

    public BetaBook(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "beta_book");
    }

    public NamespacedKey key() {
        return key;
    }

    public boolean isBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return PDC_VALUE.equals(value);
    }

    public ItemStack create(BetaLang lang) {
        BetaLang use = lang == null ? BetaLang.EN : lang;
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return book;
        }
        meta.setTitle(Texts.t(use, "Aetherion Beta", "Aetherion Beta"));
        meta.setAuthor(Texts.t(use, "Patch Courier", "Patch-Kurier"));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        if (use == BetaLang.DE) {
            meta.addPage(
                    "§d✦ Aetherion Beta\n\n§0Hey!\n\nDieses Buch ist deine Checkliste.\n\n§8Sie hakt sich automatisch ab,\n§0wenn du Dinge ausprobierst.\n\n§8Rechtsklick §0öffnet das Menü.");
            meta.addPage(
                    "§0Was wir gern von dir wissen:\n\n§8• §0Quests & Intro\n§8• §0Gathering\n§8• §0Pets\n§8• §0Bosse\n§8• §0Dungeon\n§8• §0Inseln\n\nDanach kurze Bewertung mit Glas-Auswahl.");
            meta.addPage(
                    "§0Buch weg?\n§8/checklist §0öffnet alles erneut.\n\nDanke, dass du testest.\nViel Spaß — und sag uns ehrlich, was hakelig war.");
        } else {
            meta.addPage(
                    "§d✦ Aetherion Beta\n\n§0Hey!\n\nThis book is your checklist.\n\n§8It fills itself automatically\n§0when you try things.\n\n§8Right-click §0opens the menu.");
            meta.addPage(
                    "§0What we'd love you to try:\n\n§8• §0Quests & intro\n§8• §0Gathering\n§8• §0Pets\n§8• §0Bosses\n§8• §0Dungeon\n§8• §0Islands\n\nThen a short glass multiple-choice rating.");
            meta.addPage(
                    "§0Lost the book?\n§8/checklist §0opens this again.\n\nThanks for testing.\nHave fun — and be honest about what felt rough.");
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, PDC_VALUE);
        meta.setDisplayName(Texts.t(use, "§d✦ Beta Checklist", "§d✦ Beta-Checkliste"));
        meta.setLore(List.of(
                Texts.t(use, "§7Right-click to open", "§7Rechtsklick zum Öffnen"),
                Texts.t(use, "§8Aetherion closed beta", "§8Aetherion Closed Beta")
        ));
        book.setItemMeta(meta);
        return book;
    }

    public void refreshDisplay(ItemStack item, BetaLang lang) {
        if (!isBook(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BookMeta bookMeta)) {
            return;
        }
        ItemStack fresh = create(lang);
        BookMeta freshMeta = (BookMeta) fresh.getItemMeta();
        if (freshMeta == null) {
            return;
        }
        bookMeta.setTitle(freshMeta.getTitle());
        bookMeta.setAuthor(freshMeta.getAuthor());
        bookMeta.setPages(freshMeta.getPages());
        bookMeta.setDisplayName(freshMeta.getDisplayName());
        bookMeta.setLore(freshMeta.getLore());
        item.setItemMeta(bookMeta);
    }
}
