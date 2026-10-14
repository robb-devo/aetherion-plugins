package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The walk from the Borderlands cliff to the Veil Quay: a railed stone causeway out through the Veil curtain to
 * the pier head beside the Veil lamp (the islet's walkable deck starts 64 blocks west of its origin; the rest of
 * the schem is the mooring chain hanging into the void).
 *
 * <p>{@code /exile causeway}: stand on the cliff where the walk should leave it. Air only (it never replaces a
 * block, so it cannot eat the cliff or the quay), every block is recorded, {@code /exile causeway undo} takes
 * exactly those back. The start is kept in {@code exile.quay.causeway} so a quay re-paste can lay it again.
 */
public final class ExileCauseway {

    /**
     * Where the walk meets the deck, relative to the quay origin: off the pier head's west edge, lamp side.
     * Floor z 4..6, rails z 3 and 7: clear of the barge's departure line (rel z -2..2).
     */
    public static final int[] ENTRY = {-65, 0, 5};
    private static final int MAX_LENGTH = 220;
    private static final int POST_EVERY = 8;
    private static final double FLAT_HEAD = 3.0;
    private static final double FLAT_TAIL = 5.0;
    private static final String STORE = "exile-causeway.yml";

    private ExileCauseway() {
    }

    record Leg(double ax, double az, double bx, double bz, double len, double startArc,
               boolean first, boolean last) {
    }

    record Hit(double arc, double v, double u, Leg leg) {
    }

    // ------------------------------------------------------------------ build

