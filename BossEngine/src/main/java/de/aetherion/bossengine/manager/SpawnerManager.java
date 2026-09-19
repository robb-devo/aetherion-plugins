package de.aetherion.bossengine.manager;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.config.DurationParser;
import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.SpawnCondition;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnType;
import de.aetherion.bossengine.trigger.SpawnerDefinition;
import de.aetherion.bossengine.trigger.impl.CommandTrigger;
import de.aetherion.bossengine.trigger.impl.ItemTrigger;
import de.aetherion.bossengine.trigger.impl.StationaryTrigger;
import de.aetherion.bossengine.trigger.impl.TimerTrigger;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {

    private final JavaPlugin plugin;
    private final BossManager bossManager;
    private final Map<String, BossSpawner> spawners = new ConcurrentHashMap<>();

    public SpawnerManager(JavaPlugin plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    public void reload() {
        stop();
        spawners.clear();

        File file = new File(plugin.getDataFolder(), "spawners.yml");
        if (!file.exists()) {
            plugin.saveResource("spawners.yml", false);
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("spawners");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            SpawnerDefinition definition = read(id, section);
            BossSpawner spawner = new BossSpawner(
                    definition,
                    createTrigger(definition),
                    bossManager
            );
            spawners.put(id.toLowerCase(Locale.ROOT), spawner);
        }

        start();
        plugin.getLogger().info("Loaded spawners: " + spawners.size());
    }

    public Collection<BossSpawner> getSpawners() {
        return Collections.unmodifiableCollection(spawners.values());
    }

    public String matchingSpawnerId(String bossId, Location location) {
        if (bossId == null) {
            return null;
        }
        String fallback = null;
        String closest = null;
        double best = Double.MAX_VALUE;
        for (BossSpawner spawner : spawners.values()) {
            if (!bossId.equalsIgnoreCase(spawner.getDefinition().getBossId())) {
                continue;
            }
            String id = spawner.getDefinition().getId();
            if (id != null && id.toLowerCase(Locale.ROOT).endsWith("_home")) {
                fallback = id;
            }
            Location at = spawner.getDefinition().toLocation();
            if (location == null || at == null || at.getWorld() == null || location.getWorld() == null) {
                continue;
            }
            if (!at.getWorld().equals(location.getWorld())) {
                continue;
            }
            double dist = at.distanceSquared(location);
            if (dist < best) {
                best = dist;
                closest = id;
            }
        }
        if (closest != null && best <= 80 * 80) {
            return closest;
        }
        return fallback;
    }

    public void start() {
        spawners.values().forEach(BossSpawner::start);
    }

    public void stop() {
        spawners.values().forEach(BossSpawner::stop);
    }

    public Optional<BossSpawner> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(spawners.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<BossSpawner> getAll() {
        return Collections.unmodifiableCollection(spawners.values());
    }

    public Optional<BossSpawner> findAltar(String itemId, org.bukkit.Location location) {
        return spawners.values().stream()
                .filter(spawner -> spawner.getDefinition().getType() == SpawnType.ITEM)
                .filter(spawner -> spawner.getDefinition().isEnabled())
                .filter(spawner -> itemId.equalsIgnoreCase(spawner.getDefinition().getItemId()))
                .filter(spawner -> spawner.getDefinition().isInsideAltar(location))
                .findFirst();
    }

    public boolean setStationarySpawn(String bossId, Location location) {
        if (bossId == null || location == null || location.getWorld() == null) {
            return false;
        }

        String id = bossId.toLowerCase(Locale.ROOT) + "_home";
        File file = new File(plugin.getDataFolder(), "spawners.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "spawners." + id + ".";
        yaml.set(path + "enabled", true);
        yaml.set(path + "boss", bossId);
        yaml.set(path + "type", "STATIONARY");
        yaml.set(path + "world", location.getWorld().getName());
        yaml.set(path + "x", location.getX());
        yaml.set(path + "y", location.getY());
        yaml.set(path + "z", location.getZ());
        yaml.set(path + "yaw", location.getYaw());
        yaml.set(path + "pitch", location.getPitch());
        yaml.set(path + "interval", "5m");
        yaml.set(path + "max-instances", 1);
        yaml.set(path + "leash-radius", 36);
        yaml.set(path + "leash-action", "TELEPORT");

        try {
            yaml.save(file);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save spawners.yml: " + exception.getMessage());
            return false;
        }

        bossManager.getByTemplate(bossId).forEach(instance ->
                bossManager.despawn(
                        instance,
                        de.aetherion.bossengine.event.BossDespawnEvent.Reason.COMMAND,
                        null
                )
        );

        reload();
        return true;
    }

    private SpawnerDefinition read(String id, ConfigurationSection section) {
        SpawnType type;
        try {
            type = SpawnType.valueOf(section.getString("type", "COMMAND").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            type = SpawnType.COMMAND;
        }

        SpawnCondition conditions = new SpawnCondition(
                section.getInt("max-instances", 1),
                section.getDouble("leash-radius", 48),
                leash(section.getString("leash-action"))
        );

        return new SpawnerDefinition(
                id,
                section.getBoolean("enabled", true),
                section.getString("boss", id),
                type,
                section.getString("world", "world"),
                section.getDouble("x", 0),
                section.getDouble("y", 64),
                section.getDouble("z", 0),
                (float) section.getDouble("yaw", 0),
                (float) section.getDouble("pitch", 0),
                section.getString("item-id", id),
                section.getDouble("altar-radius", 4),
                DurationParser.toTicks(section.getString("interval"), 20L * 60L * 60L),
                conditions
        );
    }

    private de.aetherion.bossengine.trigger.AbstractSpawnTrigger createTrigger(SpawnerDefinition definition) {
        return switch (definition.getType()) {
            case TIMER -> new TimerTrigger(plugin, definition);
            case ITEM -> new ItemTrigger(definition);
            case COMMAND -> new CommandTrigger(definition);
            case STATIONARY -> new StationaryTrigger(plugin, definition);
        };
    }

    private static LeashAction leash(String raw) {
        try {
            return LeashAction.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return LeashAction.TELEPORT;
        }
    }
}
