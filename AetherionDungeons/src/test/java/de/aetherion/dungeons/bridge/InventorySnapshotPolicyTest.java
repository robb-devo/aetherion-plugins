package de.aetherion.dungeons.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySnapshotPolicyTest {

    @Test
    void omittedFlagKeepsDestinationEvenWhenBlobsArePresent() {
        assertTrue(InventorySnapshotPolicy.keepDestination(5, true, "hub", false, false, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(5, true, "mmo-r", true, true, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(4, true, "dungeon", true, false, List.of()));
    }

    @Test
    void missingBlobsKeepDestination() {
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, "hub", false, false, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, "mmo-d", false, false, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(4, false, "hub", false, false, List.of()));
    }

    @Test
    void coreV5HubSourceKeepsDestinationUnlessTransferIsEnabled() {
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, "hub", true, false, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, " HUB ", true, false, List.of()));
        assertFalse(InventorySnapshotPolicy.keepDestination(5, false, "hub", true, true, List.of()));
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, "hub", true, true, List.of("hub")));
    }

    @Test
    void presentMmoBlobsStillApply() {
        assertFalse(InventorySnapshotPolicy.keepDestination(5, false, "mmo-r", true, false, List.of()));
        assertFalse(InventorySnapshotPolicy.keepDestination(5, false, "mmo-d", true, false, List.of()));
        assertFalse(InventorySnapshotPolicy.keepDestination(5, false, "mmo-c", true, false, null));
        assertFalse(InventorySnapshotPolicy.keepDestination(4, false, "dungeon", true, false, List.of()));
    }

    @Test
    void dungeonsV4RoleHubStillAppliesPresentBlobs() {
        // v4 from-server "hub" is the mmo-r role, and those snapshots include gear.
        assertFalse(InventorySnapshotPolicy.keepDestination(4, false, "hub", true, false, List.of()));
    }

    @Test
    void extraV5SourcesCanBeIgnored() {
        assertTrue(InventorySnapshotPolicy.keepDestination(5, false, "mmo-c", true, true, List.of(" MMO-C ")));
        assertFalse(InventorySnapshotPolicy.keepDestination(4, false, "mmo-c", true, true, List.of("mmo-c")));
    }
}
