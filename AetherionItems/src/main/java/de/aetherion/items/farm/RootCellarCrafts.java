package de.aetherion.items.farm;

import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Root Cellar offers — Cellar (refined mill) + Pantry (pets).
 * Costs use compressed / compacted / refined pantry tiers only.
 */
public final class RootCellarCrafts {

    public enum Category {
        CELLAR,
        PANTRY
    }

    public record Cost(ItemStack sample, int amount) {
    }

    public record Offer(
            String id,
            Category category,
            String title,
            List<String> description,
            Supplier<ItemStack> result,
            List<Cost> costs
    ) {
    }

    private RootCellarCrafts() {
    }

    public static List<Offer> of(Category category) {
        List<Offer> out = new ArrayList<>();
        for (Offer offer : all()) {
            if (offer.category() == category) {
                out.add(offer);
            }
        }
        return out;
    }

    public static List<Offer> all() {
        List<Offer> list = new ArrayList<>();

        // Cellar — Compacted → Refined (1:1)
        list.add(new Offer(
                "refine_wheat",
                Category.CELLAR,
                "§6Pantry Wheat",
                List.of("§7Refine Compacted Wheat.", "§8Sells for more."),
                () -> CompressedResource.WHEAT.refined(),
                List.of(new Cost(CompressedResource.WHEAT.compacted(), 1))
        ));
        list.add(new Offer(
                "refine_carrot",
                Category.CELLAR,
                "§6Pantry Carrot",
                List.of("§7Refine Compacted Carrot.", "§8Sells for more."),
                () -> CompressedResource.CARROT.refined(),
                List.of(new Cost(CompressedResource.CARROT.compacted(), 1))
        ));
        list.add(new Offer(
                "refine_potato",
                Category.CELLAR,
                "§6Pantry Potato",
                List.of("§7Refine Compacted Potato.", "§8Sells for more."),
                () -> CompressedResource.POTATO.refined(),
                List.of(new Cost(CompressedResource.POTATO.compacted(), 1))
        ));

        // Pantry — pet loop (refined caps at 1–2; lower tiers stay light)
        list.add(new Offer(
                "legendary_sphere",
                Category.PANTRY,
                "§6Legendary Catch Sphere",
                List.of("§7Top farm catch sphere.", "§8Two refined + one Epic sphere."),
                () -> createSphere("legendary"),
                List.of(
                        new Cost(CompressedResource.WHEAT.refined(), 1),
                        new Cost(CompressedResource.CARROT.refined(), 1),
                        new Cost(createSphere("epic"), 1)
                )
        ));
        list.add(new Offer(
                "pet_exp_1",
                Category.PANTRY,
                "§aPet EXP Treat I",
                List.of("§7+180 pet EXP.", "§8Compressed pantry."),
                () -> createTreat(1),
                List.of(
                        new Cost(CompressedResource.WHEAT.compressed(), 2),
                        new Cost(CompressedResource.CARROT.compressed(), 1),
                        new Cost(CompressedResource.POTATO.compressed(), 1)
                )
        ));
        list.add(new Offer(
                "pet_exp_2",
                Category.PANTRY,
                "§bPet EXP Treat II",
                List.of("§7+850 pet EXP.", "§8Compacted pantry."),
                () -> createTreat(2),
                List.of(
                        new Cost(CompressedResource.WHEAT.compacted(), 1),
                        new Cost(CompressedResource.CARROT.compacted(), 1)
                )
        ));
        list.add(new Offer(
                "pet_exp_3",
                Category.PANTRY,
                "§dPet EXP Treat III",
                List.of("§7+4200 pet EXP.", "§8Refined pantry."),
                () -> createTreat(3),
                List.of(
                        new Cost(CompressedResource.WHEAT.refined(), 1),
                        new Cost(CompressedResource.CARROT.refined(), 1)
                )
        ));
        return list;
    }

