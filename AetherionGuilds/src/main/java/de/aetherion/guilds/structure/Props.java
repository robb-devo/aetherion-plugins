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

    /** Old pasted looks that get swapped for props on first visit (same 3x3 footprint, same chute). */
    public static final Set<String> OLD_PRODUCTION = Set.of(
            "sm_quarry_housing", "sm_quarry_rig",
            "sm_mill", "sm_mill_2", "sm_forge", "sm_forge_2", "sm_depot", "sm_depot_2");

    private Props() {
    }

    /** Which machine a prop template draws (for the placement ghost). */
    public static StructureType typeOf(String templateId) {
        if (templateId == null) {
            return null;
        }
        return switch (templateId) {
            case QUARRY, QUARRY_SLIM -> StructureType.QUARRY_HOUSING;
            case MILL -> StructureType.MILL;
            case FORGE -> StructureType.FORGE;
            case DEPOT -> StructureType.DEPOT;
            default -> null;
        };
    }

    public static boolean isProp(String templateId) {
        return templateId != null && templateId.startsWith("prop_");
    }

    public static void register(TemplateLibrary library) {
        library.register(quarry());
        library.register(Template.synthetic(QUARRY_SLIM, 0, 0, 0, 0, 2, 0, Map.of()));
        library.register(machine(MILL, false, true));
        library.register(machine(FORGE, true, true));
        library.register(machine(DEPOT, false, false));
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
