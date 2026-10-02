package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Velocity / BungeeCord server transfer via the legacy {@code BungeeCord} plugin channel.
 */
public final class RemoteServerBridge implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final TransferSnapshotStore snapshots;
    private final boolean enabled;
    private final String targetServer;
    private final String returnServer;
    private final boolean dungeonRole;
    private final boolean skipWarmOnHub;

    public RemoteServerBridge(Plugin plugin, TransferSnapshotStore snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
        plugin.saveDefaultConfig();
        String role = plugin.getConfig().getString("role", "hub");
        this.dungeonRole = "dungeon".equalsIgnoreCase(role);
        this.enabled = plugin.getConfig().getBoolean("remote-transfer.enabled", false);
        this.targetServer = plugin.getConfig().getString("remote-transfer.target-server", "mmo-d");
        this.returnServer = plugin.getConfig().getString("remote-transfer.return-server", "mmo-r");
        this.skipWarmOnHub = plugin.getConfig().getBoolean("remote-transfer.skip-warm-on-hub",
                plugin.getConfig().getBoolean("remote-transfer.skip-floor2-warm-on-hub", true));

        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Dungeon role=" + role
                + " remoteTransfer=" + enabled
                + " target=" + targetServer);
    }

    public boolean isDungeonRole() {
        return dungeonRole;
    }

    public boolean isHubRole() {
        return !dungeonRole;
    }

    /** Hub with remote-transfer: every dungeon floor runs on mmo-d. */
    public boolean remoteDungeonsEnabled() {
        return enabled && isHubRole();
    }

    /** @deprecated use {@link #remoteDungeonsEnabled()} */
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
    public boolean shouldWarmFloor2() {
        return shouldWarmDungeons();
    }

    public boolean transferToDungeon(Player player) {
        return transferToDungeon(player, 0, false);
    }

    /**
     * @param pendingFloor 0 = land in dungeon hub only; 1–3 / 6 = auto-enter that floor on mmo-d
     */
    public boolean transferToDungeon(Player player, int pendingFloor, boolean bossOnly) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!enabled) {
            player.sendMessage("§cRemote dungeon transfer is disabled (config).");
            return false;
        }
        if (snapshots != null) {
            snapshots.save(player, pendingFloor, bossOnly);
        }
        if (pendingFloor > 0) {
            player.sendMessage("§5Dungeon Gate§7: Crossing to §f" + targetServer
                    + "§7 · Floor §f" + pendingFloor + "§7…");
        } else {
            player.sendMessage("§5Dungeon Gate§7: Crossing to §f" + targetServer + "§7…");
        }
        return connect(player, targetServer);
    }

    public boolean transferHome(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (snapshots != null) {
            snapshots.save(player, 0, false);
        }
        return connect(player, returnServer);
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
