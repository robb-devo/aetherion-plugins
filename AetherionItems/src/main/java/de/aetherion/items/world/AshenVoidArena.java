package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Preview End-themed copy of Bloodstone ({@code ashen_void}).
 * End-portal pits (former lava) instantly kill; respawn is only at the pad on death.
 * Does not touch the live bloodstone arena.
 */
public final class AshenVoidArena implements Listener {

    public static final String WORLD = "ashen_void";

    private final AetherionItems plugin;
    private final File file;
    private FileConfiguration config;
    private final Set<UUID> respawning = new HashSet<>();

    public AshenVoidArena(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ashen-void-arena.yml");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        if (!file.exists()) {
            writeDefaults();
        }
        config = YamlConfiguration.loadConfiguration(file);
        ensureKeys();
        saveQuiet();
    }

    private void writeDefaults() {
        config = new YamlConfiguration();
        config.set("player.world", WORLD);
        config.set("player.x", 0.5);
        config.set("player.y", 68.0);
        config.set("player.z", 0.5);
        config.set("player.yaw", 0.0f);
        config.set("player.pitch", 0.0f);
        config.set("notes", "Ashen Void preview — End remap of Bloodstone. /ashenvoid");
        saveQuiet();
    }

    private void ensureKeys() {
        if (!config.isConfigurationSection("player")) {
            writeDefaults();
        }
        // Legacy key from the void-y soft-floor preview — ignore if present.
        config.set("void-y", null);
    }

    public Location playerSpawn() {
        String worldName = config.getString("player.world", WORLD);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorld(WORLD);
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble("player.x"),
                config.getDouble("player.y"),
                config.getDouble("player.z"),
                (float) config.getDouble("player.yaw"),
                (float) config.getDouble("player.pitch")
        );
    }

    public void setPlayerSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        config.set("player.world", location.getWorld().getName());
        config.set("player.x", location.getX());
        config.set("player.y", location.getY());
        config.set("player.z", location.getZ());
        config.set("player.yaw", location.getYaw());
        config.set("player.pitch", location.getPitch());
        saveQuiet();
    }

    public boolean teleport(Player player) {
        Location dest = playerSpawn();
        if (dest == null || dest.getWorld() == null) {
            return false;
        }
        return player.teleport(dest);
    }

    public static boolean isAshenVoid(World world) {
        return world != null && WORLD.equalsIgnoreCase(world.getName());
    }

    /** Touching an end-portal pit (former lava) → instant death. No soft floor teleports. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalPit(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isAshenVoid(player.getWorld())) {
            return;
        }
        Location to = event.getTo();
        if (to == null || !isEndPortalPit(to)) {
            return;
        }
        killInstant(player);
    }

    /** Never ride the portal to The End — pits are lethal only. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!isAshenVoid(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return;
        }
        event.setCancelled(true);
        killInstant(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isAshenVoid(player.getWorld())) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        event.setCancelled(true);
        killInstant(player);
    }

    /** Only pad-respawn path: after an actual death in this world. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isAshenVoid(player.getWorld())) {
            return;
        }
        respawning.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!respawning.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        Location pad = playerSpawn();
        if (pad == null || pad.getWorld() == null) {
            return;
        }
        event.setRespawnLocation(pad);
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(pad);
            }
        });
    }

    private static boolean isEndPortalPit(Location at) {
        if (at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block below = at.clone().subtract(0, 0.2, 0).getBlock();
        return isPortal(feet.getType()) || isPortal(below.getType());
    }

    private static boolean isPortal(Material material) {
        return material == Material.END_PORTAL || material == Material.END_GATEWAY;
    }

    private void killInstant(Player player) {
        if (player == null || player.isDead() || respawning.contains(player.getUniqueId())) {
            return;
        }
        player.setFallDistance(0f);
        player.setHealth(0.0);
    }

    private void saveQuiet() {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save ashen-void-arena.yml", exception);
        }
    }
}
