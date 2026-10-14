package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.DungeonAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Clean Velocity hop to / from the exile backend ({@link ServerNames#EXILE}).
 * With a character transport (every MMO backend) the hop is transfer v6 via {@link NetworkRouter};
 * the Core v5 snapshot path below remains the fallback where no transport exists.
 */
public final class ExileLink {

    public static final String WARP_EXILE = "exile";

    private final AetherionCore plugin;
    private final MainWorldLink link;
    private final TransferSnapshotStore snapshots;
    private final String exileServer;

    public ExileLink(AetherionCore plugin, MainWorldLink link, TransferSnapshotStore snapshots) {
        this.plugin = plugin;
        this.link = link;
        this.snapshots = snapshots;
        String configured = plugin.getConfig().getString("network.exile-server", ServerNames.EXILE);
        this.exileServer = configured == null || configured.isBlank()
                ? ServerNames.EXILE
                : configured.trim().toLowerCase(Locale.ROOT);
    }

    public String exileServer() {
        return exileServer;
    }

    public boolean isExileServer() {
        if (ServerNames.isExile(link.serverName())) {
            return true;
        }
        // Port map wins before Velocity GetServer answers (join tick 0–5).
        return ServerNames.isExile(ServerNames.fromPort(Bukkit.getPort()));
    }

    /**
     * Snapshot + Connect to exile. Inventory stays on the player until they leave
     * (same safety model as {@link MainWorldLink#handoff}).
     *
     * @return true when this call owns the transfer (caller should not teleport locally)
     */
    public boolean send(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (isExileServer()) {
            player.sendMessage("§8Exil §8» §7You're already on the exile shore.");
            return true;
        }
        NetworkRouter router = plugin.router();
        if (router != null && router.canTravel()) {
            if (!router.travel(player, exileServer, RouteBook.Kind.NONE, "", NetworkRouter.DEPART_EXILE, WARP_EXILE)) {
                player.sendMessage("§cThe ferry could not cast off. Nothing moved.");
            }
            return true;
        }
        DungeonAccess dungeons = AetherServices.dungeons();
        if (dungeons != null) {
            dungeons.leaveInstance(player);
        }
        long savedAt = snapshots == null ? -1L : snapshots.save(
                player, 0, false, WARP_EXILE, exileServer);
        if (savedAt < 0L) {
            player.sendMessage("§cCould not save your gear for the exile crossing. Inventory left in place.");
            return true;
        }
        player.sendMessage("§8✦ §7The ferry takes you into the Veil…");
        boolean sent = link.connect(player, exileServer);
        if (!sent) {
            if (snapshots != null) {
                snapshots.discardIfUnclaimed(player.getUniqueId(), savedAt);
            }
            player.sendMessage("§cThe ferry could not cast off. Inventory left in place.");
            return true;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player still = Bukkit.getPlayer(id);
            if (still != null && still.isOnline() && snapshots != null) {
                snapshots.discardIfUnclaimed(id, savedAt);
                plugin.getLogger().info("Connect to " + exileServer + " did not move " + still.getName()
                        + " — exile snapshot discarded.");
            }
        }, 300L);
        return true;
    }

    /** Snapshot + Connect back to the main world. */
    public boolean returnToMain(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (link.isMainWorld()) {
            player.sendMessage("§8Exil §8» §7You're already on Aetherion.");
            return true;
        }
        String main = link.mainServer();
        NetworkRouter router = plugin.router();
        if (router != null && router.canTravel()) {
            // The ferry docks back at the Veil Quay ("/exile landing" runs on arrival; harbour when no islet).
            if (!router.travel(player, main, RouteBook.Kind.COMMAND, "exile landing",
                    "§8✦ §7The ferry carries you back through the Veil…", "exile-return")) {
                player.sendMessage("§cCould not reach Aetherion right now. Nothing moved.");
            }
            return true;
        }
        long savedAt = snapshots == null ? -1L : snapshots.save(player, 0, false, "harbour", main);
        if (savedAt < 0L) {
            player.sendMessage("§cCould not save your gear for the return. Inventory left in place.");
            return true;
        }
        player.sendMessage("§8✦ §7Crossing back to §fAetherion§7…");
        boolean sent = link.connect(player, main);
        if (!sent) {
            if (snapshots != null) {
                snapshots.discardIfUnclaimed(player.getUniqueId(), savedAt);
            }
            player.sendMessage("§cCould not reach Aetherion. Inventory left in place.");
            return true;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player still = Bukkit.getPlayer(id);
            if (still != null && still.isOnline() && snapshots != null) {
                snapshots.discardIfUnclaimed(id, savedAt);
                plugin.getLogger().log(Level.INFO, "Return to " + main + " did not move " + still.getName());
            }
        }, 300L);
        return true;
    }
}
