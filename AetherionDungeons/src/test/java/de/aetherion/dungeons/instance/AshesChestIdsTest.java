package de.aetherion.dungeons.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshesChestIdsTest {

    @Test
    void idsAreStableAndClearOfRoomAndVictoryIds() {
        int id = AshesChestIds.id(12, 98, -40);
        assertEquals(id, AshesChestIds.id(12, 98, -40));
        assertTrue(id >= AshesChestIds.BASE);
        assertTrue(id < AshesChestIds.BASE + AshesChestIds.SPAN);
        assertTrue(id > 1_100);
    }

    @Test
    void adjacentChestsDoNotShareAnId() {
        assertNotEquals(AshesChestIds.id(10, 90, -4), AshesChestIds.id(11, 90, -4));
        assertNotEquals(AshesChestIds.id(10, 90, -4), AshesChestIds.id(10, 91, -4));
        assertNotEquals(AshesChestIds.id(10, 90, -4), AshesChestIds.id(10, 90, -3));
    }

    @Test
    void scanCoversSpawnAndArena() {
        assertEquals(3, AshesChestIds.FLOOR);
        assertTrue(AshesChestIds.coversSpawn());
        assertTrue(AshesChestIds.coversArena());
        assertTrue(!AshesChestIds.inScan(AshesChestIds.minX() - 1, 0));
        assertTrue(!AshesChestIds.inScan(0, AshesChestIds.maxZ() + 8));
    }
}
