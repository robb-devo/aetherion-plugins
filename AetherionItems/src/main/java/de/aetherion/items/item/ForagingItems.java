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

public final class ForagingItems {

    public static final int STAT_REV = 2;

    private final ItemManager items;

    public ForagingItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "foraging_helmet" -> helmet(1);
            case "foraging_chestplate" -> chestplate(1);
            case "foraging_leggings" -> leggings(1);
            case "foraging_boots" -> boots(1);
            case "foraging_axe" -> axe(1);
            case "foraging_helmet_2" -> helmet(2);
            case "foraging_chestplate_2" -> chestplate(2);
            case "foraging_leggings_2" -> leggings(2);
            case "foraging_boots_2" -> boots(2);
            case "foraging_axe_2" -> axe(2);
            case "foraging_helmet_3" -> helmet(3);
            case "foraging_chestplate_3" -> chestplate(3);
            case "foraging_leggings_3" -> leggings(3);
            case "foraging_boots_3" -> boots(3);
            case "foraging_axe_3" -> axe(3);
            case "foraging_helmet_4" -> helmet(4);
            case "foraging_chestplate_4" -> chestplate(4);
            case "foraging_leggings_4" -> leggings(4);
            case "foraging_boots_4" -> boots(4);
            case "foraging_axe_4" -> axe(4);
            case "foraging_helmet_5" -> helmet(5);
            case "foraging_chestplate_5" -> chestplate(5);
            case "foraging_leggings_5" -> leggings(5);
            case "foraging_boots_5" -> boots(5);
            case "foraging_axe_5" -> axe(5);
            default -> null;
        };
    }

    public ItemStack helmet(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_HELMET,
                id("foraging_helmet", tier),
                name(tier, "Kindling Cap", "Windfall Hood", "Heartwood Crown", "Canopy Circlet", "Worldroot Crown"),
                tier,
                3101 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack chestplate(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_CHESTPLATE,
                id("foraging_chestplate", tier),
                name(tier, "Kindling Jacket", "Windfall Vest", "Heartwood Harness", "Canopy Plating", "Worldroot Harness"),
                tier,
                3102 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack leggings(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_LEGGINGS,
                id("foraging_leggings", tier),
                name(tier, "Kindling Greaves", "Windfall Chaps", "Heartwood Britches", "Canopy Greaves", "Worldroot Britches"),
                tier,
                3103 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack boots(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_BOOTS,
                id("foraging_boots", tier),
                name(tier, "Kindling Boots", "Windfall Treads", "Heartwood Stompers", "Canopy Treads", "Worldroot Stompers"),
                tier,
                3104 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack axe(int tier) {
        ItemStats stats = new ItemStats();
        ItemStack item = tool(
                axeMaterial(tier),
                id("foraging_axe", tier),
                name(tier, "Kindling Hatchet", "Windfall Axe", "The Felling", "Canopy Cleaver", "The Worldroot"),
                rarity(tier),
                3105 + (tier - 1) * 5,
                stats,
                flavor(tier, true),
                color(tier),
                true
        );
        ForagingAxeProgress.init(item);
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
            boolean axe
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
        lore.add(axe ? "§a🪓 Foraging Tool" : "§a🪓 Foraging Armor");
        lore.add("");
        lore.add("§8Aetherion Foraging Set");
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

    private static List<String> flavor(int tier, boolean axe) {
        return switch (tier) {
            case 5 -> axe
                    ? List.of("§7The forest filed for bankruptcy. You declined.", "§7Worldroot. Then paperwork.")
                    : List.of("§7Mythic bark. Wear it like a canopy.", "§7The trees still send invoices. Paid in full.");
            case 4 -> axe
                    ? List.of("§7Fourth swing. The stump started a podcast.", "§7Canopy clearance, on purpose.")
                    : List.of("§7Legendary leather that remembers every ring.", "§7Sawmills wrote a five-star review. Reluctantly.");
            case 3 -> axe
                    ? List.of("§7The tree files an appeal. Denied.", "§7Heartwood, then paperwork.")
                    : List.of("§7Bark that learned to be armor.", "§7The canopy still sends invoices.");
            case 2 -> axe
                    ? List.of("§7Second swing. The first one was a warning.", "§7Windfall, on purpose.")
                    : List.of("§7Slightly more of an argument with a trunk.", "§7Sawmills have started writing reviews.");
            default -> axe
                    ? List.of("§7The polite way to start a forest.", "§7Spruce first. Oak later. Always sticks.")
                    : List.of("§7Leather that smelled pine and committed.", "§7Kindling with a dress code.");
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
            case 5 -> Color.fromRGB(28, 52, 24);
            case 4 -> Color.fromRGB(36, 64, 32);
            case 3 -> Color.fromRGB(48, 78, 42);
            case 2 -> Color.fromRGB(72, 108, 52);
            default -> Color.fromRGB(118, 86, 48);
        };
    }

    private static Material axeMaterial(int tier) {
        return switch (tier) {
            case 5, 4 -> Material.NETHERITE_AXE;
            case 3 -> Material.DIAMOND_AXE;
            case 2 -> Material.IRON_AXE;
            default -> Material.WOODEN_AXE;
        };
    }
}
