package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * One boot after this build applies Shabby Mine and Eldervale mining passes.
 * Flags live in the plugin folder so a later restart does not redo the world.
 * Manual commands still run on demand.
 */
public final class WorldgenOnce {

    private WorldgenOnce() {
    }

    public static void schedule(AetherionItems plugin) {
        File file = new File(plugin.getDataFolder(), "worldgen-once.yml");
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        boolean shabby = !yaml.getBoolean("shabby-mine-applied", false);
        boolean eldervale = !yaml.getBoolean("eldervale-ores-applied", false);
        if (!shabby && !eldervale) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Runnable elder = () -> {
                if (!eldervale) {
                    return;
                }
                if (plugin.getAreas() == null
                        || plugin.getAreas().zonesOf(AreaType.ELDERVALE).isEmpty()) {
                    plugin.getLogger().info("Eldervale ore pass waiting — no Eldervale area yet.");
                    return;
                }
                plugin.getLogger().info("Eldervale mining pass (lights + ore hotspots)…");
                EldervaleMinePrep.run(Bukkit.getConsoleSender(), () -> mark(plugin, file, "eldervale-ores-applied"));
            };
            if (!shabby) {
                elder.run();
                return;
            }
            if (plugin.getAreas() == null
                    || plugin.getAreas().zonesOf(AreaType.SHABBY_MINE).isEmpty()) {
                plugin.getLogger().info("Shabby Mine ore pass waiting — no Shabby Mine area yet.");
                elder.run();
                return;
            }
            plugin.getLogger().info("Shabby Mine ore pass (coal / iron / copper veins)…");
            ShabbyMinePrep.run(Bukkit.getConsoleSender(), () -> {
                mark(plugin, file, "shabby-mine-applied");
                elder.run();
            });
        }, 200L);
    }

    private static void mark(AetherionItems plugin, File file, String key) {
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set(key, true);
        try {
            File folder = file.getParentFile();
            if (folder != null) {
                folder.mkdirs();
            }
            yaml.save(file);
            plugin.getLogger().info("Worldgen once: " + key);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save worldgen-once.yml: " + exception.getMessage());
        }
    }
}
