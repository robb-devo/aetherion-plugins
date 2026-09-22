package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferProgressGuardTest {

    @Test
    void defaultLevelWithoutSkillsDoesNotReplaceALiveBar() {
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 275, false, true));
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 275, false, false));
        assertFalse(TransferProgressGuard.applySnapshotLevel(0, 40, false, false));
    }

    @Test
    void defaultLevelDoesNotReplaceSharedSkillsWhenTheBarHasNotSynced() {
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 1, false, true));
    }

    @Test
    void untrustedDefaultBarIsNeverApplied() {
        assertFalse(TransferProgressGuard.levelFieldTrusted(1, true));
        assertTrue(TransferProgressGuard.levelFieldTrusted(1, false));
        assertTrue(TransferProgressGuard.levelFieldTrusted(275, true));
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 275, false, true, false));
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 1, false, false, false));
    }

    @Test
    void freshArrivalStillAcceptsLevelOne() {
        assertTrue(TransferProgressGuard.applySnapshotLevel(1, 1, false, false));
        assertTrue(TransferProgressGuard.applySnapshotLevel(12, 12, false, false));
    }

    @Test
    void importedSkillsOwnTheBar() {
        assertFalse(TransferProgressGuard.applySnapshotLevel(1, 275, true, true));
        assertFalse(TransferProgressGuard.applySnapshotLevel(275, 275, true, true));
    }

    @Test
    void emptyInventoryDoesNotReplaceALiveOne() {
        assertFalse(TransferProgressGuard.applyItemSection(0, 12, true));
        assertFalse(TransferProgressGuard.applyItemSection(4, 12, false));
    }

    @Test
    void populatedOrMatchingEmptyInventoriesApply() {
        assertTrue(TransferProgressGuard.applyItemSection(8, 2, true));
        assertTrue(TransferProgressGuard.applyItemSection(0, 0, true));
    }

    @Test
    void freshSkillSectionDoesNotOverwriteRicherDisk() {
        Map<String, Object> rich = new LinkedHashMap<>();
        rich.put("bonusXp", 80_000L);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("mining", Map.of("level", 40, "xp", 12));
        rich.put("progress", progress);

        Map<String, Object> fresh = new LinkedHashMap<>();
        fresh.put("bonusXp", 0);
        fresh.put("slots", java.util.List.of("", "", "", "", "", "", ""));

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("players.lime", fresh);
        Map<String, Object> disk = new LinkedHashMap<>();
        disk.put("players.lime", rich);

        Map<String, Object> kept = TransferProgressGuard.filterSkillWrites(incoming, disk);
        assertTrue(kept.isEmpty());
        assertTrue(TransferProgressGuard.richness(rich) > TransferProgressGuard.richness(fresh));
        assertTrue(TransferProgressGuard.refuseFreshSkillOverwrite(true, true));
        assertFalse(TransferProgressGuard.refuseFreshSkillOverwrite(false, true));
    }

    @Test
    void realSkillSectionStillWrites() {
        Map<String, Object> incomingPlayer = Map.of("bonusXp", 10L, "progress", Map.of("combat", Map.of("level", 3, "xp", 4)));
        Map<String, Object> incoming = Map.of("players.lime", incomingPlayer);
        Map<String, Object> disk = Map.of("players.lime", Map.of("bonusXp", 1L));
        Map<String, Object> kept = TransferProgressGuard.filterSkillWrites(incoming, disk);
        assertEquals(1, kept.size());
    }
}
