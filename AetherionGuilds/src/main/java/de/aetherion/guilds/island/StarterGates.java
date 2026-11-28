package de.aetherion.guilds.island;

import de.aetherion.core.exile.Say;
import de.aetherion.guilds.service.PersonalIslandService;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The island choice you can't miss: on the Isle Dock (where everyone without an island lands) three gates stand
 * in a row right in front of you, one per starter, each in its own colours with its icon turning in the arch and
 * its name on the lintel. Walking through one IS the choice: no screen, no second question, the island rises at
 * once (whoever already owns an island is taken home by any gate). Only a step from the arrival side through the
 * arch counts, so nobody claims by backing into a gate from behind. A header over the gates says what to do, and
 * a newcomer gets it as a title once. Every text is fixed in place like a real sign (a copy on the back for
 * whoever stands behind it), in the reader's language. Walking through is caught as a crossing of the gate's
 * plane (a sprint can't skip it), and whoever steps off the dock's edge is set back on it instead of falling.
 * Display entities only (nothing is built into the dock), never saved, rebuilt when they go missing. The dock
 * stays free of wandering animals. Config {@code starter-gates.*}.
 */
public final class StarterGates {

    /**
     * How each starter's gate looks: frame, cap, the glass in the arch, its line under the name, its sparks, the
     * floor in front of it (the column you walk through, and around it) and what stands at its feet.
     */
    private record Look(String frame, String cap, Material glass, String color, String tagline, String taglineDe,
                        Particle.DustOptions dust, Material inlay, Material inlayRim, String foot) {
    }

    private static final Map<String, Look> LOOKS = Map.of(
            "grove", new Look("minecraft:stripped_oak_log", "minecraft:flowering_azalea_leaves",
                    Material.LIME_STAINED_GLASS, "§a", "§7Mossy woods, a lily pond, quiet",
                    "§7Mooswald, ein Seerosenteich, Ruhe",
                    new Particle.DustOptions(Color.fromRGB(0x9BE36B), 1.1f),
                    Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK, "minecraft:flowering_azalea"),
            "quarry", new Look("minecraft:polished_deepslate", "minecraft:copper_block",
                    Material.ORANGE_STAINED_GLASS, "§6", "§7Rock and ore, made for long lines",
                    "§7Fels und Erz, Platz für lange Bänder",
                    new Particle.DustOptions(Color.fromRGB(0xFFA54A), 1.1f),
                    Material.WAXED_CUT_COPPER, Material.POLISHED_DEEPSLATE, "minecraft:waxed_copper_bulb[lit=true]"),
            "tide", new Look("minecraft:smooth_sandstone", "minecraft:prismarine_bricks",
                    Material.LIGHT_BLUE_STAINED_GLASS, "§b", "§7Sand, a lagoon and a dock",
                    "§7Sand, eine Lagune und ein Steg",
                    new Particle.DustOptions(Color.fromRGB(0x6FD3FF), 1.1f),
                    Material.SEA_LANTERN, Material.PRISMARINE_BRICKS, "minecraft:sea_lantern"));

    private static final String TAG = "aeg_gate";
    private static final float HALF = 1.0f;   // half the opening's width
    private static final float POST = 0.45f;  // post thickness
    private static final float HEIGHT = 3.4f; // the opening's height
    /** A light wave runs across each gate's glass every this many gate ticks (4 game ticks each). */
    private static final int WAVE = 10;

    private final JavaPlugin plugin;
    private final PersonalIslandService personal;
    private final BiConsumer<Player, StarterLayout> claim;
    private final List<Entity> entities = new ArrayList<>();
    /** The signs' two language sets: each reader sees their own (never both). */
    private final List<TextDisplay> english = new ArrayList<>();
    private final List<TextDisplay> german = new ArrayList<>();
    private final List<ItemDisplay> icons = new ArrayList<>();
    /** Per gate: the light wave over its glass, with its full size (x, y, z in world axes). */
    private final List<BlockDisplay> waves = new ArrayList<>();
    private final List<float[]> waveSizes = new ArrayList<>();
    private final IsleDock isle;
    /** Where each player stood on the last check (x, z, y), for the crossing. */
    private final Map<UUID, double[]> last = new HashMap<>();
    /** No second screen for a moment after one opened (standing in the plane, a step back and forth). */
    private final Map<UUID, Long> cool = new HashMap<>();
    private final Set<UUID> greeted = new HashSet<>();
    private final List<double[]> gates = new ArrayList<>(); // {cx, y, cz} per gate, same order as the starters
    private Location dock;
    private double[] right;   // unit vector to the player's right on the dock (axis aligned)
    private double[] forward; // unit vector the dock spawn looks along
    private long ticks;
    private float spin;

    /** {@code claim}: the player walked through this starter's gate and has no island yet. */
    public StarterGates(JavaPlugin plugin, PersonalIslandService personal, BiConsumer<Player, StarterLayout> claim) {
        this.plugin = plugin;
        this.personal = personal;
        this.claim = claim;
        this.isle = new IsleDock(plugin);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("starter-gates.enabled", true);
    }

    /** The Isle Dock spawn from Core's island host (only the island backend has one), or null. */
    private static Location lobby() {
        try {
            org.bukkit.plugin.Plugin core = Bukkit.getPluginManager().getPlugin("AetherionCore");
            if (core instanceof de.aetherion.core.AetherionCore aetherion && aetherion.islandsHost() != null) {
                return aetherion.islandsHost().lobby();
            }
        } catch (LinkageError | RuntimeException ignored) {
            // an older Core: no gates, the dock keeps its plain text
        }
        return null;
    }

    /** Every 4 ticks: keep the gates standing, spin the icons, open a gate's screen for whoever walks through. */
    public void tick() {
        ticks++;
        if (!enabled()) {
            if (!entities.isEmpty()) {
                remove();
            }
            return;
        }
        if (dock == null || ticks % 75 == 0) {
            greeted.removeIf(id -> Bukkit.getPlayer(id) == null); // back another day: greeted again
            last.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            cool.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            Location spot = lobby();
            if (spot == null || spot.getWorld() == null) {
                return;
            }
            if (dock == null || !sameSpot(dock, spot) || !alive()) {
                build(spot);
            }
        }
        if (dock == null || dock.getWorld() == null) {
            return;
        }
        World world = dock.getWorld();
        if (ticks % 25 == 0) {
            signs(world);
        }
        if (ticks % 50 == 0) {
            quiet(world);
        }
        if (ticks % 10 == 0) {
            spin += (float) (Math.PI / 2);
            for (ItemDisplay icon : icons) {
                if (icon.isValid()) {
                    Transformation t = icon.getTransformation();
                    icon.setInterpolationDelay(0);
                    icon.setInterpolationDuration(40);
                    icon.setTransformation(new Transformation(t.getTranslation(), new Quaternionf().rotateY(spin),
                            t.getScale(), t.getRightRotation()));
                }
            }
        }
        List<StarterLayout> starters = StarterLayout.personalChoices();
        boolean shimmer = ticks % 3 == 0;
        long now = System.currentTimeMillis();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            UUID id = player.getUniqueId();
            double flat = (at.getX() - dock.getX()) * (at.getX() - dock.getX()) + (at.getZ() - dock.getZ()) * (at.getZ() - dock.getZ());
            if (flat > 48 * 48) {
                last.remove(id);
                continue;
            }
            if (at.getY() < dock.getY() - 6.0 && !player.isFlying()
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                // off the dock's edge: back on it, facing the gates, before the void gets a say
                last.remove(id);
                player.setFallDistance(0f);
                player.teleport(dock);
                player.playSound(dock, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(Say.t(player,
                        "§7The dock ends there §8· §fwalk through a gate §7to choose your island",
                        "§7Hier endet der Steg §8· §fgeh durch ein Tor§7, um deine Insel zu wählen")));
                continue;
            }
            if (flat > 30 * 30) {
                last.remove(id);
                continue;
            }
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                last.remove(id);
                continue; // a camera, a staff member looking around: never a choice
            }
            boolean owner = personal.byOwner(id) != null;
            if (!owner && greeted.add(id)) {
                player.sendTitle(Say.t(player, "§6✦ Choose your island ✦", "§6✦ Wähle deine Insel ✦"),
                        Say.t(player, "§7Walk through a gate: it's yours for good",
                                "§7Geh durch ein Tor: sie gehört dir für immer"), 10, 80, 20);
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f);
            }
            double[] prev = last.put(id, new double[]{at.getX(), at.getZ(), at.getY()});
            int gate = crossed(prev, at);
            if (gate < 0 || now < cool.getOrDefault(id, 0L)) {
                continue;
            }
            cool.put(id, now + 2500L);
            if (gate >= starters.size()) {
                continue;
            }
            StarterLayout starter = starters.get(gate);
            Look look = LOOKS.get(starter.id());
            double[] c = gates.get(gate);
            world.spawnParticle(Particle.END_ROD, c[0], c[1] + 1.4, c[2], 18, 0.5, 0.9, 0.5, 0.02);
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            if (look != null) {
                world.spawnParticle(Particle.DUST, c[0], c[1] + 1.4, c[2], 24, 0.5, 0.9, 0.3, 0, look.dust());
            }
            if (owner) {
                // every gate leads home once you have an island
                last.remove(id);
                personal.goHome(player);
                continue;
            }
            last.remove(id);
            claim.accept(player, starter);
        }
        for (int i = 0; i < gates.size() && i < starters.size(); i++) {
            Look look = LOOKS.get(starters.get(i).id());
            double[] c = gates.get(i);
            if (look == null) {
                continue;
            }
            // two strands of sparks winding up through the arch, in the island's colour
            double climb = (ticks * 0.17 + i * 1.1) % HEIGHT;
            for (int strand = 0; strand < 2; strand++) {
                double a = ticks * 0.55 + strand * Math.PI + i;
                double u = Math.cos(a) * HALF * 0.78;
                double w = Math.sin(a) * 0.22;
                world.spawnParticle(Particle.DUST, c[0] + u * right[0] + w * forward[0], c[1] + 0.25 + climb,
                        c[2] + u * right[1] + w * forward[1], 1, 0, 0, 0, 0, look.dust());
            }
            if (shimmer && Math.random() < 0.35) {
                world.spawnParticle(Particle.END_ROD, c[0], c[1] + 0.4 + Math.random() * (HEIGHT - 0.6), c[2], 1,
                        HALF * 0.5, 0.1, 0.05, 0.004);
            }
        }
        wave();
    }

    /** The light wave: small in the middle of the glass, it grows to the arch's edges, then starts again. */
    private void wave() {
        long step = ticks % WAVE;
        if (step != 0 && step != 1) {
            return;
        }
        for (int i = 0; i < waves.size(); i++) {
            BlockDisplay wave = waves.get(i);
            if (!wave.isValid()) {
                continue;
            }
            float[] full = waveSizes.get(i);
            float k = step == 0 ? 0.04f : 1.0f;
            wave.setInterpolationDelay(0);
            wave.setInterpolationDuration(step == 0 ? 0 : (WAVE - 1) * 4);
            wave.setTransformation(new Transformation(
                    new Vector3f(-full[0] * k / 2f, full[1] * (1f - k) / 2f, -full[2] * k / 2f), new AxisAngle4f(),
                    new Vector3f(full[0] * k, full[1] * k, full[2] * k), new AxisAngle4f()));
        }
    }

    /**
     * Which gate (0..2) the step from {@code prev} to {@code at} went through: its plane crossed inside the opening
     * from the arrival side (the choice is made by walking in, never by backing through from behind), or -1. A jump
     * of more than 3 blocks is a teleport, not a step.
     */
    private int crossed(double[] prev, Location at) {
        if (prev == null) {
            return -1;
        }
        double mx = at.getX() - prev[0];
        double mz = at.getZ() - prev[1];
        if (mx * mx + mz * mz > 9.0 || mx * mx + mz * mz < 1.0E-6) {
            return -1;
        }
        for (int i = 0; i < gates.size(); i++) {
            double[] c = gates.get(i);
            double u0 = (prev[0] - c[0]) * right[0] + (prev[1] - c[2]) * right[1];
            double w0 = (prev[0] - c[0]) * forward[0] + (prev[1] - c[2]) * forward[1];
            double u1 = (at.getX() - c[0]) * right[0] + (at.getZ() - c[2]) * right[1];
            double w1 = (at.getX() - c[0]) * forward[0] + (at.getZ() - c[2]) * forward[1];
            if (!(w0 < 0.0 && w1 >= 0.0)) {
                continue;
            }
            double t = w0 / (w0 - w1);
            double u = u0 + t * (u1 - u0);
            if (Math.abs(u) <= HALF && at.getY() >= c[1] - 1.0 && at.getY() <= c[1] + HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameSpot(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < 0.25
                && Math.abs(a.getYaw() - b.getYaw()) < 1f;
    }

    private boolean alive() {
        if (entities.isEmpty()) {
            return false;
        }
        for (Entity entity : entities) {
            if (!entity.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void build(Location spot) {
        remove();
        dock = spot.clone();
        World world = spot.getWorld();
        // the gates stand square to the way the dock spawn looks (snapped to the nearest side)
        float yaw = Math.round(spot.getYaw() / 90f) * 90f;
        double rad = Math.toRadians(yaw);
        forward = new double[]{Math.round(-Math.sin(rad)), Math.round(Math.cos(rad))};
        right = new double[]{-forward[1], forward[0]};
        double ahead = Math.round(plugin.getConfig().getDouble("starter-gates.forward", 7.0));
        double spacing = Math.round(plugin.getConfig().getDouble("starter-gates.spacing", 4.0));
        List<StarterLayout> starters = StarterLayout.personalChoices();
        int n = starters.size();
        double y = Math.floor(spot.getY());
        double[][] frame = new double[n][];
        Material[][] inlays = new Material[n][];
        for (int i = 0; i < n; i++) {
            double u = Math.round((i - (n - 1) / 2.0) * spacing);
            Look look = LOOKS.getOrDefault(starters.get(i).id(), LOOKS.get("quarry"));
            frame[i] = new double[]{u, ahead};
            inlays[i] = new Material[]{look.inlay(), look.inlayRim()};
        }
        // the floating isle under it all (real blocks, once), then the gates on it
        int[] f = {(int) forward[0], (int) forward[1]};
        int[] r = {(int) right[0], (int) right[1]};
        String[] feet = new String[n];
        for (int i = 0; i < n; i++) {
            feet[i] = LOOKS.getOrDefault(starters.get(i).id(), LOOKS.get("quarry")).foot();
        }
        isle.ensure(spot, f, r, frame, inlays, feet);
        for (int i = 0; i < n; i++) {
            double cx = spot.getX() + frame[i][0] * right[0] + ahead * forward[0];
            double cz = spot.getZ() + frame[i][0] * right[1] + ahead * forward[1];
            gates.add(new double[]{cx, y, cz});
            gate(world, starters.get(i), cx, y, cz, yaw);
        }
        // the one line you need, as a header over the three gates: fixed like a sign, clear of every gate's own name,
        // and nothing hangs between you and the gates
        double hx = spot.getX() + (ahead - 0.36) * forward[0];
        double hz = spot.getZ() + (ahead - 0.36) * forward[1];
        sign(world, hx, y + HEIGHT + 1.62, hz, "§6✦ §f§lChoose your island §6✦", "§6✦ §f§lWähle deine Insel §6✦", 1.25f,
                true);
        sign(world, hx, y + HEIGHT + 1.3, hz, "§7Walk through a gate §8· §7it's yours for good",
                "§7Geh durch ein Tor §8· §7sie gehört dir für immer", 0.72f, true);
        double kx = spot.getX() + (ahead + 0.36) * forward[0];
        double kz = spot.getZ() + (ahead + 0.36) * forward[1];
        sign(world, kx, y + HEIGHT + 1.62, kz, "§6✦ §f§lIsle Dock §6✦", "§6✦ §f§lInsel-Steg §6✦", 1.25f, false);
        signs(world);
    }

    private void gate(World world, StarterLayout starter, double cx, double y, double cz, float yaw) {
        Look look = LOOKS.getOrDefault(starter.id(), LOOKS.get("quarry"));
        BlockData frame = Bukkit.createBlockData(look.frame());
        float outer = HALF + POST;
        // feet, posts, a lintel with a heavier top, the arch's inner corners cut round, a cap with its crown:
        // (u0, u1, v0, v1, w0, w1) in the gate's own frame
        box(world, frame, cx, y, cz, -outer - 0.12f, -HALF + 0.06f, 0f, 0.4f, -0.36f, 0.36f, false);
        box(world, frame, cx, y, cz, HALF - 0.06f, outer + 0.12f, 0f, 0.4f, -0.36f, 0.36f, false);
        box(world, frame, cx, y, cz, -outer, -HALF, 0f, HEIGHT, -0.27f, 0.27f, false);
        box(world, frame, cx, y, cz, HALF, outer, 0f, HEIGHT, -0.27f, 0.27f, false);
        box(world, frame, cx, y, cz, -HALF, -HALF + 0.32f, HEIGHT - 0.32f, HEIGHT, -0.22f, 0.22f, false);
        box(world, frame, cx, y, cz, HALF - 0.32f, HALF, HEIGHT - 0.32f, HEIGHT, -0.22f, 0.22f, false);
        box(world, frame, cx, y, cz, -outer - 0.2f, outer + 0.2f, HEIGHT, HEIGHT + 0.42f, -0.34f, 0.34f, false);
        box(world, frame, cx, y, cz, -outer + 0.1f, outer - 0.1f, HEIGHT + 0.42f, HEIGHT + 0.6f, -0.26f, 0.26f, false);
        BlockData cap = Bukkit.createBlockData(look.cap());
        box(world, cap, cx, y, cz, -0.34f, 0.34f, HEIGHT + 0.6f, HEIGHT + 1.02f, -0.3f, 0.3f, false);
        box(world, cap, cx, y, cz, -outer - 0.12f, -outer + 0.24f, HEIGHT + 0.42f, HEIGHT + 0.78f, -0.18f, 0.18f, false);
        box(world, cap, cx, y, cz, outer - 0.24f, outer + 0.12f, HEIGHT + 0.42f, HEIGHT + 0.78f, -0.18f, 0.18f, false);
        // the glass in the arch, lit, and the light wave that keeps running over it
        BlockData glass = look.glass().createBlockData();
        box(world, glass, cx, y, cz, -HALF, HALF, 0.02f, HEIGHT, -0.03f, 0.03f, true);
        wave(world, cx, y, cz);
        // its icon turning in the arch, a hand's breadth in front of the glass
        ItemStack icon = new ItemStack(starter.icon());
        double fx = -0.2 * forward[0];
        double fz = -0.2 * forward[1];
        Location mid = new Location(world, cx + fx, y + 1.35, cz + fz, yaw + 180f, 0f);
        ItemDisplay item = world.spawn(mid, ItemDisplay.class, d -> {
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.3f, 1.3f, 1.3f),
                    new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        icons.add(item);
        entities.add(item);
        // its name plate, fixed flat on the lintel toward the dock spawn (dark, so it reads against the sky), and
        // again on the back for whoever stands behind the gates
        String name = look.color() + "§l" + starter.display();
        sign(world, cx - 0.36 * forward[0], y + HEIGHT + 0.03, cz - 0.36 * forward[1], name + "\n" + look.tagline(),
                name + "\n" + look.taglineDe(), 0.7f, true);
        sign(world, cx + 0.36 * forward[0], y + HEIGHT + 0.03, cz + 0.36 * forward[1], name + "\n" + look.tagline(),
                name + "\n" + look.taglineDe(), 0.7f, false);
    }

    /** The wave: a sheet of white glass just in front of the gate's glass (sized and moved by {@link #wave()}). */
    private void wave(World world, double cx, double y, double cz) {
        float wx = (float) (Math.abs(right[0]) * 2 * HALF + Math.abs(forward[0]) * 0.02);
        float wz = (float) (Math.abs(right[1]) * 2 * HALF + Math.abs(forward[1]) * 0.02);
        float wy = HEIGHT - 0.04f;
        Location at = new Location(world, cx - 0.06 * forward[0], y + 0.04, cz - 0.06 * forward[1]);
        BlockDisplay wave = world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-wx * 0.02f, wy * 0.48f, -wz * 0.02f), new AxisAngle4f(),
                    new Vector3f(wx * 0.04f, wy * 0.04f, wz * 0.04f), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        waves.add(wave);
        waveSizes.add(new float[]{wx, wy, wz});
        entities.add(wave);
    }

    /** An axis-aligned box in the gate's frame (u right, v up, w forward) as one block display. */
    private void box(World world, BlockData data, double cx, double y, double cz, float u0, float u1, float v0, float v1,
                     float w0, float w1, boolean lit) {
        double ax = cx + u0 * right[0] + w0 * forward[0];
        double bx = cx + u1 * right[0] + w1 * forward[0];
        double az = cz + u0 * right[1] + w0 * forward[1];
        double bz = cz + u1 * right[1] + w1 * forward[1];
        double minX = Math.min(ax, bx);
        double minZ = Math.min(az, bz);
        float sx = (float) Math.abs(bx - ax);
        float sz = (float) Math.abs(bz - az);
        Location corner = new Location(world, minX, y + v0, minZ);
        BlockDisplay display = world.spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(sx, v1 - v0, sz),
                    new AxisAngle4f()));
            d.setBrightness(lit ? new Display.Brightness(15, 15) : new Display.Brightness(9, 15));
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        entities.add(display);
    }

    /**
     * A sign: text fixed in place like a painted board (it never turns with the reader, so nothing swings into a
     * gate's frame at an angle), once in English and once in German at the same spot. {@code front}: it faces the
     * dock spawn; else it faces away from it (the back of the gates). Each reader is shown their language's copy.
     */
    private void sign(World world, double x, double y, double z, String en, String de, float scale, boolean front) {
        // a text display reads from the side its yaw looks at: toward the spawn is against "forward"
        double dx = front ? -forward[0] : forward[0];
        double dz = front ? -forward[1] : forward[1];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        english.add(text(world, new Location(world, x, y, z, yaw, 0f), en, scale));
        german.add(text(world, new Location(world, x, y, z, yaw, 0f), de, scale));
    }

    private TextDisplay text(World world, Location at, String text, float scale) {
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacySection().deserialize(text));
            d.setBillboard(Display.Billboard.FIXED);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(190, 16, 13, 22));
            d.setLineWidth(300);
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        entities.add(display);
        return display;
    }

    /** Every reader on the dock sees the signs in their own language (checked about once a second). */
    private void signs(World world) {
        for (Player player : world.getPlayers()) {
            boolean de = Say.german(player);
            for (TextDisplay display : german) {
                show(player, display, de);
            }
            for (TextDisplay display : english) {
                show(player, display, !de);
            }
        }
    }

    private void show(Player player, TextDisplay display, boolean visible) {
        if (!display.isValid()) {
            return;
        }
        if (visible && !player.canSee(display)) {
            player.showEntity(plugin, display);
        } else if (!visible && player.canSee(display)) {
            player.hideEntity(plugin, display);
        }
    }

    private boolean quietAsked;

    /**
     * The dock is a place to choose in peace: no ambient wildlife is put there (Items is asked once, if it knows
     * how), and whatever wandered or was spawned in anyway is taken off it. Named pets and anything a plugin
     * tagged as its own NPC stay.
     */
    private void quiet(World world) {
        if (!quietAsked) {
            quietAsked = true;
            try {
                Class.forName("de.aetherion.items.world.WildlifeLooks").getMethod("quietWorld", String.class)
                        .invoke(null, world.getName());
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // an older Items: the sweep below keeps the dock clear
            }
        }
        for (Entity entity : world.getNearbyEntities(dock, 40, 24, 40)) {
            if (!(entity instanceof org.bukkit.entity.Mob mob) || mob.getScoreboardTags().contains("NPC")) {
                continue;
            }
            if (mob instanceof org.bukkit.entity.Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
                continue; // somebody's own animal (a camel counts as tame from birth: only an owner makes it a pet)
            }
            if (mob.getPersistentDataContainer().has(de.aetherion.core.AetherKeys.PET_ENTITY)) {
                continue; // a companion that came along with its owner (wild pets are kept off the dock by AetherMobs)
            }
            mob.remove();
        }
    }

    public void forgetPlayer(UUID id) {
        last.remove(id);
        cool.remove(id);
        greeted.remove(id);
    }

    /** Takes every gate down (shutdown, or switched off). */
    public void remove() {
        for (Entity entity : entities) {
            entity.remove();
        }
        entities.clear();
        english.clear();
        german.clear();
        icons.clear();
        waves.clear();
        waveSizes.clear();
        gates.clear();
        dock = null;
        // gate parts left by a crash or an older build (never saved, but a chunk can hold them over a restart)
        Location spot = lobby();
        if (spot != null && spot.getWorld() != null) {
            for (Entity entity : spot.getWorld().getNearbyEntities(spot, 24, 14, 24)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }
}
