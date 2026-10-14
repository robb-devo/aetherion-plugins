package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.IslandAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Island backend host (mmo-i only): arrivals land straight on their island, deaths respawn there, and the
 * main world is a quiet void "Isle Dock" for everyone without an island (or kicked off one).
 *
 * <p>Islands themselves (worlds, quarries, belts, the isle hand) stay AetherionGuilds' job; this class only
 * decides where a player stands. It never pastes, never ticks production.
 */
public final class IslandsHost implements Listener {

    private static final Set<String> ISLAND_LABELS = Set.of("island", "is", "insel", "aetherisland");
    private static final Set<String> GUILD_LABELS = Set.of("guild", "g", "gilde", "aetherguild");
    private static final Set<String> FRIEND_LABELS = Set.of("friend", "friends", "freund", "freunde");
    /** {@code /guild <sub>} that happens on the guild island. */
    private static final Set<String> GUILD_ISLAND_SUBS = Set.of("home", "island", "build", "land", "belts", "project", "projects");

    private final AetherionCore plugin;
    private final NetworkRouter router;
    private Location lobby;

    public IslandsHost(AetherionCore plugin, NetworkRouter router) {
        this.plugin = plugin;
        this.router = router;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[Isles] Island host armed on " + router.here() + ".");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        lobby = prepareLobby();
        long period = Math.max(20L, plugin.getConfig().getLong("network.islands.lobby.hint-ticks", 60L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::hintTick, period, period);
    }

    public Location lobby() {
        if (lobby == null || lobby.getWorld() == null) {
            lobby = prepareLobby();
        }
        return lobby == null ? null : lobby.clone();
    }

    private Location prepareLobby() {
        var cfg = plugin.getConfig();
        String worldName = cfg.getString("network.islands.lobby.world", "");
        World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        Location spot = new Location(world,
                cfg.getDouble("network.islands.lobby.x", 0.5),
                cfg.getDouble("network.islands.lobby.y", 100.0),
                cfg.getDouble("network.islands.lobby.z", 0.5),
                (float) cfg.getDouble("network.islands.lobby.yaw", 0.0),
                (float) cfg.getDouble("network.islands.lobby.pitch", 0.0));
        try {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setSpawnLocation(spot);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Isles] Could not tune lobby world: " + ex.getMessage());
        }
        if (cfg.getBoolean("network.islands.lobby.build-dock", true)) {
            buildDock(spot);
        }
        plugin.getLogger().info("[Isles] Isle Dock: " + world.getName() + " "
                + spot.getBlockX() + "," + spot.getBlockY() + "," + spot.getBlockZ());
        return spot;
    }

    /** A small stone landing under the dock spawn, only where there is nothing yet (void lobby). */
    private void buildDock(Location spot) {
        World world = spot.getWorld();
        int cx = spot.getBlockX();
        int y = spot.getBlockY() - 1;
        int cz = spot.getBlockZ();
        if (world == null || world.getBlockAt(cx, y, cz).getType() != Material.AIR) {
            return;
        }
        int r = 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                Block block = world.getBlockAt(cx + dx, y, cz + dz);
                if (block.getType() == Material.AIR) {
                    block.setType(edge ? Material.POLISHED_DEEPSLATE : Material.SMOOTH_QUARTZ, false);
                }
            }
        }
        for (int[] corner : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
            Block post = world.getBlockAt(cx + corner[0], y + 1, cz + corner[1]);
            if (post.getType() == Material.AIR) {
                post.setType(Material.DARK_OAK_FENCE, false);
                Block lamp = post.getRelative(0, 1, 0);
                if (lamp.getType() == Material.AIR) {
                    lamp.setType(Material.LANTERN, false);
                }
            }
        }
        plugin.getLogger().info("[Isles] Built the Isle Dock landing at " + cx + "," + y + "," + cz + ".");
    }

    private IslandAccess islands() {
        return AetherServices.islands();
    }

    /** Where this player belongs on the isles, or null. */
    private Location homeOf(UUID id, boolean guildFirst) {
        IslandAccess islands = islands();
        if (islands == null) {
            return null;
        }
        try {
            Location personal = islands.home(id);
            Location guild = islands.guildHome(id);
            return guildFirst ? (guild != null ? guild : personal) : (personal != null ? personal : guild);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Isles] Island lookup failed for " + id + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Before the join: put arrivals where their command will take them anyway (no visible jump), or on the
     * dock. A plain reconnect keeps the spot the player left on mmo-i.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        RouteBook.Route route = router.routes().pendingFor(id, router.here());
        Location target = null;
        if (route != null) {
            target = targetFor(route, id);
            if (target == null) {
                target = lobby();
            }
        } else if (!player.hasPlayedBefore()) {
            target = homeOf(id, false);
            if (target == null) {
                target = lobby();
            }
        }
        if (target != null && target.getWorld() != null) {
            event.setSpawnLocation(target);
        }
    }

    private Location targetFor(RouteBook.Route route, UUID id) {
        if (route.kind() != RouteBook.Kind.COMMAND) {
            return homeOf(id, false);
        }
        String label = route.label();
        String[] args = route.args();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (ISLAND_LABELS.contains(label)) {
            return homeOf(id, false);
        }
        if (GUILD_LABELS.contains(label)) {
            return homeOf(id, sub.isEmpty() || GUILD_ISLAND_SUBS.contains(sub) || sub.equals("menu"));
        }
        if (FRIEND_LABELS.contains(label)) {
            // A visit teleports on arrival; until then stand at home (or the dock).
            return homeOf(id, false);
        }
        return homeOf(id, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location home = homeOf(event.getPlayer().getUniqueId(), false);
        Location target = home != null ? home : lobby();
        if (target != null && target.getWorld() != null) {
            event.setRespawnLocation(target);
        }
    }

    private void hintTick() {
        Location dock = lobby;
        if (dock == null || dock.getWorld() == null) {
            return;
        }
        Component hint = LegacyComponentSerializer.legacySection()
                .deserialize("§a/island §8· §7your isle   §8|   §e/capital §8· §7back to Aetherion");
        IslandAccess islands = islands();
        for (Player player : dock.getWorld().getPlayers()) {
            if (islands != null && islands.isIslandWorld(player.getLocation())) {
                continue;
            }
            player.sendActionBar(hint);
        }
    }
}
