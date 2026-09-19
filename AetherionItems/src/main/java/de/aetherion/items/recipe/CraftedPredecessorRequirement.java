package de.aetherion.items.recipe;

import de.aetherion.items.AetherionItems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CraftedPredecessorRequirement implements UnlockRequirement {

    private final String previousRecipeId;

    public CraftedPredecessorRequirement(String previousRecipeId) {
        this.previousRecipeId = previousRecipeId;
    }

    @Override
    public boolean isUnlocked(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.recipeUnlocks() == null) {
            return false;
        }
        RecipeUnlockService unlocks = plugin.recipeUnlocks();
        if (unlocks.hasCrafted(player, previousRecipeId)) {
            return true;
        }
        // Owning the previous tier (quest, NPC, loot) unlocks the next recipe.
        if (unlocks.ownsRecipeResult(player, previousRecipeId)) {
            unlocks.markCrafted(player, previousRecipeId);
            return true;
        }
        return false;
    }

    @Override
    public String getDisplayText() {
        String label = prettyResultName();
        return "Obtain or craft " + label + " first";
    }

    private String prettyResultName() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getRecipeManager() != null) {
            RecipeDefinition recipe = plugin.getRecipeManager().getRecipe(previousRecipeId);
            if (recipe != null) {
                ItemStack result = recipe.getResult();
                if (result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                    String name = result.getItemMeta().getDisplayName();
                    return name.replaceAll("§.", "");
                }
            }
        }
        return pretty(previousRecipeId);
    }

    private static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "previous tier";
        }
        return id.replace('_', ' ');
    }
}
