package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AnimalZoneService implements Runnable {

    public static final int RADIUS = 40;
    /** Soft zone population; hard density is {@link WildlifeLooks#MAX_ANIMALS_PER_CHUNK}. */
    public static final int TARGET = 5;

    private static final Map<EntityType, Integer> MIX = new EnumMap<>(EntityType.class);

    static {
        MIX.put(EntityType.COW, 4);
        MIX.put(EntityType.PIG, 4);
        MIX.put(EntityType.SHEEP, 4);
        MIX.put(EntityType.CHICKEN, 4);
        MIX.put(EntityType.RABBIT, 3);
        MIX.put(EntityType.HORSE, 2);
        MIX.put(EntityType.DONKEY, 1);
        MIX.put(EntityType.GOAT, 2);
        MIX.put(EntityType.FOX, 3);
        MIX.put(EntityType.TURTLE, 2);
        MIX.put(EntityType.POLAR_BEAR, 1);
        MIX.put(EntityType.CAMEL, 1);
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, AnimalZone> zones = new ConcurrentHashMap<>();

    public AnimalZoneService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "animal-zones.yml");
        load();
        plugin.getServer().getScheduler().runTaskLater(plugin, this::purgeOrphans, 40L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::hardCullAllZones, 60L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 200L);
    }

    public ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.HAY_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lAnimal Anchor");
            meta.setLore(List.of(
                    "§7DEV · populate a region with animals.",
                    "§7Radius §f" + RADIUS + " §7· target §f" + TARGET,
                    "§7Max §f" + WildlifeLooks.MAX_ANIMALS_PER_CHUNK + " §7animals per chunk.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7to remove nearby."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.animalAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.animalAnchor(), PersistentDataType.BYTE);
    }

    public AnimalZone place(Location location, Player player) {
        if (location.getWorld() == null) {
            return null;
        }
        if (TestArenaGuard.isArena(location.getWorld())) {
            if (player != null) {
                player.sendMessage("§cAnimal zones are disabled in the Test Arena.");
            }
            return null;
        }
        AnimalZone existing = nearest(location, 24);
        if (existing != null) {
            if (player != null) {
                player.sendMessage("§cToo close to another animal zone.");
            }
            return null;
        }
        AnimalZone zone = new AnimalZone(
                UUID.randomUUID(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                RADIUS
        );
        spawnMarker(zone);
        zones.put(zone.getId(), zone);
        save();
        seed(zone, 3);
        if (player != null) {
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
            player.sendMessage("§aAnimal zone set. §7Up to §f" + TARGET
                    + " §7animals, max §f" + WildlifeLooks.MAX_ANIMALS_PER_CHUNK + " §7per chunk.");
        }
        return zone;
    }

    public boolean removeNearest(Location location, Player player) {
        AnimalZone zone = nearest(location, 10);
        if (zone == null) {
            if (player != null) {
                player.sendMessage("§cNo animal zone nearby.");
            }
            return false;
        }
        removeMarker(zone);
        despawnTagged(zone);
        zones.remove(zone.getId());
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_GRASS_BREAK, 0.8f, 0.8f);
            player.sendMessage("§eRemoved animal zone.");
        }
        return true;
    }

    @Override
    public void run() {
        for (AnimalZone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null || TestArenaGuard.isArena(world)) {
                continue;
            }
            Location center = zone.center(world);
            if (!center.getChunk().isLoaded()) {
                continue;
            }
            ensureMarker(zone);
            enforce(zone, world);
            trimChunkDensity(zone, world);
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0, 1.2, 0), 6, 0.4, 0.3, 0.4, 0);
            }
            int living = count(zone, world);
            if (living >= TARGET) {
                continue;
            }
            int need = Math.min(1, TARGET - living);
            seed(zone, need);
        }
    }

    private void seed(AnimalZone zone, int amount) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        int spawned = 0;
        int attempts = 0;
        while (spawned < amount && attempts < amount * 12) {
            attempts++;
            Location spot = findSpot(zone, world);
            if (spot == null) {
                continue;
            }
            if (!WildlifeLooks.chunkHasRoom(spot)) {
                continue;
            }
            EntityType type = WildlifeLooks.pickAnimal(world, spot);
            LivingEntity entity = (LivingEntity) world.spawnEntity(spot, type);
            decorate(entity, zone);
            spawned++;
        }
    }

    /** Despawn extras when a chunk is packed (old anchors left piles in one field). */
    private void trimChunkDensity(AnimalZone zone, World world) {
        Location center = zone.center(world);
        Map<Long, List<LivingEntity>> byChunk = new java.util.HashMap<>();
        double scan = zone.getRadius() + 8;
        for (Entity entity : world.getNearbyEntities(center, scan, 24, scan)) {
            if (!(entity instanceof Animals living) || !living.isValid()) {
                continue;
            }
            if (!isManaged(living)) {
                continue;
            }
            long key = (((long) living.getLocation().getChunk().getX()) << 32)
                    ^ (living.getLocation().getChunk().getZ() & 0xffffffffL);
            byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(living);
        }
        int removed = 0;
        for (List<LivingEntity> pack : byChunk.values()) {
            if (pack.size() <= WildlifeLooks.MAX_ANIMALS_PER_CHUNK) {
                continue;
            }
            pack.sort((a, b) -> {
                boolean aTagged = belongs(a, zone.getId());
                boolean bTagged = belongs(b, zone.getId());
                if (aTagged != bTagged) {
                    return aTagged ? -1 : 1; // remove tagged first
                }
                return Double.compare(
                        b.getLocation().distanceSquared(center),
                        a.getLocation().distanceSquared(center)
                );
            });
            while (pack.size() > WildlifeLooks.MAX_ANIMALS_PER_CHUNK) {
                LivingEntity extra = pack.remove(0);
                WildlifeLooks.discard(extra);
                extra.remove();
                removed++;
            }
        }
        if (removed > 0 && ThreadLocalRandom.current().nextInt(8) == 0) {
            plugin.getLogger().info("Animal zone trimmed " + removed + " overcrowded animals.");
        }
    }

    private EntityType pickType(AnimalZone zone, World world) {
        List<EntityType> open = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> entry : MIX.entrySet()) {
            int have = 0;
            for (LivingEntity entity : tagged(zone, world)) {
                if (entity.getType() == entry.getKey()) {
                    have++;
                }
            }
            if (have < entry.getValue()) {
                open.add(entry.getKey());
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        return open.get(ThreadLocalRandom.current().nextInt(open.size()));
    }

    private int count(AnimalZone zone, World world) {
        return tagged(zone, world).size();
    }

    private void decorate(LivingEntity entity, AnimalZone zone) {
        if (entity instanceof Ageable ageable && ThreadLocalRandom.current().nextDouble() < 0.18) {
            ageable.setBaby();
        }
        if (entity instanceof Sheep sheep) {
            org.bukkit.DyeColor[] colors = org.bukkit.DyeColor.values();
            sheep.setColor(colors[ThreadLocalRandom.current().nextInt(colors.length)]);
        }
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        tag(entity, zone.getId());
    }

    private void tag(LivingEntity entity, UUID zoneId) {
        entity.getPersistentDataContainer().set(
                ItemKeys.zoneSpawn(),
                PersistentDataType.STRING,
                zoneId.toString()
        );
    }

    private boolean belongs(Entity entity, UUID zoneId) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        String tagged = entity.getPersistentDataContainer().get(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
        return zoneId.toString().equals(tagged);
    }

    private boolean isManaged(Entity entity) {
        return entity instanceof Animals && MIX.containsKey(entity.getType()) && entity.isValid();
    }

    private List<LivingEntity> tagged(AnimalZone zone, World world) {
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (isManaged(entity) && belongs(entity, zone.getId())) {
                found.add(entity);
            }
        }
        return found;
    }

    private void enforce(AnimalZone zone, World world) {
        Location center = zone.center(world);
        List<LivingEntity> members = tagged(zone, world);
        double claimRadius = zone.getRadius() + 8;
        for (Entity entity : world.getNearbyEntities(center, claimRadius, 24, claimRadius)) {
            if (!isManaged(entity) || belongs(entity, zone.getId())) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (members.size() < TARGET) {
                tag(living, zone.getId());
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
                members.add(living);
            } else {
                WildlifeLooks.discard(living);
                living.remove();
            }
        }
        members.sort((a, b) -> Double.compare(
                b.getLocation().distanceSquared(center),
                a.getLocation().distanceSquared(center)
        ));
        while (members.size() > TARGET) {
            LivingEntity extra = members.remove(0);
            WildlifeLooks.discard(extra);
            extra.remove();
        }
        double maxDist = zone.getRadius() + 6;
        double maxDistSq = maxDist * maxDist;
        for (LivingEntity living : members) {
            if (!living.isValid()) {
                continue;
            }
            if (living.getLocation().distanceSquared(center) <= maxDistSq) {
                continue;
            }
            Location spot = findSpot(zone, world);
            living.teleport(spot == null ? center.clone().add(0.5, 1, 0.5) : spot);
        }
    }

    /** Wipe tagged zone animals then reseed to the soft target. */
    private void hardCullAllZones() {
        int removed = 0;
        for (AnimalZone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            for (LivingEntity entity : List.copyOf(tagged(zone, world))) {
                WildlifeLooks.discard(entity);
                entity.remove();
                removed++;
            }
            trimChunkDensity(zone, world);
            seed(zone, Math.min(3, TARGET));
        }
        if (removed > 0) {
            plugin.getLogger().info("Animal zones hard-culled " + removed + " animals.");
        }
    }

    private void despawnTagged(AnimalZone zone) {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            return;
        }
        for (LivingEntity entity : tagged(zone, world)) {
            WildlifeLooks.discard(entity);
            entity.remove();
        }
    }

    public void revealToLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revealTo(player), 15L);
    }

    public void revealTo(Player player) {
        if (player == null) {
            return;
        }
        for (AnimalZone zone : zones.values()) {
            if (zone.getMarkerId() == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, player, entity);
            }
        }
    }

    private Location findSpot(AnimalZone zone, World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = zone.center(world);
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 4 + random.nextDouble() * Math.max(6, zone.getRadius() - 6);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            Location ground = standOn(world, x, center.getBlockY(), z);
            if (ground == null) {
                continue;
            }
            Chunk chunk = ground.getChunk();
            if (!chunk.isLoaded()) {
                continue;
            }
            if (!WildlifeLooks.chunkHasRoom(ground)) {
                continue;
            }
            boolean crowded = false;
            for (Entity nearby : world.getNearbyEntities(ground, 4.5, 3, 4.5)) {
                if (nearby instanceof Animals) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                return ground;
            }
        }
        return null;
    }

    private Location standOn(World world, int x, int originY, int z) {
        for (int y = originY + 4; y >= originY - 8; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = block.getRelative(0, 1, 0);
            Block head = block.getRelative(0, 2, 0);
            if (!block.getType().isSolid() || block.isLiquid()) {
                continue;
            }
            if (block.getType() == Material.OAK_LEAVES
                    || block.getType().name().endsWith("_LEAVES")
                    || block.getType() == Material.BARRIER) {
                continue;
            }
            if (!above.getType().isAir() || !head.getType().isAir()) {
                continue;
            }
            if (above.isLiquid() || head.isLiquid()) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
    }

    public void purgeOrphans() {
        Set<String> live = new HashSet<>();
        for (UUID id : zones.keySet()) {
            live.add(id.toString());
        }
        int animals = 0;
        int markers = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                var data = entity.getPersistentDataContainer();
                if (data.has(ItemKeys.animalZone(), PersistentDataType.STRING)) {
                    String id = data.get(ItemKeys.animalZone(), PersistentDataType.STRING);
                    if (id == null || !live.contains(id)) {
                        entity.remove();
                        markers++;
                    }
                    continue;
                }
                if (!(entity instanceof Animals)) {
                    continue;
                }
                String spawn = data.get(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
                if (spawn != null && !live.contains(spawn)) {
                    WildlifeLooks.discard((LivingEntity) entity);
                    entity.remove();
                    animals++;
                }
            }
        }
        plugin.getLogger().info("Cleared leftover farm anchors: " + animals + " animals, " + markers + " markers.");
    }

    private AnimalZone nearest(Location location, double max) {
        AnimalZone best = null;
        double bestDist = max * max;
        for (AnimalZone zone : zones.values()) {
            if (!zone.getWorldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = zone.getX() - location.getX();
            double dy = zone.getY() - location.getY();
            double dz = zone.getZ() - location.getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best;
    }

    private void spawnMarker(AnimalZone zone) {
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
            spawned.setCustomName("§aAnimal Zone §8(" + zone.getRadius() + "m)");
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(
                    ItemKeys.animalZone(),
                    PersistentDataType.STRING,
                    zone.getId().toString()
            );
        });
        zone.setMarkerId(stand.getUniqueId());
        StaffVisibility.apply(plugin, stand);
    }

    private void ensureMarker(AnimalZone zone) {
        if (zone.getMarkerId() != null) {
            Entity entity = Bukkit.getEntity(zone.getMarkerId());
            if (entity != null && entity.isValid()) {
                StaffVisibility.apply(plugin, entity);
                return;
            }
        }
        spawnMarker(zone);
        save();
    }

    private void removeMarker(AnimalZone zone) {
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
                AnimalZone zone = new AnimalZone(
                        id,
                        section.getString("world", "world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        section.getInt("radius", RADIUS)
                );
                String marker = section.getString("marker");
                if (marker != null && !marker.isBlank()) {
                    zone.setMarkerId(UUID.fromString(marker));
                }
                zones.put(id, zone);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("Loaded " + zones.size() + " animal zone(s).");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AnimalZone zone : zones.values()) {
            String path = "zones." + zone.getId();
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
            plugin.getLogger().warning("Could not save animal zones: " + exception.getMessage());
        }
    }
}
