package de.aetherion.items.casino;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DEV prop: a ~1×2 furniture slot machine (display entities + click hitbox).
 * Not real world blocks — same idea as Melody's harp. The only block it ever sets
 * is one invisible light block so the machine actually lights the floor around it.
 *
 * <p>The machine mirrors whoever is playing it: lever pull, live reels, marquee bulbs,
 * front screen and a coin pop on wins. Idle, it chases its bulbs and spins its star.
 */
public final class CasinoCabinet implements Listener {

    static final int TIER_LOSE = 0;
    static final int TIER_WIN = 1;
    static final int TIER_BIG = 2;
    static final int TIER_JACKPOT = 3;
    static final int TIER_FREE = 4;

    private static final int BULBS = 8;
    private static final int REELS = 3;
    private static final float LEVER_REST = -0.16f;
    private static final float LEVER_PULLED = 1.25f;
    private static final Color SCREEN_BG = Color.fromARGB(235, 14, 10, 20);
    private static final Material[] REEL_BLUR = {
            Material.SWEET_BERRIES, Material.GOLD_INGOT, Material.EMERALD, Material.DIAMOND,
            Material.NETHERITE_INGOT, Material.NETHER_STAR, Material.ENCHANTED_GOLDEN_APPLE, Material.BOOK
    };
    private static final Material[] JACKPOT_BULBS = {
            Material.OCHRE_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT
    };

    private static final Map<String, Machine> MACHINES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SEATS = new ConcurrentHashMap<>();

