package de.aetherion.items.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftSourceTest {

    @Test
    void compressedMatsAreResourcesGearIsNot() {
        assertTrue(CraftSource.isResourceId("compressed_bone"));
        assertTrue(CraftSource.isResourceId("compacted_oak_log"));
        assertTrue(CraftSource.isResourceId("quarry_cobblestone"));
        assertFalse(CraftSource.isResourceId("combat_sword_2"));
        assertFalse(CraftSource.isResourceId("compressed_oak_chestplate"));
        assertFalse(CraftSource.isResourceId("charm_combat"));
    }

    @Test
    void gearWinsWhenTheMatIsListedFirst() {
        assertEquals(
                "combat_sword_2",
                CraftSource.preferGearId(List.of("compressed_bone", "combat_sword_2"))
        );
        assertEquals(
                "compressed_bone",
                CraftSource.preferGearId(List.of("compressed_bone", "compacted_raw_iron"))
        );
    }
}