    /** Lay the causeway from {@code start} (feet on the cliff) to the quay. */
    public static boolean build(AetherionCore plugin, CommandSender sender, Location start) {
        ExileQuay quay = plugin.exileQuay();
        if (quay == null || !quay.placed()) {
            tell(sender, "§cPlace the Veil Quay first (§f/exile setquay§c).");
            return false;
        }
        World w = start.getWorld();
        int[] o = quay.origin();
        if (w == null || !w.equals(quay.world())) {
            tell(sender, "§cStand in the quay's world (§f" + quay.world().getName() + "§c).");
            return false;
        }
        if (!loadPlaced(plugin).isEmpty()) {
            tell(sender, "§cA causeway is already laid. §f/exile causeway undo§c first.");
            return false;
        }
        int sx = start.getBlockX();
        int sy = start.getBlockY();
        int sz = start.getBlockZ();
        int ex = o[0] + ENTRY[0];
        int ey = o[1] + ENTRY[1];
        int ez = o[2] + ENTRY[2];
        if (sx > ex - 8) {
            tell(sender, "§cStand on the Borderlands side, west of the pier head (x < " + (ex - 8) + ").");
            return false;
        }
        Plan plan = plan(sx, sy, sz, ex, ey, ez);
        if (plan == null) {
            return false;
        }
        double total = plan.total();
        if (total > MAX_LENGTH) {
            tell(sender, "§cThat's " + (int) total + " blocks of causeway (max " + MAX_LENGTH + "). Walk closer to the quay.");
            return false;
        }
        double drop = ey - sy;
        if (total - FLAT_HEAD - FLAT_TAIL < 3.0 * Math.abs(drop)) {
            tell(sender, "§cToo steep: " + (int) Math.abs(drop) + " blocks of height over " + (int) total
                    + " blocks. Start higher or lower on the cliff (deck walk level is y=" + ey + ").");
            return false;
        }
        List<Leg> legs = plan.legs();
        Map<Long, Double> floor = plan.floor();
        Map<Long, Double> rail = plan.rail();
        Map<Long, Hit> railHit = plan.railHit();

        // ---- place (air only)
        List<String> placed = new ArrayList<>();
        Set<Long> walls = new HashSet<>();
        for (Map.Entry<Long, Double> e : floor.entrySet()) {
            int x = kx(e.getKey());
            int z = kz(e.getKey());
            double hh = e.getValue();
            if (hh == Math.floor(hh)) {
                Material m = ((x + z) & 1) == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                put(w, x, (int) hh - 1, z, m.createBlockData(), placed);
            } else {
                Slab slab = (Slab) Material.STONE_BRICK_SLAB.createBlockData();
                slab.setType(Slab.Type.BOTTOM);
                put(w, x, (int) Math.floor(hh), z, slab, placed);
            }
        }
        Map<Long, Integer> wallY = new HashMap<>();
        for (Map.Entry<Long, Double> e : rail.entrySet()) {
            int x = kx(e.getKey());
            int z = kz(e.getKey());
            int top = (int) Math.ceil(e.getValue());
            put(w, x, top - 1, z, Material.STONE_BRICKS.createBlockData(), placed);
            if (put(w, x, top, z, Material.STONE_BRICK_WALL.createBlockData(), placed)) {
                walls.add(e.getKey());
                wallY.put(e.getKey(), top);
            }
        }
        // posts: every POST_EVERY blocks of walk, both sides (warm by the cliff, soul-cold into the Veil)
        Map<String, Long> posts = posts(railHit, walls);
        Set<Long> postCells = new HashSet<>(posts.values());
        fixWalls(w, walls, wallY, postCells);
        // the sign stands on the first right-hand post off the cliff (the very first may be in the terrain)
        String headPost = null;
        for (int k = 0; k * POST_EVERY <= total && headPost == null; k++) {
            if (posts.containsKey(k + "R")) {
                headPost = k + "R";
            }
        }
        Location signAt = null;
        double[] dir0 = {legs.get(0).bx() - legs.get(0).ax(), legs.get(0).bz() - legs.get(0).az()};
        for (Map.Entry<String, Long> p : posts.entrySet()) {
            long cell = p.getValue();
            int x = kx(cell);
            int z = kz(cell);
            int y = wallY.get(cell) + 1;
            if (p.getKey().equals(headPost)) {
                Rotatable sign = (Rotatable) Material.DARK_OAK_SIGN.createBlockData();
                sign.setRotation(facing(-dir0[0], -dir0[1]));
                if (put(w, x, y, z, sign, placed)) {
                    signAt = new Location(w, x, y, z);
                }
                continue;
            }
            Hit h = railHit.get(cell);
            Lantern lantern = (Lantern) (h.arc() < total * 0.5 ? Material.LANTERN : Material.SOUL_LANTERN).createBlockData();
            lantern.setHanging(false);
            put(w, x, y, z, lantern, placed);
        }
        if (signAt != null) {
            writeSign(signAt.getBlock());
        }

        savePlaced(plugin, w, placed);
        plugin.getConfig().set("exile.quay.causeway", w.getName() + " " + sx + " " + sy + " " + sz);
        plugin.saveConfig();
        plugin.getLogger().info("Veil Quay causeway laid from " + sx + " " + sy + " " + sz + " (" + (int) total
                + " blocks, " + placed.size() + " placed)");
        tell(sender, "§8Veil Quay §8» §aCauseway laid §7(" + (int) total + " blocks of walk, " + placed.size()
                + " blocks placed, air only). §f/exile causeway undo §7takes it back.");
        return true;
    }

    /** The causeway's columns, pure geometry: walk level (feet y) per floor and rail column. */
    record Plan(List<Leg> legs, double total, Map<Long, Double> floor, Map<Long, Double> rail, Map<Long, Hit> railHit) {
    }

