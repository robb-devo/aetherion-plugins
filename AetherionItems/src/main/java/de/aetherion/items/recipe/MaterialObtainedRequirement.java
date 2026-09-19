package de.aetherion.items.recipe;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;

import org.bukkit.entity.Player;

public final class MaterialObtainedRequirement implements UnlockRequirement {

    private final CompressedResource resource;

    public MaterialObtainedRequirement(CompressedResource resource) {
        this.resource = resource;
    }

    @Override
    public boolean isUnlocked(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.recipeUnlocks() == null || resource == null) {
            return false;
        }
        return plugin.recipeUnlocks().hasObtained(player, resource);
    }

    @Override
    public String getDisplayText() {
        if (resource == null) {
            return "Obtain the material first";
        }
        return "Obtain " + resource.key().replace('_', ' ') + " first";
    }
}
