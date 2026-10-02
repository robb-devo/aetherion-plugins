package de.aetherion.guilds.structure;

import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.Template;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's placement preview, made of BlockDisplays only that player can see (nobody else gets the
 * packets). A structure shows as a glowing hologram of its real blocks (green outline = fits, red = blocked)
 * over a tinted footprint plate; big templates fall back to an outline box. The Belt Layer shows a cursor
 * plate, the planned run as one or two flat strips, and orange markers on the chutes nearby.
 *
 * <p>Nothing is saved: the displays are non-persistent and die with the session.
 */
final class GhostView {

    static final String TAG = "aeg_ghost";
    private static final int MAX_CELLS = 220;
    private static final float INSET = 0.04f;
    static final Color OK = Color.fromRGB(90, 230, 120);
    static final Color BAD = Color.fromRGB(240, 70, 70);
    static final Color PORT = Color.fromRGB(255, 160, 40);

    private final Plugin plugin;
    private final Player viewer;

    // structure hologram
    private Template shown;
    private World world;
    private int ax;
    private int ay;
    private int az;
    private int rot = -1;
    private Boolean ok;
    private boolean boxMode;
    private final List<BlockDisplay> blocks = new ArrayList<>();
    private final List<int[]> cells = new ArrayList<>();
    private BlockDisplay plate;
    private BlockDisplay port;

    // belt preview
    private final List<BlockDisplay> legs = new ArrayList<>();
    private BlockDisplay cursor;
    private final List<BlockDisplay> chutes = new ArrayList<>();