    public CasinoCabinet() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, CasinoCabinet::tick, 20L, 2L);
            plugin.getServer().getScheduler().runTaskTimer(plugin, CasinoCabinet::rescan, 30L, 200L);
        }
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.JUKEBOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lSlot Machine");
            meta.setLore(List.of(
                    "§7DEV · a 1×2 slot machine prop.",
                    "§7Lights up. Reels spin when people play.",
                    "",
                    "§eRight-click a block §7to place it.",
                    "§eSneak + right-click §7to pack it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ItemKeys.slotCabinetAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.slotCabinetAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isCabinet(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.slotCabinet(), PersistentDataType.BYTE);
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
                player.sendMessage("§cNo slot machine nearby.");
            }
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the machine.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = CasinoProps.look(player).getOppositeFace();
        if (!CasinoProps.fits(origin.getBlock())) {
            player.sendMessage("§cNeed 1×2 air where it stands.");
            return;
        }
        build(origin, front);
        Location at = origin.clone().add(0.5, 1.0, 0.5);
        player.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.35f, 1.35f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.4f);
        player.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.8f);
        at.getWorld().spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.6, 0), 24, 0.35, 0.6, 0.35, 0.4);
        player.sendMessage("§aSlot machine is up. Click it to play.");
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
            player.sendMessage("§ePacked up the slot machine.");
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(player.getInventory().getItemInMainHand())) {
            return;
        }
        openSlots(player, event.getRightClicked());
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
        openSlots(player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseLegacy(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isAnchor(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (de.aetherion.items.world.NpcRemoverListener.isRemover(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        ArmorStand cabinet = legacyAt(event.getClickedBlock());
        if (cabinet == null) {
            return;
        }
        event.setCancelled(true);
        openSlots(event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (legacyAt(event.getBlock()) == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isOp() || event.getPlayer().hasPermission("aetherion.dev")) {
            event.getPlayer().sendMessage("§7Sneak + slot-machine item to pack it up.");
        }
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
        Location at = entity.getLocation();
        if (entity instanceof ArmorStand stand) {
            clearLegacy(stand);
            removeCluster(at, 8.0, 8.0);
            return true;
        }
        String id = CasinoProps.id(entity);
        if (id != null) {
            removeMachine(id, at);
            return true;
        }
        removeCluster(at, 1.45, 2.8);
        return true;
    }

    private static void removeMachine(String id, Location near) {
        World world = near.getWorld();
        Machine machine = MACHINES.remove(id);
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(near, 3.0, 4.0, 3.0)) {
            if (!isCabinet(entity) || !id.equals(CasinoProps.id(entity))) {
                continue;
            }
            if (entity instanceof Interaction) {
                Block base = entity.getLocation().getBlock();
                CasinoProps.unlight(base);
                CasinoProps.unlight(base.getRelative(BlockFace.UP));
            }
            entity.remove();
        }
        if (machine != null && machine.origin != null) {
            CasinoProps.unlight(machine.origin.getBlock().getRelative(BlockFace.UP));
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
        player.sendMessage("§ePacked up the slot machine.");
        return true;
    }

    // ── play hooks (called by the slots GUI) ────────────────────────────────

    private static void openSlots(Player player, Entity clicked) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCasino() == null) {
            return;
        }
        String id = CasinoProps.id(clicked);
        Machine machine = id == null ? null : MACHINES.get(id);
        if (machine != null) {
            SEATS.put(player.getUniqueId(), id);
            if (machine.mode == Mode.IDLE) {
                machine.set(Mode.SEATED, 60);
            }
            nudgeLever(machine);
        } else {
            SEATS.remove(player.getUniqueId());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 1.25f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.9f);
        plugin.getCasino().open(player, "slots");
    }

    /** GUI pulled the lever: mirror it on the machine the player sits at. */
    static void machineSpin(Player player, int ticks, Material[] target) {
        Machine machine = seated(player);
        if (machine == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        machine.set(Mode.SPIN, ticks + 60);
        machine.spinStart = now;
        machine.target = target == null || target.length < REELS
                ? new Material[]{Material.SWEET_BERRIES, Material.GOLD_INGOT, Material.EMERALD}
                : target;
        machine.stopped = new boolean[REELS];
        machine.stopAt = new int[REELS];
        for (int i = 0; i < REELS; i++) {
            machine.stopAt[i] = now + Math.max(6, (ticks * (i + 1)) / REELS - 2);
        }
        pullLever(machine);
        Location at = machine.center(1.1);
        for (Player near : CasinoProps.viewers(at, 14)) {
            if (near.equals(player)) {
                continue;
            }
            near.playSound(at, Sound.BLOCK_PISTON_CONTRACT, 0.35f, 1.5f);
            near.playSound(at, Sound.BLOCK_LEVER_CLICK, 0.5f, 0.8f);
        }
    }

    /** GUI settled: light the machine up (or let it sigh). */
    static void machineResult(Player player, int tier, long win) {
        Machine machine = seated(player);
        if (machine == null) {
            return;
        }
        machine.win = win;
        settleReels(machine);
        Location top = machine.center(1.95);
        Location tray = machine.front(0, 0.70, 0.26);
        World world = top.getWorld();
        if (world == null) {
            return;
        }
        List<Player> near = CasinoProps.viewers(top, 16);
        near.remove(player);
        switch (tier) {
            case TIER_JACKPOT -> {
                machine.set(Mode.JACKPOT, 120);
                machine.light(15);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, top, 50, 0.35, 0.4, 0.35, 0.35);
                world.spawnParticle(Particle.ITEM, tray, 40, 0.12, 0.08, 0.12, 0.16, new ItemStack(Material.GOLD_NUGGET));
                world.spawnParticle(Particle.ITEM, tray, 16, 0.12, 0.08, 0.12, 0.14, new ItemStack(Material.GOLD_INGOT));
                for (Player other : near) {
                    other.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.1f);
                    other.playSound(top, Sound.BLOCK_BELL_USE, 0.8f, 1.2f);
                }
            }
            case TIER_BIG -> {
                machine.set(Mode.BIG, 80);
                machine.light(14);
                world.spawnParticle(Particle.WAX_ON, top, 30, 0.35, 0.3, 0.35, 0.5);
                world.spawnParticle(Particle.ITEM, tray, 24, 0.12, 0.08, 0.12, 0.14, new ItemStack(Material.GOLD_NUGGET));
                for (Player other : near) {
                    other.playSound(top, Sound.BLOCK_BELL_USE, 0.6f, 1.45f);
                    other.playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.3f);
                }
            }
            case TIER_FREE -> {
                machine.set(Mode.FREE, 90);
                machine.light(13);
                world.spawnParticle(Particle.ENCHANT, top, 60, 0.4, 0.5, 0.4, 0.9);
                world.spawnParticle(Particle.WITCH, top, 14, 0.3, 0.3, 0.3, 0.05);
                for (Player other : near) {
                    other.playSound(top, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.2f);
                }
            }
            case TIER_WIN -> {
                machine.set(Mode.WIN, 50);
                machine.light(13);
                world.spawnParticle(Particle.ITEM, tray, 10, 0.1, 0.06, 0.1, 0.11, new ItemStack(Material.GOLD_NUGGET));
                world.spawnParticle(Particle.WAX_ON, top, 10, 0.3, 0.2, 0.3, 0.3);
                for (Player other : near) {
                    other.playSound(top, Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.6f);
                }
            }
            default -> {
                machine.set(Mode.LOSE, 26);
                machine.light(7);
                for (Player other : near) {
                    other.playSound(top, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.6f);
                }
            }
        }
    }

    private static Machine seated(Player player) {
        if (player == null) {
            return null;
        }
        String id = SEATS.get(player.getUniqueId());
        Machine machine = id == null ? null : MACHINES.get(id);
        if (machine == null || machine.origin == null || machine.origin.getWorld() == null) {
            return null;
        }
        if (!machine.origin.getWorld().equals(player.getWorld())
                || machine.center(1.0).distanceSquared(player.getLocation()) > 10 * 10) {
            return null;
        }
        return machine;
    }

    // ── build ───────────────────────────────────────────────────────────────

    private static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        String id = CasinoProps.newId();
        float yaw = CasinoProps.yawOf(front);

        // Body
        box(world, origin, front, yaw, id, null, Material.BLACK_CONCRETE, 0, 0.22, 0, 0.70f, 0.40f, 0.40f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 0.52, 0, 0.94f, 0.12f, 0.44f);
        box(world, origin, front, yaw, id, null, Material.BLACK_CONCRETE, 0, 1.05, -0.02, 0.88f, 1.08f, 0.36f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 1.64, 0, 0.96f, 0.14f, 0.44f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 1.84, 0, 0.62f, 0.12f, 0.34f);
        box(world, origin, front, yaw, id, null, Material.GLOWSTONE, 0, 1.94, 0, 0.22f, 0.10f, 0.22f);
        // Neon side trims
        box(world, origin, front, yaw, id, null, Material.RED_CONCRETE, -0.425, 1.05, 0.165, 0.03f, 1.00f, 0.03f);
        box(world, origin, front, yaw, id, null, Material.RED_CONCRETE, 0.425, 1.05, 0.165, 0.03f, 1.00f, 0.03f);
        // Reel bay: white strip, gold frame, gold dividers
        box(world, origin, front, yaw, id, null, Material.SMOOTH_QUARTZ, 0, 1.14, 0.17, 0.70f, 0.40f, 0.02f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 1.355, 0.18, 0.78f, 0.03f, 0.04f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 0.925, 0.18, 0.78f, 0.03f, 0.04f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, -0.375, 1.14, 0.18, 0.03f, 0.46f, 0.04f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0.375, 1.14, 0.18, 0.03f, 0.46f, 0.04f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, -0.12, 1.14, 0.185, 0.015f, 0.40f, 0.02f);
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0.12, 1.14, 0.185, 0.015f, 0.40f, 0.02f);
        // Coin tray lip
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0, 0.66, 0.19, 0.46f, 0.04f, 0.05f);
        box(world, origin, front, yaw, id, null, Material.POLISHED_BLACKSTONE, 0, 0.635, 0.17, 0.42f, 0.02f, 0.05f);
        // Lever mount
        box(world, origin, front, yaw, id, null, Material.GOLD_BLOCK, 0.48, 0.98, 0, 0.08f, 0.12f, 0.14f);

        // Reels
        Material[] idle = {Material.ENCHANTED_GOLDEN_APPLE, Material.NETHER_STAR, Material.ENCHANTED_GOLDEN_APPLE};
        for (int i = 0; i < REELS; i++) {
            String part = "reel_" + i;
            CasinoProps.item(world, CasinoProps.piece(origin, front, -0.24 + 0.24 * i, 1.14, 0.19), yaw,
                    new ItemStack(idle[i]), 0.19f, e -> tag(e, front, id, part));
        }
        // Marquee bulbs
        for (int i = 0; i < BULBS; i++) {
            double right = i < 5 ? -0.36 + 0.18 * i : -0.2 + 0.2 * (i - 5);
            double y = i < 5 ? 1.64 : 1.84;
            double out = i < 5 ? 0.225 : 0.175;
            String part = "bulb_" + i;
            CasinoProps.box(world, CasinoProps.piece(origin, front, right, y, out), yaw, Material.OCHRE_FROGLIGHT,
                    CasinoProps.centered(0.06f, 0.06f, 0.06f), e -> tag(e, front, id, part));
        }
        // Lever (chrome arm + red ball), pivoting on the mount
        Location pivot = CasinoProps.piece(origin, front, 0.53, 0.98, 0);
        CasinoProps.box(world, pivot, yaw, Material.IRON_BLOCK, leverArm(LEVER_REST), e -> tag(e, front, id, "lever_arm"));
        CasinoProps.box(world, pivot, yaw, Material.RED_CONCRETE, leverKnob(LEVER_REST), e -> tag(e, front, id, "lever_knob"));
        // Spinning star on top
        CasinoProps.item(world, CasinoProps.piece(origin, front, 0, 2.17, 0), yaw,
                new ItemStack(Material.NETHER_STAR), 0.36f, e -> tag(e, front, id, "topper"));
        // Front screen
        CasinoProps.screen(world, CasinoProps.piece(origin, front, 0, 1.395, 0.163), yaw,
                Component.text("PULL ME", NamedTextColor.GOLD, TextDecoration.BOLD), 0.42f, SCREEN_BG,
                e -> tag(e, front, id, "screen"));
        // Floating sign
        CasinoProps.label(world, CasinoProps.piece(origin, front, 0, 2.42, 0.05),
                labelText(0, Mode.IDLE), 1.0f, e -> tag(e, front, id, "label"));

        // Click box
        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.05f);
            spawned.setInteractionHeight(2.05f);
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.customName(null);
            spawned.setCustomNameVisible(false);
            tag(spawned, front, id, "hit");
        });

        CasinoProps.light(origin.getBlock().getRelative(BlockFace.UP), 10);
    }

    private static void box(
            World world, Location origin, BlockFace front, float yaw, String id, String part,
            Material material, double right, double y, double alongFront, float w, float h, float d
    ) {
        CasinoProps.box(world, CasinoProps.piece(origin, front, right, y, alongFront), yaw, material,
                CasinoProps.centered(w, h, d), e -> tag(e, front, id, part));
    }

    private static org.bukkit.util.Transformation leverArm(float angle) {
        return CasinoProps.tilted(0.045f, 0.40f, 0.045f, 0f, angle);
    }

    private static org.bukkit.util.Transformation leverKnob(float angle) {
        return CasinoProps.tilted(0.11f, 0.11f, 0.11f, 0.37f, angle);
    }

    private static void tag(Entity entity, BlockFace front, String id, String part) {
        entity.getPersistentDataContainer().set(ItemKeys.slotCabinet(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING, front.name());
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
        Machine machine = MACHINES.computeIfAbsent(id, Machine::new);
        UUID uuid = entity.getUniqueId();
        switch (part) {
            case "hit" -> {
                machine.hit = uuid;
                machine.origin = entity.getLocation().getBlock().getLocation();
                machine.front = CasinoProps.face(entity.getPersistentDataContainer()
                        .get(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING));
            }
            case "label" -> machine.label = uuid;
            case "screen" -> machine.screen = uuid;
            case "topper" -> machine.topper = uuid;
            case "lever_arm" -> machine.leverArm = uuid;
            case "lever_knob" -> machine.leverKnob = uuid;
            default -> {
                if (part.startsWith("reel_")) {
                    int index = parseIndex(part, REELS);
                    if (index >= 0) {
                        machine.reels[index] = uuid;
                    }
                } else if (part.startsWith("bulb_")) {
                    int index = parseIndex(part, BULBS);
                    if (index >= 0) {
                        machine.bulbs[index] = uuid;
                    }
                }
            }
        }
    }

    private static int parseIndex(String part, int max) {
        try {
            int index = Integer.parseInt(part.substring(part.indexOf('_') + 1));
            return index >= 0 && index < max ? index : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Picks up loaded machines, drops removed ones, and rebuilds old-style machines in the new look. */
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
        MACHINES.values().removeIf(machine -> {
            if (machine.origin == null || machine.origin.getWorld() == null) {
                return machine.hit == null;
            }
            World world = machine.origin.getWorld();
            boolean loaded = world.isChunkLoaded(machine.origin.getBlockX() >> 4, machine.origin.getBlockZ() >> 4);
            return loaded && CasinoProps.entity(machine.hit) == null;
        });
    }

    /** Old machines (no id) get torn down together first, then rebuilt — neighbours can't eat each other. */
    private static void upgradeLegacy(List<Interaction> hits) {
        if (hits.isEmpty()) {
            return;
        }
        List<Location> origins = new ArrayList<>();
        List<BlockFace> fronts = new ArrayList<>();
        for (Interaction hit : hits) {
            origins.add(hit.getLocation().getBlock().getLocation());
            fronts.add(CasinoProps.face(hit.getPersistentDataContainer()
                    .get(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING)));
        }
        for (Interaction hit : hits) {
            World world = hit.getWorld();
            for (Entity entity : world.getNearbyEntities(hit.getLocation(), 1.45, 2.8, 1.45)) {
                if (isCabinet(entity) && !(entity instanceof ArmorStand) && CasinoProps.id(entity) == null) {
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
        for (Machine machine : MACHINES.values()) {
            if (machine.origin == null) {
                continue;
            }
            Location center = machine.center(1.0);
            if (center.getWorld() == null || !CasinoProps.anyoneNear(center, 40)) {
                continue;
            }
            if (machine.mode != Mode.IDLE && now >= machine.modeUntil) {
                machine.set(Mode.IDLE, 0);
                machine.light(10);
            }
            animateReels(machine, now);
            animateBulbs(machine, now);
            animateLabel(machine, now);
            animateScreen(machine, now);
            animateTopper(machine, now);
        }
    }

    private static void animateReels(Machine machine, int now) {
        for (int i = 0; i < REELS; i++) {
            if (machine.bounce[i] && CasinoProps.entity(machine.reels[i]) instanceof ItemDisplay reel) {
                CasinoProps.animate(reel, CasinoProps.spun(0.19f, 0f), 3);
            }
            machine.bounce[i] = false;
        }
        if (machine.mode != Mode.SPIN || machine.stopAt == null) {
            return;
        }
        Location at = machine.center(1.14);
        for (int i = 0; i < REELS; i++) {
            if (machine.stopped[i]) {
                continue;
            }
            if (!(CasinoProps.entity(machine.reels[i]) instanceof ItemDisplay reel)) {
                continue;
            }
            if (now >= machine.stopAt[i]) {
                machine.stopped[i] = true;
                reel.setItemStack(new ItemStack(machine.target[i]));
                CasinoProps.animate(reel, CasinoProps.spun(0.235f, 0f), 0);
                machine.bounce[i] = true;
                for (Player near : CasinoProps.viewers(at, 12)) {
                    near.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 0.18f, 0.9f + i * 0.25f);
                }
                continue;
            }
            Material blur = REEL_BLUR[ThreadLocalRandom.current().nextInt(REEL_BLUR.length)];
            reel.setItemStack(new ItemStack(blur));
        }
    }

    private static void settleReels(Machine machine) {
        if (machine.stopped == null || machine.target == null) {
            return;
        }
        for (int i = 0; i < REELS; i++) {
            if (machine.stopped[i]) {
                continue;
            }
            machine.stopped[i] = true;
            if (CasinoProps.entity(machine.reels[i]) instanceof ItemDisplay reel) {
                reel.setItemStack(new ItemStack(machine.target[i]));
            }
        }
    }

    private static void animateBulbs(Machine machine, int now) {
        for (int i = 0; i < BULBS; i++) {
            boolean lit;
            Material material = Material.OCHRE_FROGLIGHT;
            switch (machine.mode) {
                case SPIN -> lit = (i + now / 2) % 3 == 0;
                case WIN -> lit = (now / 4) % 2 == 0;
                case BIG -> lit = (i % 2 == 0) == ((now / 2) % 2 == 0);
                case FREE -> {
                    lit = (i + now / 2) % 2 == 0;
                    material = Material.PEARLESCENT_FROGLIGHT;
                }
                case JACKPOT -> {
                    lit = true;
                    material = JACKPOT_BULBS[Math.floorMod(i + now / 2, JACKPOT_BULBS.length)];
                }
                case LOSE -> lit = false;
                default -> lit = (i + now / 6) % 3 == 0;
            }
            if (machine.bulbLit[i] != null && machine.bulbLit[i] == lit && machine.bulbMaterial[i] == material) {
                continue;
            }
            if (!(CasinoProps.entity(machine.bulbs[i]) instanceof BlockDisplay bulb)) {
                continue;
            }
            if (machine.bulbMaterial[i] != material) {
                bulb.setBlock(material.createBlockData());
            }
            bulb.setBrightness(lit ? CasinoProps.FULL : CasinoProps.DIM);
            machine.bulbLit[i] = lit;
            machine.bulbMaterial[i] = material;
        }
    }

    private static void animateLabel(Machine machine, int now) {
        if (!(CasinoProps.entity(machine.label) instanceof TextDisplay label)) {
            return;
        }
        label.text(labelText(now / 2, machine.mode));
    }

    private static Component labelText(int phase, Mode mode) {
        return switch (mode) {
            case JACKPOT -> CasinoProps.shimmer("JACKPOT", phase * 2,
                    NamedTextColor.YELLOW, NamedTextColor.WHITE, NamedTextColor.WHITE);
            case FREE -> CasinoProps.shimmer("FREE SPINS", phase,
                    NamedTextColor.LIGHT_PURPLE, NamedTextColor.WHITE, NamedTextColor.WHITE);
            case LOSE -> Component.text("SLOTS", TextColor.color(0x9A7A2A), TextDecoration.BOLD);
            default -> CasinoProps.shimmer("SLOTS", phase,
                    NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.WHITE);
        };
    }

    private static void animateScreen(Machine machine, int now) {
        Component text;
        String key;
        switch (machine.mode) {
            case SEATED -> {
                key = "seated";
                text = Component.text("GOOD LUCK", NamedTextColor.YELLOW, TextDecoration.BOLD);
            }
            case SPIN -> {
                int dot = (now / 3) % 3;
                key = "spin" + dot;
                text = Component.text()
                        .append(Component.text("● ", dot == 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                        .append(Component.text("● ", dot == 1 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                        .append(Component.text("●", dot == 2 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                        .build();
            }
            case WIN -> {
                key = "win" + machine.win;
                text = Component.text("+" + CasinoGames.format(machine.win), NamedTextColor.GREEN, TextDecoration.BOLD);
            }
            case BIG -> {
                boolean word = (now / 16) % 2 == 0;
                key = word ? "bigw" : "big" + machine.win;
                text = word
                        ? Component.text("BIG WIN", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        : Component.text("+" + CasinoGames.format(machine.win), NamedTextColor.GREEN, TextDecoration.BOLD);
            }
            case JACKPOT -> {
                int flash = (now / 4) % 3;
                key = "jp" + flash;
                NamedTextColor color = flash == 0 ? NamedTextColor.GOLD : flash == 1 ? NamedTextColor.WHITE : NamedTextColor.YELLOW;
                text = Component.text("JACKPOT", color, TextDecoration.BOLD);
            }
            case FREE -> {
                key = "free";
                text = Component.text("FREE SPINS", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
            }
            case LOSE -> {
                key = "lose";
                text = Component.text("NO LUCK", NamedTextColor.GRAY);
            }
            default -> {
                boolean pull = (now / 50) % 2 == 0;
                key = pull ? "idle" : "idle2";
                text = pull
                        ? Component.text("PULL ME", NamedTextColor.GOLD, TextDecoration.BOLD)
                        : Component.text("★ ★ ★", NamedTextColor.YELLOW);
            }
        }
        if (key.equals(machine.screenKey)) {
            return;
        }
        if (!(CasinoProps.entity(machine.screen) instanceof TextDisplay screen)) {
            return;
        }
        screen.text(text);
        machine.screenKey = key;
    }

    private static void animateTopper(Machine machine, int now) {
        int every = machine.mode == Mode.JACKPOT || machine.mode == Mode.SPIN ? 6 : 20;
        if (now - machine.topperAt < every) {
            return;
        }
        if (!(CasinoProps.entity(machine.topper) instanceof ItemDisplay topper)) {
            return;
        }
        machine.topperAt = now;
        machine.topperAngle += (float) (Math.PI / 2);
        if (machine.topperAngle > Math.PI * 2) {
            machine.topperAngle -= (float) (Math.PI * 2);
        }
        CasinoProps.animate(topper, CasinoProps.spun(0.36f, machine.topperAngle), every);
    }

    private static void pullLever(Machine machine) {
        Entity arm = CasinoProps.entity(machine.leverArm);
        Entity knob = CasinoProps.entity(machine.leverKnob);
        if (!(arm instanceof Display armDisplay) || !(knob instanceof Display knobDisplay)) {
            return;
        }
        CasinoProps.animate(armDisplay, leverArm(LEVER_PULLED), 3);
        CasinoProps.animate(knobDisplay, leverKnob(LEVER_PULLED), 3);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (armDisplay.isValid() && knobDisplay.isValid()) {
                CasinoProps.animate(armDisplay, leverArm(LEVER_REST), 9);
                CasinoProps.animate(knobDisplay, leverKnob(LEVER_REST), 9);
            }
        }, 6L);
    }

    private static void nudgeLever(Machine machine) {
        Entity arm = CasinoProps.entity(machine.leverArm);
        Entity knob = CasinoProps.entity(machine.leverKnob);
        if (!(arm instanceof Display armDisplay) || !(knob instanceof Display knobDisplay)) {
            return;
        }
        CasinoProps.animate(armDisplay, leverArm(0.18f), 2);
        CasinoProps.animate(knobDisplay, leverKnob(0.18f), 2);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (armDisplay.isValid() && knobDisplay.isValid()) {
                CasinoProps.animate(armDisplay, leverArm(LEVER_REST), 5);
                CasinoProps.animate(knobDisplay, leverKnob(LEVER_REST), 5);
            }
        }, 3L);
    }

    private enum Mode {
        IDLE, SEATED, SPIN, WIN, BIG, JACKPOT, FREE, LOSE
    }

    private static final class Machine {
        final String id;
        UUID hit;
        UUID label;
        UUID screen;
        UUID topper;
        UUID leverArm;
        UUID leverKnob;
        final UUID[] reels = new UUID[REELS];
        final boolean[] bounce = new boolean[REELS];
        final UUID[] bulbs = new UUID[BULBS];
        final Boolean[] bulbLit = new Boolean[BULBS];
        final Material[] bulbMaterial = new Material[BULBS];
        Location origin;
        BlockFace front = BlockFace.SOUTH;

        Mode mode = Mode.IDLE;
        int modeUntil;
        long win;
        int spinStart;
        int[] stopAt;
        boolean[] stopped;
        Material[] target;
        String screenKey = "";
        int topperAt;
        float topperAngle;
        int lightLevel = 10;

        Machine(String id) {
            this.id = id;
        }

        void set(Mode next, int ticks) {
            mode = next;
            modeUntil = Bukkit.getCurrentTick() + ticks;
        }

        Location center(double y) {
            return origin.clone().add(0.5, y, 0.5);
        }

        Location front(double right, double y, double alongFront) {
            return CasinoProps.piece(origin, front, right, y, alongFront);
        }

        void light(int level) {
            if (origin == null || level == lightLevel) {
                return;
            }
            lightLevel = level;
            CasinoProps.light(origin.getBlock().getRelative(BlockFace.UP), level);
        }
    }

    // ── legacy (old block-built machines) ───────────────────────────────────

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

    private static void clearLegacy(Entity marker) {
        Block origin = marker.getLocation().getBlock();
        String facingName = marker.getPersistentDataContainer().get(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING);
        BlockFace front = CasinoProps.face(facingName);
        for (int x = -2; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z <= 2; z++) {
                    Block block = local(origin, front, x, y, z);
                    if (ours(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static ArmorStand legacyAt(Block block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 6, 8, 6)) {
            if (!(entity instanceof ArmorStand stand) || !isCabinet(stand)) {
                continue;
            }
            if (legacyInside(stand, block)) {
                return stand;
            }
        }
        return null;
    }

    private static boolean legacyInside(ArmorStand stand, Block block) {
        Block origin = stand.getLocation().getBlock();
        String facingName = stand.getPersistentDataContainer().get(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING);
        BlockFace front = CasinoProps.face(facingName);
        for (int x = -2; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (legacyOccupied(x, y, z) && local(origin, front, x, y, z).equals(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean legacyOccupied(int x, int y, int z) {
        if (x >= -2 && x <= 2 && y >= 0 && y <= 5 && z >= 0 && z <= 2) {
            return true;
        }
        if (x == 3 && y == 2 && z == 0) {
            return true;
        }
        return y == 6 && z == 0 && (x == -2 || x == 0 || x == 2);
    }

    private static Block local(Block origin, BlockFace front, int x, int y, int z) {
        Vector right = CasinoProps.right(front).getDirection();
        Vector back = front.getOppositeFace().getDirection();
        int dx = (int) Math.round(right.getX() * x + back.getX() * z);
        int dz = (int) Math.round(right.getZ() * x + back.getZ() * z);
        return origin.getRelative(dx, y, dz);
    }

    private static boolean ours(Material material) {
        return material == Material.POLISHED_BLACKSTONE
                || material == Material.POLISHED_BLACKSTONE_STAIRS
                || material == Material.GOLD_BLOCK
                || material == Material.BLACK_CONCRETE
                || material == Material.RED_CONCRETE
                || material == Material.SMOOTH_QUARTZ
                || material == Material.GILDED_BLACKSTONE
                || material == Material.HOPPER
                || material == Material.JUKEBOX
                || material == Material.YELLOW_STAINED_GLASS
                || material == Material.ORANGE_STAINED_GLASS
                || material == Material.RED_STAINED_GLASS
                || material == Material.GLOWSTONE
                || material == Material.SEA_LANTERN
                || material == Material.LANTERN
                || material == Material.LEVER
                || material == Material.END_ROD;
    }
}
