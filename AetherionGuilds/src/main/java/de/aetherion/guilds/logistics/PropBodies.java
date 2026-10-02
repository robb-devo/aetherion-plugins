package de.aetherion.guilds.logistics;

import de.aetherion.guilds.structure.Props;
import de.aetherion.guilds.structure.StructureType;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The bodies of the production props, as parts in a machine's own space: origin = the top centre of its ground
 * block, x to its right, y up, z toward its front (the chute). The same part list draws the real prop
 * ({@code MachineFx}) and the placement ghost.
 *
 * <p>Parts that spin (wheels, sails, millstones, the crane jib) are item displays centred on their pivot, so
 * the client turns them cleanly between keyframes; everything else is a block display. Looks grow with level:
 * a timber quarry rig becomes an iron headframe, a copper works, a golden crown.
 */
public final class PropBodies {

    /**
     * One piece. {@code pivot}: where it sits (spinning parts turn round it). {@code own}: its fixed tilt.
     * {@code item}: drawn as an item display (spinning parts), else as a block display.
     */
    public record Part(String role, BlockData block, ItemStack item, Vector3f pivot, Vector3f size, Quaternionf own,
                       boolean bright) {
    }

    private PropBodies() {
    }

    // ------------------------------------------------------------------------------------------------
    // building the part lists
    // ------------------------------------------------------------------------------------------------

    /** Quarry look tier from its level: 0 timber (1-2), 1 iron (3-4), 2 copper (5-6), 3 crown (7). */
    public static int quarryTier(int level) {
        return level >= 7 ? 3 : level >= 5 ? 2 : level >= 3 ? 1 : 0;
    }

    public static String quarryTierName(int level) {
        return switch (quarryTier(level)) {
            case 1 -> "Iron Headframe";
            case 2 -> "Copper Works";
            case 3 -> "Crowned Works";
            default -> "Timber Rig";
        };
    }

    /** How tall the body stands above its ground (for the status tag). */
    public static float height(StructureType type, int level, String templateId) {
        return switch (type) {
            case QUARRY_HOUSING -> Props.QUARRY_SLIM.equals(templateId)
                    ? 2.2f + 0.3f * quarryTier(level) + 0.7f
                    : 2.4f + 0.35f * quarryTier(level) + 1.6f;
            case MILL -> level >= 2 ? 4.8f : 4.4f;
            case FORGE -> 3.8f;
            case DEPOT -> 2.8f;
            default -> 3f;
        };
    }

    public static List<Part> body(StructureType type, int level, String templateId) {
        return body(type, level, templateId, null);
    }

    /** What a quarry's prop looks like: mines keep the headframe, the other trades get their own yard. */
    public enum Skin {
        MINING,
        FARMING,
        FISHING,
        FORAGING,
        COMBAT
    }

    public static Skin skinOf(de.aetherion.guilds.model.QuarryType quarry) {
        if (quarry == null) {
            return Skin.MINING;
        }
        return switch (quarry) {
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG,
                 BAMBOO_BLOCK, CRIMSON_STEM, WARPED_STEM -> Skin.FORAGING;
            case WHEAT, CARROT, POTATO, LEATHER, FEATHER -> Skin.FARMING;
            case COD -> Skin.FISHING;
            case BONE, STRING, GUNPOWDER, ROTTEN_FLESH -> Skin.COMBAT;
            default -> Skin.MINING;
        };
    }

    public static List<Part> body(StructureType type, int level, String templateId,
                                  de.aetherion.guilds.model.QuarryType quarry) {
        List<Part> parts = new ArrayList<>();
        switch (type) {
            case QUARRY_HOUSING -> {
                if (Props.QUARRY_SLIM.equals(templateId)) {
                    slimQuarry(parts, quarryTier(level));
                } else {
                    switch (skinOf(quarry)) {
                        case FARMING -> farm(parts, quarryTier(level), quarry);
                        case FISHING -> dock(parts, quarryTier(level));
                        case FORAGING -> grove(parts, quarryTier(level), quarry);
                        case COMBAT -> yard(parts, quarryTier(level));
                        default -> quarry(parts, quarryTier(level));
                    }
                }
            }
            case MILL -> mill(parts, level);
            case FORGE -> forge(parts, level);
            case DEPOT -> depot(parts, level);
            default -> {
            }
        }
        if (type != StructureType.QUARRY_HOUSING || !Props.QUARRY_SLIM.equals(templateId)) {
            ports(parts, type);
        }
        return parts;
    }

