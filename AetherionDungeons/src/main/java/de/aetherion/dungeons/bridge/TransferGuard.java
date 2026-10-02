package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freezes a player's inventory while their character is between two backends.
 * <ul>
 *     <li>{@link Phase#OUTBOUND}: snapshot written, Velocity Connect sent. Anything the player
 *     changed now would exist twice (here and in the snapshot) — so nothing changes.</li>
 *     <li>{@link Phase#WAITING}: joined, the character file is not there yet (proxy switch raced
 *     the quit hook, shared folder latency). Playing naked here would be a second truth.</li>
 *     <li>{@link Phase#SETTLING}: applied; the tick-25 re-assert has not run yet.</li>
 * </ul>
 * Held players are invulnerable and cannot drop, pick up, click, place, consume or run commands.
 */
public final class TransferGuard implements Listener {

    public enum Phase {
        OUTBOUND,
        WAITING,
        SETTLING
    }

    public record Hold(Phase phase, String snapshotId, long startedAt, BukkitTask task) {
    }

    private final Plugin plugin;
    private final Map<UUID, Hold> holds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public TransferGuard(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isHeld(UUID id) {
        return id != null && holds.containsKey(id);
    }

    public Hold hold(UUID id) {
        return id == null ? null : holds.get(id);
    }

    public Phase phase(UUID id) {
        Hold hold = hold(id);
        return hold == null ? null : hold.phase();
    }

    /** Outbound hold; {@code onTimeout} runs on the main thread if the player is still here. */
    public void beginOutbound(Player player, String snapshotId, long timeoutMs, Runnable onTimeout) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        long ticks = Math.max(20L, timeoutMs / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Hold current = holds.get(id);
            if (current == null || current.phase() != Phase.OUTBOUND || !snapshotId.equals(current.snapshotId())) {
                return;
            }
            if (onTimeout != null) {
                onTimeout.run();
            }
        }, ticks);
        replace(id, new Hold(Phase.OUTBOUND, snapshotId, System.currentTimeMillis(), task));
    }

    public void beginWaiting(Player player, String expectedId) {
        if (player == null) {
            return;
        }
        replace(player.getUniqueId(), new Hold(Phase.WAITING, expectedId == null ? "" : expectedId, System.currentTimeMillis(), null));
    }

    public void settle(Player player, String snapshotId, long ticks) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Hold current = holds.get(id);
            if (current != null && current.phase() == Phase.SETTLING) {
                holds.remove(id);
            }
        }, Math.max(1L, ticks));
        replace(id, new Hold(Phase.SETTLING, snapshotId == null ? "" : snapshotId, System.currentTimeMillis(), task));
    }

    public void release(UUID id) {
        if (id == null) {
            return;
        }
        Hold removed = holds.remove(id);
        if (removed != null && removed.task() != null) {
            removed.task().cancel();
        }
    }

    public void releaseAll() {
        for (UUID id : Map.copyOf(holds).keySet()) {
            release(id);
        }
    }

    private void replace(UUID id, Hold next) {
        Hold previous = holds.put(id, next);
        if (previous != null && previous.task() != null) {
            previous.task().cancel();
        }
    }

    // ------------------------------------------------------------------ freeze

    private boolean block(Player player, Cancellable event) {
        if (player == null || !isHeld(player.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > 1500L) {
            lastNotice.put(player.getUniqueId(), now);
            Phase phase = phase(player.getUniqueId());
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    phase == Phase.OUTBOUND ? "Crossing the gate… hold still." : "Syncing your gear… one moment."));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            block(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            block(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent event) {
        // Settling players may see join GUIs (clicks stay blocked until the re-assert ran).
        if (event.getPlayer() instanceof Player player && phase(player.getUniqueId()) != Phase.SETTLING) {
            block(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            block(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (block(event.getPlayer(), event)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isHeld(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
