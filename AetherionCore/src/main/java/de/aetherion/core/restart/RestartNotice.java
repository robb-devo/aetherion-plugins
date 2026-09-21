package de.aetherion.core.restart;

import java.util.Locale;

/**
 * Copy + argument parsing for {@code /aetherrestart}. No Bukkit.
 */
public final class RestartNotice {

    public static final int DEFAULT_COUNTDOWN = 5;
    public static final int DEFAULT_ETA = 45;
    public static final int MIN_COUNTDOWN = 1;
    public static final int MAX_COUNTDOWN = 30;
    public static final int MIN_ETA = 5;
    public static final int MAX_ETA = 600;

    public record Plan(int countdownSeconds, int etaSeconds) {
    }

    private RestartNotice() {
    }

    public static Plan parse(String[] args, int defaultCountdown, int defaultEta) {
        int countdown = clampCountdown(defaultCountdown);
        int eta = clampEta(defaultEta);
        if (args != null && args.length >= 1) {
            countdown = clampCountdown(parseInt(args[0], countdown));
        }
        if (args != null && args.length >= 2) {
            eta = clampEta(parseInt(args[1], eta));
        }
        return new Plan(countdown, eta);
    }

    public static int clampCountdown(int value) {
        return clamp(value, MIN_COUNTDOWN, MAX_COUNTDOWN);
    }

    public static int clampEta(int value) {
        return clamp(value, MIN_ETA, MAX_ETA);
    }

    public static String etaLabel(int seconds) {
        int safe = Math.max(1, seconds);
        if (safe < 90) {
            return "~" + safe + "s";
        }
        int minutes = Math.max(1, (int) Math.ceil(safe / 60.0));
        return "~" + minutes + " min";
    }

    public static boolean german(Locale locale) {
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    public static String chat(int left, int eta, boolean german) {
        String etaText = etaLabel(eta);
        if (left <= 0) {
            return german
                    ? "§6✦ §eNeustart jetzt. Bis gleich · " + etaText
                    : "§6✦ §eRestarting now. See you in " + etaText;
        }
        return german
                ? "§6✦ §eNeustart in §f" + left + "s §e· zurück in " + etaText
                : "§6✦ §eRestarting in §f" + left + "s §e· back in " + etaText;
    }

    public static String title(boolean german) {
        return german ? "§6§lNEUSTART" : "§6§lRESTARTING";
    }

    public static String subtitle(int left, int eta, boolean german) {
        String etaText = etaLabel(eta);
        if (left <= 0) {
            return german ? "§7Bis gleich · " + etaText : "§7See you in " + etaText;
        }
        return german
                ? "§f" + left + "s §7· zurück in " + etaText
                : "§f" + left + "s §7· back in " + etaText;
    }

    public static String kick(int eta, boolean german) {
        String etaText = etaLabel(eta);
        return german
                ? "Aetherion startet neu. Zurück in " + etaText + "."
                : "Aetherion is restarting. Back in " + etaText + ".";
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
