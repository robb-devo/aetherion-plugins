package de.aetherion.farming.dev;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * DEV placeable scarecrow — BlockDisplay prop plus an Interaction hitbox,
 * same build/pack flow as the Millstone 2.0 anchor.
 *
 * Anchor positions are mirrored into {@code scarecrows.yml} so
 * {@link de.aetherion.farming.ScarecrowEvent} still knows where the props are
 * after a restart, even before their chunks load.
 */
public final class ScarecrowProp implements Listener {

    public static final String TOOL_KEY = "scarecrow_tool";

    private static final double PACK_RADIUS = 5.0d;

    private final AetherionFarming plugin;
    private final NamespacedKey toolKey;
    private final NamespacedKey partKey;
    private final File file;
    private final List<Location> anchors = new ArrayList<>();

    public ScarecrowProp(AetherionFarming plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, TOOL_KEY);
        this.partKey = new NamespacedKey(plugin, "scarecrow_part");
        this.file = new File(plugin.getDataFolder(), "scarecrows.yml");
        load();
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Scarecrow §8(DEV)");
            meta.setLore(List.of(
                    "§7Farm Isle bird minigame anchor.",
                    "§7Every few minutes birds land",
                    "§7around it — click them to shoo.",
                    "§7Clearing a wave grants §6Golden Hour§7.",
                    "",
                    "§eRight-click a block §7to place.",
                    "§eSneak + right-click §7packs it up.",
                    "§8Admin tool — not consumed"
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

    /** Live copy of every placed scarecrow position. */
    public List<Location> anchors() {
        return List.copyOf(anchors);
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
                player.sendMessage("§cNo scarecrow nearby.");
                return;
            }
            player.sendMessage("§eScarecrow packed.");
            player.playSound(player.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.7f, 0.9f);
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the scarecrow.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = look(player).getOppositeFace();
        build(origin, front);
        anchors.add(center(origin));
        save();
        player.sendMessage("§aScarecrow placed §8· §7birds will visit it.");
        player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 0.8f, 0.8f);
    }

    /** Props are scenery — never let a click open or push them around. */
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

        // Post, straw body, coat, arms, pumpkin head.
        cube(world, piece(origin, front, 0, 0.55, 0), yaw, Material.OAK_FENCE, 0.16f, 1.1f, 0.16f);
        cube(world, piece(origin, front, 0, 1.25, 0), yaw, Material.HAY_BLOCK, 0.55f, 0.7f, 0.42f);
        cube(world, piece(origin, front, 0, 1.05, 0.22), yaw, Material.BROWN_WOOL, 0.6f, 0.45f, 0.1f);
        cube(world, piece(origin, front, 0, 1.55, 0), yaw, Material.STRIPPED_OAK_LOG, 1.5f, 0.13f, 0.13f);
        cube(world, piece(origin, front, 0.78, 1.42, 0), yaw, Material.WHEAT, 0.18f, 0.3f, 0.18f);
        cube(world, piece(origin, front, -0.78, 1.42, 0), yaw, Material.WHEAT, 0.18f, 0.3f, 0.18f);
        cube(world, piece(origin, front, 0, 1.85, 0), yaw, Material.CARVED_PUMPKIN, 0.5f, 0.5f, 0.5f);

        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(0.9f);
            spawned.setInteractionHeight(2.2f);
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setCustomNameVisible(false);
            tag(spawned);
        });
    }

    /** Removes the nearest prop and drops it from the saved anchor list. */
    public boolean pack(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Location nearest = null;
        double bestDist = PACK_RADIUS * PACK_RADIUS;
        for (Location anchor : anchors) {
            if (anchor.getWorld() == null || !anchor.getWorld().equals(at.getWorld())) {
                continue;
            }
            double dist = anchor.distanceSquared(at);
            if (dist <= bestDist) {
                bestDist = dist;
                nearest = anchor;
            }
        }
        boolean removedEntity = false;
        Location center = nearest != null ? nearest : at;
        for (Entity entity : center.getWorld().getNearbyEntities(center, PACK_RADIUS, PACK_RADIUS, PACK_RADIUS)) {
            if (isPart(entity)) {
                entity.remove();
                removedEntity = true;
            }
        }
        if (nearest != null) {
            anchors.remove(nearest);
            save();
        }
        return nearest != null || removedEntity;
    }

    private void load() {
        anchors.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("scarecrows")) {
            Location at = parse(raw);
            if (at != null) {
                anchors.add(at);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Location at : anchors) {
            if (at.getWorld() == null) {
                continue;
            }
            out.add(at.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ());
        }
        yaml.set("scarecrows", out);
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create the AetherionFarming data folder.");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save scarecrows.yml: " + exception.getMessage());
        }
    }

    private static Location parse(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Location center(Location origin) {
        return origin.clone().add(0.5, 0, 0.5);
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
