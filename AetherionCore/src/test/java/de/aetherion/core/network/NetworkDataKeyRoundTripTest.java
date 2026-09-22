package de.aetherion.core.network;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit YAML keys that contain ':' have bitten transfer snapshots:
 * {@code yaml:skills.yml} can be reread as something other than that key,
 * so apply logs {@code skills=false} and then writes a default level.
 */
class NetworkDataKeyRoundTripTest {

    @Test
    void colonKeyDoesNotRoundTripAsTheSameKey() throws Exception {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("bonusXp", 5000L);
        Map<String, Object> network = new LinkedHashMap<>();
        network.put("yaml:skills.yml", Map.of("players.uuid", section));
        network.put("file:storage", "unlocked-pages: 3\n");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("network-data", network);
        String dumped = yaml.saveToString();

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(dumped);
        Map<String, Object> plain = NetworkPlayerDataSync.toPlainMap(loaded.get("network-data"));

        Object skills = NetworkDataKeys.lookupYaml(plain, NetworkDataKeys.SKILLS_FILE);
        Object storage = NetworkDataKeys.lookupBlob(plain, NetworkDataKeys.STORAGE_FILE);
        assertTrue(storage != null, "storage lost\n" + dumped + "\nkeys=" + plain.keySet());
        Map<String, Object> players = TransferProgressGuard.playerSections(skills);
        assertEquals(5000L, TransferProgressGuard.richness(players.get("players.uuid")),
                dumped + "\nkeys=" + plain.keySet() + "\nplayers=" + players.keySet());
    }

    @Test
    void safeKeysRoundTrip() throws Exception {
        Map<String, Object> network = new LinkedHashMap<>();
        network.put(NetworkDataKeys.skills(), "players:\n  uuid:\n    bonusXp: 5000\n");
        network.put(NetworkDataKeys.storage(), "unlocked-pages: 3\n");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("network-data", network);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(yaml.saveToString());
        Map<String, Object> plain = NetworkPlayerDataSync.toPlainMap(loaded.get("network-data"));

        Object skills = NetworkDataKeys.lookupYaml(plain, NetworkDataKeys.SKILLS_FILE);
        assertTrue(skills instanceof String);
        org.bukkit.configuration.file.YamlConfiguration parsed = new org.bukkit.configuration.file.YamlConfiguration();
        parsed.loadFromString((String) skills);
        assertEquals(5000, parsed.getInt("players.uuid.bonusXp"));
        assertEquals("unlocked-pages: 3\n", plain.get(NetworkDataKeys.storage()));
    }
}
