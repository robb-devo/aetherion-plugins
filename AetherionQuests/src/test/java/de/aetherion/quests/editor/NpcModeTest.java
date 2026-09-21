package de.aetherion.quests.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcModeTest {

    @Test
    void missingModeFollowsLinkedQuest() {
        assertEquals(NpcMode.DIALOG, NpcMode.parse(null, false));
        assertEquals(NpcMode.QUEST, NpcMode.parse("", true));
    }

    @Test
    void aliasesMapCleanly() {
        assertEquals(NpcMode.QUEST, NpcMode.parse("quest_npc", false));
        assertEquals(NpcMode.DIALOG, NpcMode.parse("talk-only", true));
        assertEquals(NpcMode.DIALOG, NpcMode.parse("dialogue", false));
    }

    @Test
    void labelsArePlayerFacing() {
        assertEquals("Just talks", NpcMode.DIALOG.label());
        assertEquals("Gives a quest", NpcMode.QUEST.label());
    }
}

class DialogueActionTest {

    @Test
    void parseIsForgiving() {
        assertEquals(DialogueAction.CLOSE, DialogueAction.parse(null));
        assertEquals(DialogueAction.OFFER_QUEST, DialogueAction.parse("offer-quest"));
        assertEquals(DialogueAction.PAGE, DialogueAction.parse("PAGE"));
    }

    @Test
    void commandsAreAdvanced() {
        assertTrue(DialogueAction.RUN_CONSOLE.isAdvanced());
        assertFalse(DialogueAction.OFFER_QUEST.isAdvanced());
        assertFalse(DialogueAction.CLOSE.needsTarget());
        assertTrue(DialogueAction.PAGE.needsTarget());
    }
}

class EditorCancelTest {

    @Test
    void cancelWordsCoverEnglishAndGerman() {
        assertTrue(NpcEditorListener.isCancel("cancel"));
        assertTrue(NpcEditorListener.isCancel("ABORT"));
        assertTrue(NpcEditorListener.isCancel("stop"));
        assertTrue(NpcEditorListener.isCancel("abbrechen"));
        assertTrue(NpcEditorListener.isCancel("back"));
        assertFalse(NpcEditorListener.isCancel("Harbor Guide"));
    }
}

class PageIdTest {

    @Test
    void sanitizePageId() {
        assertEquals("greeting", CustomNpc.sanitizePageId(null));
        assertEquals("shop", CustomNpc.sanitizePageId("Shop!"));
        assertEquals("goodbye", CustomNpc.sanitizePageId("  goodbye  "));
    }
}
