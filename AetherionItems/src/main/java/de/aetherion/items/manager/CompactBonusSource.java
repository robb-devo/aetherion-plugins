package de.aetherion.items.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Extra compact / compacted chances from other plugins, such as equipped pets.
 */
public interface CompactBonusSource {

    double extraCompactChance(Player player, Material drop);

    double extraCompactedUpgradeChance(Player player, Material drop);
}
