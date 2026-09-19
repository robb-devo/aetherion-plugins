package de.aetherion.items.manager;

import de.aetherion.items.model.ItemCapability;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface StatProvider {

    double getStat(
            Player player,
            ItemCapability capability
    );

    /**
     * Extra multiplier applied after gear + flat provider stats.
     * 1.0 means unchanged.
     */
    default double getMultiplier(Player player, ItemCapability capability) {
        return 1.0;
    }
}