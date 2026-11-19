package de.aetherion.hub.origin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.HangingSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the arms of every pasted signpost ({@code ae_prop_signpost}) so each arm names a real place that lies in
 * the direction it points. The schematic ships one fixed set of arms; pasted at three spots with the same turn, most
 * arms pointed the wrong way (playtest 3, R-49).
 *
 * <p>Runs from the Origin loop. A post is written once its chunks are loaded and no prop paste is running, then marked
 * with {@link #VERSION} in {@code origin-props.yml}; a new paste of the spot is written again. Signs are blocks, so
 * every reader sees the same text: the map's own place names, as the location titles show them.
 */
final class OriginSignposts {

    /** Bump when the destination table or the layout changes: every post is written again. */
    static final String VERSION = "v1";
    private static final String PROP = "ae_prop_signpost";
    /** Places closer than this are "here" and never get an arm. */
    private static final double MIN_DISTANCE = 30.0;
    /** An arm only names a place within this angle of where it points. */
    private static final double MAX_ANGLE = 60.0;

    private enum Arm {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dz;

        Arm(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    /**
     * A place an arm can name. {@code rank} 1 = main stops, 2 = places with a job, 3 = districts; lower wins when two
     * places are about as good. {@code range} keeps local places (the rite) off far-away posts.
     */
    private record Place(String line1, String line2, double x, double z, int rank, double range) {
    }

    /** Spots from {@code origin.yml} (district anchors and landmarks), checked 2026-11-19. */
    private static final List<Place> PLACES = List.of(
            new Place("CAPITAL", "", 0.5, -61.5, 1, 99_999),
            new Place("ANKER", "HARBOUR", 305.5, -417.5, 1, 99_999),
            new Place("SHABBY", "MINE", 30.5, 180.5, 1, 99_999),
            new Place("BORDER-", "LANDS", 161.5, 73.5, 1, 99_999),
            new Place("RITE", "WARDEN", 251.5, 173.5, 2, 180),
            new Place("RITE", "ALTAR", 237.5, 231.5, 2, 180),
            new Place("COLOSSEUM", "", -354.5, -124.5, 2, 99_999),
            new Place("ELDERVALE", "SKYWAY", 22.5, 300.5, 2, 400),
            new Place("CLUCKS-", "WORTH", -249.5, 180.5, 3, 99_999),
            new Place("WHISPER-", "WOOD", -284.5, -499.5, 3, 99_999),
            new Place("LILY", "LAKE", -419.5, -359.5, 3, 99_999),
            new Place("BLOOM-", "FIELD", -39.5, -449.5, 3, 99_999),
            new Place("EASTWOOD", "", 349.5, -326.5, 3, 99_999),
            new Place("GLOWCAP", "CRAGS", -407.5, 27.5, 3, 99_999),
            new Place("THE", "MERES", 221.5, -143.5, 3, 99_999),
            new Place("FORAGE", "SKYWAY", 432.5, -235.5, 3, 99_999)
    );

    private final OriginIsle isle;

    OriginSignposts(OriginIsle isle) {
        this.isle = isle;
    }

    /** One pass over the pasted signposts; cheap when all are written. */
    void tick() {
        OriginProps props = isle.props();
        World world = isle.config().world();
        if (props == null || world == null || props.busy()) {
            return;
        }
        for (OriginProps.Spot spot : props.all().values()) {
            if (!PROP.equals(spot.prop()) || !props.pasted(spot.id()) || VERSION.equals(props.label(spot.id()))) {
                continue;
            }
            int[] at = spot.at();
            if (!loaded(world, at[0], at[2])) {
                continue;
            }
            int written = write(world, at);
            if (written > 0) {
                props.markLabel(spot.id(), VERSION);
                isle.plugin().getLogger().info("Origin signpost " + spot.id() + ": " + written + " arm(s) point at real places.");
            }
        }
    }

    private static boolean loaded(World world, int x, int z) {
        for (int dx : new int[] {-3, 3}) {
            for (int dz : new int[] {-3, 3}) {
                if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Finds the hanging-sign arms around the post at {@code at} and writes them; returns how many were found. */
    private static int write(World world, int[] at) {
        Map<Arm, List<HangingSign>> arms = new EnumMap<>(Arm.class);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= 9; dy++) {
                    BlockState state = world.getBlockAt(at[0] + dx, at[1] + dy, at[2] + dz).getState(false);
                    if (state instanceof HangingSign sign) {
                        arms.computeIfAbsent(armOf(dx, dz), a -> new ArrayList<>()).add(sign);
                    }
                }
            }
        }
        if (arms.isEmpty()) {
            return 0;
        }
        Map<Arm, Place> picked = pick(at[0] + 0.5, at[2] + 0.5, arms.keySet());
        int count = 0;
        for (Map.Entry<Arm, List<HangingSign>> entry : arms.entrySet()) {
            Place place = picked.get(entry.getKey());
            for (HangingSign sign : entry.getValue()) {
                fill(sign, place, at);
                count++;
            }
        }
        return count;
    }

    private static Arm armOf(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Arm.EAST : Arm.WEST;
        }
        return dz > 0 ? Arm.SOUTH : Arm.NORTH;
    }

    /**
     * Best place per arm: short, straight and important wins (distance × rank weight × angle penalty), each place on
     * at most one arm of the post. An arm with nothing in its direction stays blank.
     */
    private static Map<Arm, Place> pick(double px, double pz, Set<Arm> wanted) {
        record Option(double score, Arm arm, Place place) {
        }
        List<Option> options = new ArrayList<>();
        for (Arm arm : wanted) {
            for (Place place : PLACES) {
                double dx = place.x() - px;
                double dz = place.z() - pz;
                double distance = Math.hypot(dx, dz);
                if (distance < MIN_DISTANCE || distance > place.range()) {
                    continue;
                }
                double cos = (dx * arm.dx + dz * arm.dz) / distance;
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
                if (angle > MAX_ANGLE) {
                    continue;
                }
                double weight = switch (place.rank()) {
                    case 1 -> 1.0;
                    case 2 -> 1.25;
                    default -> 1.5;
                };
                options.add(new Option(distance * weight * (1.0 + angle / 45.0), arm, place));
            }
        }
        options.sort((a, b) -> Double.compare(a.score(), b.score()));
        Map<Arm, Place> out = new EnumMap<>(Arm.class);
        Set<Place> used = new HashSet<>();
        for (Option option : options) {
            if (out.containsKey(option.arm()) || used.contains(option.place())) {
                continue;
            }
            out.put(option.arm(), option.place());
            used.add(option.place());
        }
        return out;
    }

    /** Both faces: the place on lines 2 and 3, the distance under it. Null place = a blank arm. */
    private static void fill(HangingSign sign, Place place, int[] at) {
        for (Side side : Side.values()) {
            SignSide face = sign.getSide(side);
            if (place == null) {
                for (int i = 0; i < 4; i++) {
                    face.line(i, Component.empty());
                }
                continue;
            }
            long metres = Math.round(Math.hypot(place.x() - (at[0] + 0.5), place.z() - (at[2] + 0.5)) / 10.0) * 10L;
            boolean twoLines = !place.line2().isEmpty();
            face.line(0, Component.empty());
            face.line(1, Component.text(place.line1()));
            face.line(2, twoLines ? Component.text(place.line2()) : Component.text(metres + " m", NamedTextColor.DARK_GRAY));
            face.line(3, twoLines ? Component.text(metres + " m", NamedTextColor.DARK_GRAY) : Component.empty());
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
