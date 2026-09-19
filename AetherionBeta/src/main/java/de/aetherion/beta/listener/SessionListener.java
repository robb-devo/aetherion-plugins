package de.aetherion.beta.listener;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.Milestone;
import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.core.AetherEntities;
import de.aetherion.core.AetherKeys;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

public final class SessionListener implements Listener {

    private final AetherionBeta plugin;

    public SessionListener(AetherionBeta plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        data.setName(player.getName());
        data.startSession();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.npcs().hideNewNpcFromOthers(player);
            plugin.npcs().ensureFor(player);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        data.endSession();
        plugin.npcs().despawnFor(player);
        plugin.store().unload(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpc(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (plugin.npcs().isBetaNpc(entity)) {
            event.setCancelled(true);
            plugin.npcs().interact(event.getPlayer(), entity);
            return;
        }
        if (AetherEntities.isQuestNpc(entity)) {
            plugin.store().get(event.getPlayer().getUniqueId()).mark(Milestone.QUESTS, true);
        }
        if (entity.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
            plugin.store().get(event.getPlayer().getUniqueId()).mark(Milestone.PET, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onGatherBreak(BlockBreakEvent event) {
        if (!isGatherMaterial(event.getBlock().getType())) {
            return;
        }
        plugin.store().get(event.getPlayer().getUniqueId()).mark(Milestone.GATHER, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        plugin.store().get(event.getPlayer().getUniqueId()).mark(Milestone.GATHER, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoss(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            plugin.store().get(killer.getUniqueId()).mark(Milestone.BOSS, true);
        }
        for (Player player : event.getEntity().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(event.getEntity().getLocation()) <= 48 * 48) {
                plugin.store().get(player.getUniqueId()).mark(Milestone.BOSS, true);
            }
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        String name = event.getPlayer().getWorld().getName().toLowerCase();
        BetaPlayerData data = plugin.store().get(event.getPlayer().getUniqueId());
        if (name.startsWith("aedun_")) {
            data.mark(Milestone.DUNGEON, true);
        }
        if (name.equals("aether_islands") || name.equals("aether_guilds")) {
            data.mark(Milestone.ISLAND, true);
        }
    }

    private static boolean isGatherMaterial(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LOGS.isTagged(material)
                || Tag.LEAVES.isTagged(material)
                || Tag.CROPS.isTagged(material)
                || Tag.COAL_ORES.isTagged(material)
                || Tag.COPPER_ORES.isTagged(material)
                || Tag.IRON_ORES.isTagged(material)
                || Tag.GOLD_ORES.isTagged(material)
                || Tag.DIAMOND_ORES.isTagged(material)
                || Tag.EMERALD_ORES.isTagged(material)
                || Tag.LAPIS_ORES.isTagged(material)
                || Tag.REDSTONE_ORES.isTagged(material)) {
            return true;
        }
        return switch (material) {
            case STONE, DEEPSLATE, NETHERRACK, BLACKSTONE, BASALT,
                    COBBLESTONE, COBBLED_DEEPSLATE,
                    WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART,
                    SUGAR_CANE, BAMBOO, CACTUS, PUMPKIN, MELON,
                    SWEET_BERRY_BUSH, COCOA, GLOW_BERRIES,
                    ANCIENT_DEBRIS, NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> true;
            default -> false;
        };
    }
}
