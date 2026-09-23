package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartPlanTest {

    @Test
    void defaultsToTenSecondsAndNoReason() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart"});
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertNull(plan.reason());
        assertFalse(plan.hasReason());
    }

    @Test
    void bareTenSecondsStaysAManualRestart() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart", "10"});
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertNull(plan.reason());
    }

    @Test
    void readsSecondsThenTheRestAsReason() {
        RestartPlan plan = RestartPlan.parse(new String[]{
                "restart", "10", "Patch", "Ashen-Katana-Restore"
        });
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertEquals("Patch Ashen-Katana-Restore", plan.reason());
    }

    @Test
    void treatsAWordAsReasonWhenSecondsAreOmitted() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart", "Patch", "Ashen-Katana-Restore"});
        assertTrue(plan.ok());
        assertEquals(10, plan.seconds());
        assertEquals("Patch Ashen-Katana-Restore", plan.reason());
    }

    @Test
    void honorsACustomDuration() {
        RestartPlan plan = RestartPlan.parse(new String[]{"restart", "15"});
        assertTrue(plan.ok());
        assertEquals(15, plan.seconds());
        assertNull(plan.reason());
    }

    @Test
    void rejectsTinyOrHugeCountdowns() {
        assertFalse(RestartPlan.parse(new String[]{"restart", "1"}).ok());
        assertFalse(RestartPlan.parse(new String[]{"restart", "999"}).ok());
        assertFalse(RestartPlan.parse(new String[]{"status"}).ok());
    }
}
