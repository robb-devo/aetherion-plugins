package de.aetherion.items.guide;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Discord guide via DiscordSRV's JDA — only in the dedicated #guide channel.
 * Does not listen in #chat or #support (those stay clean).
 */
public final class DiscordGuideHook {

    private final AetherionItems plugin;
    private Object jdaListener;
    private Object jda;
    private String guideChannelId = "";

    public DiscordGuideHook(AetherionItems plugin) {
        this.plugin = plugin;
        reloadChannelId();
    }

    public void reloadChannelId() {
        File file = new File(plugin.getDataFolder(), "discord-guide.yml");
        if (!file.exists()) {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("guide-channel-id", "");
            yml.set("comments", "Discord channel ID for #guide. Leave empty to disable Discord guide.");
            try {
                yml.save(file);
            } catch (Exception ignored) {
                // ignore
            }
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        guideChannelId = yml.getString("guide-channel-id", "");
        if (guideChannelId == null) {
            guideChannelId = "";
        }
        guideChannelId = guideChannelId.trim();
    }

    public void tryHook() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (jdaListener != null) {
            return;
        }
        reloadChannelId();
        if (guideChannelId.isEmpty()) {
            plugin.getLogger().info("Discord guide: no guide-channel-id set yet.");
            return;
        }
        try {
            Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object dsrvPlugin = discordSrv.getMethod("getPlugin").invoke(null);
            jda = dsrvPlugin.getClass().getMethod("getJda").invoke(dsrvPlugin);
            if (jda == null) {
                plugin.getLogger().warning("Discord guide: JDA not ready yet.");
                return;
            }
            Class<?> eventListener;
            try {
                eventListener = Class.forName("github.scarsz.discordsrv.dependencies.jda.api.hooks.EventListener");
            } catch (ClassNotFoundException shaded) {
                eventListener = Class.forName("net.dv8tion.jda.api.hooks.EventListener");
            }
            InvocationHandler handler = (proxy, method, args) -> {
                if ("onEvent".equals(method.getName()) && args != null && args.length == 1) {
                    onJdaEvent(args[0]);
                }
                return null;
            };
            jdaListener = Proxy.newProxyInstance(
                    eventListener.getClassLoader(),
                    new Class<?>[] {eventListener},
                    handler
            );
            Method add = jda.getClass().getMethod("addEventListener", Object[].class);
            add.invoke(jda, (Object) new Object[] {jdaListener});
            plugin.getLogger().info("Guide live on Discord — type `guide` in #guide only.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not hook Discord guide: " + t.getMessage());
        }
    }

    public void unhook() {
        if (jda == null || jdaListener == null) {
            return;
        }
        try {
            Method remove = jda.getClass().getMethod("removeEventListener", Object[].class);
            remove.invoke(jda, (Object) new Object[] {jdaListener});
        } catch (Throwable ignored) {
            // ignore
        }
        jdaListener = null;
        jda = null;
    }

