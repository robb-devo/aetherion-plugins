package de.aetherion.proxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Lets the network's own test bots in through the proxy like players, without Mojang accounts.
 *
 * <p>A bot is let in offline (offline UUID, version 3) only when all of these hold: the door is enabled in
 * {@code testbots.properties}, the connection comes from this machine (loopback, unless switched off), and the
 * address the client typed starts with the door's secret token ({@code <token>.testbots}). Everyone else logs in
 * online as before. The token lives only in this file and in the runner's local {@code config.json}; the repo
 * never holds it. Backends tell bots from people by the version-3 UUID only the door hands out.
 */
public final class TestBotDoor {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String SUFFIX = ".testbots";

    private final Logger logger;
    private final Path file;
    private volatile Settings settings = Settings.OFF;
    private volatile long stamp = Long.MIN_VALUE;

    private record Settings(boolean enabled, byte[] token, boolean loopbackOnly) {
        static final Settings OFF = new Settings(false, new byte[0], true);
    }

    TestBotDoor(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.file = dataDirectory.resolve("testbots.properties");
    }

    /** Re-reads the file when it changed (cheap mtime check), writes a disabled template the first time. */
    void reload() {
        try {
            if (!Files.exists(file)) {
                writeTemplate();
            }
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified == stamp) {
                return;
            }
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            String token = props.getProperty("token", "").trim().toLowerCase(Locale.ROOT);
            boolean enabled = Boolean.parseBoolean(props.getProperty("enabled", "false").trim());
            boolean loopback = !"false".equalsIgnoreCase(props.getProperty("loopback-only", "true").trim());
            if (enabled && token.length() < 24) {
                logger.warn("Test-bot door stays shut: token in {} is shorter than 24 characters", file);
                enabled = false;
            }
            settings = new Settings(enabled, token.getBytes(StandardCharsets.UTF_8), loopback);
            stamp = modified;
            logger.info("Test-bot door {}{}", enabled ? "open" : "shut",
                    enabled ? (loopback ? " (this machine only)" : " (any address)") : "");
        } catch (IOException ex) {
            settings = Settings.OFF;
            logger.warn("Test-bot door shut: could not read {}: {}", file, ex.getMessage());
        }
    }

    private void writeTemplate() throws IOException {
        Files.createDirectories(file.getParent());
        byte[] raw = new byte[20];
        new SecureRandom().nextBytes(raw);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# Test bots (AetherionStressBots runner) log in offline through this proxy when this is on.\n");
            writer.write("# Put the same token into the runner's config.json (proxy.token). Never commit it.\n");
            writer.write("enabled=false\n");
            writer.write("token=" + HexFormat.of().formatHex(raw) + "\n");
            writer.write("# true = only connections from this machine (the runner runs next to the proxy).\n");
            writer.write("loopback-only=true\n");
        }
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        Settings current = settings;
        if (!current.enabled() || !event.getResult().isAllowed()) {
            return;
        }
        String name = event.getUsername();
        if (name == null || !NAME.matcher(name).matches()) {
            return;
        }
        InetSocketAddress remote = event.getConnection().getRemoteAddress();
        if (current.loopbackOnly() && !isLocal(remote)) {
            return;
        }
        String host = event.getConnection().getVirtualHost().map(InetSocketAddress::getHostString).orElse("");
        if (!tokenMatches(host, current.token())) {
            return;
        }
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        logger.info("Test bot {} let in offline", name);
    }

    static boolean tokenMatches(String host, byte[] token) {
        if (host == null || token == null || token.length == 0) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".")) {
            lower = lower.substring(0, lower.length() - 1);
        }
        if (!lower.endsWith(SUFFIX)) {
            return false;
        }
        byte[] given = lower.substring(0, lower.length() - SUFFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, token);
    }

    private static boolean isLocal(InetSocketAddress remote) {
        if (remote == null) {
            return false;
        }
        InetAddress address = remote.getAddress();
        return address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
    }
}
