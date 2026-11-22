package de.aetherion.guilds.logistics;

import de.aetherion.guilds.model.QuarryType;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static de.aetherion.guilds.logistics.CompactBodies.CX;
import static de.aetherion.guilds.logistics.CompactBodies.CZ;
import static de.aetherion.guilds.logistics.CompactBodies.block;
import static de.aetherion.guilds.logistics.CompactBodies.box;
import static de.aetherion.guilds.logistics.CompactBodies.boxR;
import static de.aetherion.guilds.logistics.CompactBodies.item;
import static de.aetherion.guilds.logistics.CompactBodies.lamp;
import static de.aetherion.guilds.logistics.CompactBodies.spin;
import static de.aetherion.guilds.logistics.CompactBodies.state;

/**
 * The compact quarries (2x2, same machine space as {@link CompactBodies}). The quarry itself stands on the anchor
 * cell (back right): its ore core turns there, so that cell stays open at foot height and every trade works
 * round it. The chute is the front-right cell, the left column is the trade's own yard.
 *
 * <ul>
 *   <li>Mines: a headframe over the shaft, the winding house with its drum, an ore cart on rails.</li>
 *   <li>Farms: a press shed over the core, crop beds (wheat, carrots, potatoes), a paddock (leather) or a coop
 *   (feathers).</li>
 *   <li>Groves: a tree of the very wood it gathers (a bamboo stand, a nether fungus), the saw bench.</li>
 *   <li>Fisheries: a pond with a net on a boom, the catch drying on its rack.</li>
 *   <li>Mob yards: a caged spawner in a pit, the grinder blade over the core.</li>
 * </ul>
 * Four looks per trade, by the quarry's level: timber, iron, copper, crowned.
 */
final class QuarryBodies {

    private QuarryBodies() {
    }

    // ------------------------------------------------------------------------------------------------
    // tiers: 0 timber (1-2), 1 iron (3-4), 2 copper (5-6), 3 crowned (7)
    // ------------------------------------------------------------------------------------------------

    private static final String[] LEG = {"minecraft:stripped_spruce_log[axis=y]", "minecraft:polished_deepslate",
            "minecraft:waxed_cut_copper", "minecraft:polished_blackstone"};
    private static final Material[] GIRT = {Material.SPRUCE_PLANKS, Material.POLISHED_DEEPSLATE,
            Material.WAXED_EXPOSED_CUT_COPPER, Material.POLISHED_BLACKSTONE_BRICKS};
    private static final Material[] TRIM = {Material.DARK_OAK_PLANKS, Material.IRON_BLOCK,
            Material.WAXED_CUT_COPPER, Material.GOLD_BLOCK};
    private static final Material[] APRON = {Material.PACKED_MUD, Material.POLISHED_ANDESITE,
            Material.POLISHED_TUFF, Material.POLISHED_BLACKSTONE_BRICKS};
    private static final Material[] ROOF = {Material.DARK_OAK_PLANKS, Material.DEEPSLATE_TILES,
            Material.WAXED_WEATHERED_CUT_COPPER, Material.DEEPSLATE_TILES};

    static float height(int level) {
        return 3.4f + 0.15f * PropBodies.quarryTier(level);
    }

    static void yard(List<PropBodies.Part> p, int level, QuarryType quarry) {
        int t = PropBodies.quarryTier(level);
        switch (PropBodies.skinOf(quarry)) {
            case FARMING -> farm(p, t, quarry);
            case FORAGING -> grove(p, t, quarry);
            case FISHING -> fishery(p, t);
            case COMBAT -> pit(p, t, quarry);
            default -> mine(p, t, quarry);
        }
        chute(p);
    }

    // ------------------------------------------------------------------------------------------------
    // Mine: a headframe over the shaft (the ore core turns in its mouth), the winding house on the left with
    // its drum, the cable over the sheave, an ore cart on the yard rails in front of it.
    // ------------------------------------------------------------------------------------------------

