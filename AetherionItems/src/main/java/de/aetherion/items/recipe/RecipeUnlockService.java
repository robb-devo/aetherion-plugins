package de.aetherion.items.recipe;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks crafted recipes and obtained compression materials for recipe-book gating.
 */
public final class RecipeUnlockService {

    private static final Pattern TIER_SUFFIX = Pattern.compile("^(.*)_([2-9]|[1-9][0-9]+)$");

    private final AetherionItems plugin;
    private final File file;
    private final Map<UUID, Set<String>> crafted = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> obtained = new ConcurrentHashMap<>();
    private final Set<UUID> fullUnlock = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;

    public RecipeUnlockService(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recipe_unlocks.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public boolean hasCrafted(Player player, String recipeId) {
        if (player == null || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        Set<String> set = crafted.get(player.getUniqueId());
        return set != null && set.contains(recipeId.toLowerCase(Locale.ROOT));
    }

    public boolean hasObtained(Player player, CompressedResource resource) {
        if (player == null || resource == null) {
            return false;
        }
        Set<String> set = obtained.get(player.getUniqueId());
        return set != null && set.contains(resource.key());
    }

    public boolean markCrafted(Player player, String recipeId) {
        if (player == null || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        String id = recipeId.toLowerCase(Locale.ROOT);
        Set<String> set = crafted.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(id)) {
            return false;
        }
        dirty = true;
        return true;
    }

    public boolean noteObtained(Player player, CompressedResource resource) {
        if (player == null || resource == null) {
            return false;
        }
        Set<String> set = obtained.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!set.add(resource.key())) {
            return false;
        }
        dirty = true;
        return true;
    }

    public void noteObtained(Player player, org.bukkit.Material material) {
        noteObtained(player, CompressedResource.fromDrop(material));
    }

    public void noteObtained(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        noteObtained(player, stack.getType());
        CompressedResource byId = CompressedResource.byItemId(
                plugin.getItemManager() == null ? null : plugin.getItemManager().getItemId(stack)
        );
        if (byId != null) {
            noteObtained(player, byId);
        }
        // Owning / receiving a recipe result counts like crafting it for tier unlocks.
        markCraftedForResult(player, stack);
    }

    /**
     * Marks every registered recipe crafted whose result matches the given item.
     * Used when a player obtains gear (quest, fisherman, loot) without crafting it.
     */
    public boolean markCraftedForResult(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        RecipeManager recipes = plugin.getRecipeManager();
        if (recipes == null || plugin.getItemManager() == null) {
            return false;
        }
        if (!plugin.getItemManager().isAetherionItem(stack)) {
            return false;
        }
        boolean changed = false;
        for (RecipeDefinition recipe : recipes.getAllRecipes()) {
            if (recipe == null || recipe.getResult() == null) {
                continue;
            }
            if (!plugin.getItemManager().isAetherionItem(recipe.getResult())) {
                continue;
            }
            if (!plugin.getItemManager().isSameAetherionItem(stack, recipe.getResult())) {
                continue;
            }
            if (markCrafted(player, recipe.getId())) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Scans the player's inventory/equipment and marks matching recipe results as crafted.
     * Covers quest grants and items already owned before obtain-tracking existed.
     */
    public int syncOwnedRecipes(Player player) {
        if (player == null) {
            return 0;
        }
        int added = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (markCraftedForResult(player, stack)) {
                added++;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (markCraftedForResult(player, offhand)) {
            added++;
        }
        if (added > 0) {
            BukkitRecipeService service = plugin.getRecipeService();
            if (service != null) {
                service.discoverFor(player);
            }
        }
        return added;
    }

    public boolean ownsRecipeResult(Player player, String recipeId) {
        if (player == null || recipeId == null || recipeId.isBlank()) {
            return false;
        }
        RecipeManager recipes = plugin.getRecipeManager();
        if (recipes == null || plugin.getItemManager() == null) {
            return false;
        }
        RecipeDefinition recipe = recipes.getRecipe(recipeId);
        if (recipe == null || recipe.getResult() == null) {
            return false;
        }
        ItemStack expected = recipe.getResult();
        if (!plugin.getItemManager().isAetherionItem(expected)) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && plugin.getItemManager().isSameAetherionItem(stack, expected)) {
                return true;
            }
        }
        return plugin.getItemManager().isSameAetherionItem(
                player.getInventory().getItemInOffHand(),
                expected
        );
    }

    public static String predecessorRecipeId(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        Matcher matcher = TIER_SUFFIX.matcher(recipeId.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        String base = matcher.group(1);
        int tier = Integer.parseInt(matcher.group(2));
        if (tier <= 1) {
            return null;
        }
        if (tier == 2) {
            return base;
        }
        return base + "_" + (tier - 1);
    }

    /**
     * DEV helper: unlock every recipe gate (ignore level / obtained / blueprint / spawn).
     * Marks all recipes crafted, all resources obtained, maxes account XP, unlocks blueprints.
     */
    public int unlockAll(Player player) {
        if (player == null) {
            return 0;
        }
        fullUnlock.add(player.getUniqueId());
        dirty = true;
        int added = 0;
        RecipeManager recipes = plugin.getRecipeManager();
        if (recipes != null) {
            for (RecipeDefinition recipe : recipes.getAllRecipes()) {
                if (recipe != null && recipe.getId() != null && markCrafted(player, recipe.getId())) {
                    added++;
                }
            }
        }
        for (CompressedResource resource : CompressedResource.values()) {
            noteObtained(player, resource);
        }
        try {
            if (plugin.getSkills() != null) {
                // Enough bonus XP that account level clears Island/Guild gates.
                long need = de.aetherion.items.skill.AetherionLevel.xpForLevel(
                        Math.max(50, de.aetherion.items.progress.ProgressionService.GUILD_LEVEL));
                long have = plugin.getSkills().accountXp(player);
                if (have < need) {
                    plugin.getSkills().addBonusXp(player, need - have + 500);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (plugin.blueprintUnlocks() != null) {
                for (de.aetherion.items.blueprint.BlueprintKind kind
                        : de.aetherion.items.blueprint.BlueprintKind.values()) {
                    plugin.blueprintUnlocks().unlock(player, kind.id());
                }
                plugin.blueprintUnlocks().enableHunt(player);
            }
        } catch (Throwable ignored) {
        }
        try {
            de.aetherion.items.menu.dev.DevBridges.unlockAllSpawns(player);
        } catch (Throwable ignored) {
        }
        saveIfDirty();
        return added;
    }

    public boolean hasFullUnlock(Player player) {
        return player != null && fullUnlock.contains(player.getUniqueId());
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : crafted.entrySet()) {
            yaml.set("crafted." + entry.getKey(), sorted(entry.getValue()));
        }
        for (Map.Entry<UUID, Set<String>> entry : obtained.entrySet()) {
            yaml.set("obtained." + entry.getKey(), sorted(entry.getValue()));
        }
        java.util.List<String> full = new java.util.ArrayList<>();
        for (UUID id : fullUnlock) {
            full.add(id.toString());
        }
        Collections.sort(full);
        yaml.set("full-unlock", full);
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save recipe_unlocks.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection craftedSection = yaml.getConfigurationSection("crafted");
        if (craftedSection != null) {
            for (String key : craftedSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Set<String> values = ConcurrentHashMap.newKeySet();
                    values.addAll(craftedSection.getStringList(key));
                    crafted.put(id, values);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection obtainedSection = yaml.getConfigurationSection("obtained");
        if (obtainedSection != null) {
            for (String key : obtainedSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Set<String> values = ConcurrentHashMap.newKeySet();
                    values.addAll(obtainedSection.getStringList(key));
                    obtained.put(id, values);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (String raw : yaml.getStringList("full-unlock")) {
            try {
                fullUnlock.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static java.util.List<String> sorted(Set<String> values) {
        java.util.List<String> list = new java.util.ArrayList<>(values == null ? Set.of() : values);
        Collections.sort(list);
        return list;
    }
}
