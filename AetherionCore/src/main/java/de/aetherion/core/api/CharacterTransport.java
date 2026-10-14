package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The one way a character leaves this backend for another one (transfer v6: one character,
 * one owner, ledger in the shared transfer folder).
 *
 * <p>mmo-r / mmo-d register AetherionDungeons' pipeline (it owns join + quit there).
 * mmo-c / mmo-i have no Dungeons jar; Core's own port ({@code CharacterHost}) serves them.
 * Hub has no transport on purpose (join + pit, gear never travels from there).
 */
public interface CharacterTransport {

    /** Velocity name of this backend as the transport (and the ledger) sees it. */
    String serverName();

    /**
     * Pack the character, freeze the player, Velocity Connect. The local copy is emptied only when the
     * player really leaves; if the proxy never moves them, the untouched snapshot is taken back.
     *
     * @param departLine chat line shown on departure (flavour, never a server id); null = transport default
     * @return true when this call owns the hop (the caller must not teleport locally)
     */
    boolean send(Player player, String server, String reason, String departLine);

    /** Outbound / waiting / settling: the player is between two backends right now. */
    boolean isHeld(UUID id);

    /** Joined, but the character file has not been applied yet. */
    default boolean isArriving(UUID id) {
        return false;
    }
}
