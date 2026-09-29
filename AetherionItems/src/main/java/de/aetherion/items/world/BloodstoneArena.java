package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Locked Hollow Sun arena pads (player entry + boss home snapshot).
 * BossEngine {@code spawners.yml} remains authoritative for the live boss home;
 * this file keeps the values durable across edits and powers {@code /bloodstonearena}.
 */
public final class BloodstoneArena {

    public static final String WORLD = "bloodstone";

    private final AetherionItems plugin;
    private final File file;
    private FileConfiguration config;

    public BloodstoneArena(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bloodstone-arena.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            writeDefaults();
        }
        config = YamlConfiguration.loadConfiguration(file);
        ensureKeys();
        saveQuiet();
    }

    private void writeDefaults() {
        config = new YamlConfiguration();
        // Persisted Multiverse / arena entry pad (do not move without Robbi).
        config.set("player.world", WORLD);
        config.set("player.x", 0.5);
        config.set("player.y", 68.0);
        config.set("player.z", 0.5);
        config.set("player.yaw", 0.0f);
        config.set("player.pitch", 0.0f);
        // Live BossEngine hollow_sun_home (2026-09-26).
        config.set("boss.world", WORLD);
        config.set("boss.x", 0.5);
        config.set("boss.y", 63.0);
        config.set("boss.z", 115.5);
        config.set("boss.yaw", 0.0f);
        config.set("boss.pitch", 0.0f);
        config.set("notes", "Player pad + Hollow Sun boss home. NPC later. /bloodstonearena");
        saveQuiet();
    }

    private void ensureKeys() {
        if (!config.isConfigurationSection("player")) {
            writeDefaults();
            return;
        }
        if (!config.isConfigurationSection("boss")) {
            config.set("boss.world", WORLD);
            config.set("boss.x", 0.5);
            config.set("boss.y", 63.0);
            config.set("boss.z", 115.5);
            config.set("boss.yaw", 0.0f);
            config.set("boss.pitch", 0.0f);
        }
    }

    public Location playerSpawn() {
        return read("player");
    }

    public Location bossSpawn() {
        return read("boss");
    }

    public void setPlayerSpawn(Location location) {
        write("player", location);
        saveQuiet();
    }

    public void setBossSpawn(Location location) {
        write("boss", location);
        saveQuiet();
    }

    public boolean teleport(Player player) {
        Location dest = playerSpawn();
        if (dest == null || dest.getWorld() == null) {
            return false;
        }
        return player.teleport(dest);
    }

    private Location read(String path) {
        String worldName = config.getString(path + ".world", WORLD);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorld(WORLD);
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch")
        );
    }

    private void write(String path, Location location) {
        config.set(path + ".world", location.getWorld() != null ? location.getWorld().getName() : WORLD);
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private void saveQuiet() {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save bloodstone-arena.yml", exception);
        }
    }
}
