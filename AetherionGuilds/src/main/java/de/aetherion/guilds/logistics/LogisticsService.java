package de.aetherion.guilds.logistics;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Satisfactory-lite island logistics.
 *
 * <ul>
 *   <li><b>Belts</b> are conveyor tiles (an iron plate on the ground, cargo gliding on top) laid with the Belt
 *   Layer. Each tile has a direction; a tile whose next cell is inside a sink/processor footprint delivers into
 *   it; tiles chain, merge freely, never split. Old rail belts are swapped for plates on first visit.</li>
 *   <li><b>Routes</b> are resolved once per change: every producer (quarry housing chute, mill/forge chute)
 *   follows its belt to the first machine it runs into, or to a dead end.</li>
 *   <li><b>Flow</b> is plain arithmetic on buffers, the same for online and offline islands: quarries catch up
 *   (existing math), their storage drains into the route target, processors convert with a speed limit and
 *   push on, sinks fill to capacity. Back-pressure: a full target leaves stock upstream, so a quarry with
 *   nowhere to send stops at its cap exactly like before.</li>
 *   <li><b>Visuals</b> only exist while someone is on the island: conveyor dressing on the plates (belt, rails,
 *   rollers, chevrons that glide while goods move, see {@link BeltVisuals}), gliding cargo, a turning millstone,
 *   forge fire, a working drill. Islands nobody visits cost one catch-up per minute.</li>
 * </ul>
 */
public final class LogisticsService {

    static final String FX_TAG = "aeg_fx";
    /** The conveyor tile block. */
    public static final Material BELT_BLOCK = Material.IRON_TRAPDOOR;
    /** Cargo rides on the belt surface of {@link BeltVisuals} (plate 3/16 + belt). */
    private static final double CARGO_Y = 0.36;

    public static final class Route {
        private final PlacedStructure from;
        private final List<Belt> path;
        private final PlacedStructure to;
        /** Side of the producer this belt leaves from (belt pieces have several exits). */
        private final BlockFace face;
        private long moved;
        private Res lastRes;
        private long lastSpawn;

        Route(PlacedStructure from, List<Belt> path, PlacedStructure to, BlockFace face) {
            this.from = from;
            this.path = path;
            this.to = to;
            this.face = face;
        }

        public BlockFace face() {
            return face;
        }

        /** For a belt piece: is this its front exit? */
        public boolean front() {
            return face != null && face == frontOf(from);
        }

        public PlacedStructure from() {
            return from;
        }

        public PlacedStructure to() {
            return to;
        }

        public int length() {
            return path.size();
        }

        public boolean deadEnd() {
            return to == null;
        }

        public long moved() {
            return moved;
        }

        public Res lastRes() {
            return lastRes;
        }
    }

    private static final class Net {
        final Map<Long, Belt> belts = new LinkedHashMap<>();
        long lastFlow;
        boolean routesDirty = true;
        final List<Route> routes = new ArrayList<>();
        final Map<UUID, Route> routeFrom = new HashMap<>();
        final List<PlacedStructure> processorOrder = new ArrayList<>();
        final Map<UUID, Long> receivedAt = new HashMap<>();
        /** Every exit of every producer (belt pieces have up to three). */
        final Map<UUID, List<Route>> exits = new HashMap<>();
        /** Last time a producer put something on a belt. */
        final Map<UUID, Long> sentAt = new HashMap<>();
        /** Smoothed goods per second leaving / arriving at each structure (for "▸ 240/min"). */
        final Map<UUID, Double> outRate = new HashMap<>();
        final Map<UUID, Double> inRate = new HashMap<>();
        /** Bumped whenever tiles change, so the conveyor dressing re-merges its runs. */
        int version;
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final MinionService minions;
    private final File file;
    private final Map<String, Net> nets = new HashMap<>();
    private final Map<String, Map<Long, String>> worldIndex = new HashMap<>();
    private final CargoVisuals cargo = new CargoVisuals();
    private final BeltVisuals beltVisuals = new BeltVisuals();
    private final MachineFx machineFx = new MachineFx();
    private final Map<UUID, UUID> fxEntities = new HashMap<>();
    private final Set<String> activeHosts = new HashSet<>();
    private final Set<String> prepared = new HashSet<>();
    private boolean dirty;
    private long tickCounter;
    private BeltPass beltPass;

    /**
     * The island guide's starter belt kit: while it's handed out, the first belt needs no Workshop and its first
     * tiles are free. Everything else about laying belts stays the same.
     */
    public interface BeltPass {
        boolean noWorkshopNeeded(Player player, IslandHost host);

        int freeTiles(IslandHost host);

        void useFree(IslandHost host, int tiles);
    }

    public void setBeltPass(BeltPass pass) {
        this.beltPass = pass;
    }

    public BeltPass beltPass() {
        return beltPass;
    }

    public LogisticsService(JavaPlugin plugin, HostService hosts, StructureService structures, MinionService minions) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.minions = minions;
        this.file = new File(plugin.getDataFolder(), "island_logistics.yml");
        cargo.caps(plugin.getConfig().getInt("logistics.cargo-per-island", 36),
                plugin.getConfig().getInt("logistics.cargo-global", 240));
        beltVisuals.configure(plugin.getConfig().getBoolean("logistics.conveyor-visuals", true),
                plugin.getConfig().getInt("logistics.conveyor-radius", 40),
                plugin.getConfig().getInt("logistics.conveyor-runs-per-island", 80),
                Math.max(3000, plugin.getConfig().getInt("logistics.conveyor-entities-global", 3000)));
        machineFx.configure(plugin.getConfig().getBoolean("logistics.machine-fx", true),
                plugin.getConfig().getBoolean("logistics.machine-sounds", true),
                plugin.getConfig().getInt("logistics.machine-fx-radius", 64),
                plugin.getConfig().getInt("logistics.machine-fx-per-island", 60),
                plugin.getConfig().getInt("logistics.machine-fx-entities-global", 3000));
        structures.onChange(this::markDirty);
    }

    public static long pos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private Net net(IslandHost host) {
        return nets.computeIfAbsent(host.key(), k -> new Net());
    }

    public void markDirty(IslandHost host) {
        if (host != null) {
            net(host).routesDirty = true;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // belt lookups
    // ------------------------------------------------------------------------------------------------

    public boolean isBelt(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        Map<Long, String> index = worldIndex.get(world.getName());
        return index != null && index.containsKey(pos(x, y, z));
    }

    /** Is (x,y,z) the block a belt tile stands on? */
    public boolean isBeltSupport(World world, int x, int y, int z) {
        return isBelt(world, x, y + 1, z);
    }

    public Belt belt(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        Map<Long, String> index = worldIndex.get(world.getName());
        String hostKey = index == null ? null : index.get(pos(x, y, z));
        if (hostKey == null) {
            return null;
        }
        Net net = nets.get(hostKey);
        return net == null ? null : net.belts.get(pos(x, y, z));
    }

    public IslandHost beltHost(World world, int x, int y, int z) {
        Map<Long, String> index = world == null ? null : worldIndex.get(world.getName());
        String key = index == null ? null : index.get(pos(x, y, z));
        return key == null ? null : IslandHost.parse(key);
    }

    public int count(IslandHost host) {
        Net net = nets.get(host.key());
        return net == null ? 0 : net.belts.size();
    }

    public int cap(IslandHost host) {
        return IslandTiers.beltCap(hosts.tier(host));
    }

    public long tileCost() {
        return Math.max(0L, plugin.getConfig().getLong("logistics.belt-coins", 5L));
    }

    // ------------------------------------------------------------------------------------------------
    // laying + removing
    // ------------------------------------------------------------------------------------------------

    /** Cells + directions of an L-shaped run from start to end (inclusive). Null when heights differ. */
    public static List<Object[]> plan(int[] start, int[] end, BlockFace fallback) {
        if (start[1] != end[1]) {
            return null;
        }
        List<Object[]> cells = new ArrayList<>();
        int dx = end[0] - start[0];
        int dz = end[2] - start[2];
        if (dx == 0 && dz == 0) {
            cells.add(new Object[]{start.clone(), fallback});
            return cells;
        }
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        int x = start[0];
        int z = start[2];
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{x, start[1], z});
        if (xFirst) {
            while (x != end[0]) {
                x += Integer.signum(dx);
                points.add(new int[]{x, start[1], z});
            }
            while (z != end[2]) {
                z += Integer.signum(dz);
                points.add(new int[]{x, start[1], z});
            }
        } else {
            while (z != end[2]) {
                z += Integer.signum(dz);
                points.add(new int[]{x, start[1], z});
            }
            while (x != end[0]) {
                x += Integer.signum(dx);
                points.add(new int[]{x, start[1], z});
            }
        }
        BlockFace last = fallback;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            BlockFace dir;
            if (i + 1 < points.size()) {
                int[] n = points.get(i + 1);
                dir = face(n[0] - p[0], n[2] - p[2]);
                last = dir;
            } else {
                dir = last;
            }
            cells.add(new Object[]{p, dir});
        }
        return cells;
    }

