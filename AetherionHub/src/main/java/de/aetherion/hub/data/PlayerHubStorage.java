package de.aetherion.hub.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player hub YAML under {@code players/<uuid>.yml}. Migrates legacy {@code players.yml}.
 */
public final class PlayerHubStorage {

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerHubData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public PlayerHubStorage(File dataFolder) {
        this(null, dataFolder);
    }

    public PlayerHubStorage(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.folder = new File(dataFolder, "players");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        migrateLegacy(dataFolder);
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, 20L * 60, 20L * 60);
        }
    }

    public void unload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PlayerHubData data = cache.remove(uuid);
        if (data != null) {
            write(data);
            dirty.remove(uuid);
        }
    }

    public PlayerHubData load(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::read);
    }

    public void save(PlayerHubData data) {
        if (data == null) {
            return;
        }
        cache.put(data.uuid(), data);
        dirty.add(data.uuid());
    }

    public void saveAll() {
        for (PlayerHubData data : cache.values()) {
            write(data);
        }
        dirty.clear();
    }

    public void flush() {
        for (UUID uuid : Set.copyOf(dirty)) {
            PlayerHubData data = cache.get(uuid);
            if (data != null) {
                write(data);
            }
            dirty.remove(uuid);
        }
    }

    private PlayerHubData read(UUID uuid) {
        PlayerHubData data = new PlayerHubData(uuid);
        File file = file(uuid);
        if (!file.exists()) {
            return data;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.setSelectedId(config.getString("selected"));
        data.setHintShown(config.getBoolean("hint-shown", false));
        data.unlocked().addAll(config.getStringList("unlocked"));
        // Drop retired megamap camps from unlock lists.
        boolean scrubbed = data.unlocked().removeIf(id ->
                id == null || !de.aetherion.hub.service.HubService.isOriginSpawn(id));
        if (data.unlocked().remove("ruins")) {
            data.unlocked().add("mines");
            scrubbed = true;
        }
        if (scrubbed) {
            dirty.add(uuid);
        }
        if ("ruins".equalsIgnoreCase(data.selectedId())) {
            data.setSelectedId("mines");
            dirty.add(uuid);
        }
        // Harbour is the real starter — migrate legacy Village selection.
        if ("spawn".equalsIgnoreCase(data.selectedId())
                || (data.selectedId() != null
                && !de.aetherion.hub.service.HubService.isOriginSpawn(data.selectedId()))) {
            data.setSelectedId("harbour");
            dirty.add(uuid);
        }
        if (data.unlocked().contains("spawn") && !data.unlocked().contains("harbour")) {
            data.unlocked().add("harbour");
            dirty.add(uuid);
        }
        return data;
    }

    private void write(PlayerHubData data) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("selected", data.selectedId());
        config.set("unlocked", List.copyOf(data.unlocked()));
        config.set("hint-shown", data.hintShown());
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            config.save(file(data.uuid()));
        } catch (IOException exception) {
            if (plugin != null) {
                plugin.getLogger().warning("Hub save failed for " + data.uuid() + ": " + exception.getMessage());
            } else {
                exception.printStackTrace();
            }
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private void migrateLegacy(File dataFolder) {
        File legacy = new File(dataFolder, "players.yml");
        if (!legacy.exists()) {
            return;
        }
        FileConfiguration old = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = old.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (file(uuid).exists()) {
                    continue;
                }
                PlayerHubData data = new PlayerHubData(uuid);
                String path = "players." + uuid;
                data.setSelectedId(old.getString(path + ".selected"));
                data.setHintShown(old.getBoolean(path + ".hint-shown", false));
                data.unlocked().addAll(old.getStringList(path + ".unlocked"));
                write(data);
            }
        }
        File bak = new File(dataFolder, "players.yml.migrated");
        if (!legacy.renameTo(bak) && plugin != null) {
            plugin.getLogger().warning("Could not rename legacy hub players.yml.");
        }
    }
}
