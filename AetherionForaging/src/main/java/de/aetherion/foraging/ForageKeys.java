package de.aetherion.foraging;

import org.bukkit.NamespacedKey;

/** Persistent keys for forage DEV anchors. */
public final class ForageKeys {

    private ForageKeys() {
    }

    public static NamespacedKey groveTableAnchor() {
        return key("grove_table_anchor");
    }

    public static NamespacedKey isleGuideAnchor() {
        return key("isle_guide_anchor");
    }

    private static NamespacedKey key(String name) {
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin != null) {
            return new NamespacedKey(plugin, name);
        }
        return new NamespacedKey("aetherionforaging", name);
    }
}
