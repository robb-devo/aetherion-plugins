package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.instance.worldeater.WorldEaterSite;
import de.aetherion.core.api.QuestBars;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps the Last Seed exactly as its layout says, and gives players their own sky back.
 *
 * <ul>
 *   <li>Leaving the world or the server lets go of the per-player sky, weather and world border.</li>
 *   <li>The site cannot be built on or broken (admins in creative with {@code bossengine.admin} can).</li>
 *   <li>Fluids do not flow and gravity blocks do not fall there: the eaten island stays as eaten.</li>
 *   <li>The end gateway blocks of the rift in the sky never teleport anyone.</li>
 *   <li>Inside the arena: quest bars / soft hints stay off — boss HP only.</li>
 * </ul>
 */
public class WorldEaterSiteListener implements Listener {

    private static boolean siteWorld(World world) {
        WorldEaterSite site = WorldEaterSite.get();
        return site != null && site.isSiteWorld(world);
    }

    private static boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("bossengine.admin");
    }

    /** Quest bossbar + soft NPC hints off for the whole dedicated world. */
    private static void hushHud(Player player) {
        if (player == null) {
            return;
        }
        QuestBars.suppress(player);
        try {
            Class<?> hint = Class.forName("de.aetherion.quests.ui.QuestHint");
            hint.getMethod("clear", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException ignored) {
            // Quests offline — fine.
        }
    }

    private static void restoreHud(Player player) {
        if (player == null) {
            return;
        }
        QuestBars.unsuppress(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        WorldEaterSite.forget(event.getPlayer());
        restoreHud(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (siteWorld(event.getPlayer().getWorld())) {
            hushHud(event.getPlayer());
        }
    }

    /** No natural mobs at the site: only plugin / command / egg spawns. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!siteWorld(event.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        WorldEaterSite.forget(event.getPlayer());
        if (siteWorld(event.getPlayer().getWorld())) {
            hushHud(event.getPlayer());
        } else if (siteWorld(event.getFrom())) {
            restoreHud(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (siteWorld(event.getBlock().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (siteWorld(event.getBlock().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (siteWorld(event.getBlock().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (siteWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (siteWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && siteWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (siteWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGateway(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY && siteWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }
}
