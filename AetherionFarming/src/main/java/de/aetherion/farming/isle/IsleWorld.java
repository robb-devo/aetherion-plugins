package de.aetherion.farming.isle;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.island.FarmIsleZones;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "Is this on Eldervale?" and "who is on Eldervale?" — one place for every isle loop.
 * The footprint box is cached: isle checks run per harvest, per tick and inside stat lookups.
 */
public final class IsleWorld {

    private record Box(boolean enabled, String world, double minX, double maxX, double minZ, double maxZ) {
        boolean contains(Location at) {
            return enabled
                    && at.getWorld().getName().equalsIgnoreCase(world)
                    && at.getX() >= minX && at.getX() <= maxX
                    && at.getZ() >= minZ && at.getZ() <= maxZ;
        }
    }

    /** Null = not cached yet, or the footprint uses the radius form (FarmIsleZones decides). */
    private static volatile Box box;
    private static volatile boolean cached;

    private IsleWorld() {
    }

    /** Re-read {@code farm-isle-footprint} (after a config reload). */
    public static void refresh(AetherionFarming plugin) {
        ConfigurationSection section = plugin == null ? null : plugin.getConfig().getConfigurationSection("farm-isle-footprint");
        if (section == null) {
            box = new Box(false, "", 0, 0, 0, 0);
        } else if (section.contains("min-x")) {
            box = new Box(
                    section.getBoolean("enabled", true),
                    section.getString("world", "world"),
                    Math.min(section.getDouble("min-x"), section.getDouble("max-x")),
                    Math.max(section.getDouble("min-x"), section.getDouble("max-x")),
                    Math.min(section.getDouble("min-z"), section.getDouble("max-z")),
                    Math.max(section.getDouble("min-z"), section.getDouble("max-z"))
            );
        } else {
            box = null;
        }
        cached = true;
    }

    public static boolean onIsle(AetherionFarming plugin, Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (!cached) {
            refresh(plugin);
        }
        Box current = box;
        return current != null ? current.contains(at) : FarmIsleZones.inFarmIsleFootprint(plugin, at);
    }

    public static boolean onIsle(AetherionFarming plugin, Player player) {
        return player != null && player.isOnline() && onIsle(plugin, player.getLocation());
    }

    /** Survival players inside the footprint — the ones isle loops reward. */
    public static List<Player> farmers(AetherionFarming plugin) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL && onIsle(plugin, player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Everyone inside the footprint (staff in creative included) — for FX and bars. */
    public static List<Player> visitors(AetherionFarming plugin) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (onIsle(plugin, player)) {
                out.add(player);
            }
        }
        return out;
    }

    public static World world(AetherionFarming plugin) {
        return FarmIsleZones.resolveWorld(plugin);
    }

    /** Where the hub jump pad drops players ({@code farm-island.island-exit}), or {@code null}. */
    public static Location landing(AetherionFarming plugin) {
        var config = plugin.getConfig();
        if (!config.contains("farm-island.island-exit.x")) {
            return null;
        }
        World world = Bukkit.getWorld(config.getString("farm-island.island-exit.world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble("farm-island.island-exit.x"),
                config.getDouble("farm-island.island-exit.y"),
                config.getDouble("farm-island.island-exit.z"),
                (float) config.getDouble("farm-island.island-exit.yaw", 0.0d),
                (float) config.getDouble("farm-island.island-exit.pitch", 0.0d)
        );
    }
}
