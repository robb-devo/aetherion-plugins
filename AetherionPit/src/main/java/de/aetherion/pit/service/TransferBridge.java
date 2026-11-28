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
 *
 * <p>Since the tutorial isle: a player without a character is sent to Morningbell first ({@link TutorialGate}
 * decides), and {@code /tutorial} takes anybody there for a visit.
 */
public final class TransferBridge implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";
    private static final long CONNECT_COOLDOWN_MS = 2500L;
    /** Still in the lobby this long after a connect to the isle: it did not take them. */
    private static final long ISLE_WAIT_TICKS = 140L;

    private final JavaPlugin plugin;
    private final String target;
    private final String displayName;
    private final TutorialGate gate;
    private final Map<UUID, Long> connectingUntil = new ConcurrentHashMap<>();

    public TransferBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        String server = plugin.getConfig().getString("aetherion-server", "mmo-r");
        this.target = server == null || server.isBlank() ? "mmo-r" : server.trim().toLowerCase(Locale.ROOT);
        String label = plugin.getConfig().getString("aetherion-display-name", "Aetherion");
        this.displayName = label == null || label.isBlank() ? "Aetherion" : label.trim();
        this.gate = new TutorialGate(plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void toAetherion(Player player) {
        if (player == null || !player.isOnline() || debounced(player)) {
            return;
        }
        boolean isle = false;
        try {
            isle = gate.toIsle(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Tutorial gate failed for " + player.getName() + ": " + ex);
        }
        if (isle) {
            player.sendMessage(german(player)
                    ? "§e✦ §7Erster Halt: §fMorningbell§7, die Insel, auf der alle anfangen…"
                    : "§e✦ §7First stop: §fMorningbell§7, the island where everyone starts…");
            if (connect(player, gate.isleServer())) {
                watchIsle(player);
            }
            return;
        }
        player.sendMessage("§5✦ §7Crossing to §f" + displayName + "§7…");
        connect(player, target);
    }

    /** {@code /tutorial} in the lobby: to the isle, as a newcomer or as a visitor. */
    public void toTutorial(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean de = german(player);
        if (!gate.enabled()) {
            player.sendMessage(de ? "§7Morningbell ist gerade geschlossen." : "§7Morningbell is closed right now.");
            return;
        }
        if (debounced(player)) {
            return;
        }
        boolean ok;
        try {
            ok = gate.visit(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Tutorial visit failed for " + player.getName() + ": " + ex);
            ok = false;
        }
        if (!ok) {
            player.sendMessage(de ? "§7Morningbell ist gerade geschlossen." : "§7Morningbell is closed right now.");
            return;
        }
        player.sendMessage(de
                ? "§e✦ §7Auf nach §fMorningbell§7. Dein Charakter bleibt unangetastet, wo er ist: Auf der Insel startest du mit leeren Taschen."
                : "§e✦ §7Off to §fMorningbell§7. Your character stays where it is, untouched: on the island you start with empty pockets.");
        if (connect(player, gate.isleServer())) {
            watchIsle(player);
        }
    }

    /** The isle did not take them: say so; a newcomer who was turned away twice goes to the main world. */
    private void watchIsle(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean de = german(player);
            boolean toMain;
            try {
                toMain = gate.missed(player);
            } catch (RuntimeException ex) {
                toMain = false;
            }
            if (toMain) {
                player.sendMessage(de
                        ? "§7Die Fähre nach Morningbell fährt gerade nicht. Es geht direkt nach §f" + displayName + "§7…"
                        : "§7The ferry to Morningbell is not sailing right now. Taking you straight to §f" + displayName + "§7…");
                connectingUntil.remove(player.getUniqueId());
                connect(player, target);
            } else {
                player.sendMessage(de
                        ? "§7Die Fähre nach Morningbell antwortet gerade nicht. Versuch es gleich noch mal."
                        : "§7The ferry to Morningbell is not answering right now. Try again in a moment.");
            }
        }, ISLE_WAIT_TICKS);
    }

    private boolean debounced(Player player) {
        long now = System.currentTimeMillis();
        Long until = connectingUntil.get(player.getUniqueId());
        if (until != null && until > now) {
            return true;
        }
        connectingUntil.put(player.getUniqueId(), now + CONNECT_COOLDOWN_MS);
        return false;
    }

    private boolean connect(Player player, String server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            return true;
        } catch (Exception ex) {
            connectingUntil.remove(player.getUniqueId());
            player.sendMessage("§cCould not transfer to " + displayName + ".");
            plugin.getLogger().warning("Transfer failed: " + ex.getMessage());
            return false;
        }
    }

    private static boolean german(Player player) {
        Locale locale = player.locale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    }
}
