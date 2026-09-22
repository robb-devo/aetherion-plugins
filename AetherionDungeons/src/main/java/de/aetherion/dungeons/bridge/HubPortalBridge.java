package de.aetherion.dungeons.bridge;

import de.aetherion.core.network.TransferSnapshotStore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network portals:
 * - hub-portal on mmo-r → mmo-d
 * - return-portal on mmo-d → mmo-r (capital)
 */
public final class HubPortalBridge implements Listener {

    private final Plugin plugin;
    private final RemoteServerBridge remote;
    private final TransferSnapshotStore snapshots;
    private final Portal hubPortal;
    private final Portal returnPortal;
    private final Arrival hubArrival;
    private final Arrival dungeonArrival;
    private final long joinGraceMs;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinGrace = new ConcurrentHashMap<>();

    public HubPortalBridge(Plugin plugin, RemoteServerBridge remote, TransferSnapshotStore snapshots) {
        this.plugin = plugin;
        this.remote = remote;
        this.snapshots = snapshots;
        this.hubPortal = Portal.fromConfig(plugin, "hub-portal", -1.0, 67.0, -204.0);
        this.returnPortal = Portal.fromConfig(plugin, "return-portal", -1.0, 48.0, -27.0);
        // Land ON mmo-d (dungeon hub) after crossing from capital.
        this.dungeonArrival = Arrival.fromConfig(plugin, "dungeon-hub.arrival", returnPortal.worldName, -1.0, 49.0, -15.0);
        // Land ON mmo-r (capital) after returning from dungeon hub.
        this.hubArrival = Arrival.fromConfig(plugin, "return-portal.arrival", hubPortal.worldName, -1.0, 67.0, -198.0);
        this.joinGraceMs = Math.max(1000L, plugin.getConfig().getLong("remote-transfer.join-grace-ms", 4000L));

        if (hubPortal.enabled && remote != null && remote.isHubRole()) {
            plugin.getLogger().info("Hub portal → mmo-d at " + hubPortal);
        }
        if (returnPortal.enabled && remote != null && remote.isDungeonRole()) {
            plugin.getLogger().info("Return portal → mmo-r at " + returnPortal);
        }
    }

    private boolean tryHubTransfer(Player player) {
        if (player == null || !player.isOnline() || remote == null || !remote.isHubRole()) {
            return false;
        }
        if (!hubPortal.enabled || !plugin.getConfig().getBoolean("remote-transfer.enabled", false)) {
            return false;
        }
        if (!hubPortal.contains(player.getLocation())) {
            return false;
        }
        if (inGrace(player) || !markCooldown(player)) {
            return true;
        }
        return remote.transferToDungeon(player);
    }

    private boolean tryReturnTransfer(Player player) {
        if (player == null || !player.isOnline() || remote == null || !remote.isDungeonRole()) {
            return false;
        }
        if (!returnPortal.enabled || !plugin.getConfig().getBoolean("remote-transfer.enabled", false)) {
            return false;
        }
        if (!liveReturnContains(player.getLocation())) {
            return false;
        }
        if (inGrace(player) || !markCooldown(player)) {
            return true;
        }
        player.sendMessage("§5Dungeon Gate§7: Returning to the capital…");
        return remote.transferHome(player);
    }

    private boolean inGrace(Player player) {
        Long until = joinGrace.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private boolean markCooldown(Player player) {
        long now = System.currentTimeMillis();
        long cd = Math.max(hubPortal.cooldownMs, returnPortal.cooldownMs);
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < cd) {
            return false;
        }
        cooldown.put(player.getUniqueId(), now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        if (hubPortal.contains(player.getLocation()) || hubPortal.contains(from)) {
            event.setCancelled(true);
            tryHubTransfer(player);
            return;
        }
        if (liveReturnContains(player.getLocation()) || liveReturnContains(from)) {
            event.setCancelled(true);
            tryReturnTransfer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (hubPortal.contains(player.getLocation()) || hubPortal.contains(event.getLocation())) {
            tryHubTransfer(player);
            return;
        }
        if (liveReturnContains(player.getLocation()) || liveReturnContains(event.getLocation())) {
            tryReturnTransfer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        boolean enteredHub = hubPortal.contains(event.getTo()) && !hubPortal.contains(event.getFrom());
        boolean enteredReturn = liveReturnContains(event.getTo()) && !liveReturnContains(event.getFrom());
        if (enteredHub) {
            tryHubTransfer(event.getPlayer());
        } else if (enteredReturn) {
            tryReturnTransfer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinGrace.put(player.getUniqueId(), System.currentTimeMillis() + joinGraceMs);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            TransferSnapshotStore.ApplyResult result = snapshots.applyDetailed(player);
            if (!result.applied()) {
                return;
            }
            if (remote != null && remote.isDungeonRole()) {
                Location dest = dungeonArrival.toLocation();
                if (dest != null) {
                    player.teleport(dest);
                }
                player.sendMessage("§5Dungeon Hub§7: Welcome — gear synced.");
                player.sendMessage("§7Tip: §e/dungeon return §7goes back to the main world. Your gear stays.");
                if (result.pendingFloor() > 0) {
                    int floor = result.pendingFloor();
                    boolean bossOnly = result.bossOnly();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        var pluginMain = de.aetherion.dungeons.AetherionDungeons.getInstance();
                        if (pluginMain == null || pluginMain.getInstances() == null) {
                            return;
                        }
                        player.sendMessage("§5Dungeon Gate§7: Opening Floor §f" + floor + "§7…");
                        pluginMain.getInstances().enterPrototype(player, bossOnly, floor);
                    }, 15L);
                }
            } else if (remote != null && remote.isHubRole()) {
                boolean warped = false;
                String warp = result.pendingWarp();
                if (warp != null && !warp.isBlank()) {
                    warped = landOnMainSpawn(player, warp);
                }
                if (!warped) {
                    Location dest = hubArrival.toLocation();
                    if (dest != null) {
                        player.teleport(dest);
                    }
                }
                player.sendMessage("§5Main world§7: Welcome back — gear and level synced.");
            }
        }, 8L);
    }

