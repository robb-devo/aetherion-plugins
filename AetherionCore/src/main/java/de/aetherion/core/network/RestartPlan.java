package de.aetherion.core.network;

import java.util.Arrays;

/**
 * {@code aenet restart [seconds] [reason...]}.
 * Seconds default to 10. A non-numeric token is the start of an optional reason.
 * No reason means a manual restart: countdown only, no invented patch line.
 */
public final class RestartPlan {

    public static final int DEFAULT_SECONDS = 10;
    public static final int MIN_SECONDS = 3;
    public static final int MAX_SECONDS = 120;
    public static final int MAX_REASON_LENGTH = 80;

    private final int seconds;
    private final String reason;
    private final String error;

    private RestartPlan(int seconds, String reason, String error) {
        this.seconds = seconds;
        this.reason = reason;
        this.error = error;
    }

    public int seconds() {
        return seconds;
    }

    /** Patch or other reason, or null when the operator did not pass one. */
    public String reason() {
        return reason;
    }

    public boolean hasReason() {
        return reason != null;
    }

    public String error() {
        return error;
    }

    public boolean ok() {
        return error == null;
    }

    public static RestartPlan parse(String[] args) {
        if (args == null || args.length == 0 || !"restart".equalsIgnoreCase(args[0])) {
            return new RestartPlan(0, null, "Usage: aenet restart [seconds] [reason]");
        }
        int seconds = DEFAULT_SECONDS;
        int reasonFrom = 1;
        if (args.length >= 2) {
            try {
                seconds = Integer.parseInt(args[1]);
                reasonFrom = 2;
            } catch (NumberFormatException ex) {
                reasonFrom = 1;
            }
        }
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
            return new RestartPlan(0, null, "Seconds must be between " + MIN_SECONDS + " and " + MAX_SECONDS + ".");
        }
        String reason = null;
        if (args.length > reasonFrom) {
            reason = String.join(" ", Arrays.copyOfRange(args, reasonFrom, args.length)).trim();
            if (reason.isEmpty()) {
                reason = null;
            } else if (reason.length() > MAX_REASON_LENGTH) {
                reason = reason.substring(0, MAX_REASON_LENGTH).trim();
            }
        }
        return new RestartPlan(seconds, reason, null);
    }
}
