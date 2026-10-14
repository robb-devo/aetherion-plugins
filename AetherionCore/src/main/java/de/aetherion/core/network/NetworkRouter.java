package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.api.DungeonAccess;
import de.aetherion.core.api.HubAccess;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;
import java.util.UUID;

/**
 * One way to move a player between backends so it feels like a teleport:
 * <pre>
 *   route (what they asked for) → transport (their character, v6) → Velocity Connect
 *   arrival: character applied → route finished (warp / replayed command) → route dropped
 * </pre>
 * The transport is Dungeons' pipeline on mmo-r / mmo-d and Core's {@link CharacterHost} on mmo-c / mmo-i.
 * Without a transport (hub, or a stale jar mix) callers fall back to their old v5 path.
 */
public final class NetworkRouter implements Listener {

    public static final String DEPART_MAIN = "§5✦ §7Crossing to §fAetherion§7…";
    public static final String DEPART_ISLANDS = "§a✦ §7The isle winds lift you…";
    public static final String DEPART_DUNGEON = "§5✦ §7The Dungeon Gate opens…";
    public static final String DEPART_EXILE = "§8✦ §7The ferry takes you into the Veil…";

    private static final long FIRST_TRY_TICKS = 2L;
    private static final long RETRY_TICKS = 2L;
    private static final long GIVE_UP_MS = 15_000L;

    private final AetherionCore plugin;
    private final MainWorldLink link;
    private final RouteBook routes;
    private final CharacterHost host;
    private final java.util.Set<String> routedOn = new java.util.HashSet<>();

    public NetworkRouter(AetherionCore plugin, MainWorldLink link, RouteBook routes, CharacterHost host) {
        this.plugin = plugin;
        this.link = link;
        this.routes = routes;
        this.host = host;
        var cfg = plugin.getConfig();
        for (String name : cfg.isSet("network.routed-commands.on")
                ? cfg.getStringList("network.routed-commands.on")
                : java.util.List.of(ServerNames.EXILE, ServerNames.ISLANDS)) {
            if (name != null && !name.isBlank()) {
                routedOn.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, routes::purgeExpired, 20L * 60, 20L * 300);
    }

    public RouteBook routes() {
        return routes;
    }

    public CharacterHost host() {
        return host;
    }

    public String here() {
        return link.serverName();
    }

    /** Dungeons' pipeline where it runs, else Core's own host, else null. */
    public CharacterTransport transport() {
        CharacterTransport registered = AetherServices.transport();
        if (registered != null) {
            return registered;
        }
        return host;
    }

    public boolean canTravel() {
        return transport() != null;
    }

    /** {@code network.routed-commands.on} lists this backend (default mmo-c, mmo-i). */
    public boolean routesCommandsHere() {
        return routedOn.contains(here().toLowerCase(Locale.ROOT));
    }

    /**
     * For other plugins (Items' manager buttons): on a backend that routes commands, send the player to the
     * main world and run {@code command} there. False when this backend keeps it local (or cannot travel) —
     * the caller then opens its own menu as before.
     */
    public static boolean routeCommandToMain(Player player, String command) {
        AetherionCore core = AetherionCore.get();
        NetworkRouter router = core == null ? null : core.router();
        if (router == null || player == null || command == null || command.isBlank()
                || !router.routesCommandsHere() || !router.canTravel()) {
            return false;
        }
        String main = router.serverFor("main");
        if (main.equalsIgnoreCase(router.here())) {
            return false;
        }
        String line = command.startsWith("/") ? command.substring(1) : command;
        if (!router.travel(player, main, RouteBook.Kind.COMMAND, line, DEPART_MAIN, "command")) {
            player.sendMessage("§7That road is closed right now — try again in a moment.");
        }
        return true;
    }

    /** Velocity name configured for a role key (main / dungeon / islands / exile) or a literal name. */
    public String serverFor(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "main", "mmo-r" -> link.mainServer();
            case "dungeon", "dungeons", "mmo-d" -> plugin.getConfig().getString("network.dungeon-server", ServerNames.DUNGEON);
            case "islands", "island", "isles", "mmo-i" -> plugin.getConfig().getString("network.islands-server", ServerNames.ISLANDS);
            case "exile", "mmo-c" -> plugin.getConfig().getString("network.exile-server", ServerNames.EXILE);
            default -> role.trim().toLowerCase(Locale.ROOT);
        };
    }

