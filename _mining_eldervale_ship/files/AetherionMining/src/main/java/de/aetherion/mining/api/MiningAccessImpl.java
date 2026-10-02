package de.aetherion.mining.api;

import de.aetherion.core.api.MiningAccess;
import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.MiningListener;
import de.aetherion.mining.MiningRespawnTimes;
import de.aetherion.mining.isle.MineIsle;
import de.aetherion.mining.isle.MineWorld;
import de.aetherion.mining.veins.VeinsNpcs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Core {@link MiningAccess}: The Veins bridge (unchanged) plus the Mining Eldervale hooks the Items
 * harvest listener and the DEV "Mining Island" hub call. Every isle call is a no-op while the isle
 * router is missing (disabled in config, or still starting).
 */
public final class MiningAccessImpl implements MiningAccess {

    private MineIsle isle() {
        AetherionMining plugin = AetherionMining.getInstance();
        return plugin == null ? null : plugin.getIsle();
    }

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

    // ------------------------------------------------------------------ Mining Eldervale

    @Override
    public boolean onMineIsle(Location at) {
        AetherionMining plugin = AetherionMining.getInstance();
        return plugin != null && isle() != null && MineWorld.onIsle(plugin, at);
    }

    @Override
    public double oreFortuneBonus(Player player, Material ore, Location at) {
        MineIsle isle = isle();
        return isle == null ? 0.0d : isle.oreFortune(player, ore, at);
    }

    @Override
    public double orePowerBonus(Player player, Material ore) {
        MineIsle isle = isle();
        return isle == null ? 0.0d : isle.orePower(player, ore);
    }

    @Override
    public double oreSpeedPower(Player player, Material ore, Location at) {
        MineIsle isle = isle();
        return isle == null ? 0.0d : isle.speedPower(player, ore, at);
    }

    @Override
    public int oreHarvestBonus(Player player, Material ore, Location at) {
        MineIsle isle = isle();
        return isle == null ? 0 : isle.harvestBonus(player, ore, at);
    }

    @Override
    public String oreHarvestBonusLabel(Player player, Location at) {
        MineIsle isle = isle();
        return isle == null ? null : isle.harvestBonusLabel(player, at);
    }

    @Override
    public void noteOreHarvest(Player player, Material ore, Location at, boolean vacuum) {
        MineIsle isle = isle();
        if (isle != null) {
            isle.noteVacuum(player, ore, at);
        }
    }

    @Override
    public void onForgeStart(Player player, int tier, Location anvil) {
        MineIsle isle = isle();
        if (isle != null) {
            isle.onForgeStart(player, tier, anvil);
        }
    }

    @Override
    public void onForged(Player player, int tier, Location anvil) {
        MineIsle isle = isle();
        if (isle != null) {
            isle.onForged(player, tier, anvil);
        }
    }

    @Override
    public String devAction(Player player, String action) {
        MineIsle isle = isle();
        return isle == null ? "§cMining Eldervale is disabled (mine-isle.enabled)." : isle.dev().action(player, action);
    }

    @Override
    public Map<String, ItemStack> devItems(String group) {
        MineIsle isle = isle();
        return isle == null ? Map.of() : isle.dev().items(group);
    }

    @Override
    public List<String> devStatus() {
        MineIsle isle = isle();
        return isle == null ? List.of("§cMining Eldervale disabled") : isle.dev().status();
    }

    @Override
    public Map<String, Location> devSpots() {
        MineIsle isle = isle();
        return isle == null ? Map.of() : isle.dev().spots(false);
    }
}
