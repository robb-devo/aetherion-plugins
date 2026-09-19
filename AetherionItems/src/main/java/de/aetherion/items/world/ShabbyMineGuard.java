package de.aetherion.items.world;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
 * Shabby Mine: no hostiles / monsters.
 * Cave pets (ItemDisplay + pet PDC, CUSTOM pet spawns) stay allowed.
 */
public final class ShabbyMineGuard implements Listener, Runnable {

    public ShabbyMineGuard(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 80L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location at = event.getLocation();
        if (!inShabbyMine(at)) {
            return;
        }

        Entity entity = event.getEntity();
        if (isPet(entity) || isOreTroll(entity)) {
            return;
        }

        // MobZone / natural / reinforcements — anything hostile is out.
        if (isHostile(entity)) {
            event.setCancelled(true);
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            // CUSTOM used by MobZoneService — only allow if not hostile (already checked).
            // Still block generic living wildlife refill that isn't a pet.
            if (entity instanceof LivingEntity living
                    && !(living instanceof Player)
                    && isWildlifeMob(living)) {
                event.setCancelled(true);
            }
            return;
        }

        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.DEFAULT
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                || reason == CreatureSpawnEvent.SpawnReason.PATROL
                || reason == CreatureSpawnEvent.SpawnReason.RAID
                || reason == CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER
                || reason == CreatureSpawnEvent.SpawnReason.TRAP
                || reason == CreatureSpawnEvent.SpawnReason.JOCKEY
                || reason == CreatureSpawnEvent.SpawnReason.MOUNT) {
            event.setCancelled(true);
        }
    }

    @Override
    public void run() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getAreas() == null) {
            return;
        }
        for (AreaZone zone : plugin.getAreas().zonesOf(AreaType.SHABBY_MINE)) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            Location center = zone.center(world);
            if (!center.getChunk().isLoaded()) {
                continue;
            }
            double r = zone.getRadius() + 2;
            for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
                if (!zone.contains(entity.getLocation())) {
                    continue;
                }
                if (isPet(entity) || isOreTroll(entity)) {
                    continue;
                }
                if (isHostile(entity)) {
                    entity.remove();
                }
            }
        }
    }

    static boolean inShabbyMine(Location location) {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin != null
                && plugin.getAreas() != null
                && plugin.getAreas().isType(location, AreaType.SHABBY_MINE);
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

    private static boolean isOreTroll(Entity entity) {
        try {
            return de.aetherion.items.mining.OreTrollListener.isOreTroll(entity);
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

    /** Mob-zone wildlife that is not a pet — block even on CUSTOM. */
    private static boolean isWildlifeMob(LivingEntity living) {
        return isHostile(living)
                || switch (living.getType()) {
            case ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, HUSK, STRAY, DROWNED -> true;
            default -> false;
        };
    }
}
