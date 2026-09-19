package de.aetherion.dungeons.instance;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Lerfing jigsaw template floors: prison (live Floor 1) and ice (dev test).
 * Lobby is a safe zone; combat starts after ready in the first linked chamber.
 */
public final class LerfingTestBuilder {

    /** Legacy id — prison test is now live Floor 1 ({@link PrototypeDungeonBuilder#FLOOR_ID}). */
    public static final String FLOOR_ID = "prototype_test";
    public static final String ICE_FLOOR_ID = "prototype_ice_test";
    /** Legacy enter code; treated as Floor 1. */
    public static final int FLOOR_NUMBER = 4;
    /** Dev-only ice template test (gameplay = Floor 2 Frostbound). */
    public static final int ICE_TEST_FLOOR = 5;

    private static final int PIECE = 21;
    private static final int WALL_H = 6;
    private static final int HALL_H = 5;
    private static final ThreadLocal<TemplateStyle> STYLE = new ThreadLocal<>();

    public enum TemplateStyle {
        PRISON(
                "prison",
                "prison",
                12,
                Material.STONE_BRICKS,
                Material.STONE_BRICKS,
                Material.STONE_BRICK_SLAB,
                Material.DEEPSLATE_TILES
        ),
        ICE(
                "ice",
                "ice",
                12,
                Material.PACKED_ICE,
                Material.BLUE_ICE,
                Material.SNOW_BLOCK,
                Material.PACKED_ICE
        );

        final String folder;
        final String filePrefix;
        final int pieceCount;
        final Material wall;
        final Material floor;
        final Material ceil;
        final Material exitFloor;

        TemplateStyle(
                String folder,
                String filePrefix,
                int pieceCount,
                Material wall,
                Material floor,
                Material ceil,
                Material exitFloor
        ) {
            this.folder = folder;
            this.filePrefix = filePrefix;
            this.pieceCount = pieceCount;
            this.wall = wall;
            this.floor = floor;
            this.ceil = ceil;
            this.exitFloor = exitFloor;
        }
    }

    private LerfingTestBuilder() {
    }

    public static boolean isTemplateEnterCode(int floor) {
        return floor == 1 || floor == FLOOR_NUMBER;
    }

    public static TemplateStyle styleForEnterCode(int floor) {
        return TemplateStyle.PRISON;
    }

    public static void ensureStructures(Plugin plugin) {
        ensureStructures(plugin, TemplateStyle.PRISON);
        ensureStructures(plugin, TemplateStyle.ICE);
    }

