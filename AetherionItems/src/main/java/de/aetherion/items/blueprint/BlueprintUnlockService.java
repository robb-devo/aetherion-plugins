package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surveyor blueprint hunt + collection.
 * Hunt unlocks when the player first speaks with the Surveyor.
 * Collected = found (troll drop) / stamped in the Surveyor desk.
 */
public final class BlueprintUnlockService {

    public static final String VEIN_SIPHON = BlueprintKind.VEIN_SIPHON.id();

    private final AetherionItems plugin;
    private final File file;
    private final Set<UUID> huntEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deskReady = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> collected = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> stamped = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public BlueprintUnlockService(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "unlocked_blueprints.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public boolean isHuntEnabled(Player player) {
        return player != null && huntEnabled.contains(player.getUniqueId());
    }

    public boolean enableHunt(Player player) {
        if (player == null) {
            return false;
        }
        if (!huntEnabled.add(player.getUniqueId())) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** After the first blueprint visit, desk always opens on click. */
    public boolean isDeskReady(Player player) {
        return player != null && deskReady.contains(player.getUniqueId());
    }

    /** @return true if this was the first time marking desk ready (show short welcome). */
    public boolean markDeskReady(Player player) {
        if (player == null) {
            return false;
        }
        if (!deskReady.add(player.getUniqueId())) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Alias used by recipe gate / craft stamp. */
    public boolean has(Player player, String blueprintId) {
        return hasCollected(player, blueprintId);
    }

    public boolean hasCollected(Player player, String blueprintId) {
        if (player == null || blueprintId == null || blueprintId.isBlank()) {
            return false;
        }
        Set<String> set = collected.get(player.getUniqueId());
        return set != null && set.contains(blueprintId.toLowerCase(Locale.ROOT));
    }

    public boolean unlock(Player player, String blueprintId) {
        return markCollected(player, blueprintId);
    }

    public boolean markCollected(Player player, String blueprintId) {
        if (player == null || blueprintId == null || blueprintId.isBlank()) {
            return false;
        }
        String id = blueprintId.toLowerCase(Locale.ROOT);
        Set<String> set = collected.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(id)) {
            return false;
        }
        deskReady.add(player.getUniqueId());
        dirty = true;
        return true;
    }

    /** Surveyor desk stamped this blueprint into a tool. */
    public boolean markStamped(Player player, String blueprintId) {
        if (player == null || blueprintId == null || blueprintId.isBlank()) {
            return false;
        }
        String id = blueprintId.toLowerCase(Locale.ROOT);
        markCollected(player, id);
        Set<String> set = stamped.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(id)) {
            return false;
        }
        dirty = true;
        return true;
    }

    public boolean hasStamped(Player player, String blueprintId) {
        if (player == null || blueprintId == null || blueprintId.isBlank()) {
            return false;
        }
        Set<String> set = stamped.get(player.getUniqueId());
        return set != null && set.contains(blueprintId.toLowerCase(Locale.ROOT));
    }

    /** True if the player stamped at least one Surveyor blueprint into a tool. */
    public boolean hasStampedAny(Player player) {
        if (player == null) {
            return false;
        }
        Set<String> set = stamped.get(player.getUniqueId());
        return set != null && !set.isEmpty();
    }

    public Set<String> stampedOf(Player player) {
        if (player == null) {
            return Collections.emptySet();
        }
        Set<String> set = stamped.get(player.getUniqueId());
        return set == null ? Collections.emptySet() : Set.copyOf(set);
    }

    public Set<String> unlockedOf(Player player) {
        return collectedOf(player);
    }

    public Set<String> collectedOf(Player player) {
        if (player == null) {
            return Collections.emptySet();
        }
        Set<String> set = collected.get(player.getUniqueId());
        return set == null ? Collections.emptySet() : Set.copyOf(set);
    }

