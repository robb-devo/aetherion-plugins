package de.aetherion.items.economy;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ShardService {

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private final AtomicLong mutationEpoch = new AtomicLong();
    private volatile long savedEpoch;

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
        markDirty();
    }

    public boolean take(Player player, long amount) {
        return player != null && take(player.getUniqueId(), amount);
    }

    public boolean take(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return false;
        }
        while (true) {
            Long current = balances.get(playerId);
            if (current == null || current < amount) {
                return false;
            }
            long next = current - amount;
            if (balances.replace(playerId, current, next)) {
                markDirty();
                return true;
            }
        }
    }

    public void save() {
        synchronized (saveLock) {
            long epoch = mutationEpoch.get();
            Map<UUID, Long> snap = new HashMap<>(balances);
            YamlConfiguration config = file.isFile()
                    ? YamlConfiguration.loadConfiguration(file)
                    : new YamlConfiguration();
            snap.forEach((id, amount) -> config.set("players." + id, amount));
            try {
                AtomicYaml.save(config, file, plugin.getLogger());
                if (mutationEpoch.get() == epoch) {
                    savedEpoch = epoch;
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not save shards.yml: " + exception.getMessage());
            }
        }
    }

    public void saveIfDirty() {
        if (mutationEpoch.get() != savedEpoch) {
            save();
        }
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null) {
            return;
        }
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String key = playerId.toString();
        if (config.contains("players." + key)) {
            balances.put(playerId, config.getLong("players." + key));
        }
    }

    private void markDirty() {
        mutationEpoch.incrementAndGet();
    }

    private void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
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
