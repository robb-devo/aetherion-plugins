package de.aetherion.items.recipe.GUI;

/**
 * Shared double-chest layout for the recipe book GUI and its listener.
 */
public final class RecipeBookLayout {

    public static final int SIZE = 54;

    public static final int HEADER_SLOT = 4;
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;
    public static final int PREV_PAGE_SLOT = 52;
    public static final int NEXT_PAGE_SLOT = 53;

    public static final int ARROW_SLOT = 16;
    public static final int RESULT_SLOT = 25;
    public static final int INFO_SLOT = 34;

    public static final int[] CATEGORY_SLOTS = {
            11, 13, 15,
            20, 22, 24,
            29, 31, 33,
            38, 40, 42
    };

    public static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public static final int ARMOR_SETS_PER_PAGE = 7;
    public static final int ARMOR_COLUMNS = 7;
    public static final int ARMOR_PIECES = 4;

    public static final int[] CRAFTING_GRID_SLOTS = {
            12, 13, 14,
            21, 22, 23,
            30, 31, 32
    };

    private RecipeBookLayout() {
    }

    public static int recipeIndexFromSlot(int slot) {
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            if (RECIPE_SLOTS[i] == slot) {
                return i;
            }
        }

        return -1;
    }

    public static int armorSlot(int column, int piece) {
        return RECIPE_SLOTS[piece * ARMOR_COLUMNS + column];
    }

    public static int armorColumnFromSlot(int slot) {
        int index = recipeIndexFromSlot(slot);
        if (index < 0) {
            return -1;
        }
        return index % ARMOR_COLUMNS;
    }

    public static int armorPieceFromSlot(int slot) {
        int index = recipeIndexFromSlot(slot);
        if (index < 0) {
            return -1;
        }
        return index / ARMOR_COLUMNS;
    }

    public static int craftingIndexFromSlot(int slot) {
        for (int i = 0; i < CRAFTING_GRID_SLOTS.length; i++) {
            if (CRAFTING_GRID_SLOTS[i] == slot) {
                return i;
            }
        }

        return -1;
    }

    public static boolean isCraftingGridSlot(int slot) {
        return craftingIndexFromSlot(slot) >= 0;
    }
}
