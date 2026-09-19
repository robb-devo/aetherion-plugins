package de.aetherion.items.api;

import de.aetherion.core.api.HarvestAccess;
import de.aetherion.items.listener.HarvestListener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Items harvest payouts exposed to gather plugins via {@link de.aetherion.core.api.AetherServices}.
 */
public final class HarvestAccessImpl implements HarvestAccess {

    private final HarvestListener harvest;

    public HarvestAccessImpl(HarvestListener harvest) {
        this.harvest = harvest;
    }

    @Override
    public double fortuneOf(Player player) {
        return harvest == null ? 0.0d : harvest.fortuneOf(player);
    }

    @Override
    public void payWood(Player player, Material material) {
        if (harvest != null) {
            harvest.payWood(player, material);
        }
    }

    @Override
    public void vacuumHarvest(Player player, Block block) {
        if (harvest != null) {
            harvest.vacuumHarvest(player, block);
        }
    }

    @Override
    public void refreshMiningPower(Player player) {
        if (harvest != null) {
            harvest.refreshMiningPower(player);
        }
    }
}
