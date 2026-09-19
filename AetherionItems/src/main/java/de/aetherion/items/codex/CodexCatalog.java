package de.aetherion.items.codex;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CodexCatalog {

    public record Entry(String id, String name, Material icon, String category) {
    }

    public static final String MOB_HOSTILE = "hostile";
    public static final String MOB_NETHER = "nether_mobs";
    public static final String MOB_END = "end";
    public static final String MOB_BOSSES = "bosses";
    public static final String MOB_DUNGEON = "dungeon";
    public static final String MOB_ANIMALS = "animals";
    public static final String MOB_WATER = "water";

    public static final String BLOCK_ORES = "ores";
    public static final String BLOCK_STONE = "stone";
    public static final String BLOCK_WOOD = "wood";
    public static final String BLOCK_DIRT = "dirt";
    public static final String BLOCK_CROPS = "crops";
    public static final String BLOCK_NETHER = "nether_blocks";
    public static final String BLOCK_OTHER = "other";

    public static final List<String> MOB_CATEGORIES = List.of(
            MOB_HOSTILE, MOB_NETHER, MOB_END, MOB_BOSSES, MOB_DUNGEON, MOB_ANIMALS, MOB_WATER
    );
    public static final List<String> BLOCK_CATEGORIES = List.of(
            BLOCK_ORES, BLOCK_STONE, BLOCK_WOOD, BLOCK_DIRT, BLOCK_CROPS, BLOCK_NETHER, BLOCK_OTHER
    );

    private static final Map<String, Entry> MOBS = new LinkedHashMap<>();
    private static final Map<String, Entry> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, String> BLOCK_ALIASES = new LinkedHashMap<>();

    static {
        mob("ZOMBIE", "Zombie", Material.ZOMBIE_SPAWN_EGG, MOB_HOSTILE);
        mob("HUSK", "Husk", Material.HUSK_SPAWN_EGG, MOB_HOSTILE);
        mob("DROWNED", "Drowned", Material.DROWNED_SPAWN_EGG, MOB_HOSTILE);
        mob("SKELETON", "Skeleton", Material.SKELETON_SPAWN_EGG, MOB_HOSTILE);
        mob("STRAY", "Stray", Material.STRAY_SPAWN_EGG, MOB_HOSTILE);
        mob("BOGGED", "Bogged", Material.BOGGED_SPAWN_EGG, MOB_HOSTILE);
        mob("CREEPER", "Creeper", Material.CREEPER_SPAWN_EGG, MOB_HOSTILE);
        mob("SPIDER", "Spider", Material.SPIDER_SPAWN_EGG, MOB_HOSTILE);
        mob("CAVE_SPIDER", "Cave Spider", Material.CAVE_SPIDER_SPAWN_EGG, MOB_HOSTILE);
        mob("SLIME", "Slime", Material.SLIME_SPAWN_EGG, MOB_HOSTILE);
        mob("PHANTOM", "Phantom", Material.PHANTOM_SPAWN_EGG, MOB_HOSTILE);
        mob("WITCH", "Witch", Material.WITCH_SPAWN_EGG, MOB_HOSTILE);
        mob("PILLAGER", "Pillager", Material.PILLAGER_SPAWN_EGG, MOB_HOSTILE);
        mob("VINDICATOR", "Vindicator", Material.VINDICATOR_SPAWN_EGG, MOB_HOSTILE);
        mob("EVOKER", "Evoker", Material.EVOKER_SPAWN_EGG, MOB_HOSTILE);
        mob("RAVAGER", "Ravager", Material.RAVAGER_SPAWN_EGG, MOB_HOSTILE);
        mob("VEX", "Vex", Material.VEX_SPAWN_EGG, MOB_HOSTILE);
        mob("ENDERMAN", "Enderman", Material.ENDERMAN_SPAWN_EGG, MOB_HOSTILE);
        mob("SILVERFISH", "Silverfish", Material.SILVERFISH_SPAWN_EGG, MOB_HOSTILE);
        mob("WARDEN", "Warden", Material.WARDEN_SPAWN_EGG, MOB_HOSTILE);
        mob("BREEZE", "Breeze", Material.BREEZE_SPAWN_EGG, MOB_HOSTILE);
        mob("GUARDIAN", "Guardian", Material.GUARDIAN_SPAWN_EGG, MOB_HOSTILE);
        mob("ELDER_GUARDIAN", "Elder Guardian", Material.ELDER_GUARDIAN_SPAWN_EGG, MOB_HOSTILE);

        mob("BLAZE", "Blaze", Material.BLAZE_SPAWN_EGG, MOB_NETHER);
        mob("GHAST", "Ghast", Material.GHAST_SPAWN_EGG, MOB_NETHER);
        mob("MAGMA_CUBE", "Magma Cube", Material.MAGMA_CUBE_SPAWN_EGG, MOB_NETHER);
        mob("WITHER_SKELETON", "Wither Skeleton", Material.WITHER_SKELETON_SPAWN_EGG, MOB_NETHER);
        mob("PIGLIN", "Piglin", Material.PIGLIN_SPAWN_EGG, MOB_NETHER);
        mob("PIGLIN_BRUTE", "Piglin Brute", Material.PIGLIN_BRUTE_SPAWN_EGG, MOB_NETHER);
        mob("HOGLIN", "Hoglin", Material.HOGLIN_SPAWN_EGG, MOB_NETHER);
        mob("ZOGLIN", "Zoglin", Material.ZOGLIN_SPAWN_EGG, MOB_NETHER);
        mob("STRIDER", "Strider", Material.STRIDER_SPAWN_EGG, MOB_NETHER);
        mob("WITHER", "Wither", Material.WITHER_SPAWN_EGG, MOB_NETHER);

        mob("ENDERMITE", "Endermite", Material.ENDERMITE_SPAWN_EGG, MOB_END);
        mob("SHULKER", "Shulker", Material.SHULKER_SPAWN_EGG, MOB_END);
        mob("ENDER_DRAGON", "Ender Dragon", Material.DRAGON_HEAD, MOB_END);

        mob("boss:mcnugget", "McNugget", Material.COOKED_CHICKEN, MOB_BOSSES);
        mob("boss:hollow_lurker", "Hollow Lurker", Material.ENDER_EYE, MOB_BOSSES);
        mob("boss:pathwarden", "Pathwarden", Material.NETHERITE_SWORD, MOB_BOSSES);
        mob("boss:bridge_troll", "Bridge Troll", Material.IRON_AXE, MOB_BOSSES);
        mob("boss:squidward", "Squidward", Material.INK_SAC, MOB_BOSSES);
        mob("boss:skuldugery", "Skuldugery", Material.BOW, MOB_BOSSES);
        mob("boss:aetherion", "Aetherion", Material.NETHER_STAR, MOB_BOSSES);
        mob("boss:aether_colossus", "Aether Colossus", Material.END_CRYSTAL, MOB_BOSSES);
        mob("boss:sir_balthazar", "Sir Balthazar", Material.TOTEM_OF_UNDYING, MOB_BOSSES);
        mob("boss:lobby_cleaner", "The Lobby Cleaner", Material.HOPPER, MOB_BOSSES);
        mob("boss:sparky", "Sparky", Material.MAGMA_CREAM, MOB_BOSSES);
        mob("boss:baron_von_wurm", "Baron von Wurm", Material.STONE, MOB_BOSSES);
        mob("boss:insolvent_wither", "The Insolvent Wither", Material.NETHER_STAR, MOB_BOSSES);

        mob("dungeon:dungeon_zombie", "Dungeon Walker", Material.ZOMBIE_HEAD, MOB_DUNGEON);
        mob("dungeon:dungeon_skeleton", "Dungeon Archer", Material.SKELETON_SKULL, MOB_DUNGEON);
        mob("dungeon:dungeon_brute", "Dungeon Brute", Material.HUSK_SPAWN_EGG, MOB_DUNGEON);
        mob("dungeon:dungeon_sentinel", "Prototype Sentinel", Material.TOTEM_OF_UNDYING, MOB_DUNGEON);
        mob("boss:dungeon_sentinel", "Prototype Sentinel", Material.TOTEM_OF_UNDYING, MOB_DUNGEON);

        mob("COW", "Cow", Material.COW_SPAWN_EGG, MOB_ANIMALS);
        mob("PIG", "Pig", Material.PIG_SPAWN_EGG, MOB_ANIMALS);
        mob("SHEEP", "Sheep", Material.SHEEP_SPAWN_EGG, MOB_ANIMALS);
        mob("CHICKEN", "Chicken", Material.CHICKEN_SPAWN_EGG, MOB_ANIMALS);
        mob("RABBIT", "Rabbit", Material.RABBIT_SPAWN_EGG, MOB_ANIMALS);
        mob("HORSE", "Horse", Material.HORSE_SPAWN_EGG, MOB_ANIMALS);
        mob("DONKEY", "Donkey", Material.DONKEY_SPAWN_EGG, MOB_ANIMALS);
        mob("MULE", "Mule", Material.MULE_SPAWN_EGG, MOB_ANIMALS);
        mob("WOLF", "Wolf", Material.WOLF_SPAWN_EGG, MOB_ANIMALS);
        mob("CAT", "Cat", Material.CAT_SPAWN_EGG, MOB_ANIMALS);
        mob("FOX", "Fox", Material.FOX_SPAWN_EGG, MOB_ANIMALS);
        mob("BEE", "Bee", Material.BEE_SPAWN_EGG, MOB_ANIMALS);
        mob("GOAT", "Goat", Material.GOAT_SPAWN_EGG, MOB_ANIMALS);
        mob("CAMEL", "Camel", Material.CAMEL_SPAWN_EGG, MOB_ANIMALS);
        mob("SNIFFER", "Sniffer", Material.SNIFFER_SPAWN_EGG, MOB_ANIMALS);
        mob("PANDA", "Panda", Material.PANDA_SPAWN_EGG, MOB_ANIMALS);
        mob("POLAR_BEAR", "Polar Bear", Material.POLAR_BEAR_SPAWN_EGG, MOB_ANIMALS);
        mob("LLAMA", "Llama", Material.LLAMA_SPAWN_EGG, MOB_ANIMALS);
        mob("PARROT", "Parrot", Material.PARROT_SPAWN_EGG, MOB_ANIMALS);
        mob("BAT", "Bat", Material.BAT_SPAWN_EGG, MOB_ANIMALS);
        mob("VILLAGER", "Villager", Material.VILLAGER_SPAWN_EGG, MOB_ANIMALS);
        mob("IRON_GOLEM", "Iron Golem", Material.IRON_BLOCK, MOB_ANIMALS);
        mob("SNOW_GOLEM", "Snow Golem", Material.SNOW_BLOCK, MOB_ANIMALS);
        mob("TURTLE", "Turtle", Material.TURTLE_SPAWN_EGG, MOB_ANIMALS);
        mob("FROG", "Frog", Material.FROG_SPAWN_EGG, MOB_ANIMALS);

        mob("SQUID", "Squid", Material.SQUID_SPAWN_EGG, MOB_WATER);
        mob("GLOW_SQUID", "Glow Squid", Material.GLOW_SQUID_SPAWN_EGG, MOB_WATER);
        mob("DOLPHIN", "Dolphin", Material.DOLPHIN_SPAWN_EGG, MOB_WATER);
        mob("COD", "Cod", Material.COD_SPAWN_EGG, MOB_WATER);
        mob("SALMON", "Salmon", Material.SALMON_SPAWN_EGG, MOB_WATER);
        mob("TROPICAL_FISH", "Tropical Fish", Material.TROPICAL_FISH_SPAWN_EGG, MOB_WATER);
        mob("PUFFERFISH", "Pufferfish", Material.PUFFERFISH_SPAWN_EGG, MOB_WATER);
        mob("AXOLOTL", "Axolotl", Material.AXOLOTL_SPAWN_EGG, MOB_WATER);
        mob("TADPOLE", "Tadpole", Material.TADPOLE_SPAWN_EGG, MOB_WATER);

        block("coal", "Coal", Material.COAL, BLOCK_ORES, "COAL_ORE", "DEEPSLATE_COAL_ORE");
        block("iron", "Iron", Material.IRON_INGOT, BLOCK_ORES, "IRON_ORE", "DEEPSLATE_IRON_ORE");
        block("gold", "Gold", Material.GOLD_INGOT, BLOCK_ORES, "GOLD_ORE", "DEEPSLATE_GOLD_ORE");
        block("copper", "Copper", Material.COPPER_INGOT, BLOCK_ORES, "COPPER_ORE", "DEEPSLATE_COPPER_ORE");
        block("diamond", "Diamond", Material.DIAMOND, BLOCK_ORES, "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE");
        block("emerald", "Emerald", Material.EMERALD, BLOCK_ORES, "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE");
        block("lapis", "Lapis", Material.LAPIS_LAZULI, BLOCK_ORES, "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE");
        block("redstone", "Redstone", Material.REDSTONE, BLOCK_ORES, "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE");
        block("quartz", "Nether Quartz", Material.QUARTZ, BLOCK_ORES, "NETHER_QUARTZ_ORE");
        block("nether_gold", "Nether Gold", Material.GOLD_NUGGET, BLOCK_ORES, "NETHER_GOLD_ORE");
        block("ancient_debris", "Ancient Debris", Material.NETHERITE_INGOT, BLOCK_ORES, "ANCIENT_DEBRIS");
        block("amethyst", "Amethyst", Material.AMETHYST_SHARD, BLOCK_ORES, "AMETHYST_CLUSTER", "BUDDING_AMETHYST");

        block("stone", "Stone", Material.STONE, BLOCK_STONE, "STONE", "COBBLESTONE");
        block("deepslate", "Deepslate", Material.DEEPSLATE, BLOCK_STONE, "DEEPSLATE", "COBBLED_DEEPSLATE");
        block("granite", "Granite", Material.GRANITE, BLOCK_STONE, "GRANITE");
        block("diorite", "Diorite", Material.DIORITE, BLOCK_STONE, "DIORITE");
        block("andesite", "Andesite", Material.ANDESITE, BLOCK_STONE, "ANDESITE");
        block("tuff", "Tuff", Material.TUFF, BLOCK_STONE, "TUFF");
        block("calcite", "Calcite", Material.CALCITE, BLOCK_STONE, "CALCITE");
        block("dripstone", "Dripstone", Material.POINTED_DRIPSTONE, BLOCK_STONE, "DRIPSTONE_BLOCK", "POINTED_DRIPSTONE");
        block("obsidian", "Obsidian", Material.OBSIDIAN, BLOCK_STONE, "OBSIDIAN");
        block("end_stone", "End Stone", Material.END_STONE, BLOCK_STONE, "END_STONE");

        block("oak", "Oak", Material.OAK_LOG, BLOCK_WOOD, "OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "OAK_LEAVES");
        block("spruce", "Spruce", Material.SPRUCE_LOG, BLOCK_WOOD, "SPRUCE_LOG", "SPRUCE_WOOD", "STRIPPED_SPRUCE_LOG", "SPRUCE_LEAVES");
        block("birch", "Birch", Material.BIRCH_LOG, BLOCK_WOOD, "BIRCH_LOG", "BIRCH_WOOD", "STRIPPED_BIRCH_LOG", "BIRCH_LEAVES");
        block("jungle", "Jungle", Material.JUNGLE_LOG, BLOCK_WOOD, "JUNGLE_LOG", "JUNGLE_WOOD", "STRIPPED_JUNGLE_LOG", "JUNGLE_LEAVES");
        block("acacia", "Acacia", Material.ACACIA_LOG, BLOCK_WOOD, "ACACIA_LOG", "ACACIA_WOOD", "STRIPPED_ACACIA_LOG", "ACACIA_LEAVES");
        block("dark_oak", "Dark Oak", Material.DARK_OAK_LOG, BLOCK_WOOD, "DARK_OAK_LOG", "DARK_OAK_WOOD", "STRIPPED_DARK_OAK_LOG", "DARK_OAK_LEAVES");
        block("mangrove", "Mangrove", Material.MANGROVE_LOG, BLOCK_WOOD, "MANGROVE_LOG", "MANGROVE_WOOD", "STRIPPED_MANGROVE_LOG", "MANGROVE_LEAVES", "MANGROVE_ROOTS");
        block("cherry", "Cherry", Material.CHERRY_LOG, BLOCK_WOOD, "CHERRY_LOG", "CHERRY_WOOD", "STRIPPED_CHERRY_LOG", "CHERRY_LEAVES");
        block("bamboo", "Bamboo", Material.BAMBOO, BLOCK_WOOD, "BAMBOO", "BAMBOO_BLOCK");
        block("crimson", "Crimson", Material.CRIMSON_STEM, BLOCK_WOOD, "CRIMSON_STEM", "STRIPPED_CRIMSON_STEM", "CRIMSON_HYPHAE");
        block("warped", "Warped", Material.WARPED_STEM, BLOCK_WOOD, "WARPED_STEM", "STRIPPED_WARPED_STEM", "WARPED_HYPHAE");

        block("dirt", "Dirt", Material.DIRT, BLOCK_DIRT, "DIRT", "COARSE_DIRT", "ROOTED_DIRT");
        block("grass", "Grass", Material.GRASS_BLOCK, BLOCK_DIRT, "GRASS_BLOCK", "PODZOL", "MYCELIUM");
        block("sand", "Sand", Material.SAND, BLOCK_DIRT, "SAND", "RED_SAND");
        block("gravel", "Gravel", Material.GRAVEL, BLOCK_DIRT, "GRAVEL");
        block("clay", "Clay", Material.CLAY, BLOCK_DIRT, "CLAY");
        block("snow", "Snow", Material.SNOW_BLOCK, BLOCK_DIRT, "SNOW", "SNOW_BLOCK", "POWDER_SNOW");
        block("moss", "Moss", Material.MOSS_BLOCK, BLOCK_DIRT, "MOSS_BLOCK", "MOSS_CARPET");

        block("wheat", "Wheat", Material.WHEAT, BLOCK_CROPS, "WHEAT");
        block("carrot", "Carrot", Material.CARROT, BLOCK_CROPS, "CARROTS");
        block("potato", "Potato", Material.POTATO, BLOCK_CROPS, "POTATOES");
        block("beetroot", "Beetroot", Material.BEETROOT, BLOCK_CROPS, "BEETROOTS");
        block("nether_wart", "Nether Wart", Material.NETHER_WART, BLOCK_CROPS, "NETHER_WART");
        block("sugar_cane", "Sugar Cane", Material.SUGAR_CANE, BLOCK_CROPS, "SUGAR_CANE");
        block("melon", "Melon", Material.MELON, BLOCK_CROPS, "MELON", "MELON_STEM");
        block("pumpkin", "Pumpkin", Material.PUMPKIN, BLOCK_CROPS, "PUMPKIN", "PUMPKIN_STEM");
        block("cocoa", "Cocoa", Material.COCOA_BEANS, BLOCK_CROPS, "COCOA");
        block("sweet_berries", "Sweet Berries", Material.SWEET_BERRIES, BLOCK_CROPS, "SWEET_BERRY_BUSH");
        block("glow_berries", "Glow Berries", Material.GLOW_BERRIES, BLOCK_CROPS, "CAVE_VINES", "CAVE_VINES_PLANT");

        block("netherrack", "Netherrack", Material.NETHERRACK, BLOCK_NETHER, "NETHERRACK");
        block("soul_sand", "Soul Sand", Material.SOUL_SAND, BLOCK_NETHER, "SOUL_SAND", "SOUL_SOIL");
        block("basalt", "Basalt", Material.BASALT, BLOCK_NETHER, "BASALT", "SMOOTH_BASALT", "POLISHED_BASALT");
        block("blackstone", "Blackstone", Material.BLACKSTONE, BLOCK_NETHER, "BLACKSTONE", "GILDED_BLACKSTONE");
        block("magma", "Magma", Material.MAGMA_BLOCK, BLOCK_NETHER, "MAGMA_BLOCK");
        block("glowstone", "Glowstone", Material.GLOWSTONE, BLOCK_NETHER, "GLOWSTONE");
        block("nether_wart_block", "Nether Wart Block", Material.NETHER_WART_BLOCK, BLOCK_NETHER, "NETHER_WART_BLOCK", "WARPED_WART_BLOCK");
        block("shroomlight", "Shroomlight", Material.SHROOMLIGHT, BLOCK_NETHER, "SHROOMLIGHT");

        block("ice", "Ice", Material.ICE, BLOCK_OTHER, "ICE", "PACKED_ICE", "BLUE_ICE");
        block("prismarine", "Prismarine", Material.PRISMARINE, BLOCK_OTHER, "PRISMARINE", "DARK_PRISMARINE", "PRISMARINE_BRICKS", "SEA_LANTERN");
        block("sponge", "Sponge", Material.SPONGE, BLOCK_OTHER, "SPONGE", "WET_SPONGE");
        block("sculk", "Sculk", Material.SCULK, BLOCK_OTHER, "SCULK", "SCULK_VEIN", "SCULK_CATALYST", "SCULK_SENSOR", "SCULK_SHRIEKER");
        block("crying_obsidian", "Crying Obsidian", Material.CRYING_OBSIDIAN, BLOCK_OTHER, "CRYING_OBSIDIAN");
    }

    private CodexCatalog() {
    }

    public static String categoryTitle(String category) {
        return switch (category) {
            case MOB_HOSTILE -> "Overworld Hostile";
            case MOB_NETHER -> "Nether Mobs";
            case MOB_END -> "The End";
            case MOB_BOSSES -> "Bosses";
            case MOB_DUNGEON -> "Dungeon";
            case MOB_ANIMALS -> "Animals";
            case MOB_WATER -> "Water";
            case BLOCK_ORES -> "Ores";
            case BLOCK_STONE -> "Stone";
            case BLOCK_WOOD -> "Wood";
            case BLOCK_DIRT -> "Dirt & Sand";
            case BLOCK_CROPS -> "Crops";
            case BLOCK_NETHER -> "Nether Blocks";
            case BLOCK_OTHER -> "Other";
            default -> category;
        };
    }

    public static Material categoryIcon(String category) {
        return switch (category) {
            case MOB_HOSTILE -> Material.IRON_SWORD;
            case MOB_NETHER -> Material.BLAZE_POWDER;
            case MOB_END -> Material.ENDER_PEARL;
            case MOB_BOSSES -> Material.NETHER_STAR;
            case MOB_DUNGEON -> Material.DEEPSLATE_BRICKS;
            case MOB_ANIMALS -> Material.LEATHER;
            case MOB_WATER -> Material.HEART_OF_THE_SEA;
            case BLOCK_ORES -> Material.DIAMOND;
            case BLOCK_STONE -> Material.COBBLESTONE;
            case BLOCK_WOOD -> Material.OAK_LOG;
            case BLOCK_DIRT -> Material.DIRT;
            case BLOCK_CROPS -> Material.WHEAT;
            case BLOCK_NETHER -> Material.NETHERRACK;
            case BLOCK_OTHER -> Material.CHEST;
            default -> Material.PAPER;
        };
    }

    public static Entry mob(String id) {
        return MOBS.get(id);
    }

    public static Entry block(String id) {
        return BLOCKS.get(id);
    }

    public static List<Entry> mobs(String category) {
        return MOBS.values().stream().filter(entry -> entry.category().equals(category)).toList();
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

    public static List<Entry> allMobs() {
        return new ArrayList<>(MOBS.values());
    }

    public static List<Entry> allBlocks() {
        return new ArrayList<>(BLOCKS.values());
    }

    private static void mob(String id, String name, Material icon, String category) {
        MOBS.put(id, new Entry(id, name, icon, category));
    }

    private static void block(String id, String name, Material icon, String category, String... materials) {
        BLOCKS.put(id, new Entry(id, name, icon, category));
        for (String material : materials) {
            BLOCK_ALIASES.put(material.toUpperCase(Locale.ROOT), id);
        }
    }
}
