package de.aetherion.items.world;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class AreaZone {

    private final UUID id;
    private final AreaType type;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final int radius;
    private UUID markerId;

    public AreaZone(UUID id, AreaType type, String worldName, double x, double y, double z, int radius) {
        this.id = id;
        this.type = type;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public UUID getId() {
        return id;
    }

    public AreaType getType() {
        return type;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getRadius() {
        return radius;
    }

    public UUID getMarkerId() {
        return markerId;
    }

    public void setMarkerId(UUID markerId) {
        this.markerId = markerId;
    }

    public Location center(World world) {
        return new Location(world, x, y, z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!worldName.equals(location.getWorld().getName())) {
            return false;
        }
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        // Borderlands / Colosseum / Eldervale: horizontal disk.
        if (type.horizontalDisk()) {
            return dx * dx + dz * dz <= (double) radius * radius;
        }
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    public double distanceSquared(Location location) {
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        if (type.horizontalDisk()) {
            return dx * dx + dz * dz;
        }
        return dx * dx + dy * dy + dz * dz;
    }
}