    public void save() {
        dirty = true;
        saveIfDirty();
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        for (UUID uuid : huntEnabled) {
            yaml.set("players." + uuid + ".hunt", true);
        }
        for (UUID uuid : deskReady) {
            yaml.set("players." + uuid + ".desk", true);
        }
        for (Map.Entry<UUID, Set<String>> entry : collected.entrySet()) {
            yaml.set("players." + entry.getKey() + ".hunt",
                    yaml.getBoolean("players." + entry.getKey() + ".hunt", huntEnabled.contains(entry.getKey())));
            yaml.set("players." + entry.getKey() + ".desk",
                    yaml.getBoolean("players." + entry.getKey() + ".desk", deskReady.contains(entry.getKey())));
            yaml.set("players." + entry.getKey() + ".unlocked", entry.getValue().stream().sorted().toList());
            yaml.set("players." + entry.getKey() + ".collected", entry.getValue().stream().sorted().toList());
        }
        for (Map.Entry<UUID, Set<String>> entry : stamped.entrySet()) {
            yaml.set("players." + entry.getKey() + ".stamped", entry.getValue().stream().sorted().toList());
            if (!yaml.contains("players." + entry.getKey() + ".hunt")) {
                yaml.set("players." + entry.getKey() + ".hunt", huntEnabled.contains(entry.getKey()));
            }
            if (!yaml.contains("players." + entry.getKey() + ".desk")) {
                yaml.set("players." + entry.getKey() + ".desk", deskReady.contains(entry.getKey()));
            }
        }
        for (UUID uuid : huntEnabled) {
            if (!yaml.contains("players." + uuid + ".hunt")) {
                yaml.set("players." + uuid + ".hunt", true);
            }
        }
        for (UUID uuid : deskReady) {
            if (!yaml.contains("players." + uuid + ".desk")) {
                yaml.set("players." + uuid + ".desk", true);
            }
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save unlocked_blueprints.yml: " + exception.getMessage());
        }
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null || !file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        String key = playerId.toString();
        if (!players.contains(key)) {
            huntEnabled.remove(playerId);
            deskReady.remove(playerId);
            collected.remove(playerId);
            stamped.remove(playerId);
            return;
        }
        if (players.getBoolean(key + ".hunt", false)) {
            huntEnabled.add(playerId);
        } else {
            huntEnabled.remove(playerId);
        }
        if (players.getBoolean(key + ".desk", false)) {
            deskReady.add(playerId);
        } else {
            deskReady.remove(playerId);
        }
        Set<String> set = ConcurrentHashMap.newKeySet();
        for (String id : players.getStringList(key + ".collected")) {
            if (id != null && !id.isBlank()) {
                set.add(id.toLowerCase(Locale.ROOT));
            }
        }
        for (String id : players.getStringList(key + ".unlocked")) {
            if (id != null && !id.isBlank()) {
                set.add(id.toLowerCase(Locale.ROOT));
                huntEnabled.add(playerId);
            }
        }
        if (set.isEmpty()) {
            collected.remove(playerId);
        } else {
            collected.put(playerId, set);
            deskReady.add(playerId);
        }
        Set<String> stampedSet = ConcurrentHashMap.newKeySet();
        for (String id : players.getStringList(key + ".stamped")) {
            if (id != null && !id.isBlank()) {
                stampedSet.add(id.toLowerCase(Locale.ROOT));
            }
        }
        if (stampedSet.isEmpty()) {
            stamped.remove(playerId);
        } else {
            stamped.put(playerId, stampedSet);
            deskReady.add(playerId);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (players.getBoolean(key + ".hunt", false)) {
                huntEnabled.add(uuid);
            }
            if (players.getBoolean(key + ".desk", false)) {
                deskReady.add(uuid);
            }
            Set<String> set = ConcurrentHashMap.newKeySet();
            for (String id : players.getStringList(key + ".collected")) {
                if (id != null && !id.isBlank()) {
                    set.add(id.toLowerCase(Locale.ROOT));
                }
            }
            // Legacy key
            for (String id : players.getStringList(key + ".unlocked")) {
                if (id != null && !id.isBlank()) {
                    set.add(id.toLowerCase(Locale.ROOT));
                    huntEnabled.add(uuid);
                }
            }
            if (!set.isEmpty()) {
                collected.put(uuid, set);
                // Anyone who already stamped a page keeps the desk open.
                deskReady.add(uuid);
            }
            Set<String> stampedSet = ConcurrentHashMap.newKeySet();
            for (String id : players.getStringList(key + ".stamped")) {
                if (id != null && !id.isBlank()) {
                    stampedSet.add(id.toLowerCase(Locale.ROOT));
                }
            }
            if (!stampedSet.isEmpty()) {
                stamped.put(uuid, stampedSet);
                deskReady.add(uuid);
            }
        }
    }
}
