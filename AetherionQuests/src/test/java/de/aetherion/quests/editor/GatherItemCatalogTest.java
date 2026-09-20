package de.aetherion.quests.editor;

import de.aetherion.quests.model.ObjectiveType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherItemCatalogTest {

    @Test
    void listsAetherionAndVanillaGatherTargets() {
        assertTrue(GatherItemCatalog.containsId("simple_pickaxe"));
        assertTrue(GatherItemCatalog.containsId("simple_axe"));
        assertTrue(GatherItemCatalog.containsId("OAK_LOG"));
        assertTrue(GatherItemCatalog.containsId("WHEAT"));
        assertFalse(GatherItemCatalog.custom().isEmpty());
        assertFalse(GatherItemCatalog.vanilla().isEmpty());
        assertTrue(GatherItemCatalog.all().size() > GatherItemCatalog.custom().size());
    }

    @Test
    void prettyTitleCasesIds() {
        assertTrue(GatherItemCatalog.pretty("oak_log").equalsIgnoreCase("Oak Log"));
        assertTrue(GatherItemCatalog.pretty("simple_pickaxe").contains("Simple"));
    }

    @Test
    void gatherTypesMatchFactory() {
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.COLLECT));
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.MINE));
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.HARVEST));
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.DELIVER));
        assertTrue(EditorQuestFactory.isGatherType(ObjectiveType.FISH));
        assertFalse(EditorQuestFactory.isGatherType(ObjectiveType.TALK));
        assertFalse(EditorQuestFactory.isGatherType(ObjectiveType.KILL));
    }
}
