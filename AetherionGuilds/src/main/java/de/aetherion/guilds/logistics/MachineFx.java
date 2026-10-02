package de.aetherion.guilds.logistics;

import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.template.StateRotator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Living machine props: everything that makes a production piece readable from the ground without a menu.
 * <ul>
 *   <li><b>Status tag</b> over every machine, hut and belt piece: its name and one word in colour
 *   ({@link MachineState}) — "● Running", "○ Needs a belt out", "■ Full".</li>
 *   <li><b>Port markers</b> only while something is missing: a glowing <b>orange</b> pad with an arrow where a
 *   belt should start ("belt out"), <b>green</b> pads pointing in where one may end ("belt in"). Connect it
 *   and the markers vanish: the island stays clean once it works.</li>
 *   <li><b>Running animations</b>: mill stones turn, the forge hammer beats the anvil, the quarry drill pumps,
 *   belt pieces show their exits (gold = front) and a sorter holds up what it sorts.</li>
 *   <li>Soft machine sounds close by (config {@code logistics.machine-sounds}).</li>
 * </ul>
 * Only near players on the island, capped per island and server-wide, never saved. Motion is client-side
 * interpolation; nothing here sends per-tick packets except the forge hammer's two keyframes per beat.
 */
final class MachineFx {

    static final String TAG = "aeg_mfx";

    private static final TextColor ORANGE = TextColor.color(0xFF9A2E);
    private static final TextColor GREEN = TextColor.color(0x7CFF6B);
    private static final TextColor GOLD = TextColor.color(0xF2C14E);
    private static final TextColor GREY = TextColor.color(0xC8C8C8);
    private static final BlockData PAD_OUT = Material.ORANGE_STAINED_GLASS.createBlockData();
    private static final BlockData PAD_IN = Material.LIME_STAINED_GLASS.createBlockData();
    private static final BlockData PISTON_HEAD = Bukkit.createBlockData("minecraft:piston_head[facing=down,short=false]");

    /** What one machine looks like right now (prepared by the logistics service). */
    record View(PlacedStructure structure, MachineState state, String title, String sub, boolean tag,
                int[] outCell, BlockFace outDir, List<int[]> inCells, List<BlockFace> exits, ItemStack filterIcon,
                boolean working, double tagY, double fill, int level,
                de.aetherion.guilds.model.QuarryType quarry) {
    }

    /** One spawned body part and the spec it was made from. */
    private record BodyPart(Display entity, PropBodies.Part part) {
    }

    /** A part flying into place (assembly when a machine is built or grows a tier). */
    private record Pending(Display entity, Transformation to, long due, int duration) {
    }

    private static final class Props {
        TextDisplay tag;
        String tagText = "";
        String markerKey = "";
        final List<Entity> markers = new ArrayList<>();
        final List<TextDisplay> pulsing = new ArrayList<>();
        String pieceKey = "";
        final List<Entity> piece = new ArrayList<>();
        String bodyKey = "";
        final List<BodyPart> body = new ArrayList<>();
        final List<BodyPart> crates = new ArrayList<>();
        BodyPart hammer;
        boolean assemble;
        MachineState lampState;
        Boolean lit;
        boolean drillDown;
        boolean bellowsUp;
        boolean working;
        StructureType type;
        String templateId;
        de.aetherion.guilds.model.QuarryType quarry;
        int rot;
        int x;
        int y;
        int z;
        int level;
        long lastSound;
    }

    private final List<Pending> pending = new ArrayList<>();
    private final Map<String, Map<UUID, Props>> hosts = new HashMap<>();
    private int entities;
    private boolean enabled = true;
    private boolean sounds = true;
    private int radius = 32;
    private int perHost = 60;
    private int global = 1500;

    void configure(boolean enabled, boolean sounds, int radius, int perHost, int global) {
        this.enabled = enabled;
        this.sounds = sounds;
        this.radius = Math.max(48, radius); // the props ARE the machines: never cull them close
        this.perHost = Math.max(8, perHost);
        this.global = Math.max(64, global);
    }

    // ------------------------------------------------------------------------------------------------
    // render (every second, per active island)
    // ------------------------------------------------------------------------------------------------

