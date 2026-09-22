package de.aetherion.items.recipe;

import de.aetherion.items.economy.CompressedResource;

import java.util.Locale;

/**
 * Picks the gear ingredient when a craft mixes a tool with compressed mats.
 * Ingredient maps list the surround material first, and that material is itself
 * an Aetherion item. Copying sockets off the mat drops the gear's sockets.
 */
public final class CraftSource {

    private CraftSource() {
    }

    public static boolean isResourceId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        if (CompressedResource.byItemId(itemId) != null) {
            return true;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return id.startsWith("quarry_");
    }

    /**
     * First non-resource id, or the first id when every ingredient is a resource.
     */
    public static String preferGearId(Iterable<String> ingredientIds) {
        String fallback = null;
        if (ingredientIds == null) {
            return null;
        }
        for (String id : ingredientIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (fallback == null) {
                fallback = id;
            }
            if (!isResourceId(id)) {
                return id;
            }
        }
        return fallback;
    }
}