    private void onJdaEvent(Object event) {
        try {
            String simple = event.getClass().getSimpleName();
            if (!"MessageReceivedEvent".equals(simple) && !simple.endsWith("MessageReceivedEvent")) {
                return;
            }
            Object author = event.getClass().getMethod("getAuthor").invoke(event);
            Boolean bot = (Boolean) author.getClass().getMethod("isBot").invoke(author);
            if (Boolean.TRUE.equals(bot)) {
                return;
            }
            Object channel = event.getClass().getMethod("getChannel").invoke(event);
            String channelId = String.valueOf(channel.getClass().getMethod("getId").invoke(channel));
            Object message = event.getClass().getMethod("getMessage").invoke(event);
            String content = String.valueOf(message.getClass().getMethod("getContentRaw").invoke(message));
            String authorId = String.valueOf(author.getClass().getMethod("getId").invoke(author));

            String reply = handleCommand(authorId, channelId, content);
            if (reply == null) {
                return;
            }
            try {
                Object action = message.getClass().getMethod("reply", CharSequence.class).invoke(message, reply);
                action.getClass().getMethod("queue").invoke(action);
            } catch (Throwable replyFail) {
                Object send = channel.getClass().getMethod("sendMessage", CharSequence.class).invoke(channel, reply);
                send.getClass().getMethod("queue").invoke(send);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Discord guide event error", t);
        }
    }

    public String handleCommand(String discordUserId, String channelId, String raw) {
        if (guideChannelId.isEmpty() || !guideChannelId.equals(channelId)) {
            return null;
        }
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        String argsPart;
        if (lower.equals("guide") || lower.startsWith("guide ")) {
            argsPart = lower.equals("guide") ? "" : content.substring(5).trim();
        } else if (lower.equals("/guide") || lower.startsWith("/guide ")) {
            argsPart = lower.equals("/guide") ? "" : content.substring(6).trim();
        } else if (lower.equals("!guide") || lower.startsWith("!guide ")) {
            // still accept, but only inside #guide
            argsPart = lower.equals("!guide") ? "" : content.substring(6).trim();
        } else {
            // bare message in #guide = next tip
            if (!lower.contains(" ") && lower.length() < 3) {
                return null;
            }
            // any other short command-like: treat whole line as craft? No — only guide commands
            return null;
        }

        String[] parts = argsPart.isEmpty() ? new String[0] : argsPart.split("\\s+");
        if (parts.length > 0 && equalsAny(parts[0], "how", "help", "?")) {
            return howText();
        }
        if (parts.length > 0 && equalsAny(parts[0], "craft", "recipe", "item")) {
            if (parts.length < 2) {
                return "Usage: `guide craft <item name>`";
            }
            String query = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
            return "**Craft**\n" + String.join("\n", GuideAdvice.craftLines(query, 3));
        }
        if (parts.length == 0 || equalsAny(parts[0], "next", "what", "todo")) {
            return nextReply(discordUserId);
        }
        // guide <item> as craft shorthand
        return "**Craft**\n" + String.join("\n", GuideAdvice.craftLines(String.join(" ", parts), 3));
    }

    private String nextReply(String discordUserId) {
        UUID uuid = linkedUuid(discordUserId);
        if (uuid == null) {
            return "Link your account first (`/discord link` in-game → `#link`), then ask again.\n"
                    + "Brand-new? Start at the pier with **Egon** on Aetherion.";
        }
        GuideAdvice.Result result = GuideAdvice.nextOnlineOrNull(uuid);
        if (result == null) {
            return "I need you **online on Aetherion** to read your quest progress.\n"
                    + "Join and type `guide` here again — or use `/guide` in-game.\n"
                    + "Starter tip: talk to **Egon** on the pier.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Aetherion Guide**");
        if (result.tutorial) {
            sb.append(" · orientation");
        }
        sb.append('\n').append(result.header).append('\n');
        sb.append("**Next steps:**\n");
        int n = 0;
        for (String tip : result.tips) {
            if (n++ >= 4) {
                break;
            }
            sb.append("› ").append(tip).append('\n');
        }
        sb.append("\nAlso: `guide craft <item>` · in-game `/guide`");
        return sb.toString().trim();
    }

    private UUID linkedUuid(String discordUserId) {
        try {
            Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object dsrvPlugin = discordSrv.getMethod("getPlugin").invoke(null);
            Object alm = dsrvPlugin.getClass().getMethod("getAccountLinkManager").invoke(dsrvPlugin);
            Object uuid = alm.getClass().getMethod("getUuid", String.class).invoke(alm, discordUserId);
            return uuid instanceof UUID u ? u : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String howText() {
        return """
                **Aetherion Guide** (simple helper, not an AI)
                Uses your linked Minecraft account + quest progress.

                `guide` — what to do next
                `guide craft <item>` — recipe ingredients
                `guide how` — this text

                Same as in-game `/guide`. Only works in **#guide** (not in #chat).
                """;
    }

    private static boolean equalsAny(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }
}
