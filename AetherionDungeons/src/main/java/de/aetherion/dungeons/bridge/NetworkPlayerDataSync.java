package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Full progression sync for network transfers (Hypixel-style on same host).
 * Per-player folders (storage/loadouts/sacks/pets) should also be junctioned;
 * this class still flushes + reloads memory caches so both directions stay hot.
 */
public final class NetworkPlayerDataSync {

    private static final List<String[]> YAML_KEYS = List.of(
            new String[]{"coins.yml", "players", "lifetime"},
            new String[]{"skills.yml", "players"},
            new String[]{"progress.yml", "players"},
            new String[]{"player-ranks.yml", "players"},
            new String[]{"shards.yml", "players"},
            new String[]{"xp-boosts.yml", "players"},
            new String[]{"recipe_unlocks.yml", "players"},
            new String[]{"unlocked_blueprints.yml", "players"},
            new String[]{"areas.yml", "players"},
            new String[]{"codex.yml", "players"},
            new String[]{"colosseum-unlock.yml", "players"}
    );

    private final Plugin plugin;

    public NetworkPlayerDataSync(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Call on the leaving server before Velocity Connect. */
    public void flushPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        player.closeInventory();

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null && items.isEnabled()) {
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.flushPlayer(player);
            }
        }

        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        if (pets != null) {
            pets.flushPlayer(id);
        }

        // Quests: savePlayer/saveAll were invoked reflectively but never existed on
        // AetherionQuests — those calls no-oped. Quest yaml is still copied below.
    }

    public Map<String, Object> exportAll(Player player) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (player == null) {
            return out;
        }
        flushPlayer(player);
        UUID id = player.getUniqueId();
        String key = id.toString();

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null) {
            File folder = items.getDataFolder();
            for (String[] spec : YAML_KEYS) {
                Map<String, Object> found = readKeys(new File(folder, spec[0]), key, spec);
                if (!found.isEmpty()) {
                    out.put("yaml:" + spec[0], found);
                }
            }
            // Always include per-player files (even if junctions share them — keeps non-junction setups working)
            putFile(out, "file:storage", new File(folder, "storage/" + key + ".yml"));
            putFile(out, "file:loadout", new File(folder, "loadouts/" + key + ".yml"));
            putFile(out, "file:sack", new File(folder, "sacks/" + key + ".yml"));
        }

        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null) {
            putFile(out, "file:pets", new File(mobs.getDataFolder(), "pets/" + key + ".yml"));
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            putFile(out, "file:quests", new File(quests.getDataFolder(), "players/" + key + ".yml"));
            putFile(out, "file:quests2", new File(quests.getDataFolder(), "data/" + key + ".yml"));
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        putFile(out, "file:stats", new File(worldFolder, "stats/" + key + ".json"));
        putFile(out, "file:advancements", new File(worldFolder, "advancements/" + key + ".json"));
        return out;
    }

    @SuppressWarnings("unchecked")
    public void importAll(Player player, Map<?, ?> data) {
        if (player == null || data == null || data.isEmpty()) {
            return;
        }
        UUID id = player.getUniqueId();
        String key = id.toString();
        Map<String, Object> plain = toPlainMap(data);

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null) {
            File folder = items.getDataFolder();
            for (Map.Entry<String, Object> entry : plain.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                try {
                    if (name.startsWith("yaml:") && value instanceof Map<?, ?> map) {
                        writeKeys(new File(folder, name.substring(5)), (Map<String, Object>) map);
                    } else if (name.equals("file:storage") && value instanceof String raw) {
                        writeString(new File(folder, "storage/" + key + ".yml"), raw);
                    } else if (name.equals("file:loadout") && value instanceof String raw) {
                        writeString(new File(folder, "loadouts/" + key + ".yml"), raw);
                    } else if (name.equals("file:sack") && value instanceof String raw) {
                        writeString(new File(folder, "sacks/" + key + ".yml"), raw);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Import failed for " + name, ex);
                }
            }
            applyCoinsInMemory(id, plain);
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.reloadAfterImport(player);
            }
        }

        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        if (pets != null && plain.get("file:pets") instanceof String rawPets) {
            Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (mobs != null) {
                writeString(new File(mobs.getDataFolder(), "pets/" + key + ".yml"), rawPets);
            }
            pets.reloadAfterImport(id);
            Bukkit.getScheduler().runTaskLater(plugin, () -> pets.reequip(player), 2L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> pets.reequip(player), 12L);
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            if (plain.get("file:quests") instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "players/" + key + ".yml"), raw);
            }
            if (plain.get("file:quests2") instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "data/" + key + ".yml"), raw);
            }
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        if (plain.get("file:stats") instanceof String raw) {
            writeString(new File(worldFolder, "stats/" + key + ".json"), raw);
        }
        if (plain.get("file:advancements") instanceof String raw) {
            writeString(new File(worldFolder, "advancements/" + key + ".json"), raw);
        }

        if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "tab scoreboard show main " + player.getName()), 20L);
        }
        plugin.getLogger().info("Network progression imported for " + player.getName()
                + " keys=" + plain.keySet());
    }

    /**
     * Bukkit YAML reloads nested maps as {@link org.bukkit.configuration.ConfigurationSection}.
     * Convert those (and nested sections) into plain maps so transfer import never skips data.
     */
    public static Map<String, Object> toPlainMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), toPlainValue(e.getValue()));
            }
            return out;
        }
        if (raw instanceof org.bukkit.configuration.ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                out.put(key, toPlainValue(section.get(key)));
            }
        }
        return out;
    }

    private static Object toPlainValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            return toPlainMap(section);
        }
        if (value instanceof Map<?, ?> map) {
            return toPlainMap(map);
        }
        if (value instanceof List<?> list) {
            java.util.ArrayList<Object> copy = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(toPlainValue(item));
            }
            return copy;
        }
        return value;
    }

    /**
     * Drop in-memory loadout/active state after a network snapshot so join hooks
     * cannot re-apply stale armor over the transferred inventory.
     */
    public void resetLoadoutRuntime(Player player) {
        if (player == null) {
            return;
        }
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.resetLoadoutRuntime(player);
        }
    }

    private void applyCoinsInMemory(UUID id, Map<String, Object> data) {
        Object raw = data.get("yaml:coins.yml");
        Map<String, Object> map = toPlainMap(raw);
        if (map.isEmpty()) {
            return;
        }
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.applyImportedCoins(id, map);
        }
    }

    private Map<String, Object> readKeys(File file, String uuid, String[] spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (file == null || !file.isFile()) {
            return out;
        }
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (int i = 1; i < spec.length; i++) {
            String path = spec[i] + "." + uuid;
            if (yaml.contains(path)) {
                // Always plain maps so SnakeYAML round-trip never becomes dead ConfigurationSections.
                out.put(path, toPlainValue(yaml.get(path)));
            }
        }
        return out;
    }

    private void writeKeys(File file, Map<String, Object> values) throws Exception {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        var yaml = file.isFile()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            yaml.set(entry.getKey(), toPlainValue(entry.getValue()));
        }
        yaml.save(file);
    }

    private void putFile(Map<String, Object> out, String key, File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            return;
        }
        try {
            out.put(key, Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void writeString(File file, String raw) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.writeString(file.toPath(), raw, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not write " + file.getName() + ": " + ex.getMessage());
        }
    }
}
