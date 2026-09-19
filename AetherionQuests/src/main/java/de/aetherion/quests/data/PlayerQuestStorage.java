package de.aetherion.quests.data;

import de.aetherion.quests.model.QuestState;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player quest YAML under {@code players/<uuid>.yml}, with dirty flush.
 * Migrates once from legacy single {@code players.yml}.
 */
public class PlayerQuestStorage {

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, FileConfiguration> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public PlayerQuestStorage(File dataFolder) {
        this(null, dataFolder);
    }

    public PlayerQuestStorage(JavaPlugin plugin, File dataFolder) {
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

    public void saveQuest(UUID uuid, String questId, QuestState state) {
        FileConfiguration config = config(uuid);
        config.set("quests." + questId + ".state", state.name());
        markDirty(uuid);
    }

    public boolean hasStarterKit(UUID uuid, String questId) {
        return config(uuid).getBoolean("quests." + questId + ".starter", false);
    }

    public void markStarterKit(UUID uuid, String questId) {
        config(uuid).set("quests." + questId + ".starter", true);
        markDirty(uuid);
    }

    public boolean hasLanguageChoice(UUID uuid) {
        return config(uuid).getBoolean("language-chosen", false);
    }

    public de.aetherion.quests.lang.LangCode getLanguage(UUID uuid) {
        return de.aetherion.quests.lang.LangCode.parse(config(uuid).getString("language", "en"));
    }

    public void setLanguage(UUID uuid, de.aetherion.quests.lang.LangCode code) {
        if (uuid == null || code == null) {
            return;
        }
        config(uuid).set("language", code.id());
        config(uuid).set("language-chosen", true);
        markDirty(uuid);
    }

    /** -1 means never recorded (pre-backfill player data). */
    public int getAetherXpGranted(UUID uuid, String questId) {
        if (!config(uuid).contains("quests." + questId + ".aetherXpGranted")) {
            return -1;
        }
        return config(uuid).getInt("quests." + questId + ".aetherXpGranted", 0);
    }

    public void setAetherXpGranted(UUID uuid, String questId, int amount) {
        config(uuid).set("quests." + questId + ".aetherXpGranted", Math.max(0, amount));
        markDirty(uuid);
    }

    public QuestState loadQuest(UUID uuid, String questId) {
        String value = config(uuid).getString("quests." + questId + ".state");
        if (value == null) {
            return QuestState.AVAILABLE;
        }
        return QuestState.valueOf(value);
    }

    public void saveProgress(UUID uuid, String questId, String objective, int amount) {
        config(uuid).set("quests." + questId + ".progress." + objective, amount);
        markDirty(uuid);
    }

    public int loadProgress(UUID uuid, String questId, String objective) {
        return config(uuid).getInt("quests." + questId + ".progress." + objective, 0);
    }

    public void loadAllProgress(UUID uuid, String questId, PlayerQuestData data) {
        String path = "quests." + questId + ".progress";
        FileConfiguration config = config(uuid);
        if (!config.contains(path)) {
            return;
        }
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String objective : section.getKeys(false)) {
            data.setProgress(questId, objective, config.getInt(path + "." + objective));
        }
    }

    public void flush() {
        for (UUID uuid : Set.copyOf(dirty)) {
            persist(uuid);
        }
    }

    public void unload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (dirty.contains(uuid) || cache.containsKey(uuid)) {
            persist(uuid);
        }
        cache.remove(uuid);
        dirty.remove(uuid);
    }

    /** Delete all quest progress for a player (disk + cache). */
    public void wipePlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        dirty.remove(uuid);
        cache.remove(uuid);
        File file = file(uuid);
        if (file.exists() && !file.delete()) {
            if (plugin != null) {
                plugin.getLogger().warning("Could not delete quest file: " + file.getName());
            }
        }
        cache.put(uuid, new YamlConfiguration());
        markDirty(uuid);
        persist(uuid);
    }

    private void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    private FileConfiguration config(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFile);
    }

    private FileConfiguration loadFile(UUID uuid) {
        File file = file(uuid);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private void persist(UUID uuid) {
        FileConfiguration config = cache.get(uuid);
        if (config == null) {
            dirty.remove(uuid);
            return;
        }
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            config.save(file(uuid));
            dirty.remove(uuid);
        } catch (IOException exception) {
            if (plugin != null) {
                plugin.getLogger().warning("Quest save failed for " + uuid + ": " + exception.getMessage());
            } else {
                exception.printStackTrace();
            }
        }
    }

    private void migrateLegacy(File dataFolder) {
        File legacy = new File(dataFolder, "players.yml");
        if (!legacy.exists()) {
            return;
        }
        FileConfiguration old = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = old.getConfigurationSection("players");
        if (players == null) {
            renameLegacy(legacy);
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            File target = file(uuid);
            if (target.exists()) {
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            YamlConfiguration next = new YamlConfiguration();
            for (String child : section.getKeys(false)) {
                next.set(child, section.get(child));
            }
            try {
                next.save(target);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        renameLegacy(legacy);
    }

    private void renameLegacy(File legacy) {
        File bak = new File(legacy.getParentFile(), "players.yml.migrated");
        if (!legacy.renameTo(bak) && plugin != null) {
            plugin.getLogger().warning("Could not rename legacy players.yml — delete manually after checking players/.");
        }
    }
}
