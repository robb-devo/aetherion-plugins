package de.aetherion.mining.api;

import de.aetherion.core.api.MiningAccess;
import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.MiningListener;
import de.aetherion.mining.MiningRespawnTimes;
import de.aetherion.mining.veins.VeinsNpcs;
import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Mining API surface for Quests / Elder Vale NPC / Hub unlocks.
 *
 * <p>Elder Vale NPC entry into Amethyst Mines:
 * <pre>{@code
 * MiningAccess mining = AetherServices.mining();
 * if (mining != null && mining.meetsVeinsMiningLevel(player)) {
 *     mining.teleportToVeinsHub(player); // TPs + unlocks /amethyst
 * }
 * // Or after your own TP: AetherServices.hub().unlock(player, "amethyst");
 * }</pre>
 */
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

    @Override
    public boolean teleportToVeinsHub(Player player) {
        AetherionMining plugin = AetherionMining.getInstance();
        if (plugin == null || player == null) {
            return false;
        }
        VeinsWorld veins = plugin.getVeins();
        return veins != null && veins.enter(player);
    }

    @Override
    public boolean meetsVeinsMiningLevel(Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("aetherion.mines.admin")) {
            return true;
        }
        int required = veinsMinMiningLevel();
        org.bukkit.plugin.Plugin items = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return true;
        }
        try {
            Object skills = items.getClass().getMethod("getSkills").invoke(items);
            if (skills == null) {
                return true;
            }
            Object level = skills.getClass().getMethod("miningLevel", Player.class).invoke(skills, player);
            return level instanceof Number number && number.intValue() >= required;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    @Override
    public int veinsMinMiningLevel() {
        AetherionMining plugin = AetherionMining.getInstance();
        if (plugin == null) {
            return 30;
        }
        return Math.max(1, plugin.getConfig().getInt("veins.min-mining-level", 30));
    }
}
