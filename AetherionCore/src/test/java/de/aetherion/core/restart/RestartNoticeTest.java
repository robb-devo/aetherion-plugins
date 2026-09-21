package de.aetherion.core.restart;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartNoticeTest {

    @Test
    void defaultsWhenArgsMissing() {
        RestartNotice.Plan plan = RestartNotice.parse(new String[0], 5, 45);
        assertEquals(5, plan.countdownSeconds());
        assertEquals(45, plan.etaSeconds());
    }

    @Test
    void parseCountdownAndEta() {
        RestartNotice.Plan plan = RestartNotice.parse(new String[] {"5", "45"}, 10, 60);
        assertEquals(5, plan.countdownSeconds());
        assertEquals(45, plan.etaSeconds());
    }

    @Test
    void clampsWildValues() {
        RestartNotice.Plan plan = RestartNotice.parse(new String[] {"0", "9999"}, 5, 45);
        assertEquals(RestartNotice.MIN_COUNTDOWN, plan.countdownSeconds());
        assertEquals(RestartNotice.MAX_ETA, plan.etaSeconds());
    }

    @Test
    void etaStaysSecondsUntilAMinuteAndAHalf() {
        assertEquals("~45s", RestartNotice.etaLabel(45));
        assertEquals("~2 min", RestartNotice.etaLabel(90));
    }

    @Test
    void germanFromLocale() {
        assertTrue(RestartNotice.german(Locale.GERMAN));
        assertTrue(RestartNotice.german(Locale.GERMANY));
        assertFalse(RestartNotice.german(Locale.ENGLISH));
        assertFalse(RestartNotice.german(null));
    }

    @Test
    void chatMentionsCountdownAndEta() {
        String en = RestartNotice.chat(5, 45, false);
        assertTrue(en.contains("5s"));
        assertTrue(en.contains("~45s"));
        String de = RestartNotice.chat(5, 45, true);
        assertTrue(de.contains("5s"));
        assertTrue(de.toLowerCase(Locale.ROOT).contains("neustart"));
    }
}
