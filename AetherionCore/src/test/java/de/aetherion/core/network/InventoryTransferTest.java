package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTransferTest {

    @Test
    void hubIsIsolatedByDefault() {
        assertTrue(InventoryTransfer.omit("hub", false, List.of()));
        assertTrue(InventoryTransfer.omit(" HUB ", false, List.of()));
        assertTrue(InventoryTransfer.omitSnapshot("hub", false, false, List.of()));
    }

    @Test
    void writerFlagOmitsEvenWhenTheNameIsAnMmoBackend() {
        assertTrue(InventoryTransfer.omitSnapshot("mmo-r", true, true, List.of()));
    }

    @Test
    void mmoTransfersStillCarryInventory() {
        assertFalse(InventoryTransfer.omit("mmo-r", false, List.of()));
        assertFalse(InventoryTransfer.omit("mmo-d", false, List.of()));
        assertFalse(InventoryTransfer.omit("mmo-c", false, null));
        assertFalse(InventoryTransfer.omit(null, false, List.of("hub")));
        assertFalse(InventoryTransfer.omit("  ", false, List.of("hub")));
        assertFalse(InventoryTransfer.omitSnapshot("mmo-d", false, false, List.of()));
    }

    @Test
    void hubCanBeReenabledUnlessItIsAlsoListed() {
        assertFalse(InventoryTransfer.omit("hub", true, List.of()));
        assertTrue(InventoryTransfer.omit("hub", true, List.of("hub")));
    }

    @Test
    void extraNamesAreIgnored() {
        assertTrue(InventoryTransfer.omit("mmo-c", true, List.of(" MMO-C ")));
    }
}
