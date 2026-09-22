package de.aetherion.mining.api;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.MiningAccess;
import de.aetherion.core.api.ProgressAccess;
import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.MiningListener;
import de.aetherion.mining.MiningRespawnTimes;
import de.aetherion.mining.veins.VeinsNpcs;
import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Mining API for Quests / Crystal Guide / Hub {@code /amethyst}.
 *
 * <p>Player entry into the Amethyst Area:
 * <pre>{@code
 * MiningAccess mining = AetherServices.mining();
 * if (mining != null && mining.meetsVeinsMiningLevel(player)) {
 *     mining.teleportToVeinsHub(player); // Hub spawn amethyst + unlock
 * }
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
        if (player == null) {
            return false;
        }

        AetherionMining plugin = AetherionMining.getInstance();
        VeinsWorld veins = plugin == null ? null : plugin.getVeins();
        if (veins != null) {
            veins.ensureLoaded();
            veins.rememberExit(player);
        }

        HubAccess hub = AetherServices.hub();
        Location spawn = hub == null ? null : hub.location("amethyst");
        if (spawn == null || spawn.getWorld() == null) {
            spawn = defaultVeinsSpawn(veins);
        }
        if (spawn == null || spawn.getWorld() == null) {
            player.sendMessage("§cAmethyst Mines world is not loaded.");
            return false;
        }

        if (player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }

        boolean first = false;
        if (hub != null) {
            first = hub.unlockNew(player.getUniqueId(), "amethyst");
            if (!hub.isUnlocked(player, "amethyst")) {
                hub.unlock(player, "amethyst");
            }
        }

        player.teleport(spawn);
        player.setFallDistance(0f);
        player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        player.sendMessage("§7Amethyst Mines. §8Dig the zone. Hub stay put.");

        if (first) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.15f);
            player.sendMessage("§b✦ §eNew area: §fAmethyst Mines§e.");
            player.sendMessage("§7Teleport unlocked — §f/amethyst §7or Manager → Teleports.");
            ProgressAccess progress = AetherServices.progress();
            if (progress != null) {
                progress.unlock(player, "SPAWN_UNLOCKER", "Teleports", "Manager → Teleports");
            }
        }
        return true;
    }

    /** Hub planted amethyst spawn overrides this. Used until Robb places the anchor. */
    private Location defaultVeinsSpawn(VeinsWorld veins) {
        AetherionMining plugin = AetherionMining.getInstance();
        World world = veins == null ? null : veins.world();
        if (world == null && plugin != null) {
            world = org.bukkit.Bukkit.getWorld(veinsWorldName());
        }
        if (world == null) {
            return null;
        }
        double x = plugin == null ? 8.5 : plugin.getConfig().getDouble("veins.spawn-x", 8.5);
        double y = plugin == null ? 18.0 : plugin.getConfig().getDouble("veins.spawn-y", 18.0);
        double z = plugin == null ? 8.5 : plugin.getConfig().getDouble("veins.spawn-z", 8.5);
        float yaw = plugin == null ? 0f : (float) plugin.getConfig().getDouble("veins.spawn-yaw", 0);
        float pitch = plugin == null ? 0f : (float) plugin.getConfig().getDouble("veins.spawn-pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public boolean meetsVeinsMiningLevel(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp() || player.hasPermission("aetherion.mines.admin")) {
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
