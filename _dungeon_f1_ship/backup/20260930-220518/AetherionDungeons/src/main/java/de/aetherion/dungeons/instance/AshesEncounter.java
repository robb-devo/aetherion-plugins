package de.aetherion.dungeons.instance;

import de.aetherion.core.AetherKeys;
import de.aetherion.dungeons.bridge.BossEngineBridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floor 3 · Throne of Ashes: clear trash → drop barrier wall → Aetherion in the open arena.
 *
 * Coords from live map walk (player-marked spawn + barrier + arena corners).
 */
public final class AshesEncounter {

    public static final String FLOOR_ID = "prototype_ashes_3";
    public static final String MOB_TAG = "ashes_trash";
    public static final int PREP_VERSION = 1;

    /** Entrance spawn (Multiverse mark on mmo-c). */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = 100;
    public static final int SPAWN_Z = 0;

    /**
     * Boss-gate barrier in the end wall (drops after clearance).
     * From 194,96,-74 up to 194,102,-68.
     */
    private static final int GATE_X = 194;
    private static final int GATE_Y_MIN = 96;
    private static final int GATE_Y_MAX = 102;
    private static final int GATE_Z_MIN = -74;
    private static final int GATE_Z_MAX = -68;

    /** Rough open boss arena (player corners). */
    public static final int ARENA_MIN_X = 206;
    public static final int ARENA_MAX_X = 288;
    public static final int ARENA_MIN_Z = -111;
    public static final int ARENA_MAX_Z = -31;

    public static final int BOSS_X = (ARENA_MIN_X + ARENA_MAX_X) / 2;
    public static final int BOSS_Y = 98;
    public static final int BOSS_Z = (ARENA_MIN_Z + ARENA_MAX_Z) / 2;

    private static final Material DOOR = Material.RED_STAINED_GLASS;
    private static final double CLEAR_RATIO = 0.75;
    private static final int MOB_WANT_MAX = 64;
    private static final int SPAWN_SAFE = 12;

    private static final Map<String, AshesState> BY_WORLD = new ConcurrentHashMap<>();

    private AshesEncounter() {
    }

    public static final class Prep {
        private final List<int[]> doorBlocks;
        private final List<int[]> mobSpots;

        Prep(List<int[]> doorBlocks, List<int[]> mobSpots) {
            this.doorBlocks = List.copyOf(doorBlocks);
            this.mobSpots = List.copyOf(mobSpots);
        }

        public List<int[]> doorBlocks() {
            return doorBlocks;
        }

        public List<int[]> mobSpots() {
            return mobSpots;
        }
    }

    public static final class AshesState {
        private final List<Block> doorBlocks = new ArrayList<>();
        private final Location spawn;
        private final Location bossAt;
        private final int mobsTotal;
        private final int killsNeeded;
        private BossBar bar;
        private boolean doorOpen;
        private boolean bossSpawned;
        private int kills;
        private int mobsLeft;
        private BukkitTask spawnTask;

        AshesState(Location spawn, Location bossAt, int mobsTotal, int killsNeeded) {
            this.spawn = spawn;
            this.bossAt = bossAt;
            this.mobsTotal = mobsTotal;
            this.killsNeeded = killsNeeded;
            this.mobsLeft = mobsTotal;
        }

        double progress() {
            return Math.min(1.0, kills / (double) Math.max(1, killsNeeded));
        }
    }

    public static boolean isAshes(DungeonSession session) {
        return session != null && FLOOR_ID.equals(session.floorId());
    }

    public static DungeonLayout layoutShell() {
        return DungeonLayout.schemShell(ARENA_MIN_X - 40, ARENA_MAX_X + 10, ARENA_MIN_Z - 10, ARENA_MAX_Z + 40);
    }

