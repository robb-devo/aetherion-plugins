package de.aetherion.items.guide;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.recipe.RecipeDefinition;
import de.aetherion.items.recipe.RecipeManager;
import de.aetherion.items.skill.AetherionLevel;
import de.aetherion.items.skill.SkillService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared guide logic for /guide and Discord (via DiscordSRV hook).
 * Tutorial tips come from AetherionQuests when present; otherwise harbour fallback.
 */
public final class GuideAdvice {

    private GuideAdvice() {
    }

    public static final class Result {
        public final String header;
        public final List<String> tips;
        public final boolean tutorial;

        public Result(String header, List<String> tips, boolean tutorial) {
            this.header = header;
            this.tips = tips;
            this.tutorial = tutorial;
        }
    }

    public static Result next(Player player) {
        SkillService skills = AetherionItems.getInstance() == null
                ? null : AetherionItems.getInstance().getSkills();
        int level = skills == null ? 1 : skills.accountLevel(player);
        String header = "You are " + strip(AetherionLevel.tag(level)) + " "
                + strip(AetherionLevel.coloredTitle(level));

        List<String> tutorial = questGuideTips(player);
        if (tutorial != null && !tutorial.isEmpty()) {
            return new Result(header, stripAll(tutorial), true);
        }

        List<String> tips = new ArrayList<>();
        ProgressionService progress = AetherionItems.getInstance() == null
                ? null : AetherionItems.getInstance().progress();
        if (progress != null) {
            if (!progress.skills(player)) {
                tips.add("Talk to Miss Ledger — unlocks Skills in the Manager.");
            }
            if (!progress.recipeBook(player)) {
                tips.add("Visit the Market stall / Craftsman — unlocks crafting & /atrecipes.");
            }
            if (!progress.anvil(player)) {
                tips.add("Talk to Temper (Booster Tutor) — unlocks the anvil.");
            }
            if (!progress.bazaar(player)) {
                tips.add("Visit the Trader once — unlocks Bazaar / AH.");
            }
            if (!progress.pets(player)) {
                tips.add("Find Lark for pet spheres when you're ready.");
            }
            if (!progress.spawns(player)) {
                tips.add("Get a spawn unlocker so more of the map opens.");
            }
            if (!progress.island(player)) {
                tips.add(strip(progress.islandHint()));
            } else if (!progress.guild(player)) {
                tips.add(strip(progress.guildHint()));
            }
        }
        if (tips.isEmpty()) {
            tips.add("Explore dungeons, upgrade gear, and keep leveling skills.");
            tips.add("Use /atrecipes for crafting and /aetherion for the Manager.");
        }
        return new Result(header, tips, false);
    }

    public static Result nextOnlineOrNull(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return null;
        }
        return next(player);
    }

    public static List<String> craftLines(String query, int limit) {
        List<String> lines = new ArrayList<>();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            lines.add("Recipe data isn't loaded.");
            return lines;
        }
        RecipeManager recipes = plugin.getRecipeManager();
        if (recipes == null || recipes.getRecipeCount() == 0) {
            lines.add("Recipe data isn't loaded.");
            return lines;
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<RecipeDefinition> hits = new ArrayList<>();
        for (RecipeDefinition recipe : recipes.getAllRecipes()) {
            String id = recipe.getId() == null ? "" : recipe.getId().toLowerCase(Locale.ROOT);
            String name = displayName(recipe.getResult()).toLowerCase(Locale.ROOT);
            if (id.contains(q) || name.contains(q)) {
                hits.add(recipe);
            }
        }
        if (hits.isEmpty()) {
            lines.add("No recipe matching \"" + query + "\". Try a shorter name or /atrecipes in-game.");
            return lines;
        }
        for (RecipeDefinition recipe : hits.stream().limit(limit).collect(Collectors.toList())) {
            lines.add("**" + displayName(recipe.getResult()) + "** (`" + recipe.getId() + "`)");
            Map<String, Integer> counts = countIngredients(recipe);
            if (counts.isEmpty()) {
                lines.add("· ingredients: see /atrecipes");
            } else {
                counts.forEach((name, amount) -> lines.add("· x" + amount + " " + name));
            }
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<String> questGuideTips(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AetherionQuests")) {
            return List.of("Talk to **Egon** on the pier at Anker Harbour — start there.");
        }
        try {
            Class<?> gate = Class.forName("de.aetherion.quests.util.QuestStoryGate");
            Method m = gate.getMethod("guideTips", Player.class);
            Object raw = m.invoke(null, player);
            if (raw instanceof List<?> list) {
                return (List<String>) list;
            }
        } catch (Throwable ignored) {
            // Quests API missing / old jar
        }
        return List.of();
    }

    private static Map<String, Integer> countIngredients(RecipeDefinition recipe) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<Character, ItemStack> map = recipe.getIngredients();
        List<String> shape = recipe.getShape();
        if (shape == null || shape.isEmpty()) {
            for (ItemStack ing : map.values()) {
                if (ing == null) {
                    continue;
                }
                counts.merge(displayName(ing), Math.max(1, ing.getAmount()), Integer::sum);
            }
            return counts;
        }
        for (String row : shape) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == ' ' || c == '.') {
                    continue;
                }
                ItemStack ing = map.get(c);
                if (ing == null) {
                    continue;
                }
                counts.merge(displayName(ing), Math.max(1, ing.getAmount()), Integer::sum);
            }
        }
        return counts;
    }

    private static String displayName(ItemStack stack) {
        if (stack == null) {
            return "?";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return strip(meta.getDisplayName());
        }
        String name = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    public static String strip(String colored) {
        return colored == null ? "" : colored.replaceAll("§.", "");
    }

    private static List<String> stripAll(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            out.add(strip(s));
        }
        return out;
    }
}
