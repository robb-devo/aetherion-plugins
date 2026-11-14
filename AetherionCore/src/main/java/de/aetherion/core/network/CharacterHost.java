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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * One character across every character backend: the way out, the way in, and the hop between.
 *
 * <ul>
 *     <li>every way out (hop, quit, shutdown) packs the character; a hop also empties the local copy
 *     once the player has really left;</li>
 *     <li>every way in applies the pending snapshot once (ledger), whichever backend wrote it;</li>
 *     <li>crash without a quit hook: the ledger still names the backend that holds the character, and
 *     joining anywhere else redirects there instead of playing a stale or empty copy.</li>
 * </ul>
 *
 * <p>There is one host per backend and one implementation for all of them. Where AetherionDungeons runs
 * (mmo-r, mmo-d) it builds the host from its own config and calls {@link #arrive} from its join hook, because
 * the Dungeon Gate has things to do once the character is in place. Everywhere else Core arms it itself
 * ({@link #createIfApplicable}). Never on the hub: gear does not travel from the lobby.
 */
public final class CharacterHost implements Listener, CharacterTransport {

    public static final String CHANNEL = "BungeeCord";

    /**
     * What the owner of a host decides.
     *
     * @param sync             pack on quit and shutdown, stage files before login, and follow the ledger on
     *                         join. Off, only hops move a character (single-server setups).
     * @param arrivalWaitMs    how long a join waits for a snapshot the ledger says is in flight
     * @param connectTimeoutMs how long a hop may hang before the snapshot is taken back
     * @param ownsJoin         the host applies the character on {@code PlayerJoinEvent} itself; off, the
     *                         owner calls {@link #arrive}
     * @param peers            backend names that count as character backends besides
     *                         {@link ServerNames#CHARACTER_BACKENDS}
     */
    public record Settings(boolean sync, long arrivalWaitMs, long connectTimeoutMs, boolean ownsJoin,
                           Collection<String> peers) {

        public Settings {
            arrivalWaitMs = Math.max(1000L, arrivalWaitMs);
            connectTimeoutMs = Math.max(3000L, connectTimeoutMs);
            Set<String> names = new HashSet<>();
            if (peers != null) {
                for (String peer : peers) {
                    if (peer != null && !peer.isBlank()) {
                        names.add(peer.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            peers = Set.copyOf(names);
        }
    }

    private final Plugin plugin;
    private final CharacterStore store;
    private final CharacterGuard guard;
    private final Settings settings;
    private final Set<UUID> guests = ConcurrentHashMap.newKeySet();
    private final Set<UUID> packedOnStop = ConcurrentHashMap.newKeySet();
    private volatile ToIntFunction<Player> runFloor = player -> 0;
    private volatile File mainWorldFolder;

    /**
     * Builds a host; the caller registers {@link #guard()} and the host itself as listeners of {@code plugin}.
     */
    public CharacterHost(Plugin plugin, CharacterStore store, Settings settings) {
        this.plugin = plugin;
        this.store = store;
        this.settings = settings;
        this.guard = new CharacterGuard(plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /**
     * @return the armed host, or null when this backend must not own characters through Core (hub, Dungeons
     *         present, unknown name, or {@code network.character.host: off})
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
        CharacterStore store = new CharacterStore(plugin, transferDir, here, CharacterStore.Policy.plain());
        CharacterHost host = new CharacterHost(plugin, store, new Settings(
                true,
                plugin.getConfig().getLong("network.character.arrival-wait-ms", 6000L),
                plugin.getConfig().getLong("network.character.connect-timeout-ms", 8000L),
                true,
                List.of()));
        plugin.getServer().getPluginManager().registerEvents(host.guard, plugin);
        plugin.getServer().getPluginManager().registerEvents(host, plugin);
        int keepDays = Math.max(1, plugin.getConfig().getInt("network.character.keep-history-days", 3));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.prune(keepDays));
        plugin.getLogger().info("[Transfer] Core character host armed · here=" + store.serverName()
                + " dir=" + transferDir.getAbsolutePath()
                + " arrivalWait=" + host.settings.arrivalWaitMs() + "ms v" + CharacterStore.CHAR_VERSION);
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

    public Settings settings() {
        return settings;
    }

    /**
     * Which floor of a dungeon run the player is in right now (0 = none). Written into a quit or shutdown
     * snapshot so the arriving side can tell them that run is over.
     */
    public void runFloor(ToIntFunction<Player> lookup) {
        this.runFloor = lookup == null ? player -> 0 : lookup;
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

    /** Let in on a backend that does not hold their character (holder unreachable). */
    public boolean isGuest(UUID id) {
        return id != null && guests.contains(id);
    }

    @Override
    public boolean send(Player player, String server, String reason, String departLine) {
        return send(player, server, reason, departLine, 0, false);
    }

    /**
     * The single hop: pack once, freeze, Connect. Returns false when nothing left this server.
     *
     * @param departLine   chat line on departure; null = "Crossing to …", blank = none
     * @param pendingFloor dungeon floor to open on arrival (0 = none)
     */
    public boolean send(Player player, String server, String reason, String departLine,
                        int pendingFloor, boolean bossOnly) {
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
        CharacterStore.SaveResult saved = store.saveCharacter(player,
                reason == null || reason.isBlank() ? "travel" : reason, target, pendingFloor, bossOnly, 0);
        if (!saved.ok()) {
            player.sendMessage("§cYour gear could not be packed for the crossing §7(" + saved.error()
                    + ")§c. Nothing moved, nothing was lost.");
            return false;
        }
        String snapshotId = saved.snapshotId();
        guard.beginOutbound(player, snapshotId, settings.connectTimeoutMs(), () -> rollback(player, snapshotId, target));
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
        if (!settings.sync() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        store.stageBeforeLogin(event.getUniqueId(), mainWorldFolder());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        handleLeave(event.getPlayer(), "quit");
    }

    /**
     * Server stop: Paper disables plugins before it saves playerdata, so the quit hook never runs. Pack while
     * the data plugins are still enabled — the first of them going down is the cue. An owner that packs in its
     * own {@code onDisable} ({@link #flushAllOnDisable}) is covered too: each player is packed once.
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
        if (!settings.sync()) {
            return;
        }
        // Same-server quit / shutdown: the snapshot is a backup only. Never empty the live inventory —
        // Paper must keep writing playerdata, otherwise a hard restart (or a missed APPLY) leaves an empty .dat.
        CharacterStore.SaveResult saved = store.saveCharacter(player, reason, "", 0, false, currentRunFloor(player));
        if (!saved.ok()) {
            // Keep the playerdata copy; tell the ledger it lives here.
            store.ledger().markHolder(id, store.serverName());
        }
    }

    private int currentRunFloor(Player player) {
        try {
            return Math.max(0, runFloor.applyAsInt(player));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Transfer] Could not read the dungeon floor of " + player.getName()
                    + ": " + ex.getMessage());
            return 0;
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
        if (settings.ownsJoin()) {
            arrive(event.getPlayer());
        }
    }

    public void arrive(Player player) {
        arrive(player, null);
    }

    /**
     * The join path, also for a plugin reload with players online. {@code after} runs once the character is
     * applied (never for a plain local join).
     */
    public void arrive(Player player, Consumer<CharacterStore.ApplyResult> after) {
        if (player == null || !player.isOnline()) {
            return;
        }
        CharacterStore.ApplyResult result = store.applyOnJoin(player);
        if (result.applied()) {
            finish(player, result, after);
            return;
        }
        if (!settings.sync()) {
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
        long deadline = System.currentTimeMillis() + settings.arrivalWaitMs();
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
        if (!isCharacterBackend(holder)) {
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

    private boolean isCharacterBackend(String holder) {
        return ServerNames.isCharacterBackend(holder)
                || (holder != null && settings.peers().contains(holder.trim().toLowerCase(Locale.ROOT)));
    }

    private File mainWorldFolder() {
        File folder = mainWorldFolder;
        if (folder == null && !Bukkit.getWorlds().isEmpty()) {
            folder = Bukkit.getWorlds().getFirst().getWorldFolder();
            mainWorldFolder = folder;
        }
        return folder;
    }

    /** Admin readout ({@code /aetransfer}, {@code /dungeon sync}). */
    public String describe(UUID id) {
        CharacterGuard.Phase phase = guard.phase(id);
        return store.describe(id) + " phase=" + (phase == null ? "-" : phase) + (isGuest(id) ? " guest" : "");
    }

    static boolean isDataPlugin(Plugin plugin) {
        return plugin != null && List.of("AetherionGuilds", "AetherMobs", "AetherionQuests", "AetherionItems")
                .contains(plugin.getName());
    }
}
