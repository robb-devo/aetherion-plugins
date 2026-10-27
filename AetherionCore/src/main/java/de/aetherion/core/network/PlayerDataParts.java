package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PlayerDataPart;
import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Snapshot side of {@link PlayerDataPart}: every registered part goes into the character as {@code blob_part_<id>},
 * and arrives through the same part on the other backend. Used by Core's sync and by Dungeons' copy of it.
 *
 * <p>A part this backend does not run is parked in {@code plugins/AetherionCore/carry/<uuid>.yml} on arrival and
 * leaves again with the next hop, so mmo-m → mmo-i → mmo-r still brings the mine profile home.
 */
public final class PlayerDataParts {

    private static final String PREFIX = "part_";
    private static final String BLOB_PREFIX = NetworkDataKeys.blob(PREFIX);

    private PlayerDataParts() {
    }

    /** Leaving: the live parts, then whatever this backend only carries. */
    public static void exportInto(Map<String, Object> out, UUID id) {
        if (out == null || id == null) {
            return;
        }
        Map<String, PlayerDataPart> parts = AetherServices.playerData();
        for (PlayerDataPart part : parts.values()) {
            try {
                String data = part.export(id);
                if (data != null && !data.isBlank()) {
                    out.put(NetworkDataKeys.blob(PREFIX + part.id()), data);
                }
            } catch (RuntimeException ex) {
                log().log(Level.WARNING, "[Transfer] Player data '" + part.id() + "' export failed for " + id, ex);
            }
        }
        readCarry(id).forEach((key, data) -> {
            if (!parts.containsKey(key)) {
                out.put(NetworkDataKeys.blob(PREFIX + key), data);
            }
        });
    }

    /** Arriving: each part takes its own text; the rest is carried until the next hop. */
    public static void importFrom(Map<String, Object> plain, UUID id) {
        if (plain == null || id == null) {
            return;
        }
        Map<String, PlayerDataPart> parts = AetherServices.playerData();
        Map<String, String> carried = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : plain.entrySet()) {
            if (!entry.getKey().startsWith(BLOB_PREFIX) || !(entry.getValue() instanceof String data) || data.isBlank()) {
                continue;
            }
            String key = entry.getKey().substring(BLOB_PREFIX.length());
            PlayerDataPart part = parts.get(key);
            if (part == null) {
                carried.put(key, data);
                continue;
            }
            try {
                part.importData(id, data);
            } catch (RuntimeException ex) {
                log().log(Level.WARNING, "[Transfer] Player data '" + key + "' import failed for " + id, ex);
            }
        }
        writeCarry(id, carried);
    }

    private static File carryFile(UUID id) {
        AetherionCore core = AetherionCore.get();
        return core == null ? null : new File(core.getDataFolder(), "carry/" + id + ".yml");
    }

    private static Map<String, String> readCarry(UUID id) {
        Map<String, String> out = new LinkedHashMap<>();
        File file = carryFile(id);
        if (file == null || !file.isFile()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String data = yaml.getString(key);
            if (data != null && !data.isBlank()) {
                out.put(key, data);
            }
        }
        return out;
    }

    private static void writeCarry(UUID id, Map<String, String> carried) {
        File file = carryFile(id);
        if (file == null) {
            return;
        }
        if (carried.isEmpty()) {
            if (file.isFile() && !file.delete()) {
                log().warning("[Transfer] Could not clear carried player data " + file.getName());
            }
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        carried.forEach(yaml::set);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            AtomicYaml.save(yaml, file, log());
        } catch (IOException ex) {
            log().log(Level.WARNING, "[Transfer] Could not carry player data for " + id, ex);
        }
    }

    private static Logger log() {
        AetherionCore core = AetherionCore.get();
        return core != null ? core.getLogger() : Logger.getLogger("AetherionCore");
    }
}
