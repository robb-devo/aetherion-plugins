package de.aetherion.guilds.island;

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
 * its name in it at eye height. Walk through one and its claim screen opens (a second "yes" there, the choice is
 * for good). A banner behind them says what to do, and a newcomer gets it as a title once. Walking through is
 * caught as a crossing of the gate's plane (a sprint can't skip it), and whoever steps off the dock's edge is
 * set back on it instead of falling. Display entities only (nothing is built into the dock), never saved,
 * rebuilt when they go missing. Config {@code starter-gates.*}.
 */
public final class StarterGates {

    /** How each starter's gate looks: frame, cap, the glass in the arch, its line under the name. */
    private record Look(String frame, String cap, Material glass, String color, String tagline, Particle.DustOptions dust) {
    }

    private static final Map<String, Look> LOOKS = Map.of(
            "grove", new Look("minecraft:stripped_oak_log", "minecraft:flowering_azalea_leaves",
                    Material.LIME_STAINED_GLASS, "§a", "§7Mossy woods, a lily pond, quiet",
                    new Particle.DustOptions(Color.fromRGB(0x9BE36B), 1.1f)),
            "quarry", new Look("minecraft:polished_deepslate", "minecraft:copper_block",
                    Material.ORANGE_STAINED_GLASS, "§6", "§7Rock and ore, made for long lines",
                    new Particle.DustOptions(Color.fromRGB(0xFFA54A), 1.1f)),
            "tide", new Look("minecraft:smooth_sandstone", "minecraft:prismarine_bricks",
                    Material.LIGHT_BLUE_STAINED_GLASS, "§b", "§7Sand, a lagoon and a dock",
                    new Particle.DustOptions(Color.fromRGB(0x6FD3FF), 1.1f)));

    private static final String TAG = "aeg_gate";
    private static final float HALF = 0.8f;   // half the opening's width
    private static final float POST = 0.4f;   // post thickness
    private static final float HEIGHT = 2.9f; // the opening's height

    private final JavaPlugin plugin;
    private final PersonalIslandService personal;
    private final BiConsumer<Player, StarterLayout> open;
    private final List<Entity> entities = new ArrayList<>();
    private final List<ItemDisplay> icons = new ArrayList<>();
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

