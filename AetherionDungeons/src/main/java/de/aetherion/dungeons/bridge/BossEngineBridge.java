package de.aetherion.dungeons.bridge;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BossSpawnAccess;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BossEngineBridge {

    public static final String SENTINEL = "dungeon_sentinel";
    public static final String FROSTBOUND = "dungeon_frostbound";
    public static final String AETHERION = "dungeon_aetherion";
    public static final String TEMPLATE = SENTINEL;
    private static final Set<String> DUNGEON_BOSSES = Set.of(SENTINEL, FROSTBOUND, AETHERION);
    /** Floor 3 mid-clear elites. Never floor bosses: killing one must not end the floor. */
    public static final String CHAINWARDEN = "ashen_chainwarden";
    public static final String HERALD = "cinder_herald";
    private static final Set<String> ASHES_ELITES = Set.of(CHAINWARDEN, HERALD);

    private BossEngineBridge() {
    }

    public static boolean spawn(Location location, Player initiator) {
        return spawn(location, initiator, SENTINEL);
    }

    public static boolean spawn(Location location, Player initiator, String templateId) {
        return spawn(location, initiator, templateId, false);
    }

    /**
     * @param compactHearths Endless XL only — tight hearth square. Floor 2 must pass false.
     */
    public static boolean spawn(Location location, Player initiator, String templateId, boolean compactHearths) {
        if (location == null) {
            return false;
        }
        String id = templateId == null || templateId.isBlank() ? SENTINEL : templateId;
        BossSpawnAccess access = AetherServices.bosses();
        boolean ok;
        if (access != null) {
            ok = access.spawn(id, location, initiator);
            if (ok && compactHearths) {
                // Entity is tagged after spawn via API path below when reflection used;
                // typed access tags separately when we have the entity.
                tagCompactHearthsNear(location, access);
            }
        } else {
            ok = spawnReflect(location, initiator, id, compactHearths);
        }
        if (!ok) {
            explainRefusal(id, location);
        }
        return ok;
    }

    /**
     * BossEngine refuses a spawn without saying why (max-instances counts live encounters and every loaded body
     * tagged with the template). Log what holds the slot, so a fallback mob never goes unexplained again.
     */
    private static void explainRefusal(String id, Location location) {
        StringBuilder bodies = new StringBuilder();
        int tagged = 0;
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
                String tid = templateId(entity);
                if (tid == null || !tid.equalsIgnoreCase(id)) {
                    continue;
                }
                tagged++;
                if (tagged <= 6) {
                    bodies.append(' ').append(world.getName()).append(':').append(entity.getType())
                            .append('@').append(entity.getLocation().getBlockX()).append(',')
                            .append(entity.getLocation().getBlockY()).append(',').append(entity.getLocation().getBlockZ())
                            .append(entity.isValid() ? "" : "(invalid)").append(entity.isDead() ? "(dead)" : "");
                }
            }
        }
        StringBuilder slots = new StringBuilder();
        Plugin engine = Bukkit.getPluginManager().getPlugin("BossEngine");
        try {
            if (engine != null) {
                Object manager = engine.getClass().getMethod("getBossManager").invoke(engine);
                Object list = manager.getClass().getMethod("getByTemplate", String.class).invoke(manager, id);
                if (list instanceof java.util.Collection<?> encounters) {
                    slots.append(encounters.size()).append(" encounter(s) holding the slot");
                    for (Object encounter : encounters) {
                        Object body = encounter.getClass().getMethod("getEntity").invoke(encounter);
                        Object spawn = encounter.getClass().getMethod("getSpawnLocation").invoke(encounter);
                        slots.append(" [body=").append(body instanceof Entity e
                                        ? e.getWorld().getName() + ":" + e.getType() + (e.isValid() ? "" : "(gone)")
                                        : "none")
                                .append(" spawn=").append(spawn instanceof Location l && l.getWorld() != null
                                        ? l.getWorld().getName() + "@" + l.getBlockX() + "," + l.getBlockZ() : "?")
                                .append(']');
                    }
                }
            }
        } catch (Exception exception) {
            slots.append("encounters unknown (").append(exception.getClass().getSimpleName()).append(')');
        }
        Plugin owner = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (owner != null) {
            owner.getLogger().warning("[Boss] BossEngine refused '" + id + "' at "
                    + (location.getWorld() == null ? "?" : location.getWorld().getName()) + " "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                    + ": " + tagged + " loaded bod" + (tagged == 1 ? "y" : "ies") + bodies + "; " + slots);
        }
    }

    private static void tagCompactHearthsNear(Location location, BossSpawnAccess access) {
        if (location.getWorld() == null) {
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, 3, 3, 3)) {
            String tid = templateId(entity);
            if (tid != null && DUNGEON_BOSSES.contains(tid.toLowerCase())) {
                access.tagCompactHearths(entity);
                return;
            }
        }
    }

    private static boolean spawnReflect(Location location, Player initiator, String id, boolean compactHearths) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (engine == null || !engine.isEnabled()) {
            return false;
        }
        try {
            Object api = engine.getClass().getMethod("getAPI").invoke(null);
            if (api == null) {
                return false;
            }
            Class<?> causeClass = Class.forName("de.aetherion.bossengine.api.SpawnCause");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object cause = Enum.valueOf((Class<Enum>) causeClass, "API");
            Method spawn = api.getClass().getMethod("spawn", String.class, Location.class, causeClass, Player.class);
            Object result = spawn.invoke(api, id, location, cause, initiator);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            if (compactHearths) {
                Object instance = optional.get();
                Object entity = instance.getClass().getMethod("getEntity").invoke(instance);
                if (entity instanceof Entity living) {
                    tagCompactHearths(living);
                }
            }
            return true;
        } catch (Exception exception) {
            engine.getLogger().warning("Dungeon boss spawn failed: " + exception.getMessage());
            return false;
        }
    }

    /** Endless XL test: keep Frostbound hearths in a clickable square. */
    public static void tagCompactHearths(Entity entity) {
        if (entity == null) {
            return;
        }
        BossSpawnAccess access = AetherServices.bosses();
        if (access != null) {
            access.tagCompactHearths(entity);
            return;
        }
        Plugin engine = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (engine == null || !engine.isEnabled()) {
            return;
        }
        try {
            Object keys = engine.getClass().getMethod("getKeys").invoke(engine);
            keys.getClass().getMethod("tagCompactHearths", Entity.class).invoke(keys, entity);
        } catch (Exception exception) {
            entity.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("bossengine", "compact_hearths"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        }
    }

    public static boolean isEngineBoss(Entity entity) {
        String id = templateId(entity);
        return id != null && DUNGEON_BOSSES.contains(id.toLowerCase());
    }

    public static boolean isAshesElite(Entity entity) {
        String id = templateId(entity);
        return id != null && ASHES_ELITES.contains(id.toLowerCase());
    }

    public static boolean isMythBoss(Entity entity) {
        return AETHERION.equalsIgnoreCase(templateId(entity));
    }

    public static String templateId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(
                AetherKeys.BOSS_ID,
                PersistentDataType.STRING
        );
    }

    public static String templateForFloor(int floor) {
        if (floor >= 3) {
            return AETHERION;
        }
        if (floor >= 2) {
            return FROSTBOUND;
        }
        return SENTINEL;
    }

    public static void despawnInWorld(World world) {
        if (world == null) {
            return;
        }
        BossSpawnAccess access = AetherServices.bosses();
        if (access != null) {
            access.despawnInWorld(world);
            return;
        }
        Plugin engine = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (engine == null || !engine.isEnabled()) {
            return;
        }
        try {
            Object api = engine.getClass().getMethod("getAPI").invoke(null);
            if (api == null) {
                return;
            }
            Object raw = api.getClass().getMethod("getActiveBosses").invoke(api);
            if (!(raw instanceof java.util.Collection<?> bosses)) {
                return;
            }
            for (Object instance : new java.util.ArrayList<>(bosses)) {
                if (instance == null) {
                    continue;
                }
                boolean here = false;
                Object entity = instance.getClass().getMethod("getEntity").invoke(instance);
                if (entity instanceof Entity living && world.equals(living.getWorld())) {
                    here = true;
                }
                if (!here) {
                    Object spawn = instance.getClass().getMethod("getSpawnLocation").invoke(instance);
                    if (spawn instanceof Location location && world.equals(location.getWorld())) {
                        here = true;
                    }
                }
                if (!here) {
                    continue;
                }
                Object id = instance.getClass().getMethod("getInstanceId").invoke(instance);
                if (id instanceof UUID uuid) {
                    api.getClass().getMethod("despawn", UUID.class).invoke(api, uuid);
                }
            }
        } catch (Exception exception) {
            engine.getLogger().warning("Dungeon boss despawn failed: " + exception.getMessage());
        }
    }
}
