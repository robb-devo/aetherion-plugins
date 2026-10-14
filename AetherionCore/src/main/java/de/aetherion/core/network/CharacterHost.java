package de.aetherion.core.network;

import de.aetherion.core.api.CharacterTransport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * One character across every character backend — Core's half (mmo-c, mmo-i).
 *
 * <p>Port of AetherionDungeons' {@code CharacterSync} + the send pipeline of its
 * {@code RemoteServerBridge}. mmo-r / mmo-d keep running the Dungeons originals; both sides write the
 * same snapshot files and the same ledger, so the character follows the player whichever way they move:
 * <ul>
 *     <li>every way out (hop, quit, shutdown) packs the character; a hop also empties the local copy
 *     once the player has really left;</li>
 *     <li>every way in applies the pending snapshot once (ledger), whichever backend wrote it;</li>
 *     <li>crash without a quit hook: the ledger still names the backend that holds the character, and
 *     joining anywhere else redirects there instead of playing a stale or empty copy.</li>
 * </ul>
 *
 * <p>Arms only on a character backend that has no AetherionDungeons jar (Dungeons owns join/quit where it
 * runs) and never on the hub.
 */
public final class CharacterHost implements Listener, CharacterTransport {

    public static final String CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final CharacterStore store;
    private final CharacterGuard guard;
    private final long arrivalWaitMs;
    private final long connectTimeoutMs;
    private final Set<UUID> guests = ConcurrentHashMap.newKeySet();
    private final Set<UUID> packedOnStop = ConcurrentHashMap.newKeySet();
    private volatile File mainWorldFolder;

    private CharacterHost(JavaPlugin plugin, CharacterStore store) {
        this.plugin = plugin;
        this.store = store;
        this.guard = new CharacterGuard(plugin);
        this.arrivalWaitMs = Math.max(1000L, plugin.getConfig().getLong("network.character.arrival-wait-ms", 6000L));
        this.connectTimeoutMs = Math.max(3000L, plugin.getConfig().getLong("network.character.connect-timeout-ms", 8000L));
    }

