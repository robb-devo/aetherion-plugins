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
                    "§5§lBeta-Journal\n§8Aetherion · 2026\n\n§0Sieben Schritte quer durch die Welt. Jeder hakt sich selbst ab, sobald du ihn machst.\n\n§8Rechtsklick §0öffnet dein Journal.");
            meta.addPage(
                    "§5Belohnungen\n\n§0Bei §83§0, §85 §0und §8allen 7 §0erkannten Schritten wartet eine kleine Coin-Belohnung.\n\n§0Alles erledigt? Dazu gibt's das §5Kurier-Siegel§0 — nur zur Erinnerung.");
            meta.addPage(
                    "§5Sag's ehrlich\n\n§0Vier schnelle Klicks im Feedback helfen uns am meisten.\n\n§0Buch weg?\n§8/checklist\n\n§0Danke fürs Testen.");
        } else {
            meta.addPage(
                    "§5§lBeta Journal\n§8Aetherion · 2026\n\n§0Seven steps across the world. Each one ticks itself off the moment you do it.\n\n§8Right-click §0to open your journal.");
            meta.addPage(
                    "§5Rewards\n\n§0At §83§0, §85 §0and §8all 7 §0tracked steps a small coin reward waits for you.\n\n§0Full clear also earns the §5Courier's Seal§0 — a keepsake, nothing more.");
            meta.addPage(
                    "§5Be honest\n\n§0Four quick picks in the feedback form help us most.\n\n§0Lost this book?\n§8/checklist\n\n§0Thanks for testing.");
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, PDC_VALUE);
        meta.setDisplayName(Texts.t(use, "§d✦ Beta Journal", "§d✦ Beta-Journal"));
        meta.setLore(List.of(
                Texts.t(use, "§7Tracks your beta steps live.", "§7Verfolgt deine Beta-Schritte live."),
                "",
                Texts.t(use, "§e▸ Right-click to open", "§e▸ Rechtsklick zum Öffnen"),
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
