package de.aetherion.items.world;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * One-shot markers for world edits that must not re-roll every boot.
 * File: {@code plugins/AetherionItems/worldgen-flags.yml}.
 */
public final class WorldgenFlags {

    public static final String SHABBY = "shabby-mine-ores";
    public static final String ELDERVALE = "eldervale-ores";

    private static final String FILE_NAME = "worldgen-flags.yml";
    private static final String HEADER = """
            One-shot ore passes. A missing key runs once on next boot, then is set true.
            Re-run Shabby only: delete shabby-mine-ores and restart.
            Re-run Eldervale only: delete eldervale-ores and restart.
            Or: /hubadmin oregen reset <shabby|eldervale|both>
            """;

    private WorldgenFlags() {
    }

    public static String key(AreaType type) {
        if (type == AreaType.SHABBY_MINE) {
            return SHABBY;
        }
        if (type == AreaType.ELDERVALE) {
            return ELDERVALE;
        }
        return null;
    }

    public static boolean done(JavaPlugin plugin, String key) {
        if (plugin == null || key == null) {
            return false;
        }
        File file = file(plugin);
        if (!file.isFile()) {
            return false;
        }
        return YamlConfiguration.loadConfiguration(file).getBoolean(key, false);
    }

    public static void mark(JavaPlugin plugin, String key) {
        write(plugin, key, true);
    }

    public static void clear(JavaPlugin plugin, String key) {
        write(plugin, key, false);
    }

    private static void write(JavaPlugin plugin, String key, boolean value) {
        if (plugin == null || key == null) {
            return;
        }
        File file = file(plugin);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create " + parent);
            return;
        }
        YamlConfiguration yaml = file.isFile()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        if (value) {
            yaml.set(key, true);
        } else {
            yaml.set(key, null);
        }
        yaml.options().header(HEADER);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + file.getName(), exception);
        }
    }

    private static File file(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }
}
