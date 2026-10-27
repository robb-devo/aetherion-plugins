package de.aetherion.mining.veins;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.network.NetworkRouter;
import de.aetherion.mining.AetherionMining;

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

/**
 * Mine server host (mmo-m only): whoever crosses in stands on the Amethyst Mine hub at once (no visible jump), and the
 * void main world is only a quiet landing for the minutes the mine is being restored.
 *
 * <p>The mine itself (paste, reset, NPCs) stays {@link VeinsWorld}'s job; this class only decides where a player
 * stands. Leaving goes back through {@link VeinsWorld#leave}.
 */
public final class MinesHost implements Listener {

    private final AetherionMining plugin;
    private final VeinsWorld veins;
    private Location landing;

    public MinesHost(AetherionMining plugin, VeinsWorld veins) {
        this.plugin = plugin;
        this.veins = veins;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[Mines] Mine host armed: arrivals land on the Amethyst Mine hub.");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        landing = prepareLanding();
        Bukkit.getScheduler().runTaskTimer(plugin, this::hintTick, 60L, 60L);
    }

    private Location landing() {
        if (landing == null || landing.getWorld() == null) {
            landing = prepareLanding();
        }
        return landing == null ? null : landing.clone();
    }

    /** Jawcamp when it stands, else the Veins hub, else the landing in the main world. */
    private Location arrival(boolean veinsFirst) {
        Location camp = veins.campSpot();
        Location hub = veins.isRestoring() || !veins.isSchematicReady() ? null : veins.hubSpawn();
        if (veinsFirst && hub != null && hub.getWorld() != null) {
            return hub;
        }
        if (camp != null) {
            return camp;
        }
        return hub != null && hub.getWorld() != null ? hub : landing();
    }

    private Location prepareLanding() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorlds().getFirst();
        var cfg = plugin.getConfig();
        Location spot = new Location(world,
                cfg.getDouble("veins.landing.x", 0.5),
                cfg.getDouble("veins.landing.y", 100.0),
                cfg.getDouble("veins.landing.z", 0.5),
                (float) cfg.getDouble("veins.landing.yaw", 0.0),
                0.0f);
        try {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setTime(18000L);
            world.setSpawnLocation(spot);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Mines] Could not tune the landing world: " + ex.getMessage());
        }
        buildLanding(spot);
        return spot;
    }

    /** A small lantern-lit stone landing under the spawn, only where there is nothing yet (void main world). */
    private void buildLanding(Location spot) {
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
                    block.setType(edge ? Material.POLISHED_DEEPSLATE : Material.AMETHYST_BLOCK, false);
                }
            }
        }
        for (int[] corner : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
            Block post = world.getBlockAt(cx + corner[0], y + 1, cz + corner[1]);
            if (post.getType() == Material.AIR) {
                post.setType(Material.DEEPSLATE_TILE_WALL, false);
                Block lamp = post.getRelative(0, 1, 0);
                if (lamp.getType() == Material.AIR) {
                    lamp.setType(Material.LANTERN, false);
                }
            }
        }
        plugin.getLogger().info("[Mines] Built the mine landing at " + cx + "," + y + "," + cz + ".");
    }

    /**
     * Before the join: a crossing (route pending), a first visit or anyone outside the mine lands on the hub.
     * A plain reconnect inside the mine keeps the spot the player left.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        Location at = event.getSpawnLocation();
        NetworkRouter router = AetherionCore.get() == null ? null : AetherionCore.get().router();
        de.aetherion.core.network.RouteBook.Route route = router == null ? null
                : router.routes().pendingFor(player.getUniqueId(), router.here());
        boolean routed = route != null;
        if (!routed && player.hasPlayedBefore() && at.getWorld() != null
                && (veins.isVeins(at.getWorld()) || at.getWorld() != Bukkit.getWorlds().getFirst())) {
            return;
        }
        // "/amethyst" from the main world goes straight into the Veins; everything else lands in Jawcamp.
        Location target = arrival(route != null && route.value().toLowerCase(java.util.Locale.ROOT).startsWith("amethyst"));
        if (target != null && target.getWorld() != null) {
            event.setSpawnLocation(target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn() || veins.isVeins(event.getRespawnLocation().getWorld())) {
            return;
        }
        Location target = arrival(false);
        if (target != null && target.getWorld() != null) {
            event.setRespawnLocation(target);
        }
    }

    private void hintTick() {
        Location spot = landing;
        if (spot == null || spot.getWorld() == null || veins.campSpot() != null) {
            return;
        }
        Component hint = LegacyComponentSerializer.legacySection().deserialize(veins.isRestoring()
                ? "§dThe Amethyst Mine §7is being restored §8· §e/capital §8· §7back to Aetherion"
                : "§d/amethyst §8· §7into the mine   §8|   §e/capital §8· §7back to Aetherion");
        for (Player player : spot.getWorld().getPlayers()) {
            player.sendActionBar(hint);
        }
    }
}
