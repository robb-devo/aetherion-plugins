package de.aetherion.items.listener;

import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.recipe.BukkitRecipeService;
import de.aetherion.items.recipe.CraftingMatcher;
import de.aetherion.items.recipe.RecipeDefinition;
import de.aetherion.items.recipe.RecipeManager;
import de.aetherion.items.storage.StorageInventory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {

    private final ItemManager itemManager;
    private final CraftingMatcher matcher;
    private final BukkitRecipeService recipeService;
    private final StorageInventory storageInventory;
    private final de.aetherion.items.recipe.RecipeUnlockService recipeUnlocks;

    public CraftingListener(
            ItemManager itemManager,
            RecipeManager recipeManager,
            BukkitRecipeService recipeService,
            StorageInventory storageInventory,
            de.aetherion.items.recipe.RecipeUnlockService recipeUnlocks
    ) {
        this.itemManager = itemManager;
        this.matcher = new CraftingMatcher(recipeManager, itemManager);
        this.recipeService = recipeService;
        this.storageInventory = storageInventory;
        this.recipeUnlocks = recipeUnlocks;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (recipeUnlocks != null) {
            recipeUnlocks.syncOwnedRecipes(event.getPlayer());
        }
        recipeService.discoverFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || recipeUnlocks == null) {
            return;
        }
        recipeUnlocks.noteObtained(player, event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        RecipeDefinition matched = matcher.find(inventory.getMatrix());

        if (matched != null) {
            if (event.getView().getPlayer() instanceof Player player
                    && !matched.isUnlocked(player)) {
                inventory.setResult(null);
                return;
            }

            ItemStack result = buildCraftResult(matched, inventory.getMatrix());
            result.setAmount(1);
            inventory.setResult(result);
            return;
        }

        ItemStack current = inventory.getResult();

        if (recipeService.isAetherionRecipe(event.getRecipe())
                || itemManager.isAetherionItem(current)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        RecipeDefinition matched = matcher.find(crafting.getMatrix());
        if (matched == null || !matched.hasStackedIngredients()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!matched.isUnlocked(player)) {
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.DROP
                || event.getClick() == ClickType.CONTROL_DROP
                || event.getClick() == ClickType.SWAP_OFFHAND) {
            return;
        }
        completeStackedCraft(player, crafting, matched, event.isShiftClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResultDrag(InventoryDragEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        RecipeDefinition matched = matcher.find(crafting.getMatrix());
        if (matched == null || !matched.hasStackedIngredients()) {
            return;
        }
        if (event.getRawSlots().contains(0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        RecipeDefinition matched = matcher.find(event.getInventory().getMatrix());

        if (matched == null) {
            if (recipeService.isAetherionRecipe(event.getRecipe())
                    || itemManager.isAetherionItem(event.getCurrentItem())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getWhoClicked() instanceof Player player && !matched.isUnlocked(player)) {
            event.setCancelled(true);
            return;
        }

        if (matched.hasStackedIngredients()) {
            event.setCancelled(true);
            return;
        }

        ItemStack result = buildCraftResult(matched, event.getInventory().getMatrix());
        result.setAmount(1);
        event.getInventory().setResult(result);
        event.setCurrentItem(result);

        if (event.getWhoClicked() instanceof Player player) {
            if (recipeUnlocks != null) {
                recipeUnlocks.markCrafted(player, matched.getId());
                recipeUnlocks.noteObtained(player, result);
            }
            de.aetherion.items.util.QuestProgressHook.noteCrafted(player, matched.getId(), 1);
            String itemId = itemManager.getItemId(result);
            if (itemId != null && !itemId.equalsIgnoreCase(matched.getId())) {
                de.aetherion.items.util.QuestProgressHook.noteCrafted(player, itemId, 1);
            }
            if ("aetherion_storage".equals(matched.getId())) {
                storageInventory.grantHubAccess(player);
            }
        }
    }

    private void completeStackedCraft(
            Player player,
            CraftingInventory inventory,
            RecipeDefinition recipe,
            boolean shiftClick
    ) {
        ItemStack result = buildCraftResult(recipe, inventory.getMatrix());
        result.setAmount(1);
        if (!shiftClick) {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!cursor.isSimilar(result) || cursor.getAmount() >= cursor.getMaxStackSize()) {
                    return;
                }
            }
        }
        matcher.consume(inventory, recipe);
        inventory.setResult(null);
        if (shiftClick) {
            player.getInventory().addItem(result).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        } else {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir()) {
                player.setItemOnCursor(result);
            } else {
                cursor.setAmount(cursor.getAmount() + 1);
                player.setItemOnCursor(cursor);
            }
        }
        if (recipeUnlocks != null) {
            recipeUnlocks.markCrafted(player, recipe.getId());
            recipeUnlocks.noteObtained(player, result);
        }
        de.aetherion.items.util.QuestProgressHook.noteCrafted(player, recipe.getId(), 1);
        String itemId = itemManager.getItemId(result);
        if (itemId != null && !itemId.equalsIgnoreCase(recipe.getId())) {
            de.aetherion.items.util.QuestProgressHook.noteCrafted(player, itemId, 1);
        }
        if ("aetherion_storage".equals(recipe.getId())) {
            storageInventory.grantHubAccess(player);
        }
        player.updateInventory();
    }

    private ItemStack buildCraftResult(RecipeDefinition recipe, ItemStack[] matrix) {
        ItemStack result = recipe.getResult();
        if (recipe.hasStackedIngredients()) {
            return result;
        }
        ItemStack[] carried = carryGear(recipe, matrix);
        return itemManager.applyUpgradeProgress(result, carried[0], carried[1]);
    }

    /**
     * {@code [live gear, recipe template]}. Compressed mats are Aetherion items and
     * are listed first in ladder recipes; copying progress off them drops sockets.
     */
    private ItemStack[] carryGear(RecipeDefinition recipe, ItemStack[] matrix) {
        ItemStack gear = null;
        ItemStack gearTemplate = null;
        ItemStack resource = null;
        ItemStack resourceTemplate = null;
        for (ItemStack expected : recipe.getIngredients().values()) {
            if (!itemManager.isAetherionItem(expected)) {
                continue;
            }
            ItemStack found = null;
            if (matrix != null) {
                for (ItemStack slot : matrix) {
                    if (itemManager.isSameAetherionItem(slot, expected)) {
                        found = slot;
                        break;
                    }
                }
            }
            if (found == null) {
                continue;
            }
            if (de.aetherion.items.recipe.CraftSource.isResourceId(itemManager.getItemId(expected))) {
                if (resource == null) {
                    resource = found.clone();
                    resourceTemplate = expected;
                }
                continue;
            }
            gear = found.clone();
            gearTemplate = expected;
            break;
        }
        if (gear == null) {
            gear = resource;
            gearTemplate = resourceTemplate;
        }
        return new ItemStack[]{gear, gearTemplate};
    }
}
