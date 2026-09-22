package de.aetherion.items.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoosterPersistTest {

    @Test
    void upgradeKeepsBoosterFlatsWhenOldBaseIsZero() {
        ItemStats oldActual = new ItemStats();
        oldActual.setDefense(4.0);
        oldActual.setDamage(15.0);
        oldActual.setCoalBoosters(2);

        ItemStats oldBase = new ItemStats();
        oldBase.setDamage(10.0);

        ItemStats newBase = new ItemStats();
        newBase.setDamage(20.0);

        ItemStats merged = ItemStats.upgradeFrom(oldActual, oldBase, newBase);
        assertEquals(25.0, merged.getDamage(), 0.001);
        assertEquals(4.0, merged.getDefense(), 0.001);
        assertEquals(2, merged.getCoalBoosters());
    }

    @Test
    void socketCodecRoundTrip() {
        BoosterType[] slots = BoosterSockets.empty();
        slots[0] = BoosterType.COAL;
        slots[3] = BoosterType.LAPIS;
        slots[13] = BoosterType.BIRCH;
        BoosterType[] decoded = BoosterSockets.decode(BoosterSockets.encode(slots));
        assertEquals(BoosterType.COAL, decoded[0]);
        assertNull(decoded[1]);
        assertEquals(BoosterType.LAPIS, decoded[3]);
        assertEquals(BoosterType.BIRCH, decoded[13]);
        assertEquals(14, decoded.length);
    }

    @Test
    void countsFillSocketsInOrder() {
        ItemStats stats = new ItemStats();
        stats.setCoalBoosters(2);
        stats.setIronBoosters(1);
        BoosterType[] slots = BoosterSockets.fromCounts(stats);
        assertEquals(BoosterType.COAL, slots[0]);
        assertEquals(BoosterType.COAL, slots[1]);
        assertEquals(BoosterType.IRON, slots[2]);
        assertNull(slots[3]);
    }
}
