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

public final class ProgressionItems {

    public static final int STAT_REV = 1;

    private final ItemManager items;

    public ProgressionItems(ItemManager items) {
        this.items = items;
    }

    public ItemStack byId(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case "compressed_oak_chestplate" -> createCompressedOakChestplate();
            case "compacted_timber_axe" -> createCompactedTimberAxe();
            case "compressed_stone_pickaxe" -> createCompressedStonePickaxe();
            case "compacted_cobble_hammer" -> createCompactedCobbleHammer();
            case "compressed_coal_ring" -> createCompressedCoalRing();
            case "copper_sword" -> createCopperSword();
            case "compressed_gold_sword" -> createCompressedGoldSword();
            case "compacted_midas_dagger" -> createCompactedMidasDagger();
            case "redstone_infused_boots" -> createRedstoneInfusedBoots();
            case "lapis_pendant" -> createLapisPendant();
            case "compacted_diamond_chestplate" -> createCompactedDiamondChestplate();
            case "compacted_diamond_sword" -> createCompactedDiamondSword();
            case "emerald_crown" -> createEmeraldCrown();
            case "compacted_emerald_scythe" -> createCompactedEmeraldScythe();
            case "compacted_iron_pickaxe" -> createCompactedIronPickaxe();
            case "compacted_diamond_pickaxe" -> createCompactedDiamondPickaxe();
            case "voided_455" -> createVoided455();
            default -> null;
        };
    }

    public ItemStack createCompressedOakChestplate() {
        ItemStats stats = new ItemStats();
        stats.setDefense(14);
        stats.setFortune(5);
        stats.setHealth(10);
        return leather(
                Material.LEATHER_CHESTPLATE,
                "compressed_oak_chestplate",
                "§aCompressed Oak Chestplate",
                Rarity.UNCOMMON,
                Color.fromRGB(122, 84, 46),
                2701,
                stats,
                List.of(
                        "§7Wood that decided it was armor.",
                        "§7The trees are still arguing.",
                        "",
                        "§8+5% wood drops. Wear with dignity."
                )
        );
    }

    public ItemStack createCompactedTimberAxe() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(16);
        stats.setFortune(18);
        stats.setSpeed(5);
        return tool(
                Material.IRON_AXE,
                "compacted_timber_axe",
                "§bCompacted Timber Axe",
                Rarity.RARE,
                2702,
                stats,
                List.of(
                        "§7Packs a suitcase while it chops.",
                        "§72.5% chance to drop Compressed Oak Log.",
                        "",
                        "§8Foraging tool. Trees hate paperwork."
                )
        );
    }

    public ItemStack createCompressedStonePickaxe() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(12);
        stats.setFortune(16);
        stats.setSpread(1);
        return tool(
                Material.STONE_PICKAXE,
                "compressed_stone_pickaxe",
                "§aCompressed Stone Pickaxe",
                Rarity.UNCOMMON,
                2703,
                stats,
                List.of(
                        "§7Cobble, but it went to finishing school.",
                        "§71% chance to compact cobble as you mine.",
                        "",
                        "§8Mining tool. The first real one."
                )
        );
    }

    public ItemStack createCompactedCobbleHammer() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(22);
        stats.setFortune(12);
        stats.setSpread(2);
        return tool(
                Material.IRON_PICKAXE,
                "compacted_cobble_hammer",
                "§bCompacted Cobble Hammer",
                Rarity.RARE,
                2704,
                stats,
                List.of(
                        "§7Quarries listen when you knock like this.",
                        "§73.5% chance to compact cobble on break.",
                        "",
                        "§8The polite way to double a pile."
                )
        );
    }

    public ItemStack createCompressedCoalRing() {
        ItemStats stats = new ItemStats();
        stats.setDefense(8);
        stats.setHealth(6);
        stats.setSpeed(8);
        return accessory(
                Material.FIREWORK_STAR,
                "compressed_coal_ring",
                "§8Compressed Coal Ring",
                Rarity.UNCOMMON,
                2705,
                stats,
                List.of(
                        "§7Keeps a little night in your pocket.",
                        "§7Base speed, then more when the sun gives up.",
                        "",
                        "§8Off-hand. Does not actually burn."
                )
        );
    }

    public ItemStack createCopperSword() {
        ItemStats stats = new ItemStats();
        stats.setDamage(18);
        stats.setAttackSpread(35);
        stats.setCritChance(4);
        stats.setCritDamage(35);
        return tool(
                Material.GOLDEN_SWORD,
                "copper_sword",
                "§eCopper Sword",
                Rarity.UNCOMMON,
                2706,
                stats,
                List.of(
                        "§7Less gossip than the rod. Still nosy.",
                        "§7Weak swing. Jumps to nearby targets.",
                        "",
                        "§8Early combat. The rod remains the loud one."
                )
        );
    }

    public ItemStack createCompressedGoldSword() {
        ItemStats stats = new ItemStats();
        stats.setDamage(28);
        stats.setAttackSpread(8);
        stats.setCritChance(8);
        stats.setCritDamage(40);
        return tool(
                Material.GOLDEN_SWORD,
                "compressed_gold_sword",
                "§6Compressed Gold Sword",
                Rarity.RARE,
                2707,
                stats,
                List.of(
                        "§7Mobs pay rent now.",
                        "§7Kills drop extra coins.",
                        "",
                        "§8Low damage. High invoices."
                )
        );
    }

    public ItemStack createCompactedMidasDagger() {
        ItemStats stats = new ItemStats();
        stats.setDamage(36);
        stats.setCritChance(12);
        stats.setCritDamage(50);
        stats.setAttackSpread(8);
        return tool(
                Material.GOLDEN_SWORD,
                "compacted_midas_dagger",
                "§6Compacted Midas Dagger",
                Rarity.EPIC,
                2708,
                stats,
                List.of(
                        "§7Every swing files an expense report.",
                        "§7Consumes §610 coins §7per hit.",
                        "§7Miss the fee and the edge goes polite.",
                        "",
                        "§8Crits that know what gold costs."
                )
        );
    }

    public ItemStack createRedstoneInfusedBoots() {
        ItemStats stats = new ItemStats();
        stats.setDefense(16);
        stats.setSpeed(10);
        stats.setHealth(8);
        return leather(
                Material.LEATHER_BOOTS,
                "redstone_infused_boots",
                "§cRedstone Infused Boots",
                Rarity.RARE,
                Color.fromRGB(180, 20, 20),
                2709,
                stats,
                List.of(
                        "§7Haste, with fewer extra steps.",
                        "§7Ability cooldowns: §c-15%§7.",
                        "",
                        "§8Wands, blinks, and other showing off."
                )
        );
    }

    public ItemStack createLapisPendant() {
        ItemStats stats = new ItemStats();
        stats.setHealth(25);
        stats.setDefense(4);
        stats.setSpeed(3);
        return accessory(
                Material.HEART_OF_THE_SEA,
                "lapis_pendant",
                "§9Lapis Pendant",
                Rarity.RARE,
                2710,
                stats,
                List.of(
                        "§7Your bruises moonlight as a shield.",
                        "§710% of hits become absorption.",
                        "",
                        "§8Off-hand. We do not have mana. On purpose."
                )
        );
    }

    public ItemStack createCompactedDiamondChestplate() {
        ItemStats stats = new ItemStats();
        stats.setDefense(58);
        stats.setHealth(32);
        stats.setAttackSpread(6);
        return tool(
                Material.DIAMOND_CHESTPLATE,
                "compacted_diamond_chestplate",
                "§bCompacted Diamond Chestplate",
                Rarity.LEGENDARY,
                2711,
                stats,
                List.of(
                        "§7If they hit you, they can have some back.",
                        "§7Reflects 10% melee damage.",
                        "",
                        "§8Boss-slayer plate. Sparkles under pressure."
                )
        );
    }

    public ItemStack createCompactedDiamondSword() {
        ItemStats stats = new ItemStats();
        stats.setDamage(72);
        stats.setAttackSpread(18);
        stats.setCritChance(14);
        stats.setCritDamage(85);
        return tool(
                Material.DIAMOND_SWORD,
                "compacted_diamond_sword",
                "§bCompacted Diamond Sword",
                Rarity.LEGENDARY,
                2712,
                stats,
                List.of(
                        "§7Bosses hate paperwork. This is paperwork.",
                        "§7+25% damage to bosses.",
                        "",
                        "§8McNugget has been served."
                )
        );
    }

    public ItemStack createEmeraldCrown() {
        ItemStats stats = new ItemStats();
        stats.setDefense(18);
        stats.setHealth(8);
        stats.setCatchRate(25);
        stats.setFortune(12);
        return tool(
                Material.GOLDEN_HELMET,
                "emerald_crown",
                "§aEmerald Crown",
                Rarity.LEGENDARY,
                2713,
                stats,
                List.of(
                        "§7Luck so loud the loot table can hear it.",
                        "§7+25% Catch Rate. Rare eggs notice you.",
                        "",
                        "§8Wear it. Pretend it was earned."
                )
        );
    }

    public ItemStack createCompactedEmeraldScythe() {
        ItemStats stats = new ItemStats();
        stats.setDamage(64);
        stats.setCritChance(16);
        stats.setCritDamage(70);
        stats.setSpeed(6);
        stats.setFortune(10);
        return tool(
                Material.NETHERITE_HOE,
                "compacted_emerald_scythe",
                "§aCompacted Emerald Scythe",
                Rarity.LEGENDARY,
                2714,
                stats,
                List.of(
                        "§7Sometimes the coins applaud.",
                        "§75% chance: coin explosion and a short stun.",
                        "",
                        "§8Hybrid graft. Diamond spine, emerald manners."
                )
        );
    }

    public ItemStack createCompactedIronPickaxe() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(32);
        stats.setFortune(40);
        stats.setSpread(3);
        return tool(
                Material.IRON_PICKAXE,
                "compacted_iron_pickaxe",
                "§fCompacted Iron Pickaxe",
                Rarity.EPIC,
                2715,
                stats,
                List.of(
                        "§7Smelted the waiting out of mining.",
                        "§72% chance to compact ores as you go.",
                        "",
                        "§8Mid-game pick. The quarry's older sibling."
                )
        );
    }

    public ItemStack createCompactedDiamondPickaxe() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(55);
        stats.setFortune(70);
        stats.setSpread(5);
        return tool(
                Material.DIAMOND_PICKAXE,
                "compacted_diamond_pickaxe",
                "§bCompacted Diamond Pickaxe",
                Rarity.LEGENDARY,
                2716,
                stats,
                List.of(
                        "§7The rock files for compressed status.",
                        "§74% chance to compact while mining.",
                        "",
                        "§8Endgame pick. Still jealous of the mythic set."
                )
        );
    }

    public ItemStack createVoided455() {
        ItemStats stats = new ItemStats();
        stats.setMiningPower(220);
        stats.setFortune(255);
        stats.setSpread(24);
        return tool(
                Material.NETHERITE_PICKAXE,
                "voided_455",
                "§5Voided 455",
                Rarity.MYTHIC,
                2799,
                stats,
                List.of(
                        "§7A drill that almost finished becoming a rounder name.",
                        "§7The last catalyst never made the crossing.",
                        "§7Void postage marked it RETURN TO SENDER, unpaid.",
                        "§7Whoever held it learned that holes keep receipts.",
                        "",
                        "§dOres leave as Compressed. Sometimes Compacted.",
                        "§8No charm. No skill. Greed still stacks.",
                        "",
                        "§8Not craftable. Not in the book."
                )
        );
    }

    private ItemStack leather(
            Material material,
            String id,
            String name,
            Rarity rarity,
            Color color,
            int model,
            ItemStats stats,
            List<String> flavor
    ) {
        ItemStack item = tool(material, id, name, rarity, model, stats, flavor);
        if (item.getItemMeta() instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            item.setItemMeta(leather);
        }
        return item;
    }

    private ItemStack accessory(
            Material material,
            String id,
            String name,
            Rarity rarity,
            int model,
            ItemStats stats,
            List<String> flavor
    ) {
        ItemStack item = tool(material, id, name, rarity, model, stats, flavor);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack tool(
            Material material,
            String id,
            String name,
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
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(flavor);
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

    public static boolean migrateBaseStats(String id, ItemStats stats) {
        if (id == null || stats == null) {
            return false;
        }
        return switch (id) {
            case "compressed_coal_ring" -> bumpRing(stats);
            case "compressed_oak_chestplate" -> bumpHealth(stats, 10);
            case "compressed_gold_sword" -> bumpAttackSpread(stats, 8);
            case "lapis_pendant" -> bumpSpeed(stats, 3);
            case "compacted_diamond_chestplate" -> bumpAttackSpread(stats, 6);
            case "emerald_crown" -> bumpHealth(stats, 8);
            default -> false;
        };
    }

    private static boolean bumpRing(ItemStats stats) {
        stats.setDefense(stats.getDefense() + 6);
        stats.setHealth(stats.getHealth() + 6);
        stats.setSpeed(stats.getSpeed() + 8);
        return true;
    }

    private static boolean bumpHealth(ItemStats stats, double amount) {
        stats.setHealth(stats.getHealth() + amount);
        return true;
    }

    private static boolean bumpSpeed(ItemStats stats, double amount) {
        stats.setSpeed(stats.getSpeed() + amount);
        return true;
    }

    private static boolean bumpAttackSpread(ItemStats stats, double amount) {
        stats.setAttackSpread(stats.getAttackSpread() + amount);
        return true;
    }
}
