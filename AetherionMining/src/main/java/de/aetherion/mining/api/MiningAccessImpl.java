package de.aetherion.mining.api;

import de.aetherion.core.api.MiningAccess;
import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.MiningListener;
import de.aetherion.mining.MiningRespawnTimes;
import de.aetherion.mining.veins.VeinsNpcs;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MiningAccessImpl implements MiningAccess {

    @Override
    public ItemStack veinsForemanAnchor() {
        return VeinsNpcs.anchor();
    }

    @Override
    public String veinsWorldName() {
        AetherionMining plugin = AetherionMining.getInstance();
        if (plugin == null) {
            return "aether_veins";
        }
        return plugin.getConfig().getString("veins.world", "aether_veins");
    }

    @Override
    public boolean isVeinsWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = veinsWorldName();
        return name != null && world.getName().equalsIgnoreCase(name);
    }

    @Override
    public void sealForVacuum(Player player, Block block, BlockData original) {
        MiningListener.sealForVacuum(player, block, original);
    }

    @Override
    public long respawnSeconds(Material material) {
        return MiningRespawnTimes.seconds(material);
    }
}
