package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Fortune / skill-tool payouts owned by AetherionItems.
 * Gather plugins (Foraging tree-fell, Vein Siphon, pets) call this instead of
 * reaching into Items' harvest listener.
 */
public interface HarvestAccess {

    double fortuneOf(Player player);

    void payWood(Player player, Material material);

    void vacuumHarvest(Player player, Block block);

    void refreshMiningPower(Player player);
}
