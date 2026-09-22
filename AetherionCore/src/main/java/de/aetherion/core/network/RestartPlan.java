package de.aetherion.core.network;

import java.util.Arrays;

/**
 * {@code /aenet restart [seconds] [eta...]}.
 * Seconds default to 10. A non-numeric second token is treated as the start of the ETA.
 */
public final class RestartPlan {

    public static final int DEFAULT_SECONDS = 10;
    public static final int MIN_SECONDS = 3;
    public static final int MAX_SECONDS = 120;
    public static final String DEFAULT_ETA = "about a minute";

    private final int seconds;
    private final String eta;
    private final String error;

    private RestartPlan(int seconds, String eta, String error) {
        this.seconds = seconds;
        this.eta = eta;
        this.error = error;
    }

    public int seconds() {
        return seconds;
    }

    public String eta() {
        return eta;
    }

    public String error() {
        return error;
    }

    public boolean ok() {
        return error == null;
    }

    public static RestartPlan parse(String[] args) {
        if (args == null || args.length == 0 || !"restart".equalsIgnoreCase(args[0])) {
            return new RestartPlan(0, null, "Usage: /aenet restart [seconds] [eta]");
        }
        int seconds = DEFAULT_SECONDS;
        int etaFrom = 1;
        if (args.length >= 2) {
            try {
                seconds = Integer.parseInt(args[1]);
                etaFrom = 2;
            } catch (NumberFormatException ex) {
                etaFrom = 1;
            }
        }
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
            return new RestartPlan(0, null, "Seconds must be between " + MIN_SECONDS + " and " + MAX_SECONDS + ".");
        }
        String eta = DEFAULT_ETA;
        if (args.length > etaFrom) {
            eta = String.join(" ", Arrays.copyOfRange(args, etaFrom, args.length)).trim();
        }
        if (eta.isBlank()) {
            eta = DEFAULT_ETA;
        }
        if (eta.length() > 80) {
            eta = eta.substring(0, 80).trim();
        }
        return new RestartPlan(seconds, eta, null);
    }
}
