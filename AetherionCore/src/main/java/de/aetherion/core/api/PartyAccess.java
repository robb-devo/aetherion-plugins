package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Cross-plugin party queries. Implemented by AetherionItems, consumed by Dungeons (etc.).
 */
public interface PartyAccess {

    boolean inParty(Player player);

    boolean isLeader(Player player);

    List<Player> onlineMembers(Player player);
}
