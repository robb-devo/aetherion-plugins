package de.aetherion.items.util;

import de.aetherion.core.api.BookPage;
import de.aetherion.core.api.JournalText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerChromeTest {

    @AfterEach
    void journalOff() {
        JournalText.setEnabled(false);
    }

    @Test
    void journalOffKeepsTheOldOverlay() {
        JournalText.setEnabled(false);
        for (ManagerChrome.Page page : ManagerChrome.Page.values()) {
            String text = plain(ManagerChrome.title(page));
            assertEquals(2, text.length(), page.name());
            assertEquals('', text.charAt(0), page.name());
        }
    }

    @Test
    void everyLayoutHasOneWordPerRow() {
        for (ManagerChrome.Page page : ManagerChrome.Page.values()) {
            assertTrue(page.rows() >= 1 && page.rows() <= 6, page.name());
        }
    }

    @Test
    void journalOnDrawsKitGlyphsOnly() {
        JournalText.setEnabled(true);
        for (ManagerChrome.Page page : ManagerChrome.Page.values()) {
            String text = plain(ManagerChrome.title(page));
            assertTrue(text.indexOf((char) (0xE450 + page.rows())) >= 0, page.name() + " has its leaf");
            for (char c : text.toCharArray()) {
                assertTrue(c >= 0xE100 && c <= 0xE5FF || c >= 0xE980 && c <= 0xE998,
                        page.name() + " " + Integer.toHexString(c));
            }
        }
    }

    /** Writes every page as JSON for tools/render_title.py when -Djournal.samples=<file> is set. */
    @Test
    void samples() throws IOException {
        String out = System.getProperty("journal.samples");
        if (out == null) {
            return;
        }
        JournalText.setEnabled(true);
        List<String> lines = new ArrayList<>();
        for (ManagerChrome.Page page : ManagerChrome.Page.values()) {
            Component title;
            switch (page) {
                case CODEX -> title = ManagerChrome.title(page, List.of(
                        new BookPage.Caption(18, "Lv 12", BookPage.State.OPEN),
                        new BookPage.Caption(20, "Lv 3", BookPage.State.NEXT),
                        new BookPage.Caption(22, "4.5", BookPage.State.OPEN),
                        new BookPage.Caption(24, "3/9", BookPage.State.OPEN),
                        new BookPage.Caption(26, "Locked", BookPage.State.LOCKED)), null, 0);
                case STATS -> title = ManagerChrome.title(page, List.of(
                        new BookPage.Caption(0, "1,240", BookPage.State.OPEN),
                        new BookPage.Caption(2, "380", BookPage.State.OPEN),
                        new BookPage.Caption(4, "12%", BookPage.State.OPEN),
                        new BookPage.Caption(6, "150%", BookPage.State.OPEN),
                        new BookPage.Caption(8, "+40", BookPage.State.NEXT)));
                case SKILLS -> title = ManagerChrome.title(page, null, null, 29);
                case SHOP -> title = ManagerChrome.title(page, List.of(
                        new BookPage.Caption(4, "1,250", BookPage.State.OPEN),
                        new BookPage.Caption(19, "Boosts", BookPage.State.OPEN),
                        new BookPage.Caption(22, "Materials", BookPage.State.OPEN),
                        new BookPage.Caption(25, "Exclusive", BookPage.State.LOCKED)));
                default -> title = ManagerChrome.title(page);
            }
            lines.add(page.name().toLowerCase() + " " + page.rows() + " "
                    + page.layoutPicture() + "\t" + GsonComponentSerializer.gson().serialize(title));
        }
        Files.write(Path.of(out), lines, StandardCharsets.UTF_8);
    }

    private static String plain(Component component) {
        StringBuilder out = new StringBuilder();
        if (component instanceof TextComponent text) {
            out.append(text.content());
        }
        for (Component child : component.children()) {
            out.append(plain(child));
        }
        return out.toString();
    }
}
