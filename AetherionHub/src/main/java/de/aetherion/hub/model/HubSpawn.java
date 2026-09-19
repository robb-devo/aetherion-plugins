package de.aetherion.hub.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class HubSpawn {

    private final String id;
    private String displayName;
    private String description;
    private Material icon;
    private int slot;
    private boolean unlockedByDefault;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean hasLocation;

    private double discoverRadius;
    private boolean hasDiscoverRadius;

    public HubSpawn(String id) {
        this.id = id;
        this.displayName = id;
        this.description = "";
        this.icon = Material.BEACON;
        this.slot = 13;
        this.unlockedByDefault = false;
        this.discoverRadius = 0;
        this.hasDiscoverRadius = false;
    }

    public static HubSpawn fromConfig(String id, ConfigurationSection section) {
        HubSpawn spawn = new HubSpawn(id);

        if (section == null) {
            return spawn;
        }

        spawn.displayName = section.getString("display-name", id);
        spawn.description = section.getString("description", "");
        spawn.slot = section.getInt("slot", 13);
        spawn.unlockedByDefault = section.getBoolean("unlocked-by-default", false);
        if (section.contains("discover-radius")) {
            spawn.discoverRadius = section.getDouble("discover-radius");
            spawn.hasDiscoverRadius = true;
        }

        try {
            spawn.icon = Material.valueOf(section.getString("icon", "BEACON").toUpperCase());
        } catch (IllegalArgumentException ignored) {
            spawn.icon = Material.BEACON;
        }

        ConfigurationSection location = section.getConfigurationSection("location");
        if (location != null && location.getString("world") != null) {
            spawn.worldName = location.getString("world");
            spawn.x = location.getDouble("x");
            spawn.y = location.getDouble("y");
            spawn.z = location.getDouble("z");
            spawn.yaw = (float) location.getDouble("yaw");
            spawn.pitch = (float) location.getDouble("pitch");
            spawn.hasLocation = spawn.worldName != null && !spawn.worldName.isBlank();
        }

        return spawn;
    }

    public void setLocation(Location location) {
        this.worldName = location.getWorld() == null ? null : location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.hasLocation = this.worldName != null;
    }

    public Location toLocation() {
        if (!hasLocation || worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    public void writeTo(ConfigurationSection section) {
        section.set("display-name", displayName);
        section.set("description", description);
        section.set("icon", icon.name());
        section.set("slot", slot);
        section.set("unlocked-by-default", unlockedByDefault);
        if (hasDiscoverRadius) {
            section.set("discover-radius", discoverRadius);
        }

        if (hasLocation && worldName != null) {
            section.set("location.world", worldName);
            section.set("location.x", x);
            section.set("location.y", y);
            section.set("location.z", z);
            section.set("location.yaw", yaw);
            section.set("location.pitch", pitch);
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Material icon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public int slot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public boolean unlockedByDefault() {
        return unlockedByDefault;
    }

    public void setUnlockedByDefault(boolean unlockedByDefault) {
        this.unlockedByDefault = unlockedByDefault;
    }

    public boolean hasLocation() {
        return hasLocation;
    }

    public boolean hasDiscoverRadius() {
        return hasDiscoverRadius;
    }

    public double discoverRadius() {
        return discoverRadius;
    }

    public void setDiscoverRadius(double discoverRadius) {
        this.discoverRadius = discoverRadius;
        this.hasDiscoverRadius = true;
    }
}
