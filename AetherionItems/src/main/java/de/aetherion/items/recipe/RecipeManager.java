package de.aetherion.items.recipe;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * =========================================================
 * RECIPE MANAGER
 * =========================================================
 *
 * Zentrale Registry für alle Aetherion-Rezepte.
 *
 * Andere Systeme greifen später auf diese Registry zu:
 *
 * - Recipe Book
 * - Crafting
 * - Quest Unlocks
 * - Progression
 * - eventuell Admin Tools
 *
 * =========================================================
 */

public class RecipeManager {


    /*
     * =========================================================
     * RECIPE REGISTRY
     * =========================================================
     *
     * LinkedHashMap sorgt dafür, dass die Registrierungs-
     * reihenfolge erhalten bleibt.
     *
     * Das ist später praktisch für die Reihenfolge im Buch.
     *
     * =========================================================
     */

    private final Map<String, RecipeDefinition> recipes =
            new LinkedHashMap<>();


    /*
     * =========================================================
     * REGISTER
     * =========================================================
     */

    public void register(
            RecipeDefinition recipe
    ) {

        if (recipe == null) {
            throw new IllegalArgumentException(
                    "Recipe cannot be null."
            );
        }

        String id =
                recipe.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipe ID cannot be null or blank."
            );
        }

        if (recipes.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Recipe already registered: " + id
            );
        }

        recipes.put(
                id,
                recipe
        );
    }


    /*
     * =========================================================
     * UNREGISTER
     * =========================================================
     */

    public void unregister(
            String id
    ) {

        if (id == null) {
            return;
        }

        recipes.remove(id);
    }


    /*
     * =========================================================
     * GET BY ID
     * =========================================================
     */

    public RecipeDefinition getRecipe(
            String id
    ) {

        if (id == null) {
            return null;
        }

        return recipes.get(id);
    }


    /*
     * =========================================================
     * EXISTS
     * =========================================================
     */

    public boolean hasRecipe(
            String id
    ) {

        if (id == null) {
            return false;
        }

        return recipes.containsKey(id);
    }


    /*
     * =========================================================
     * ALL RECIPES
     * =========================================================
     */

    public Collection<RecipeDefinition> getAllRecipes() {

        return Collections.unmodifiableCollection(
                recipes.values()
        );
    }


    /*
     * =========================================================
     * RECIPES BY CATEGORY
     * =========================================================
     */

    public List<RecipeDefinition> getRecipesByCategory(
            RecipeCategory category
    ) {

        List<RecipeDefinition> result =
                new ArrayList<>();

        if (category == null) {
            return result;
        }

        for (
                RecipeDefinition recipe
                : recipes.values()
        ) {

            if (
                    recipe.getCategory()
                            == category
            ) {

                result.add(recipe);
            }
        }

        return Collections.unmodifiableList(
                result
        );
    }


    /*
     * =========================================================
     * RECIPE COUNT
     * =========================================================
     */

    public int getRecipeCount() {

        return recipes.size();
    }


    /*
     * =========================================================
     * CLEAR
     * =========================================================
     *
     * Primär für Reloads / Tests.
     * =========================================================
     */

    public void clear() {

        recipes.clear();
    }
}