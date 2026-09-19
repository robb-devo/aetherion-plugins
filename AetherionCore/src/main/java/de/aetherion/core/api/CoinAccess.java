package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Coin wallet surface used by Guilds and network transfer.
 * Implemented by AetherionItems {@code CoinService}.
 */
public interface CoinAccess {

    long get(Player player);

    boolean take(Player player, long amount);

    void add(Player player, long amount);

    void save();

    void reloadFromDisk();

    /**
     * Overlay imported yaml values onto in-memory maps (does not clear other players).
     * Null amounts are skipped.
     */
    void applyImported(UUID playerId, Long balance, Long lifetime);
}
