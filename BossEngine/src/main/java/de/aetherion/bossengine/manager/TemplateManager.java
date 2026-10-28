package de.aetherion.bossengine.manager;

import de.aetherion.bossengine.config.YamlTemplateLoader;
import de.aetherion.bossengine.model.BossTemplate;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateManager {

    private final JavaPlugin plugin;
    private final YamlTemplateLoader loader;
    private final Map<String, BossTemplate> templates = new ConcurrentHashMap<>();

    public TemplateManager(JavaPlugin plugin, YamlTemplateLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    public void reload() {
        templates.clear();
        File folder = new File(plugin.getDataFolder(), "bosses");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        // Active bosses only; the ones retired 10-28 are no longer in the jar (see BossEngine#onEnable).
        String[] bundled = {
                "hollow_lurker.yml", "skuldugery.yml", "mcnugget.yml", "bridge_troll.yml",
                "dungeon_sentinel.yml", "dungeon_frostbound.yml", "dungeon_aetherion.yml",
                "pathwarden.yml", "helios_herald.yml", "helios_requiem.yml"
        };
        for (String name : bundled) {
            File target = new File(folder, name);
            if (!target.exists()) {
                plugin.saveResource("bosses/" + name, false);
            }
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                BossTemplate template = loader.load(file);
                templates.put(template.getId().toLowerCase(Locale.ROOT), template);
                plugin.getLogger().info("Loaded boss template: " + template.getId());
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to load boss template " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    public Optional<BossTemplate> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<BossTemplate> getAll() {
        return Collections.unmodifiableCollection(templates.values());
    }
}
