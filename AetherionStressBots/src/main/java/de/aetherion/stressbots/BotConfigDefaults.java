package de.aetherion.stressbots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Live {@code config.yml} is never overwritten by {@code saveDefaultConfig()}.
 * Fill only <em>missing</em> keys from the jar so a 2h fleet can start farm/fish
 * without a hand-merge. Existing live values stay as-is (including {@code enabled: false}).
 */
final class BotConfigDefaults {

    private BotConfigDefaults() {
    }

    static List<String> mergeMissingFromJar(JavaPlugin plugin) {
        List<String> added = new ArrayList<>();
        InputStream in = plugin.getResource("config.yml");
        if (in == null) {
            return added;
        }
        YamlConfiguration jar = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        merge(plugin.getConfig(), jar, "", added);
        return added;
    }

    static void merge(ConfigurationSection live, ConfigurationSection defaults, String prefix, List<String> added) {
        if (live == null || defaults == null) {
            return;
        }
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object defVal = defaults.get(key);
            if (defVal instanceof ConfigurationSection defSec) {
                ConfigurationSection liveSec = live.getConfigurationSection(key);
                if (liveSec == null) {
                    if (live.contains(key)) {
                        continue;
                    }
                    liveSec = live.createSection(key);
                    added.add(path);
                }
                merge(liveSec, defSec, path, added);
            } else if (!live.contains(key)) {
                live.set(key, defVal);
                added.add(path);
            }
        }
    }
}
