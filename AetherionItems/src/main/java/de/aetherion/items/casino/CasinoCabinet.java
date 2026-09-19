package de.aetherion.items.casino;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * DEV prop: a ~1×2 furniture slot machine (ItemDisplays + click hitbox).
 * Not real world blocks — same idea as Melody's harp.
 */
public final class CasinoCabinet implements Listener {

    public CasinoCabinet() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, CasinoCabinet::blinkLabels, 10L, 10L);
        }
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.JUKEBOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eSlot Machine");
            meta.setLore(List.of(
                    "§7DEV · place a 1×2 slot prop.",
                    "§7Looks like a machine, not blocks.",
                    "§7Click it in-world to open slots.",
                    "",
                    "§eRight-click a block to place it.",
                    "§eSneak + right-click §7packs it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
        BlockFace front = look(player).getOppositeFace();
        if (!fits(origin.getBlock())) {
            player.sendMessage("§cNeed 1×2 air where it stands.");
            return;
        }
        build(origin, front);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.35f, 1.35f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.4f);
        player.sendMessage("§aSlot's up. Click it to play.");
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
        openSlots(player);
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
        openSlots(player);
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
        openSlots(event.getPlayer());
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
        removeCluster(at, 1.45, 2.8);
        return true;
    }

    private static void openSlots(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCasino() == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 1.25f);
        plugin.getCasino().open(player, "slots");
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

    private static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        float yaw = yawOf(front);
        cube(world, piece(origin, front, 0, 0.22, 0), yaw, front, Material.BLACK_CONCRETE, 0.70f, 0.40f, 0.40f);
        cube(world, piece(origin, front, 0, 0.52, 0), yaw, front, Material.GOLD_BLOCK, 0.94f, 0.12f, 0.44f);
        cube(world, piece(origin, front, 0, 1.05, -0.02), yaw, front, Material.BLACK_CONCRETE, 0.88f, 1.08f, 0.36f);
        cube(world, piece(origin, front, 0, 1.64, 0), yaw, front, Material.GOLD_BLOCK, 0.96f, 0.14f, 0.44f);
        cube(world, piece(origin, front, 0, 1.84, 0), yaw, front, Material.GOLD_BLOCK, 0.62f, 0.12f, 0.34f);
        cube(world, piece(origin, front, 0, 1.94, 0), yaw, front, Material.GLOWSTONE, 0.22f, 0.10f, 0.22f);
        cube(world, piece(origin, front, -0.24, 1.14, 0.20), yaw, front, Material.YELLOW_STAINED_GLASS, 0.20f, 0.50f, 0.06f);
        cube(world, piece(origin, front, 0, 1.14, 0.20), yaw, front, Material.ORANGE_STAINED_GLASS, 0.20f, 0.50f, 0.06f);
        cube(world, piece(origin, front, 0.24, 1.14, 0.20), yaw, front, Material.RED_STAINED_GLASS, 0.20f, 0.50f, 0.06f);
        cube(world, piece(origin, front, 0.52, 1.18, 0.04), yaw, front, Material.RED_CONCRETE, 0.07f, 0.46f, 0.07f);
        cube(world, piece(origin, front, 0.52, 1.44, 0.04), yaw, front, Material.GOLD_BLOCK, 0.10f, 0.08f, 0.10f);

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
            tag(spawned, front);
        });

        Location labelAt = piece(origin, front, 0, 2.18, 0.12);
        world.spawn(labelAt, TextDisplay.class, spawned -> {
            spawned.text(labelText(true));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setViewRange(48f);
            tag(spawned, front);
        });
    }

    private static void blinkLabels() {
        boolean on = (Bukkit.getCurrentTick() / 10) % 2 == 0;
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!isCabinet(display)) {
                    continue;
                }
                display.text(labelText(on));
                display.setTextOpacity(on ? (byte) -1 : (byte) 90);
            }
            for (Interaction hit : world.getEntitiesByClass(Interaction.class)) {
                if (!isCabinet(hit)) {
                    continue;
                }
                if (hit.customName() != null || hit.isCustomNameVisible()) {
                    hit.customName(null);
                    hit.setCustomNameVisible(false);
                }
            }
        }
    }

    private static Component labelText(boolean bright) {
        return Component.text("SLOTS", bright ? NamedTextColor.GOLD : NamedTextColor.YELLOW, TextDecoration.BOLD);
    }

    private static void cube(
            World world,
            Location at,
            float yaw,
            BlockFace front,
            Material material,
            float width,
            float height,
            float depth
    ) {
        world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0f);
            spawned.setShadowStrength(0f);
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-width / 2f, -height / 2f, -depth / 2f),
                    new Quaternionf(),
                    new Vector3f(width, height, depth),
                    new Quaternionf()
            ));
            spawned.setRotation(yaw, 0f);
            tag(spawned, front);
        });
    }

    private static void tag(Entity entity, BlockFace front) {
        entity.getPersistentDataContainer().set(ItemKeys.slotCabinet(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING, front.name());
    }

    private static Location piece(Location origin, BlockFace front, double right, double y, double alongFront) {
        Vector r = right(front).getDirection().multiply(right);
        Vector f = front.getDirection().multiply(alongFront);
        return origin.clone().add(0.5, y, 0.5).add(r).add(f);
    }

    private static void removeCluster(Location at, double xz, double y) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(at, xz, y, xz)) {
            if (isCabinet(entity)) {
                entity.remove();
            }
        }
    }

    private static void clearLegacy(Entity marker) {
        Block origin = marker.getLocation().getBlock();
        String facingName = marker.getPersistentDataContainer().get(ItemKeys.slotCabinetFacing(), PersistentDataType.STRING);
        BlockFace front = facingName == null ? BlockFace.SOUTH : BlockFace.valueOf(facingName);
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

    private static boolean fits(Block origin) {
        return replaceable(origin.getType()) && replaceable(origin.getRelative(BlockFace.UP).getType());
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
        BlockFace front = facingName == null ? BlockFace.SOUTH : BlockFace.valueOf(facingName);
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
        Vector right = right(front).getDirection();
        Vector back = front.getOppositeFace().getDirection();
        int dx = (int) Math.round(right.getX() * x + back.getX() * z);
        int dz = (int) Math.round(right.getZ() * x + back.getZ() * z);
        return origin.getRelative(dx, y, dz);
    }

    private static BlockFace look(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360f + 360f) % 360f;
        if (yaw >= 315f || yaw < 45f) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135f) {
            return BlockFace.WEST;
        }
        if (yaw < 225f) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private static float yawOf(BlockFace front) {
        return switch (front) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    private static BlockFace right(BlockFace front) {
        return switch (front) {
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private static boolean replaceable(Material material) {
        return material.isAir()
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.SNOW
                || material == Material.VINE
                || material == Material.FERN
                || material == Material.DEAD_BUSH;
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