    public static String departLineFor(String server) {
        if (ServerNames.isIslands(server)) {
            return DEPART_ISLANDS;
        }
        if (ServerNames.isDungeon(server)) {
            return DEPART_DUNGEON;
        }
        if (ServerNames.isExile(server)) {
            return DEPART_EXILE;
        }
        return DEPART_MAIN;
    }

    /**
     * Move the player (character + intent). Already on {@code server}: the intent runs here at once.
     *
     * @return true when the hop (or the local action) is underway; false when nothing moved
     */
    public boolean travel(Player player, String server, RouteBook.Kind kind, String value, String departLine, String reason) {
        if (player == null || !player.isOnline() || server == null || server.isBlank()) {
            return false;
        }
        String target = server.trim().toLowerCase(Locale.ROOT);
        if (target.equalsIgnoreCase(here())) {
            finishLocally(player, kind, value);
            return true;
        }
        CharacterTransport transport = transport();
        if (transport == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (transport.isHeld(id)) {
            return true;
        }
        DungeonAccess dungeons = AetherServices.dungeons();
        if (dungeons != null) {
            dungeons.leaveInstance(player);
        }
        RouteBook.Route route = routes.put(player, target, here(), kind, value);
        boolean sent = transport.send(player, target, reason == null ? "travel" : reason,
                departLine == null ? departLineFor(target) : departLine);
        if (!sent && route != null) {
            routes.drop(id, route.id());
        }
        if (sent) {
            plugin.getLogger().info("[Route] " + player.getName() + " " + here() + " → " + target
                    + " " + (route == null ? "(no route)" : route.kind() + (route.value().isBlank() ? "" : " '" + route.value() + "'")));
        }
        return sent;
    }

    // ------------------------------------------------------------------ arrival

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RouteBook.Route route = routes.pendingFor(player.getUniqueId(), here());
        if (route == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + GIVE_UP_MS;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                CharacterTransport transport = transport();
                UUID id = player.getUniqueId();
                if (transport != null && transport.isArriving(id) && System.currentTimeMillis() < deadline) {
                    return;
                }
                cancel();
                RouteBook.Route current = routes.peek(id);
                if (current == null || !current.id().equals(route.id())) {
                    return;
                }
                routes.drop(id, route.id());
                if (transport != null && transport.isArriving(id)) {
                    plugin.getLogger().warning("[Route] Gave up on '" + route.value() + "' for " + player.getName()
                            + " — character still not here after " + (GIVE_UP_MS / 1000) + "s.");
                    return;
                }
                finishLocally(player, route.kind(), route.value());
            }
        }.runTaskTimer(plugin, FIRST_TRY_TICKS, RETRY_TICKS);
    }

    private void finishLocally(Player player, RouteBook.Kind kind, String value) {
        if (player == null || !player.isOnline() || kind == null || value == null || value.isBlank()) {
            return;
        }
        switch (kind) {
            case COMMAND -> {
                String command = value.startsWith("/") ? value.substring(1) : value;
                plugin.getLogger().info("[Route] " + player.getName() + " arrived on " + here() + " → /" + command);
                player.performCommand(command);
            }
            case WARP -> warp(player, value);
            default -> {
            }
        }
    }

    /** Silent teleport to a hub spawn the player has unlocked (no message when locked or missing). */
    private void warp(Player player, String spawnId) {
        HubAccess hub = AetherServices.hub();
        if (hub == null) {
            return;
        }
        String id = spawnId.trim().toLowerCase(Locale.ROOT);
        try {
            if (!hub.isUnlocked(player, id)) {
                return;
            }
            Location location = hub.location(id);
            if (location != null && location.getWorld() != null) {
                player.teleport(location);
                player.setFallDistance(0f);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Route] Warp '" + id + "' failed for " + player.getName() + ": " + ex.getMessage());
        }
    }
}
