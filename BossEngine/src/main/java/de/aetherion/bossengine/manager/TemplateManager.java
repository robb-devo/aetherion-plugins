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
        String[] bundled = {
                "aether_colossus.yml", "hollow_lurker.yml", "skuldugery.yml", "mcnugget.yml",
                "bridge_troll.yml", "squidward.yml", "aetherion.yml", "dungeon_sentinel.yml",
                "dungeon_frostbound.yml", "dungeon_aetherion.yml", "sir_balthazar.yml",
                "lobby_cleaner.yml", "sparky.yml", "baron_von_wurm.yml", "insolvent_wither.yml",
                "pathwarden.yml", "ashen_sheath.yml",
                "test_echo.yml", "test_parity.yml", "test_curator.yml", "test_nullspace.yml",
                "test_loadbearing.yml", "test_softlock.yml", "test_heartbeat.yml", "test_broker.yml",
                "test_afterimage.yml", "test_gravity.yml", "test_quiet.yml", "test_petjury.yml"
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
