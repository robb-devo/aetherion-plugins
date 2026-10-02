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
        private long moved;
        private Res lastRes;
        private long lastSpawn;

        Route(PlacedStructure from, List<Belt> path, PlacedStructure to) {
            this.from = from;
            this.path = path;
            this.to = to;
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
                plugin.getConfig().getInt("logistics.conveyor-entities-global", 1800));
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
            return "inside your " + structure.label();
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
        for (int i = 0; i < touched.size(); i += 2) {
            Belt b = touched.get(i);
            world.spawnParticle(Particle.CRIT, b.x() + 0.5, b.y() + 0.2, b.z() + 0.5, 2, 0.2, 0.05, 0.2, 0.01);
        }
        player.sendMessage("§a" + fresh + " belt tile" + (fresh == 1 ? "" : "s") + " laid"
                + (price > 0 ? " §8(§6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host) + "§8)" : "")
                + (free > 0 ? " §8(§e" + free + " free, starter kit§8)" : "")
                + "§a. §7" + count(host) + "/" + cap(host) + " tiles.");
        return true;
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
        net.processorOrder.clear();
        net.routesDirty = false;
        World world = hosts.world(host);
        if (world == null) {
            return;
        }
        List<PlacedStructure> all = new ArrayList<>(structures.of(host));
        for (PlacedStructure structure : all) {
            if (!structure.type().hasOutput() || structure.building()) {
                continue;
            }
            Belt first = null;
            int[] port = structures.outputPort(structure);
            if (port == null) {
                // frameless quarry / drill rig: a belt may start on any cell next to it
                int y = structure.y() + 1;
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
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
            if (first == null) {
                continue;
            }
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
            Route route = new Route(structure, path, target);
            net.routes.add(route);
            net.routeFrom.put(structure.id(), route);
        }
        // processors in chain order (sources feed first, then what they feed, ...)
        Set<UUID> seen = new HashSet<>();
        Deque<PlacedStructure> queue = new ArrayDeque<>();
        for (Route route : net.routes) {
            if (route.from().type().role() == StructureType.Role.SOURCE && route.to() != null
                    && route.to().type().role() == StructureType.Role.PROCESSOR) {
                queue.add(route.to());
            }
        }
        while (!queue.isEmpty()) {
            PlacedStructure p = queue.poll();
            if (!seen.add(p.id())) {
                continue;
            }
            net.processorOrder.add(p);
            Route out = net.routeFrom.get(p.id());
            if (out != null && out.to() != null && out.to().type().role() == StructureType.Role.PROCESSOR) {
                queue.add(out.to());
            }
        }
        for (PlacedStructure structure : all) {
            if (structure.type().role() == StructureType.Role.PROCESSOR && seen.add(structure.id())) {
                net.processorOrder.add(structure);
            }
        }
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
            boolean moving = route.to() != null && (route.moved > 0L
                    || (route.from().type().role() == StructureType.Role.PROCESSOR && now - route.from().lastWork() < 3000L));
            if (moving) {
                for (Belt belt : route.path) {
                    flowing.add(belt.key());
                }
            }
        }
        beltVisuals.render(host.key(), world, net.belts, net.version, near, flowing, tickCounter);
    }

    /** Every tick: glide cargo. */
    public void tickCargo() {
        tickCounter++;
        cargo.tick();
        beltVisuals.tick(tickCounter);
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
        // 2) processors in chain order: convert, then push on
        for (PlacedStructure processor : net.processorOrder) {
            if (process(processor, seconds, itemsOk, now)) {
                changed = true;
            }
            Route out = net.routeFrom.get(processor.id());
            if (out != null && out.to() != null) {
                long moved = pushBuffer(processor.output(), out, now, net);
                if (moved > 0L) {
                    changed = true;
                }
            }
        }
        if (changed) {
            // minion storage is written by the minute save like before; structures here
            structures.markDirty();
        }
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
        }
        return moved;
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
        double rawPerStep = processor.ratePerSecond() * seconds;
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
            boolean flowing = route.moved > 0L
                    || (route.from().type().role() == StructureType.Role.PROCESSOR && now - route.from().lastWork() < 3000L);
            if (!flowing || tickCounter - route.lastSpawn < 16) {
                continue;
            }
            route.lastSpawn = tickCounter;
            Res res = route.lastRes;
            if (res == null) {
                continue;
            }
            cargo.spawn(host.key(), points(world, route), res.icon());
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
            case MILL -> {
                boolean working = now - structure.lastWork() < 3000L;
                ItemDisplay stone = fx(world, structure.id(), new Location(world, structure.x() + 0.5, structure.y() + 2.3,
                        structure.z() + 0.5), new ItemStack(Material.SMOOTH_STONE_SLAB), 1.9f, 1.3f);
                spin(stone, working, 1, 1.9f, 1.3f);
                if (structure.level() >= 2) {
                    // Twin-Stone Mill: the upper runner stone turns the other way
                    ItemDisplay upper = fx(world, twin(structure.id()), new Location(world, structure.x() + 0.5,
                            structure.y() + 3.15, structure.z() + 0.5), new ItemStack(Material.SMOOTH_STONE_SLAB), 1.6f, 1.0f);
                    spin(upper, working, -1, 1.6f, 1.0f);
                }
                if (stone != null && working) {
                    world.spawnParticle(Particle.WHITE_ASH, stone.getLocation(), structure.level() >= 2 ? 10 : 6,
                            0.6, 0.3, 0.6, 0.01);
                }
            }
            case FORGE -> {
                if (now - structure.lastWork() < 3000L) {
                    // the open cell in front of the blast furnace (the centre of both forge templates)
                    Location mouth = new Location(world, structure.x() + 0.5, structure.y() + 1.35,
                            structure.z() + 0.5);
                    world.spawnParticle(Particle.FLAME, mouth, structure.level() >= 2 ? 7 : 4, 0.2, 0.2, 0.2, 0.01);
                    world.spawnParticle(Particle.LAVA, mouth, 1, 0.1, 0.1, 0.1, 0);
                    if (tickCounter % 60L < 20L) {
                        world.playSound(mouth, Sound.BLOCK_ANVIL_USE, 0.25f, 1.6f);
                    }
                }
            }
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
                    // compact housing / rig: the drill motor puffs while the belt takes ore away
                    double top = StructureType.QUARRY_RIG.equals(structure.templateId()) ? 4.6 : 4.1;
                    world.spawnParticle(Particle.SMOKE, structure.x() + 0.5, structure.y() + top, structure.z() + 0.5,
                            2, 0.08, 0.05, 0.08, 0.01);
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

    // ------------------------------------------------------------------------------------------------
    // text for menus / nametags
    // ------------------------------------------------------------------------------------------------

    public String summary(PlacedStructure structure) {
        Route route = routeFrom(structure);
        if (route == null) {
            if (!structure.type().hasOutput()) {
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
            return "";
        }
        return route.deadEnd() ? " §c→ ✖" : " §a→ §7" + route.to().label();
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
