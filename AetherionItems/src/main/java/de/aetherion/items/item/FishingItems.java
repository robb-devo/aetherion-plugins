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

public final class FishingItems {

    public static final int STAT_REV = 5;

    private final ItemManager items;

    public FishingItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "fishing_helmet" -> helmet(1);
            case "fishing_chestplate" -> chestplate(1);
            case "fishing_leggings" -> leggings(1);
            case "fishing_boots" -> boots(1);
            case "fishing_rod" -> rod(1);
            case "fishing_helmet_2" -> helmet(2);
            case "fishing_chestplate_2" -> chestplate(2);
            case "fishing_leggings_2" -> leggings(2);
            case "fishing_boots_2" -> boots(2);
            case "fishing_rod_2" -> rod(2);
            case "fishing_helmet_3" -> helmet(3);
            case "fishing_chestplate_3" -> chestplate(3);
            case "fishing_leggings_3" -> leggings(3);
            case "fishing_boots_3" -> boots(3);
            case "fishing_rod_3" -> rod(3);
            case "fishing_helmet_4" -> helmet(4);
            case "fishing_chestplate_4" -> chestplate(4);
            case "fishing_leggings_4" -> leggings(4);
            case "fishing_boots_4" -> boots(4);
            case "fishing_rod_4" -> rod(4);
            case "fishing_helmet_5" -> helmet(5);
            case "fishing_chestplate_5" -> chestplate(5);
            case "fishing_leggings_5" -> leggings(5);
            case "fishing_boots_5" -> boots(5);
            case "fishing_rod_5" -> rod(5);
            case "diving_helmet" -> divingHelmet();
            case "diving_chestplate" -> divingChestplate();
            case "diving_leggings" -> divingLeggings();
            case "diving_boots" -> divingBoots();
            default -> null;
        };
    }

    public ItemStack helmet(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_HELMET,
                id("fishing_helmet", tier),
                name(tier, "Nibble Cap", "Ripple Hood", "Keelhaul Crown", "Abyssal Circlet", "Leviathan Crown"),
                tier,
                2901 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack chestplate(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_CHESTPLATE,
                id("fishing_chestplate", tier),
                name(tier, "Nibble Smock", "Ripple Vest", "Keelhaul Harness", "Abyssal Plating", "Leviathan Harness"),
                tier,
                2902 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack leggings(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_LEGGINGS,
                id("fishing_leggings", tier),
                name(tier, "Nibble Trousers", "Ripple Waders", "Keelhaul Britches", "Abyssal Greaves", "Leviathan Britches"),
                tier,
                2903 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack boots(int tier) {
        ItemStats stats = new ItemStats();
        return armor(
                Material.LEATHER_BOOTS,
                id("fishing_boots", tier),
                name(tier, "Nibble Clogs", "Ripple Flippers", "Keelhaul Stompers", "Abyssal Fins", "Leviathan Stompers"),
                tier,
                2904 + (tier - 1) * 5,
                stats,
                flavor(tier, false)
        );
    }

    public ItemStack rod(int tier) {
        ItemStats stats = new ItemStats();
        ItemStack item = tool(
                Material.FISHING_ROD,
                id("fishing_rod", tier),
                name(tier, "Nibble Rod", "Ripple Rod", "The Keelhaul", "Abyssal Rod", "The Leviathan"),
                rarity(tier),
                2905 + (tier - 1) * 5,
                stats,
                flavor(tier, true),
                color(tier),
                true
        );
        FishingRodProgress.init(item);
        return item;
    }

    public ItemStack divingHelmet() {
        ItemStats stats = new ItemStats();
        stats.setDefense(8);
        stats.setFortune(12);
        stats.setFishingSpeed(6);
        stats.setFishingCatch(18);
        return armor(
                Material.LEATHER_HELMET,
                "diving_helmet",
                "Tideglass Helm",
                2,
                2940,
                stats,
                List.of("§7Air is a suggestion down here.", "§7The ocean issued a visitor badge.")
        );
    }

    public ItemStack divingChestplate() {
        ItemStats stats = new ItemStats();
        stats.setDefense(12);
        stats.setFortune(14);
        stats.setFishingSpeed(6);
        stats.setFishingCatch(22);
        return armor(
                Material.LEATHER_CHESTPLATE,
                "diving_chestplate",
                "Tideglass Vest",
                2,
                2941,
                stats,
                List.of("§7Pressure, but make it fashion.", "§7Keeps the brine on the outside.")
        );
    }

    public ItemStack divingLeggings() {
        ItemStats stats = new ItemStats();
        stats.setDefense(10);
        stats.setFortune(12);
        stats.setFishingSpeed(5);
        stats.setFishingCatch(18);
        return armor(
                Material.LEATHER_LEGGINGS,
                "diving_leggings",
                "Tideglass Greaves",
                2,
                2942,
                stats,
                List.of("§7Kicking, now with a depth chart.", "§7The current filed a complaint.")
        );
    }

    public ItemStack divingBoots() {
        ItemStats stats = new ItemStats();
        stats.setDefense(7);
        stats.setFortune(10);
        stats.setFishingSpeed(5);
        stats.setFishingCatch(16);
        stats.setSpeed(4);
        return armor(
                Material.LEATHER_BOOTS,
                "diving_boots",
                "Tideglass Fins",
                2,
                2943,
                stats,
                List.of("§7Walking on water was taken.", "§7So you walked under it instead.")
        );
    }

    public ItemStack randomDivingPiece() {
        return switch (java.util.concurrent.ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> divingHelmet();
            case 1 -> divingChestplate();
            case 2 -> divingLeggings();
            default -> divingBoots();
        };
    }

    public static boolean isDiving(String itemId) {
        return itemId != null && itemId.toLowerCase(java.util.Locale.ROOT).startsWith("diving_");
    }

    public static boolean migrate(String itemId, ItemStats stats, ItemMeta meta) {
        if (itemId == null || stats == null || meta == null) {
            return false;
        }
        String lower = itemId.toLowerCase(java.util.Locale.ROOT);
        if (FishingRodProgress.isCharmId(lower)) {
            boolean missing = !meta.getPersistentDataContainer().has(
                    ItemKeys.fishingRodLevel(),
                    PersistentDataType.INTEGER
            );
            FishingRodProgress.ensureCharmMeta(meta);
            return missing;
        }
        boolean fishing = lower.startsWith("fishing_");
        boolean diving = isDiving(itemId);
        if (!fishing && !diving) {
            return false;
        }
        Integer revision = meta.getPersistentDataContainer().get(ItemKeys.fishingRev(), PersistentDataType.INTEGER);
        if (revision != null && revision >= STAT_REV) {
            return false;
        }
        boolean changed = false;
        if (FishingRodProgress.isRodId(lower) || FishingRodProgress.isArmorId(lower)) {
            // Ladder bases migrate via StarterSetBalance.apply (extras preserved as flat delta).
            FishingRodProgress.ensureMeta(meta, FishingRodProgress.isRodId(lower));
            changed = true;
        }
        boolean boots = lower.contains("_boots");
        if (!boots && stats.getSpeed() != 0) {
            stats.setSpeed(0);
            changed = true;
        }
        meta.getPersistentDataContainer().set(ItemKeys.fishingRev(), PersistentDataType.INTEGER, STAT_REV);
        return changed || revision == null || revision < STAT_REV;
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
        Color dye = isDiving(id) ? Color.fromRGB(8, 72, 92) : color(tier);
        return tool(material, id, display, isDiving(id) ? Rarity.RARE : rarity(tier), model, stats, flavor, dye, false);
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
            boolean rod
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        if (!isDiving(id)) {
            StarterSetBalance.ensureBase(id, stats, meta);
        }
        items.applyItemData(meta, rarity, stats);
        meta.getPersistentDataContainer().set(ItemKeys.statRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.getPersistentDataContainer().set(ItemKeys.fishingRev(), PersistentDataType.INTEGER, STAT_REV);
        meta.setDisplayName(rarity.getChatColor() + display);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor);
        lore.add("");
        lore.add(rod ? "§b🎣 Fishing Tool" : isDiving(id) ? "§3Diving Armor" : "§b🎣 Fishing Armor");
        lore.add("");
        lore.add(isDiving(id) ? "§8Aetherion Diving Set" : "§8Aetherion Fishing Set");
        if (isDiving(id)) {
            lore.add("§7Breathe underwater. Full set: conduit.");
        }
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
        if (id.startsWith("fishing_rod")) {
            FishingRodProgress.init(item);
        }
        return item;
    }

    private static List<String> flavor(int tier, boolean rod) {
        return switch (tier) {
            case 5 -> rod
                    ? List.of("§7The last cast. The ocean signed the NDA.", "§7Leviathan bait. Still rude to fish.")
                    : List.of("§7Mythic brine. Wear it like a verdict.", "§7Fish write five-star reviews. Under protest.");
            case 4 -> rod
                    ? List.of("§7Fourth cast. The tide started a support group.", "§7Abyss hours. Same spreadsheet, wetter.")
                    : List.of("§7Legendary leather that remembers every swell.", "§7Docks have a dress code now. This is it.");
            case 3 -> rod
                    ? List.of("§7It argues with the tide. The tide loses.", "§7The ocean filed a complaint. You declined.")
                    : List.of("§7Epic brine. Wear it like a verdict.", "§7Fish started writing reviews. One star.");
            case 2 -> rod
                    ? List.of("§7The second cast. The first one filed a complaint.", "§7Bites, wait times, excuses. A spreadsheet with water.")
                    : List.of("§7Slightly more of an argument with waves.", "§7Docks have started taking notes.");
            default -> rod
                    ? List.of("§7The polite way to start a rumor with fish.", "§7It levels up. The ocean is keeping score.")
                    : List.of("§7Leather that smelled a harbor and committed.", "§7Catches the neighbors. Professionally.");
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
            case 5 -> Color.fromRGB(8, 24, 64);
            case 4 -> Color.fromRGB(12, 36, 80);
            case 3 -> Color.fromRGB(16, 48, 96);
            case 2 -> Color.fromRGB(32, 80, 140);
            default -> Color.fromRGB(70, 130, 180);
        };
    }
}
