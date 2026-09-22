package de.aetherion.items.recipe;

import de.aetherion.items.manager.ItemManager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-click crafting from the recipe book when the player has all ingredients.
 */
public final class RecipeBookCrafter {

    private RecipeBookCrafter() {
    }

    public static boolean canCraft(Player player, RecipeDefinition recipe, ItemManager itemManager) {
        if (player == null || recipe == null || itemManager == null) {
            return false;
        }
        if (!recipe.isUnlocked(player)) {
            return false;
        }
        return missingIngredients(player, recipe, itemManager).isEmpty();
    }

    /**
     * @return human-readable missing ingredient lines, empty if craftable
     */
    public static List<String> missingIngredients(
            Player player,
            RecipeDefinition recipe,
            ItemManager itemManager
    ) {
        List<String> missing = new ArrayList<>();
        if (player == null || recipe == null || itemManager == null) {
            missing.add("Unknown");
            return missing;
        }

        ItemStack[] snapshot = cloneStorage(player.getInventory());
        for (ItemStack required : flattenRequirements(recipe)) {
            int need = Math.max(1, required.getAmount());
            int taken = takeFromSnapshot(snapshot, required, need, itemManager);
            if (taken < need) {
                missing.add(displayName(required) + " §8x" + (need - taken));
            }
        }
        return missing;
    }

    /**
     * Consumes ingredients and returns the crafted result, or null on failure.
     */
    public static ItemStack craft(Player player, RecipeDefinition recipe, ItemManager itemManager) {
        if (!canCraft(player, recipe, itemManager)) {
            return null;
        }

        ItemStack previousTemplate = null;
        ItemStack previousItem = null;
        for (ItemStack expected : recipe.getIngredients().values()) {
            if (!itemManager.isAetherionItem(expected)) {
                continue;
            }
            previousTemplate = expected;
            previousItem = findInStorage(player.getInventory(), expected, itemManager);
            if (previousItem != null) {
                previousItem = previousItem.clone();
            }
            break;
        }

        for (ItemStack required : flattenRequirements(recipe)) {
            int need = Math.max(1, required.getAmount());
            if (!removeFromStorage(player.getInventory(), required, need, itemManager)) {
                return null;
            }
        }

        ItemStack result = recipe.getResult();
        result.setAmount(1);
        if (!recipe.hasStackedIngredients()) {
            result = itemManager.applyUpgradeProgress(result, previousItem, previousTemplate);
        }
        return result;
    }

    public static List<ItemStack> flattenRequirements(RecipeDefinition recipe) {
        List<ItemStack> required = new ArrayList<>();
        if (recipe == null) {
            return required;
        }
        Map<Character, ItemStack> ingredients = recipe.getIngredients();
        for (String row : recipe.getShape()) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol == ' ') {
                    continue;
                }
                ItemStack ingredient = ingredients.get(symbol);
                if (ingredient != null && !ingredient.getType().isAir()) {
                    required.add(ingredient.clone());
                }
            }
        }
        return required;
    }

    private static ItemStack[] cloneStorage(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        ItemStack[] clone = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            clone[i] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }
        return clone;
    }

    private static ItemStack findInStorage(
            PlayerInventory inventory,
            ItemStack required,
            ItemManager itemManager
    ) {
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, required, itemManager)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean removeFromStorage(
            PlayerInventory inventory,
            ItemStack required,
            int amount,
            ItemManager itemManager
    ) {
        ItemStack[] storage = inventory.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack stack = storage[i];
            if (!matches(stack, required, itemManager)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                storage[i] = null;
            } else {
                stack.setAmount(left);
            }
            remaining -= take;
        }
        inventory.setStorageContents(storage);
        return remaining <= 0;
    }

    private static int takeFromSnapshot(
            ItemStack[] snapshot,
            ItemStack required,
            int amount,
            ItemManager itemManager
    ) {
        int remaining = amount;
        int taken = 0;
        for (int i = 0; i < snapshot.length && remaining > 0; i++) {
            ItemStack stack = snapshot[i];
            if (!matches(stack, required, itemManager)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                snapshot[i] = null;
            } else {
                ItemStack copy = stack.clone();
                copy.setAmount(left);
                snapshot[i] = copy;
            }
            remaining -= take;
            taken += take;
        }
        return taken;
    }

    private static boolean matches(ItemStack actual, ItemStack required, ItemManager itemManager) {
        if (actual == null || actual.getType().isAir() || required == null || required.getType().isAir()) {
            return false;
        }
        if (itemManager.isAetherionItem(required)) {
            return itemManager.isSameAetherionItem(actual, required);
        }
        return actual.getType() == required.getType() && !itemManager.isAetherionItem(actual);
    }

    private static String displayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        if (item == null) {
            return "Item";
        }
        String[] parts = item.getType().name().toLowerCase().split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                name.append(part.substring(1));
            }
        }
        return "§f" + name;
    }
}
