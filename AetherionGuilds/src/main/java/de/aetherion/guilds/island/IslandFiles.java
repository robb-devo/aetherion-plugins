package de.aetherion.guilds.island;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * One save file per island: {@code islands/p_<uuid>.yml} / {@code islands/g_<uuid>.yml}. Each file holds
 * everything the island factory owns — its structures, belts, unlocks, goals, first-time milestones — under a
 * {@code version} field. Services fill their own section; {@link #flush()} writes only the islands whose
 * content actually changed, each through {@link AtomicYaml} (temp file + atomic replace). No more rewriting
 * every island because one belt moved.
 *
 * <p><b>Two servers, one folder.</b> Each file records which server wrote it and when. If a file on disk was
 * written by someone else after we read it, we do <i>not</i> overwrite it: our copy goes next to it as
 * {@code .conflict-<time>.yml} and the log says so loudly. Islands are meant to run on one server (the
 * island server); this guard only makes sure a mistake can never silently eat an island.
 *
 * <p><b>Upgrade from the old layout.</b> The first start with this class reads the old shared files
 * ({@code island_structures.yml}, {@code island_logistics.yml}, {@code island_goals.yml}), splits them into
 * per-island files, and renames the old ones to {@code *.pre-v2.bak} (kept, never deleted). Every step is
 * logged; a broken entry is logged and skipped, never swallowed.
 */
public final class IslandFiles {

    public static final int VERSION = 2;

    private final JavaPlugin plugin;
    private final File folder;
    private final String server;
    private final Map<String, YamlConfiguration> docs = new HashMap<>();
    private final Map<String, String> written = new HashMap<>();
    private final Map<String, Long> readAt = new HashMap<>();

    public IslandFiles(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "islands");
        String id = plugin.getConfig().getString("server-id", "");
        this.server = id == null || id.isBlank() ? "port-" + Bukkit.getPort() : id;
    }

    // ------------------------------------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------------------------------------

    /** Reads every island file (and splits the old shared files on the first start). */
    public void load() {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Could not create " + folder + " — island saves will fail.");
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") && !name.contains(".conflict-"));
        if (files != null) {
            for (File file : files) {
                String key = keyOf(file.getName());
                if (key == null) {
                    continue;
                }
                AtomicYaml.recoverTemp(file, plugin.getLogger());
                try {
                    YamlConfiguration doc = new YamlConfiguration();
                    doc.load(file);
                    int version = doc.getInt("version", 0);
                    if (version > VERSION) {
                        plugin.getLogger().warning("Island file " + file.getName() + " is version " + version
                                + " (this plugin knows " + VERSION + "): loaded read-carefully, unknown keys are kept.");
                    }
                    docs.put(key, doc);
                    // compare like flush() does (without the server/saved stamps), so an unchanged island
                    // isn't rewritten on the first save after a start
                    YamlConfiguration bare = new YamlConfiguration();
                    bare.loadFromString(doc.saveToString());
                    bare.set("version", VERSION);
                    bare.set("server", null);
                    bare.set("saved", null);
                    written.put(key, bare.saveToString());
                    readAt.put(key, file.lastModified());
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, "Island file " + file.getName() + " could not be read."
                            + " It is left untouched on disk; that island starts empty this run.", exception);
                    // keep it from being overwritten by an empty doc
                    written.put(key, "\u0000unreadable");
                    readAt.put(key, Long.MAX_VALUE);
                }
            }
        }
        if (docs.isEmpty()) {
            for (String name : LEGACY) {
                if (new File(plugin.getDataFolder(), name).exists()) {
                    splitting = true;
                }
            }
        }
        plugin.getLogger().info("Island saves: " + docs.size() + " islands in " + folder.getName() + "/ (v" + VERSION
                + ", server " + server + ")" + (splitting ? "; splitting the old shared files now." : "."));
    }

    /** The old shared files, split into per-island files on the first start with this layout. */
    public static final String[] LEGACY = {"island_structures.yml", "island_logistics.yml", "island_goals.yml"};

    private boolean splitting;

    /** True on the one start that reads the old shared files (no island files yet, old files present). */
    public boolean splitting() {
        return splitting;
    }

    /** After every service read the old files and filled its sections: write them out, retire the old files. */
    public void finishSplit() {
        if (!splitting) {
            return;
        }
        int written = flush();
        plugin.getLogger().info("Island saves: split the old shared files into " + written + " island files.");
        for (String name : LEGACY) {
            retireLegacy(name);
        }
        splitting = false;
    }

    /** The old shared file, for the one-time split (null when it isn't there). */
    public YamlConfiguration legacy(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            return null;
        }
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        return YamlConfiguration.loadConfiguration(file);
    }

    /** After the split was flushed: the old file is renamed, never deleted. */
    public void retireLegacy(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            return;
        }
        File backup = new File(plugin.getDataFolder(), name + ".pre-v2.bak");
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Island saves: old " + name + " kept as " + backup.getName() + ".");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not rename " + name + " after splitting it; it stays"
                    + " where it is (the per-island files are the truth now).", exception);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the per-island document
    // ------------------------------------------------------------------------------------------------

    public Collection<String> keys() {
        return new ArrayList<>(docs.keySet());
    }

    /** The island's document (created empty on first use). */
    public YamlConfiguration doc(String hostKey) {
        return docs.computeIfAbsent(hostKey, k -> new YamlConfiguration());
    }

    public YamlConfiguration existing(String hostKey) {
        return docs.get(hostKey);
    }

    /** Replace one section of an island's file with fresh content (services own their sections). */
    public ConfigurationSection replace(String hostKey, String section) {
        YamlConfiguration doc = doc(hostKey);
        doc.set(section, null);
        return doc.createSection(section);
    }

    /** The island is gone: its file moves to {@code islands/removed/} (kept for a rollback, never deleted). */
    public void remove(String hostKey) {
        docs.remove(hostKey);
        written.remove(hostKey);
        readAt.remove(hostKey);
        File file = fileOf(hostKey);
        if (!file.exists()) {
            return;
        }
        File removed = new File(folder, "removed");
        if (!removed.exists() && !removed.mkdirs()) {
            plugin.getLogger().warning("Could not create " + removed);
            return;
        }
        try {
            Files.move(file.toPath(), new File(removed, file.getName().replace(".yml", "-" + System.currentTimeMillis()
                    + ".yml")).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not move the save of removed island " + hostKey, exception);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // writing
    // ------------------------------------------------------------------------------------------------

    /** Writes every island whose content changed since it was last written. Returns how many were written. */
    public int flush() {
        int count = 0;
        for (Map.Entry<String, YamlConfiguration> entry : docs.entrySet()) {
            String key = entry.getKey();
            YamlConfiguration doc = entry.getValue();
            String before = written.get(key);
            if (before != null && before.startsWith("\u0000")) {
                continue; // unreadable on load: never overwrite it
            }
            doc.set("version", VERSION);
            doc.set("server", null);
            doc.set("saved", null);
            String body = doc.saveToString();
            if (body.equals(before)) {
                continue;
            }
            File file = fileOf(key);
            Long seen = readAt.get(key);
            if (file.exists() && seen != null && file.lastModified() > seen + 1000L) {
                File conflict = new File(folder, file.getName().replace(".yml", ".conflict-" + System.currentTimeMillis() + ".yml"));
                plugin.getLogger().severe("Island " + key + ": its save was changed on disk by someone else since this"
                        + " server read it (another server sharing the folder?). NOT overwriting it; this server's copy"
                        + " is in " + conflict.getName() + ". Check which server should host this island.");
                try {
                    stamp(doc);
                    AtomicYaml.save(doc, conflict, plugin.getLogger());
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not write " + conflict.getName(), exception);
                }
                written.put(key, body);
                continue;
            }
            try {
                stamp(doc);
                AtomicYaml.save(doc, file, plugin.getLogger());
                written.put(key, body);
                readAt.put(key, file.lastModified());
                count++;
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save island " + key + " (" + file.getName() + ")", exception);
            }
        }
        return count;
    }

    private void stamp(YamlConfiguration doc) {
        doc.set("server", server);
        doc.set("saved", System.currentTimeMillis());
    }

    private File fileOf(String hostKey) {
        return new File(folder, hostKey.replace(':', '_') + ".yml");
    }

    private static String keyOf(String fileName) {
        if (fileName.length() < 4 || fileName.charAt(1) != '_') {
            return null;
        }
        String key = fileName.substring(0, fileName.length() - 4).replaceFirst("_", ":");
        return IslandHost.parse(key) == null ? null : key;
    }

    public List<String> describe() {
        return List.of("islands: " + docs.size(), "server: " + server, "version: " + VERSION);
    }
}