    void render(String hostKey, World world, Collection<View> views, Collection<Location> viewers, long tick) {
        if (!enabled || world == null || viewers.isEmpty()) {
            clearHost(hostKey);
            return;
        }
        Map<UUID, Props> shown = hosts.computeIfAbsent(hostKey, k -> new HashMap<>());
        double r2 = (double) radius * radius;
        List<View> wanted = new ArrayList<>();
        for (View view : views) {
            PlacedStructure s = view.structure();
            double best = nearest(s, viewers);
            if (best <= r2) {
                wanted.add(view);
            }
        }
        if (wanted.size() > perHost) {
            wanted.sort((a, b) -> Double.compare(nearest(a.structure(), viewers), nearest(b.structure(), viewers)));
            wanted = new ArrayList<>(wanted.subList(0, perHost));
        }
        Set<UUID> keep = new HashSet<>();
        for (View view : wanted) {
            keep.add(view.structure().id());
        }
        Iterator<Map.Entry<UUID, Props>> it = shown.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Props> entry = it.next();
            if (!keep.contains(entry.getKey())) {
                kill(entry.getValue());
                it.remove();
            }
        }
        for (View view : wanted) {
            PlacedStructure s = view.structure();
            if (!world.isChunkLoaded(s.x() >> 4, s.z() >> 4)) {
                continue;
            }
            Props props = shown.get(s.id());
            if (props == null) {
                props = new Props();
                // built a moment ago: it assembles in front of you instead of popping in
                props.assemble = System.currentTimeMillis() - s.placedAt() < 12_000L;
                shown.put(s.id(), props);
            }
            if (props.type != null && (props.level != view.level() || props.rot != s.rot()
                    || !java.util.Objects.equals(props.templateId, s.templateId()) || view.state() == MachineState.BUILDING)) {
                kill(props); // grown a tier or rebuilt: it assembles again at its new size
                props.markerKey = "";
                props.pieceKey = "";
                props.bodyKey = "";
                props.assemble = true; // after the build / upgrade it assembles in front of you
            }
            props.type = s.type();
            props.templateId = s.templateId();
            props.quarry = view.quarry();
            props.rot = s.rot();
            props.x = s.x();
            props.y = s.y();
            props.z = s.z();
            props.level = view.level();
            props.working = view.working();
            if (entities < global) {
                tag(world, props, view);
                markers(world, props, view, tick);
                piece(world, props, view);
                body(world, props, view);
                animate(world, props, view, tick);
            }
            sound(world, props, viewers, tick);
        }
    }

    private static double nearest(PlacedStructure s, Collection<Location> viewers) {
        double best = Double.MAX_VALUE;
        for (Location viewer : viewers) {
            double dx = viewer.getX() - (s.x() + 0.5);
            double dz = viewer.getZ() - (s.z() + 0.5);
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------
    // status tag
    // ------------------------------------------------------------------------------------------------

    private void tag(World world, Props props, View view) {
        if (!view.tag()) {
            remove(props.tag);
            props.tag = null;
            return;
        }
        String text = view.title() + "\n" + view.state().tag() + (view.sub() == null || view.sub().isEmpty() ? "" : "\n" + view.sub());
        if (props.tag == null || !props.tag.isValid()) {
            PlacedStructure s = view.structure();
            Location at = new Location(world, s.x() + 0.5, view.tagY(), s.z() + 0.5);
            props.tag = world.spawn(at, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(96, 18, 14, 10));
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setLineWidth(280);
                d.setViewRange(0.32f);
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.85f, 0.85f, 0.85f), new Quaternionf()));
                d.addScoreboardTag(TAG);
            });
            entities++;
            props.tagText = "";
        }
        if (!text.equals(props.tagText)) {
            props.tagText = text;
            props.tag.text(LegacyComponentSerializer.legacySection().deserialize(text));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // port markers: orange = a belt starts here, green = a belt may end here
    // ------------------------------------------------------------------------------------------------

    private void markers(World world, Props props, View view, long tick) {
        StringBuilder key = new StringBuilder();
        if (view.outCell() != null) {
            key.append("o").append(view.outCell()[0]).append(',').append(view.outCell()[1]).append(',')
                    .append(view.outCell()[2]).append(view.outDir());
        }
        for (int[] cell : view.inCells()) {
            key.append("i").append(cell[0]).append(',').append(cell[1]).append(',').append(cell[2]).append(cell[3]);
        }
        String k = key.toString();
        if (!k.equals(props.markerKey) || !alive(props.markers)) {
            killList(props.markers);
            props.pulsing.clear();
            props.markerKey = k;
            if (view.outCell() != null) {
                int[] c = view.outCell();
                pad(world, props, c[0], c[1], c[2], view.outDir(), PAD_OUT, ORANGE);
                label(world, props, c[0] + 0.5, c[1] + 0.75, c[2] + 0.5, "§6belt out");
            }
            boolean labelled = false;
            for (int[] cell : view.inCells()) {
                BlockFace into = BlockFace.values()[cell[3]];
                pad(world, props, cell[0], cell[1], cell[2], into, PAD_IN, GREEN);
                if (!labelled) {
                    label(world, props, cell[0] + 0.5, cell[1] + 0.75, cell[2] + 0.5, "§abelt in");
                    labelled = true;
                }
            }
        }
        // breathe: the arrows grow and shrink a little, every second
        boolean big = (tick / 20L) % 2L == 0L;
        for (TextDisplay arrow : props.pulsing) {
            if (arrow.isValid()) {
                Transformation t = arrow.getTransformation();
                float scale = big ? 2.9f : 2.3f;
                arrow.setInterpolationDelay(0);
                arrow.setInterpolationDuration(18);
                arrow.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                        new Vector3f(scale, scale, scale), t.getRightRotation()));
            }
        }
    }

    private void pad(World world, Props props, int x, int y, int z, BlockFace dir, BlockData glass, TextColor color) {
        Location corner = new Location(world, x, y, z);
        BlockDisplay plate = world.spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(glass);
            d.setTransformation(new Transformation(new Vector3f(0.12f, 0.01f, 0.12f), new Quaternionf(),
                    new Vector3f(0.76f, 0.02f, 0.76f), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.4f);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        props.markers.add(plate);
        TextDisplay arrow = flatGlyph(world, corner, "»", color, dir, 2.6f, 0.035f);
        props.markers.add(arrow);
        props.pulsing.add(arrow);
        entities += 2;
    }

    private void label(World world, Props props, double x, double y, double z, String text) {
        TextDisplay label = world.spawn(new Location(world, x, y, z), TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacySection().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setViewRange(0.25f);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
            d.addScoreboardTag(TAG);
        });
        props.markers.add(label);
        entities++;
    }

    /** A glyph lying flat on a cell's floor, pointing {@code dir} (same maths as the belt chevrons). */
    private static TextDisplay flatGlyph(World world, Location corner, String glyph, TextColor color, BlockFace dir,
                                         float scale, float lift) {
        float angle = switch (dir) {
            case NORTH -> (float) (Math.PI / 2);
            case SOUTH -> (float) (-Math.PI / 2);
            case WEST -> (float) Math.PI;
            default -> 0f;
        };
        Quaternionf rotation = new Quaternionf().rotateY(angle).rotateX((float) (-Math.PI / 2));
        Vector3f shift = rotation.transform(new Vector3f(0f, 0.11f * scale, 0f));
        Vector3f translation = new Vector3f(0.5f, lift, 0.5f).sub(shift);
        return world.spawn(corner, TextDisplay.class, d -> {
            d.text(Component.text(glyph, color).decorate(TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.FIXED);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.4f);
            d.setPersistent(false);
            d.setTransformation(new Transformation(translation, rotation, new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            d.addScoreboardTag(TAG);
        });
    }

    // ------------------------------------------------------------------------------------------------
    // belt pieces: exit arrows on the lid, the sorter's chosen good floating above
    // ------------------------------------------------------------------------------------------------

    private void piece(World world, Props props, View view) {
        PlacedStructure s = view.structure();
        if (!s.type().router()) {
            return;
        }
        BlockFace front = LogisticsService.frontOf(s);
        StringBuilder key = new StringBuilder(front.name());
        for (BlockFace exit : view.exits()) {
            key.append(',').append(exit.name());
        }
        ItemStack icon = view.filterIcon();
        key.append('|').append(icon == null ? "-" : icon.getType().name());
        String k = key.toString();
        if (k.equals(props.pieceKey) && alive(props.piece)) {
            return;
        }
        killList(props.piece);
        props.pieceKey = k;
        // the grate fills y+1..y+2: arrows lie on its lid
        Location lid = new Location(world, s.x(), s.y() + 2, s.z());
        List<BlockFace> arrows = new ArrayList<>(view.exits());
        if (arrows.isEmpty()) {
            arrows.add(front); // nothing leads away yet: show which way the front is
        }
        for (BlockFace exit : arrows) {
            boolean isFront = exit == front;
            TextColor color = s.type() == StructureType.SPLITTER || isFront ? GOLD : GREY;
            TextDisplay arrow = flatGlyph(world, lid, "›", color, exit, 1.9f, 0.012f);
            // nudge it toward its edge
            Transformation t = arrow.getTransformation();
            Vector3f moved = new Vector3f(t.getTranslation()).add(exit.getModX() * 0.28f, 0f, exit.getModZ() * 0.28f);
            arrow.setTransformation(new Transformation(moved, t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            props.piece.add(arrow);
            entities++;
        }
        if (s.type() == StructureType.SORTER && icon != null) {
            ItemDisplay held = world.spawn(new Location(world, s.x() + 0.5, s.y() + 2.55, s.z() + 0.5), ItemDisplay.class, d -> {
                d.setItemStack(icon);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f),
                        new AxisAngle4f()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.4f);
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
            });
            props.piece.add(held);
            entities++;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // running animations
    // ------------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------------
    // bodies: the machine is the prop (posts, wheels, sails, chimneys, crates), drawn here
    // ------------------------------------------------------------------------------------------------

    private void body(World world, Props props, View view) {
        PlacedStructure s = view.structure();
        if (!de.aetherion.guilds.structure.Props.isProp(s.templateId()) || view.state() == MachineState.BUILDING) {
            return;
        }
        String key = s.type() + "|" + view.level() + "|" + s.templateId() + "|" + s.rot() + "|" + view.quarry();
        if (!key.equals(props.bodyKey) || !aliveParts(props.body)) {
            killParts(props.body);
            killParts(props.crates);
            if (props.hammer != null) {
                killParts(List.of(props.hammer));
                props.hammer = null;
            }
            props.bodyKey = key;
            props.lampState = null;
            props.lit = null;
            List<PropBodies.Part> parts = PropBodies.body(s.type(), view.level(), s.templateId(), view.quarry());
            int i = 0;
            for (PropBodies.Part part : parts) {
                props.body.add(new BodyPart(spawnPart(world, s, part, props.assemble, i++), part));
            }
            if (s.type() == StructureType.FORGE) {
                PropBodies.Part hammer = new PropBodies.Part("hammer", null, new ItemStack(Material.MACE),
                        new org.joml.Vector3f(0.95f, 1.32f, 0.75f), new org.joml.Vector3f(0.9f, 0.9f, 0.9f),
                        new Quaternionf(), false);
                props.hammer = new BodyPart(spawnPart(world, s, hammer, props.assemble, i), hammer);
            }
            if (props.assemble) {
                Location at = new Location(world, s.x() + 0.5, s.y() + 1.5, s.z() + 0.5);
                world.playSound(at, Sound.BLOCK_SCAFFOLDING_PLACE, SoundCategory.BLOCKS, 0.9f, 0.8f);
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.8f, 0.7f);
            }
        }
        if (s.type() == StructureType.DEPOT) {
            crates(world, props, s, view.fill());
        }
        props.assemble = false;
        // the lamp says how it's doing, from across the island
        if (view.state() != props.lampState) {
            props.lampState = view.state();
            for (BodyPart part : props.body) {
                if ("lamp".equals(part.part().role()) && part.entity() instanceof BlockDisplay lamp) {
                    lamp.setBlock(PropBodies.lampBlock(view.state()));
                }
            }
        }
        if (s.type() == StructureType.FORGE && (props.lit == null || props.lit != view.working())) {
            props.lit = view.working();
            for (BodyPart part : props.body) {
                if ("furnace".equals(part.part().role()) && part.entity() instanceof BlockDisplay furnace) {
                    furnace.setBlock(Bukkit.createBlockData("minecraft:blast_furnace[facing=south,lit=" + view.working() + "]"));
                }
            }
        }
    }

    private void crates(World world, Props props, PlacedStructure s, double fill) {
        int slots = PropBodies.crateSlots(s.level());
        int want = fill <= 0.0 ? 0 : Math.max(1, (int) Math.round(fill * slots));
        if (!aliveParts(props.crates)) {
            killParts(props.crates);
        }
        int have = props.crates.size();
        if (want == have) {
            return;
        }
        List<PropBodies.Part> all = PropBodies.crates(want, s.level());
        if (want < have) {
            while (props.crates.size() > want) {
                BodyPart last = props.crates.remove(props.crates.size() - 1);
                killParts(List.of(last));
            }
            return;
        }
        for (int i = have; i < all.size(); i++) {
            // a new crate drops onto the stack
            props.crates.add(new BodyPart(spawnPart(world, s, all.get(i), true, i - have), all.get(i)));
        }
    }

    private Display spawnPart(World world, PlacedStructure s, PropBodies.Part part, boolean assemble, int index) {
        Location corner = new Location(world, s.x(), s.y(), s.z());
        Transformation target = PropBodies.transform(part, s.rot(), null, 0f, 0f);
        Transformation start = assemble
                ? new Transformation(new org.joml.Vector3f(target.getTranslation()).add(0f, 3.2f + index * 0.08f, 0f),
                target.getLeftRotation(), new org.joml.Vector3f(target.getScale()).mul(0.15f), target.getRightRotation())
                : target;
        Display display;
        if (part.item() != null) {
            display = world.spawn(corner, ItemDisplay.class, d -> {
                d.setItemStack(part.item());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(start);
                d.setViewRange(1.0f);
                d.setShadowRadius(0f);
                d.setPersistent(false);
                if (part.bright()) {
                    d.setBrightness(new Display.Brightness(15, 15));
                }
                d.addScoreboardTag(TAG);
            });
        } else {
            display = world.spawn(corner, BlockDisplay.class, d -> {
                d.setBlock(part.block());
                d.setTransformation(start);
                d.setViewRange(1.0f);
                d.setShadowRadius(0f);
                d.setPersistent(false);
                if (part.bright()) {
                    d.setBrightness(new Display.Brightness(15, 15));
                }
                d.addScoreboardTag(TAG);
            });
        }
        entities++;
        if (assemble) {
            pending.add(new Pending(display, target, clock + 2 + index / 2, 10 + Math.min(10, index / 3)));
        }
        return display;
    }

    private long clock;

    private void animate(World world, Props props, View view, long tick) {
        PlacedStructure s = view.structure();
        boolean working = view.working();
        if (!working || props.body.isEmpty()) {
            return;
        }
        int phase = (int) ((tick / 20L) % 4L);
        for (BodyPart bp : props.body) {
            String role = bp.part().role();
            Quaternionf turn = switch (role) {
                case "wheel" -> new Quaternionf().rotateX((float) (phase * Math.PI / 2));
                case "sails" -> new Quaternionf().rotateZ((float) (-phase * Math.PI / (s.level() >= 2 ? 4 : 2)));
                case "stone" -> new Quaternionf().rotateY((float) (phase * Math.PI / 2));
                case "stone2" -> new Quaternionf().rotateY((float) (-phase * Math.PI / 2));
                case "jib" -> new Quaternionf().rotateY((float) (Math.sin(tick / 40.0) * 0.45));
                default -> null;
            };
            if (turn == null || !bp.entity().isValid()) {
                continue;
            }
            bp.entity().setInterpolationDelay(0);
            bp.entity().setInterpolationDuration(20);
            bp.entity().setTransformation(PropBodies.transform(bp.part(), s.rot(), turn, 0f, 0f));
        }
        Location center = new Location(world, s.x() + 0.5, s.y() + 1.0, s.z() + 0.5);
        switch (s.type()) {
            case MILL -> world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 0.6, 0), s.level() >= 2 ? 10 : 6,
                    0.6, 0.3, 0.6, 0.01);
            case FORGE -> {
                int[] chimney = de.aetherion.guilds.template.StateRotator.rotateXZ(-1, -1, s.rot());
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, s.x() + chimney[0] * 0.85 + 0.5, s.y() + 4.8,
                        s.z() + chimney[1] * 0.85 + 0.5, 1, 0.08, 0.1, 0.08, 0.01);
                int[] mouth = de.aetherion.guilds.template.StateRotator.rotateXZ(0, 1, s.rot());
                world.spawnParticle(Particle.FLAME, s.x() + 0.5 + mouth[0] * 0.45, s.y() + 1.6, s.z() + 0.5 + mouth[1] * 0.45,
                        s.level() >= 2 ? 6 : 3, 0.18, 0.15, 0.18, 0.01);
            }
            case QUARRY_HOUSING -> {
                int tier = PropBodies.quarryTier(view.level());
                PropBodies.Skin skin = PropBodies.skinOf(view.quarry());
                switch (skin) {
                    case FARMING -> world.spawnParticle(Particle.COMPOSTER, center.clone().add(-0.8, 0.6, 0.6), 3, 0.4, 0.2, 0.5, 0);
                    case FISHING -> {
                        world.spawnParticle(Particle.SPLASH, center.clone().add(0, 0.15, 0.4), 8, 0.4, 0.05, 0.6, 0.1);
                        world.spawnParticle(Particle.BUBBLE_POP, center.clone().add(0, 0.15, 0.4), 3, 0.3, 0.05, 0.5, 0.01);
                    }
                    case FORAGING -> world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0, 3.0 + tier * 0.35, 0),
                            6, 1.2, 0.3, 1.2, 0);
                    case COMBAT -> world.spawnParticle(Particle.CRIT, center.clone().add(0.8, 1.3, -0.6), 4, 0.3, 0.3, 0.3, 0.1);
                    default -> {
                    }
                }
                if (skin != PropBodies.Skin.MINING) {
                    return;
                }
                if (tier >= 2) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 0.9, 0), 3 + tier, 0.25, 0.2, 0.25, 0.04);
                }
                if (tier >= 3) {
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 3.8, 0), 2, 0.6, 0.3, 0.6, 0.01);
                }
            }
            default -> {
            }
        }
    }

    /** Every tick: parts flying into place, the forge hammer and bellows, the quarry drill. */
    void tick(long tick) {
        clock = tick;
        if (!pending.isEmpty()) {
            Iterator<Pending> it = pending.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (p.due() > tick) {
                    continue;
                }
                it.remove();
                if (p.entity().isValid()) {
                    p.entity().setInterpolationDelay(0);
                    p.entity().setInterpolationDuration(p.duration());
                    p.entity().setTransformation(p.to());
                }
            }
        }
        if (hosts.isEmpty() || tick % 2L != 0L) {
            return;
        }
        int phase = (int) (tick % 20L);
        for (Map<UUID, Props> host : hosts.values()) {
            for (Props props : host.values()) {
                if (!props.working || props.body.isEmpty()) {
                    continue;
                }
                switch (props.type) {
                    case QUARRY_HOUSING -> {
                        if (phase % 10 == 0) {
                            props.drillDown = !props.drillDown;
                            moveRole(props, "drill", props.drillDown ? -0.32f : 0f, 0f, props.drillDown ? 3 : 7);
                            if (props.drillDown && sounds && phase == 0
                                    && PropBodies.skinOf(props.quarry) == PropBodies.Skin.MINING) {
                                World world = props.body.get(0).entity().getWorld();
                                world.playSound(new Location(world, props.x + 0.5, props.y + 1.5, props.z + 0.5),
                                        Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.35f, 0.7f);
                            }
                        }
                    }
                    case FORGE -> forgeBeat(props, phase);
                    default -> {
                    }
                }
            }
        }
    }

    private void forgeBeat(Props props, int phase) {
        boolean bellows = props.level >= 2 ? phase % 10 == 0 : phase == 0;
        if (bellows) {
            props.bellowsUp = !props.bellowsUp;
            moveRole(props, "bellows", 0f, props.bellowsUp ? 0.62f : 0.3f, 8);
        }
        boolean beat = phase == 0 || (props.level >= 2 && phase == 10);
        boolean lift = phase == 4 || (props.level >= 2 && phase == 14);
        if ((!beat && !lift) || props.hammer == null || !props.hammer.entity().isValid()) {
            return;
        }
        Quaternionf swing = new Quaternionf().rotateZ(beat ? -0.9f : 0.5f);
        props.hammer.entity().setInterpolationDelay(0);
        props.hammer.entity().setInterpolationDuration(beat ? 3 : 6);
        props.hammer.entity().setTransformation(PropBodies.transform(props.hammer.part(), props.rot, swing, 0f, 0f));
        if (beat) {
            Location at = props.hammer.entity().getLocation().add(0, 0, 0);
            int[] r = de.aetherion.guilds.template.StateRotator.rotateXZ(1, 1, props.rot);
            Location anvil = new Location(at.getWorld(), props.x + 0.5 + r[0] * 0.9, props.y + 1.9, props.z + 0.5 + r[1] * 0.75);
            World world = anvil.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, anvil, 6, 0.15, 0.05, 0.15, 0.1);
                world.spawnParticle(Particle.LAVA, anvil, 1, 0.05, 0.02, 0.05, 0);
                if (sounds) {
                    world.playSound(anvil, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.2f, 1.7f);
                }
            }
        }
    }

    private static void moveRole(Props props, String role, float lift, float stretch, int duration) {
        for (BodyPart bp : props.body) {
            if (role.equals(bp.part().role()) && bp.entity().isValid()) {
                bp.entity().setInterpolationDelay(0);
                bp.entity().setInterpolationDuration(duration);
                bp.entity().setTransformation(PropBodies.transform(bp.part(), props.rot, null, lift, stretch));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // sound: a soft loop when you stand close to a working machine
    // ------------------------------------------------------------------------------------------------

    private void sound(World world, Props props, Collection<Location> viewers, long tick) {
        if (!sounds || !props.working || tick - props.lastSound < 50L) {
            return;
        }
        Location at = new Location(world, props.x + 0.5, props.y + 1.5, props.z + 0.5);
        boolean close = false;
        for (Location viewer : viewers) {
            if (viewer.getWorld() == world && viewer.distanceSquared(at) < 196) {
                close = true;
                break;
            }
        }
        if (!close) {
            return;
        }
        props.lastSound = tick;
        switch (props.type) {
            case MILL -> {
                world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.22f, 0.7f);
                world.playSound(at.clone().add(0, 1.5, 0), Sound.BLOCK_WOOL_STEP, SoundCategory.BLOCKS, 0.3f, 0.6f);
            }
            case QUARRY_HOUSING -> {
                switch (PropBodies.skinOf(props.quarry)) {
                    case FARMING -> world.playSound(at, Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 0.4f, 0.9f);
                    case FISHING -> world.playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.BLOCKS, 0.3f, 1.0f);
                    case FORAGING -> world.playSound(at, Sound.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.5f, 0.8f);
                    case COMBAT -> world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.BLOCKS, 0.3f, 1.1f);
                    default -> world.playSound(at, Sound.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.35f, 0.6f);
                }
            }
            case FORGE -> world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5f, 0.9f);
            case SPLITTER, OVERFLOW, SORTER -> world.playSound(at, Sound.BLOCK_COPPER_GRATE_STEP, SoundCategory.BLOCKS,
                    0.25f, 1.4f);
            case STORAGE_HUT, DEPOT -> world.playSound(at, Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.15f, 1.2f);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------------------------------------

    private static boolean alive(List<Entity> list) {
        for (Entity entity : list) {
            if (!entity.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void remove(Entity entity) {
        if (entity != null) {
            if (entity.isValid()) {
                entity.remove();
            }
            entities = Math.max(0, entities - 1);
        }
    }

    private void killList(List<Entity> list) {
        for (Entity entity : list) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        entities = Math.max(0, entities - list.size());
        list.clear();
    }

    private static boolean aliveParts(List<BodyPart> list) {
        if (list.isEmpty()) {
            return false;
        }
        for (BodyPart part : list) {
            if (!part.entity().isValid()) {
                return false;
            }
        }
        return true;
    }

    private void killParts(List<BodyPart> list) {
        for (BodyPart part : list) {
            if (part.entity().isValid()) {
                part.entity().remove();
            }
        }
        entities = Math.max(0, entities - list.size());
        try {
            list.clear();
        } catch (UnsupportedOperationException ignored) {
            // a one-off List.of(...)
        }
    }

    private void kill(Props props) {
        remove(props.tag);
        props.tag = null;
        killList(props.markers);
        killList(props.piece);
        killParts(props.body);
        killParts(props.crates);
        if (props.hammer != null) {
            killParts(List.of(props.hammer));
            props.hammer = null;
        }
        props.pulsing.clear();
    }

    void clearHost(String hostKey) {
        Map<UUID, Props> shown = hosts.remove(hostKey);
        if (shown != null) {
            for (Props props : shown.values()) {
                kill(props);
            }
        }
    }

    /** One structure changed shape or went away: drop its props, they come back next second. */
    void forget(String hostKey, UUID structureId) {
        Map<UUID, Props> shown = hosts.get(hostKey);
        Props props = shown == null ? null : shown.remove(structureId);
        if (props != null) {
            kill(props);
        }
    }

    void clearAll() {
        for (Map<UUID, Props> shown : hosts.values()) {
            for (Props props : shown.values()) {
                kill(props);
            }
        }
        hosts.clear();
        entities = 0;
    }

    int entityCount() {
        return entities;
    }
}
