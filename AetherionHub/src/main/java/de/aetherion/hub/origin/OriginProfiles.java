package de.aetherion.hub.origin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code plugins/AetherionHub/origin-players/<uuid>.yml}, cached while the player is online. */
public final class OriginProfiles {

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, OriginProfile> cache = new ConcurrentHashMap<>();

    OriginProfiles(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "origin-players");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public OriginProfile get(Player player) {
        return get(player.getUniqueId());
    }

    public OriginProfile get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    private OriginProfile read(UUID id) {
        OriginProfile profile = new OriginProfile(id);
        File file = file(id);
        if (file.isFile()) {
            profile.read(YamlConfiguration.loadConfiguration(file));
        }
        return profile;
    }

    private void write(OriginProfile profile) {
        try {
            profile.write().save(file(profile.id));
            profile.dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Origin: could not save profile " + profile.id + ": " + exception.getMessage());
        }
    }

    /** Autosave: only profiles that changed. */
    void flush() {
        for (OriginProfile profile : cache.values()) {
            if (profile.dirty) {
                write(profile);
            }
        }
    }

    void unload(UUID id) {
        OriginProfile profile = cache.remove(id);
        if (profile != null && profile.dirty) {
            write(profile);
        }
    }

    void saveAll() {
        for (OriginProfile profile : cache.values()) {
            write(profile);
        }
    }

    /** DEV reset / full player wipe: forget and delete. */
    public void wipe(UUID id) {
        cache.remove(id);
        File file = file(id);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Origin: could not delete " + file.getName());
        }
    }

    public int cached() {
        return cache.size();
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }
}
