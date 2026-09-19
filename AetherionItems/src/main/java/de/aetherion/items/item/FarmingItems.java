package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemProfile;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class FarmingItems {

    public static final int STAT_REV = 2;

    private final ItemManager items;

    public FarmingItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "farming_helmet" -> helmet(1);
            case "farming_chestplate" -> chestplate(1);
            case "farming_leggings" -> leggings(1);
            case "farming_boots" -> boots(1);
            case "farming_hoe" -> hoe(1);
            case "farming_helmet_2" -> helmet(2);
            case "farming_chestplate_2" -> chestplate(2);
            case "farming_leggings_2" -> leggings(2);
            case "farming_boots_2" -> boots(2);
            case "farming_hoe_2" -> hoe(2);
            case "farming_helmet_3" -> helmet(3);
            case "farming_chestplate_3" -> chestplate(3);
            case "farming_leggings_3" -> leggings(3);
            case "farming_boots_3" -> boots(3);
            case "farming_hoe_3" -> hoe(3);
            case "farming_helmet_4" -> helmet(4);
            case "farming_chestplate_4" -> chestplate(4);
            case "farming_leggings_4" -> leggings(4);
            case "farming_boots_4" -> boots(4);
            case "farming_hoe_4" -> hoe(4);
            case "farming_helmet_5" -> helmet(5);
            case "farming_chestplate_5" -> chestplate(5);
            case "farming_leggings_5" -> leggings(5);
            case "farming_boots_5" -> boots(5);
            case "farming_hoe_5" -> hoe(5);
            default -> null;
        };
    }

    public ItemStack helmet(int tier) {
        ItemStats stats = new ItemStats();
        ItemStack item = armor(
                Material.LEATHER_HELMET,
                id("farming_helmet", tier),
                name(tier, "Furrow Cap", "Barnstorm Bonnet", "Haymaker Halo", "Threshlord Circlet", "Verdant Halo"),
                tier,
                2801 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
        return item;
    }

    public ItemStack chestplate(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_CHESTPLATE,
                id("farming_chestplate", tier),
                name(tier, "Furrow Smock", "Barnstorm Vest", "Haymaker Harness", "Threshlord Plating", "Verdant Harness"),
                tier,
                2802 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack leggings(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_LEGGINGS,
                id("farming_leggings", tier),
                name(tier, "Furrow Trousers", "Barnstorm Overalls", "Haymaker Britches", "Threshlord Greaves", "Verdant Britches"),
                tier,
                2803 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack boots(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_BOOTS,
                id("farming_boots", tier),
                name(tier, "Furrow Clogs", "Barnstorm Waders", "Haymaker Stompers", "Threshlord Treads", "Verdant Stompers"),
                tier,
                2804 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack hoe(int tier) {
        ItemStats stats = new ItemStats();
        ItemStack item = tool(
                hoeMaterial(tier),
                id("farming_hoe", tier),
                name(tier, "Furrow Ledger", "Barnstorm Rake", "The Haymaker", "Threshlord Scythe", "The Verdant"),
                rarity(tier),
                2805 + (tier - 1) * 5,
                stats,
                flavor(tier, true),
                color(tier),
                true
        );
        FarmingHoeProgress.init(item);
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
        return tool(material, id, display, rarity(tier), model, stats, flavor, color(tier), false);
    }

    private ItemStack tool(
            Material material,
            String id,
            String display,
            Rarity rarity,
            int model,
            ItemStats stats,
            List<String> flavor,
            Color color,
            boolean hoe
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        StarterSetBalance.ensureBase(id, stats, meta);
        items.applyItemData(meta, rarity, stats);
        meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.setDisplayName(rarity.getChatColor() + display);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor);
        lore.add("");
        lore.add(hoe ? "§e🌾 Farming Tool" : "§e🌾 Farming Armor");
        lore.add("");
        lore.add("§8Aetherion Farming Set");
        ItemProfile profile = ItemProfile.fromItemId(id);
        ItemLore.ensureStatLines(lore, stats, profile);
        ItemLore.appendBoosterSections(lore, stats, profile);
        meta.setLore(lore);
        meta.setCustomModelData(model);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
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
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        ArmorAppearance.apply(meta, id);
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> flavor(int tier, boolean hoe) {
        return switch (tier) {
            case 5 -> hoe
                    ? List.of("§7The last harvest argument. The field signed.", "§7Crops send flowers. You still clock in.")
                    : List.of("§7Mythic hay. Wear it like a verdict.", "§7The scarecrow board promoted you.");
            case 4 -> hoe
                    ? List.of("§7Fourth draft of the same rude rake.", "§7Wheat started writing reviews. Five stars, begrudgingly.")
                    : List.of("§7Legendary dirt under the nails.", "§7Barns have a dress code now. This is it.");
            case 3 -> hoe
                    ? List.of("§7It harvests the argument and the crop.", "§7The field asked for a lawyer. You declined.")
                    : List.of("§7Epic hay. Wear it like a verdict.", "§7The scarecrow unionized. You still clock in.");
            case 2 -> hoe
                    ? List.of("§7The second rake. The first one filed a complaint.", "§7Rows, columns, wheat. A spreadsheet with dirt.")
                    : List.of("§7Slightly more of an argument with grass.", "§7Barns have started writing reviews.");
            default -> hoe
                    ? List.of("§7The polite way to start a field.", "§7It levels up. The wheat is keeping score.")
                    : List.of("§7Leather that smelled a farm and committed.", "§7Harvests the neighbors. Professionally.");
        };
    }

    private static String id(String base, int tier) {
        return tier <= 1 ? base : base + "_" + tier;
    }

    private static String name(int tier, String t1, String t2, String t3, String t4, String t5) {
        return switch (tier) {
            case 5 -> t5;
            case 4 -> t4;
            case 3 -> t3;
            case 2 -> t2;
            default -> t1;
        };
    }

    private static Rarity rarity(int tier) {
        return switch (tier) {
            case 5 -> Rarity.MYTHIC;
            case 4 -> Rarity.LEGENDARY;
            case 3 -> Rarity.EPIC;
            case 2 -> Rarity.RARE;
            default -> Rarity.UNCOMMON;
        };
    }

    private static Color color(int tier) {
        return switch (tier) {
            case 5 -> Color.fromRGB(36, 96, 28);
            case 4 -> Color.fromRGB(52, 112, 36);
            case 3 -> Color.fromRGB(72, 128, 48);
            case 2 -> Color.fromRGB(154, 118, 42);
            default -> Color.fromRGB(214, 178, 68);
        };
    }

    private static Material hoeMaterial(int tier) {
        return switch (tier) {
            case 5, 4 -> Material.NETHERITE_HOE;
            case 3 -> Material.DIAMOND_HOE;
            case 2 -> Material.IRON_HOE;
            default -> Material.WOODEN_HOE;
        };
    }
}
