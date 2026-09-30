package de.aetherion.core.playtime;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Per-player YAML under the wipe-exempt playtime directory.
 * Only the backend that has the player online writes that file, so a shared
 * Crafty folder can be used by Hub and the MMO backends together.
 */
public final class PlaytimeStore {

    private final File directory;
    private final Logger log;
    private final LongSupplier clock;
    private final Object ioLock = new Object();
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mtimes = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PlaytimeStore(File directory, Logger log) {
        this(directory, log, System::nanoTime);
    }

    PlaytimeStore(File directory, Logger log, LongSupplier clock) {
        this.directory = directory;
        this.log = log == null ? Logger.getLogger("AetherionPlaytime") : log;
        this.clock = clock == null ? System::nanoTime : clock;
    }

    public void open() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create playtime directory: " + directory.getAbsolutePath());
        }
        synchronized (ioLock) {
            loadAllLocked();
        }
    }

    public File directory() {
        return directory;
    }

    public void join(UUID playerId, String name) {
        if (playerId == null) {
            return;
        }
        synchronized (ioLock) {
            if (sessions.containsKey(playerId)) {
                flushLocked(playerId, clock.getAsLong());
            }
            mtimes.remove(playerId);
            Profile disk = readIfChangedLocked(playerId);
            long base = disk == null ? 0L : Math.max(0L, disk.seconds);
            String kept = preferName(name, disk);
            sessions.put(playerId, new Session(base, clock.getAsLong(), kept));
            profiles.put(playerId, new Profile(base, kept));
        }
    }

    public void quit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (ioLock) {
            flushLocked(playerId, clock.getAsLong());
            sessions.remove(playerId);
        }
    }

    public void flushOnline() {
        synchronized (ioLock) {
            long mark = clock.getAsLong();
            for (UUID playerId : List.copyOf(sessions.keySet())) {
                flushLocked(playerId, mark);
            }
        }
    }

    public long seconds(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        synchronized (ioLock) {
            return secondsLocked(playerId);
        }
    }

    public String name(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        synchronized (ioLock) {
            return nameLocked(playerId);
        }
    }

    /**
     * @return previous seconds, or {@code -1} when the zeroed total could not be saved
     */
    public long reset(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        synchronized (ioLock) {
            long previous = secondsLocked(playerId);
            Session session = sessions.get(playerId);
            String name = session != null ? session.name : nameLocked(playerId);
            if (!writeLocked(playerId, 0L, name)) {
                return -1L;
            }
            if (session != null) {
                session.baseSeconds = 0L;
                session.startedNanos = clock.getAsLong();
            }
            return previous;
        }
    }

    public UUID findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        synchronized (ioLock) {
            UUID hit = matchLocked(name);
            if (hit != null) {
                return hit;
            }
            loadAllLocked();
            return matchLocked(name);
        }
    }

    public List<String> knownNames() {
        synchronized (ioLock) {
            List<String> names = new ArrayList<>();
            for (Profile profile : profiles.values()) {
                if (profile.name != null && !profile.name.isBlank()) {
                    names.add(profile.name);
                }
            }
            return names;
        }
    }

    private long secondsLocked(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session != null) {
            return session.total(clock.getAsLong());
        }
        Profile profile = readIfChangedLocked(playerId);
        return profile == null ? 0L : Math.max(0L, profile.seconds);
    }

    private String nameLocked(UUID playerId) {
        Profile profile = profiles.get(playerId);
        if (profile != null && profile.name != null && !profile.name.isBlank()) {
            return profile.name;
        }
        Session session = sessions.get(playerId);
        return session == null ? null : session.name;
    }

    private void flushLocked(UUID playerId, long mark) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        long total = session.total(mark);
        if (!writeLocked(playerId, total, session.name)) {
            return;
        }
        session.baseSeconds = total;
        session.startedNanos = mark;
    }

    private boolean writeLocked(UUID playerId, long seconds, String name) {
        long safe = Math.max(0L, seconds);
        String kept = name;
        if (kept == null || kept.isBlank()) {
            Profile previous = profiles.get(playerId);
            kept = previous == null ? null : previous.name;
        }
        File file = fileFor(playerId);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("seconds", safe);
        if (kept != null && !kept.isBlank()) {
            yaml.set("name", kept);
        }
        try {
            AtomicYaml.save(yaml, file, log);
        } catch (IOException exception) {
            log.warning("Could not save playtime for " + playerId + ": " + exception.getMessage());
            return false;
        }
        profiles.put(playerId, new Profile(safe, kept));
        mtimes.put(playerId, file.lastModified());
        return true;
    }

    private Profile readIfChangedLocked(UUID playerId) {
        File file = fileFor(playerId);
        AtomicYaml.recoverTemp(file, log);
        if (!file.isFile()) {
            return profiles.get(playerId);
        }
        long modified = file.lastModified();
        Profile cached = profiles.get(playerId);
        Long seen = mtimes.get(playerId);
        if (cached != null && seen != null && seen.longValue() == modified) {
            return cached;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long seconds = Math.max(0L, yaml.getLong("seconds", 0L));
        String name = yaml.getString("name");
        if (name != null && name.isBlank()) {
            name = null;
        }
        Profile loaded = new Profile(seconds, name);
        profiles.put(playerId, loaded);
        mtimes.put(playerId, modified);
        return loaded;
    }

    private void loadAllLocked() {
        File[] files = directory.listFiles((dir, filename) -> filename.endsWith(".yml") && !filename.endsWith(".tmp"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String filename = file.getName();
            String base = filename.substring(0, filename.length() - 4);
            UUID playerId;
            try {
                playerId = UUID.fromString(base);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (sessions.containsKey(playerId)) {
                continue;
            }
            mtimes.remove(playerId);
            readIfChangedLocked(playerId);
        }
    }

    private UUID matchLocked(String name) {
        UUID bestExact = null;
        long bestExactSeconds = -1L;
        UUID bestIgnore = null;
        long bestIgnoreSeconds = -1L;
        int ignoreMatches = 0;
        long mark = clock.getAsLong();
        for (Map.Entry<UUID, Profile> entry : profiles.entrySet()) {
            String known = entry.getValue().name;
            if (known == null || known.isBlank()) {
                continue;
            }
            Session session = sessions.get(entry.getKey());
            long seconds = session == null ? entry.getValue().seconds : session.total(mark);
            if (known.equals(name)) {
                if (seconds > bestExactSeconds) {
                    bestExact = entry.getKey();
                    bestExactSeconds = seconds;
                }
            } else if (known.equalsIgnoreCase(name)) {
                ignoreMatches++;
                if (seconds > bestIgnoreSeconds) {
                    bestIgnore = entry.getKey();
                    bestIgnoreSeconds = seconds;
                }
            }
        }
        if (bestExact != null) {
            return bestExact;
        }
        if (ignoreMatches == 1) {
            return bestIgnore;
        }
        return null;
    }

    private File fileFor(UUID playerId) {
        return new File(directory, playerId + ".yml");
    }

    private static String preferName(String incoming, Profile disk) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return disk == null ? null : disk.name;
    }

    static long elapsedSeconds(long startedNanos, long nowNanos) {
        if (nowNanos <= startedNanos) {
            return 0L;
        }
        return (nowNanos - startedNanos) / 1_000_000_000L;
    }

    private static final class Profile {
        private final long seconds;
        private final String name;

        private Profile(long seconds, String name) {
            this.seconds = seconds;
            this.name = name;
        }
    }

    private static final class Session {
        private long baseSeconds;
        private long startedNanos;
        private final String name;

        private Session(long baseSeconds, long startedNanos, String name) {
            this.baseSeconds = baseSeconds;
            this.startedNanos = startedNanos;
            this.name = name;
        }

        private long total(long nowNanos) {
            return Math.max(0L, baseSeconds + elapsedSeconds(startedNanos, nowNanos));
        }
    }
}
