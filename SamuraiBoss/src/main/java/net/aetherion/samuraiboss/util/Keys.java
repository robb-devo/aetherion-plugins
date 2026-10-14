package net.aetherion.samuraiboss.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Persistent-Data-Schlüssel. Jede Entity, die das Plugin erschafft, bekommt eine Markierung,
 * damit wir sie jederzeit wiederfinden und aufräumen können ("Kein Display-Müll").
 */
public final class Keys {

    /** Wert: Kategorie ("model", "fx", "hitbox", "deco", "debris", "anchor"). */
    public static NamespacedKey OWNED;
    /** Wert: Arena-ID, zu der eine Entity gehört. */
    public static NamespacedKey ARENA;
    /** Für zukünftige Erweiterungen (Loot etc.) – markiert die Hitbox des Bosses. */
    public static NamespacedKey BOSS;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        OWNED = new NamespacedKey(plugin, "owned");
        ARENA = new NamespacedKey(plugin, "arena");
        BOSS = new NamespacedKey(plugin, "boss");
    }

    public static void mark(Entity e, String category, String arenaId) {
        e.getPersistentDataContainer().set(OWNED, PersistentDataType.STRING, category);
        if (arenaId != null) e.getPersistentDataContainer().set(ARENA, PersistentDataType.STRING, arenaId);
    }

    public static boolean isOwned(Entity e) {
        return OWNED != null && e.getPersistentDataContainer().has(OWNED, PersistentDataType.STRING);
    }

    public static String category(Entity e) {
        return e.getPersistentDataContainer().get(OWNED, PersistentDataType.STRING);
    }

    public static String arena(Entity e) {
        return e.getPersistentDataContainer().get(ARENA, PersistentDataType.STRING);
    }
}
