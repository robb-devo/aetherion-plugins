package de.aetherion.dungeons.bridge;

import de.aetherion.core.network.CharacterHost;
import de.aetherion.core.network.CharacterStore;
import de.aetherion.core.network.ServerNames;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The Dungeon Gate's side of the network: role, target and return server, and the gate wording.
 * <p>
 * The character itself moves through Core's {@link CharacterHost}, the same host mmo-c / mmo-i run, built here
 * from this plugin's {@code remote-transfer} config with {@link CharacterStore.Policy#gate}. Portal,
 * {@code /dungeon transfer|enter|boss|home}, {@code /dhub}, the Dev Menu floor buttons and the pending-floor
 * auto-enter all end in {@link #send}: pack the character once, freeze, Connect. The local copy is only emptied
 * when the player really leaves; if the proxy never moves them, the untouched snapshot is taken back.
 */
public final class RemoteServerBridge {

    private final CharacterHost host;
    private final boolean enabled;
    private final String targetServer;
    private final String returnServer;
    private final boolean dungeonRole;
    private final boolean skipWarmOnHub;

    public RemoteServerBridge(Plugin plugin) {
        plugin.saveDefaultConfig();
        var config = plugin.getConfig();
        String role = config.getString("role", "hub");
        this.dungeonRole = "dungeon".equalsIgnoreCase(role);
        this.enabled = config.getBoolean("remote-transfer.enabled", false);
        this.targetServer = config.getString("remote-transfer.target-server", "mmo-d");
        this.returnServer = config.getString("remote-transfer.return-server", "mmo-r");
        this.skipWarmOnHub = config.getBoolean("remote-transfer.skip-warm-on-hub",
                config.getBoolean("remote-transfer.skip-floor2-warm-on-hub", true));

        CharacterStore store = new CharacterStore(plugin, transferDir(plugin), thisServer(plugin, role),
                CharacterStore.Policy.gate(role));
        this.host = new CharacterHost(plugin, store, new CharacterHost.Settings(
                enabled && config.getBoolean("remote-transfer.character-sync", true),
                config.getLong("remote-transfer.arrival-wait-ms", 6000L),
                config.getLong("remote-transfer.connect-timeout-ms", 8000L),
                false,
                List.of(targetServer, returnServer)));
        plugin.getLogger().info("Dungeon role=" + role
                + " remoteTransfer=" + enabled
                + " target=" + targetServer
                + " here=" + store.serverName()
                + " transfer=v" + CharacterStore.CHAR_VERSION
                + " character-sync=" + host.settings().sync()
                + " dir=" + store.directory().getAbsolutePath());
    }

    /** {@code remote-transfer.shared-dir}: absolute, or relative to the plugin folder; blank = own folder. */
    private static File transferDir(Plugin plugin) {
        String configured = plugin.getConfig().getString("remote-transfer.shared-dir", "");
        if (configured == null || configured.isBlank()) {
            return new File(plugin.getDataFolder(), "transfer-snapshots");
        }
        File candidate = new File(configured);
        return candidate.isAbsolute() ? candidate : new File(plugin.getDataFolder(), configured);
    }

    /** Velocity name of this backend: {@code remote-transfer.this-server}, else target (dungeon) / return (hub). */
    private static String thisServer(Plugin plugin, String role) {
        var config = plugin.getConfig();
        String configured = config.getString("remote-transfer.this-server", "");
        String fallback = "dungeon".equalsIgnoreCase(role)
                ? config.getString("remote-transfer.target-server", "mmo-d")
                : config.getString("remote-transfer.return-server", "mmo-r");
        String resolved = configured == null || configured.isBlank() ? fallback : configured;
        return resolved == null || resolved.isBlank() ? role : resolved.trim().toLowerCase(Locale.ROOT);
    }

    /** The character host of this backend (pack, apply, freeze, ledger). */
    public CharacterHost host() {
        return host;
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
     *
     * @param pendingFloor dungeon floor to open on arrival (0 = none)
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
        String line = "§5Dungeon Gate§7: Crossing to §f" + ServerNames.display(server)
                + (pendingFloor > 0 ? "§7 · Floor §f" + pendingFloor : "") + "§7…";
        return host.send(player, server, reason, line, pendingFloor, bossOnly);
    }
}
