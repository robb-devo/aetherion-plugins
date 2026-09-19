package de.aetherion.items.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShardService {

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Long> balances = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public ShardService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shards.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public long get(Player player) {
        return player == null ? 0L : get(player.getUniqueId());
    }

    public long get(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        return balances.getOrDefault(playerId, 0L);
    }

    public String formatted(Player player) {
        return String.format(Locale.US, "%,d", get(player));
    }

    public void add(Player player, long amount) {
        if (player != null) {
            add(player.getUniqueId(), amount);
        }
    }

    public void add(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return;
        }
        balances.merge(playerId, amount, Long::sum);
        dirty = true;
    }

    public boolean take(Player player, long amount) {
        return player != null && take(player.getUniqueId(), amount);
    }

    public boolean take(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return false;
        }
        long current = get(playerId);
        if (current < amount) {
            return false;
        }
        balances.put(playerId, current - amount);
        dirty = true;
        return true;
    }

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        balances.forEach((id, amount) -> config.set("players." + id, amount));
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save shards.yml: " + exception.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
