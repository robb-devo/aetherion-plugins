package de.aetherion.dungeons.instance;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class DungeonLayout {

    public static final int FLOOR_Y = 64;
    public static final int GATE_Z = 12;
    public static final int LOBBY = -1;
    public static final int BOSS = -2;
    public static final int EXIT = -3;

    public enum Shape {
        HALL,
        PILLARS,
        CROSS,
        OFFSET,
        TIGHT
    }

    public record Room(int minX, int maxX, int minZ, int maxZ) {
        public int centerX() {
            return (minX + maxX) / 2;
        }

        public int centerZ() {
            return (minZ + maxZ) / 2;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public Room inflate(int pad) {
            return new Room(minX - pad, maxX + pad, minZ - pad, maxZ + pad);
        }

        public boolean overlaps(Room other, int pad) {
            return minX - pad <= other.maxX
                    && maxX + pad >= other.minX
                    && minZ - pad <= other.maxZ
                    && maxZ + pad >= other.minZ;
        }
    }

    public record Link(
            int fromIndex,
            int toIndex,
            int fromWallX,
            int fromWallZ,
            int toWallX,
            int toWallZ,
            boolean alongX
    ) {
        public Room corridorBounds() {
            if (alongX) {
                int z = fromWallZ;
                int minX = Math.min(fromWallX, toWallX) + 1;
                int maxX = Math.max(fromWallX, toWallX) - 1;
                if (maxX < minX) {
                    minX = Math.min(fromWallX, toWallX);
                    maxX = Math.max(fromWallX, toWallX);
                }
                return new Room(minX, maxX, z - 2, z + 2);
            }
            int x = fromWallX;
            int minZ = Math.min(fromWallZ, toWallZ) + 1;
            int maxZ = Math.max(fromWallZ, toWallZ) - 1;
            if (maxZ < minZ) {
                minZ = Math.min(fromWallZ, toWallZ);
                maxZ = Math.max(fromWallZ, toWallZ);
            }
            return new Room(x - 2, x + 2, minZ, maxZ);
        }
    }

    public record CombatRoom(
            int index,
            Room bounds,
            Shape shape,
            int gateX,
            int gateZ,
            boolean gateAlongX,
            boolean westAlcove,
            boolean eastAlcove,
            int walkers,
            int archers,
            boolean brute,
            boolean lootRoom,
            int lootX,
            int lootZ,
            boolean miniBoss
    ) {
        public String title() {
            return "Chamber " + roman(index + 1);
        }

        public BlockFace gateFacing() {
            if (gateAlongX) {
                return gateZ <= bounds.minZ() ? BlockFace.SOUTH : BlockFace.NORTH;
            }
            return gateX <= bounds.minX() ? BlockFace.EAST : BlockFace.WEST;
        }
    }

    private final int seed;
    private final Room lobby;
    private final List<CombatRoom> combatRooms;
    private final List<Link> links;
    private final Room boss;
    private final Room exit;
    private final int bossGateX;
    private final int bossGateZ;
    private final boolean bossGateAlongX;
    private final boolean templateFloor;

    private DungeonLayout(
            int seed,
            Room lobby,
            List<CombatRoom> combatRooms,
            List<Link> links,
            Room boss,
            Room exit,
            int bossGateX,
            int bossGateZ,
            boolean bossGateAlongX
    ) {
        this(seed, lobby, combatRooms, links, boss, exit, bossGateX, bossGateZ, bossGateAlongX, false);
    }

    private DungeonLayout(
            int seed,
            Room lobby,
            List<CombatRoom> combatRooms,
            List<Link> links,
            Room boss,
            Room exit,
            int bossGateX,
            int bossGateZ,
            boolean bossGateAlongX,
            boolean templateFloor
    ) {
        this.seed = seed;
        this.lobby = lobby;
        this.combatRooms = List.copyOf(combatRooms);
        this.links = List.copyOf(links);
        this.boss = boss;
        this.exit = exit;
        this.bossGateX = bossGateX;
        this.bossGateZ = bossGateZ;
        this.bossGateAlongX = bossGateAlongX;
        this.templateFloor = templateFloor;
    }

    public static DungeonLayout generate(long seedValue) {
        return generate(seedValue, 1);
    }

    public static DungeonLayout generate(long seedValue, int floor) {
        int tier = Math.max(1, Math.min(3, floor));
        Random random = new Random(seedValue);
        Room lobby;
        if (tier >= 3) {
            lobby = new Room(-18, 18, 0, 24);
        } else if (tier >= 2) {
            lobby = new Room(-16, 16, 0, 21);
        } else {
            lobby = new Room(-13, 13, 0, 17);
        }
        int count;
        if (tier >= 3) {
            count = 7 + random.nextInt(3);
        } else if (tier >= 2) {
            count = 6 + random.nextInt(2);
        } else {
            count = 4 + random.nextInt(2);
        }
        List<CombatRoom> rooms = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<Room> occupied = new ArrayList<>();
        occupied.add(lobby);

        CombatRoom first = place(random, lobby, 0, 1, occupied, true, tier);
        rooms.add(first);
        occupied.add(first.bounds());
        links.add(new Link(LOBBY, 0, 0, lobby.maxZ(), first.gateX(), first.bounds().minZ(), false));

        List<Integer> frontier = new ArrayList<>();
        frontier.add(0);

        for (int i = 1; i < count; i++) {
            boolean placed = false;
            List<Integer> order = new ArrayList<>(frontier);
            Collections.shuffle(order, random);
            List<Integer> dirs = new ArrayList<>();
            if (i == 1) {
                dirs.add(random.nextBoolean() ? 2 : 3);
                dirs.add(dirs.get(0) == 2 ? 3 : 2);
                dirs.add(1);
            } else {
                dirs.add(1);
                dirs.add(2);
                dirs.add(3);
                Collections.shuffle(dirs.subList(1, dirs.size()), random);
                if (random.nextBoolean()) {
                    Collections.swap(dirs, 0, 1 + random.nextInt(2));
                }
            }
            for (int parentIdx : order) {
                for (int dir : dirs) {
                    CombatRoom next = place(random, rooms.get(parentIdx).bounds(), i, dir, occupied, false, tier);
                    if (next == null) {
                        continue;
                    }
                    rooms.add(next);
                    occupied.add(next.bounds());
                    links.add(linkOf(parentIdx, rooms.get(parentIdx).bounds(), i, next));
                    frontier.add(i);
                    placed = true;
                    break;
                }
                if (placed) {
                    break;
                }
            }
            if (!placed) {
                CombatRoom deepest = rooms.get(deepestIndex(rooms));
                CombatRoom forced = forceSouth(random, deepest.bounds(), i, occupied, tier);
                rooms.add(forced);
                occupied.add(forced.bounds());
                links.add(linkOf(deepest.index(), deepest.bounds(), i, forced));
                frontier.add(i);
            }
        }

        int lootIndex = 1 + random.nextInt(Math.max(1, count - 1));
        rooms.set(lootIndex, withLoot(rooms.get(lootIndex), true));
        if (tier >= 3 && count >= 5) {
            int second = 1 + random.nextInt(Math.max(1, count - 1));
            if (second == lootIndex) {
                second = (lootIndex + 2) % count;
            }
            rooms.set(second, withLoot(rooms.get(second), true));
        }
        for (int i = 0; i < rooms.size(); i++) {
            if (!rooms.get(i).lootRoom()) {
                rooms.set(i, withLoot(rooms.get(i), false));
            }
        }
        int miniIndex = 1 + random.nextInt(Math.max(1, count - 1));
        rooms.set(miniIndex, withMiniBoss(rooms.get(miniIndex)));

        CombatRoom deepest = rooms.get(deepestIndex(rooms));
        int corridor = (tier >= 3 ? 14 : tier >= 2 ? 12 : 8) + random.nextInt(5);
        int bossMinZ = maxZ(occupied) + corridor + 1;
        int bossHalf;
        int bossLength;
        if (tier >= 3) {
            bossHalf = 28 + random.nextInt(5);
            bossLength = 48 + random.nextInt(10);
        } else if (tier >= 2) {
            bossHalf = 24 + random.nextInt(4);
            bossLength = 42 + random.nextInt(6);
        } else {
            bossHalf = 13 + random.nextInt(3);
            bossLength = 24 + random.nextInt(5);
        }
        int bossCx = deepest.bounds().centerX();
        Room boss = new Room(bossCx - bossHalf, bossCx + bossHalf, bossMinZ, bossMinZ + bossLength - 1);
        int exitPad = tier >= 3 ? 7 : 5;
        Room exit = new Room(bossCx - exitPad, bossCx + exitPad, boss.maxZ() + 1, boss.maxZ() + (tier >= 3 ? 12 : 9));
        int bossGateX = bossCx;
        int bossGateZ = boss.minZ();
        links.add(new Link(deepest.index(), BOSS, deepest.bounds().centerX(), deepest.bounds().maxZ(), bossGateX, bossGateZ, false));
        links.add(new Link(BOSS, EXIT, boss.centerX(), boss.maxZ(), exit.centerX(), exit.minZ(), false));

        return new DungeonLayout(
                Long.hashCode(seedValue),
                lobby,
                rooms,
                links,
                boss,
                exit,
                bossGateX,
                bossGateZ,
                true
        );
    }

    /**
     * Branching Lerfing prison test: separate safe lobby, then combat chambers
     * in different directions (like Floor 1), boss at the far end.
     */
    public static DungeonLayout lerfingTest(long seedValue) {
        final int piece = 21;
        final int gap = 11;
        final int step = piece + gap;

        // Safe lobby — no combat index.
        Room lobby = new Room(0, piece - 1, 0, piece - 1);

        // Combat 0 north of lobby.
        Room c0 = shift(lobby, 0, step);
        // Combat 1 east of c0.
        Room c1 = shift(c0, step, 0);
        // Combat 2 west of c0 (or north if we want variety — branch both sides).
        Room c2 = shift(c0, -step, 0);
        // Combat 3 north of c0 (deeper).
        Room c3 = shift(c0, 0, step);

        List<CombatRoom> rooms = new ArrayList<>();
        rooms.add(combat(0, c0, false, true, false));
        rooms.add(combat(1, c1, true, false, false));
        rooms.add(combat(2, c2, false, false, false));
        rooms.add(combat(3, c3, false, true, true));

        List<Link> links = new ArrayList<>();
        links.add(linkZ(LOBBY, 0, lobby, c0));
        links.add(linkX(0, 1, c0, c1));
        links.add(linkX(0, 2, c2, c0));
        links.add(linkZ(0, 3, c0, c3));

        Room boss = shift(c3, 0, step);
        Room exit = new Room(boss.minX(), boss.maxX(), boss.maxZ() + 1, boss.maxZ() + 8);
        links.add(linkZ(3, BOSS, c3, boss));
        links.add(new Link(BOSS, EXIT, boss.centerX(), boss.maxZ(), exit.centerX(), exit.minZ(), false));

        return new DungeonLayout(
                Long.hashCode(seedValue),
                lobby,
                rooms,
                links,
                boss,
                exit,
                boss.centerX(),
                boss.minZ(),
                false,
                true
        );
    }

    /**
     * Shell layout for a pasted schematic test — no combat rooms, lobby = full footprint.
     */
    public static DungeonLayout schemShell(int minX, int maxX, int minZ, int maxZ) {
        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loZ = Math.min(minZ, maxZ);
        int hiZ = Math.max(minZ, maxZ);
        Room footprint = new Room(loX, hiX, loZ, hiZ);
        Room exit = new Room(footprint.centerX() - 2, footprint.centerX() + 2, loZ - 6, loZ - 1);
        return new DungeonLayout(
                footprint.hashCode(),
                footprint,
                List.of(),
                List.of(),
                footprint,
                exit,
                footprint.centerX(),
                footprint.minZ(),
                false,
                true
        );
    }

    private static Room shift(Room base, int dx, int dz) {
        return new Room(
                base.minX() + dx,
                base.maxX() + dx,
                base.minZ() + dz,
                base.maxZ() + dz
        );
    }

    private static CombatRoom combat(int index, Room bounds, boolean loot, boolean brute, boolean mini) {
        return new CombatRoom(
                index,
                bounds,
                Shape.HALL,
                bounds.centerX(),
                bounds.minZ(),
                false,
                false,
                false,
                5,
                2,
                brute,
                loot,
                bounds.centerX(),
                bounds.centerZ(),
                mini
        );
    }

    /** {@code south} is the lower-Z room, {@code north} the higher-Z room. */
    private static Link linkZ(int from, int to, Room south, Room north) {
        return new Link(from, to, south.centerX(), south.maxZ(), north.centerX(), north.minZ(), false);
    }

    /** {@code west} is the lower-X room, {@code east} the higher-X room. */
    private static Link linkX(int from, int to, Room west, Room east) {
        return new Link(from, to, west.maxX(), west.centerZ(), east.minX(), east.centerZ(), true);
    }

    private static CombatRoom place(Random random, Room parent, int index, int dir, List<Room> occupied, boolean first, int floor) {
        int grow = floor >= 3 ? 10 : floor >= 2 ? 10 : 5;
        for (int attempt = 0; attempt < 8; attempt++) {
            Shape shape = first ? Shape.HALL : Shape.values()[random.nextInt(Shape.values().length)];
            int corridor = (floor >= 3 ? 12 : floor >= 2 ? 10 : 8) + random.nextInt(8);
            int width;
            int depth;
            if (shape == Shape.TIGHT) {
                width = 12 + grow + random.nextInt(4);
                depth = 15 + grow + random.nextInt(6);
            } else if (shape == Shape.CROSS) {
                width = 16 + grow + random.nextInt(6);
                depth = 16 + grow + random.nextInt(6);
            } else if (shape == Shape.PILLARS) {
                width = 16 + grow + random.nextInt(6);
                depth = 16 + grow + random.nextInt(6);
            } else {
                width = 15 + grow + random.nextInt(7);
                depth = 16 + grow + random.nextInt(7);
            }
            if (width % 2 == 0) {
                width++;
            }
            int half = width / 2;
            Room bounds;
            int gateX;
            int gateZ;
            boolean gateAlongX;
            if (dir == 1) {
                int cx = first ? 0 : parent.centerX() + random.nextInt(5) - 2;
                int minZ = parent.maxZ() + corridor + 1;
                bounds = new Room(cx - half, cx + half, minZ, minZ + depth - 1);
                gateX = cx;
                gateZ = index == 0 ? parent.maxZ() + 1 : bounds.minZ();
                gateAlongX = true;
            } else if (dir == 2) {
                int cz = parent.centerZ() + random.nextInt(5) - 2;
                int minX = parent.maxX() + corridor + 1;
                bounds = new Room(minX, minX + depth - 1, cz - half, cz + half);
                gateX = bounds.minX();
                gateZ = cz;
                gateAlongX = false;
            } else if (dir == 3) {
                int cz = parent.centerZ() + random.nextInt(5) - 2;
                int maxX = parent.minX() - corridor - 1;
                bounds = new Room(maxX - depth + 1, maxX, cz - half, cz + half);
                gateX = bounds.maxX();
                gateZ = cz;
                gateAlongX = false;
            } else {
                return null;
            }
            if (bounds.minX() >= bounds.maxX() || bounds.minZ() >= bounds.maxZ()) {
                continue;
            }
            if (collides(bounds, occupied, 4)) {
                continue;
            }
            return makeRoom(random, index, bounds, shape, gateX, gateZ, gateAlongX, false);
        }
        return null;
    }

    private static CombatRoom forceSouth(Random random, Room parent, int index, List<Room> occupied, int floor) {
        int pad = floor >= 3 ? 16 : floor >= 2 ? 14 : 11;
        int depth = 13 + (floor >= 3 ? 10 : floor >= 2 ? 6 : 8);
        int z = maxZ(occupied) + 8;
        Room bounds = new Room(parent.centerX() - pad, parent.centerX() + pad, z, z + depth - 1);
        while (collides(bounds, occupied, 3)) {
            z += 4;
            bounds = new Room(bounds.minX(), bounds.maxX(), z, z + depth - 1);
        }
        return makeRoom(random, index, bounds, Shape.HALL, bounds.centerX(), bounds.minZ(), true, false);
    }

    private static CombatRoom makeRoom(
            Random random,
            int index,
            Room bounds,
            Shape shape,
            int gateX,
            int gateZ,
            boolean gateAlongX,
            boolean loot
    ) {
        boolean westAlcove = (shape == Shape.CROSS || random.nextInt(10) < 4)
                && !( !gateAlongX && gateX == bounds.minX());
        boolean eastAlcove = (shape == Shape.CROSS || random.nextInt(10) < 4)
                && !( !gateAlongX && gateX == bounds.maxX());
        return new CombatRoom(
                index,
                bounds,
                shape,
                gateX,
                gateZ,
                gateAlongX,
                westAlcove,
                eastAlcove,
                4 + random.nextInt(3),
                2 + random.nextInt(2),
                shape != Shape.TIGHT && random.nextInt(10) < 7,
                loot,
                bounds.minX() + 2,
                bounds.minZ() + 2,
                false
        );
    }

    private static CombatRoom withLoot(CombatRoom room, boolean loot) {
        return new CombatRoom(
                room.index(),
                room.bounds(),
                room.shape(),
                room.gateX(),
                room.gateZ(),
                room.gateAlongX(),
                room.westAlcove(),
                room.eastAlcove(),
                room.walkers(),
                room.archers(),
                room.brute(),
                loot,
                room.bounds().minX() + 2,
                room.bounds().minZ() + 2,
                room.miniBoss()
        );
    }

    private static CombatRoom withMiniBoss(CombatRoom room) {
        return new CombatRoom(
                room.index(),
                room.bounds(),
                room.shape(),
                room.gateX(),
                room.gateZ(),
                room.gateAlongX(),
                room.westAlcove(),
                room.eastAlcove(),
                room.walkers(),
                room.archers(),
                room.brute(),
                room.lootRoom(),
                room.lootX(),
                room.lootZ(),
                true
        );
    }

    private static Link linkOf(int fromIndex, Room from, int toIndex, CombatRoom to) {
        boolean alongX = !to.gateAlongX();
        int toX = to.gateX();
        int toZ = to.gateZ();
        int fromX;
        int fromZ;
        if (alongX) {
            fromZ = toZ;
            fromX = toX < from.centerX() ? from.minX() : from.maxX();
        } else {
            fromX = toX;
            fromZ = toZ < from.centerZ() ? from.minZ() : from.maxZ();
        }
        return new Link(fromIndex, toIndex, fromX, fromZ, toX, toZ, alongX);
    }

    private static boolean collides(Room bounds, List<Room> occupied, int pad) {
        for (Room room : occupied) {
            if (bounds.overlaps(room, pad)) {
                return true;
            }
        }
        return false;
    }

    private static int maxZ(List<Room> occupied) {
        int max = 0;
        for (Room room : occupied) {
            max = Math.max(max, room.maxZ());
        }
        return max;
    }

    private static int deepestIndex(List<CombatRoom> rooms) {
        int best = 0;
        for (int i = 1; i < rooms.size(); i++) {
            if (rooms.get(i).bounds().maxZ() > rooms.get(best).bounds().maxZ()) {
                best = i;
            }
        }
        return best;
    }

    public int seed() {
        return seed;
    }

    public int gateZ() {
        return lobby.maxZ() + 1;
    }

    public int lobbyGateX() {
        return 0;
    }

    public int lobbyGateZ() {
        return lobby.maxZ() + 1;
    }

    public int bossGateX() {
        return bossGateX;
    }

    public int bossGateZ() {
        return bossGateZ;
    }

    public boolean bossGateAlongX() {
        return bossGateAlongX;
    }

    public boolean templateFloor() {
        return templateFloor;
    }

    public Room lobby() {
        return lobby;
    }

    public List<CombatRoom> combatRooms() {
        return combatRooms;
    }

    public List<Link> links() {
        return links;
    }

    public CombatRoom combat(int index) {
        return combatRooms.get(index);
    }

    public CombatRoom lootRoom() {
        for (CombatRoom room : combatRooms) {
            if (room.lootRoom()) {
                return room;
            }
        }
        return combatRooms.get(Math.min(1, combatRooms.size() - 1));
    }

    public int combatCount() {
        return combatRooms.size();
    }

    public Room boss() {
        return boss;
    }

    public Room exit() {
        return exit;
    }

    public CombatRoom hall() {
        return combatRooms.get(0);
    }

    public List<Integer> neighbors(int roomIndex) {
        List<Integer> next = new ArrayList<>();
        for (Link link : links) {
            if (link.fromIndex() == roomIndex && link.toIndex() >= 0) {
                next.add(link.toIndex());
            }
        }
        return next;
    }

    /** Incoming link that unlocks {@code toIndex} (lobby→0, room→room, room→boss). */
    public Link linkInto(int toIndex) {
        return incoming(toIndex);
    }

    public Room bounds() {
        int minX = lobby.minX();
        int maxX = lobby.maxX();
        int minZ = lobby.minZ();
        int maxZ = exit.maxZ();
        for (CombatRoom room : combatRooms) {
            minX = Math.min(minX, room.bounds().minX());
            maxX = Math.max(maxX, room.bounds().maxX());
            minZ = Math.min(minZ, room.bounds().minZ());
            maxZ = Math.max(maxZ, room.bounds().maxZ());
        }
        minX = Math.min(minX, Math.min(boss.minX(), exit.minX()));
        maxX = Math.max(maxX, Math.max(boss.maxX(), exit.maxX()));
        return new Room(minX - 6, maxX + 6, minZ - 4, maxZ + 6);
    }

    public boolean allows(int x, int z, Set<Integer> unlocked, Set<Integer> cleared, boolean started, boolean bossReleased) {
        if (lobby.inflate(1).contains(x, z)) {
            return true;
        }
        if (!started) {
            return z <= lobby.maxZ() + 1.45 && Math.abs(x) <= 3;
        }
        if (bossReleased && (boss.inflate(2).contains(x, z) || exit.inflate(2).contains(x, z))) {
            return true;
        }
        for (CombatRoom room : combatRooms) {
            if (open(room.index(), unlocked, cleared) && room.bounds().inflate(1).contains(x, z)) {
                return true;
            }
        }
        for (Link link : links) {
            if (!linkWalkable(link, unlocked, cleared, started, bossReleased)) {
                continue;
            }
            if (link.corridorBounds().inflate(1).contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    public Location bounceTarget(World world, int x, int z, Set<Integer> unlocked, Set<Integer> cleared, boolean started) {
        double y = FLOOR_Y + 1;
        if (!started) {
            return new Location(world, 0.5, y, 3.5);
        }
        for (CombatRoom room : combatRooms) {
            if (room.bounds().contains(x, z) && !open(room.index(), unlocked, cleared)) {
                Link incoming = incoming(room.index());
                if (incoming != null && incoming.fromIndex() >= 0) {
                    Room from = combat(incoming.fromIndex()).bounds();
                    return new Location(world, from.centerX() + 0.5, y, from.centerZ() + 0.5);
                }
                return new Location(world, 0.5, y, 3.5);
            }
        }
        if (boss.contains(x, z) || exit.contains(x, z)) {
            CombatRoom deepest = combat(deepestIndex(combatRooms));
            return new Location(world, deepest.bounds().centerX() + 0.5, y, deepest.bounds().centerZ() + 0.5);
        }
        return new Location(world, 0.5, y, 3.5);
    }

    private Link incoming(int roomIndex) {
        for (Link link : links) {
            if (link.toIndex() == roomIndex) {
                return link;
            }
        }
        return null;
    }

    private static boolean open(int index, Set<Integer> unlocked, Set<Integer> cleared) {
        return unlocked.contains(index) || cleared.contains(index);
    }

    private boolean linkWalkable(Link link, Set<Integer> unlocked, Set<Integer> cleared, boolean started, boolean bossReleased) {
        if (link.toIndex() == BOSS || link.fromIndex() == BOSS) {
            return bossReleased;
        }
        if (link.fromIndex() == LOBBY) {
            return started;
        }
        return open(link.fromIndex(), unlocked, cleared);
    }

    public String roomId(double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        if (lobby.contains(ix, iz)) {
            return "lobby";
        }
        for (CombatRoom room : combatRooms) {
            if (room.bounds().contains(ix, iz)) {
                return "combat_" + room.index();
            }
        }
        if (boss.contains(ix, iz)) {
            return "boss";
        }
        if (exit.contains(ix, iz)) {
            return "exit";
        }
        return "corridor";
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    public static String chamberName(int index) {
        return "Chamber " + roman(index + 1);
    }

    public static String shapeLabel(Shape shape) {
        return switch (shape) {
            case HALL -> "Open hall";
            case PILLARS -> "Pillar hall";
            case CROSS -> "Cross chamber";
            case OFFSET -> "Shifted hall";
            case TIGHT -> "Tight cut";
        };
    }
}
