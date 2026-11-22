package de.aetherion.guilds.structure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Build Tool's signboard: a little work sign planted beside the hologram that says what the part in your hand
 * does, what goes in and what comes out (with the real items), what it costs and, on the island tour, where it
 * fits best. It stays as long as the part is held, beside the hologram (never in it), turned toward you, and it
 * grows a little with distance so it reads the same from three blocks or fifteen.
 *
 * <p>Look: a slim dark-oak post with a copper collar, a dark panel framed in copper, the part's name on a copper
 * tab across the top edge, a copper arrow on the side pointing at the hologram, and the In / Out goods as items
 * at the left of their rows (green = in, orange = out, like the belt pads and chutes).
 *
 * <p>Every piece shares one root position and yaw, and lays itself out through its transformation, so the whole
 * sign moves and turns as one (teleport interpolation). Only its viewer gets the entities; nothing is saved.
 */
final class PartBoard {

    /** One body row: legacy text, an optional item at its left. */
    record Row(String text, ItemStack icon) {
        static Row of(String text) {
            return new Row(text, null);
        }
    }

    /** What the sign says: the part's name on the tab and the rows of the panel. */
    record Spec(String title, List<Row> rows) {
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    /** One text pixel in blocks at scale 1 (vanilla text displays). */
    private static final float PX = 0.025f;
    private static final int LINE = 10;
    /** Copper frame thickness, in text pixels. */
    private static final float RIM = 1.6f;
    /** Room at the left of an icon row (the item sits there). */
    private static final int ICON_PX = 14;
    private static final float BASE_SCALE = 0.42f;
    /** Panel centre above the ground under the sign: about eye height. */
    private static final float EYE = 1.62f;
    /** Low alpha the client still draws as nothing (the frame is the same text, only its background shows). */
    private static final byte INVISIBLE_TEXT = 12;

    private static final Color INK = Color.fromARGB(234, 36, 26, 20);
    private static final Color COPPER = Color.fromARGB(255, 190, 112, 58);
    private static final BlockData POST = Bukkit.createBlockData("minecraft:stripped_dark_oak_log[axis=y]");
    private static final BlockData COLLAR = Material.WAXED_CUT_COPPER.createBlockData();

    private final Plugin plugin;
    private final Player viewer;

    private Spec spec;
    private int widthPx;
    private int lines;
    private World world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float scale = BASE_SCALE;
    private float postLength;

    private BlockDisplay post;
    private BlockDisplay collar;
    private TextDisplay rim;
    private TextDisplay body;
    private TextDisplay tab;
    private TextDisplay pointer;
    private final List<ItemDisplay> icons = new ArrayList<>();
    private final List<Integer> iconRows = new ArrayList<>();

    PartBoard(Plugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    /** Half the sign's width in blocks at this distance (for placing it beside the hologram). */
    double halfWidth(Spec wanted, double distance) {
        int w = measure(wanted);
        float s = scaleFor(distance);
        return (w / 2f + RIM + 12f) * PX * s;
    }

    /**
     * Shows (or moves) the sign: {@code (bx, by, bz)} is the foot of its post (by = the floor the hologram
     * stands on), {@code facing} the yaw it turns to, {@code distance} how far the viewer's eye is.
     */
    void show(World in, double bx, double by, double bz, float facing, double distance, Spec wanted) {
        boolean fresh = body == null || !body.isValid() || in != world;
        if (!fresh && !sameSpec(wanted)) {
            retext(wanted);
        }
        float s = scaleFor(distance);
        float ground = groundUnder(in, bx, by, bz);
        if (fresh) {
            clear();
            world = in;
            x = bx;
            y = by;
            z = bz;
            yaw = facing;
            scale = s;
            postLength = ground;
            spawnAll(wanted);
            return;
        }
        boolean moved = Math.abs(bx - x) > 0.03 || Math.abs(by - y) > 0.03 || Math.abs(bz - z) > 0.03
                || Math.abs(wrap(facing - yaw)) > 1.5f;
        if (moved) {
            x = bx;
            y = by;
            z = bz;
            yaw = facing;
            Location at = root();
            for (Entity piece : pieces()) {
                piece.teleport(at);
            }
        }
        if (Math.abs(s - scale) / scale > 0.05f || Math.abs(ground - postLength) > 0.05f) {
            scale = s;
            postLength = ground;
            layout(3);
        }
    }

    void clear() {
        for (Entity piece : pieces()) {
            piece.remove();
        }
        post = null;
        collar = null;
        rim = null;
        body = null;
        tab = null;
        pointer = null;
        icons.clear();
        iconRows.clear();
        spec = null;
        world = null;
    }

    // ------------------------------------------------------------------------------------------------

    private void spawnAll(Spec wanted) {
        Location at = root();
        post = block(at, POST);
        collar = block(at, COLLAR);
        rim = text(at, td -> {
            td.setShadowed(false);
            td.setTextOpacity(INVISIBLE_TEXT);
            td.setBackgroundColor(COPPER);
        });
        body = text(at, td -> td.setBackgroundColor(INK));
        tab = text(at, td -> {
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(COPPER);
        });
        pointer = text(at, td -> {
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        });
        pointer.text(Component.text("◀", TextColor.color(0xC87A40)));
        retext(wanted);
        // pop in: everything starts folded flat on the post and opens up in a few ticks
        float target = scale;
        scale = 0.001f;
        layout(0);
        scale = target;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (body != null && body.isValid()) {
                layout(5);
            }
        }, 2L);
    }

