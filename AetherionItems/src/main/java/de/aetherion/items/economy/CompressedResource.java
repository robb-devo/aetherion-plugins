package de.aetherion.items.economy;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum CompressedResource {

    COBBLESTONE(
            "cobblestone",
            Material.COBBLESTONE,
            Material.COBBLESTONE,
            "§7Compressed Cobblestone",
            "§bCompacted Cobblestone",
            "09b6af55f2f3bbd5b23387f43b7c4a84676b86b2892f8f4cc0a9f81377c79"
    ),
    COAL(
            "coal",
            Material.COAL,
            Material.COAL_BLOCK,
            "§8Compressed Coal",
            "§bCompacted Coal",
            "a67b47fbd37c6d9d8d22d973e7290e80852e9266a076fb7ab215afe91b81ed6c"
    ),
    RAW_IRON(
            "raw_iron",
            Material.IRON_INGOT,
            Material.IRON_BLOCK,
            "§fCompressed Iron",
            "§bCompacted Iron",
            "46a5da5a4d2b8213b2e98e2c5be5bd89a5c791492ca7c46c174d9a1376f0503d"
    ),
    RAW_GOLD(
            "raw_gold",
            Material.GOLD_INGOT,
            Material.GOLD_BLOCK,
            "§6Compressed Gold",
            "§bCompacted Gold",
            "97f57e7aa8de86591bb0bc52cba30a49d931bfabbd47bbc80bdd662251392161"
    ),
    RAW_COPPER(
            "raw_copper",
            Material.COPPER_INGOT,
            Material.COPPER_BLOCK,
            "§6Compressed Copper",
            "§bCompacted Copper",
            "40a6e6aa7ce99c81daf816d69638d52b2882a2558880222e9a466dcedc88015a"
    ),
    REDSTONE(
            "redstone",
            Material.REDSTONE,
            Material.REDSTONE_BLOCK,
            "§cCompressed Redstone",
            "§bCompacted Redstone",
            "bb78fa5defe72debcd9c76ab9f4e114250479bb9b44f42887bbf6f738612b"
    ),
    LAPIS(
            "lapis",
            Material.LAPIS_LAZULI,
            Material.LAPIS_BLOCK,
            "§9Compressed Lapis",
            "§bCompacted Lapis",
            "86f476871ed23f79e7b9d495489887e244c619c5e19e41cf95b271a2ebe75"
    ),
    DIAMOND(
            "diamond",
            Material.DIAMOND,
            Material.DIAMOND_BLOCK,
            "§bCompressed Diamond",
            "§bCompacted Diamond",
            "dbfc7fa4e74b6e401ae064148ae5e768b893f1d9297f87475101f7225a2a5a6a"
    ),
    EMERALD(
            "emerald",
            Material.EMERALD,
            Material.EMERALD_BLOCK,
            "§aCompressed Emerald",
            "§bCompacted Emerald",
            "bc0e6d9e242735481918c5fd14498bd760bb9f4ff6430ad4696b38e8a883da97"
    ),
    OAK_LOG(
            "oak_log",
            Material.OAK_LOG,
            Material.OAK_LOG,
            "§6Compressed Oak Log",
            "§bCompacted Oak Log",
            "8729613664e38d914de40f8e4aec3f34f476d8881fe789fc3b839903a865e85f"
    ),
    LEATHER(
            "leather",
            Material.LEATHER,
            Material.BROWN_WOOL,
            "§6Compressed Leather",
            "§bCompacted Leather",
            null,
            true
    ),
    BONE(
            "bone",
            Material.BONE,
            Material.BONE_BLOCK,
            "§fCompressed Bone",
            "§bCompacted Bone",
            "1e8f36b2715ae4d897acda7f06cb2b053de5328869e3fa7608ef5ec75238ad2d",
            true
    ),
    STRING(
            "string",
            Material.STRING,
            Material.WHITE_WOOL,
            "§fCompressed String",
            "§bCompacted String",
            null,
            true
    ),
    WHEAT(
            "wheat",
            Material.WHEAT,
            Material.HAY_BLOCK,
            "§eCompressed Wheat",
            "§bCompacted Wheat",
            null,
            true
    ),
    CARROT(
            "carrot",
            Material.CARROT,
            Material.ORANGE_WOOL,
            "§6Compressed Carrot",
            "§bCompacted Carrot",
            null,
            true
    ),
    POTATO(
            "potato",
            Material.POTATO,
            Material.BROWN_TERRACOTTA,
            "§eCompressed Potato",
            "§bCompacted Potato",
            null,
            true
    ),
    GUNPOWDER(
            "gunpowder",
            Material.GUNPOWDER,
            Material.TNT,
            "§8Compressed Gunpowder",
            "§bCompacted Gunpowder",
            "fdd4364fe2b19a6c119d17b56944eff566c14b4fea45e9b4bc32928d597f468",
            true
    ),
    FEATHER(
            "feather",
            Material.FEATHER,
            Material.WHITE_WOOL,
            "§fCompressed Feather",
            "§bCompacted Feather",
            null,
            true
    ),
    ROTTEN_FLESH(
            "rotten_flesh",
            Material.ROTTEN_FLESH,
            Material.NETHERRACK,
            "§cCompressed Rotten Flesh",
            "§bCompacted Rotten Flesh",
            "70c8e01738278abc99bcc63549ddfd3cce174761b136b01da01af6b8ba630e71",
            true
    ),
    COD(
            "cod",
            Material.COD,
            Material.DRIED_KELP_BLOCK,
            "§eCompressed Cod",
            "§bCompacted Cod",
            "4feeff4b7fcfce68b0f74df0db0ad0c01f7301d0c6d893699b402bd50bb376b0",
            null,
            true
    ),
    SALMON(
            "salmon",
            Material.SALMON,
            Material.ORANGE_CONCRETE,
            "§6Compressed Salmon",
            "§bCompacted Salmon",
            "20ea9a223620cdb54b357413d43bd89c4008bca6a227f3b7db97f7733ead5fcf",
            null,
            true
    ),
    PUFFERFISH(
            "pufferfish",
            Material.PUFFERFISH,
            Material.YELLOW_CONCRETE,
            "§eCompressed Pufferfish",
            "§bCompacted Pufferfish",
            "c3b1955d3b6eb42f50e536c1a3285ab73ed7e2be051b09b21e17811f1a6d",
            null,
            true
    ),
    BIRCH_LOG(
            "birch_log",
            Material.BIRCH_LOG,
            Material.BIRCH_LOG,
            "§fCompressed Birch Log",
            "§bCompacted Birch Log",
            "dd5f9a5f2db4eaeae87dd4d981faa91d5f87b8990bd38f25c9937f4643e567ac",
            true
    ),
    SPRUCE_LOG(
            "spruce_log",
            Material.SPRUCE_LOG,
            Material.SPRUCE_LOG,
            "§8Compressed Spruce Log",
            "§bCompacted Spruce Log",
            "dd5f9a5f2db4eaeae87dd4d981faa91d5f87b8990bd38f25c9937f4643e567ac",
            true
    ),
    JUNGLE_LOG(
            "jungle_log",
            Material.JUNGLE_LOG,
            Material.JUNGLE_LOG,
            "§2Compressed Jungle Log",
            "§bCompacted Jungle Log",
            "8729613664e38d914de40f8e4aec3f34f476d8881fe789fc3b839903a865e85f",
            true
    ),
    ACACIA_LOG(
            "acacia_log",
            Material.ACACIA_LOG,
            Material.ACACIA_LOG,
            "§6Compressed Acacia Log",
            "§bCompacted Acacia Log",
            "f05f7526ea9332390462ae8c5381179cb7e66d1ab2c94cdae9109ccb4feb5131",
            true
    ),
    DARK_OAK_LOG(
            "dark_oak_log",
            Material.DARK_OAK_LOG,
            Material.DARK_OAK_LOG,
            "§8Compressed Dark Oak Log",
            "§bCompacted Dark Oak Log",
            "f0ec6cf3fcb59fb63621442d2130bd9b82480d4d73d9a0775fd38992195d49ae",
            true
    ),
    MANGROVE_LOG(
            "mangrove_log",
            Material.MANGROVE_LOG,
            Material.MANGROVE_LOG,
            "§4Compressed Mangrove Log",
            "§bCompacted Mangrove Log",
            "f0ec6cf3fcb59fb63621442d2130bd9b82480d4d73d9a0775fd38992195d49ae",
            true
    ),
    CHERRY_LOG(
            "cherry_log",
            Material.CHERRY_LOG,
            Material.CHERRY_LOG,
            "§dCompressed Cherry Log",
            "§bCompacted Cherry Log",
            "f05f7526ea9332390462ae8c5381179cb7e66d1ab2c94cdae9109ccb4feb5131",
            true
    ),
    BAMBOO_BLOCK(
            "bamboo_block",
            Material.BAMBOO_BLOCK,
            Material.BAMBOO_BLOCK,
            "§aCompressed Bamboo",
            "§bCompacted Bamboo",
            "8729613664e38d914de40f8e4aec3f34f476d8881fe789fc3b839903a865e85f",
            true
    ),
    /** Kept in enum for later nether content — not in current recipes / forage flow. */
    CRIMSON_STEM(
            "crimson_stem",
            Material.CRIMSON_STEM,
            Material.CRIMSON_STEM,
            "§cCompressed Crimson Stem",
            "§bCompacted Crimson Stem",
            null,
            true,
            false
    ),
    /** Kept in enum for later nether content — not in current recipes / forage flow. */
    WARPED_STEM(
            "warped_stem",
            Material.WARPED_STEM,
            Material.WARPED_STEM,
            "§3Compressed Warped Stem",
            "§bCompacted Warped Stem",
            null,
            true,
            false
    ),
    SUGAR_CANE(
            "sugar_cane",
            Material.SUGAR_CANE,
            Material.LIME_TERRACOTTA,
            "§aCompressed Sugar Cane",
            "§bCompacted Sugar Cane",
            null,
            true
    ),
    NETHER_WART(
            "nether_wart",
            Material.NETHER_WART,
            Material.NETHER_WART_BLOCK,
            "§cCompressed Nether Wart",
            "§bCompacted Nether Wart",
            null,
            true
    ),
    PRISMARINE_SHARD(
            "prismarine_shard",
            Material.PRISMARINE_SHARD,
            Material.PRISMARINE,
            "§3Compressed Prismarine",
            "§bCompacted Prismarine",
            null,
            true
    );

    private final String key;
    private final Material input;
    private final Material compactedBlock;
    private final String compressedName;
    private final String compactedName;
    private final String textureHash;
    private final String compactedHash;
    private final boolean quarry;
    /** When false: kept for later (e.g. nether woods) but hidden from recipes / menus / forage. */
    private final boolean contentEnabled;

    CompressedResource(
            String key,
            Material input,
            Material compactedBlock,
            String compressedName,
            String compactedName,
            String textureHash
    ) {
        this(key, input, compactedBlock, compressedName, compactedName, textureHash, null, true, true);
    }

    CompressedResource(
            String key,
            Material input,
            Material compactedBlock,
            String compressedName,
            String compactedName,
            String textureHash,
            boolean quarry
    ) {
        this(key, input, compactedBlock, compressedName, compactedName, textureHash, null, quarry, true);
    }

    CompressedResource(
            String key,
            Material input,
            Material compactedBlock,
            String compressedName,
            String compactedName,
            String textureHash,
            boolean quarry,
            boolean contentEnabled
    ) {
        this(key, input, compactedBlock, compressedName, compactedName, textureHash, null, quarry, contentEnabled);
    }

    CompressedResource(
            String key,
            Material input,
            Material compactedBlock,
            String compressedName,
            String compactedName,
            String textureHash,
            String compactedHash,
            boolean quarry
    ) {
        this(key, input, compactedBlock, compressedName, compactedName, textureHash, compactedHash, quarry, true);
    }

    CompressedResource(
            String key,
            Material input,
            Material compactedBlock,
            String compressedName,
            String compactedName,
            String textureHash,
            String compactedHash,
            boolean quarry,
            boolean contentEnabled
    ) {
        this.key = key;
        this.input = input;
        this.compactedBlock = compactedBlock;
        this.compressedName = compressedName;
        this.compactedName = compactedName;
        this.textureHash = textureHash;
        this.compactedHash = compactedHash;
        this.quarry = quarry;
        this.contentEnabled = contentEnabled;
    }

    public boolean contentEnabled() {
        return contentEnabled;
    }

    public static java.util.List<CompressedResource> contentOrdered() {
        java.util.List<CompressedResource> list = new java.util.ArrayList<>();
        for (CompressedResource resource : values()) {
            if (resource.contentEnabled()) {
                list.add(resource);
            }
        }
        list.sort(java.util.Comparator.comparingInt(CompressedResource::displayOrder));
        return list;
    }

    /** Mining → woods → farm → mob drops → fish. */
    public int displayOrder() {
        return switch (this) {
            case COBBLESTONE -> 10;
            case COAL -> 20;
            case RAW_IRON -> 30;
            case RAW_GOLD -> 40;
            case RAW_COPPER -> 50;
            case REDSTONE -> 60;
            case LAPIS -> 70;
            case DIAMOND -> 80;
            case EMERALD -> 90;
            case OAK_LOG -> 200;
            case BIRCH_LOG -> 210;
            case SPRUCE_LOG -> 220;
            case JUNGLE_LOG -> 230;
            case ACACIA_LOG -> 240;
            case DARK_OAK_LOG -> 250;
            case MANGROVE_LOG -> 260;
            case CHERRY_LOG -> 270;
            case BAMBOO_BLOCK -> 280;
            case CRIMSON_STEM -> 900;
            case WARPED_STEM -> 910;
            case WHEAT -> 400;
            case CARROT -> 410;
            case POTATO -> 420;
            case SUGAR_CANE -> 430;
            case NETHER_WART -> 440;
            case LEATHER -> 500;
            case BONE -> 510;
            case STRING -> 520;
            case FEATHER -> 530;
            case GUNPOWDER -> 540;
            case ROTTEN_FLESH -> 550;
            case COD -> 600;
            case SALMON -> 610;
            case PUFFERFISH -> 620;
            case PRISMARINE_SHARD -> 630;
        };
    }

    public String key() {
        return key;
    }

    public Material input() {
        return input;
    }

    public Material compactedBlock() {
        return compactedBlock;
    }

    public boolean hasQuarry() {
        return quarry;
    }

    /** Ore / stone drops — Pack Rat mining compact only. */
    public boolean isMiningDrop() {
        return switch (this) {
            case COBBLESTONE, COAL, RAW_IRON, RAW_GOLD, RAW_COPPER,
                 REDSTONE, LAPIS, DIAMOND, EMERALD -> true;
            default -> false;
        };
    }

    /** Foraging wood drops — Timber Tax only. */
    public boolean isForagingDrop() {
        return switch (this) {
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG, CHERRY_LOG, BAMBOO_BLOCK -> true;
            default -> false;
        };
    }

    /** Crop drops — Seed Ledger only. */
    public boolean isFarmingDrop() {
        return this == WHEAT || this == CARROT || this == POTATO
                || this == SUGAR_CANE || this == NETHER_WART;
    }

    /** Fish drops — Fish Ledger only. */
    public boolean isFishingDrop() {
        return this == COD || this == SALMON || this == PUFFERFISH || this == PRISMARINE_SHARD;
    }

    public String compressedId() {
        return "compressed_" + key;
    }

    public String compactedId() {
        return "compacted_" + key;
    }

    /** Millstone output — farm crops only. */
    public String refinedId() {
        return "refined_" + key;
    }

    public boolean canRefine() {
        // Millstone pantry loop: wheat / carrot / potato only.
        return this == WHEAT || this == CARROT || this == POTATO;
    }

    public String refinedName() {
        return switch (this) {
            case WHEAT -> "§6Pantry Wheat";
            case CARROT -> "§6Pantry Carrot";
            case POTATO -> "§6Pantry Potato";
            case SUGAR_CANE -> "§6Pantry Cane";
            case NETHER_WART -> "§6Pantry Wart";
            default -> "§6Refined " + pretty(key);
        };
    }

    public String quarryItemId() {
        return "quarry_" + key;
    }

    public String compactedHash() {
        return compactedHash;
    }

    public String quarryTypeId() {
        return this == COBBLESTONE ? "cobble_quarry" : key + "_quarry";
    }

    public String quarryDisplay() {
        return switch (this) {
            case COBBLESTONE -> "Cobble Quarry";
            case RAW_IRON -> "Iron Quarry";
            case RAW_GOLD -> "Gold Quarry";
            case RAW_COPPER -> "Copper Quarry";
            case OAK_LOG -> "Oak Quarry";
            case BIRCH_LOG -> "Birch Quarry";
            case SPRUCE_LOG -> "Spruce Quarry";
            case JUNGLE_LOG -> "Jungle Quarry";
            case ACACIA_LOG -> "Acacia Quarry";
            case DARK_OAK_LOG -> "Dark Oak Quarry";
            case MANGROVE_LOG -> "Mangrove Quarry";
            case CHERRY_LOG -> "Cherry Quarry";
            case BAMBOO_BLOCK -> "Bamboo Quarry";
            case CRIMSON_STEM -> "Crimson Quarry";
            case WARPED_STEM -> "Warped Quarry";
            case LEATHER -> "Leather Quarry";
            case BONE -> "Bone Quarry";
            case STRING -> "String Quarry";
            case WHEAT -> "Wheat Quarry";
            case CARROT -> "Carrot Quarry";
            case POTATO -> "Potato Quarry";
            case SUGAR_CANE -> "Sugar Cane Quarry";
            case NETHER_WART -> "Nether Wart Quarry";
            case GUNPOWDER -> "Gunpowder Quarry";
            case FEATHER -> "Feather Quarry";
            case ROTTEN_FLESH -> "Rotten Quarry";
            case COD -> "Cod Quarry";
            case SALMON -> "Salmon Quarry";
            case PUFFERFISH -> "Pufferfish Quarry";
            case PRISMARINE_SHARD -> "Prismarine Quarry";
            default -> pretty(key) + " Quarry";
        };
    }

    public String prettyName() {
        return pretty(key);
    }

    public String flavor() {
        return switch (this) {
            case COBBLESTONE -> "§7Rocks that learned to sit still.";
            case COAL -> "§7Night sky, now in briquette form.";
            case RAW_IRON -> "§7Ore with a gym membership.";
            case RAW_GOLD -> "§7Shiny. Still not paying rent.";
            case RAW_COPPER -> "§7The metal that oxidizes on purpose.";
            case REDSTONE -> "§7Dust that thinks it has opinions.";
            case LAPIS -> "§7Blue enough to start a rumor.";
            case DIAMOND -> "§7Pressure, but make it jewelry.";
            case EMERALD -> "§7Villagers can smell this from spawn.";
            case OAK_LOG -> "§7A forest that filed for compression.";
            case BIRCH_LOG -> "§7Pale wood. Still opinionated.";
            case SPRUCE_LOG -> "§7Cold climate, warm stacks.";
            case JUNGLE_LOG -> "§7Canopy overtime in a neat stack.";
            case ACACIA_LOG -> "§7Savanna shade, vacuum packed.";
            case DARK_OAK_LOG -> "§7Thicket night, shelf-stable.";
            case MANGROVE_LOG -> "§7Brackish overtime. Roots included.";
            case CHERRY_LOG -> "§7Blossoms compressed. Petals pending.";
            case BAMBOO_BLOCK -> "§7A grove that learned to sit still.";
            case CRIMSON_STEM -> "§7Nether redwood with a work ethic.";
            case WARPED_STEM -> "§7Blue fungus that got organized.";
            case LEATHER -> "§7Several cows, one polite stack.";
            case BONE -> "§7The skeleton's retirement plan.";
            case STRING -> "§7Webs that got their life together.";
            case WHEAT -> "§7A field, pretending to be a loaf.";
            case CARROT -> "§7Orange. Crunchy. Unionized.";
            case POTATO -> "§7A tuber that filed for compression.";
            case SUGAR_CANE -> "§7Sweet overtime. Paper pending.";
            case NETHER_WART -> "§7Nether spice. Shelf-stable spite.";
            case GUNPOWDER -> "§7Creeper diplomacy, vacuum packed.";
            case FEATHER -> "§7Flight, cancelled and folded.";
            case ROTTEN_FLESH -> "§7Zombies send their regards. Warmly.";
            case COD -> "§7A school that learned to sit still.";
            case SALMON -> "§7Upstream, now shelf-stable.";
            case PUFFERFISH -> "§7Inflated ego. Compressed anyway.";
            case PRISMARINE_SHARD -> "§7Ocean glass with a work ethic.";
        };
    }

    public String quarryBlurb() {
        return switch (this) {
            case COBBLESTONE -> "§8It mines the floor. The floor filed a complaint.";
            case COAL -> "§8A night shift that never learned to clock out.";
            case RAW_IRON -> "§8Eats stone. Pays in ingots and attitude.";
            case RAW_GOLD -> "§8Still not paying rent. Now it's unionized.";
            case RAW_COPPER -> "§8Oxidizes on a schedule. Very professional.";
            case REDSTONE -> "§8Click. Click. Profit. Click.";
            case LAPIS -> "§8Enchanting tables hate this one trick.";
            case DIAMOND -> "§8Pressure, but make it a business model.";
            case EMERALD -> "§8Villagers can smell this from spawn.";
            case OAK_LOG -> "§8A forest with a punch clock.";
            case BIRCH_LOG -> "§8Pale overtime. The bees filed a complaint.";
            case SPRUCE_LOG -> "§8Cold climate mill. Warm profits.";
            case JUNGLE_LOG -> "§8Canopy mill. Monkeys unionized.";
            case ACACIA_LOG -> "§8Savanna mill. Shade on a schedule.";
            case DARK_OAK_LOG -> "§8Thicket mill. Night shift forever.";
            case MANGROVE_LOG -> "§8Swamp mill. Frogs approved.";
            case CHERRY_LOG -> "§8Blossom mill. Petals optional.";
            case BAMBOO_BLOCK -> "§8Bamboo mill. Pandas filed a memo.";
            case CRIMSON_STEM -> "§8Crimson mill. Nether HR shrugged.";
            case WARPED_STEM -> "§8Warped mill. Dreams, automated.";
            case LEATHER -> "§8No cows were interviewed for this quarry.";
            case BONE -> "§8The skeleton's retirement plan, automated.";
            case STRING -> "§8Spider HR approved this installation.";
            case WHEAT -> "§8A field that learned to sit still. Then work.";
            case CARROT -> "§8Orange overtime. The rabbits filed a complaint.";
            case POTATO -> "§8A tuber with a punch clock.";
            case SUGAR_CANE -> "§8Sweet mill. Paperwork optional.";
            case NETHER_WART -> "§8Nether spice on a schedule.";
            case GUNPOWDER -> "§8Creeper diplomacy, now with a mill.";
            case FEATHER -> "§8Flight, cancelled. Production, not.";
            case ROTTEN_FLESH -> "§8Zombies send leftovers. Warmly.";
            case COD -> "§8A school that unionized and got a mill.";
            case SALMON -> "§8Upstream, automated.";
            case PUFFERFISH -> "§8Inflated ego. Still on the clock.";
            case PRISMARINE_SHARD -> "§8Ocean glass with a mill and a grudge.";
        };
    }

    public static String flavorFor(String itemId) {
        CompressedResource resource = byItemId(itemId);
        return resource == null ? null : resource.flavor();
    }

    public ItemStack compressed() {
        ItemStack item = usesHead() ? TexturedHeads.hashed(textureHash) : new ItemStack(input);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, compressedId(), Rarity.UNCOMMON);
            meta.setDisplayName(compressedName);
            if (!usesHead()) {
                meta.setEnchantmentGlintOverride(true);
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            }
            meta.setLore(lore(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§72 stacks of " + pretty(input.name()) + ",",
                    usesHead() ? "§7packed into one head." : "§7packed into one stack.",
                    "",
                    "§8Trade value: §6" + coins(compressedId()) + " coins"
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack compacted() {
        ItemStack item = compactedHash != null && !compactedHash.isBlank()
                ? TexturedHeads.hashed(compactedHash)
                : new ItemStack(compactedBlock);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, compactedId(), Rarity.RARE);
            meta.setDisplayName(compactedName);
            if (compactedHash == null || compactedHash.isBlank()) {
                meta.setEnchantmentGlintOverride(true);
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            }
            meta.setLore(lore(
                    "§7✦ §bRARE",
                    "",
                    "§716 Compressed " + pretty(key) + ",",
                    compactedHash != null && !compactedHash.isBlank()
                            ? "§7forged into one crate."
                            : "§7forged into one enchanted block.",
                    "",
                    "§8Trade value: §6" + coins(compactedId()) + " coins"
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack refined() {
        if (!canRefine()) {
            return compacted();
        }
        ItemStack item = new ItemStack(compactedBlock);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, refinedId(), Rarity.EPIC);
            meta.setDisplayName(refinedName());
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(lore(
                    "§7✦ §5EPIC",
                    "",
                    "§7Millstone-cured " + pretty(key) + ".",
                    "§7Worth more than the Compacted it cost.",
                    "",
                    "§8Trade value: §6" + coins(refinedId()) + " coins",
                    "§8Aetherion Farm Pantry"
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static CompressedResource fromDrop(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case COBBLESTONE, STONE, COBBLED_DEEPSLATE, DEEPSLATE, ANDESITE, DIORITE, GRANITE -> COBBLESTONE;
            case COAL, COAL_ORE, DEEPSLATE_COAL_ORE, COAL_BLOCK -> COAL;
            case RAW_IRON, IRON_INGOT, IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK -> RAW_IRON;
            case RAW_GOLD, GOLD_INGOT, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD_BLOCK -> RAW_GOLD;
            case RAW_COPPER, COPPER_INGOT, COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK -> RAW_COPPER;
            case REDSTONE, REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, REDSTONE_BLOCK -> REDSTONE;
            case LAPIS_LAZULI, LAPIS_ORE, DEEPSLATE_LAPIS_ORE, LAPIS_BLOCK -> LAPIS;
            case DIAMOND, DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, DIAMOND_BLOCK -> DIAMOND;
            case EMERALD, EMERALD_ORE, DEEPSLATE_EMERALD_ORE, EMERALD_BLOCK -> EMERALD;
            case OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD -> OAK_LOG;
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD -> BIRCH_LOG;
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD -> SPRUCE_LOG;
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD -> JUNGLE_LOG;
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD -> ACACIA_LOG;
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD -> DARK_OAK_LOG;
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD -> MANGROVE_LOG;
            case CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD -> CHERRY_LOG;
            case BAMBOO, BAMBOO_BLOCK, BAMBOO_MOSAIC -> BAMBOO_BLOCK;
            case CRIMSON_STEM, CRIMSON_HYPHAE, STRIPPED_CRIMSON_STEM, STRIPPED_CRIMSON_HYPHAE -> CRIMSON_STEM;
            case WARPED_STEM, WARPED_HYPHAE, STRIPPED_WARPED_STEM, STRIPPED_WARPED_HYPHAE -> WARPED_STEM;
            case LEATHER, RABBIT_HIDE -> LEATHER;
            case BONE, BONE_BLOCK, BONE_MEAL -> BONE;
            case STRING, COBWEB -> STRING;
            case WHEAT, HAY_BLOCK, WHEAT_SEEDS -> WHEAT;
            case CARROT, GOLDEN_CARROT, CARROTS -> CARROT;
            case POTATO, BAKED_POTATO, POISONOUS_POTATO, POTATOES -> POTATO;
            case SUGAR_CANE -> SUGAR_CANE;
            case NETHER_WART, NETHER_WART_BLOCK -> NETHER_WART;
            case GUNPOWDER, TNT -> GUNPOWDER;
            case FEATHER -> FEATHER;
            case ROTTEN_FLESH -> ROTTEN_FLESH;
            case COD, COOKED_COD, KELP, DRIED_KELP, DRIED_KELP_BLOCK, TROPICAL_FISH -> COD;
            case SALMON, COOKED_SALMON -> SALMON;
            case PUFFERFISH -> PUFFERFISH;
            case PRISMARINE_SHARD, PRISMARINE_CRYSTALS, PRISMARINE, PRISMARINE_BRICKS, DARK_PRISMARINE -> PRISMARINE_SHARD;
            default -> null;
        };
    }

    public static boolean isTradeable(String itemId) {
        return byItemId(itemId) != null;
    }

    public static ItemStack fromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        for (CompressedResource resource : values()) {
            if (resource.compressedId().equals(id)) {
                return resource.compressed();
            }
            if (resource.compactedId().equals(id)) {
                return resource.compacted();
            }
            if (resource.canRefine() && resource.refinedId().equals(id)) {
                return resource.refined();
            }
        }
        return QuarryItems.fromId(itemId);
    }

    public static CompressedResource byItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        for (CompressedResource resource : values()) {
            if (resource.compressedId().equals(id) || resource.compactedId().equals(id)) {
                return resource;
            }
            if (resource.canRefine() && resource.refinedId().equals(id)) {
                return resource;
            }
        }
        return null;
    }

    private boolean usesHead() {
        return textureHash != null && !textureHash.isBlank();
    }

    private static void tag(ItemMeta meta, String itemId, Rarity rarity) {
        ItemManager manager = AetherionItems.getInstance().getItemManager();
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId);
        manager.applyItemData(meta, rarity, new ItemStats());
    }

    private static List<String> lore(String... lines) {
        return new ArrayList<>(List.of(lines));
    }

    private static String pretty(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String coins(String itemId) {
        ItemValueService values = AetherionItems.getInstance().getItemValues();
        long amount = values == null ? 0L : values.unitValue(itemId);
        return String.format(Locale.US, "%,d", amount);
    }
}
