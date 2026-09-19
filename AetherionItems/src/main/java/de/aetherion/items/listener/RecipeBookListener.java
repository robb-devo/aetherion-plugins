package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.recipe.GUI.RecipeBookGUI;
import de.aetherion.items.recipe.GUI.RecipeBookLayout;
import de.aetherion.items.recipe.RecipeBookCrafter;
import de.aetherion.items.recipe.RecipeCategory;
import de.aetherion.items.recipe.RecipeDefinition;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class RecipeBookListener implements Listener {

    private final RecipeBookGUI recipeBookGUI;
    private final ItemManager itemManager;

    public RecipeBookListener(RecipeBookGUI recipeBookGUI, ItemManager itemManager) {
        this.recipeBookGUI = recipeBookGUI;
        this.itemManager = itemManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (item == null || item.getType().isAir()) {
            return;
        }

        if (!"recipe_book".equals(itemManager.getItemId(item))) {
            return;
        }

        event.setCancelled(true);
        recipeBookGUI.open(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof RecipeBookGUI.RecipeBookHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();

        switch (holder.getType()) {
            case CATEGORIES -> handleCategoryClick(player, slot);
            case RECIPE_LIST -> handleRecipeListClick(player, holder, slot);
            case RECIPE_DETAIL -> handleRecipeDetailClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof RecipeBookGUI.RecipeBookHolder) {
            event.setCancelled(true);
        }
    }

    private void handleCategoryClick(Player player, int slot) {
        if (slot == RecipeBookLayout.BACK_SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }

        RecipeCategory[] categories = RecipeCategory.values();

        for (int i = 0; i < RecipeBookLayout.CATEGORY_SLOTS.length && i < categories.length; i++) {
            if (RecipeBookLayout.CATEGORY_SLOTS[i] != slot) {
                continue;
            }

            RecipeCategory category = categories[i];

            if (!recipeBookGUI.isCategoryUnlocked(player, category)) {
                player.sendMessage("§cThis category is still locked.");
                return;
            }

            recipeBookGUI.openCategory(player, category);
            return;
        }
    }

    private void handleRecipeListClick(
            Player player,
            RecipeBookGUI.RecipeBookHolder holder,
            int slot
    ) {
        if (slot == RecipeBookLayout.BACK_SLOT) {
            recipeBookGUI.openCategories(player);
            return;
        }

        if (slot == RecipeBookLayout.PREV_PAGE_SLOT && holder.getPage() > 1) {
            recipeBookGUI.openCategory(player, holder.getCategory(), holder.getPage() - 1);
            return;
        }

        if (slot == RecipeBookLayout.NEXT_PAGE_SLOT) {
            recipeBookGUI.openCategory(player, holder.getCategory(), holder.getPage() + 1);
            return;
        }

        RecipeCategory category = holder.getCategory();

        if (category == null) {
            return;
        }

        if (recipeBookGUI.isGearSetCategory(category)) {
            RecipeDefinition armorRecipe = recipeBookGUI.getGearRecipe(player, category, holder.getPage(), slot);
            if (armorRecipe == null) {
                return;
            }
            if (!armorRecipe.isUnlocked(player)) {
                player.sendMessage("§cThis recipe is still locked.");
                return;
            }
            recipeBookGUI.openRecipe(player, armorRecipe, holder.getPage());
            return;
        }

        int slotIndex = RecipeBookLayout.recipeIndexFromSlot(slot);

        if (slotIndex < 0) {
            return;
        }

        List<RecipeDefinition> recipes = recipeBookGUI.getRecipes(player, category);

        int recipeIndex = slotIndex + (holder.getPage() - 1) * RecipeBookLayout.RECIPE_SLOTS.length;

        if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
            return;
        }

        RecipeDefinition recipe = recipes.get(recipeIndex);

        if (!recipe.isUnlocked(player)) {
            player.sendMessage("§cThis recipe is still locked.");
            return;
        }

        recipeBookGUI.openRecipe(player, recipe, holder.getPage());
    }

    private void handleRecipeDetailClick(
            Player player,
            RecipeBookGUI.RecipeBookHolder holder,
            int slot
    ) {
        if (slot == RecipeBookLayout.BACK_SLOT) {
            if (holder.getCategory() != null) {
                recipeBookGUI.openCategory(player, holder.getCategory(), holder.getPage());
            } else {
                recipeBookGUI.openCategories(player);
            }
            return;
        }

        if (slot == RecipeBookLayout.INFO_SLOT) {
            tryInstaCraft(player, holder);
            return;
        }

        if (!RecipeBookLayout.isCraftingGridSlot(slot)) {
            return;
        }

        RecipeDefinition currentRecipe = holder.getRecipe();

        if (currentRecipe == null) {
            return;
        }

        ItemStack ingredient = getIngredientAtSlot(currentRecipe, slot);

        if (ingredient == null) {
            return;
        }

        RecipeDefinition ingredientRecipe = findRecipeForItem(ingredient);

        if (ingredientRecipe == null) {
            return;
        }

        if (!ingredientRecipe.isUnlocked(player)) {
            player.sendMessage("§cThis recipe is still locked.");
            return;
        }

        recipeBookGUI.openRecipe(player, ingredientRecipe, holder.getPage());
    }

    private void tryInstaCraft(Player player, RecipeBookGUI.RecipeBookHolder holder) {
        RecipeDefinition recipe = holder.getRecipe();
        if (recipe == null) {
            return;
        }
        if (!recipe.isUnlocked(player)) {
            player.sendMessage("§cThis recipe is still locked.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return;
        }
        if (!RecipeBookCrafter.canCraft(player, recipe, itemManager)) {
            player.sendMessage("§cMissing ingredients for Insta Craft.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            recipeBookGUI.openRecipe(player, recipe, holder.getPage());
            return;
        }

        ItemStack result = RecipeBookCrafter.craft(player, recipe, itemManager);
        if (result == null) {
            player.sendMessage("§cCould not craft that recipe.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            recipeBookGUI.openRecipe(player, recipe, holder.getPage());
            return;
        }

        player.getInventory().addItem(result).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().markCrafted(player, recipe.getId());
            plugin.recipeUnlocks().noteObtained(player, result);
            if (plugin.getRecipeService() != null) {
                plugin.getRecipeService().discoverFor(player);
            }
        }
        de.aetherion.items.util.QuestProgressHook.noteCrafted(player, recipe.getId(), 1);
        String itemId = itemManager.getItemId(result);
        if (itemId != null && !itemId.equalsIgnoreCase(recipe.getId())) {
            de.aetherion.items.util.QuestProgressHook.noteCrafted(player, itemId, 1);
        }
        if (plugin != null
                && plugin.getStorageInventory() != null
                && "aetherion_storage".equals(recipe.getId())) {
            plugin.getStorageInventory().grantHubAccess(player);
        }

        player.sendMessage("§aCrafted §f" + displayName(result) + "§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        player.updateInventory();
        recipeBookGUI.openRecipe(player, recipe, holder.getPage());
    }

    private String displayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item == null ? "Item" : item.getType().name().toLowerCase().replace('_', ' ');
    }

    private ItemStack getIngredientAtSlot(RecipeDefinition recipe, int slot) {
        int index = RecipeBookLayout.craftingIndexFromSlot(slot);

        if (index < 0) {
            return null;
        }

        int row = index / 3;
        int column = index % 3;
        List<String> shape = recipe.getShape();

        if (row >= shape.size()) {
            return null;
        }

        String currentRow = shape.get(row);

        if (column >= currentRow.length()) {
            return null;
        }

        char symbol = currentRow.charAt(column);

        if (symbol == ' ' || !recipe.getIngredients().containsKey(symbol)) {
            return null;
        }

        return recipe.getIngredients().get(symbol);
    }

    private RecipeDefinition findRecipeForItem(ItemStack item) {
        String itemId = itemManager.getItemId(item);

        if (itemId == null) {
            return null;
        }

        for (RecipeCategory category : RecipeCategory.values()) {
            for (RecipeDefinition recipe : recipeBookGUI.getRecipes(null, category)) {
                if (itemId.equals(itemManager.getItemId(recipe.getResult()))) {
                    return recipe;
                }
            }
        }

        return null;
    }
}
