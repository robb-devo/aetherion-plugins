package de.aetherion.quests.lang;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny quest/hint language pack. English stays in code as source of truth;
 * {@code lang/de.yml} overrides when the player chose German.
 */
public final class LangPack {

    private static final Map<String, FileConfiguration> CACHE = new ConcurrentHashMap<>();

    private LangPack() {
    }

    public static void reload() {
        CACHE.clear();
        load(LangCode.DE);
    }

    public static String[] dialogs(Player player, String dialogId, String[] english) {
        return lines(player, "dialogs." + key(dialogId), english);
    }

    public static String[] completed(Player player, String npcId, String[] english) {
        return lines(player, "completed." + key(npcId), english);
    }

    public static String questTitle(Player player, String questId, String english) {
        return text(player, "quests." + key(questId) + ".title", english);
    }

    public static String questDescription(Player player, String questId, String english) {
        return text(player, "quests." + key(questId) + ".description", english);
    }

    public static String ui(Player player, String path, String english) {
        return text(player, "ui." + path, english);
    }

    public static String progress(Player player, String path, String english) {
        return text(player, "progress." + path, english);
    }

    public static String msg(Player player, String path, String english) {
        return text(player, "msg." + path, english);
    }

    public static String format(Player player, String path, String english, Object... args) {
        String raw = text(player, path, english);
        if (args == null || args.length == 0) {
            return raw;
        }
        try {
            return java.text.MessageFormat.format(raw, args);
        } catch (IllegalArgumentException ignored) {
            return raw;
        }
    }

    private static String[] lines(Player player, String path, String[] english) {
        if (english == null) {
            return new String[0];
        }
        if (!PlayerLang.of(player).german()) {
            return english;
        }
        FileConfiguration de = load(LangCode.DE);
        if (de == null) {
            return english;
        }
        List<String> list = de.getStringList(path);
        if (list == null || list.isEmpty()) {
            return english;
        }
        return list.toArray(new String[0]);
    }

    private static String text(Player player, String path, String english) {
        if (english == null) {
            return "";
        }
        if (!PlayerLang.of(player).german()) {
            return english;
        }
        FileConfiguration de = load(LangCode.DE);
        if (de == null) {
            return english;
        }
        String value = de.getString(path);
        if (value == null || value.isBlank()) {
            return english;
        }
        return value;
    }

    private static FileConfiguration load(LangCode code) {
        if (code == null || code == LangCode.EN) {
            return null;
        }
        return CACHE.computeIfAbsent(code.id(), id -> {
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin == null) {
                return new YamlConfiguration();
            }
            String resource = "lang/" + id + ".yml";
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    plugin.getLogger().warning("[Lang] Missing " + resource + " — German falls back to English.");
                    return new YamlConfiguration();
                }
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)
                );
                ConfigurationSection root = yaml.getConfigurationSection("");
                if (root == null && yaml.getKeys(false).isEmpty()) {
                    plugin.getLogger().warning("[Lang] Empty " + resource);
                }
                return yaml;
            } catch (Exception exception) {
                plugin.getLogger().warning("[Lang] Failed to load " + resource + ": " + exception.getMessage());
                return new YamlConfiguration();
            }
        });
    }

    private static String key(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Debug helper — how many dialog overrides are loaded. */
    public static int dialogOverrideCount() {
        FileConfiguration de = load(LangCode.DE);
        if (de == null) {
            return 0;
        }
        ConfigurationSection section = de.getConfigurationSection("dialogs");
        return section == null ? 0 : section.getKeys(false).size();
    }

    public static List<String> missingHint() {
        List<String> out = new ArrayList<>();
        out.add("§8German pack is experimental — missing lines stay English.");
        return out;
    }
}
