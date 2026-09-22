package de.aetherion.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerNamesTest {

    @Test
    void livePortsMatchVelocityBackends() {
        assertEquals("hub", ServerNames.fromPort(25566));
        assertEquals("mmo-r", ServerNames.fromPort(25567));
        assertEquals("mmo-d", ServerNames.fromPort(25568));
        assertEquals("mmo-c", ServerNames.fromPort(25569));
        assertEquals("", ServerNames.fromPort(25565));
    }

    @Test
    void onlyMainWorldKeepsLocalWarps() {
        assertTrue(ServerNames.isMain("mmo-r"));
        assertTrue(ServerNames.isMain("MMO-R"));
        assertFalse(ServerNames.isMain("mmo-d"));
        assertFalse(ServerNames.isMain("hub"));
        assertFalse(ServerNames.isMain("mmo-c"));
        assertFalse(ServerNames.isMain(""));
        assertFalse(ServerNames.isMain(null));
    }
}
