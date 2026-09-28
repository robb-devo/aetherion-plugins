package de.aetherion.fishing.isle;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.fishing.AetherionFishing;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Fishing Eldervale progress per player ({@code isle-anglers.yml}): the Angler's Log (species,
 * counts, personal-best weights), discovered waters, trophies, rank, bait — plus the isle-wide
 * heaviest-fish records. Saved on a dirty timer, on quit and on disable.
 */
public final class AnglerProfiles {

    /** One Log line. */
    public static final class Entry {
        long count;
        double bestKg;

        public long count() {
            return count;
        }

        public double bestKg() {
            return bestKg;
        }
    }

    public static final class Profile {
        final Set<String> waters = new LinkedHashSet<>();
        boolean waterfinder;
        final Map<Species, Entry> log = new EnumMap<>(Species.class);
        int trophies;
        int rankPaid;
        int eldermaw;
        int bestStreak;
        long catches;
        final Map<Bait, Integer> bait = new EnumMap<>(Bait.class);
        Bait activeBait;

        public Set<String> waters() {
            return waters;
        }

        public Entry entry(Species species) {
            return log.get(species);
        }

        public boolean caught(Species species) {
            Entry entry = log.get(species);
            return entry != null && entry.count > 0;
        }

        public int speciesCount() {
            int n = 0;
            for (Entry entry : log.values()) {
                if (entry.count > 0) {
                    n++;
                }
            }
            return n;
        }

        public int trophies() {
            return trophies;
        }

        public int eldermaw() {
            return eldermaw;
        }

        public int bestStreak() {
            return bestStreak;
        }

        public long catches() {
            return catches;
        }

        public int charges(Bait kind) {
            return bait.getOrDefault(kind, 0);
        }

        public Bait activeBait() {
            return activeBait != null && charges(activeBait) > 0 ? activeBait : null;
        }
    }

    /** Heaviest ever landed per species — the isle's bragging board. */
    public record Record(UUID holder, String name, double kg) {
    }

    private final AetherionFishing plugin;
    private final File file;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<Species, Record> records = new EnumMap<>(Species.class);
    private final YamlConfiguration disk;
    private boolean dirty;
    private BukkitTask saveTask;

    public AnglerProfiles(AetherionFishing plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "isle-anglers.yml");
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        disk = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        loadRecords();
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 120L, 20L * 120L);
    }

    public Profile of(Player player) {
        return of(player.getUniqueId());
    }

    public Profile of(UUID id) {
        return profiles.computeIfAbsent(id, this::load);
    }

    public void markDirty() {
        dirty = true;
    }

    public Map<Species, Record> records() {
        return records;
    }

    /** True when {@code kg} beats the isle record for {@code species} (and stores it). */
    public boolean offerRecord(Species species, Player player, double kg) {
        Record current = records.get(species);
        if (current != null && current.kg() >= kg) {
            return false;
        }
        records.put(species, new Record(player.getUniqueId(), player.getName(), kg));
        dirty = true;
        return true;
    }

    /** Wipe one player's Fishing Eldervale progress (DEV). */
    public void reset(UUID id) {
        profiles.put(id, new Profile());
        disk.set("players." + id, null);
        dirty = true;
    }

    public void unload(UUID id) {
        Profile profile = profiles.get(id);
        if (profile != null) {
            write(id, profile);
            profiles.remove(id);
            dirty = true;
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        profiles.forEach(this::write);
        disk.set("records", null);
        records.forEach((species, record) -> {
            String path = "records." + species.id();
            disk.set(path + ".uuid", record.holder().toString());
            disk.set(path + ".name", record.name());
            disk.set(path + ".kg", record.kg());
        });
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            AtomicYaml.save(disk, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save isle-anglers.yml", exception);
        }
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        save();
    }

    private Profile load(UUID id) {
        Profile profile = new Profile();
        ConfigurationSection section = disk.getConfigurationSection("players." + id);
        if (section == null) {
            return profile;
        }
        profile.waters.addAll(section.getStringList("waters"));
        profile.waterfinder = section.getBoolean("waterfinder", false);
        ConfigurationSection log = section.getConfigurationSection("log");
        if (log != null) {
            for (String key : log.getKeys(false)) {
                Species species = Species.byId(key);
                if (species == null) {
                    continue;
                }
                Entry entry = new Entry();
                entry.count = log.getLong(key + ".count", 0L);
                entry.bestKg = log.getDouble(key + ".best-kg", 0.0d);
                profile.log.put(species, entry);
            }
        }
        profile.trophies = section.getInt("trophies", 0);
        profile.rankPaid = section.getInt("rank-paid", 0);
        profile.eldermaw = section.getInt("eldermaw", 0);
        profile.bestStreak = section.getInt("best-streak", 0);
        profile.catches = section.getLong("catches", 0L);
        ConfigurationSection bait = section.getConfigurationSection("bait");
        if (bait != null) {
            for (String key : bait.getKeys(false)) {
                Bait kind = Bait.byId(key);
                if (kind != null) {
                    profile.bait.put(kind, Math.max(0, bait.getInt(key)));
                }
            }
        }
        profile.activeBait = Bait.byId(section.getString("active-bait"));
        return profile;
    }

    private void write(UUID id, Profile profile) {
        String path = "players." + id;
        disk.set(path, null);
        ConfigurationSection section = disk.createSection(path);
        section.set("waters", List.copyOf(profile.waters));
        section.set("waterfinder", profile.waterfinder);
        profile.log.forEach((species, entry) -> {
            section.set("log." + species.id() + ".count", entry.count);
            section.set("log." + species.id() + ".best-kg", entry.bestKg);
        });
        section.set("trophies", profile.trophies);
        section.set("rank-paid", profile.rankPaid);
        section.set("eldermaw", profile.eldermaw);
        section.set("best-streak", profile.bestStreak);
        section.set("catches", profile.catches);
        profile.bait.forEach((kind, charges) -> {
            if (charges > 0) {
                section.set("bait." + kind.id(), charges);
            }
        });
        section.set("active-bait", profile.activeBait == null ? null : profile.activeBait.id());
    }

    private void loadRecords() {
        ConfigurationSection section = disk.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Species species = Species.byId(key);
            String uuid = section.getString(key + ".uuid");
            if (species == null || uuid == null) {
                continue;
            }
            try {
                records.put(species, new Record(UUID.fromString(uuid),
                        section.getString(key + ".name", "?"), section.getDouble(key + ".kg")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
