package de.aetherion.dungeons.instance;

/**
 * Stable ids for Throne of Ashes map chests adopted as Floor 3 loot caches.
 * Kept off Bukkit so the range can be tested without a server.
 */
public final class AshesChestIds {

    /** Floor passed to {@code DungeonLootFx} combat rolls (Dungeon Core III). */
    public static final int FLOOR = 3;

    /** Above room indexes and vestige ids (1000+). Victory cache stays at -2. */
    public static final int BASE = 5_000;
    public static final int SPAN = 2_000_000;

    /**
     * Padding around spawn and the ash arena. Chunk loads outside this box stay vanilla
     * so a full overworld copy is not turned into loot chests.
     */
    public static final int SCAN_PAD = 512;

    private AshesChestIds() {
    }

    public static int id(int x, int y, int z) {
        long mixed = (long) x * 73856093L ^ (long) y * 19349663L ^ (long) z * 83492791L;
        int positive = (int) (mixed & 0x7fffffffL);
        return BASE + Math.floorMod(positive, SPAN);
    }

    /**
     * Spawn and arena corners copied from {@link AshesEncounter}. Inlined so this class
     * does not load Bukkit when unit tests touch the id range.
     */
    private static final int SPAWN_X = 0;
    private static final int SPAWN_Z = 0;
    private static final int ARENA_MIN_X = 206;
    private static final int ARENA_MAX_X = 288;
    private static final int ARENA_MIN_Z = -111;
    private static final int ARENA_MAX_Z = -31;
    private static final int BOSS_X = (ARENA_MIN_X + ARENA_MAX_X) / 2;
    private static final int BOSS_Z = (ARENA_MIN_Z + ARENA_MAX_Z) / 2;

    public static int minX() {
        return Math.min(SPAWN_X, ARENA_MIN_X) - SCAN_PAD;
    }

    public static int maxX() {
        return Math.max(SPAWN_X, ARENA_MAX_X) + SCAN_PAD;
    }

    public static int minZ() {
        return Math.min(SPAWN_Z, ARENA_MIN_Z) - SCAN_PAD;
    }

    public static int maxZ() {
        return Math.max(SPAWN_Z, ARENA_MAX_Z) + SCAN_PAD;
    }

    public static boolean coversSpawn() {
        return inScan(SPAWN_X, SPAWN_Z);
    }

    public static boolean coversArena() {
        return inScan(ARENA_MIN_X, ARENA_MIN_Z)
                && inScan(ARENA_MAX_X, ARENA_MAX_Z)
                && inScan(BOSS_X, BOSS_Z);
    }

    public static boolean inScan(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }
}
