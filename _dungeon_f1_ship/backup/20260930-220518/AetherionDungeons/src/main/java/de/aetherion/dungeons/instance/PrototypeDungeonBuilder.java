package de.aetherion.dungeons.instance;

import de.aetherion.core.AetherKeys;
import de.aetherion.dungeons.AetherionDungeons;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PrototypeDungeonBuilder {

    public static final String FLOOR_ID = "prototype_1";
    public static final String FLOOR_2_ID = "prototype_2";
    public static final String FLOOR_3_ID = "prototype_3";
    public static final String READY_NPC = "ready_steward";
    public static final int GATE_Z = DungeonLayout.GATE_Z;
    public static final int FLOOR_Y = DungeonLayout.FLOOR_Y;

    private static double healthMul = 1.0;
    private static double damageMul = 1.0;
    private static int scaleFloor = 1;
    private static int themeFloor = 1;
    private static int mixSeed = 1;

    private enum Mix {
        FLOOR,
        WALL,
        CEIL
    }

    private PrototypeDungeonBuilder() {
    }

    public static void applyPartyScale(int players) {
        applyPartyScale(players, 1);
    }

    public static void applyPartyScale(int players, int floor) {
        int extra = Math.max(0, players - 1);
        scaleFloor = Math.max(1, floor);
        themeFloor = scaleFloor;
        healthMul = 1.0 + (0.40d * extra);
        damageMul = 1.0 + (0.12d * extra);
    }

    public static void resetPartyScale() {
        healthMul = 1.0;
        damageMul = 1.0;
        scaleFloor = 1;
        themeFloor = 1;
    }

    /*
     * Floor 1: rare/epic combat + pet/boosters — entry fight.
     * Floor 2: needs dungeon kit (levels/core), skills, pet, boosters. F1 gear has no chance.
     * Floor 3: extreme. Aetherion is a brick; the set is not a souvenir.
     */
    private static double hp(double floor1, double floor2, double floor3) {
        return floorStat(floor1, floor2, floor3) * healthMul;
    }

    private static double dmg(double floor1, double floor2, double floor3) {
        return floorStat(floor1, floor2, floor3) * damageMul;
    }

    private static double floorStat(double floor1, double floor2, double floor3) {
        if (themeFloor >= 3) {
            return floor3;
        }
        if (themeFloor == 2) {
            return floor2;
        }
        return floor1;
    }

    public static Location build(Plugin plugin, World world, DungeonLayout layout) {
        return build(plugin, world, layout, 1);
    }

    public static Location build(Plugin plugin, World world, DungeonLayout layout, int floor) {
        themeFloor = Math.max(1, Math.min(3, floor));
        scaleFloor = themeFloor;
        mixSeed = layout.seed();
        int y = FLOOR_Y;
        DungeonLayout.Room lobby = layout.lobby();
        DungeonLayout.Room boss = layout.boss();
        DungeonLayout.Room exit = layout.exit();
        int lobbyH = 8;
        int bossH = themeFloor >= 3 ? 22 : themeFloor >= 2 ? 18 : 10;

        room(world, lobby, y, lobbyH, palFloor(), palWall(), palCeil());
        fill(world, lobby.minX() + 1, y + 1, lobby.minZ() + 1, lobby.maxX() - 1, y + lobbyH - 1, lobby.maxZ() - 1, Material.AIR);
        weatherFloor1(world, lobby, y, lobbyH);
        themeLandmarks(world, lobby, y, -1);
        if (themeFloor >= 3) {
            world.getBlockAt(-4, y + 1, 4).setType(Material.END_ROD, false);
        } else {
            world.getBlockAt(-4, y + 1, 4).setType(Material.ENCHANTING_TABLE, false);
        }
        stair(world, -5, y + 1, 2, BlockFace.SOUTH);
        stair(world, -4, y + 1, 2, BlockFace.SOUTH);
        stair(world, 4, y + 1, 2, BlockFace.SOUTH);
        stair(world, 5, y + 1, 2, BlockFace.SOUTH);
        wallSign(world, -6, y + 2, 6, BlockFace.EAST, "§8Ready Check", "Talk to the", "Gate Warden", "then wait");
        lightArea(world, lobby, y);

        for (DungeonLayout.CombatRoom combat : layout.combatRooms()) {
            buildCombat(world, y, combat);
        }

        if (themeFloor >= 3) {
            buildEndArena(world, y, boss, bossH);
        } else {
            room(world, boss, y, bossH, palFloor(), palWall(), palCeil());
            fill(world, boss.minX() + 1, y + 1, boss.minZ() + 1, boss.maxX() - 1, y + bossH - 1, boss.maxZ() - 1, Material.AIR);
            weatherFloor1(world, boss, y, bossH);
            if (themeFloor == 2) {
                frostHearthPedestal(world, boss.minX() + 3, y, boss.minZ() + 3);
                frostHearthPedestal(world, boss.maxX() - 3, y, boss.minZ() + 3);
                frostHearthPedestal(world, boss.minX() + 3, y, boss.maxZ() - 3);
                frostHearthPedestal(world, boss.maxX() - 3, y, boss.maxZ() - 3);
                frostArenaDress(world, boss, y);
                snowMezzanine(world, y, boss);
            } else {
                brazier(world, boss.minX() + 2, y, boss.minZ() + 2);
                brazier(world, boss.maxX() - 2, y, boss.minZ() + 2);
                brazier(world, boss.minX() + 2, y, boss.maxZ() - 2);
                brazier(world, boss.maxX() - 2, y, boss.maxZ() - 2);
            }
            themeLandmarks(world, boss, y, -2);
        }
        lightArea(world, boss, y);

        int exitH = themeFloor >= 3 ? 7 : themeFloor == 1 ? 6 : 5;
        room(world, exit, y, exitH, palFloor(), palWall(), palCeil());
        fill(world, exit.minX() + 1, y + 1, exit.minZ() + 1, exit.maxX() - 1, y + exitH - 1, exit.maxZ() - 1, Material.AIR);
        lightArea(world, exit, y);

        for (DungeonLayout.Link link : layout.links()) {
            buildCorridor(world, y, link);
        }
        punchOpenings(world, y, layout);
        carveWalkways(world, y, layout);
        placeGate(world, layout.lobbyGateX(), layout.lobbyGateZ(), true, BlockFace.SOUTH, false);
        for (int i = 1; i < layout.combatCount(); i++) {
            DungeonLayout.CombatRoom room = layout.combat(i);
            placeGate(world, room.gateX(), room.gateZ(), room.gateAlongX(), room.gateFacing(), false);
        }
        placeGate(world, layout.bossGateX(), layout.bossGateZ(), layout.bossGateAlongX(), BlockFace.SOUTH, false);
        placePortal(world, layout);
        spawnReadyNpc(plugin, world, new Location(world, 4.5, y + 1, 5.5, 90, 0));
        relight(world, layout);
        paintBiome(world, layout);
        return new Location(world, 0.5, y + 1, 3.5, 0, 0);
    }

    public static void startEncounter(Plugin plugin, World world, DungeonLayout layout) {
        mixSeed = layout.seed();
        if (layout.templateFloor()) {
            // Lobby stays safe — open lobby→first combat, spawn there.
            DungeonLayout.Link intoFirst = layout.linkInto(0);
            if (intoFirst != null) {
                LerfingTestBuilder.openSeam(world, intoFirst);
            }
            spawnCombatRoom(plugin, world, layout.combat(0));
            return;
        }
        placeGate(world, layout.lobbyGateX(), layout.lobbyGateZ(), true, BlockFace.SOUTH, true);
        spawnCombatRoom(plugin, world, layout.combat(0));
    }

    public static void unlockRoom(Plugin plugin, World world, DungeonLayout layout, int index) {
        mixSeed = layout.seed();
        DungeonLayout.CombatRoom room = layout.combat(index);
        if (layout.templateFloor()) {
            DungeonLayout.Link link = layout.linkInto(index);
            if (link != null) {
                LerfingTestBuilder.openSeam(world, link);
            }
            spawnCombatRoom(plugin, world, room);
            return;
        }
        placeGate(world, room.gateX(), room.gateZ(), room.gateAlongX(), room.gateFacing(), true);
        spawnCombatRoom(plugin, world, room);
    }

    public static void unlockBoss(Plugin plugin, World world, DungeonLayout layout, org.bukkit.entity.Player initiator) {
        mixSeed = layout.seed();
        if (layout.templateFloor()) {
            DungeonLayout.Link link = layout.linkInto(DungeonLayout.BOSS);
            if (link != null) {
                LerfingTestBuilder.openSeam(world, link);
            }
            spawnBoss(plugin, world, layout, initiator);
            return;
        }
        placeGate(world, layout.bossGateX(), layout.bossGateZ(), layout.bossGateAlongX(), BlockFace.SOUTH, true);
        spawnBoss(plugin, world, layout, initiator);
    }

    public static boolean spawnBoss(Plugin plugin, World world, DungeonLayout layout, org.bukkit.entity.Player initiator) {
        Location at = new Location(world, layout.boss().centerX() + 0.5, FLOOR_Y + 1, layout.boss().centerZ() + 0.5);
        String template = de.aetherion.dungeons.bridge.BossEngineBridge.templateForFloor(scaleFloor);
        if (de.aetherion.dungeons.bridge.BossEngineBridge.spawn(at, initiator, template)) {
            return true;
        }
        if (scaleFloor >= 3) {
            spawnFallbackAetherion(plugin, world, at.getX(), at.getY(), at.getZ());
        } else if (scaleFloor >= 2) {
            spawnFallbackFrostbound(plugin, world, at.getX(), at.getY(), at.getZ());
        } else {
            spawnFallbackSentinel(plugin, world, at.getX(), at.getY(), at.getZ());
        }
        return false;
    }

    public static int countRoomMobs(Plugin plugin, World world, int roomIndex) {
        if (world == null || plugin == null) {
            return 0;
        }
        NamespacedKey key = roomKey(plugin);
        NamespacedKey mobKey = AetherKeys.DUNGEON_MOB;
        int count = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!isCombatRoomMob(plugin, entity, mobKey)) {
                continue;
            }
            Integer room = entity.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (room != null && room == roomIndex) {
                count++;
            }
        }
        return count;
    }

    public static boolean isCombatRoomMob(Plugin plugin, LivingEntity entity) {
        if (plugin == null) {
            return false;
        }
        return isCombatRoomMob(plugin, entity, AetherKeys.DUNGEON_MOB);
    }

    private static boolean isCombatRoomMob(Plugin plugin, LivingEntity entity, NamespacedKey mobKey) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        if (entity instanceof org.bukkit.entity.Player || entity instanceof org.bukkit.entity.ArmorStand) {
            return false;
        }
        if (isHelperEntity(entity)) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return false;
        }
        return !"dungeon_sentinel".equals(id)
                && !"dungeon_frostbound".equals(id)
                && !"dungeon_aetherion".equals(id);
    }

    public static boolean isHelperEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof org.bukkit.entity.Player || entity instanceof org.bukkit.entity.ArmorStand) {
            return true;
        }
        var data = entity.getPersistentDataContainer();
        return data.has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)
                || data.has(AetherKeys.PET_ENTITY, PersistentDataType.STRING)
                || data.has(AetherKeys.SET_MINION, PersistentDataType.BYTE)
                || entity.getScoreboardTags().contains("dungeon_keeper");
    }

    public static int countCombatMobs(Plugin plugin, World world) {
        if (world == null || plugin == null) {
            return 0;
        }
        NamespacedKey key = AetherKeys.DUNGEON_MOB;
        int count = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            String id = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (id != null && !"dungeon_sentinel".equals(id)) {
                count++;
            }
        }
        return count;
    }

    public static ChestLoot placeLootChest(World world, DungeonLayout.CombatRoom room) {
        return placeLootChest(world, room, 1);
    }

    public static ChestLoot placeLootChest(World world, DungeonLayout.CombatRoom room, int floor) {
        Plugin plugin = AetherionDungeons.getInstance();
        DungeonLootFx.placeCombat(plugin, world, room, floor);
        return new ChestLoot(false, false, false, false, false);
    }

    public static ChestLoot placeVictoryChest(World world, DungeonLayout layout) {
        return placeVictoryChest(world, layout, 1);
    }

    public static ChestLoot placeVictoryChest(World world, DungeonLayout layout, int floor) {
        Plugin plugin = AetherionDungeons.getInstance();
        DungeonLootFx.placeVictory(plugin, world, layout, floor);
        return new ChestLoot(false, false, false, false, false);
    }

    public record ChestLoot(boolean core, boolean armor, boolean relic, boolean myth, boolean schematic) {
        public ChestLoot(boolean core, boolean armor, boolean relic, boolean myth) {
            this(core, armor, relic, myth, false);
        }

        public ChestLoot(boolean core, boolean armor, boolean relic) {
            this(core, armor, relic, false, false);
        }

        public ChestLoot(boolean core, boolean armor) {
            this(core, armor, false, false, false);
        }
    }

    public static boolean isPortal(Block block) {
        return block != null && block.getType() == Material.NETHER_PORTAL;
    }

    public static boolean isReadyNpc(Plugin plugin, Entity entity) {
        if (entity == null || plugin == null) {
            return false;
        }
        String id = entity.getPersistentDataContainer().get(npcKey(plugin), PersistentDataType.STRING);
        return READY_NPC.equals(id) || entity.getScoreboardTags().contains(READY_NPC);
    }

    public static NamespacedKey npcKey(Plugin plugin) {
        return new NamespacedKey(plugin, "dungeon_ready_npc");
    }

    public static NamespacedKey roomKey(Plugin plugin) {
        return new NamespacedKey(plugin, "dungeon_room");
    }

    private static void buildCombat(World world, int y, DungeonLayout.CombatRoom combat) {
        DungeonLayout.Room bounds = combat.bounds();
        int height = 8;
        int pillarH = themeFloor == 1 ? 6 : 5;
        room(world, bounds, y, height, palFloor(), palWall(), palCeil());
        fill(world, bounds.minX() + 1, y + 1, bounds.minZ() + 1, bounds.maxX() - 1, y + height - 1, bounds.maxZ() - 1, Material.AIR);
        switch (combat.shape()) {
            case PILLARS -> {
                pillar(world, bounds.minX() + 3, y + 1, bounds.centerZ() - 3, pillarH, palPillar());
                pillar(world, bounds.maxX() - 3, y + 1, bounds.centerZ() - 3, pillarH, palPillar());
                pillar(world, bounds.minX() + 3, y + 1, bounds.centerZ() + 3, pillarH, palPillar());
                pillar(world, bounds.maxX() - 3, y + 1, bounds.centerZ() + 3, pillarH, palPillar());
            }
            case HALL -> {
                pillar(world, bounds.minX() + 3, y + 1, bounds.centerZ(), pillarH, palPillar());
                pillar(world, bounds.maxX() - 3, y + 1, bounds.centerZ(), pillarH, palPillar());
                lowWall(world, bounds.minX() + 2, y, bounds.minZ() + 4, bounds.minX() + 2, bounds.centerZ() - 2);
                lowWall(world, bounds.maxX() - 2, y, bounds.centerZ() + 2, bounds.maxX() - 2, bounds.maxZ() - 4);
            }
            case OFFSET -> {
                pillar(world, bounds.minX() + 3, y + 1, bounds.centerZ(), pillarH, palPillar());
                pillar(world, bounds.maxX() - 3, y + 1, bounds.centerZ(), pillarH, palPillar());
                dais(world, bounds.centerX() - 2, y, bounds.centerZ() - 2, bounds.centerX() + 2, bounds.centerZ() + 2);
            }
            case TIGHT -> {
                world.getBlockAt(bounds.minX() + 1, y + height - 2, bounds.minZ() + 1).setType(Material.COBWEB, false);
                world.getBlockAt(bounds.maxX() - 1, y + height - 2, bounds.maxZ() - 1).setType(Material.COBWEB, false);
                world.getBlockAt(bounds.minX() + 1, y + 2, bounds.centerZ()).setType(Material.CRACKED_DEEPSLATE_BRICKS, false);
                world.getBlockAt(bounds.maxX() - 1, y + 2, bounds.centerZ()).setType(Material.CRACKED_DEEPSLATE_BRICKS, false);
            }
            case CROSS -> {
                lowWall(world, bounds.centerX() - 4, y, bounds.centerZ() - 1, bounds.centerX() - 2, bounds.centerZ() - 1);
                lowWall(world, bounds.centerX() + 2, y, bounds.centerZ() + 1, bounds.centerX() + 4, bounds.centerZ() + 1);
                pillar(world, bounds.centerX() - 3, y + 1, bounds.centerZ() - 3, themeFloor == 1 ? 5 : 3, palPillar());
                pillar(world, bounds.centerX() + 3, y + 1, bounds.centerZ() + 3, themeFloor == 1 ? 5 : 3, palPillar());
            }
        }
        stainFloor(world, bounds, y, combat.index());
        weatherFloor1(world, bounds, y, height);
        if (themeFloor == 2) {
            snowMezzanine(world, y, bounds);
        }
        if (combat.westAlcove()) {
            alcove(world, y, bounds.minX() - 4, bounds.minX(), bounds.centerZ() - 2, bounds.centerZ() + 2);
            fill(world, bounds.minX(), y + 1, bounds.centerZ() - 1, bounds.minX(), y + 3, bounds.centerZ() + 1, Material.AIR);
        }
        if (combat.eastAlcove()) {
            alcove(world, y, bounds.maxX(), bounds.maxX() + 4, bounds.centerZ() - 2, bounds.centerZ() + 2);
            fill(world, bounds.maxX(), y + 1, bounds.centerZ() - 1, bounds.maxX(), y + 3, bounds.centerZ() + 1, Material.AIR);
        }
        ruinScatter(world, combat, y);
        themeLandmarks(world, bounds, y, combat.index());
        lightArea(world, bounds, y);
    }

    private static void spawnCombatRoom(Plugin plugin, World world, DungeonLayout.CombatRoom combat) {
        int y = FLOOR_Y + 1;
        DungeonLayout.Room bounds = combat.bounds();
        double cx = bounds.centerX() + 0.5;
        double cz = bounds.centerZ() + 0.5;
        int room = combat.index();
        spawnWalker(plugin, world, cx - 2.5, y, cz, room);
        spawnWalker(plugin, world, cx + 2.5, y, cz, room);
        spawnWalker(plugin, world, cx, y, bounds.minZ() + 3.5, room);
        for (int i = 3; i < combat.walkers(); i++) {
            spawnWalker(plugin, world, cx + (i % 2 == 0 ? -1.8 : 1.8), y, cz + (i % 2 == 0 ? 2 : -2), room);
        }
        spawnArcher(plugin, world, bounds.maxX() - 1.5, y, cz, room);
        if (combat.archers() > 1) {
            spawnArcher(plugin, world, bounds.minX() + 1.5, y, bounds.maxZ() - 2.5, room);
        }
        if (combat.brute()) {
            spawnBrute(plugin, world, cx, y, cz, room);
        }
        if (combat.westAlcove()) {
            spawnWalker(plugin, world, bounds.minX() - 2.5, y, cz, room);
        }
        if (combat.eastAlcove()) {
            spawnWalker(plugin, world, bounds.maxX() + 2.5, y, cz, room);
        }
        if (combat.miniBoss()) {
            spawnMiniBoss(plugin, world, cx, y, cz + 3.2, room);
        }
        if (scaleFloor == 1) {
            spawnWalker(plugin, world, cx, y, bounds.maxZ() - 3.5, room);
        }
        if (scaleFloor >= 2) {
            spawnWalker(plugin, world, cx, y, bounds.maxZ() - 3.5, room);
            spawnArcher(plugin, world, bounds.minX() + 2.5, y, bounds.minZ() + 2.5, room);
        }
        if (scaleFloor == 2) {
            spawnArcher(plugin, world, bounds.minX() + 2.5, y + 4, cz, room);
        }
        if (scaleFloor >= 3) {
            spawnWalker(plugin, world, cx - 3.2, y, cz - 3.2, room);
            spawnArcher(plugin, world, bounds.maxX() - 2.5, y, bounds.maxZ() - 2.5, room);
        }
    }

    private static void lightArea(World world, DungeonLayout.Room room, int y) {
        int step = themeFloor == 1 ? 4 : 2;
        for (int x = room.minX() + 1; x <= room.maxX() - 1; x += step) {
            for (int z = room.minZ() + 1; z <= room.maxZ() - 1; z += step) {
                light(world, x, y + 2, z);
                if ((x + z) % 4 == 0) {
                    light(world, x, y + 4, z);
                }
            }
        }
        lantern(world, room.minX() + 2, y + 4, room.minZ() + 2);
        lantern(world, room.maxX() - 2, y + 4, room.minZ() + 2);
        lantern(world, room.minX() + 2, y + 4, room.maxZ() - 2);
        lantern(world, room.maxX() - 2, y + 4, room.maxZ() - 2);
    }

    private static void relight(World world, DungeonLayout layout) {
        int y = FLOOR_Y;
        lightArea(world, layout.lobby(), y);
        for (DungeonLayout.CombatRoom combat : layout.combatRooms()) {
            lightArea(world, combat.bounds(), y);
            if (combat.westAlcove()) {
                light(world, combat.bounds().minX() - 2, y + 3, combat.bounds().centerZ());
            }
            if (combat.eastAlcove()) {
                light(world, combat.bounds().maxX() + 2, y + 3, combat.bounds().centerZ());
            }
        }
        lightArea(world, layout.boss(), y);
        lightArea(world, layout.exit(), y);
        for (DungeonLayout.Link link : layout.links()) {
            lightArea(world, link.corridorBounds(), y);
        }
    }

    private static void light(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (!block.getType().isAir() && block.getType() != Material.LIGHT) {
            return;
        }
        org.bukkit.block.data.BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof Levelled levelled) {
            levelled.setLevel(15);
            block.setBlockData(levelled, true);
            return;
        }
        block.setType(Material.LIGHT, true);
    }

    private static void room(
            World world,
            DungeonLayout.Room room,
            int y,
            int height,
            Material floor,
            Material wall,
            Material ceiling
    ) {
        fillMix(world, room.minX(), y, room.minZ(), room.maxX(), y, room.maxZ(), Mix.FLOOR);
        wallsMix(world, room.minX(), y, room.minZ(), room.maxX(), y + height, room.maxZ());
        fillMix(world, room.minX(), y + height, room.minZ(), room.maxX(), y + height, room.maxZ(), Mix.CEIL);
        ribCeiling(world, room, y + height);
    }

    private static void buildCorridor(World world, int y, DungeonLayout.Link link) {
        if (link.alongX()) {
            corridorAlongX(world, y, link.fromWallZ(), Math.min(link.fromWallX(), link.toWallX()) + 1, Math.max(link.fromWallX(), link.toWallX()) - 1);
            return;
        }
        corridorAlongZ(world, y, link.fromWallX(), Math.min(link.fromWallZ(), link.toWallZ()) + 1, Math.max(link.fromWallZ(), link.toWallZ()) - 1);
    }

    private static void corridorAlongZ(World world, int y, int cx, int z1, int z2) {
        if (z2 < z1) {
            int swap = z1;
            z1 = z2;
            z2 = swap;
        }
        if (z2 < z1) {
            return;
        }
        int hall = 7;
        fillMix(world, cx - 3, y, z1, cx + 3, y, z2, Mix.FLOOR);
        fillMix(world, cx - 3, y, z1, cx - 3, y + hall, z2, Mix.WALL);
        fillMix(world, cx + 3, y, z1, cx + 3, y + hall, z2, Mix.WALL);
        fill(world, cx - 2, y + 1, z1, cx + 2, y + hall - 1, z2, Material.AIR);
        fillMix(world, cx - 3, y + hall, z1, cx + 3, y + hall, z2, Mix.CEIL);
        for (int z = z1; z <= z2; z += 2) {
            light(world, cx, y + 3, z);
        }
        if (themeFloor != 3) {
            for (int z = z1 + 2; z <= z2 - 2; z += 5) {
                hangChainLantern(world, cx - 2, y + hall, z, 2);
                hangChainLantern(world, cx + 2, y + hall, z + 2, 2);
            }
        }
    }

    private static void corridorAlongX(World world, int y, int cz, int x1, int x2) {
        if (x2 < x1) {
            int swap = x1;
            x1 = x2;
            x2 = swap;
        }
        if (x2 < x1) {
            return;
        }
        int hall = 7;
        fillMix(world, x1, y, cz - 3, x2, y, cz + 3, Mix.FLOOR);
        fillMix(world, x1, y, cz - 3, x2, y + hall, cz - 3, Mix.WALL);
        fillMix(world, x1, y, cz + 3, x2, y + hall, cz + 3, Mix.WALL);
        fill(world, x1, y + 1, cz - 2, x2, y + hall - 1, cz + 2, Material.AIR);
        fillMix(world, x1, y + hall, cz - 3, x2, y + hall, cz + 3, Mix.CEIL);
        for (int x = x1; x <= x2; x += 2) {
            light(world, x, y + 3, cz);
        }
        if (themeFloor != 3) {
            for (int x = x1 + 2; x <= x2 - 2; x += 5) {
                hangChainLantern(world, x, y + hall, cz - 2, 2);
                hangChainLantern(world, x + 2, y + hall, cz + 2, 2);
            }
        }
    }

    private static void punchOpenings(World world, int y, DungeonLayout layout) {
        for (DungeonLayout.Link link : layout.links()) {
            punchWall(world, y, link.fromWallX(), link.fromWallZ(), link.alongX());
            punchWall(world, y, link.toWallX(), link.toWallZ(), link.alongX());
        }
    }

    private static void carveWalkways(World world, int y, DungeonLayout layout) {
        for (DungeonLayout.Link link : layout.links()) {
            if (link.alongX()) {
                int z = link.fromWallZ();
                int x1 = Math.min(link.fromWallX(), link.toWallX());
                int x2 = Math.max(link.fromWallX(), link.toWallX());
                fill(world, x1, y + 1, z - 2, x2, y + 5, z + 2, Material.AIR);
            } else {
                int x = link.fromWallX();
                int z1 = Math.min(link.fromWallZ(), link.toWallZ());
                int z2 = Math.max(link.fromWallZ(), link.toWallZ());
                fill(world, x - 2, y + 1, z1, x + 2, y + 5, z2, Material.AIR);
            }
        }
    }

    private static void punchWall(World world, int y, int x, int z, boolean alongX) {
        int top = 5;
        int half = 2;
        if (alongX) {
            fill(world, x, y + 1, z - half, x, y + top, z + half, Material.AIR);
            fillMix(world, x, y, z - half, x, y, z + half, Mix.FLOOR);
            return;
        }
        fill(world, x - half, y + 1, z, x + half, y + top, z, Material.AIR);
        fillMix(world, x - half, y, z, x + half, y, z, Mix.FLOOR);
    }

    private static void placeGate(World world, int gx, int gz, boolean alongX, BlockFace facing, boolean open) {
        if (open) {
            DungeonGateFx.release(world, gx, gz, alongX);
            punchWall(world, FLOOR_Y, gx, gz, !alongX);
            return;
        }
        placeDungeonArch(world, gx, gz, alongX);
        DungeonGateFx.seal(world, gx, gz, alongX);
    }

    private static void placeDungeonArch(World world, int gx, int gz, boolean alongX) {
        int y = FLOOR_Y;
        if (alongX) {
            for (int x = gx - 2; x <= gx + 2; x++) {
                setMix(world, x, y, gz, Mix.FLOOR);
            }
            for (int x : new int[]{gx - 2, gx + 2}) {
                for (int dy = 1; dy <= 4; dy++) {
                    world.getBlockAt(x, y + dy, gz).setType(dy == 1 || dy == 4 ? palAccent() : palPillar(), false);
                }
            }
            for (int x = gx - 1; x <= gx + 1; x++) {
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(x, y + dy, gz).setType(Material.AIR, false);
                }
            }
            upsideDownStair(world, gx - 1, y + 4, gz, BlockFace.EAST);
            topSlab(world, gx, y + 4, gz);
            upsideDownStair(world, gx + 1, y + 4, gz, BlockFace.WEST);
            topSlab(world, gx - 2, y + 5, gz);
            world.getBlockAt(gx, y + 5, gz).setType(palPillar(), false);
            topSlab(world, gx + 2, y + 5, gz);
            lantern(world, gx - 2, y + 5, gz);
            lantern(world, gx + 2, y + 5, gz);
            return;
        }
        for (int z = gz - 2; z <= gz + 2; z++) {
            setMix(world, gx, y, z, Mix.FLOOR);
        }
        for (int z : new int[]{gz - 2, gz + 2}) {
            for (int dy = 1; dy <= 4; dy++) {
                world.getBlockAt(gx, y + dy, z).setType(dy == 1 || dy == 4 ? palAccent() : palPillar(), false);
            }
        }
        for (int z = gz - 1; z <= gz + 1; z++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(gx, y + dy, z).setType(Material.AIR, false);
            }
        }
        upsideDownStair(world, gx, y + 4, gz - 1, BlockFace.SOUTH);
        topSlab(world, gx, y + 4, gz);
        upsideDownStair(world, gx, y + 4, gz + 1, BlockFace.NORTH);
        topSlab(world, gx, y + 5, gz - 2);
        world.getBlockAt(gx, y + 5, gz).setType(palPillar(), false);
        topSlab(world, gx, y + 5, gz + 2);
        lantern(world, gx, y + 5, gz - 2);
        lantern(world, gx, y + 5, gz + 2);
    }

    private static void alcove(World world, int y, int minX, int maxX, int minZ, int maxZ) {
        fillMix(world, minX, y, minZ, maxX, y, maxZ, Mix.FLOOR);
        wallsMix(world, minX, y, minZ, maxX, y + 5, maxZ);
        fill(world, minX + 1, y + 1, minZ + 1, maxX - 1, y + 4, maxZ - 1, Material.AIR);
        fillMix(world, minX, y + 5, minZ, maxX, y + 5, maxZ, Mix.CEIL);
        lantern(world, (minX + maxX) / 2, y + 4, (minZ + maxZ) / 2);
    }

    static void placePortal(World world, DungeonLayout layout) {
        int y = FLOOR_Y;
        int x = layout.exit().centerX();
        int z = layout.exit().maxZ() - 2;
        for (int dx = -1; dx <= 2; dx++) {
            world.getBlockAt(x + dx, y + 1, z).setType(Material.OBSIDIAN, false);
            world.getBlockAt(x + dx, y + 5, z).setType(Material.OBSIDIAN, false);
        }
        for (int dy = 2; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN, false);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN, false);
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 2; dy <= 4; dy++) {
                Block portal = world.getBlockAt(x + dx, y + dy, z);
                portal.setType(Material.NETHER_PORTAL, false);
                if (portal.getBlockData() instanceof Orientable orientable) {
                    orientable.setAxis(Axis.X);
                    portal.setBlockData(orientable, false);
                }
            }
        }
        wallSign(world, x + 3, y + 2, z - 1, BlockFace.WEST, "§5Exit", "Walk in to", "return home", "");
    }

    static void spawnReadyNpc(Plugin plugin, World world, Location location) {
        Villager villager = world.spawn(location, Villager.class);
        villager.setCustomName("§eGate Warden");
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(true);
        villager.setSilent(true);
        villager.setGravity(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.setAdult();
        villager.setAgeLock(true);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setRecipes(java.util.List.of());
        villager.addScoreboardTag(READY_NPC);
        villager.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        villager.getPersistentDataContainer().set(npcKey(plugin), PersistentDataType.STRING, READY_NPC);

        Interaction hitbox = world.spawn(location, Interaction.class);
        hitbox.setInteractionWidth(1.1f);
        hitbox.setInteractionHeight(2.1f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(true);
        hitbox.addScoreboardTag(READY_NPC);
        hitbox.getPersistentDataContainer().set(npcKey(plugin), PersistentDataType.STRING, READY_NPC);
    }

    private static void spawnWalker(Plugin plugin, World world, double x, double y, double z, int room) {
        if (themeFloor >= 3) {
            Enderman enderman = (Enderman) world.spawnEntity(new Location(world, x, y, z), EntityType.ENDERMAN);
            tag(plugin, enderman, "dungeon_zombie", room);
            setDungeonLabel(plugin, enderman, "§5Void Walker");
            setHealth(plugin, enderman, hp(1900, 1900, 8200));
            setDamage(enderman, dmg(125, 125, 330));
            setSpeed(enderman, 0.30);
            return;
        }
        Zombie zombie = (Zombie) world.spawnEntity(new Location(world, x, y, z), EntityType.ZOMBIE);
        tag(plugin, zombie, "dungeon_zombie", room);
        setDungeonLabel(plugin, zombie, themeFloor == 2 ? "§bPacked Walker" : "§8Dungeon Walker");
        zombie.setAdult();
        arm(zombie, Material.IRON_SWORD, themeFloor == 2 ? Material.LEATHER_CHESTPLATE : Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS);
        setHealth(plugin, zombie, hp(750, 12000, 8200));
        setDamage(zombie, dmg(46, 420, 330));
        setSpeed(zombie, 0.28);
    }

    private static void spawnMiniBoss(Plugin plugin, World world, double x, double y, double z, int room) {
        org.bukkit.entity.WitherSkeleton warden = (org.bukkit.entity.WitherSkeleton) world.spawnEntity(
                new Location(world, x, y, z),
                EntityType.WITHER_SKELETON
        );
        tag(plugin, warden, "dungeon_warden", room);
        setDungeonLabel(plugin, warden, themeFloor >= 3 ? "§5Void Warden" : themeFloor == 2 ? "§bFrost Warden" : "§6Chamber Warden");
        warden.setGlowing(true);
        arm(warden, Material.NETHERITE_AXE, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET);
        setHealth(plugin, warden, hp(3400, 42000, 21000));
        setDamage(warden, dmg(78, 680, 460));
        setSpeed(warden, 0.26);
        AttributeInstance scale = warden.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(1.45);
        }
    }

    private static void spawnBrute(Plugin plugin, World world, double x, double y, double z, int room) {
        Husk husk = (Husk) world.spawnEntity(new Location(world, x, y, z), EntityType.HUSK);
        tag(plugin, husk, "dungeon_brute", room);
        setDungeonLabel(plugin, husk, "§8Dungeon Brute");
        husk.setAdult();
        arm(husk, Material.IRON_AXE, Material.IRON_CHESTPLATE, Material.IRON_HELMET);
        setHealth(plugin, husk, hp(1200, 24000, 13500));
        setDamage(husk, dmg(64, 580, 400));
        setSpeed(husk, 0.24);
        AttributeInstance scale = husk.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(1.25);
        }
    }

    private static void spawnArcher(Plugin plugin, World world, double x, double y, double z, int room) {
        EntityType type = themeFloor == 2 ? EntityType.STRAY : EntityType.SKELETON;
        Skeleton skeleton = (Skeleton) world.spawnEntity(new Location(world, x, y, z), type);
        tag(plugin, skeleton, "dungeon_skeleton", room);
        setDungeonLabel(plugin, skeleton, themeFloor >= 3 ? "§5Void Archer" : themeFloor == 2 ? "§bPacked Archer" : "§8Dungeon Archer");
        arm(skeleton, Material.BOW, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_HELMET);
        setHealth(plugin, skeleton, hp(480, 9000, 5800));
        setDamage(skeleton, dmg(40, 340, 265));
        setSpeed(skeleton, 0.26);
    }

    private static void spawnFallbackFrostbound(Plugin plugin, World world, double x, double y, double z) {
        Snowman golem = (Snowman) world.spawnEntity(new Location(world, x, y, z), EntityType.SNOW_GOLEM);
        tag(plugin, golem, "dungeon_frostbound");
        setDungeonLabel(plugin, golem, "§b§lThe Frostbound");
        golem.setDerp(false);
        golem.setGlowing(true);
        setHealth(plugin, golem, hp(2300, 90000, 28000));
        setDamage(golem, dmg(72, 200, 280));
        AttributeInstance scale = golem.getAttribute(Attribute.GENERIC_SCALE);
        if (scale == null) {
            try {
                scale = golem.getAttribute(Attribute.valueOf("SCALE"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (scale != null) {
            scale.setBaseValue(2.85);
        }
    }

    private static void spawnFallbackAetherion(Plugin plugin, World world, double x, double y, double z) {
        Enderman enderman = (Enderman) world.spawnEntity(new Location(world, x, y, z), EntityType.ENDERMAN);
        tag(plugin, enderman, "dungeon_aetherion");
        setDungeonLabel(plugin, enderman, "§5§l✦✦✦ Aetherion");
        enderman.setGlowing(true);
        setHealth(plugin, enderman, hp(24000, 24000, 120000));
        setDamage(enderman, dmg(140, 140, 260));
        AttributeInstance scale = enderman.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(1.8);
        }
    }

    private static void spawnFallbackSentinel(Plugin plugin, World world, double x, double y, double z) {
        Husk husk = (Husk) world.spawnEntity(new Location(world, x, y, z), EntityType.HUSK);
        tag(plugin, husk, "dungeon_sentinel");
        setDungeonLabel(plugin, husk, "§5Prototype Sentinel");
        husk.setAdult();
        husk.setGlowing(true);
        arm(husk, Material.IRON_SWORD, Material.IRON_CHESTPLATE, Material.IRON_HELMET);
        setHealth(plugin, husk, hp(5800, 5800, 5800));
        setDamage(husk, dmg(72, 72, 72));
        AttributeInstance scale = husk.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) {
            scale.setBaseValue(1.7);
        }
    }

    private static void arm(LivingEntity entity, Material weapon, Material chest, Material extra) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItemInMainHand(new ItemStack(weapon));
        equipment.setChestplate(new ItemStack(chest));
        if (extra != null) {
            if (extra.name().contains("HELMET")) {
                equipment.setHelmet(new ItemStack(extra));
            } else {
                equipment.setLeggings(new ItemStack(extra));
            }
        }
        equipment.setItemInMainHandDropChance(0f);
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
    }

    private static void tag(Plugin plugin, LivingEntity entity, String id, int room) {
        entity.getPersistentDataContainer().set(
                AetherKeys.DUNGEON_MOB,
                PersistentDataType.STRING,
                id
        );
        entity.getPersistentDataContainer().set(roomKey(plugin), PersistentDataType.INTEGER, room);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
    }

    private static void tag(Plugin plugin, LivingEntity entity, String id) {
        tag(plugin, entity, id, -1);
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
            try {
                attribute.setBaseValue(vanilla);
            } catch (IllegalArgumentException ignored) {
                attribute.setBaseValue(Math.min(vanilla, 1024.0));
            }
        }
        entity.setHealth(vanilla);
        refreshDungeonNameplate(plugin, entity);
    }

    private static void setDungeonLabel(Plugin plugin, LivingEntity entity, String label) {
        if (plugin == null || entity == null || label == null || label.isBlank()) {
            return;
        }
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "dungeon_label"),
                PersistentDataType.STRING,
                label
        );
        entity.setCustomName(label);
        entity.setCustomNameVisible(true);
    }

    /**
     * Skyblock-style floating HP on the nametag (not a screen BossBar — those stay for bosses / progress).
     */
    public static void refreshDungeonNameplate(Plugin plugin, LivingEntity entity) {
        if (plugin == null || entity == null || !entity.isValid()) {
            return;
        }
        String label = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_label"),
                PersistentDataType.STRING
        );
        if (label == null || label.isBlank()) {
            return;
        }
        Double hp = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_hp"),
                PersistentDataType.DOUBLE
        );
        Double max = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "dungeon_max_hp"),
                PersistentDataType.DOUBLE
        );
        if (hp == null || max == null || max <= 0.0) {
            entity.setCustomName(label);
            entity.setCustomNameVisible(true);
            return;
        }
        int shownHp = (int) Math.max(0, Math.ceil(hp));
        int shownMax = (int) Math.max(1, Math.ceil(max));
        entity.setCustomName(label + " §c" + shownHp + "§7/§c" + shownMax);
        entity.setCustomNameVisible(true);
    }

    private static void setDamage(LivingEntity entity, double damage) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.setBaseValue(damage);
        }
    }

    private static void setSpeed(LivingEntity entity, double speed) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(speed);
        }
    }

    private static void fillMix(World world, int x1, int y1, int z1, int x2, int y2, int z2, Mix mix) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(palMix(mix, x, y, z), false);
                }
            }
        }
    }

    private static void wallsMix(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        fillMix(world, x1, y1, z1, x2, y2, z1, Mix.WALL);
        fillMix(world, x1, y1, z2, x2, y2, z2, Mix.WALL);
        fillMix(world, x1, y1, z1, x1, y2, z2, Mix.WALL);
        fillMix(world, x2, y1, z1, x2, y2, z2, Mix.WALL);
    }

    private static void setMix(World world, int x, int y, int z, Mix mix) {
        world.getBlockAt(x, y, z).setType(palMix(mix, x, y, z), false);
    }

    private static int mixHash(int x, int y, int z) {
        int h = mixSeed;
        h ^= x * 374761393;
        h ^= y * 668265263;
        h ^= z * 1274126177;
        return h ^ (h >>> 13);
    }

    private static Material palMix(Mix mix, int x, int y, int z) {
        int r = Math.floorMod(mixHash(x, y, z), 100);
        if (themeFloor == 2) {
            return palMixFrost(mix, y, r);
        }
        if (themeFloor >= 3) {
            return palMixEnd(mix, y, r);
        }
        if (mix == Mix.FLOOR) {
            if (r < 38) {
                return Material.DEEPSLATE_BRICKS;
            }
            if (r < 56) {
                return Material.POLISHED_DEEPSLATE;
            }
            if (r < 68) {
                return Material.DEEPSLATE_TILES;
            }
            if (r < 78) {
                return Material.CRACKED_DEEPSLATE_BRICKS;
            }
            if (r < 86) {
                return Material.COBBLED_DEEPSLATE;
            }
            if (r < 91) {
                return Material.DEEPSLATE;
            }
            if (r < 94) {
                return Material.TUFF;
            }
            if (r < 97) {
                return Material.TUFF_BRICKS;
            }
            return Material.MOSSY_COBBLESTONE;
        }
        if (mix == Mix.CEIL) {
            if (r < 45) {
                return Material.DEEPSLATE_TILES;
            }
            if (r < 65) {
                return Material.POLISHED_DEEPSLATE;
            }
            if (r < 77) {
                return Material.DEEPSLATE_BRICKS;
            }
            if (r < 87) {
                return Material.COBBLED_DEEPSLATE;
            }
            if (r < 93) {
                return Material.TUFF;
            }
            if (r < 97) {
                return Material.CRACKED_DEEPSLATE_TILES;
            }
            return Material.POLISHED_TUFF;
        }
        if (y <= FLOOR_Y + 2) {
            if (r < 10) {
                return Material.MOSSY_COBBLESTONE;
            }
            if (r < 18) {
                return Material.MOSSY_STONE_BRICKS;
            }
            if (r < 28) {
                return Material.CRACKED_DEEPSLATE_BRICKS;
            }
            if (r < 36) {
                return Material.COBBLED_DEEPSLATE;
            }
        }
        if (r < 40) {
            return Material.DEEPSLATE_BRICKS;
        }
        if (r < 55) {
            return Material.POLISHED_DEEPSLATE;
        }
        if (r < 65) {
            return Material.CRACKED_DEEPSLATE_BRICKS;
        }
        if (r < 73) {
            return Material.COBBLED_DEEPSLATE;
        }
        if (r < 80) {
            return Material.DEEPSLATE_TILES;
        }
        if (r < 85) {
            return Material.TUFF_BRICKS;
        }
        if (r < 89) {
            return Material.CHISELED_DEEPSLATE;
        }
        if (r < 93) {
            return Material.TUFF;
        }
        if (r < 96) {
            return Material.CHISELED_TUFF;
        }
        if (r < 98) {
            return Material.CRACKED_DEEPSLATE_TILES;
        }
        return Material.POLISHED_TUFF;
    }

    private static Material palMixFrost(Mix mix, int y, int r) {
        if (mix == Mix.FLOOR) {
            if (r < 42) {
                return Material.SNOW_BLOCK;
            }
            if (r < 64) {
                return Material.PACKED_ICE;
            }
            if (r < 78) {
                return Material.PACKED_ICE;
            }
            if (r < 88) {
                return Material.CALCITE;
            }
            if (r < 95) {
                return Material.SPRUCE_PLANKS;
            }
            return Material.BLUE_ICE;
        }
        if (mix == Mix.CEIL) {
            if (r < 50) {
                return Material.PACKED_ICE;
            }
            if (r < 72) {
                return Material.SPRUCE_PLANKS;
            }
            if (r < 88) {
                return Material.PACKED_ICE;
            }
            return Material.SNOW_BLOCK;
        }
        if (y <= FLOOR_Y + 2 && r < 18) {
            return Material.SPRUCE_LOG;
        }
        if (r < 38) {
            return Material.PACKED_ICE;
        }
        if (r < 54) {
            return Material.SPRUCE_WOOD;
        }
        if (r < 66) {
            return Material.STRIPPED_SPRUCE_LOG;
        }
        if (r < 76) {
            return Material.PACKED_ICE;
        }
        if (r < 86) {
            return Material.CALCITE;
        }
        if (r < 93) {
            return Material.BLUE_ICE;
        }
        return Material.WHITE_TERRACOTTA;
    }

    private static Material palMixEnd(Mix mix, int y, int r) {
        if (mix == Mix.FLOOR) {
            if (r < 40) {
                return Material.END_STONE_BRICKS;
            }
            if (r < 62) {
                return Material.PURPUR_BLOCK;
            }
            if (r < 78) {
                return Material.END_STONE;
            }
            if (r < 90) {
                return Material.PURPUR_PILLAR;
            }
            if (r < 97) {
                return Material.OBSIDIAN;
            }
            return Material.CRYING_OBSIDIAN;
        }
        if (mix == Mix.CEIL) {
            if (r < 48) {
                return Material.END_STONE;
            }
            if (r < 72) {
                return Material.PURPUR_BLOCK;
            }
            if (r < 90) {
                return Material.END_STONE_BRICKS;
            }
            return Material.OBSIDIAN;
        }
        if (y <= FLOOR_Y + 2 && r < 12) {
            return Material.OBSIDIAN;
        }
        if (r < 40) {
            return Material.PURPUR_BLOCK;
        }
        if (r < 58) {
            return Material.PURPUR_PILLAR;
        }
        if (r < 74) {
            return Material.END_STONE_BRICKS;
        }
        if (r < 86) {
            return Material.END_STONE;
        }
        if (r < 94) {
            return Material.OBSIDIAN;
        }
        return Material.CRYING_OBSIDIAN;
    }

    private static void ribCeiling(World world, DungeonLayout.Room room, int ceilY) {
        for (int z = room.minZ() + 3; z <= room.maxZ() - 3; z += 4) {
            for (int x = room.minX() + 1; x <= room.maxX() - 1; x++) {
                world.getBlockAt(x, ceilY, z).setType(palPillar(), false);
            }
        }
    }

    private static void ruinScatter(World world, DungeonLayout.CombatRoom combat, int y) {
        DungeonLayout.Room bounds = combat.bounds();
        java.util.Random random = new java.util.Random(mixSeed * 131L + combat.index() * 17L);
        if (random.nextInt(10) < 2) {
            return;
        }
        int clusters = (themeFloor == 2 ? 4 : 2) + random.nextInt(3);
        for (int i = 0; i < clusters; i++) {
            int width = Math.max(4, bounds.maxX() - bounds.minX() - 6);
            int depth = Math.max(4, bounds.maxZ() - bounds.minZ() - 6);
            int x = bounds.minX() + 3 + random.nextInt(width);
            int z = bounds.minZ() + 3 + random.nextInt(depth);
            if (Math.abs(x - combat.lootX()) < 3 && Math.abs(z - combat.lootZ()) < 3) {
                continue;
            }
            if (Math.abs(x - bounds.centerX()) < 2 && Math.abs(z - bounds.centerZ()) < 2) {
                continue;
            }
            if (Math.abs(x - combat.gateX()) < 3 && Math.abs(z - combat.gateZ()) < 3) {
                continue;
            }
            switch (random.nextInt(4)) {
                case 0 -> {
                    world.getBlockAt(x, y + 1, z).setType(palPillar(), false);
                    world.getBlockAt(x, y + 2, z).setType(palAccent(), false);
                    stair(world, x + 1, y + 1, z, BlockFace.WEST);
                    world.getBlockAt(x + 2, y + 1, z).setType(palSlab(), false);
                }
                case 1 -> {
                    world.getBlockAt(x, y + 1, z).setType(palWall(), false);
                    world.getBlockAt(x + 1, y + 1, z).setType(palFloor(), false);
                    world.getBlockAt(x, y + 1, z + 1).setType(palSlab(), false);
                    world.getBlockAt(x, y + 2, z).setType(Material.COBWEB, false);
                }
                case 2 -> {
                    world.getBlockAt(x, y + 1, z).setType(palPillar(), false);
                    world.getBlockAt(x, y + 2, z).setType(palPillar(), false);
                    world.getBlockAt(x + 1, y + 1, z).setType(palAccent(), false);
                }
                default -> {
                    world.getBlockAt(x, y + 1, z).setType(palAccent(), false);
                    stair(world, x, y + 1, z + 1, BlockFace.NORTH);
                    world.getBlockAt(x + 1, y + 1, z).setType(palCeil(), false);
                }
            }
        }
    }

    private static void themeLandmarks(World world, DungeonLayout.Room bounds, int y, int seed) {
        if (themeFloor < 2 || bounds == null) {
            return;
        }
        java.util.Random random = new java.util.Random(mixSeed * 44L + seed * 19L);
        int backX = bounds.maxX() - 3;
        int backZ = bounds.maxZ() - 3;
        int sideX = bounds.minX() + 3;
        int sideZ = bounds.minZ() + 5;
        if (themeFloor == 2) {
            frostShrine(world, backX, y, backZ);
            frostShrine(world, bounds.minX() + 3, y, bounds.maxZ() - 3);
            if (bounds.maxX() - bounds.minX() > 16) {
                frostSpike(world, sideX, y, sideZ);
                frostSpike(world, bounds.maxX() - 4, y, bounds.centerZ());
            }
            if (bounds.maxZ() - bounds.minZ() > 16) {
                frostBench(world, bounds.minX() + 4, y, bounds.maxZ() - 4);
                frostSpike(world, bounds.centerX() - 5, y, bounds.minZ() + 4);
            }
            return;
        }
        endShrine(world, backX, y, backZ);
        if (bounds.maxX() - bounds.minX() > 16) {
            chorusNook(world, sideX, y, Math.min(bounds.maxZ() - 4, bounds.centerZ() + 4));
        }
        if (random.nextBoolean()) {
            endLanternPost(world, bounds.maxX() - 4, y, bounds.minZ() + 5);
        }
    }

    private static void frostHearthPedestal(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.PACKED_ICE, false);
        world.getBlockAt(x, y + 1, z).setType(Material.BLUE_ICE, false);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.SPRUCE_SLAB, false);
        world.getBlockAt(x - 1, y + 1, z).setType(Material.SPRUCE_SLAB, false);
        world.getBlockAt(x, y + 1, z + 1).setType(Material.SPRUCE_SLAB, false);
        world.getBlockAt(x, y + 1, z - 1).setType(Material.SPRUCE_SLAB, false);
        lantern(world, x, y + 3, z);
    }

    private static void frostArenaDress(World world, DungeonLayout.Room boss, int y) {
        frostSpike(world, boss.centerX() - 8, y, boss.minZ() + 6);
        frostSpike(world, boss.centerX() + 8, y, boss.minZ() + 6);
        frostSpike(world, boss.centerX() - 8, y, boss.maxZ() - 6);
        frostSpike(world, boss.centerX() + 8, y, boss.maxZ() - 6);
        frostShrine(world, boss.minX() + 6, y, boss.centerZ() - 2);
        frostShrine(world, boss.maxX() - 7, y, boss.centerZ() - 2);
        frostBench(world, boss.centerX() - 10, y, boss.maxZ() - 8);
        world.getBlockAt(boss.centerX() - 4, y + 1, boss.centerZ() - 10).setType(Material.PACKED_ICE, false);
        world.getBlockAt(boss.centerX() + 4, y + 1, boss.centerZ() - 10).setType(Material.BLUE_ICE, false);
        world.getBlockAt(boss.centerX() - 4, y + 1, boss.centerZ() + 10).setType(Material.SPRUCE_LOG, false);
        world.getBlockAt(boss.centerX() + 4, y + 1, boss.centerZ() + 10).setType(Material.PACKED_ICE, false);
    }

    private static void frostShrine(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.PACKED_ICE, false);
        world.getBlockAt(x + 1, y, z).setType(Material.PACKED_ICE, false);
        world.getBlockAt(x, y, z + 1).setType(Material.BLUE_ICE, false);
        world.getBlockAt(x, y + 1, z).setType(Material.SPRUCE_LOG, false);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.SPRUCE_SLAB, false);
        stair(world, x, y + 1, z + 1, BlockFace.NORTH);
        lantern(world, x, y + 2, z);
    }

    private static void frostSpike(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z).setType(Material.PACKED_ICE, false);
        world.getBlockAt(x, y + 2, z).setType(Material.BLUE_ICE, false);
        world.getBlockAt(x, y + 3, z).setType(Material.PACKED_ICE, false);
    }

    private static void frostBench(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z).setType(Material.SPRUCE_STAIRS, false);
        setStairFacing(world, x, y + 1, z, BlockFace.EAST);
        world.getBlockAt(x, y + 1, z + 1).setType(Material.SPRUCE_STAIRS, false);
        setStairFacing(world, x, y + 1, z + 1, BlockFace.EAST);
        world.getBlockAt(x, y + 1, z + 2).setType(Material.SPRUCE_SLAB, false);
        lantern(world, x, y + 3, z + 1);
    }

    private static void endShrine(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.OBSIDIAN, false);
        world.getBlockAt(x + 1, y, z).setType(Material.PURPUR_BLOCK, false);
        world.getBlockAt(x, y, z + 1).setType(Material.CRYING_OBSIDIAN, false);
        world.getBlockAt(x, y + 1, z).setType(Material.END_ROD, false);
        stair(world, x + 1, y + 1, z, BlockFace.WEST);
        world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.PURPUR_SLAB, false);
    }

    private static void chorusNook(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z).setType(Material.END_STONE, false);
        world.getBlockAt(x, y + 2, z).setType(Material.CHORUS_PLANT, false);
        world.getBlockAt(x, y + 3, z).setType(Material.CHORUS_FLOWER, false);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.PURPUR_SLAB, false);
    }

    private static void endLanternPost(World world, int x, int y, int z) {
        pillar(world, x, y + 1, z, 3, Material.PURPUR_PILLAR);
        world.getBlockAt(x, y + 4, z).setType(Material.END_ROD, false);
    }

    private static void weatherFloor1(World world, DungeonLayout.Room bounds, int y, int height) {
        int ceil = y + height;
        world.getBlockAt(bounds.minX() + 1, ceil - 1, bounds.minZ() + 1).setType(Material.COBWEB, false);
        world.getBlockAt(bounds.maxX() - 1, ceil - 1, bounds.minZ() + 1).setType(Material.COBWEB, false);
        world.getBlockAt(bounds.minX() + 1, ceil - 1, bounds.maxZ() - 1).setType(Material.COBWEB, false);
        world.getBlockAt(bounds.maxX() - 1, ceil - 1, bounds.maxZ() - 1).setType(Material.COBWEB, false);
        hangChainLantern(world, bounds.centerX(), ceil, bounds.minZ() + 4, 2);
        hangChainLantern(world, bounds.centerX(), ceil, bounds.maxZ() - 4, 2);
        hangChainLantern(world, bounds.minX() + 4, ceil, bounds.centerZ(), 2);
        hangChainLantern(world, bounds.maxX() - 4, ceil, bounds.centerZ(), 2);
    }

    private static void hangChainLantern(World world, int x, int ceilY, int z, int drop) {
        Block support = world.getBlockAt(x, ceilY, z);
        if (!support.getType().isSolid()) {
            return;
        }
        int lanternY = ceilY - Math.max(1, drop);
        for (int chainY = ceilY - 1; chainY > lanternY; chainY--) {
            Block chain = world.getBlockAt(x, chainY, z);
            if (!chain.getType().isAir() && chain.getType() != Material.LIGHT) {
                return;
            }
            chain.setType(Material.CHAIN, false);
            if (chain.getBlockData() instanceof Orientable orientable) {
                orientable.setAxis(Axis.Y);
                chain.setBlockData(orientable, false);
            }
        }
        Block lanternBlock = world.getBlockAt(x, lanternY, z);
        if (!lanternBlock.getType().isAir() && lanternBlock.getType() != Material.LIGHT) {
            return;
        }
        org.bukkit.block.data.BlockData data = Material.SOUL_LANTERN.createBlockData();
        if (data instanceof Lantern hanging) {
            hanging.setHanging(true);
            lanternBlock.setBlockData(hanging, false);
            return;
        }
        lanternBlock.setType(Material.SOUL_LANTERN, false);
    }

    private static void upsideDownStair(World world, int x, int y, int z, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(palStair(), false);
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(facing);
            stairs.setHalf(Bisected.Half.TOP);
            block.setBlockData(stairs, false);
        }
    }

    private static void topSlab(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(palSlab(), false);
        if (block.getBlockData() instanceof Slab slab) {
            slab.setType(Slab.Type.TOP);
            block.setBlockData(slab, false);
        }
    }

    private static Material palFloor() {
        if (themeFloor >= 3) {
            return Material.END_STONE_BRICKS;
        }
        if (themeFloor == 2) {
            return Material.SNOW_BLOCK;
        }
        return Material.DEEPSLATE_BRICKS;
    }

    private static Material palWall() {
        if (themeFloor >= 3) {
            return Material.PURPUR_BLOCK;
        }
        if (themeFloor == 2) {
            return Material.PACKED_ICE;
        }
        return Material.DEEPSLATE_BRICKS;
    }

    private static Material palCeil() {
        if (themeFloor >= 3) {
            return Material.END_STONE;
        }
        if (themeFloor == 2) {
            return Material.PACKED_ICE;
        }
        return Material.DEEPSLATE_TILES;
    }

    private static Material palPillar() {
        if (themeFloor >= 3) {
            return Material.PURPUR_PILLAR;
        }
        if (themeFloor == 2) {
            return Material.SPRUCE_LOG;
        }
        return Material.POLISHED_DEEPSLATE;
    }

    private static Material palAccent() {
        if (themeFloor >= 3) {
            return Material.CRYING_OBSIDIAN;
        }
        if (themeFloor == 2) {
            return Material.BLUE_ICE;
        }
        return Material.CHISELED_DEEPSLATE;
    }

    private static Material palStair() {
        if (themeFloor >= 3) {
            return Material.PURPUR_STAIRS;
        }
        if (themeFloor == 2) {
            return Material.SPRUCE_STAIRS;
        }
        return Material.DEEPSLATE_BRICK_STAIRS;
    }

    private static Material palSlab() {
        if (themeFloor >= 3) {
            return Material.PURPUR_SLAB;
        }
        if (themeFloor == 2) {
            return Material.SPRUCE_SLAB;
        }
        return Material.DEEPSLATE_BRICK_SLAB;
    }

    private static void snowMezzanine(World world, int y, DungeonLayout.Room bounds) {
        int deck = y + 4;
        int minX = bounds.minX() + 1;
        int maxX = bounds.maxX() - 1;
        int minZ = bounds.minZ() + 1;
        int maxZ = bounds.maxZ() - 1;
        if (maxX - minX < 8 || maxZ - minZ < 8) {
            return;
        }
        fill(world, minX, deck, minZ, minX + 1, deck, maxZ, Material.SPRUCE_SLAB);
        fill(world, maxX - 1, deck, minZ, maxX, deck, maxZ, Material.SPRUCE_SLAB);
        int sx = minX + 3;
        int sz = minZ + 5;
        world.getBlockAt(sx, y + 1, sz).setType(Material.SPRUCE_STAIRS, false);
        setStairFacing(world, sx, y + 1, sz, BlockFace.SOUTH);
        world.getBlockAt(sx, y + 2, sz + 1).setType(Material.SPRUCE_STAIRS, false);
        setStairFacing(world, sx, y + 2, sz + 1, BlockFace.SOUTH);
        world.getBlockAt(sx, y + 3, sz + 2).setType(Material.SPRUCE_STAIRS, false);
        setStairFacing(world, sx, y + 3, sz + 2, BlockFace.SOUTH);
        world.getBlockAt(sx, deck, sz + 3).setType(Material.SPRUCE_PLANKS, false);
        lantern(world, minX + 1, deck + 2, bounds.centerZ());
        lantern(world, maxX - 1, deck + 2, bounds.centerZ());
    }

    private static void setStairFacing(World world, int x, int y, int z, BlockFace face) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(face);
            block.setBlockData(stairs, false);
        }
    }

    private static void buildEndArena(World world, int y, DungeonLayout.Room boss, int height) {
        fillMix(world, boss.minX(), y - 1, boss.minZ(), boss.maxX(), y, boss.maxZ(), Mix.FLOOR);
        fillMix(world, boss.minX() + 1, y, boss.minZ() + 1, boss.maxX() - 1, y, boss.maxZ() - 1, Mix.FLOOR);
        wallsMix(world, boss.minX(), y, boss.minZ(), boss.maxX(), y + 8, boss.maxZ());
        fill(world, boss.minX() + 1, y + 1, boss.minZ() + 1, boss.maxX() - 1, y + height, boss.maxZ() - 1, Material.AIR);
        fillMix(world, boss.minX(), y + height, boss.minZ(), boss.maxX(), y + height, boss.maxZ(), Mix.CEIL);
        ribCeiling(world, boss, y + height);
        for (int x = boss.minX() + 2; x <= boss.maxX() - 2; x += 3) {
            world.getBlockAt(x, y + 8, boss.minZ()).setType(Material.END_ROD, false);
            world.getBlockAt(x, y + 8, boss.maxZ()).setType(Material.END_ROD, false);
        }
        for (int z = boss.minZ() + 2; z <= boss.maxZ() - 2; z += 3) {
            world.getBlockAt(boss.minX(), y + 8, z).setType(Material.END_ROD, false);
            world.getBlockAt(boss.maxX(), y + 8, z).setType(Material.END_ROD, false);
        }
        world.getBlockAt(boss.minX() + 1, y + 1, boss.minZ() + 1).setType(Material.CRYING_OBSIDIAN, false);
        world.getBlockAt(boss.maxX() - 1, y + 1, boss.minZ() + 1).setType(Material.CRYING_OBSIDIAN, false);
        world.getBlockAt(boss.minX() + 1, y + 1, boss.maxZ() - 1).setType(Material.CRYING_OBSIDIAN, false);
        world.getBlockAt(boss.maxX() - 1, y + 1, boss.maxZ() - 1).setType(Material.CRYING_OBSIDIAN, false);
        int[][] posts = {
                {boss.minX() + 5, boss.minZ() + 5},
                {boss.maxX() - 5, boss.minZ() + 5},
                {boss.minX() + 5, boss.maxZ() - 5},
                {boss.maxX() - 5, boss.maxZ() - 5},
                {boss.centerX() - 12, boss.centerZ() - 8},
                {boss.centerX() + 12, boss.centerZ() - 8},
                {boss.centerX() - 12, boss.centerZ() + 8},
                {boss.centerX() + 12, boss.centerZ() + 8}
        };
        for (int[] post : posts) {
            if (!boss.contains(post[0], post[1])) {
                continue;
            }
            pillar(world, post[0], y + 1, post[1], 12, Material.OBSIDIAN);
            world.getBlockAt(post[0], y + 13, post[1]).setType(Material.END_ROD, false);
            world.getBlockAt(post[0] + 1, y + 1, post[1]).setType(Material.PURPUR_STAIRS, false);
            world.getBlockAt(post[0] - 1, y + 1, post[1]).setType(Material.PURPUR_STAIRS, false);
        }
        world.getBlockAt(boss.minX() + 7, y + 1, boss.centerZ()).setType(Material.CHORUS_FLOWER, false);
        world.getBlockAt(boss.maxX() - 7, y + 1, boss.centerZ()).setType(Material.CHORUS_FLOWER, false);
        world.getBlockAt(boss.centerX(), y + 1, boss.minZ() + 7).setType(Material.CHORUS_PLANT, false);
        world.getBlockAt(boss.centerX(), y + 1, boss.maxZ() - 7).setType(Material.CHORUS_PLANT, false);
        dais(world, boss.centerX() - 6, y, boss.centerZ() - 6, boss.centerX() + 6, boss.centerZ() + 6);
        fill(world, boss.centerX() - 7, y, boss.centerZ() - 7, boss.centerX() + 7, y, boss.centerZ() - 7, Material.PURPUR_STAIRS);
        fill(world, boss.centerX() - 7, y, boss.centerZ() + 7, boss.centerX() + 7, y, boss.centerZ() + 7, Material.PURPUR_STAIRS);
        fill(world, boss.centerX() - 7, y, boss.centerZ() - 7, boss.centerX() - 7, y, boss.centerZ() + 7, Material.PURPUR_STAIRS);
        fill(world, boss.centerX() + 7, y, boss.centerZ() - 7, boss.centerX() + 7, y, boss.centerZ() + 7, Material.PURPUR_STAIRS);
        lantern(world, boss.minX() + 3, y + 6, boss.minZ() + 3);
        lantern(world, boss.maxX() - 3, y + 6, boss.minZ() + 3);
        lantern(world, boss.minX() + 3, y + 6, boss.maxZ() - 3);
        lantern(world, boss.maxX() - 3, y + 6, boss.maxZ() - 3);
    }

    private static void paintBiome(World world, DungeonLayout layout) {
        Biome biome = themeFloor >= 3 ? Biome.THE_END : themeFloor == 2 ? Biome.SNOWY_PLAINS : Biome.DEEP_DARK;
        DungeonLayout.Room area = layout.bounds();
        for (int x = area.minX(); x <= area.maxX(); x += 2) {
            for (int z = area.minZ(); z <= area.maxZ(); z += 2) {
                try {
                    world.setBiome(x, FLOOR_Y, z, biome);
                } catch (Throwable ignored) {
                }
            }
        }
        if (themeFloor == 2) {
            world.setStorm(true);
            world.setWeatherDuration(20 * 60 * 30);
        }
    }

    private static void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material current = world.getBlockAt(x, y, z).getType();
                    if (material == Material.AIR && (current == Material.LIGHT
                            || current == Material.SOUL_LANTERN
                            || current == Material.CHAIN
                            || current == Material.COBWEB)) {
                        continue;
                    }
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private static void walls(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        fill(world, x1, y1, z1, x2, y2, z1, material);
        fill(world, x1, y1, z2, x2, y2, z2, material);
        fill(world, x1, y1, z1, x1, y2, z2, material);
        fill(world, x2, y1, z1, x2, y2, z2, material);
    }

    private static void pillar(World world, int x, int y, int z, int height, Material material) {
        fill(world, x, y, z, x, y + height - 1, z, material);
    }

    private static void dais(World world, int minX, int y, int minZ, int maxX, int maxZ) {
        fill(world, minX, y, minZ, maxX, y, maxZ, palPillar());
        fill(world, minX + 1, y + 1, minZ + 1, maxX - 1, y + 1, maxZ - 1, palAccent());
        light(world, (minX + maxX) / 2, y + 3, (minZ + maxZ) / 2);
    }

    private static void lowWall(World world, int x1, int y, int z1, int x2, int z2) {
        fill(world, x1, y + 1, z1, x2, y + 1, z2, themeFloor >= 3 ? Material.PURPUR_PILLAR : themeFloor == 2 ? Material.SPRUCE_FENCE : Material.DEEPSLATE_BRICK_WALL);
    }

    private static void stainFloor(World world, DungeonLayout.Room bounds, int y, int seed) {
        java.util.Random random = new java.util.Random(seed * 97L + bounds.minZ());
        int width = Math.max(1, bounds.maxX() - bounds.minX() - 2);
        int depth = Math.max(1, bounds.maxZ() - bounds.minZ() - 2);
        int spots = themeFloor == 1 ? 12 + random.nextInt(10) : 6 + random.nextInt(8);
        for (int i = 0; i < spots; i++) {
            int x = bounds.minX() + 1 + random.nextInt(width);
            int z = bounds.minZ() + 1 + random.nextInt(depth);
            if (Math.abs(x) <= 1) {
                continue;
            }
            Material stain;
            if (themeFloor >= 3) {
                stain = random.nextBoolean() ? Material.PURPUR_BLOCK : Material.END_STONE;
            } else if (themeFloor == 2) {
                stain = random.nextBoolean() ? Material.PACKED_ICE : Material.BLUE_ICE;
            } else {
                stain = random.nextBoolean() ? Material.CRACKED_DEEPSLATE_BRICKS : Material.MOSSY_STONE_BRICKS;
                if (random.nextInt(5) == 0) {
                    stain = Material.MOSSY_COBBLESTONE;
                } else if (random.nextInt(4) == 0) {
                    stain = Material.COBBLED_DEEPSLATE;
                }
            }
            world.getBlockAt(x, y, z).setType(stain, false);
        }
    }

    private static void lantern(World world, int x, int y, int z) {
        int placeY = y;
        for (int check = y; check <= y + 8; check++) {
            if (world.getBlockAt(x, check + 1, z).getType().isSolid()) {
                placeY = check;
                break;
            }
        }
        Block support = world.getBlockAt(x, placeY + 1, z);
        if (!support.getType().isSolid()) {
            return;
        }
        Block block = world.getBlockAt(x, placeY, z);
        if (!block.getType().isAir()
                && block.getType() != Material.LIGHT
                && block.getType() != Material.SOUL_LANTERN) {
            return;
        }
        org.bukkit.block.data.BlockData data = Material.SOUL_LANTERN.createBlockData();
        if (data instanceof Lantern hanging) {
            hanging.setHanging(true);
            block.setBlockData(hanging, false);
            return;
        }
        block.setType(Material.SOUL_LANTERN, false);
    }

    private static void brazier(World world, int x, int y, int z) {
        if (themeFloor >= 3) {
            world.getBlockAt(x, y, z).setType(Material.CRYING_OBSIDIAN, false);
            world.getBlockAt(x, y + 1, z).setType(Material.END_ROD, false);
            return;
        }
        if (themeFloor == 2) {
            world.getBlockAt(x, y, z).setType(Material.PACKED_ICE, false);
            world.getBlockAt(x, y + 1, z).setType(Material.SOUL_CAMPFIRE, false);
            return;
        }
        world.getBlockAt(x, y, z).setType(Material.SOUL_SOIL, false);
        world.getBlockAt(x, y + 1, z).setType(Material.SOUL_FIRE, false);
    }

    private static void stair(World world, int x, int y, int z, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(palStair(), false);
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(facing);
            block.setBlockData(stairs, false);
        }
    }

    private static void wallSign(World world, int x, int y, int z, BlockFace facing, String... lines) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_WALL_SIGN, false);
        if (block.getBlockData() instanceof WallSign data) {
            data.setFacing(facing);
            block.setBlockData(data, false);
        }
        if (block.getState() instanceof Sign sign) {
            var side = sign.getSide(Side.FRONT);
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                side.setLine(i, lines[i]);
            }
            sign.update(true, false);
        }
    }

    private static void door(World world, int x, int y, int z, BlockFace facing, boolean open) {
        Block lower = world.getBlockAt(x, y, z);
        Block upper = world.getBlockAt(x, y + 1, z);
        lower.setType(Material.IRON_DOOR, false);
        upper.setType(Material.IRON_DOOR, false);
        if (lower.getBlockData() instanceof Door door) {
            door.setFacing(facing);
            door.setHalf(Bisected.Half.BOTTOM);
            door.setOpen(open);
            lower.setBlockData(door, false);
        }
        if (upper.getBlockData() instanceof Door door) {
            door.setFacing(facing);
            door.setHalf(Bisected.Half.TOP);
            door.setOpen(open);
            upper.setBlockData(door, false);
        }
    }
}
