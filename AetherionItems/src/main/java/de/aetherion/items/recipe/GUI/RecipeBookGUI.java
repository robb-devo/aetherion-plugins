package de.aetherion.items.recipe.GUI;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.TexturedHeads;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.recipe.AetherMobsUnlockRequirement;
import de.aetherion.items.recipe.RecipeBookCrafter;
import de.aetherion.items.recipe.RecipeCategory;
import de.aetherion.items.recipe.RecipeDefinition;
import de.aetherion.items.recipe.RecipeManager;
import de.aetherion.items.recipe.UnlockRequirement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecipeBookGUI {

    private static final String CATEGORY_TITLE = "§8Aetherion Recipe Book";
    private static final String RECIPE_LIST_TITLE = "§8Aetherion Recipes";
    private static final String RECIPE_DETAIL_TITLE = "§8Recipe Details";

    private final RecipeManager recipeManager;

    public RecipeBookGUI(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    public void open(Player player) {
        openCategories(player);
    }

    public void openCategories(Player player) {
        syncOwned(player);

        Inventory inventory = Bukkit.createInventory(
                new RecipeBookHolder(GUIType.CATEGORIES, null, null),
                RecipeBookLayout.SIZE,
                CATEGORY_TITLE
        );

        fillBackground(inventory);

        inventory.setItem(
                RecipeBookLayout.HEADER_SLOT,
                createSimpleItem(
                        Material.KNOWLEDGE_BOOK,
                        "§6Aetherion Recipe Book",
                        List.of(
                                "",
                                "§7Choose a category."
                        )
                )
        );

        RecipeCategory[] categories = RecipeCategory.values();

        for (int i = 0; i < categories.length && i < RecipeBookLayout.CATEGORY_SLOTS.length; i++) {
            RecipeCategory category = categories[i];

            inventory.setItem(
                    RecipeBookLayout.CATEGORY_SLOTS[i],
                    createCategoryItem(category, isCategoryUnlocked(player, category))
            );
        }

        inventory.setItem(
                RecipeBookLayout.BACK_SLOT,
                de.aetherion.items.util.ManagerNav.button()
        );

        player.openInventory(inventory);
        playBookSound(player);
    }

    public void openCategory(Player player, RecipeCategory category) {
        openCategory(player, category, 1);
    }

    public void openCategory(Player player, RecipeCategory category, int page) {
        if (category == null) {
            return;
        }

        syncOwned(player);

        if (!isCategoryUnlocked(player, category)) {
            player.sendMessage("§cThis category is still locked.");
            playFailSound(player);
            return;
        }

        if (category == RecipeCategory.ARMOR
                || category == RecipeCategory.MINING
                || category == RecipeCategory.COMBAT
                || category == RecipeCategory.FARMING
                || category == RecipeCategory.FORAGING
                || category == RecipeCategory.FISHING
                || category == RecipeCategory.AETHER_MOBS) {
            openGearSetCategory(player, category, page);
            return;
        }

        List<RecipeDefinition> recipes = getRecipes(player, category);

        int pageSize = RecipeBookLayout.RECIPE_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil(recipes.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));

        Inventory inventory = Bukkit.createInventory(
                new RecipeBookHolder(GUIType.RECIPE_LIST, category, null, currentPage),
                RecipeBookLayout.SIZE,
                RECIPE_LIST_TITLE
        );

        fillBackground(inventory);

        inventory.setItem(
                RecipeBookLayout.HEADER_SLOT,
                createCategoryIcon(
                        category,
                        "§6" + category.getIcon() + " " + category.getDisplayName(),
                        totalPages > 1
                                ? List.of("", "§7Recipes in this category", "§eClick §7to return to categories.", "", "§7Page §f" + currentPage + "§7/§f" + totalPages)
                                : List.of("", "§7Recipes in this category", "§eClick §7to return to categories.")
                )
        );

        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, recipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            RecipeDefinition recipe = recipes.get(i);
            inventory.setItem(
                    RecipeBookLayout.RECIPE_SLOTS[i - startIndex],
                    createRecipeItem(recipe, recipe.isUnlocked(player))
            );
        }

        inventory.setItem(
                RecipeBookLayout.BACK_SLOT,
                createSimpleItem(Material.ARROW, "§eBack", List.of("§7Return to categories"))
        );

        if (totalPages > 1 && currentPage > 1) {
            inventory.setItem(
                    RecipeBookLayout.PREV_PAGE_SLOT,
                    createSimpleItem(
                            Material.ARROW,
                            "§ePrevious Page",
                            List.of("", "§7Page " + (currentPage - 1) + " / " + totalPages)
                    )
            );
        }

        if (totalPages > 1 && currentPage < totalPages) {
            inventory.setItem(
                    RecipeBookLayout.NEXT_PAGE_SLOT,
                    createSimpleItem(
                            Material.ARROW,
                            "§eNext Page",
                            List.of("", "§7Page " + (currentPage + 1) + " / " + totalPages)
                    )
            );
        }

        player.openInventory(inventory);
        playBookSound(player);
    }

    private void openGearSetCategory(Player player, RecipeCategory category, int page) {
        List<List<RecipeDefinition>> sets = groupedGearSets(player, category);
        int pageSize = RecipeBookLayout.ARMOR_SETS_PER_PAGE;
        int totalPages = Math.max(1, (int) Math.ceil(sets.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));

        Inventory inventory = Bukkit.createInventory(
                new RecipeBookHolder(GUIType.RECIPE_LIST, category, null, currentPage),
                RecipeBookLayout.SIZE,
                RECIPE_LIST_TITLE
        );

        fillBackground(inventory);

        List<String> headerLore = new ArrayList<>(gearCategoryLore(category));
        if (totalPages > 1) {
            headerLore.add("");
            headerLore.add("§7Page §f" + currentPage + "§7/§f" + totalPages);
        }

        inventory.setItem(
                RecipeBookLayout.HEADER_SLOT,
                createSimpleItem(
                        getCategoryMaterial(category),
                        "§6" + category.getIcon() + " " + category.getDisplayName(),
                        headerLore
                )
        );

        int start = (currentPage - 1) * pageSize;
        for (int column = 0; column < RecipeBookLayout.ARMOR_COLUMNS; column++) {
            int setIndex = start + column;
            if (setIndex >= sets.size()) {
                break;
            }
            List<RecipeDefinition> pieces = sets.get(setIndex);
            for (int piece = 0; piece < pieces.size() && piece < RecipeBookLayout.ARMOR_PIECES; piece++) {
                RecipeDefinition recipe = pieces.get(piece);
                inventory.setItem(
                        RecipeBookLayout.armorSlot(column, piece),
                        createRecipeItem(recipe, recipe.isUnlocked(player))
                );
            }
        }

        inventory.setItem(
                RecipeBookLayout.BACK_SLOT,
                createSimpleItem(Material.ARROW, "§eBack", List.of("§7Return to categories"))
        );

        if (totalPages > 1 && currentPage > 1) {
            inventory.setItem(
                    RecipeBookLayout.PREV_PAGE_SLOT,
                    createSimpleItem(
                            Material.ARROW,
                            "§ePrevious Page",
                            List.of("", "§7Page " + (currentPage - 1) + " / " + totalPages)
                    )
            );
        }

        if (totalPages > 1 && currentPage < totalPages) {
            inventory.setItem(
                    RecipeBookLayout.NEXT_PAGE_SLOT,
                    createSimpleItem(
                            Material.ARROW,
                            "§eNext Page",
                            List.of("", "§7Page " + (currentPage + 1) + " / " + totalPages)
                    )
            );
        }

        player.openInventory(inventory);
        playBookSound(player);
    }

    public RecipeDefinition getArmorRecipe(Player player, int page, int slot) {
        return getGearRecipe(player, RecipeCategory.ARMOR, page, slot);
    }

    public RecipeDefinition getGearRecipe(Player player, RecipeCategory category, int page, int slot) {
        int column = RecipeBookLayout.armorColumnFromSlot(slot);
        int piece = RecipeBookLayout.armorPieceFromSlot(slot);
        if (column < 0 || piece < 0 || category == null) {
            return null;
        }
        List<List<RecipeDefinition>> sets = groupedGearSets(player, category);
        int setIndex = (Math.max(1, page) - 1) * RecipeBookLayout.ARMOR_SETS_PER_PAGE + column;
        if (setIndex < 0 || setIndex >= sets.size()) {
            return null;
        }
        List<RecipeDefinition> pieces = sets.get(setIndex);
        if (piece >= pieces.size()) {
            return null;
        }
        return pieces.get(piece);
    }

    public boolean isGearSetCategory(RecipeCategory category) {
        return category == RecipeCategory.ARMOR
                || category == RecipeCategory.MINING
                || category == RecipeCategory.COMBAT
                || category == RecipeCategory.FARMING
                || category == RecipeCategory.FORAGING
                || category == RecipeCategory.FISHING
                || category == RecipeCategory.AETHER_MOBS;
    }

    public void openRecipe(Player player, RecipeDefinition recipe) {
        openRecipe(player, recipe, 1);
    }

    public void openRecipe(Player player, RecipeDefinition recipe, int page) {
        if (recipe == null) {
            return;
        }

        if (!recipe.isUnlocked(player)) {
            player.sendMessage("§cThis recipe is still locked.");
            playFailSound(player);
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new RecipeBookHolder(GUIType.RECIPE_DETAIL, recipe.getCategory(), recipe, page),
                RecipeBookLayout.SIZE,
                RECIPE_DETAIL_TITLE
        );

        fillBackground(inventory);

        inventory.setItem(
                RecipeBookLayout.HEADER_SLOT,
                createSimpleItem(
                        Material.KNOWLEDGE_BOOK,
                        "§6" + getDisplayName(recipe.getResult()),
                        List.of("", "§7Rarity: §f" + recipe.getRarity().name())
                )
        );

        List<String> shape = recipe.getShape();

        for (int row = 0; row < 3; row++) {
            String currentRow = row < shape.size() ? shape.get(row) : "";

            for (int column = 0; column < 3; column++) {
                int index = row * 3 + column;
                char symbol = column < currentRow.length() ? currentRow.charAt(column) : ' ';

                if (symbol == ' ' || !recipe.getIngredients().containsKey(symbol)) {
                    inventory.setItem(
                            RecipeBookLayout.CRAFTING_GRID_SLOTS[index],
                            createSimpleItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8", List.of())
                    );
                    continue;
                }

                inventory.setItem(
                        RecipeBookLayout.CRAFTING_GRID_SLOTS[index],
                        createIngredientItem(recipe.getIngredients().get(symbol), symbol)
                );
            }
        }

        inventory.setItem(
                RecipeBookLayout.ARROW_SLOT,
                createSimpleItem(Material.ARROW, "§6➜", List.of("§7Crafting result"))
        );

        inventory.setItem(
                RecipeBookLayout.RESULT_SLOT,
                createResultItem(recipe.getResult())
        );

        inventory.setItem(
                RecipeBookLayout.INFO_SLOT,
                createCraftButton(player, recipe)
        );

        inventory.setItem(
                RecipeBookLayout.BACK_SLOT,
                createSimpleItem(Material.ARROW, "§eBack", List.of("§7Return to recipes"))
        );

        player.openInventory(inventory);
        playBookSound(player);
    }

    public List<RecipeDefinition> getRecipes(Player player, RecipeCategory category) {
        List<RecipeDefinition> recipes = new ArrayList<>(recipeManager.getRecipesByCategory(category));

        if (category == RecipeCategory.ARMOR
                || category == RecipeCategory.MINING
                || category == RecipeCategory.COMBAT
                || category == RecipeCategory.FARMING
                || category == RecipeCategory.FORAGING
                || category == RecipeCategory.FISHING
                || category == RecipeCategory.AETHER_MOBS) {
            recipes.sort(this::compareArmor);
        } else if (category == RecipeCategory.RESOURCES) {
            recipes.sort(this::compareResources);
        }

        return recipes;
    }

    private int compareResources(RecipeDefinition first, RecipeDefinition second) {
        int o1 = resourceOrder(first);
        int o2 = resourceOrder(second);
        if (o1 != o2) {
            return Integer.compare(o1, o2);
        }
        // compressed → compacted → quarry within the same material
        int k1 = resourceKindRank(first);
        int k2 = resourceKindRank(second);
        if (k1 != k2) {
            return Integer.compare(k1, k2);
        }
        return first.getId().compareToIgnoreCase(second.getId());
    }

    private int resourceOrder(RecipeDefinition recipe) {
        if (recipe == null || recipe.getId() == null) {
            return 9999;
        }
        String id = recipe.getId().toLowerCase(Locale.ROOT);
        for (de.aetherion.items.economy.CompressedResource resource
                : de.aetherion.items.economy.CompressedResource.contentOrdered()) {
            String key = resource.key();
            if (id.equals("compressed_" + key)
                    || id.equals("compacted_" + key)
                    || id.equals("quarry_" + key)
                    || id.equals(key + "_quarry")) {
                return resource.displayOrder();
            }
        }
        if (id.contains("quarry_core") || id.contains("compressor") || id.contains("compactor")
                || id.contains("quarry_core_shard")) {
            return 5;
        }
        return 8000;
    }

    private int resourceKindRank(RecipeDefinition recipe) {
        String id = recipe.getId().toLowerCase(Locale.ROOT);
        if (id.startsWith("compressed_")) {
            return 0;
        }
        if (id.startsWith("compacted_")) {
            return 1;
        }
        if (id.startsWith("quarry_") || id.endsWith("_quarry")) {
            return 2;
        }
        return 3;
    }

    public boolean isCategoryUnlocked(Player player, RecipeCategory category) {
        if (category == RecipeCategory.AETHER_MOBS) {
            return new AetherMobsUnlockRequirement().isUnlocked(player);
        }

        return true;
    }

    private static final String[] SET_ORDER = {
            "combat_", "mining_", "farming_", "foraging_", "fishing_",
            "rotten_", "bone_", "webweave_",
            "ironhide_", "healer_",
            "catcher_", "diving_"
    };

    private int compareArmor(RecipeDefinition first, RecipeDefinition second) {
        int g1 = armorSetGroup(first);
        int g2 = armorSetGroup(second);
        if (g1 != g2) return Integer.compare(g1, g2);
        int t1 = armorTier(first);
        int t2 = armorTier(second);
        if (t1 != t2) return Integer.compare(t1, t2);
        return Integer.compare(armorSlotOrder(first), armorSlotOrder(second));
    }

    private int armorSetGroup(RecipeDefinition recipe) {
        String id = recipe != null ? recipe.getId().toLowerCase() : "";
        for (int i = 0; i < SET_ORDER.length; i++) {
            if (id.startsWith(SET_ORDER[i])) return i;
        }
        return SET_ORDER.length;
    }

    private int armorTier(RecipeDefinition recipe) {
        String id = recipe != null ? recipe.getId() : "";
        int last = id.lastIndexOf('_');
        if (last > 0 && last < id.length() - 1) {
            String tail = id.substring(last + 1);
            if (tail.length() == 1 && Character.isDigit(tail.charAt(0))) {
                return tail.charAt(0) - '0';
            }
        }
        return 1;
    }

    private int armorSlotOrder(RecipeDefinition recipe) {
        String id = recipe != null ? recipe.getId().toLowerCase() : "";
        if (id.startsWith("catch_sphere_")) {
            if (id.endsWith("_common")) return 0;
            if (id.endsWith("_rare")) return 1;
            if (id.endsWith("_epic")) return 2;
            return 3;
        }
        if (id.contains("helmet")) return 0;
        if (id.contains("chestplate")) return 1;
        if (id.contains("leggings")) return 2;
        if (id.contains("boots")) return 3;
        if (isSetTool(recipe)) return 4;
        return 5;
    }

    private List<String> gearCategoryLore(RecipeCategory category) {
        if (category == RecipeCategory.AETHER_MOBS) {
            return List.of(
                    "",
                    "§7Catch spheres, common → epic.",
                    "§7Catcher set: helmet → boots."
            );
        }
        if (category == RecipeCategory.MINING || category == RecipeCategory.COMBAT) {
            return List.of(
                    "",
                    "§7Helmet → boots, top to bottom.",
                    "§7Specials start on the next page."
            );
        }
        return List.of(
                "",
                "§7Helmet → boots, top to bottom.",
                "§7Set tools sit beside their tier."
        );
    }

    private List<List<RecipeDefinition>> groupedArmorSets(Player player) {
        return groupedGearSets(player, RecipeCategory.ARMOR);
    }

    private List<List<RecipeDefinition>> groupedGearSets(Player player, RecipeCategory category) {
        Map<String, List<RecipeDefinition>> grouped = new LinkedHashMap<>();
        Map<String, List<RecipeDefinition>> toolsBySet = new LinkedHashMap<>();
        List<RecipeDefinition> orphanTools = new ArrayList<>();
        List<RecipeDefinition> recipes = new ArrayList<>(getRecipes(player, category));

        for (RecipeDefinition recipe : recipes) {
            if (isSetTool(recipe)) {
                continue;
            }
            grouped.computeIfAbsent(armorSetKey(recipe), ignored -> new ArrayList<>()).add(recipe);
        }
        for (RecipeDefinition recipe : recipes) {
            if (!isSetTool(recipe)) {
                continue;
            }
            String key = armorSetKey(recipe);
            if (grouped.containsKey(key) || looksLikeTieredSetKey(key)) {
                toolsBySet.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
            } else {
                orphanTools.add(recipe);
            }
        }

        List<String> keys = new ArrayList<>(grouped.keySet());
        for (String key : toolsBySet.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparingInt(this::setKeyRank));

        if (category == RecipeCategory.MINING || category == RecipeCategory.COMBAT) {
            return groupedArmorThenSpecials(grouped, toolsBySet, orphanTools, keys);
        }

        List<List<RecipeDefinition>> sets = new ArrayList<>();
        for (String key : keys) {
            List<RecipeDefinition> pieces = grouped.getOrDefault(key, List.of());
            if (!pieces.isEmpty()) {
                List<RecipeDefinition> sorted = new ArrayList<>(pieces);
                sorted.sort(Comparator.comparingInt(this::armorSlotOrder));
                if (sorted.size() > RecipeBookLayout.ARMOR_PIECES) {
                    sorted = new ArrayList<>(sorted.subList(0, RecipeBookLayout.ARMOR_PIECES));
                }
                sets.add(sorted);
            }
            List<RecipeDefinition> tools = new ArrayList<>(toolsBySet.getOrDefault(key, List.of()));
            tools.sort(this::compareArmor);
            for (RecipeDefinition tool : tools) {
                sets.add(List.of(tool));
            }
        }
        orphanTools.sort(this::compareArmor);
        for (RecipeDefinition tool : orphanTools) {
            sets.add(List.of(tool));
        }
        return sets;
    }

    /**
     * Mining / Combat: T1–T5 armor columns first (like Farming), then a new page of
     * pickaxes/swords and specials.
     */
    private List<List<RecipeDefinition>> groupedArmorThenSpecials(
            Map<String, List<RecipeDefinition>> grouped,
            Map<String, List<RecipeDefinition>> toolsBySet,
            List<RecipeDefinition> orphanTools,
            List<String> keys
    ) {
        List<List<RecipeDefinition>> armor = new ArrayList<>();
        List<List<RecipeDefinition>> tools = new ArrayList<>();
        List<List<RecipeDefinition>> specials = new ArrayList<>();

        for (String key : keys) {
            List<RecipeDefinition> pieces = new ArrayList<>(grouped.getOrDefault(key, List.of()));
            pieces.sort(Comparator.comparingInt(this::armorSlotOrder));
            boolean armorSet = !pieces.isEmpty() && pieces.stream().anyMatch(this::isArmorPiece);
            if (armorSet) {
                if (pieces.size() > RecipeBookLayout.ARMOR_PIECES) {
                    pieces = new ArrayList<>(pieces.subList(0, RecipeBookLayout.ARMOR_PIECES));
                }
                armor.add(pieces);
            } else if (!pieces.isEmpty()) {
                specials.add(pieces.size() > RecipeBookLayout.ARMOR_PIECES
                        ? new ArrayList<>(pieces.subList(0, RecipeBookLayout.ARMOR_PIECES))
                        : pieces);
            }
            List<RecipeDefinition> setTools = new ArrayList<>(toolsBySet.getOrDefault(key, List.of()));
            setTools.sort(this::compareArmor);
            for (RecipeDefinition tool : setTools) {
                tools.add(List.of(tool));
            }
        }
        orphanTools.sort(this::compareArmor);
        for (RecipeDefinition tool : orphanTools) {
            if (isSetTool(tool)) {
                tools.add(List.of(tool));
            } else {
                specials.add(List.of(tool));
            }
        }

        List<List<RecipeDefinition>> sets = new ArrayList<>(armor);
        if (!tools.isEmpty() || !specials.isEmpty()) {
            while (!sets.isEmpty() && sets.size() % RecipeBookLayout.ARMOR_SETS_PER_PAGE != 0) {
                sets.add(List.of());
            }
            sets.addAll(tools);
            sets.addAll(specials);
        }
        return sets;
    }

    private boolean isArmorPiece(RecipeDefinition recipe) {
        if (recipe == null || recipe.getId() == null) {
            return false;
        }
        String id = recipe.getId().toLowerCase();
        return id.contains("helmet")
                || id.contains("chestplate")
                || id.contains("leggings")
                || id.contains("boots");
    }

    private static boolean looksLikeTieredSetKey(String key) {
        return key != null && key.matches("^(combat|mining|farming|foraging|fishing|catcher)_\\d+$");
    }

    private boolean isSetTool(RecipeDefinition recipe) {
        if (recipe == null || recipe.getId() == null) {
            return false;
        }
        String id = recipe.getId().toLowerCase();
        return id.contains("_hoe")
                || id.contains("_axe")
                || id.contains("_rod")
                || id.contains("_pickaxe")
                || id.contains("_sword")
                || id.contains("_gaff");
    }

    private String armorSetKey(RecipeDefinition recipe) {
        String id = recipe != null ? recipe.getId().toLowerCase() : "unknown";
        int tier = armorTier(recipe);
        if (id.startsWith("combat_")) {
            return "combat_" + tier;
        }
        if (id.startsWith("mining_")) {
            return "mining_" + tier;
        }
        if (id.startsWith("farming_")) {
            return "farming_" + tier;
        }
        if (id.startsWith("foraging_")) {
            return "foraging_" + tier;
        }
        if (id.startsWith("fishing_")) {
            return "fishing_" + tier;
        }
        if (id.startsWith("catch_sphere_")) {
            return "catch_sphere";
        }
        if (id.startsWith("catcher_")) {
            return "catcher_" + tier;
        }
        if (id.startsWith("diving_")) {
            return "diving";
        }
        if (id.startsWith("rotten_")) {
            return "rotten";
        }
        if (id.startsWith("bone_")) {
            return "bone";
        }
        if (id.startsWith("webweave_")) {
            return "webweave";
        }
        if (id.startsWith("ironhide_")) {
            return "ironhide";
        }
        if (id.equals("compressed_oak_chestplate") || id.equals("redstone_infused_boots")
                || id.equals("compacted_diamond_chestplate") || id.equals("emerald_crown")) {
            return id;
        }
        if (id.startsWith("healer_")) {
            return "healer";
        }
        if (id.startsWith("aetherion_")) {
            return "aetherion";
        }
        return id;
    }

    private int setKeyRank(String key) {
        String[] order = {
                "combat_1", "combat_2", "combat_3", "combat_4", "combat_5",
                "ironhide", "healer",
                "mining_1", "mining_2", "mining_3", "mining_4", "mining_5",
                "farming_1", "farming_2", "farming_3", "farming_4", "farming_5",
                "foraging_1", "foraging_2", "foraging_3", "foraging_4", "foraging_5",
                "fishing_1", "fishing_2", "fishing_3", "fishing_4", "fishing_5",
                "diving",
                "catch_sphere",
                "catcher_1", "catcher_2", "catcher_3",
                "rotten", "bone", "webweave", "aetherion"
        };
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(key)) {
                return i;
            }
        }
        return order.length;
    }

    private ItemStack createCategoryItem(RecipeCategory category, boolean unlocked) {
        if (!unlocked) {
            UnlockRequirement requirement = new AetherMobsUnlockRequirement();
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§c§lLOCKED");
            lore.add("");

            String unlockText = requirement.getDisplayText();

            if (unlockText != null && !unlockText.isBlank()) {
                lore.add("§7" + unlockText);
            } else {
                lore.add("§7This category is");
                lore.add("§7not available yet.");
            }

            lore.add("");
            lore.add("§8Unlock through progression.");

            return createSimpleItem(
                    Material.BARRIER,
                    "§c§l" + category.getIcon() + " " + category.getDisplayName(),
                    lore
            );
        }

        return createCategoryIcon(
                category,
                "§6" + category.getIcon() + " " + category.getDisplayName(),
                List.of("", "§7Click to view recipes.")
        );
    }

    private ItemStack createCategoryIcon(RecipeCategory category, String name, List<String> lore) {
        if (category == RecipeCategory.RESOURCES) {
            // Packed cobble crate head — reads as "resources" without a blank player skull.
            ItemStack icon = TexturedHeads.hashed("09b6af55f2f3bbd5b23387f43b7c4a84676b86b2892f8f4cc0a9f81377c79");
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            return icon;
        }
        return createSimpleItem(getCategoryMaterial(category), name, lore);
    }

    private ItemStack createRecipeItem(RecipeDefinition recipe, boolean unlocked) {
        if (!unlocked) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§c§lLOCKED");
            lore.add("");

            String unlockText = recipe.getUnlockDisplayText();

            if (unlockText != null && !unlockText.isBlank()) {
                lore.add("§7" + unlockText);
            }

            lore.add("");
            lore.add("§8Complete the required");
            lore.add("§8progression to unlock this.");

            return createSimpleItem(Material.BARRIER, "§c§l???", lore);
        }

        ItemStack result = recipe.getResult();
        ItemStack displayItem = result.clone();
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6" + getDisplayName(result));
            meta.setLore(List.of("", "§7Rarity: §f" + recipe.getRarity().name(), "", "§eClick to view recipe."));
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private ItemStack createIngredientItem(ItemStack ingredient, char symbol) {
        if (ingredient == null) {
            return createSimpleItem(
                    Material.BARRIER,
                    "§cUnknown Ingredient",
                    List.of("§7Symbol: §f" + symbol)
            );
        }

        ItemStack item = ingredient.clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String displayName = meta.hasDisplayName()
                    ? meta.getDisplayName()
                    : "§f" + formatMaterialName(ingredient.getType());

            meta.setDisplayName(displayName);
            meta.setLore(List.of("§7Symbol: §f" + symbol));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createResultItem(ItemStack result) {
        ItemStack item = result.clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§a§lCRAFTING RESULT");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCraftButton(Player player, RecipeDefinition recipe) {
        ItemManager itemManager = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().getItemManager();
        boolean ready = RecipeBookCrafter.canCraft(player, recipe, itemManager);
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (ready) {
            lore.add("§7You have all ingredients.");
            lore.add("§aClick §7to craft now.");
            return createSimpleItem(Material.CRAFTING_TABLE, "§a§lInsta Craft", lore);
        }

        lore.add("§7Missing ingredients:");
        List<String> missing = RecipeBookCrafter.missingIngredients(player, recipe, itemManager);
        if (missing.isEmpty()) {
            lore.add("§8Unknown");
        } else {
            int shown = 0;
            for (String line : missing) {
                lore.add("§c- " + line);
                shown++;
                if (shown >= 6) {
                    lore.add("§8...");
                    break;
                }
            }
        }
        lore.add("");
        lore.add("§8Gather the materials first.");
        return createSimpleItem(Material.BARRIER, "§c§lInsta Craft", lore);
    }

    private void syncOwned(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().syncOwnedRecipes(player);
        }
    }

    private ItemStack createSimpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, "§8", List.of());
        ItemStack border = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, "§8", List.of());
        int rows = inventory.getSize() / 9;

        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int column = i % 9;
            boolean edge = row == 0 || row == rows - 1 || column == 0 || column == 8;
            inventory.setItem(i, edge ? border : filler);
        }
    }

    private Material getCategoryMaterial(RecipeCategory category) {
        return switch (category) {
            case MINING -> Material.IRON_PICKAXE;
            case RESOURCES -> Material.NETHERITE_BLOCK;
            case COMBAT -> Material.IRON_SWORD;
            case ARMOR -> Material.IRON_CHESTPLATE;
            case FARMING -> Material.IRON_HOE;
            case FORAGING -> Material.IRON_AXE;
            case FISHING -> Material.FISHING_ROD;
            case BOOSTERS -> Material.DIAMOND_BLOCK;
            case CHARMS -> Material.AMETHYST_SHARD;
            case AETHER_MOBS -> Material.LEAD;
        };
    }

    private String getDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        return formatMaterialName(item != null ? item.getType() : Material.AIR);
    }

    private String formatMaterialName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append(" ");
            }

            result.append(Character.toUpperCase(part.charAt(0)));

            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }

        return result.toString();
    }

    private void playBookSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    private void playFailSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    public enum GUIType {
        CATEGORIES,
        RECIPE_LIST,
        RECIPE_DETAIL
    }

    public static class RecipeBookHolder implements InventoryHolder {

        private final GUIType type;
        private final RecipeCategory category;
        private final RecipeDefinition recipe;
        private final int page;

        public RecipeBookHolder(GUIType type, RecipeCategory category, RecipeDefinition recipe) {
            this(type, category, recipe, 1);
        }

        public RecipeBookHolder(GUIType type, RecipeCategory category, RecipeDefinition recipe, int page) {
            this.type = type;
            this.category = category;
            this.recipe = recipe;
            this.page = Math.max(1, page);
        }

        public GUIType getType() {
            return type;
        }

        public RecipeCategory getCategory() {
            return category;
        }

        public RecipeDefinition getRecipe() {
            return recipe;
        }

        public int getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
