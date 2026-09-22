package de.aetherion.items.world;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One persistent marker entity per owner id (area label, mob-zone stand, hologram).
 *
 * <p>{@code Bukkit.getEntity} returns null while that entity's chunk is unloaded.
 * Treating null as "missing" and spawning a replacement force-loads the chunk and
 * stacks another persistent copy on the saved one. Capital stays flat because the
 * spawn chunks never unload, so the lookup always hits. Mine, Forage Isle, and
 * Eldervale unload the anchor chunk while players are still inside the wider area,
 * so the old "respawn if missing" timers piled nametags for hours.
 *
 * <p>This helper never spawns and never touches entities that lack {@code key}.
 * Callers may spawn only when {@link State#MISSING} (chunk is loaded and no tagged
 * marker is already there).
 */
public final class MarkerLifecycle {

    public enum State {
        /** Anchor chunk is not loaded. Leave the saved marker alone. */
        UNLOADED,
        /** A tagged marker is live. Extras with the same owner id were removed. */
        KEPT,
        /** Chunk is loaded and nothing with this owner id exists. Safe to spawn one. */
        MISSING
    }

    public record Result(State state, Entity entity) {
    }

    private MarkerLifecycle() {
    }

    public static Result resolve(Location anchor, NamespacedKey key, String ownerId, UUID prefer, double radius) {
        if (anchor == null || anchor.getWorld() == null || key == null || ownerId == null || ownerId.isBlank()) {
            return new Result(State.UNLOADED, null);
        }
        if (!anchor.getChunk().isLoaded()) {
            return new Result(State.UNLOADED, null);
        }
        World world = anchor.getWorld();
        List<Entity> matches = new ArrayList<>();
        collect(anchor.getChunk().getEntities(), key, ownerId, matches);
        double reach = Math.max(1.0, radius);
        for (Entity entity : world.getNearbyEntities(anchor, reach, reach, reach)) {
            if (owned(entity, key, ownerId) && !matches.contains(entity)) {
                matches.add(entity);
            }
        }
        if (matches.isEmpty()) {
            return new Result(State.MISSING, null);
        }
        Entity keep = matches.get(0);
        if (prefer != null) {
            for (Entity entity : matches) {
                if (prefer.equals(entity.getUniqueId())) {
                    keep = entity;
                    break;
                }
            }
        }
        for (Entity entity : matches) {
            if (entity != keep && entity.isValid()) {
                entity.remove();
            }
        }
        return new Result(State.KEPT, keep);
    }

    private static void collect(Entity[] entities, NamespacedKey key, String ownerId, List<Entity> into) {
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            if (owned(entity, key, ownerId)) {
                into.add(entity);
            }
        }
    }

    private static boolean owned(Entity entity, NamespacedKey key, String ownerId) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return ownerId.equals(id);
    }
}
