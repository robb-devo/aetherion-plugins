package de.aetherion.dungeons.instance;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Floor 1 (Warden's Prison) room library + per-run shuffle.
 * <p>
 * The pool lives in {@code structures/floor1/pool.yml} (jar) — every template is a square
 * structure NBT with its door sides, loot anchor and mob pads. A data-folder copy of
 * {@code structures/floor1/pool.yml} / {@code <id>.nbt} extends or overrides the jar pool.
 * <p>
 * Each run: lobby → spine of combat rooms (straight or with side turns) → boss → exit,
 * plus side branches off the spine. Templates are picked per slot (size mix, no repeats),
 * rotated so their doors face the links, and the seed is logged so a run can be reproduced.
 */
public final class FloorOnePool {

    public static final String FOLDER = "structures/floor1";
    private static final int GAP_MIN = 7;
    private static final int GAP_MAX = 11;

    /** Clockwise order: north, east, south, west. */
    public enum Side {
        MINZ, MAXX, MAXZ, MINX;

        public Side rotate(int quarterTurnsCw) {
            return values()[Math.floorMod(ordinal() + quarterTurnsCw, 4)];
        }

        public Side opposite() {
            return rotate(2);
        }

        static Side parse(String raw) {
            if (raw == null) {
                return null;
            }
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "MINZ", "N", "NORTH" -> MINZ;
                case "MAXX", "E", "EAST" -> MAXX;
                case "MAXZ", "S", "SOUTH" -> MAXZ;
                case "MINX", "W", "WEST" -> MINX;
                default -> null;
            };
        }
    }

    public record Template(
            String id,
            String file,
            String title,
            String sizeClass,
            int size,
            int height,
            int weight,
            Set<Side> doors,
            int lootX,
            int lootZ,
            List<int[]> pads
    ) {
        boolean fits(Set<Side> required, int rotation) {
            for (Side need : required) {
                boolean ok = false;
                for (Side door : doors) {
                    if (door.rotate(rotation) == need) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return false;
                }
            }
            return true;
        }

        List<Integer> rotationsFor(Set<Side> required) {
            List<Integer> out = new ArrayList<>(4);
            for (int r = 0; r < 4; r++) {
                if (fits(required, r)) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    /** A template placed in the world: min corner = bounds, turned {@code rotation} quarter turns clockwise. */
    public record Placed(int index, Template template, int rotation, DungeonLayout.Room bounds) {

        public int[] world(int localX, int localZ) {
            int w = template.size() - 1;
            int mx = bounds.minX();
            int mz = bounds.minZ();
            return switch (Math.floorMod(rotation, 4)) {
                case 1 -> new int[]{mx + w - localZ, mz + localX};
                case 2 -> new int[]{mx + w - localX, mz + w - localZ};
                case 3 -> new int[]{mx + localZ, mz + w - localX};
                default -> new int[]{mx + localX, mz + localZ};
            };
        }

        public Location origin(World world, int y) {
            int w = template.size() - 1;
            return switch (Math.floorMod(rotation, 4)) {
                case 1 -> new Location(world, bounds.minX() + w, y, bounds.minZ());
                case 2 -> new Location(world, bounds.minX() + w, y, bounds.minZ() + w);
                case 3 -> new Location(world, bounds.minX(), y, bounds.minZ() + w);
                default -> new Location(world, bounds.minX(), y, bounds.minZ());
            };
        }

        public StructureRotation structureRotation() {
            return switch (Math.floorMod(rotation, 4)) {
                case 1 -> StructureRotation.CLOCKWISE_90;
                case 2 -> StructureRotation.CLOCKWISE_180;
                case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            };
        }
    }

    public record Plan(long seed, Placed lobby, List<Placed> combat, Placed boss, DungeonLayout layout) {
        public List<Placed> all() {
            List<Placed> out = new ArrayList<>(combat.size() + 2);
            out.add(lobby);
            out.addAll(combat);
            out.add(boss);
            return out;
        }

        public String summary() {
            StringBuilder out = new StringBuilder();
            out.append("seed=").append(seed).append(" lobby=").append(lobby.template().id());
            for (Placed placed : combat) {
                out.append(" c").append(placed.index()).append('=').append(placed.template().id())
                        .append('@').append(placed.rotation() * 90);
            }
            out.append(" boss=").append(boss.template().id());
            return out.toString();
        }
    }

    private record Catalog(List<Template> lobby, List<Template> boss, List<Template> combat) {
    }

    private static final Map<String, Plan> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Structure> STRUCTURES = new ConcurrentHashMap<>();
    private static volatile Catalog catalog;
    private static volatile String lastSummary = "";

    private FloorOnePool() {
    }

    // ------------------------------------------------------------------ config / catalog

    public static boolean enabled(Plugin plugin) {
        return plugin != null && plugin.getConfig().getBoolean("floor1-pool.enabled", true);
    }

    public static String lastSummary() {
        return lastSummary;
    }

    public static int catalogSize(Plugin plugin) {
        Catalog cat = catalog(plugin);
        return cat == null ? 0 : cat.combat().size();
    }

    /** Drop cached catalog + structures (e.g. after adding templates to the data folder). */
    public static void reload() {
        catalog = null;
        STRUCTURES.clear();
    }

    private static Catalog catalog(Plugin plugin) {
        Catalog cached = catalog;
        if (cached != null) {
            return cached;
        }
        Map<String, Template> templates = new LinkedHashMap<>();
        try (InputStream in = plugin.getResource(FOLDER + "/pool.yml")) {
            if (in != null) {
                readPool(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), templates);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Floor1] Could not read jar pool.yml", ex);
        }
        File local = new File(plugin.getDataFolder(), FOLDER + "/pool.yml");
        if (local.isFile()) {
            readPool(YamlConfiguration.loadConfiguration(local), templates);
        }
        List<Template> lobby = new ArrayList<>();
        List<Template> boss = new ArrayList<>();
        List<Template> combat = new ArrayList<>();
        for (Template template : templates.values()) {
            switch (template.sizeClass()) {
                case "LOBBY" -> lobby.add(template);
                case "BOSS" -> boss.add(template);
                default -> {
                    if (!template.doors().isEmpty()) {
                        combat.add(template);
                    }
                }
            }
        }
        Catalog built = new Catalog(List.copyOf(lobby), List.copyOf(boss), List.copyOf(combat));
        catalog = built;
        plugin.getLogger().info("[Floor1] Room pool: " + combat.size() + " combat, " + lobby.size() + " lobby, "
                + boss.size() + " boss template(s).");
        return built;
    }

    private static void readPool(YamlConfiguration yaml, Map<String, Template> into) {
        ConfigurationSection section = yaml.getConfigurationSection("templates");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) {
                continue;
            }
            if (t.getBoolean("disabled", false)) {
                into.remove(id);
                continue;
            }
            Set<Side> doors = EnumSet.noneOf(Side.class);
            for (String raw : t.getStringList("doors")) {
                Side side = Side.parse(raw);
                if (side != null) {
                    doors.add(side);
                }
            }
            List<Integer> loot = t.getIntegerList("loot");
            int size = t.getInt("size", 21);
            int lootX = loot.size() >= 2 ? loot.get(0) : size / 2;
            int lootZ = loot.size() >= 2 ? loot.get(1) : size / 2;
            List<int[]> pads = new ArrayList<>();
            for (Object raw : t.getList("pads", List.of())) {
                if (raw instanceof List<?> pair && pair.size() >= 2
                        && pair.get(0) instanceof Number px && pair.get(1) instanceof Number pz) {
                    // optional third value: feet height above the floor (balcony / deck pads)
                    int dy = pair.size() >= 3 && pair.get(2) instanceof Number py ? Math.max(0, py.intValue()) : 0;
                    pads.add(new int[]{px.intValue(), pz.intValue(), dy});
                }
            }
            into.put(id, new Template(
                    id,
                    t.getString("file", id + ".nbt"),
                    t.getString("title", id),
                    t.getString("class", "SMALL").toUpperCase(Locale.ROOT),
                    size,
                    t.getInt("height", 12),
                    Math.max(1, t.getInt("weight", 1)),
                    doors,
                    lootX,
                    lootZ,
                    pads
            ));
        }
    }

    // ------------------------------------------------------------------ structures

    static Structure structure(Plugin plugin, Template template) {
        Structure cached = STRUCTURES.get(template.id());
        if (cached != null) {
            return cached;
        }
        Structure loaded = null;
        File local = new File(plugin.getDataFolder(), FOLDER + "/" + template.file());
        try {
            if (local.isFile()) {
                loaded = plugin.getServer().getStructureManager().loadStructure(local);
            } else {
                try (InputStream in = plugin.getResource(FOLDER + "/" + template.file())) {
                    if (in != null) {
                        loaded = plugin.getServer().getStructureManager().loadStructure(in);
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Floor1] Could not load " + template.file(), ex);
        }
        if (loaded != null) {
            STRUCTURES.put(template.id(), loaded);
        }
        return loaded;
    }

    /** Paste one placed template. False when the NBT is missing (caller builds a fallback box). */
    static boolean paste(Plugin plugin, World world, Placed placed, int y) {
        Structure structure = structure(plugin, placed.template());
        if (structure == null) {
            return false;
        }
        structure.place(
                placed.origin(world, y),
                false,
                placed.structureRotation(),
                Mirror.NONE,
                0,
                1.0f,
                new Random(placed.template().id().hashCode() ^ placed.bounds().minX() ^ placed.bounds().minZ())
        );
        return true;
    }

    // ------------------------------------------------------------------ active runs

    public static void register(String worldName, Plan plan) {
        if (worldName != null && plan != null) {
            ACTIVE.put(worldName, plan);
        }
    }

    public static void forget(String worldName) {
        if (worldName != null) {
            ACTIVE.remove(worldName);
        }
    }

    public static Plan active(World world) {
        return world == null ? null : ACTIVE.get(world.getName());
    }

    /** Mob pads (world coords, feet level; deck pads carry their height) for a pooled combat room; empty for other floors. */
    public static List<Location> spawnPads(World world, int roomIndex) {
        Plan plan = active(world);
        if (plan == null || roomIndex < 0 || roomIndex >= plan.combat().size()) {
            return List.of();
        }
        Placed placed = plan.combat().get(roomIndex);
        List<Location> out = new ArrayList<>(placed.template().pads().size());
        for (int[] pad : placed.template().pads()) {
            int[] at = placed.world(pad[0], pad[1]);
            int dy = pad.length > 2 ? pad[2] : 0;
            out.add(new Location(world, at[0] + 0.5, DungeonLayout.FLOOR_Y + 1 + dy, at[1] + 0.5));
        }
        return out;
    }

    public static String roomTitle(World world, int roomIndex) {
        Plan plan = active(world);
        if (plan == null || roomIndex < 0 || roomIndex >= plan.combat().size()) {
            return null;
        }
        return plan.combat().get(roomIndex).template().title();
    }

    // ------------------------------------------------------------------ planning

    public static Plan plan(Plugin plugin, long seed) {
        Catalog cat = catalog(plugin);
        if (cat == null || cat.lobby().isEmpty() || cat.boss().isEmpty() || cat.combat().size() < 4) {
            plugin.getLogger().warning("[Floor1] Room pool incomplete — using the legacy fixed layout.");
            return null;
        }
        int rooms = Math.max(4, Math.min(12, plugin.getConfig().getInt("floor1-pool.rooms-per-run", 7)));
        int branches = Math.max(0, Math.min(rooms - 2, plugin.getConfig().getInt("floor1-pool.branches", 2)));
        int minLarge = Math.max(0, plugin.getConfig().getInt("floor1-pool.min-large", 1));
        int maxHuge = Math.max(0, plugin.getConfig().getInt("floor1-pool.max-huge", 1));
        double turnChance = Math.max(0.0, Math.min(0.6, plugin.getConfig().getDouble("floor1-pool.turn-chance", 0.28)));
        Plan plan = planWith(cat.lobby(), cat.boss(), cat.combat(), seed, rooms, branches, minLarge, maxHuge, turnChance);
        if (plan == null) {
            plugin.getLogger().warning("[Floor1] Could not lay out a run for seed " + seed + " — using the legacy fixed layout.");
            return null;
        }
        lastSummary = plan.summary();
        plugin.getLogger().info("[Floor1] Run " + lastSummary);
        return plan;
    }

    /** Pure planner (no Bukkit calls) — deterministic for a given seed. */
    static Plan planWith(List<Template> lobby, List<Template> boss, List<Template> combat, long seed,
                         int rooms, int branches, int minLarge, int maxHuge, double turnChance) {
        Catalog cat = new Catalog(lobby, boss, combat);
        Random seeds = new Random(seed);
        for (int attempt = 0; attempt < 30; attempt++) {
            int useBranches = attempt >= 20 ? 0 : attempt >= 10 ? Math.max(0, branches - 1) : branches;
            double useTurn = attempt >= 20 ? 0.0 : turnChance;
            Plan plan = tryPlan(cat, seed, new Random(seeds.nextLong()), rooms, useBranches, minLarge, maxHuge, useTurn);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private static final class Slot {
        final int index;
        final int parent;          // combat index, or -1 for lobby
        final Side fromParent;     // side of the parent this room hangs off
        final boolean spine;
        final Set<Side> required = EnumSet.noneOf(Side.class);
        String sizeClass;
        Template template;
        int rotation;
        DungeonLayout.Room bounds;

        Slot(int index, int parent, Side fromParent, boolean spine) {
            this.index = index;
            this.parent = parent;
            this.fromParent = fromParent;
            this.spine = spine;
        }
    }

    private static Plan tryPlan(Catalog cat, long seed, Random rng, int rooms, int branches,
                                int minLarge, int maxHuge, double turnChance) {
        int spineLen = Math.max(2, rooms - branches);
        branches = rooms - spineLen;

        // 1) topology --------------------------------------------------------------
        Side[] dir = new Side[spineLen];
        dir[0] = Side.MAXZ;
        Side lastTurn = null;
        for (int i = 1; i < spineLen; i++) {
            if (dir[i - 1] != Side.MAXZ || i == spineLen - 1 || rng.nextDouble() >= turnChance) {
                dir[i] = Side.MAXZ;
            } else {
                Side turn = lastTurn == null ? (rng.nextBoolean() ? Side.MAXX : Side.MINX)
                        : (rng.nextDouble() < 0.7 ? lastTurn.opposite() : lastTurn);
                dir[i] = turn;
                lastTurn = turn;
            }
        }
        List<Set<Side>> spineReq = new ArrayList<>();
        for (int i = 0; i < spineLen; i++) {
            Set<Side> req = EnumSet.of(dir[i].opposite());
            req.add(i + 1 < spineLen ? dir[i + 1] : Side.MAXZ);
            spineReq.add(req);
        }
        List<Integer> junctionOrder = new ArrayList<>();
        for (int i = 0; i < spineLen; i++) {
            junctionOrder.add(i);
        }
        Collections.shuffle(junctionOrder, rng);
        Map<Integer, Side> branchAt = new HashMap<>();
        for (int i : junctionOrder) {
            if (branchAt.size() >= branches) {
                break;
            }
            List<Side> free = new ArrayList<>();
            for (Side lateral : new Side[]{Side.MAXX, Side.MINX}) {
                if (!spineReq.get(i).contains(lateral)) {
                    free.add(lateral);
                }
            }
            if (free.isEmpty()) {
                continue;
            }
            Side side = free.get(rng.nextInt(free.size()));
            branchAt.put(i, side);
            spineReq.get(i).add(side);
        }
        if (branchAt.size() < branches) {
            return null;
        }
        // Combat indices follow the walk: spine room, then its side branch, then the next spine room.
        List<Slot> slots = new ArrayList<>();
        int[] spineIndexOf = new int[spineLen];
        for (int i = 0; i < spineLen; i++) {
            int parentIndex = i == 0 ? -1 : spineIndexOf[i - 1];
            Slot s = new Slot(slots.size(), parentIndex, dir[i], true);
            s.required.addAll(spineReq.get(i));
            spineIndexOf[i] = s.index;
            slots.add(s);
            Side branchSide = branchAt.get(i);
            if (branchSide != null) {
                Slot leaf = new Slot(slots.size(), s.index, branchSide, false);
                leaf.required.add(branchSide.opposite());
                slots.add(leaf);
            }
        }
        Slot lastSpine = slots.get(spineIndexOf[spineLen - 1]);

        // 2) size classes ------------------------------------------------------------
        List<Slot> spineSlots = new ArrayList<>();
        for (Slot s : slots) {
            if (s.spine) {
                spineSlots.add(s);
            }
        }
        int huge = maxHuge > 0 && rooms >= 6 && rng.nextDouble() < 0.65 ? 1 : 0;
        int large = Math.max(minLarge, rooms >= 8 ? 2 : 1);
        if (huge > 0 && lastSpine != null) {
            lastSpine.sizeClass = "HUGE";
        }
        List<Slot> bigCandidates = new ArrayList<>();
        for (Slot s : spineSlots) {
            if (s.sizeClass == null && s != spineSlots.get(0)) {
                bigCandidates.add(s);
            }
        }
        Collections.shuffle(bigCandidates, rng);
        for (int i = 0; i < large && i < bigCandidates.size(); i++) {
            bigCandidates.get(i).sizeClass = "LARGE";
        }
        for (Slot s : slots) {
            if (s.sizeClass == null) {
                s.sizeClass = rng.nextDouble() < 0.5 ? "MEDIUM" : "SMALL";
            }
        }

        // 3) templates ---------------------------------------------------------------
        Set<String> used = new HashSet<>();
        for (Slot s : slots) {
            if (!assign(s, cat.combat(), used, rng, true) && !assign(s, cat.combat(), used, rng, false)) {
                return null;
            }
        }
        Template lobbyT = weighted(cat.lobby(), rng);
        Template bossT = weighted(cat.boss(), rng);
        int lobbyRot = pickRotation(lobbyT, EnumSet.of(Side.MAXZ), rng);
        // Boss rooms are authored entry-north / exit-south (throne over the way out): keep them unrotated when they can be.
        int bossRot = bossT.rotationsFor(EnumSet.of(Side.MINZ, Side.MAXZ)).contains(0)
                ? 0
                : pickRotation(bossT, EnumSet.of(Side.MINZ, Side.MAXZ), rng);
        if (lobbyRot < 0 || bossRot < 0) {
            return null;
        }

        // 4) geometry ----------------------------------------------------------------
        int lh = (lobbyT.size() - 1) / 2;
        DungeonLayout.Room lobbyRoom = new DungeonLayout.Room(-lh, lh, 0, lobbyT.size() - 1);
        List<DungeonLayout.Room> occupied = new ArrayList<>();
        occupied.add(lobbyRoom);
        for (Slot s : slots) {
            DungeonLayout.Room parent = s.parent < 0 ? lobbyRoom : slots.get(s.parent).bounds;
            s.bounds = attach(parent, s.fromParent, s.template.size(), GAP_MIN + rng.nextInt(GAP_MAX - GAP_MIN + 1));
            if (collides(s.bounds, occupied, 3) || corridorBlocked(parent, s.bounds, s.fromParent, occupied)) {
                return null;
            }
            occupied.add(s.bounds);
        }
        DungeonLayout.Room bossRoom = attach(lastSpine.bounds, Side.MAXZ, bossT.size(), GAP_MIN + rng.nextInt(GAP_MAX - GAP_MIN + 1));
        if (collides(bossRoom, occupied, 3) || corridorBlocked(lastSpine.bounds, bossRoom, Side.MAXZ, occupied)) {
            return null;
        }
        occupied.add(bossRoom);
        DungeonLayout.Room exit = new DungeonLayout.Room(bossRoom.centerX() - 3, bossRoom.centerX() + 3,
                bossRoom.maxZ() + 1, bossRoom.maxZ() + 9);
        if (collides(exit, occupied.subList(0, occupied.size() - 1), 2)) {
            return null;
        }

        // 5) layout ------------------------------------------------------------------
        List<DungeonLayout.Link> links = new ArrayList<>();
        links.add(link(-1, lobbyRoom, slots.get(0).index, slots.get(0).bounds, Side.MAXZ));
        for (Slot s : slots) {
            if (s.parent < 0) {
                continue;
            }
            links.add(link(s.parent, slots.get(s.parent).bounds, s.index, s.bounds, s.fromParent));
        }
        links.add(link(lastSpine.index, lastSpine.bounds, DungeonLayout.BOSS, bossRoom, Side.MAXZ));
        links.add(new DungeonLayout.Link(DungeonLayout.BOSS, DungeonLayout.EXIT,
                bossRoom.centerX(), bossRoom.maxZ(), exit.centerX(), exit.minZ(), false));

        List<Slot> lootCandidates = new ArrayList<>();
        for (Slot s : slots) {
            if (s.spine && s != lastSpine && !"SMALL".equals(s.sizeClass)) {
                lootCandidates.add(s);
            }
        }
        Slot lootRoom = lootCandidates.isEmpty() ? null : lootCandidates.get(rng.nextInt(lootCandidates.size()));

        List<DungeonLayout.CombatRoom> combat = new ArrayList<>();
        List<Placed> placedCombat = new ArrayList<>();
        for (Slot s : slots) {
            Placed placed = new Placed(s.index, s.template, s.rotation, s.bounds);
            placedCombat.add(placed);
            int[] loot = placed.world(s.template.lootX(), s.template.lootZ());
            // Gate = this room's own mouth on the side facing its parent.
            Side facing = s.fromParent.opposite();
            int gateX = switch (facing) {
                case MINX -> s.bounds.minX();
                case MAXX -> s.bounds.maxX();
                default -> s.bounds.centerX();
            };
            int gateZ = switch (facing) {
                case MINZ -> s.bounds.minZ();
                case MAXZ -> s.bounds.maxZ();
                default -> s.bounds.centerZ();
            };
            boolean gateAlongX = facing == Side.MINZ || facing == Side.MAXZ;
            String cls = s.sizeClass;
            int walkers = switch (cls) {
                case "HUGE" -> 9;
                case "LARGE" -> 7;
                case "MEDIUM" -> 6;
                default -> 5;
            };
            int archers = "HUGE".equals(cls) || "LARGE".equals(cls) ? 3 : 2;
            boolean brute = !"SMALL".equals(cls) || rng.nextBoolean();
            boolean mini = s == lastSpine || ("HUGE".equals(cls) && rng.nextDouble() < 0.5);
            combat.add(new DungeonLayout.CombatRoom(
                    s.index, s.bounds, DungeonLayout.Shape.HALL,
                    gateX, gateZ, gateAlongX,
                    false, false,
                    walkers, archers, brute,
                    s == lootRoom, loot[0], loot[1],
                    mini
            ));
        }
        DungeonLayout layout = DungeonLayout.fromParts(
                Long.hashCode(seed), lobbyRoom, combat, links, bossRoom, exit,
                bossRoom.centerX(), bossRoom.minZ(), false, true);
        Placed lobbyPlaced = new Placed(DungeonLayout.LOBBY, lobbyT, lobbyRot, lobbyRoom);
        Placed bossPlaced = new Placed(DungeonLayout.BOSS, bossT, bossRot, bossRoom);
        return new Plan(seed, lobbyPlaced, List.copyOf(placedCombat), bossPlaced, layout);
    }

    private static boolean assign(Slot slot, List<Template> pool, Set<String> used, Random rng, boolean strictClass) {
        List<Template> fitting = new ArrayList<>();
        for (Template t : pool) {
            if (strictClass && !t.sizeClass().equals(slot.sizeClass)) {
                continue;
            }
            if (used.contains(t.id())) {
                continue;
            }
            if (!t.rotationsFor(slot.required).isEmpty()) {
                fitting.add(t);
            }
        }
        if (fitting.isEmpty() && !strictClass) {
            for (Template t : pool) {
                if (!t.rotationsFor(slot.required).isEmpty()) {
                    fitting.add(t);
                }
            }
        }
        if (fitting.isEmpty()) {
            return false;
        }
        Template chosen = weighted(fitting, rng);
        List<Integer> rotations = chosen.rotationsFor(slot.required);
        slot.template = chosen;
        slot.rotation = rotations.get(rng.nextInt(rotations.size()));
        slot.sizeClass = chosen.sizeClass();
        used.add(chosen.id());
        return true;
    }

    private static int pickRotation(Template template, Set<Side> required, Random rng) {
        List<Integer> rotations = template.rotationsFor(required);
        if (rotations.isEmpty()) {
            return template.doors().isEmpty() ? 0 : -1;
        }
        return rotations.get(rng.nextInt(rotations.size()));
    }

    private static Template weighted(List<Template> list, Random rng) {
        int total = 0;
        for (Template t : list) {
            total += Math.max(1, t.weight());
        }
        int roll = rng.nextInt(Math.max(1, total));
        for (Template t : list) {
            roll -= Math.max(1, t.weight());
            if (roll < 0) {
                return t;
            }
        }
        return list.get(list.size() - 1);
    }

    /** New room of {@code size} hanging off {@code parent}'s {@code side}, centres aligned, {@code gap} corridor blocks. */
    private static DungeonLayout.Room attach(DungeonLayout.Room parent, Side side, int size, int gap) {
        int half = (size - 1) / 2;
        return switch (side) {
            case MAXZ -> new DungeonLayout.Room(parent.centerX() - half, parent.centerX() - half + size - 1,
                    parent.maxZ() + gap + 1, parent.maxZ() + gap + size);
            case MINZ -> new DungeonLayout.Room(parent.centerX() - half, parent.centerX() - half + size - 1,
                    parent.minZ() - gap - size, parent.minZ() - gap - 1);
            case MAXX -> new DungeonLayout.Room(parent.maxX() + gap + 1, parent.maxX() + gap + size,
                    parent.centerZ() - half, parent.centerZ() - half + size - 1);
            case MINX -> new DungeonLayout.Room(parent.minX() - gap - size, parent.minX() - gap - 1,
                    parent.centerZ() - half, parent.centerZ() - half + size - 1);
        };
    }

    private static DungeonLayout.Link link(int fromIndex, DungeonLayout.Room from, int toIndex, DungeonLayout.Room to, Side side) {
        return switch (side) {
            case MAXZ -> new DungeonLayout.Link(fromIndex, toIndex, from.centerX(), from.maxZ(), to.centerX(), to.minZ(), false);
            case MINZ -> new DungeonLayout.Link(fromIndex, toIndex, to.centerX(), to.maxZ(), from.centerX(), from.minZ(), false);
            case MAXX -> new DungeonLayout.Link(fromIndex, toIndex, from.maxX(), from.centerZ(), to.minX(), to.centerZ(), true);
            case MINX -> new DungeonLayout.Link(fromIndex, toIndex, to.maxX(), to.centerZ(), from.minX(), from.centerZ(), true);
        };
    }

    private static boolean collides(DungeonLayout.Room bounds, List<DungeonLayout.Room> occupied, int pad) {
        for (DungeonLayout.Room other : occupied) {
            if (bounds.overlaps(other, pad)) {
                return true;
            }
        }
        return false;
    }

    /** The corridor between parent and child must not run through any third room. */
    private static boolean corridorBlocked(DungeonLayout.Room parent, DungeonLayout.Room child, Side side, List<DungeonLayout.Room> occupied) {
        DungeonLayout.Room corridor = switch (side) {
            case MAXZ -> new DungeonLayout.Room(parent.centerX() - 3, parent.centerX() + 3, parent.maxZ() + 1, child.minZ() - 1);
            case MINZ -> new DungeonLayout.Room(parent.centerX() - 3, parent.centerX() + 3, child.maxZ() + 1, parent.minZ() - 1);
            case MAXX -> new DungeonLayout.Room(parent.maxX() + 1, child.minX() - 1, parent.centerZ() - 3, parent.centerZ() + 3);
            case MINX -> new DungeonLayout.Room(child.maxX() + 1, parent.minX() - 1, parent.centerZ() - 3, parent.centerZ() + 3);
        };
        for (DungeonLayout.Room other : occupied) {
            if (other.equals(parent)) {
                continue;
            }
            if (corridor.overlaps(other, 1)) {
                return true;
            }
        }
        return false;
    }
}
