package de.aetherion.core.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Thin FancyNpcs reflection glue. Plugin-specific NPC setup (skins, holograms,
 * villager fallbacks, display names) stays in the owning plugin.
 */
public final class FancyNpcFacade {

    public static final String PLUGIN_NAME = "FancyNpcs";
    public static final String API_CLASS = "de.oliver.fancynpcs.api.FancyNpcsPlugin";
    public static final String NPC_CLASS = "de.oliver.fancynpcs.api.Npc";
    public static final String NPC_DATA_CLASS = "de.oliver.fancynpcs.api.NpcData";
    public static final String INTERACT_EVENT_CLASS = "de.oliver.fancynpcs.api.events.NpcInteractEvent";
    public static final String EQUIPMENT_SLOT_CLASS = "de.oliver.fancynpcs.api.utils.NpcEquipmentSlot";

    private FancyNpcFacade() {
    }

    public static boolean isAvailable() {
        Plugin fancy = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return fancy != null && fancy.isEnabled();
    }

    public static Object plugin() throws ReflectiveOperationException {
        return Class.forName(API_CLASS).getMethod("get").invoke(null);
    }

    public static Object manager() throws ReflectiveOperationException {
        Object api = plugin();
        return api == null ? null : api.getClass().getMethod("getNpcManager").invoke(api);
    }

    public static Object manager(Object api) throws ReflectiveOperationException {
        return api == null ? null : api.getClass().getMethod("getNpcManager").invoke(api);
    }

