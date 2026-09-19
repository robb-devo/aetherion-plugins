package de.aetherion.beta.data;

import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Milestone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BetaStore {

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, BetaPlayerData> cache = new ConcurrentHashMap<>();

    public BetaStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create beta players folder.");
        }
    }

    public BetaPlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    public Collection<BetaPlayerData> cached() {
        return cache.values();
    }

    /** Loads every saved UUID into cache so the admin board sees offline testers too. */
    public void loadAllFromDisk() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                get(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void unload(UUID uuid) {
        BetaPlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    public void reset(UUID uuid) {
        cache.remove(uuid);
        File file = fileOf(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete beta data file for " + uuid);
        }
        // Fresh empty entry only if they are online, so GUI stays consistent
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) {
            BetaPlayerData fresh = get(uuid);
            fresh.setName(online.getName());
            fresh.startSession();
            save(fresh);
        }
    }

    public void saveDirty() {
        for (BetaPlayerData data : cache.values()) {
            if (data.dirty()) {
                save(data);
            }
        }
    }

    public void saveAll() {
        for (BetaPlayerData data : cache.values()) {
            save(data);
        }
    }

    private BetaPlayerData load(UUID uuid) {
        BetaPlayerData data = new BetaPlayerData(uuid);
        File file = fileOf(uuid);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        data.setName(yaml.getString("name", ""));
        data.setLang(BetaLang.parse(yaml.getString("lang")));
        data.setBookGiven(yaml.getBoolean("book-given", false));
        data.setSubmitted(yaml.getBoolean("submitted", false));
        data.setFirstSeenMs(yaml.getLong("first-seen", data.firstSeenMs()));
        data.setPlayedMs(yaml.getLong("played-ms", 0L));
        data.touchSeen();
        data.clearDirty();

        ConfigurationSection milestones = yaml.getConfigurationSection("milestones");
        if (milestones != null) {
            for (String key : milestones.getKeys(false)) {
                Milestone milestone;
                try {
                    milestone = Milestone.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (milestones.getBoolean(key + ".done", milestones.getBoolean(key, false))) {
                    data.mark(milestone, milestones.getBoolean(key + ".auto", false));
                }
            }
        }
        ConfigurationSection answers = yaml.getConfigurationSection("answers");
        if (answers != null) {
            for (String key : answers.getKeys(false)) {
                data.answer(key, answers.getString(key, ""));
            }
        }
        data.clearDirty();
        return data;
    }

    public void save(BetaPlayerData data) {
        if (data == null) {
            return;
        }
        File file = fileOf(data.uuid());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", data.name());
        yaml.set("lang", data.lang() == null ? null : data.lang().name());
        yaml.set("book-given", data.bookGiven());
        yaml.set("submitted", data.submitted());
        yaml.set("first-seen", data.firstSeenMs());
        yaml.set("last-seen", data.lastSeenMs());
        yaml.set("played-ms", data.playedMs());
        for (Milestone milestone : Milestone.values()) {
            String path = "milestones." + milestone.name();
            yaml.set(path + ".done", data.isDone(milestone));
            yaml.set(path + ".auto", data.isAuto(milestone));
        }
        for (Map.Entry<String, String> entry : data.answers().entrySet()) {
            yaml.set("answers." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
            data.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to save beta data for " + data.uuid(), exception);
        }
    }

    private File fileOf(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}