    private static void mine(List<PropBodies.Part> p, int t, QuarryType quarry) {
        box(p, APRON[t], CX, 0.03f, CZ, 1.98f, 0.06f, 1.98f);
        // the shaft mouth and its collar
        box(p, Material.BLACK_CONCRETE, 0f, 0.065f, 0f, 0.6f, 0.01f, 0.6f);
        Material collar = t == 0 ? Material.SPRUCE_PLANKS : t == 1 ? Material.STONE_BRICKS : t == 2
                ? Material.POLISHED_TUFF : Material.POLISHED_BLACKSTONE_BRICKS;
        box(p, collar, 0f, 0.17f, -0.37f, 0.9f, 0.22f, 0.16f);
        box(p, collar, 0f, 0.17f, 0.37f, 0.9f, 0.22f, 0.16f);
        box(p, collar, -0.37f, 0.17f, 0f, 0.16f, 0.22f, 0.58f);
        box(p, collar, 0.37f, 0.17f, 0f, 0.16f, 0.22f, 0.58f);
        if (t >= 1) {
            box(p, TRIM[t], 0f, 0.29f, 0.37f, 0.92f, 0.03f, 0.18f);
        }
        // the headframe: four legs leaning in, two rings of girts, braced on the back and the right
        float top = 2.7f;
        float w0 = 0.43f;
        float w1 = 0.25f;
        float thick = t == 0 ? 0.13f : 0.11f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                strut(p, LEG[t], sx * w0, 0.06f, sz * w0, sx * w1, top, sz * w1, thick);
            }
        }
        float[] rings = {0.95f, 1.85f};
        float prevY = 0.3f;
        for (float gy : rings) {
            float w = w0 - (w0 - w1) * (gy - 0.06f) / (top - 0.06f);
            box(p, GIRT[t], 0f, gy, -w, 2 * w + 0.06f, 0.07f, 0.07f);
            box(p, GIRT[t], 0f, gy, w, 2 * w + 0.06f, 0.07f, 0.07f);
            box(p, GIRT[t], -w, gy, 0f, 0.07f, 0.07f, 2 * w + 0.06f);
            box(p, GIRT[t], w, gy, 0f, 0.07f, 0.07f, 2 * w + 0.06f);
            float wp = w0 - (w0 - w1) * (prevY - 0.06f) / (top - 0.06f);
            // X braces: back face and right face
            strut(p, LEG[t], -wp, prevY, -wp, w, gy, -w, 0.045f);
            strut(p, LEG[t], wp, prevY, -wp, -w, gy, -w, 0.045f);
            strut(p, LEG[t], wp, prevY, -wp, w, gy, w, 0.045f);
            strut(p, LEG[t], wp, prevY, wp, w, gy, -w, 0.045f);
            prevY = gy;
        }
        // the head: a deck that reaches out over the winding side, the sheave on two bearings
        box(p, GIRT[t], -0.12f, top + 0.04f, 0f, 0.86f, 0.08f, 0.6f);
        strut(p, LEG[t], -w1, top - 0.55f, 0f, -0.52f, top, 0f, 0.07f);
        float r = 0.36f;
        float wx = -r;
        float wy = top + 0.5f;
        for (int s = -1; s <= 1; s += 2) {
            box(p, GIRT[t], wx, top + 0.25f, s * 0.13f, 0.1f, 0.42f, 0.06f);
        }
        box(p, Material.POLISHED_DEEPSLATE, wx, wy, 0f, 0.07f, 0.07f, 0.36f);
        sheave(p, t, wx, wy, 0f, r);
        // the cable: over the sheave, down the shaft to the drill; and back to the drum in the winding house
        Material cable = Material.POLISHED_DEEPSLATE;
        block(p, "rope", "minecraft:chain[axis=y]", 0f, (wy + 1.56f) / 2f, 0f, 0.5f, wy - 1.56f, 0.5f, false);
        block(p, "drill", "minecraft:piston[facing=down]", 0f, 1.42f, 0f, 0.3f, 0.26f, 0.3f, false);
        block(p, "drill", drillBit(t), 0f, 1.17f, 0f, 0.3f, 0.32f, 0.3f, t >= 3);
        float drumX = -1.2f;
        float drumY = 2.05f;
        strutM(p, cable, wx - r * 0.74f, wy + r * 0.67f, 0f, drumX + 0.1f, drumY + 0.13f, 0f, 0.035f);
        // the winding house: a tuff footing, plaster in dark timber, a gable roof, the chimney at the back
        float hx = -1.0f;
        box(p, Material.TUFF_BRICKS, hx, 0.3f, 0f, 0.92f, 0.54f, 0.92f);
        box(p, Material.POLISHED_TUFF, hx, 0.58f, 0f, 0.96f, 0.06f, 0.96f);
        box(p, Material.CALCITE, hx, 1.06f, 0f, 0.84f, 0.92f, 0.84f);
        Material timber = t == 2 ? Material.WAXED_CUT_COPPER : t == 3 ? Material.POLISHED_BLACKSTONE : Material.STRIPPED_DARK_OAK_LOG;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, timber, hx + sx * 0.42f, 1.06f, sz * 0.42f, 0.1f, 0.92f, 0.1f);
            }
        }
        box(p, Material.DARK_OAK_PLANKS, hx, 1.5f, 0f, 0.9f, 0.07f, 0.9f);
        // the drum turns in a timber cradle on the ridge, the cable winds onto it
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.DARK_OAK_PLANKS, drumX, drumY - 0.1f, s * 0.2f, 0.12f, 0.3f, 0.05f);
        }
        spin(p, "reel", Material.STRIPPED_DARK_OAK_LOG, drumX, drumY, 0f, 0.26f, 0.26f, 0.36f,
                new Quaternionf().rotateX((float) (Math.PI / 2)));
        for (int s = -1; s <= 1; s += 2) {
            box(p, TRIM[t], drumX, drumY, s * 0.17f, 0.32f, 0.32f, 0.03f);
        }
        float half = 0.48f;
        float plane = half * (float) Math.sqrt(2) + 0.08f;
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, ROOF[t], hx + s * half / 2f, 1.53f + half / 2f, 0f, plane, 0.08f, 1.06f,
                    new Quaternionf().rotateZ(-s * (float) Math.PI / 4));
        }
        float dia = (half - 0.04f) * (float) Math.sqrt(2);
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, Material.CALCITE, hx, 1.52f, s * 0.39f, dia, dia, 0.05f, new Quaternionf().rotateZ((float) Math.PI / 4));
        }
        Material ridge = t == 3 ? Material.GOLD_BLOCK : t == 2 ? Material.WAXED_CUT_COPPER : Material.STRIPPED_DARK_OAK_WOOD;
        box(p, ridge, hx, 1.53f + half + 0.02f, 0f, 0.1f, 0.1f, 1.08f);
        // window on the front gable wall, a door to the yard
        box(p, Material.SPRUCE_PLANKS, hx + 0.18f, 1.08f, 0.425f, 0.3f, 0.36f, 0.02f);
        box(p, Material.BLACK_STAINED_GLASS, hx + 0.18f, 1.08f, 0.43f, 0.22f, 0.28f, 0.02f);
        box(p, Material.SPRUCE_PLANKS, hx - 0.2f, 0.87f, 0.43f, 0.28f, 0.6f, 0.02f);
        box(p, Material.IRON_BLOCK, hx - 0.11f, 0.85f, 0.445f, 0.04f, 0.04f, 0.02f);
        // chimney through the back of the roof
        Material stack = t == 3 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BRICKS;
        box(p, stack, hx - 0.25f, 1.95f, -0.27f, 0.24f, 1.3f, 0.24f);
        box(p, t >= 2 ? Material.WAXED_CUT_COPPER : Material.POLISHED_DEEPSLATE, hx - 0.25f, 2.63f, -0.27f, 0.3f, 0.07f, 0.3f);
        if (t >= 2) {
            block(p, "", "minecraft:lightning_rod[facing=up]", hx - 0.25f, 2.9f, -0.27f, 0.3f, 0.46f, 0.3f, false);
        }
        // the ore yard: rails to the shaft, a loaded cart, a lantern post, a pick by the door
        block(p, "", "minecraft:rail[shape=north_south]", hx, 0.53f, 1.0f, 0.8f, 1f, 1.0f, false);
        cart(p, t, hx, 1.02f, ore(quarry));
        box(p, Material.DARK_OAK_FENCE, hx - 0.38f, 0.66f, 1.38f, 0.08f, 1.2f, 0.08f);
        box(p, Material.DARK_OAK_PLANKS, hx - 0.3f, 1.24f, 1.38f, 0.22f, 0.05f, 0.05f);
        light(p, t, hx - 0.22f, 1.08f, 1.38f, 0.3f);
        item(p, t >= 3 ? Material.GOLDEN_PICKAXE : t == 2 ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE,
                hx + 0.32f, 0.82f, 0.5f, 0.44f, new Quaternionf().rotateZ(0.5f));
        // the head lights and, at the top tier, the crown
        if (t >= 1) {
            block(p, "", "minecraft:lantern[hanging=true]", 0.22f, top - 0.24f, w1 + 0.06f, 0.32f, 0.32f, 0.32f, true);
        }
        if (t >= 3) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    boxR(p, Material.GOLD_BLOCK, sx * (w1 + 0.02f), top + 0.14f, sz * (w1 + 0.02f), 0.13f, 0.13f, 0.13f,
                            new Quaternionf().rotateY((float) Math.PI / 4));
                }
            }
            block(p, "", "minecraft:end_rod[facing=up]", wx, wy + r + 0.28f, 0f, 0.36f, 0.5f, 0.36f, true);
        }
        lamp(p, 0.22f, top + 0.16f, -0.18f);
    }

    /** The drill bit: stone, then a copper rod, then a white-hot end rod. */
    private static String drillBit(int t) {
        return switch (t) {
            case 2 -> "minecraft:lightning_rod[facing=down]";
            case 3 -> "minecraft:end_rod[facing=down]";
            default -> "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]";
        };
    }

    /** What the cart carries and the core is made of, per ore. */
    private static Material ore(QuarryType quarry) {
        if (quarry == null) {
            return Material.COBBLESTONE;
        }
        return switch (quarry) {
            case COAL -> Material.COAL_ORE;
            case RAW_IRON -> Material.RAW_IRON_BLOCK;
            case RAW_GOLD -> Material.RAW_GOLD_BLOCK;
            case RAW_COPPER -> Material.RAW_COPPER_BLOCK;
            case REDSTONE -> Material.REDSTONE_ORE;
            case LAPIS -> Material.LAPIS_ORE;
            case DIAMOND -> Material.DIAMOND_ORE;
            case EMERALD -> Material.EMERALD_ORE;
            default -> Material.COBBLESTONE;
        };
    }

    /** A mine cart on the rails, heaped with what the shaft gives. */
    private static void cart(List<PropBodies.Part> p, int t, float x, float z, Material load) {
        Material body = t == 0 ? Material.SPRUCE_PLANKS : Material.POLISHED_DEEPSLATE;
        Material rim = t >= 3 ? Material.GOLD_BLOCK : t == 2 ? Material.WAXED_CUT_COPPER : Material.IRON_BLOCK;
        box(p, body, x, 0.24f, z, 0.46f, 0.05f, 0.6f);
        box(p, body, x - 0.21f, 0.36f, z, 0.04f, 0.22f, 0.6f);
        box(p, body, x + 0.21f, 0.36f, z, 0.04f, 0.22f, 0.6f);
        box(p, body, x, 0.36f, z - 0.28f, 0.46f, 0.22f, 0.04f);
        box(p, body, x, 0.36f, z + 0.28f, 0.46f, 0.22f, 0.04f);
        box(p, rim, x, 0.48f, z - 0.29f, 0.5f, 0.03f, 0.03f);
        box(p, rim, x, 0.48f, z + 0.29f, 0.5f, 0.03f, 0.03f);
        box(p, rim, x - 0.22f, 0.48f, z, 0.03f, 0.03f, 0.6f);
        box(p, rim, x + 0.22f, 0.48f, z, 0.03f, 0.03f, 0.6f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, Material.POLISHED_BLACKSTONE, x + sx * 0.22f, 0.16f, z + sz * 0.18f, 0.05f, 0.13f, 0.13f);
            }
        }
        boxR(p, load, x - 0.06f, 0.47f, z - 0.1f, 0.26f, 0.26f, 0.26f, new Quaternionf().rotateY(0.3f).rotateX(0.15f));
        boxR(p, load, x + 0.08f, 0.45f, z + 0.13f, 0.22f, 0.22f, 0.22f, new Quaternionf().rotateY(-0.4f).rotateZ(0.2f));
        boxR(p, load, x + 0.02f, 0.62f, z + 0.02f, 0.16f, 0.16f, 0.16f, new Quaternionf().rotateY(0.7f).rotateX(0.3f));
    }

    // ------------------------------------------------------------------------------------------------
    // Farm: a red barn over the core (a bale goes up and down on its hoist), the silo beside it, the field
    // (or the paddock for leather, the coop for feathers) in front of the silo
    // ------------------------------------------------------------------------------------------------

    private static void farm(List<PropBodies.Part> p, int t, QuarryType quarry) {
        box(p, Material.PACKED_MUD, CX, 0.03f, CZ, 1.98f, 0.06f, 1.98f);
        barn(p, t);
        silo(p, t);
        if (quarry == QuarryType.LEATHER) {
            paddock(p, t);
        } else if (quarry == QuarryType.FEATHER) {
            coop(p, t);
        } else {
            bed(p, t, quarry);
        }
    }

    /** The barn on the core's cell: barn-red boards, white trim, an open front, the hayloft and its hoist. */
    private static void barn(List<PropBodies.Part> p, int t) {
        Material red = Material.MANGROVE_PLANKS;
        Material white = Material.CALCITE;
        box(p, Material.SPRUCE_PLANKS, 0f, 0.08f, 0f, 0.9f, 0.06f, 0.9f);
        float wall = 1.3f;
        box(p, red, 0f, 0.06f + wall / 2f, -0.42f, 0.9f, wall, 0.08f);
        box(p, red, -0.42f, 0.06f + wall / 2f, 0f, 0.08f, wall, 0.9f);
        box(p, red, 0.42f, 0.06f + wall / 2f, 0f, 0.08f, wall, 0.9f);
        // white trim: corners, the open front's frame, a window on the right
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, white, sx * 0.44f, 0.06f + wall / 2f, sz * 0.44f, 0.08f, wall, 0.08f);
            }
        }
        box(p, white, 0f, wall + 0.02f, 0.44f, 0.96f, 0.08f, 0.08f);
        box(p, white, 0.465f, 0.85f, 0f, 0.02f, 0.36f, 0.32f);
        box(p, Material.BLACK_STAINED_GLASS, 0.47f, 0.85f, 0f, 0.02f, 0.28f, 0.24f);
        // half doors swung back against the walls inside, X-braced
        for (int s = -1; s <= 1; s += 2) {
            box(p, red, s * 0.36f, 0.42f, 0.3f, 0.04f, 0.62f, 0.26f);
            boxR(p, white, s * 0.335f, 0.42f, 0.3f, 0.02f, 0.66f, 0.04f, new Quaternionf().rotateX(0.62f));
            boxR(p, white, s * 0.335f, 0.42f, 0.3f, 0.02f, 0.66f, 0.04f, new Quaternionf().rotateX(-0.62f));
        }
        // the roof: a steep gable, ridge front to back, the gable over the front
        float eave = wall + 0.06f;
        float half = 0.52f;
        float plane = half * (float) Math.sqrt(2) + 0.06f;
        Material roof = t >= 3 ? Material.DEEPSLATE_TILES : t == 2 ? Material.WAXED_EXPOSED_CUT_COPPER
                : t == 1 ? Material.DEEPSLATE_TILES : Material.SPRUCE_PLANKS;
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, roof, s * half / 2f, eave + half / 2f, 0f, plane, 0.08f, 1.06f,
                    new Quaternionf().rotateZ(-s * (float) Math.PI / 4));
        }
        float dia = (half - 0.08f) * (float) Math.sqrt(2);
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, red, 0f, eave - 0.02f, s * 0.42f, dia, dia, 0.06f, new Quaternionf().rotateZ((float) Math.PI / 4));
        }
        box(p, t >= 3 ? Material.GOLD_BLOCK : white, 0f, eave + half + 0.02f, 0f, 0.09f, 0.09f, 1.08f);
        // the hayloft door and the hoist beam over the chute: a bale rides up and down on it
        box(p, white, 0f, eave + 0.2f, 0.455f, 0.3f, 0.3f, 0.02f);
        box(p, Material.DARK_OAK_PLANKS, 0f, eave + 0.2f, 0.47f, 0.22f, 0.22f, 0.02f);
        state(p, "minecraft:stripped_dark_oak_log[axis=z]", 0f, eave + 0.5f, 0.62f, 0.09f, 0.09f, 0.5f);
        block(p, "rope", "minecraft:chain[axis=y]", 0f, eave + 0.18f, 0.8f, 0.36f, 0.6f, 0.36f, false);
        block(p, "drill", "minecraft:hay_block[axis=y]", 0f, eave - 0.24f, 0.8f, 0.3f, 0.3f, 0.3f, false);
        // the bale table under the core, a lantern inside
        box(p, Material.SMOOTH_STONE, 0f, 0.16f, -0.02f, 0.5f, 0.12f, 0.5f);
        box(p, Material.HAY_BLOCK, 0f, 0.33f, -0.02f, 0.4f, 0.22f, 0.4f);
        light(p, t, 0.22f, wall - 0.18f, -0.22f, 0.26f);
        // weathervane at the back of the ridge
        box(p, Material.IRON_BARS, 0f, eave + half + 0.3f, -0.32f, 0.04f, 0.5f, 0.04f);
        spin(p, "stone", t >= 3 ? Material.GOLDEN_SWORD : Material.ARROW, 0f, eave + half + 0.52f, -0.32f, 0.36f, 0.36f, 0.36f,
                new Quaternionf().rotateX((float) (-Math.PI / 2)).rotateZ((float) (Math.PI / 4)));
    }

    /** The silo on the back-left cell: an eight-sided stave tower with hoops, a dome, a chute pipe to the barn. */
    private static void silo(List<PropBodies.Part> p, int t) {
        float x = -1.0f;
        float z = 0.0f;
        float top = 2.45f;
        Material skin = t >= 2 ? Material.CALCITE : Material.STRIPPED_BIRCH_WOOD;
        octagonAt(p, skin, x, z, 0.06f, top, 0.4f, 0.08f);
        Material hoop = t >= 3 ? Material.GOLD_BLOCK : t == 2 ? Material.WAXED_CUT_COPPER : Material.POLISHED_DEEPSLATE;
        for (float hy : new float[]{0.55f, 1.3f, 2.05f}) {
            octagonAt(p, hoop, x, z, hy - 0.03f, hy + 0.03f, 0.43f, 0.03f);
        }
        Material dome = t >= 2 ? Material.WAXED_WEATHERED_CUT_COPPER : t == 1 ? Material.DEEPSLATE_TILES : Material.DARK_OAK_PLANKS;
        octagonAt(p, dome, x, z, top, top + 0.2f, 0.44f, 0.08f);
        box(p, dome, x, top + 0.26f, z, 0.62f, 0.12f, 0.62f);
        box(p, dome, x, top + 0.37f, z, 0.42f, 0.1f, 0.42f);
        box(p, dome, x, top + 0.46f, z, 0.22f, 0.08f, 0.22f);
        box(p, t >= 3 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE, x, top + 0.53f, z, 0.08f, 0.08f, 0.08f);
        // the fill pipe from the dome down to the barn's loft
        strutM(p, t >= 2 ? Material.WAXED_CUT_COPPER : Material.STRIPPED_SPRUCE_WOOD, x + 0.3f, top - 0.05f, z,
                -0.36f, 1.62f, z, 0.11f);
        // a ladder up the front
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.SPRUCE_PLANKS, x + s * 0.1f, 1.25f, z + 0.43f, 0.03f, 2.3f, 0.03f);
        }
        for (int i = 0; i < 8; i++) {
            box(p, Material.SPRUCE_PLANKS, x, 0.35f + i * 0.27f, z + 0.43f, 0.22f, 0.025f, 0.025f);
        }
        lamp(p, x, top + 0.64f, z);
    }

    /** Wheat, carrots or potatoes in a kerbed bed, a scarecrow keeping watch. */
    private static void bed(List<PropBodies.Part> p, int t, QuarryType quarry) {
        float bx = -1.0f;
        float bz = 1.0f;
        box(p, Material.FARMLAND, bx, 0.09f, bz, 0.86f, 0.12f, 0.86f);
        Material kerb = t >= 2 ? Material.STRIPPED_SPRUCE_LOG : Material.STRIPPED_OAK_LOG;
        state(p, kerbState(kerb, "z"), bx - 0.45f, 0.12f, bz, 0.08f, 0.16f, 0.98f);
        state(p, kerbState(kerb, "z"), bx + 0.45f, 0.12f, bz, 0.08f, 0.16f, 0.98f);
        state(p, kerbState(kerb, "x"), bx, 0.12f, bz - 0.45f, 0.82f, 0.16f, 0.08f);
        state(p, kerbState(kerb, "x"), bx, 0.12f, bz + 0.45f, 0.82f, 0.16f, 0.08f);
        String crop = switch (quarry == null ? QuarryType.WHEAT : quarry) {
            case CARROT -> "minecraft:carrots[age=7]";
            case POTATO -> "minecraft:potatoes[age=7]";
            default -> "minecraft:wheat[age=7]";
        };
        float tall = quarry == QuarryType.CARROT || quarry == QuarryType.POTATO ? 0.4f : 0.56f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 0 && col == 0) {
                    continue; // the scarecrow's corner
                }
                block(p, "", crop, bx - 0.27f + col * 0.27f, 0.15f + tall / 2f, bz - 0.27f + row * 0.27f, 0.3f, tall, 0.3f, false);
            }
        }
        // the scarecrow
        float sx = bx - 0.27f;
        float sz = bz - 0.27f;
        box(p, Material.STRIPPED_SPRUCE_LOG, sx, 0.62f, sz, 0.06f, 0.96f, 0.06f);
        box(p, Material.STRIPPED_SPRUCE_LOG, sx, 0.9f, sz, 0.5f, 0.045f, 0.045f);
        box(p, Material.BROWN_WOOL, sx, 0.84f, sz, 0.18f, 0.24f, 0.12f);
        block(p, "", "minecraft:carved_pumpkin[facing=south]", sx, 1.1f, sz, 0.22f, 0.22f, 0.22f, false);
        Material hat = t >= 3 ? Material.GOLD_BLOCK : Material.HAY_BLOCK;
        box(p, hat, sx, 1.23f, sz, 0.34f, 0.03f, 0.34f);
        box(p, hat, sx, 1.27f, sz, 0.17f, 0.07f, 0.17f);
        if (t >= 2) {
            // a copper sprinkler post at the field's front corner
            box(p, Material.WAXED_CUT_COPPER, bx + 0.4f, 0.55f, bz + 0.4f, 0.05f, 0.9f, 0.05f);
            block(p, "", "minecraft:lightning_rod[facing=up]", bx + 0.4f, 1.08f, bz + 0.4f, 0.24f, 0.24f, 0.24f, false);
        }
    }

    private static String kerbState(Material log, String axis) {
        return "minecraft:" + log.name().toLowerCase(java.util.Locale.ROOT) + "[axis=" + axis + "]";
    }

    /** Leather: a fenced corner with hay, a trough and hides drying on a rail. */
    private static void paddock(List<PropBodies.Part> p, int t) {
        float bx = -1.0f;
        float bz = 1.0f;
        box(p, Material.COARSE_DIRT, bx, 0.07f, bz, 0.96f, 0.04f, 0.96f);
        fenceRun(p, bx - 0.44f, bz, 0.9f, true);
        fenceRun(p, bx, bz + 0.44f, 0.9f, false);
        state(p, "minecraft:hay_block[axis=x]", bx - 0.12f, 0.24f, bz - 0.22f, 0.4f, 0.32f, 0.34f);
        block(p, "", "minecraft:water_cauldron[level=3]", bx + 0.22f, 0.25f, bz + 0.2f, 0.36f, 0.36f, 0.36f, false);
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.STRIPPED_SPRUCE_LOG, bx + 0.3f, 0.6f, bz - 0.1f + s * 0.3f, 0.06f, 1.08f, 0.06f);
        }
        box(p, Material.STRIPPED_SPRUCE_LOG, bx + 0.3f, 1.12f, bz - 0.1f, 0.05f, 0.05f, 0.7f);
        for (int i = 0; i < 2; i++) {
            item(p, Material.LEATHER, bx + 0.3f, 0.92f, bz - 0.24f + i * 0.28f, 0.34f,
                    new Quaternionf().rotateY((float) Math.PI / 2).rotateZ((float) Math.PI));
        }
        if (t >= 1) {
            box(p, Material.SPRUCE_FENCE, bx - 0.44f, 0.7f, bz + 0.44f, 0.08f, 0.5f, 0.08f);
            light(p, t, bx - 0.44f, 1.04f, bz + 0.44f, 0.24f);
        }
    }

    /** Feathers: a little hen house on stilts, its ramp, straw, eggs and feathers. */
    private static void coop(List<PropBodies.Part> p, int t) {
        float bx = -1.0f;
        float bz = 1.0f;
        box(p, Material.COARSE_DIRT, bx, 0.07f, bz, 0.96f, 0.04f, 0.96f);
        fenceRun(p, bx - 0.44f, bz, 0.9f, true);
        fenceRun(p, bx, bz + 0.44f, 0.9f, false);
        float hx = bx + 0.08f;
        float hz = bz - 0.12f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, Material.STRIPPED_SPRUCE_LOG, hx + sx * 0.24f, 0.27f, hz + sz * 0.2f, 0.05f, 0.42f, 0.05f);
            }
        }
        box(p, Material.SPRUCE_PLANKS, hx, 0.5f, hz, 0.58f, 0.05f, 0.5f);
        box(p, Material.WHITE_TERRACOTTA, hx, 0.72f, hz, 0.54f, 0.4f, 0.46f);
        box(p, Material.BLACK_STAINED_GLASS, hx + 0.1f, 0.68f, hz + 0.235f, 0.14f, 0.18f, 0.02f);
        boxR(p, t >= 2 ? Material.WAXED_CUT_COPPER : Material.DARK_OAK_PLANKS, hx, 1.0f, hz, 0.7f, 0.05f, 0.62f,
                new Quaternionf().rotateZ(0.3f));
        boxR(p, Material.SPRUCE_SLAB, hx - 0.12f, 0.3f, hz + 0.42f, 0.16f, 0.03f, 0.42f, new Quaternionf().rotateX(0.8f));
        box(p, Material.HAY_BLOCK, bx + 0.22f, 0.12f, bz + 0.3f, 0.36f, 0.05f, 0.3f);
        item(p, Material.EGG, bx + 0.24f, 0.18f, bz + 0.3f, 0.16f, new Quaternionf().rotateX((float) (-Math.PI / 2)));
        item(p, Material.FEATHER, bx - 0.22f, 0.11f, bz + 0.3f, 0.22f, new Quaternionf().rotateX((float) (-Math.PI / 2)).rotateZ(0.6f));
    }

    /** A run of fence: posts and two rails, along z ({@code alongZ}) or x, centred on (x, z). */
    private static void fenceRun(List<PropBodies.Part> p, float x, float z, float length, boolean alongZ) {
        int posts = Math.max(2, Math.round(length / 0.45f) + 1);
        for (int i = 0; i < posts; i++) {
            float o = -length / 2f + i * length / (posts - 1);
            box(p, Material.SPRUCE_FENCE, alongZ ? x : x + o, 0.33f, alongZ ? z + o : z, 0.08f, 0.5f, 0.08f);
        }
        for (float ry : new float[]{0.26f, 0.48f}) {
            box(p, Material.SPRUCE_PLANKS, x, ry, z, alongZ ? 0.04f : length, 0.05f, alongZ ? length : 0.04f);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Grove: a tree of the wood it gathers, the saw shed over the core (blade turning, logs lowered onto it),
    // cut logs and the stump with the axe in front
    // ------------------------------------------------------------------------------------------------

    private static void grove(List<PropBodies.Part> p, int t, QuarryType quarry) {
        Material log = quarry == null ? Material.OAK_LOG : quarry.product().isBlock() ? quarry.product() : Material.OAK_LOG;
        String axisLog = "minecraft:" + log.name().toLowerCase(java.util.Locale.ROOT);
        boolean nether = quarry == QuarryType.CRIMSON_STEM || quarry == QuarryType.WARPED_STEM;
        boolean bamboo = quarry == QuarryType.BAMBOO_BLOCK;
        box(p, nether ? (quarry == QuarryType.CRIMSON_STEM ? Material.CRIMSON_NYLIUM : Material.WARPED_NYLIUM)
                : Material.MOSS_BLOCK, -1.0f, 0.04f, CZ, 0.98f, 0.08f, 1.98f);
        box(p, Material.PACKED_MUD, 0f, 0.03f, 0f, 0.98f, 0.06f, 0.98f);
        box(p, Material.PACKED_MUD, 0f, 0.03f, 1.0f, 0.98f, 0.06f, 0.98f);
        if (bamboo) {
            bambooStand(p);
        } else if (nether) {
            fungus(p, t, quarry);
        } else {
            tree(p, t, quarry, log, axisLog);
        }
        sawShed(p, t, axisLog);
        // in front: cut logs on a rack, the stump with the axe, a sapling for the next one
        String stripped = strippedOf(log);
        float lx = -1.05f;
        float lz = 1.1f;
        for (int s = -1; s <= 1; s += 2) {
            state(p, "minecraft:stripped_spruce_log[axis=y]", lx + s * 0.32f, 0.22f, lz, 0.06f, 0.36f, 0.06f);
        }
        for (int i = 0; i < 3; i++) {
            state(p, stripped + "[axis=x]", lx, 0.17f, lz - 0.2f + i * 0.2f, 0.72f, 0.19f, 0.19f);
        }
        for (int i = 0; i < 2; i++) {
            state(p, stripped + "[axis=x]", lx, 0.35f, lz - 0.1f + i * 0.2f, 0.72f, 0.19f, 0.19f);
        }
        state(p, stripped + "[axis=x]", lx, 0.53f, lz, 0.72f, 0.19f, 0.19f);
        state(p, axisLog + "[axis=y]", -0.52f, 0.2f, 1.36f, 0.26f, 0.26f, 0.26f);
        item(p, t >= 3 ? Material.GOLDEN_AXE : t == 2 ? Material.DIAMOND_AXE : t == 1 ? Material.IRON_AXE : Material.STONE_AXE,
                -0.5f, 0.43f, 1.36f, 0.34f, new Quaternionf().rotateZ(-0.5f));
        String sapling = switch (quarry == null ? QuarryType.OAK_LOG : quarry) {
            case BIRCH_LOG -> "minecraft:birch_sapling";
            case SPRUCE_LOG -> "minecraft:spruce_sapling";
            case JUNGLE_LOG -> "minecraft:jungle_sapling";
            case ACACIA_LOG -> "minecraft:acacia_sapling";
            case DARK_OAK_LOG -> "minecraft:dark_oak_sapling";
            case MANGROVE_LOG -> "minecraft:mangrove_propagule";
            case CHERRY_LOG -> "minecraft:cherry_sapling";
            case BAMBOO_BLOCK -> "minecraft:bamboo_sapling";
            case CRIMSON_STEM -> "minecraft:crimson_fungus";
            case WARPED_STEM -> "minecraft:warped_fungus";
            default -> "minecraft:oak_sapling";
        };
        block(p, "", sapling, -1.32f, 0.3f, 1.36f, 0.36f, 0.36f, 0.36f, false);
    }

    /** The saw shed on the core's cell: four posts, a pent roof, the blade turning into the log on the bench. */
    private static void sawShed(List<PropBodies.Part> p, int t, String axisLog) {
        Material post = t == 0 ? Material.STRIPPED_SPRUCE_LOG : t == 1 ? Material.POLISHED_DEEPSLATE
                : t == 2 ? Material.WAXED_CUT_COPPER : Material.POLISHED_BLACKSTONE;
        float front = 1.9f;
        float back = 1.55f;
        for (int sx = -1; sx <= 1; sx += 2) {
            box(p, post, sx * 0.42f, 0.06f + front / 2f, 0.4f, 0.1f, front, 0.1f);
            box(p, post, sx * 0.42f, 0.06f + back / 2f, -0.42f, 0.1f, back, 0.1f);
        }
        box(p, GIRT[t], 0f, front + 0.02f, 0.4f, 0.96f, 0.09f, 0.09f);
        box(p, GIRT[t], 0f, back + 0.02f, -0.42f, 0.96f, 0.09f, 0.09f);
        float pitch = (float) Math.atan2(front - back, 0.82f);
        Material roof = t >= 3 ? Material.DEEPSLATE_TILES : t == 2 ? Material.WAXED_EXPOSED_CUT_COPPER
                : t == 1 ? Material.SPRUCE_PLANKS : Material.OAK_PLANKS;
        boxR(p, roof, 0f, (front + back) / 2f + 0.12f, 0f, 1.1f, 0.08f, 1.16f, new Quaternionf().rotateX(pitch));
        if (t >= 3) {
            boxR(p, Material.GOLD_BLOCK, 0f, front + 0.14f, 0.58f, 1.12f, 0.05f, 0.05f, new Quaternionf().rotateX(pitch));
        }
        // the bench and the blade (it turns in the x-y plane, biting into the log from below)
        state(p, axisLog + "[axis=y]", 0f, 0.2f, -0.05f, 0.56f, 0.28f, 0.5f);
        block(p, "", "minecraft:stonecutter[facing=south]", 0f, 0.44f, -0.05f, 0.56f, 0.56f, 0.56f, false);
        Material steel = t >= 3 ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
        float r = 0.26f;
        float seg = 2f * r * (float) Math.tan(Math.PI / 8) + 0.02f;
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4;
            boxR(p, steel, 0.12f + (float) Math.cos(th) * r, 0.62f + (float) Math.sin(th) * r, 0.24f, 0.05f, seg, 0.02f,
                    new Quaternionf().rotateZ((float) th));
        }
        for (int i = 0; i < 4; i++) {
            spin(p, "reel", Material.IRON_BLOCK, 0.12f, 0.62f, 0.24f, 0.04f, 2f * r - 0.04f, 0.015f,
                    new Quaternionf().rotateZ((float) (i * Math.PI / 4)));
        }
        box(p, Material.POLISHED_DEEPSLATE, 0.12f, 0.62f, 0.24f, 0.08f, 0.08f, 0.05f);
        // the log on its chain, lowered onto the blade
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", 0f, 1.66f, 0f, 0.96f, 0.1f, 0.1f);
        block(p, "rope", "minecraft:chain[axis=y]", 0f, 1.48f, 0f, 0.4f, 0.32f, 0.4f, false);
        block(p, "drill", axisLog + "[axis=z]", 0f, 1.22f, 0f, 0.26f, 0.26f, 0.46f, false);
        light(p, t, 0.28f, front - 0.22f, 0.28f, 0.26f);
        lamp(p, -0.32f, front + 0.2f, 0.36f);
    }

    private static String strippedOf(Material log) {
        String name = log.name().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("bamboo_block")) {
            return "minecraft:stripped_bamboo_block";
        }
        return "minecraft:stripped_" + name;
    }

    /** A young tree on the back-left cell: trunk, roots, a cloud of leaves that leans over the shed. */
    private static void tree(List<PropBodies.Part> p, int t, QuarryType quarry, Material log, String axisLog) {
        Material leaves = switch (quarry == null ? QuarryType.OAK_LOG : quarry) {
            case BIRCH_LOG -> Material.BIRCH_LEAVES;
            case SPRUCE_LOG -> Material.SPRUCE_LEAVES;
            case CHERRY_LOG -> Material.CHERRY_LEAVES;
            default -> t >= 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES;
        };
        float tx = -1.05f;
        float tz = -0.05f;
        state(p, axisLog + "[axis=y]", tx, 1.15f, tz, 0.34f, 2.2f, 0.34f);
        boxR(p, log, tx + 0.17f, 0.12f, tz + 0.12f, 0.14f, 0.14f, 0.32f, new Quaternionf().rotateY(0.7f).rotateX(0.5f));
        boxR(p, log, tx - 0.16f, 0.12f, tz - 0.1f, 0.13f, 0.13f, 0.28f, new Quaternionf().rotateY(-2.2f).rotateX(0.5f));
        if (quarry == QuarryType.SPRUCE_LOG) {
            float[][] tiers = {{1.0f, 1.25f, 0.42f}, {0.8f, 1.75f, 0.4f}, {0.6f, 2.2f, 0.38f}, {0.38f, 2.6f, 0.34f},
                    {0.18f, 2.9f, 0.26f}};
            for (float[] tier : tiers) {
                box(p, leaves, tx, tier[1], tz, tier[0], tier[2], tier[0]);
            }
            return;
        }
        state(p, axisLog + "[axis=x]", tx + 0.32f, 1.7f, tz, 0.46f, 0.15f, 0.15f);
        float[][] puffs = {
                {0f, 2.3f, 0f, 0.96f, 0.6f, 0.96f},
                {0.4f, 1.98f, 0.1f, 0.6f, 0.46f, 0.66f},
                {-0.2f, 2.0f, 0.3f, 0.56f, 0.46f, 0.5f},
                {0.12f, 2.02f, -0.34f, 0.6f, 0.44f, 0.5f},
                {-0.08f, 2.72f, 0.02f, 0.64f, 0.36f, 0.64f},
        };
        for (float[] puff : puffs) {
            box(p, leaves, tx + puff[0], puff[1], tz + puff[2], puff[3], puff[4], puff[5]);
        }
        if (t >= 3) {
            item(p, Material.GOLDEN_APPLE, tx + 0.42f, 1.68f, tz + 0.36f, 0.2f, new Quaternionf());
        }
    }

    /** Bamboo: a stand of stalks of different heights on the back-left cell. */
    private static void bambooStand(List<PropBodies.Part> p) {
        float[][] stalks = {{-1.28f, -0.25f, 4}, {-0.86f, -0.18f, 3}, {-1.1f, 0.12f, 4}, {-0.78f, 0.25f, 2},
                {-1.3f, 0.36f, 3}};
        for (float[] s : stalks) {
            int n = (int) s[2];
            for (int i = 0; i < n; i++) {
                String leaves = i == n - 1 ? "large" : i == n - 2 ? "small" : "none";
                block(p, "", "minecraft:bamboo[age=1,leaves=" + leaves + ",stage=0]", s[0], 0.08f + 0.33f + i * 0.66f, s[1],
                        0.66f, 0.66f, 0.66f, false);
            }
        }
    }

    /** Crimson / warped: a huge fungus on the back-left cell, its cap, shroomlights in it. */
    private static void fungus(List<PropBodies.Part> p, int t, QuarryType quarry) {
        boolean crimson = quarry == QuarryType.CRIMSON_STEM;
        String stem = crimson ? "minecraft:crimson_stem[axis=y]" : "minecraft:warped_stem[axis=y]";
        Material cap = crimson ? Material.NETHER_WART_BLOCK : Material.WARPED_WART_BLOCK;
        float tx = -1.05f;
        float tz = 0.0f;
        state(p, stem, tx, 1.1f, tz, 0.34f, 2.2f, 0.34f);
        box(p, cap, tx, 2.2f, tz, 1.02f, 0.34f, 1.02f);
        box(p, cap, tx, 2.5f, tz, 0.7f, 0.28f, 0.7f);
        box(p, cap, tx, 2.72f, tz, 0.36f, 0.18f, 0.36f);
        for (int s = -1; s <= 1; s += 2) {
            box(p, cap, tx + s * 0.42f, 1.9f, tz, 0.18f, 0.32f, 0.9f);
            box(p, cap, tx, 1.92f, tz + s * 0.42f, 0.66f, 0.26f, 0.18f);
        }
        block(p, "", "minecraft:shroomlight", tx + 0.3f, 1.98f, tz + 0.4f, 0.2f, 0.2f, 0.2f, true);
        block(p, "", "minecraft:shroomlight", tx - 0.34f, 1.96f, tz - 0.28f, 0.18f, 0.18f, 0.18f, true);
        if (t >= 2) {
            block(p, "", "minecraft:shroomlight", tx, 2.86f, tz, 0.2f, 0.12f, 0.2f, true);
        }
        block(p, "", crimson ? "minecraft:crimson_roots" : "minecraft:warped_roots", tx + 0.3f, 0.26f, tz + 0.32f,
                0.3f, 0.3f, 0.3f, false);
    }

    // ------------------------------------------------------------------------------------------------
    // Fishery: a pond down the left side with the fisher's hut on stilts at its back, the net on a boom over
    // the open water, the catch drying on a rack beside the core
    // ------------------------------------------------------------------------------------------------

    private static void fishery(List<PropBodies.Part> p, int t) {
        float px = -1.0f;
        box(p, Material.DARK_PRISMARINE, px, 0.05f, CZ, 0.86f, 0.06f, 1.86f);
        block(p, "", "minecraft:light_blue_stained_glass", px, 0.13f, CZ, 0.86f, 0.03f, 1.86f, false);
        Material kerb = t >= 3 ? Material.POLISHED_BLACKSTONE_BRICKS : t >= 1 ? Material.STONE_BRICKS : Material.MOSSY_COBBLESTONE;
        box(p, kerb, px - 0.45f, 0.11f, CZ, 0.1f, 0.22f, 2.0f);
        box(p, kerb, px + 0.45f, 0.11f, CZ, 0.1f, 0.22f, 2.0f);
        box(p, kerb, px, 0.11f, -0.45f, 0.8f, 0.22f, 0.1f);
        box(p, kerb, px, 0.11f, 1.45f, 0.8f, 0.22f, 0.1f);
        block(p, "", "minecraft:seagrass", px - 0.22f, 0.24f, 1.2f, 0.34f, 0.34f, 0.34f, false);
        block(p, "", "minecraft:sea_pickle[pickles=3,waterlogged=false]", px + 0.25f, 0.19f, 1.25f, 0.26f, 0.26f, 0.26f, t >= 2);
        // the hut on stilts over the back of the pond
        Material post = t == 0 ? Material.STRIPPED_SPRUCE_LOG : t == 1 ? Material.STRIPPED_DARK_OAK_LOG
                : t == 2 ? Material.WAXED_CUT_COPPER : Material.POLISHED_BLACKSTONE;
        float hz = -0.02f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, post, px + sx * 0.36f, 0.4f, hz + sz * 0.36f, 0.08f, 0.8f, 0.08f);
            }
        }
        box(p, Material.SPRUCE_PLANKS, px, 0.82f, hz, 0.9f, 0.06f, 0.9f);
        Material boards = t >= 2 ? Material.STRIPPED_DARK_OAK_WOOD : Material.SPRUCE_PLANKS;
        box(p, boards, px - 0.06f, 1.2f, hz - 0.08f, 0.7f, 0.7f, 0.66f);
        box(p, Material.SPRUCE_PLANKS, px - 0.06f + 0.2f, 1.12f, hz + 0.255f, 0.2f, 0.26f, 0.02f);
        box(p, Material.BLACK_STAINED_GLASS, px - 0.06f + 0.2f, 1.12f, hz + 0.26f, 0.14f, 0.2f, 0.02f);
        box(p, Material.DARK_OAK_PLANKS, px - 0.24f, 1.05f, hz + 0.255f, 0.18f, 0.42f, 0.02f);
        Material roof = t >= 3 ? Material.DEEPSLATE_TILES : t == 2 ? Material.WAXED_WEATHERED_CUT_COPPER
                : t == 1 ? Material.DEEPSLATE_TILES : Material.DARK_OAK_PLANKS;
        boxR(p, roof, px - 0.06f, 1.66f, hz - 0.02f, 0.92f, 0.07f, 0.94f, new Quaternionf().rotateX(-0.3f));
        if (t >= 3) {
            boxR(p, Material.GOLD_BLOCK, px - 0.06f, 1.8f, hz + 0.43f, 0.94f, 0.04f, 0.05f, new Quaternionf().rotateX(-0.3f));
        }
        // the smoke stack of the fish smoker inside
        box(p, Material.BRICKS, px - 0.3f, 1.85f, hz - 0.28f, 0.16f, 0.5f, 0.16f);
        box(p, Material.POLISHED_DEEPSLATE, px - 0.3f, 2.12f, hz - 0.28f, 0.2f, 0.05f, 0.2f);
        // the boom from the hut out over the open water, the net on its rope
        float tipX = px + 0.12f;
        float tipZ = 0.95f;
        float armY = 1.95f;
        box(p, post, px + 0.36f, 1.4f, hz + 0.36f, 0.08f, 1.2f, 0.08f);
        strut(p, "minecraft:stripped_spruce_log[axis=y]", px + 0.36f, armY - 0.05f, hz + 0.36f, tipX, armY + 0.08f, tipZ, 0.07f);
        strut(p, "minecraft:stripped_spruce_log[axis=y]", px + 0.36f, 1.35f, hz + 0.36f, (px + 0.36f + tipX) / 2f,
                armY + 0.01f, (hz + 0.36f + tipZ) / 2f, 0.045f);
        block(p, "rope", "minecraft:chain[axis=y]", tipX, (armY + 0.08f + 0.86f) / 2f, tipZ, 0.36f, armY + 0.08f - 0.86f, 0.36f, false);
        block(p, "drill", "minecraft:cobweb", tipX, 0.64f, tipZ, 0.46f, 0.44f, 0.46f, false);
        box(p, Material.SPRUCE_PLANKS, tipX, 0.87f, tipZ, 0.46f, 0.035f, 0.035f);
        // the deck on the right, the catch on its barrel under the drying rack
        Material deck = t >= 2 ? Material.STRIPPED_DARK_OAK_WOOD : Material.SPRUCE_PLANKS;
        box(p, deck, 0f, 0.07f, 0f, 0.98f, 0.1f, 0.98f);
        box(p, Material.SPRUCE_PLANKS, 0f, 0.07f, 1.0f, 0.98f, 0.1f, 0.98f);
        block(p, "", "minecraft:barrel[facing=up]", 0f, 0.4f, 0f, 0.5f, 0.56f, 0.5f, false);
        for (int s = -1; s <= 1; s += 2) {
            strut(p, "minecraft:stripped_spruce_log[axis=y]", s * 0.42f, 0.1f, -0.42f, s * 0.42f, 1.5f, -0.3f, 0.07f);
            strut(p, "minecraft:stripped_spruce_log[axis=y]", s * 0.42f, 0.1f, -0.1f, s * 0.42f, 1.5f, -0.3f, 0.07f);
        }
        box(p, Material.STRIPPED_SPRUCE_LOG, 0f, 1.5f, -0.3f, 0.92f, 0.06f, 0.06f);
        for (int i = -1; i <= 1; i++) {
            item(p, Material.COD, i * 0.25f, 1.28f, -0.27f, 0.32f, new Quaternionf().rotateZ((float) (Math.PI / 2 + i * 0.12)));
        }
        box(p, Material.SPRUCE_PLANKS, -0.3f, 0.3f, 0.32f, 0.28f, 0.28f, 0.28f);
        item(p, Material.COD, -0.3f, 0.48f, 0.32f, 0.24f, new Quaternionf().rotateX((float) (-Math.PI / 2)));
        item(p, Material.FISHING_ROD, 0.3f, 0.6f, 0.35f, 0.5f, new Quaternionf().rotateZ(-0.3f));
        // a lamp post at the pond's front corner
        box(p, Material.DARK_OAK_FENCE, px + 0.42f, 0.7f, 1.42f, 0.07f, 1.1f, 0.07f);
        if (t >= 2) {
            block(p, "", "minecraft:sea_lantern", px + 0.42f, 1.32f, 1.42f, 0.2f, 0.2f, 0.2f, true);
        } else {
            block(p, "", "minecraft:lantern[hanging=false]", px + 0.42f, 1.38f, 1.42f, 0.3f, 0.3f, 0.3f, true);
        }
        lamp(p, px - 0.06f, 1.92f, hz + 0.3f);
    }

    // ------------------------------------------------------------------------------------------------
    // Mob yard: a dark keep at the back with the spawner caged in its gate, the pit with bones in front of it,
    // the grinder over the core (its blades turn)
    // ------------------------------------------------------------------------------------------------

    private static void pit(List<PropBodies.Part> p, int t, QuarryType quarry) {
        Material stone = t >= 3 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS;
        Material floor = t >= 3 ? Material.GILDED_BLACKSTONE : Material.DEEPSLATE_TILES;
        box(p, floor, CX, 0.03f, CZ, 1.98f, 0.06f, 1.98f);
        // the keep on the back-left cell: walls round the cage, a barred gate to the front, battlements
        float kx = -1.0f;
        float kz = 0.0f;
        float kh = 1.75f;
        box(p, stone, kx, kh / 2f, kz - 0.4f, 0.92f, kh, 0.12f);
        box(p, stone, kx - 0.4f, kh / 2f, kz, 0.12f, kh, 0.92f);
        box(p, stone, kx + 0.4f, kh / 2f, kz, 0.12f, kh, 0.92f);
        box(p, stone, kx, kh - 0.3f, kz + 0.4f, 0.92f, 0.6f, 0.12f);
        box(p, Material.POLISHED_BLACKSTONE, kx, kh + 0.03f, kz, 0.98f, 0.06f, 0.98f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, stone, kx + sx * 0.38f, kh + 0.18f, kz + sz * 0.38f, 0.22f, 0.26f, 0.22f);
            }
        }
        for (int i = 0; i < 3; i++) {
            block(p, "", "minecraft:iron_bars[north=true,south=true]", kx - 0.24f + i * 0.24f, 0.62f, kz + 0.42f, 0.3f, 1.1f, 0.6f, false);
        }
        block(p, "", "minecraft:iron_bars[east=true,west=true]", kx, 0.62f, kz + 0.42f, 0.8f, 1.1f, 0.3f, false);
        block(p, "", "minecraft:spawner", kx, 0.42f, kz, 0.6f, 0.6f, 0.6f, false);
        block(p, "", "minecraft:soul_lantern[hanging=false]", kx, 0.38f, kz, 0.28f, 0.28f, 0.28f, true);
        block(p, "", "minecraft:soul_lantern[hanging=false]", kx, kh + 0.24f, kz, 0.3f, 0.3f, 0.3f, true);
        if (t >= 2) {
            box(p, Material.WAXED_CUT_COPPER, kx, kh - 0.02f, kz + 0.47f, 0.96f, 0.06f, 0.04f);
        }
        if (t >= 3) {
            block(p, "", "minecraft:crying_obsidian", kx, kh + 0.08f, kz, 0.3f, 0.06f, 0.3f, true);
            for (int sx = -1; sx <= 1; sx += 2) {
                box(p, Material.GOLD_BLOCK, kx + sx * 0.38f, kh + 0.33f, kz + 0.38f, 0.1f, 0.06f, 0.1f);
            }
        }
        // the pit in front: a low kerb, a grate, what the mobs left
        float bz = 1.0f;
        box(p, stone, kx - 0.44f, 0.15f, bz, 0.1f, 0.3f, 0.98f);
        box(p, stone, kx, 0.15f, bz + 0.44f, 0.98f, 0.3f, 0.1f);
        box(p, Material.BLACK_CONCRETE, kx, 0.07f, bz, 0.78f, 0.01f, 0.78f);
        Material drop = switch (quarry == null ? QuarryType.BONE : quarry) {
            case STRING -> Material.STRING;
            case GUNPOWDER -> Material.GUNPOWDER;
            case ROTTEN_FLESH -> Material.ROTTEN_FLESH;
            default -> Material.BONE;
        };
        item(p, drop, kx - 0.12f, 0.1f, bz + 0.05f, 0.28f, new Quaternionf().rotateX((float) (-Math.PI / 2)).rotateZ(0.5f));
        item(p, drop, kx + 0.18f, 0.1f, bz - 0.15f, 0.24f, new Quaternionf().rotateX((float) (-Math.PI / 2)).rotateZ(-0.9f));
        item(p, drop, kx + 0.05f, 0.1f, bz + 0.25f, 0.22f, new Quaternionf().rotateX((float) (-Math.PI / 2)).rotateZ(2.1f));
        for (int i = 0; i < 3; i++) {
            block(p, "", "minecraft:iron_bars[north=true,south=true]", kx - 0.25f + i * 0.25f, 0.33f, bz, 0.3f, 0.06f, 0.92f, false);
        }
        if (quarry == QuarryType.STRING) {
            block(p, "", "minecraft:cobweb", kx - 0.3f, 1.0f, kz + 0.52f, 0.32f, 0.32f, 0.32f, false);
        }
        box(p, Material.DARK_OAK_FENCE, kx + 0.36f, 0.62f, bz + 0.36f, 0.07f, 1.0f, 0.07f);
        block(p, "", quarry == QuarryType.ROTTEN_FLESH ? "minecraft:zombie_head" : quarry == QuarryType.GUNPOWDER
                ? "minecraft:creeper_head" : "minecraft:skeleton_skull", kx + 0.36f, 1.25f, bz + 0.36f, 0.4f, 0.4f, 0.4f, false);
        // the grinder: a dark frame over the core, two blades turning under its hood
        Material frame = t == 0 ? Material.STRIPPED_DARK_OAK_LOG : t == 1 ? Material.POLISHED_DEEPSLATE
                : t == 2 ? Material.WAXED_CUT_COPPER : Material.POLISHED_BLACKSTONE;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, frame, sx * 0.4f, 0.88f, sz * 0.4f, 0.1f, 1.64f, 0.1f);
            }
        }
        box(p, frame, 0f, 1.74f, 0f, 0.92f, 0.08f, 0.92f);
        boxR(p, stone, 0f, 1.88f, 0f, 0.62f, 0.2f, 0.62f, new Quaternionf().rotateY((float) Math.PI / 4));
        box(p, Material.CHAIN, 0f, 1.5f, 0f, 0.06f, 0.4f, 0.06f);
        Material blade = t >= 3 ? Material.GOLDEN_SWORD : t == 2 ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
        for (int i = 0; i < 2; i++) {
            spin(p, "stone", blade, 0f, 1.3f, 0f, 0.66f, 0.66f, 0.66f,
                    new Quaternionf().rotateY((float) (i * Math.PI / 2)).rotateX((float) (-Math.PI / 2)).rotateZ((float) (Math.PI / 4)));
        }
        box(p, Material.POLISHED_BLACKSTONE, 0f, 0.13f, 0f, 0.6f, 0.14f, 0.6f);
        box(p, Material.BLACK_CONCRETE, 0f, 0.205f, 0f, 0.44f, 0.01f, 0.44f);
        if (t >= 1) {
            light(p, t, 0.3f, 1.52f, 0.3f, 0.26f);
        }
        if (t >= 3) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    boxR(p, Material.GOLD_BLOCK, sx * 0.4f, 1.84f, sz * 0.4f, 0.12f, 0.12f, 0.12f,
                            new Quaternionf().rotateY((float) Math.PI / 4));
                }
            }
        }
        lamp(p, 0f, 2.05f, 0f);
    }

    /** Eight staves round (x, z) from {@code y0} to {@code y1}, apothem {@code a}, {@code thick} deep. */
    private static void octagonAt(List<PropBodies.Part> p, Material skin, float x, float z, float y0, float y1, float a,
                                  float thick) {
        float side = 2f * a * (float) Math.tan(Math.PI / 8) + 0.02f;
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4;
            boxR(p, skin, x + (float) Math.sin(th) * a, (y0 + y1) / 2f, z + (float) Math.cos(th) * a, side, y1 - y0, thick,
                    new Quaternionf().rotateY((float) th));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the slim rig (one column, no shell): a lattice tower with its own little sheave and drill
    // ------------------------------------------------------------------------------------------------

    static void slim(List<PropBodies.Part> p, int level) {
        int t = PropBodies.quarryTier(level);
        float top = 2.35f + 0.15f * t;
        float w0 = 0.4f;
        float w1 = 0.2f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                strut(p, LEG[t], sx * w0, 0.0f, sz * w0, sx * w1, top, sz * w1, 0.09f);
            }
        }
        float prevY = 0.05f;
        for (float gy : new float[]{0.9f, 1.75f}) {
            float w = w0 - (w0 - w1) * gy / top;
            box(p, GIRT[t], 0f, gy, -w, 2 * w + 0.05f, 0.06f, 0.06f);
            box(p, GIRT[t], 0f, gy, w, 2 * w + 0.05f, 0.06f, 0.06f);
            box(p, GIRT[t], -w, gy, 0f, 0.06f, 0.06f, 2 * w + 0.05f);
            box(p, GIRT[t], w, gy, 0f, 0.06f, 0.06f, 2 * w + 0.05f);
            float wp = w0 - (w0 - w1) * prevY / top;
            strut(p, LEG[t], -wp, prevY, -wp, w, gy, -w, 0.04f);
            strut(p, LEG[t], wp, prevY, -wp, -w, gy, -w, 0.04f);
            prevY = gy;
        }
        box(p, GIRT[t], 0f, top + 0.03f, 0f, 0.5f, 0.06f, 0.5f);
        float r = 0.24f;
        for (int s = -1; s <= 1; s += 2) {
            box(p, GIRT[t], 0f, top + 0.17f, s * 0.12f, 0.07f, 0.3f, 0.05f);
        }
        sheave(p, t, 0f, top + 0.33f, 0f, r);
        block(p, "rope", "minecraft:chain[axis=y]", 0f, (top + 1.46f) / 2f, 0f, 0.45f, top - 1.46f, 0.45f, false);
        block(p, "drill", "minecraft:piston[facing=down]", 0f, 1.33f, 0f, 0.26f, 0.24f, 0.26f, false);
        block(p, "drill", drillBit(t), 0f, 1.12f, 0f, 0.26f, 0.28f, 0.26f, t >= 3);
        if (t >= 1) {
            block(p, "", "minecraft:lantern[hanging=true]", 0.16f, top - 0.2f, 0.16f, 0.26f, 0.26f, 0.26f, true);
        }
        lamp(p, w1, top + 0.12f, -w1);
    }

    // ------------------------------------------------------------------------------------------------
    // shared pieces
    // ------------------------------------------------------------------------------------------------

    /** A hanging light: a lantern, from the copper tier a lit copper bulb on a short chain. */
    private static void light(List<PropBodies.Part> p, int t, float x, float y, float z, float size) {
        if (t >= 2) {
            block(p, "", "minecraft:chain[axis=y]", x, y + size * 0.55f, z, 0.4f, size * 0.5f, 0.4f, false);
            block(p, "", "minecraft:waxed_copper_bulb[lit=true]", x, y, z, size * 0.66f, size * 0.66f, size * 0.66f, true);
        } else {
            block(p, "", "minecraft:lantern[hanging=true]", x, y, z, size, size, size, true);
        }
    }

    /** The orange lip on the chute (where the belt starts) and its spout. */
    private static void chute(List<PropBodies.Part> p) {
        p.add(new PropBodies.Part("", Material.ORANGE_CONCRETE.createBlockData(), null, new Vector3f(0, 1.03f, 1.44f),
                new Vector3f(0.72f, 0.07f, 0.08f), new Quaternionf(), true));
        p.add(new PropBodies.Part("", Material.SMOOTH_STONE_SLAB.createBlockData(), null, new Vector3f(0, 0.62f, 1.72f),
                new Vector3f(0.5f, 0.06f, 0.62f), new Quaternionf().rotateX(0.42f), false));
    }

    /**
     * A sheave in the x-y plane: the rim stands still (it looks the same at every angle), the spokes turn
     * ({@code reel}: a quarter turn a second round z).
     */
    private static void sheave(List<PropBodies.Part> p, int t, float x, float y, float z, float r) {
        Material rim = t == 0 ? Material.DARK_OAK_PLANKS : t == 1 ? Material.IRON_BLOCK : t == 2
                ? Material.WAXED_COPPER_BLOCK : Material.GOLD_BLOCK;
        Material spoke = t == 0 ? Material.SPRUCE_PLANKS : t == 1 ? Material.POLISHED_DEEPSLATE : t == 2
                ? Material.WAXED_CUT_COPPER : Material.POLISHED_BLACKSTONE;
        float seg = 2f * r * (float) Math.tan(Math.PI / 8) + 0.02f;
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4;
            boxR(p, rim, x + (float) Math.cos(th) * r, y + (float) Math.sin(th) * r, z, 0.07f, seg, 0.08f,
                    new Quaternionf().rotateZ((float) th));
        }
        for (int i = 0; i < 4; i++) {
            spin(p, "reel", spoke, x, y, z, 0.05f, 2f * r - 0.04f, 0.05f, new Quaternionf().rotateZ((float) (i * Math.PI / 4)));
        }
        box(p, rim, x, y, z, 0.12f, 0.12f, 0.12f);
    }

    /** A straight piece from one point to another (legs, braces, cables), drawn as a block state. */
    private static void strut(List<PropBodies.Part> p, String state, float x0, float y0, float z0, float x1, float y1,
                              float z1, float thick) {
        Vector3f d = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        float length = d.length();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.normalize(new Vector3f()));
        p.add(new PropBodies.Part("", Bukkit.createBlockData(state), null,
                new Vector3f((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f), new Vector3f(thick, length, thick), q, false));
    }

    private static void strutM(List<PropBodies.Part> p, Material m, float x0, float y0, float z0, float x1, float y1,
                               float z1, float thick) {
        strut(p, m.createBlockData().getAsString(), x0, y0, z0, x1, y1, z1, thick);
    }
}
