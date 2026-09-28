package de.aetherion.bossengine.helios.world;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Instance slots in the Helios world and the crash journal that guards them.
 *
 * <p>Every state change is written atomically to {@code helios/state.yml} before the world is touched.
 * On enable, every slot that is not FREE is treated as dirty (the server died mid-fight, mid-build or
 * mid-restore): it gets cleared completely and freed. The players listed there are also released on
 * their next login (their return point lives in their own player data too; see {@code Participants}).
 */
public final class ArenaSlots {

    public enum State { FREE, PREPARING, ACTIVE, RESTORING }

    public static final class Slot {
        final int index;
        State state = State.FREE;
        long since;
        final List<UUID> players = new ArrayList<>();

        Slot(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }

        public State state() {
            return state;
        }

        public List<UUID> players() {
            return players;
        }
    }

    private final File file;
    private final Logger log;
    private final Map<Integer, Slot> slots = new TreeMap<>();

    public ArenaSlots(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Loads the journal. @return slots left dirty by a crash (anything not FREE) */
    public List<Slot> load() {
        slots.clear();
        AtomicYaml.recoverTemp(file, log);
        List<Slot> dirty = new ArrayList<>();
        if (!file.isFile()) {
            return dirty;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("slots");
        if (root == null) {
            return dirty;
        }
        for (String key : root.getKeys(false)) {
            try {
                int idx = Integer.parseInt(key);
                Slot s = new Slot(idx);
                s.state = State.valueOf(root.getString(key + ".state", "FREE"));
                s.since = root.getLong(key + ".since", 0L);
                for (String id : root.getStringList(key + ".players")) {
                    try {
                        s.players.add(UUID.fromString(id));
                    } catch (IllegalArgumentException ignored) {
                        // corrupt id: skip it
                    }
                }
                slots.put(idx, s);
                if (s.state != State.FREE) {
                    dirty.add(s);
                }
            } catch (IllegalArgumentException ignored) {
                // unknown state or index: the slot is re-cleared on the next claim anyway
            }
        }
        return dirty;
    }

    /** Claims the lowest free slot below {@code max}. @return the index or -1 */
    public int claim(int max, Collection<UUID> players) {
        for (int i = 0; i < max; i++) {
            Slot s = slots.computeIfAbsent(i, Slot::new);
            if (s.state == State.FREE) {
                s.state = State.PREPARING;
                s.since = System.currentTimeMillis();
                s.players.clear();
                s.players.addAll(players);
                save();
                return i;
            }
        }
        return -1;
    }

    public void set(int index, State state) {
        Slot s = slots.computeIfAbsent(index, Slot::new);
        s.state = state;
        s.since = System.currentTimeMillis();
        if (state == State.FREE) {
            s.players.clear();
        }
        save();
    }

    public void players(int index, Collection<UUID> players) {
        Slot s = slots.computeIfAbsent(index, Slot::new);
        s.players.clear();
        s.players.addAll(players);
        save();
    }

    public int busy() {
        int n = 0;
        for (Slot s : slots.values()) {
            if (s.state != State.FREE) {
                n++;
            }
        }
        return n;
    }

    public Collection<Slot> all() {
        return slots.values();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Helios Requiem instance journal. Written by the plugin; do not edit while the server runs."));
        for (Slot s : slots.values()) {
            String k = "slots." + s.index;
            yaml.set(k + ".state", s.state.name());
            yaml.set(k + ".since", s.since);
            List<String> ids = new ArrayList<>();
            for (UUID id : s.players) {
                ids.add(id.toString());
            }
            yaml.set(k + ".players", ids);
        }
        try {
            AtomicYaml.save(yaml, file, log);
        } catch (IOException e) {
            log.warning("[Helios] Could not write journal: " + e.getMessage());
        }
    }
}
