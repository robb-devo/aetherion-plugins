package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pet catcher set — hay T1, raw-feather T2, Compacted Feather T3.
 */
public final class CatcherItems {

    public static final int STAT_REV = 1;

    private final ItemManager items;

    public CatcherItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "catcher_helmet" -> helmet(1);
            case "catcher_chestplate" -> chestplate(1);
            case "catcher_leggings" -> leggings(1);
            case "catcher_boots" -> boots(1);
            case "catcher_gaff" -> gaff(1);
            case "catcher_helmet_2" -> helmet(2);
            case "catcher_chestplate_2" -> chestplate(2);
            case "catcher_leggings_2" -> leggings(2);
            case "catcher_boots_2" -> boots(2);
            case "catcher_gaff_2" -> gaff(2);
            case "catcher_helmet_3" -> helmet(3);
            case "catcher_chestplate_3" -> chestplate(3);
            case "catcher_leggings_3" -> leggings(3);
            case "catcher_boots_3" -> boots(3);
            case "catcher_gaff_3" -> gaff(3);
            default -> null;
        };
    }

    public ItemStack helmet(int tier) {
        ItemStats stats = new ItemStats();
        stats.setDefense(stat(tier, 2, 5, 9));
        stats.setCatchRate(stat(tier, 2, 5, 9));
        return armor(
                Material.GOLDEN_HELMET,
                id("catcher_helmet", tier),
                name(tier, "Catcher Helmet", "Snare Visor", "Menagerie Crown"),
                tier,
                4001 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack chestplate(int tier) {
        ItemStats stats = new ItemStats();
        stats.setDefense(stat(tier, 4, 8, 14));
        stats.setCatchRate(stat(tier, 3, 7, 12));
        return armor(
                Material.GOLDEN_CHESTPLATE,
                id("catcher_chestplate", tier),
                name(tier, "Catcher Chestplate", "Snare Harness", "Menagerie Plating"),
                tier,
                4002 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack leggings(int tier) {
        ItemStats stats = new ItemStats();
        stats.setDefense(stat(tier, 3, 6, 11));
        stats.setCatchRate(stat(tier, 2, 5, 9));
        return armor(
                Material.GOLDEN_LEGGINGS,
                id("catcher_leggings", tier),
                name(tier, "Catcher Leggings", "Snare Greaves", "Menagerie Striders"),
                tier,
                4003 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack boots(int tier) {
        ItemStats stats = new ItemStats();
        stats.setDefense(stat(tier, 1, 3, 6));
        stats.setCatchRate(stat(tier, 2, 5, 9));
        stats.setSpeed(stat(tier, 2, 4, 7));
        return armor(
                Material.GOLDEN_BOOTS,
                id("catcher_boots", tier),
                name(tier, "Catcher Boots", "Snare Treads", "Menagerie Softsteps"),
                tier,
                4004 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack gaff(int tier) {
        ItemStats stats = new ItemStats();
        stats.setCatchRate(stat(tier, 8, 14, 22));
        stats.setSpeed(stat(tier, 3, 5, 8));
        ItemStack item = tool(
                gaffMaterial(tier),
                id("catcher_gaff", tier),
                name(tier, "Catcher Gaff", "Snare Hook", "Menagerie Crook"),
                rarity(tier),
                1022 + (tier - 1),
                stats,
                flavor(tier, true)
        );
        CatcherGaffProgress.init(item);
        return item;
    }

    private ItemStack armor(
            Material material,
            String id,
            String display,
            int tier,
            int model,
            ItemStats stats,
            List<String> flavor
    ) {
        return tool(material, id, display, rarity(tier), model, stats, flavor);
    }

    private ItemStack tool(
            Material material,
            String id,
            String display,
            Rarity rarity,
            int model,
            ItemStats stats,
            List<String> flavor
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        items.applyItemData(meta, rarity, stats);
        meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.setDisplayName(rarity.getChatColor() + display);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor);
        lore.add("");
        lore.add(id.contains("gaff") ? "§e🐾 Off-hand Catch Tool" : "§e🐾 Catcher Armor");
        lore.add("");
        lore.add("§8Aetherion Catcher Set");
        if (id.contains("gaff")) {
            lore.add("§8Wear in off-hand. Looks like a hoe. Levels from catches.");
        }
        ItemProfile profile = ItemProfile.fromItemId(id);
        ItemLore.ensureStatLines(lore, stats, profile);
        ItemLore.appendBoosterSections(lore, stats, profile);
        meta.setLore(lore);
        meta.setCustomModelData(model);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        try {
            meta.addAttributeModifier(
                    Attribute.GENERIC_LUCK,
                    new AttributeModifier(
                            ItemKeys.key("hidden_attribute"),
                            0,
                            AttributeModifier.Operation.ADD_NUMBER
                    )
            );
        } catch (IllegalArgumentException ignored) {
        }
        ArmorAppearance.apply(meta, id);
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> flavor(int tier, boolean gaff) {
        return switch (tier) {
            case 3 -> gaff
                    ? List.of("§7Off-hand endgame hoe. Spheres do the catching.", "§7Utility charm can wait. This one is for pets.")
                    : List.of("§7Endgame softsteps. Loud intentions.", "§7The menagerie filed a complaint. Denied.");
            case 2 -> gaff
                    ? List.of("§7Off-hand upgrade hoe. Catch rate with opinions.", "§7Not a weapon. A negotiation tool.")
                    : List.of("§7Feathers in the lining. Ambition in the stitching.", "§7Pets smell the upgrade. They should.");
            default -> gaff
                    ? List.of("§7Off-hand hoe. Not a sword. Not a sphere.", "§7Wear it left. Throw spheres right. Numbers go up.")
                    : List.of("§7The eggs can smell the desperation.", "§7Quiet steps. Loud intentions.");
        };
    }

    private static String id(String base, int tier) {
        return tier <= 1 ? base : base + "_" + tier;
    }

    private static String name(int tier, String first, String second, String third) {
        return switch (tier) {
            case 3 -> third;
            case 2 -> second;
            default -> first;
        };
    }

    private static Rarity rarity(int tier) {
        return switch (tier) {
            case 3 -> Rarity.LEGENDARY;
            case 2 -> Rarity.EPIC;
            default -> Rarity.RARE;
        };
    }

    private static Material gaffMaterial(int tier) {
        return switch (tier) {
            case 3 -> Material.NETHERITE_HOE;
            case 2 -> Material.DIAMOND_HOE;
            default -> Material.IRON_HOE;
        };
    }

    private static double stat(int tier, double a, double b, double c) {
        return switch (tier) {
            case 3 -> c;
            case 2 -> b;
            default -> a;
        };
    }
}
