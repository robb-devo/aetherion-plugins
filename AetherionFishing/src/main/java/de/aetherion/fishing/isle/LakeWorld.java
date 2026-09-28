package de.aetherion.fishing.isle;

import de.aetherion.fishing.AetherionFishing;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "Is this Fishing Eldervale?" and "who is fishing there?" — one place for every isle loop.
 * The footprint box is cached: isle checks run per cast, per tick and inside stat lookups.
 *
 * <p>Defaults match {@code /aetherpaste fishing} at −585 90 −649: the schematic's Offset is
 * (−215, −132, −215), so schem local (x, y, z) lands at world (x − 800, y − 42, z − 864) and the
 * lake surface (local y 131) sits at world y 89.
 */
public final class LakeWorld {

    private record Box(boolean enabled, String world, double minX, double maxX, double minZ, double maxZ) {
        boolean contains(Location at) {
            return enabled
                    && at.getWorld() != null
                    && at.getWorld().getName().equalsIgnoreCase(world)
                    && at.getX() >= minX && at.getX() <= maxX
                    && at.getZ() >= minZ && at.getZ() <= maxZ;
        }
    }

    private static volatile Box box = new Box(false, "", 0, 0, 0, 0);
    private static volatile int lakeY = 89;

    private LakeWorld() {
    }

    /** Re-read {@code fish-isle.footprint} and the lake surface (after a config reload). */
    public static void refresh(AetherionFishing plugin) {
        ConfigurationSection section = plugin == null ? null : plugin.getConfig().getConfigurationSection("fish-isle.footprint");
        if (section == null || !plugin.getConfig().getBoolean("fish-isle.enabled", true)) {
            box = new Box(false, "", 0, 0, 0, 0);
        } else {
            box = new Box(
                    true,
                    section.getString("world", "world"),
                    Math.min(section.getDouble("min-x"), section.getDouble("max-x")),
                    Math.max(section.getDouble("min-x"), section.getDouble("max-x")),
                    Math.min(section.getDouble("min-z"), section.getDouble("max-z")),
                    Math.max(section.getDouble("min-z"), section.getDouble("max-z"))
            );
        }
        lakeY = plugin == null ? 89 : plugin.getConfig().getInt("fish-isle.lake-surface-y", 89);
    }

    public static boolean onIsle(Location at) {
        return at != null && at.getWorld() != null && box.contains(at);
    }

    public static boolean onIsle(Player player) {
        return player != null && player.isOnline() && onIsle(player.getLocation());
    }

    /** Lake-level water surface (world y). */
    public static int lakeY() {
        return lakeY;
    }

    /** True when {@code y} is on the main lake (not up in a highland tarn). */
    public static boolean lakeLevel(double y) {
        return y >= lakeY - 6 && y <= lakeY + 3;
    }

    /** Survival players inside the footprint — the ones isle loops reward. */
    public static List<Player> anglers() {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL && onIsle(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Everyone inside the footprint (staff in creative included) — for FX and bars. */
    public static List<Player> visitors() {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (onIsle(player)) {
                out.add(player);
            }
        }
        return out;
    }

    public static World world() {
        return box.enabled() ? Bukkit.getWorld(box.world()) : null;
    }

    /** Footprint for DEV status, or {@code null} when off. */
    public static String describe() {
        Box current = box;
        if (!current.enabled()) {
            return null;
        }
        return current.world() + " §8x " + (int) current.minX() + "…" + (int) current.maxX()
                + " z " + (int) current.minZ() + "…" + (int) current.maxZ();
    }

    /** Where arrivals land ({@code fish-isle.landing}), or {@code null}. */
    public static Location landing(AetherionFishing plugin) {
        return location(plugin.getConfig().getConfigurationSection("fish-isle.landing"));
    }

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
}
