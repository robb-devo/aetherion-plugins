package de.aetherion.items.rank;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabScoreboardRebindTest {

    @Test
    void recheckWaitsOutTheJoinDelayAndStillRunsPromptly() {
        boolean prompt = false;
        boolean afterJoinDelay = false;
        for (long ticks : TabScoreboardRebind.RECHECK_DELAY_TICKS) {
            if (ticks > 0 && ticks <= 5) {
                prompt = true;
            }
            // 2500ms is 50 ticks at 20 TPS. A later pass has to sit past that.
            if (ticks >= 50) {
                afterJoinDelay = true;
            }
        }
        assertTrue(prompt, "Dev Menu grant/clear needs a recheck on the next ticks");
        assertTrue(afterJoinDelay, "join delay of 2500ms must not be the last assignment");
    }

    @Test
    void conditionPlaceholderIsRefreshedBeforeTheChain() {
        assertEquals("%aetherion_has_ultra%", TabScoreboardRebind.PLACEHOLDERS[0]);
        assertTrue(contains("%aetherion_sidebar_1%"));
        assertTrue(contains("%aetherion_sidebar_2%"));
        assertTrue(contains("%aetherion_ultra_rank%"));
    }

    @Test
    void resolveUsesConditionChainNotAForcedBoard() {
        assertEquals("sendHighestScoreboard", TabScoreboardRebind.RESOLVE_METHOD);
        Object player = new Object();
        Method resolved = TabScoreboardRebind.findResolveMethod(StubManager.class, player);
        assertNotNull(resolved);
        assertEquals("sendHighestScoreboard", resolved.getName());
        assertEquals(1, resolved.getParameterCount());
        assertNull(TabScoreboardRebind.findResolveMethod(ForcedOnly.class, player));
        assertNull(TabScoreboardRebind.findUpdate(ForcedOnly.class, player));
        Method update = TabScoreboardRebind.findUpdate(StubPlaceholder.class, player);
        assertNotNull(update);
        assertEquals("update", update.getName());
    }

    private static boolean contains(String placeholder) {
        for (String candidate : TabScoreboardRebind.PLACEHOLDERS) {
            if (placeholder.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the two TAB entry points. Only the condition walk may be chosen. */
    public static final class StubManager {
        public void showScoreboard(Object player, Object scoreboard) {
        }

        public void sendHighestScoreboard(Object player) {
        }
    }

    public static final class ForcedOnly {
        public void showScoreboard(Object player, Object scoreboard) {
        }
    }

    public static final class StubPlaceholder {
        public void update(Object player) {
        }

        public void updateValue(Object player, String value) {
        }
    }
}
