package de.aetherion.items.recipe;

import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * =========================================================
 * RECIPE DEFINITION
 * =========================================================
 *
 * Eine vollständige Definition eines Aetherion-Rezepts.
 *
 * Ingredients können sein:
 *
 * - normale Minecraft Materialien
 * - bestehende Aetherion Items
 *
 * =========================================================
 */

public class RecipeDefinition {


    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private final String id;

    private final RecipeCategory category;

    private final ItemStack result;

    private final Rarity rarity;

    private final List<String> shape;

    private final Map<Character, ItemStack> ingredients;

    private final UnlockRequirement unlockRequirement;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public RecipeDefinition(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            List<String> shape,
            Map<Character, ItemStack> ingredients,
            UnlockRequirement unlockRequirement
    ) {

        this.id = id;
        this.category = category;
        this.result = result.clone();
        this.rarity = rarity;

        this.shape =
                Collections.unmodifiableList(
                        new ArrayList<>(shape)
                );

        Map<Character, ItemStack> ingredientCopies =
                new LinkedHashMap<>();

        for (
                Map.Entry<Character, ItemStack> entry
                : ingredients.entrySet()
        ) {

            ingredientCopies.put(
                    entry.getKey(),
                    entry.getValue().clone()
            );
        }

        this.ingredients =
                Collections.unmodifiableMap(
                        ingredientCopies
                );

        this.unlockRequirement =
                unlockRequirement != null
                        ? unlockRequirement
                        : UnlockRequirement.ALWAYS_UNLOCKED;
    }

    public boolean hasStackedIngredients() {
        for (ItemStack ingredient : ingredients.values()) {
            if (ingredient != null && ingredient.getAmount() > 1) {
                return true;
            }
        }
        return false;
    }


    /*
     * =========================================================
     * ID
     * =========================================================
     */

    public String getId() {

        return id;
    }


    /*
     * =========================================================
     * CATEGORY
     * =========================================================
     */

    public RecipeCategory getCategory() {

        return category;
    }


    /*
     * =========================================================
     * RESULT
     * =========================================================
     */

    public ItemStack getResult() {

        return result.clone();
    }


    /*
     * =========================================================
     * RARITY
     * =========================================================
     */

    public Rarity getRarity() {

        return rarity;
    }


    /*
     * =========================================================
     * SHAPE
     * =========================================================
     */

    public List<String> getShape() {

        return shape;
    }


    /*
     * =========================================================
     * INGREDIENTS
     * =========================================================
     *
     * Jeder Ingredient ist jetzt ein vollständiges ItemStack.
     *
     * Dadurch können wir später z.B. verwenden:
     *
     * I -> IRON_INGOT
     *
     * oder:
     *
     * C -> createCombatHelmet()
     *
     * =========================================================
     */

    public Map<Character, ItemStack> getIngredients() {

        Map<Character, ItemStack> copies =
                new LinkedHashMap<>();

        for (
                Map.Entry<Character, ItemStack> entry
                : ingredients.entrySet()
        ) {

            copies.put(
                    entry.getKey(),
                    entry.getValue().clone()
            );
        }

        return Collections.unmodifiableMap(
                copies
        );
    }


    /*
     * =========================================================
     * UNLOCK REQUIREMENT
     * =========================================================
     */

    public UnlockRequirement getUnlockRequirement() {

        return unlockRequirement;
    }


    /*
     * =========================================================
     * UNLOCK CHECK
     * =========================================================
     */

    public boolean isUnlocked(
            org.bukkit.entity.Player player
    ) {
        try {
            de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
            if (plugin != null && plugin.recipeUnlocks() != null
                    && plugin.recipeUnlocks().hasFullUnlock(player)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return unlockRequirement.isUnlocked(
                player
        );
    }


    /*
     * =========================================================
     * UNLOCK DISPLAY
     * =========================================================
     */

    public String getUnlockDisplayText() {

        return unlockRequirement.getDisplayText();
    }
}