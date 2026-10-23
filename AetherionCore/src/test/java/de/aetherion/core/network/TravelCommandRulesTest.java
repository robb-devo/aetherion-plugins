package de.aetherion.core.network;

import com.mojang.brigadier.Command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelCommandRulesTest {

    @Test
    void successfulTeleportReportsSuccessNotAnError() {
        assertEquals(1, TravelCommandRules.SUCCESS);
        assertEquals(Command.SINGLE_SUCCESS, TravelCommandRules.SUCCESS);
        assertTrue(TravelCommandRules.SUCCESS > 0);
    }

    @Test
    void capitalOnAnotherBackendIsPublished() {
        boolean steals = TravelCommandRules.steals(false, false, false, false, true, false);
        assertTrue(TravelCommandRules.publish(true, true, steals));
    }

    @Test
    void mainWorldDoesNotPublishTravelCommands() {
        assertFalse(TravelCommandRules.publish(false, true, true));
    }

    @Test
    void unreachableTargetIsNotPublished() {
        assertFalse(TravelCommandRules.publish(true, false, true));
    }

    @Test
    void hubKeepsAWarpItAlreadyAnswers() {
        assertFalse(TravelCommandRules.steals(true, false, true, false, true, false));
    }

    @Test
    void dungeonsKeepsItsOwnLabelOnTheDungeonServer() {
        assertFalse(TravelCommandRules.steals(true, false, false, true, true, true));
    }

    @Test
    void anOwnedMainWorldLabelStillCrosses() {
        assertTrue(TravelCommandRules.steals(true, false, false, false, true, false));
    }

    @Test
    void forcedMarketCommandStillCrosses() {
        assertTrue(TravelCommandRules.steals(true, true, false, false, true, false));
    }

    @Test
    void aLocalNonMainCommandStays() {
        assertFalse(TravelCommandRules.steals(true, false, false, false, false, false));
    }

    @Test
    void harborReplaysAsHarbour() {
        assertEquals("harbour", TravelCommandRules.replay("harbor", ""));
        assertEquals("harbour extra", TravelCommandRules.replay("hafen", " extra"));
        assertEquals("capital", TravelCommandRules.replay("capital", ""));
    }

    @Test
    void namespaceAndCaseAreStripped() {
        assertEquals("capital", TravelCommandRules.bareLabel("Essentials:Capital"));
        assertEquals("spawn", TravelCommandRules.bareLabel("spawn"));
    }
}
