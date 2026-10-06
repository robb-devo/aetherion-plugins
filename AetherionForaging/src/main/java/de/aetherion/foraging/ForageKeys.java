package de.aetherion.foraging;

import org.bukkit.NamespacedKey;

/** Persistent keys for forage DEV anchors and owned isle displays. */
public final class ForageKeys {

    private ForageKeys() {
    }

    public static NamespacedKey groveTableAnchor() {
        return key("grove_table_anchor");
    }

    public static NamespacedKey isleGuideAnchor() {
        return key("isle_guide_anchor");
    }

    /** Successful chops counted for newcomer ActionBar hints (cap 10). */
    public static NamespacedKey chopHintCount() {
        return key("chop_hint_count");
    }

    /** Grove cast nameplate (role id as string) — Farm IsleCast style. */
    public static NamespacedKey castPlate() {
        return key("grove_cast_plate");
    }

    /** Miss Canopy nameplate. */
    public static NamespacedKey canopyPlate() {
        return key("miss_canopy_plate");
    }

    /** Ephemeral FX (finds / critters / windfall / widowmaker). */
    public static NamespacedKey fx() {
        return key("forage_fx");
    }

    private static NamespacedKey key(String name) {
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin != null) {
            return new NamespacedKey(plugin, name);
        }
        return new NamespacedKey("aetherionforaging", name);
    }
}
