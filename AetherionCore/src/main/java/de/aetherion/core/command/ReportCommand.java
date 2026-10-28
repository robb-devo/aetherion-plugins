package de.aetherion.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /report <what happened>}: a bug report from inside the game. Every report goes to
 * {@code plugins/AetherionCore/reports.log} and to staff online; with {@code report.discord-webhook} set in the
 * live config it is also posted to that Discord channel. One report a minute per player.
 */
public final class ReportCommand implements CommandExecutor {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ReportCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        boolean de = german(player);
        String text = String.join(" ", args).trim();
        if (text.length() < 5) {
            player.sendMessage(Component.text(de
                    ? "Schreib kurz, was passiert ist: /report <was ist kaputt>"
                    : "Tell us what happened: /report <what broke>", NamedTextColor.YELLOW));
            return true;
        }
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, plugin.getConfig().getLong("report.cooldown-seconds", 60)) * 1000L;
        Long last = lastReport.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            player.sendMessage(Component.text(de
                    ? "Einen Moment, du hast gerade erst einen Bericht geschickt."
                    : "One moment, you just sent a report.", NamedTextColor.GRAY));
            return true;
        }
        lastReport.put(player.getUniqueId(), now);

        Location at = player.getLocation();
        String server = serverName();
        String where = at.getWorld() == null ? "?" : at.getWorld().getName() + " " + at.getBlockX() + " "
                + at.getBlockY() + " " + at.getBlockZ();
        String when = LocalDateTime.now().format(STAMP);
        String line = when + " | " + server + " | " + player.getName() + " | " + where + " | " + text;
        String body = text;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            append(line);
            post(server, player.getName(), where, when, body);
        });
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("aetherion.report.notify") && !staff.equals(player)) {
                staff.sendMessage(Component.text("Report ", NamedTextColor.GOLD)
                        .append(Component.text(player.getName() + " @ " + server + " " + where + ": ", NamedTextColor.GRAY))
                        .append(Component.text(body, NamedTextColor.WHITE)));
            }
        }
        player.sendMessage(Component.text(de
                ? "Danke! Dein Bericht ist angekommen, wir schauen ihn uns an."
                : "Thanks! Your report reached us, we'll look into it.", NamedTextColor.GREEN));
        return true;
    }

    private void append(String line) {
        File file = new File(plugin.getDataFolder(), "reports.log");
        try (FileWriter out = new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8, true)) {
            out.write(line.replace('\n', ' ') + System.lineSeparator());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write reports.log: " + ex.getMessage());
        }
    }

    private void post(String server, String player, String where, String when, String text) {
        String url = plugin.getConfig().getString("report.discord-webhook", "").trim();
        if (url.isEmpty() || !url.startsWith("https://")) {
            return;
        }
        String content = "**Bug report** · " + server + " · " + player + "\n" + where + " · " + when + "\n> " + text;
        String json = "{\"username\":\"Aetherion Reports\",\"allowed_mentions\":{\"parse\":[]},\"content\":"
                + quote(content) + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                plugin.getLogger().warning("Report webhook answered " + response.statusCode());
            }
        } catch (IOException | InterruptedException | RuntimeException ex) {
            plugin.getLogger().warning("Report webhook failed: " + ex.getClass().getSimpleName());
        }
    }

    private String serverName() {
        String name = plugin.getConfig().getString("network.this-server", "").trim();
        if (!name.isEmpty()) {
            return name;
        }
        return switch (Bukkit.getPort()) {
            case 25566 -> "hub";
            case 25567 -> "mmo-r";
            case 25568 -> "mmo-d";
            case 25569 -> "mmo-c";
            case 25570 -> "mmo-i";
            case 25572 -> "mmo-m";
            default -> "port " + Bukkit.getPort();
        };
    }

    private static boolean german(Player player) {
        Locale locale = player.locale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> {
                }
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
