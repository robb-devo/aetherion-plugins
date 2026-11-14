package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the one character store that decide behaviour without a running server. */
class CharacterStoreRulesTest {

    @Test
    void gateAndPlainBackendsDifferOnlyWhereTheyMeanTo() {
        CharacterStore.Policy plain = CharacterStore.Policy.plain();
        CharacterStore.Policy gate = CharacterStore.Policy.gate(" dungeon ");

        assertEquals("", plain.fromServerLabel());
        assertTrue(plain.travelAsSurvival());
        assertFalse(plain.adoptLeftovers());
        assertFalse(plain.applyLegacy());

        assertEquals("dungeon", gate.fromServerLabel());
        assertFalse(gate.travelAsSurvival());
        assertTrue(gate.adoptLeftovers());
        assertTrue(gate.applyLegacy());
    }

    @Test
    void hostSettingsKeepSaneWaitsAndNormalisePeerNames() {
        CharacterHost.Settings settings = new CharacterHost.Settings(true, 10L, 10L, false,
                Arrays.asList(" MMO-D ", "mmo-r", "", null));

        assertEquals(1000L, settings.arrivalWaitMs());
        assertEquals(3000L, settings.connectTimeoutMs());
        assertEquals(Set.of("mmo-d", "mmo-r"), settings.peers());
        assertTrue(new CharacterHost.Settings(true, 6000L, 8000L, true, null).peers().isEmpty());
    }

    @Test
    void preV6SnapshotKeysAreFlattenedAgain() {
        // Bukkit split "yaml:coins.yml" at the dot: {yaml:coins -> {yml -> {players -> {<uuid> -> 12}}}}.
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("yaml:coins", Map.of("yml", Map.of("players", Map.of("u1", 12))));
        plain.put("stats", "{}");

        Map<String, Object> repaired = CharacterStore.repairLegacyKeys(plain);

        assertEquals(List.of("yaml:coins.yml", "stats"), List.copyOf(repaired.keySet()));
        assertEquals(Map.of("players.u1", 12), repaired.get("yaml:coins.yml"));
        assertEquals("{}", repaired.get("stats"));
        assertTrue(CharacterStore.repairLegacyKeys(null).isEmpty());
    }
}
