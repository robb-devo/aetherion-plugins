package de.aetherion.guilds.logistics;

import de.aetherion.guilds.structure.Props;
import de.aetherion.guilds.structure.StructureType;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The compact buildings (2x2 machines and hut, the 4x2 Hub, the 3x2 Dock). Same machine space as
 * {@link PropBodies}: origin = top centre of the anchor's ground block, x right, y up, z toward the front. The
 * anchor is the back-right cell (2x2: cells x -1..0, z 0..1), so the chute keeps its old cell (0,1,1) and the
 * belt still starts at (0,1,2).
 *
 * <p>One look for the whole island: tuff and stone plinths, calcite plaster in dark timber, slate and copper
 * roofs whose patina grows with the tier, a warm light in every building.
 */
final class CompactBodies {

    private CompactBodies() {
    }

    static boolean handles(String templateId) {
        return templateId != null && templateId.startsWith(Props.V2);
    }

    // 2x2 centre (machine space)
    private static final float CX = -0.5f;
    private static final float CZ = 0.5f;

    /** Centre of the footprint in machine space (x, z). */
    static float[] centre(String templateId) {
        if (Props.DOCK_V2.equals(templateId)) {
            return new float[]{0f, CZ};
        }
        return new float[]{CX, CZ};
    }

    static float height(StructureType type, int level) {
        return switch (type) {
            // above the sails' highest sweep and the spires, so the status tag never sits in them
            case MILL -> level >= 2 ? 5.8f : 5.4f;
            case FORGE -> 4.3f;
            case DEPOT -> 2.6f;
            case STORAGE_HUT -> hutWall(level) + 1.3f;
            case WORKSHOP -> 6.2f;
            case DOCK -> 6.0f;
            default -> 3f;
        };
    }

    static List<PropBodies.Part> body(StructureType type, int level) {
        List<PropBodies.Part> p = new ArrayList<>();
        switch (type) {
            case MILL -> mill(p, level);
            case FORGE -> forge(p, level);
            case STORAGE_HUT -> hut(p, level);
            case WORKSHOP -> hub(p, level);
            case DOCK -> dock(p);
            case DEPOT -> depot(p, level);
            default -> {
            }
        }
        return p;
    }

    // ------------------------------------------------------------------------------------------------
    // Mill: a white smock tower on a tuff plinth, a hooded cap, four lattice sails on the front.
    // Twin-Stone Mill: taller, a slate cap, the fantail and a sack hoist.
    // ------------------------------------------------------------------------------------------------

