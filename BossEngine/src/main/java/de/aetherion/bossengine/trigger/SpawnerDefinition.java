package de.aetherion.bossengine.trigger;

import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.SpawnCondition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class SpawnerDefinition {

    private final String id;
    private final boolean enabled;
    private final String bossId;
    private final SpawnType type;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String itemId;
    private final double altarRadius;
    private final long intervalTicks;
    private final SpawnCondition conditions;

    public SpawnerDefinition(
            String id,
            boolean enabled,
            String bossId,
            SpawnType type,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String itemId,
            double altarRadius,
            long intervalTicks,
            SpawnCondition conditions
    ) {
        this.id = id;
        this.enabled = enabled;
        this.bossId = bossId;
        this.type = type;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.itemId = itemId;
        this.altarRadius = altarRadius;
        this.intervalTicks = intervalTicks;
        this.conditions = conditions;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBossId() {
        return bossId;
    }

    public SpawnType getType() {
        return type;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getItemId() {
        return itemId;
    }

    public double getAltarRadius() {
        return altarRadius;
    }

    public long getIntervalTicks() {
        return intervalTicks;
    }

    public SpawnCondition getConditions() {
        return conditions;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isInsideAltar(Location location) {
        Location altar = toLocation();
        if (altar == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (!altar.getWorld().equals(location.getWorld())) {
            return false;
        }
        double radius = Math.max(1.0, altarRadius);
        return altar.distanceSquared(location) <= radius * radius;
    }

    public LeashAction getLeashAction() {
        return conditions.getLeashAction();
    }
}