    static Plan plan(int sx, int sy, int sz, int ex, int ey, int ez) {
        // A straight run when the cliff point lines up with the lamp; otherwise one dogleg just off the cliff.
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {sx + 0.5, sz + 0.5});
        if (Math.abs(sz - ez) > 1) {
            double bend = sx + 4.5;
            pts.add(new double[] {bend, sz + 0.5});
            pts.add(new double[] {bend, ez + 0.5});
        } else {
            pts.set(0, new double[] {sx + 0.5, ez + 0.5});
        }
        pts.add(new double[] {ex + 0.5, ez + 0.5});
        List<Leg> legs = new ArrayList<>();
        double arc = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (len < 1.0e-6) {
                continue;
            }
            legs.add(new Leg(a[0], a[1], b[0], b[1], len, arc, legs.isEmpty(), i + 2 == pts.size()));
            arc += len;
        }
        if (legs.isEmpty()) {
            return null;
        }
        double total = arc;
        int minX = Math.min(sx, ex) - 4;
        int maxX = Math.max(sx, ex) + 4;
        int minZ = Math.min(sz, ez) - 4;
        int maxZ = Math.max(sz, ez) + 4;
        Map<Long, Double> floor = new HashMap<>();
        Map<Long, Double> rail = new HashMap<>();
        Map<Long, Hit> railHit = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                Hit floorAt = null;
                Hit railAt = null;
                for (Leg leg : legs) {
                    Hit h = project(leg, cx, cz);
                    // floor 3 wide; joints overlap by one so corners fill; the last leg runs onto the deck notch
                    double lo = leg.first() ? -0.01 : -1.01;
                    double hi = leg.len() + 1.01;
                    if (Math.abs(h.v()) <= 1.01 && h.u() >= lo && h.u() <= hi
                            && (floorAt == null || Math.abs(h.v()) < Math.abs(floorAt.v()))) {
                        floorAt = h;
                    }
                    // rails one further out; open toward the cliff, capped round corners, up to the deck edge
                    double rlo = leg.first() ? -0.01 : -2.01;
                    double rhi = leg.last() ? leg.len() + 1.01 : leg.len() + 2.01;
                    if (Math.abs(h.v()) <= 2.01 && h.u() >= rlo && h.u() <= rhi && railAt == null) {
                        railAt = h;
                    }
                }
                long key = key(x, z);
                if (floorAt != null) {
                    floor.put(key, level(floorAt.arc(), sy, ey, total));
                } else if (railAt != null) {
                    rail.put(key, level(railAt.arc(), sy, ey, total));
                    railHit.put(key, railAt);
                }
            }
        }
        return new Plan(legs, total, floor, rail, railHit);
    }

    /** One post per side every POST_EVERY blocks of walk ("{k}L" / "{k}R"), among the rail cells that got a wall. */
    static Map<String, Long> posts(Map<Long, Hit> railHit, Set<Long> walls) {
        Map<String, Long> posts = new HashMap<>();
        Map<String, Double> postErr = new HashMap<>();
        for (Map.Entry<Long, Hit> e : railHit.entrySet()) {
            if (!walls.contains(e.getKey())) {
                continue;
            }
            Hit h = e.getValue();
            int k = (int) Math.round(h.arc() / POST_EVERY);
            double err = Math.abs(h.arc() - k * POST_EVERY);
            if (err > 0.75 || Math.abs(Math.abs(h.v()) - 2.0) > 0.6) {
                continue;
            }
            String id = k + (h.v() > 0 ? "R" : "L");
            if (!postErr.containsKey(id) || err < postErr.get(id)) {
                postErr.put(id, err);
                posts.put(id, e.getKey());
            }
        }
        return posts;
    }

    /** After a quay re-paste (its clear takes the pad, causeway included): lay it again from the saved start. */
    public static void relay(AetherionCore plugin, CommandSender sender) {
        String raw = plugin.getConfig().getString("exile.quay.causeway", "");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] p = raw.trim().split("\\s+");
        if (p.length < 4) {
            return;
        }
        World w = Bukkit.getWorld(p[0]);
        if (w == null) {
            return;
        }
        try {
            Location start = new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            undo(plugin, null); // whatever survived the clear (the cliff end), so the record stays exact
            build(plugin, sender, start);
        } catch (NumberFormatException ignored) {
        }
    }

    /** Remove exactly what the last build placed (only where the block is still what was put there). */
    public static void undo(AetherionCore plugin, CommandSender sender) {
        File file = new File(plugin.getDataFolder(), STORE);
        if (!file.isFile()) {
            tell(sender, "§7No causeway recorded.");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World w = Bukkit.getWorld(yaml.getString("world", ""));
        if (w == null) {
            tell(sender, "§cCauseway world not loaded.");
            return;
        }
        int removed = 0;
        List<String> list = yaml.getStringList("blocks");
        for (int i = list.size() - 1; i >= 0; i--) {
            String[] p = list.get(i).split(" ");
            if (p.length < 4) {
                continue;
            }
            try {
                Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (b.getType().name().equals(p[3])) {
                    b.setType(Material.AIR, false);
                    removed++;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        forget(plugin);
        plugin.getConfig().set("exile.quay.causeway", "");
        plugin.saveConfig();
        tell(sender, "§8Veil Quay §8» §7Causeway taken back (§f" + removed + "§7 blocks).");
    }

    /** True when a causeway is recorded (the walk home exists). */
    public static boolean laid(AetherionCore plugin) {
        String raw = plugin.getConfig().getString("exile.quay.causeway", "");
        return raw != null && !raw.isBlank();
    }

    // ------------------------------------------------------------------ geometry

    private static Hit project(Leg leg, double cx, double cz) {
        double dx = (leg.bx() - leg.ax()) / leg.len();
        double dz = (leg.bz() - leg.az()) / leg.len();
        double rx = cx - leg.ax();
        double rz = cz - leg.az();
        double u = rx * dx + rz * dz;
        double v = rx * -dz + rz * dx;
        double arc = leg.startArc() + Math.max(0.0, Math.min(leg.len(), u));
        return new Hit(arc, v, u, leg);
    }

    /** Walk level (feet y) at {@code arc}: flat off the cliff, an even ramp, flat onto the pier; half-block steps. */
    private static double level(double arc, int startY, int endY, double total) {
        double ramp = total - FLAT_HEAD - FLAT_TAIL;
        double f = ramp <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (arc - FLAT_HEAD) / ramp));
        double y = startY + (endY - startY) * f;
        return Math.round(y * 2.0) / 2.0;
    }

    /** Standing-sign rotation whose front faces along (tx, tz). */
    private static BlockFace facing(double tx, double tz) {
        BlockFace[] ring = {BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
                BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
                BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};
        double deg = Math.toDegrees(Math.atan2(-tx, tz));
        int r = (int) Math.round(deg / 22.5);
        return ring[Math.floorMod(r, 16)];
    }

    /** Walls placed without physics keep their default shape: join them to each other by hand. */
    private static void fixWalls(World w, Set<Long> walls, Map<Long, Integer> wallY, Set<Long> posts) {
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (long cell : walls) {
            int x = kx(cell);
            int z = kz(cell);
            int y = wallY.get(cell);
            Block b = w.getBlockAt(x, y, z);
            if (!(b.getBlockData() instanceof Wall wall)) {
                continue;
            }
            boolean[] joined = new boolean[4];
            for (int i = 0; i < 4; i++) {
                Block n = b.getRelative(sides[i]);
                joined[i] = n.getBlockData() instanceof Wall || n.getType().isOccluding();
                wall.setHeight(sides[i], joined[i] ? Wall.Height.LOW : Wall.Height.NONE);
            }
            boolean straight = (joined[0] && joined[2] && !joined[1] && !joined[3])
                    || (joined[1] && joined[3] && !joined[0] && !joined[2]);
            wall.setUp(posts.contains(cell) || !straight);
            b.setBlockData(wall, false);
        }
    }

    private static void writeSign(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        front.line(0, Component.text("VEIL QUAY", NamedTextColor.BLACK));
        front.line(1, Component.text("the King's Ferry", NamedTextColor.DARK_GRAY));
        front.line(2, Component.text("straight on,", NamedTextColor.DARK_GRAY));
        front.line(3, Component.text("into the fog", NamedTextColor.DARK_GRAY));
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private static boolean put(World w, int x, int y, int z, BlockData data, List<String> placed) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) {
            return false;
        }
        b.setBlockData(data, false);
        placed.add(x + " " + y + " " + z + " " + data.getMaterial().name());
        return true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int kx(long key) {
        return (int) (key >> 32);
    }

    private static int kz(long key) {
        return (int) key;
    }

    // ------------------------------------------------------------------ store

    private static List<String> loadPlaced(AetherionCore plugin) {
        File file = new File(plugin.getDataFolder(), STORE);
        if (!file.isFile()) {
            return List.of();
        }
        return YamlConfiguration.loadConfiguration(file).getStringList("blocks");
    }

    private static void savePlaced(AetherionCore plugin, World w, List<String> placed) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", w.getName());
        yaml.set("blocks", placed);
        try {
            yaml.save(new File(plugin.getDataFolder(), STORE));
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save " + STORE + ": " + ex.getMessage());
        }
    }

    private static void forget(AetherionCore plugin) {
        File file = new File(plugin.getDataFolder(), STORE);
        if (file.isFile() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + STORE);
        }
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
