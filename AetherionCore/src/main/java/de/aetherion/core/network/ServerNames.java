package de.aetherion.core.network;

import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Live Velocity names. Ports match the host map and are only a fallback when
 * {@code network.this-server} is blank and Velocity has not answered GetServer yet.
 */
public final class ServerNames {

    public static final String MAIN = "mmo-r";
    public static final String HUB = "hub";
    public static final String DUNGEON = "mmo-d";
    public static final String COLOSSEUM = "mmo-c";

    public static final int PORT_HUB = 25566;
    public static final int PORT_MAIN = 25567;
    public static final int PORT_DUNGEON = 25568;
    public static final int PORT_COLOSSEUM = 25569;

    private ServerNames() {
    }

    public static String fromPort(int port) {
        return switch (port) {
            case PORT_HUB -> HUB;
            case PORT_MAIN -> MAIN;
            case PORT_DUNGEON -> DUNGEON;
            case PORT_COLOSSEUM -> COLOSSEUM;
            default -> "";
        };
    }

    public static boolean isMain(String name) {
        return name != null && MAIN.equalsIgnoreCase(name.trim());
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
