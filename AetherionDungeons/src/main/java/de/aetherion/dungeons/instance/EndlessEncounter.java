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
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Endless XL: spawn 0 64 0 → clear ~75% trash (boss bar) → red glass gate → Frostbound placeholder.
 */
public final class EndlessEncounter {

    public static final int ROOM = 0;
    public static final String MOB_TAG = "endless_trash";
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = DungeonLayout.FLOOR_Y;
    public static final int SPAWN_Z = 0;
    /** Kill this share of the pack to open the boss gate. */
    public static final double CLEAR_RATIO = 0.75;
    /** Bump when fixed gate / spot rules change — forces prep rebuild without re-paste. */
    public static final int PREP_VERSION = 3;

    private static final int SPAWN_SAFE = 14;
    private static final Material DOOR_GLASS = Material.RED_STAINED_GLASS;

    /** Player-mapped red gate (passage rim). */
    private static final int GATE_X_MIN = 5;
    private static final int GATE_X_MAX = 7;
    private static final int GATE_Y_MIN = 130;
    private static final int GATE_Y_MAX = 138;
    private static final int GATE_Z = -138;

    /** Frostbound arena center. */
    public static final int BOSS_X = 6;
    public static final int BOSS_Y = 130;
    public static final int BOSS_Z = -166;
    private static final int MOB_WANT_MAX = 72;

    private static final Map<UUID, Deque<Location>> SAFE_HISTORY = new ConcurrentHashMap<>();
    private static final Map<String, EndlessState> BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RESCUE_COOLDOWN = new ConcurrentHashMap<>();

    private EndlessEncounter() {
    }

    /** Heavy work done during warm-pool bake (door + mob spots). */
    public static final class Prep {
        private final List<int[]> doorBlocks;
        private final List<int[]> mobSpots;
        private final int bossX;
        private final int bossY;
        private final int bossZ;
        private final int doorSlice;
        private final boolean alongZ;

        Prep(List<int[]> doorBlocks, List<int[]> mobSpots, int bossX, int bossY, int bossZ, int doorSlice, boolean alongZ) {
            this.doorBlocks = List.copyOf(doorBlocks);
            this.mobSpots = List.copyOf(mobSpots);
            this.bossX = bossX;
            this.bossY = bossY;
            this.bossZ = bossZ;
            this.doorSlice = doorSlice;
            this.alongZ = alongZ;
        }

        public int mobSpotCount() {
            return mobSpots.size();
        }

        public int doorBlockCount() {
            return doorBlocks.size();
        }

        public List<int[]> doorBlockList() {
            return doorBlocks;
        }

        public List<int[]> mobSpotList() {
            return mobSpots;
        }

        public int bossX() {
            return bossX;
        }

        public int bossY() {
            return bossY;
        }

        public int bossZ() {
            return bossZ;
        }

        public int doorSlice() {
            return doorSlice;
        }

        public boolean alongZ() {
            return alongZ;
        }
    }

    public static final class EndlessState {
        private final List<Block> doorBlocks = new ArrayList<>();
        private final Location spawn;
        private final Location bossAt;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final int mobsTotal;
        private final int killsNeeded;
        private BossBar bar;
        private boolean doorOpen;
        private boolean bossSpawned;
        private int kills;
        private int mobsLeft;
        private BukkitTask spawnTask;

        EndlessState(
                Location spawn,
                Location bossAt,
                int minX,
                int maxX,
                int minY,
                int maxY,
                int minZ,
                int maxZ,
                int mobsTotal,
                int killsNeeded
        ) {
            this.spawn = spawn.clone();
            this.bossAt = bossAt.clone();
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.mobsTotal = Math.max(1, mobsTotal);
            this.killsNeeded = Math.max(1, killsNeeded);
        }

        public int mobsLeft() {
            return mobsLeft;
        }

        public boolean doorOpen() {
            return doorOpen;
        }

        public double progress() {
            return Math.min(1.0, kills / (double) killsNeeded);
        }
    }

    public static EndlessState stateOf(World world) {
        return world == null ? null : BY_WORLD.get(world.getName());
    }

