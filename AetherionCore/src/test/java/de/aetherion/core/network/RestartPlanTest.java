package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartPlanTest {

    @Test
    void defaultsToTenSecondsAndAShortEta() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart"});
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertEquals("about a minute", plan.eta());
    }

    @Test
    void readsSecondsThenTheRestAsEta() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart", "15", "back", "in", "two", "minutes"});
        assertTrue(plan.ok());
        assertEquals(15, plan.seconds());
        assertEquals("back in two minutes", plan.eta());
    }

    @Test
    void treatsAWordAsEtaWhenSecondsAreOmitted() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart", "after", "the", "wipe"});
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertEquals("after the wipe", plan.eta());
    }

    @Test
    void rejectsTinyOrHugeCountdowns() {
        assertFalse(RestartPlan.parse(new String[]{"restart", "1"}).ok());
        assertFalse(RestartPlan.parse(new String[]{"restart", "999"}).ok());
        assertFalse(RestartPlan.parse(new String[]{"status"}).ok());
    }
}
