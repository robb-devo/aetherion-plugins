package de.aetherion.foraging.isle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A named place inside a district — the Pagoda, the Hollow, the Bell Lodge… Loaded from
 * {@code forage-isle.yml → landmarks}. {@code anchor} is a measured standing spot (teleports, the
 * wayfinder and fall-catch checkpoints use it); the box (or radius) is what "being there" means.
 * A landmark with a {@code grove} overrides the district grid inside its box — the Pagoda is the
 * Blossom Shelf's even though ornamental oaks stand around it.
 */
public record Landmark(
        String id,
        String display,
        Grove grove,
        double ax, double ay, double az,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        String blurb
) {

    public static Landmark fromConfig(String id, ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        double[] anchor = ForageConfig.xyz(sec.getString("anchor"));
        if (anchor == null) {
            return null;
        }
        int[] min;
        int[] max;
        double[] boxMin = ForageConfig.xyz(sec.getString("min"));
        double[] boxMax = ForageConfig.xyz(sec.getString("max"));
        if (boxMin != null && boxMax != null) {
            min = new int[] {(int) Math.floor(Math.min(boxMin[0], boxMax[0])), (int) Math.floor(Math.min(boxMin[1], boxMax[1])),
                    (int) Math.floor(Math.min(boxMin[2], boxMax[2]))};
            max = new int[] {(int) Math.floor(Math.max(boxMin[0], boxMax[0])), (int) Math.floor(Math.max(boxMin[1], boxMax[1])),
                    (int) Math.floor(Math.max(boxMin[2], boxMax[2]))};
        } else {
            int r = Math.max(3, sec.getInt("radius", 10));
            int ry = Math.max(3, sec.getInt("height", 8));
            min = new int[] {(int) anchor[0] - r, (int) anchor[1] - ry, (int) anchor[2] - r};
            max = new int[] {(int) anchor[0] + r, (int) anchor[1] + ry, (int) anchor[2] + r};
        }
        return new Landmark(
                id.toLowerCase(java.util.Locale.ROOT),
                sec.getString("display", ForageText.pretty(id)),
                Grove.byId(sec.getString("grove")),
                anchor[0], anchor[1], anchor[2],
                min[0], min[1], min[2], max[0], max[1], max[2],
                sec.getString("blurb", ""));
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public Location anchor(World world) {
        return world == null ? null : new Location(world, ax, ay, az);
    }

    public String colored() {
        return (grove == null ? "§f" : grove.color()) + display;
    }
}
