package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Static BlockDisplay ambience prop — same place / sneak-pack flow as Hay Wagon.
 * No tick, no AI, no holograms. Safe to place many.
 */
public abstract class AmbientProp implements Listener {

    protected static final double PACK_RADIUS = 5.0d;

    private final NamespacedKey toolKey;
    private final NamespacedKey partKey;

    protected AmbientProp(AetherionItems plugin, String id) {
        this.toolKey = new NamespacedKey(plugin, id + "_tool");
        this.partKey = new NamespacedKey(plugin, id + "_part");
    }

    public abstract String id();

    protected abstract String title();

    protected abstract List<String> loreLines();

    protected abstract Material icon();

    protected abstract float hitWidth();

    protected abstract float hitHeight();

    protected abstract void buildMesh(World world, Location origin, BlockFace front, float yaw);

    public final ItemStack create() {
        ItemStack item = new ItemStack(icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title() + " §8(DEV)");
            List<String> lore = new ArrayList<>(loreLines());
            lore.add("");
            lore.add("§eRight-click a block §7to place.");
            lore.add("§eSneak + right-click §7packs it up.");
            lore.add("§8Admin prop — not consumed");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public final boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public final boolean isPart(Entity entity) {
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
                player.sendMessage("§cNo " + plainName() + " nearby.");
                return;
            }
            player.sendMessage("§e" + plainName() + " packed.");
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.7f, 0.9f);
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = look(player).getOppositeFace();
        build(origin, front);
        player.sendMessage("§a" + plainName() + " placed.");
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
        buildMesh(world, origin, front, yaw);
        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(hitWidth());
            spawned.setInteractionHeight(hitHeight());
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

    protected final void cube(World world, Location at, float yaw, Material material, float width, float height, float depth) {
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

    protected static Location piece(Location origin, BlockFace front, double right, double y, double alongFront) {
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

    protected static float yawOf(BlockFace face) {
        return switch (face) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    private String plainName() {
        return title().replaceAll("§.", "");
    }
}
