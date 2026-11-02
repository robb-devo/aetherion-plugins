package de.aetherion.core.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromePageTest {

    @Test
    void labelKeepsOnlyPlateLetters() {
        assertEquals("PET DETAILS", ChromePage.label("§5§l✦ PET DETAILS ✦"));
        assertEquals("AETHERLEX · SKY", ChromePage.label("§5§l✦ AETHERLEX · SKY ✦"));
        assertEquals("ZURUECK", ChromePage.label("Zurück"));
        assertEquals("SLOTS", ChromePage.label("§8✦ §6§lSLOTS §8✦"));
    }

    @Test
    void longLabelsKeepTheirLastPartThenWholeWords() {
        assertEquals("OPEN ROADS AND OTHER", ChromePage.fit("MISS LEDGER · OPEN ROADS AND OTHER WAYS OUT OF HERE"));
        assertEquals("A VERY LONG MENU TITLE", ChromePage.fit("A VERY LONG MENU TITLE THAT DOES NOT FIT"));
        assertEquals("GUILD BANK", ChromePage.fit("GUILD BANK"));
    }

    @Test
    void widthMatchesThePixelCaps() {
        assertEquals(23, ChromePage.width("PETS"));
        assertEquals(3, ChromePage.width("I"));
        assertEquals(15, ChromePage.width("A A"));
    }

    @Test
    void titleIsWhiteAndOnlyKitAndSpaceGlyphs() {
        Map<Integer, ChromePage.Mark> marks = new HashMap<>();
        ChromePage.block(marks, 0, 44, ChromePage.Mark.POCKET);
        marks.put(49, ChromePage.Mark.BUTTON);
        String title = ChromePage.title(ChromePage.Theme.TRADE, 6, "FENCE", marks);
        assertTrue(title.startsWith("§f"));
        for (char c : title.substring(2).toCharArray()) {
            assertTrue(c >= 0xE100 && c <= 0xE5FF || c >= 0xE980 && c <= 0xE998, Integer.toHexString(c));
        }
    }

    /** Writes sample titles for tools/gen_gui_kit.py's renderer when -Dchrome.samples=<file> is set. */
    @Test
    void samples() throws IOException {
        String out = System.getProperty("chrome.samples");
        if (out == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        Map<Integer, ChromePage.Mark> details = new HashMap<>();
        details.put(13, ChromePage.Mark.SEAL);
        details.put(29, ChromePage.Mark.SEAL);
        details.put(33, ChromePage.Mark.SEAL);
        details.put(45, ChromePage.Mark.BUTTON);
        lines.add("pet_details 6 " + escape(ChromePage.title(ChromePage.Theme.PETS, 6, "§5§l✦ PET DETAILS ✦", details)));
        Map<Integer, ChromePage.Mark> lex = new HashMap<>();
        ChromePage.block(lex, 0, 44, ChromePage.Mark.POCKET);
        lex.remove(44);
        ChromePage.block(lex, 45, 53, ChromePage.Mark.BUTTON);
        lines.add("aetherlex 6 " + escape(ChromePage.title(ChromePage.Theme.PETS, 6, "AETHERLEX · NETHER", lex)));
        Map<Integer, ChromePage.Mark> bank = new HashMap<>();
        bank.put(11, ChromePage.Mark.SEAL);
        bank.put(13, ChromePage.Mark.SEAL);
        bank.put(15, ChromePage.Mark.SEAL);
        bank.put(22, ChromePage.Mark.BUTTON);
        lines.add("guild_bank 3 " + escape(ChromePage.title(ChromePage.Theme.GUILD, 3, "Guild Bank", bank)));
        lines.add("long 1 " + escape(ChromePage.title(ChromePage.Theme.QUEST, 1,
                "A VERY LONG MENU TITLE THAT DOES NOT FIT", Map.of(4, ChromePage.Mark.SEAL))));
        Files.write(Path.of(out), lines, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append(String.format("\\u%04x", (int) c));
        }
        return out.toString();
    }
}