    GhostView(Plugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    // ------------------------------------------------------------------------------------------------
    // structures
    // ------------------------------------------------------------------------------------------------

    void structure(Template template, World in, int x, int y, int z, int rotation, boolean fits, int[] portLocal) {
        clearBelts();
        if (template != shown || in != world || plate == null || !plate.isValid()) {
            clearStructure();
            build(template, in, x, y, z, rotation, fits, portLocal);
            return;
        }
        if (rotation != rot) {
            if (boxMode) {
                clearStructure();
                build(template, in, x, y, z, rotation, fits, portLocal);
                return;
            }
            reorient(rotation, portLocal);
        }
        if (x != ax || y != ay || z != az) {
            ax = x;
            ay = y;
            az = z;
            Location at = new Location(world, ax, ay, az);
            for (BlockDisplay display : blocks) {
                display.teleport(at);
            }
            plate.teleport(at);
            if (port != null) {
                port.teleport(at);
            }
        }
        if (ok == null || ok != fits) {
            ok = fits;
            Color glow = fits ? OK : BAD;
            for (BlockDisplay display : blocks) {
                display.setGlowColorOverride(glow);
            }
            plate.setBlock(plateBlock(fits));
            plate.setGlowColorOverride(glow);
        }
    }

    private void build(Template template, World in, int x, int y, int z, int rotation, boolean fits, int[] portLocal) {
        shown = template;
        world = in;
        ax = x;
        ay = y;
        az = z;
        rot = rotation;
        ok = fits;
        Location at = new Location(world, ax, ay, az);
        Color glow = fits ? OK : BAD;
        StructureType propType = Props.typeOf(template.id());
        List<int[]> ghost = propType != null ? null : template.ghostCells(MAX_CELLS);
        boxMode = ghost == null;
        if (propType != null) {
            // the prop itself, as a hologram: what you see is what stands there after the click
            for (de.aetherion.guilds.logistics.PropBodies.Part part
                    : de.aetherion.guilds.logistics.PropBodies.body(propType, 1, template.id())) {
                de.aetherion.guilds.logistics.PropBodies.Part solid = part.item() == null ? part
                        : new de.aetherion.guilds.logistics.PropBodies.Part(part.role(),
                        part.item().getType().createBlockData(), null, part.pivot(), part.size(), part.own(), false);
                blocks.add(spawn(at, solid.block(),
                        de.aetherion.guilds.logistics.PropBodies.transform(solid, rotation, null, 0f, 0f), glow));
            }
        } else if (!boxMode) {
            for (int[] cell : ghost) {
                BlockData data = ghostBlock(template.stateAtLocal(cell[0], cell[1], cell[2]), rotation);
                if (data == null) {
                    continue;
                }
                blocks.add(spawn(at, data, cellTransform(cell, rotation), glow));
                cells.add(cell);
            }
        } else {
            int[] fp = template.rotatedFootprint(rotation);
            float x1 = fp[0];
            float z1 = fp[1];
            float x2 = fp[2] + 1f;
            float z2 = fp[3] + 1f;
            float h = Math.max(2, Math.min(16, template.maxY()));
            float t = 0.08f;
            BlockData edge = Material.WHITE_STAINED_GLASS.createBlockData();
            float[][] posts = {{x1, z1}, {x2 - t, z1}, {x1, z2 - t}, {x2 - t, z2 - t}};
            for (float[] p : posts) {
                blocks.add(spawn(at, edge, box(p[0], 1f, p[1], t, h, t), glow));
            }
            blocks.add(spawn(at, edge, box(x1, 1f + h, z1, x2 - x1, t, t), glow));
            blocks.add(spawn(at, edge, box(x1, 1f + h, z2 - t, x2 - x1, t, t), glow));
            blocks.add(spawn(at, edge, box(x1, 1f + h, z1, t, t, z2 - z1), glow));
            blocks.add(spawn(at, edge, box(x2 - t, 1f + h, z1, t, t, z2 - z1), glow));
        }
        plate = spawn(at, plateBlock(fits), plateTransform(template, rotation), glow);
        port = portLocal == null ? null : spawn(at, Material.ORANGE_STAINED_GLASS.createBlockData(),
                portTransform(portLocal, rotation), PORT);
    }

    private void reorient(int rotation, int[] portLocal) {
        rot = rotation;
        for (int i = 0; i < blocks.size() && i < cells.size(); i++) {
            int[] cell = cells.get(i);
            BlockData data = ghostBlock(shown.stateAtLocal(cell[0], cell[1], cell[2]), rotation);
            BlockDisplay display = blocks.get(i);
            if (data != null) {
                display.setBlock(data);
            }
            display.setTransformation(cellTransform(cell, rotation));
        }
        plate.setTransformation(plateTransform(shown, rotation));
        if (port != null && portLocal != null) {
            port.setTransformation(portTransform(portLocal, rotation));
        }
    }

    private static Transformation cellTransform(int[] cell, int rotation) {
        int[] r = StateRotator.rotateXZ(cell[0], cell[2], rotation);
        float s = 1f - 2f * INSET;
        return box(r[0] + INSET, cell[1] + INSET, r[1] + INSET, s, s, s);
    }

    private static Transformation plateTransform(Template template, int rotation) {
        int[] fp = template.rotatedFootprint(rotation);
        return box(fp[0] + 0.02f, 1.01f, fp[1] + 0.02f, fp[2] - fp[0] + 0.96f, 0.03f, fp[3] - fp[1] + 0.96f);
    }

    private static Transformation portTransform(int[] portLocal, int rotation) {
        int[] r = StateRotator.rotateXZ(portLocal[0], portLocal[2], rotation);
        return box(r[0] + 0.2f, portLocal[1] + 0.02f, r[1] + 0.2f, 0.6f, 0.1f, 0.6f);
    }

    private static BlockData plateBlock(boolean fits) {
        return (fits ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS).createBlockData();
    }

    /** Block for a hologram cell. Block-entity models (chests, signs, banners, beds, heads) don't render in a
     *  BlockDisplay, so chests show as barrels and the rest is left out. */
    private static BlockData ghostBlock(String state, int rotation) {
        String name = StateRotator.name(state);
        if (name.endsWith("_sign") || name.endsWith("_banner") || name.endsWith("_bed") || name.endsWith("_head")
                || name.endsWith("_skull") || name.endsWith("shulker_box") || name.equals("minecraft:bell")
                || name.equals("minecraft:decorated_pot") || name.equals("minecraft:conduit")) {
            return null;
        }
        if (name.endsWith("chest")) {
            return Material.BARREL.createBlockData();
        }
        try {
            return Bukkit.createBlockData(StateRotator.rotate(state, rotation));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    void clearStructure() {
        for (BlockDisplay display : blocks) {
            display.remove();
        }
        blocks.clear();
        cells.clear();
        if (plate != null) {
            plate.remove();
            plate = null;
        }
        if (port != null) {
            port.remove();
            port = null;
        }
        shown = null;
        rot = -1;
        ok = null;
    }

    // ------------------------------------------------------------------------------------------------
    // belts
    // ------------------------------------------------------------------------------------------------

    /** A flat marker on one cell (where the belt would start). */
    void cursor(World in, int x, int y, int z, boolean fits) {
        clearStructure();
        clearLegs();
        Location at = new Location(in, x, y, z);
        if (cursor == null || !cursor.isValid() || cursor.getWorld() != in) {
            if (cursor != null) {
                cursor.remove();
            }
            cursor = spawn(at, plateBlock(fits), box(0.08f, 0.01f, 0.08f, 0.84f, 0.12f, 0.84f), fits ? OK : BAD);
            return;
        }
        cursor.teleport(at);
        cursor.setBlock(plateBlock(fits));
        cursor.setGlowColorOverride(fits ? OK : BAD);
    }

    /** The planned run as straight strips (an L is two). {@code corners} are the run's turning points. */
    void run(World in, List<int[]> points, boolean fits) {
        clearStructure();
        if (cursor != null) {
            cursor.remove();
            cursor = null;
        }
        List<int[][]> segments = new ArrayList<>();
        if (!points.isEmpty()) {
            int[] start = points.get(0);
            int[] prev = start;
            int dirX = 0;
            int dirZ = 0;
            for (int i = 1; i < points.size(); i++) {
                int[] p = points.get(i);
                int dx = Integer.signum(p[0] - prev[0]);
                int dz = Integer.signum(p[2] - prev[2]);
                if (i > 1 && (dx != dirX || dz != dirZ)) {
                    segments.add(new int[][]{start, prev});
                    start = prev;
                }
                dirX = dx;
                dirZ = dz;
                prev = p;
            }
            segments.add(new int[][]{start, prev});
        }
        while (legs.size() > segments.size()) {
            legs.remove(legs.size() - 1).remove();
        }
        Color glow = fits ? OK : BAD;
        for (int i = 0; i < segments.size(); i++) {
            int[] a = segments.get(i)[0];
            int[] b = segments.get(i)[1];
            int x1 = Math.min(a[0], b[0]);
            int z1 = Math.min(a[2], b[2]);
            float w = Math.abs(a[0] - b[0]) + 1f;
            float d = Math.abs(a[2] - b[2]) + 1f;
            Location at = new Location(in, x1, a[1], z1);
            Transformation t = box(0.1f, 0.01f, 0.1f, w - 0.2f, 0.14f, d - 0.2f);
            if (i < legs.size() && legs.get(i).isValid() && legs.get(i).getWorld() == in) {
                BlockDisplay leg = legs.get(i);
                leg.setTeleportDuration(0);
                leg.teleport(at);
                leg.setTransformation(t);
                leg.setBlock(plateBlock(fits));
                leg.setGlowColorOverride(glow);
            } else {
                BlockDisplay leg = spawn(at, plateBlock(fits), t, glow);
                if (i < legs.size()) {
                    legs.get(i).remove();
                    legs.set(i, leg);
                } else {
                    legs.add(leg);
                }
            }
        }
    }

    /** Orange markers on the output chutes near the player (where belts start). */
    void chutes(World in, List<int[]> ports) {
        while (chutes.size() > ports.size()) {
            chutes.remove(chutes.size() - 1).remove();
        }
        for (int i = 0; i < ports.size(); i++) {
            int[] p = ports.get(i);
            Location at = new Location(in, p[0], p[1], p[2]);
            if (i < chutes.size() && chutes.get(i).isValid() && chutes.get(i).getWorld() == in) {
                BlockDisplay marker = chutes.get(i);
                marker.setTeleportDuration(0);
                marker.teleport(at);
            } else {
                BlockDisplay marker = spawn(at, Material.ORANGE_STAINED_GLASS.createBlockData(),
                        box(0.25f, 0.02f, 0.25f, 0.5f, 0.08f, 0.5f), PORT);
                if (i < chutes.size()) {
                    chutes.get(i).remove();
                    chutes.set(i, marker);
                } else {
                    chutes.add(marker);
                }
            }
        }
    }

    private void clearLegs() {
        for (BlockDisplay leg : legs) {
            leg.remove();
        }
        legs.clear();
    }

    /** Hide the cursor / planned run (chute markers stay). */
    void clearRun() {
        clearLegs();
        if (cursor != null) {
            cursor.remove();
            cursor = null;
        }
    }

    void clearBelts() {
        clearLegs();
        if (cursor != null) {
            cursor.remove();
            cursor = null;
        }
        for (BlockDisplay marker : chutes) {
            marker.remove();
        }
        chutes.clear();
    }

    void clear() {
        clearStructure();
        clearBelts();
    }

    // ------------------------------------------------------------------------------------------------

    private BlockDisplay spawn(Location at, BlockData data, Transformation transformation, Color glow) {
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBlock(data);
            d.setTransformation(transformation);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setTeleportDuration(2);
            if (glow != null) {
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
            }
            d.addScoreboardTag(TAG);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private static Transformation box(float x, float y, float z, float w, float h, float d) {
        return new Transformation(new Vector3f(x, y, z), new AxisAngle4f(), new Vector3f(w, h, d), new AxisAngle4f());
    }
}
