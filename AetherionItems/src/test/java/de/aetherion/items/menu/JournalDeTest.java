package de.aetherion.items.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The German Journal: whole words, longest first, counts kept. */
class JournalDeTest {

    @Test
    void chaptersAndTiles() {
        assertEquals("Journal · Werkstatt", JournalDe.t("Journal · Workshop"));
        assertEquals("Shop", JournalDe.t("Shop"));
        assertEquals("§7In §fCharakter › Skills", JournalDe.t("§7In §fCharacter › Skills"));
        assertEquals("Auktionshaus", JournalDe.t("Auction House"));
        assertEquals("Auktion", JournalDe.t("Auction"));
        assertEquals("Rezepte", JournalDe.t("Recipes"));
    }

    @Test
    void countsAndClicks() {
        assertEquals("Abgeben: 2", JournalDe.t("Turn in 2"));
        assertEquals("Abholen: 3", JournalDe.t("Claim 3"));
        assertEquals("§63 abzuholen", JournalDe.t("§63 rewards to claim"));
        assertEquals("§7Schritt §f2 §7von §f5", JournalDe.t("§7Step §f2 §7of §f5"));
        assertEquals("§eKlicken zum Hinblättern", JournalDe.t("§eClick to turn there"));
        assertEquals("Weg 3/9", JournalDe.t("Path 3/9"));
    }

    @Test
    void leavesOtherWordsAlone() {
        assertEquals("Ring of Fire", JournalDe.t("Ring of Fire"));
        assertEquals("Pathwarden", JournalDe.t("Pathwarden"));
        assertEquals("Workshops", JournalDe.t("Workshops"));
    }
}
