package de.aetherion.helm.gateway;

/** Small German formatting helpers for texts that go into game chat, events and Discord. */
public final class Fmt {
    private Fmt() {}

    public static String duration(long ms) {
        long m = ms / 60_000;
        if (m < 60) return m + " Min.";
        long hrs = m / 60;
        if (hrs < 48) return hrs + " Std.";
        long d = hrs / 24;
        return d + " Tage";
    }

    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        double v = b;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(java.util.Locale.GERMANY, "%.1f %s", v, u[i]);
    }
}