    /** Teleport (or Velocity-transfer) the player to the dungeon hub. Usable from anywhere. */
    public boolean sendToDungeonHub(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (remote != null && remote.isHubRole()
                && plugin.getConfig().getBoolean("remote-transfer.enabled", false)) {
            return remote.transferToDungeon(player, 0, false);
        }
        var pluginMain = de.aetherion.dungeons.AetherionDungeons.getInstance();
        boolean wasInDungeon = false;
        if (pluginMain != null && pluginMain.getInstances() != null) {
            var instances = pluginMain.getInstances();
            wasInDungeon = instances.sessionOf(player) != null || instances.isDungeonWorld(player.getWorld());
            if (wasInDungeon) {
                instances.leave(player, false);
            }
        }
        Location dest = dungeonArrival.toLocation();
        if (dest == null) {
            player.sendMessage("§cDungeon hub arrival is not configured.");
            return false;
        }
        if (wasInDungeon) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(dest);
                    player.sendMessage("§5Dungeon Hub§7: You are back at the gate.");
                }
            }, 3L);
        } else {
            player.teleport(dest);
            player.sendMessage("§5Dungeon Hub§7: You are back at the gate.");
        }
        return true;
    }

    public Location dungeonHubLocation() {
        return dungeonArrival.toLocation();
    }

    /**
     * Arrival is already the main world. Teleport to the requested hub spawn.
     * Amethyst uses the mining gate, same as {@code /amethyst} on mmo-r.
     */
    private boolean landOnMainSpawn(Player player, String warp) {
        if ("amethyst".equalsIgnoreCase(warp)) {
            de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
            de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
            boolean unlocked = hub != null && hub.isUnlocked(player, "amethyst");
            if (!unlocked && mining != null && !mining.meetsVeinsMiningLevel(player)) {
                int need = mining.veinsMinMiningLevel();
                player.sendMessage("§cNeed Mining Skill " + need + " for Amethyst Mines.");
                return false;
            }
            if (mining != null) {
                return mining.teleportToVeinsHub(player);
            }
        }
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            return false;
        }
        Location location = hub.location(warp);
        if (location == null && !"harbour".equalsIgnoreCase(warp)) {
            location = hub.location("harbour");
        }
        if (location == null) {
            return false;
        }
        player.teleport(location);
        player.setFallDistance(0f);
        return true;
    }


    /** Live coords — frame discovery may rewrite return-portal in config.yml after enable. */
    private boolean liveReturnContains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("return-portal.enabled", false)) {
            return false;
        }
        String worldName = plugin.getConfig().getString("return-portal.world", "world");
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        double x = plugin.getConfig().getDouble("return-portal.x", returnPortal.x());
        double y = plugin.getConfig().getDouble("return-portal.y", returnPortal.y());
        double z = plugin.getConfig().getDouble("return-portal.z", returnPortal.z());
        double radius = Math.max(1.0, plugin.getConfig().getDouble("return-portal.radius", returnPortal.radius()));
        double dx = loc.getX() - x;
        double dy = loc.getY() - y;
        double dz = loc.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
    }
    private record Portal(
            boolean enabled,
            String worldName,
            double x,
            double y,
            double z,
            double radius,
            long cooldownMs
    ) {
        static Portal fromConfig(Plugin plugin, String path, double dx, double dy, double dz) {
            return new Portal(
                    plugin.getConfig().getBoolean(path + ".enabled", false),
                    plugin.getConfig().getString(path + ".world", "world"),
                    plugin.getConfig().getDouble(path + ".x", dx),
                    plugin.getConfig().getDouble(path + ".y", dy),
                    plugin.getConfig().getDouble(path + ".z", dz),
                    Math.max(1.0, plugin.getConfig().getDouble(path + ".radius", 2.5)),
                    Math.max(500L, plugin.getConfig().getLong(path + ".cooldown-ms", 3000L))
            );
        }

        boolean contains(Location loc) {
            if (!enabled || loc == null || loc.getWorld() == null) {
                return false;
            }
            if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                return false;
            }
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        }

        @Override
        public String toString() {
            return worldName + " " + x + "," + y + "," + z + " r=" + radius;
        }
    }

    private record Arrival(String worldName, double x, double y, double z, float yaw, float pitch) {
        static Arrival fromConfig(Plugin plugin, String path, String defaultWorld, double dx, double dy, double dz) {
            return new Arrival(
                    plugin.getConfig().getString(path + ".world", defaultWorld),
                    plugin.getConfig().getDouble(path + ".x", dx),
                    plugin.getConfig().getDouble(path + ".y", dy),
                    plugin.getConfig().getDouble(path + ".z", dz),
                    (float) plugin.getConfig().getDouble(path + ".yaw", 0.0),
                    (float) plugin.getConfig().getDouble(path + ".pitch", 0.0)
            );
        }

        Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null && !Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().getFirst();
            }
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
}
