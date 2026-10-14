package de.aetherion.gnomon;

import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.encounter.Party;
import de.aetherion.gnomon.render.Parts;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.List;

/**
 * Boring, reliable cleanup paths: relogs and respawns get their sky back, the Gnomon's temporary walls cannot be mined,
 * blown up or pushed (so restore always finds what it placed), and a freshly loaded world is swept of orphans.
 */
final class GuardListener implements Listener {

    private final GnomonPlugin plugin;

    GuardListener(GnomonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (Party.marked(p)) {
            // A crash or quit mid-fight: hand back the sky (time, weather, border) on the next tick.
            Bukkit.getScheduler().runTask(plugin, () -> Party.restore(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        for (Encounter e : plugin.encounters()) {
            e.party().remove(event.getPlayer().getUniqueId(), event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Encounter e : plugin.encounters()) {
                if (e.party().contains(p)) {
                    return;
                }
            }
            if (Party.marked(p)) {
                Party.restore(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (owned(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::owned);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::owned);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyOwned(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyOwned(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        int n = Parts.sweep(event.getWorld());
        if (n > 0) {
            plugin.getLogger().warning("[Gnomon] Removed " + n + " orphaned display(s) in " + event.getWorld().getName());
        }
    }

    private boolean anyOwned(List<Block> blocks) {
        for (Block b : blocks) {
            if (owned(b)) {
                return true;
            }
        }
        return false;
    }

    private boolean owned(Block b) {
        for (Encounter e : plugin.encounters()) {
            if (e.blocks().owns(b)) {
                return true;
            }
        }
        return false;
    }
}