    public StarterGates(JavaPlugin plugin, PersonalIslandService personal, BiConsumer<Player, StarterLayout> open) {
        this.plugin = plugin;
        this.personal = personal;
        this.open = open;
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
                player.sendActionBar(LegacyComponentSerializer.legacySection()
                        .deserialize("§7The dock ends there §8· §fwalk through a gate §7to choose your island"));
                continue;
            }
            if (flat > 30 * 30) {
                last.remove(id);
                continue;
            }
            boolean owner = personal.byOwner(id) != null;
            if (!owner && greeted.add(id)) {
                player.sendTitle("§6✦ Choose your island ✦", "§7Walk through one of the three gates", 10, 70, 20);
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f);
            }
            double[] prev = last.put(id, new double[]{at.getX(), at.getZ(), at.getY()});
            int gate = crossed(prev, at);
            if (gate < 0 || now < cool.getOrDefault(id, 0L)) {
                continue;
            }
            cool.put(id, now + 1500L);
            if (gate >= starters.size()) {
                continue;
            }
            StarterLayout starter = starters.get(gate);
            Look look = LOOKS.get(starter.id());
            double[] c = gates.get(gate);
            world.spawnParticle(Particle.END_ROD, c[0], c[1] + 1.4, c[2], 18, 0.5, 0.9, 0.5, 0.02);
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            if (owner) {
                player.sendActionBar(LegacyComponentSerializer.legacySection()
                        .deserialize("§7Your island is already yours §8· §f/island home"));
                continue;
            }
            if (look != null) {
                world.spawnParticle(Particle.DUST, c[0], c[1] + 1.4, c[2], 24, 0.5, 0.9, 0.3, 0, look.dust());
            }
            open.accept(player, starter);
        }
        if (shimmer) {
            for (int i = 0; i < gates.size() && i < starters.size(); i++) {
                Look look = LOOKS.get(starters.get(i).id());
                double[] c = gates.get(i);
                if (look != null) {
                    world.spawnParticle(Particle.DUST, c[0], c[1] + 0.3 + Math.random() * HEIGHT, c[2], 2,
                            HALF * 0.7, 0.2, 0.05, 0, look.dust());
                }
            }
        }
    }

    /**
     * Which gate (0..2) the step from {@code prev} to {@code at} went through (its plane crossed inside the
     * opening, either way), or -1. A jump of more than 3 blocks is a teleport, not a step.
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
            if ((w0 < 0.0) == (w1 < 0.0)) {
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
        double ahead = plugin.getConfig().getDouble("starter-gates.forward", 3.0);
        double spacing = plugin.getConfig().getDouble("starter-gates.spacing", 3.0);
        List<StarterLayout> starters = StarterLayout.personalChoices();
        int n = starters.size();
        double y = Math.floor(spot.getY());
        for (int i = 0; i < n; i++) {
            double u = (i - (n - 1) / 2.0) * spacing;
            double cx = spot.getX() + u * right[0] + ahead * forward[0];
            double cz = spot.getZ() + u * right[1] + ahead * forward[1];
            gates.add(new double[]{cx, y, cz});
            gate(world, starters.get(i), cx, y, cz, yaw);
        }
        // one banner behind the middle gate, high enough to clear the arches, low enough to be seen on arrival
        double bx = spot.getX() + (ahead + 1.2) * forward[0];
        double bz = spot.getZ() + (ahead + 1.2) * forward[1];
        text(world, new Location(world, bx, y + HEIGHT + 0.9, bz),
                "§6✦ §f§lChoose your island §6✦\n§7Walk through a gate. §8It's yours for good.", 1.25f, true);
    }

    private void gate(World world, StarterLayout starter, double cx, double y, double cz, float yaw) {
        Look look = LOOKS.getOrDefault(starter.id(), LOOKS.get("quarry"));
        BlockData frame = Bukkit.createBlockData(look.frame());
        // posts, lintel, a cap on top: (u0, u1, v0, v1, w0, w1) in the gate's own frame
        box(world, frame, cx, y, cz, -HALF - POST, -HALF, 0f, HEIGHT, -0.25f, 0.25f, false);
        box(world, frame, cx, y, cz, HALF, HALF + POST, 0f, HEIGHT, -0.25f, 0.25f, false);
        box(world, frame, cx, y, cz, -HALF - POST - 0.15f, HALF + POST + 0.15f, HEIGHT, HEIGHT + 0.4f, -0.32f, 0.32f, false);
        box(world, Bukkit.createBlockData(look.cap()), cx, y, cz, -0.3f, 0.3f, HEIGHT + 0.4f, HEIGHT + 0.85f, -0.3f, 0.3f, false);
        // the glass in the arch and a strip of it on the floor where you step through
        BlockData glass = look.glass().createBlockData();
        box(world, glass, cx, y, cz, -HALF, HALF, 0.02f, HEIGHT, -0.02f, 0.02f, true);
        box(world, glass, cx, y, cz, -HALF, HALF, 0.0f, 0.03f, -0.5f, 0.5f, true);
        // its icon turning in the arch, a hand's breadth in front of the glass
        ItemStack icon = new ItemStack(starter.icon());
        double fx = -0.15 * forward[0];
        double fz = -0.15 * forward[1];
        Location mid = new Location(world, cx + fx, y + 1.2, cz + fz, yaw + 180f, 0f);
        ItemDisplay item = world.spawn(mid, ItemDisplay.class, d -> {
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f),
                    new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        icons.add(item);
        entities.add(item);
        // its name and line in the arch's top, at eye height from the dock spawn
        text(world, new Location(world, cx + fx, y + 1.95, cz + fz), look.color() + "§l" + starter.display()
                + "\n" + look.tagline(), 0.62f, false);
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

    private void text(World world, Location at, String text, float scale, boolean background) {
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacySection().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(background ? Color.fromARGB(150, 18, 14, 24) : Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(!background);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        entities.add(display);
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
        icons.clear();
        gates.clear();
        dock = null;
        // gate parts left by a crash or an older build (never saved, but a chunk can hold them over a restart)
        Location spot = lobby();
        if (spot != null && spot.getWorld() != null) {
            for (Entity entity : spot.getWorld().getNearbyEntities(spot, 16, 12, 16)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }
}