    /**
     * @return the armed host, or null when this backend must not own characters (hub, Dungeons present,
     *         unknown name, or {@code network.character.host: off})
     */
    public static CharacterHost createIfApplicable(JavaPlugin plugin, String here, File transferDir) {
        String mode = plugin.getConfig().getString("network.character.host", "auto");
        mode = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        if (mode.equals("off") || mode.equals("false")) {
            plugin.getLogger().info("[Transfer] Core character host off by config.");
            return null;
        }
        boolean dungeonsHere = plugin.getServer().getPluginManager().getPlugin("AetherionDungeons") != null;
        if (dungeonsHere) {
            plugin.getLogger().info("[Transfer] AetherionDungeons owns the character on " + here + " — Core host idle.");
            return null;
        }
        if (!ServerNames.isCharacterBackend(here) && !mode.equals("on")) {
            plugin.getLogger().info("[Transfer] " + here + " is not a character backend — Core host idle"
                    + (ServerNames.isHub(here) ? " (hub: join + pit, gear never travels)." : "."));
            return null;
        }
        CharacterStore store = new CharacterStore(plugin, transferDir, here);
        CharacterHost host = new CharacterHost(plugin, store);
        plugin.getServer().getPluginManager().registerEvents(host.guard, plugin);
        plugin.getServer().getPluginManager().registerEvents(host, plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        int keepDays = Math.max(1, plugin.getConfig().getInt("network.character.keep-history-days", 3));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.prune(keepDays));
        plugin.getLogger().info("[Transfer] Core character host armed · here=" + store.serverName()
                + " dir=" + transferDir.getAbsolutePath()
                + " arrivalWait=" + host.arrivalWaitMs + "ms v" + CharacterStore.CHAR_VERSION);
        // Core reload with players online: hand back what was packed on disable.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                host.arrive(player);
            }
        }, 5L);
        return host;
    }

    public CharacterStore store() {
        return store;
    }

    public CharacterGuard guard() {
        return guard;
    }

    // ------------------------------------------------------------------ CharacterTransport

    @Override
    public String serverName() {
        return store.serverName();
    }

    @Override
    public boolean isHeld(UUID id) {
        return guard.isHeld(id);
    }

    @Override
    public boolean isArriving(UUID id) {
        return guard.phase(id) == CharacterGuard.Phase.WAITING;
    }

    public boolean isGuest(UUID id) {
        return id != null && guests.contains(id);
    }

    /**
     * The single hop: pack once, freeze, Connect. Returns false when nothing left this server.
     */
    @Override
    public boolean send(Player player, String server, String reason, String departLine) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (server == null || server.isBlank()) {
            return false;
        }
        String target = server.trim().toLowerCase(Locale.ROOT);
        UUID id = player.getUniqueId();
        if (guard.isHeld(id)) {
            // Already crossing (double click, double portal tick). One hop only.
            return true;
        }
        if (player.isDead()) {
            player.sendMessage("§cRespawn first, then travel.");
            return false;
        }
        if (guests.contains(id)) {
            // Guest session: the real character lives in another backend's playerdata. Do not pack
            // this copy over it — just walk them over.
            say(player, departLine, target);
            return connect(player, target);
        }
        CharacterStore.SaveResult saved = store.saveCharacter(player, reason == null ? "travel" : reason, target);
        if (!saved.ok()) {
            player.sendMessage("§cYour gear could not be packed for the crossing §7(" + saved.error()
                    + ")§c. Nothing moved, nothing was lost.");
            return false;
        }
        String snapshotId = saved.snapshotId();
        guard.beginOutbound(player, snapshotId, connectTimeoutMs, () -> rollback(player, snapshotId, target));
        say(player, departLine, target);
        if (!connect(player, target)) {
            rollback(player, snapshotId, target);
            return false;
        }
        return true;
    }

    private static void say(Player player, String departLine, String target) {
        if (departLine == null) {
            player.sendMessage("§5✦ §7Crossing to §f" + ServerNames.display(target) + "§7…");
        } else if (!departLine.isBlank()) {
            player.sendMessage(departLine);
        }
    }

    /** Connect never moved the player: take the snapshot back if nobody claimed it. */
    private void rollback(Player player, String snapshotId, String server) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            // They left after all — the quit hook owns the rest.
            guard.release(id);
            return;
        }
        boolean reclaimed = store.reclaimUnclaimed(id, snapshotId);
        guard.release(id);
        if (reclaimed) {
            player.sendMessage("§7The way to §f" + ServerNames.display(server) + " §7is closed right now. You stay here — nothing moved.");
            return;
        }
        // Claimed elsewhere, yet the player is still here. Keep the live copy (no deletion); this backend
        // is the holder again, the next hop overwrites the stale remote copy (with a quarantine backup).
        store.ledger().markHolder(id, store.serverName());
        plugin.getLogger().warning("[Transfer] id=" + snapshotId + " was claimed on " + server + " but "
                + player.getName() + " is still here — keeping the live inventory as the truth.");
        player.sendMessage("§7The crossing stalled. You stay here — your gear is safe.");
    }

    public boolean connect(Player player, String server) {
        if (player == null || server == null || server.isBlank()) {
            return false;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server.trim().toLowerCase(Locale.ROOT));
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Transfer] Connect failed for " + player.getName() + " → " + server, ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ leave

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        store.stageBeforeLogin(event.getUniqueId(), mainWorldFolder());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        handleLeave(event.getPlayer(), "quit");
    }

    /**
     * Server stop: Paper disables plugins (Core last) before it saves playerdata, so the quit hook never
     * runs. Pack while the data plugins are still enabled — the first of them going down is the cue.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!Bukkit.isStopping()) {
            return;
        }
        if (!isDataPlugin(event.getPlugin())) {
            return;
        }
        flushAllOnDisable();
    }

    private void handleLeave(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        CharacterGuard.Phase phase = guard.phase(id);
        if (phase == CharacterGuard.Phase.OUTBOUND) {
            // The hop succeeded: the snapshot is the character now, this copy must go.
            store.clearLocalCharacter(player);
            guard.release(id);
            return;
        }
        if (phase == CharacterGuard.Phase.WAITING) {
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
        // Same-server quit / shutdown: the snapshot is a backup only. Never empty the live inventory —
        // Paper must keep writing playerdata, otherwise a hard restart (or a missed APPLY) leaves an empty .dat.
        CharacterStore.SaveResult saved = store.saveCharacter(player, reason, "");
        if (!saved.ok()) {
            // Keep the playerdata copy; tell the ledger it lives here.
            store.ledger().markHolder(id, store.serverName());
        }
    }

    /** Server stop / plugin disable: pack every online character once. */
    public void flushAllOnDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!packedOnStop.add(player.getUniqueId())) {
                continue;
            }
            try {
                handleLeave(player, "shutdown");
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("[Transfer] Shutdown pack failed for " + player.getName() + ": " + ex.getMessage());
            }
        }
        guard.releaseAll();
    }

    // ------------------------------------------------------------------ arrive

    /**
     * LOWEST: the character must be in place before other plugins' join handlers read it
     * (loadouts, quests, pets, islands).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        packedOnStop.remove(event.getPlayer().getUniqueId());
        arrive(event.getPlayer());
    }

    public void arrive(Player player) {
        arrive(player, null);
    }

    /** {@code after} runs once the character is applied (never for a plain local join). */
    public void arrive(Player player, Consumer<CharacterStore.ApplyResult> after) {
        if (player == null || !player.isOnline()) {
            return;
        }
        CharacterStore.ApplyResult result = store.applyOnJoin(player);
        if (result.applied()) {
            finish(player, result, after);
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
        String expected = holder.startsWith(CharacterLedger.SNAPSHOT_PREFIX)
                ? holder.substring(CharacterLedger.SNAPSHOT_PREFIX.length())
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
                if (guard.phase(id) != CharacterGuard.Phase.WAITING) {
                    cancel();
                    return;
                }
                CharacterStore.ApplyResult retry = store.applyOnJoin(player);
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

    private void finish(Player player, CharacterStore.ApplyResult result, Consumer<CharacterStore.ApplyResult> after) {
        guests.remove(player.getUniqueId());
        guard.settle(player, result.snapshotId(), 30L);
        if (after != null) {
            after.accept(result);
        }
    }

    private void onArrivalTimeout(Player player, String holder, String expected,
                                  Consumer<CharacterStore.ApplyResult> after) {
        UUID id = player.getUniqueId();
        String here = store.serverName();
        if (!expected.isBlank()) {
            CharacterStore.ApplyResult recovered = store.recoverFromOutbox(player, expected);
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
                player.kickPlayer("§5Aetherion\n§7Your character is still on its way.\n§7Rejoin in a few seconds — nothing is lost.");
                return;
            }
            guests.add(id);
            store.stashLiveForGuest(player);
            player.sendMessage("§cYour gear could not be located yet. Staff have the snapshot id §f" + expected
                    + "§c — nothing was deleted. You are here as a guest; this visit will not overwrite it.");
            return;
        }
        if (!ServerNames.isCharacterBackend(holder)) {
            plugin.getLogger().warning("[Transfer] Unknown holder '" + holder + "' for " + player.getName()
                    + " — taking over on " + here);
            store.ledger().markHolder(id, here);
            guard.release(id);
            return;
        }
        plugin.getLogger().warning("[Transfer] REDIRECT " + player.getName() + " → " + holder
                + " (character is held there, no snapshot pending)");
        player.sendMessage("§7You are still expected on §f" + ServerNames.display(holder) + "§7 — taking you there…");
        connect(player, holder);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || guard.phase(id) != CharacterGuard.Phase.WAITING) {
                return;
            }
            guard.release(id);
            guests.add(id);
            store.stashLiveForGuest(player);
            plugin.getLogger().severe("[Transfer] " + player.getName() + " could not reach " + holder
                    + " — guest session on " + here + " (their gear stays on " + holder + ").");
            player.sendMessage("§f" + ServerNames.display(holder) + " §cis out of reach right now. Your gear is safe there "
                    + "and stays there — you can look around here as a guest; this visit is not saved over it.");
        }, 200L);
    }

    private File mainWorldFolder() {
        File folder = mainWorldFolder;
        if (folder == null && !Bukkit.getWorlds().isEmpty()) {
            folder = Bukkit.getWorlds().getFirst().getWorldFolder();
            mainWorldFolder = folder;
        }
        return folder;
    }

    /** Admin readout ({@code /aenet where}). */
    public String describe(UUID id) {
        CharacterGuard.Phase phase = guard.phase(id);
        return store.describe(id) + " phase=" + (phase == null ? "-" : phase) + (isGuest(id) ? " guest" : "");
    }

    static boolean isDataPlugin(Plugin plugin) {
        return plugin != null && List.of("AetherionGuilds", "AetherMobs", "AetherionQuests", "AetherionItems")
                .contains(plugin.getName());
    }
}
