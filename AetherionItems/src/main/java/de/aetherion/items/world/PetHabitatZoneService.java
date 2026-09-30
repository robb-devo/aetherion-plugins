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

/**
 * DEV paint disks for wild pet biotopes. AetherMobs reads {@code pet-habitats.yml}.
 */
public final class PetHabitatZoneService implements Runnable {

    public static final int DEFAULT_RADIUS = 50;
    public static final int[] RADII = {30, 50, 80, 120};

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PetHabitatZone> zones = new ConcurrentHashMap<>();

    public PetHabitatZoneService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pet-habitats.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(this.plugin, this, 80L, 200L);
    }

    public ItemStack createAnchor(PetHabitatKind habitat) {
        return createAnchor(habitat, DEFAULT_RADIUS);
    }

    public ItemStack createAnchor(PetHabitatKind habitat, int radius) {
        if (habitat == null) {
            return null;
        }
        int use = normalizeRadius(radius);
        ItemStack item = new ItemStack(habitat.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(habitat.color() + "§lPet Habitat §8· §f" + habitat.display());
            meta.setLore(lore(habitat, use));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.petHabitatAnchor(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.petHabitatId(), PersistentDataType.STRING, habitat.id());
            meta.getPersistentDataContainer().set(ItemKeys.petHabitatRadius(), PersistentDataType.INTEGER, use);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(
                ItemKeys.petHabitatAnchor(),
                PersistentDataType.BYTE
        );
    }

    public PetHabitatKind habitatOf(ItemStack item) {
        if (!isAnchor(item)) {
            return null;
        }
        return PetHabitatKind.fromId(item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.petHabitatId(),
                PersistentDataType.STRING
        ));
    }

    public int radiusOf(ItemStack item) {
        if (!isAnchor(item)) {
            return DEFAULT_RADIUS;
        }
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(
                ItemKeys.petHabitatRadius(),
                PersistentDataType.INTEGER
        );
        return normalizeRadius(stored == null ? DEFAULT_RADIUS : stored);
    }

    public int cycleRadius(ItemStack item) {
        if (!isAnchor(item) || !item.hasItemMeta()) {
            return DEFAULT_RADIUS;
        }
        PetHabitatKind habitat = habitatOf(item);
        if (habitat == null) {
            return DEFAULT_RADIUS;
        }
        int current = radiusOf(item);
        int next = RADII[0];
        for (int i = 0; i < RADII.length; i++) {
            if (RADII[i] == current) {
                next = RADII[(i + 1) % RADII.length];
                break;
            }
        }
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore(habitat, next));
        meta.getPersistentDataContainer().set(ItemKeys.petHabitatRadius(), PersistentDataType.INTEGER, next);
        item.setItemMeta(meta);
        return next;
    }

    private static List<String> lore(PetHabitatKind habitat, int radius) {
        return List.of(
                "§7DEV · paint a wild pet biotope.",
                "§7Habitat §f" + habitat.display(),
                "§7Radius §f" + radius + " §7blocks.",
                "§8Overrides block auto-detect here.",
                "",
                "§eRight-click a block §7to place.",
                "§eLeft-click §7to cycle radius.",
                "§eSneak + right-click §7removes nearby."
        );
    }

    public static int normalizeRadius(int radius) {
        for (int option : RADII) {
            if (option == radius) {
                return option;
            }
        }
        return DEFAULT_RADIUS;
    }

    public PetHabitatZone place(Location location, PetHabitatKind habitat, Player player, int radius) {
        if (location == null || location.getWorld() == null || habitat == null) {
            return null;
        }
        if (TestArenaGuard.isArena(location.getWorld())) {
            if (player != null) {
                player.sendMessage("§cPet habitats are disabled in the Test Arena.");
            }
            return null;
        }
        PetHabitatZone existing = nearest(location, 12);
        if (existing != null) {
            if (player != null) {
                player.sendMessage("§cToo close to another pet habitat. §7Sneak-click to remove first.");
            }
            return null;
        }
        PetHabitatZone zone = new PetHabitatZone(
                UUID.randomUUID(),
                habitat,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                normalizeRadius(radius)
        );
        spawnMarker(zone);
        zones.put(zone.getId(), zone);
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_MOSS_PLACE, 0.8f, 1.2f);
            player.sendMessage(habitat.coloredName() + " §7pet habitat §f" + zone.getRadius()
                    + "m§7. AetherMobs will prefer this biotope.");
        }
        return zone;
    }

    public boolean removeNearest(Location location, Player player) {
        PetHabitatZone zone = nearest(location, 14);
        if (zone == null) {
            if (player != null) {
                player.sendMessage("§cNo pet habitat nearby.");
            }
            return false;
        }
        removeMarker(zone);
        zones.remove(zone.getId());
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_MOSS_BREAK, 0.8f, 0.8f);
            player.sendMessage("§eRemoved " + zone.getHabitat().coloredName() + " §epet habitat.");
        }
        return true;
    }

    public PetHabitatKind habitatAt(Location location) {
        PetHabitatZone zone = zoneAt(location);
        return zone == null ? null : zone.getHabitat();
    }

    public PetHabitatZone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        PetHabitatZone best = null;
        int bestRadius = Integer.MAX_VALUE;
        for (PetHabitatZone zone : zones.values()) {
            if (!zone.containsHorizontal(location)) {
                continue;
            }
            // Smaller disk wins on overlap — more precise paint.
            if (zone.getRadius() < bestRadius) {
                bestRadius = zone.getRadius();
                best = zone;
            }
        }
        return best;
    }

    public java.util.Collection<PetHabitatZone> zones() {
        return List.copyOf(zones.values());
    }

    @Override
    public void run() {
        for (PetHabitatZone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            Location center = zone.center(world);
            if (!center.getChunk().isLoaded()) {
                continue;
            }
            ensureMarker(zone);
            if (Math.random() < 0.18) {
                world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        center.clone().add(0, 1.1, 0),
                        4,
                        0.35,
                        0.25,
                        0.35,
                        0
                );
            }
        }
    }

    public void revealTo(Player player) {
        for (PetHabitatZone zone : zones.values()) {
            if (zone.getMarkerId() == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, player, entity);
            }
        }
    }

    public void revealToLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revealTo(player), 10L);
    }

    private PetHabitatZone nearest(Location location, double max) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        PetHabitatZone best = null;
        double bestDist = max * max;
        for (PetHabitatZone zone : zones.values()) {
            if (!zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = zone.getX() - location.getX();
            double dz = zone.getZ() - location.getZ();
            double dist = dx * dx + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best;
    }

    private void spawnMarker(PetHabitatZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        Location at = zone.center(world).add(0, 0.2, 0);
        PetHabitatKind habitat = zone.getHabitat();
        ArmorStand stand = world.spawn(at, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSmall(true);
            spawned.setCustomName(habitat.coloredName() + " §8Pet Habitat §7(" + zone.getRadius() + "m)");
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(
                    ItemKeys.petHabitatZone(),
                    PersistentDataType.STRING,
                    zone.getId().toString()
            );
        });
        zone.setMarkerId(stand.getUniqueId());
        StaffVisibility.apply(plugin, stand);
    }

    private void ensureMarker(PetHabitatZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        MarkerLifecycle.Result marker = MarkerLifecycle.resolve(
                zone.center(world).add(0, 0.2, 0),
                ItemKeys.petHabitatZone(),
                zone.getId().toString(),
                zone.getMarkerId(),
                6
        );
        if (marker.state() == MarkerLifecycle.State.UNLOADED) {
            return;
        }
        if (marker.state() == MarkerLifecycle.State.KEPT && marker.entity() != null) {
            if (!marker.entity().getUniqueId().equals(zone.getMarkerId())) {
                zone.setMarkerId(marker.entity().getUniqueId());
                save();
            }
            StaffVisibility.apply(plugin, marker.entity());
            return;
        }
        spawnMarker(zone);
        save();
    }

    private void removeMarker(PetHabitatZone zone) {
        if (zone.getMarkerId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(zone.getMarkerId());
        if (entity != null) {
            entity.remove();
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                PetHabitatKind habitat = PetHabitatKind.fromId(section.getString("habitat"));
                if (habitat == null) {
                    continue;
                }
                PetHabitatZone zone = new PetHabitatZone(
                        id,
                        habitat,
                        section.getString("world", "world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        section.getInt("radius", DEFAULT_RADIUS)
                );
                String marker = section.getString("marker");
                if (marker != null && !marker.isBlank()) {
                    zone.setMarkerId(UUID.fromString(marker));
                }
                zones.put(id, zone);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("Loaded " + zones.size() + " pet habitat zone(s).");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PetHabitatZone zone : zones.values()) {
            String path = "zones." + zone.getId();
            yaml.set(path + ".habitat", zone.getHabitat().id());
            yaml.set(path + ".world", zone.getWorldName());
            yaml.set(path + ".x", zone.getX());
            yaml.set(path + ".y", zone.getY());
            yaml.set(path + ".z", zone.getZ());
            yaml.set(path + ".radius", zone.getRadius());
            if (zone.getMarkerId() != null) {
                yaml.set(path + ".marker", zone.getMarkerId().toString());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save pet habitats: " + exception.getMessage());
        }
    }
}
