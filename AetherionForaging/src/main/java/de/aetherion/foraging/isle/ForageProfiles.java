package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Foraging Eldervale progress per player ({@code forage-isle-players.yml}) plus the isle-wide records
 * (largest fell per district, heaviest find per kind). Serialised on the main thread, written off it,
 * on a dirty timer, on quit and on disable.
 */
public final class ForageProfiles {

    public record Record(String name, UUID holder, int value, String note) {
    }

    private final AetherionForaging plugin;
    private final File file;
    private final Map<UUID, ForageProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Record> records = new LinkedHashMap<>();
    private boolean recordsDirty;
    private final Object writeLock = new Object();

    public ForageProfiles(AetherionForaging plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "forage-isle-players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    profiles.put(id, ForageProfile.read(id, players.getConfigurationSection(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection rec = yaml.getConfigurationSection("records");
        if (rec != null) {
            for (String key : rec.getKeys(false)) {
                ConfigurationSection r = rec.getConfigurationSection(key);
                if (r == null) {
                    continue;
                }
                UUID holder = null;
                try {
                    holder = UUID.fromString(r.getString("uuid", ""));
                } catch (IllegalArgumentException ignored) {
                }
                records.put(key, new Record(r.getString("name", "?"), holder, r.getInt("value"), r.getString("note", "")));
            }
        }
        plugin.getLogger().info("Foraging Eldervale: " + profiles.size() + " forager profiles, " + records.size() + " records.");
    }

    public ForageProfile get(Player player) {
        ForageProfile profile = profiles.computeIfAbsent(player.getUniqueId(), ForageProfile::new);
        if (!player.getName().equals(profile.name)) {
            profile.name = player.getName();
            profile.dirty = true;
        }
        return profile;
    }

    public ForageProfile peek(UUID id) {
        return profiles.get(id);
    }

    public Collection<ForageProfile> all() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public void wipe(UUID id) {
        profiles.remove(id);
        records.entrySet().removeIf(e -> id.equals(e.getValue().holder()));
        recordsDirty = true;
        saveAsync(true);
    }

    public Map<String, Record> records() {
        return Collections.unmodifiableMap(records);
    }

    public Record record(String key) {
        return records.get(key);
    }

    /** Sets the record when {@code value} beats it. Returns true for a new record. */
    public boolean offerRecord(String key, Player player, int value, String note) {
        Record current = records.get(key);
        if (current != null && current.value() >= value) {
            return false;
        }
        records.put(key, new Record(player.getName(), player.getUniqueId(), value, note == null ? "" : note));
        recordsDirty = true;
        return true;
    }

    /** Dirty timer: only writes when something changed. */
    public void saveAsync(boolean force) {
        boolean any = force || recordsDirty;
        for (ForageProfile profile : profiles.values()) {
            any |= profile.dirty;
        }
        if (!any) {
            return;
        }
        String text = serialise();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(text));
    }

    public void saveNow() {
        write(serialise());
    }

    private String serialise() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ForageProfile profile : profiles.values()) {
            profile.write(yaml.createSection("players." + profile.id));
            profile.dirty = false;
        }
        for (Map.Entry<String, Record> e : records.entrySet()) {
            ConfigurationSection r = yaml.createSection("records." + e.getKey());
            Record rec = e.getValue();
            r.set("name", rec.name());
            r.set("uuid", rec.holder() == null ? "" : rec.holder().toString());
            r.set("value", rec.value());
            r.set("note", rec.note());
        }
        recordsDirty = false;
        return yaml.saveToString();
    }

    private void write(String text) {
        synchronized (writeLock) {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists() && !dir.mkdirs()) {
                    return;
                }
                File tmp = new File(dir, file.getName() + ".tmp");
                Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not save forage-isle-players.yml", ex);
            }
        }
    }
}
