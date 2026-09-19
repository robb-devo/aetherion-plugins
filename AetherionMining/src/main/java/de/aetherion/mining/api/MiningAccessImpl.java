package de.aetherion.mining.api;

import de.aetherion.core.api.MiningAccess;
import de.aetherion.mining.veins.VeinsNpcs;

import org.bukkit.inventory.ItemStack;

public final class MiningAccessImpl implements MiningAccess {

    @Override
    public ItemStack veinsForemanAnchor() {
        return VeinsNpcs.anchor();
    }
}
