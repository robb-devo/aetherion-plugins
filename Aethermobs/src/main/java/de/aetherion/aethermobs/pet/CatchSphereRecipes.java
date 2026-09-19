package de.aetherion.aethermobs.pet;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.recipe.RecipeCategory;
import de.aetherion.items.recipe.RecipeDefinition;
import de.aetherion.items.recipe.UnlockRequirement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatchSphereRecipes {

    private final BetaSphereManager sphereManager;

    public CatchSphereRecipes(BetaSphereManager sphereManager) {
        this.sphereManager = sphereManager;
    }

    public void register() {
        AetherionItems items = AetherionItems.getInstance();

        if (items == null) {
            return;
        }

        items.registerRecipe(commonRecipe());
        items.registerRecipe(rareRecipe());
        items.registerRecipe(epicRecipe());
        // Legendary + pet EXP treats live in Root Cellar pantry GUI only.

        if (items.getRecipeService() == null) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            items.getRecipeService().discoverFor(player);
        }
    }

    private RecipeDefinition commonRecipe() {
        Map<Character, ItemStack> ingredients = new LinkedHashMap<>();
        ingredients.put('W', new ItemStack(Material.OAK_PLANKS));
        ingredients.put('C', new ItemStack(Material.COAL));

        return new RecipeDefinition(
                "catch_sphere_common",
                RecipeCategory.AETHER_MOBS,
                sphereManager.createCatchSphere("common"),
                Rarity.COMMON,
                List.of("CWC", "W W", "CWC"),
                ingredients,
                UnlockRequirement.ALWAYS_UNLOCKED
        );
    }

    private RecipeDefinition rareRecipe() {
        Map<Character, ItemStack> ingredients = new LinkedHashMap<>();
        ingredients.put('W', new ItemStack(Material.OAK_PLANKS));
        ingredients.put('G', new ItemStack(Material.GOLD_INGOT));

        return new RecipeDefinition(
                "catch_sphere_rare",
                RecipeCategory.AETHER_MOBS,
                sphereManager.createCatchSphere("rare"),
                Rarity.RARE,
                List.of("GWG", "W W", "GWG"),
                ingredients,
                UnlockRequirement.ALWAYS_UNLOCKED
        );
    }

    private RecipeDefinition epicRecipe() {
        Map<Character, ItemStack> ingredients = new LinkedHashMap<>();
        ingredients.put('W', new ItemStack(Material.OAK_PLANKS));
        ingredients.put('D', new ItemStack(Material.DIAMOND));

        return new RecipeDefinition(
                "catch_sphere_epic",
                RecipeCategory.AETHER_MOBS,
                sphereManager.createCatchSphere("epic"),
                Rarity.EPIC,
                List.of("DWD", "W W", "DWD"),
                ingredients,
                UnlockRequirement.ALWAYS_UNLOCKED
        );
    }
}
