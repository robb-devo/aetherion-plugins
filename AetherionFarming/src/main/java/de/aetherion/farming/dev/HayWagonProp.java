package de.aetherion.farming.dev;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
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
 * DEV ambience prop — a loaded hay wagon built from BlockDisplays.
 * Same place / sneak-pack flow as the Millstone 2.0 anchor. No weather, no ticking.
 */
public final class HayWagonProp implements Listener {

    public static final String TOOL_KEY = "hay_wagon_tool";

    private static final double PACK_RADIUS = 5.0d;

    private final NamespacedKey toolKey;
    private final NamespacedKey partKey;

    public HayWagonProp(AetherionFarming plugin) {
        this.toolKey = new NamespacedKey(plugin, TOOL_KEY);
        this.partKey = new NamespacedKey(plugin, "hay_wagon_part");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.HAY_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Hay Wagon §8(DEV)");
            meta.setLore(List.of(
                    "§7Loaded cart for the Mill Yard",
                    "§7and the field edges.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7packs it up.",
                    "§8Admin prop — not consumed"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public boolean isPart(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(partKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev") && !player.hasPermission("aetherion.dev.content")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            if (!pack(player.getLocation())) {
                player.sendMessage("§cNo hay wagon nearby.");
                return;
            }
            player.sendMessage("§eHay wagon packed.");
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.7f, 0.9f);
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the hay wagon.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        build(origin, look(player).getOppositeFace());
        player.sendMessage("§aHay wagon placed.");
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 0.9f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (isPart(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (isPart(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        float yaw = yawOf(front);

        // Cart bed + side rails
        cube(world, piece(origin, front, 0, 0.62, 0), yaw, Material.OAK_PLANKS, 1.5f, 0.14f, 2.3f);
        cube(world, piece(origin, front, 0.72, 0.85, 0), yaw, Material.OAK_PLANKS, 0.1f, 0.45f, 2.3f);
        cube(world, piece(origin, front, -0.72, 0.85, 0), yaw, Material.OAK_PLANKS, 0.1f, 0.45f, 2.3f);
        cube(world, piece(origin, front, 0, 0.85, -1.12), yaw, Material.OAK_PLANKS, 1.5f, 0.45f, 0.1f);

        // Load
        cube(world, piece(origin, front, -0.3, 1.0, -0.55), yaw, Material.HAY_BLOCK, 0.7f, 0.7f, 0.7f);
        cube(world, piece(origin, front, 0.32, 1.0, 0.15), yaw, Material.HAY_BLOCK, 0.7f, 0.7f, 0.7f);
        cube(world, piece(origin, front, -0.1, 1.55, -0.2), yaw, Material.HAY_BLOCK, 0.62f, 0.62f, 0.62f);

        // Wheels + draw bar
        cube(world, piece(origin, front, 0.78, 0.4, -0.75), yaw, Material.STRIPPED_DARK_OAK_LOG, 0.12f, 0.8f, 0.8f);
        cube(world, piece(origin, front, -0.78, 0.4, -0.75), yaw, Material.STRIPPED_DARK_OAK_LOG, 0.12f, 0.8f, 0.8f);
        cube(world, piece(origin, front, 0.78, 0.4, 0.75), yaw, Material.STRIPPED_DARK_OAK_LOG, 0.12f, 0.8f, 0.8f);
        cube(world, piece(origin, front, -0.78, 0.4, 0.75), yaw, Material.STRIPPED_DARK_OAK_LOG, 0.12f, 0.8f, 0.8f);
        cube(world, piece(origin, front, 0, 0.5, 1.5), yaw, Material.STRIPPED_OAK_LOG, 0.12f, 0.12f, 1.1f);

        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.7f);
            spawned.setInteractionHeight(1.8f);
            spawned.setResponsive(false);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setCustomNameVisible(false);
            tag(spawned);
        });
    }

    private boolean pack(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        boolean removed = false;
        for (Entity entity : at.getWorld().getNearbyEntities(at, PACK_RADIUS, PACK_RADIUS, PACK_RADIUS)) {
            if (isPart(entity)) {
                entity.remove();
                removed = true;
            }
        }
        return removed;
    }

    private void cube(
            World world,
            Location at,
            float yaw,
            Material material,
            float width,
            float height,
            float depth
    ) {
        world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0f);
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-width / 2f, -height / 2f, -depth / 2f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(width, height, depth),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            spawned.setRotation(yaw, 0f);
            tag(spawned);
        });
    }

    private void tag(Entity entity) {
        entity.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
    }

    private static Location piece(Location origin, BlockFace front, double right, double y, double alongFront) {
        Vector f = front.getDirection().normalize();
        Vector r = rightOf(front).getDirection().normalize();
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

    private static BlockFace rightOf(BlockFace front) {
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
}
