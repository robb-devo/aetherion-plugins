package de.aetherion.dungeons.bridge;

import de.aetherion.core.api.CharacterTransport;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Exposes the Dungeons send pipeline (pack → freeze → Connect → rollback, quit hook empties the local copy)
 * to Core's network router, so hops to mmo-c / mmo-i from mmo-r / mmo-d use the same v6 character
 * protocol as the dungeon gate. Dungeons stays the owner of join + quit on these backends.
 */
public final class DungeonCharacterTransport implements CharacterTransport {

    private final RemoteServerBridge remote;
    private final TransferSnapshotStore snapshots;
    private final TransferGuard guard;

    public DungeonCharacterTransport(RemoteServerBridge remote, TransferSnapshotStore snapshots, TransferGuard guard) {
        this.remote = remote;
        this.snapshots = snapshots;
        this.guard = guard;
    }

    @Override
    public String serverName() {
        return snapshots.serverName();
    }

    @Override
    public boolean send(Player player, String server, String reason, String departLine) {
        return remote.send(player, server, 0, false, reason == null || reason.isBlank() ? "travel" : reason,
                departLine == null ? "" : departLine);
    }

    @Override
    public boolean isHeld(UUID id) {
        return guard.isHeld(id);
    }

    @Override
    public boolean isArriving(UUID id) {
        return guard.phase(id) == TransferGuard.Phase.WAITING;
    }
}
