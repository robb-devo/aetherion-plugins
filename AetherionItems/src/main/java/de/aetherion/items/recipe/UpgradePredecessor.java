package de.aetherion.items.recipe;

import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterSockets;
import de.aetherion.items.model.ItemStats;

import de.aetherion.items.storage.SackItems;
import de.aetherion.items.storage.SkillSack;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * LOCKED booster rule: on an upgrade craft, the previous gear piece hands its sockets to the result.
 * The crafting grid ({@code CraftingListener}) and Insta Craft ({@link RecipeBookCrafter}) both pick that
 * piece the same way: the recipe's predecessor id, then shape key 'C', then the Aetherion ingredient with
 * the most boosters. Never "the first Aetherion ingredient": ladder mats (compressed/compacted) are
 * Aetherion items too and would wipe the sockets.
 */
public final class UpgradePredecessor {

    private UpgradePredecessor() {
    }

    /** The ingredient the recipe data names as the previous piece (predecessor id, else an Aetherion 'C'). */
    public static ItemStack namedIngredient(RecipeDefinition recipe, ItemManager items) {
        if (recipe == null || items == null) {
            return null;
        }
        String predecessorId = RecipeUnlockService.predecessorForCraft(recipe);
        if (predecessorId != null) {
            for (ItemStack expected : recipe.getIngredients().values()) {
                if (predecessorId.equalsIgnoreCase(items.getItemId(expected))) {
                    return expected;
                }
            }
        }
        ItemStack center = recipe.getIngredients().get('C');
        return items.isAetherionItem(center) ? center : null;
    }

    /** Booster count of a candidate piece; real gear outranks pure mats when the counts tie. */
    public static int score(ItemManager items, ItemStack stack) {
        int score = BoosterSockets.filled(BoosterSockets.read(items, stack));
        score = Math.max(score, items.getItemStats(stack).getTotalBoosters());
        if (looksLikeGear(items, stack)) {
            score += 1000;
        }
        return score;
    }

    /**
     * A skill sack upgrade keeps the old sack's id, so its stored counts carry over.
     * Returns the re-issued sack, or null when this craft is not a sack upgrade.
     */
    public static ItemStack keepSackId(ItemStack result, ItemStack previousItem) {
        if (!SkillSack.is(result) || !SkillSack.is(previousItem)) {
            return null;
        }
        UUID keep = SackItems.idOf(previousItem);
        var skill = SkillSack.skillOf(result);
        var tier = SkillSack.tierOf(result);
        if (keep == null || skill == null || tier == null) {
            return null;
        }
        return SkillSack.create(skill, tier, keep);
    }

    public static boolean looksLikeGear(ItemManager items, ItemStack item) {
        if (item == null || items == null || !items.isAetherionItem(item)) {
            return false;
        }
        ItemStats stats = items.getItemStats(item);
        return stats.getDamage() > 0.0
                || stats.getDefense() > 0.0
                || stats.getHealth() > 0.0
                || stats.getMiningPower() > 0.0
                || stats.getFortune() > 0.0
                || stats.getFishingSpeed() > 0.0
                || stats.getHarvestSpread() > 0.0;
    }
}
