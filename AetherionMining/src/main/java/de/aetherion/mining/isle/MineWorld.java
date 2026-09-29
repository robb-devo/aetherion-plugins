package de.aetherion.mining.isle;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "Is this on Mining Eldervale?" and "who is down the mine?" — one place for every isle loop.
 * The footprint box ({@code mine-isle-footprint}) is cached: isle checks run per strike, per tick
 * and inside stat lookups. The Veins are a separate world and never count as the isle.
 */
public final class MineWorld {

    record Box(boolean enabled, String world, double minX, double maxX, double minZ, double maxZ,
               double minY, double maxY) {

        boolean contains(Location at) {
            return enabled
                    && at.getWorld() != null
                    && at.getWorld().getName().equalsIgnoreCase(world)
                    && at.getX() >= minX && at.getX() <= maxX
                    && at.getZ() >= minZ && at.getZ() <= maxZ
                    && at.getY() >= minY && at.getY() <= maxY;
        }
    }

    private static volatile Box box;

    private MineWorld() {
    }

    /** Re-read {@code mine-isle-footprint} (after a config reload). */
    public static void refresh(AetherionMining plugin) {
        ConfigurationSection section = plugin == null ? null : plugin.getConfig().getConfigurationSection("mine-isle-footprint");
        if (section == null) {
            box = new Box(false, "world", 0, 0, 0, 0, 0, 0);
            return;
        }
        box = new Box(
                section.getBoolean("enabled", true),
                section.getString("world", "world"),
                Math.min(section.getDouble("min-x"), section.getDouble("max-x")),
                Math.max(section.getDouble("min-x"), section.getDouble("max-x")),
                Math.min(section.getDouble("min-z"), section.getDouble("max-z")),
                Math.max(section.getDouble("min-z"), section.getDouble("max-z")),
                section.getDouble("min-y", -64.0d),
                section.getDouble("max-y", 320.0d)
        );
    }

    static Box box(AetherionMining plugin) {
        Box current = box;
        if (current == null) {
            refresh(plugin);
            current = box;
        }
        return current;
    }

    public static boolean onIsle(AetherionMining plugin, Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        return box(plugin).contains(at);
    }

    public static boolean onIsle(AetherionMining plugin, Player player) {
        return player != null && player.isOnline() && onIsle(plugin, player.getLocation());
    }

    /** Survival players inside the footprint — the ones isle loops reward. */
    public static List<Player> miners(AetherionMining plugin) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL && onIsle(plugin, player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Everyone inside the footprint (staff in creative included) — for FX and bars. */
    public static List<Player> visitors(AetherionMining plugin) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (onIsle(plugin, player)) {
                out.add(player);
            }
        }
        return out;
    }

    public static World world(AetherionMining plugin) {
        return Bukkit.getWorld(box(plugin).world());
    }

    /** "x a…b z c…d" for DEV status. */
    public static String describe(AetherionMining plugin) {
        Box current = box(plugin);
        if (!current.enabled()) {
            return "§cFootprint: off";
        }
        return "§7Footprint: §f" + current.world() + " §8x " + (int) current.minX() + "…" + (int) current.maxX()
                + " z " + (int) current.minZ() + "…" + (int) current.maxZ()
                + " y " + (int) current.minY() + "…" + (int) current.maxY();
    }

    /** {@code world/x/y/z/yaw/pitch} section → location (null when the world is not loaded). */
    public static Location location(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0d), (float) section.getDouble("pitch", 0.0d));
    }

    /** {@code "x y z [yaw]"} in the isle world → location (null on a bad string or no world). */
    public static Location point(AetherionMining plugin, String raw) {
        double[] v = MineText.numbers(raw, 3);
        World world = world(plugin);
        if (v == null || world == null) {
            return null;
        }
        double[] withYaw = MineText.numbers(raw, 4);
        return new Location(world, v[0], v[1], v[2], withYaw == null ? 0.0f : (float) withYaw[3], 0.0f);
    }

    /** Where the Origin jump pad drops players ({@code mine-isle.landing}), or {@code null}. */
    public static Location landing(AetherionMining plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mine-isle.landing");
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0d), (float) section.getDouble("pitch", 0.0d));
    }
}
