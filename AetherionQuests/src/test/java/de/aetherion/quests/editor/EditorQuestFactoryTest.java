package de.aetherion.quests.editor;

import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorQuestFactoryTest {

    @Test
    void slugNormalizesTitle() {
        assertEquals("dock_errand", EditorQuestFactory.slug("Dock Errand!"));
        assertEquals("editor_quest", EditorQuestFactory.slug("   "));
        assertEquals("beta_wave", EditorQuestFactory.slug("Beta Wave"));
    }

    @Test
    void uniqueIdAvoidsCollisions() {
        assertEquals("dock_errand", EditorQuestFactory.uniqueId("Dock Errand", Set.of()));
        assertEquals("dock_errand_2", EditorQuestFactory.uniqueId("Dock Errand", Set.of("dock_errand")));
    }

    @Test
    void talkQuestTargetsTheNpc() {
        Quest quest = EditorQuestFactory.talkQuest("dock_errand", "Dock Errand", "harbour_clerk");
        assertEquals("dock_errand", quest.getId());
        assertEquals("Dock Errand", quest.getTitle());
        assertEquals(1, quest.getObjectives().size());
        assertEquals(ObjectiveType.TALK, quest.getObjectives().get(0).getType());
        assertEquals("harbour_clerk", quest.getObjectives().get(0).getTarget());
        assertTrue(quest.getDescription().toLowerCase().contains("talk"));
    }
}
