package de.aetherion.core;

import org.bukkit.NamespacedKey;

/**
 * Cross-plugin PDC keys. Strings stay identical to the old copies
 * so existing entities keep matching.
 *
 * Add a key here when two plugins need it. Plugin-private keys stay put.
 */
public final class AetherKeys {

    public static final NamespacedKey BOSS_ID = namespaced("bossengine", "boss_id");
    public static final NamespacedKey BOSS_MINION = namespaced("bossengine", "minion");
    public static final NamespacedKey PET_ENTITY = namespaced("aethermobs", "pet_entity");
    public static final NamespacedKey SET_MINION = namespaced("aetherion", "aetherion_minion");
    public static final NamespacedKey SET_MINION_OWNER = namespaced("aetherion", "aetherion_minion_owner");
    public static final NamespacedKey DUNGEON_MOB = namespaced("aetheriondungeons", "dungeon_mob");
    public static final NamespacedKey DUNGEON_NPC = namespaced("aetheriondungeons", "dungeon_npc");
    public static final NamespacedKey QUEST_NPC = namespaced("aetherionquests", "quest_npc");
    public static final NamespacedKey FISHING_ENCOUNTER = namespaced("aetherionfishing", "encounter");
    public static final NamespacedKey TRUE_DAMAGE = namespaced("aetherion", "true_damage");
    public static final NamespacedKey NO_SET_SAVE = namespaced("aetherion", "no_set_save");
    public static final NamespacedKey ITEM_ID = namespaced("aetherion", "item");
    public static final NamespacedKey CHARM_SUPPRESS = namespaced("aetherion", "charm_suppressed_until");

    private AetherKeys() {
    }

    public static NamespacedKey namespaced(String namespace, String key) {
        return new NamespacedKey(namespace, key);
    }
}
