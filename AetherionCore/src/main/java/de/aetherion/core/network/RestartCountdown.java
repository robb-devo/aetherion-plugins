package de.aetherion.core.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing restart copy and the one-second clock behind it.
 * Chat is announced only on positive even remaining seconds, so a 10s restart
 * shows 10, 8, 6, 4, 2. The scheduler still steps once per second so an odd
 * duration lands on those even seconds instead of drifting onto odds.
 */
public final class RestartCountdown {

    /** Chat announcements are this many seconds apart. */
    public static final int CHAT_EVERY_SECONDS = 2;

    /** Scheduler period. One second, so odd totals still hit even remainders. */
    public static final long CLOCK_PERIOD_TICKS = 20L;

    private int remaining;
    private boolean stopped;

    public RestartCountdown(int seconds) {
        this.remaining = seconds;
    }

    public boolean stopped() {
        return stopped;
    }

    /**
     * Advance one second.
     * Returns the countdown line to broadcast, or null when this second is silent.
     * The call that finds the timer already expired sets {@link #stopped()} and returns null.
     */
    public String tick() {
        if (stopped) {
            return null;
        }
        if (remaining <= 0) {
            stopped = true;
            return null;
        }
        String line = announce(remaining) ? countdownLine(remaining) : null;
        remaining--;
        return line;
    }

    public static boolean announce(int remainingSeconds) {
        return remainingSeconds > 0 && remainingSeconds % CHAT_EVERY_SECONDS == 0;
    }

    /** Positive even remaining seconds, high to low. Odd totals start at the next lower even second. */
    public static List<Integer> evenTicks(int totalSeconds) {
        int start = totalSeconds - (totalSeconds % CHAT_EVERY_SECONDS);
        List<Integer> ticks = new ArrayList<>();
        for (int second = start; second >= CHAT_EVERY_SECONDS; second -= CHAT_EVERY_SECONDS) {
            ticks.add(second);
        }
        return ticks;
    }

    /** Dry countdown tick: the remaining seconds, nothing else. */
    public static String countdownLine(int remainingSeconds) {
        return Integer.toString(remainingSeconds);
    }

    public static String openLine(String reason) {
        return reason + " goes live — server will reset. Expected back in about 1 minute.";
    }

    /** Opening line (only when a reason was passed) followed by the even countdown. */
    public static List<String> playerLines(String reason, int totalSeconds) {
        List<String> lines = new ArrayList<>();
        if (reason != null && !reason.isBlank()) {
            lines.add(openLine(reason));
        }
        for (int second : evenTicks(totalSeconds)) {
            lines.add(countdownLine(second));
        }
        return lines;
    }
}