    private void retext(Spec wanted) {
        spec = wanted;
        widthPx = measure(wanted);
        lines = Math.max(1, wanted.rows().size());
        StringBuilder text = new StringBuilder();
        StringBuilder hidden = new StringBuilder();
        for (int i = 0; i < wanted.rows().size(); i++) {
            Row row = wanted.rows().get(i);
            String line = (hasIcon(row) ? "    " : "") + row.text();
            if (i > 0) {
                text.append('\n');
                hidden.append('\n');
            }
            text.append(line);
            hidden.append(line.replaceAll("(?i)§[0-9a-fr]", ""));
        }
        if (body != null) {
            body.text(LEGACY.deserialize(text.toString()));
        }
        if (rim != null) {
            rim.text(LEGACY.deserialize(hidden.toString()));
        }
        if (tab != null) {
            tab.text(LEGACY.deserialize("§f§l " + wanted.title() + " "));
        }
        for (ItemDisplay icon : icons) {
            icon.remove();
        }
        icons.clear();
        iconRows.clear();
        Location at = root();
        for (int i = 0; i < wanted.rows().size(); i++) {
            Row row = wanted.rows().get(i);
            if (!hasIcon(row) || at.getWorld() == null) {
                continue;
            }
            ItemStack item = row.icon().clone();
            ItemDisplay icon = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                base(d);
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            });
            viewer.showEntity(plugin, icon);
            icons.add(icon);
            iconRows.add(i);
        }
        if (body != null) {
            layout(0);
        }
    }

    /** Puts every piece where it belongs at the current scale (interpolated over {@code ticks}). */
    private void layout(int ticks) {
        float s = scale;
        float w = widthPx * PX * s;
        float h = lines * LINE * PX * s;
        float bottom = Math.max(0.55f, EYE - h / 2f);
        float rimPx = RIM * PX * s;
        float postTop = bottom - rimPx;
        float postFoot = -Math.max(0f, postLength);
        float postH = Math.max(0.05f, postTop - postFoot);
        set(post, box(-0.045f, postFoot, -0.045f, 0.09f, postH, 0.09f), ticks);
        set(collar, box(-0.075f, postTop - 0.07f, -0.075f, 0.15f, 0.06f, 0.15f), ticks);
        float rimSx = (widthPx + 1f + 2f * RIM) / (widthPx + 1f);
        float rimSy = (lines * LINE + 2f * RIM) / (lines * LINE);
        set(rim, new Transformation(new Vector3f(0f, bottom - rimPx, -0.004f), new AxisAngle4f(),
                new Vector3f(s * rimSx, s * rimSy, s), new AxisAngle4f()), ticks);
        set(body, new Transformation(new Vector3f(0f, bottom, 0f), new AxisAngle4f(),
                new Vector3f(s, s, s), new AxisAngle4f()), ticks);
        // the name tab sits on the frame's top edge: its foot covers the copper band (same colour, one piece) and
        // stops at the panel's top, so it never covers the first row's letters
        float ts = s * 1.12f;
        set(tab, new Transformation(new Vector3f(0f, bottom + h + PX * s, 0.006f), new AxisAngle4f(),
                new Vector3f(ts, ts, ts), new AxisAngle4f()), ticks);
        float ps = s * 1.5f;
        float pointerX = -(w / 2f + rimPx + 5f * PX * ps);
        set(pointer, new Transformation(new Vector3f(pointerX, bottom + h / 2f - LINE * PX * ps / 2f, 0.002f),
                new AxisAngle4f(), new Vector3f(ps, ps, ps), new AxisAngle4f()), ticks);
        for (int i = 0; i < icons.size(); i++) {
            int row = iconRows.get(i);
            ItemDisplay icon = icons.get(i);
            ItemStack item = icon.getItemStack();
            boolean block = item != null && item.getType().isBlock();
            float size = LINE * PX * s * (block ? 1.3f : 1.05f);
            float cy = bottom + h - (row + 0.5f) * LINE * PX * s;
            float cx = -w / 2f + (ICON_PX / 2f + 1f) * PX * s;
            set(icon, new Transformation(new Vector3f(cx, cy, 0.01f), new AxisAngle4f(),
                    new Vector3f(size, size, size), new AxisAngle4f()), ticks);
        }
    }

    private Location root() {
        Location at = new Location(world, x, y, z);
        at.setYaw(yaw);
        at.setPitch(0f);
        return at;
    }

    private List<Entity> pieces() {
        List<Entity> out = new ArrayList<>(6 + icons.size());
        for (Entity e : new Entity[]{post, collar, rim, body, tab, pointer}) {
            if (e != null && e.isValid()) {
                out.add(e);
            }
        }
        for (ItemDisplay icon : icons) {
            if (icon.isValid()) {
                out.add(icon);
            }
        }
        return out;
    }

    private boolean sameSpec(Spec wanted) {
        return spec != null && spec.equals(wanted);
    }

    private static boolean hasIcon(Row row) {
        return row.icon() != null && !row.icon().getType().isAir();
    }

    /** The panel's width in text pixels: its widest row, as the client will draw it. */
    private static int measure(Spec wanted) {
        int w = 0;
        for (Row row : wanted.rows()) {
            w = Math.max(w, width(row.text()) + (hasIcon(row) ? 16 : 0));
        }
        return Math.max(40, w);
    }

    /** The sign keeps its size on screen out to about 12 blocks, then just gets further away. */
    private static float scaleFor(double distance) {
        return BASE_SCALE * (float) Math.max(1.0, Math.min(2.0, distance / 6.5));
    }

    /** How far the ground under the post lies below the hologram's floor (0 when level, up to 3). */
    private static float groundUnder(World in, double bx, double by, double bz) {
        int fx = (int) Math.floor(bx);
        int fz = (int) Math.floor(bz);
        int floor = (int) Math.floor(by);
        for (int dy = 0; dy <= 3; dy++) {
            if (in.getBlockAt(fx, floor - 1 - dy, fz).getType().isSolid()) {
                return dy;
            }
        }
        return 0f; // over the void: the post stops at the floor, like a peg
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Pixel width of one legacy line in the default font (bold adds 1px per glyph). */
    static int width(String legacy) {
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    bold = false;
                }
                i++;
                continue;
            }
            w += advance(c) + (bold ? 1 : 0);
        }
        return w;
    }

    private static int advance(char c) {
        switch (c) {
            case ' ':
                return 4;
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|': case '\'':
                return 2;
            case 'l': case '`':
                return 3;
            case 'I': case 't': case '[': case ']': case '(': case ')': case '{': case '}': case '"': case '*':
                return 4;
            case 'f': case 'k': case '<': case '>':
                return 5;
            case '@': case '~':
                return 7;
            default:
                if (c < 128) {
                    return 6;
                }
                return c == '·' ? 2 : c == '×' || c == '•' ? 5 : 8;
        }
    }

    private void set(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(transformation);
    }

    private BlockDisplay block(Location at, BlockData data) {
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, d -> {
            base(d);
            d.setBlock(data);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private TextDisplay text(Location at, java.util.function.Consumer<TextDisplay> setup) {
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, d -> {
            base(d);
            d.setBillboard(Display.Billboard.FIXED);
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setLineWidth(400);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            setup.accept(d);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private static void base(Display d) {
        d.setVisibleByDefault(false);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0f);
        d.setTeleportDuration(3);
        d.setViewRange(1.0f);
        d.addScoreboardTag(GhostView.TAG);
    }

    private static Transformation box(float x0, float y0, float z0, float w, float h, float d) {
        return new Transformation(new Vector3f(x0, y0, z0), new AxisAngle4f(), new Vector3f(w, h, d), new AxisAngle4f());
    }
}
