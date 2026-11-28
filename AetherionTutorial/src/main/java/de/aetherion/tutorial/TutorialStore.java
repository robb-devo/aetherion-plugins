package de.aetherion.tutorial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Where a player stands with the tutorial: one small YAML per player in a folder every backend shares
 * ({@code <shared>/tutorial}). The hub reads it to decide where "play" leads, the isle writes the progress, the main
 * world reads the hand-over.
 *
 * <p>It is not part of the character on purpose: {@code completedOnce} must outlive a character wipe (only somebody
 * who has finished the isle once may skip it).
 */
public final class TutorialStore {

    public enum State {
        /** Never set foot on the isle (or was reset). */
        NEW,
        /** On the isle, somewhere between the bell and the ferry. */
        IN_PROGRESS,
        /** The ferry has cast off: the main world owes this player the arrival. */
        GRADUATING,
        /** Arrived on the main world. */
        DONE
    }

    /** One player's file. Plain fields: read, change, {@link TutorialStore#save}. */
    public static final class Entry {
        public State state = State.NEW;
        /** {@code Step} name the player is at (isle only). */
        public String step = "";
        public String name = "";
        public long started;
        public long finished;
        /** Seconds actually spent on the isle (logged-in time), for the pacing report. */
        public long seconds;
        /** Finished the isle at least once, ever. Survives wipes. */
        public boolean completedOnce;
        /** This run was the short way (a returning player who chose to skip). */
        public boolean skipped;
        public int deaths;
        /**
         * This run is a visit: somebody who already has a character walks the isle again. The isle lets them in
         * without that character (Core guest pass), and the main world owes them nothing afterwards.
         */
        public boolean guest;
        /** Things that happen once per run and must not happen again after a relog ("boosters": the set was handed over). */
        public final java.util.Set<String> flags = new java.util.LinkedHashSet<>();
    }

    private final Plugin plugin;
    private final File dir;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public TutorialStore(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the tutorial store folder " + dir.getAbsolutePath());
        }
    }

    public File directory() {
        return dir;
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    /** True when this player has a file at all. */
    public boolean known(UUID id) {
        return id != null && (cache.containsKey(id) || file(id).isFile());
    }

    /** The player's entry (a fresh NEW one when there is no file). Always read from disk: other backends write too. */
    public Entry load(UUID id) {
        Entry entry = new Entry();
        File file = file(id);
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            entry.state = parse(yaml.getString("state"));
            entry.step = yaml.getString("step", "");
            entry.name = yaml.getString("name", "");
            entry.started = yaml.getLong("started", 0L);
            entry.finished = yaml.getLong("finished", 0L);
            entry.seconds = yaml.getLong("seconds", 0L);
            entry.completedOnce = yaml.getBoolean("completed-once", false);
            entry.skipped = yaml.getBoolean("skipped", false);
            entry.deaths = yaml.getInt("deaths", 0);
            entry.guest = yaml.getBoolean("guest", false);
            entry.flags.addAll(yaml.getStringList("flags"));
        }
        cache.put(id, entry);
        return entry;
    }

    /**
     * True when the file says this player is on a visit that is still running. Reads the file only (no cache):
     * safe on the async pre-login thread.
     */
    public boolean visiting(UUID id) {
        if (id == null) {
            return false;
        }
        File file = file(id);
        if (!file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return yaml.getBoolean("guest", false) && parse(yaml.getString("state")) == State.IN_PROGRESS;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            return false;
        }
    }

    /**
     * True when the file holds a newcomer's ticket that has not been used yet: in progress, not a visit, no step
     * written. File only, like {@link #visiting}.
     */
    public boolean fresh(UUID id) {
        if (id == null) {
            return false;
        }
        File file = file(id);
        if (!file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            String step = yaml.getString("step", "");
            return !yaml.getBoolean("guest", false) && parse(yaml.getString("state")) == State.IN_PROGRESS
                    && (step == null || step.isBlank());
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            return false;
        }
    }

    /** The entry as last loaded or saved here (loads it when this backend has not seen the player yet). */
    public Entry get(UUID id) {
        Entry entry = cache.get(id);
        return entry != null ? entry : load(id);
    }

    /** True when the file names no step yet: the run has not begun on the isle. File only, like {@link #visiting}. */
    public boolean unbegun(UUID id) {
        if (id == null) {
            return false;
        }
        File file = file(id);
        if (!file.isFile()) {
            return true;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            String step = yaml.getString("step", "");
            return step == null || step.isBlank();
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            return false;
        }
    }

    public void save(UUID id, Entry entry) {
        cache.put(id, entry);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("state", entry.state.name().toLowerCase(Locale.ROOT));
        yaml.set("step", entry.step);
        yaml.set("name", entry.name);
        yaml.set("started", entry.started);
        yaml.set("finished", entry.finished);
        yaml.set("seconds", entry.seconds);
        yaml.set("completed-once", entry.completedOnce);
        yaml.set("skipped", entry.skipped);
        yaml.set("deaths", entry.deaths);
        yaml.set("guest", entry.guest);
        yaml.set("flags", new java.util.ArrayList<>(entry.flags));
        File target = file(id);
        File temp = new File(dir, id + ".yml.tmp");
        try {
            Files.writeString(temp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException again) {
                plugin.getLogger().log(Level.WARNING, "Could not save the tutorial state of " + id, again);
            }
        }
    }

    public void forget(UUID id) {
        cache.remove(id);
    }

    private static State parse(String raw) {
        if (raw == null) {
            return State.NEW;
        }
        try {
            return State.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return State.NEW;
        }
    }
}
