package de.aetherion.items.recipe;

import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.economy.QuarryItems;
import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.item.CatcherItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.item.FarmingItems;
import de.aetherion.items.item.ForagingItems;
import de.aetherion.items.item.FishingItems;
import de.aetherion.items.item.ProgressionItems;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * =========================================================
 * RECIPE REGISTRY
 * =========================================================
 *
 * Zentrale Registrierung der Aetherion-Rezepte.
 *
 * Ingredients können sein:
 *
 * - normale Minecraft Materialien
 * - bestehende Aetherion Items
 *
 * =========================================================
 */

public class RecipeRegistry {


    private final RecipeManager recipeManager;

    private final CustomItem customItem;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public RecipeRegistry(
            RecipeManager recipeManager,
            CustomItem customItem
    ) {
        this.recipeManager = recipeManager;
        this.customItem = customItem;
    }


    /*
     * =========================================================
     * REGISTER ALL
     * =========================================================
     */

    public void registerAll() {

        recipeManager.clear();


        /*
         * =====================================================
         * BOOSTERS
         * =====================================================
         */

        registerSimple(
                "coal_booster",
                RecipeCategory.BOOSTERS,
                customItem.createCoalBooster(),
                Rarity.COMMON,
                List.of(
                        "CCC",
                        "CCC",
                        "CCC"
                ),
                createMaterialIngredients(
                        'C',
                        Material.COAL_BLOCK
                )
        );


        registerSimple(
                "iron_booster",
                RecipeCategory.BOOSTERS,
                customItem.createIronBooster(),
                Rarity.COMMON,
                List.of(
                        "III",
                        "III",
                        "III"
                ),
                createMaterialIngredients(
                        'I',
                        Material.IRON_BLOCK
                )
        );


        registerSimple(
                "gold_booster",
                RecipeCategory.BOOSTERS,
                customItem.createGoldBooster(),
                Rarity.COMMON,
                List.of(
                        "GGG",
                        "GGG",
                        "GGG"
                ),
                createMaterialIngredients(
                        'G',
                        Material.GOLD_BLOCK
                )
        );


        registerSimple(
                "diamond_booster",
                RecipeCategory.BOOSTERS,
                customItem.createDiamondBooster(),
                Rarity.COMMON,
                List.of(
                        "DDD",
                        "DDD",
                        "DDD"
                ),
                createMaterialIngredients(
                        'D',
                        Material.DIAMOND_BLOCK
                )
        );


        registerSimple(
                "emerald_booster",
                RecipeCategory.BOOSTERS,
                customItem.createEmeraldBooster(),
                Rarity.COMMON,
                List.of(
                        "EEE",
                        "EEE",
                        "EEE"
                ),
                createMaterialIngredients(
                        'E',
                        Material.EMERALD_BLOCK
                )
        );


        registerSimple(
                "redstone_booster",
                RecipeCategory.BOOSTERS,
                customItem.createRedstoneBooster(),
                Rarity.COMMON,
                List.of(
                        "RRR",
                        "RRR",
                        "RRR"
                ),
                createMaterialIngredients(
                        'R',
                        Material.REDSTONE_BLOCK
                )
        );


        registerSimple(
                "lapis_booster",
                RecipeCategory.BOOSTERS,
                customItem.createLapisBooster(),
                Rarity.COMMON,
                List.of(
                        "LLL",
                        "LLL",
                        "LLL"
                ),
                createMaterialIngredients(
                        'L',
                        Material.LAPIS_BLOCK
                )
        );


        registerSimple(
                "glowstone_booster",
                RecipeCategory.BOOSTERS,
                customItem.createGlowstoneBooster(),
                Rarity.COMMON,
                List.of(
                        "GGG",
                        "GGG",
                        "GGG"
                ),
                createMaterialIngredients(
                        'G',
                        Material.GLOWSTONE
                )
        );


        registerSimple(
                "wheat_booster",
                RecipeCategory.BOOSTERS,
                customItem.createWheatBooster(),
                Rarity.COMMON,
                List.of(
                        "WWW",
                        "WWW",
                        "WWW"
                ),
                createMaterialIngredients(
                        'W',
                        Material.HAY_BLOCK
                )
        );


        registerSimple(
                "oak_booster",
                RecipeCategory.BOOSTERS,
                customItem.createOakBooster(),
                Rarity.COMMON,
                List.of(
                        "OOO",
                        "OOO",
                        "OOO"
                ),
                createMaterialIngredients(
                        'O',
                        Material.OAK_LOG
                )
        );


        registerSimple(
                "birch_booster",
                RecipeCategory.BOOSTERS,
                customItem.createBirchBooster(),
                Rarity.COMMON,
                List.of(
                        "BBB",
                        "BBB",
                        "BBB"
                ),
                createMaterialIngredients(
                        'B',
                        Material.BIRCH_LOG
                )
        );


        registerSimple(
                "carrot_booster",
                RecipeCategory.BOOSTERS,
                customItem.createCarrotBooster(),
                Rarity.COMMON,
                List.of(
                        "CCC",
                        "CCC",
                        "CCC"
                ),
                createMaterialIngredients(
                        'C',
                        Material.CARROT,
                        32
                )
        );


        /*
         * =====================================================
         * COMBAT ARMOR I
         * Vanilla cobble shapes. No simple leather gate.
         * =====================================================
         */

        registerSimple(
                "combat_helmet",
                RecipeCategory.COMBAT,
                customItem.createCombatHelmet(),
                Rarity.COMMON,
                List.of("CCC", "C C"),
                createMaterialIngredients('C', Material.COBBLESTONE)
        );

        registerSimple(
                "combat_chestplate",
                RecipeCategory.COMBAT,
                customItem.createCombatChestplate(),
                Rarity.COMMON,
                List.of("C C", "CCC", "CCC"),
                createMaterialIngredients('C', Material.COBBLESTONE)
        );

        registerSimple(
                "combat_leggings",
                RecipeCategory.COMBAT,
                customItem.createCombatLeggings(),
                Rarity.COMMON,
                List.of("CCC", "C C", "C C"),
                createMaterialIngredients('C', Material.COBBLESTONE)
        );

        registerSimple(
                "combat_boots",
                RecipeCategory.COMBAT,
                customItem.createCombatBoots(),
                Rarity.COMMON,
                List.of("C C", "C C"),
                createMaterialIngredients('C', Material.COBBLESTONE)
        );


        /*
         * =====================================================
         * COMBAT ARMOR II–V
         * Peter / Wave 2: exactly 1 previous piece + new tier mats.
         * Never 2–3× the same helmet/sword in the grid.
         * T2 mix · T3 mix-compressed · T4 diagonal cross · T5 peak.
         * =====================================================
         */

        registerLadderMix(
                "combat_helmet_2", RecipeCategory.COMBAT, customItem.createCombatHelmet2(), Rarity.RARE,
                customItem.createCombatHelmet(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BONE));
        registerLadderMix(
                "combat_chestplate_2", RecipeCategory.COMBAT, customItem.createCombatChestplate2(), Rarity.RARE,
                customItem.createCombatChestplate(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BONE));
        registerLadderMix(
                "combat_leggings_2", RecipeCategory.COMBAT, customItem.createCombatLeggings2(), Rarity.RARE,
                customItem.createCombatLeggings(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BONE));
        registerLadderMix(
                "combat_boots_2", RecipeCategory.COMBAT, customItem.createCombatBoots2(), Rarity.RARE,
                customItem.createCombatBoots(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BONE));

        registerLadderMix(
                "combat_helmet_3", RecipeCategory.COMBAT, customItem.createCombatHelmet3(), Rarity.EPIC,
                customItem.createCombatHelmet2(),
                CompressedResource.BONE.compressed(),
                CompressedResource.ROTTEN_FLESH.compressed());
        registerLadderMix(
                "combat_chestplate_3", RecipeCategory.COMBAT, customItem.createCombatChestplate3(), Rarity.EPIC,
                customItem.createCombatChestplate2(),
                CompressedResource.BONE.compressed(),
                CompressedResource.ROTTEN_FLESH.compressed());
        registerLadderMix(
                "combat_leggings_3", RecipeCategory.COMBAT, customItem.createCombatLeggings3(), Rarity.EPIC,
                customItem.createCombatLeggings2(),
                CompressedResource.BONE.compressed(),
                CompressedResource.ROTTEN_FLESH.compressed());
        registerLadderMix(
                "combat_boots_3", RecipeCategory.COMBAT, customItem.createCombatBoots3(), Rarity.EPIC,
                customItem.createCombatBoots2(),
                CompressedResource.BONE.compressed(),
                CompressedResource.ROTTEN_FLESH.compressed());

        registerLadderCross(
                "combat_helmet_4", RecipeCategory.COMBAT, customItem.createCombatHelmet4(), Rarity.LEGENDARY,
                customItem.createCombatHelmet3(),
                CompressedResource.RAW_GOLD.compacted(),
                CompressedResource.BONE.compacted());
        registerLadderCross(
                "combat_chestplate_4", RecipeCategory.COMBAT, customItem.createCombatChestplate4(), Rarity.LEGENDARY,
                customItem.createCombatChestplate3(),
                CompressedResource.RAW_GOLD.compacted(),
                CompressedResource.BONE.compacted());
        registerLadderCross(
                "combat_leggings_4", RecipeCategory.COMBAT, customItem.createCombatLeggings4(), Rarity.LEGENDARY,
                customItem.createCombatLeggings3(),
                CompressedResource.RAW_GOLD.compacted(),
                CompressedResource.BONE.compacted());
        registerLadderCross(
                "combat_boots_4", RecipeCategory.COMBAT, customItem.createCombatBoots4(), Rarity.LEGENDARY,
                customItem.createCombatBoots3(),
                CompressedResource.RAW_GOLD.compacted(),
                CompressedResource.BONE.compacted());

        registerLadderPeak(
                "combat_helmet_5", RecipeCategory.COMBAT, customItem.createCombatHelmet5(), Rarity.MYTHIC,
                customItem.createCombatHelmet4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.EMERALD.compacted());
        registerLadderPeak(
                "combat_chestplate_5", RecipeCategory.COMBAT, customItem.createCombatChestplate5(), Rarity.MYTHIC,
                customItem.createCombatChestplate4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.EMERALD.compacted());
        registerLadderPeak(
                "combat_leggings_5", RecipeCategory.COMBAT, customItem.createCombatLeggings5(), Rarity.MYTHIC,
                customItem.createCombatLeggings4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.EMERALD.compacted());
        registerLadderPeak(
                "combat_boots_5", RecipeCategory.COMBAT, customItem.createCombatBoots5(), Rarity.MYTHIC,
                customItem.createCombatBoots4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.EMERALD.compacted());


        /*
         * =====================================================
         * COMBAT SWORD I
         * =====================================================
         */

        registerSimple(
                "combat_sword",
                RecipeCategory.COMBAT,
                customItem.createCombatSword(),
                Rarity.COMMON,
                List.of(" C ", " C ", " S "),
                createMixedIngredients(
                        'C',
                        new ItemStack(Material.COBBLESTONE),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );


        /*
         * =====================================================
         * COMBAT SWORD II–V
         * =====================================================
         */

        registerLadderMix(
                "combat_sword_2", RecipeCategory.COMBAT, customItem.createCombatSword2(), Rarity.RARE,
                customItem.createCombatSword(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BONE));
        registerLadderMix(
                "combat_sword_3", RecipeCategory.COMBAT, customItem.createCombatSword3(), Rarity.EPIC,
                customItem.createCombatSword2(),
                CompressedResource.BONE.compressed(),
                CompressedResource.ROTTEN_FLESH.compressed());
        registerLadderCross(
                "combat_sword_4", RecipeCategory.COMBAT, customItem.createCombatSword4(), Rarity.LEGENDARY,
                customItem.createCombatSword3(),
                CompressedResource.RAW_GOLD.compacted(),
                CompressedResource.BONE.compacted());
        registerLadderPeak(
                "combat_sword_5", RecipeCategory.COMBAT, customItem.createCombatSword5(), Rarity.MYTHIC,
                customItem.createCombatSword4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.EMERALD.compacted());


        /*
         * =====================================================
         * MINING ARMOR I
         * Vanilla copper shapes. No simple leather gate.
         * =====================================================
         */

        registerSimple(
                "mining_helmet",
                RecipeCategory.MINING,
                customItem.createMiningHelmet(),
                Rarity.COMMON,
                List.of("CCC", "C C"),
                createMaterialIngredients('C', Material.COPPER_INGOT)
        );

        registerSimple(
                "mining_chestplate",
                RecipeCategory.MINING,
                customItem.createMiningChestplate(),
                Rarity.COMMON,
                List.of("C C", "CCC", "CCC"),
                createMaterialIngredients('C', Material.COPPER_INGOT)
        );

        registerSimple(
                "mining_leggings",
                RecipeCategory.MINING,
                customItem.createMiningLeggings(),
                Rarity.COMMON,
                List.of("CCC", "C C", "C C"),
                createMaterialIngredients('C', Material.COPPER_INGOT)
        );

        registerSimple(
                "mining_boots",
                RecipeCategory.MINING,
                customItem.createMiningBoots(),
                Rarity.COMMON,
                List.of("C C", "C C"),
                createMaterialIngredients('C', Material.COPPER_INGOT)
        );


        /*
         * =====================================================
         * MINING ARMOR II–V
         * Peter / Wave 2: exactly 1 previous piece + new tier mats.
         * T2 mix · T3 mix-compressed · T4 diagonal cross · T5 peak.
         * =====================================================
         */

        registerLadderMix(
                "mining_helmet_2", RecipeCategory.MINING, customItem.createMiningHelmet2(), Rarity.RARE,
                customItem.createMiningHelmet(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL));
        registerLadderMix(
                "mining_chestplate_2", RecipeCategory.MINING, customItem.createMiningChestplate2(), Rarity.RARE,
                customItem.createMiningChestplate(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL));
        registerLadderMix(
                "mining_leggings_2", RecipeCategory.MINING, customItem.createMiningLeggings2(), Rarity.RARE,
                customItem.createMiningLeggings(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL));
        registerLadderMix(
                "mining_boots_2", RecipeCategory.MINING, customItem.createMiningBoots2(), Rarity.RARE,
                customItem.createMiningBoots(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL));

        registerLadderMix(
                "mining_helmet_3", RecipeCategory.MINING, customItem.createMiningHelmet3(), Rarity.EPIC,
                customItem.createMiningHelmet2(),
                CompressedResource.RAW_IRON.compressed(),
                CompressedResource.COAL.compressed());
        registerLadderMix(
                "mining_chestplate_3", RecipeCategory.MINING, customItem.createMiningChestplate3(), Rarity.EPIC,
                customItem.createMiningChestplate2(),
                CompressedResource.RAW_IRON.compressed(),
                CompressedResource.COAL.compressed());
        registerLadderMix(
                "mining_leggings_3", RecipeCategory.MINING, customItem.createMiningLeggings3(), Rarity.EPIC,
                customItem.createMiningLeggings2(),
                CompressedResource.RAW_IRON.compressed(),
                CompressedResource.COAL.compressed());
        registerLadderMix(
                "mining_boots_3", RecipeCategory.MINING, customItem.createMiningBoots3(), Rarity.EPIC,
                customItem.createMiningBoots2(),
                CompressedResource.RAW_IRON.compressed(),
                CompressedResource.COAL.compressed());

        registerLadderCross(
                "mining_helmet_4", RecipeCategory.MINING, customItem.createMiningHelmet4(), Rarity.LEGENDARY,
                customItem.createMiningHelmet3(),
                CompressedResource.RAW_COPPER.compacted(),
                CompressedResource.COAL.compacted());
        registerLadderCross(
                "mining_chestplate_4", RecipeCategory.MINING, customItem.createMiningChestplate4(), Rarity.LEGENDARY,
                customItem.createMiningChestplate3(),
                CompressedResource.RAW_COPPER.compacted(),
                CompressedResource.COAL.compacted());
        registerLadderCross(
                "mining_leggings_4", RecipeCategory.MINING, customItem.createMiningLeggings4(), Rarity.LEGENDARY,
                customItem.createMiningLeggings3(),
                CompressedResource.RAW_COPPER.compacted(),
                CompressedResource.COAL.compacted());
        registerLadderCross(
                "mining_boots_4", RecipeCategory.MINING, customItem.createMiningBoots4(), Rarity.LEGENDARY,
                customItem.createMiningBoots3(),
                CompressedResource.RAW_COPPER.compacted(),
                CompressedResource.COAL.compacted());

        registerLadderPeak(
                "mining_helmet_5", RecipeCategory.MINING, customItem.createMiningHelmet5(), Rarity.MYTHIC,
                customItem.createMiningHelmet4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.REDSTONE.compacted());
        registerLadderPeak(
                "mining_chestplate_5", RecipeCategory.MINING, customItem.createMiningChestplate5(), Rarity.MYTHIC,
                customItem.createMiningChestplate4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.REDSTONE.compacted());
        registerLadderPeak(
                "mining_leggings_5", RecipeCategory.MINING, customItem.createMiningLeggings5(), Rarity.MYTHIC,
                customItem.createMiningLeggings4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.REDSTONE.compacted());
        registerLadderPeak(
                "mining_boots_5", RecipeCategory.MINING, customItem.createMiningBoots5(), Rarity.MYTHIC,
                customItem.createMiningBoots4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.REDSTONE.compacted());


        /*
         * =====================================================
         * SIMPLE PICKAXE
         * First craftable pick. Also sold at the gear trader.
         * =====================================================
         */

        registerSimple(
                "simple_pickaxe",
                RecipeCategory.MINING,
                customItem.createSimplePickaxe(),
                Rarity.COMMON,
                List.of("CCC", " S ", " S "),
                createMixedIngredients(
                        'C',
                        new ItemStack(Material.COAL),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );


        /*
         * =====================================================
         * MINING PICKAXE I
         * Exactly one predecessor (Simple Pickaxe) + eight coal.
         * Not 2–3× the previous pick — coal is the new mat.
         * =====================================================
         */

        registerSimple(
                "mining_pickaxe",
                RecipeCategory.MINING,
                customItem.createMiningPickaxe(),
                Rarity.COMMON,
                List.of("XXX", "XCX", "XXX"),
                createMixedIngredients(
                        'X',
                        new ItemStack(Material.COAL),
                        'C',
                        customItem.createSimplePickaxe()
                ),
                new CraftedPredecessorRequirement("simple_pickaxe")
        );


        /*
         * =====================================================
         * MINING PICKAXE II–V  (Peter one-predecessor ladder)
         * T2 mix 2 iron + 2 coal
         * T3 mix 2 compressed copper + 2 compressed iron
         * T4 diagonal cross 2 compacted copper + 2 compacted coal
         * T5 peak 1 compacted diamond + 3 compacted redstone
         * Center is always exactly one previous pickaxe.
         * =====================================================
         */

        registerLadderMix(
                "mining_pickaxe_2", RecipeCategory.MINING, customItem.createMiningPickaxe2(), Rarity.RARE,
                customItem.createMiningPickaxe(), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL));
        registerLadderMix(
                "mining_pickaxe_3", RecipeCategory.MINING, customItem.createMiningPickaxe3(), Rarity.EPIC,
                customItem.createMiningPickaxe2(),
                CompressedResource.RAW_COPPER.compressed(),
                CompressedResource.RAW_IRON.compressed());
        registerLadderCross(
                "mining_pickaxe_4", RecipeCategory.MINING, customItem.createMiningPickaxe4(), Rarity.LEGENDARY,
                customItem.createMiningPickaxe3(),
                CompressedResource.RAW_COPPER.compacted(),
                CompressedResource.COAL.compacted());
        registerLadderPeak(
                "mining_pickaxe_5", RecipeCategory.MINING, customItem.createMiningPickaxe5(), Rarity.MYTHIC,
                customItem.createMiningPickaxe4(),
                CompressedResource.DIAMOND.compacted(),
                CompressedResource.REDSTONE.compacted());

        registerSimple(
                "vein_siphon",
                RecipeCategory.MINING,
                customItem.createVeinSiphon(),
                Rarity.RARE,
                List.of("IRI", "RPR", "IRI"),
                createMixedIngredients(
                        'I',
                        new ItemStack(Material.IRON_INGOT),
                        'R',
                        new ItemStack(Material.REDSTONE),
                        'P',
                        customItem.createMiningPickaxe()
                ),
                new BlueprintUnlockRequirement(
                        de.aetherion.items.blueprint.BlueprintUnlockService.VEIN_SIPHON,
                        "Turn in Blueprint: Vein Siphon at the Surveyor"
                )
        );

        FarmingItems farming = customItem.farming();
        registerSimple(
                "farming_helmet",
                RecipeCategory.FARMING,
                farming.helmet(1),
                Rarity.UNCOMMON,
                List.of("WWW", "W W"),
                createMaterialIngredients('W', Material.WHEAT)
        );
        registerSimple(
                "farming_chestplate",
                RecipeCategory.FARMING,
                farming.chestplate(1),
                Rarity.UNCOMMON,
                List.of("W W", "WWW", "WWW"),
                createMaterialIngredients('W', Material.WHEAT)
        );
        registerSimple(
                "farming_leggings",
                RecipeCategory.FARMING,
                farming.leggings(1),
                Rarity.UNCOMMON,
                List.of("WWW", "W W", "W W"),
                createMaterialIngredients('W', Material.WHEAT)
        );
        registerSimple(
                "farming_boots",
                RecipeCategory.FARMING,
                farming.boots(1),
                Rarity.UNCOMMON,
                List.of("W W", "W W"),
                createMaterialIngredients('W', Material.WHEAT)
        );
        registerSimple(
                "farming_hoe",
                RecipeCategory.FARMING,
                farming.hoe(1),
                Rarity.UNCOMMON,
                List.of("WWW", " S ", " S "),
                createMixedIngredients(
                        'W',
                        new ItemStack(Material.WHEAT),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );
        registerLadderMix(
                "farming_helmet_2", RecipeCategory.FARMING, farming.helmet(2), Rarity.RARE,
                farming.helmet(1), new ItemStack(Material.CARROT), new ItemStack(Material.WHEAT));
        registerLadderMix(
                "farming_chestplate_2", RecipeCategory.FARMING, farming.chestplate(2), Rarity.RARE,
                farming.chestplate(1), new ItemStack(Material.CARROT), new ItemStack(Material.WHEAT));
        registerLadderMix(
                "farming_leggings_2", RecipeCategory.FARMING, farming.leggings(2), Rarity.RARE,
                farming.leggings(1), new ItemStack(Material.CARROT), new ItemStack(Material.WHEAT));
        registerLadderMix(
                "farming_boots_2", RecipeCategory.FARMING, farming.boots(2), Rarity.RARE,
                farming.boots(1), new ItemStack(Material.CARROT), new ItemStack(Material.WHEAT));
        registerLadderMix(
                "farming_hoe_2", RecipeCategory.FARMING, farming.hoe(2), Rarity.RARE,
                farming.hoe(1), new ItemStack(Material.CARROT), new ItemStack(Material.WHEAT));
        registerLadderMix(
                "farming_helmet_3", RecipeCategory.FARMING, farming.helmet(3), Rarity.EPIC,
                farming.helmet(2), CompressedResource.POTATO.compressed(), CompressedResource.WHEAT.compressed());
        registerLadderMix(
                "farming_chestplate_3", RecipeCategory.FARMING, farming.chestplate(3), Rarity.EPIC,
                farming.chestplate(2), CompressedResource.POTATO.compressed(), CompressedResource.WHEAT.compressed());
        registerLadderMix(
                "farming_leggings_3", RecipeCategory.FARMING, farming.leggings(3), Rarity.EPIC,
                farming.leggings(2), CompressedResource.POTATO.compressed(), CompressedResource.WHEAT.compressed());
        registerLadderMix(
                "farming_boots_3", RecipeCategory.FARMING, farming.boots(3), Rarity.EPIC,
                farming.boots(2), CompressedResource.POTATO.compressed(), CompressedResource.WHEAT.compressed());
        registerLadderMix(
                "farming_hoe_3", RecipeCategory.FARMING, farming.hoe(3), Rarity.EPIC,
                farming.hoe(2), CompressedResource.POTATO.compressed(), CompressedResource.WHEAT.compressed());
        registerLadderCross(
                "farming_helmet_4", RecipeCategory.FARMING, farming.helmet(4), Rarity.LEGENDARY,
                farming.helmet(3), CompressedResource.SUGAR_CANE.compacted(), CompressedResource.CARROT.compacted());
        registerLadderCross(
                "farming_chestplate_4", RecipeCategory.FARMING, farming.chestplate(4), Rarity.LEGENDARY,
                farming.chestplate(3), CompressedResource.SUGAR_CANE.compacted(), CompressedResource.CARROT.compacted());
        registerLadderCross(
                "farming_leggings_4", RecipeCategory.FARMING, farming.leggings(4), Rarity.LEGENDARY,
                farming.leggings(3), CompressedResource.SUGAR_CANE.compacted(), CompressedResource.CARROT.compacted());
        registerLadderCross(
                "farming_boots_4", RecipeCategory.FARMING, farming.boots(4), Rarity.LEGENDARY,
                farming.boots(3), CompressedResource.SUGAR_CANE.compacted(), CompressedResource.CARROT.compacted());
        registerLadderCross(
                "farming_hoe_4", RecipeCategory.FARMING, farming.hoe(4), Rarity.LEGENDARY,
                farming.hoe(3), CompressedResource.SUGAR_CANE.compacted(), CompressedResource.WHEAT.compacted());
        registerLadderPeak(
                "farming_helmet_5", RecipeCategory.FARMING, farming.helmet(5), Rarity.MYTHIC,
                farming.helmet(4), CompressedResource.NETHER_WART.compacted(), CompressedResource.POTATO.compacted());
        registerLadderPeak(
                "farming_chestplate_5", RecipeCategory.FARMING, farming.chestplate(5), Rarity.MYTHIC,
                farming.chestplate(4), CompressedResource.NETHER_WART.compacted(), CompressedResource.POTATO.compacted());
        registerLadderPeak(
                "farming_leggings_5", RecipeCategory.FARMING, farming.leggings(5), Rarity.MYTHIC,
                farming.leggings(4), CompressedResource.NETHER_WART.compacted(), CompressedResource.POTATO.compacted());
        registerLadderPeak(
                "farming_boots_5", RecipeCategory.FARMING, farming.boots(5), Rarity.MYTHIC,
                farming.boots(4), CompressedResource.NETHER_WART.compacted(), CompressedResource.POTATO.compacted());
        registerLadderPeak(
                "farming_hoe_5", RecipeCategory.FARMING, farming.hoe(5), Rarity.MYTHIC,
                farming.hoe(4), CompressedResource.NETHER_WART.compacted(), CompressedResource.WHEAT.compacted());

        ForagingItems foraging = customItem.foraging();
        // T1 Oak · T2 Birch+Spruce · T3 Jungle+Acacia compressed · T4 Dark Oak+Mangrove compacted · T5 Cherry+Bamboo compacted
        registerSimple(
                "foraging_helmet",
                RecipeCategory.FORAGING,
                foraging.helmet(1),
                Rarity.UNCOMMON,
                List.of("SSS", "S S"),
                createMaterialIngredients('S', Material.OAK_LOG)
        );
        registerSimple(
                "foraging_chestplate",
                RecipeCategory.FORAGING,
                foraging.chestplate(1),
                Rarity.UNCOMMON,
                List.of("S S", "SSS", "SSS"),
                createMaterialIngredients('S', Material.OAK_LOG)
        );
        registerSimple(
                "foraging_leggings",
                RecipeCategory.FORAGING,
                foraging.leggings(1),
                Rarity.UNCOMMON,
                List.of("SSS", "S S", "S S"),
                createMaterialIngredients('S', Material.OAK_LOG)
        );
        registerSimple(
                "foraging_boots",
                RecipeCategory.FORAGING,
                foraging.boots(1),
                Rarity.UNCOMMON,
                List.of("S S", "S S"),
                createMaterialIngredients('S', Material.OAK_LOG)
        );
        registerSimple(
                "foraging_axe",
                RecipeCategory.FORAGING,
                foraging.axe(1),
                Rarity.UNCOMMON,
                List.of("SS", "ST", " T"),
                createMixedIngredients(
                        'S',
                        new ItemStack(Material.OAK_LOG),
                        'T',
                        new ItemStack(Material.STICK)
                )
        );
        registerLadderMix(
                "foraging_helmet_2", RecipeCategory.FORAGING, foraging.helmet(2), Rarity.RARE,
                foraging.helmet(1), new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.SPRUCE_LOG));
        registerLadderMix(
                "foraging_chestplate_2", RecipeCategory.FORAGING, foraging.chestplate(2), Rarity.RARE,
                foraging.chestplate(1), new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.SPRUCE_LOG));
        registerLadderMix(
                "foraging_leggings_2", RecipeCategory.FORAGING, foraging.leggings(2), Rarity.RARE,
                foraging.leggings(1), new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.SPRUCE_LOG));
        registerLadderMix(
                "foraging_boots_2", RecipeCategory.FORAGING, foraging.boots(2), Rarity.RARE,
                foraging.boots(1), new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.SPRUCE_LOG));
        registerLadderMix(
                "foraging_axe_2", RecipeCategory.FORAGING, foraging.axe(2), Rarity.RARE,
                foraging.axe(1), new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.SPRUCE_LOG));
        registerLadderMix(
                "foraging_helmet_3", RecipeCategory.FORAGING, foraging.helmet(3), Rarity.EPIC,
                foraging.helmet(2), CompressedResource.JUNGLE_LOG.compressed(), CompressedResource.ACACIA_LOG.compressed());
        registerLadderMix(
                "foraging_chestplate_3", RecipeCategory.FORAGING, foraging.chestplate(3), Rarity.EPIC,
                foraging.chestplate(2), CompressedResource.JUNGLE_LOG.compressed(), CompressedResource.ACACIA_LOG.compressed());
        registerLadderMix(
                "foraging_leggings_3", RecipeCategory.FORAGING, foraging.leggings(3), Rarity.EPIC,
                foraging.leggings(2), CompressedResource.JUNGLE_LOG.compressed(), CompressedResource.ACACIA_LOG.compressed());
        registerLadderMix(
                "foraging_boots_3", RecipeCategory.FORAGING, foraging.boots(3), Rarity.EPIC,
                foraging.boots(2), CompressedResource.JUNGLE_LOG.compressed(), CompressedResource.ACACIA_LOG.compressed());
        registerLadderMix(
                "foraging_axe_3", RecipeCategory.FORAGING, foraging.axe(3), Rarity.EPIC,
                foraging.axe(2), CompressedResource.JUNGLE_LOG.compressed(), CompressedResource.ACACIA_LOG.compressed());
        registerLadderCross(
                "foraging_helmet_4", RecipeCategory.FORAGING, foraging.helmet(4), Rarity.LEGENDARY,
                foraging.helmet(3), CompressedResource.DARK_OAK_LOG.compacted(), CompressedResource.MANGROVE_LOG.compacted());
        registerLadderCross(
                "foraging_chestplate_4", RecipeCategory.FORAGING, foraging.chestplate(4), Rarity.LEGENDARY,
                foraging.chestplate(3), CompressedResource.DARK_OAK_LOG.compacted(), CompressedResource.MANGROVE_LOG.compacted());
        registerLadderCross(
                "foraging_leggings_4", RecipeCategory.FORAGING, foraging.leggings(4), Rarity.LEGENDARY,
                foraging.leggings(3), CompressedResource.DARK_OAK_LOG.compacted(), CompressedResource.MANGROVE_LOG.compacted());
        registerLadderCross(
                "foraging_boots_4", RecipeCategory.FORAGING, foraging.boots(4), Rarity.LEGENDARY,
                foraging.boots(3), CompressedResource.DARK_OAK_LOG.compacted(), CompressedResource.MANGROVE_LOG.compacted());
        registerLadderCross(
                "foraging_axe_4", RecipeCategory.FORAGING, foraging.axe(4), Rarity.LEGENDARY,
                foraging.axe(3), CompressedResource.DARK_OAK_LOG.compacted(), CompressedResource.MANGROVE_LOG.compacted());
        registerLadderPeak(
                "foraging_helmet_5", RecipeCategory.FORAGING, foraging.helmet(5), Rarity.MYTHIC,
                foraging.helmet(4), CompressedResource.CHERRY_LOG.compacted(), CompressedResource.BAMBOO_BLOCK.compacted());
        registerLadderPeak(
                "foraging_chestplate_5", RecipeCategory.FORAGING, foraging.chestplate(5), Rarity.MYTHIC,
                foraging.chestplate(4), CompressedResource.CHERRY_LOG.compacted(), CompressedResource.BAMBOO_BLOCK.compacted());
        registerLadderPeak(
                "foraging_leggings_5", RecipeCategory.FORAGING, foraging.leggings(5), Rarity.MYTHIC,
                foraging.leggings(4), CompressedResource.CHERRY_LOG.compacted(), CompressedResource.BAMBOO_BLOCK.compacted());
        registerLadderPeak(
                "foraging_boots_5", RecipeCategory.FORAGING, foraging.boots(5), Rarity.MYTHIC,
                foraging.boots(4), CompressedResource.CHERRY_LOG.compacted(), CompressedResource.BAMBOO_BLOCK.compacted());
        registerLadderPeak(
                "foraging_axe_5", RecipeCategory.FORAGING, foraging.axe(5), Rarity.MYTHIC,
                foraging.axe(4), CompressedResource.CHERRY_LOG.compacted(), CompressedResource.BAMBOO_BLOCK.compacted());

        FishingItems fishing = customItem.fishing();
        registerSimple(
                "fishing_helmet",
                RecipeCategory.FISHING,
                fishing.helmet(1),
                Rarity.UNCOMMON,
                List.of("CCC", "C C"),
                createMaterialIngredients('C', Material.COD)
        );
        registerSimple(
                "fishing_chestplate",
                RecipeCategory.FISHING,
                fishing.chestplate(1),
                Rarity.UNCOMMON,
                List.of("C C", "CCC", "CCC"),
                createMaterialIngredients('C', Material.COD)
        );
        registerSimple(
                "fishing_leggings",
                RecipeCategory.FISHING,
                fishing.leggings(1),
                Rarity.UNCOMMON,
                List.of("CCC", "C C", "C C"),
                createMaterialIngredients('C', Material.COD)
        );
        registerSimple(
                "fishing_boots",
                RecipeCategory.FISHING,
                fishing.boots(1),
                Rarity.UNCOMMON,
                List.of("C C", "C C"),
                createMaterialIngredients('C', Material.COD)
        );
        registerSimple(
                "fishing_rod",
                RecipeCategory.FISHING,
                fishing.rod(1),
                Rarity.UNCOMMON,
                List.of("  C", " SC", "S  "),
                createMixedIngredients(
                        'C',
                        new ItemStack(Material.COD),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );
        registerLadderMix(
                "fishing_helmet_2", RecipeCategory.FISHING, fishing.helmet(2), Rarity.RARE,
                fishing.helmet(1), new ItemStack(Material.SALMON), new ItemStack(Material.COD));
        registerLadderMix(
                "fishing_chestplate_2", RecipeCategory.FISHING, fishing.chestplate(2), Rarity.RARE,
                fishing.chestplate(1), new ItemStack(Material.SALMON), new ItemStack(Material.COD));
        registerLadderMix(
                "fishing_leggings_2", RecipeCategory.FISHING, fishing.leggings(2), Rarity.RARE,
                fishing.leggings(1), new ItemStack(Material.SALMON), new ItemStack(Material.COD));
        registerLadderMix(
                "fishing_boots_2", RecipeCategory.FISHING, fishing.boots(2), Rarity.RARE,
                fishing.boots(1), new ItemStack(Material.SALMON), new ItemStack(Material.COD));
        registerLadderMix(
                "fishing_rod_2", RecipeCategory.FISHING, fishing.rod(2), Rarity.RARE,
                fishing.rod(1), new ItemStack(Material.SALMON), new ItemStack(Material.COD));
        registerLadderMix(
                "fishing_helmet_3", RecipeCategory.FISHING, fishing.helmet(3), Rarity.EPIC,
                fishing.helmet(2), CompressedResource.PUFFERFISH.compressed(), CompressedResource.SALMON.compressed());
        registerLadderMix(
                "fishing_chestplate_3", RecipeCategory.FISHING, fishing.chestplate(3), Rarity.EPIC,
                fishing.chestplate(2), CompressedResource.PUFFERFISH.compressed(), CompressedResource.SALMON.compressed());
        registerLadderMix(
                "fishing_leggings_3", RecipeCategory.FISHING, fishing.leggings(3), Rarity.EPIC,
                fishing.leggings(2), CompressedResource.PUFFERFISH.compressed(), CompressedResource.SALMON.compressed());
        registerLadderMix(
                "fishing_boots_3", RecipeCategory.FISHING, fishing.boots(3), Rarity.EPIC,
                fishing.boots(2), CompressedResource.PUFFERFISH.compressed(), CompressedResource.SALMON.compressed());
        registerLadderMix(
                "fishing_rod_3", RecipeCategory.FISHING, fishing.rod(3), Rarity.EPIC,
                fishing.rod(2), CompressedResource.PUFFERFISH.compressed(), CompressedResource.SALMON.compressed());
        registerLadderCross(
                "fishing_helmet_4", RecipeCategory.FISHING, fishing.helmet(4), Rarity.LEGENDARY,
                fishing.helmet(3), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.SALMON.compacted());
        registerLadderCross(
                "fishing_chestplate_4", RecipeCategory.FISHING, fishing.chestplate(4), Rarity.LEGENDARY,
                fishing.chestplate(3), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.SALMON.compacted());
        registerLadderCross(
                "fishing_leggings_4", RecipeCategory.FISHING, fishing.leggings(4), Rarity.LEGENDARY,
                fishing.leggings(3), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.SALMON.compacted());
        registerLadderCross(
                "fishing_boots_4", RecipeCategory.FISHING, fishing.boots(4), Rarity.LEGENDARY,
                fishing.boots(3), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.SALMON.compacted());
        registerLadderCross(
                "fishing_rod_4", RecipeCategory.FISHING, fishing.rod(4), Rarity.LEGENDARY,
                fishing.rod(3), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.SALMON.compacted());
        registerLadderPeak(
                "fishing_helmet_5", RecipeCategory.FISHING, fishing.helmet(5), Rarity.MYTHIC,
                fishing.helmet(4), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.PUFFERFISH.compacted());
        registerLadderPeak(
                "fishing_chestplate_5", RecipeCategory.FISHING, fishing.chestplate(5), Rarity.MYTHIC,
                fishing.chestplate(4), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.PUFFERFISH.compacted());
        registerLadderPeak(
                "fishing_leggings_5", RecipeCategory.FISHING, fishing.leggings(5), Rarity.MYTHIC,
                fishing.leggings(4), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.PUFFERFISH.compacted());
        registerLadderPeak(
                "fishing_boots_5", RecipeCategory.FISHING, fishing.boots(5), Rarity.MYTHIC,
                fishing.boots(4), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.PUFFERFISH.compacted());
        registerLadderPeak(
                "fishing_rod_5", RecipeCategory.FISHING, fishing.rod(5), Rarity.MYTHIC,
                fishing.rod(4), CompressedResource.PRISMARINE_SHARD.compacted(), CompressedResource.PUFFERFISH.compacted());

        registerCharms(customItem.accessories());

        /*
         * =====================================================
         * STARTER RANGED / MAGIC
         * =====================================================
         */

        Map<Character, ItemStack> longbow = new LinkedHashMap<>();
        longbow.put('S', new ItemStack(Material.STICK));
        longbow.put('G', new ItemStack(Material.STRING));
        registerSimple(
                "simple_longbow",
                RecipeCategory.COMBAT,
                customItem.createSimpleLongbow(),
                Rarity.COMMON,
                List.of("SSG", "SSG", "SSG"),
                longbow
        );

        registerSimple(
                "simple_shortbow",
                RecipeCategory.COMBAT,
                customItem.createSimpleShortbow(),
                Rarity.COMMON,
                List.of("SG", "SG"),
                createMixedIngredients(
                        'S',
                        new ItemStack(Material.STICK),
                        'G',
                        new ItemStack(Material.STRING)
                )
        );

        Map<Character, ItemStack> rod = new LinkedHashMap<>();
        rod.put('S', new ItemStack(Material.STICK));
        rod.put('G', new ItemStack(Material.STRING));
        registerSimple(
                "wooden_rod",
                RecipeCategory.COMBAT,
                customItem.createWoodenRod(),
                Rarity.COMMON,
                List.of("  S", " SG", "SG "),
                rod
        );

        registerSimple(
                "splinter_glaive",
                RecipeCategory.COMBAT,
                customItem.createSplinterGlaive(),
                Rarity.UNCOMMON,
                List.of("  O", " S ", "S  "),
                createMixedIngredients(
                        'O',
                        new ItemStack(Material.OAK_LOG),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );

        {
            Map<Character, ItemStack> cleaver = new LinkedHashMap<>();
            cleaver.put('C', CompressedResource.COAL.compressed());
            cleaver.put('I', new ItemStack(Material.IRON_INGOT));
            cleaver.put('S', new ItemStack(Material.STICK));
            registerSimple(
                    "ashen_cleaver",
                    RecipeCategory.COMBAT,
                    customItem.createAshenCleaver(),
                    Rarity.RARE,
                    List.of(" CC", " IC", "S  "),
                    cleaver
            );
        }

        registerSimple(
                "bone_knife",
                RecipeCategory.COMBAT,
                customItem.createBoneKnife(),
                Rarity.COMMON,
                List.of("B", "B", "F"),
                createMixedIngredients(
                        'B',
                        new ItemStack(Material.BONE),
                        'F',
                        new ItemStack(Material.FLINT)
                )
        );

        Map<Character, ItemStack> dagger = new LinkedHashMap<>();
        dagger.put('G', new ItemStack(Material.GOLD_INGOT));
        dagger.put('S', new ItemStack(Material.STICK));
        dagger.put('E', new ItemStack(Material.SPIDER_EYE));
        registerSimple(
                "venom_dagger",
                RecipeCategory.COMBAT,
                customItem.createVenomDagger(),
                Rarity.UNCOMMON,
                List.of("G", "S", "E"),
                dagger
        );

        registerSimple(
                "iron_longbow",
                RecipeCategory.COMBAT,
                customItem.createIronLongbow(),
                Rarity.UNCOMMON,
                List.of("III", "ILI", "III"),
                createMixedIngredients(
                        'I',
                        new ItemStack(Material.IRON_INGOT),
                        'L',
                        customItem.createSimpleLongbow()
                )
        );

        registerSimple(
                "reinforced_shortbow",
                RecipeCategory.COMBAT,
                customItem.createReinforcedShortbow(),
                Rarity.UNCOMMON,
                List.of(" I ", "ISI", " I "),
                createMixedIngredients(
                        'I',
                        new ItemStack(Material.IRON_INGOT),
                        'S',
                        customItem.createSimpleShortbow()
                )
        );

        registerSimple(
                "copper_rod",
                RecipeCategory.COMBAT,
                customItem.createCopperRod(),
                Rarity.UNCOMMON,
                List.of(" C ", "CRC", " C "),
                createMixedIngredients(
                        'C',
                        new ItemStack(Material.COPPER_INGOT),
                        'R',
                        customItem.createWoodenRod()
                )
        );

        Map<Character, ItemStack> frost = new LinkedHashMap<>();
        frost.put('P', new ItemStack(Material.PACKED_ICE));
        frost.put('S', new ItemStack(Material.SNOWBALL));
        frost.put('A', new ItemStack(Material.AMETHYST_CLUSTER));
        registerSimple(
                "frost_shard",
                RecipeCategory.COMBAT,
                customItem.createFrostShard(),
                Rarity.UNCOMMON,
                List.of(" P ", "SAS", " P "),
                frost
        );

        registerSimple(
                "rotten_helmet",
                RecipeCategory.ARMOR,
                customItem.createRottenHelmet(),
                Rarity.UNCOMMON,
                List.of("RRR", "R R"),
                createMaterialIngredients('R', Material.ROTTEN_FLESH)
        );
        registerSimple(
                "rotten_chestplate",
                RecipeCategory.ARMOR,
                customItem.createRottenChestplate(),
                Rarity.UNCOMMON,
                List.of("R R", "RRR", "RRR"),
                createMaterialIngredients('R', Material.ROTTEN_FLESH)
        );
        registerSimple(
                "rotten_leggings",
                RecipeCategory.ARMOR,
                customItem.createRottenLeggings(),
                Rarity.UNCOMMON,
                List.of("RRR", "R R", "R R"),
                createMaterialIngredients('R', Material.ROTTEN_FLESH)
        );
        registerSimple(
                "rotten_boots",
                RecipeCategory.ARMOR,
                customItem.createRottenBoots(),
                Rarity.UNCOMMON,
                List.of("R R", "R R"),
                createMaterialIngredients('R', Material.ROTTEN_FLESH)
        );

        registerSimple(
                "bone_helmet",
                RecipeCategory.ARMOR,
                customItem.createBoneHelmet(),
                Rarity.UNCOMMON,
                List.of("BBB", "B B"),
                createMaterialIngredients('B', Material.BONE)
        );
        registerSimple(
                "bone_chestplate",
                RecipeCategory.ARMOR,
                customItem.createBoneChestplate(),
                Rarity.UNCOMMON,
                List.of("B B", "BBB", "BBB"),
                createMaterialIngredients('B', Material.BONE)
        );
        registerSimple(
                "bone_leggings",
                RecipeCategory.ARMOR,
                customItem.createBoneLeggings(),
                Rarity.UNCOMMON,
                List.of("BBB", "B B", "B B"),
                createMaterialIngredients('B', Material.BONE)
        );
        registerSimple(
                "bone_boots",
                RecipeCategory.ARMOR,
                customItem.createBoneBoots(),
                Rarity.UNCOMMON,
                List.of("B B", "B B"),
                createMaterialIngredients('B', Material.BONE)
        );

        registerSimple(
                "webweave_helmet",
                RecipeCategory.ARMOR,
                customItem.createWebweaveHelmet(),
                Rarity.UNCOMMON,
                List.of("WWW", "W W"),
                createMaterialIngredients('W', Material.STRING)
        );
        registerSimple(
                "webweave_chestplate",
                RecipeCategory.ARMOR,
                customItem.createWebweaveChestplate(),
                Rarity.UNCOMMON,
                List.of("W W", "WWW", "WWW"),
                createMaterialIngredients('W', Material.STRING)
        );
        registerSimple(
                "webweave_leggings",
                RecipeCategory.ARMOR,
                customItem.createWebweaveLeggings(),
                Rarity.UNCOMMON,
                List.of("WWW", "W W", "W W"),
                createMaterialIngredients('W', Material.STRING)
        );
        registerSimple(
                "webweave_boots",
                RecipeCategory.ARMOR,
                customItem.createWebweaveBoots(),
                Rarity.UNCOMMON,
                List.of("W W", "W W"),
                createMaterialIngredients('W', Material.STRING)
        );

        /*
         * =====================================================
         * IRONHIDE ARMOR (Tank, EPIC)
         * =====================================================
         */

        registerSimple(
                "ironhide_helmet",
                RecipeCategory.ARMOR,
                customItem.createIronhideHelmet(),
                Rarity.EPIC,
                List.of("CCC", "I I"),
                createMixedIngredients('C', CompressedResource.RAW_IRON.compacted(), 'I', new ItemStack(Material.IRON_BLOCK))
        );
        registerSimple(
                "ironhide_chestplate",
                RecipeCategory.ARMOR,
                customItem.createIronhideChestplate(),
                Rarity.EPIC,
                List.of("C C", "ICI", "III"),
                createMixedIngredients('C', CompressedResource.RAW_IRON.compacted(), 'I', new ItemStack(Material.IRON_BLOCK))
        );
        registerSimple(
                "ironhide_leggings",
                RecipeCategory.ARMOR,
                customItem.createIronhideLeggings(),
                Rarity.EPIC,
                List.of("CCC", "I I", "I I"),
                createMixedIngredients('C', CompressedResource.RAW_IRON.compacted(), 'I', new ItemStack(Material.IRON_BLOCK))
        );
        registerSimple(
                "ironhide_boots",
                RecipeCategory.ARMOR,
                customItem.createIronhideBoots(),
                Rarity.EPIC,
                List.of("C C", "I I"),
                createMixedIngredients('C', CompressedResource.RAW_IRON.compacted(), 'I', new ItemStack(Material.IRON_BLOCK))
        );

        /*
         * =====================================================
         * HEALER ARMOR (Support, RARE)
         * =====================================================
         */

        registerSimple(
                "healer_helmet",
                RecipeCategory.ARMOR,
                customItem.createHealerHelmet(),
                Rarity.RARE,
                List.of("CCC", "G G"),
                createMixedIngredients('C', CompressedResource.EMERALD.compressed(), 'G', new ItemStack(Material.GOLD_INGOT))
        );
        registerSimple(
                "healer_chestplate",
                RecipeCategory.ARMOR,
                customItem.createHealerChestplate(),
                Rarity.RARE,
                List.of("C C", "GCG", "GGG"),
                createMixedIngredients('C', CompressedResource.EMERALD.compressed(), 'G', new ItemStack(Material.GOLD_INGOT))
        );
        registerSimple(
                "healer_leggings",
                RecipeCategory.ARMOR,
                customItem.createHealerLeggings(),
                Rarity.RARE,
                List.of("CCC", "G G", "G G"),
                createMixedIngredients('C', CompressedResource.EMERALD.compressed(), 'G', new ItemStack(Material.GOLD_INGOT))
        );
        registerSimple(
                "healer_boots",
                RecipeCategory.ARMOR,
                customItem.createHealerBoots(),
                Rarity.RARE,
                List.of("C C", "G G"),
                createMixedIngredients('C', CompressedResource.EMERALD.compressed(), 'G', new ItemStack(Material.GOLD_INGOT))
        );

        /*
         * =====================================================
         * MENDER STAFF / EMBER ROD / REINFORCED PICKAXE / OBSIDIAN MAUL
         * =====================================================
         */

        {
            Map<Character, ItemStack> mender = new LinkedHashMap<>();
            mender.put('E', CompressedResource.EMERALD.compressed());
            mender.put('B', new ItemStack(Material.BLAZE_ROD));
            mender.put('G', new ItemStack(Material.GOLD_INGOT));
            registerSimple(
                    "mender_staff",
                    RecipeCategory.COMBAT,
                    customItem.createMenderStaff(),
                    Rarity.RARE,
                    List.of(" E ", "GBG", " G "),
                    mender
            );
        }

        {
            Map<Character, ItemStack> ember = new LinkedHashMap<>();
            ember.put('R', CompressedResource.REDSTONE.compressed());
            ember.put('B', new ItemStack(Material.BLAZE_ROD));
            ember.put('M', new ItemStack(Material.MAGMA_CREAM));
            registerSimple(
                    "ember_rod",
                    RecipeCategory.COMBAT,
                    customItem.createEmberRod(),
                    Rarity.RARE,
                    List.of(" R ", "MBM", " R "),
                    ember
            );
        }

        registerSimple(
                "reinforced_pickaxe",
                RecipeCategory.MINING,
                customItem.createReinforcedPickaxe(),
                Rarity.RARE,
                List.of("CCC", "CPC", "CCC"),
                createMixedIngredients('C', CompressedResource.RAW_IRON.compressed(), 'P', customItem.createSimplePickaxe())
        );

        {
            Map<Character, ItemStack> maul = new LinkedHashMap<>();
            maul.put('C', CompressedResource.COBBLESTONE.compacted());
            maul.put('O', new ItemStack(Material.OBSIDIAN));
            maul.put('S', new ItemStack(Material.STICK));
            registerSimple(
                    "obsidian_maul",
                    RecipeCategory.COMBAT,
                    customItem.createObsidianMaul(),
                    Rarity.RARE,
                    List.of("COC", " S ", " S "),
                    maul
            );
        }

        registerMaterialProgression();

        /*
         * =====================================================
         * AETHER MOBS / CATCHER ARMOR
         * =====================================================
         */

        registerSimple(
                "catcher_helmet",
                RecipeCategory.AETHER_MOBS,
                customItem.catcher().helmet(1),
                Rarity.RARE,
                List.of(
                        "HHH",
                        "H H"
                ),
                createMaterialIngredients(
                        'H',
                        Material.HAY_BLOCK
                )
        );

        registerSimple(
                "catcher_chestplate",
                RecipeCategory.AETHER_MOBS,
                customItem.catcher().chestplate(1),
                Rarity.RARE,
                List.of(
                        "H H",
                        "HHH",
                        "HHH"
                ),
                createMaterialIngredients(
                        'H',
                        Material.HAY_BLOCK
                )
        );

        registerSimple(
                "catcher_leggings",
                RecipeCategory.AETHER_MOBS,
                customItem.catcher().leggings(1),
                Rarity.RARE,
                List.of(
                        "HHH",
                        "H H",
                        "H H"
                ),
                createMaterialIngredients(
                        'H',
                        Material.HAY_BLOCK
                )
        );

        registerSimple(
                "catcher_boots",
                RecipeCategory.AETHER_MOBS,
                customItem.catcher().boots(1),
                Rarity.RARE,
                List.of(
                        "H H",
                        "H H"
                ),
                createMaterialIngredients(
                        'H',
                        Material.HAY_BLOCK
                )
        );

        registerSimple(
                "catcher_gaff",
                RecipeCategory.AETHER_MOBS,
                customItem.catcher().gaff(1),
                Rarity.RARE,
                List.of(" H ", " S ", " S "),
                createMixedIngredients(
                        'H',
                        new ItemStack(Material.HAY_BLOCK),
                        'S',
                        new ItemStack(Material.STICK)
                )
        );

        CatcherItems catcher = customItem.catcher();
        registerPlusUpgrade(
                "catcher_helmet_2", RecipeCategory.AETHER_MOBS, catcher.helmet(2), Rarity.EPIC,
                catcher.helmet(1), new ItemStack(Material.FEATHER));
        registerPlusUpgrade(
                "catcher_chestplate_2", RecipeCategory.AETHER_MOBS, catcher.chestplate(2), Rarity.EPIC,
                catcher.chestplate(1), new ItemStack(Material.FEATHER));
        registerPlusUpgrade(
                "catcher_leggings_2", RecipeCategory.AETHER_MOBS, catcher.leggings(2), Rarity.EPIC,
                catcher.leggings(1), new ItemStack(Material.FEATHER));
        registerPlusUpgrade(
                "catcher_boots_2", RecipeCategory.AETHER_MOBS, catcher.boots(2), Rarity.EPIC,
                catcher.boots(1), new ItemStack(Material.FEATHER));
        registerPlusUpgrade(
                "catcher_gaff_2", RecipeCategory.AETHER_MOBS, catcher.gaff(2), Rarity.EPIC,
                catcher.gaff(1), new ItemStack(Material.FEATHER));
        registerPlusUpgrade(
                "catcher_helmet_3", RecipeCategory.AETHER_MOBS, catcher.helmet(3), Rarity.LEGENDARY,
                catcher.helmet(2), CompressedResource.FEATHER.compacted());
        registerPlusUpgrade(
                "catcher_chestplate_3", RecipeCategory.AETHER_MOBS, catcher.chestplate(3), Rarity.LEGENDARY,
                catcher.chestplate(2), CompressedResource.FEATHER.compacted());
        registerPlusUpgrade(
                "catcher_leggings_3", RecipeCategory.AETHER_MOBS, catcher.leggings(3), Rarity.LEGENDARY,
                catcher.leggings(2), CompressedResource.FEATHER.compacted());
        registerPlusUpgrade(
                "catcher_boots_3", RecipeCategory.AETHER_MOBS, catcher.boots(3), Rarity.LEGENDARY,
                catcher.boots(2), CompressedResource.FEATHER.compacted());
        registerPlusUpgrade(
                "catcher_gaff_3", RecipeCategory.AETHER_MOBS, catcher.gaff(3), Rarity.LEGENDARY,
                catcher.gaff(2), CompressedResource.FEATHER.compacted());

        registerResourceCompression();
        registerSacks();
    }

    private void registerCharms(AccessoryItems charms) {
        registerSimple(
                "charm_combat",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.COMBAT, 1),
                Rarity.UNCOMMON,
                List.of("MB", "BM"),
                createMixedIngredients('M', new ItemStack(Material.GUNPOWDER), 'B', new ItemStack(Material.BONE))
        );
        registerSimple(
                "charm_mining",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.MINING, 1),
                Rarity.UNCOMMON,
                List.of("CI", "IC"),
                createMixedIngredients('C', new ItemStack(Material.COPPER_INGOT), 'I', new ItemStack(Material.COBBLESTONE))
        );
        registerSimple(
                "charm_foraging",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.FORAGING, 1),
                Rarity.UNCOMMON,
                List.of("SA", "AS"),
                createMixedIngredients('S', new ItemStack(Material.OAK_LOG), 'A', new ItemStack(Material.BIRCH_LOG))
        );
        registerSimple(
                "charm_farming",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.FARMING, 1),
                Rarity.UNCOMMON,
                List.of("WB", "BW"),
                createMixedIngredients('W', new ItemStack(Material.WHEAT), 'B', new ItemStack(Material.CARROT))
        );
        registerSimple(
                "charm_fishing",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.FISHING, 1),
                Rarity.UNCOMMON,
                List.of("CS", "SC"),
                createMixedIngredients('C', new ItemStack(Material.COD), 'S', new ItemStack(Material.SALMON))
        );
        registerSimple(
                "charm_utility",
                RecipeCategory.CHARMS,
                charms.charm(AccessoryItems.Charm.UTILITY, 1),
                Rarity.UNCOMMON,
                List.of("AG", "GA"),
                createMixedIngredients('A', new ItemStack(Material.LAPIS_LAZULI), 'G', new ItemStack(Material.REDSTONE))
        );

        registerLadderPlus(
                "charm_combat_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.COMBAT, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.COMBAT, 1), CompressedResource.BONE.compressed());
        registerLadderPlus(
                "charm_mining_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.MINING, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.MINING, 1), CompressedResource.RAW_COPPER.compressed());
        registerLadderPlus(
                "charm_foraging_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FORAGING, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.FORAGING, 1), CompressedResource.OAK_LOG.compressed());
        registerLadderPlus(
                "charm_farming_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FARMING, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.FARMING, 1), CompressedResource.CARROT.compressed());
        registerLadderPlus(
                "charm_fishing_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FISHING, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.FISHING, 1), CompressedResource.COD.compressed());
        registerLadderPlus(
                "charm_utility_2", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.UTILITY, 2), Rarity.RARE,
                charms.charm(AccessoryItems.Charm.UTILITY, 1), CompressedResource.LAPIS.compressed());

        registerLadderPlus(
                "charm_combat_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.COMBAT, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.COMBAT, 2), CompressedResource.GUNPOWDER.compacted());
        registerLadderPlus(
                "charm_mining_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.MINING, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.MINING, 2), CompressedResource.RAW_IRON.compacted());
        registerLadderPlus(
                "charm_foraging_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FORAGING, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.FORAGING, 2), CompressedResource.OAK_LOG.compacted());
        registerLadderPlus(
                "charm_farming_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FARMING, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.FARMING, 2), CompressedResource.POTATO.compacted());
        registerLadderPlus(
                "charm_fishing_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.FISHING, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.FISHING, 2), CompressedResource.COD.compacted());
        registerLadderPlus(
                "charm_utility_3", RecipeCategory.CHARMS, charms.charm(AccessoryItems.Charm.UTILITY, 3), Rarity.EPIC,
                charms.charm(AccessoryItems.Charm.UTILITY, 2), CompressedResource.LAPIS.compacted());

        Map<Character, ItemStack> shiny = new LinkedHashMap<>();
        shiny.put('G', CompressedResource.RAW_GOLD.compressed());
        shiny.put('I', new ItemStack(Material.GOLD_INGOT));
        shiny.put('B', CompressedResource.BONE.compressed());
        registerSimple(
                AccessoryItems.SHINY_ID,
                RecipeCategory.CHARMS,
                charms.shinyCharm(),
                Rarity.RARE,
                List.of("GI", "IB"),
                shiny
        );

        Map<Character, ItemStack> forge = new LinkedHashMap<>();
        forge.put('C', CompressedResource.COBBLESTONE.compacted());
        forge.put('F', CompressedResource.COAL.compressed());
        forge.put('I', CompressedResource.RAW_IRON.compressed());
        forge.put('O', CompressedResource.OAK_LOG.compressed());
        registerSimple(
                AccessoryItems.FORGE_ID,
                RecipeCategory.CHARMS,
                charms.forgeCharm(),
                Rarity.RARE,
                List.of("CF", "IO"),
                forge
        );

        Map<Character, ItemStack> estate = new LinkedHashMap<>();
        estate.put('S', CompressedResource.COBBLESTONE.compressed());
        estate.put('L', new ItemStack(Material.LEATHER));
        estate.put('B', new ItemStack(Material.BONE));
        estate.put('G', new ItemStack(Material.GOLD_INGOT));
        registerSimple(
                AccessoryItems.ESTATE_ID,
                RecipeCategory.CHARMS,
                charms.estateCharm(),
                Rarity.RARE,
                List.of("SL", "BG"),
                estate
        );
    }

    private void registerResourceCompression() {
        UnlockRequirement islandLevel = new AccountLevelRequirement(de.aetherion.items.progress.ProgressionService.ISLAND_LEVEL);
        for (CompressedResource resource : CompressedResource.contentOrdered()) {
            UnlockRequirement obtained = new MaterialObtainedRequirement(resource);
            ItemStack twoStacks = new ItemStack(resource.input(), 64);
            registerSimple(
                    resource.compressedId(),
                    RecipeCategory.RESOURCES,
                    resource.compressed(),
                    Rarity.UNCOMMON,
                    List.of("CC"),
                    Map.of('C', twoStacks),
                    obtained
            );

            ItemStack thirtyTwoCompressed = resource.compressed();
            thirtyTwoCompressed.setAmount(32);
            registerSimple(
                    resource.compactedId(),
                    RecipeCategory.RESOURCES,
                    resource.compacted(),
                    Rarity.RARE,
                    List.of("CC", "CC"),
                    Map.of('C', thirtyTwoCompressed),
                    obtained
            );

            if (resource.hasQuarry()) {
                Map<Character, ItemStack> quarryIngredients = new LinkedHashMap<>();
                quarryIngredients.put('C', resource.compressed());
                quarryIngredients.put('K', QuarryItems.core());
                registerSimple(
                        resource.quarryItemId(),
                        RecipeCategory.RESOURCES,
                        QuarryItems.quarry(resource),
                        Rarity.RARE,
                        List.of("CCC", "CKC", "CCC"),
                        quarryIngredients,
                        new AllUnlockRequirements(obtained, islandLevel)
                );
            }
        }

        registerSimple(
                QuarryItems.CORE_ID,
                RecipeCategory.RESOURCES,
                QuarryItems.core(),
                Rarity.EPIC,
                List.of(" S ", "S S", " S "),
                Map.of('S', QuarryItems.shard()),
                islandLevel
        );

        Map<Character, ItemStack> mill = new LinkedHashMap<>();
        mill.put('C', CompressedResource.COBBLESTONE.compacted());
        mill.put('K', QuarryItems.core());
        registerSimple(
                QuarryItems.COMPRESSOR_ID,
                RecipeCategory.RESOURCES,
                QuarryItems.compressor(),
                Rarity.RARE,
                List.of("CCC", "CKC", "CCC"),
                mill,
                new AllUnlockRequirements(islandLevel, new CraftedPredecessorRequirement(QuarryItems.CORE_ID))
        );

        Map<Character, ItemStack> forge = new LinkedHashMap<>();
        forge.put('D', CompressedResource.DIAMOND.compacted());
        forge.put('M', QuarryItems.compressor());
        registerSimple(
                QuarryItems.COMPACTOR_ID,
                RecipeCategory.RESOURCES,
                QuarryItems.compactor(),
                Rarity.EPIC,
                List.of("DDD", "DMD", "DDD"),
                forge,
                new AllUnlockRequirements(islandLevel, new CraftedPredecessorRequirement(QuarryItems.COMPRESSOR_ID))
        );
    }

    private void registerSacks() {
        Map<Character, ItemStack> resource = new LinkedHashMap<>();
        resource.put('L', new ItemStack(Material.LEATHER));
        resource.put('C', new ItemStack(Material.OAK_LOG, 2));
        resource.put('S', new ItemStack(Material.STRING));
        registerSimple(
                "resource_sack",
                RecipeCategory.RESOURCES,
                de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.RESOURCE),
                Rarity.UNCOMMON,
                List.of("LSL", "LCL", "LSL"),
                resource
        );

        Map<Character, ItemStack> booster = new LinkedHashMap<>();
        booster.put('L', new ItemStack(Material.LEATHER));
        booster.put('C', new ItemStack(Material.OAK_LOG, 2));
        booster.put('G', new ItemStack(Material.GOLD_INGOT));
        registerSimple(
                "booster_sack",
                RecipeCategory.BOOSTERS,
                de.aetherion.items.storage.SackItems.create(de.aetherion.items.storage.SackType.BOOSTER),
                Rarity.UNCOMMON,
                List.of("LGL", "LCL", "LGL"),
                booster
        );
    }

    private void registerMaterialProgression() {
        ProgressionItems gear = customItem.progression();
        ItemStack stick = new ItemStack(Material.STICK);

        registerSimple(
                "compressed_oak_chestplate",
                RecipeCategory.ARMOR,
                gear.createCompressedOakChestplate(),
                Rarity.UNCOMMON,
                List.of("CCC", "CLC", "CCC"),
                createMixedIngredients('C', CompressedResource.OAK_LOG.compressed(), 'L', new ItemStack(Material.LEATHER))
        );
        {
            Map<Character, ItemStack> axe = new LinkedHashMap<>();
            axe.put('C', CompressedResource.OAK_LOG.compacted());
            axe.put('A', customItem.foraging().axe(2));
            axe.put('S', stick);
            registerSimple(
                    "compacted_timber_axe",
                    RecipeCategory.FORAGING,
                    gear.createCompactedTimberAxe(),
                    Rarity.RARE,
                    List.of(" C ", " A ", " S "),
                    axe,
                    new CraftedPredecessorRequirement("foraging_axe_2")
            );
        }
        registerSimple(
                "compressed_stone_pickaxe",
                RecipeCategory.MINING,
                gear.createCompressedStonePickaxe(),
                Rarity.UNCOMMON,
                List.of("CCC", " S ", " S "),
                createMixedIngredients('C', CompressedResource.COBBLESTONE.compressed(), 'S', stick)
        );
        {
            Map<Character, ItemStack> hammer = new LinkedHashMap<>();
            hammer.put('C', CompressedResource.COBBLESTONE.compacted());
            hammer.put('P', gear.createCompressedStonePickaxe());
            hammer.put('S', stick);
            registerSimple(
                    "compacted_cobble_hammer",
                    RecipeCategory.MINING,
                    gear.createCompactedCobbleHammer(),
                    Rarity.RARE,
                    List.of(" C ", " P ", " S "),
                    hammer,
                    new CraftedPredecessorRequirement("compressed_stone_pickaxe")
            );
        }
        registerSimple(
                "compressed_coal_ring",
                RecipeCategory.CHARMS,
                gear.createCompressedCoalRing(),
                Rarity.UNCOMMON,
                List.of("CCC", "CNC", "CCC"),
                createMixedIngredients('C', CompressedResource.COAL.compressed(), 'N', new ItemStack(Material.GOLD_INGOT))
        );
        registerSimple(
                "copper_sword",
                RecipeCategory.COMBAT,
                gear.createCopperSword(),
                Rarity.UNCOMMON,
                List.of(" C ", " C ", " S "),
                createMixedIngredients('C', CompressedResource.RAW_COPPER.compressed(), 'S', stick)
        );
        registerSimple(
                "compressed_gold_sword",
                RecipeCategory.COMBAT,
                gear.createCompressedGoldSword(),
                Rarity.RARE,
                List.of(" G ", " G ", " S "),
                createMixedIngredients('G', CompressedResource.RAW_GOLD.compressed(), 'S', stick)
        );
        {
            Map<Character, ItemStack> midas = new LinkedHashMap<>();
            midas.put('G', CompressedResource.RAW_GOLD.compacted());
            midas.put('D', gear.createCompressedGoldSword());
            midas.put('S', stick);
            registerSimple(
                    "compacted_midas_dagger",
                    RecipeCategory.COMBAT,
                    gear.createCompactedMidasDagger(),
                    Rarity.EPIC,
                    List.of(" G ", "GDG", " S "),
                    midas,
                    new CraftedPredecessorRequirement("compressed_gold_sword")
            );
        }
        registerSimple(
                "redstone_infused_boots",
                RecipeCategory.ARMOR,
                gear.createRedstoneInfusedBoots(),
                Rarity.RARE,
                List.of(" R ", "RBR", " R "),
                createMixedIngredients('R', CompressedResource.REDSTONE.compacted(), 'B', customItem.createCombatBoots2()),
                new CraftedPredecessorRequirement("combat_boots_2")
        );
        registerSimple(
                "lapis_pendant",
                RecipeCategory.CHARMS,
                gear.createLapisPendant(),
                Rarity.RARE,
                List.of("LLL", "LAL", "LLL"),
                createMixedIngredients('L', CompressedResource.LAPIS.compressed(), 'A', new ItemStack(Material.EMERALD))
        );
        {
            Map<Character, ItemStack> diamondChest = new LinkedHashMap<>();
            diamondChest.put('D', CompressedResource.DIAMOND.compacted());
            diamondChest.put('C', customItem.createCombatChestplate3());
            registerSimple(
                    "compacted_diamond_chestplate",
                    RecipeCategory.ARMOR,
                    gear.createCompactedDiamondChestplate(),
                    Rarity.LEGENDARY,
                    List.of(" D ", " C ", " D "),
                    diamondChest,
                    new CraftedPredecessorRequirement("combat_chestplate_3")
            );
        }
        {
            Map<Character, ItemStack> diamondSword = new LinkedHashMap<>();
            diamondSword.put('D', CompressedResource.DIAMOND.compacted());
            diamondSword.put('N', new ItemStack(Material.NETHERITE_SCRAP));
            diamondSword.put('C', customItem.createCombatSword3());
            registerSimple(
                    "compacted_diamond_sword",
                    RecipeCategory.COMBAT,
                    gear.createCompactedDiamondSword(),
                    Rarity.LEGENDARY,
                    List.of(" D ", " C ", " N "),
                    diamondSword,
                    new CraftedPredecessorRequirement("combat_sword_3")
            );
        }
        {
            Map<Character, ItemStack> crown = new LinkedHashMap<>();
            crown.put('E', CompressedResource.EMERALD.compacted());
            crown.put('H', customItem.createCombatHelmet3());
            registerSimple(
                    "emerald_crown",
                    RecipeCategory.ARMOR,
                    gear.createEmeraldCrown(),
                    Rarity.LEGENDARY,
                    List.of(" E ", " H ", " E "),
                    crown,
                    new CraftedPredecessorRequirement("combat_helmet_3")
            );
        }
        {
            Map<Character, ItemStack> scythe = new LinkedHashMap<>();
            scythe.put('E', CompressedResource.EMERALD.compacted());
            scythe.put('D', CompressedResource.DIAMOND.compacted());
            scythe.put('C', customItem.createCombatSword3());
            registerSimple(
                    "compacted_emerald_scythe",
                    RecipeCategory.COMBAT,
                    gear.createCompactedEmeraldScythe(),
                    Rarity.LEGENDARY,
                    List.of(" E ", " C ", " D "),
                    scythe,
                    new CraftedPredecessorRequirement("combat_sword_3")
            );
        }
        {
            Map<Character, ItemStack> ironPick = new LinkedHashMap<>();
            ironPick.put('I', CompressedResource.RAW_IRON.compressed());
            ironPick.put('P', gear.createCompressedStonePickaxe());
            registerSimple(
                    "compacted_iron_pickaxe",
                    RecipeCategory.MINING,
                    gear.createCompactedIronPickaxe(),
                    Rarity.EPIC,
                    List.of(" I ", " P ", " I "),
                    ironPick,
                    new CraftedPredecessorRequirement("compressed_stone_pickaxe")
            );
        }
        {
            Map<Character, ItemStack> diamondPick = new LinkedHashMap<>();
            diamondPick.put('D', CompressedResource.DIAMOND.compacted());
            diamondPick.put('P', gear.createCompactedIronPickaxe());
            registerSimple(
                    "compacted_diamond_pickaxe",
                    RecipeCategory.MINING,
                    gear.createCompactedDiamondPickaxe(),
                    Rarity.LEGENDARY,
                    List.of(" D ", " P ", " D "),
                    diamondPick,
                    new CraftedPredecessorRequirement("compacted_iron_pickaxe")
            );
        }

        /*
         * Blueprint Upgrade Stones — steep Eldervale progression (II/III/IV).
         * Stone II unlocks on Eldervale discover (no fake T1). III/IV chain off prior craft.
         */
        {
            Map<Character, ItemStack> stone2 = new LinkedHashMap<>();
            stone2.put('I', CompressedResource.RAW_IRON.compacted());
            stone2.put('D', CompressedResource.DIAMOND.compressed());
            registerSimple(
                    "blueprint_upgrade_stone_2",
                    RecipeCategory.MINING,
                    customItem.createBlueprintUpgradeStone2(),
                    Rarity.EPIC,
                    List.of("III", "IDI", "III"),
                    stone2,
                    new HubSpawnUnlockRequirement("eldervale", "Discover Eldervale")
            );
        }
        {
            Map<Character, ItemStack> stone3 = new LinkedHashMap<>();
            stone3.put('D', CompressedResource.DIAMOND.compacted());
            stone3.put('N', new ItemStack(Material.NETHERITE_SCRAP));
            stone3.put('G', CompressedResource.RAW_GOLD.compacted());
            registerSimple(
                    "blueprint_upgrade_stone_3",
                    RecipeCategory.MINING,
                    customItem.createBlueprintUpgradeStone3(),
                    Rarity.LEGENDARY,
                    List.of("DDD", "GNG", "DDD"),
                    stone3,
                    new CraftedPredecessorRequirement("blueprint_upgrade_stone_2")
            );
        }
        {
            Map<Character, ItemStack> stone4 = new LinkedHashMap<>();
            stone4.put('D', CompressedResource.DIAMOND.compacted());
            stone4.put('A', new ItemStack(Material.ANCIENT_DEBRIS));
            stone4.put('B', new ItemStack(Material.NETHERITE_BLOCK));
            registerSimple(
                    "blueprint_upgrade_stone_4",
                    RecipeCategory.MINING,
                    customItem.createBlueprintUpgradeStone4(),
                    Rarity.MYTHIC,
                    List.of("DDD", "ABA", "DDD"),
                    stone4,
                    new CraftedPredecessorRequirement("blueprint_upgrade_stone_3")
            );
        }
    }



    /*
     * =========================================================
     * REGISTER SIMPLE
     * =========================================================
     */

    private void registerSimple(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            List<String> shape,
            Map<Character, ItemStack> ingredients
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                shape,
                ingredients,
                UnlockRequirement.ALWAYS_UNLOCKED
        );
    }

    private void registerSimple(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            List<String> shape,
            Map<Character, ItemStack> ingredients,
            UnlockRequirement unlockRequirement
    ) {

        UnlockRequirement unlock = unlockRequirement;
        if (unlock == UnlockRequirement.ALWAYS_UNLOCKED) {
            String predecessor = RecipeUnlockService.predecessorRecipeId(id);
            if (predecessor != null) {
                unlock = new CraftedPredecessorRequirement(predecessor);
            }
        }

        recipeManager.register(
                new RecipeDefinition(
                        id,
                        category,
                        result,
                        rarity,
                        shape,
                        ingredients,
                        unlock
                )
        );
    }


    /*
     * =========================================================
     * MATERIAL INGREDIENTS
     * =========================================================
     */

    private Map<Character, ItemStack> createMaterialIngredients(
            char key,
            Material material
    ) {
        return createMaterialIngredients(key, material, 1);
    }

    private Map<Character, ItemStack> createMaterialIngredients(
            char key,
            Material material,
            int amount
    ) {

        Map<Character, ItemStack> ingredients =
                new LinkedHashMap<>();

        ingredients.put(
                key,
                new ItemStack(material, Math.max(1, amount))
        );

        return ingredients;
    }


    /*
     * =========================================================
     * MIXED INGREDIENTS
     * =========================================================
     */

    private Map<Character, ItemStack> createMixedIngredients(
            char firstKey,
            ItemStack firstItem,
            char secondKey,
            ItemStack secondItem
    ) {

        Map<Character, ItemStack> ingredients =
                new LinkedHashMap<>();

        ingredients.put(
                firstKey,
                firstItem.clone()
        );

        ingredients.put(
                secondKey,
                secondItem.clone()
        );

        return ingredients;
    }

    private Map<Character, ItemStack> createMixedIngredients(
            char firstKey,
            ItemStack firstItem,
            char secondKey,
            ItemStack secondItem,
            char thirdKey,
            ItemStack thirdItem
    ) {

        Map<Character, ItemStack> ingredients =
                createMixedIngredients(firstKey, firstItem, secondKey, secondItem);

        ingredients.put(thirdKey, thirdItem.clone());
        return ingredients;
    }

    /**
     * Full wrap (8 surround). Kept for Catcher / legacy 8-mat crafts.
     * Skill T1–T5 ladders use {@link #registerLadderPlus} instead.
     */
    private void registerPlusUpgrade(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack surround
    ) {
        registerWrapUpgrade(id, category, result, rarity, center, surround);
    }

    /**
     * Full checker wrap (4+4). Kept for any leftover 8-mat crafts.
     * Skill ladders use {@link #registerLadderMix}.
     */
    private void registerMixedPlusUpgrade(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack vertical,
            ItemStack horizontal
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of("VHV", "HCH", "VHV"),
                createMixedIngredients('V', vertical, 'H', horizontal, 'C', center)
        );
    }

    private void registerWrapUpgrade(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack surround
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of("XXX", "XCX", "XXX"),
                createMixedIngredients('X', surround, 'C', center)
        );
    }

    /**
     * Corners + edges (4+4). Kept as a primitive; T5 skill ladders use
     * {@link #registerLadderPeak} (1 premium + 3 support).
     */
    private void registerDualWrapUpgrade(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack corners,
            ItemStack edges
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of("ACA", "ECE", "ACA"),
                createMixedIngredients('A', corners, 'E', edges, 'C', center)
        );
    }

    /**
     * Charm T2/T3 and leftover single-mat plus crafts: exactly 1 previous + 4 tier mats.
     * Skill T2 ladders use {@link #registerLadderMix}. Never 2–3× the predecessor.
     * Shape: {@code  X  / XCX /  X }
     */
    private void registerLadderPlus(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack surround
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of(" X ", "XCX", " X "),
                createMixedIngredients('X', surround, 'C', center)
        );
    }

    /**
     * Wave 2 T3: exactly 1 previous + 2+2 new compressed mats (plus-shaped).
     * Shape: {@code  V  / HCH /  V }
     */
    private void registerLadderMix(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack vertical,
            ItemStack horizontal
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of(" V ", "HCH", " V "),
                createMixedIngredients('V', vertical, 'H', horizontal, 'C', center)
        );
    }

    /**
     * Wave 2 T4: exactly 1 previous + 2+2 new compacted mats, diagonal cross.
     * Harder to read than T3 plus — same count, rarer mats, more interesting shape.
     * Shape: {@code A B /  C  / B A}
     */
    private void registerLadderCross(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack first,
            ItemStack second
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of("A B", " C ", "B A"),
                createMixedIngredients('A', first, 'B', second, 'C', center)
        );
    }

    /**
     * Wave 2 T5: exactly 1 previous + 1 premium compacted + 3 support compacted.
     * Same pattern as the Wave 1 mining pick T5 fix. Never 2–3× the predecessor.
     * Shape: {@code  S  / SCS /  P }
     */
    private void registerLadderPeak(
            String id,
            RecipeCategory category,
            ItemStack result,
            Rarity rarity,
            ItemStack center,
            ItemStack premium,
            ItemStack support
    ) {
        registerSimple(
                id,
                category,
                result,
                rarity,
                List.of(" S ", "SCS", " P "),
                createMixedIngredients('S', support, 'C', center, 'P', premium)
        );
    }
}