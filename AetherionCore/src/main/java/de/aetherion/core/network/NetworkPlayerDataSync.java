package de.aetherion.core.network;

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

        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        if (quests != null) {
            quests.flushPlayer(player);
        }
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
                    // A string payload. Map keys like yaml:skills.yml / players.<uuid>
                    // are split on '.' by Bukkit and come back as skills=false.
                    out.put(NetworkDataKeys.yamlFile(spec[0]), dumpFragment(found));
                }
            }
            // Always include per-player files (even if junctions share them — keeps non-junction setups working)
            putFile(out, NetworkDataKeys.blob("storage"), new File(folder, "storage/" + key + ".yml"));
            putFile(out, NetworkDataKeys.blob("loadout"), new File(folder, "loadouts/" + key + ".yml"));
            putFile(out, NetworkDataKeys.blob("sack"), new File(folder, "sacks/" + key + ".yml"));
        }

        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null) {
            putFile(out, NetworkDataKeys.blob("pets"), new File(mobs.getDataFolder(), "pets/" + key + ".yml"));
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            putFile(out, NetworkDataKeys.blob("quests"), new File(quests.getDataFolder(), "players/" + key + ".yml"));
            putFile(out, NetworkDataKeys.blob("quests2"), new File(quests.getDataFolder(), "data/" + key + ".yml"));
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        putFile(out, NetworkDataKeys.blob("stats"), new File(worldFolder, "stats/" + key + ".json"));
        putFile(out, NetworkDataKeys.blob("advancements"), new File(worldFolder, "advancements/" + key + ".json"));
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
            for (String[] spec : YAML_KEYS) {
                Object raw = NetworkDataKeys.lookupYaml(plain, spec[0]);
                if (raw == null) {
                    continue;
                }
                try {
                    if ("skills.yml".equals(spec[0])) {
                        importSkills(new File(folder, spec[0]), raw);
                    } else {
                        importYamlFragment(new File(folder, spec[0]), raw);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Import failed for " + spec[0], ex);
                }
            }
            importBlob(plain, "storage", new File(folder, "storage/" + key + ".yml"), true);
            importBlob(plain, "loadout", new File(folder, "loadouts/" + key + ".yml"), false);
            importBlob(plain, "sack", new File(folder, "sacks/" + key + ".yml"), false);
            applyCoinsInMemory(id, plain);
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.reloadAfterImport(player);
            }
        }

        de.aetherion.core.api.PetAccess pets = de.aetherion.core.api.AetherServices.pets();
        Object petsBlob = NetworkDataKeys.lookupBlob(plain, "pets");
        if (pets != null && petsBlob instanceof String rawPets) {
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
            importBlob(plain, "quests", new File(quests.getDataFolder(), "players/" + key + ".yml"), false);
            importBlob(plain, "quests2", new File(quests.getDataFolder(), "data/" + key + ".yml"), false);
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        importBlob(plain, "stats", new File(worldFolder, "stats/" + key + ".json"), false);
        importBlob(plain, "advancements", new File(worldFolder, "advancements/" + key + ".json"), false);

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
        Object raw = NetworkDataKeys.lookupYaml(data, "coins.yml");
        Map<String, Object> map = coinPaths(raw, id);
        if (map.isEmpty()) {
            return;
        }
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.applyImportedCoins(id, map);
        }
    }

    private static String dumpFragment(Map<String, Object> found) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : found.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        return yaml.saveToString();
    }

    private void importSkills(File file, Object raw) throws Exception {
        org.bukkit.configuration.file.YamlConfiguration incomingYaml = asYaml(raw);
        Map<String, Object> incoming = TransferProgressGuard.playerSections(
                incomingYaml == null ? raw : toPlainMap(incomingYaml)
        );
        Map<String, Object> existing = playerSectionsOnDisk(file);
        Map<String, Object> kept = TransferProgressGuard.filterSkillWrites(incoming, existing);
        int dropped = incoming.size() - kept.size();
        if (dropped > 0) {
            plugin.getLogger().warning("Refusing to overwrite richer skills in " + file.getName()
                    + " (" + dropped + " section(s) kept on disk).");
        }
        if (!kept.isEmpty()) {
            writeKeys(file, kept);
        }
    }

    private void importYamlFragment(File file, Object raw) throws Exception {
        org.bukkit.configuration.file.YamlConfiguration incoming = asYaml(raw);
        if (incoming == null) {
            return;
        }
        Map<String, Object> leaves = new LinkedHashMap<>();
        for (String key : incoming.getKeys(true)) {
            if (!incoming.isConfigurationSection(key)) {
                leaves.put(key, incoming.get(key));
            }
        }
        if (!leaves.isEmpty()) {
            writeKeys(file, leaves);
        }
    }

    private void importBlob(Map<String, Object> plain, String name, File file, boolean storage) {
        Object raw = NetworkDataKeys.lookupBlob(plain, name);
        if (!(raw instanceof String text) || text.isBlank()) {
            return;
        }
        try {
            if (storage && file.isFile()) {
                String existing = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                String merged = StorageFileMerge.merge(existing, text);
                int existingSlots = StorageFileMerge.countSlots(existing);
                int incomingSlots = StorageFileMerge.countSlots(text);
                if (incomingSlots < existingSlots || StorageFileMerge.unlockedPages(text) < StorageFileMerge.unlockedPages(existing)) {
                    plugin.getLogger().warning("Transfer storage for " + file.getName()
                            + " was smaller than disk (incoming slots " + incomingSlots
                            + ", disk slots " + existingSlots + "). Missing pages were kept.");
                }
                if (!merged.equals(existing)) {
                    writeString(file, merged);
                }
                return;
            }
            writeString(file, text);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Import failed for " + name, ex);
        }
    }

    private static Map<String, Object> playerSectionsOnDisk(File file) {
        if (file == null || !file.isFile()) {
            return Map.of();
        }
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        return TransferProgressGuard.playerSections(toPlainMap(yaml));
    }

    private static org.bukkit.configuration.file.YamlConfiguration asYaml(Object raw) {
        try {
            if (raw instanceof String text) {
                org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
                yaml.loadFromString(text);
                return yaml;
            }
            Map<String, Object> map = toPlainMap(raw);
            if (map.isEmpty()) {
                return null;
            }
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                yaml.set(entry.getKey(), toPlainValue(entry.getValue()));
            }
            return yaml;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, Object> coinPaths(Object raw, java.util.UUID id) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (id == null) {
            return out;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = asYaml(raw);
        if (yaml == null) {
            return out;
        }
        String player = "players." + id;
        String lifetime = "lifetime." + id;
        if (yaml.contains(player)) {
            out.put(player, yaml.get(player));
        }
        if (yaml.contains(lifetime)) {
            out.put(lifetime, yaml.get(lifetime));
        }
        return out;
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
        de.aetherion.core.persist.AtomicYaml.recoverTemp(file, plugin.getLogger());
        var yaml = file.isFile()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            yaml.set(entry.getKey(), toPlainValue(entry.getValue()));
        }
        de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
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
            de.aetherion.core.persist.AtomicYaml.writeString(file, raw);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not write " + file.getName() + ": " + ex.getMessage());
        }
    }
}