    private static BlockFace face(int dx, int dz) {
        if (dx > 0) {
            return BlockFace.EAST;
        }
        if (dx < 0) {
            return BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Why a belt can't go into this cell, or null. {@code continuing} = the cell already holds our belt. */
    public String cellProblem(IslandHost host, World world, int x, int y, int z, boolean continuing) {
        if (continuing) {
            return null;
        }
        if (!hosts.inBuildZone(host, x, z)) {
            return "outside your land";
        }
        if (isBelt(world, x, y, z)) {
            return "there is a belt there already";
        }
        PlacedStructure structure = structures.atColumn(world, x, z);
        if (structure != null && !(structure.type() == StructureType.QUARRY_HOUSING && structure.frameless())
                && y >= structure.minY() && y <= structure.maxY()) {
            return structure.type().router()
                    ? "that's your " + structure.label() + "; start the belt on a cell next to it"
                    : "inside your " + structure.label();
        }
        Block cell = world.getBlockAt(x, y, z);
        if (!PasteService.replaceable(cell)) {
            return "a block is in the way";
        }
        Block below = cell.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() || isBelt(world, x, y - 1, z)) {
            return "belts need solid ground";
        }
        return null;
    }

    public boolean lay(Player player, IslandHost host, int[] start, int[] end, BlockFace fallback) {
        World world = hosts.world(host);
        if (world == null) {
            return false;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "lay belts"));
            return false;
        }
        List<Object[]> cells = plan(start, end, fallback);
        if (cells == null) {
            player.sendMessage("§cBelts run flat: start and end must be at the same height.");
            return false;
        }
        int maxRun = Math.max(8, plugin.getConfig().getInt("logistics.max-run", 64));
        if (cells.size() > maxRun) {
            player.sendMessage("§cThat run is " + cells.size() + " long; lay at most " + maxRun + " at once.");
            return false;
        }
        Net net = net(host);
        Set<String> linkedBefore = linked(host);
        int fresh = 0;
        for (Object[] cell : cells) {
            int[] p = (int[]) cell[0];
            boolean continuing = net.belts.containsKey(pos(p[0], p[1], p[2]));
            String problem = cellProblem(host, world, p[0], p[1], p[2], continuing);
            if (problem != null) {
                player.sendMessage("§cCan't lay there §8(" + p[0] + " " + p[1] + " " + p[2] + ")§c: " + problem + ".");
                return false;
            }
            if (!continuing) {
                fresh++;
            }
        }
        if (count(host) + fresh > cap(host)) {
            player.sendMessage("§cBelt limit: §f" + cap(host) + " §ctiles at Island Tier " + hosts.tier(host)
                    + ". Raise the tier for more.");
            return false;
        }
        int free = beltPass == null ? 0 : Math.max(0, Math.min(fresh, beltPass.freeTiles(host)));
        long price = tileCost() * (fresh - free);
        if (!hosts.charge(player, host, price)) {
            return false;
        }
        if (free > 0) {
            beltPass.useFree(host, free);
        }
        Map<Long, String> index = worldIndex.computeIfAbsent(world.getName(), k -> new HashMap<>());
        List<Belt> touched = new ArrayList<>();
        for (Object[] cell : cells) {
            int[] p = (int[]) cell[0];
            BlockFace dir = (BlockFace) cell[1];
            long key = pos(p[0], p[1], p[2]);
            Belt belt = net.belts.get(key);
            if (belt == null) {
                belt = new Belt(p[0], p[1], p[2], dir);
                net.belts.put(key, belt);
                index.put(key, host.key());
            } else {
                belt.setDir(dir);
            }
            touched.add(belt);
        }
        for (Belt belt : touched) {
            refreshAround(world, net, belt);
        }
        net.routesDirty = true;
        net.version++;
        dirty = true;
        Belt first = touched.get(0);
        world.playSound(new Location(world, first.x() + 0.5, first.y(), first.z() + 0.5), Sound.BLOCK_CHAIN_PLACE, 0.9f, 1.1f);
        // the run rolls out tile by tile, a soft clack every few
        for (int i = 0; i < touched.size(); i++) {
            Belt b = touched.get(i);
            int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.CRIT, b.x() + 0.5, b.y() + 0.25, b.z() + 0.5, 3, 0.2, 0.04, 0.2, 0.02);
                world.spawnParticle(Particle.WAX_OFF, b.x() + 0.5, b.y() + 0.3, b.z() + 0.5, 1, 0.15, 0.02, 0.15, 0.0);
                if (step % 3 == 0) {
                    world.playSound(new Location(world, b.x() + 0.5, b.y(), b.z() + 0.5), Sound.BLOCK_IRON_TRAPDOOR_CLOSE,
                            0.35f, 1.4f + (step % 9) * 0.05f);
                }
            }, 1L + i);
        }
        player.sendMessage("§a" + fresh + " belt tile" + (fresh == 1 ? "" : "s") + " laid"
                + (price > 0 ? " §8(§6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host) + "§8)" : "")
                + (free > 0 ? " §8(§e" + free + " free, starter kit§8)" : "")
                + "§a. §7" + count(host) + "/" + cap(host) + " tiles.");
        connections(player, host, world, linkedBefore);
        return true;
    }

    /** "from>to" for every line that reaches a machine right now. */
    private Set<String> linked(IslandHost host) {
        Set<String> out = new HashSet<>();
        for (Route route : routes(host)) {
            if (route.to() != null) {
                out.add(route.from().id() + ">" + route.to().id());
            }
        }
        return out;
    }

    /**
     * The plug-in moment: a belt that just made a line reach a machine clunks into its port, sparks, and the
     * first goods ride it at once. A run that ends in the open gets a one-line nudge instead.
     */
    private boolean lastLayTalked;

    /** Did the last belt lay already put something on the action bar (connected / ends in the open)? */
    public boolean lastLayTalked() {
        return lastLayTalked;
    }

    private void connections(Player player, IslandHost host, World world, Set<String> before) {
        boolean any = false;
        lastLayTalked = false;
        for (Route route : routes(host)) {
            if (route.to() == null || before.contains(route.from().id() + ">" + route.to().id())) {
                continue;
            }
            any = true;
            lastLayTalked = true;
            Location port = route.path.isEmpty()
                    ? new Location(world, route.to().x() + 0.5, route.to().y() + 1.2, route.to().z() + 0.5)
                    : new Location(world, route.path.get(route.path.size() - 1).nextX() + 0.5,
                    route.path.get(route.path.size() - 1).y() + 0.5, route.path.get(route.path.size() - 1).nextZ() + 0.5);
            world.spawnParticle(Particle.ELECTRIC_SPARK, port, 18, 0.25, 0.25, 0.25, 0.12);
            world.spawnParticle(Particle.WAX_OFF, port, 8, 0.3, 0.3, 0.3, 0.02);
            world.playSound(port, Sound.BLOCK_COPPER_BULB_TURN_ON, 1f, 1.1f);
            world.playSound(port, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 0.7f);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§a✔ Connected: §f" + label(route.from()) + " §8→ §f" + label(route.to())));
            // the first goods ride it right away (the island is lively while you stand on it)
            Res icon = route.lastRes;
            if (icon == null && route.from().type() == StructureType.QUARRY_HOUSING) {
                QuarryMinion minion = hosts.minion(host, route.from().minionId());
                icon = minion == null ? null : Res.of(minion.quarryType(), Res.Form.RAW);
            }
            if (icon != null && !route.path.isEmpty()) {
                List<Location> points = points(world, route);
                for (int i = 0; i < 3; i++) {
                    cargo.spawnLater(host.key(), points, icon.icon(), i * 6);
                }
            }
        }
        if (!any) {
            for (Route route : routes(host)) {
                if (route.to() == null && !route.path.isEmpty()) {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize("§e" + label(route.from()) + "'s belt ends in the open §7· run it onto a §agreen arrow"));
                    lastLayTalked = true;
                    break;
                }
            }
        }
    }

    public boolean remove(Player player, IslandHost host, int x, int y, int z) {
        World world = hosts.world(host);
        Net net = nets.get(host.key());
        if (world == null || net == null) {
            return false;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "take belts up"));
            return false;
        }
        Belt belt = net.belts.remove(pos(x, y, z));
        if (belt == null) {
            return false;
        }
        Map<Long, String> index = worldIndex.get(world.getName());
        if (index != null) {
            index.remove(belt.key());
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == BELT_BLOCK || block.getType() == Material.RAIL) {
            block.setType(Material.AIR, false);
        }
        hosts.refund(player, host, tileCost());
        net.routesDirty = true;
        net.version++;
        dirty = true;
        world.playSound(block.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8f, 1f);
        return true;
    }

    private void refreshAround(World world, Net net, Belt belt) {
        placeBelt(world, belt);
    }

    /** A conveyor tile: a closed iron plate on the ground, facing the way it runs (replaces old rail belts). */
    private static boolean placeBelt(World world, Belt belt) {
        Block block = world.getBlockAt(belt.x(), belt.y(), belt.z());
        if (block.getType() != BELT_BLOCK && block.getType() != Material.RAIL && !PasteService.replaceable(block)) {
            return false;
        }
        TrapDoor data = (TrapDoor) BELT_BLOCK.createBlockData();
        data.setHalf(Bisected.Half.BOTTOM);
        data.setOpen(false);
        data.setPowered(false);
        data.setFacing(belt.dir());
        if (block.getBlockData().matches(data)) {
            return false;
        }
        block.setBlockData(data, false);
        return true;
    }

    /** Re-place every belt tile from YAML (after a wipe of blocks, or an admin rebuild). */
    public int rebuild(IslandHost host) {
        World world = hosts.world(host);
        Net net = nets.get(host.key());
        if (world == null || net == null) {
            return 0;
        }
        int n = 0;
        for (Belt belt : net.belts.values()) {
            placeBelt(world, belt);
            n++;
        }
        net.routesDirty = true;
        return n;
    }

    /** First visit after a restart: old rail belts become conveyor plates, frameless quarries get housing. */
    private void prepare(IslandHost host) {
        if (!prepared.add(host.key())) {
            return;
        }
        World world = hosts.world(host);
        Net net = nets.get(host.key());
        int swapped = 0;
        int waiting = 0;
        if (world != null && net != null) {
            for (Belt belt : net.belts.values()) {
                if (!world.isChunkLoaded(belt.x() >> 4, belt.z() >> 4)) {
                    waiting++;
                } else if (world.getBlockAt(belt.x(), belt.y(), belt.z()).getType() == Material.RAIL
                        && placeBelt(world, belt)) {
                    swapped++;
                }
            }
        }
        int[] upgrade = structures.upgradeFrameless(host);
        int housed = upgrade[0];
        int props = structures.migrateToProps(host);
        if (props > 0) {
            markDirty(host);
            plugin.getLogger().info("Island elevation: " + host.key() + ": " + props + " pasted machines became props.");
        }
        if (waiting > 0 || upgrade[1] > 0) {
            prepared.remove(host.key()); // parts of the island aren't loaded yet: look again next second
        }
        if (swapped > 0 || housed > 0) {
            markDirty(host);
            plugin.getLogger().info("Island highlight: " + host.key() + ": " + swapped + " rail belts -> conveyor plates, "
                    + housed + " quarries got housing.");
        }
    }

    public void dropHost(IslandHost host) {
        Net net = nets.remove(host.key());
        cargo.clearHost(host.key());
        beltVisuals.clearHost(host.key());
        machineFx.clearHost(host.key());
        if (net == null) {
            return;
        }
        World world = hosts.world(host);
        Map<Long, String> index = world == null ? null : worldIndex.get(world.getName());
        if (index != null) {
            for (Long key : net.belts.keySet()) {
                index.remove(key);
            }
        }
        dirty = true;
    }

    // ------------------------------------------------------------------------------------------------
    // routes
    // ------------------------------------------------------------------------------------------------

    public Route routeFrom(PlacedStructure structure) {
        if (structure == null) {
            return null;
        }
        Net net = nets.get(structure.host().key());
        if (net == null) {
            return null;
        }
        if (net.routesDirty) {
            computeRoutes(structure.host(), net);
        }
        return net.routeFrom.get(structure.id());
    }

    public List<Route> routes(IslandHost host) {
        Net net = nets.get(host.key());
        if (net == null) {
            return List.of();
        }
        if (net.routesDirty) {
            computeRoutes(host, net);
        }
        return List.copyOf(net.routes);
    }

    public int feeding(PlacedStructure target) {
        int n = 0;
        for (Route route : routes(target.host())) {
            if (route.to() == target) {
                n++;
            }
        }
        return n;
    }

    private void computeRoutes(IslandHost host, Net net) {
        net.routes.clear();
        net.routeFrom.clear();
        net.exits.clear();
        net.processorOrder.clear();
        net.routesDirty = false;
        World world = hosts.world(host);
        if (world == null) {
            return;
        }
        List<PlacedStructure> all = new ArrayList<>(structures.of(host));
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (PlacedStructure structure : all) {
            if (!structure.type().hasOutput() || structure.building()) {
                continue;
            }
            if (structure.type().router()) {
                // every belt that starts next to the piece and points away from it is an exit
                for (BlockFace face : sides) {
                    int fx = structure.x() + face.getModX();
                    int fz = structure.z() + face.getModZ();
                    Belt first = net.belts.get(pos(fx, structure.y() + 1, fz));
                    if (first != null && first.dir() == face) {
                        addRoute(net, world, structure, first, face);
                        continue;
                    }
                    if (first != null) {
                        continue;
                    }
                    // flush against a hut / machine / another piece: hands straight over, no belt needed
                    PlacedStructure next = structures.atColumn(world, fx, fz);
                    if (next != null && next != structure && structures.acceptsAt(next, fx, structure.y() + 1, fz)
                            && !feedsCell(next, structure.x(), structure.z())) {
                        directRoute(net, structure, next, face);
                    }
                }
                continue;
            }
            Belt first = null;
            int[] port = structures.outputPort(structure);
            if (port == null) {
                // frameless quarry / drill rig: a belt may start on any cell next to it
                int y = structure.y() + 1;
                for (BlockFace face : sides) {
                    Belt candidate = net.belts.get(pos(structure.x() + face.getModX(), y, structure.z() + face.getModZ()));
                    if (candidate != null && candidate.dir() == face) {
                        first = candidate;
                        break;
                    }
                    if (candidate != null && first == null) {
                        first = candidate;
                    }
                }
            } else {
                first = net.belts.get(pos(port[0], port[1], port[2]));
                if (first == null) {
                    // housing raised round an old belt that started right next to the quarry
                    int[] chute = structures.chuteCell(structure);
                    first = chute == null ? null : net.belts.get(pos(chute[0], chute[1], chute[2]));
                }
            }
            if (first != null) {
                addRoute(net, world, structure, first, null);
            } else if (port != null) {
                // a belt piece set right on the chute's cell takes the goods straight from the chute
                PlacedStructure next = structures.atColumn(world, port[0], port[2]);
                if (next != null && next.type().router() && structures.acceptsAt(next, port[0], port[1], port[2])) {
                    directRoute(net, structure, next, null);
                }
            }
        }
        // processors and belt pieces in chain order (sources feed first, then what they feed, ...)
        Set<UUID> seen = new HashSet<>();
        Deque<PlacedStructure> queue = new ArrayDeque<>();
        for (Route route : net.routes) {
            if (route.from().type().role() == StructureType.Role.SOURCE && passesOn(route.to())) {
                queue.add(route.to());
            }
        }
        while (!queue.isEmpty()) {
            PlacedStructure p = queue.poll();
            if (!seen.add(p.id())) {
                continue;
            }
            net.processorOrder.add(p);
            for (Route out : net.exits.getOrDefault(p.id(), List.of())) {
                if (passesOn(out.to())) {
                    queue.add(out.to());
                }
            }
        }
        for (PlacedStructure structure : all) {
            if (passesOn(structure) && seen.add(structure.id())) {
                net.processorOrder.add(structure);
            }
        }
    }

    /** Does this structure's own chute (or exit) point at that cell? (Then handing back would loop.) */
    private boolean feedsCell(PlacedStructure structure, int x, int z) {
        int[] port = structures.outputPort(structure);
        return port != null && port[0] == x && port[2] == z;
    }

    private static void directRoute(Net net, PlacedStructure from, PlacedStructure to, BlockFace face) {
        Route route = new Route(from, new ArrayList<>(), to, face);
        net.routes.add(route);
        net.routeFrom.putIfAbsent(from.id(), route);
        net.exits.computeIfAbsent(from.id(), k -> new ArrayList<>()).add(route);
    }

    private static boolean passesOn(PlacedStructure structure) {
        return structure != null && (structure.type().role() == StructureType.Role.PROCESSOR || structure.type().router());
    }

    /** Follow a belt from its first tile to the first machine it runs into (or to its dead end). */
    private void addRoute(Net net, World world, PlacedStructure structure, Belt first, BlockFace face) {
        List<Belt> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        PlacedStructure target = null;
        Belt current = first;
        while (current != null && path.size() < 1024 && seen.add(current.key())) {
            path.add(current);
            int nx = current.nextX();
            int nz = current.nextZ();
            PlacedStructure hit = structures.atColumn(world, nx, nz);
            if (hit != null && hit != structure && structures.acceptsAt(hit, nx, current.y(), nz)) {
                target = hit;
                break;
            }
            current = net.belts.get(current.nextKey());
        }
        Route route = new Route(structure, path, target, face);
        net.routes.add(route);
        net.routeFrom.putIfAbsent(structure.id(), route);
        net.exits.computeIfAbsent(structure.id(), k -> new ArrayList<>()).add(route);
    }

    /** The way a belt piece faces (its "front" exit). */
    public static BlockFace frontOf(PlacedStructure structure) {
        return switch (Math.floorMod(structure.rot(), 4)) {
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
    }

    public static int rotOf(BlockFace face) {
        return switch (face) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** Every exit of a producer (one for quarries and machines, up to three for belt pieces). */
    public List<Route> exits(PlacedStructure structure) {
        if (structure == null) {
            return List.of();
        }
        Net net = nets.get(structure.host().key());
        if (net == null) {
            return List.of();
        }
        if (net.routesDirty) {
            computeRoutes(structure.host(), net);
        }
        return List.copyOf(net.exits.getOrDefault(structure.id(), List.of()));
    }

    // ------------------------------------------------------------------------------------------------
    // flow
    // ------------------------------------------------------------------------------------------------

    /** Every second: step the islands people are standing on, and animate them. */
    public void tickActive() {
        Set<String> now = new HashSet<>();
        Map<String, List<Location>> viewers = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hosts.isIslandWorld(player.getWorld())) {
                continue;
            }
            IslandHost host = hosts.at(player.getLocation());
            if (host != null && Math.abs(player.getLocation().getBlockX() - hosts.originX(host)) <= 128) {
                now.add(host.key());
                viewers.computeIfAbsent(host.key(), k -> new ArrayList<>()).add(player.getLocation());
            }
        }
        for (String key : new ArrayList<>(activeHosts)) {
            if (!now.contains(key)) {
                deactivate(key);
            }
        }
        activeHosts.clear();
        activeHosts.addAll(now);
        long time = System.currentTimeMillis();
        for (String key : now) {
            IslandHost host = IslandHost.parse(key);
            if (host == null || !hosts.exists(host)) {
                continue;
            }
            prepare(host);
            step(host, time);
            animate(host);
            dressBelts(host, viewers.getOrDefault(key, List.of()));
            dressMachines(host, viewers.getOrDefault(key, List.of()));
        }
    }

    /** Conveyor dressing near the people on this island; chevrons glide on runs that carried goods this step. */
    private void dressBelts(IslandHost host, List<Location> near) {
        Net net = nets.get(host.key());
        World world = hosts.world(host);
        if (net == null || world == null || net.belts.isEmpty()) {
            beltVisuals.clearHost(host.key());
            return;
        }
        Set<Long> flowing = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Route route : net.routes) {
            boolean moving = route.to() != null && flowing(net, route, now);
            if (moving) {
                for (Belt belt : route.path) {
                    flowing.add(belt.key());
                }
            }
        }
        // where belts plug into a machine (copper hood) and where they start under a chute (orange lip)
        Set<Long> plugEnds = new HashSet<>();
        Set<Long> chuteStarts = new HashSet<>();
        for (Route route : net.routes) {
            if (route.path.isEmpty()) {
                continue;
            }
            if (route.to() != null) {
                plugEnds.add(route.path.get(route.path.size() - 1).key());
            }
            if (!route.from().type().router()) {
                chuteStarts.add(route.path.get(0).key());
            }
        }
        beltVisuals.render(host.key(), world, net.belts, net.version, near, flowing, plugEnds, chuteStarts, tickCounter);
    }

    /**
     * Is this line carrying goods right now? Quarries hand over in steps (every minion interval), so a line that
     * moved goods within the last step still counts as moving: belts glide and cargo flows between the steps
     * instead of twitching once every ten seconds.
     */
    private boolean flowing(Net net, Route route, long now) {
        if (route.moved > 0L) {
            return true;
        }
        long sent = net.sentAt.getOrDefault(route.from().id(), 0L);
        if (route.from().type().role() == StructureType.Role.PROCESSOR && now - route.from().lastWork() < flowWindow()) {
            return true;
        }
        return route.lastRes != null && now - sent < flowWindow();
    }

    /** How long a line counts as busy after goods last moved: one quarry step plus a little slack. */
    private long flowWindow() {
        return Math.max(1, plugin.getConfig().getInt("minion-interval-seconds", 10)) * 1000L + 2500L;
    }

    /** Status tags, port markers and running animations for the machines near the people on this island. */
    private void dressMachines(IslandHost host, List<Location> near) {
        World world = hosts.world(host);
        Net net = nets.get(host.key());
        if (world == null || near.isEmpty()) {
            machineFx.clearHost(host.key());
            return;
        }
        long now = System.currentTimeMillis();
        List<MachineFx.View> views = new ArrayList<>();
        Map<UUID, Double> feeds = feedPerMinute(host);
        for (PlacedStructure s : structures.of(host)) {
            StructureType type = s.type();
            if (type == StructureType.PROJECT) {
                continue;
            }
            if (type == StructureType.WORKSHOP) {
                views.add(new MachineFx.View(s, MachineState.LECTERN, hubTitle(s), hubBoard(host), !s.building(),
                        null, null, List.of(), List.of(), null, false, s.maxY() + 1.2, 0.0, s.level(), null));
                continue;
            }
            MachineState state = status(s);
            int[] outCell = null;
            BlockFace outDir = null;
            List<int[]> in = new ArrayList<>();
            boolean needsOut = state == MachineState.NEEDS_BELT_OUT
                    || (type.role() == StructureType.Role.SOURCE && state == MachineState.NEEDS_BELT);
            if (needsOut) {
                if (type.router()) {
                    BlockFace front = frontOf(s);
                    outCell = new int[]{s.x() + front.getModX(), s.y() + 1, s.z() + front.getModZ()};
                    outDir = front;
                } else {
                    int[] port = structures.outputPort(s);
                    if (port != null) {
                        outCell = port;
                        int[] f = de.aetherion.guilds.template.StateRotator.rotateXZ(0, 1, s.rot());
                        outDir = f[0] > 0 ? BlockFace.EAST : f[0] < 0 ? BlockFace.WEST : f[1] > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
                    }
                }
            }
            if (state == MachineState.NEEDS_BELT_IN) {
                if (type.router()) {
                    BlockFace front = frontOf(s);
                    addIn(host, world, in, s.x() - front.getModX(), s.y() + 1, s.z() - front.getModZ(), front);
                } else {
                    int[] port = structures.outputPort(s);
                    int y = s.y() + 1;
                    int[][] sides = {
                            {s.x(), s.minZ() - 1, BlockFace.SOUTH.ordinal()},
                            {s.x(), s.maxZ() + 1, BlockFace.NORTH.ordinal()},
                            {s.minX() - 1, s.z(), BlockFace.EAST.ordinal()},
                            {s.maxX() + 1, s.z(), BlockFace.WEST.ordinal()}};
                    for (int[] side : sides) {
                        int cx = side[0];
                        int cz = side[1];
                        if (side[2] == BlockFace.SOUTH.ordinal() || side[2] == BlockFace.NORTH.ordinal()) {
                            cx = s.x();
                        } else {
                            cz = s.z();
                        }
                        if (port != null && port[0] == cx && port[2] == cz) {
                            continue;
                        }
                        addIn(host, world, in, cx, y, cz, BlockFace.values()[side[2]]);
                    }
                }
            }
            List<BlockFace> exitFaces = new ArrayList<>();
            if (type.router()) {
                for (Route route : exits(s)) {
                    if (route.face() != null) {
                        exitFaces.add(route.face());
                    }
                }
            }
            long sent = net == null ? 0L : net.sentAt.getOrDefault(s.id(), 0L);
            QuarryMinion digger = type == StructureType.QUARRY_HOUSING ? hosts.minion(host, s.minionId()) : null;
            boolean working = switch (type.role()) {
                case PROCESSOR -> now - s.lastWork() < flowWindow();
                // a quarry digs (wheel turns, drill pumps) until it is full, belt or not
                case SOURCE -> digger != null && digger.rawEquivalent() < digger.cap();
                case ROUTER -> now - sent < flowWindow();
                case SINK -> state == MachineState.RECEIVING;
                default -> false;
            };
            // world labels show the steady rate the quarries feed (from their levels), never a jumpy average
            long feed = Math.round(feeds.getOrDefault(s.id(), 0.0));
            long used = PlacedStructure.total(s.store());
            String sub = switch (type.role()) {
                case SINK -> fillBar(used, s.capacity())
                        + (feed > 0 ? "\n§7▸ fed " + perMinute(feed) + fullIn(s.capacity() - used, feed) : "");
                case PROCESSOR -> (type == StructureType.FORGE ? "§8Compressed → Compacted" : "§8Raw → Compressed")
                        + (feed > 0 ? "\n§7▸ fed " + perMinute(feed) : "");
                case ROUTER -> (type == StructureType.SORTER
                        ? (s.filter() == null ? "§8front: nothing picked" : "§8front: §f" + filterName(s.filter()))
                        : type == StructureType.OVERFLOW ? "§8front first, then sides" : "§8shares evenly")
                        + (feed > 0 ? "\n§7▸ " + perMinute(feed) + " through" : "");
                default -> null;
            };
            boolean tag = type.role() != StructureType.Role.SOURCE;
            double tagY = type.router() ? s.y() + (type == StructureType.SORTER ? 3.35 : 2.75)
                    : de.aetherion.guilds.structure.Props.isProp(s.templateId())
                    ? s.y() + 1.0 + PropBodies.height(type, s.level(), s.templateId()) + 0.45
                    : s.maxY() + 1.0;
            double fill = s.capacity() <= 0 ? 0.0 : Math.min(1.0, used / (double) s.capacity());
            int level = s.level();
            de.aetherion.guilds.model.QuarryType quarryType = null;
            if (type == StructureType.QUARRY_HOUSING) {
                QuarryMinion minion = hosts.minion(host, s.minionId());
                level = minion == null ? 1 : minion.level(); // the quarry's look follows the quarry's level
                quarryType = minion == null ? null : minion.quarryType(); // ...and its trade picks the skin
            }
            views.add(new MachineFx.View(s, state, "§f" + label(s), sub, tag, outCell, outDir, in, exitFaces,
                    type == StructureType.SORTER ? filterIcon(s.filter()) : null, working, tagY, fill, level, quarryType));
        }
        machineFx.render(host.key(), world, views, near, tickCounter);
    }

    // ------------------------------------------------------------------------------------------------
    // steady rates: what the quarries dig per minute (their level, the minion interval), followed down the lines
    // ------------------------------------------------------------------------------------------------

    private int intervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("minion-interval-seconds", 10));
    }

    /** A quarry's dig rate per minute (rock solid: level and interval only). */
    public long digPerMinute(QuarryMinion minion) {
        return minion == null ? 0L : minion.quarryType().perMinute(minion.level(), intervalSeconds());
    }

    /**
     * Goods per minute each machine is fed, following every quarry's dig rate down its line: splitters share it,
     * overflow gates send it front, sorters by their pick, mills and depots pass it on.
     */
    public Map<UUID, Double> feedPerMinute(IslandHost host) {
        Map<UUID, Double> in = new HashMap<>();
        for (Route route : routes(host)) {
            if (route.from().type().role() != StructureType.Role.SOURCE || route.to() == null) {
                continue;
            }
            QuarryMinion minion = hosts.minion(host, route.from().minionId());
            if (minion == null) {
                continue;
            }
            flow(route.to(), digPerMinute(minion), Res.of(minion.quarryType(), Res.Form.RAW), in, 0, new HashSet<>());
        }
        return in;
    }

    private void flow(PlacedStructure node, double rate, Res res, Map<UUID, Double> in, int depth, Set<UUID> path) {
        if (node == null || rate <= 0.0 || depth > 24 || !path.add(node.id())) {
            return;
        }
        in.merge(node.id(), rate, Double::sum);
        List<Route> live = new ArrayList<>();
        for (Route route : exits(node)) {
            if (route.to() != null) {
                live.add(route);
            }
        }
        if (!live.isEmpty()) {
            if (node.type().router()) {
                List<Route> front = new ArrayList<>();
                List<Route> sides = new ArrayList<>();
                for (Route route : live) {
                    (route.front() ? front : sides).add(route);
                }
                List<Route> take = switch (node.type()) {
                    case OVERFLOW -> front.isEmpty() ? sides : front;
                    case SORTER -> node.filterMatches(res) ? front : sides;
                    default -> live;
                };
                for (Route route : take) {
                    flow(route.to(), rate / take.size(), res, in, depth + 1, path);
                }
            } else if (node.type().role() == StructureType.Role.PROCESSOR || node.type() == StructureType.DEPOT) {
                flow(live.get(0).to(), rate, res, in, depth + 1, path);
            }
        }
        path.remove(node.id());
    }

    /** " · full in 3h 20m" at the rate it is fed. */
    private static String fullIn(long free, long perMinute) {
        if (perMinute <= 0 || free <= 0) {
            return free <= 0 ? " §c· full" : "";
        }
        long minutes = free / perMinute;
        if (minutes > 60L * 24 * 30) {
            return "";
        }
        String time = minutes >= 1440 ? (minutes / 1440) + "d " + (minutes % 1440 / 60) + "h"
                : minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : Math.max(1, minutes) + "m";
        return " §8· full in " + time;
    }

    // ------------------------------------------------------------------------------------------------
    // the Hub board (holotable over the Workshop): every line on the island at a glance
    // ------------------------------------------------------------------------------------------------

    private java.util.function.Function<IslandHost, String> hubFooter = host -> null;

    /** The board's last line (the next factory goal), supplied by the goals. */
    public void setHubFooter(java.util.function.Function<IslandHost, String> footer) {
        this.hubFooter = footer == null ? host -> null : footer;
    }

    public static String hubName(int level) {
        return switch (level) {
            case 2 -> "Foreman's Hall";
            case 3 -> "Grand Hub";
            default -> "Hub";
        };
    }

    private String hubTitle(PlacedStructure workshop) {
        return "§6§l✦ " + hubName(workshop.level()).toUpperCase(java.util.Locale.ROOT) + " ✦";
    }

    /** One line per quarry: where its goods end up and how that line is doing. */
    public String hubBoard(IslandHost host) {
        StringBuilder out = new StringBuilder("§e✎ Right-click the lectern");
        int shown = 0;
        List<Route> lines = new ArrayList<>();
        for (Route route : routes(host)) {
            if (route.from().type().role() == StructureType.Role.SOURCE) {
                lines.add(route);
            }
        }
        for (Route route : lines) {
            if (shown >= 6) {
                out.append("\n§8…and ").append(lines.size() - 6).append(" more lines");
                break;
            }
            shown++;
            MachineState state = status(route.from());
            StringBuilder chain = new StringBuilder("§f").append(label(route.from()));
            PlacedStructure at = route.to();
            Set<UUID> seen = new HashSet<>();
            int hops = 0;
            while (at != null && hops++ < 4 && seen.add(at.id())) {
                chain.append(" §8→ §7").append(label(at));
                Route next = null;
                for (Route exit : exits(at)) {
                    if (exit.to() != null) {
                        next = exit;
                        break;
                    }
                }
                at = next == null ? null : next.to();
            }
            if (route.to() == null) {
                chain.append(" §8→ §c✖");
            }
            out.append("\n").append(state.color()).append("● ").append(chain);
        }
        if (lines.isEmpty()) {
            out.append("\n§7No lines yet: quarry → belt → hut.");
        }
        String footer = hubFooter.apply(host);
        if (footer != null) {
            out.append("\n").append(footer);
        }
        return out.toString();
    }

    private void addIn(IslandHost host, World world, List<int[]> in, int x, int y, int z, BlockFace into) {
        if (!hosts.inBuildZone(host, x, z) || isBelt(world, x, y, z) || structures.atColumn(world, x, z) != null) {
            return;
        }
        if (!PasteService.replaceable(world.getBlockAt(x, y, z)) || !world.getBlockAt(x, y - 1, z).getType().isSolid()) {
            return;
        }
        in.add(new int[]{x, y, z, into.ordinal()});
    }

    static String fillBar(long used, long cap) {
        int pct = cap <= 0 ? 0 : (int) Math.min(100, used * 100 / cap);
        int filled = Math.min(10, pct / 10);
        String color = pct >= 100 ? "§c" : pct >= 80 ? "§e" : "§a";
        return color + "▮".repeat(filled) + "§8" + "▮".repeat(10 - filled) + " §f" + pct + "%";
    }

    /** "Coal", "anything Compressed"... */
    public static String filterName(String filter) {
        if (filter == null) {
            return "nothing";
        }
        if (filter.startsWith("type:")) {
            try {
                return de.aetherion.guilds.model.QuarryType.valueOf(filter.substring(5)).productName();
            } catch (IllegalArgumentException ignored) {
                return filter.substring(5);
            }
        }
        if (filter.startsWith("form:")) {
            return switch (filter.substring(5)) {
                case "RAW" -> "anything Raw";
                case "COMPRESSED" -> "anything Compressed";
                case "COMPACTED" -> "anything Compacted";
                default -> filter.substring(5);
            };
        }
        Res res = Res.parse(filter);
        return res == null ? filter : res.display();
    }

    public static ItemStack filterIcon(String filter) {
        if (filter == null) {
            return new ItemStack(Material.BARRIER);
        }
        if (filter.startsWith("type:")) {
            try {
                return Res.of(de.aetherion.guilds.model.QuarryType.valueOf(filter.substring(5)), Res.Form.RAW).icon();
            } catch (IllegalArgumentException ignored) {
                return new ItemStack(Material.PAPER);
            }
        }
        if (filter.startsWith("form:")) {
            try {
                return Res.of(de.aetherion.guilds.model.QuarryType.COBBLESTONE, Res.Form.valueOf(filter.substring(5))).icon();
            } catch (IllegalArgumentException ignored) {
                return new ItemStack(Material.PAPER);
            }
        }
        Res res = Res.parse(filter);
        return res == null ? new ItemStack(Material.PAPER) : res.icon();
    }

    /** Every tick: glide cargo. */
    public void tickCargo() {
        tickCounter++;
        cargo.tick();
        beltVisuals.tick(tickCounter);
        machineFx.tick(tickCounter);
    }

    /** Catch-up for everything (minute timer and shutdown). */
    public void stepAll() {
        long time = System.currentTimeMillis();
        for (IslandHost host : hosts.all()) {
            if (!activeHosts.contains(host.key())) {
                step(host, time);
            }
        }
    }

    public boolean isActive(IslandHost host) {
        return host != null && activeHosts.contains(host.key());
    }

    public void step(IslandHost host, long now) {
        Net net = nets.get(host.key());
        boolean hasMachines = false;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type().role() == StructureType.Role.PROCESSOR || structure.type().role() == StructureType.Role.SINK) {
                hasMachines = true;
                break;
            }
        }
        if (net == null || net.belts.isEmpty() || !hasMachines) {
            return;
        }
        if (net.routesDirty) {
            computeRoutes(host, net);
        }
        long dt = net.lastFlow <= 0L ? 1000L : Math.max(0L, Math.min(now - net.lastFlow, 7L * 24 * 3_600_000L));
        net.lastFlow = now;
        double seconds = dt / 1000.0;
        boolean itemsOk = AetherionItemsAccess.available();
        boolean changed = false;
        for (Route route : net.routes) {
            route.moved = 0L;
        }
        // 1) quarries -> their route target
        for (Route route : net.routes) {
            if (route.from().type().role() != StructureType.Role.SOURCE || route.to() == null) {
                continue;
            }
            QuarryMinion minion = hosts.minion(host, route.from().minionId());
            if (minion == null) {
                continue;
            }
            minions.catchUp(minion);
            long moved = pushMinion(minion, route, now, net);
            if (moved > 0L) {
                changed = true;
            }
        }
        // 2) processors and belt pieces in chain order: convert / decide, then push on
        for (PlacedStructure processor : net.processorOrder) {
            if (processor.type().router()) {
                if (distribute(processor, net, now)) {
                    changed = true;
                }
                continue;
            }
            if (process(processor, seconds, itemsOk, now)) {
                changed = true;
            }
            Route out = net.routeFrom.get(processor.id());
            if (out != null && out.to() != null) {
                long moved = pushBuffer(processor.output(), out, now, net);
                if (moved > 0L) {
                    changed = true;
                    net.sentAt.put(processor.id(), now);
                }
            }
        }
        // 3) depots with a belt led away from their chute pass their stock on (a buffer in the line)
        for (PlacedStructure depot : structures.of(host)) {
            if (depot.type() != StructureType.DEPOT || depot.store().isEmpty()) {
                continue;
            }
            Route out = net.routeFrom.get(depot.id());
            if (out != null && out.to() != null && out.to() != depot) {
                long moved = pushBuffer(depot.store(), out, now, net);
                if (moved > 0L) {
                    changed = true;
                    net.sentAt.put(depot.id(), now);
                }
            }
        }
        if (changed) {
            // minion storage is written by the minute save like before; structures here
            structures.markDirty();
        }
        // throughput, smoothed: what left each producer and reached each target this step
        if (seconds > 0.0 && seconds <= 5.0) {
            Map<UUID, Long> out = new HashMap<>();
            Map<UUID, Long> in = new HashMap<>();
            for (Route route : net.routes) {
                if (route.moved > 0L) {
                    out.merge(route.from().id(), route.moved, Long::sum);
                    in.merge(route.to().id(), route.moved, Long::sum);
                }
            }
            for (PlacedStructure structure : structures.of(host)) {
                UUID id = structure.id();
                net.outRate.merge(id, out.getOrDefault(id, 0L) / seconds, (old, now2) -> old * 0.93 + now2 * 0.07);
                net.inRate.merge(id, in.getOrDefault(id, 0L) / seconds, (old, now2) -> old * 0.93 + now2 * 0.07);
            }
        }
    }

    /** Goods per minute leaving this structure (smoothed over the last seconds while someone watches). */
    public long outPerMinute(PlacedStructure structure) {
        Net net = nets.get(structure.host().key());
        Double rate = net == null ? null : net.outRate.get(structure.id());
        return rate == null ? 0L : Math.round(rate * 60.0);
    }

    public long inPerMinute(PlacedStructure structure) {
        Net net = nets.get(structure.host().key());
        Double rate = net == null ? null : net.inRate.get(structure.id());
        return rate == null ? 0L : Math.round(rate * 60.0);
    }

    public static String perMinute(long value) {
        return GuildFormat.compact(value) + "/min";
    }

    private long pushMinion(QuarryMinion minion, Route route, long now, Net net) {
        long moved = 0L;
        PlacedStructure to = route.to();
        int compacted = minion.storedCompacted();
        if (compacted > 0) {
            Res res = Res.of(minion.quarryType(), Res.Form.COMPACTED);
            long accepted = accept(to, res, compacted);
            if (accepted > 0L) {
                minion.setStoredCompacted((int) (compacted - accepted));
                moved += accepted;
                route.lastRes = res;
            }
        }
        int compressed = minion.storedCompressed();
        if (compressed > 0) {
            Res res = Res.of(minion.quarryType(), Res.Form.COMPRESSED);
            long accepted = accept(to, res, compressed);
            if (accepted > 0L) {
                minion.setStoredCompressed((int) (compressed - accepted));
                moved += accepted;
                route.lastRes = res;
            }
        }
        int raw = minion.stored();
        if (raw > 0) {
            Res res = Res.of(minion.quarryType(), Res.Form.RAW);
            long accepted = accept(to, res, raw);
            if (accepted > 0L) {
                minion.setStored((int) (raw - accepted));
                moved += accepted;
                route.lastRes = res;
            }
        }
        if (moved > 0L) {
            route.moved += moved;
            net.receivedAt.put(to.id(), now);
            net.sentAt.put(route.from().id(), now);
        }
        return moved;
    }

    // ------------------------------------------------------------------------------------------------
    // belt pieces: Splitter / Overflow Gate / Sorter
    // ------------------------------------------------------------------------------------------------

    /** Hand what sits in a belt piece to its exits, by the piece's rule. */
    private boolean distribute(PlacedStructure piece, Net net, long now) {
        if (piece.input().isEmpty() || piece.building()) {
            return false;
        }
        List<Route> live = new ArrayList<>();
        for (Route route : net.exits.getOrDefault(piece.id(), List.of())) {
            if (route.to() != null) {
                live.add(route);
            }
        }
        if (live.isEmpty()) {
            return false;
        }
        List<Route> front = new ArrayList<>();
        List<Route> sides = new ArrayList<>();
        for (Route route : live) {
            (route.front() ? front : sides).add(route);
        }
        long movedTotal = 0L;
        for (Res res : new ArrayList<>(piece.input().keySet())) {
            long have = piece.input().getOrDefault(res, 0L);
            if (have <= 0L) {
                continue;
            }
            long given = switch (piece.type()) {
                case OVERFLOW -> {
                    long first = spread(piece, res, have, front, net, now);
                    yield first + spread(piece, res, have - first, sides, net, now);
                }
                case SORTER -> piece.filterMatches(res)
                        ? spread(piece, res, have, front, net, now)
                        : spread(piece, res, have, sides, net, now);
                default -> spread(piece, res, have, live, net, now);
            };
            if (given > 0L) {
                PlacedStructure.take(piece.input(), res, given);
                movedTotal += given;
            }
        }
        if (movedTotal > 0L) {
            piece.setLastWork(now);
            net.sentAt.put(piece.id(), now);
            return true;
        }
        return false;
    }

    /**
     * Even shares over the exits (the odd unit rotates, so a trickle alternates), then whatever an exit refused
     * goes to the others: one full branch never stops the rest. Returns how much left the piece.
     */
    private long spread(PlacedStructure piece, Res res, long amount, List<Route> exits, Net net, long now) {
        int n = exits.size();
        if (n == 0 || amount <= 0L) {
            return 0L;
        }
        long share = amount / n;
        long rest = amount % n;
        int start = Math.floorMod(piece.turn(), n);
        long given = 0L;
        for (int i = 0; i < n; i++) {
            Route route = exits.get((start + i) % n);
            long want = share + (i < rest ? 1L : 0L);
            given += offer(route, res, want, net, now);
        }
        piece.setTurn(start + (int) Math.max(1L, rest));
        long left = amount - given;
        for (int i = 0; i < n && left > 0L; i++) {
            long more = offer(exits.get((start + i) % n), res, left, net, now);
            given += more;
            left -= more;
        }
        return given;
    }

    private long offer(Route route, Res res, long amount, Net net, long now) {
        long accepted = accept(route.to(), res, amount);
        if (accepted > 0L) {
            route.moved += accepted;
            route.lastRes = res;
            net.receivedAt.put(route.to().id(), now);
        }
        return accepted;
    }

    private long pushBuffer(Map<Res, Long> buffer, Route route, long now, Net net) {
        long moved = 0L;
        for (Res res : new ArrayList<>(buffer.keySet())) {
            long have = buffer.getOrDefault(res, 0L);
            long accepted = accept(route.to(), res, have);
            if (accepted > 0L) {
                PlacedStructure.take(buffer, res, accepted);
                moved += accepted;
                route.lastRes = res;
            }
        }
        if (moved > 0L) {
            route.moved += moved;
            net.receivedAt.put(route.to().id(), now);
        }
        return moved;
    }

    /** How much of {@code amount} the target takes (and takes it). */
    private long accept(PlacedStructure target, Res res, long amount) {
        if (amount <= 0L || target == null || target.building()) {
            return 0L;
        }
        Map<Res, Long> into = target.type().role() == StructureType.Role.SINK ? target.store() : target.input();
        long free = target.capacity() - PlacedStructure.total(into);
        if (free <= 0L) {
            return 0L;
        }
        long unit = res.rawEquivalent(1L);
        long fits = free / unit;
        long accepted = Math.min(amount, fits);
        if (accepted > 0L) {
            PlacedStructure.add(into, res, accepted);
        }
        return accepted;
    }

    private boolean process(PlacedStructure processor, double seconds, boolean itemsOk, long now) {
        if (processor.input().isEmpty()) {
            return false;
        }
        if (PlacedStructure.total(processor.output()) >= processor.capacity()) {
            return false; // nowhere to put it: back-pressure up the belt
        }
        // the Grand Hub drives every mill and forge on the island at double speed
        double rawPerStep = processor.ratePerSecond() * seconds * (structures.hubLevel(processor.host()) >= 3 ? 2.0 : 1.0);
        boolean forge = processor.type() == StructureType.FORGE;
        boolean worked = false;
        for (int pass = 0; pass < 2; pass++) {
            for (Res res : new ArrayList<>(processor.input().keySet())) {
                long have = processor.input().getOrDefault(res, 0L);
                if (have <= 0L) {
                    continue;
                }
                boolean converts = itemsOk && res.canUpgrade()
                        && (res.form() == Res.Form.RAW || (forge && res.form() == Res.Form.COMPRESSED));
                if (!converts) {
                    // not this machine's recipe (or no AetherionItems): pass it straight through
                    PlacedStructure.take(processor.input(), res, have);
                    PlacedStructure.add(processor.output(), res, have);
                    worked = true;
                    continue;
                }
                long unit = res.rawEquivalent(1L);
                long speed = Math.max(1L, (long) (rawPerStep / (unit * Res.UNIT)));
                long packs = Math.min(have / Res.UNIT, speed);
                if (packs <= 0L) {
                    continue;
                }
                PlacedStructure.take(processor.input(), res, packs * Res.UNIT);
                Res made = Res.of(res.type(), res.form().next());
                boolean pressAgain = forge && made.form() == Res.Form.COMPRESSED && made.canUpgrade();
                PlacedStructure.add(pressAgain ? processor.input() : processor.output(), made, packs);
                worked = true;
            }
        }
        // leftovers under 128 wait in the input for the next batch (collectable from the machine menu)
        if (worked) {
            processor.setLastWork(now);
        }
        return worked;
    }

    // ------------------------------------------------------------------------------------------------
    // visuals
    // ------------------------------------------------------------------------------------------------

    private void animate(IslandHost host) {
        Net net = nets.get(host.key());
        World world = hosts.world(host);
        if (net == null || world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Route route : net.routes) {
            if (route.to() == null || route.path.isEmpty()) {
                continue;
            }
            if (!flowing(net, route, now) || tickCounter - route.lastSpawn < 16) {
                continue;
            }
            route.lastSpawn = tickCounter;
            Res res = route.lastRes;
            if (res == null) {
                continue;
            }
            // a busy line looks busy: more goods per second, more cargo on it (evenly spaced, still capped)
            long rate = route.moved;
            int pieces = rate >= 256 ? 3 : rate >= 32 ? 2 : 1;
            List<Location> points = points(world, route);
            for (int i = 0; i < pieces; i++) {
                cargo.spawnLater(host.key(), points, res.icon(), i * (20 / pieces));
            }
        }
        for (PlacedStructure structure : structures.of(host)) {
            animateMachine(world, net, structure, now);
        }
    }

    private List<Location> points(World world, Route route) {
        List<Location> points = new ArrayList<>();
        Location chute = structures.chute(route.from());
        if (chute != null) {
            points.add(chute);
        }
        for (Belt belt : route.path) {
            points.add(new Location(world, belt.x() + 0.5, belt.y() + CARGO_Y, belt.z() + 0.5));
        }
        Belt last = route.path.get(route.path.size() - 1);
        points.add(new Location(world, last.nextX() + 0.5, last.y() + CARGO_Y + 0.15, last.nextZ() + 0.5));
        return points;
    }

    private void animateMachine(World world, Net net, PlacedStructure structure, long now) {
        if (structure.building()) {
            return;
        }
        switch (structure.type()) {
            case QUARRY_HOUSING -> {
                if (structure.frameless()) {
                    return;
                }
                Route route = net.routeFrom.get(structure.id());
                boolean flowing = route != null && route.to() != null && route.moved > 0L;
                if (!flowing) {
                    return;
                }
                if (!"st_quarry_housing".equals(structure.templateId())) {
                    // the chute spits what the quarry dug onto the belt, harder the higher it's grown
                    QuarryMinion minion = hosts.minion(structure.host(), structure.minionId());
                    Location spout = structures.chute(structure);
                    if (minion != null && spout != null) {
                        int tier = PropBodies.quarryTier(minion.level());
                        world.spawnParticle(Particle.ITEM, spout.clone().add(0, 0.3, 0), 3 + tier * 3, 0.12, 0.1, 0.12,
                                0.08 + tier * 0.03, new ItemStack(minion.quarryType().product()));
                        world.spawnParticle(Particle.SMOKE, spout.clone().add(0, 0.4, 0), 2, 0.1, 0.05, 0.1, 0.01);
                        if (tier >= 3) {
                            world.spawnParticle(Particle.WAX_OFF, spout.clone().add(0, 0.5, 0), 4, 0.2, 0.2, 0.2, 0.02);
                        }
                        world.playSound(spout, Sound.BLOCK_GRAVEL_FALL, 0.35f, 0.8f + tier * 0.1f);
                    }
                    return;
                }
                boolean up = (tickCounter / 20L) % 2L == 0L;
                Location at = new Location(world, structure.x() + 0.5, structure.y() + (up ? 3.9 : 3.2),
                        structure.z() + 0.5);
                ItemDisplay bucket = fx(world, structure.id(), at, new ItemStack(Material.BARREL), 0.45f, 0.45f);
                if (bucket != null) {
                    bucket.setTeleportDuration(20);
                    bucket.teleport(at);
                }
            }
            case STORAGE_HUT, DEPOT -> {
                Long received = net.receivedAt.get(structure.id());
                if (received != null && now - received < 1500L) {
                    world.spawnParticle(Particle.WAX_ON, structure.x() + 0.5, structure.y() + 1.6, structure.z() + 0.5,
                            3, 0.8, 0.4, 0.8, 0);
                }
            }
            default -> {
            }
        }
    }

    private static UUID twin(UUID id) {
        return new UUID(id.getMostSignificantBits() ^ 0x5A5A5A5AL, id.getLeastSignificantBits());
    }

    private void spin(ItemDisplay stone, boolean working, int way, float width, float height) {
        if (stone == null || !working) {
            return;
        }
        float angle = (float) Math.toRadians(way * ((tickCounter / 20L % 4L) * 90.0 + 90.0));
        stone.setInterpolationDelay(0);
        stone.setInterpolationDuration(20);
        stone.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 0f, 1f, 0f),
                new Vector3f(width, height, width), new AxisAngle4f()));
    }

    private ItemDisplay fx(World world, UUID key, Location at, ItemStack item, float width, float height) {
        UUID entityId = fxEntities.get(key);
        Entity existing = entityId == null ? null : Bukkit.getEntity(entityId);
        if (existing instanceof ItemDisplay display && display.isValid()) {
            return display;
        }
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return null;
        }
        ItemDisplay display = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(width, height, width),
                    new AxisAngle4f()));
            d.setViewRange(0.6f);
            d.setPersistent(false);
            d.addScoreboardTag(FX_TAG);
        });
        fxEntities.put(key, display.getUniqueId());
        return display;
    }

    private void deactivate(String hostKey) {
        cargo.clearHost(hostKey);
        beltVisuals.clearHost(hostKey);
        machineFx.clearHost(hostKey);
        IslandHost host = IslandHost.parse(hostKey);
        if (host == null) {
            return;
        }
        for (PlacedStructure structure : structures.of(host)) {
            for (UUID key : new UUID[]{structure.id(), twin(structure.id())}) {
                UUID entityId = fxEntities.remove(key);
                Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
    }

    public void clearVisuals() {
        cargo.clearAll();
        beltVisuals.clearAll();
        machineFx.clearAll();
        for (UUID entityId : fxEntities.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        fxEntities.clear();
        activeHosts.clear();
    }

    public void purgeLeftovers() {
        CargoVisuals.purgeLeftovers(hosts.personalService().world());
        CargoVisuals.purgeLeftovers(hosts.guildIslands().world());
    }

    public int cargoCount() {
        return cargo.size();
    }

    public int conveyorEntities() {
        return beltVisuals.entityCount();
    }

    public int machineFxEntities() {
        return machineFx.entityCount();
    }

    // ------------------------------------------------------------------------------------------------
    // status: one word per machine (tags, menus, island bar)
    // ------------------------------------------------------------------------------------------------

    public MachineState status(PlacedStructure structure) {
        if (structure == null) {
            return MachineState.IDLE;
        }
        if (structure.building()) {
            return MachineState.BUILDING;
        }
        Net net = nets.get(structure.host().key());
        long now = System.currentTimeMillis();
        long sent = net == null ? 0L : net.sentAt.getOrDefault(structure.id(), 0L);
        long got = net == null ? 0L : net.receivedAt.getOrDefault(structure.id(), 0L);
        StructureType type = structure.type();
        switch (type.role()) {
            case SOURCE -> {
                QuarryMinion minion = hosts.minion(structure.host(), structure.minionId());
                Route route = routeFrom(structure);
                boolean full = minion != null && minion.rawEquivalent() >= minion.cap();
                if (route == null) {
                    return full ? MachineState.FULL : MachineState.NEEDS_BELT;
                }
                if (route.deadEnd()) {
                    return MachineState.DEAD_END;
                }
                return full && now - sent > flowWindow() ? MachineState.BACKED_UP : MachineState.RUNNING;
            }
            case PROCESSOR -> {
                Route out = routeFrom(structure);
                if (out == null) {
                    return MachineState.NEEDS_BELT_OUT;
                }
                if (out.deadEnd()) {
                    return MachineState.DEAD_END;
                }
                if (PlacedStructure.total(structure.output()) >= structure.capacity()) {
                    return MachineState.BACKED_UP;
                }
                if (now - structure.lastWork() < flowWindow()) {
                    return MachineState.RUNNING;
                }
                if (feeding(structure) == 0) {
                    return MachineState.NEEDS_BELT_IN;
                }
                return structure.input().isEmpty() ? MachineState.WAITING : MachineState.BATCHING;
            }
            case SINK -> {
                if (PlacedStructure.total(structure.store()) >= structure.capacity()) {
                    return MachineState.FULL;
                }
                if (now - got < flowWindow()) {
                    return MachineState.RECEIVING;
                }
                return feeding(structure) == 0 ? MachineState.NEEDS_BELT_IN : MachineState.WAITING;
            }
            case ROUTER -> {
                if (type == StructureType.SORTER && structure.filter() == null) {
                    return MachineState.PICK_FILTER;
                }
                List<Route> exits = exits(structure);
                boolean anyLive = false;
                boolean anyDead = false;
                for (Route route : exits) {
                    anyLive |= route.to() != null;
                    anyDead |= route.to() == null;
                }
                if (!anyLive) {
                    return exits.isEmpty() ? MachineState.NEEDS_BELT_OUT : MachineState.DEAD_END;
                }
                if (now - sent < flowWindow()) {
                    return MachineState.RUNNING;
                }
                if (feeding(structure) == 0) {
                    return MachineState.NEEDS_BELT_IN;
                }
                if (!structure.input().isEmpty()) {
                    return MachineState.BACKED_UP;
                }
                return anyDead ? MachineState.DEAD_END : MachineState.WAITING;
            }
            default -> {
                return MachineState.IDLE;
            }
        }
    }

    /** The worst thing on an island right now (for the island bar), or null when all is well. */
    public String worstProblem(IslandHost host) {
        String best = null;
        int rank = Integer.MAX_VALUE;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == StructureType.PROJECT || structure.type() == StructureType.WORKSHOP) {
                continue;
            }
            MachineState state = status(structure);
            if (!state.problem()) {
                continue;
            }
            int r = switch (state) {
                case FULL -> 0;
                case DEAD_END -> 1;
                case BACKED_UP -> 2;
                case PICK_FILTER -> 3;
                case NEEDS_BELT_OUT -> 4;
                case NEEDS_BELT -> 5;
                default -> 6;
            };
            if (r < rank) {
                rank = r;
                best = label(structure) + ": " + state.word();
            }
        }
        return best;
    }

    /** "Cobble Quarry" for housings, the building's name otherwise. */
    public String label(PlacedStructure structure) {
        if (structure.type() == StructureType.QUARRY_HOUSING) {
            QuarryMinion minion = hosts.minion(structure.host(), structure.minionId());
            return minion == null ? "Quarry" : minion.quarryType().display();
        }
        return structure.label();
    }

    // ------------------------------------------------------------------------------------------------
    // belt pieces: placing (on a free cell, or straight onto a belt to cut it)
    // ------------------------------------------------------------------------------------------------

    /** Why a belt piece can't go into this cell, or null. A belt of this island in the cell is fine (cut in). */
    public String pieceProblem(IslandHost host, World world, int x, int y, int z) {
        if (!hosts.inBuildZone(host, x, z)) {
            return "outside your land";
        }
        PlacedStructure there = structures.atColumn(world, x, z);
        if (there != null && !(there.type() == StructureType.QUARRY_HOUSING && there.frameless())) {
            return "inside your " + there.label();
        }
        Belt belt = belt(world, x, y, z);
        if (belt != null) {
            return null;
        }
        Block cell = world.getBlockAt(x, y, z);
        if (!PasteService.replaceable(cell)) {
            return "a block is in the way";
        }
        if (!cell.getRelative(BlockFace.DOWN).getType().isSolid() || isBelt(world, x, y - 1, z)) {
            return "needs solid ground";
        }
        return null;
    }

    public PlacedStructure placePiece(Player player, IslandHost host, StructureType type, int x, int y, int z,
                                      BlockFace facing) {
        World world = hosts.world(host);
        if (world == null || !type.router()) {
            return null;
        }
        String blocked = structures.blockedReason(player, host, type);
        if (blocked != null) {
            player.sendMessage("§c" + blocked);
            return null;
        }
        String problem = pieceProblem(host, world, x, y, z);
        if (problem != null) {
            player.sendMessage("§cCan't set it there: " + problem + ".");
            return null;
        }
        long price = structures.priceFor(host, type);
        if (!hosts.charge(player, host, price)) {
            return null;
        }
        Belt cut = belt(world, x, y, z);
        BlockFace front = cut != null ? cut.dir() : facing;
        if (cut != null) {
            // cut into the line: the tile under it goes (refunded), the belt before now feeds the piece
            Net net = nets.get(host.key());
            if (net != null) {
                net.belts.remove(cut.key());
                Map<Long, String> index = worldIndex.get(world.getName());
                if (index != null) {
                    index.remove(cut.key());
                }
                net.version++;
                net.routesDirty = true;
                dirty = true;
            }
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
            hosts.refund(player, host, tileCost());
        }
        PlacedStructure piece = structures.registerRouter(host, type, x, y - 1, z, rotOf(front));
        if (piece == null) {
            return null;
        }
        markDirty(host);
        Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        world.playSound(at, Sound.BLOCK_COPPER_GRATE_PLACE, 1f, 0.9f);
        world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 0.7f, 1.3f);
        world.spawnParticle(Particle.WAX_OFF, at, 10, 0.35, 0.35, 0.35, 0.02);
        player.sendMessage("§a" + type.display() + " set down" + (price > 0 ? " §8(§6" + GuildFormat.compact(price)
                + " " + hosts.fundsLabel(host) + "§8)" : "") + "§a. §7" + switch (type) {
            case SORTER -> "Right-click it to pick what goes out the front.";
            case OVERFLOW -> "Front belt first, sides take the spill.";
            default -> "Every belt leading away from it gets an equal share.";
        });
        if (cut != null) {
            player.sendMessage("§7It cut into your belt. Lay a belt §faway §7from its side to make a second exit.");
        }
        return piece;
    }

    // ------------------------------------------------------------------------------------------------
    // text for menus / nametags
    // ------------------------------------------------------------------------------------------------

    public String summary(PlacedStructure structure) {
        if (structure.type().router()) {
            List<Route> exits = exits(structure);
            int live = 0;
            for (Route route : exits) {
                if (route.to() != null) {
                    live++;
                }
            }
            if (exits.isEmpty()) {
                return "§8No belt leads away from it yet.";
            }
            return (live == exits.size() ? "§a→ " : "§e→ ") + live + "/" + exits.size() + " exit"
                    + (exits.size() == 1 ? "" : "s") + " reach a machine";
        }
        Route route = routeFrom(structure);
        if (route == null) {
            if (!structure.type().hasOutput() || structure.type().role() == StructureType.Role.SINK) {
                int in = feeding(structure);
                return in > 0 ? "§a← fed by " + in + " belt" + (in == 1 ? "" : "s") : "§8no belts in";
            }
            return structures.outputPort(structure) == null
                    ? "§8No belt next to the quarry yet."
                    : "§8No belt at the chute yet.";
        }
        if (route.deadEnd()) {
            return "§c→ belt ends nowhere §8(" + route.length() + " tiles)";
        }
        return "§a→ " + route.to().label() + " §8(" + route.length() + " tile" + (route.length() == 1 ? "" : "s") + ")";
    }

    public String nametagSuffix(QuarryMinion minion) {
        PlacedStructure housing = structures.housingOfMinion(minion.id());
        if (housing == null) {
            return "";
        }
        Route route = routeFrom(housing);
        if (route == null) {
            return " §6○ needs belt out";
        }
        if (route.deadEnd()) {
            return " §c✖ belt leads nowhere";
        }
        MachineState state = status(housing);
        return (state == MachineState.BACKED_UP ? " §e■ backed up" : " §a●") + " §8→ §7" + route.to().label()
                + " §8(" + perMinute(digPerMinute(minion)) + ")";
    }

    public Collection<Belt> belts(IslandHost host) {
        Net net = nets.get(host.key());
        return net == null ? List.of() : List.copyOf(net.belts.values());
    }

    // ------------------------------------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------------------------------------

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Net> entry : nets.entrySet()) {
            Net net = entry.getValue();
            if (net.belts.isEmpty()) {
                continue;
            }
            List<String> belts = new ArrayList<>(net.belts.size());
            for (Belt belt : net.belts.values()) {
                belts.add(belt.serialize());
            }
            config.set("hosts." + entry.getKey() + ".last-flow", net.lastFlow);
            config.set("hosts." + entry.getKey() + ".belts", belts);
        }
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save island_logistics.yml: " + exception.getMessage());
        }
    }

    public void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("hosts");
        if (root == null) {
            return;
        }
        int loaded = 0;
        for (String hostKey : root.getKeys(false)) {
            IslandHost host = IslandHost.parse(hostKey);
            if (host == null) {
                continue;
            }
            World world = hosts.world(host);
            if (world == null) {
                continue;
            }
            Net net = net(host);
            net.lastFlow = root.getLong(hostKey + ".last-flow", 0L);
            Map<Long, String> index = worldIndex.computeIfAbsent(world.getName(), k -> new HashMap<>());
            for (String raw : root.getStringList(hostKey + ".belts")) {
                Belt belt = Belt.parse(raw);
                if (belt != null) {
                    net.belts.put(belt.key(), belt);
                    index.put(belt.key(), host.key());
                    loaded++;
                }
            }
        }
        plugin.getLogger().info("Island highlight: loaded " + loaded + " belt tiles.");
    }
}
