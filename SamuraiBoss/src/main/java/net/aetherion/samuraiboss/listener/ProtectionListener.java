package net.aetherion.samuraiboss.listener;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.arena.Arena;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Schützt das Dojo, solange es "arbeitet" (Bau, Kampf, Wiederaufbau, Abriss): Spieler können
 * nichts abbauen/setzen, Explosionen/Feuer/Wasser verändern nichts. Außerhalb dieser Zeiten ist
 * das Dojo eine ganz normale Baustelle für Admins.
 */
public final class ProtectionListener implements Listener {

    private final SamuraiBossPlugin plugin;

    public ProtectionListener(SamuraiBossPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Location l) {
        Arena a = plugin.arenas().at(l);
        if (a == null) return false;
        if (a.state == Arena.State.IDLE) return false;
        if (a.state == Arena.State.FIGHT) return plugin.settings().protectDuringFight;
        return true; // BUILDING, REBUILDING, REMOVING
    }

    private boolean locked(Block b) {
        return locked(b.getLocation());
    }

    private void deny(Cancellable c, Player p) {
        c.setCancelled(true);
        if (p != null) p.sendActionBar(plugin.msgPlain("<gray>Das Dojo ist während des Kampfes geschützt.</gray>"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getBlock())) deny(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getBlock())) deny(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (locked(e.getBlock())) deny(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (locked(e.getBlock())) deny(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::locked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::locked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (locked(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (locked(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (locked(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (locked(e.getToBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (plugin.arenas().at(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (locked(e.getBlock())) e.setCancelled(true);
    }
}
