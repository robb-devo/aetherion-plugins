package de.aetherion.items.farm;

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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Millstone 2.0 — taller mill house + windmill sails (idle spin).
 * Extra DEV prop; v1 stays. Refine still uses the bottom grind arm.
 */
public final class MillstoneWindmill implements Listener {

    private static final float SAIL_IDLE = 0.035f;

    public MillstoneWindmill(AetherionItems plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, MillstoneWindmill::tickSails, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(plugin, MillstoneWindmill::blinkLabels, 10L, 10L);
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.OAK_LOG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Millstone 2.0");
            meta.setLore(List.of(
                    "§7DEV · larger mill + windmill sails.",
                    "§7Sails idle-spin; grind arm mills crops.",
                    "",
                    "§eRight-click a block to place.",
                    "§eSneak + right-click §7packs it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE);
    }

    public static boolean isV2(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE);
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
                player.sendMessage("§cNo Millstone 2.0 nearby.");
            }
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place Millstone 2.0.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = look(player).getOppositeFace();
        if (!fits(origin.getBlock())) {
            player.sendMessage("§cNeed clear space (~3×3×6) for Millstone 2.0.");
            return;
        }
        build(origin, front);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 0.75f);
        player.sendMessage("§aMillstone 2.0 placed.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isV2(event.getRightClicked()) && !isV2Part(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isV2(event.getRightClicked()) && !isV2Part(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§eRoot Cellar runs the mill — talk to them.");
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.9f);
    }

    public static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        float yaw = yawOf(front);

        // Base / mill house
        cube(world, piece(origin, front, 0, 0.2, 0), yaw, front, Material.STONE_BRICKS, 1.6f, 0.4f, 1.6f);
        cube(world, piece(origin, front, 0, 0.55, 0), yaw, front, Material.STRIPPED_OAK_LOG, 1.35f, 0.35f, 1.35f);
        cube(world, piece(origin, front, 0, 0.95, 0), yaw, front, Material.COBBLESTONE, 1.2f, 0.55f, 1.2f);

        // Bottom grind arm (refine animation target)
        Location armAt = piece(origin, front, 0, 1.25, 0);
        world.spawn(armAt, BlockDisplay.class, spawned -> {
            spawned.setBlock(Material.SMOOTH_STONE.createBlockData());
            style(spawned);
            spawned.setTeleportDuration(8);
            spawned.setInterpolationDuration(8);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.55f, -0.14f, -0.55f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.1f, 0.28f, 1.1f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            spawned.setRotation(yaw, 0f);
            tag(spawned, front, true);
            spawned.getPersistentDataContainer().set(ItemKeys.millstoneArm(), PersistentDataType.BYTE, (byte) 1);
        });

        // Roof of mill house
        cube(world, piece(origin, front, 0, 1.65, 0), yaw, front, Material.SPRUCE_PLANKS, 1.25f, 0.22f, 1.25f);
        cube(world, piece(origin, front, 0.55, 0.85, 0.1), yaw, front, Material.HAY_BLOCK, 0.28f, 0.28f, 0.28f);
        cube(world, piece(origin, front, -0.55, 0.85, -0.1), yaw, front, Material.HAY_BLOCK, 0.28f, 0.28f, 0.28f);

        // Tower shaft
        cube(world, piece(origin, front, 0, 2.15, 0), yaw, front, Material.STRIPPED_OAK_LOG, 0.45f, 0.9f, 0.45f);
        cube(world, piece(origin, front, 0, 3.05, 0), yaw, front, Material.STRIPPED_OAK_LOG, 0.4f, 0.9f, 0.4f);
        cube(world, piece(origin, front, 0, 3.9, 0), yaw, front, Material.STRIPPED_SPRUCE_LOG, 0.38f, 0.7f, 0.38f);

        // Cap / axle hub
        Location hub = piece(origin, front, 0, 4.45, 0.15);
        cube(world, hub, yaw, front, Material.OAK_WOOD, 0.55f, 0.4f, 0.55f);
        cube(world, piece(origin, front, 0, 4.45, 0.45), yaw, front, Material.STRIPPED_OAK_LOG, 0.22f, 0.22f, 0.55f);

        // Four sails on the hub (idle spin)
        Location sailHub = piece(origin, front, 0, 4.45, 0.55);
        for (int blade = 0; blade < 4; blade++) {
            int index = blade;
            world.spawn(sailHub.clone(), BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.WHITE_WOOL.createBlockData());
                style(spawned);
                spawned.setInterpolationDuration(2);
                spawned.setTeleportDuration(2);
                spawned.setRotation(yaw, 0f);
                tag(spawned, front, true);
                spawned.getPersistentDataContainer().set(ItemKeys.millstoneSail(), PersistentDataType.INTEGER, index);
                applySail(spawned, 0f, index);
            });
        }

        // Hitbox covers house + lower tower
        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.8f);
            spawned.setInteractionHeight(2.4f);
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.customName(null);
            spawned.setCustomNameVisible(false);
            tag(spawned, front, true);
        });

        Location labelAt = piece(origin, front, 0, 2.55, 0.7);
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
            spawned.setViewRange(56f);
            tag(spawned, front, true);
        });
    }

    private static void tickSails() {
        float spin = (Bukkit.getCurrentTick() * SAIL_IDLE) % ((float) (Math.PI * 2));
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (!display.getPersistentDataContainer().has(ItemKeys.millstoneSail(), PersistentDataType.INTEGER)) {
                    continue;
                }
                Integer blade = display.getPersistentDataContainer().get(ItemKeys.millstoneSail(), PersistentDataType.INTEGER);
                if (blade == null) {
                    continue;
                }
                applySail(display, spin, blade);
            }
        }
    }

    private static void applySail(BlockDisplay display, float spin, int blade) {
        float angle = spin + blade * ((float) Math.PI / 2f);
        float width = 0.5f;
        float length = 2.35f;
        float depth = 0.12f;
        display.setInterpolationDuration(2);
        display.setTransformation(new Transformation(
                new Vector3f(-width / 2f, -0.05f, -depth / 2f),
                new AxisAngle4f(angle, 0f, 0f, 1f),
                new Vector3f(width, length, depth),
                new AxisAngle4f(0f, 0f, 1f, 0f)
        ));
    }

    private static void blinkLabels() {
        boolean on = (Bukkit.getCurrentTick() / 10) % 2 == 0;
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!isV2Part(display)) {
                    continue;
                }
                display.text(labelText(on));
                display.setTextOpacity(on ? (byte) -1 : (byte) 90);
            }
        }
    }

    private static Component labelText(boolean bright) {
        return Component.text("MILLSTONE", bright ? NamedTextColor.GOLD : NamedTextColor.YELLOW, TextDecoration.BOLD);
    }

    private boolean despawnNearest(Player player) {
        Interaction hit = findNearestV2(player.getLocation(), 6.0);
        if (hit == null) {
            return false;
        }
        Location center = hit.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        for (Entity entity : world.getNearbyEntities(center, 3.5, 6.0, 3.5)) {
            if (isV2Part(entity) || isV2(entity)) {
                entity.remove();
            }
        }
        player.sendMessage("§eMillstone 2.0 packed.");
        return true;
    }

    private static Interaction findNearestV2(Location at, double radius) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        Interaction best = null;
        double bestDist = radius * radius;
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof Interaction hit) || !isV2(hit)) {
                continue;
            }
            double dist = hit.getLocation().distanceSquared(at);
            if (dist <= bestDist) {
                bestDist = dist;
                best = hit;
            }
        }
        return best;
    }

    private static boolean isV2Part(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE);
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
            style(spawned);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-width / 2f, -height / 2f, -depth / 2f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(width, height, depth),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            spawned.setRotation(yaw, 0f);
            tag(spawned, front, true);
        });
    }

    private static void style(BlockDisplay spawned) {
        spawned.setBrightness(new Display.Brightness(15, 15));
        spawned.setShadowRadius(0f);
        spawned.setPersistent(true);
        spawned.setGravity(false);
        spawned.setInvulnerable(true);
    }

    private static void tag(Entity entity, BlockFace front, boolean v2) {
        entity.getPersistentDataContainer().set(ItemKeys.millstone(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.millstoneFacing(), PersistentDataType.STRING, front.name());
        if (v2) {
            entity.getPersistentDataContainer().set(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE, (byte) 1);
        }
    }

    private static Location piece(Location origin, BlockFace front, double right, double y, double alongFront) {
        Vector f = front.getDirection().normalize();
        Vector r = right(front).getDirection().normalize();
        return origin.clone().add(0.5, y, 0.5).add(r.multiply(right)).add(f.multiply(alongFront));
    }

    private static BlockFace look(Player player) {
        float yaw = player.getLocation().getYaw();
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) {
            return BlockFace.SOUTH;
        }
        if (rot < 135) {
            return BlockFace.WEST;
        }
        if (rot < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private static BlockFace right(BlockFace front) {
        return switch (front) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static float yawOf(BlockFace face) {
        return switch (face) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    private static boolean fits(Block block) {
        for (int y = 0; y <= 5; y++) {
            Block at = block.getRelative(0, y, 0);
            if (!at.getType().isAir()) {
                return false;
            }
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (!at.getRelative(face).getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }
}
