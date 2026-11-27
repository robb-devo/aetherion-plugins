package de.aetherion.foraging;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static org.bukkit.event.Event.Result.ALLOW;

/**
 * Lets living trees through WorldGuard's break protection. Its own listener since 2026-11-27, so the plugin also
 * loads on a server without WorldGuard (the island server): registered only when WorldGuard is there.
 */
final class WorldGuardLogAllow implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardBreak(BreakBlockEvent event) {
        if (ForagingListener.allowsLog(event.getCause().getFirstBlock())) {
            event.setResult(ALLOW);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (ForagingListener.allowsLog(block)) {
                event.setResult(ALLOW);
                return;
            }
        }
    }
}
