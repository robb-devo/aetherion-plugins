package de.aetherion.proxy;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** {@code pack.properties} plus the SHA-1 of the zip it points at. */
final class ResourcePack {

    private static final String FILE = "pack.properties";
    private static final String DEFAULT_URL = "http://play.donnernet.de:8088/Aetherion_texturepack.zip";
    private static final String DEFAULT_PROMPT =
            "<gold>Aetherion</gold> <gray>needs its resource pack · braucht sein Ressourcenpaket</gray>";
    /** A player who joins while the hash is still being read is retried this often, 2 s apart. */
    private static final int OFFER_RETRIES = 15;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile String url = DEFAULT_URL;
    private volatile ResourcePackInfo info;
    private volatile String status = "loading";

    ResourcePack(Object plugin, ProxyServer proxy, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    boolean isOurs(String candidate) {
        return candidate != null && candidate.equals(url);
    }

    String status() {
        return status;
    }

    /** Re-reads the config and the zip's hash off the event threads; {@code source} hears the result. */
    CompletableFuture<Void> reload(CommandSource source) {
        return CompletableFuture.runAsync(() -> {
            try {
                Properties config = config();
                String packUrl = config.getProperty("url", DEFAULT_URL).trim();
                boolean required = Boolean.parseBoolean(config.getProperty("required", "true").trim());
                Component prompt = MiniMessage.miniMessage().deserialize(config.getProperty("prompt", DEFAULT_PROMPT));
                byte[] sha1 = sha1(packUrl);
                url = packUrl;
                info = proxy.createResourcePackBuilder(packUrl)
                        .setId(UUID.nameUUIDFromBytes(("aetherion:" + packUrl).getBytes(StandardCharsets.UTF_8)))
                        .setHash(sha1)
                        .setShouldForce(required)
                        .setPrompt(prompt)
                        .build();
                status = "ready, sha1 " + HexFormat.of().formatHex(sha1) + (required ? ", required" : ", optional");
                logger.info("Resource pack {}: {}", packUrl, status);
            } catch (IOException | RuntimeException e) {
                status = "failed: " + e.getMessage();
                logger.error("Resource pack not loaded, players join without it", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                status = "interrupted";
            }
            if (source != null) {
                source.sendPlainMessage("[aepack] " + status);
            }
        });
    }

    void offer(Player player) {
        offer(player, OFFER_RETRIES);
    }

    private void offer(Player player, int retries) {
        ResourcePackInfo pack = info;
        if (pack != null) {
            player.sendResourcePackOffer(pack);
            return;
        }
        if (retries > 0 && player.isActive()) {
            proxy.getScheduler().buildTask(plugin, () -> offer(player, retries - 1))
                    .delay(2, TimeUnit.SECONDS)
                    .schedule();
        }
    }

    /** Sends the current pack to everyone online, e.g. right after a swap. */
    int pushAll() {
        ResourcePackInfo pack = info;
        if (pack == null) {
            return 0;
        }
        int sent = 0;
        for (Player player : proxy.getAllPlayers()) {
            player.sendResourcePackOffer(pack);
            sent++;
        }
        return sent;
    }

    private Properties config() throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve(FILE);
        Properties config = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config.load(in);
            }
            return config;
        }
        config.setProperty("url", DEFAULT_URL);
        config.setProperty("required", "true");
        config.setProperty("prompt", DEFAULT_PROMPT);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            config.store(out, "Aetherion resource pack, sent once per session by the proxy. "
                    + "After replacing the zip: aepack reload, then aepack push.");
        }
        return config;
    }

    private byte[] sha1(String packUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(packUrl)).timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + packUrl);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
