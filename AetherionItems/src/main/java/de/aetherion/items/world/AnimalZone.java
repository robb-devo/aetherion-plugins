package de.aetherion.items.world;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class AnimalZone {

    private final UUID id;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final int radius;
    private String profile;
    private UUID markerId;

    public AnimalZone(UUID id, String worldName, double x, double y, double z, int radius) {
        this(id, worldName, x, y, z, radius, null);
    }

    public AnimalZone(UUID id, String worldName, double x, double y, double z, int radius, String profile) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.profile = profile;
    }

    public UUID getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getRadius() {
        return radius;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
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
}
