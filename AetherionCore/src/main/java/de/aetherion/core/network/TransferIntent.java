package de.aetherion.core.network;

/**
 * Where a transfer snapshot may be applied. Pure so the rules can be tested
 * without a player or a disk.
 */
public final class TransferIntent {

    public enum Action {
        /** This backend is the destination. Claim and apply. */
        APPLY,
        /** Written here and the player never left. Delete it; do not touch inventory. */
        DISCARD,
        /** Destination is another backend. Leave the file alone. */
        LEAVE
    }

    private TransferIntent() {
    }

    public static Action decide(String toServer, String fromServer, String here) {
        if (toServer == null || toServer.isBlank()
                || here == null || here.isBlank()
                || "unknown".equalsIgnoreCase(here.trim())) {
            return Action.APPLY;
        }
        if (toServer.trim().equalsIgnoreCase(here.trim())) {
            return Action.APPLY;
        }
        if (fromServer != null && fromServer.trim().equalsIgnoreCase(here.trim())) {
            return Action.DISCARD;
        }
        return Action.LEAVE;
    }
}
