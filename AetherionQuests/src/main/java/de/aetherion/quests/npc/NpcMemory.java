package de.aetherion.quests.npc;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * What NPCs remember about each player: how often they talked, when, the last greeting,
 * and the player's talk-UI preference. Own file ({@code npc-memory.yml}) so quest data
 * stays untouched. Saved every few minutes off the main thread and on disable.
 */
public final class NpcMemory {

    private static NpcMemory instance;

    private final AetherionQuests plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private volatile boolean dirty;

    private NpcMemory(AetherionQuests plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npc-memory.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public static NpcMemory start(AetherionQuests plugin) {
        instance = new NpcMemory(plugin);
        long period = 20L * 60L * 3L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, instance::saveAsync, period, period);
        return instance;
    }

    public static NpcMemory get() {
        return instance;
    }

    private static String key(UUID player, String npcId) {
        return "players." + player + ".npc." + npcId.toLowerCase(Locale.ROOT);
    }

    public int talks(UUID player, String npcId) {
        if (player == null || npcId == null) {
            return 0;
        }
        return yaml.getInt(key(player, npcId) + ".talks", 0);
    }

    public long lastTalk(UUID player, String npcId) {
        if (player == null || npcId == null) {
            return 0L;
        }
        return yaml.getLong(key(player, npcId) + ".last", 0L);
    }

    public void noteTalk(UUID player, String npcId) {
        if (player == null || npcId == null) {
            return;
        }
        String k = key(player, npcId);
        yaml.set(k + ".talks", yaml.getInt(k + ".talks", 0) + 1);
        yaml.set(k + ".last", System.currentTimeMillis());
        dirty = true;
    }

    public long lastGreet(UUID player, String npcId) {
        if (player == null || npcId == null) {
            return 0L;
        }
        return yaml.getLong(key(player, npcId) + ".greet", 0L);
    }

    public void noteGreet(UUID player, String npcId) {
        if (player == null || npcId == null) {
            return;
        }
        yaml.set(key(player, npcId) + ".greet", System.currentTimeMillis());
        dirty = true;
    }

    public boolean flag(UUID player, String flag) {
        return player != null && flag != null && yaml.getBoolean("players." + player + ".flags." + flag, false);
    }

    public void setFlag(UUID player, String flag, boolean value) {
        if (player == null || flag == null) {
            return;
        }
        yaml.set("players." + player + ".flags." + flag, value ? Boolean.TRUE : null);
        dirty = true;
    }

    public boolean prefersClassic(UUID player) {
        return flag(player, "classic_talk");
    }

    public void setClassic(UUID player, boolean classic) {
        setFlag(player, "classic_talk", classic);
    }

    /** Forget one player (admin wipe paths). */
    public void wipe(UUID player) {
        if (player == null) {
            return;
        }
        yaml.set("players." + player, null);
        dirty = true;
    }

    public int knownPlayers() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        return players == null ? 0 : players.getKeys(false).size();
    }

    private void saveAsync() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    /** Synchronous save — onDisable only. */
    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        write(yaml.saveToString());
    }

    private synchronized void write(String data) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save npc-memory.yml: " + ex.getMessage());
            dirty = true;
        }
    }
}
