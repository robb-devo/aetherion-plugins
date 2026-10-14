package de.aetherion.core.network;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Live Velocity names. Ports match the host map and are only a fallback when
 * {@code network.this-server} is blank and Velocity has not answered GetServer yet.
 *
 * <p>Roles (fixed): hub = lobby + pit, mmo-r = main world, mmo-d = dungeons only,
 * mmo-c = exile (story / ferry), mmo-i = private + guild islands only.
 */
public final class ServerNames {

    public static final String MAIN = "mmo-r";
    public static final String HUB = "hub";
    public static final String DUNGEON = "mmo-d";
    /** Exile backend ({@code aetherion_exile}): story / ferry. Never islands, never dungeons. */
    public static final String EXILE = "mmo-c";
    /** Private + guild islands ({@code aether_islands} / {@code aether_guilds}). Never exile, never dungeons. */
    public static final String ISLANDS = "mmo-i";
    /**
     * Historical alias — same Velocity backend as {@link #EXILE}.
     * Colosseum combat itself lives on the main world, not this backend.
     */
    public static final String COLOSSEUM = EXILE;

    public static final int PORT_HUB = 25566;
    public static final int PORT_MAIN = 25567;
    public static final int PORT_DUNGEON = 25568;
    public static final int PORT_EXILE = 25569;
    public static final int PORT_ISLANDS = 25570;
    public static final int PORT_COLOSSEUM = PORT_EXILE;

    /** Backends that carry a character between each other (transfer v6). Hub is join + pit only. */
    public static final List<String> CHARACTER_BACKENDS = List.of(MAIN, DUNGEON, EXILE, ISLANDS);

    private ServerNames() {
    }

    public static String fromPort(int port) {
        return switch (port) {
            case PORT_HUB -> HUB;
            case PORT_MAIN -> MAIN;
            case PORT_DUNGEON -> DUNGEON;
            case PORT_EXILE -> EXILE;
            case PORT_ISLANDS -> ISLANDS;
            default -> "";
        };
    }

    public static boolean isMain(String name) {
        return is(MAIN, name);
    }

    public static boolean isExile(String name) {
        return is(EXILE, name);
    }

    public static boolean isIslands(String name) {
        return is(ISLANDS, name);
    }

    public static boolean isDungeon(String name) {
        return is(DUNGEON, name);
    }

    public static boolean isHub(String name) {
        return is(HUB, name);
    }

    /** A backend whose playerdata can hold the character (anything but the hub). */
    public static boolean isCharacterBackend(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return CHARACTER_BACKENDS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * What a player reads. Never the Velocity id — players should not notice they changed servers.
     */
    public static String display(String name) {
        if (name == null) {
            return "Aetherion";
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case DUNGEON -> "the Dungeon Hub";
            case EXILE -> "the Exile shore";
            case ISLANDS -> "the Isles";
            case HUB -> "the Harbour Lobby";
            default -> "Aetherion";
        };
    }

    private static boolean is(String role, String name) {
        return name != null && role.equalsIgnoreCase(name.trim());
    }

    /**
     * Config name, else a known live port, else the Velocity GetServer reply.
     * A mapped port wins over GetServer so a late or odd proxy name cannot
     * send /capital on mmo-r back through Velocity.
     */
    public static String resolve(String configured, int port, String velocityName) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String fromPort = fromPort(port);
        if (!fromPort.isBlank()) {
            return fromPort;
        }
        if (velocityName != null && !velocityName.isBlank()) {
            return velocityName.trim();
        }
        return "unknown";
    }

    /** Shared transfer folder. Explicit config wins, then Crafty {@code shared/transfer}. */
    public static File snapshotDir(Plugin plugin) {
        String configured = plugin == null ? "" : plugin.getConfig().getString("network.shared-dir", "");
        if (configured != null && !configured.isBlank()) {
            File candidate = new File(configured.trim());
            if (candidate.isAbsolute() || plugin == null) {
                return candidate;
            }
            return new File(plugin.getDataFolder(), configured.trim());
        }
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            File shared = new de.aetherion.core.wipe.WipeLayout(
                    javaPlugin, javaPlugin.getServer().getWorldContainer()).sharedRootCandidate();
            if (shared != null) {
                return new File(shared, "transfer");
            }
        }
        return new File("/var/opt/minecraft/crafty/shared/transfer");
    }
}