    public static Prep prepare(Plugin plugin, World world) {
        List<int[]> door = placeGate(world);
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);
        List<int[]> spots = collectMobSpots(world, spawn);
        plugin.getLogger().info("Ashes prep: door=" + door.size() + " spots=" + spots.size()
                + " boss=" + BOSS_X + "," + BOSS_Y + "," + BOSS_Z
                + " gateX=" + GATE_X);
        return new Prep(door, spots);
    }

    public static void resetForReuse(Plugin plugin, World world, Prep prep) {
        if (world == null || prep == null) {
            return;
        }
        clear(world);
        for (org.bukkit.entity.Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity instanceof Player) {
                continue;
            }
            entity.remove();
        }
        for (int[] xyz : prep.doorBlocks()) {
            world.getBlockAt(xyz[0], xyz[1], xyz[2]).setType(DOOR, false);
        }
        BossEngineBridge.despawnInWorld(world);
        plugin.getLogger().info("Ashes base recycled (" + world.getName() + ").");
    }

    public static void placeVictoryChest(Plugin plugin, World world) {
        if (world == null) {
            return;
        }
        world.getChunkAt(BOSS_X >> 4, BOSS_Z >> 4).load(true);
        DungeonLootFx.placeVictoryAt(plugin, world, BOSS_X + 4, BOSS_Y, BOSS_Z, 3);
    }

    public static Location begin(Plugin plugin, World world, DungeonSession session, Prep prep) {
        if (prep == null) {
            prep = prepare(plugin, world);
        }
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5, 0f, 0f);
        Location bossAt = new Location(world, BOSS_X + 0.5, BOSS_Y, BOSS_Z + 0.5);
        int planned = Math.min(MOB_WANT_MAX, Math.max(0, prep.mobSpots().size()));
        int killsNeeded = Math.max(1, (int) Math.ceil(Math.max(1, planned) * CLEAR_RATIO));
        AshesState state = new AshesState(spawn, bossAt, planned, killsNeeded);
        for (int[] xyz : prep.doorBlocks()) {
            Block block = world.getBlockAt(xyz[0], xyz[1], xyz[2]);
            if (block.getType() != DOOR) {
                block.setType(DOOR, false);
            }
            state.doorBlocks.add(block);
        }
        if (state.doorBlocks.isEmpty()) {
            for (int[] xyz : placeGate(world)) {
                state.doorBlocks.add(world.getBlockAt(xyz[0], xyz[1], xyz[2]));
            }
        }

        BossBar bar = Bukkit.createBossBar("§cClearance §8· §f0%", BarColor.RED, BarStyle.SEGMENTED_10);
        bar.setProgress(0.0);
        bar.setVisible(true);
        state.bar = bar;
        BY_WORLD.put(world.getName(), state);

        world.setSpawnLocation(spawn);
        for (UUID id : session.party()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                bar.addPlayer(member);
            }
        }

        staggerSpawn(plugin, world, state, prep.mobSpots(), planned);
        plugin.getLogger().info("Ashes Floor 3 live. spawn=" + SPAWN_X + "," + SPAWN_Y + "," + SPAWN_Z
                + " need=" + killsNeeded + "/" + planned);
        return spawn;
    }

    public static void clear(World world) {
        if (world == null) {
            return;
        }
        AshesState state = BY_WORLD.remove(world.getName());
        if (state == null) {
            return;
        }
        if (state.spawnTask != null) {
            state.spawnTask.cancel();
            state.spawnTask = null;
        }
        if (state.bar != null) {
            state.bar.removeAll();
            state.bar.setVisible(false);
            state.bar = null;
        }
    }

    public static void onMobDeath(Plugin plugin, LivingEntity entity) {
        if (entity == null || entity.getWorld() == null) {
            return;
        }
        World world = entity.getWorld();
        AshesState state = BY_WORLD.get(world.getName());
        if (state == null || state.doorOpen) {
            return;
        }
        Byte flag = entity.getPersistentDataContainer().get(ashesKey(plugin), PersistentDataType.BYTE);
        if (flag == null || flag != (byte) 1) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            AshesState live = BY_WORLD.get(world.getName());
            if (live == null || live.doorOpen) {
                return;
            }
            live.kills++;
            live.mobsLeft = Math.max(0, live.mobsLeft - 1);
            refreshBar(live);
            if (live.kills >= live.killsNeeded) {
                openBossDoor(plugin, world, live);
            }
        });
    }

    private static void refreshBar(AshesState state) {
        if (state.bar == null) {
            return;
        }
        double progress = state.progress();
        state.bar.setProgress(progress);
        int pct = (int) Math.round(progress * 100.0);
        if (state.doorOpen) {
            state.bar.setTitle("§aGate open §8· §5Aetherion");
            state.bar.setColor(BarColor.PURPLE);
        } else {
            state.bar.setTitle("§cClearance §8· §f" + pct + "% §8(§7need §f"
                    + state.killsNeeded + " §7kills§8)");
        }
    }

    private static void openBossDoor(Plugin plugin, World world, AshesState state) {
        if (state.doorOpen) {
            return;
        }
        state.doorOpen = true;
        refreshBar(state);
        List<Block> blocks = new ArrayList<>(state.doorBlocks);
        blocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        final int[] index = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(index[0] + 4, blocks.size());
            for (; index[0] < end; index[0]++) {
                Block block = blocks.get(index[0]);
                if (block.getType() == DOOR || block.getType().name().endsWith("_STAINED_GLASS")) {
                    block.setType(Material.AIR, false);
                }
            }
            if (index[0] >= blocks.size() && holder[0] != null) {
                holder[0].cancel();
            }
        }, 1L, 1L);

        world.playSound(state.spawn, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.55f);
        world.playSound(state.bossAt, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.7f);
        for (Player player : world.getPlayers()) {
            player.sendMessage("§aClearance reached. §7The barrier falls — §5Aetherion §7waits in the ash arena.");
            player.sendTitle("§aGate open", "§5Aetherion §7ahead", 8, 40, 12);
        }
        spawnAetherion(plugin, world, state);
        try {
            Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
            if (dungeons instanceof de.aetherion.dungeons.AetherionDungeons ae) {
                DungeonSession session = ae.getInstances().sessionOf(world);
                if (session != null) {
                    session.setBossReleased(true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void spawnAetherion(Plugin plugin, World world, AshesState state) {
        if (state.bossSpawned) {
            return;
        }
        state.bossSpawned = true;
        Player initiator = world.getPlayers().isEmpty() ? null : world.getPlayers().get(0);
        Location at = state.bossAt.clone();
        at.getWorld().getChunkAt(at).load(true);
        boolean ok = BossEngineBridge.spawn(at, initiator, BossEngineBridge.AETHERION, false);
        if (!ok) {
            org.bukkit.entity.Enderman enderman =
                    (org.bukkit.entity.Enderman) world.spawnEntity(at, EntityType.ENDERMAN);
            enderman.setCustomName("§5§lAetherion");
            enderman.setCustomNameVisible(true);
            enderman.getPersistentDataContainer().set(
                    AetherKeys.DUNGEON_MOB, PersistentDataType.STRING, "dungeon_aetherion");
            enderman.getPersistentDataContainer().set(
                    AetherKeys.BOSS_ID, PersistentDataType.STRING, BossEngineBridge.AETHERION);
            AttributeInstance hp = enderman.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(400);
                enderman.setHealth(400);
            }
            plugin.getLogger().warning("Ashes Aetherion: BossEngine spawn missed — fallback enderman.");
        }
    }

    private static void staggerSpawn(Plugin plugin, World world, AshesState state, List<int[]> spots, int want) {
        List<int[]> pick = new ArrayList<>(spots);
        if (pick.isEmpty()) {
            state.mobsLeft = 0;
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = pick.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = pick.get(i);
            pick.set(i, pick.get(j));
            pick.set(j, tmp);
        }
        if (pick.size() > want) {
            pick = new ArrayList<>(pick.subList(0, want));
        }
        final List<int[]> queue = pick;
        final int[] cursor = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (BY_WORLD.get(world.getName()) != state) {
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                return;
            }
            for (int n = 0; n < 4 && cursor[0] < queue.size(); n++) {
                int[] xyz = queue.get(cursor[0]++);
                Location at = new Location(world, xyz[0] + 0.5, xyz[1] + 1, xyz[2] + 0.5);
                if (cursor[0] % 3 == 0) {
                    spawnBrute(plugin, world, at);
                } else {
                    spawnSkeleton(plugin, world, at);
                }
            }
            if (cursor[0] >= queue.size()) {
                state.mobsLeft = countTrash(plugin, world);
                state.spawnTask = null;
                if (holder[0] != null) {
                    holder[0].cancel();
                }
            }
        }, 5L, 2L);
        state.spawnTask = holder[0];
    }

    private static void spawnSkeleton(Plugin plugin, World world, Location at) {
        WitherSkeleton mob = (WitherSkeleton) world.spawnEntity(at, EntityType.WITHER_SKELETON);
        tagTrash(plugin, mob);
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(28);
            mob.setHealth(28);
        }
    }

    private static void spawnBrute(Plugin plugin, World world, Location at) {
        PiglinBrute mob = (PiglinBrute) world.spawnEntity(at, EntityType.PIGLIN_BRUTE);
        mob.setImmuneToZombification(true);
        tagTrash(plugin, mob);
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(40);
            mob.setHealth(40);
        }
    }

    private static void tagTrash(Plugin plugin, LivingEntity entity) {
        entity.getPersistentDataContainer().set(ashesKey(plugin), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(
                AetherKeys.DUNGEON_MOB, PersistentDataType.STRING, MOB_TAG);
        entity.setRemoveWhenFarAway(false);
    }

    private static int countTrash(Plugin plugin, World world) {
        int count = 0;
        NamespacedKey key = ashesKey(plugin);
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.isDead() || !entity.isValid()) {
                continue;
            }
            Byte flag = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
            if (flag != null && flag == (byte) 1) {
                count++;
            }
        }
        return count;
    }

    private static NamespacedKey ashesKey(Plugin plugin) {
        return new NamespacedKey(plugin, MOB_TAG);
    }

    private static List<int[]> placeGate(World world) {
        List<int[]> placed = new ArrayList<>();
        world.getChunkAt(GATE_X >> 4, GATE_Z_MIN >> 4).load(true);
        world.getChunkAt(BOSS_X >> 4, BOSS_Z >> 4).load(true);
        for (int y = GATE_Y_MIN; y <= GATE_Y_MAX; y++) {
            for (int z = GATE_Z_MIN; z <= GATE_Z_MAX; z++) {
                Block block = world.getBlockAt(GATE_X, y, z);
                block.setType(DOOR, false);
                placed.add(new int[]{GATE_X, y, z});
            }
        }
        return placed;
    }

    private static List<int[]> collectMobSpots(World world, Location spawn) {
        List<int[]> spots = new ArrayList<>();
        int sx = spawn.getBlockX();
        int sz = spawn.getBlockZ();
        // Crawl volume before the barrier — not inside the boss arena.
        int minX = Math.min(sx - 30, GATE_X - 80);
        int maxX = GATE_X - 2;
        int minZ = Math.min(sz - 40, GATE_Z_MIN - 40);
        int maxZ = Math.max(sz + 40, GATE_Z_MAX + 20);
        for (int x = minX; x <= maxX; x += 4) {
            for (int z = minZ; z <= maxZ; z += 4) {
                if (Math.abs(x - sx) + Math.abs(z - sz) < SPAWN_SAFE) {
                    continue;
                }
                if (x >= ARENA_MIN_X) {
                    continue;
                }
                int cx = x >> 4;
                int cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    try {
                        world.getChunkAt(cx, cz).load(true);
                    } catch (Throwable ignored) {
                        continue;
                    }
                }
                for (int floorY = SPAWN_Y - 8; floorY <= SPAWN_Y + 6; floorY++) {
                    if (!standable(world, x, floorY, z)) {
                        continue;
                    }
                    spots.add(new int[]{x, floorY, z});
                    break;
                }
            }
        }
        return spots;
    }

    private static boolean standable(World world, int x, int y, int z) {
        Block floor = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        return floor.getType().isSolid()
                && !feet.getType().isSolid()
                && !head.getType().isSolid()
                && feet.getType() != Material.LAVA
                && feet.getType() != Material.WATER;
    }
}
