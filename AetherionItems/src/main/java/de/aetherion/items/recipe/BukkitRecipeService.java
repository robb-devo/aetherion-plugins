package de.aetherion.items.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registers RecipeDefinitions as Bukkit recipes.
 *
 * Custom Aetherion ingredients use MaterialChoice. The actual item-id check
 * happens in CraftingListener so boosted items still craft.
 */
public class BukkitRecipeService {

    private final JavaPlugin plugin;
    private final RecipeManager recipeManager;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public BukkitRecipeService(
            JavaPlugin plugin,
            RecipeManager recipeManager
    ) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    public void registerAll() {
        unregisterAll();
        removeVanillaToolAndArmorRecipes();

        for (RecipeDefinition recipe : recipeManager.getAllRecipes()) {
            register(recipe);
        }
    }

    /**
     * Aetherion owns tool/armor progression. Vanilla tools and armor crafts are disabled;
     * sticks, chests, tables, and other utility recipes stay.
     */
    private void removeVanillaToolAndArmorRecipes() {
        List<NamespacedKey> toRemove = new ArrayList<>();
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof Keyed keyed)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (result == null || !isVanillaToolOrArmor(result.getType())) {
                continue;
            }
            toRemove.add(keyed.getKey());
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("Disabled " + toRemove.size() + " vanilla tool/armor recipes.");
        }
    }

    private static boolean isVanillaToolOrArmor(org.bukkit.Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SWORD")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    public void unregisterAll() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }

        recipeKeys.clear();
    }

    public boolean isAetherionRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }

        return recipeKeys.contains(keyed.getKey());
    }

    public void discoverFor(Player player) {
        if (player == null || recipeKeys.isEmpty()) {
            return;
        }

        List<NamespacedKey> unlocked = new ArrayList<>();
        for (NamespacedKey key : recipeKeys) {
            RecipeDefinition definition = recipeManager.getRecipe(key.getKey());
            if (definition == null || definition.isUnlocked(player)) {
                unlocked.add(key);
            }
        }
        if (!unlocked.isEmpty()) {
            player.discoverRecipes(unlocked);
        }
    }

    public List<NamespacedKey> getRecipeKeys() {
        return Collections.unmodifiableList(recipeKeys);
    }

    public void register(RecipeDefinition definition) {
        if (definition == null || definition.hasStackedIngredients()) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, definition.getId());
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, definition.getResult());
        List<String> normalized = normalizeShape(definition.getShape());

        recipe.shape(normalized.toArray(new String[0]));

        for (Map.Entry<Character, ItemStack> entry : definition.getIngredients().entrySet()) {
            ItemStack ingredient = entry.getValue();

            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }

            recipe.setIngredient(
                    entry.getKey(),
                    new RecipeChoice.MaterialChoice(ingredient.getType())
            );
        }

        if (Bukkit.addRecipe(recipe)) {
            if (!recipeKeys.contains(key)) {
                recipeKeys.add(key);
            }
        } else {
            plugin.getLogger().warning("Could not register recipe: " + definition.getId());
        }
    }

    private List<String> normalizeShape(List<String> shape) {
        int width = 0;

        for (String row : shape) {
            width = Math.max(width, row.length());
        }

        List<String> normalized = new ArrayList<>();

        for (String row : shape) {
            StringBuilder builder = new StringBuilder(row);

            while (builder.length() < width) {
                builder.append(' ');
            }

            normalized.add(builder.toString());
        }

        return normalized;
    }
}
