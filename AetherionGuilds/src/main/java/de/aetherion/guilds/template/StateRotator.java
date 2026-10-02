package de.aetherion.guilds.template;

import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rotates block-state strings ("minecraft:oak_stairs[facing=north,half=bottom]") by quarter turns clockwise
 * seen from above. Done on the string before {@code Bukkit.createBlockData}, so it does not depend on any
 * newer BlockData rotation API.
 */
public final class StateRotator {

    private static final String[] HORIZ = {"north", "east", "south", "west"};
    private static final Map<String, String> RAIL_CW = Map.of(
            "north_south", "east_west",
            "east_west", "north_south",
            "ascending_north", "ascending_east",
            "ascending_east", "ascending_south",
            "ascending_south", "ascending_west",
            "ascending_west", "ascending_north",
            "north_east", "south_east",
            "south_east", "south_west",
            "south_west", "north_west",
            "north_west", "north_east"
    );

    private StateRotator() {
    }

    public static String name(String state) {
        int bracket = state.indexOf('[');
        String name = bracket < 0 ? state : state.substring(0, bracket);
        return name.contains(":") ? name : "minecraft:" + name;
    }

    /** Clockwise quarter turns: (x, z) -> (-z, x). */
    public static int[] rotateXZ(int x, int z, int k) {
        int turns = Math.floorMod(k, 4);
        int rx = x;
        int rz = z;
        for (int i = 0; i < turns; i++) {
            int nx = -rz;
            rz = rx;
            rx = nx;
        }
        return new int[]{rx, rz};
    }

    public static int[] unrotateXZ(int x, int z, int k) {
        return rotateXZ(x, z, 4 - Math.floorMod(k, 4));
    }

    public static String cw(String dir, int k) {
        for (int i = 0; i < 4; i++) {
            if (HORIZ[i].equals(dir)) {
                return HORIZ[Math.floorMod(i + k, 4)];
            }
        }
        return dir;
    }

    public static BlockFace cw(BlockFace face, int k) {
        String rotated = cw(face.name().toLowerCase(Locale.ROOT), k);
        try {
            return BlockFace.valueOf(rotated.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return face;
        }
    }

    /** Horizontal index of a yaw: 0 south, 1 west, 2 north, 3 east. */
    public static int yawIndex(float yaw) {
        return Math.floorMod(Math.round(yaw / 90f), 4);
    }

    /** Rotation that makes a template's front (south) face the viewer who looks along {@code yaw}. */
    public static int frontTowardViewer(float yaw) {
        return (yawIndex(yaw) + 2) & 3;
    }

    public static BlockFace facingOfYaw(float yaw) {
        return switch (yawIndex(yaw)) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public static String rotate(String state, int k) {
        int turns = Math.floorMod(k, 4);
        if (turns == 0 || state.indexOf('[') < 0) {
            return state;
        }
        int bracket = state.indexOf('[');
        String name = state.substring(0, bracket);
        String body = state.substring(bracket + 1, state.endsWith("]") ? state.length() - 1 : state.length());
        Map<String, String> props = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        Map<String, String> out = new LinkedHashMap<>(props);
        String facing = props.get("facing");
        if (facing != null) {
            out.put("facing", cw(facing, turns));
        }
        String axis = props.get("axis");
        if (axis != null && (turns % 2 == 1) && (axis.equals("x") || axis.equals("z"))) {
            out.put("axis", axis.equals("x") ? "z" : "x");
        }
        String rotation = props.get("rotation");
        if (rotation != null) {
            try {
                out.put("rotation", Integer.toString(Math.floorMod(Integer.parseInt(rotation) + 4 * turns, 16)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (props.containsKey("north") && props.containsKey("east")
                && props.containsKey("south") && props.containsKey("west")) {
            for (String dir : HORIZ) {
                out.put(cw(dir, turns), props.get(dir));
            }
        }
        String shape = props.get("shape");
        if (shape != null && RAIL_CW.containsKey(shape)) {
            String s = shape;
            for (int i = 0; i < turns; i++) {
                s = RAIL_CW.get(s);
            }
            out.put("shape", s);
        }
        StringBuilder builder = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : out.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.append(']').toString();
    }
}
