package de.aetherion.items.world;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BossSpawnAccess;
import de.aetherion.core.world.VoidChunkGenerator;
import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated void sandbox for boss / content experiments — not part of the live loop.
 */
public final class TestArenaService {

    public static final String WORLD_NAME = "aether_test";
    public static final int PLATFORM_RADIUS = 48;
    public static final int PLATFORM_Y = 64;

    private final AetherionItems plugin;
    private final Map<UUID, Location> returnPoints = new ConcurrentHashMap<>();
    private boolean platformReady;

    public TestArenaService(AetherionItems plugin) {
        this.plugin = plugin;
    }

    public World ensureWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            applyRules(existing);
            if (!platformReady) {
                ensurePlatform(existing);
            }
            return existing;
        }
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().warning("Could not create test arena world " + WORLD_NAME);
            return null;
        }
        applyRules(world);
        ensurePlatform(world);
        plugin.getLogger().info("Test arena ready: " + WORLD_NAME);
        return world;
    }

    public Location arenaSpawn(World world) {
        return new Location(world, 0.5, PLATFORM_Y + 1.0, 0.5, 0f, 0f);
    }

    public boolean enter(Player player) {
        World world = ensureWorld();
        if (world == null || player == null) {
            return false;
        }
        if (!isInArena(player)) {
            returnPoints.put(player.getUniqueId(), player.getLocation().clone());
        }
        player.teleport(arenaSpawn(world));
        player.sendMessage("§d✦ §fTest Arena §8· §7Isolated sandbox. Not the live game.");
        player.sendMessage("§7Dev → Test Arena for bosses / clear / leave.");
        return true;
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        Location back = returnPoints.remove(player.getUniqueId());
        if (back == null || back.getWorld() == null) {
            World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            back = main == null ? player.getLocation() : main.getSpawnLocation();
        }
        if (isArenaWorld(back.getWorld())) {
            World main = Bukkit.getWorlds().get(0);
            back = main.getSpawnLocation();
        }
        player.teleport(back);
        player.sendMessage("§7Left the test arena.");
        return true;
    }

    public boolean isInArena(Player player) {
        return player != null && isArenaWorld(player.getWorld());
    }

    public boolean isArenaWorld(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    public void rebuildPlatform(Player player) {
        World world = ensureWorld();
        if (world == null) {
            if (player != null) {
                player.sendMessage("§cTest arena world missing.");
            }
            return;
        }
        platformReady = false;
        ensurePlatform(world);
        if (player != null) {
            player.sendMessage("§aTest arena platform rebuilt (§f" + (PLATFORM_RADIUS * 2 + 1) + "§ax§f"
                    + (PLATFORM_RADIUS * 2 + 1) + "§a).");
        }
    }

    public int clearBosses(Player player) {
        World world = ensureWorld();
        if (world == null) {
            return 0;
        }
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses != null) {
            bosses.despawnInWorld(world);
        }
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)
                    || entity.getPersistentDataContainer().has(AetherKeys.BOSS_MINION, PersistentDataType.STRING)) {
                entity.remove();
                removed++;
            }
        }
        if (player != null) {
            player.sendMessage("§eCleared bosses in the test arena.");
        }
        return removed;
    }

    public int clearMobs(Player player) {
        World world = ensureWorld();
        if (world == null) {
            return 0;
        }
        clearBosses(null);
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            var pdc = entity.getPersistentDataContainer();
            // Keep equipped pets (body + nameplate), set minions, boss leftovers handled above
            if (pdc.has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)
                    || pdc.has(AetherKeys.SET_MINION, PersistentDataType.BYTE)
                    || pdc.has(AetherKeys.SET_MINION_OWNER, PersistentDataType.STRING)
                    || pdc.has(AetherKeys.BOSS_ID, PersistentDataType.STRING)
                    || pdc.has(AetherKeys.BOSS_MINION, PersistentDataType.STRING)) {
                continue;
            }
            entity.remove();
            removed++;
        }
        if (player != null) {
            player.sendMessage("§eCleared §f" + removed + " §enon-player entities.");
        }
        return removed;
    }

    public boolean spawnBoss(Player player, String templateId) {
        World world = ensureWorld();
        if (world == null || player == null || templateId == null || templateId.isBlank()) {
            return false;
        }
        boolean needEnter = !isInArena(player);
        if (needEnter) {
            enter(player);
        }
        // Delay one tick so teleport/chunks are ready, then spawn at a fixed pad spot.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            World arena = ensureWorld();
            if (arena == null) {
                player.sendMessage("§cTest arena world missing.");
                return;
            }
            if (!isInArena(player)) {
                player.teleport(arenaSpawn(arena));
            }
            Location at = arenaSpawn(arena).clone().add(8.0, 0.0, 0.0);
            at.setYaw(90f);
            BossSpawnAccess bosses = AetherServices.bosses();
            if (bosses == null) {
                player.sendMessage("§cBossEngine is not loaded.");
                return;
            }
            boolean ok = bosses.spawnSandbox(templateId, at, player);
            if (ok) {
                player.sendMessage("§d✦ §fSpawned §e" + templateId + " §7in the test arena.");
            } else {
                player.sendMessage("§cCould not spawn §f" + templateId
                        + "§c. Check BossEngine log (unknown id / spawn cancelled).");
            }
        }, needEnter ? 3L : 1L);
        return true;
    }

    /** Simple vanilla monsters for ability testing — never bosses/pets. */
    public int spawnDummyMobs(Player player, int amount) {
        World world = ensureWorld();
        if (world == null) {
            return 0;
        }
        if (player != null && !isInArena(player)) {
            enter(player);
        }
        Location base = player != null && isInArena(player)
                ? player.getLocation()
                : arenaSpawn(world);
        org.bukkit.entity.EntityType[] types = {
                org.bukkit.entity.EntityType.ZOMBIE,
                org.bukkit.entity.EntityType.SKELETON,
                org.bukkit.entity.EntityType.HUSK,
                org.bukkit.entity.EntityType.SPIDER,
                org.bukkit.entity.EntityType.DROWNED
        };
        int spawned = 0;
        int want = Math.max(1, Math.min(20, amount));
        for (int i = 0; i < want; i++) {
            double angle = (Math.PI * 2 * i) / want;
            Location at = base.clone().add(Math.cos(angle) * 4.5, 0.1, Math.sin(angle) * 4.5);
            at.setY(PLATFORM_Y + 1.0);
            org.bukkit.entity.EntityType type = types[i % types.length];
            org.bukkit.entity.Entity entity = world.spawnEntity(at, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(false);
                living.getPersistentDataContainer().set(
                        AetherKeys.namespaced("aetherion", "test_dummy"),
                        PersistentDataType.BYTE,
                        (byte) 1
                );
            }
            spawned++;
        }
        if (player != null) {
            player.sendMessage("§aSpawned §f" + spawned + " §atest mobs. §7(/summon works here too)");
        }
        return spawned;
    }

    private void ensurePlatform(World world) {
        // Floor
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                boolean edge = Math.abs(x) == PLATFORM_RADIUS || Math.abs(z) == PLATFORM_RADIUS;
                world.getBlockAt(x, PLATFORM_Y, z).setType(edge ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE, false);
                // Clear air column for fights
                for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + 12; y++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
                if ((x % 8 == 0 && z % 8 == 0) && !edge) {
                    world.getBlockAt(x, PLATFORM_Y, z).setType(Material.SEA_LANTERN, false);
                }
            }
        }
        // Center marker
        world.getBlockAt(0, PLATFORM_Y, 0).setType(Material.LODESTONE, false);
        world.setSpawnLocation(arenaSpawn(world));
        platformReady = true;
    }

    private void applyRules(World world) {
        world.setKeepSpawnInMemory(true);
        world.setAutoSave(true);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setDifficulty(Difficulty.HARD);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        try {
            world.getClass().getMethod("setViewDistance", int.class).invoke(world, 6);
        } catch (Throwable ignored) {
        }
        try {
            world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, 6);
        } catch (Throwable ignored) {
        }
    }
}
