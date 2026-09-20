package de.aetherion.quests.editor;

import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;
import de.aetherion.quests.util.QuestSkillGate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(2, quest.getRewards().size());
        assertEquals("XP", quest.getRewards().get(0).getName());
        assertEquals("Coins", quest.getRewards().get(1).getName());
    }

    @Test
    void rewardsAndGatherObjectiveReplaceInPlace() {
        Quest quest = EditorQuestFactory.talkQuest("oak_run", "Oak Run", "mod_clerk");
        EditorQuestFactory.setRewards(quest, List.of(
                new Reward("Coins", 200),
                new Reward("simple_axe", 1)
        ));
        EditorQuestFactory.setObjective(quest, ObjectiveType.COLLECT, "OAK_LOG", 10);
        assertEquals(1, quest.getObjectives().size());
        assertEquals(ObjectiveType.COLLECT, quest.getObjectives().get(0).getType());
        assertEquals("OAK_LOG", quest.getObjectives().get(0).getTarget());
        assertEquals(10, quest.getObjectives().get(0).getAmount());
        assertEquals(2, quest.getRewards().size());
        assertEquals(200, quest.getRewards().get(0).getAmount());
        assertEquals("simple_axe", quest.getRewards().get(1).getName());
    }

    @Test
    void requirementsUseExistingQuestGates() {
        Quest quest = EditorQuestFactory.talkQuest("later", "Later", "mod_clerk");
        quest.requireAccountLevel(5);
        quest.requirePriorQuest("dock_errand");
        quest.requireItem("OAK_LOG", 8);
        assertTrue(quest.hasRequirement());
        assertTrue(quest.hasSkillRequirement());
        assertTrue(quest.hasPriorQuestRequirement());
        assertTrue(quest.hasItemRequirement());
        String hint = QuestSkillGate.requirementHint(quest);
        assertTrue(hint.contains("dock_errand"));
        assertTrue(hint.contains("5"));
        assertTrue(hint.toLowerCase().contains("oak"));
        quest.requirePriorQuest(null);
        quest.clearItemRequirement();
        quest.requireAccountLevel(0);
        assertFalse(quest.hasRequirement());
    }

    @Test
    void bumpAmountUsesCurrencySteps() {
        assertEquals(60, EditorQuestFactory.bumpAmount("Coins", 50, 1));
        assertEquals(40, EditorQuestFactory.bumpAmount("Coins", 50, -1));
        assertEquals(2, EditorQuestFactory.bumpAmount("simple_axe", 1, 1));
        assertEquals(1, EditorQuestFactory.bumpAmount("XP", 10, -1));
    }
}
