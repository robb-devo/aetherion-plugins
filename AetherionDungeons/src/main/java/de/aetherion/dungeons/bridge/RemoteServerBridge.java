package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Velocity / BungeeCord server transfer via the legacy {@code BungeeCord} plugin channel.
 * <p>
 * <b>The</b> send pipeline. Portal, {@code /dungeon transfer|enter|boss|home}, {@code /dhub},
 * the Dev Menu floor buttons and the pending-floor auto-enter all end in {@link #send}:
 * flush → pack the character once ({@link TransferSnapshotStore#saveCharacter}) → freeze
 * ({@link TransferGuard}) → Connect. The local copy is only emptied when the player really
 * leaves (quit hook); if the proxy never moves them, the untouched snapshot is taken back.
 */
public final class RemoteServerBridge implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final TransferSnapshotStore snapshots;
    private final TransferGuard guard;
    private final boolean enabled;
    private final String targetServer;
    private final String returnServer;
    private final boolean dungeonRole;
    private final boolean skipWarmOnHub;
    private final long connectTimeoutMs;
    private CharacterSync sync;

    public RemoteServerBridge(Plugin plugin, TransferSnapshotStore snapshots, TransferGuard guard) {
        this.plugin = plugin;
        this.snapshots = snapshots;
        this.guard = guard;
        plugin.saveDefaultConfig();
        String role = plugin.getConfig().getString("role", "hub");
        this.dungeonRole = "dungeon".equalsIgnoreCase(role);
        this.enabled = plugin.getConfig().getBoolean("remote-transfer.enabled", false);
        this.targetServer = plugin.getConfig().getString("remote-transfer.target-server", "mmo-d");
        this.returnServer = plugin.getConfig().getString("remote-transfer.return-server", "mmo-r");
        this.skipWarmOnHub = plugin.getConfig().getBoolean("remote-transfer.skip-warm-on-hub",
                plugin.getConfig().getBoolean("remote-transfer.skip-floor2-warm-on-hub", true));
        this.connectTimeoutMs = Math.max(3000L, plugin.getConfig().getLong("remote-transfer.connect-timeout-ms", 8000L));

        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Dungeon role=" + role
                + " remoteTransfer=" + enabled
                + " target=" + targetServer
                + " here=" + (snapshots == null ? "?" : snapshots.serverName())
                + " transfer=v" + TransferSnapshotStore.CHAR_VERSION);
    }

    /** Wired after construction (CharacterSync needs this bridge for redirects). */
    public void setCharacterSync(CharacterSync sync) {
        this.sync = sync;
    }

    public boolean isDungeonRole() {
        return dungeonRole;
    }

    public boolean isHubRole() {
        return !dungeonRole;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Hub with remote-transfer: every dungeon floor runs on mmo-d. */
    public boolean remoteDungeonsEnabled() {
        return enabled && isHubRole();
    }

    /** @deprecated use {@link #remoteDungeonsEnabled()} */
    @Deprecated
    public boolean remoteFloor2Enabled() {
        return remoteDungeonsEnabled();
    }

    public boolean shouldWarmDungeons() {
        if (dungeonRole) {
            return true;
        }
        // Hub skips warm pools when instances live on mmo-d.
        return !(enabled && skipWarmOnHub);
    }

    /** @deprecated use {@link #shouldWarmDungeons()} */
    @Deprecated
    public boolean shouldWarmFloor2() {
        return shouldWarmDungeons();
    }

    public String targetServer() {
        return targetServer;
    }

    public String returnServer() {
        return returnServer;
    }

    public boolean transferToDungeon(Player player) {
        return transferToDungeon(player, 0, false);
    }

    /**
     * @param pendingFloor 0 = land in dungeon hub only; 1–3 / 6 = auto-enter that floor on mmo-d
     */
    public boolean transferToDungeon(Player player, int pendingFloor, boolean bossOnly) {
        return send(player, targetServer, pendingFloor, bossOnly, pendingFloor > 0 ? "floor" : "dungeon-hub");
    }

    public boolean transferHome(Player player) {
        return send(player, returnServer, 0, false, "home");
    }

    /**
     * The single hop: pack once, freeze, Connect. Returns false when nothing left this server.
     */
    public boolean send(Player player, String server, int pendingFloor, boolean bossOnly, String reason) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!enabled) {
            player.sendMessage("§cRemote dungeon transfer is disabled (config).");
            return false;
        }
        if (server == null || server.isBlank()) {
            player.sendMessage("§cNo target server configured.");
            return false;
        }
        UUID id = player.getUniqueId();
        if (guard != null && guard.isHeld(id)) {
            // Already crossing (double portal tick, double click). One hop only.
            return true;
        }
        if (player.isDead()) {
            player.sendMessage("§cRespawn first, then use the gate.");
            return false;
        }
        if (sync != null && sync.isGuest(id)) {
            // Guest session: the real character lives on another backend's playerdata.
            // Do not pack this copy over it — just walk them over.
            player.sendMessage("§5Dungeon Gate§7: Crossing to §f" + server + "§7…");
            return connect(player, server);
        }
        TransferSnapshotStore.SaveResult saved = snapshots == null
                ? TransferSnapshotStore.SaveResult.failed("no snapshot store")
                : snapshots.saveCharacter(player, reason, server, pendingFloor, bossOnly, 0);
        if (!saved.ok()) {
            player.sendMessage("§cThe gate refused: your gear could not be packed §7(" + saved.error() + ")§c. Nothing moved, nothing was lost.");
            return false;
        }
        String snapshotId = saved.snapshotId();
        if (guard != null) {
            guard.beginOutbound(player, snapshotId, connectTimeoutMs, () -> rollback(player, snapshotId, server));
        }
        if (pendingFloor > 0) {
            player.sendMessage("§5Dungeon Gate§7: Crossing to §f" + server
                    + "§7 · Floor §f" + pendingFloor + "§7…");
        } else {
            player.sendMessage("§5Dungeon Gate§7: Crossing to §f" + server + "§7…");
        }
        if (!connect(player, server)) {
            rollback(player, snapshotId, server);
            return false;
        }
        return true;
    }

    /** Connect never moved the player: take the snapshot back if nobody claimed it. */
    private void rollback(Player player, String snapshotId, String server) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            // They left after all — the quit hook owns the rest.
            if (guard != null) {
                guard.release(id);
            }
            return;
        }
        boolean reclaimed = snapshots != null && snapshots.reclaimUnclaimed(id, snapshotId);
        if (guard != null) {
            guard.release(id);
        }
        if (reclaimed) {
            player.sendMessage("§5Dungeon Gate§7: §f" + server + " §7did not answer. You stay here — nothing moved.");
            return;
        }
        // Claimed elsewhere, yet the player is still here. Keep the live copy (no deletion);
        // this backend is the holder again, the next hop overwrites the stale remote copy
        // (with a quarantine backup).
        if (snapshots != null) {
            snapshots.ledger().markHolder(id, snapshots.serverName());
        }
        plugin.getLogger().warning("[Transfer] id=" + snapshotId + " was claimed on " + server + " but "
                + player.getName() + " is still here — keeping the live inventory as the truth.");
        player.sendMessage("§5Dungeon Gate§7: The crossing stalled. You stay here — your gear is safe.");
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
            plugin.getLogger().log(Level.WARNING, "Failed to transfer " + player.getName() + " → " + server, ex);
            player.sendMessage("§cCould not transfer to §f" + server + "§c.");
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // No-op — we only send Connect.
    }
}
