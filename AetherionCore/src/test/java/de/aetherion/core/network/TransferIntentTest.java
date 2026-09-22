package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferIntentTest {

    @Test
    void appliesOnlyOnTheDestination() {
        assertEquals(TransferIntent.Action.APPLY, TransferIntent.decide("mmo-r", "mmo-d", "mmo-r"));
        assertEquals(TransferIntent.Action.APPLY, TransferIntent.decide("mmo-d", "mmo-r", "MMO-D"));
    }

    @Test
    void discardsWhenThePlayerNeverLeft() {
        assertEquals(TransferIntent.Action.DISCARD, TransferIntent.decide("mmo-r", "mmo-d", "mmo-d"));
        assertEquals(TransferIntent.Action.DISCARD, TransferIntent.decide("mmo-d", "hub", "hub"));
    }

    @Test
    void leavesSnapshotsAddressedSomewhereElse() {
        assertEquals(TransferIntent.Action.LEAVE, TransferIntent.decide("mmo-r", "mmo-d", "hub"));
        assertEquals(TransferIntent.Action.LEAVE, TransferIntent.decide("mmo-d", "mmo-r", "mmo-c"));
    }

    @Test
    void legacySnapshotsStillApply() {
        assertEquals(TransferIntent.Action.APPLY, TransferIntent.decide(null, "mmo-d", "mmo-r"));
        assertEquals(TransferIntent.Action.APPLY, TransferIntent.decide("", "mmo-r", "unknown"));
        assertEquals(TransferIntent.Action.APPLY, TransferIntent.decide("mmo-r", "hub", null));
    }
}
