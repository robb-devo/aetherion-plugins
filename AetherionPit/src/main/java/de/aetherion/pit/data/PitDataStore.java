package de.aetherion.pit.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PitDataStore {

    public record Stats(int level, int xp, int gold) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Mutable> cache = new ConcurrentHashMap<>();

    public PitDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public Stats of(Player player) {
        Mutable m = cache.computeIfAbsent(player.getUniqueId(), id -> new Mutable(1, 0, 0));
        return new Stats(m.level, m.xp, m.gold);
    }

    public Mutable mutable(UUID id) {
        return cache.computeIfAbsent(id, u -> new Mutable(1, 0, 0));
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Mutable> entry : cache.entrySet()) {
            String key = entry.getKey().toString();
            Mutable m = entry.getValue();
            yaml.set(key + ".level", m.level);
            yaml.set(key + ".xp", m.xp);
            yaml.set(key + ".gold", m.gold);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save pit players: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                cache.put(id, new Mutable(
                        Math.max(1, yaml.getInt(key + ".level", 1)),
                        Math.max(0, yaml.getInt(key + ".xp", 0)),
                        Math.max(0, yaml.getInt(key + ".gold", 0))
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static final class Mutable {
        public int level;
        public int xp;
        public int gold;

        Mutable(int level, int xp, int gold) {
            this.level = level;
            this.xp = xp;
            this.gold = gold;
        }
    }
}
