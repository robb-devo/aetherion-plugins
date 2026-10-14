package de.aetherion.items.casino;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DEV prop: ~1×2 roulette table (display entities + click hitbox), same idea as {@link CasinoCabinet}.
 *
 * <p>The wheel head really turns. When someone plays at the table, the ball runs the
 * track against the wheel and drops into a pocket of the colour the GUI rolled.
 * A small board above the table keeps the last results.
 */
public final class RouletteCabinet implements Listener {

    private static final int POCKETS = 12;
    private static final float POCKET_RADIUS = 0.22f;
    private static final float TRACK_RADIUS = 0.31f;
    private static final float BALL = 0.055f;
    private static final float BALL_Y_POCKET = 0.0425f;
    private static final float BALL_Y_TRACK = 0.005f;
    private static final float IDLE_SPEED = 0.017f;
    private static final double WHEEL_Y = 0.845;
    private static final int HISTORY = 6;
    private static final Color BOARD_BG = Color.fromARGB(150, 8, 6, 10);

    private static final Map<String, Table> TABLES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SEATS = new ConcurrentHashMap<>();

    public RouletteCabinet() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, RouletteCabinet::tick, 20L, 1L);
            plugin.getServer().getScheduler().runTaskTimer(plugin, RouletteCabinet::rescan, 35L, 200L);
        }
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lRoulette Table");
            meta.setLore(List.of(
                    "§7DEV · a 1×2 roulette table prop.",
                    "§7The wheel turns. The ball really runs.",
                    "",
                    "§eRight-click a block §7to place it.",
                    "§eSneak + right-click §7to pack it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ItemKeys.rouletteCabinetAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.rouletteCabinetAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isCabinet(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.rouletteCabinet(), PersistentDataType.BYTE);
    }

    // ── events ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isAnchor(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            if (!despawnNearest(player)) {
                player.sendMessage("§cNo roulette table nearby.");
            }
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the table.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = CasinoProps.look(player).getOppositeFace();
        if (!CasinoProps.fits(origin.getBlock())) {
            player.sendMessage("§cNeed 1×2 air where it stands.");
            return;
        }
        build(origin, front);
        Location at = origin.clone().add(0.5, 0.9, 0.5);
        player.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.35f, 1.2f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 0.85f);
        player.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 1.6f);
        at.getWorld().spawnParticle(Particle.WAX_ON, at, 20, 0.45, 0.2, 0.45, 0.4);
        player.sendMessage("§aRoulette table is up. Click it to play.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickMachine(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isCabinet(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (isAnchor(player.getInventory().getItemInMainHand()) && player.isSneaking()) {
            despawn(event.getRightClicked());
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 0.7f, 0.8f);
            player.sendMessage("§ePacked up the roulette table.");
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(player.getInventory().getItemInMainHand())) {
            return;
        }
        openRoulette(player, event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunchMachine(EntityDamageByEntityEvent event) {
        if (!isCabinet(event.getEntity()) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(player.getInventory().getItemInMainHand())) {
            return;
        }
        openRoulette(player, event.getEntity());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            register(entity);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SEATS.remove(event.getPlayer().getUniqueId());
    }

    // ── despawn ─────────────────────────────────────────────────────────────

    public static boolean despawn(Entity entity) {
        if (!isCabinet(entity)) {
            return false;
        }
        String id = CasinoProps.id(entity);
        if (id != null) {
            removeTable(id, entity.getLocation());
            return true;
        }
        removeCluster(entity.getLocation(), 1.55, 2.9);
        return true;
    }

    private static void removeTable(String id, Location near) {
        World world = near.getWorld();
        Table table = TABLES.remove(id);
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(near, 3.0, 4.0, 3.0)) {
            if (!isCabinet(entity) || !id.equals(CasinoProps.id(entity))) {
                continue;
            }
            if (entity instanceof Interaction) {
                CasinoProps.unlight(entity.getLocation().getBlock().getRelative(BlockFace.UP));
            }
            entity.remove();
        }
        if (table != null && table.origin != null) {
            CasinoProps.unlight(table.origin.getBlock().getRelative(BlockFace.UP));
        }
        SEATS.values().removeIf(id::equals);
    }

    private static boolean despawnNearest(Player player) {
        Entity closest = null;
        double best = 10.0;
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (!isCabinet(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < best * best) {
                best = Math.sqrt(distance);
                closest = entity;
            }
        }
        if (closest == null) {
            return false;
        }
        Location at = closest.getLocation();
        despawn(closest);
        player.playSound(at, Sound.BLOCK_STONE_BREAK, 0.7f, 0.8f);
        player.sendMessage("§ePacked up the roulette table.");
        return true;
    }

    // ── play hooks (called by the roulette GUI) ─────────────────────────────

    private static void openRoulette(Player player, Entity clicked) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCasino() == null) {
            return;
        }
        String id = CasinoProps.id(clicked);
        Table table = id == null ? null : TABLES.get(id);
        if (table != null) {
            SEATS.put(player.getUniqueId(), id);
        } else {
            SEATS.remove(player.getUniqueId());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.35f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
        plugin.getCasino().open(player, "roulette");
    }

    /**
     * GUI launched the ball. {@code number} is what the GUI already rolled; the table
     * lands the ball in a pocket of the same colour after {@code ticks}.
     */
    static void tableSpin(Player player, int ticks, int number) {
        Table table = seated(player);
        if (table == null) {
            return;
        }
        table.spinner = player.getUniqueId();
        table.spinStart = Bukkit.getCurrentTick();
        table.spinTicks = Math.max(20, ticks);
        table.wheelFrom = table.wheelAngle;
        table.targetPocket = pocketFor(number);
        table.lastBallSector = Integer.MIN_VALUE;
        table.spinPending = true;
        table.set(Mode.SPIN, table.spinTicks + 80);
        Location at = table.center(0.9);
        for (Player near : CasinoProps.viewers(at, 14)) {
            if (!near.equals(player)) {
                near.playSound(at, Sound.BLOCK_PISTON_EXTEND, 0.3f, 1.5f);
            }
        }
    }

    /** GUI paid out (or didn't): flash the table and log the number on the board. */
    static void tableResult(Player player, int number, boolean won) {
        Table table = seated(player);
        if (table == null) {
            return;
        }
        table.spinner = null;
        table.history.addFirst(number);
        while (table.history.size() > HISTORY) {
            table.history.removeLast();
        }
        table.boardDirty = true;
        Location top = table.center(1.05);
        World world = top.getWorld();
        if (world == null) {
            return;
        }
        List<Player> near = CasinoProps.viewers(top, 16);
        near.remove(player);
        if (won && number == 0) {
            table.set(Mode.ZERO, 120);
            table.light(15);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, top, 50, 0.4, 0.3, 0.4, 0.3);
            world.spawnParticle(Particle.HAPPY_VILLAGER, top, 20, 0.45, 0.2, 0.45, 0);
            for (Player other : near) {
                other.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        } else if (won) {
            table.set(Mode.WIN, 60);
            table.light(13);
            world.spawnParticle(Particle.WAX_ON, top, 18, 0.4, 0.2, 0.4, 0.4);
            world.spawnParticle(Particle.HAPPY_VILLAGER, top, 12, 0.4, 0.15, 0.4, 0);
            for (Player other : near) {
                other.playSound(top, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);
            }
        } else {
            table.set(Mode.LOSE, 30);
            table.light(7);
            for (Player other : near) {
                other.playSound(top, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.6f);
            }
        }
    }

    private static Table seated(Player player) {
        if (player == null) {
            return null;
        }
        String id = SEATS.get(player.getUniqueId());
        Table table = id == null ? null : TABLES.get(id);
        if (table == null || table.origin == null || table.origin.getWorld() == null) {
            return null;
        }
        if (!table.origin.getWorld().equals(player.getWorld())
                || table.center(1.0).distanceSquared(player.getLocation()) > 10 * 10) {
            return null;
        }
        return table;
    }

    private static int pocketFor(int number) {
        if (number == 0) {
            return 0;
        }
        boolean red = CasinoGames.isRed(number);
        // odd pockets are red, even (non-zero) pockets are black
        int pick = ThreadLocalRandom.current().nextInt(POCKETS / 2 - (red ? 0 : 1));
        return red ? 1 + pick * 2 : 2 + pick * 2;
    }

    private static Material pocketMaterial(int pocket) {
        if (pocket == 0) {
            return Material.LIME_CONCRETE;
        }
        return pocket % 2 == 1 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }

    private static float pocketAngle(int pocket) {
        return (float) (pocket * Math.PI * 2 / POCKETS);
    }

    // ── build ───────────────────────────────────────────────────────────────

    private static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        String id = CasinoProps.newId();
        float yaw = CasinoProps.yawOf(front);

        // Stand
        box(world, origin, front, yaw, id, Material.BLACK_CONCRETE, 0, 0.20, 0, 0.78f, 0.36f, 0.78f);
        box(world, origin, front, yaw, id, Material.GOLD_BLOCK, 0, 0.42, 0, 0.90f, 0.10f, 0.90f);
        box(world, origin, front, yaw, id, Material.DARK_OAK_PLANKS, 0, 0.565, 0, 0.30f, 0.20f, 0.30f);
        // Table: dark wood skirt, felt, gold edge
        box(world, origin, front, yaw, id, Material.DARK_OAK_PLANKS, 0, 0.645, 0, 1.09f, 0.05f, 1.09f);
        box(world, origin, front, yaw, id, Material.GREEN_WOOL, 0, 0.72, 0, 1.05f, 0.12f, 1.05f);
        box(world, origin, front, yaw, id, Material.GOLD_BLOCK, 0, 0.79, 0.53, 1.10f, 0.03f, 0.04f);
        box(world, origin, front, yaw, id, Material.GOLD_BLOCK, 0, 0.79, -0.53, 1.10f, 0.03f, 0.04f);
        box(world, origin, front, yaw, id, Material.GOLD_BLOCK, 0.53, 0.79, 0, 0.04f, 0.03f, 1.10f);
        box(world, origin, front, yaw, id, Material.GOLD_BLOCK, -0.53, 0.79, 0, 0.04f, 0.03f, 1.10f);
        // Chip stacks on the felt
        chips(world, origin, front, yaw, id, -0.37, 0.37,
                Material.RED_CONCRETE, Material.WHITE_CONCRETE, Material.RED_CONCRETE);
        chips(world, origin, front, yaw, id, 0.37, -0.37,
                Material.BLACK_CONCRETE, Material.YELLOW_CONCRETE, Material.BLACK_CONCRETE);
        chips(world, origin, front, yaw, id, 0.38, 0.36,
                Material.BLUE_CONCRETE, Material.WHITE_CONCRETE);

        // Wheel bowl (three turned squares read as round) + gold rim ring
        Location bowlAt = CasinoProps.piece(origin, front, 0, 0.80, 0);
        for (int i = 0; i < 3; i++) {
            float turn = (float) (i * Math.PI / 6);
            CasinoProps.box(world, bowlAt, yaw, Material.DARK_OAK_PLANKS,
                    CasinoProps.orbit(0.66f, 0.04f, 0.66f, 0f, 0f, turn), e -> tag(e, front, id, null));
        }
        Location rimAt = CasinoProps.piece(origin, front, 0, 0.83, 0);
        for (int i = 0; i < 16; i++) {
            float turn = (float) (i * Math.PI * 2 / 16);
            CasinoProps.box(world, rimAt, yaw, Material.GOLD_BLOCK,
                    CasinoProps.orbit(0.09f, 0.05f, 0.175f, 0.425f, 0f, turn), e -> tag(e, front, id, null));
        }

        // Wheel head (turns): pockets, cone, cross
        Location wheelAt = CasinoProps.piece(origin, front, 0, WHEEL_Y, 0);
        for (int i = 0; i < POCKETS; i++) {
            int pocket = i;
            CasinoProps.box(world, wheelAt, yaw, pocketMaterial(i), pocketShape(pocket, 0f),
                    e -> tag(e, front, id, "pocket_" + pocket));
        }
        CasinoProps.box(world, wheelAt, yaw, Material.GOLD_BLOCK, coneShape(0f), e -> tag(e, front, id, "cone"));
        CasinoProps.box(world, wheelAt, yaw, Material.GOLD_BLOCK, crossShape(0f), e -> tag(e, front, id, "cross"));
        CasinoProps.box(world, wheelAt, yaw, Material.GOLD_BLOCK,
                CasinoProps.orbit(0.045f, 0.10f, 0.045f, 0f, 0.07f, 0f), e -> tag(e, front, id, null));
        // Ball (rests in the green pocket)
        CasinoProps.box(world, wheelAt, yaw, Material.SMOOTH_QUARTZ,
                ballShape(POCKET_RADIUS, BALL_Y_POCKET, pocketAngle(0)), e -> tag(e, front, id, "ball"));

        // Click box
        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.15f);
            spawned.setInteractionHeight(1.55f);
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.customName(null);
            spawned.setCustomNameVisible(false);
            tag(spawned, front, id, "hit");
        });

        // Sign + results board
        CasinoProps.label(world, CasinoProps.piece(origin, front, 0, 1.62, 0.0),
                labelText(0, Mode.IDLE), 1.0f, e -> tag(e, front, id, "label"));
        TextDisplay board = CasinoProps.label(world, CasinoProps.piece(origin, front, 0, 1.36, 0.0),
                boardText(new ArrayDeque<>()), 0.6f, e -> tag(e, front, id, "board"));
        board.setBackgroundColor(BOARD_BG);
        board.setShadowed(false);

        CasinoProps.light(origin.getBlock().getRelative(BlockFace.UP), 10);
    }

    private static void box(
            World world, Location origin, BlockFace front, float yaw, String id,
            Material material, double right, double y, double alongFront, float w, float h, float d
    ) {
        CasinoProps.box(world, CasinoProps.piece(origin, front, right, y, alongFront), yaw, material,
                CasinoProps.centered(w, h, d), e -> tag(e, front, id, null));
    }

    private static void chips(
            World world, Location origin, BlockFace front, float yaw, String id,
            double right, double alongFront, Material... layers
    ) {
        for (int i = 0; i < layers.length; i++) {
            box(world, origin, front, yaw, id, layers[i], right, 0.791 + i * 0.022, alongFront, 0.10f, 0.02f, 0.10f);
        }
    }

    private static Transformation pocketShape(int pocket, float wheel) {
        return CasinoProps.orbit(0.10f, 0.03f, 0.095f, POCKET_RADIUS, 0f, wheel + pocketAngle(pocket));
    }

    private static Transformation coneShape(float wheel) {
        return CasinoProps.orbit(0.12f, 0.05f, 0.12f, 0f, 0.015f, wheel);
    }

    private static Transformation crossShape(float wheel) {
        return CasinoProps.orbit(0.26f, 0.02f, 0.025f, 0f, 0.09f, wheel);
    }

    private static Transformation ballShape(float radius, float y, float angle) {
        return CasinoProps.orbit(BALL, BALL, BALL, radius, y, angle);
    }

    private static void tag(Entity entity, BlockFace front, String id, String part) {
        entity.getPersistentDataContainer().set(ItemKeys.rouletteCabinet(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.rouletteCabinetFacing(), PersistentDataType.STRING, front.name());
        CasinoProps.mark(entity, id, part);
        register(entity);
    }

    // ── registry ────────────────────────────────────────────────────────────

    private static void register(Entity entity) {
        if (!isCabinet(entity)) {
            return;
        }
        String id = CasinoProps.id(entity);
        String part = CasinoProps.part(entity);
        if (id == null || part == null) {
            return;
        }
        Table table = TABLES.computeIfAbsent(id, Table::new);
        UUID uuid = entity.getUniqueId();
        switch (part) {
            case "hit" -> {
                table.hit = uuid;
                table.origin = entity.getLocation().getBlock().getLocation();
            }
            case "label" -> table.label = uuid;
            case "board" -> table.board = uuid;
            case "cone" -> table.cone = uuid;
            case "cross" -> table.cross = uuid;
            case "ball" -> table.ball = uuid;
            default -> {
                if (part.startsWith("pocket_")) {
                    try {
                        int index = Integer.parseInt(part.substring(7));
                        if (index >= 0 && index < POCKETS) {
                            table.pockets[index] = uuid;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private static void rescan() {
        List<Interaction> legacy = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Interaction hit : world.getEntitiesByClass(Interaction.class)) {
                if (!isCabinet(hit)) {
                    continue;
                }
                if (hit.customName() != null || hit.isCustomNameVisible()) {
                    hit.customName(null);
                    hit.setCustomNameVisible(false);
                }
                if (CasinoProps.id(hit) == null) {
                    legacy.add(hit);
                } else {
                    register(hit);
                }
            }
            for (Display display : world.getEntitiesByClass(Display.class)) {
                register(display);
            }
        }
        upgradeLegacy(legacy);
        TABLES.values().removeIf(table -> {
            if (table.origin == null || table.origin.getWorld() == null) {
                return table.hit == null;
            }
            World world = table.origin.getWorld();
            boolean loaded = world.isChunkLoaded(table.origin.getBlockX() >> 4, table.origin.getBlockZ() >> 4);
            return loaded && CasinoProps.entity(table.hit) == null;
        });
    }

    /** Old tables (no id) are torn down together first, then rebuilt in the new look. */
    private static void upgradeLegacy(List<Interaction> hits) {
        if (hits.isEmpty()) {
            return;
        }
        List<Location> origins = new ArrayList<>();
        List<BlockFace> fronts = new ArrayList<>();
        for (Interaction hit : hits) {
            origins.add(hit.getLocation().getBlock().getLocation());
            fronts.add(CasinoProps.face(hit.getPersistentDataContainer()
                    .get(ItemKeys.rouletteCabinetFacing(), PersistentDataType.STRING)));
        }
        for (Interaction hit : hits) {
            for (Entity entity : hit.getWorld().getNearbyEntities(hit.getLocation(), 1.55, 2.9, 1.55)) {
                if (isCabinet(entity) && CasinoProps.id(entity) == null) {
                    entity.remove();
                }
            }
        }
        for (int i = 0; i < origins.size(); i++) {
            build(origins.get(i), fronts.get(i));
        }
    }

    // ── animation ───────────────────────────────────────────────────────────

    private static void tick() {
        int now = Bukkit.getCurrentTick();
        for (Table table : TABLES.values()) {
            if (table.origin == null) {
                continue;
            }
            Location center = table.center(1.0);
            if (center.getWorld() == null || !CasinoProps.anyoneNear(center, 40)) {
                continue;
            }
            if (table.mode != Mode.IDLE && now >= table.modeUntil) {
                table.set(Mode.IDLE, 0);
                table.light(10);
            }
            boolean spinning = table.mode == Mode.SPIN && now - table.spinStart <= table.spinTicks;
            if (spinning) {
                spinFrame(table, now);
            } else if (table.spinPending) {
                landBall(table);
            } else if (now % 2 == 0) {
                idleFrame(table);
            }
            if (now % 2 == 0) {
                animateLabel(table, now);
                if (table.boardDirty && CasinoProps.entity(table.board) instanceof TextDisplay board) {
                    board.text(boardText(table.history));
                    table.boardDirty = false;
                }
            }
        }
    }

    private static void idleFrame(Table table) {
        table.wheelAngle = wrap(table.wheelAngle + IDLE_SPEED * 2);
        turnWheel(table, 2);
        if (CasinoProps.entity(table.ball) instanceof Display ball) {
            CasinoProps.animate(ball, ballShape(POCKET_RADIUS, BALL_Y_POCKET,
                    table.wheelAngle + pocketAngle(table.restPocket)), 2);
        }
    }

    private static void spinFrame(Table table, int now) {
        float u = Math.min(1f, (now - table.spinStart) / (float) table.spinTicks);
        float ease = 1f - (1f - u) * (1f - u);
        float wheelTravel = (float) (Math.PI * 3);
        float ballTravel = (float) (Math.PI * 6);
        float wheelEnd = table.wheelFrom + wheelTravel;
        table.wheelAngle = table.wheelFrom + wheelTravel * ease;
        float ballEnd = wheelEnd + pocketAngle(table.targetPocket);
        float ballAngle = ballEnd + ballTravel * (1f - ease);

        float radius = TRACK_RADIUS;
        float y = BALL_Y_TRACK;
        if (u > 0.72f) {
            float k = (u - 0.72f) / 0.28f;
            radius = TRACK_RADIUS + (POCKET_RADIUS - TRACK_RADIUS) * k;
            y = BALL_Y_TRACK + (BALL_Y_POCKET - BALL_Y_TRACK) * k
                    + 0.035f * (float) Math.abs(Math.sin(k * Math.PI * 2.5)) * (1f - k);
        }
        turnWheel(table, 1);
        if (CasinoProps.entity(table.ball) instanceof Display ball) {
            CasinoProps.animate(ball, ballShape(radius, y, ballAngle), 1);
        }
        int sector = (int) Math.floor(ballAngle / (Math.PI * 2 / POCKETS));
        if (sector != table.lastBallSector) {
            boolean first = table.lastBallSector == Integer.MIN_VALUE;
            table.lastBallSector = sector;
            if (!first) {
                Location at = table.center(0.9);
                for (Player near : CasinoProps.viewers(at, 10)) {
                    if (!near.getUniqueId().equals(table.spinner)) {
                        near.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.12f, 1.6f);
                    }
                }
            }
        }
        if (u >= 1f) {
            landBall(table);
        }
    }

    private static void landBall(Table table) {
        table.spinPending = false;
        table.wheelAngle = wrap(table.wheelFrom + (float) (Math.PI * 3));
        table.wheelFrom = table.wheelAngle;
        table.restPocket = table.targetPocket;
    }

    private static void turnWheel(Table table, int ticks) {
        for (int i = 0; i < POCKETS; i++) {
            if (CasinoProps.entity(table.pockets[i]) instanceof BlockDisplay pocket) {
                CasinoProps.animate(pocket, pocketShape(i, table.wheelAngle), ticks);
            }
        }
        if (CasinoProps.entity(table.cone) instanceof Display cone) {
            CasinoProps.animate(cone, coneShape(table.wheelAngle), ticks);
        }
        if (CasinoProps.entity(table.cross) instanceof Display cross) {
            CasinoProps.animate(cross, crossShape(table.wheelAngle), ticks);
        }
    }

    private static float wrap(float angle) {
        float full = (float) (Math.PI * 2);
        return ((angle % full) + full) % full;
    }

    private static void animateLabel(Table table, int now) {
        if (!(CasinoProps.entity(table.label) instanceof TextDisplay label)) {
            return;
        }
        label.text(labelText(now / 2, table.mode));
    }

    private static Component labelText(int phase, Mode mode) {
        return switch (mode) {
            case ZERO -> CasinoProps.shimmer("ZERO!", phase * 2,
                    NamedTextColor.GREEN, NamedTextColor.WHITE, NamedTextColor.WHITE);
            case WIN -> CasinoProps.shimmer("WINNER", phase * 2,
                    NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.WHITE);
            case LOSE -> Component.text("ROULETTE", TextColor.color(0x7A1E1E), TextDecoration.BOLD);
            default -> CasinoProps.shimmer("ROULETTE", phase,
                    NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.WHITE);
        };
    }

    private static Component boardText(Deque<Integer> history) {
        if (history.isEmpty()) {
            return Component.text("· · ·", NamedTextColor.GRAY);
        }
        TextComponent.Builder out = Component.text();
        boolean first = true;
        for (int number : history) {
            if (!first) {
                out.append(Component.text("  "));
            }
            NamedTextColor color = number == 0 ? NamedTextColor.GREEN
                    : CasinoGames.isRed(number) ? NamedTextColor.RED : NamedTextColor.WHITE;
            String label = String.valueOf(number);
            out.append(first ? Component.text(label, color, TextDecoration.BOLD) : Component.text(label, color));
            first = false;
        }
        return out.build();
    }

    private enum Mode {
        IDLE, SPIN, WIN, ZERO, LOSE
    }

    private static final class Table {
        final String id;
        UUID hit;
        UUID label;
        UUID board;
        UUID cone;
        UUID cross;
        UUID ball;
        final UUID[] pockets = new UUID[POCKETS];
        Location origin;

        Mode mode = Mode.IDLE;
        int modeUntil;
        float wheelAngle;
        float wheelFrom;
        int spinStart;
        int spinTicks = 1;
        int targetPocket;
        int restPocket;
        int lastBallSector = Integer.MIN_VALUE;
        boolean spinPending;
        UUID spinner;
        final Deque<Integer> history = new ArrayDeque<>();
        boolean boardDirty;
        int lightLevel = 10;

        Table(String id) {
            this.id = id;
        }

        void set(Mode next, int ticks) {
            mode = next;
            modeUntil = Bukkit.getCurrentTick() + ticks;
        }

        Location center(double y) {
            return origin.clone().add(0.5, y, 0.5);
        }

        void light(int level) {
            if (origin == null || level == lightLevel) {
                return;
            }
            lightLevel = level;
            CasinoProps.light(origin.getBlock().getRelative(BlockFace.UP), level);
        }
    }

    // ── legacy ──────────────────────────────────────────────────────────────

    private static void removeCluster(Location at, double xz, double y) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(at, xz, y, xz)) {
            if (isCabinet(entity)) {
                if (entity instanceof Interaction) {
                    Block base = entity.getLocation().getBlock();
                    CasinoProps.unlight(base.getRelative(BlockFace.UP));
                }
                entity.remove();
            }
        }
    }
}
