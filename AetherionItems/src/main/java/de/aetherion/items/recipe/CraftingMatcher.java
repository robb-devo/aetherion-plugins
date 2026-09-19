package de.aetherion.items.recipe;

import de.aetherion.items.manager.ItemManager;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Matches a crafting matrix against RecipeDefinitions.
 *
 * Aetherion ingredients are compared by item id, not by ExactChoice NBT.
 * Boosted tools/armor therefore still count as the previous-tier item.
 */
public class CraftingMatcher {

    private final RecipeManager recipeManager;
    private final ItemManager itemManager;

    public CraftingMatcher(RecipeManager recipeManager, ItemManager itemManager) {
        this.recipeManager = recipeManager;
        this.itemManager = itemManager;
    }

    public RecipeDefinition find(ItemStack[] matrix) {
        ItemStack[] grid = toThreeByThree(matrix);

        if (grid == null) {
            return null;
        }

        for (RecipeDefinition recipe : recipeManager.getAllRecipes()) {
            if (matches(recipe, grid)) {
                return recipe;
            }
        }

        return null;
    }

    private boolean matches(RecipeDefinition recipe, ItemStack[] grid) {
        List<String> shape = recipe.getShape();

        if (shape == null || shape.isEmpty()) {
            return false;
        }

        int height = shape.size();
        int width = 0;

        for (String row : shape) {
            width = Math.max(width, row.length());
        }

        if (width == 0 || height > 3 || width > 3) {
            return false;
        }

        for (int rowOffset = 0; rowOffset <= 3 - height; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= 3 - width; columnOffset++) {
                if (matchesAt(recipe, grid, rowOffset, columnOffset, height, width)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesAt(
            RecipeDefinition recipe,
            ItemStack[] grid,
            int rowOffset,
            int columnOffset,
            int height,
            int width
    ) {
        List<String> shape = recipe.getShape();
        Map<Character, ItemStack> ingredients = recipe.getIngredients();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                ItemStack actual = grid[row * 3 + column];
                boolean inside = row >= rowOffset
                        && row < rowOffset + height
                        && column >= columnOffset
                        && column < columnOffset + width;

                if (!inside) {
                    if (!isEmpty(actual)) {
                        return false;
                    }

                    continue;
                }

                String shapeRow = shape.get(row - rowOffset);
                int shapeColumn = column - columnOffset;
                char symbol = shapeColumn < shapeRow.length() ? shapeRow.charAt(shapeColumn) : ' ';

                if (symbol == ' ') {
                    if (!isEmpty(actual)) {
                        return false;
                    }

                    continue;
                }

                ItemStack required = ingredients.get(symbol);

                if (required == null || !matchesIngredient(actual, required)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesIngredient(ItemStack actual, ItemStack required) {
        if (isEmpty(actual) || isEmpty(required)) {
            return false;
        }

        if (itemManager.isAetherionItem(required)) {
            if (!itemManager.isSameAetherionItem(actual, required)) {
                return false;
            }
        } else if (actual.getType() != required.getType()) {
            return false;
        }

        return actual.getAmount() >= required.getAmount();
    }

    public void consume(org.bukkit.inventory.CraftingInventory inventory, RecipeDefinition recipe) {
        if (inventory == null || recipe == null) {
            return;
        }
        ItemStack[] matrix = inventory.getMatrix();
        ItemStack[] grid = toThreeByThree(matrix);
        if (grid == null) {
            return;
        }
        int[] offset = locate(recipe, grid);
        if (offset == null) {
            return;
        }
        subtract(recipe, grid, offset[0], offset[1]);
        if (matrix.length == 4) {
            matrix[0] = grid[0];
            matrix[1] = grid[1];
            matrix[2] = grid[3];
            matrix[3] = grid[4];
        } else {
            System.arraycopy(grid, 0, matrix, 0, Math.min(matrix.length, grid.length));
        }
        inventory.setMatrix(matrix);
    }

    private int[] locate(RecipeDefinition recipe, ItemStack[] grid) {
        List<String> shape = recipe.getShape();
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        int height = shape.size();
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row.length());
        }
        if (width == 0 || height > 3 || width > 3) {
            return null;
        }
        for (int rowOffset = 0; rowOffset <= 3 - height; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= 3 - width; columnOffset++) {
                if (matchesAt(recipe, grid, rowOffset, columnOffset, height, width)) {
                    return new int[]{rowOffset, columnOffset};
                }
            }
        }
        return null;
    }

    private void subtract(RecipeDefinition recipe, ItemStack[] grid, int rowOffset, int columnOffset) {
        List<String> shape = recipe.getShape();
        int height = shape.size();
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row.length());
        }
        for (int row = 0; row < height; row++) {
            String shapeRow = shape.get(row);
            for (int column = 0; column < width; column++) {
                char symbol = column < shapeRow.length() ? shapeRow.charAt(column) : ' ';
                if (symbol == ' ') {
                    continue;
                }
                ItemStack required = recipe.getIngredients().get(symbol);
                if (required == null) {
                    continue;
                }
                int index = (row + rowOffset) * 3 + (column + columnOffset);
                ItemStack actual = grid[index];
                if (isEmpty(actual)) {
                    continue;
                }
                int take = Math.max(1, required.getAmount());
                ItemStack copy = actual.clone();
                if (copy.getAmount() <= take) {
                    grid[index] = null;
                } else {
                    copy.setAmount(copy.getAmount() - take);
                    grid[index] = copy;
                }
            }
        }
    }

    private ItemStack[] toThreeByThree(ItemStack[] matrix) {
        if (matrix == null) {
            return null;
        }

        if (matrix.length == 9) {
            return matrix;
        }

        if (matrix.length == 4) {
            ItemStack[] grid = new ItemStack[9];
            grid[0] = matrix[0];
            grid[1] = matrix[1];
            grid[3] = matrix[2];
            grid[4] = matrix[3];
            return grid;
        }

        return null;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