    private static void mill(List<PropBodies.Part> p, int level) {
        boolean twin = level >= 2;
        // plinth: L-shaped, the chute sits in the open front-right bay under a timber lintel
        box(p, Material.TUFF_BRICKS, CX, 0.45f, 0.0f, 1.96f, 0.9f, 0.98f);
        box(p, Material.TUFF_BRICKS, -1.0f, 0.45f, 1.0f, 0.96f, 0.9f, 0.98f);
        box(p, Material.POLISHED_TUFF, CX, 0.95f, 0.0f, 2.06f, 0.12f, 1.06f);
        box(p, Material.POLISHED_TUFF, -1.0f, 0.95f, 1.02f, 1.06f, 0.12f, 1.02f);
        box(p, Material.SPRUCE_PLANKS, 0f, 0.98f, 1.0f, 0.98f, 0.08f, 0.98f);
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", 0f, 0.9f, 1.44f, 1.0f, 0.16f, 0.14f);
        // the tower: eight plaster boards leaning in, a dark curb under the cap
        float y0 = 1.0f;
        float y1 = twin ? 3.75f : 3.45f;
        float a0 = 0.93f;
        float a1 = 0.70f;
        float h = y1 - y0;
        float tilt = (float) Math.atan2(a0 - a1, h);
        octagon(p, Material.CALCITE, y0, y1, a0, a1, 0.1f);
        float curbA = a1 + 0.05f;
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4;
            boxR(p, Material.DARK_OAK_PLANKS, CX + (float) Math.sin(th) * curbA, y1 - 0.08f, CZ + (float) Math.cos(th) * curbA,
                    2f * curbA * (float) Math.tan(Math.PI / 8) + 0.05f, 0.16f, 0.08f, new Quaternionf().rotateY((float) th));
        }
        // windows: one high on the front, one on each side
        window(p, 0, y0 + h * 0.66f, a0 - (a0 - a1) * 0.66f, tilt);
        window(p, 2, y0 + h * 0.4f, a0 - (a0 - a1) * 0.4f, tilt);
        window(p, 6, y0 + h * 0.4f, a0 - (a0 - a1) * 0.4f, tilt);
        // the cap: a hooded gable running front to back
        float eave = y1 + 0.02f;
        float half = a1 + 0.16f;
        float len = 2f * a1 + 0.36f;
        Material roof = twin ? Material.DEEPSLATE_TILES : Material.DARK_OAK_PLANKS;
        float plane = half * (float) Math.sqrt(2) + 0.1f;
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, roof, CX + s * half / 2f, eave + half / 2f, CZ, plane, 0.1f, len,
                    new Quaternionf().rotateZ(-s * (float) Math.PI / 4));
        }
        float dia = (half - 0.02f) * (float) Math.sqrt(2);
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, Material.SPRUCE_PLANKS, CX, eave, CZ + s * (a1 + 0.1f), dia, dia, 0.08f,
                    new Quaternionf().rotateZ((float) Math.PI / 4));
        }
        state(p, "minecraft:stripped_dark_oak_log[axis=z]", CX, eave + half + 0.04f, CZ, 0.13f, 0.13f, len + 0.06f);
        // windshaft and the sails (they turn about the hub: every piece is centred on it)
        float hubY = eave + 0.3f;
        float hubZ = CZ + a1 + 0.5f;
        state(p, "minecraft:stripped_dark_oak_log[axis=z]", CX, hubY, CZ + a1 + 0.22f, 0.2f, 0.2f, 0.62f);
        float span = twin ? 4.2f : 3.9f;
        for (int i = 0; i < 2; i++) {
            Quaternionf q = new Quaternionf().rotateZ((float) (Math.PI / 4 + i * Math.PI / 2));
            spin(p, "sails", Material.STRIPPED_DARK_OAK_LOG, CX, hubY, hubZ, 0.1f, span, 0.1f, q);
            spin(p, "sails", Material.WHITE_WOOL, CX, hubY, hubZ + 0.06f, 0.5f, span - 0.7f, 0.03f, q);
            spin(p, "sails", Material.LADDER, CX, hubY, hubZ + 0.1f, 0.56f, span - 0.6f, 1.0f, q);
        }
        box(p, Material.DARK_OAK_PLANKS, CX, hubY, hubZ + 0.16f, 0.3f, 0.3f, 0.12f);
        if (twin) {
            // the fantail at the back keeps the cap in the wind
            state(p, "minecraft:stripped_dark_oak_log[axis=z]", CX, eave + 0.35f, CZ - a1 - 0.35f, 0.1f, 0.1f, 0.7f);
            for (int i = 0; i < 3; i++) {
                spin(p, "wheel", Material.WHITE_WOOL, CX, eave + 0.35f, CZ - a1 - 0.62f, 0.04f, 0.9f, 0.16f,
                        new Quaternionf().rotateX((float) (i * Math.PI / 3)));
            }
            box(p, Material.WAXED_CUT_COPPER, CX, eave + half + 0.16f, CZ, 0.2f, 0.12f, 0.2f);
            // the sack hoist on the right: a beam, a rope, a sack that goes up and down while it grinds
            state(p, "minecraft:stripped_dark_oak_log[axis=x]", CX + a1 + 0.32f, y1 - 0.3f, CZ + 0.2f, 0.62f, 0.12f, 0.12f);
            block(p, "rope", "minecraft:chain[axis=y]", CX + a1 + 0.5f, y1 - 0.75f, CZ + 0.2f, 0.7f, 0.8f, 0.7f, false);
            block(p, "drill", "minecraft:white_wool", CX + a1 + 0.5f, y1 - 1.35f, CZ + 0.2f, 0.3f, 0.36f, 0.26f, false);
        }
        // ledge dressing: a lantern, flour sacks, a barrel
        block(p, "", "minecraft:lantern[hanging=false]", CX - 0.86f, 1.38f, CZ + 0.86f, 0.62f, 0.62f, 0.62f, true);
        boxR(p, Material.WHITE_WOOL, CX - 0.86f, 1.17f, CZ - 0.84f, 0.34f, 0.32f, 0.28f, new Quaternionf().rotateY(0.4f));
        boxR(p, Material.WHITE_WOOL, CX - 0.72f, 1.13f, CZ - 0.98f, 0.26f, 0.24f, 0.22f, new Quaternionf().rotateY(-0.3f));
        box(p, Material.BARREL, CX + 0.85f, 1.22f, CZ - 0.85f, 0.4f, 0.42f, 0.4f);
        lamp(p, CX, eave + half + (twin ? 0.36f : 0.22f), CZ - a1 + 0.05f);
        ports2x2(p);
    }

    /** Eight boards leaning in from apothem {@code a0} at {@code y0} to {@code a1} at {@code y1}, round the 2x2. */
    private static void octagon(List<PropBodies.Part> p, Material skin, float y0, float y1, float a0, float a1, float t) {
        float h = y1 - y0;
        float r = (a0 + a1) / 2f;
        float tilt = (float) Math.atan2(a0 - a1, h);
        float side = 2f * a0 * (float) Math.tan(Math.PI / 8) + 0.05f;
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4;
            boxR(p, skin, CX + (float) Math.sin(th) * r, y0 + h / 2f, CZ + (float) Math.cos(th) * r,
                    side, h / (float) Math.cos(tilt), t, new Quaternionf().rotateY((float) th).rotateX(-tilt));
        }
    }

    /** A little framed window on board {@code i} of the mill tower (0 = front, 2 = right, 6 = left). */
    private static void window(List<PropBodies.Part> p, int i, float y, float a, float tilt) {
        double th = i * Math.PI / 4;
        Quaternionf q = new Quaternionf().rotateY((float) th).rotateX(-tilt);
        float out = a + 0.05f;
        boxR(p, Material.SPRUCE_PLANKS, CX + (float) Math.sin(th) * out, y, CZ + (float) Math.cos(th) * out, 0.34f, 0.42f, 0.04f, q);
        float glass = a + 0.075f;
        boxR(p, Material.BLACK_STAINED_GLASS, CX + (float) Math.sin(th) * glass, y, CZ + (float) Math.cos(th) * glass, 0.24f, 0.3f, 0.02f, q);
    }

    // ------------------------------------------------------------------------------------------------
    // Forge: a brick smithy in the back row (the blast furnace right over the chute, the hearth fire and its
    // chimney on the left), the anvil yard in front. Bellows Forge: a second stack and a copper hood.
    // ------------------------------------------------------------------------------------------------

    private static void forge(List<PropBodies.Part> p, int level) {
        boolean bellows = level >= 2;
        box(p, Material.DEEPSLATE_BRICKS, CX, 0.06f, 0.0f, 2.0f, 0.12f, 1.0f);
        box(p, Material.DEEPSLATE_TILES, -1.0f, 0.05f, 1.0f, 1.0f, 0.1f, 1.0f);
        // back wall: a deepslate footing, brick above, a barred window
        box(p, Material.DEEPSLATE_BRICKS, CX, 0.36f, -0.36f, 1.98f, 0.5f, 0.28f);
        box(p, Material.BRICKS, CX, 1.24f, -0.36f, 1.96f, 1.28f, 0.26f);
        box(p, Material.BLACK_STAINED_GLASS, -0.05f, 1.3f, -0.5f, 0.42f, 0.4f, 0.02f);
        block(p, "", "minecraft:iron_bars[east=true,west=true]", -0.05f, 1.3f, -0.51f, 0.46f, 0.44f, 0.6f, false);
        // the furnace mass behind the chute: the blast furnace sits right over it
        box(p, Material.BRICKS, 0f, 1.0f, 0.06f, 0.96f, 1.8f, 0.6f);
        block(p, "furnace", "minecraft:blast_furnace[facing=south,lit=false]", 0f, 1.4f, 0.42f, 0.8f, 0.8f, 0.16f, false);
        box(p, Material.POLISHED_BLACKSTONE, 0f, 1.88f, 0.42f, 0.98f, 0.12f, 0.2f);
        box(p, Material.POLISHED_BLACKSTONE, 0f, 0.93f, 0.42f, 0.9f, 0.06f, 0.18f);
        // the hearth bay on the left: a fire under a stone hood, the chimney above
        box(p, Material.BRICKS, -1.42f, 1.0f, 0.02f, 0.16f, 1.8f, 0.96f);
        block(p, "", "minecraft:campfire[lit=true,facing=south]", -1.0f, 0.42f, 0.05f, 0.6f, 0.6f, 0.6f, true);
        box(p, Material.POLISHED_DEEPSLATE, -1.0f, 0.2f, 0.05f, 0.82f, 0.18f, 0.76f);
        boxR(p, bellows ? Material.WAXED_CUT_COPPER : Material.POLISHED_DEEPSLATE, -1.0f, 1.55f, 0.22f, 0.92f, 0.5f, 0.12f,
                new Quaternionf().rotateX(-0.55f));
        box(p, Material.POLISHED_DEEPSLATE, -1.0f, 1.86f, 0.0f, 0.96f, 0.14f, 0.8f);
        box(p, Material.BRICKS, -1.0f, 2.85f, -0.1f, 0.66f, 2.0f, 0.66f);
        box(p, Material.POLISHED_BLACKSTONE, -1.0f, 2.2f, -0.1f, 0.8f, 0.1f, 0.8f);
        box(p, Material.BRICKS, -1.0f, 3.95f, -0.1f, 0.74f, 0.2f, 0.74f);
        box(p, Material.POLISHED_BLACKSTONE, -1.0f, 4.1f, -0.1f, 0.82f, 0.1f, 0.82f);
        if (bellows) {
            box(p, Material.BRICKS, 0f, 2.55f, -0.2f, 0.5f, 1.4f, 0.5f);
            box(p, Material.BRICKS, 0f, 3.3f, -0.2f, 0.58f, 0.16f, 0.58f);
            box(p, Material.POLISHED_BLACKSTONE, 0f, 3.42f, -0.2f, 0.64f, 0.08f, 0.64f);
        }
        // roof over the furnace: slate, pitched toward the yard
        boxR(p, Material.DEEPSLATE_TILES, 0.02f, 2.05f, 0.05f, 1.12f, 0.1f, 1.2f, new Quaternionf().rotateX(0.32f));
        // the bellows by the fire (it breathes while the forge works)
        block(p, "bellows", "minecraft:brown_terracotta", -1.0f, 0.86f, -0.28f, 0.6f, 0.3f, 0.36f, false);
        box(p, Material.SPRUCE_SLAB, -1.0f, 1.04f, -0.28f, 0.68f, 0.06f, 0.42f);
        // the anvil yard
        state(p, "minecraft:stripped_oak_log[axis=y]", -1.0f, 0.32f, 1.0f, 0.5f, 0.44f, 0.5f);
        block(p, "anvil", "minecraft:anvil[facing=west]", -1.0f, 0.82f, 1.0f, 0.62f, 0.62f, 0.62f, false);
        block(p, "", "minecraft:water_cauldron[level=3]", -1.3f, 0.27f, 1.3f, 0.42f, 0.42f, 0.42f, false);
        box(p, Material.COAL_BLOCK, -0.66f, 0.18f, 1.34f, 0.3f, 0.16f, 0.26f);
        // tools on the hearth wall
        item(p, Material.IRON_PICKAXE, -1.33f, 1.3f, 0.6f, 0.42f, new Quaternionf().rotateY((float) Math.PI / 2).rotateZ(0.6f));
        item(p, Material.IRON_SHOVEL, -1.33f, 1.32f, 0.85f, 0.42f, new Quaternionf().rotateY((float) Math.PI / 2).rotateZ(-0.2f));
        block(p, "", "minecraft:lantern[hanging=true]", -0.5f, 1.66f, 0.5f, 0.5f, 0.5f, 0.5f, true);
        lamp(p, 0.36f, 2.34f, -0.36f);
        ports2x2(p);
    }

    /** The forge hammer (an item display MachineFx swings over the anvil). */
    static PropBodies.Part hammer() {
        return new PropBodies.Part("hammer", null, new ItemStack(Material.MACE), new Vector3f(-1.0f, 1.62f, 1.0f),
                new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf(), false);
    }

    // ------------------------------------------------------------------------------------------------
    // Storage Hut: an open timber store, a storey of crates per tier (Hut, Storage Loft, Warehouse)
    // ------------------------------------------------------------------------------------------------

    private static final float[] FLOOR = {0f, 2.1f, 4.0f, 5.9f};

    static float hutWall(int level) {
        return FLOOR[Math.max(1, Math.min(3, level))];
    }

    private static void hut(List<PropBodies.Part> p, int level) {
        int storeys = Math.max(1, Math.min(3, level));
        float w = hutWall(level);
        boolean warehouse = storeys >= 3;
        Material post = warehouse ? Material.STRIPPED_DARK_OAK_LOG : Material.STRIPPED_SPRUCE_LOG;
        String beam = warehouse ? "stripped_dark_oak_log" : "stripped_spruce_log";
        // stone footing and the ground floor
        box(p, Material.STONE_BRICKS, CX, 0.1f, CZ, 2.0f, 0.2f, 2.0f);
        box(p, Material.SPRUCE_PLANKS, CX, 0.21f, CZ, 1.8f, 0.04f, 1.8f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, post, CX + sx * 0.88f, w / 2f + 0.1f, CZ + sz * 0.88f, 0.22f, w - 0.2f, 0.22f);
            }
        }
        // back and side walls in plaster (brick on the warehouse's ground floor), the front open on the stock
        box(p, Material.CALCITE, CX, w / 2f + 0.1f, CZ - 0.9f, 1.62f, w - 0.2f, 0.1f);
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.CALCITE, CX + s * 0.9f, w / 2f + 0.1f, CZ, 0.1f, w - 0.2f, 1.62f);
        }
        if (warehouse) {
            box(p, Material.BRICKS, CX, FLOOR[1] / 2f + 0.1f, CZ - 0.92f, 1.64f, FLOOR[1] - 0.2f, 0.1f);
            for (int s = -1; s <= 1; s += 2) {
                box(p, Material.BRICKS, CX + s * 0.92f, FLOOR[1] / 2f + 0.1f, CZ, 0.1f, FLOOR[1] - 0.2f, 1.64f);
            }
        }
        for (int k = 1; k <= storeys; k++) {
            float top = FLOOR[k];
            float mid = (FLOOR[k - 1] + top) / 2f + 0.1f;
            // the floor above and its beams on the front and back
            box(p, Material.SPRUCE_PLANKS, CX, top, CZ, 1.86f, 0.12f, 1.86f);
            state(p, "minecraft:" + beam + "[axis=x]", CX, top - 0.02f, CZ + 0.93f, 2.0f, 0.18f, 0.16f);
            state(p, "minecraft:" + beam + "[axis=x]", CX, top - 0.02f, CZ - 0.95f, 2.0f, 0.16f, 0.1f);
            // a shuttered window on each side, a brace on the back
            for (int s = -1; s <= 1; s += 2) {
                block(p, "", "minecraft:spruce_trapdoor[facing=" + (s > 0 ? "west" : "east") + ",half=bottom,open=true]",
                        CX + s * 0.75f, mid + 0.15f, CZ + 0.1f, 0.62f, 0.7f, 0.62f, false);
            }
            if (k > 1 || !warehouse) {
                boxR(p, post, CX, mid, CZ - 0.97f, 0.1f, (top - FLOOR[k - 1]) * 1.12f, 0.06f,
                        new Quaternionf().rotateZ(k % 2 == 0 ? 0.62f : -0.62f));
            }
            if (k > 1) {
                // the loft rail across the open front
                box(p, Material.SPRUCE_PLANKS, CX, FLOOR[k - 1] + 0.62f, CZ + 0.92f, 1.6f, 0.07f, 0.07f);
                box(p, Material.SPRUCE_PLANKS, CX, FLOOR[k - 1] + 0.36f, CZ + 0.92f, 0.07f, 0.5f, 0.07f);
            }
        }
        if (warehouse) {
            // copper bands round the warehouse
            box(p, Material.WAXED_CUT_COPPER, CX, FLOOR[1] - 0.2f, CZ - 0.97f, 1.92f, 0.1f, 0.04f);
            box(p, Material.WAXED_CUT_COPPER, CX, FLOOR[2] - 0.2f, CZ - 0.97f, 1.92f, 0.1f, 0.04f);
        }
        // the roof: ridge front to back, a loading door and the hoist beam out of the front gable
        float half = warehouse ? 1.22f : 1.12f;
        Material roof = warehouse ? Material.DEEPSLATE_TILES : storeys == 2 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
        float plane = half * (float) Math.sqrt(2) + 0.08f;
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, roof, CX + s * half / 2f, w + half / 2f + 0.04f - (half - 1.12f), CZ, plane, 0.1f, warehouse ? 2.46f : 2.26f,
                    new Quaternionf().rotateZ(-s * (float) Math.PI / 4));
        }
        if (warehouse) {
            // the warehouse crane: a jib out of the right wall at the top storey, its load bobbing
            box(p, Material.STRIPPED_DARK_OAK_LOG, CX + 1.02f, FLOOR[2] + 0.9f, CZ + 0.55f, 0.16f, 1.7f, 0.16f);
            state(p, "minecraft:stripped_dark_oak_log[axis=x]", CX + 1.42f, FLOOR[3] - 0.18f, CZ + 0.55f, 0.95f, 0.14f, 0.14f);
            boxR(p, Material.STRIPPED_DARK_OAK_LOG, CX + 1.22f, FLOOR[3] - 0.5f, CZ + 0.55f, 0.08f, 0.7f, 0.08f,
                    new Quaternionf().rotateZ(0.8f));
            block(p, "rope", "minecraft:chain[axis=y]", CX + 1.78f, FLOOR[3] - 0.62f, CZ + 0.55f, 0.6f, 0.8f, 0.6f, false);
            block(p, "drill", "minecraft:spruce_planks", CX + 1.78f, FLOOR[3] - 1.2f, CZ + 0.55f, 0.4f, 0.4f, 0.4f, false);
        }
        float dia = (half - 0.08f) * (float) Math.sqrt(2);
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, Material.CALCITE, CX, w + 0.04f, CZ + s * 0.9f, dia, dia, 0.08f, new Quaternionf().rotateZ((float) Math.PI / 4));
        }
        state(p, "minecraft:" + beam + "[axis=z]", CX, w + half + 0.1f, CZ, 0.14f, 0.14f, 2.32f);
        box(p, Material.SPRUCE_PLANKS, CX, w + 0.4f, CZ + 0.95f, 0.46f, 0.56f, 0.04f);
        box(p, Material.BLACK_STAINED_GLASS, CX, w + 0.38f, CZ + 0.97f, 0.34f, 0.44f, 0.02f);
        state(p, "minecraft:" + beam + "[axis=z]", CX, w + 0.82f, CZ + 1.1f, 0.12f, 0.12f, 0.92f);
        block(p, "rope", "minecraft:chain[axis=y]", CX, w + 0.3f, CZ + 1.44f, 0.8f, 0.9f, 0.8f, false);
        block(p, "drill", "minecraft:barrel[facing=up]", CX, w - 0.38f, CZ + 1.44f, 0.42f, 0.42f, 0.42f, false);
        // the name board over the opening, a lantern by it
        box(p, Material.DARK_OAK_PLANKS, CX, FLOOR[1] - 0.36f, CZ + 0.99f, 0.96f, 0.3f, 0.05f);
        item(p, Material.BARREL, CX, FLOOR[1] - 0.36f, CZ + 1.03f, 0.22f, new Quaternionf());
        block(p, "", "minecraft:lantern[hanging=true]", CX + 0.86f, FLOOR[1] - 0.34f, CZ + 1.12f, 0.46f, 0.46f, 0.46f, true);
        if (warehouse) {
            block(p, "", "minecraft:lightning_rod[facing=up]", CX, w + half + 0.6f, CZ - 0.7f, 0.6f, 0.9f, 0.6f, false);
            block(p, "", "minecraft:lantern[hanging=true]", CX - 0.86f, FLOOR[2] - 0.34f, CZ + 1.12f, 0.46f, 0.46f, 0.46f, true);
        }
        lamp(p, CX, w + half + 0.27f, CZ + 1.0f);
        // belts end here from any side: green lips on every wall
        lip(p, CX, 0.32f, CZ - 1.03f, 1.0f, 0.06f);
        lip(p, CX, 0.32f, CZ + 1.03f, 1.0f, 0.06f);
        lip(p, CX - 1.03f, 0.32f, CZ, 0.06f, 1.0f);
        lip(p, CX + 1.03f, 0.32f, CZ, 0.06f, 1.0f);
    }

    /** Crates stack 2x2, three high per storey, the lowest storey first. */
    static List<PropBodies.Part> hutCrates(int count, int level) {
        List<PropBodies.Part> p = new ArrayList<>();
        int max = hutSlots(level);
        for (int i = 0; i < Math.min(count, max); i++) {
            int storey = i / 12;
            int inStorey = i % 12;
            int layer = inStorey / 4;
            int cell = inStorey % 4;
            float size = storey == 0 ? 0.62f : 0.56f;
            float x = CX + (cell % 2 == 0 ? -0.38f : 0.38f);
            float z = CZ + (cell / 2 == 0 ? -0.36f : 0.38f);
            Material crate = (i % 4 == 0) ? Material.BARREL : (i % 4 == 1) ? Material.STRIPPED_OAK_WOOD
                    : (i % 4 == 2) ? Material.SPRUCE_PLANKS : Material.COMPOSTER;
            float base = FLOOR[storey] + (storey == 0 ? 0.23f : 0.06f);
            p.add(new PropBodies.Part("crate", crate.createBlockData(), null,
                    new Vector3f(x, base + size / 2f + layer * (size + 0.01f), z), new Vector3f(size, size, size),
                    new Quaternionf().rotateY((float) ((i * 0.37) % 0.3 - 0.15)), false));
        }
        return p;
    }

    static int hutSlots(int level) {
        return 12 * Math.max(1, Math.min(3, level));
    }

    // ------------------------------------------------------------------------------------------------
    // Hub (4x2): a market hall under one big copper roof. The back half is the hall (shelves, the map, the
    // crafter), the front half the porch with the real lectern (cell 0,1,1) and the map table. On the ridge a
    // turret: the clock, and in its open lantern the turning gem. Patina grows with the tier; the Grand Hub
    // gets gold and a light.
    // ------------------------------------------------------------------------------------------------

    private static void hub(List<PropBodies.Part> p, int level) {
        boolean hall = level >= 2;
        boolean grand = level >= 3;
        float hx = CX;
        Material trim = grand ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER;
        Material roof = grand ? Material.WAXED_OXIDIZED_CUT_COPPER : hall ? Material.WAXED_WEATHERED_CUT_COPPER
                : Material.WAXED_EXPOSED_CUT_COPPER;
        float floor = 0.24f;
        float eave = 2.5f;
        // dais
        box(p, Material.POLISHED_ANDESITE, hx, floor / 2f, CZ, 4.0f, floor, 2.0f);
        box(p, Material.SMOOTH_STONE, hx, floor + 0.01f, CZ + 0.05f, 3.84f, 0.02f, 1.84f);
        // corner posts on stone feet
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, Material.STRIPPED_DARK_OAK_LOG, hx + sx * 1.86f, (eave + floor) / 2f, CZ + sz * 0.86f, 0.22f, eave - floor, 0.22f);
                box(p, Material.POLISHED_TUFF, hx + sx * 1.86f, floor + 0.12f, CZ + sz * 0.86f, 0.32f, 0.24f, 0.32f);
            }
        }
        // the hall: plaster back wall and side walls, a beam on top all round
        box(p, Material.CALCITE, hx, (eave + floor) / 2f, -0.42f, 3.62f, eave - floor, 0.1f);
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.CALCITE, hx + s * 1.92f, (eave + floor) / 2f, CZ, 0.1f, eave - floor, 1.62f);
            state(p, "minecraft:stripped_dark_oak_log[axis=z]", hx + s * 1.92f, eave, CZ, 0.18f, 0.18f, 2.0f);
            // a timber cross on each side wall
            boxR(p, Material.STRIPPED_DARK_OAK_LOG, hx + s * 1.98f, (eave + floor) / 2f, CZ, 0.06f, 2.3f, 0.1f,
                    new Quaternionf().rotateX(0.72f));
            boxR(p, Material.STRIPPED_DARK_OAK_LOG, hx + s * 1.98f, (eave + floor) / 2f, CZ, 0.06f, 2.3f, 0.1f,
                    new Quaternionf().rotateX(-0.72f));
        }
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", hx, eave, -0.44f, 4.0f, 0.18f, 0.18f);
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", hx, eave, 1.36f, 4.0f, 0.18f, 0.18f);
        // the back wall's timber frame: two posts, a rail, braces
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.STRIPPED_DARK_OAK_LOG, hx + s * 0.62f, (eave + floor) / 2f, -0.49f, 0.14f, eave - floor, 0.06f);
            boxR(p, Material.STRIPPED_DARK_OAK_LOG, hx + s * 1.25f, (eave + floor) / 2f + 0.55f, -0.49f, 0.08f, 1.5f, 0.05f,
                    new Quaternionf().rotateZ(s * 0.7f));
        }
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", hx, 1.3f, -0.49f, 3.7f, 0.12f, 0.05f);
        box(p, Material.SPRUCE_PLANKS, hx, 1.84f, -0.5f, 0.92f, 0.72f, 0.02f);
        box(p, Material.BLACK_STAINED_GLASS, hx, 1.84f, -0.515f, 0.78f, 0.58f, 0.02f);
        // the roof: two copper planes, the gables in plaster, a ridge beam
        float half = 1.15f;
        float plane = half * (float) Math.sqrt(2) + 0.12f;
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, roof, hx, eave + half / 2f + 0.06f, CZ + s * half / 2f, 4.3f, 0.1f, plane,
                    new Quaternionf().rotateX(s * (float) Math.PI / 4));
        }
        float dia = (half - 0.04f) * (float) Math.sqrt(2);
        for (int s = -1; s <= 1; s += 2) {
            boxR(p, Material.CALCITE, hx + s * 1.93f, eave + 0.06f, CZ, 0.06f, dia, dia, new Quaternionf().rotateX((float) Math.PI / 4));
        }
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", hx, eave + half + 0.12f, CZ, 4.4f, 0.14f, 0.14f);
        // the turret on the ridge: a plaster base with the clock, an open lantern with the gem, a spire
        float base = eave + half - 0.1f;
        box(p, Material.CALCITE, hx, base + 0.35f, CZ, 0.86f, 0.7f, 0.86f);
        box(p, trim, hx, base + 0.74f, CZ, 0.98f, 0.08f, 0.98f);
        item(p, Material.CLOCK, hx, base + 0.36f, CZ + 0.44f, 0.5f, new Quaternionf());
        float lantern = base + 0.78f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, trim, hx + sx * 0.38f, lantern + 0.4f, CZ + sz * 0.38f, 0.1f, 0.8f, 0.1f);
            }
        }
        box(p, trim, hx, lantern + 0.84f, CZ, 1.0f, 0.08f, 1.0f);
        box(p, roof, hx, lantern + 1.0f, CZ, 0.78f, 0.24f, 0.78f);
        box(p, roof, hx, lantern + 1.22f, CZ, 0.5f, 0.22f, 0.5f);
        box(p, roof, hx, lantern + 1.42f, CZ, 0.26f, 0.2f, 0.26f);
        if (grand) {
            box(p, Material.GOLD_BLOCK, hx, lantern + 1.58f, CZ, 0.14f, 0.14f, 0.14f);
            block(p, "", "minecraft:end_rod[facing=up]", hx, lantern + 1.95f, CZ, 0.6f, 0.6f, 0.6f, true);
        } else {
            block(p, "", "minecraft:lightning_rod[facing=up]", hx, lantern + 1.85f, CZ, 0.5f, 0.7f, 0.5f, false);
        }
        Quaternionf gem = new Quaternionf().rotateX(0.6155f).rotateZ((float) (Math.PI / 4));
        spin(p, "stone", grand ? Material.YELLOW_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS,
                hx, lantern + 0.4f, CZ, 0.44f, 0.44f, 0.44f, gem);
        p.add(new PropBodies.Part("stone2", null, new ItemStack(hall ? Material.SEA_LANTERN : Material.GLOWSTONE),
                new Vector3f(hx, lantern + 0.4f, CZ), new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf(gem), true));
        // inside the hall: shelves, the map, the crafter, warm copper lights
        box(p, Material.BOOKSHELF, hx - 1.3f, 0.82f, -0.3f, 0.9f, 0.9f, 0.14f);
        box(p, Material.BOOKSHELF, hx - 1.3f, 1.62f, -0.3f, 0.9f, 0.7f, 0.14f);
        item(p, Material.FILLED_MAP, hx, 1.55f, -0.35f, 0.72f, new Quaternionf());
        block(p, "", "minecraft:crafter[orientation=north_up]", hx + 1.3f, 0.6f, -0.12f, 0.66f, 0.66f, 0.66f, false);
        block(p, "", "minecraft:waxed_copper_bulb[lit=true]", hx - 0.5f, 2.15f, -0.3f, 0.26f, 0.26f, 0.26f, true);
        block(p, "", "minecraft:waxed_copper_bulb[lit=true]", hx + 0.5f, 2.15f, -0.3f, 0.26f, 0.26f, 0.26f, true);
        // the porch: the map table left of the lectern, a bench and barrels at the ends, lanterns
        box(p, Material.CARTOGRAPHY_TABLE, -1.0f, floor + 0.38f, 0.92f, 0.66f, 0.76f, 0.6f);
        block(p, "", "minecraft:spruce_stairs[facing=north,half=bottom]", hx - 1.45f, floor + 0.22f, 1.0f, 0.62f, 0.44f, 0.5f, false);
        block(p, "", "minecraft:potted_azalea_bush", hx - 1.86f, floor + 0.32f, 1.36f, 0.62f, 0.62f, 0.62f, false);
        box(p, Material.BARREL, hx + 1.45f, floor + 0.3f, 0.95f, 0.52f, 0.6f, 0.52f);
        block(p, "", "minecraft:potted_flowering_azalea_bush", hx + 1.86f, floor + 0.32f, 1.36f, 0.62f, 0.62f, 0.62f, false);
        block(p, "", "minecraft:lantern[hanging=true]", hx - 1.0f, eave - 0.34f, 1.36f, 0.46f, 0.46f, 0.46f, true);
        block(p, "", "minecraft:lantern[hanging=true]", hx + 1.0f, eave - 0.34f, 1.36f, 0.46f, 0.46f, 0.46f, true);
        if (hall) {
            // the Foreman's Hall: work benches in the hall, banners on the front posts
            box(p, Material.SMITHING_TABLE, hx - 0.5f, floor + 0.36f, -0.12f, 0.62f, 0.72f, 0.56f);
            for (int s = -1; s <= 1; s += 2) {
                box(p, Material.BLUE_WOOL, hx + s * 1.86f, 1.55f, 1.49f, 0.32f, 1.0f, 0.03f);
                box(p, trim, hx + s * 1.86f, 2.06f, 1.5f, 0.38f, 0.05f, 0.05f);
                box(p, Material.YELLOW_WOOL, hx + s * 1.86f, 1.55f, 1.51f, 0.1f, 0.6f, 0.01f);
            }
        }
        if (grand) {
            for (int s = -1; s <= 1; s += 2) {
                block(p, "", "minecraft:amethyst_cluster[facing=up]", hx + s * 1.86f, eave + 0.36f, 1.36f, 0.5f, 0.5f, 0.5f, true);
            }
            box(p, Material.GOLD_BLOCK, hx, eave - 0.12f, 1.47f, 3.6f, 0.06f, 0.04f);
        }
        lamp(p, hx + 1.86f, eave + 0.22f, -0.36f);
    }

    // ------------------------------------------------------------------------------------------------
    // Dock (3x2): a raised pier with a rail, the mooring mast at the back right (crow's nest, lantern,
    // pennant), a cargo crane, the contract board under its own little roof, cargo waiting
    // ------------------------------------------------------------------------------------------------

    private static void dock(List<PropBodies.Part> p) {
        // the deck is the top of the barrier shell: you walk on the planks you see
        float deck = 1.0f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(p, Material.TUFF_BRICKS, sx * 1.3f, 0.43f, CZ + sz * 0.8f, 0.36f, 0.86f, 0.36f);
                box(p, Material.POLISHED_TUFF, sx * 1.3f, 0.05f, CZ + sz * 0.8f, 0.46f, 0.1f, 0.46f);
            }
        }
        // cross bracing under the deck between the piers
        for (int sz = -1; sz <= 1; sz += 2) {
            boxR(p, Material.STRIPPED_SPRUCE_LOG, 0f, 0.48f, CZ + sz * 0.8f, 2.7f, 0.08f, 0.08f,
                    new Quaternionf().rotateZ(0.28f));
            boxR(p, Material.STRIPPED_SPRUCE_LOG, 0f, 0.48f, CZ + sz * 0.8f, 2.7f, 0.08f, 0.08f,
                    new Quaternionf().rotateZ(-0.28f));
        }
        box(p, Material.SPRUCE_PLANKS, 0f, deck - 0.07f, CZ, 3.0f, 0.14f, 2.0f);
        state(p, "minecraft:stripped_spruce_log[axis=x]", 0f, deck - 0.12f, CZ + 0.95f, 3.06f, 0.2f, 0.16f);
        state(p, "minecraft:stripped_spruce_log[axis=x]", 0f, deck - 0.12f, CZ - 0.95f, 3.06f, 0.2f, 0.16f);
        // the rail on the back and the sides (the front is the loading edge)
        for (int sx = -1; sx <= 1; sx++) {
            box(p, Material.STRIPPED_SPRUCE_LOG, sx * 1.42f, deck + 0.32f, CZ - 0.92f, 0.1f, 0.64f, 0.1f);
        }
        box(p, Material.SPRUCE_PLANKS, 0f, deck + 0.6f, CZ - 0.92f, 2.94f, 0.08f, 0.08f);
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.STRIPPED_SPRUCE_LOG, s * 1.42f, deck + 0.32f, CZ + 0.4f, 0.1f, 0.64f, 0.1f);
            box(p, Material.SPRUCE_PLANKS, s * 1.42f, deck + 0.6f, CZ - 0.25f, 0.08f, 0.08f, 1.4f);
        }
        // mooring mast: crow's nest, yard, lantern, pennant (it flutters)
        float mx = 1.05f;
        float mz = CZ - 0.6f;
        box(p, Material.STRIPPED_DARK_OAK_LOG, mx, deck + 2.3f, mz, 0.24f, 4.6f, 0.24f);
        block(p, "", "minecraft:ladder[facing=south]", mx, deck + 1.5f, mz + 0.2f, 0.4f, 2.6f, 0.4f, false);
        box(p, Material.SPRUCE_SLAB, mx, deck + 3.2f, mz, 0.8f, 0.08f, 0.8f);
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.SPRUCE_PLANKS, mx + s * 0.38f, deck + 3.42f, mz, 0.05f, 0.36f, 0.8f);
            box(p, Material.SPRUCE_PLANKS, mx, deck + 3.42f, mz + s * 0.38f, 0.8f, 0.36f, 0.05f);
        }
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", mx - 0.3f, deck + 4.2f, mz, 1.0f, 0.12f, 0.12f);
        block(p, "", "minecraft:lantern[hanging=true]", mx - 0.65f, deck + 3.88f, mz, 0.5f, 0.5f, 0.5f, true);
        p.add(new PropBodies.Part("jib", null, new ItemStack(Material.RED_WOOL), new Vector3f(mx, deck + 4.75f, mz),
                new Vector3f(0.04f, 0.3f, 0.72f), new Quaternionf(), false));
        block(p, "", "minecraft:chain[axis=y]", mx - 0.3f, deck + 3.85f, mz, 0.5f, 0.6f, 0.5f, false);
        // cargo crane at the back left; its load bobs while the dock takes goods
        box(p, Material.STRIPPED_SPRUCE_LOG, -1.15f, deck + 1.1f, mz, 0.2f, 2.2f, 0.2f);
        box(p, Material.SPRUCE_PLANKS, -0.6f, deck + 2.2f, mz, 1.3f, 0.14f, 0.14f);
        boxR(p, Material.STRIPPED_SPRUCE_LOG, -0.92f, deck + 1.9f, mz, 0.08f, 0.7f, 0.08f, new Quaternionf().rotateZ(-0.8f));
        block(p, "rope", "minecraft:chain[axis=y]", -0.1f, deck + 1.82f, mz, 0.6f, 0.7f, 0.6f, false);
        block(p, "drill", "minecraft:stripped_oak_wood", -0.1f, deck + 1.28f, mz, 0.44f, 0.44f, 0.44f, false);
        // the contract board under its own roof
        float bx = -0.9f;
        float bz = CZ + 0.72f;
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.STRIPPED_DARK_OAK_LOG, bx + s * 0.44f, deck + 0.62f, bz, 0.09f, 1.24f, 0.09f);
        }
        box(p, Material.DARK_OAK_PLANKS, bx, deck + 0.82f, bz, 0.9f, 0.6f, 0.05f);
        boxR(p, Material.DARK_OAK_SLAB, bx, deck + 1.3f, bz + 0.04f, 1.08f, 0.06f, 0.3f, new Quaternionf().rotateX(0.35f));
        for (int i = 0; i < 3; i++) {
            item(p, i == 1 ? Material.MAP : Material.PAPER, bx - 0.26f + i * 0.26f, deck + 0.84f + (i == 1 ? 0.04f : -0.02f),
                    bz + 0.04f, 0.3f, new Quaternionf().rotateZ((i - 1) * 0.12f));
        }
        // cargo on the pier
        box(p, Material.BARREL, 0.25f, deck + 0.29f, CZ + 0.62f, 0.5f, 0.58f, 0.5f);
        boxR(p, Material.STRIPPED_OAK_WOOD, 0.85f, deck + 0.25f, CZ + 0.55f, 0.5f, 0.5f, 0.5f, new Quaternionf().rotateY(0.25f));
        box(p, Material.SPRUCE_PLANKS, 0.82f, deck + 0.71f, CZ + 0.55f, 0.42f, 0.42f, 0.42f);
        boxR(p, Material.HAY_BLOCK, 0.35f, deck + 0.25f, CZ - 0.25f, 0.5f, 0.5f, 0.5f, new Quaternionf().rotateY(-0.2f));
        boxR(p, Material.WHITE_WOOL, -0.45f, deck + 0.16f, CZ - 0.15f, 0.34f, 0.32f, 0.28f, new Quaternionf().rotateY(0.5f));
        // bollards at the front edge
        for (int s = -1; s <= 1; s += 2) {
            box(p, Material.STRIPPED_DARK_OAK_LOG, s * 1.3f, deck + 0.18f, CZ + 0.88f, 0.16f, 0.36f, 0.16f);
        }
        lamp(p, mx, deck + 4.42f, mz);
        lip(p, 0f, 0.3f, CZ + 1.04f, 1.2f, 0.06f);
        lip(p, 0f, 0.3f, CZ - 1.04f, 1.2f, 0.06f);
        lip(p, -1.54f, 0.3f, CZ, 0.06f, 0.8f);
        lip(p, 1.54f, 0.3f, CZ, 0.06f, 0.8f);
    }

    // ------------------------------------------------------------------------------------------------
    // Depot (guild islands): a timber crate yard with a little crane
    // ------------------------------------------------------------------------------------------------

    private static void depot(List<PropBodies.Part> p, int level) {
        boolean stack = level >= 2;
        box(p, Material.SPRUCE_PLANKS, CX, 0.06f, CZ, 1.96f, 0.12f, 1.96f);
        state(p, "minecraft:stripped_spruce_log[axis=x]", CX, 0.1f, CZ - 0.94f, 2.0f, 0.16f, 0.14f);
        state(p, "minecraft:stripped_spruce_log[axis=z]", CX - 0.94f, 0.1f, CZ, 0.14f, 0.16f, 2.0f);
        // a low fence on the back and left, posts at the corners (the chute corner stays open)
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (sx == 1 && sz == 1) {
                    continue;
                }
                box(p, Material.STRIPPED_SPRUCE_LOG, CX + sx * 0.9f, 0.45f, CZ + sz * 0.9f, 0.14f, 0.7f, 0.14f);
            }
        }
        box(p, Material.SPRUCE_PLANKS, CX, 0.62f, CZ - 0.9f, 1.8f, 0.07f, 0.06f);
        box(p, Material.SPRUCE_PLANKS, CX - 0.9f, 0.62f, CZ, 0.06f, 0.07f, 1.8f);
        // the crane at the back-left corner; its hook swings over the stack
        box(p, Material.STRIPPED_DARK_OAK_LOG, CX - 0.88f, 1.4f, CZ - 0.88f, 0.18f, 2.6f, 0.18f);
        state(p, "minecraft:stripped_dark_oak_log[axis=x]", CX - 0.4f, 2.66f, CZ - 0.88f, 1.2f, 0.14f, 0.14f);
        boxR(p, Material.STRIPPED_DARK_OAK_LOG, CX - 0.66f, 2.38f, CZ - 0.88f, 0.07f, 0.6f, 0.07f, new Quaternionf().rotateZ(-0.8f));
        block(p, "", "minecraft:chain[axis=y]", CX + 0.1f, 2.3f, CZ - 0.88f, 0.6f, 0.6f, 0.6f, false);
        block(p, "", "minecraft:lantern[hanging=true]", CX - 0.88f + 0.0f, 2.42f, CZ - 0.7f, 0.42f, 0.42f, 0.42f, true);
        if (stack) {
            block(p, "", "minecraft:lightning_rod[facing=up]", CX - 0.88f, 2.95f, CZ - 0.88f, 0.4f, 0.6f, 0.4f, false);
        }
        lamp(p, CX - 0.88f, 2.82f + (stack ? 0.0f : -0.1f), CZ - 0.7f);
        ports2x2(p);
    }

    /** Depot crates on the three cells the chute leaves free, two high (Crate Stack: three high). */
    static List<PropBodies.Part> depotCrates(int count, int level) {
        List<PropBodies.Part> p = new ArrayList<>();
        float[][] cells = {{CX - 0.45f, CZ - 0.45f}, {CX + 0.45f, CZ - 0.45f}, {CX - 0.45f, CZ + 0.45f}};
        int max = depotSlots(level);
        for (int i = 0; i < Math.min(count, max); i++) {
            float[] c = cells[i % 3];
            int layer = i / 3;
            Material crate = (i % 3 == 0) ? Material.BARREL : (i % 3 == 1) ? Material.STRIPPED_OAK_WOOD : Material.SPRUCE_PLANKS;
            p.add(new PropBodies.Part("crate", crate.createBlockData(), null,
                    new Vector3f(c[0], 0.12f + 0.33f + layer * 0.67f, c[1]), new Vector3f(0.66f, 0.66f, 0.66f),
                    new Quaternionf().rotateY((float) ((i * 0.37) % 0.4 - 0.2)), false));
        }
        return p;
    }

    static int depotSlots(int level) {
        return level >= 2 ? 9 : 6;
    }

    // ------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------

    /** Orange lip on the chute (belt out), green lips on the other three faces (belt in). */
    private static void ports2x2(List<PropBodies.Part> p) {
        p.add(new PropBodies.Part("", Material.ORANGE_CONCRETE.createBlockData(), null, new Vector3f(0, 1.03f, 1.44f),
                new Vector3f(0.72f, 0.07f, 0.08f), new Quaternionf(), true));
        p.add(new PropBodies.Part("", Material.SMOOTH_STONE_SLAB.createBlockData(), null, new Vector3f(0, 0.62f, 1.72f),
                new Vector3f(0.5f, 0.06f, 0.62f), new Quaternionf().rotateX(0.42f), false));
        lip(p, CX, 0.3f, -0.53f, 0.9f, 0.06f);
        lip(p, -1.53f, 0.3f, CZ, 0.06f, 0.9f);
        lip(p, 0.53f, 0.3f, 0.0f, 0.06f, 0.7f);
    }

    private static void lip(List<PropBodies.Part> p, float x, float y, float z, float sx, float sz) {
        p.add(new PropBodies.Part("", Material.LIME_CONCRETE.createBlockData(), null, new Vector3f(x, y, z),
                new Vector3f(sx, 0.08f, sz), new Quaternionf(), true));
    }

    private static void lamp(List<PropBodies.Part> p, float x, float y, float z) {
        p.add(new PropBodies.Part("lamp", Material.LIGHT_GRAY_CONCRETE.createBlockData(), null, new Vector3f(x, y, z),
                new Vector3f(0.24f, 0.24f, 0.24f), new Quaternionf(), true));
    }

    private static void box(List<PropBodies.Part> p, Material m, float x, float y, float z, float sx, float sy, float sz) {
        p.add(new PropBodies.Part("", m.createBlockData(), null, new Vector3f(x, y, z), new Vector3f(sx, sy, sz),
                new Quaternionf(), false));
    }

    private static void boxR(List<PropBodies.Part> p, Material m, float x, float y, float z, float sx, float sy, float sz,
                             Quaternionf q) {
        p.add(new PropBodies.Part("", m.createBlockData(), null, new Vector3f(x, y, z), new Vector3f(sx, sy, sz), q, false));
    }

    private static void state(List<PropBodies.Part> p, String state, float x, float y, float z, float sx, float sy, float sz) {
        p.add(new PropBodies.Part("", Bukkit.createBlockData(state), null, new Vector3f(x, y, z), new Vector3f(sx, sy, sz),
                new Quaternionf(), false));
    }

    private static void block(List<PropBodies.Part> p, String role, String state, float x, float y, float z,
                              float sx, float sy, float sz, boolean bright) {
        p.add(new PropBodies.Part(role, Bukkit.createBlockData(state), null, new Vector3f(x, y, z),
                new Vector3f(sx, sy, sz), new Quaternionf(), bright));
    }

    private static void spin(List<PropBodies.Part> p, String role, Material m, float x, float y, float z,
                             float sx, float sy, float sz, Quaternionf own) {
        p.add(new PropBodies.Part(role, null, new ItemStack(m), new Vector3f(x, y, z), new Vector3f(sx, sy, sz), own, false));
    }

    /** A flat item (tool, paper, clock, map) hung on a wall, facing the front. */
    private static void item(List<PropBodies.Part> p, Material m, float x, float y, float z, float size, Quaternionf q) {
        p.add(new PropBodies.Part("", null, new ItemStack(m), new Vector3f(x, y, z), new Vector3f(size, size, size), q, false));
    }
}
