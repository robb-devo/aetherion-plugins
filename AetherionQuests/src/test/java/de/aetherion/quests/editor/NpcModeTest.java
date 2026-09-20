package de.aetherion.quests.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcModeTest {

    @Test
    void parseDefaultsFromLinkedQuestWhenModeMissing() {
        assertEquals(NpcMode.DIALOG, NpcMode.parse(null, false));
        assertEquals(NpcMode.QUEST, NpcMode.parse("  ", true));
        assertEquals(NpcMode.QUEST, NpcMode.parse(null, true));
    }

    @Test
    void parseKnownAliases() {
        assertEquals(NpcMode.DIALOG, NpcMode.parse("dialog-only", true));
        assertEquals(NpcMode.DIALOG, NpcMode.parse("talk", false));
        assertEquals(NpcMode.QUEST, NpcMode.parse("quest_npc", false));
        assertEquals(NpcMode.QUEST, NpcMode.parse("Quest", false));
    }

    @Test
    void labelsStayShort() {
        assertEquals("Dialog-only", NpcMode.DIALOG.label());
        assertEquals("Quest NPC", NpcMode.QUEST.label());
        assertEquals("dialog", NpcMode.DIALOG.id());
        assertEquals("quest", NpcMode.QUEST.id());
    }

    @Test
    void customNpcDefaultsDialogUntilToggled() {
        CustomNpc npc = new CustomNpc("mod_clerk", "Clerk");
        assertEquals(NpcMode.DIALOG, npc.getMode());
        assertFalse(npc.isQuestNpc());
        npc.setMode(NpcMode.QUEST);
        npc.setLinkedQuestId("dock_errand");
        CustomNpc copy = npc.copy("mod_clerk_2", "Clerk Copy");
        assertTrue(copy.isQuestNpc());
        assertEquals("dock_errand", copy.getLinkedQuestId());
    }
}