    public static void clear(World world) {
        if (world == null) {
            return;
        }
        EndlessState state = BY_WORLD.remove(world.getName());
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

    public static boolean isEndless(DungeonSession session) {
        return session != null && EndlessSchemBuilder.FLOOR_ID.equals(session.floorId());
    }

    /**
     * Warm-pool bake: fixed red gate + interior mob spots. Safe to run before any players join.
     */
    public static Prep prepare(
            Plugin plugin,
            World world,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);
        List<int[]> door = placeFixedGate(world);
        Location bossAt = new Location(world, BOSS_X + 0.5, BOSS_Y, BOSS_Z + 0.5);
        List<int[]> spots = collectMobSpots(world, spawn, minX, maxX, minY, maxY, minZ, maxZ);
        plugin.getLogger().info("Endless prep: door=" + door.size() + " spots=" + spots.size()
                + " boss=" + BOSS_X + "," + BOSS_Y + "," + BOSS_Z
                + " gateZ=" + GATE_Z);
        return new Prep(door, spots, BOSS_X, BOSS_Y, BOSS_Z, GATE_Z, true);
    }

    /**
     * After a run on the reusable base world: strip entities and rebuild the red gate.
     */
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
        for (int[] xyz : prep.doorBlocks) {
            world.getBlockAt(xyz[0], xyz[1], xyz[2]).setType(DOOR_GLASS, false);
        }
        Block reward = world.getBlockAt(BOSS_X + 3, BOSS_Y, BOSS_Z);
        if (DungeonLootFx.isLootChest(reward)) {
            reward.setType(Material.AIR, false);
        }
        BossEngineBridge.despawnInWorld(world);
        plugin.getLogger().info("Endless base recycled (" + world.getName() + ").");
    }

    /** Reward chest 3 blocks beside the Frostbound spawn. */
    public static void placeVictoryChest(Plugin plugin, World world) {
        if (world == null) {
            return;
        }
        world.getChunkAt(BOSS_X >> 4, BOSS_Z >> 4).load(true);
        DungeonLootFx.placeVictoryAt(plugin, world, BOSS_X + 3, BOSS_Y, BOSS_Z, 2);
    }

    public static Location begin(
            Plugin plugin,
            World world,
            DungeonSession session,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Prep prep
    ) {
        if (prep == null) {
            prep = prepare(plugin, world, minX, maxX, minY, maxY, minZ, maxZ);
        }
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5, 0f, 0f);
        Location bossAt = new Location(world, BOSS_X + 0.5, BOSS_Y, BOSS_Z + 0.5);
        // Cap to available spots so the door can never ask for more kills than can spawn.
        int planned = Math.min(MOB_WANT_MAX, Math.max(0, prep.mobSpots.size()));
        int killsNeeded = Math.max(1, (int) Math.ceil(Math.max(1, planned) * CLEAR_RATIO));
        EndlessState state = new EndlessState(
                spawn, bossAt, minX, maxX, minY, maxY, minZ, maxZ, planned, killsNeeded
        );
        for (int[] xyz : prep.doorBlocks) {
            Block block = world.getBlockAt(xyz[0], xyz[1], xyz[2]);
            if (block.getType() != DOOR_GLASS) {
                block.setType(DOOR_GLASS, false);
            }
            state.doorBlocks.add(block);
        }
        if (state.doorBlocks.isEmpty()) {
            for (int[] xyz : placeFixedGate(world)) {
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
                rememberSafe(member, spawn);
                bar.addPlayer(member);
            }
        }

        staggerSpawn(plugin, world, state, prep.mobSpots, planned);

        plugin.getLogger().info("Endless encounter ready. spawn=0,64,0 doorGlass=" + state.doorBlocks.size()
                + " plannedMobs=" + planned + " needKills=" + killsNeeded);
        return spawn;
    }

    public static void tickSafeHistory(DungeonSession session) {
        if (!isEndless(session) || session.world() == null) {
            return;
        }
        for (UUID id : session.party()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !player.getWorld().equals(session.world())) {
                continue;
            }
            if (inWater(player)) {
                continue;
            }
            Block below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock();
            if (!below.getType().isSolid()) {
                continue;
            }
            rememberSafe(player, player.getLocation());
            EndlessState state = stateOf(player.getWorld());
            if (state != null && state.bar != null && !state.bar.getPlayers().contains(player)) {
                state.bar.addPlayer(player);
            }
        }
    }

    public static boolean tryWaterRescue(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        EndlessState state = stateOf(player.getWorld());
        if (state == null || !inWater(player)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = RESCUE_COOLDOWN.get(player.getUniqueId());
        if (last != null && now - last < 1200L) {
            return true;
        }
        RESCUE_COOLDOWN.put(player.getUniqueId(), now);
        Location safe = safeFromHistory(player.getUniqueId());
        if (safe == null) {
            safe = state.spawn.clone();
        }
        safe.setYaw(player.getLocation().getYaw());
        safe.setPitch(player.getLocation().getPitch());
        player.teleport(safe);
        player.setFallDistance(0f);
        player.sendMessage("§bThe basin spit you back. §7Try the long way around.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.6f, 1.4f);
        return true;
    }

    public static void onMobDeath(Plugin plugin, LivingEntity entity) {
        if (entity == null || plugin == null) {
            return;
        }
        World world = entity.getWorld();
        EndlessState state = stateOf(world);
        if (state == null || state.doorOpen) {
            return;
        }
        Byte flag = entity.getPersistentDataContainer().get(endlessKey(plugin), PersistentDataType.BYTE);
        if (flag == null || flag != (byte) 1) {
            return;
        }
        state.kills++;
        state.mobsLeft = Math.max(0, countTrash(plugin, world) - 1);
        refreshBar(state);
        Bukkit.getScheduler().runTask(plugin, () -> {
            EndlessState live = stateOf(world);
            if (live == null || live.doorOpen) {
                return;
            }
            live.mobsLeft = countTrash(plugin, world);
            refreshBar(live);
            if (live.kills >= live.killsNeeded) {
                openBossDoor(plugin, world, live);
            }
        });
    }

    private static void refreshBar(EndlessState state) {
        if (state.bar == null) {
            return;
        }
        double progress = state.progress();
        state.bar.setProgress(progress);
        int pct = (int) Math.round(progress * 100.0);
        if (state.doorOpen) {
            state.bar.setTitle("§aGate open §8· §bFrostbound");
            state.bar.setColor(BarColor.BLUE);
        } else {
            state.bar.setTitle("§cClearance §8· §f" + pct + "% §8(§7need §f"
                    + state.killsNeeded + " §7kills§8)");
        }
    }

    private static void openBossDoor(Plugin plugin, World world, EndlessState state) {
        if (state.doorOpen) {
            return;
        }
        state.doorOpen = true;
        refreshBar(state);
        // Drop the red gate from top → bottom over a few ticks.
        List<Block> blocks = new ArrayList<>(state.doorBlocks);
        blocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        final int[] index = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(index[0] + 6, blocks.size());
            for (; index[0] < end; index[0]++) {
                Block block = blocks.get(index[0]);
                if (block.getType() == DOOR_GLASS || isGlass(block.getType())) {
                    block.setType(Material.AIR, false);
                }
            }
            if (index[0] >= blocks.size() && holder[0] != null) {
                holder[0].cancel();
            }
        }, 1L, 1L);

        world.playSound(state.spawn, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.55f);
        world.playSound(state.bossAt, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.1f);
        for (Player player : world.getPlayers()) {
            player.sendMessage("§aClearance reached. §7The §cred gate §7drops — §bFrostbound §7waits.");
            player.sendTitle("§aGate open", "§bFrostbound §7ahead", 8, 40, 12);
        }
        spawnFrostbound(plugin, world, state);
        DungeonSession session = null;
        try {
            Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
            if (dungeons instanceof de.aetherion.dungeons.AetherionDungeons ae) {
                session = ae.getInstances().sessionOf(world);
            }
        } catch (Throwable ignored) {
        }
        if (session != null) {
            session.setBossReleased(true);
        }
    }

    private static void spawnFrostbound(Plugin plugin, World world, EndlessState state) {
        if (state.bossSpawned) {
            return;
        }
        state.bossSpawned = true;
        Player initiator = world.getPlayers().isEmpty() ? null : world.getPlayers().get(0);
        Location at = state.bossAt.clone();
        at.getWorld().getChunkAt(at).load(true);
        boolean ok = BossEngineBridge.spawn(at, initiator, BossEngineBridge.FROSTBOUND, true);
        if (!ok) {
            // Fallback snow golem-ish from PrototypeDungeonBuilder path via engine miss.
            org.bukkit.entity.Snowman snowman = (org.bukkit.entity.Snowman) world.spawnEntity(at, EntityType.SNOW_GOLEM);
            snowman.setCustomName("§b§lThe Frostbound");
            snowman.setCustomNameVisible(true);
            snowman.getPersistentDataContainer().set(AetherKeys.DUNGEON_MOB, PersistentDataType.STRING, "dungeon_frostbound");
            snowman.getPersistentDataContainer().set(AetherKeys.BOSS_ID, PersistentDataType.STRING, BossEngineBridge.FROSTBOUND);
            AttributeInstance hp = snowman.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(200);
                snowman.setHealth(200);
            }
            plugin.getLogger().warning("Endless Frostbound: BossEngine spawn missed — used fallback snowman.");
        }
    }

    private static void staggerSpawn(Plugin plugin, World world, EndlessState state, List<int[]> spots, int want) {
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
        final int[] spawned = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (stateOf(world) != state) {
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                return;
            }
            int batch = 4;
            for (int n = 0; n < batch && cursor[0] < queue.size(); n++) {
                int[] xyz = queue.get(cursor[0]++);
                Location at = new Location(world, xyz[0] + 0.5, xyz[1] + 1, xyz[2] + 0.5);
                int kind = spawned[0]++;
                if (kind % 4 == 3) {
                    spawnArcher(plugin, world, at);
                } else if (kind % 5 == 4) {
                    spawnBrute(plugin, world, at);
                } else {
                    spawnWalker(plugin, world, at);
                }
            }
            if (cursor[0] >= queue.size()) {
                state.mobsLeft = countTrash(plugin, world);
                state.spawnTask = null;
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                plugin.getLogger().info("Endless trash finished spawning: " + state.mobsLeft);
            }
        }, 5L, 2L);
        state.spawnTask = holder[0];
    }

    private static int countTrash(Plugin plugin, World world) {
        int count = 0;
        NamespacedKey key = endlessKey(plugin);
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

    private static NamespacedKey endlessKey(Plugin plugin) {
        return new NamespacedKey(plugin, "endless_trash");
    }

    private static void rememberSafe(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        Deque<Location> history = SAFE_HISTORY.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        history.addLast(location.clone());
        while (history.size() > 5) {
            history.removeFirst();
        }
    }

    private static Location safeFromHistory(UUID playerId) {
        Deque<Location> history = SAFE_HISTORY.get(playerId);
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.peekFirst().clone();
    }

    public static void clearHistory(UUID playerId) {
        if (playerId != null) {
            SAFE_HISTORY.remove(playerId);
        }
    }

    private static boolean inWater(Player player) {
        Location loc = player.getLocation();
        Block at = loc.getBlock();
        Block eyes = loc.clone().add(0, 1.0, 0).getBlock();
        return at.isLiquid() || eyes.isLiquid()
                || at.getType() == Material.WATER
                || eyes.getType() == Material.WATER
                || player.isInWater();
    }

    private static boolean isGlass(Material material) {
        return material == Material.GLASS
                || material == Material.GRAY_STAINED_GLASS
                || material == Material.BLACK_STAINED_GLASS
                || material == Material.LIGHT_GRAY_STAINED_GLASS
                || material == DOOR_GLASS
                || material.name().endsWith("_STAINED_GLASS");
    }

    private static List<int[]> placeFixedGate(World world) {
        List<int[]> placed = new ArrayList<>();
        world.getChunkAt(GATE_X_MIN >> 4, GATE_Z >> 4).load(true);
        world.getChunkAt(BOSS_X >> 4, BOSS_Z >> 4).load(true);
        for (int x = GATE_X_MIN; x <= GATE_X_MAX; x++) {
            for (int y = GATE_Y_MIN; y <= GATE_Y_MAX; y++) {
                Block block = world.getBlockAt(x, y, GATE_Z);
                block.setType(DOOR_GLASS, false);
                placed.add(new int[]{x, y, GATE_Z});
            }
        }
        return placed;
    }

    private static List<int[]> collectMobSpots(
            World world,
            Location spawn,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        List<int[]> spots = new ArrayList<>();
        int sx = spawn.getBlockX();
        int sz = spawn.getBlockZ();
        int yMin = Math.max(minY, Math.min(SPAWN_Y - 2, GATE_Y_MIN - 8));
        int yMax = Math.min(maxY - 3, GATE_Y_MAX + 6);
        for (int x = minX + 3; x <= maxX - 3; x += 4) {
            for (int z = minZ + 3; z <= maxZ - 3; z += 4) {
                if (z <= GATE_Z + 1) {
                    continue;
                }
                double dist = Math.abs(x - sx) + Math.abs(z - sz);
                if (dist < SPAWN_SAFE) {
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
                for (int floorY = yMin; floorY <= yMax; floorY++) {
                    if (!indoorStandable(world, x, floorY, z)) {
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
                && feet.getType() != Material.WATER
                && head.getType() != Material.WATER;
    }

    private static boolean indoorStandable(World world, int x, int floorY, int z) {
        if (!standable(world, x, floorY, z)) {
            return false;
        }
        for (int dy = 3; dy <= 10; dy++) {
            if (world.getBlockAt(x, floorY + dy, z).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
    private static void spawnWalker(Plugin plugin, World world, Location at) {
        Zombie zombie = (Zombie) world.spawnEntity(at, EntityType.ZOMBIE);
        tagEndless(plugin, zombie, "dungeon_zombie");
        label(plugin, zombie, "§bPacked Walker");
        zombie.setAdult();
        arm(zombie, Material.IRON_SWORD, Material.LEATHER_CHESTPLATE, Material.IRON_LEGGINGS);
        setHealth(plugin, zombie, 12000);
        setDamage(zombie, 420);
    }

    private static void spawnArcher(Plugin plugin, World world, Location at) {
        Skeleton skeleton = (Skeleton) world.spawnEntity(at, EntityType.STRAY);
        tagEndless(plugin, skeleton, "dungeon_skeleton");
        label(plugin, skeleton, "§bPacked Archer");
        arm(skeleton, Material.BOW, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_HELMET);
        setHealth(plugin, skeleton, 9000);
        setDamage(skeleton, 340);
    }

    private static void spawnBrute(Plugin plugin, World world, Location at) {
        Husk husk = (Husk) world.spawnEntity(at, EntityType.HUSK);
        tagEndless(plugin, husk, "dungeon_brute");
        label(plugin, husk, "§bPacked Brute");
        husk.setAdult();
        arm(husk, Material.IRON_AXE, Material.IRON_CHESTPLATE, Material.IRON_HELMET);
        setHealth(plugin, husk, 24000);
        setDamage(husk, 580);
        AttributeInstance scale = husk.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(1.25);
        }
    }

    private static void tagEndless(Plugin plugin, LivingEntity entity, String id) {
        entity.getPersistentDataContainer().set(AetherKeys.DUNGEON_MOB, PersistentDataType.STRING, id);
        entity.getPersistentDataContainer().set(endlessKey(plugin), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_room"),
                PersistentDataType.INTEGER,
                ROOM
        );
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
    }

    private static void label(Plugin plugin, LivingEntity entity, String label) {
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_label"),
                PersistentDataType.STRING,
                label
        );
        entity.setCustomName(label);
        entity.setCustomNameVisible(true);
    }

    private static void setHealth(Plugin plugin, LivingEntity entity, double health) {
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_hp"),
                PersistentDataType.DOUBLE,
                health
        );
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_max_hp"),
                PersistentDataType.DOUBLE,
                health
        );
        double vanilla = Math.min(health, 1024.0);
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(vanilla);
        }
        entity.setHealth(vanilla);
        PrototypeDungeonBuilder.refreshDungeonNameplate(plugin, entity);
    }

    private static void setDamage(LivingEntity entity, double damage) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.setBaseValue(damage);
        }
    }

    private static void arm(LivingEntity entity, Material weapon, Material chest, Material head) {
        if (entity.getEquipment() == null) {
            return;
        }
        entity.getEquipment().setItemInMainHand(new ItemStack(weapon));
        entity.getEquipment().setChestplate(new ItemStack(chest));
        entity.getEquipment().setHelmet(new ItemStack(head));
        entity.getEquipment().setItemInMainHandDropChance(0f);
        entity.getEquipment().setChestplateDropChance(0f);
        entity.getEquipment().setHelmetDropChance(0f);
    }
}
