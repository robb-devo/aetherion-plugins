package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaService {

    public static final int RADIUS = 50;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, AreaZone> zones = new ConcurrentHashMap<>();

    public AreaService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "areas.yml");
        load();
        consolidateBorderlandsAreas();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ensureMarkers, 80L, 200L);
    }

    public ItemStack createTool(AreaType type) {
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lArea Tool §8· §f" + type.display());
            meta.setLore(List.of(
                    "§7DEV · mark a region as",
                    "§f" + type.display(),
                    "§7Radius §f" + type.defaultRadius() + " §7blocks.",
                    "",
                    "§eClick a block §7to set.",
                    "§eSneak + click §7removes the nearest",
                    "§7zone of this type."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.areaTool(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.areaId(), PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.areaTool(), PersistentDataType.BYTE);
    }

    public AreaType typeOf(ItemStack item) {
        if (!isTool(item)) {
            return null;
        }
        return AreaType.fromId(item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.areaId(),
                PersistentDataType.STRING
        ));
    }

    public AreaZone place(Location location, AreaType type, Player player) {
        return place(location, type, player, type == null ? RADIUS : type.defaultRadius());
    }

    public AreaZone place(Location location, AreaType type, Player player, int radius) {
        if (location.getWorld() == null || type == null) {
            return null;
        }
        if (type == AreaType.BORDERLANDS) {
            clearType(AreaType.BORDERLANDS);
        }
        if (type == AreaType.COLOSSEUM) {
            clearType(AreaType.COLOSSEUM);
        }
        if (type == AreaType.ELDERVALE) {
            clearType(AreaType.ELDERVALE);
        }
        if (type == AreaType.FORAGE_ISLE) {
            clearType(AreaType.FORAGE_ISLE);
        }
        int useRadius = Math.max(8, radius);
        AreaZone zone = new AreaZone(
                UUID.randomUUID(),
                type,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                useRadius
        );
        spawnMarker(zone);
        zones.put(zone.getId(), zone);
        save();
        if (player != null && player.isOnline()) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1.2, 0), 18, 0.6, 0.4, 0.6, 0);
        }
        if (player != null) {
            player.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            player.sendMessage("§aArea set: §f" + type.display() + " §7(" + useRadius + " block radius).");
        }
        return zone;
    }

    public void clearType(AreaType type) {
        if (type == null) {
            return;
        }
        List<AreaZone> remove = new ArrayList<>();
        for (AreaZone zone : zones.values()) {
            if (zone.getType() == type) {
                remove.add(zone);
            }
        }
        for (AreaZone zone : remove) {
            removeMarker(zone);
            zones.remove(zone.getId());
        }
        if (!remove.isEmpty()) {
            save();
        }
    }

    /** Merge overlapping Borderlands TAB areas into one covering disk. */
    public void consolidateBorderlandsAreas() {
        List<AreaZone> borderlands = new ArrayList<>();
        for (AreaZone zone : zones.values()) {
            if (zone.getType() == AreaType.BORDERLANDS) {
                borderlands.add(zone);
            }
        }
        if (borderlands.size() <= 1) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double ySum = 0;
        String worldName = borderlands.get(0).getWorldName();
        for (AreaZone zone : borderlands) {
            minX = Math.min(minX, zone.getX() - zone.getRadius());
            maxX = Math.max(maxX, zone.getX() + zone.getRadius());
            minZ = Math.min(minZ, zone.getZ() - zone.getRadius());
            maxZ = Math.max(maxZ, zone.getZ() + zone.getRadius());
            ySum += zone.getY();
            removeMarker(zone);
            zones.remove(zone.getId());
        }
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        double cy = ySum / borderlands.size();
        double need = 0;
        for (AreaZone zone : borderlands) {
            double dx = zone.getX() - cx;
            double dz = zone.getZ() - cz;
            need = Math.max(need, Math.sqrt(dx * dx + dz * dz) + zone.getRadius());
        }
        int radius = (int) Math.ceil(need);
        radius = Math.max(40, Math.min(250, radius));
        AreaZone merged = new AreaZone(
                UUID.fromString("b2c3d4e5-f6a7-4890-b123-456789abcdef"),
                AreaType.BORDERLANDS,
                worldName,
                cx,
                cy,
                cz,
                radius
        );
        zones.put(merged.getId(), merged);
        save();
        plugin.getLogger().info("Consolidated " + borderlands.size()
                + " Borderlands areas into one (" + radius + "m).");
    }

    public boolean removeNearest(Location location, AreaType type, Player player) {
        AreaZone zone = nearestOfType(location, type, 200);
        if (zone == null) {
            if (player != null) {
                player.sendMessage("§cNo " + type.display() + " zone nearby.");
            }
            return false;
        }
        removeMarker(zone);
        zones.remove(zone.getId());
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            player.sendMessage("§eRemoved §f" + zone.getType().display() + "§e.");
        }
        return true;
    }

    public String nameAt(Location location) {
        AreaType type = typeAt(location);
        // Crypt is the Y≤55 band inside Borderlands — show as its own area on TAB.
        if (type == AreaType.BORDERLANDS
                && location != null
                && location.getY() <= MobZoneService.CRYPT_CEILING_Y) {
            return "Crypt";
        }
        // Forage habitats (pet-style swamp/mushroom/cherry…) only on FORAGE_ISLE disk
        // or wilderness inside the forage footprint AABB — never overrides other Area Tools.
        boolean onForage = type == AreaType.FORAGE_ISLE
                || (type == null && inForageFootprint(location));
        if (onForage) {
            String habitat = forageHabitatName(location);
            if (habitat != null && !habitat.isBlank()) {
                return habitat;
            }
            if (type == AreaType.FORAGE_ISLE) {
                return type.display();
            }
            return AreaType.FORAGE_ISLE.display();
        }
        if (type != null) {
            return type.display();
        }
        if (location == null || location.getWorld() == null) {
            return "Wilderness";
        }
        String world = location.getWorld().getName();
        if (world.startsWith("aedun_")) {
            return "Dungeon";
        }
        if (world.equals("aether_guilds") || world.startsWith("aether_guild")) {
            String named = namedGuildIsland(location);
            return named == null || named.isBlank() ? "Guild Island" : named;
        }
        if (world.equals("aether_islands") || world.startsWith("aether_island")) {
            String named = namedGuildIsland(location);
            return named == null || named.isBlank() ? "Personal Island" : named;
        }
        return "Wilderness";
    }

    /** Soft-depend AetherionForaging habitat labels for TAB. */
    private static String forageHabitatName(Location location) {
        try {
            Class<?> bridge = Class.forName("de.aetherion.foraging.habitat.ForageHabitatService");
            Object name = bridge.getMethod("resolveDisplay", Location.class).invoke(null, location);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Soft-depend forage-isle light AABB — island footprint, not Area Tool disks. */
    private static boolean inForageFootprint(Location location) {
        try {
            Class<?> bridge = Class.forName("de.aetherion.foraging.habitat.ForageHabitatService");
            Object ok = bridge.getMethod("inIsleFootprint", Location.class).invoke(null, location);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Nearest containing zone type, or null if wilderness / special world. */
    public AreaType typeAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        if (world.startsWith("aedun_")
                || world.equals("aether_guilds")
                || world.startsWith("aether_guild")
                || world.equals("aether_islands")
                || world.startsWith("aether_island")) {
            return null;
        }
        AreaZone best = null;
        double bestDist = Double.MAX_VALUE;
        for (AreaZone zone : zones.values()) {
            if (!zone.contains(location)) {
                continue;
            }
            double dist = zone.distanceSquared(location);
            if (dist < bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best == null ? null : best.getType();
    }

    public boolean isType(Location location, AreaType type) {
        return type != null && typeAt(location) == type;
    }

    public java.util.Collection<AreaZone> zones() {
        return java.util.List.copyOf(zones.values());
    }

    public java.util.List<AreaZone> zonesOf(AreaType type) {
        java.util.List<AreaZone> out = new java.util.ArrayList<>();
        for (AreaZone zone : zones.values()) {
            if (type == null || zone.getType() == type) {
                out.add(zone);
            }
        }
        return out;
    }

    private static String namedGuildIsland(Location location) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("de.aetherion.guilds.AetherionGuilds");
            Object plugin = clazz.getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return null;
            }
            Object title = clazz.getMethod("islandTitle", Location.class).invoke(plugin, location);
            return title instanceof String text ? text : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private AreaZone nearestOfType(Location location, AreaType type, double max) {
        AreaZone best = null;
        double bestDist = max * max;
        for (AreaZone zone : zones.values()) {
            if (type != null && zone.getType() != type) {
                continue;
            }
            if (location.getWorld() == null || !zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dist = zone.distanceSquared(location);
            if (dist <= bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best;
    }

    private void ensureMarkers() {
        for (AreaZone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            Location center = zone.center(world);
            if (!center.getChunk().isLoaded()) {
                continue;
            }
            if (zone.getMarkerId() != null) {
                Entity entity = Bukkit.getEntity(zone.getMarkerId());
                if (entity != null && entity.isValid()) {
                    applyVisibility(entity);
                    continue;
                }
            }
            spawnMarker(zone);
            save();
        }
    }

    public void revealToLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revealTo(player), 15L);
    }

    public void revealTo(Player player) {
        if (player == null) {
            return;
        }
        for (AreaZone zone : zones.values()) {
            if (zone.getMarkerId() == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, player, entity);
            }
        }
    }

    public static boolean isStaff(Player player) {
        return StaffVisibility.isStaff(player);
    }

    private void applyVisibility(Entity entity) {
        StaffVisibility.apply(plugin, entity);
    }

    private void spawnMarker(AreaZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        Location at = zone.center(world).add(0, 0.2, 0);
        ArmorStand stand = world.spawn(at, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSmall(true);
            spawned.setCustomName("§e" + zone.getType().display() + " §8(" + zone.getRadius() + "m)");
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(
                    ItemKeys.areaZone(),
                    PersistentDataType.STRING,
                    zone.getId().toString()
            );
        });
        zone.setMarkerId(stand.getUniqueId());
        applyVisibility(stand);
    }

    private void removeMarker(AreaZone zone) {
        if (zone.getMarkerId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(zone.getMarkerId());
        if (entity != null) {
            entity.remove();
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (AreaZone zone : zones.values()) {
            String path = "zones." + zone.getId();
            config.set(path + ".type", zone.getType().id());
            config.set(path + ".world", zone.getWorldName());
            config.set(path + ".x", zone.getX());
            config.set(path + ".y", zone.getY());
            config.set(path + ".z", zone.getZ());
            config.set(path + ".radius", zone.getRadius());
            if (zone.getMarkerId() != null) {
                config.set(path + ".marker", zone.getMarkerId().toString());
            }
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save areas.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            AreaType type = AreaType.fromId(config.getString("zones." + key + ".type"));
            if (type == null) {
                continue;
            }
            AreaZone zone = new AreaZone(
                    id,
                    type,
                    config.getString("zones." + key + ".world"),
                    config.getDouble("zones." + key + ".x"),
                    config.getDouble("zones." + key + ".y"),
                    config.getDouble("zones." + key + ".z"),
                    config.getInt("zones." + key + ".radius", RADIUS)
            );
            String marker = config.getString("zones." + key + ".marker");
            if (marker != null) {
                try {
                    zone.setMarkerId(UUID.fromString(marker));
                } catch (IllegalArgumentException ignored) {
                }
            }
            zones.put(id, zone);
        }
    }
}
