package de.aetherion.quests.editor;

import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorQuestFactoryTest {

    @Test
    void slugStripsColorAndSpaces() {
        assertEquals("harbor_guide", EditorQuestFactory.slug("Harbor Guide"));
        assertEquals("editor_quest", EditorQuestFactory.slug("   "));
    }

    @Test
    void uniqueIdSkipsTakenKeys() {
        Set<String> taken = new HashSet<>();
        taken.add("fetch_oak");
        taken.add("fetch_oak_2");
        assertEquals("fetch_oak_3", EditorQuestFactory.uniqueId("Fetch Oak", taken));
    }

    @Test
    void talkQuestUsesEditorServiceAndDefaultRewards() {
        Quest quest = EditorQuestFactory.talkQuest("fetch_oak", "Fetch Oak", "mod_guide");
        assertEquals("editor", quest.getServiceId());
        assertTrue(EditorQuestFactory.isEditorQuest(quest));
        assertEquals(1, quest.getObjectives().size());
        assertEquals(ObjectiveType.TALK, quest.getObjectives().get(0).getType());
        assertEquals("mod_guide", quest.getObjectives().get(0).getTarget());
        assertEquals(2, quest.getRewards().size());
    }

    @Test
    void gatherTypesMatchLiveObjectiveEngine() {
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.COLLECT));
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.DELIVER));
        assertFalse(EditorQuestFactory.isGatherType(ObjectiveType.TALK));
        assertFalse(EditorQuestFactory.isGatherType(ObjectiveType.KILL));
    }

    @Test
    void currencyBumpUsesBiggerSteps() {
        assertEquals(35, EditorQuestFactory.bumpAmount("XP", 25, 1));
        assertEquals(15, EditorQuestFactory.bumpAmount("Coins", 25, -1));
        assertEquals(2, EditorQuestFactory.bumpAmount("OAK_LOG", 1, 1));
    }
}
