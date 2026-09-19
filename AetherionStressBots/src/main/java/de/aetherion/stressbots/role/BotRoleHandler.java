package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Kit + teleport + metadata for one bot identity.
 */
public interface BotRoleHandler {

    BotRole role();

    String prefix();

    int cap();

    void kit(Player player, CustomItem items);

    Location destination(Player player);

    String description();
}