    private static final Material[] POST = {Material.STRIPPED_SPRUCE_LOG, Material.POLISHED_ANDESITE,
            Material.WAXED_CUT_COPPER, Material.GOLD_BLOCK};
    private static final Material[] BEAM = {Material.SPRUCE_PLANKS, Material.POLISHED_DEEPSLATE,
            Material.WAXED_COPPER_BLOCK, Material.POLISHED_BLACKSTONE};
    private static final Material[] SPOKE = {Material.SPRUCE_PLANKS, Material.IRON_BLOCK,
            Material.WAXED_COPPER_BLOCK, Material.GOLD_BLOCK};
    private static final Material[] PLINTH = {Material.COARSE_DIRT, Material.POLISHED_ANDESITE,
            Material.POLISHED_ANDESITE, Material.POLISHED_BLACKSTONE_BRICKS};

    private static void quarry(List<Part> parts, int t) {
        float h = 2.4f + 0.35f * t;
        box(parts, "", PLINTH[t], 0, 0.05f, 0, 3.0f, 0.1f, 3.0f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", POST[t], sx * 1.2f, h / 2f, sz * 1.2f, 0.3f, h, 0.3f);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            box(parts, "", BEAM[t], 0, h, s * 1.2f, 2.7f, 0.25f, 0.25f);
            box(parts, "", BEAM[t], s * 1.2f, h, 0, 0.25f, 0.25f, 2.7f);
            box(parts, "", POST[t], s * 0.55f, h + 0.45f, 0, 0.18f, 0.9f, 0.18f);
        }
        float axle = h + 0.85f;
        box(parts, "", BEAM[t], 0, axle, 0, 1.3f, 0.12f, 0.12f);
        // headframe wheel: three crossed spokes turning round the axle (x)
        for (int i = 0; i < 3; i++) {
            spin(parts, "wheel", SPOKE[t], 0, axle, 0, 0.08f, 1.3f + 0.15f * t, 0.14f,
                    new Quaternionf().rotateX((float) (i * Math.PI / 3)));
        }
        // the cable down to the drill motor, the motor, the drill bit
        float cableTop = axle;
        float cableBottom = 2.0f;
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(0, (cableTop + cableBottom) / 2f, 0), new Vector3f(1f, cableTop - cableBottom, 1f),
                new Quaternionf(), false));
        parts.add(new Part("", Bukkit.createBlockData("minecraft:piston[facing=down]"), null,
                new Vector3f(0, 1.75f, 0), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf(), false));
        parts.add(new Part("drill", Bukkit.createBlockData("minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]"),
                null, new Vector3f(0, 1.4f, 0), new Vector3f(0.6f, 0.7f, 0.6f), new Quaternionf(), false));
        if (t >= 1) {
            for (int s = -1; s <= 1; s += 2) {
                parts.add(new Part("", Bukkit.createBlockData("minecraft:lantern[hanging=true]"), null,
                        new Vector3f(s * 1.2f, h - 0.4f, 1.2f), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf(), true));
            }
        }
        if (t >= 2) {
            for (int s = -1; s <= 1; s += 2) {
                parts.add(new Part("", Bukkit.createBlockData("minecraft:lightning_rod[facing=up]"), null,
                        new Vector3f(s * 1.2f, h + 0.55f, -1.2f), new Vector3f(0.6f, 0.9f, 0.6f), new Quaternionf(), false));
            }
        }
        if (t >= 3) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    parts.add(new Part("", Material.GOLD_BLOCK.createBlockData(), null,
                            new Vector3f(sx * 1.2f, h + 0.3f, sz * 1.2f), new Vector3f(0.38f, 0.38f, 0.38f),
                            new Quaternionf().rotateY((float) (Math.PI / 4)), true));
                }
            }
            parts.add(new Part("", Bukkit.createBlockData("minecraft:end_rod[facing=up]"), null,
                    new Vector3f(0, axle + 0.95f, 0), new Vector3f(0.6f, 0.8f, 0.6f), new Quaternionf(), true));
        }
        lamp(parts, 1.2f, h + 0.3f, 1.2f);
    }

    // ------------------------------------------------------------------------------------------------
    // the other trades: same shell, same chute in front, their own silhouette
    // ------------------------------------------------------------------------------------------------

    private static final Material[] TRIM = {Material.STRIPPED_OAK_LOG, Material.STRIPPED_BIRCH_LOG,
            Material.WAXED_CUT_COPPER, Material.GOLD_BLOCK};

    private static void block(List<Part> parts, String role, String state, float x, float y, float z,
                              float sx, float sy, float sz, boolean bright) {
        parts.add(new Part(role, Bukkit.createBlockData(state), null, new Vector3f(x, y, z), new Vector3f(sx, sy, sz),
                new Quaternionf(), bright));
    }

    /** Farming: a little homestead barn over a crop patch, a hay bale on the hoist, a weathervane. */
    private static void farm(List<Part> parts, int t, de.aetherion.guilds.model.QuarryType quarry) {
        float h = 2.3f + 0.3f * t;
        box(parts, "", Material.COARSE_DIRT, 0, 0.05f, 0, 3.0f, 0.1f, 3.0f);
        box(parts, "", Material.FARMLAND, -0.85f, 0.12f, 0.55f, 1.1f, 0.12f, 1.4f);
        String crop = switch (quarry) {
            case CARROT -> "minecraft:carrots[age=7]";
            case POTATO -> "minecraft:potatoes[age=7]";
            case LEATHER, FEATHER -> "minecraft:short_grass";
            default -> "minecraft:wheat[age=7]";
        };
        for (int i = 0; i < 2; i++) {
            block(parts, "crop", crop, -0.85f, 0.55f, 0.2f + i * 0.7f, 0.8f, 0.8f, 0.6f, false);
        }
        Material post = TRIM[t];
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", post, sx * 1.2f, h / 2f, sz * 1.2f, 0.26f, h, 0.26f);
            }
        }
        Material wall = t >= 2 ? Material.WAXED_EXPOSED_CUT_COPPER : Material.RED_TERRACOTTA;
        box(parts, "", wall, 0, h * 0.45f, -1.2f, 2.2f, h * 0.9f, 0.14f);
        box(parts, "", wall, 1.2f, h * 0.45f, -0.3f, 0.14f, h * 0.9f, 1.6f);
        box(parts, "", Material.WHITE_TERRACOTTA, 0, h * 0.9f, -1.13f, 2.2f, 0.12f, 0.06f);
        Material roof = t >= 3 ? Material.GOLD_BLOCK : t >= 2 ? Material.WAXED_CUT_COPPER : Material.DARK_OAK_PLANKS;
        for (int s = -1; s <= 1; s += 2) {
            parts.add(new Part("", roof.createBlockData(), null, new Vector3f(0, h + 0.4f, s * 0.75f),
                    new Vector3f(3.2f, 0.16f, 1.85f), new Quaternionf().rotateX(s * 0.55f), false));
        }
        // the hay hoist: a bale on a chain, lifted and dropped while it works
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(0.45f, h - 0.35f, 0.3f), new Vector3f(1f, 1.1f, 1f), new Quaternionf(), false));
        block(parts, "drill", "minecraft:hay_block[axis=y]", 0.45f, h - 1.1f, 0.3f, 0.55f, 0.55f, 0.55f, false);
        // weathervane on the ridge
        spin(parts, "stone", Material.LIGHTNING_ROD, 0, h + 1.05f, 0, 0.12f, 0.12f, 0.9f, new Quaternionf());
        box(parts, "", Material.IRON_BARS, 0, h + 0.85f, 0, 0.08f, 0.4f, 0.08f);
        if (t >= 1) {
            parts.add(new Part("", Bukkit.createBlockData("minecraft:lantern[hanging=true]"), null,
                    new Vector3f(1.2f, h - 0.4f, 1.2f), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf(), true));
        }
        lamp(parts, -1.2f, h + 0.25f, 1.2f);
    }

    /** Fishing: a plank dock round a little pond, a net that dips, a boom that swings, crates of the catch. */
    private static void dock(List<Part> parts, int t) {
        float h = 2.4f + 0.3f * t;
        box(parts, "", Material.SPRUCE_PLANKS, 0, 0.1f, -1.05f, 3.0f, 0.2f, 0.9f);
        box(parts, "", Material.SPRUCE_PLANKS, -1.05f, 0.1f, 0.3f, 0.9f, 0.2f, 1.8f);
        box(parts, "", Material.SPRUCE_PLANKS, 1.05f, 0.1f, 0.3f, 0.9f, 0.2f, 1.8f);
        parts.add(new Part("", Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), null, new Vector3f(0, 0.06f, 0.35f),
                new Vector3f(1.2f, 0.06f, 1.7f), new Quaternionf(), true));
        Material post = t >= 3 ? Material.GOLD_BLOCK : t >= 2 ? Material.WAXED_CUT_COPPER : Material.STRIPPED_SPRUCE_LOG;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", post, sx * 1.3f, h / 2f - 0.2f, sz * 1.3f, 0.22f, h + 0.4f, 0.22f);
            }
        }
        box(parts, "", Material.STRIPPED_SPRUCE_LOG, -1.25f, h + 0.1f, -1.25f, 0.26f, 0.5f, 0.26f);
        // the boom swings over the water
        spin(parts, "jib", Material.SPRUCE_PLANKS, -1.25f, h + 0.4f, -1.25f, 0.16f, 0.16f, 2.8f, new Quaternionf());
        // the net dips into the pond
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(0, h - 0.35f, 0.35f), new Vector3f(1f, 1.2f, 1f), new Quaternionf(), false));
        block(parts, "drill", "minecraft:cobweb", 0, h - 1.25f, 0.35f, 0.9f, 0.7f, 0.9f, false);
        box(parts, "", Material.BARREL, 1.05f, 0.55f, -1.05f, 0.6f, 0.6f, 0.6f);
        parts.add(new Part("", null, new ItemStack(Material.COD), new Vector3f(1.05f, 1.0f, -1.05f),
                new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf().rotateX((float) (Math.PI / 2)), false));
        box(parts, "", Material.SPRUCE_PLANKS, 1.05f, 0.45f, -0.35f, 0.55f, 0.5f, 0.55f);
        if (t >= 1) {
            for (int s = -1; s <= 1; s += 2) {
                parts.add(new Part("", Bukkit.createBlockData(t >= 2 ? "minecraft:soul_lantern[hanging=true]"
                        : "minecraft:lantern[hanging=true]"), null, new Vector3f(s * 1.3f, h - 0.35f, 1.3f),
                        new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf(), true));
            }
        }
        lamp(parts, 1.3f, h + 0.15f, 1.3f);
    }

    /** Foraging: trunks of the very wood it gathers, a leaf canopy, a saw blade that turns. */
    private static void grove(List<Part> parts, int t, de.aetherion.guilds.model.QuarryType quarry) {
        float h = 2.6f + 0.35f * t;
        Material log = quarry.product().isBlock() ? quarry.product() : Material.OAK_LOG;
        Material leaves = switch (quarry) {
            case BIRCH_LOG -> Material.BIRCH_LEAVES;
            case SPRUCE_LOG -> Material.SPRUCE_LEAVES;
            case CHERRY_LOG -> Material.CHERRY_LEAVES;
            case CRIMSON_STEM -> Material.NETHER_WART_BLOCK;
            case WARPED_STEM -> Material.WARPED_WART_BLOCK;
            default -> t >= 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES;
        };
        box(parts, "", Material.MOSS_BLOCK, 0, 0.05f, 0, 3.0f, 0.1f, 3.0f);
        box(parts, "", Material.PODZOL, 0.7f, 0.12f, -0.7f, 1.2f, 0.06f, 1.2f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", log, sx * 1.15f, h / 2f, sz * 1.15f, 0.4f, h, 0.4f);
            }
        }
        // canopy: a full crown up top, smaller tufts at the corners
        box(parts, "", leaves, 0, h + 0.35f, 0, 3.2f, 0.7f, 3.2f);
        box(parts, "", leaves, 0, h + 0.95f, 0, 2.0f, 0.55f, 2.0f);
        // log pile and the saw blade (it turns while it works)
        box(parts, "", log, 0.75f, 0.35f, -0.75f, 0.9f, 0.45f, 0.45f);
        box(parts, "", Material.STRIPPED_SPRUCE_LOG, -0.8f, 0.5f, -0.8f, 0.2f, 0.9f, 0.2f);
        spin(parts, "wheel", Material.IRON_BLOCK, -0.8f, 1.05f, -0.8f, 0.05f, 0.85f, 0.85f,
                new Quaternionf().rotateX((float) (Math.PI / 4)));
        if (t >= 1) {
            parts.add(new Part("", Bukkit.createBlockData("minecraft:lantern[hanging=true]"), null,
                    new Vector3f(1.15f, h - 0.4f, 1.15f), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf(), true));
        }
        if (t >= 3) {
            box(parts, "", Material.GOLD_BLOCK, 0, h + 1.3f, 0, 0.35f, 0.35f, 0.35f);
        }
        lamp(parts, 1.15f, h + 0.1f, -1.15f);
    }

    /** Combat: a sparring yard — roped ring, a weapon rack, a spinning blade post, a target that takes hits. */
    private static void yard(List<Part> parts, int t) {
        float h = 2.2f + 0.3f * t;
        Material floor = t >= 3 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.STONE_BRICKS;
        box(parts, "", floor, 0, 0.06f, 0, 3.0f, 0.12f, 3.0f);
        Material post = t >= 3 ? Material.GOLD_BLOCK : t >= 2 ? Material.POLISHED_BLACKSTONE : Material.STRIPPED_SPRUCE_LOG;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", post, sx * 1.3f, h / 2f, sz * 1.3f, 0.2f, h, 0.2f);
            }
        }
        // ropes round the ring
        for (int s = -1; s <= 1; s += 2) {
            parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=x]"), null,
                    new Vector3f(0, h * 0.55f, s * 1.3f), new Vector3f(2.6f, 1f, 1f), new Quaternionf(), false));
            parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=z]"), null,
                    new Vector3f(s * 1.3f, h * 0.55f, 0), new Vector3f(1f, 1f, 2.6f), new Quaternionf(), false));
        }
        // weapon rack on the back rope
        box(parts, "", Material.SPRUCE_PLANKS, 0, 1.25f, -1.15f, 1.6f, 0.12f, 0.12f);
        for (int i = -1; i <= 1; i++) {
            Material weapon = i == 0 ? Material.BOW : t >= 2 ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
            parts.add(new Part("", null, new ItemStack(weapon), new Vector3f(i * 0.5f, 0.85f, -1.1f),
                    new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf().rotateZ((float) (Math.PI / 4)), false));
        }
        // the blade post turns, the target swings when it works
        box(parts, "", Material.STRIPPED_DARK_OAK_LOG, 0.85f, 0.75f, -0.6f, 0.18f, 1.5f, 0.18f);
        spin(parts, "stone", Material.IRON_SWORD, 0.85f, 1.35f, -0.6f, 0.9f, 0.9f, 0.9f,
                new Quaternionf().rotateX((float) (-Math.PI / 2)));
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(-0.8f, h - 0.4f, -0.6f), new Vector3f(1f, 0.8f, 1f), new Quaternionf(), false));
        block(parts, "drill", "minecraft:target", -0.8f, h - 1.15f, -0.6f, 0.55f, 0.7f, 0.55f, false);
        if (t >= 1) {
            for (int s = -1; s <= 1; s += 2) {
                parts.add(new Part("", Bukkit.createBlockData(t >= 2 ? "minecraft:soul_lantern[hanging=false]"
                        : "minecraft:lantern[hanging=false]"), null, new Vector3f(s * 1.3f, h + 0.25f, 1.3f),
                        new Vector3f(0.45f, 0.45f, 0.45f), new Quaternionf(), true));
            }
        }
        lamp(parts, -1.3f, h + 0.25f, -1.3f);
    }

    private static void slimQuarry(List<Part> parts, int t) {
        float h = 2.2f + 0.3f * t;
        for (int s = -1; s <= 1; s += 2) {
            box(parts, "", POST[t], s * 0.42f, h / 2f, 0, 0.14f, h, 0.14f);
        }
        float axle = h;
        box(parts, "", BEAM[t], 0, axle, 0, 1.0f, 0.12f, 0.12f);
        for (int i = 0; i < 2; i++) {
            spin(parts, "wheel", SPOKE[t], 0, axle, 0, 0.06f, 0.9f, 0.1f,
                    new Quaternionf().rotateX((float) (i * Math.PI / 2)));
        }
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(0, (axle + 1.6f) / 2f, 0), new Vector3f(1f, axle - 1.6f, 1f), new Quaternionf(), false));
        parts.add(new Part("drill", Bukkit.createBlockData("minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]"),
                null, new Vector3f(0, 1.4f, 0), new Vector3f(0.5f, 0.6f, 0.5f), new Quaternionf(), false));
        lamp(parts, 0.42f, h + 0.25f, 0f);
    }

    private static void mill(List<Part> parts, int level) {
        boolean twin = level >= 2;
        box(parts, "", Material.SMOOTH_STONE, 0, 0.08f, 0, 3.0f, 0.16f, 3.0f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", Material.STRIPPED_SPRUCE_LOG, sx * 1.15f, 1.08f, sz * 1.15f, 0.28f, 2.0f, 0.28f);
            }
        }
        box(parts, "", Material.WHITE_TERRACOTTA, 0, 1.05f, -1.15f, 2.05f, 1.7f, 0.14f);
        box(parts, "", Material.SPRUCE_PLANKS, 0, 2.0f, 1.15f, 2.3f, 0.2f, 0.2f);
        Material roof = twin ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
        for (int s = -1; s <= 1; s += 2) {
            parts.add(new Part("", roof.createBlockData(), null, new Vector3f(0, 2.55f, s * 0.78f),
                    new Vector3f(3.3f, 0.18f, 1.9f), new Quaternionf().rotateX(s * 0.56f), false));
        }
        // the millstones turning in the open house
        spin(parts, "stone", Material.SMOOTH_STONE_SLAB, 0, 0.55f, 0, 1.7f, 1.2f, 1.7f, new Quaternionf());
        if (twin) {
            spin(parts, "stone2", Material.SMOOTH_STONE_SLAB, 0, 1.15f, 0, 1.45f, 1.0f, 1.45f, new Quaternionf());
        }
        // sails on the gable: the silhouette you see from across the island
        float length = twin ? 3.6f : 3.2f;
        int bars = twin ? 4 : 2;
        for (int i = 0; i < bars; i++) {
            spin(parts, "sails", Material.WHITE_WOOL, 0, 2.75f, 1.78f, 0.34f, length, 0.05f,
                    new Quaternionf().rotateZ((float) (i * Math.PI / bars)));
        }
        box(parts, "", Material.STRIPPED_SPRUCE_LOG, 0, 2.75f, 1.62f, 0.3f, 0.3f, 0.36f);
        lamp(parts, 1.15f, 2.2f, 1.15f);
    }

    private static void forge(List<Part> parts, int level) {
        box(parts, "", Material.POLISHED_BLACKSTONE_BRICKS, 0, 0.08f, 0, 3.0f, 0.16f, 3.0f);
        box(parts, "", Material.BRICKS, 0, 0.86f, -0.55f, 2.2f, 1.4f, 1.5f);
        parts.add(new Part("furnace", Bukkit.createBlockData("minecraft:blast_furnace[facing=south,lit=false]"), null,
                new Vector3f(0, 0.62f, 0.32f), new Vector3f(0.95f, 0.92f, 0.42f), new Quaternionf(), false));
        box(parts, "", Material.POLISHED_BLACKSTONE, 0, 1.66f, -0.25f, 2.4f, 0.24f, 2.0f);
        box(parts, "", Material.BRICKS, -0.85f, 2.4f, -0.85f, 0.65f, 2.4f, 0.65f);
        box(parts, "", Material.POLISHED_BLACKSTONE, -0.85f, 3.66f, -0.85f, 0.82f, 0.14f, 0.82f);
        if (level >= 2) {
            box(parts, "", Material.BRICKS, 0.85f, 2.2f, -0.85f, 0.55f, 2.0f, 0.55f);
            box(parts, "", Material.POLISHED_BLACKSTONE, 0.85f, 3.26f, -0.85f, 0.7f, 0.12f, 0.7f);
            box(parts, "", Material.WAXED_CUT_COPPER, 0, 1.2f, 0.22f, 2.25f, 0.12f, 0.06f);
        }
        parts.add(new Part("anvil", Bukkit.createBlockData("minecraft:anvil[facing=east]"), null,
                new Vector3f(0.95f, 0.47f, 0.75f), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf(), false));
        parts.add(new Part("bellows", Material.BROWN_TERRACOTTA.createBlockData(), null,
                new Vector3f(-0.95f, 0.36f, 0.75f), new Vector3f(0.7f, 0.32f, 0.8f), new Quaternionf(), false));
        box(parts, "", Material.SPRUCE_PLANKS, -0.95f, 0.56f, 0.75f, 0.78f, 0.06f, 0.88f);
        lamp(parts, 1.15f, 1.95f, -0.2f);
    }

    private static void depot(List<Part> parts, int level) {
        box(parts, "", Material.SPRUCE_PLANKS, 0, 0.06f, 0, 3.0f, 0.12f, 3.0f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(parts, "", Material.STRIPPED_SPRUCE_LOG, sx * 1.32f, 0.55f, sz * 1.32f, 0.16f, 0.9f, 0.16f);
            }
        }
        box(parts, "", Material.STRIPPED_SPRUCE_LOG, 1.2f, 1.42f, -1.2f, 0.22f, 2.6f, 0.22f);
        spin(parts, "jib", Material.DARK_OAK_PLANKS, 1.2f, 2.72f, -1.2f, 2.6f, 0.18f, 0.18f, new Quaternionf());
        parts.add(new Part("", Bukkit.createBlockData("minecraft:chain[axis=y]"), null,
                new Vector3f(0.05f, 2.2f, -1.2f), new Vector3f(1f, 1.0f, 1f), new Quaternionf(), false));
        lamp(parts, -1.32f, 1.15f, 1.32f);
    }

    /** Where crates stand in a depot (fill shows as crates: empty yard → stacked full). */
    public static List<Part> crates(int count, int level) {
        List<Part> parts = new ArrayList<>();
        float[][] spots = {{-0.8f, -0.8f}, {0f, -0.8f}, {-0.8f, 0f}, {0f, 0f}, {0.8f, 0f}, {-0.8f, 0.8f},
                {0.8f, 0.8f}, {0.8f, -0.8f}};
        int layers = level >= 2 ? 2 : 1;
        int max = spots.length * layers;
        for (int i = 0; i < Math.min(count, max); i++) {
            float[] spot = spots[i % spots.length];
            int layer = i / spots.length;
            Material crate = (i % 3 == 0) ? Material.BARREL : (i % 3 == 1) ? Material.STRIPPED_OAK_WOOD : Material.SPRUCE_PLANKS;
            parts.add(new Part("crate", crate.createBlockData(), null,
                    new Vector3f(spot[0], 0.12f + 0.36f + layer * 0.72f, spot[1]), new Vector3f(0.7f, 0.7f, 0.7f),
                    new Quaternionf().rotateY((float) ((i * 0.37) % 0.4 - 0.2)), false));
        }
        return parts;
    }

    public static int crateSlots(int level) {
        return level >= 2 ? 16 : 8;
    }

    /** Authored ports: orange lip on the chute (belt out), green lips on the faces belts may run into. */
    private static void ports(List<Part> parts, StructureType type) {
        parts.add(new Part("", Material.ORANGE_CONCRETE.createBlockData(), null, new Vector3f(0, 1.03f, 1.44f),
                new Vector3f(0.72f, 0.07f, 0.08f), new Quaternionf(), true));
        parts.add(new Part("", Material.SMOOTH_STONE_SLAB.createBlockData(), null, new Vector3f(0, 0.62f, 1.72f),
                new Vector3f(0.5f, 0.06f, 0.62f), new Quaternionf().rotateX(0.42f), false));
        if (type == StructureType.QUARRY_HOUSING) {
            return; // quarries only give
        }
        parts.add(new Part("", Material.LIME_CONCRETE.createBlockData(), null, new Vector3f(0, 0.3f, -1.47f),
                new Vector3f(0.8f, 0.08f, 0.06f), new Quaternionf(), true));
        parts.add(new Part("", Material.LIME_CONCRETE.createBlockData(), null, new Vector3f(-1.47f, 0.3f, 0),
                new Vector3f(0.06f, 0.08f, 0.8f), new Quaternionf(), true));
        parts.add(new Part("", Material.LIME_CONCRETE.createBlockData(), null, new Vector3f(1.47f, 0.3f, 0),
                new Vector3f(0.06f, 0.08f, 0.8f), new Quaternionf(), true));
    }

    /** The status lamp: green running, orange "needs a belt", red full / blocked, grey waiting. */
    private static void lamp(List<Part> parts, float x, float y, float z) {
        parts.add(new Part("lamp", Material.LIGHT_GRAY_CONCRETE.createBlockData(), null, new Vector3f(x, y, z),
                new Vector3f(0.24f, 0.24f, 0.24f), new Quaternionf(), true));
    }

    public static BlockData lampBlock(MachineState state) {
        Material material = switch (state) {
            case RUNNING, RECEIVING -> Material.LIME_CONCRETE;
            case NEEDS_BELT, NEEDS_BELT_IN, NEEDS_BELT_OUT, PICK_FILTER -> Material.ORANGE_CONCRETE;
            case FULL, BACKED_UP, DEAD_END -> Material.RED_CONCRETE;
            case BUILDING -> Material.YELLOW_CONCRETE;
            default -> Material.LIGHT_GRAY_CONCRETE;
        };
        return material.createBlockData();
    }

    private static void box(List<Part> parts, String role, Material material, float x, float y, float z,
                            float sx, float sy, float sz) {
        parts.add(new Part(role, material.createBlockData(), null, new Vector3f(x, y, z), new Vector3f(sx, sy, sz),
                new Quaternionf(), false));
    }

    private static void spin(List<Part> parts, String role, Material material, float x, float y, float z,
                             float sx, float sy, float sz, Quaternionf own) {
        parts.add(new Part(role, null, new ItemStack(material), new Vector3f(x, y, z), new Vector3f(sx, sy, sz), own,
                false));
    }

    // ------------------------------------------------------------------------------------------------
    // placing a part in the world
    // ------------------------------------------------------------------------------------------------

    /**
     * The display transformation of a part for a machine turned {@code rot} quarter turns, relative to the block
     * corner of its ground block. {@code turn}: the part's animated rotation (in machine space), {@code lift}:
     * an animated vertical offset, {@code stretchY}: an animated height (bottom stays put).
     */
    public static Transformation transform(Part part, int rot, Quaternionf turn, float lift, float stretchY) {
        Quaternionf facing = new Quaternionf().rotateY((float) (-Math.floorMod(rot, 4) * Math.PI / 2));
        Quaternionf left = new Quaternionf(facing);
        if (turn != null) {
            left.mul(turn);
        }
        left.mul(part.own());
        Vector3f size = new Vector3f(part.size());
        Vector3f pivot = new Vector3f(part.pivot()).add(0, lift, 0);
        if (stretchY > 0f) {
            pivot.y += (stretchY - size.y) / 2f;
            size.y = stretchY;
        }
        Vector3f center = facing.transform(new Vector3f(pivot)).add(0.5f, 1.0f, 0.5f);
        if (part.item() != null) {
            // drawn with the NONE item transform: the block model, one block across, centred on the entity
            return new Transformation(center, left, size, new Quaternionf());
        }
        Vector3f half = left.transform(new Vector3f(size).mul(0.5f));
        return new Transformation(center.sub(half), left, size, new Quaternionf());
    }
}
