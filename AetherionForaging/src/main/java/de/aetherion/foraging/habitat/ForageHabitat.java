package de.aetherion.foraging.habitat;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Display + gameplay sub-zone inside the Forage Isle disk (TAB area label).
 */
public final class ForageHabitat {

    private final String id;
    private final String display;
    private final int priority;
    private final String world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public ForageHabitat(
            String id,
            String display,
            int priority,
            String world,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        this.id = id;
        this.display = display;
        this.priority = priority;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static ForageHabitat fromConfig(String id, ConfigurationSection sec) {
        if (sec == null || id == null || id.isBlank()) {
            return null;
        }
        String display = sec.getString("display", id);
        int priority = sec.getInt("priority", 0);
        String world = sec.getString("world", "world");
        ConfigurationSection min = sec.getConfigurationSection("min");
        ConfigurationSection max = sec.getConfigurationSection("max");
        if (min == null || max == null) {
            return null;
        }
        return new ForageHabitat(
                id.toLowerCase(),
                display,
                priority,
                world,
                min.getDouble("x"),
                min.getDouble("y", 0),
                min.getDouble("z"),
                max.getDouble("x"),
                max.getDouble("y", 256),
                max.getDouble("z")
        );
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!world.equalsIgnoreCase(loc.getWorld().getName())) {
            return false;
        }
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public int priority() {
        return priority;
    }

    public String describe() {
        return display + " §8[" + id + "] §7"
                + String.format("x[%.0f..%.0f] y[%.0f..%.0f] z[%.0f..%.0f]",
                minX, maxX, minY, maxY, minZ, maxZ);
    }
}
