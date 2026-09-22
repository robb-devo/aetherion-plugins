package de.aetherion.items.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerSlotGuardTest {

    @Test
    void realStacksLeaveTheManagerSlot() {
        assertTrue(ManagerSlotGuard.displace(false, false));
        assertFalse(ManagerSlotGuard.displace(true, false));
        assertFalse(ManagerSlotGuard.displace(false, true));
    }

    @Test
    void aRestoredSlotMatchesItsParkedCopy() {
        assertTrue(ManagerSlotGuard.parkedAlreadyInSlot(false, false, true, true));
        assertFalse(ManagerSlotGuard.parkedAlreadyInSlot(false, true, true, true));
        assertFalse(ManagerSlotGuard.parkedAlreadyInSlot(true, false, true, true));
        assertFalse(ManagerSlotGuard.parkedAlreadyInSlot(false, false, true, false));
        assertFalse(ManagerSlotGuard.parkedAlreadyInSlot(false, false, false, true));
    }
}
