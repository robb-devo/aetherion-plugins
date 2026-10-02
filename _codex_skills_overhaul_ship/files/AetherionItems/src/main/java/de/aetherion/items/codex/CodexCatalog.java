package de.aetherion.items.codex;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the Codex can count.
 *
 * <p>Collection ids live in the {@code blocks} ledger (ores, wood, crops, catches…); Bestiary ids
 * in {@code kills} (vanilla type names, {@code dungeon:*}, {@code sea:*}, {@code odd:*}). Bosses
 * are not listed statically: they come live from BossEngine templates, see {@link #bosses}.
 */
public final class CodexCatalog {

    public record Entry(String id, String name, Material icon, String category) {
    }

    /** Ladder + flavor for one entry. */
    public record Info(CodexTiers.Scale scale, String hint) {
    }

    public static final String MOB_HOSTILE = "hostile";
    public static final String MOB_NETHER = "nether_mobs";
    public static final String MOB_END = "end";
    public static final String MOB_BOSSES = "bosses";
    public static final String MOB_DUNGEON = "dungeon";
    public static final String MOB_ANIMALS = "animals";
    public static final String MOB_WATER = "water";
    public static final String MOB_SEA = "sea";

    public static final String BLOCK_ORES = "ores";
    public static final String BLOCK_STONE = "stone";
    public static final String BLOCK_WOOD = "wood";
    public static final String BLOCK_DIRT = "dirt";
    public static final String BLOCK_CROPS = "crops";
    public static final String BLOCK_FISHING = "fishing";
    public static final String BLOCK_NETHER = "nether_blocks";
    public static final String BLOCK_OTHER = "other";

    /** Pseudo-category: every entry of a ledger on one sortable list. */
    public static final String ALL = "all";

    public static final List<String> MOB_CATEGORIES = List.of(
            MOB_HOSTILE, MOB_NETHER, MOB_END, MOB_BOSSES, MOB_DUNGEON, MOB_ANIMALS, MOB_WATER, MOB_SEA
    );
    public static final List<String> BLOCK_CATEGORIES = List.of(
            BLOCK_ORES, BLOCK_STONE, BLOCK_WOOD, BLOCK_DIRT, BLOCK_CROPS, BLOCK_FISHING, BLOCK_NETHER, BLOCK_OTHER
    );
    /** Tab order on the Bestiary page (row 1). */
    public static final List<String> BESTIARY_TABS = List.of(
            ALL, MOB_HOSTILE, MOB_NETHER, MOB_END, MOB_ANIMALS, MOB_WATER, MOB_SEA, MOB_DUNGEON, MOB_BOSSES
    );
    /** Tab order on the Collection page (row 1). */
    public static final List<String> COLLECTION_TABS = List.of(
            ALL, BLOCK_ORES, BLOCK_STONE, BLOCK_WOOD, BLOCK_DIRT, BLOCK_CROPS, BLOCK_FISHING, BLOCK_NETHER, BLOCK_OTHER
    );

    /** Borderlands variants tracked beside the base kill count. */
    public static final List<String> VARIANTS = List.of("sturdy", "brute", "crypt");

    private static final Map<String, Entry> MOBS = new LinkedHashMap<>();
    private static final Map<String, Entry> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, String> BLOCK_ALIASES = new LinkedHashMap<>();
    private static final Map<String, Info> INFO = new LinkedHashMap<>();
    /** Resolvable ids without a card of their own (legacy boss ids, dungeon bosses shown live). */
    private static final java.util.Set<String> HIDDEN = new java.util.HashSet<>();

    private static final CodexTiers.Scale C = CodexTiers.Scale.MOB_COMMON;
    private static final CodexTiers.Scale T = CodexTiers.Scale.MOB_TOUGH;
    private static final CodexTiers.Scale R = CodexTiers.Scale.MOB_RARE;
    private static final CodexTiers.Scale A = CodexTiers.Scale.ANIMAL;

    static {
        String hostile = "Roams the overworld after dark and in the Borderlands.";
        mob("ZOMBIE", "Zombie", Material.ZOMBIE_SPAWN_EGG, MOB_HOSTILE, C, "The Borderlands' most reliable employee.");
        mob("HUSK", "Husk", Material.HUSK_SPAWN_EGG, MOB_HOSTILE, C, "Sun-baked and still clocking in. Deserts and the Borderlands.");
        mob("ZOMBIE_VILLAGER", "Zombie Villager", Material.ZOMBIE_VILLAGER_SPAWN_EGG, MOB_HOSTILE, C, "Still wants to trade. Mostly bites.");
        mob("DROWNED", "Drowned", Material.DROWNED_SPAWN_EGG, MOB_HOSTILE, C, "Rivers, oceans, and anywhere a zombie fell in.");
        mob("SKELETON", "Skeleton", Material.SKELETON_SPAWN_EGG, MOB_HOSTILE, C, hostile);
        mob("STRAY", "Stray", Material.STRAY_SPAWN_EGG, MOB_HOSTILE, C, "Frozen archers. The Borderlands keeps a few.");
        mob("BOGGED", "Bogged", Material.BOGGED_SPAWN_EGG, MOB_HOSTILE, C, "Swamps and trial chambers. Poison arrows, mossy manners.");
        mob("CREEPER", "Creeper", Material.CREEPER_SPAWN_EGG, MOB_HOSTILE, C, "Quiet. Then very loud.");
        mob("SPIDER", "Spider", Material.SPIDER_SPAWN_EGG, MOB_HOSTILE, C, hostile);
        mob("CAVE_SPIDER", "Cave Spider", Material.CAVE_SPIDER_SPAWN_EGG, MOB_HOSTILE, C, "Mineshafts. Small, fast, rude.");
        mob("SLIME", "Slime", Material.SLIME_SPAWN_EGG, MOB_HOSTILE, C, "Swamps and slime chunks deep down.");
        mob("SILVERFISH", "Silverfish", Material.SILVERFISH_SPAWN_EGG, MOB_HOSTILE, C, "Hides in stone. Mountains and strongholds.");
        mob("PILLAGER", "Pillager", Material.PILLAGER_SPAWN_EGG, MOB_HOSTILE, C, "Outposts and Borderlands patrols.");
        mob("PHANTOM", "Phantom", Material.PHANTOM_SPAWN_EGG, MOB_HOSTILE, T, "Shows up when you skip sleep. Bring a bow.");
        mob("WITCH", "Witch", Material.WITCH_SPAWN_EGG, MOB_HOSTILE, T, "Swamp huts and the Borderlands. Throws opinions.");
        mob("VINDICATOR", "Vindicator", Material.VINDICATOR_SPAWN_EGG, MOB_HOSTILE, T, "Axe first, questions never.");
        mob("VEX", "Vex", Material.VEX_SPAWN_EGG, MOB_HOSTILE, T, "Summoned by Evokers. Ignores walls and feelings.");
        mob("ENDERMAN", "Enderman", Material.ENDERMAN_SPAWN_EGG, MOB_HOSTILE, T, "Don't look. Or do, and profit.");
        mob("BREEZE", "Breeze", Material.BREEZE_SPAWN_EGG, MOB_HOSTILE, T, "Trial chambers. Wind with a grudge.");
        mob("GUARDIAN", "Guardian", Material.GUARDIAN_SPAWN_EGG, MOB_HOSTILE, T, "Ocean monuments.");
        mob("EVOKER", "Evoker", Material.EVOKER_SPAWN_EGG, MOB_HOSTILE, R, "Woodland mansions and raids. Fangs included.");
        mob("RAVAGER", "Ravager", Material.RAVAGER_SPAWN_EGG, MOB_HOSTILE, R, "Raids. A cow that chose violence.");
        mob("WARDEN", "Warden", Material.WARDEN_SPAWN_EGG, MOB_HOSTILE, R, "The Deep Dark. Blind, not deaf.");
        mob("ELDER_GUARDIAN", "Elder Guardian", Material.ELDER_GUARDIAN_SPAWN_EGG, MOB_HOSTILE, R, "Three per ocean monument. Mining Fatigue on arrival.");
        mob("odd:ore_troll", "Ore Troll", Material.IRON_ORE, MOB_HOSTILE, R, "Wakes when you mine ore in the wrong mood. Hits like a landslide.");

        mob("BLAZE", "Blaze", Material.BLAZE_SPAWN_EGG, MOB_NETHER, C, "Nether fortresses.");
        mob("MAGMA_CUBE", "Magma Cube", Material.MAGMA_CUBE_SPAWN_EGG, MOB_NETHER, C, "Basalt deltas and fortresses.");
        mob("ZOMBIFIED_PIGLIN", "Zombified Piglin", Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, MOB_NETHER, C, "Peaceful until it isn't. Then the whole herd.");
        mob("PIGLIN", "Piglin", Material.PIGLIN_SPAWN_EGG, MOB_NETHER, C, "Crimson forests. Likes gold, dislikes you.");
        mob("HOGLIN", "Hoglin", Material.HOGLIN_SPAWN_EGG, MOB_NETHER, C, "Crimson forests. Pork with a temper.");
        mob("STRIDER", "Strider", Material.STRIDER_SPAWN_EGG, MOB_NETHER, C, "Lava lakes. Cold outside of them.");
        mob("GHAST", "Ghast", Material.GHAST_SPAWN_EGG, MOB_NETHER, T, "Soul sand valleys and wastes. Loud crying.");
        mob("WITHER_SKELETON", "Wither Skeleton", Material.WITHER_SKELETON_SPAWN_EGG, MOB_NETHER, T, "Nether fortresses. Skulls are rare.");
        mob("PIGLIN_BRUTE", "Piglin Brute", Material.PIGLIN_BRUTE_SPAWN_EGG, MOB_NETHER, T, "Bastion remnants. Not here to barter.");
        mob("ZOGLIN", "Zoglin", Material.ZOGLIN_SPAWN_EGG, MOB_NETHER, T, "A Hoglin that took a wrong turn to the overworld.");
        mob("WITHER", "Wither", Material.WITHER_SKELETON_SKULL, MOB_NETHER, R, "Built, not found. Please build it somewhere else.");

        mob("ENDERMITE", "Endermite", Material.ENDERMITE_SPAWN_EGG, MOB_END, T, "Sometimes hatches from a thrown pearl.");
        mob("SHULKER", "Shulker", Material.SHULKER_SPAWN_EGG, MOB_END, T, "End cities. A box with levitation issues.");
        mob("ENDER_DRAGON", "Ender Dragon", Material.DRAGON_HEAD, MOB_END, R, "The End's landlord.");

        // Legacy static boss ids (never counted — kept so old lookups resolve). Live bosses: bosses().
        legacyBoss("boss:mcnugget", "McNugget", Material.COOKED_CHICKEN);
        legacyBoss("boss:hollow_lurker", "Hollow Lurker", Material.ENDER_EYE);
        legacyBoss("boss:pathwarden", "Pathwarden", Material.NETHERITE_SWORD);
        legacyBoss("boss:bridge_troll", "Bridge Troll", Material.IRON_AXE);
        legacyBoss("boss:squidward", "Squidward", Material.INK_SAC);
        legacyBoss("boss:skuldugery", "Skuldugery", Material.BOW);
        legacyBoss("boss:aetherion", "Aetherion", Material.NETHER_STAR);
        legacyBoss("boss:aether_colossus", "Aether Colossus", Material.END_CRYSTAL);
        legacyBoss("boss:sir_balthazar", "Sir Balthazar", Material.TOTEM_OF_UNDYING);
        legacyBoss("boss:lobby_cleaner", "The Lobby Cleaner", Material.HOPPER);
        legacyBoss("boss:sparky", "Sparky", Material.MAGMA_CREAM);
        legacyBoss("boss:baron_von_wurm", "Baron von Wurm", Material.STONE);
        legacyBoss("boss:insolvent_wither", "The Insolvent Wither", Material.NETHER_STAR);

        String dungeon = "Dungeon floors. Queue at the gate and bring friends.";
        mob("dungeon:dungeon_zombie", "Dungeon Walker", Material.ZOMBIE_HEAD, MOB_DUNGEON, C, dungeon);
        mob("dungeon:dungeon_skeleton", "Dungeon Archer", Material.SKELETON_SKULL, MOB_DUNGEON, C, dungeon);
        mob("dungeon:dungeon_brute", "Dungeon Brute", Material.HUSK_SPAWN_EGG, MOB_DUNGEON, C, dungeon);
        mob("dungeon:dungeon_warden", "Chamber Warden", Material.WITHER_SKELETON_SKULL, MOB_DUNGEON, T,
                "The mini-boss that holds each floor's key room. Glows. Hits hard.");
        mob("dungeon:ashes_trash", "Ashen Remnant", Material.BLACKSTONE, MOB_DUNGEON, C,
                "What the Ashes floor coughs up between waves.");
        mob("dungeon:endless_trash", "Endless Drifter", Material.PACKED_ICE, MOB_DUNGEON, C,
                "Endless mode's waves. There is always another one.");
        mob("dungeon:dungeon_sentinel", "Prototype Sentinel", Material.TOTEM_OF_UNDYING, MOB_DUNGEON, CodexTiers.Scale.BOSS,
                "Floor 1's final door. Beat it to wake the Dungeon skill page.");
        mob("boss:dungeon_sentinel", "Prototype Sentinel", Material.TOTEM_OF_UNDYING, MOB_DUNGEON, CodexTiers.Scale.BOSS,
                "Floor 1's final door.");
        // The Sentinel's card comes from the live boss list (its kills are filed as a boss).
        HIDDEN.add("dungeon:dungeon_sentinel");
        HIDDEN.add("boss:dungeon_sentinel");

        mob("COW", "Cow", Material.COW_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("PIG", "Pig", Material.PIG_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("SHEEP", "Sheep", Material.SHEEP_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("CHICKEN", "Chicken", Material.CHICKEN_SPAWN_EGG, MOB_ANIMALS, A, "McNugget's cousins. They know.");
        mob("RABBIT", "Rabbit", Material.RABBIT_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("MOOSHROOM", "Mooshroom", Material.MOOSHROOM_SPAWN_EGG, MOB_ANIMALS, A, "Mushroom fields. A cow with a garden.");
        mob("HORSE", "Horse", Material.HORSE_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("DONKEY", "Donkey", Material.DONKEY_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("MULE", "Mule", Material.MULE_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("WOLF", "Wolf", Material.WOLF_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("CAT", "Cat", Material.CAT_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("OCELOT", "Ocelot", Material.OCELOT_SPAWN_EGG, MOB_ANIMALS, A, "Jungles. Shy.");
        mob("FOX", "Fox", Material.FOX_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("BEE", "Bee", Material.BEE_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("GOAT", "Goat", Material.GOAT_SPAWN_EGG, MOB_ANIMALS, A, "Mountains. Rams first.");
        mob("CAMEL", "Camel", Material.CAMEL_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("ARMADILLO", "Armadillo", Material.ARMADILLO_SPAWN_EGG, MOB_ANIMALS, A, "Savannas and badlands. Rolls up when you stare.");
        mob("SNIFFER", "Sniffer", Material.SNIFFER_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("PANDA", "Panda", Material.PANDA_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("POLAR_BEAR", "Polar Bear", Material.POLAR_BEAR_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("LLAMA", "Llama", Material.LLAMA_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("TRADER_LLAMA", "Trader Llama", Material.TRADER_LLAMA_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("PARROT", "Parrot", Material.PARROT_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("BAT", "Bat", Material.BAT_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("VILLAGER", "Villager", Material.VILLAGER_SPAWN_EGG, MOB_ANIMALS, A, "Hm.");
        mob("IRON_GOLEM", "Iron Golem", Material.IRON_BLOCK, MOB_ANIMALS, A, null);
        mob("SNOW_GOLEM", "Snow Golem", Material.SNOW_BLOCK, MOB_ANIMALS, A, null);
        mob("TURTLE", "Turtle", Material.TURTLE_SPAWN_EGG, MOB_ANIMALS, A, null);
        mob("FROG", "Frog", Material.FROG_SPAWN_EGG, MOB_ANIMALS, A, null);

        mob("SQUID", "Squid", Material.SQUID_SPAWN_EGG, MOB_WATER, A, null);
        mob("GLOW_SQUID", "Glow Squid", Material.GLOW_SQUID_SPAWN_EGG, MOB_WATER, A, "Dark water, deep down.");
        mob("DOLPHIN", "Dolphin", Material.DOLPHIN_SPAWN_EGG, MOB_WATER, A, null);
        mob("COD", "Cod", Material.COD_SPAWN_EGG, MOB_WATER, A, null);
        mob("SALMON", "Salmon", Material.SALMON_SPAWN_EGG, MOB_WATER, A, null);
        mob("TROPICAL_FISH", "Tropical Fish", Material.TROPICAL_FISH_SPAWN_EGG, MOB_WATER, A, null);
        mob("PUFFERFISH", "Pufferfish", Material.PUFFERFISH_SPAWN_EGG, MOB_WATER, A, null);
        mob("AXOLOTL", "Axolotl", Material.AXOLOTL_SPAWN_EGG, MOB_WATER, A, null);
        mob("TADPOLE", "Tadpole", Material.TADPOLE_SPAWN_EGG, MOB_WATER, A, null);

        String sea = "Hooks sometimes pull up more than fish. Land a catch and hope it's heavier.";
        CodexTiers.Scale s = CodexTiers.Scale.SEA;
        mob("sea:dock_dredger", "Dock Dredger", Material.KELP, MOB_SEA, s, sea);
        mob("sea:brackish_walker", "Brackish Walker", Material.SEAGRASS, MOB_SEA, s, sea);
        mob("sea:reef_scavenger", "Reef Scavenger", Material.BRAIN_CORAL, MOB_SEA, s, sea);
        mob("sea:tide_rangler", "Tide Rangler", Material.TRIDENT, MOB_SEA, s, sea);
        mob("sea:brine_sentinel", "Brine Sentinel", Material.PRISMARINE_SHARD, MOB_SEA, s, sea);
        mob("sea:current_wretch", "Current Wretch", Material.GUARDIAN_SPAWN_EGG, MOB_SEA, s, "Guardian stock. Higher fishing levels only.");
        mob("sea:riptide_reaper", "Riptide Reaper", Material.NAUTILUS_SHELL, MOB_SEA, s, "Higher fishing levels only.");
        mob("sea:abyssal_claimant", "Abyssal Claimant", Material.PRISMARINE_CRYSTALS, MOB_SEA, s, "Guardian stock. Deep anglers only.");
        mob("sea:deep_auctioneer", "Deep Auctioneer", Material.GOLD_NUGGET, MOB_SEA, s, "It wants to sell you your own rod.");
        mob("sea:leviathan_bait", "Leviathan Bait", Material.HEART_OF_THE_SEA, MOB_SEA, s, "The top of the ladder. Master anglers only.");

        CodexTiers.Scale bulk = CodexTiers.Scale.BULK;
        CodexTiers.Scale common = CodexTiers.Scale.COMMON;
        CodexTiers.Scale uncommon = CodexTiers.Scale.UNCOMMON;
        CodexTiers.Scale rare = CodexTiers.Scale.RARE;
        CodexTiers.Scale precious = CodexTiers.Scale.PRECIOUS;

        block("coal", "Coal", Material.COAL, BLOCK_ORES, common, "Everywhere a pickaxe goes. The first ore on every list.",
                "COAL_ORE", "DEEPSLATE_COAL_ORE", "COAL_BLOCK");
        block("iron", "Iron", Material.IRON_INGOT, BLOCK_ORES, common, "Mid-depth stone and mountain faces.",
                "IRON_ORE", "DEEPSLATE_IRON_ORE", "RAW_IRON_BLOCK", "IRON_BLOCK");
        block("gold", "Gold", Material.GOLD_INGOT, BLOCK_ORES, uncommon, "Deep stone and badlands. Needs real Mining Power.",
                "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "RAW_GOLD_BLOCK", "GOLD_BLOCK");
        block("copper", "Copper", Material.COPPER_INGOT, BLOCK_ORES, common, "Shallow veins. Comes in heaps.",
                "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "RAW_COPPER_BLOCK", "COPPER_BLOCK");
        block("diamond", "Diamond", Material.DIAMOND, BLOCK_ORES, uncommon, "The deep layers. Bring your best pick.",
                "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "DIAMOND_BLOCK");
        block("emerald", "Emerald", Material.EMERALD, BLOCK_ORES, uncommon, "Mountains, and the richest mine seams.",
                "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "EMERALD_BLOCK");
        block("lapis", "Lapis", Material.LAPIS_LAZULI, BLOCK_ORES, common, "Deep stone. Enchanters pay attention.",
                "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "LAPIS_BLOCK");
        block("redstone", "Redstone", Material.REDSTONE, BLOCK_ORES, common, "Deep stone. Glows when you touch it.",
                "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "REDSTONE_BLOCK");
        block("quartz", "Nether Quartz", Material.QUARTZ, BLOCK_ORES, common, "Everywhere in the Nether.",
                "NETHER_QUARTZ_ORE", "QUARTZ_BLOCK");
        block("nether_gold", "Nether Gold", Material.GOLD_NUGGET, BLOCK_ORES, common, "Nether walls. Piglins notice.",
                "NETHER_GOLD_ORE");
        block("ancient_debris", "Ancient Debris", Material.NETHERITE_INGOT, BLOCK_ORES, rare, "Low Nether, hidden in netherrack. Blast-proof.",
                "ANCIENT_DEBRIS", "NETHERITE_BLOCK");
        block("amethyst", "Amethyst", Material.AMETHYST_SHARD, BLOCK_ORES, rare, "Geodes, and Crystal Finds on Mining Eldervale.",
                "AMETHYST_CLUSTER", "BUDDING_AMETHYST", "AMETHYST_BLOCK", "LARGE_AMETHYST_BUD");

        block("stone", "Stone", Material.STONE, BLOCK_STONE, bulk, "The world's filler. Counts anyway.", "STONE", "COBBLESTONE");
        block("deepslate", "Deepslate", Material.DEEPSLATE, BLOCK_STONE, bulk, "Below y 0. Slower to break, same pride.",
                "DEEPSLATE", "COBBLED_DEEPSLATE");
        block("granite", "Granite", Material.GRANITE, BLOCK_STONE, common, null, "GRANITE");
        block("diorite", "Diorite", Material.DIORITE, BLOCK_STONE, common, "Nobody's favourite. Still counts.", "DIORITE");
        block("andesite", "Andesite", Material.ANDESITE, BLOCK_STONE, common, null, "ANDESITE");
        block("tuff", "Tuff", Material.TUFF, BLOCK_STONE, common, "Deep caves.", "TUFF");
        block("calcite", "Calcite", Material.CALCITE, BLOCK_STONE, uncommon, "Geode shells.", "CALCITE");
        block("dripstone", "Dripstone", Material.POINTED_DRIPSTONE, BLOCK_STONE, uncommon, "Dripstone caves.",
                "DRIPSTONE_BLOCK", "POINTED_DRIPSTONE");
        block("sandstone", "Sandstone", Material.SANDSTONE, BLOCK_STONE, common, "Under every desert.",
                "SANDSTONE", "RED_SANDSTONE");
        block("obsidian", "Obsidian", Material.OBSIDIAN, BLOCK_STONE, uncommon, "Where water met lava and neither won.", "OBSIDIAN");
        block("end_stone", "End Stone", Material.END_STONE, BLOCK_STONE, common, "The End.", "END_STONE");

        String forest = "Any tree. Foraging Eldervale pays best.";
        block("oak", "Oak", Material.OAK_LOG, BLOCK_WOOD, common, forest, "OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD");
        block("spruce", "Spruce", Material.SPRUCE_LOG, BLOCK_WOOD, common, forest,
                "SPRUCE_LOG", "SPRUCE_WOOD", "STRIPPED_SPRUCE_LOG", "STRIPPED_SPRUCE_WOOD");
        block("birch", "Birch", Material.BIRCH_LOG, BLOCK_WOOD, common, forest,
                "BIRCH_LOG", "BIRCH_WOOD", "STRIPPED_BIRCH_LOG", "STRIPPED_BIRCH_WOOD");
        block("jungle", "Jungle", Material.JUNGLE_LOG, BLOCK_WOOD, common, forest,
                "JUNGLE_LOG", "JUNGLE_WOOD", "STRIPPED_JUNGLE_LOG", "STRIPPED_JUNGLE_WOOD");
        block("acacia", "Acacia", Material.ACACIA_LOG, BLOCK_WOOD, common, forest,
                "ACACIA_LOG", "ACACIA_WOOD", "STRIPPED_ACACIA_LOG", "STRIPPED_ACACIA_WOOD");
        block("dark_oak", "Dark Oak", Material.DARK_OAK_LOG, BLOCK_WOOD, common, forest,
                "DARK_OAK_LOG", "DARK_OAK_WOOD", "STRIPPED_DARK_OAK_LOG", "STRIPPED_DARK_OAK_WOOD");
        block("mangrove", "Mangrove", Material.MANGROVE_LOG, BLOCK_WOOD, common, "Swamp roots and Eldervale's wetland grove.",
                "MANGROVE_LOG", "MANGROVE_WOOD", "STRIPPED_MANGROVE_LOG", "STRIPPED_MANGROVE_WOOD", "MANGROVE_ROOTS");
        block("cherry", "Cherry", Material.CHERRY_LOG, BLOCK_WOOD, common, "Cherry groves. Pink everything.",
                "CHERRY_LOG", "CHERRY_WOOD", "STRIPPED_CHERRY_LOG", "STRIPPED_CHERRY_WOOD");
        block("bamboo", "Bamboo", Material.BAMBOO, BLOCK_WOOD, bulk, "Jungles. Grows faster than you chop.", "BAMBOO", "BAMBOO_BLOCK");
        block("giant_mushroom", "Giant Mushroom", Material.RED_MUSHROOM_BLOCK, BLOCK_WOOD, uncommon, "Huge caps in dark forests and mushroom fields.",
                "RED_MUSHROOM_BLOCK", "BROWN_MUSHROOM_BLOCK", "MUSHROOM_STEM");
        block("crimson", "Crimson", Material.CRIMSON_STEM, BLOCK_WOOD, common, "Crimson forests in the Nether.",
                "CRIMSON_STEM", "STRIPPED_CRIMSON_STEM", "CRIMSON_HYPHAE", "STRIPPED_CRIMSON_HYPHAE");
        block("warped", "Warped", Material.WARPED_STEM, BLOCK_WOOD, common, "Warped forests in the Nether.",
                "WARPED_STEM", "STRIPPED_WARPED_STEM", "WARPED_HYPHAE", "STRIPPED_WARPED_HYPHAE");

        block("dirt", "Dirt", Material.DIRT, BLOCK_DIRT, bulk, "Literally anywhere.", "DIRT", "COARSE_DIRT", "ROOTED_DIRT");
        block("grass", "Grass", Material.GRASS_BLOCK, BLOCK_DIRT, common, null, "GRASS_BLOCK", "PODZOL", "MYCELIUM", "DIRT_PATH");
        block("sand", "Sand", Material.SAND, BLOCK_DIRT, bulk, "Deserts and beaches.", "SAND", "RED_SAND", "SUSPICIOUS_SAND");
        block("gravel", "Gravel", Material.GRAVEL, BLOCK_DIRT, common, "Riverbeds and cave floors.", "GRAVEL", "SUSPICIOUS_GRAVEL");
        block("clay", "Clay", Material.CLAY, BLOCK_DIRT, uncommon, "Shallow lakes and lush caves.", "CLAY");
        block("mud", "Mud", Material.MUD, BLOCK_DIRT, common, "Mangrove swamps.", "MUD", "MUDDY_MANGROVE_ROOTS", "PACKED_MUD");
        block("snow", "Snow", Material.SNOW_BLOCK, BLOCK_DIRT, common, "Peaks and tundra.", "SNOW", "SNOW_BLOCK", "POWDER_SNOW");
        block("moss", "Moss", Material.MOSS_BLOCK, BLOCK_DIRT, common, "Lush caves.", "MOSS_BLOCK", "MOSS_CARPET");

        String farm = "Fully grown only. The Farm Isle pays best.";
        block("wheat", "Wheat", Material.WHEAT, BLOCK_CROPS, bulk, farm, "WHEAT");
        block("carrot", "Carrot", Material.CARROT, BLOCK_CROPS, bulk, farm, "CARROTS");
        block("potato", "Potato", Material.POTATO, BLOCK_CROPS, bulk, farm, "POTATOES");
        block("beetroot", "Beetroot", Material.BEETROOT, BLOCK_CROPS, common, farm, "BEETROOTS");
        block("nether_wart", "Nether Wart", Material.NETHER_WART, BLOCK_CROPS, bulk, "Soul sand, fully grown.", "NETHER_WART");
        block("sugar_cane", "Sugar Cane", Material.SUGAR_CANE, BLOCK_CROPS, bulk, "Riverbanks. Every stalk counts.", "SUGAR_CANE");
        block("melon", "Melon", Material.MELON, BLOCK_CROPS, bulk, "The fruit, not the stem.", "MELON");
        block("pumpkin", "Pumpkin", Material.PUMPKIN, BLOCK_CROPS, common, "The fruit, not the stem.", "PUMPKIN", "CARVED_PUMPKIN");
        block("cactus", "Cactus", Material.CACTUS, BLOCK_CROPS, common, "Deserts. Wear gloves.", "CACTUS");
        block("mushroom_patch", "Mushrooms", Material.RED_MUSHROOM, BLOCK_CROPS, uncommon, "Dark corners and mushroom fields.",
                "RED_MUSHROOM", "BROWN_MUSHROOM");
        block("cocoa", "Cocoa", Material.COCOA_BEANS, BLOCK_CROPS, common, "Jungle trunks, fully ripe.", "COCOA");
        block("sweet_berries", "Sweet Berries", Material.SWEET_BERRIES, BLOCK_CROPS, common, "Taiga bushes, fully ripe.", "SWEET_BERRY_BUSH");
        block("glow_berries", "Glow Berries", Material.GLOW_BERRIES, BLOCK_CROPS, uncommon, "Lush cave vines.", "CAVE_VINES", "CAVE_VINES_PLANT");

        // Fishing: counted from landed catches (no block aliases).
        block("fish_cod", "Cod", Material.COD, BLOCK_FISHING, common, "The bread and butter of every pier.");
        block("fish_salmon", "Salmon", Material.SALMON, BLOCK_FISHING, uncommon, "Rivers and cold water. Fights a little.");
        block("fish_tropical", "Tropical Fish", Material.TROPICAL_FISH, BLOCK_FISHING, rare, "Warm water. Every one a different outfit.");
        block("fish_puffer", "Pufferfish", Material.PUFFERFISH, BLOCK_FISHING, rare, "Warm oceans. Do not lick.");
        block("fish_treasure", "Treasure", Material.NAUTILUS_SHELL, BLOCK_FISHING, precious, "Books, shells, bows… the lake's lost and found.");
        block("fish_junk", "Junk", Material.LILY_PAD, BLOCK_FISHING, common, "Boots, bowls, string. Somebody has to clean the lake.");

        block("netherrack", "Netherrack", Material.NETHERRACK, BLOCK_NETHER, bulk, "The Nether's filler.", "NETHERRACK");
        block("soul_sand", "Soul Sand", Material.SOUL_SAND, BLOCK_NETHER, common, "Soul sand valleys.", "SOUL_SAND", "SOUL_SOIL");
        block("basalt", "Basalt", Material.BASALT, BLOCK_NETHER, common, "Basalt deltas.", "BASALT", "SMOOTH_BASALT", "POLISHED_BASALT");
        block("blackstone", "Blackstone", Material.BLACKSTONE, BLOCK_NETHER, common, "Deltas and bastions.", "BLACKSTONE", "GILDED_BLACKSTONE");
        block("magma", "Magma", Material.MAGMA_BLOCK, BLOCK_NETHER, uncommon, "Nether oceans' floor. Hot.", "MAGMA_BLOCK");
        block("glowstone", "Glowstone", Material.GLOWSTONE, BLOCK_NETHER, uncommon, "Nether ceilings.", "GLOWSTONE");
        block("nether_wart_block", "Nether Wart Block", Material.NETHER_WART_BLOCK, BLOCK_NETHER, common, "Crimson and warped canopies.",
                "NETHER_WART_BLOCK", "WARPED_WART_BLOCK");
        block("shroomlight", "Shroomlight", Material.SHROOMLIGHT, BLOCK_NETHER, uncommon, "Hidden in huge fungus canopies.", "SHROOMLIGHT");

        block("ice", "Ice", Material.ICE, BLOCK_OTHER, common, "Frozen lakes and icebergs.", "ICE", "PACKED_ICE", "BLUE_ICE");
        block("prismarine", "Prismarine", Material.PRISMARINE, BLOCK_OTHER, uncommon, "Ocean monuments.",
                "PRISMARINE", "DARK_PRISMARINE", "PRISMARINE_BRICKS", "SEA_LANTERN");
        block("sponge", "Sponge", Material.SPONGE, BLOCK_OTHER, precious, "Monument rooms. Elder Guardians keep them.", "SPONGE", "WET_SPONGE");
        block("sculk", "Sculk", Material.SCULK, BLOCK_OTHER, rare, "The Deep Dark. Tread quietly.",
                "SCULK", "SCULK_VEIN", "SCULK_CATALYST", "SCULK_SENSOR", "SCULK_SHRIEKER");
        block("chorus", "Chorus", Material.CHORUS_FRUIT, BLOCK_OTHER, uncommon, "The End's outer islands.", "CHORUS_PLANT", "CHORUS_FLOWER");
        block("crying_obsidian", "Crying Obsidian", Material.CRYING_OBSIDIAN, BLOCK_OTHER, rare, "Ruined portals and bastions.", "CRYING_OBSIDIAN");
    }

    private CodexCatalog() {
    }

    public static String categoryTitle(String category) {
        return switch (category) {
            case ALL -> "Everything";
            case MOB_HOSTILE -> "Borderlands";
            case MOB_NETHER -> "Nether";
            case MOB_END -> "The End";
            case MOB_BOSSES -> "Bosses";
            case MOB_DUNGEON -> "Dungeon";
            case MOB_ANIMALS -> "Wildlife";
            case MOB_WATER -> "Waters";
            case MOB_SEA -> "Sea Creatures";
            case BLOCK_ORES -> "Ores";
            case BLOCK_STONE -> "Stone";
            case BLOCK_WOOD -> "Wood";
            case BLOCK_DIRT -> "Dirt & Sand";
            case BLOCK_CROPS -> "Crops";
            case BLOCK_FISHING -> "Catches";
            case BLOCK_NETHER -> "Nether Blocks";
            case BLOCK_OTHER -> "Oddities";
            default -> category;
        };
    }

    /** One-line pitch for a category tab. */
    public static String categoryBlurb(String category) {
        return switch (category) {
            case ALL -> "Every entry on one list. Sort it.";
            case MOB_HOSTILE -> "Overworld hostiles. Sturdy, Brute and Crypt variants tallied.";
            case MOB_NETHER -> "Fortresses, bastions and the heat in between.";
            case MOB_END -> "Pearls, boxes and one very large landlord.";
            case MOB_BOSSES -> "Every boss BossEngine knows. Loot tables unlock on the first kill.";
            case MOB_DUNGEON -> "Floor mobs, wardens and the bosses behind the last door.";
            case MOB_ANIMALS -> "Livestock and wild things. No judgement.";
            case MOB_WATER -> "Squid, fish and friends.";
            case MOB_SEA -> "What bites back when you fish. Ten tiers of it.";
            case BLOCK_ORES -> "Mining. The ore ledger.";
            case BLOCK_STONE -> "Stone, deepslate and their cousins.";
            case BLOCK_WOOD -> "Foraging. Every log and stem.";
            case BLOCK_DIRT -> "Shovel work: dirt, sand, gravel, clay.";
            case BLOCK_CROPS -> "Farming. Ripe harvests only.";
            case BLOCK_FISHING -> "Fishing. Every landed catch, treasure and junk.";
            case BLOCK_NETHER -> "Netherrack and the rest of the basement.";
            case BLOCK_OTHER -> "Ice, sponge, sculk and the stranger shelves.";
            default -> "";
        };
    }

    public static Material categoryIcon(String category) {
        return switch (category) {
            case ALL -> Material.KNOWLEDGE_BOOK;
            case MOB_HOSTILE -> Material.IRON_SWORD;
            case MOB_NETHER -> Material.BLAZE_POWDER;
            case MOB_END -> Material.ENDER_PEARL;
            case MOB_BOSSES -> Material.NETHER_STAR;
            case MOB_DUNGEON -> Material.DEEPSLATE_BRICKS;
            case MOB_ANIMALS -> Material.LEATHER;
            case MOB_WATER -> Material.HEART_OF_THE_SEA;
            case MOB_SEA -> Material.TRIDENT;
            case BLOCK_ORES -> Material.DIAMOND;
            case BLOCK_STONE -> Material.COBBLESTONE;
            case BLOCK_WOOD -> Material.OAK_LOG;
            case BLOCK_DIRT -> Material.DIRT;
            case BLOCK_CROPS -> Material.WHEAT;
            case BLOCK_FISHING -> Material.FISHING_ROD;
            case BLOCK_NETHER -> Material.NETHERRACK;
            case BLOCK_OTHER -> Material.CHEST;
            default -> Material.PAPER;
        };
    }

    /** Color code a category is tinted with (frame panes, titles). */
    public static String categoryColor(String category) {
        return switch (category) {
            case MOB_HOSTILE -> "§c";
            case MOB_NETHER, BLOCK_NETHER -> "§4";
            case MOB_END -> "§5";
            case MOB_BOSSES -> "§6";
            case MOB_DUNGEON -> "§d";
            case MOB_ANIMALS, BLOCK_DIRT -> "§e";
            case MOB_WATER, BLOCK_FISHING -> "§3";
            case MOB_SEA -> "§b";
            case BLOCK_ORES -> "§b";
            case BLOCK_STONE -> "§7";
            case BLOCK_WOOD -> "§2";
            case BLOCK_CROPS -> "§6";
            case BLOCK_OTHER -> "§9";
            default -> "§f";
        };
    }

    public static Material categoryPane(String category) {
        return switch (category) {
            case MOB_HOSTILE -> Material.RED_STAINED_GLASS_PANE;
            case MOB_NETHER, BLOCK_NETHER -> Material.BROWN_STAINED_GLASS_PANE;
            case MOB_END -> Material.PURPLE_STAINED_GLASS_PANE;
            case MOB_BOSSES, BLOCK_CROPS -> Material.ORANGE_STAINED_GLASS_PANE;
            case MOB_DUNGEON -> Material.MAGENTA_STAINED_GLASS_PANE;
            case MOB_ANIMALS, BLOCK_DIRT -> Material.YELLOW_STAINED_GLASS_PANE;
            case MOB_WATER, BLOCK_FISHING -> Material.CYAN_STAINED_GLASS_PANE;
            case MOB_SEA, BLOCK_ORES -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case BLOCK_STONE -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case BLOCK_WOOD -> Material.GREEN_STAINED_GLASS_PANE;
            case BLOCK_OTHER -> Material.BLUE_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    public static Entry mob(String id) {
        return MOBS.get(id);
    }

    public static Entry block(String id) {
        return BLOCKS.get(id);
    }

    /** Mobs of a category as the Bestiary lists them (legacy / duplicate ids left out). */
    public static List<Entry> mobs(String category) {
        return MOBS.values().stream()
                .filter(entry -> entry.category().equals(category))
                .filter(entry -> !HIDDEN.contains(entry.id()))
                .toList();
    }

    /** True for ids that resolve but never get their own Bestiary card. */
    public static boolean hidden(String id) {
        return HIDDEN.contains(id);
    }

    public static List<Entry> blocks(String category) {
        return BLOCKS.values().stream().filter(entry -> entry.category().equals(category)).toList();
    }

    public static String resolveBlock(Material material) {
        if (material == null) {
            return null;
        }
        return BLOCK_ALIASES.get(material.name());
    }

    /**
     * Collection id for a broken block, or null when it must not count yet: unripe crops (a
     * seedling is not a harvest) are skipped here so the ledger reads like the barn.
     */
    public static String resolveHarvest(Block block) {
        if (block == null) {
            return null;
        }
        String id = resolveBlock(block.getType());
        if (id == null) {
            return null;
        }
        if (block.getBlockData() instanceof Ageable ageable
                && BLOCK_CROPS.equals(categoryOf(id))
                && !"sugar_cane".equals(id) && !"cactus".equals(id)
                && !"glow_berries".equals(id)) {
            return ageable.getAge() >= ageable.getMaximumAge() ? id : null;
        }
        return id;
    }

    /** Collection id for a landed catch (vanilla fish families; everything else is treasure or junk). */
    public static String resolveCatch(Material material) {
        if (material == null || material.isAir()) {
            return null;
        }
        return switch (material) {
            case COD, COOKED_COD -> "fish_cod";
            case SALMON, COOKED_SALMON -> "fish_salmon";
            case TROPICAL_FISH -> "fish_tropical";
            case PUFFERFISH -> "fish_puffer";
            case ENCHANTED_BOOK, NAME_TAG, NAUTILUS_SHELL, SADDLE, BOW, FISHING_ROD, HEART_OF_THE_SEA,
                 PRISMARINE_CRYSTALS, PRISMARINE_SHARD, TRIDENT, EMERALD, DIAMOND, GOLD_INGOT, SPONGE -> "fish_treasure";
            default -> "fish_junk";
        };
    }

    /** Sea creature id for a fishing encounter band (0–9), or null. */
    public static String seaCreature(int band) {
        return switch (band) {
            case 0 -> "sea:dock_dredger";
            case 1 -> "sea:brackish_walker";
            case 2 -> "sea:reef_scavenger";
            case 3 -> "sea:tide_rangler";
            case 4 -> "sea:brine_sentinel";
            case 5 -> "sea:current_wretch";
            case 6 -> "sea:riptide_reaper";
            case 7 -> "sea:abyssal_claimant";
            case 8 -> "sea:deep_auctioneer";
            case 9 -> "sea:leviathan_bait";
            default -> null;
        };
    }

    public static List<Entry> allMobs() {
        return new ArrayList<>(MOBS.values());
    }

    public static List<Entry> allBlocks() {
        return new ArrayList<>(BLOCKS.values());
    }

    /** Ladder + hint for an id. Unknown ids get a sensible default from their prefix. */
    public static Info info(String id) {
        Info info = id == null ? null : INFO.get(id);
        if (info != null) {
            return info;
        }
        if (id != null && (id.startsWith("boss:") || id.startsWith("bossid:"))) {
            return new Info(CodexTiers.Scale.BOSS, null);
        }
        if (id != null && BLOCKS.containsKey(id)) {
            return new Info(CodexTiers.Scale.COMMON, null);
        }
        return new Info(CodexTiers.Scale.MOB_COMMON, null);
    }

    public static CodexTiers.Scale scale(String id) {
        return info(id).scale();
    }

    public static String categoryOf(String id) {
        Entry entry = BLOCKS.get(id);
        if (entry == null) {
            entry = MOBS.get(id);
        }
        return entry == null ? null : entry.category();
    }

    // ------------------------------------------------------------------ bosses (live)

    /** A boss as the Bestiary shows it. {@code templateId} is the BossEngine id kills are filed under. */
    public record Boss(String templateId, String name, Material icon, boolean dungeon) {

        /** Entry id used for claims: {@code boss:<templateId>}. */
        public String entryId() {
            return "boss:" + templateId;
        }
    }

    private static final List<Boss> FALLBACK_BOSSES = List.of(
            new Boss("mcnugget", "McNugget", Material.COOKED_CHICKEN, false),
            new Boss("hollow_lurker", "Hollow Lurker", Material.ENDER_EYE, false),
            new Boss("pathwarden", "Pathwarden", Material.NETHERITE_SWORD, false),
            new Boss("bridge_troll", "Bridge Troll", Material.IRON_AXE, false),
            new Boss("skuldugery", "Skuldugery", Material.BOW, false),
            new Boss("squidward", "Squidward", Material.INK_SAC, false),
            new Boss("sir_balthazar", "Sir Balthazar", Material.TOTEM_OF_UNDYING, false),
            new Boss("hollow_sun", "Hollow Sun", Material.SUNFLOWER, false),
            new Boss("hanging_saint", "Hanging Saint", Material.CHAIN, false),
            new Boss("ashen_sheath", "Ashen Sheath", Material.BLACKSTONE, false),
            new Boss("ashen_chainwarden", "Ashen Chainwarden", Material.CHAIN, false),
            new Boss("cinder_herald", "Cinder Herald", Material.BLAZE_ROD, false),
            new Boss("helios_herald", "Helios Herald", Material.GLOWSTONE_DUST, false),
            new Boss("helios_requiem", "Helios Requiem", Material.GOLD_BLOCK, false),
            new Boss("world_eater", "World Eater", Material.DRAGON_EGG, false),
            new Boss("world_eater_unbroken", "World Eater Unbroken", Material.DRAGON_HEAD, false),
            new Boss("aether_colossus", "Aether Colossus", Material.END_CRYSTAL, false),
            new Boss("aetherion", "Aetherion", Material.NETHER_STAR, false),
            new Boss("lobby_cleaner", "The Lobby Cleaner", Material.HOPPER, false),
            new Boss("sparky", "Sparky", Material.MAGMA_CREAM, false),
            new Boss("baron_von_wurm", "Baron von Wurm", Material.STONE, false),
            new Boss("insolvent_wither", "The Insolvent Wither", Material.NETHER_STAR, false),
            new Boss("dungeon_sentinel", "Prototype Sentinel", Material.TOTEM_OF_UNDYING, true),
            new Boss("dungeon_frostbound", "The Frostbound", Material.PACKED_ICE, true),
            new Boss("dungeon_aetherion", "Aetherion (Floor)", Material.NETHER_STAR, true)
    );

    private static volatile List<Boss> bossCache = List.of();
    private static volatile long bossCacheAt;

    /**
     * Every boss worth listing: live BossEngine templates (minus {@code test_*} rigs), then the
     * known roster as a fallback so the page is never empty. Cached for 30 s.
     */
    public static List<Boss> bosses() {
        long now = System.currentTimeMillis();
        if (now - bossCacheAt < 30_000L && !bossCache.isEmpty()) {
            return bossCache;
        }
        Map<String, Boss> byId = new LinkedHashMap<>();
        for (BossJournal.Entry entry : BossJournal.entries()) {
            String id = entry.id().toLowerCase(Locale.ROOT);
            if (hiddenBoss(id)) {
                continue;
            }
            Material icon = fallbackIcon(id, entry.icon());
            byId.put(id, new Boss(id, CodexText.strip(entry.displayName()), icon, id.startsWith("dungeon")));
        }
        for (Boss boss : FALLBACK_BOSSES) {
            byId.putIfAbsent(boss.templateId(), boss);
        }
        List<Boss> list = new ArrayList<>(byId.values());
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        bossCache = Collections.unmodifiableList(list);
        bossCacheAt = now;
        return bossCache;
    }

    public static Boss boss(String templateId) {
        if (templateId == null) {
            return null;
        }
        String key = templateId.toLowerCase(Locale.ROOT);
        for (Boss boss : bosses()) {
            if (boss.templateId().equals(key)) {
                return boss;
            }
        }
        return null;
    }

    /** BossEngine test rigs never show up in the Bestiary. */
    public static boolean hiddenBoss(String templateId) {
        return templateId == null || templateId.startsWith("test_") || templateId.startsWith("dev_");
    }

    private static Material fallbackIcon(String id, Material live) {
        for (Boss boss : FALLBACK_BOSSES) {
            if (boss.templateId().equals(id)) {
                return boss.icon();
            }
        }
        return live == null ? Material.WITHER_SKELETON_SKULL : live;
    }

    // ------------------------------------------------------------------ registration

    private static void mob(String id, String name, Material icon, String category, CodexTiers.Scale scale, String hint) {
        MOBS.put(id, new Entry(id, name, icon, category));
        INFO.put(id, new Info(scale, hint));
    }

    private static void legacyBoss(String id, String name, Material icon) {
        MOBS.put(id, new Entry(id, name, icon, MOB_BOSSES));
        INFO.put(id, new Info(CodexTiers.Scale.BOSS, null));
        HIDDEN.add(id);
    }

    private static void block(String id, String name, Material icon, String category, CodexTiers.Scale scale,
                              String hint, String... materials) {
        BLOCKS.put(id, new Entry(id, name, icon, category));
        INFO.put(id, new Info(scale, hint));
        for (String material : materials) {
            BLOCK_ALIASES.put(material.toUpperCase(Locale.ROOT), id);
        }
    }
}
