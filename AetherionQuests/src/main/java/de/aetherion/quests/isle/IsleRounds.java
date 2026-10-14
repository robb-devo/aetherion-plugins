package de.aetherion.quests.isle;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Walking rounds for the Origin island cast: absolute waypoints, walked out and back (ping-pong)
 * by {@code LivingNpcLife}. Every leg was checked on the live map — standable the whole way,
 * at most one block up or down per step, no water, outside the pier cage, clear of other NPCs.
 * <p>
 * The first point is the NPC's seat. A round is only used while the NPC still stands within
 * 3 blocks of it — move an NPC with an anchor and it simply goes back to standing work.
 */
public final class IsleRounds {

    private IsleRounds() {
    }

    private static final Map<String, double[][]> ROUTES = Map.ofEntries(
            Map.entry("crane_hand", new double[][] {{314.5, 63, -394.5}, {315.5, 63, -395.5}, {313.5, 63, -398.5}}),
            Map.entry("lamp_lighter", new double[][] {{262.5, 63, -386.5}, {276.5, 63, -372.5}, {262.5, 63, -385.5}, {250.5, 64, -383.5}}),
            Map.entry("harbourmaster", new double[][] {{287.5, 63, -380.5}, {272.5, 63, -381.5}, {287.5, 63, -380.5}, {291.5, 63, -383.5}}),
            Map.entry("stall_crumb", new double[][] {{238.5, 64, -399.5}, {231.5, 64, -399.5}, {238.5, 64, -398.5}, {245.5, 64, -398.5}}),
            Map.entry("stall_tack", new double[][] {{202.5, 64, -462.5}, {196.5, 64, -461.5}, {208.5, 64, -462.5}}),
            Map.entry("stall_brine", new double[][] {{252.5, 63, -450.5}, {256.5, 63, -452.5}, {250.5, 63, -450.5}, {248.5, 63, -450.5}}),
            Map.entry("fry_gossip", new double[][] {{206.5, 64, -359.5}, {212.5, 64, -355.5}, {200.5, 64, -353.5}}),
            Map.entry("bait_theory", new double[][] {{260.5, 63, -426.5}, {256.5, 63, -431.5}}),
            Map.entry("painter", new double[][] {{64.5, 58, -48.5}, {60.5, 58, -39.5}}),
            Map.entry("mushroom_forager", new double[][] {{353.5, 67, -354.5}, {354.5, 68, -361.5}, {349.5, 68, -364.5}, {358.5, 69, -371.5}}),
            Map.entry("gate_warden", new double[][] {{168.5, 58, 87.5}, {158.5, 58, 84.5}}),
            Map.entry("tollkeeper", new double[][] {{-261.5, 56, -258.5}, {-265.5, 58, -261.5}}),
            Map.entry("timber_clerk", new double[][] {{338.5, 65, -317.5}, {330.5, 65, -311.5}}),
            Map.entry("street_sweeper", new double[][] {{210.5, 64, -404.5}, {204.5, 64, -403.5}, {204.5, 64, -413.5}, {212.5, 64, -412.5}}),
            Map.entry("lighthouse_keeper", new double[][] {{266.5, 63, -461.5}, {257.5, 63, -461.5}, {256.5, 63, -454.5}}),
            Map.entry("rivet", new double[][] {{287.5, 62, -394.5}, {288.5, 62, -395.5}, {288.5, 62, -398.5}, {286.5, 62, -402.5}}),
            Map.entry("dockhand", new double[][] {{278.5, 63, -427.5}, {272.5, 63, -423.5}, {272.5, 63, -416.5}}),
            Map.entry("lost_tourist", new double[][] {{9.5, 58, -67.5}, {-3.5, 58, -63.5}, {-12.5, 58, -71.5}, {-11.5, 58, -81.5}}),
            Map.entry("dust", new double[][] {{-29.5, 58, -19.5}, {-30.5, 58, -21.5}, {-28.5, 58, -29.5}, {-29.5, 58, -23.5}, {-36.5, 58, -23.5}}),
            Map.entry("bookie", new double[][] {{6.5, 58, 79.5}, {6.5, 58, 76.5}, {10.5, 58, 76.5}, {4.5, 58, 73.5}}),
            Map.entry("chicken_keeper", new double[][] {{-261.5, 61, 170.5}, {-265.5, 61, 176.5}, {-269.5, 60, 170.5}}),
            Map.entry("town_crier", new double[][] {{187.5, 64, -432.5}, {178.5, 64, -435.5}, {178.5, 64, -447.5}, {190.5, 64, -449.5}, {194.5, 65, -444.5}}),
            Map.entry("dock_scaler", new double[][] {{266.5, 63, -365.5}, {272.5, 64, -361.5}, {260.5, 65, -365.5}}),
            Map.entry("shanty_busker", new double[][] {{236.5, 64, -366.5}, {232.5, 64, -371.5}, {232.5, 64, -374.5}}),
            Map.entry("beekeeper", new double[][] {{290.5, 61, -44.5}, {295.5, 61, -54.5}, {294.5, 61, -57.5}, {291.5, 61, -58.5}, {294.5, 61, -70.5}, {290.5, 61, -74.5}, {290.5, 61, -79.5}})
    );

    /** The round for this NPC at this home, or null (no round, other world, or moved away from its seat). */
    public static List<Location> routeFor(String npcId, Location home) {
        if (npcId == null || home == null || home.getWorld() == null) {
            return null;
        }
        double[][] pts = ROUTES.get(npcId.toLowerCase(Locale.ROOT));
        if (pts == null || pts.length < 2) {
            return null;
        }
        double dx = home.getX() - pts[0][0];
        double dz = home.getZ() - pts[0][2];
        if (dx * dx + dz * dz > 9.0d || Math.abs(home.getY() - pts[0][1]) > 2.0d) {
            return null;
        }
        List<Location> out = new ArrayList<>(pts.length);
        out.add(home.clone());
        for (int i = 1; i < pts.length; i++) {
            out.add(new Location(home.getWorld(), pts[i][0], pts[i][1], pts[i][2]));
        }
        return out;
    }
}