    public static Offer byId(String id) {
        if (id == null) {
            return null;
        }
        for (Offer offer : all()) {
            if (offer.id().equalsIgnoreCase(id)) {
                return offer;
            }
        }
        return null;
    }

    /** Cellar refine crop for mill ritual, or null if not a refine offer. */
    public static CompressedResource refineCrop(Offer offer) {
        if (offer == null || offer.category() != Category.CELLAR) {
            return null;
        }
        return switch (offer.id()) {
            case "refine_wheat" -> CompressedResource.WHEAT;
            case "refine_carrot" -> CompressedResource.CARROT;
            case "refine_potato" -> CompressedResource.POTATO;
            default -> null;
        };
    }

    public static boolean canAfford(Player player, Offer offer, ItemManager manager) {
        if (player == null || offer == null) {
            return false;
        }
        for (Cost cost : offer.costs()) {
            if (countMatching(player.getInventory(), cost.sample(), manager) < cost.amount()) {
                return false;
            }
        }
        return true;
    }

    public static boolean consumeCosts(Player player, Offer offer, ItemManager manager) {
        if (!canAfford(player, offer, manager)) {
            return false;
        }
        for (Cost cost : offer.costs()) {
            if (!consume(player.getInventory(), cost.sample(), cost.amount(), manager)) {
                return false;
            }
        }
        return true;
    }

    public static boolean craft(Player player, Offer offer, ItemManager manager) {
        if (!consumeCosts(player, offer, manager)) {
            return false;
        }
        ItemStack result = offer.result().get();
        if (result == null || result.getType().isAir()) {
            return false;
        }
        player.getInventory().addItem(result).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    public static List<String> missingLines(Player player, Offer offer, ItemManager manager) {
        List<String> missing = new ArrayList<>();
        if (player == null || offer == null) {
            return missing;
        }
        for (Cost cost : offer.costs()) {
            int have = countMatching(player.getInventory(), cost.sample(), manager);
            if (have < cost.amount()) {
                missing.add("§c" + label(cost.sample()) + " §8· §f" + have + "§7/§f" + cost.amount());
            } else {
                missing.add("§a" + label(cost.sample()) + " §8· §f" + have + "§7/§f" + cost.amount());
            }
        }
        return missing;
    }

    private static String label(ItemStack sample) {
        if (sample == null) {
            return "Item";
        }
        if (sample.hasItemMeta() && sample.getItemMeta().hasDisplayName()) {
            return sample.getItemMeta().getDisplayName();
        }
        return sample.getType().name().toLowerCase().replace('_', ' ');
    }

    private static int countMatching(PlayerInventory inv, ItemStack sample, ItemManager manager) {
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (matches(stack, sample, manager)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static boolean consume(PlayerInventory inv, ItemStack sample, int amount, ItemManager manager) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, sample, manager)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            left -= take;
        }
        inv.setContents(contents);
        return left <= 0;
    }

    private static boolean matches(ItemStack actual, ItemStack sample, ItemManager manager) {
        if (actual == null || sample == null || actual.getType().isAir()) {
            return false;
        }
        if (manager != null) {
            String expect = manager.getItemId(sample);
            String got = manager.getItemId(actual);
            if (expect != null) {
                return expect.equalsIgnoreCase(got);
            }
        }
        return actual.getType() == sample.getType();
    }

    private static ItemStack createSphere(String id) {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin == null) {
                return new ItemStack(Material.SNOWBALL);
            }
            return (ItemStack) plugin.getClass()
                    .getMethod("createDevCatchSphere", String.class)
                    .invoke(plugin, id);
        } catch (Throwable ignored) {
            return new ItemStack(Material.SNOWBALL);
        }
    }

    private static ItemStack createTreat(int tier) {
        try {
            Class<?> type = Class.forName("de.aetherion.aethermobs.pet.PetExpTreat");
            return (ItemStack) type.getMethod("create", int.class).invoke(null, tier);
        } catch (Throwable ignored) {
            return new ItemStack(Material.APPLE);
        }
    }
}
