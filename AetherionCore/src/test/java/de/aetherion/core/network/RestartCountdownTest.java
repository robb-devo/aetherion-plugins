package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartCountdownTest {

    @Test
    void tenSecondRestartAnnouncesEvenSecondsOnly() {
        assertEquals(List.of(10, 8, 6, 4, 2), RestartCountdown.evenTicks(10));
        assertEquals(List.of("10", "8", "6", "4", "2"), linesFromClock(10));
    }

    @Test
    void oddDurationStillAnnouncesOnlyEvenRemainders() {
        assertEquals(List.of(14, 12, 10, 8, 6, 4, 2), RestartCountdown.evenTicks(15));
        assertEquals(List.of(8, 6, 4, 2), RestartCountdown.evenTicks(9));
        assertEquals(List.of("14", "12", "10", "8", "6", "4", "2"), linesFromClock(15));
    }

    @Test
    void manualRestartIsCountdownOnly() {
        assertEquals(
                List.of("10", "8", "6", "4", "2"),
                RestartCountdown.playerLines(null, 10)
        );
    }

    @Test
    void reasonAddsOneEnglishOpenLineThenTheCountdown() {
        List<String> lines = RestartCountdown.playerLines("Patch Ashen-Katana-Restore", 10);
        assertEquals(
                "Patch Ashen-Katana-Restore goes live — server will reset. Expected back in about 1 minute.",
                lines.get(0)
        );
        assertEquals(
                List.of("10", "8", "6", "4", "2"),
                lines.subList(1, lines.size())
        );
        assertEquals(6, lines.size());
    }

    @Test
    void clockStopsOneStepAfterTheLastSecond() {
        RestartCountdown countdown = new RestartCountdown(10);
        int steps = 0;
        while (!countdown.stopped()) {
            countdown.tick();
            steps++;
            assertTrue(steps < 30, "countdown did not stop");
        }
        // Announce at t=0, then one step per second, then the expiry step at t=10.
        assertEquals(11, steps);
    }

    private static List<String> linesFromClock(int seconds) {
        RestartCountdown countdown = new RestartCountdown(seconds);
        List<String> lines = new ArrayList<>();
        int guard = 0;
        while (!countdown.stopped()) {
            String line = countdown.tick();
            if (line != null) {
                lines.add(line);
            }
            if (++guard > 500) {
                throw new AssertionError("countdown did not stop");
            }
        }
        return lines;
    }
}
