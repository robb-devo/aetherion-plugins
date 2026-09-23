package de.aetherion.core.network;

import java.util.Collection;
import java.util.Locale;

/**
 * Hub is join + pit. Its snapshots must not carry inventory onto MMO backends.
 * MMO-R ↔ MMO-D stays intact because those names are not hub and are not listed.
 */
public final class InventoryTransfer {

    private InventoryTransfer() {
    }

    /**
     * @param transferInventoryFromHub config {@code network.transfer-inventory-from-hub};
     *                                 false (the default) isolates hub
     * @param ignoreFrom config {@code network.ignore-inventory-from}; extra Velocity names
     */
    public static boolean omit(String fromServer, boolean transferInventoryFromHub, Collection<String> ignoreFrom) {
        if (fromServer == null || fromServer.isBlank()) {
            return false;
        }
        String name = fromServer.trim().toLowerCase(Locale.ROOT);
        if (ServerNames.HUB.equals(name) && !transferInventoryFromHub) {
            return true;
        }
        if (ignoreFrom == null) {
            return false;
        }
        for (String raw : ignoreFrom) {
            if (raw != null && name.equals(raw.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Writer flag or source-name policy. Either one is enough to leave destination gear alone. */
    public static boolean omitSnapshot(
            String fromServer,
            boolean inventoryOmittedFlag,
            boolean transferInventoryFromHub,
            Collection<String> ignoreFrom
    ) {
        return inventoryOmittedFlag || omit(fromServer, transferInventoryFromHub, ignoreFrom);
    }
}
