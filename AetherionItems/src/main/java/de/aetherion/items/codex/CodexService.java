package de.aetherion.items.codex;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CodexService {

    public record Rank(int place, String name, long amount) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerCodex> players = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public CodexService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "codex.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public void addKill(Player player, String id) {
        if (player == null || id == null || CodexCatalog.mob(id) == null) {
            return;
        }
        data(player).kills.merge(id, 1L, Long::sum);
        dirty = true;
    }

    public void addBossKill(Player player, String bossId) {
        if (player == null || bossId == null || bossId.isBlank()) {
            return;
        }
        data(player).bosses.merge(bossId.toLowerCase(java.util.Locale.ROOT), 1L, Long::sum);
        dirty = true;
    }

    public void addBlock(Player player, String id) {
        if (player == null || id == null || CodexCatalog.block(id) == null) {
            return;
        }
        data(player).blocks.merge(id, 1L, Long::sum);
        dirty = true;
    }

    public long kills(Player player, String id) {
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.kills.getOrDefault(id, 0L);
    }

    public long bossKills(Player player, String bossId) {
        if (player == null || bossId == null) {
            return 0L;
        }
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.bosses.getOrDefault(bossId.toLowerCase(java.util.Locale.ROOT), 0L);
    }

    public long blocks(Player player, String id) {
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.blocks.getOrDefault(id, 0L);
    }

    public boolean hasAnyKill(Player player) {
        return hasAny(player, true, false);
    }

    public boolean hasAnyBlock(Player player) {
        return hasAny(player, false, false);
    }

    public boolean hasAnyBossKill(Player player) {
        return hasAny(player, false, true);
    }

    private boolean hasAny(Player player, boolean kills, boolean bosses) {
        if (player == null) {
            return false;
        }
        PlayerCodex data = players.get(player.getUniqueId());
        if (data == null) {
            return false;
        }
        if (bosses) {
            return data.bosses.values().stream().anyMatch(amount -> amount > 0);
        }
        return (kills ? data.kills : data.blocks).values().stream().anyMatch(amount -> amount > 0);
    }

    public List<Rank> topKills(String id) {
        return top(id, true);
    }

    public List<Rank> topBlocks(String id) {
        return top(id, false);
    }

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        for (PlayerCodex data : players.values()) {
            String path = "players." + data.id;
            config.set(path, null);
            config.set(path + ".name", data.name);
            for (Map.Entry<String, Long> entry : data.kills.entrySet()) {
                config.set(path + ".kills." + yamlKey(entry.getKey()), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : data.blocks.entrySet()) {
                config.set(path + ".blocks." + yamlKey(entry.getKey()), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : data.bosses.entrySet()) {
                config.set(path + ".bosses." + yamlKey(entry.getKey()), entry.getValue());
            }
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save codex.yml: " + exception.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private List<Rank> top(String id, boolean kills) {
        List<PlayerCodex> ranked = new ArrayList<>();
        for (PlayerCodex data : players.values()) {
            long amount = kills ? data.kills.getOrDefault(id, 0L) : data.blocks.getOrDefault(id, 0L);
            if (amount > 0) {
                ranked.add(data);
            }
        }
        ranked.sort(Comparator
                .comparingLong((PlayerCodex data) -> kills ? data.kills.getOrDefault(id, 0L) : data.blocks.getOrDefault(id, 0L))
                .reversed()
                .thenComparing(data -> data.name, String.CASE_INSENSITIVE_ORDER));

        List<Rank> result = new ArrayList<>();
        int place = 1;
        for (PlayerCodex data : ranked) {
            if (place > 3) {
                break;
            }
            long amount = kills ? data.kills.getOrDefault(id, 0L) : data.blocks.getOrDefault(id, 0L);
            result.add(new Rank(place, data.name, amount));
            place++;
        }
        return result;
    }

    private PlayerCodex data(Player player) {
        PlayerCodex data = players.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerCodex(uuid, player.getName()));
        data.name = player.getName();
        return data;
    }

    public void reloadFromDisk() {
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            PlayerCodex data = new PlayerCodex(uuid, section.getString("name", "Unknown"));
            readMap(section.getConfigurationSection("kills"), data.kills, true);
            readMap(section.getConfigurationSection("blocks"), data.blocks, false);
            readBosses(section.getConfigurationSection("bosses"), data.bosses);
            players.put(uuid, data);
        }
    }

    private void readBosses(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(fromYamlKey(key).toLowerCase(java.util.Locale.ROOT), section.getLong(key));
        }
    }

    private void readMap(ConfigurationSection section, Map<String, Long> target, boolean kills) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String id = fromYamlKey(key);
            if (kills && CodexCatalog.mob(id) == null && CodexCatalog.mob(key) != null) {
                id = key;
            }
            if (!kills && CodexCatalog.block(id) == null && CodexCatalog.block(key) != null) {
                id = key;
            }
            target.put(id, section.getLong(key));
        }
    }

    private static String yamlKey(String id) {
        return id.replace(":", "__");
    }

    private static String fromYamlKey(String key) {
        return key.replace("__", ":");
    }

    private static final class PlayerCodex {
        private final UUID id;
        private volatile String name;
        private final Map<String, Long> kills = new ConcurrentHashMap<>();
        private final Map<String, Long> blocks = new ConcurrentHashMap<>();
        private final Map<String, Long> bosses = new ConcurrentHashMap<>();

        private PlayerCodex(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
