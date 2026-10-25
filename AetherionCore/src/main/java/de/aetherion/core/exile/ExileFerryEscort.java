package de.aetherion.core.exile;

import de.aetherion.core.npc.FancyNpcFacade;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ferryman walking one passenger to the ferry. A personal copy of the quay's Ferryman (FancyNpcs, never
 * registered or saved, spawned for that one player) leaves his spot and leads the way; the quay's own Ferryman and
 * his name marker are hidden for that player meanwhile, so there is only ever one of him. Everyone else keeps
 * seeing him at his post. Any step FancyNpcs does not offer: {@link #start} returns null and the ferry casts off
 * the old way.
 */
final class ExileFerryEscort {

    /** The quay Ferryman's FancyNpcs name (Quests' living cast prefix + id). */
    static final String QUAY_FERRYMAN = "ae_living_ferryman";

    private final Plugin plugin;
    private final Player player;
    private final Object npc;
    private final Object home;
    private final List<Entity> markers = new ArrayList<>();
    private Location at;
    private boolean ended;

    private ExileFerryEscort(Plugin plugin, Player player, Object npc, Object home, Location at) {
        this.plugin = plugin;
        this.player = player;
        this.npc = npc;
        this.home = home;
        this.at = at.clone();
    }

    /** Null when FancyNpcs is missing, the quay Ferryman is not there, or a per-player call is not available. */
    static ExileFerryEscort start(Plugin plugin, Player player, Location spot) {
        if (player == null || spot == null || !FancyNpcFacade.isAvailable()) {
            return null;
        }
        Object made = null;
        try {
            Object manager = FancyNpcFacade.manager();
            Object home = FancyNpcFacade.getNpc(manager, QUAY_FERRYMAN);
            if (home == null) {
                return null;
            }
            Location from = FancyNpcFacade.locationOf(home);
            if (from == null || from.getWorld() == null || !from.getWorld().equals(player.getWorld())
                    || from.distanceSquared(spot) > 12 * 12) {
                from = spot.clone();
            }
            Object homeData = FancyNpcFacade.data(home);
            String name = "ae_ferry_escort_" + player.getUniqueId().toString().substring(0, 8);
            Object data = FancyNpcFacade.createNpcData(name, new UUID(0L, 0L), from.clone());
            FancyNpcFacade.invokeQuiet(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setSpawnEntity", boolean.class, true);
            copySkin(homeData, data);
            copyGear(homeData, data);
            made = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(made, false);
            FancyNpcFacade.create(made);
            // per-player calls: show the copy to this player only, hide the real one for them
            made.getClass().getMethod("spawn", Player.class).invoke(made, player);
            Map<UUID, Boolean> seen = visibility(home);
            home.getClass().getMethod("remove", Player.class).invoke(home, player);
            // marked as shown so FancyNpcs' tracker does not bring him straight back for this player
            seen.put(player.getUniqueId(), true);
            ExileFerryEscort escort = new ExileFerryEscort(plugin, player, made, home, from);
            escort.hideMarkers(from);
            return escort;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (made != null) {
                removeFor(made, player);
            }
            plugin.getLogger().info("Ferry escort unavailable (" + ex + "); the ferry casts off without the walk.");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Boolean> visibility(Object npc) throws ReflectiveOperationException {
        Object map = npc.getClass().getMethod("getIsVisibleForPlayer").invoke(npc);
        if (!(map instanceof Map<?, ?>)) {
            throw new NoSuchMethodException("getIsVisibleForPlayer");
        }
        return (Map<UUID, Boolean>) map;
    }

    private static void copySkin(Object from, Object to) {
        for (String[] pair : new String[][] {{"getSkinData", "setSkinData"}, {"getSkin", "setSkin"}}) {
            try {
                Method get = from.getClass().getMethod(pair[0]);
                Object skin = get.invoke(from);
                if (skin == null) {
                    continue;
                }
                to.getClass().getMethod(pair[1], get.getReturnType()).invoke(to, skin);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private static void copyGear(Object from, Object to) {
        try {
            Object gear = from.getClass().getMethod("getEquipment").invoke(from);
            if (!(gear instanceof Map<?, ?> map)) {
                return;
            }
            Method add = to.getClass().getMethod("addEquipment", FancyNpcFacade.equipmentSlotClass(), ItemStack.class);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof ItemStack item) {
                    add.invoke(to, e.getKey(), item.clone());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** Name plates and click boxes floating over his spot belong to the real one: hidden with him. */
    private void hideMarkers(Location spot) {
        for (Entity e : spot.getWorld().getNearbyEntities(spot.clone().add(0, 1.6, 0), 1.6, 2.2, 1.6)) {
            if (e instanceof Display || e instanceof ArmorStand || e instanceof Interaction) {
                player.hideEntity(plugin, e);
                markers.add(e);
            }
        }
    }

    Location at() {
        return at.clone();
    }

    /** Puts him at {@code to} for this player (the client glides there and animates the step). */
    void moveTo(Location to) {
        if (ended || to == null) {
            return;
        }
        at = to.clone();
        try {
            FancyNpcFacade.setLocation(FancyNpcFacade.data(npc), at);
            try {
                npc.getClass().getMethod("move", Player.class, boolean.class).invoke(npc, player, false);
            } catch (NoSuchMethodException old) {
                npc.getClass().getMethod("move", Player.class).invoke(npc, player);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // a missed step; the next tick moves him again
        }
    }

    /** The copy goes, the quay's Ferryman (and his marker) comes back for this player. */
    void end() {
        if (ended) {
            return;
        }
        ended = true;
        removeFor(npc, player);
        try {
            visibility(home).put(player.getUniqueId(), false);
            if (player.isOnline() && player.getWorld().equals(FancyNpcFacade.locationOf(home).getWorld())) {
                home.getClass().getMethod("spawn", Player.class).invoke(home, player);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // FancyNpcs' tracker shows him again on its next pass
        }
        if (player.isOnline()) {
            for (Entity e : markers) {
                if (e.isValid()) {
                    player.showEntity(plugin, e);
                }
            }
        }
        markers.clear();
    }

    private static void removeFor(Object npc, Player player) {
        try {
            npc.getClass().getMethod("remove", Player.class).invoke(npc, player);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
