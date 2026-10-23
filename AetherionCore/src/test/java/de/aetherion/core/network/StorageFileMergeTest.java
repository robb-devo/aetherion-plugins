package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageFileMergeTest {

    // ------------------------------------------------------------------ the storage follows the player

    @Test
    void itemsTakenOutBeforeTheHopStayOut() {
        String disk = """
                unlocked-pages: 5
                hub-access: false
                pages:
                  '1':
                    0-bytes: conch
                    1-bytes: liquidator
                    2-bytes: flesh
                  '5':
                    23-bytes: survey_token
                    23-id: survey_token
                """;
        String incoming = """
                unlocked-pages: 5
                hub-access: false
                pages:
                  '1':
                    0-bytes: conch
                """;

        String merged = StorageFileMerge.merge(disk, incoming);

        assertEquals(1, StorageFileMerge.countSlots(merged));
        assertTrue(merged.contains("conch"));
        assertFalse(merged.contains("liquidator"));
        assertFalse(merged.contains("flesh"));
        assertFalse(merged.contains("survey_token"));
    }

    @Test
    void itemMovedToAnotherSlotIsNotDuplicated() {
        String disk = """
                unlocked-pages: 5
                pages:
                  '1':
                    0-bytes: conch
                    1-bytes: liquidator
                  '5':
                    27-bytes: mining_chestplate
                    27-id: mining_chestplate
                """;
        String incoming = """
                unlocked-pages: 5
                pages:
                  '1':
                    0-bytes: conch
                    3-bytes: mining_chestplate
                    3-id: mining_chestplate
                """;

        String merged = StorageFileMerge.merge(disk, incoming);

        assertEquals(2, StorageFileMerge.countSlots(merged));
        assertEquals(1, occurrences(merged, "-bytes: mining_chestplate"));
        assertFalse(merged.contains("liquidator"));
    }

    @Test
    void storageFollowsThePlayerToTheIslandAndBack() {
        String mainWorldBeforeHop = storage(5, "a", "b", "c");
        String islandFromLastVisit = storage(5, "a", "b", "c", "d");

        // mmo-r: c taken out, then /island. mmo-i still has its copy from the last visit.
        String leavingMainWorld = storage(5, "a", "b");
        assertEquals(slots("a", "b"), slotValues(StorageFileMerge.merge(islandFromLastVisit, leavingMainWorld)));

        // mmo-i: b taken out, then /hub. mmo-r's file is what it wrote at the hop ...
        String leavingIsland = storage(5, "a");
        assertEquals(slots("a"), slotValues(StorageFileMerge.merge(leavingMainWorld, leavingIsland)));
        // ... or an older copy another backend rewrote from its cache meanwhile.
        assertEquals(slots("a"), slotValues(StorageFileMerge.merge(mainWorldBeforeHop, leavingIsland)));
    }

    // ------------------------------------------------------------------ protections that stay

    @Test
    void exportWithFewerPagesKeepsTheDiskSlots() {
        String disk = """
                unlocked-pages: 3
                hub-access: true
                pages:
                  1:
                    0-bytes: aaa
                  2:
                    4-bytes: bbb
                    4-id: old_sword
                """;
        String incoming = """
                unlocked-pages: 1
                hub-access: false
                pages:
                  1:
                    0-bytes: aaa
                """;

        String merged = StorageFileMerge.merge(disk, incoming);

        assertEquals(2, StorageFileMerge.countSlots(merged));
        assertEquals(3, StorageFileMerge.unlockedPages(merged));
        assertTrue(merged.contains("bbb"));
        assertTrue(merged.contains("old_sword"));
        assertTrue(merged.contains("hub-access: true"));
    }

    @Test
    void exportWithoutItemsNeverWipesAFilledDisk() {
        String disk = """
                unlocked-pages: 1
                hub-access: false
                pages:
                  '1':
                    0-bytes: aaa
                    1-bytes: bbb
                """;
        String freshFile = """
                unlocked-pages: 1
                hub-access: false
                """;

        String merged = StorageFileMerge.merge(disk, freshFile);

        assertEquals(2, StorageFileMerge.countSlots(merged));
        assertTrue(merged.contains("aaa"));
        assertTrue(merged.contains("bbb"));
    }

    @Test
    void richerIncomingReplacesDisk() {
        String disk = """
                unlocked-pages: 1
                pages:
                  1:
                    0-bytes: aaa
                """;
        String incoming = """
                unlocked-pages: 2
                pages:
                  1:
                    0-bytes: aaa
                    1-bytes: ccc
                """;

        assertEquals(incoming, StorageFileMerge.merge(disk, incoming));
    }

    @Test
    void blankOrUnreadableIncomingKeepsDisk() {
        String disk = "unlocked-pages: 2\npages:\n  1:\n    0-bytes: aaa\n";

        assertEquals(disk, StorageFileMerge.merge(disk, "  \n"));
        assertEquals(disk, StorageFileMerge.merge(disk, null));
        assertEquals(disk, StorageFileMerge.merge(disk, "pages: [unclosed"));
    }

    @Test
    void firstFileOnThisBackendIsTakenAsIs() {
        String incoming = storage(5, "a");

        assertEquals(incoming, StorageFileMerge.merge("", incoming));
        assertEquals(incoming, StorageFileMerge.merge(null, incoming));
    }

    /** AetherionDungeons on mmo-r / mmo-d links against these three methods of the Core jar. */
    @Test
    void methodsUsedByTheDungeonsJarKeepTheirSignatures() throws Exception {
        assertPublicStatic(StorageFileMerge.class.getMethod("merge", String.class, String.class), String.class);
        assertPublicStatic(StorageFileMerge.class.getMethod("countSlots", String.class), int.class);
        assertPublicStatic(StorageFileMerge.class.getMethod("unlockedPages", String.class), int.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void assertPublicStatic(Method method, Class<?> returnType) {
        assertTrue(Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()),
                method + " must stay public static");
        assertEquals(returnType, method.getReturnType());
    }

    /** One page-1 slot per value, in order. */
    private static String storage(int pages, String... values) {
        StringBuilder out = new StringBuilder("unlocked-pages: " + pages + "\nhub-access: false\n");
        if (values.length > 0) {
            out.append("pages:\n  '1':\n");
            for (int slot = 0; slot < values.length; slot++) {
                out.append("    ").append(slot).append("-bytes: ").append(values[slot]).append('\n');
            }
        }
        return out.toString();
    }

    private static java.util.Set<String> slots(String... values) {
        return new java.util.TreeSet<>(java.util.List.of(values));
    }

    private static java.util.Set<String> slotValues(String raw) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (key.startsWith("pages.") && key.endsWith("-bytes")) {
                out.add(yaml.getString(key));
            }
        }
        return out;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
