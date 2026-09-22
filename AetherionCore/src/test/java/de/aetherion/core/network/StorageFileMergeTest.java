package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageFileMergeTest {

    @Test
    void missingPageOnTheSnapshotIsKeptFromDisk() {
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
        assertTrue(StorageFileMerge.unlockedPages(merged) >= 3);
        assertTrue(merged.contains("bbb"));
        assertTrue(merged.contains("old_sword"));
        assertTrue(merged.contains("hub-access: true") || merged.contains("hub-access: true\n"));
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
    void blankIncomingDoesNotEraseDisk() {
        String disk = "unlocked-pages: 2\npages:\n  1:\n    0-bytes: aaa\n";
        assertEquals(disk, StorageFileMerge.merge(disk, "  \n"));
        assertEquals(disk, StorageFileMerge.merge(disk, null));
    }
}
