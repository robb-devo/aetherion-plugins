package de.aetherion.guilds.structure;

import de.aetherion.guilds.template.Template;
import de.aetherion.guilds.template.TemplateLibrary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The production machines are display props, not pasted houses. Each still has a tiny real-block template made
 * in code, only so the rest of the island code keeps working unchanged (footprint, protection, belts, ghost):
 * <ul>
 *   <li>invisible <b>barriers</b> as the collision shell (you can't walk through a machine, can click it, and
 *   it can't be mined),</li>
 *   <li>one real <b>hopper</b> at the front: the out chute, so the port (the belt start in front of it) is
 *   exactly where it always was on the old 3x3 machines, and old belts keep feeding.</li>
 * </ul>
 * The body itself — posts, wheels, sails, chimneys, crates, hammers — is drawn by {@code PropBodies} and owned
 * by {@code MachineFx}.
 */
public final class Props {

    public static final String QUARRY = "prop_quarry";
    /** Tight spot: a one-column rig over the quarry, belts start on any cell next to it. */
    public static final String QUARRY_SLIM = "prop_quarry_slim";
    public static final String MILL = "prop_mill";
    public static final String FORGE = "prop_forge";
    public static final String DEPOT = "prop_depot";
    /** 5x5 timber store with crates that stack up as it fills (Storage Loft, Warehouse as it grows). */
    public static final String HUT = "prop_hut";
    /** 5x5 Hub: a dais, a tower, a spinning holo core over the real lectern (canopy, then a gold dome). */
    public static final String HUB = "prop_hub";
    /** 3x3 Dock: a timber quay with a cargo crane and a mooring mast the airship ties up to. */
    public static final String DOCK = "prop_dock";

    /**
     * The compact set every new building uses (2x2 machines and hut, the 4x2 Hub, the 3x2 Dock). The anchor is
     * the back-right cell, so the chute keeps its cell (0,1,1) and the belt still starts at (0,1,2). Buildings
     * placed before keep their 3x3 / 5x5 templates and bodies.
     */
    public static final String V2 = "prop_v2_";
    public static final String MILL_V2 = "prop_v2_mill";
    public static final String FORGE_V2 = "prop_v2_forge";
    public static final String DEPOT_V2 = "prop_v2_depot";
    public static final String HUT_V2 = "prop_v2_hut";
    public static final String HUB_V2 = "prop_v2_hub";
    public static final String DOCK_V2 = "prop_v2_dock";

    /** Old pasted looks that get swapped for props on first visit (same 3x3 footprint, same chute). */
    public static final Set<String> OLD_PRODUCTION = Set.of(
            "sm_quarry_housing", "sm_quarry_rig",
            "sm_mill", "sm_mill_2", "sm_forge", "sm_forge_2", "sm_depot", "sm_depot_2",
            // 5x5 hut and Hub (same footprint, the Hub's lectern stays on the same cell)
            "sm_storage_hut", "sm_storage_hut_2", "sm_storage_hut_3", "sm_workshop", "sm_workshop_2", "sm_workshop_3");

    private Props() {
    }

    /** Which machine a prop template draws (for the placement ghost). */
    public static StructureType typeOf(String templateId) {
        if (templateId == null) {
            return null;
        }
        return switch (templateId) {
            case QUARRY, QUARRY_SLIM -> StructureType.QUARRY_HOUSING;
            case MILL, MILL_V2 -> StructureType.MILL;
            case FORGE, FORGE_V2 -> StructureType.FORGE;
            case DEPOT, DEPOT_V2 -> StructureType.DEPOT;
            case HUT, HUT_V2 -> StructureType.STORAGE_HUT;
            case HUB, HUB_V2 -> StructureType.WORKSHOP;
            case DOCK, DOCK_V2 -> StructureType.DOCK;
            default -> null;
        };
    }

    public static boolean isProp(String templateId) {
        return templateId != null && templateId.startsWith("prop_");
    }

    /** One of the compact buildings (2x2 machines and hut, the 4x2 Hub, the 3x2 Dock)? */
    public static boolean compact(String templateId) {
        return templateId != null && templateId.startsWith(V2);
    }

    public static void register(TemplateLibrary library) {
        library.register(quarry());
        library.register(Template.synthetic(QUARRY_SLIM, 0, 0, 0, 0, 2, 0, Map.of()));
        library.register(machine(MILL, false, true));
        library.register(machine(FORGE, true, true));
        library.register(machine(DEPOT, false, false));
        library.register(hut());
        library.register(hub());
        library.register(dock());
        library.register(machine2(MILL_V2, 3));
        library.register(machine2(FORGE_V2, 2));
        library.register(depot2());
        library.register(hut2());
        library.register(hub2());
        library.register(dock2());
    }

    // ------------------------------------------------------------------------------------------------
    // the compact set: cells x -1..0, z 0..1 (Hub x -2..1, Dock x -1..1); the anchor is the back-right cell
    // ------------------------------------------------------------------------------------------------

    /** 2x2 machine: a solid shell {@code high} blocks up, the chute in the front-right cell (forge: its chimney). */
    private static Template machine2(String id, int high) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int y = 1; y <= high; y++) {
            for (int x = -1; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    cells.put(x + "," + y + "," + z, y == 1 && x == 0 && z == 1 ? CHUTE : BARRIER);
                }
            }
        }
        if (FORGE_V2.equals(id)) {
            cells.put("-1,3,0", BARRIER);
            cells.put("-1,4,0", BARRIER);
        }
        return Template.synthetic(id, -1, 0, 0, 0, 5, 1, cells);
    }

    /** 2x2 Depot: a knee-high yard shell, the chute in the front-right cell, the crate cells two high. */
    private static Template depot2() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -1; x <= 0; x++) {
            for (int z = 0; z <= 1; z++) {
                boolean chute = x == 0 && z == 1;
                cells.put(x + ",1," + z, chute ? CHUTE : BARRIER);
                if (!chute) {
                    cells.put(x + ",2," + z, BARRIER);
                }
            }
        }
        return Template.synthetic(DEPOT_V2, -1, 0, 0, 0, 3, 1, cells);
    }

    /** 2x2 hut: a solid three-high shell (belts run into any wall); the storeys above are drawn. */
    private static Template hut2() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    cells.put(x + "," + y + "," + z, BARRIER);
                }
            }
        }
        return Template.synthetic(HUT_V2, -1, 0, 0, 0, 7, 1, cells);
    }

    /**
     * 4x2 Hub: the back row is the hall (three high), the front row the porch (two high), the real lectern at
     * (0,1,1) with free air above it so you reach it from the front.
     */
    private static Template hub2() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -2; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                cells.put(x + "," + y + ",0", BARRIER);
            }
            if (x != 0) {
                cells.put(x + ",1,1", BARRIER);
                cells.put(x + ",2,1", BARRIER);
            }
        }
        cells.put("0,1,1", "minecraft:lectern[facing=south,has_book=true,powered=false]");
        return Template.synthetic(HUB_V2, -2, 0, 0, 1, 6, 1, cells);
    }

    /** 3x2 Dock: the quay deck is the shell's top (you walk on it), the mast and crane posts go up from it. */
    private static Template dock2() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                cells.put(x + ",1," + z, BARRIER);
            }
        }
        cells.put("1,2,0", BARRIER);
        cells.put("1,3,0", BARRIER);
        cells.put("1,4,0", BARRIER);
        cells.put("-1,2,0", BARRIER);
        cells.put("-1,2,1", BARRIER);
        return Template.synthetic(DOCK_V2, -1, 0, 0, 1, 6, 1, cells);
    }

    /** 3x3 Dock: a knee-high quay shell (belts run into any side), the mooring mast at the back corner. */
    private static Template dock() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                cells.put(x + ",1," + z, BARRIER);
            }
        }
        cells.put("1,2,-1", BARRIER);
        cells.put("1,3,-1", BARRIER);
        return Template.synthetic(DOCK, -1, 0, -1, 1, 4, 1, cells);
    }

    /** 5x5 hut: a solid two-high shell (belts run into any wall), the roof core above. */
    private static Template hut() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                cells.put(x + ",1," + z, BARRIER);
                cells.put(x + ",2," + z, BARRIER);
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    cells.put(x + ",3," + z, BARRIER);
                }
            }
        }
        return Template.synthetic(HUT, -2, 0, -2, 2, 4, 2, cells);
    }

    /**
     * 5x5 Hub: the back three rows are the dais and tower (shell), the corner posts are solid, and the real
     * lectern stands at (0,1,1) — the same cell as on the old Workshop — with a free porch in front of it.
     */
    private static Template hub() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 0; z++) {
                cells.put(x + ",1," + z, BARRIER);
            }
            for (int z = -2; z <= -1; z++) {
                if (Math.abs(x) <= 1) {
                    cells.put(x + ",2," + z, BARRIER);
                }
            }
        }
        cells.put("0,3,-1", BARRIER);
        cells.put("0,4,-1", BARRIER);
        for (int x = -2; x <= 2; x += 4) {
            cells.put(x + ",1,1", BARRIER);
            cells.put(x + ",1,2", BARRIER);
            cells.put(x + ",2,2", BARRIER);
        }
        cells.put("0,1,1", "minecraft:lectern[facing=south,has_book=true,powered=false]");
        return Template.synthetic(HUB, -2, 0, -2, 2, 5, 2, cells);
    }

    private static final String BARRIER = "minecraft:barrier";
    private static final String CHUTE = "minecraft:hopper[enabled=false,facing=south]";

    /** 3x3: shell ring at knee height around the quarry (its core stays open), corner posts, chute in front. */
    private static Template quarry() {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                cells.put(x + ",1," + z, x == 0 && z == 1 ? CHUTE : BARRIER);
            }
        }
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                cells.put(x + ",2," + z, BARRIER);
            }
        }
        return Template.synthetic(QUARRY, -1, 0, -1, 1, 2, 1, cells);
    }

    /** 3x3 machine: solid shell, a taller core, the chute in front (forge: its chimney corner too). */
    private static Template machine(String id, boolean chimney, boolean tall) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                cells.put(x + ",1," + z, x == 0 && z == 1 ? CHUTE : BARRIER);
                if (tall && !(x == 0 && z == 1)) {
                    cells.put(x + ",2," + z, BARRIER); // you can't hop onto a two-storey machine
                }
            }
        }
        cells.put("0,2,0", BARRIER);
        cells.put("0,2,-1", BARRIER);
        if (chimney) {
            cells.put("-1,2,-1", BARRIER);
            cells.put("-1,3,-1", BARRIER);
        }
        return Template.synthetic(id, -1, 0, -1, 1, 3, 1, cells);
    }
}
