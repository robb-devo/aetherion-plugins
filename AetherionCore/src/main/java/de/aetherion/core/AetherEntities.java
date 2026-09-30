package de.aetherion.core;

import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Identity checks for tagged Aetherion entities. Read-only. No spawning, no ticks.
 *
 * <p>{@link #isSystemOwned} covers bosses, pets, minions, dungeon/quest NPCs, fishing
 * encounters — intentional entities that cleanup sweeps must leave alone. Temporary
 * runtime entities (ambient wildlife, HP labels) are tagged in AetherionItems instead.
 */
public final class AetherEntities {

    private AetherEntities() {
    }

    public static Entity root(Entity entity) {
        if (entity instanceof EnderDragonPart part) {
            Entity parent = part.getParent();
            return parent != null ? parent : entity;
        }
        return entity;
    }

    public static boolean isBoss(Entity entity) {
        return hasString(root(entity), AetherKeys.BOSS_ID);
    }

    public static boolean isBossMinion(Entity entity) {
        return hasString(root(entity), AetherKeys.BOSS_MINION);
    }

    public static boolean isPet(Entity entity) {
        return hasByte(root(entity), AetherKeys.PET_ENTITY);
    }

    public static boolean isSetMinion(Entity entity) {
        return hasByte(root(entity), AetherKeys.SET_MINION);
    }

    public static boolean isDungeonMob(Entity entity) {
        return hasString(root(entity), AetherKeys.DUNGEON_MOB);
    }

    public static boolean isQuestNpc(Entity entity) {
        return hasString(root(entity), AetherKeys.QUEST_NPC);
    }

    public static boolean isDungeonNpc(Entity entity) {
        return hasString(root(entity), AetherKeys.DUNGEON_NPC);
    }

    public static boolean isFishingEncounter(Entity entity) {
        return hasByte(root(entity), AetherKeys.FISHING_ENCOUNTER);
    }

    /**
     * Boss, pet, set minion, dungeon mob, fishing encounter, or tagged NPC. Not traders — those stay in Items.
     */
    public static boolean isSystemOwned(Entity entity) {
        if (entity == null) {
            return false;
        }
        Entity root = root(entity);
        return isBoss(root)
                || isBossMinion(root)
                || isPet(root)
                || isSetMinion(root)
                || isDungeonMob(root)
                || isQuestNpc(root)
                || isDungeonNpc(root)
                || isFishingEncounter(root);
    }

    private static boolean hasString(Entity entity, org.bukkit.NamespacedKey key) {
        return entity != null && data(entity).has(key, PersistentDataType.STRING);
    }

    private static boolean hasByte(Entity entity, org.bukkit.NamespacedKey key) {
        return entity != null && data(entity).has(key, PersistentDataType.BYTE);
    }

    private static PersistentDataContainer data(Entity entity) {
        return entity.getPersistentDataContainer();
    }
}
