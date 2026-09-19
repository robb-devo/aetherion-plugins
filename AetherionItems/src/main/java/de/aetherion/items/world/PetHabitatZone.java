package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class PetHabitatZone {

    private final UUID id;
    private final PetHabitatKind habitat;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final int radius;
    private UUID markerId;

    public PetHabitatZone(
            UUID id,
            PetHabitatKind habitat,
            String worldName,
            double x,
            double y,
            double z,
            int radius
    ) {
        this.id = id;
        this.habitat = habitat;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(8, radius);
    }

    public UUID getId() {
        return id;
    }

    public PetHabitatKind getHabitat() {
        return habitat;
    }

    public String getWorldName() {
        return worldName;
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

    public boolean containsHorizontal(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!worldName.equals(location.getWorld().getName())) {
            return false;
        }
        double dx = x - location.getX();
        double dz = z - location.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }
}
