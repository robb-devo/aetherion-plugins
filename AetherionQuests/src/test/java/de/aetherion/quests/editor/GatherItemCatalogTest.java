package de.aetherion.quests.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void includesCampingMeatsRawAndCooked() {
        for (String id : List.of(
                "BEEF", "COOKED_BEEF",
                "PORKCHOP", "COOKED_PORKCHOP",
                "CHICKEN", "COOKED_CHICKEN",
                "MUTTON", "COOKED_MUTTON",
                "RABBIT", "COOKED_RABBIT"
        )) {
            assertTrue(GatherItemCatalog.containsId(id), id);
        }
    }

    @Test
    void includesGatherableFish() {
        for (String id : List.of("COD", "COOKED_COD", "SALMON", "COOKED_SALMON", "TROPICAL_FISH", "PUFFERFISH")) {
            assertTrue(GatherItemCatalog.containsId(id), id);
        }
    }

    @Test
    void meatsAreFirstOnTheAllShelf() {
        assertEquals("BEEF", GatherItemCatalog.all().get(0).id());
        assertEquals(GatherItemCatalog.Kind.MEAT, GatherItemCatalog.all().get(0).kind());
    }

    @Test
    void searchMeatFindsTheCampingShelf() {
        Set<String> ids = ids(GatherItemCatalog.find(GatherItemCatalog.Kind.ALL, "meat"));
        assertTrue(ids.contains("BEEF"));
        assertTrue(ids.contains("COOKED_BEEF"));
        assertTrue(ids.contains("PORKCHOP"));
        assertTrue(ids.contains("COOKED_PORKCHOP"));
        assertTrue(ids.contains("CHICKEN"));
        assertTrue(ids.contains("MUTTON"));
        assertTrue(ids.contains("RABBIT"));
        assertFalse(ids.contains("OAK_LOG"));
        assertEquals(GatherItemCatalog.of(GatherItemCatalog.Kind.MEAT).size(), ids.size());
    }

    @Test
    void searchBeefAndSteak() {
        Set<String> beef = ids(GatherItemCatalog.find(GatherItemCatalog.Kind.ALL, "beef"));
        assertTrue(beef.contains("BEEF"));
        assertTrue(beef.contains("COOKED_BEEF"));
        Set<String> steak = ids(GatherItemCatalog.find(GatherItemCatalog.Kind.ALL, "steak"));
        assertTrue(steak.contains("COOKED_BEEF"));
        assertTrue(steak.contains("BEEF"));
    }

    @Test
    void searchCookedKeepsMeatsNotJustBreadShelf() {
        Set<String> cooked = ids(GatherItemCatalog.find(GatherItemCatalog.Kind.ALL, "cooked"));
        assertTrue(cooked.contains("COOKED_BEEF"));
        assertTrue(cooked.contains("COOKED_PORKCHOP"));
        assertTrue(cooked.contains("COOKED_COD"));
        assertFalse(cooked.contains("BREAD"));
    }

    @Test
    void germanFleischFindsMeats() {
        Set<String> ids = ids(GatherItemCatalog.find(GatherItemCatalog.Kind.ALL, "fleisch"));
        assertTrue(ids.contains("BEEF"));
        assertTrue(ids.contains("PORKCHOP"));
        assertFalse(ids.contains("OAK_LOG"));
    }

    @Test
    void meatsTabIsOnlyMeats() {
        List<GatherItemCatalog.Entry> meats = GatherItemCatalog.of(GatherItemCatalog.Kind.MEAT);
        assertFalse(meats.isEmpty());
        for (GatherItemCatalog.Entry entry : meats) {
            assertEquals(GatherItemCatalog.Kind.MEAT, entry.kind());
        }
    }

    @Test
    void catalogStaysCurated() {
        int size = GatherItemCatalog.all().size();
        assertTrue(size > 40, "expected a usable gather shelf, got " + size);
        assertTrue(size < 150, "picker must not dump Material, got " + size);
    }

    @Test
    void prettyLabel() {
        assertEquals("Oak Log", GatherItemCatalog.pretty("OAK_LOG"));
        assertEquals("Cooked Beef", GatherItemCatalog.pretty("COOKED_BEEF"));
        assertEquals("Item", GatherItemCatalog.pretty(" "));
    }

    @Test
    void catalogIsNonEmpty() {
        assertTrue(GatherItemCatalog.all().size() > 10);
        assertEquals("BEEF", GatherItemCatalog.all().get(0).id());
    }

    private static Set<String> ids(List<GatherItemCatalog.Entry> entries) {
        return entries.stream().map(GatherItemCatalog.Entry::id).collect(Collectors.toSet());
    }
}
