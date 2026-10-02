package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One character across mmo-r and mmo-d.
 * <p>
 * Every way out of a backend (transfer, quit, crash-free shutdown, kick) packs the character into
 * a snapshot and empties the local copy; every way in applies the pending snapshot once. So it
 * does not matter how the player comes back (portal, proxy default server, {@code /server},
 * reconnect after a crash in a dungeon) — the gear follows them and never exists twice.
 * <p>
 * Crash (no quit hook): the backend's own playerdata still holds the character and the ledger
 * says so. Joining the other backend then redirects the player there instead of letting them
 * play a stale or empty copy.
 * <p>
 * Dungeon session policy: <b>fresh</b>. Disconnecting inside an instance ends that run for the
 * player (party members keep theirs); on return they land at the dungeon hub with their gear as
 * of the disconnect, and start a new run.
 */
public final class CharacterSync implements Listener {

    private final Plugin plugin;
    private final TransferSnapshotStore store;
    private final TransferGuard guard;
    private final RemoteServerBridge remote;
    private final boolean enabled;
    private final long arrivalWaitMs;
    private final File mainWorldFolder;
    private final Set<UUID> guests = ConcurrentHashMap.newKeySet();

    public CharacterSync(Plugin plugin, TransferSnapshotStore store, TransferGuard guard, RemoteServerBridge remote) {
        this.plugin = plugin;
        this.store = store;
        this.guard = guard;
        this.remote = remote;
        this.enabled = remote != null && remote.enabled()
                && plugin.getConfig().getBoolean("remote-transfer.character-sync", true);
        this.arrivalWaitMs = Math.max(1000L, plugin.getConfig().getLong("remote-transfer.arrival-wait-ms", 6000L));
        this.mainWorldFolder = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getWorldFolder();
        plugin.getLogger().info("[Transfer] character-sync=" + enabled + " here=" + store.serverName()
                + " arrivalWait=" + arrivalWaitMs + "ms dcPolicy=fresh");
    }

    public boolean enabled() {
        return enabled;
    }

    /** Let in on a backend that does not hold their character (holder unreachable). */
    public boolean isGuest(UUID id) {
        return id != null && guests.contains(id);
    }

