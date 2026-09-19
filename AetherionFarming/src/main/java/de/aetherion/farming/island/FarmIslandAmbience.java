package de.aetherion.farming.island;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.Crops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Crop mix, ambient animals, and catchable farm pets on the shared island.
 */
public final class FarmIslandAmbience implements Listener, Runnable {

    private static final EntityType[] AMBIENT = {
            EntityType.CHICKEN,
            EntityType.COW,
            EntityType.PIG,
            EntityType.SHEEP,
            EntityType.RABBIT
    };

    private static final String[] FARM_PET_IDS = {
            "pig", "cow", "sheep", "farm_rabbit", "horse", "sack_of_potatoes",
            "bee", "butterfly"
    };

    private final AetherionFarming plugin;
    private final FarmIslandService island;
    private final NamespacedKey ambientKey;
    private final NamespacedKey petMarkerKey;

    public FarmIslandAmbience(AetherionFarming plugin, FarmIslandService island) {
        this.plugin = plugin;
        this.island = island;
        this.ambientKey = new NamespacedKey(plugin, "farm_island_ambient");
        this.petMarkerKey = new NamespacedKey(plugin, "farm_island_pet");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 20L * 45);
    }

    /** Call after paste / rebuild. */
    public void setup(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (world == null) {
            return;
        }
        diversifyAndRipenCrops(world, minX, maxX, minY, maxY, minZ, maxZ);
        lightIsland(world, minX, maxX, minZ, maxZ);
        clearAmbience(world);
        int animals = spawnAmbientAnimals(world, 14);
        int pets = spawnFarmPets(world, 12);
        plugin.getLogger().info("Farm island seeded ambient=+" + animals + " pets=+" + pets);
        plugin.getConfig().set("farm-island.ambience-ready", true);
        plugin.saveConfig();
    }

    /** For an already-pasted island: crop mix + pets around the exit. */
    public String refreshNearExit() {
        World world = Bukkit.getWorld(island.worldName());
        if (world == null) {
            world = island.ensureWorld();
        }
        if (world == null) {
            return "§cFarm island world missing.";
        }
        Location exit = null;
        if (plugin.portals() != null) {
            exit = plugin.portals().islandExit();
        }
        int cx = exit != null ? exit.getBlockX() : 0;
        int cy = exit != null ? exit.getBlockY() : 66;
        int cz = exit != null ? exit.getBlockZ() : 0;
        int r = 56;
        setup(world, cx - r, cx + r, cy - 24, cy + 40, cz - r, cz + r);
        return "§aFarm island refreshed (full crops, lights, animals, pets).";
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(island.worldName());
        if (world == null || !island.isPasted()) {
            return;
        }
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        int ambient = countTagged(world, ambientKey);
        if (ambient < 9) {
            int added = spawnAmbientAnimals(world, 14 - ambient);
            if (added > 0) {
                plugin.getLogger().info("Farm island ambient animals: +" + added + " (now ~" + (ambient + added) + ")");
            }
        }
        int pets = countFarmPets(world);
        if (pets < 6) {
            int want = Math.min(8, 12 - pets);
            int added = spawnFarmPets(world, want);
            if (added > 0) {
                plugin.getLogger().info("Farm island catchable pets: +" + added + " (now ~" + (pets + added) + ")");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(island.worldName())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Monster || entity instanceof Phantom) {
            event.setCancelled(true);
            return;
        }
        // Block natural animal floods — we seed our own ambience.
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            event.setCancelled(true);
        }
    }

    private void diversifyAndRipenCrops(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int wheat = 0;
        int carrots = 0;
        int potatoes = 0;
        int ripened = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = Math.min(minX, maxX); x <= Math.max(minX, maxX); x++) {
            for (int y = Math.min(minY, maxY); y <= Math.max(minY, maxY); y++) {
                for (int z = Math.min(minZ, maxZ); z <= Math.max(minZ, maxZ); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.WHEAT) {
                        wheat++;
                        double roll = random.nextDouble();
                        if (roll < 0.28) {
                            type = Material.CARROTS;
                            carrots++;
                        } else if (roll < 0.56) {
                            type = Material.POTATOES;
                            potatoes++;
                        }
                    }
                    if (!Crops.isCrop(type) && type != Material.WHEAT
                            && type != Material.CARROTS && type != Material.POTATOES
                            && type != Material.BEETROOTS && type != Material.NETHER_WART
                            && type != Material.MELON_STEM && type != Material.PUMPKIN_STEM
                            && type != Material.TORCHFLOWER_CROP && type != Material.PITCHER_CROP) {
                        continue;
                    }
                    if (!(type.createBlockData() instanceof Ageable)) {
                        continue;
                    }
                    Ageable age = (Ageable) type.createBlockData();
                    age.setAge(age.getMaximumAge());
                    block.setBlockData(age, false);
                    ripened++;
                }
            }
        }
        plugin.getLogger().info("Farm island crops ripened=" + ripened
                + " carrots=" + carrots + " potatoes=" + potatoes + " (scanned wheat=" + wheat + ")");
    }

    private void lightIsland(World world, int minX, int maxX, int minZ, int maxZ) {
        int placed = 0;
        int step = 4;
        int yPad = 40;
        Location exit = plugin.portals() != null ? plugin.portals().islandExit() : null;
        int midY = exit != null ? exit.getBlockY() : 66;
        for (int x = Math.min(minX, maxX); x <= Math.max(minX, maxX); x += step) {
            for (int z = Math.min(minZ, maxZ); z <= Math.max(minZ, maxZ); z += step) {
                int y = world.getHighestBlockYAt(x, z);
                if (y < midY - yPad || y > midY + yPad) {
                    continue;
                }
                Block ground = world.getBlockAt(x, y, z);
                Block air = ground.getRelative(0, 1, 0);
                if (air.getType().isSolid() && air.getType() != Material.LIGHT) {
                    continue;
                }
                if (air.getLightFromBlocks() >= 10 && air.getLightFromSky() >= 10) {
                    continue;
                }
                if (air.getType() != Material.AIR && air.getType() != Material.LIGHT
                        && !air.getType().isAir()) {
                    continue;
                }
                org.bukkit.block.data.type.Light data =
                        (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
                data.setLevel(15);
                air.setBlockData(data, false);
                placed++;
            }
        }
        plugin.getLogger().info("Farm island soft lights placed: " + placed);
    }

    private int spawnAmbientAnimals(World world, int want) {
        if (want <= 0) {
            return 0;
        }
        List<Location> spots = softGroundSpots(world, 48);
        if (spots.isEmpty()) {
            spots.add(new Location(world, 0.5, 66, 0.5));
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            Location at = spots.get(random.nextInt(spots.size())).clone().add(0.5, 0.1, 0.5);
            EntityType type = AMBIENT[random.nextInt(AMBIENT.length)];
            Entity entity = world.spawnEntity(at, type);
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                continue;
            }
            living.setRemoveWhenFarAway(false);
            living.setPersistent(true);
            living.setCanPickupItems(false);
            living.getPersistentDataContainer().set(ambientKey, PersistentDataType.BYTE, (byte) 1);
            if (living instanceof Animals animals) {
                animals.setBreed(false);
                if (random.nextDouble() < 0.25) {
                    animals.setBaby();
                }
            }
            spawned++;
        }
        return spawned;
    }

    private int spawnFarmPets(World world, int want) {
        if (want <= 0) {
            return 0;
        }
        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs == null || !mobs.isEnabled()) {
            plugin.getLogger().warning("AetherMobs missing — farm pets not seeded.");
            return 0;
        }
        List<Location> spots = softGroundSpots(world, 40);
        if (spots.isEmpty()) {
            return 0;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            String petId = FARM_PET_IDS[i % FARM_PET_IDS.length];
            Location at = spots.get(random.nextInt(spots.size())).clone().add(0.5, 0.2, 0.5);
            if (spawnCatchablePet(mobs, petId, at)) {
                spawned++;
            }
        }
        return spawned;
    }

    private boolean spawnCatchablePet(Plugin mobs, String petId, Location at) {
        try {
            Object registry = mobs.getClass().getMethod("getPetRegistry").invoke(mobs);
            if (registry == null) {
                return false;
            }
            Object definition = registry.getClass().getMethod("get", String.class).invoke(registry, petId);
            if (definition == null) {
                return false;
            }
            Object generator = mobs.getClass().getMethod("getPetGenerator").invoke(mobs);
            if (generator == null) {
                return false;
            }
            Class<?> defClass = Class.forName("de.aetherion.aethermobs.pet.PetDefinition");
            Class<?> instanceClass = Class.forName("de.aetherion.aethermobs.pet.PetInstance");
            Class<?> petEntityClass = Class.forName("de.aetherion.aethermobs.pet.PetEntity");
            Object generated = generator.getClass().getMethod("generate", defClass).invoke(generator, definition);
            Object entity = petEntityClass.getConstructor(instanceClass).newInstance(generated);
            petEntityClass.getMethod("spawn", Location.class, boolean.class).invoke(entity, at, true);
            Boolean spawned = (Boolean) petEntityClass.getMethod("isSpawned").invoke(entity);
            if (spawned == null || !spawned) {
                return false;
            }
            Object spawnManager = mobs.getClass().getMethod("getPetSpawnManager").invoke(mobs);
            if (spawnManager != null) {
                spawnManager.getClass().getMethod("registerTestPet", petEntityClass).invoke(spawnManager, entity);
            }
            // Pets are ItemDisplays — mark the display entity, not LivingEntity.
            Object bukkitEntity = petEntityClass.getMethod("getEntity").invoke(entity);
            if (bukkitEntity instanceof Entity display) {
                display.getPersistentDataContainer().set(petMarkerKey, PersistentDataType.BYTE, (byte) 1);
                display.setPersistent(true);
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not seed farm pet " + petId + ": " + exception.getMessage());
            return false;
        }
    }

    private void clearAmbience(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(ambientKey, PersistentDataType.BYTE)
                    || entity.getPersistentDataContainer().has(petMarkerKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    /** Prefer AetherMobs' live pet count; fall back to our PDC tags on ItemDisplays. */
    private int countFarmPets(World world) {
        int viaMobs = countPetsViaAetherMobs(world);
        int tagged = countTagged(world, petMarkerKey);
        return Math.max(viaMobs, tagged);
    }

    private int countPetsViaAetherMobs(World world) {
        try {
            Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (mobs == null || !mobs.isEnabled()) {
                return 0;
            }
            Object spawnManager = mobs.getClass().getMethod("getPetSpawnManager").invoke(mobs);
            if (spawnManager == null) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            List<Object> active = (List<Object>) spawnManager.getClass().getMethod("getActivePets").invoke(spawnManager);
            if (active == null || active.isEmpty()) {
                return 0;
            }
            int count = 0;
            Class<?> petEntityClass = Class.forName("de.aetherion.aethermobs.pet.PetEntity");
            for (Object pet : active) {
                if (pet == null) {
                    continue;
                }
                Boolean spawned = (Boolean) petEntityClass.getMethod("isSpawned").invoke(pet);
                if (spawned == null || !spawned) {
                    continue;
                }
                Object owner = petEntityClass.getMethod("getOwner").invoke(pet);
                if (owner != null) {
                    continue;
                }
                Object display = petEntityClass.getMethod("getEntity").invoke(pet);
                if (display instanceof Entity entity
                        && entity.getWorld() != null
                        && entity.getWorld().equals(world)) {
                    count++;
                }
            }
            return count;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return 0;
        }
    }

    private int countTagged(World world, NamespacedKey key) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                count++;
            }
        }
        return count;
    }

    private List<Location> softGroundSpots(World world, int budget) {
        List<Location> spots = new ArrayList<>();
        Location exit = null;
        try {
            if (plugin.portals() != null) {
                exit = plugin.portals().islandExit();
            }
        } catch (Exception ignored) {
        }
        int cx = exit != null ? exit.getBlockX() : 0;
        int cz = exit != null ? exit.getBlockZ() : 0;
        int cy = exit != null ? exit.getBlockY() : 66;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < budget * 4 && spots.size() < budget; i++) {
            int x = cx + random.nextInt(-18, 19);
            int z = cz + random.nextInt(-18, 19);
            int y = world.getHighestBlockYAt(x, z);
            if (y < 40 || y > 120) {
                y = cy;
            }
            Block ground = world.getBlockAt(x, y, z);
            Block above = ground.getRelative(0, 1, 0);
            Material g = ground.getType();
            if (!g.isSolid() || above.getType().isSolid()) {
                continue;
            }
            if (Crops.isCrop(above.getType()) || above.getType() == Material.NETHER_PORTAL) {
                continue;
            }
            spots.add(above.getLocation());
        }
        return spots;
    }
}
