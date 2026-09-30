package de.aetherion.dungeons.bridge;

import java.util.Collection;
import java.util.Locale;

/**
 * Whether a transfer snapshot may replace the destination player's gear.
 *
 * <p><b>Omit means keep the destination inventory.</b> Core v5 hub transfers
 * write {@code inventory-omitted: true} and leave out {@code inventory-b64},
 * {@code armor-b64}, {@code extra-b64}, {@code enderchest-b64}, and
 * {@code cursor-b64} (log line {@code inv=omitted}). A missing blob is not an
 * empty inventory. The apply path must not clear storage, armor, offhand,
 * ender chest, or the cursor in that case.
 *
 * <p>Blobs that are present still apply. Dungeons v4 always writes those keys,
 * including an all-air inventory, and {@code from-server: hub} on v4 is the
 * mmo-r role rather than the Velocity lobby. Core v5 uses {@code from-server: hub}
 * for the Velocity lobby; that source keeps destination gear unless hub
 * inventory transfer is explicitly enabled and the blobs are actually present.
 */
public final class InventorySnapshotPolicy {

    /** Velocity lobby name written by Core. Not the Dungeons role string on v4. */
    static final String VELOCITY_HUB = "hub";

    private InventorySnapshotPolicy() {
    }

    /**
     * @param version snapshot {@code version} (Dungeons writes 4; Core hub isolation writes 5)
     * @param inventoryOmitted writer flag {@code inventory-omitted}
     * @param fromServer snapshot {@code from-server}
     * @param inventoryBlobsPresent true only when inventory, armor, extra, and ender blobs are all present
     * @param transferInventoryFromHub Core {@code network.transfer-inventory-from-hub} (default false)
     * @param ignoreFrom Core {@code network.ignore-inventory-from}
     * @return true when destination gear must be left untouched
     */
    public static boolean keepDestination(
            int version,
            boolean inventoryOmitted,
            String fromServer,
            boolean inventoryBlobsPresent,
            boolean transferInventoryFromHub,
            Collection<String> ignoreFrom
    ) {
        if (inventoryOmitted || !inventoryBlobsPresent) {
            return true;
        }
        // v4 "hub" is the mmo-r role and carries blobs. Only Core v5 names the lobby.
        if (version < 5) {
            return false;
        }
        return omitSource(fromServer, transferInventoryFromHub, ignoreFrom);
    }

    static boolean omitSource(String fromServer, boolean transferInventoryFromHub, Collection<String> ignoreFrom) {
        if (fromServer == null || fromServer.isBlank()) {
            return false;
        }
        String name = fromServer.trim().toLowerCase(Locale.ROOT);
        if (VELOCITY_HUB.equals(name) && !transferInventoryFromHub) {
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
}