    // ------------------------------------------------------------------ leave

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        store.stageBeforeLogin(event.getUniqueId(), mainWorldFolder);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        handleLeave(event.getPlayer(), "quit");
    }

    private void handleLeave(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        TransferGuard.Phase phase = guard.phase(id);
        if (phase == TransferGuard.Phase.OUTBOUND) {
            // The hop succeeded: the snapshot is the character now, this copy must go.
            store.clearLocalCharacter(player);
            guard.release(id);
            return;
        }
        if (phase == TransferGuard.Phase.WAITING) {
            // Never applied here: the pending snapshot stays pending, nothing to pack.
            guard.release(id);
            return;
        }
        guard.release(id);
        if (guests.remove(id)) {
            plugin.getLogger().info("[Transfer] " + player.getName() + " left as a guest — character still held by "
                    + store.ledger().holder(id));
            return;
        }
        if (!enabled) {
            return;
        }
        TransferSnapshotStore.SaveResult saved = store.saveCharacter(player, reason, "", 0, false, currentRunFloor(player));
        if (saved.ok()) {
            store.clearLocalCharacter(player);
        } else {
            // Keep the playerdata copy; tell the ledger it lives here.
            store.ledger().markHolder(id, store.serverName());
        }
    }

    private int currentRunFloor(Player player) {
        de.aetherion.dungeons.AetherionDungeons main = de.aetherion.dungeons.AetherionDungeons.getInstance();
        if (main == null || main.getInstances() == null) {
            return 0;
        }
        var instances = main.getInstances();
        var session = instances.sessionOf(player);
        if (session == null || !instances.isDungeonWorld(player.getWorld())) {
            return 0;
        }
        return Math.max(1, session.floorNumber());
    }

    /** Server stop / plugin disable: Paper saves playerdata after plugins are gone, so pack now. */
    public void flushAllOnDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                handleLeave(player, "shutdown");
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("[Transfer] Shutdown pack failed for " + player.getName() + ": " + ex.getMessage());
            }
        }
        guard.releaseAll();
    }

    /** Plugin re-enabled while players are online (reload): give them their packed character back. */
    public void resyncOnlineAfterEnable(Consumer<Player> joinHook) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                joinHook.accept(player);
            }
        }, 5L);
    }

    // ------------------------------------------------------------------ arrive

    /**
     * Join hook (called at LOWEST so other plugins' join handlers see the transferred state).
     * {@code after} runs once the character is applied (never for a plain local join).
     */
    public void arrive(Player player, Consumer<TransferSnapshotStore.ApplyResult> after) {
        if (player == null || !player.isOnline()) {
            return;
        }
        TransferSnapshotStore.ApplyResult result = store.applyOnJoin(player);
        if (result.applied()) {
            finish(player, result, after);
            return;
        }
        if (!enabled) {
            return;
        }
        UUID id = player.getUniqueId();
        String here = store.serverName();
        String holder = store.ledger().holder(id);
        if (holder == null || holder.isBlank()) {
            store.ledger().markHolder(id, here);
            return;
        }
        if (holder.equalsIgnoreCase(here)) {
            return;
        }
        String expected = holder.startsWith(TransferLedger.SNAPSHOT_PREFIX)
                ? holder.substring(TransferLedger.SNAPSHOT_PREFIX.length())
                : "";
        guard.beginWaiting(player, expected);
        long deadline = System.currentTimeMillis() + arrivalWaitMs;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    guard.release(id);
                    return;
                }
                if (guard.phase(id) != TransferGuard.Phase.WAITING) {
                    cancel();
                    return;
                }
                TransferSnapshotStore.ApplyResult retry = store.applyOnJoin(player);
                if (retry.applied()) {
                    cancel();
                    finish(player, retry, after);
                    return;
                }
                if (System.currentTimeMillis() < deadline) {
                    return;
                }
                cancel();
                onArrivalTimeout(player, holder, expected, after);
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private void finish(Player player, TransferSnapshotStore.ApplyResult result,
                        Consumer<TransferSnapshotStore.ApplyResult> after) {
        guests.remove(player.getUniqueId());
        guard.settle(player, result.snapshotId(), 30L);
        if (after != null) {
            after.accept(result);
        }
    }

    private void onArrivalTimeout(Player player, String holder, String expected,
                                  Consumer<TransferSnapshotStore.ApplyResult> after) {
        UUID id = player.getUniqueId();
        String here = store.serverName();
        if (!expected.isBlank()) {
            TransferSnapshotStore.ApplyResult recovered = store.recoverFromOutbox(player, expected);
            if (recovered.applied()) {
                finish(player, recovered, after);
                return;
            }
            int kicks = store.ledger().missingKicks(id);
            plugin.getLogger().severe("[Transfer] MISSING snapshot " + expected + " for " + player.getName()
                    + " (holder=" + holder + ", here=" + here + ", attempt " + (kicks + 1)
                    + "). Not in " + store.directory().getAbsolutePath() + " nor outbox/.");
            guard.release(id);
            if (kicks < 2) {
                store.ledger().noteMissingKick(id);
                player.kickPlayer("§5Aetherion\n§7Your character is still crossing between servers.\n§7Rejoin in a few seconds — nothing is lost.");
                return;
            }
            guests.add(id);
            player.sendMessage("§cYour gear could not be located yet. Staff have the snapshot id §f" + expected
                    + "§c — nothing was deleted. You are here as a guest; this session will not overwrite it.");
            return;
        }
        boolean knownBackend = remote != null
                && (holder.equalsIgnoreCase(remote.targetServer()) || holder.equalsIgnoreCase(remote.returnServer()));
        if (!knownBackend) {
            plugin.getLogger().warning("[Transfer] Unknown holder '" + holder + "' for " + player.getName()
                    + " — taking over on " + here);
            store.ledger().markHolder(id, here);
            guard.release(id);
            return;
        }
        plugin.getLogger().warning("[Transfer] REDIRECT " + player.getName() + " → " + holder
                + " (character is held there, no snapshot pending)");
        player.sendMessage("§5Dungeon Gate§7: Your character is on §f" + holder + "§7 — taking you there…");
        remote.connect(player, holder);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || guard.phase(id) != TransferGuard.Phase.WAITING) {
                return;
            }
            guard.release(id);
            guests.add(id);
            plugin.getLogger().severe("[Transfer] " + player.getName() + " could not reach " + holder
                    + " — guest session on " + here + " (their gear stays on " + holder + ").");
            player.sendMessage("§f" + holder + " §cis not reachable right now. Your gear is safe there. "
                    + "You can look around here, but this visit will not be saved over it.");
        }, 200L);
    }
}
