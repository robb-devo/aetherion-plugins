package de.aetherion.pit.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One Velocity Connect to the MMO backend. No MainWorldLink handoff, no pending-warp —
 * players land on mmo-r's normal join (harbour). FancyNPC often fires twice; debounce stops that.
 */
public final class TransferBridge implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";
    private static final long CONNECT_COOLDOWN_MS = 2500L;

    private final JavaPlugin plugin;
    private final String target;
    private final String displayName;
    private final Map<UUID, Long> connectingUntil = new ConcurrentHashMap<>();

    public TransferBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        String server = plugin.getConfig().getString("aetherion-server", "mmo-r");
        this.target = server == null || server.isBlank() ? "mmo-r" : server.trim().toLowerCase(Locale.ROOT);
        String label = plugin.getConfig().getString("aetherion-display-name", "Aetherion");
        this.displayName = label == null || label.isBlank() ? "Aetherion" : label.trim();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void toAetherion(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = connectingUntil.get(player.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        connectingUntil.put(player.getUniqueId(), now + CONNECT_COOLDOWN_MS);
        player.sendMessage("§5✦ §7Crossing to §f" + displayName + "§7…");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception ex) {
            connectingUntil.remove(player.getUniqueId());
            player.sendMessage("§cCould not transfer to " + displayName + ".");
            plugin.getLogger().warning("Transfer failed: " + ex.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    }
}