    /**
     * {@code true} when FancyNpcs is missing (callers skip the wait) or when
     * {@code isLoaded} is absent / already true.
     */
    public static boolean isManagerLoaded(Object manager) {
        if (manager == null) {
            return true;
        }
        try {
            Object loaded = manager.getClass().getMethod("isLoaded").invoke(manager);
            return Boolean.TRUE.equals(loaded);
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    public static Class<?> npcClass() throws ClassNotFoundException {
        return Class.forName(NPC_CLASS);
    }

    public static Class<?> npcDataClass() throws ClassNotFoundException {
        return Class.forName(NPC_DATA_CLASS);
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Event> interactEventClass() throws ClassNotFoundException {
        return (Class<? extends Event>) Class.forName(INTERACT_EVENT_CLASS);
    }

    public static Object createNpcData(String name, UUID creator, Location location)
            throws ReflectiveOperationException {
        return npcDataClass()
                .getConstructor(String.class, UUID.class, Location.class)
                .newInstance(name, creator, location.clone());
    }

    public static Object adapt(Object data) throws ReflectiveOperationException {
        Object api = plugin();
        @SuppressWarnings("unchecked")
        Function<Object, Object> adapter =
                (Function<Object, Object>) api.getClass().getMethod("getNpcAdapter").invoke(api);
        return adapter.apply(data);
    }

    /**
     * String overloads only — {@code getNpc(int)} throws argument-type mismatch.
     */
    public static Object getNpc(Object manager, String name) {
        if (manager == null || name == null) {
            return null;
        }
        for (String method : List.of("getNpc", "getNpcById")) {
            try {
                Object npc = manager.getClass().getMethod(method, String.class).invoke(manager, name);
                if (npc != null) {
                    return npc;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Object all = manager.getClass().getMethod("getAllNpcs").invoke(manager);
            if (all instanceof Map<?, ?> map) {
                Object fromMap = map.get(name);
                if (fromMap != null) {
                    return fromMap;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static Object data(Object npc) throws ReflectiveOperationException {
        return npc == null ? null : npc.getClass().getMethod("getData").invoke(npc);
    }

    public static String nameOf(Object npc) throws ReflectiveOperationException {
        Object npcData = data(npc);
        if (npcData == null) {
            return null;
        }
        Object name = npcData.getClass().getMethod("getName").invoke(npcData);
        return name == null ? null : String.valueOf(name);
    }

    public static Location locationOf(Object npc) throws ReflectiveOperationException {
        Object npcData = data(npc);
        if (npcData == null) {
            return null;
        }
        Object loc = npcData.getClass().getMethod("getLocation").invoke(npcData);
        return loc instanceof Location location ? location.clone() : null;
    }

    public static void setLocation(Object npcData, Location location) throws ReflectiveOperationException {
        npcData.getClass().getMethod("setLocation", Location.class).invoke(npcData, location.clone());
    }

    public static void invoke(Object target, String method, Class<?> type, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(method, type).invoke(target, value);
    }

    public static boolean invokeQuiet(Object target, String method) {
        if (target == null) {
            return false;
        }
        try {
            target.getClass().getMethod(method).invoke(target);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean invokeQuiet(Object target, String method, Class<?> type, Object value) {
        if (target == null) {
            return false;
        }
        try {
            invoke(target, method, type, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void register(Object manager, Object npc) throws ReflectiveOperationException {
        manager.getClass().getMethod("registerNpc", npcClass()).invoke(manager, npc);
    }

    public static void unregister(Object manager, Object npc) throws ReflectiveOperationException {
        try {
            manager.getClass().getMethod("removeNpc", npcClass()).invoke(manager, npc);
        } catch (ReflectiveOperationException ex) {
            manager.getClass().getMethod("removeNpc", npc.getClass()).invoke(manager, npc);
        }
    }

    public static void spawnForAll(Object npc) throws ReflectiveOperationException {
        npc.getClass().getMethod("spawnForAll").invoke(npc);
    }

    public static void removeForAll(Object npc) throws ReflectiveOperationException {
        try {
            npc.getClass().getMethod("removeForAll").invoke(npc);
        } catch (NoSuchMethodException missing) {
            npc.getClass().getMethod("despawnForAll").invoke(npc);
        }
    }

    public static void removeFromPlayersQuiet(Object npc) {
        if (!invokeQuiet(npc, "removeForAll")) {
            invokeQuiet(npc, "despawnForAll");
        }
    }

    public static void setSaveToFile(Object npc, boolean save) throws ReflectiveOperationException {
        npc.getClass().getMethod("setSaveToFile", boolean.class).invoke(npc, save);
    }

    public static void create(Object npc) throws ReflectiveOperationException {
        npc.getClass().getMethod("create").invoke(npc);
    }

    public static void moveForAll(Object npc) {
        invokeQuiet(npc, "moveForAll");
    }

    /** Living NPCs: {@code moveForAll(false)} avoids arm-swing / bottle spam. */
    public static void moveForAll(Object npc, boolean swingArm) throws ReflectiveOperationException {
        try {
            npc.getClass().getMethod("moveForAll", boolean.class).invoke(npc, swingArm);
        } catch (NoSuchMethodException missing) {
            npc.getClass().getMethod("moveForAll").invoke(npc);
        }
    }

    public static void updateForAll(Object npc) {
        invokeQuiet(npc, "updateForAll");
    }

    public static void updateForAll(Object npc, boolean swingArm) {
        if (!invokeQuiet(npc, "updateForAll", boolean.class, swingArm)) {
            invokeQuiet(npc, "updateForAll");
        }
    }

    public static void saveNpcs(Object manager, boolean persist) {
        if (manager == null) {
            return;
        }
        try {
            manager.getClass().getMethod("saveNpcs", boolean.class).invoke(manager, persist);
        } catch (ReflectiveOperationException ignored) {
            invokeQuiet(manager, "saveNpcs");
        }
    }

    public static Collection<Object> allNpcs(Object manager) throws ReflectiveOperationException {
        List<Object> out = new ArrayList<>();
        if (manager == null) {
            return out;
        }
        Object all = manager.getClass().getMethod("getAllNpcs").invoke(manager);
        if (all instanceof Collection<?> npcs) {
            for (Object npc : npcs) {
                if (npc != null) {
                    out.add(npc);
                }
            }
        } else if (all instanceof Map<?, ?> map) {
            for (Object npc : map.values()) {
                if (npc != null) {
                    out.add(npc);
                }
            }
        }
        return out;
    }

    public static Class<?> equipmentSlotClass() throws ClassNotFoundException {
        return Class.forName(EQUIPMENT_SLOT_CLASS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object equipmentSlot(String name) throws ReflectiveOperationException {
        Class<?> slotClass = equipmentSlotClass();
        return Enum.valueOf((Class<? extends Enum>) slotClass.asSubclass(Enum.class), name);
    }

    public static Interact readInteract(Object event) throws ReflectiveOperationException {
        Object npc = event.getClass().getMethod("getNpc").invoke(event);
        Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
        return new Interact(player, npc, nameOf(npc));
    }

    public static void cancel(Object event) {
        invokeQuiet(event, "setCancelled", boolean.class, true);
    }

    public record Interact(Player player, Object npc, String name) {
    }

    /** Lookup helper used when FancyNpcs exposes both getNpc(String) and getNpcById. */
    public static Object tryGetNpc(Object manager, String name) throws ReflectiveOperationException {
        if (manager == null || name == null) {
            return null;
        }
        for (String method : List.of("getNpc", "getNpcById")) {
            try {
                Method m = manager.getClass().getMethod(method, String.class);
                Object npc = m.invoke(manager, name);
                if (npc != null) {
                    return npc;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
