package de.aetherion.items.recipe;

import de.aetherion.items.economy.Signatures;

import org.bukkit.inventory.ItemStack;

/**
 * P2: lets a Signature slot accept a find from before the P1 id stamp (it has the gather plugin's
 * keys but no Items id yet). Stamped finds match the normal way, by Items id.
 */
public final class RecipeSignatures {

    private RecipeSignatures() {
    }

    /** True when {@code actual} is an un-stamped find of the Signature family {@code requiredId}. */
    public static boolean legacyMatch(ItemStack actual, String requiredId) {
        if (actual == null || requiredId == null || !Signatures.isStampable(requiredId)) {
            return false;
        }
        return requiredId.equalsIgnoreCase(Signatures.idOf(actual));
    }
}
