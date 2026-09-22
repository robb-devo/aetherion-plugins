package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.DungeonAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Locale;
import java.util.logging.Level;

/**
 * One path for main-world warps: already on {@code mmo-r} means a local teleport;
 * every other Velocity backend connects to {@code mmo-r} with a pending destination.
 * The snapshot never clears the live inventory.
 */
public final class MainWorldLink implements PluginMessageListener, Listener {

    public static final String CHANNEL = "BungeeCord";

    private final AetherionCore plugin;
    private final TransferSnapshotStore snapshots;
    private final String configuredServer;
    private final String mainServer;
    private volatile String velocityServer = "";

    public MainWorldLink(AetherionCore plugin, TransferSnapshotStore snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
        String configured = plugin.getConfig().getString("network.this-server", "");
        this.configuredServer = configured == null ? "" : configured.trim();
        String main = plugin.getConfig().getString("network.main-server", ServerNames.MAIN);
        this.mainServer = main == null || main.isBlank() ? ServerNames.MAIN : main.trim();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Network server=" + serverName()
                + " main=" + mainServer
                + " port=" + Bukkit.getPort()
                + (isMainWorld() ? " (local warps)" : " (warps connect to " + mainServer + ")"));
    }

    public TransferSnapshotStore snapshots() {
        return snapshots;
    }

    public String mainServer() {
        return mainServer;
    }

    /**
     * Config name, else the Velocity GetServer answer, else the live port map.
     */
    public String serverName() {
        if (!configuredServer.isBlank()) {
            return configuredServer;
        }
        if (velocityServer != null && !velocityServer.isBlank()) {
            return velocityServer;
        }
        String fromPort = ServerNames.fromPort(Bukkit.getPort());
        return fromPort.isBlank() ? "unknown" : fromPort;
    }

    public boolean isMainWorld() {
        return ServerNames.isMain(serverName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> requestServerName(player), 5L);
    }

    public void requestServerName(Player player) {
        if (player == null || !player.isOnline() || !configuredServer.isBlank()) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("GetServer");
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "GetServer failed for " + player.getName(), ex);
        }
    }

    /**
     * @return true when this server is not mmo-r and the caller must not teleport locally
     */
    public boolean handoff(Player player, String spawnId) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (isMainWorld()) {
            return false;
        }
        DungeonAccess dungeons = AetherServices.dungeons();
        if (dungeons != null) {
            dungeons.leaveInstance(player);
        }
        String warp = spawnId == null ? "" : spawnId.trim().toLowerCase(Locale.ROOT);
        long savedAt = snapshots == null ? -1L : snapshots.save(player, 0, false, warp.isBlank() ? null : warp);
        if (savedAt < 0L) {
            player.sendMessage("§cCould not save your gear for the transfer. Your inventory was not touched.");
            return true;
        }
        String where = warp.isBlank() ? "Capital" : warp;
        player.sendMessage("§5Gate§7: Crossing to §f" + mainServer + "§7 · §f" + where + "§7…");
        boolean sent = connect(player, mainServer);
        if (!sent) {
            if (snapshots != null) {
                snapshots.discardIfUnclaimed(player.getUniqueId(), savedAt);
            }
            player.sendMessage("§cCould not reach the main world. Your inventory was not touched.");
            return true;
        }
        java.util.UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player still = Bukkit.getPlayer(id);
            if (still != null && still.isOnline() && snapshots != null) {
                snapshots.discardIfUnclaimed(id, savedAt);
                plugin.getLogger().info("Connect to " + mainServer + " did not move " + still.getName()
                        + " — snapshot discarded, inventory left in place.");
            }
        }, 300L);
        return true;
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
        if (!CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String sub = in.readUTF();
            if (!"GetServer".equals(sub)) {
                return;
            }
            String name = in.readUTF();
            if (name != null && !name.isBlank()) {
                velocityServer = name.trim();
                plugin.getLogger().info("Velocity server name is " + velocityServer);
            }
        } catch (Exception ignored) {
            // Not a GetServer reply.
        }
    }
}
