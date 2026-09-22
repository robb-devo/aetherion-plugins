package de.aetherion.pit.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Locale;

public final class TransferBridge implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final String target;

    public TransferBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.target = plugin.getConfig().getString("aetherion-server", "mmo-r");
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void toAetherion(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        de.aetherion.core.AetherionCore core = de.aetherion.core.AetherionCore.get();
        if (core != null && core.link() != null) {
            if (core.link().isMainWorld()) {
                player.sendMessage("§7You are already on the main world. Use §e/capital§7.");
                return;
            }
            if (core.link().handoff(player, "capital")) {
                return;
            }
        }
        player.sendMessage("§5Aetherion§7: Crossing to §f" + target + "§7…");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(target.trim().toLowerCase(Locale.ROOT));
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception ex) {
            player.sendMessage("§cCould not transfer to Aetherion.");
            plugin.getLogger().warning("Transfer failed: " + ex.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    }
}
