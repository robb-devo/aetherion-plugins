package de.aetherion.stressbots.control;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the clickable dashboard URL from {@code testbots.dashboard.public-url}
 * plus a runner session token. Must stay http(s) or the client will not open it.
 */
public final class DashboardLinks {

    private DashboardLinks() {
    }

    public static String join(String base, String token) {
        if (base == null) {
            return "";
        }
        String root = base.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (!root.startsWith("http://") && !root.startsWith("https://")) {
            return "";
        }
        if (token == null || token.isBlank()) {
            return root + "/";
        }
        return root + "/?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