    public static void ensureStructures(Plugin plugin, TemplateStyle style) {
        if (plugin == null || style == null) {
            return;
        }
        File dir = new File(plugin.getDataFolder(), "structures/lerfing/" + style.folder);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create structures/lerfing/" + style.folder + ".");
        }
        for (int i = 1; i <= style.pieceCount; i++) {
            String path = "structures/lerfing/" + style.folder + "/" + style.filePrefix + i + ".nbt";
            File out = new File(plugin.getDataFolder(), path);
            if (!out.exists()) {
                try {
                    plugin.saveResource(path, false);
                } catch (IllegalArgumentException ignored) {
                    // Ice (and optional extras) may only exist once dropped into the data folder.
                }
            }
        }
    }

    public static Location build(Plugin plugin, World world, DungeonLayout layout) {
        return build(plugin, world, layout, TemplateStyle.PRISON);
    }

    public static Location build(Plugin plugin, World world, DungeonLayout layout, TemplateStyle style) {
        TemplateStyle active = style == null ? TemplateStyle.PRISON : style;
        ensureStructures(plugin, active);
        STYLE.set(active);
        try {
            PrototypeDungeonBuilder.applyPartyScale(1, 1);
            int y = DungeonLayout.FLOOR_Y;

            int pieceIndex = 1;
            paste(plugin, world, layout.lobby().minX(), y, layout.lobby().minZ(), pieceIndex++);
            scrubWalkLayer(world, y, layout.lobby());

            for (DungeonLayout.CombatRoom room : layout.combatRooms()) {
                paste(plugin, world, room.bounds().minX(), y, room.bounds().minZ(), pieceIndex++);
                scrubWalkLayer(world, y, room.bounds());
            }
            paste(plugin, world, layout.boss().minX(), y, layout.boss().minZ(), pieceIndex);
            scrubWalkLayer(world, y, layout.boss());

            for (DungeonLayout.Link link : layout.links()) {
                if (link.toIndex() == DungeonLayout.EXIT) {
                    continue;
                }
                buildCorridor(world, y, link);
            }

            sealRoom(world, y, layout.lobby());
            for (DungeonLayout.CombatRoom room : layout.combatRooms()) {
                sealRoom(world, y, room.bounds());
            }
            sealRoom(world, y, layout.boss());

            for (DungeonLayout.Link link : layout.links()) {
                if (link.toIndex() == DungeonLayout.EXIT) {
                    continue;
                }
                prepareMouth(world, y, link, true);
                prepareMouth(world, y, link, false);
            }

            placeAmbientLights(world, y, layout);

            DungeonLayout.Room exit = layout.exit();
            for (int x = exit.minX(); x <= exit.maxX(); x++) {
                for (int z = exit.minZ(); z <= exit.maxZ(); z++) {
                    world.getBlockAt(x, y, z).setType(active.exitFloor, false);
                    world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                    world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
                }
            }
            PrototypeDungeonBuilder.placePortal(world, layout);

            DungeonLayout.Room lobby = layout.lobby();
            double spawnX = lobby.centerX() + 0.5;
            double spawnZ = lobby.centerZ() + 0.5;
            clearSpawnPocket(world, lobby.centerX(), y, lobby.centerZ());
            PrototypeDungeonBuilder.spawnReadyNpc(
                    plugin,
                    world,
                    new Location(world, spawnX + 2.0, y + 1, spawnZ, -90f, 0f)
            );

            return new Location(world, spawnX, y + 1, spawnZ, 0f, 0f);
        } finally {
            STYLE.remove();
        }
    }

    public static void openSeam(World world, DungeonLayout.Link link) {
        if (world == null || link == null || link.toIndex() == DungeonLayout.EXIT) {
            return;
        }
        int y = DungeonLayout.FLOOR_Y;
        boolean spanX = gateSpanAlongX(link);
        DungeonGateFx.release(world, link.fromWallX(), link.fromWallZ(), spanX);
        DungeonGateFx.release(world, link.toWallX(), link.toWallZ(), spanX);
        openMouth(world, y, link.fromWallX(), link.fromWallZ(), link.alongX());
        openMouth(world, y, link.toWallX(), link.toWallZ(), link.alongX());
        clearCorridorAir(world, y, link);
    }

    /** @deprecated use {@link #openSeam(World, DungeonLayout.Link)} */
    public static void openSeam(World world, DungeonLayout.Room from, DungeonLayout.Room to) {
        if (world == null || from == null || to == null) {
            return;
        }
        DungeonLayout.Link fake = new DungeonLayout.Link(
                0, 1, from.centerX(), from.maxZ(), to.centerX(), to.minZ(), false
        );
        openSeam(world, fake);
    }

    private static TemplateStyle style() {
        TemplateStyle style = STYLE.get();
        return style == null ? TemplateStyle.PRISON : style;
    }

    private static void scrubWalkLayer(World world, int y, DungeonLayout.Room room) {
        Material floorMat = style().floor;
        for (int x = room.minX(); x <= room.maxX(); x++) {
            for (int z = room.minZ(); z <= room.maxZ(); z++) {
                Material floor = world.getBlockAt(x, y, z).getType();
                if (floor == Material.BEDROCK || floor == Material.BARRIER) {
                    world.getBlockAt(x, y, z).setType(floorMat, false);
                }
                for (int dy = 1; dy <= 4; dy++) {
                    Material m = world.getBlockAt(x, y + dy, z).getType();
                    if (m == Material.BEDROCK
                            || m == Material.BARRIER
                            || m == Material.IRON_DOOR
                            || m == Material.IRON_BARS
                            || m.name().endsWith("_DOOR")) {
                        world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static void buildCorridor(World world, int y, DungeonLayout.Link link) {
        Material wall = style().wall;
        Material floor = style().floor;
        Material ceil = style().ceil;
        if (link.alongX()) {
            int cz = link.fromWallZ();
            int x1 = Math.min(link.fromWallX(), link.toWallX()) + 1;
            int x2 = Math.max(link.fromWallX(), link.toWallX()) - 1;
            if (x2 < x1) {
                return;
            }
            for (int x = x1; x <= x2; x++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.getBlockAt(x, y, cz + dz).setType(floor, false);
                    world.getBlockAt(x, y + HALL_H, cz + dz).setType(ceil, false);
                }
                for (int dy = 1; dy < HALL_H; dy++) {
                    world.getBlockAt(x, y + dy, cz - 3).setType(wall, false);
                    world.getBlockAt(x, y + dy, cz + 3).setType(wall, false);
                    for (int dz = -2; dz <= 2; dz++) {
                        world.getBlockAt(x, y + dy, cz + dz).setType(Material.AIR, false);
                    }
                }
                if ((x - x1) % 3 == 0) {
                    placeLight(world, x, y + 3, cz);
                }
            }
            return;
        }
        int cx = link.fromWallX();
        int z1 = Math.min(link.fromWallZ(), link.toWallZ()) + 1;
        int z2 = Math.max(link.fromWallZ(), link.toWallZ()) - 1;
        if (z2 < z1) {
            return;
        }
        for (int z = z1; z <= z2; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                world.getBlockAt(cx + dx, y, z).setType(floor, false);
                world.getBlockAt(cx + dx, y + HALL_H, z).setType(ceil, false);
            }
            for (int dy = 1; dy < HALL_H; dy++) {
                world.getBlockAt(cx - 3, y + dy, z).setType(wall, false);
                world.getBlockAt(cx + 3, y + dy, z).setType(wall, false);
                for (int dx = -2; dx <= 2; dx++) {
                    world.getBlockAt(cx + dx, y + dy, z).setType(Material.AIR, false);
                }
            }
            if ((z - z1) % 3 == 0) {
                placeLight(world, cx, y + 3, z);
            }
        }
    }

    private static void clearCorridorAir(World world, int y, DungeonLayout.Link link) {
        if (link.alongX()) {
            int cz = link.fromWallZ();
            int x1 = Math.min(link.fromWallX(), link.toWallX());
            int x2 = Math.max(link.fromWallX(), link.toWallX());
            for (int x = x1; x <= x2; x++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        if (world.getBlockAt(x, y + dy, cz + dz).getType() != Material.LIGHT) {
                            world.getBlockAt(x, y + dy, cz + dz).setType(Material.AIR, false);
                        }
                    }
                }
            }
            return;
        }
        int cx = link.fromWallX();
        int z1 = Math.min(link.fromWallZ(), link.toWallZ());
        int z2 = Math.max(link.fromWallZ(), link.toWallZ());
        for (int z = z1; z <= z2; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    if (world.getBlockAt(cx + dx, y + dy, z).getType() != Material.LIGHT) {
                        world.getBlockAt(cx + dx, y + dy, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static void sealRoom(World world, int y, DungeonLayout.Room room) {
        Material wall = style().wall;
        Material floor = style().floor;
        Material ceil = style().ceil;
        int minX = room.minX();
        int maxX = room.maxX();
        int minZ = room.minZ();
        int maxZ = room.maxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Material under = world.getBlockAt(x, y, z).getType();
                if (under.isAir() || under == Material.BEDROCK || under == Material.BARRIER) {
                    world.getBlockAt(x, y, z).setType(floor, false);
                }
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            for (int dy = 0; dy <= WALL_H; dy++) {
                world.getBlockAt(minX, y + dy, z).setType(wall, false);
                world.getBlockAt(maxX, y + dy, z).setType(wall, false);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int dy = 0; dy <= WALL_H; dy++) {
                world.getBlockAt(x, y + dy, minZ).setType(wall, false);
                world.getBlockAt(x, y + dy, maxZ).setType(wall, false);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            world.getBlockAt(x, y + WALL_H, minZ).setType(ceil, false);
            world.getBlockAt(x, y + WALL_H, maxZ).setType(ceil, false);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.getBlockAt(minX, y + WALL_H, z).setType(ceil, false);
            world.getBlockAt(maxX, y + WALL_H, z).setType(ceil, false);
        }
    }

    private static void prepareMouth(World world, int y, DungeonLayout.Link link, boolean fromSide) {
        int wallX = fromSide ? link.fromWallX() : link.toWallX();
        int wallZ = fromSide ? link.fromWallZ() : link.toWallZ();
        punchDeep(world, y, wallX, wallZ, link.alongX());
        DungeonGateFx.seal(world, wallX, wallZ, gateSpanAlongX(link));
    }

    private static void openMouth(World world, int y, int wallX, int wallZ, boolean alongX) {
        punchDeep(world, y, wallX, wallZ, alongX);
    }

    private static boolean gateSpanAlongX(DungeonLayout.Link link) {
        return !link.alongX();
    }

    private static void punchDeep(World world, int y, int wallX, int wallZ, boolean alongX) {
        Material floor = style().floor;
        if (alongX) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        Material m = world.getBlockAt(wallX + dx, y + dy, wallZ + dz).getType();
                        if (m != Material.LIGHT) {
                            world.getBlockAt(wallX + dx, y + dy, wallZ + dz).setType(Material.AIR, false);
                        }
                    }
                    Material under = world.getBlockAt(wallX + dx, y, wallZ + dz).getType();
                    if (under.isAir() || under == Material.BEDROCK || under == Material.BARRIER) {
                        world.getBlockAt(wallX + dx, y, wallZ + dz).setType(floor, false);
                    }
                }
            }
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    Material m = world.getBlockAt(wallX + dx, y + dy, wallZ + dz).getType();
                    if (m != Material.LIGHT) {
                        world.getBlockAt(wallX + dx, y + dy, wallZ + dz).setType(Material.AIR, false);
                    }
                }
                Material under = world.getBlockAt(wallX + dx, y, wallZ + dz).getType();
                if (under.isAir() || under == Material.BEDROCK || under == Material.BARRIER) {
                    world.getBlockAt(wallX + dx, y, wallZ + dz).setType(floor, false);
                }
            }
        }
    }

    private static void placeAmbientLights(World world, int y, DungeonLayout layout) {
        scatterLights(world, y, layout.lobby(), 10);
        for (DungeonLayout.CombatRoom room : layout.combatRooms()) {
            scatterLights(world, y, room.bounds(), 12);
        }
        scatterLights(world, y, layout.boss(), 14);
        for (DungeonLayout.Link link : layout.links()) {
            if (link.toIndex() == DungeonLayout.EXIT) {
                continue;
            }
            scatterLights(world, y, link.corridorBounds(), 4);
        }
    }

    private static void scatterLights(World world, int floorY, DungeonLayout.Room room, int budget) {
        if (room == null || budget <= 0) {
            return;
        }
        placeLight(world, room.centerX(), floorY + 2, room.centerZ());
        if (room.maxX() <= room.minX() + 2 || room.maxZ() <= room.minZ() + 2) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = budget * 8;
        int placed = 0;
        for (int i = 0; i < attempts && placed < budget; i++) {
            int x = random.nextInt(room.minX() + 1, room.maxX());
            int z = random.nextInt(room.minZ() + 1, room.maxZ());
            Material under = world.getBlockAt(x, floorY, z).getType();
            if (!under.isSolid() || under == Material.BARRIER) {
                continue;
            }
            if (placeLight(world, x, floorY + 2, z)) {
                placed++;
            }
        }
        placeLight(world, room.minX() + 2, floorY + 2, room.minZ() + 2);
        placeLight(world, room.maxX() - 2, floorY + 2, room.maxZ() - 2);
    }

    private static boolean placeLight(World world, int x, int y, int z) {
        Material current = world.getBlockAt(x, y, z).getType();
        if (!current.isAir() && current != Material.LIGHT) {
            return false;
        }
        org.bukkit.block.data.BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof org.bukkit.block.data.Levelled levelled) {
            levelled.setLevel(15);
            world.getBlockAt(x, y, z).setBlockData(levelled, false);
            return true;
        }
        world.getBlockAt(x, y, z).setType(Material.LIGHT, false);
        return true;
    }

    private static void clearSpawnPocket(World world, int cx, int y, int z) {
        Material floor = style().floor;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(cx + dx, y + dy, z + dz).setType(Material.AIR, false);
                }
                Material under = world.getBlockAt(cx + dx, y, z + dz).getType();
                if (under.isAir() || under == Material.BEDROCK || under == Material.BARRIER) {
                    world.getBlockAt(cx + dx, y, z + dz).setType(floor, false);
                }
            }
        }
    }

    private static void paste(Plugin plugin, World world, int x, int y, int z, int pieceIndex) {
        TemplateStyle style = style();
        int safe = ((pieceIndex - 1) % style.pieceCount) + 1;
        File file = resolvePiece(plugin, style, safe);
        if (file == null || !file.isFile()) {
            if (style == TemplateStyle.ICE) {
                plugin.getLogger().warning(
                        "Ice piece missing (ice" + safe + ".nbt). Using ice fallback chamber. "
                                + "Drop Patreon ICE NBTs into plugins/AetherionDungeons/structures/lerfing/ice/"
                );
            } else {
                plugin.getLogger().warning("Prison piece missing for index " + safe);
            }
            fillFallbackCube(world, x, y, z);
            return;
        }
        StructureManager manager = plugin.getServer().getStructureManager();
        try {
            Structure structure = manager.loadStructure(file);
            Location origin = new Location(world, x, y, z);
            structure.place(
                    origin,
                    true,
                    StructureRotation.NONE,
                    Mirror.NONE,
                    0,
                    1.0f,
                    ThreadLocalRandom.current()
            );
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to paste " + file.getName(), exception);
            fillFallbackCube(world, x, y, z);
        }
    }

    private static File resolvePiece(Plugin plugin, TemplateStyle style, int index) {
        File primary = new File(
                plugin.getDataFolder(),
                "structures/lerfing/" + style.folder + "/" + style.filePrefix + index + ".nbt"
        );
        if (primary.isFile()) {
            return primary;
        }
        if (style == TemplateStyle.ICE) {
            // Common Patreon / generator naming variants.
            String[] alts = {
                    "ice_dungeon" + index + ".nbt",
                    "ice_dungeon_" + index + ".nbt",
                    "frozen" + index + ".nbt",
                    "snow" + index + ".nbt"
            };
            File dir = new File(plugin.getDataFolder(), "structures/lerfing/ice");
            for (String name : alts) {
                File alt = new File(dir, name);
                if (alt.isFile()) {
                    return alt;
                }
            }
        }
        return primary;
    }

    private static void fillFallbackCube(World world, int x, int y, int z) {
        TemplateStyle style = style();
        for (int dx = 0; dx < PIECE; dx++) {
            for (int dz = 0; dz < PIECE; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(style.floor, false);
                if (style == TemplateStyle.ICE) {
                    boolean rim = dx == 0 || dz == 0 || dx == PIECE - 1 || dz == PIECE - 1;
                    boolean pillar = (dx == 4 || dx == 16) && (dz == 4 || dz == 16);
                    for (int dy = 1; dy <= 4; dy++) {
                        if (rim) {
                            world.getBlockAt(x + dx, y + dy, z + dz).setType(style.wall, false);
                        } else if (pillar && dy <= 3) {
                            world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.BLUE_ICE, false);
                        } else {
                            world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                        }
                    }
                    if (!rim) {
                        world.getBlockAt(x + dx, y + 5, z + dz).setType(style.ceil, false);
                    }
                }
            }
        }
    }
}
