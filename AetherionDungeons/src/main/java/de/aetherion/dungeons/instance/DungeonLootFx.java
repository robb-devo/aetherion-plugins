package de.aetherion.dungeons.instance;

import de.aetherion.dungeons.AetherionDungeons;
import de.aetherion.dungeons.bridge.ItemLootBridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonLootFx {

    public static final int VICTORY_ID = -2;
    public static final String SPIN_TAG = "aether_loot_spin";
    public static final String LABEL_TAG = "aether_loot_label";

    private static final NamespacedKey CHEST_ID = new NamespacedKey("aetheriondungeons", "loot_chest");
    private static final NamespacedKey CHEST_FLOOR = new NamespacedKey("aetheriondungeons", "loot_floor");
    private static final NamespacedKey CHEST_VICTORY = new NamespacedKey("aetheriondungeons", "loot_victory");
    private static final NamespacedKey CHEST_VESTIGE = new NamespacedKey("aetheriondungeons", "loot_vestige");
    private static final Set<String> SPINNING = ConcurrentHashMap.newKeySet();

    private DungeonLootFx() {
    }

    public static void placeCombat(Plugin plugin, World world, DungeonLayout.CombatRoom room, int floor) {
        place(plugin, world, room.lootX(), PrototypeDungeonBuilder.FLOOR_Y + 1, room.lootZ(), room.index(), floor, false, false);
    }

    public static void placeVictory(Plugin plugin, World world, DungeonLayout layout, int floor) {
        DungeonLayout.Room boss = layout.boss();
        place(plugin, world, boss.centerX() + 2, PrototypeDungeonBuilder.FLOOR_Y + 1, boss.centerZ(), VICTORY_ID, floor, true, false);
    }

    /** Victory cache at an explicit block (Endless XL / schematic floors). */
    public static void placeVictoryAt(Plugin plugin, World world, int x, int y, int z, int floor) {
        place(plugin, world, x, y, z, VICTORY_ID, floor, true, false);
    }

    public static void placeGuaranteedVestige(Plugin plugin, World world, int x, int z, int chestId, int floor) {
        place(plugin, world, x, PrototypeDungeonBuilder.FLOOR_Y + 1, z, chestId, floor, false, true);
    }

    public static Integer chestId(Block block) {
        if (block == null || !(block.getState() instanceof TileState state)) {
            return null;
        }
        return state.getPersistentDataContainer().get(CHEST_ID, PersistentDataType.INTEGER);
    }

    public static boolean isLootChest(Block block) {
        return chestId(block) != null;
    }

    public static boolean isStorageChest(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    /**
     * Block behind a chest inventory, including the left half of a double chest.
     */
    public static Block chestBlock(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest chest) {
            return chest.getBlock();
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            org.bukkit.inventory.InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest chest) {
                return chest.getBlock();
            }
            org.bukkit.inventory.InventoryHolder right = doubleChest.getRightSide();
            if (right instanceof Chest chest) {
                return chest.getBlock();
            }
        }
        return null;
    }

    /**
     * Turn one vanilla (or trapped) chest into a Floor 3 combat cache.
     * Double chests share one id. Victory caches are left alone.
     *
     * @return true when this call newly tagged a vanilla chest
     */
    public static boolean adoptMapChest(Plugin plugin, Block block, int floor) {
        if (!isStorageChest(block) || !(block.getBlockData() instanceof org.bukkit.block.data.type.Chest data)) {
            return false;
        }
        Block partner = partnerChest(block, data);
        if (isVictory(block) || isVictory(partner)) {
            return false;
        }
        Integer existing = firstId(block, partner);
        boolean fresh = existing == null;
        Block anchor = anchor(block, partner);
        int id = fresh
                ? AshesChestIds.id(anchor.getX(), anchor.getY(), anchor.getZ())
                : existing;
        tagLoot(block, id, floor, false, false);
        if (partner != null) {
            tagLoot(partner, id, floor, false, false);
        }
        present(plugin, anchor, id, floor, false, false);
        return fresh;
    }

    public static void tryLoot(Plugin plugin, DungeonSession session, Player player, Block block) {
        Integer id = chestId(block);
        if (id == null || session == null || player == null) {
            return;
        }
        if (session.hasClaimedChest(id, player.getUniqueId())) {
            player.sendMessage("§7You already took your share.");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.4f, 1.4f);
            return;
        }
        if (session.isChestBusy(id)) {
            player.sendMessage("§7Wait for the spin.");
            return;
        }
        boolean victory = false;
        boolean vestige = false;
        int floor = session.floorNumber();
        if (block.getState() instanceof TileState state) {
            Byte flag = state.getPersistentDataContainer().get(CHEST_VICTORY, PersistentDataType.BYTE);
            victory = flag != null && flag == 1;
            Byte vestigeFlag = state.getPersistentDataContainer().get(CHEST_VESTIGE, PersistentDataType.BYTE);
            vestige = vestigeFlag != null && vestigeFlag == 1;
            Integer storedFloor = state.getPersistentDataContainer().get(CHEST_FLOOR, PersistentDataType.INTEGER);
            if (storedFloor != null) {
                floor = storedFloor;
            }
        }
        RolledLoot rolled = vestige
                ? new RolledLoot(orFallback(ItemLootBridge.rollDungeonArmor(1.0, session.vestigeSlotsThisRun(player.getUniqueId())), Kind.VESTIGE, floor, player, session), Kind.VESTIGE)
                : victory ? rollVictory(floor, player, session) : rollCombat(floor, player, session);
        if (rolled.item() == null) {
            player.sendMessage("§cThe cache jammed. Try again in a second.");
            return;
        }
        session.setChestBusy(id, true);
        ItemDisplay spinner = spinnerAt(block);
        TextDisplay label = labelAt(block);
        List<ItemStack> pool = vestige ? vestigePool() : showcasePool(floor, victory);
        try {
            if (spinner == null) {
                finishGive(session, player, block, id, rolled, label);
                return;
            }
            spin(plugin, session, player, block, id, spinner, label, pool, rolled);
        } catch (RuntimeException exception) {
            session.setChestBusy(id, false);
            throw exception;
        }
    }

    private static void place(Plugin plugin, World world, int x, int y, int z, int chestId, int floor, boolean victory, boolean vestige) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        tagLoot(block, chestId, floor, victory, vestige);
        present(plugin, block, chestId, floor, victory, vestige);
    }

    private static void tagLoot(Block block, int chestId, int floor, boolean victory, boolean vestige) {
        asPlainChest(block);
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Integer have = chest.getPersistentDataContainer().get(CHEST_ID, PersistentDataType.INTEGER);
        Integer haveFloor = chest.getPersistentDataContainer().get(CHEST_FLOOR, PersistentDataType.INTEGER);
        Byte haveVictory = chest.getPersistentDataContainer().get(CHEST_VICTORY, PersistentDataType.BYTE);
        Byte haveVestige = chest.getPersistentDataContainer().get(CHEST_VESTIGE, PersistentDataType.BYTE);
        boolean tagged = have != null && have == chestId
                && haveFloor != null && haveFloor == floor
                && haveVictory != null && haveVictory == (victory ? (byte) 1 : (byte) 0)
                && haveVestige != null && haveVestige == (vestige ? (byte) 1 : (byte) 0);
        boolean empty = isEmpty(chest);
        if (tagged && empty) {
            return;
        }
        chest.getSnapshotInventory().clear();
        chest.getPersistentDataContainer().set(CHEST_ID, PersistentDataType.INTEGER, chestId);
        chest.getPersistentDataContainer().set(CHEST_FLOOR, PersistentDataType.INTEGER, floor);
        chest.getPersistentDataContainer().set(CHEST_VICTORY, PersistentDataType.BYTE, victory ? (byte) 1 : (byte) 0);
        chest.getPersistentDataContainer().set(CHEST_VESTIGE, PersistentDataType.BYTE, vestige ? (byte) 1 : (byte) 0);
        chest.update(true, false);
    }

    private static boolean isEmpty(Chest chest) {
        for (ItemStack stack : chest.getSnapshotInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void asPlainChest(Block block) {
        if (block.getType() != Material.TRAPPED_CHEST) {
            return;
        }
        org.bukkit.block.data.type.Chest old = block.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                ? data
                : null;
        block.setType(Material.CHEST, false);
        if (old != null && block.getBlockData() instanceof org.bukkit.block.data.type.Chest next) {
            next.setFacing(old.getFacing());
            next.setType(old.getType());
            next.setWaterlogged(old.isWaterlogged());
            block.setBlockData(next, false);
        }
    }

    private static void present(Plugin plugin, Block block, int chestId, int floor, boolean victory, boolean vestige) {
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        List<ItemStack> pool = vestige ? vestigePool() : showcasePool(floor, victory);
        ItemDisplay spinner = spinnerAt(block);
        if (spinner != null) {
            Integer shown = spinner.getPersistentDataContainer().get(CHEST_ID, PersistentDataType.INTEGER);
            if (shown == null || shown != chestId) {
                spinner.remove();
                spinner = null;
                TextDisplay stale = labelAt(block);
                if (stale != null) {
                    stale.remove();
                }
            }
        }
        if (spinner == null) {
            Location spinAt = new Location(world, x + 0.5, y + 1.15, z + 0.5);
            ItemStack first = pool.isEmpty() ? new ItemStack(Material.CHEST) : pool.get(0);
            spinner = world.spawn(spinAt, ItemDisplay.class, display -> {
                display.setItemStack(first);
                display.setPersistent(true);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0f);
                display.setTransformation(spinPose(0f, 0.55f));
                display.addScoreboardTag(SPIN_TAG);
                display.getPersistentDataContainer().set(CHEST_ID, PersistentDataType.INTEGER, chestId);
            });
        }
        if (labelAt(block) == null) {
            Location textAt = new Location(world, x + 0.5, y + 1.65, z + 0.5);
            world.spawn(textAt, TextDisplay.class, text -> {
                String headline = vestige ? "Warden vestige cache" : "Right-click to loot";
                String sub = vestige ? "Guaranteed set piece" : "One item each";
                text.text(Component.text(headline)
                        .color(vestige ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.newline())
                        .append(Component.text(sub).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setSeeThrough(false);
                text.setShadowed(true);
                text.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                text.setPersistent(true);
                text.setGravity(false);
                text.addScoreboardTag(LABEL_TAG);
                text.getPersistentDataContainer().set(CHEST_ID, PersistentDataType.INTEGER, chestId);
            });
        }
        ensureSpin(plugin, world, chestId, spinner, pool);
    }

    private static void ensureSpin(Plugin plugin, World world, int chestId, ItemDisplay spinner, List<ItemStack> pool) {
        if (plugin == null || spinner == null || world == null) {
            return;
        }
        String key = world.getUID() + ":" + chestId;
        if (!SPINNING.add(key)) {
            return;
        }
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!spinner.isValid() || world.getPlayers().isEmpty()) {
                    SPINNING.remove(key);
                    cancel();
                    return;
                }
                DungeonSession session = AetherionDungeons.getInstance() == null
                        ? null
                        : AetherionDungeons.getInstance().getInstances().sessionOf(world);
                if (session != null && session.isChestBusy(chestId)) {
                    return;
                }
                if (pool.isEmpty()) {
                    return;
                }
                step++;
                spinner.setItemStack(pool.get(step % pool.size()));
                spinner.setTransformation(spinPose(step * 18f, 0.55f));
            }
        }.runTaskTimer(plugin, 10L, 8L);
    }

    private static boolean isVictory(Block block) {
        if (block == null || !(block.getState() instanceof TileState state)) {
            return false;
        }
        Byte flag = state.getPersistentDataContainer().get(CHEST_VICTORY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private static Integer firstId(Block block, Block partner) {
        Integer id = chestId(block);
        if (id != null) {
            return id;
        }
        return partner == null ? null : chestId(partner);
    }

    private static Block anchor(Block block, Block partner) {
        if (partner == null) {
            return block;
        }
        if (partner.getX() < block.getX()) {
            return partner;
        }
        if (partner.getX() == block.getX() && partner.getZ() < block.getZ()) {
            return partner;
        }
        if (partner.getX() == block.getX() && partner.getZ() == block.getZ() && partner.getY() < block.getY()) {
            return partner;
        }
        return block;
    }

    /**
     * Other half of a double chest. Vanilla puts LEFT's partner clockwise of facing
     * and RIGHT's partner counter-clockwise. If that block is not the pair, take the
     * single horizontal neighbor that is the opposite half.
     */
    private static Block partnerChest(Block block, org.bukkit.block.data.type.Chest data) {
        Type type = data.getType();
        if (type == Type.SINGLE) {
            return null;
        }
        BlockFace facing = data.getFacing();
        Type want = type == Type.LEFT ? Type.RIGHT : Type.LEFT;
        BlockFace preferred = type == Type.LEFT ? clockwise(facing) : counterClockwise(facing);
        Block preferredBlock = block.getRelative(preferred);
        if (isPartner(preferredBlock, facing, want)) {
            return preferredBlock;
        }
        Block found = null;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block adjacent = block.getRelative(face);
            if (!isPartner(adjacent, facing, want)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = adjacent;
        }
        return found;
    }

    private static boolean isPartner(Block adjacent, BlockFace facing, Type want) {
        if (!isStorageChest(adjacent) || !(adjacent.getBlockData() instanceof org.bukkit.block.data.type.Chest other)) {
            return false;
        }
        return other.getFacing() == facing && other.getType() == want;
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private static BlockFace counterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private static void spin(
            Plugin plugin,
            DungeonSession session,
            Player player,
            Block block,
            int chestId,
            ItemDisplay spinner,
            TextDisplay label,
            List<ItemStack> pool,
            RolledLoot rolled
    ) {
        if (label != null && label.isValid()) {
            label.text(Component.text("...").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        new BukkitRunnable() {
            int step = 0;
            final int total = 22 + ThreadLocalRandom.current().nextInt(6);

            @Override
            public void run() {
                if (!spinner.isValid() || !player.isOnline()) {
                    session.setChestBusy(chestId, false);
                    cancel();
                    return;
                }
                step++;
                if (step < total) {
                    if (!pool.isEmpty()) {
                        spinner.setItemStack(pool.get(step % pool.size()));
                    }
                    spinner.setTransformation(spinPose(step * 42f, 0.62f));
                    player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.4f + (step % 5) * 0.08f);
                    return;
                }
                spinner.setItemStack(rolled.item());
                spinner.setTransformation(spinPose(0f, 0.85f));
                burst(player, block.getLocation().add(0.5, 1.3, 0.5), rolled.kind());
                cancel();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    finishGive(session, player, block, chestId, rolled, label);
                    if (spinner.isValid()) {
                        spinner.setTransformation(spinPose(0f, 0.55f));
                    }
                }, 16L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void finishGive(
            DungeonSession session,
            Player player,
            Block block,
            int chestId,
            RolledLoot rolled,
            TextDisplay label
    ) {
        session.claimChest(chestId, player.getUniqueId());
        session.setChestBusy(chestId, false);
        if (!player.isOnline()) {
            return;
        }
        org.bukkit.World world = player.getWorld();
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(rolled.item().clone());
        leftover.values().forEach(stack -> {
            if (world != null) {
                world.dropItemNaturally(player.getLocation(), stack);
            }
        });
        if (rolled.kind() == Kind.VESTIGE) {
            String pieceId = ItemLootBridge.vestigeIdOf(rolled.item());
            if (pieceId != null) {
                session.rememberVestigeSlot(player.getUniqueId(), pieceId);
            }
        }
        player.sendMessage("§6Cache §7pays out: " + nameOf(rolled.item()));
        if (allClaimed(session, chestId)) {
            if (label != null && label.isValid()) {
                label.text(Component.text("Empty").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
            ItemDisplay spinner = spinnerAt(block);
            if (spinner != null) {
                spinner.remove();
            }
        } else if (label != null && label.isValid()) {
            label.text(Component.text("Right-click to loot")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.newline())
                    .append(Component.text("One item each").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        }
    }

    private static boolean allClaimed(DungeonSession session, int chestId) {
        if (session.party().isEmpty()) {
            return true;
        }
        for (UUID id : session.party()) {
            if (!session.hasClaimedChest(chestId, id)) {
                return false;
            }
        }
        return true;
    }

    private static void burst(Player player, Location at, Kind kind) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        switch (kind) {
            case COMPACTED, RELIC, MYTH -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 18, 0.25, 0.35, 0.25, 0.15);
                world.spawnParticle(Particle.END_ROD, at, 10, 0.2, 0.3, 0.2, 0.02);
                player.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.35f);
            }
            case VESTIGE, SCHEMATIC, CORE -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.3, 0.3, 0.3, 0);
                world.spawnParticle(Particle.CRIT, at, 8, 0.2, 0.2, 0.2, 0.05);
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            }
            case BOOSTER -> {
                world.spawnParticle(Particle.WAX_ON, at, 8, 0.25, 0.25, 0.25, 0);
                player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
            }
            default -> {
                world.spawnParticle(Particle.CLOUD, at, 6, 0.2, 0.15, 0.2, 0.01);
                player.playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.9f);
            }
        }
    }

    private static RolledLoot rollCombat(int floor, Player player, DungeonSession session) {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 2) {
            return new RolledLoot(orFallback(ItemLootBridge.rollCompacted(), Kind.COMPACTED, floor, player, session), Kind.COMPACTED);
        }
        if (r < 18) {
            return new RolledLoot(orFallback(ItemLootBridge.rollCompressed(), Kind.COMPRESSED, floor, player, session), Kind.COMPRESSED);
        }
        if (r < 44) {
            return new RolledLoot(orFallback(ItemLootBridge.create("random_booster"), Kind.BOOSTER, floor, player, session), Kind.BOOSTER);
        }
        // Floor 1 only: vestige armor + dungeon weapon schematics.
        if (floor <= 1) {
            if (r < 66) {
                return new RolledLoot(orFallback(ItemLootBridge.rollWeaponSchematic(1.0, floor), Kind.SCHEMATIC, floor, player, session), Kind.SCHEMATIC);
            }
            return new RolledLoot(orFallback(
                    ItemLootBridge.rollDungeonArmor(1.0, session.vestigeSlotsThisRun(player.getUniqueId())),
                    Kind.VESTIGE,
                    floor,
                    player,
                    session
            ), Kind.VESTIGE);
        }
        // F2/F3: cores and mats — no unidentified / no vestige from random chests.
        if (r < 62) {
            return new RolledLoot(orFallback(ItemLootBridge.rollDungeonCore(floor, 1.0), Kind.CORE, floor, player, session), Kind.CORE);
        }
        if (r < 78) {
            return new RolledLoot(orFallback(ItemLootBridge.rollCompressed(), Kind.COMPRESSED, floor, player, session), Kind.COMPRESSED);
        }
        return new RolledLoot(orFallback(ItemLootBridge.create("random_booster"), Kind.BOOSTER, floor, player, session), Kind.BOOSTER);
    }

    private static RolledLoot rollVictory(int floor, Player player, DungeonSession session) {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (floor >= 3 && r < 3) {
            return new RolledLoot(orFallback(ItemLootBridge.rollAetherionPiece(1.0), Kind.MYTH, floor, player, session), Kind.MYTH);
        }
        if (r < 8) {
            return new RolledLoot(orFallback(ItemLootBridge.rollBossItem(1.0), Kind.RELIC, floor, player, session), Kind.RELIC);
        }
        if (r < 22) {
            return new RolledLoot(orFallback(ItemLootBridge.rollDungeonCore(floor, 1.0), Kind.CORE, floor, player, session), Kind.CORE);
        }
        if (r < 24) {
            return new RolledLoot(orFallback(ItemLootBridge.rollCompacted(), Kind.COMPACTED, floor, player, session), Kind.COMPACTED);
        }
        if (r < 40) {
            return new RolledLoot(orFallback(ItemLootBridge.rollCompressed(), Kind.COMPRESSED, floor, player, session), Kind.COMPRESSED);
        }
        if (r < 58) {
            return new RolledLoot(orFallback(ItemLootBridge.create("random_booster"), Kind.BOOSTER, floor, player, session), Kind.BOOSTER);
        }
        if (floor <= 1) {
            if (r < 76) {
                return new RolledLoot(orFallback(ItemLootBridge.rollWeaponSchematic(1.0, floor), Kind.SCHEMATIC, floor, player, session), Kind.SCHEMATIC);
            }
            return new RolledLoot(orFallback(
                    ItemLootBridge.rollDungeonArmor(1.0, session.vestigeSlotsThisRun(player.getUniqueId())),
                    Kind.VESTIGE,
                    floor,
                    player,
                    session
            ), Kind.VESTIGE);
        }
        // F2/F3 victory: more cores / mats, no schematic / vestige.
        if (r < 82) {
            return new RolledLoot(orFallback(ItemLootBridge.rollDungeonCore(floor, 1.0), Kind.CORE, floor, player, session), Kind.CORE);
        }
        return new RolledLoot(orFallback(ItemLootBridge.create("random_booster"), Kind.BOOSTER, floor, player, session), Kind.BOOSTER);
    }

    private static ItemStack orFallback(ItemStack item, Kind kind, int floor, Player player, DungeonSession session) {
        if (item != null) {
            return item;
        }
        if (floor <= 1) {
            java.util.Set<String> avoid = session != null && player != null
                    ? session.vestigeSlotsThisRun(player.getUniqueId())
                    : null;
            ItemStack vestige = ItemLootBridge.rollDungeonArmor(1.0, avoid);
            if (vestige != null) {
                return vestige;
            }
            ItemStack schematic = ItemLootBridge.rollWeaponSchematic(1.0, floor);
            if (schematic != null) {
                return schematic;
            }
        }
        ItemStack core = ItemLootBridge.rollDungeonCore(Math.max(1, floor), 1.0);
        if (core != null) {
            return core;
        }
        ItemStack compressed = ItemLootBridge.rollCompressed();
        if (compressed != null) {
            return compressed;
        }
        return new ItemStack(kind == Kind.BOOSTER ? Material.FIREWORK_ROCKET : Material.PAPER);
    }

    private static List<ItemStack> showcasePool(int floor, boolean victory) {
        List<ItemStack> pool = new ArrayList<>();
        if (floor <= 1) {
            add(pool, ItemLootBridge.rollDungeonArmor(1.0));
            add(pool, ItemLootBridge.rollWeaponSchematic(1.0, floor));
        } else {
            add(pool, ItemLootBridge.rollDungeonCore(floor, 1.0));
        }
        add(pool, ItemLootBridge.create("random_booster"));
        add(pool, ItemLootBridge.rollCompressed());
        add(pool, ItemLootBridge.rollCompacted());
        if (victory) {
            add(pool, ItemLootBridge.rollDungeonCore(floor, 1.0));
            add(pool, ItemLootBridge.rollBossItem(1.0));
        }
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Material.CHEST));
        }
        return pool;
    }

    private static List<ItemStack> vestigePool() {
        List<ItemStack> pool = new ArrayList<>();
        for (String id : ItemLootBridge.vestigePieceIds()) {
            add(pool, ItemLootBridge.create(id));
        }
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Material.LEATHER_CHESTPLATE));
        }
        return pool;
    }

    private static void add(List<ItemStack> pool, ItemStack item) {
        if (item != null) {
            pool.add(item);
        }
    }

    private static ItemDisplay spinnerAt(Block block) {
        Location at = block.getLocation().add(0.5, 1.15, 0.5);
        for (var entity : block.getWorld().getNearbyEntities(at, 1.5, 2.0, 1.5)) {
            if (entity instanceof ItemDisplay display && display.getScoreboardTags().contains(SPIN_TAG)) {
                return display;
            }
        }
        return null;
    }

    private static TextDisplay labelAt(Block block) {
        Location at = block.getLocation().add(0.5, 1.65, 0.5);
        for (var entity : block.getWorld().getNearbyEntities(at, 1.5, 2.0, 1.5)) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(LABEL_TAG)) {
                return display;
            }
        }
        return null;
    }

    private static Transformation spinPose(float yawDeg, float scale) {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f((float) Math.toRadians(yawDeg), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        );
    }

    private static String nameOf(ItemStack item) {
        if (item == null) {
            return "nothing";
        }
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    public enum Kind {
        VESTIGE,
        SCHEMATIC,
        BOOSTER,
        COMPRESSED,
        COMPACTED,
        CORE,
        RELIC,
        MYTH
    }

    public record RolledLoot(ItemStack item, Kind kind) {
    }
}
