package de.aetherion.core.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JournalTextTest {

    @AfterEach
    void off() {
        JournalText.setEnabled(false);
    }

    @Test
    void offLeavesTextAlone() {
        String text = "Nether Star → Skills";
        assertSame(text, JournalText.t(text));
    }

    @Test
    void managerPathsBecomeJournalPaths() {
        JournalText.setEnabled(true);
        assertEquals("Open Journal (slot 9) › Character › Skills will glow",
                JournalText.t("Open Nether Star (slot 9) → Skills will blink"));
        assertEquals("Kit on · Journal (slot 9) › Atlas › Teleports → Capital · or /capital",
                JournalText.t("Kit on · Nether Star (slot 9) → Teleports → Capital · or /capital"));
        assertEquals("Journal › Workshop › Recipes. That's recipes — not the crafting table.",
                JournalText.t("Nether Star → Manager → green book. That's recipes — not the crafting table."));
        assertEquals("§eAnvils run through the §fAether Journal §e › Workshop › Anvil.",
                JournalText.t("§eAnvils run through the §fAetherion Manager §e→ Anvil."));
        assertEquals("Journal, slot nine › Company › Island. Claim it there.",
                JournalText.t("Nether Star, slot nine → Island tab. Claim it there."));
    }

    @Test
    void germanTakesTheNeuter() {
        JournalText.setEnabled(true);
        assertEquals("Das Buch in Slot 9 ist keine Deko. Das ist dein Aether Journal.",
                JournalText.t("Der Nether Star ist kein Schmuck. Das ist der Aetherion Manager."));
        assertEquals("Alles andere steckt im §eJournal§f, Slot 9. §eTeleports§f → Capital.",
                JournalText.t("Alles andere steckt im §eNether Star§f, Slot 9: dein Manager. §eTeleports§f → Capital."));
        assertEquals("Hört, hört! Das Journal wohnt in Hotbar-Slot neun!",
                JournalText.t("Hört, hört! Der Manager wohnt in Hotbar-Slot neun!"));
        assertEquals("§7zu deinen §fTeleports §7hinzugefügt §8(Journal)§7.",
                JournalText.t("§7zu deinen §fTeleports §7hinzugefügt §8(Netherstern)§7."));
    }

    @Test
    void colourCodesDoNotHideTheManager() {
        JournalText.setEnabled(true);
        assertEquals("Now equip it: §eJournal › Character › Pets§f — it glows. Pick your catch.",
                JournalText.t("Now equip it: §eManager → Pets§f — it blinks. Pick your catch."));
        assertEquals("That book in slot 9 isn't decoration. It's your Aether Journal.",
                JournalText.t("That Nether Star isn't jewelry. It's the Aetherion Manager."));
        assertEquals("Use /atrecipes for crafting and /journal for the Journal.",
                JournalText.t("Use /atrecipes for crafting and /aetherion for the Manager."));
    }

    @Test
    void rewriteIsStableAndLeavesOtherTextAlone() {
        JournalText.setEnabled(true);
        String once = JournalText.t("Then Manager → §eAnvil§f. Gear left, booster right.");
        assertEquals("Then Journal › Workshop › §eAnvil§f. Gear left, booster right.", once);
        assertEquals(once, JournalText.t(once));
        String other = "The DialogManagers blink at night.";
        assertEquals(other, JournalText.t(other));
    }
}
