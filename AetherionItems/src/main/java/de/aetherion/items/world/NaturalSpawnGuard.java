package de.aetherion.items.world;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * World overworld: no vanilla/hostile natural spawns (MobZone / borderlands use CUSTOM).
 * Also culls lingering vanilla hostiles outside borderlands.
 * Borderlands: no animals — combat waste only. CUSTOM MobZone borderlands mobs stay.
 */
public final class NaturalSpawnGuard implements Listener, Runnable {

    public NaturalSpawnGuard(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location at = event.getLocation();
        World world = at.getWorld();
        if (world == null || !applies(world)) {
            return;
        }

        Entity entity = event.getEntity();
        if (isPet(entity)) {
            return;
        }

        // Vanilla cave bats only — AetherMobs pet bats keep PET_ENTITY and are skipped above.
        if (entity.getType() == org.bukkit.entity.EntityType.BAT) {
            if (!allowedExplicit(event.getSpawnReason())) {
                event.setCancelled(true);
            }
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean allowedExplicit = allowedExplicit(reason);

        if (isHostile(entity)) {
            // MobZoneService / BossEngine / rites spawn via CUSTOM — keep those.
            if (allowedExplicit) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        if (entity instanceof Animals && (inBorderlands(at) || inEldervale(at))) {
            // Bosses can be Animals (e.g. McNugget = CHICKEN) and spawn via CUSTOM.
            if (allowedExplicit || isProtectedEntity(entity)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    private static boolean allowedExplicit(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG;
    }

    @Override
    public void run() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        AreaService areas = plugin.getAreas();
        MobZoneService mobs = plugin.getMobZones();
        cullBorderlandsAnimals(areas, mobs);
        cullHostilesOutsideBorderlands(areas, mobs);
        cullVanillaBats();
    }

    /** Remove lingering vanilla bats near players (pets are tagged and skipped). */
    private void cullVanillaBats() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (!applies(world)) {
                continue;
            }
            for (Entity entity : world.getNearbyEntities(player.getLocation(), 96, 64, 96)) {
                if (entity.getType() != org.bukkit.entity.EntityType.BAT || !entity.isValid()) {
                    continue;
                }
                if (isPet(entity) || isProtectedEntity(entity)) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    /**
     * Remove vanilla/lingering hostiles outside borderlands / Eldervale MobZones.
     * Keeps BossEngine / pets / dungeon / NPCs / zone-tagged MobZone hostiles.
     */
    private void cullHostilesOutsideBorderlands(AreaService areas, MobZoneService mobs) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (!applies(world)) {
                continue;
            }
            Location at = player.getLocation();
            for (Entity entity : world.getNearbyEntities(at, 96, 64, 96)) {
                if (!(entity instanceof LivingEntity living)
                        || !living.isValid()
                        || living instanceof Player
                        || !isHostile(living)
                        || isPet(living)) {
                    continue;
                }
                if (inBorderlands(living.getLocation(), areas, mobs)) {
                    continue;
                }
                if (inEldervale(living.getLocation(), areas, mobs)) {
                    continue;
                }
                if (isProtectedHostile(living)) {
                    continue;
                }
                living.remove();
            }
        }
    }

    private void cullBorderlandsAnimals(AreaService areas, MobZoneService mobs) {
        if (areas == null && mobs == null) {
            return;
        }
        // Near online players only — spawn cancel already blocks new animals in borderlands.
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (!applies(world)) {
                continue;
            }
            Location at = player.getLocation();
            boolean borderlands = inBorderlands(at, areas, mobs);
            boolean eldervale = inEldervale(at, areas, mobs);
            if (!borderlands && !eldervale) {
                continue;
            }
            for (Entity entity : world.getNearbyEntities(at, 96, 48, 96)) {
                if (!(entity instanceof Animals living) || !living.isValid()) {
                    continue;
                }
                if (living instanceof Player || isPet(living) || isProtectedEntity(living)) {
                    continue;
                }
                Location loc = living.getLocation();
                if (inBorderlands(loc, areas, mobs) || inEldervale(loc, areas, mobs)) {
                    living.remove();
                }
            }
        }
    }

    private static boolean isProtectedHostile(LivingEntity living) {
        return isProtectedEntity(living);
    }

    private static boolean isProtectedEntity(Entity entity) {
        try {
            if (de.aetherion.core.AetherEntities.isSystemOwned(entity)
                    || de.aetherion.core.AetherEntities.isBoss(entity)) {
                return true;
            }
        } catch (NoClassDefFoundError | Exception ignored) {
        }
        try {
            if (entity.getPersistentDataContainer().has(
                    de.aetherion.core.AetherKeys.FISHING_ENCOUNTER,
                    PersistentDataType.BYTE
            )) {
                return true;
            }
        } catch (NoClassDefFoundError | Exception ignored) {
        }
        try {
            if (entity.getPersistentDataContainer().has(
                    de.aetherion.items.core.ItemKeys.zoneSpawn(),
                    PersistentDataType.STRING
            )) {
                return true;
            }
        } catch (NoClassDefFoundError | Exception ignored) {
        }
        try {
            return de.aetherion.items.mining.OreTrollListener.isOreTroll(entity);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
        }
    }

    private static boolean inBorderlands(Location location) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return false;
        }
        return inBorderlands(location, plugin.getAreas(), plugin.getMobZones());
    }

    private static boolean inEldervale(Location location) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return false;
        }
        return inEldervale(location, plugin.getAreas(), plugin.getMobZones());
    }

    private static boolean inBorderlands(Location location, AreaService areas, MobZoneService mobs) {
        if (location == null) {
            return false;
        }
        if (mobs != null && mobs.containsBorderlands(location)) {
            return true;
        }
        return areas != null && areas.isType(location, AreaType.BORDERLANDS);
    }

    private static boolean inEldervale(Location location, AreaService areas, MobZoneService mobs) {
        if (location == null) {
            return false;
        }
        if (mobs != null && mobs.containsEldervale(location)) {
            return true;
        }
        return areas != null && areas.isType(location, AreaType.ELDERVALE);
    }

    static boolean applies(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        String name = world.getName().toLowerCase();
        if (name.equals("aether_test") || name.startsWith("aether_test_")) {
            return false;
        }
        if (name.equals("aether_farm_island") || name.startsWith("aether_farm_")) {
            return false;
        }
        return !name.startsWith("aedun_") && !name.startsWith("ae_dun");
    }

    private static boolean isPet(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return entity.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
        }
    }

    private static boolean isHostile(Entity entity) {
        if (entity instanceof Monster) {
            return true;
        }
        try {
            if (entity instanceof Enemy) {
                return true;
            }
        } catch (NoClassDefFoundError ignored) {
        }
        return switch (entity.getType()) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY,
                 SPIDER, CAVE_SPIDER, CREEPER, ENDERMAN, WITCH, SLIME,
                 PHANTOM, SILVERFISH, VEX, PILLAGER, VINDICATOR, EVOKER,
                 RAVAGER, WARDEN, GUARDIAN, ELDER_GUARDIAN, BLAZE,
                 WITHER_SKELETON, GHAST, MAGMA_CUBE, HOGLIN, ZOGLIN,
                 PIGLIN_BRUTE, SHULKER -> true;
            default -> false;
        };
    }
}
