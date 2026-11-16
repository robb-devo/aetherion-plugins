package de.aetherion.dungeons.bridge;

import de.aetherion.core.network.NetworkDataKeys;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two steps around a pending snapshot that only the Dungeons store needs. Everything else about the
 * progression a character carries (which files, which keys, how they are merged) lives once, in Core's
 * {@link de.aetherion.core.network.NetworkPlayerDataSync}.
 */
final class PendingSnapshotFiles {

    private PendingSnapshotFiles() {
    }

    /**
     * Pre-login staging (async): files that are read before {@code PlayerJoinEvent}
     * are written early so the arriving backend loads the transferred copy.
     */
    static void stageEarly(Plugin plugin, UUID id, Map<String, Object> plain, File mainWorldFolder) {
        if (id == null || plain == null || plain.isEmpty()) {
            return;
        }
        String key = id.toString();
        if (mainWorldFolder != null) {
            Object stats = NetworkDataKeys.lookupBlob(plain, "stats");
            if (stats instanceof String raw) {
                writeString(plugin, new File(mainWorldFolder, "stats/" + key + ".json"), raw);
            }
            Object adv = NetworkDataKeys.lookupBlob(plain, "advancements");
            if (adv instanceof String raw) {
                writeString(plugin, new File(mainWorldFolder, "advancements/" + key + ".json"), raw);
            }
        }
        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            Object q1 = NetworkDataKeys.lookupBlob(plain, "quests");
            if (q1 instanceof String raw) {
                writeString(plugin, new File(quests.getDataFolder(), "players/" + key + ".yml"), raw);
            }
            Object q2 = NetworkDataKeys.lookupBlob(plain, "quests2");
            if (q2 instanceof String raw) {
                writeString(plugin, new File(quests.getDataFolder(), "data/" + key + ".yml"), raw);
            }
        }
    }

    /**
     * Snapshots before v6 stored {@code yaml:coins.yml -> players.<uuid>} inside a normal YAML
     * document, and Bukkit split the dots on load. Rebuild flat keys for pending files.
     */
    static Map<String, Object> repairLegacyKeys(Map<String, Object> plain) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (plain == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : plain.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name.startsWith("yaml:") && !name.endsWith(".yml") && value instanceof Map<?, ?> nested
                    && nested.size() == 1 && nested.get("yml") instanceof Map<?, ?> inner) {
                Map<String, Object> flat = new LinkedHashMap<>();
                for (Map.Entry<?, ?> section : inner.entrySet()) {
                    if (section.getValue() instanceof Map<?, ?> perPlayer) {
                        for (Map.Entry<?, ?> player : perPlayer.entrySet()) {
                            flat.put(section.getKey() + "." + player.getKey(), player.getValue());
                        }
                    }
                }
                out.put(name + ".yml", flat);
            } else {
                out.put(name, value);
            }
        }
        return out;
    }

    private static void writeString(Plugin plugin, File file, String raw) {
        try {
            de.aetherion.core.persist.AtomicYaml.writeString(file, raw);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not write " + file.getName() + ": " + ex.getMessage());
        }
    }
}
