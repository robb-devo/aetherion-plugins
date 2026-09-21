package de.aetherion.quests.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherItemCatalogTest {

    @Test
    void includesStoryFriendlyVanilla() {
        assertTrue(GatherItemCatalog.containsId("OAK_LOG"));
        assertTrue(GatherItemCatalog.containsId("COAL"));
        assertTrue(GatherItemCatalog.containsId("WHEAT"));
        assertTrue(GatherItemCatalog.containsId("COD"));
    }

    @Test
    void prettyLabel() {
        assertEquals("Oak Log", GatherItemCatalog.pretty("OAK_LOG"));
        assertEquals("Item", GatherItemCatalog.pretty(" "));
    }

    @Test
    void catalogIsNonEmpty() {
        assertTrue(GatherItemCatalog.all().size() > 10);
        assertEquals("OAK_LOG", GatherItemCatalog.all().get(0).id());
    }
}
