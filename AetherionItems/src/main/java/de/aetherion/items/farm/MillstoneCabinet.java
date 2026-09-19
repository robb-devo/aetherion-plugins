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
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Farm Isle millstone — casino-style prop; place via DEV anchor only (no auto-place).
 */
public final class MillstoneCabinet implements Listener {

    private static final String STATION_FILE = "millstone.yml";

    private final AetherionItems plugin;
    private final MillstoneRitual ritual;

    public MillstoneCabinet(AetherionItems plugin, MillstoneRitual ritual) {
        this.plugin = plugin;
        this.ritual = ritual;
        Bukkit.getScheduler().runTaskTimer(plugin, MillstoneCabinet::blinkLabels, 10L, 10L);
    }

    public MillstoneRitual ritual() {
        return ritual;
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.GRINDSTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eMillstone");
            meta.setLore(List.of(
                    "§7DEV · place the Farm Isle mill.",
                    "§7Compacted crops → Pantry (refined).",
                    "",
                    "§eRight-click a block to place.",
                    "§eSneak + right-click §7packs it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.millstoneAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.millstoneAnchor(), PersistentDataType.BYTE);
    }

    public static boolean isMillstone(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.millstone(), PersistentDataType.BYTE);
    }

    private void saveStation(Location origin, BlockFace face) {
        File file = new File(plugin.getDataFolder(), STATION_FILE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("station.placed", true);
        config.set("station.world", origin.getWorld() != null ? origin.getWorld().getName() : "world");
        config.set("station.x", origin.getX() + 0.5);
        config.set("station.y", origin.getY());
        config.set("station.z", origin.getZ() + 0.5);
        config.set("station.facing", face.name());
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save millstone.yml: " + ex.getMessage());
        }
    }

    private void clearStationFile() {
        File file = new File(plugin.getDataFolder(), STATION_FILE);
        if (file.exists() && !file.delete()) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("station.placed", false);
            try {
                config.save(file);
            } catch (IOException ignored) {
            }
        }
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
                player.sendMessage("§cNo millstone nearby.");
            }
            return;
        }
        if (event.getClickedBlock() == null) {
            player.sendMessage("§cRight-click a block to place the millstone.");
            return;
        }
        Location origin = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        BlockFace front = look(player).getOppositeFace();
        if (!fits(origin.getBlock())) {
            player.sendMessage("§cNeed clear space for the mill.");
            return;
        }
        build(origin, front);
        saveStation(origin, front);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 0.9f);
        player.sendMessage("§aMillstone placed.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isMillstone(event.getRightClicked()) || isWindmill(event.getRightClicked())) {
            return;
        }
        // Cancel only — InteractAt also fires for Interaction entities (would double-message).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isMillstone(event.getRightClicked()) || isWindmill(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer(), event.getRightClicked());
    }

    private static boolean isWindmill(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.millstoneV2Anchor(), PersistentDataType.BYTE);
    }

    private void open(Player player, Entity hit) {
        player.sendMessage("§eRoot Cellar runs the mill — talk to them.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.9f);
    }

    public static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        float yaw = yawOf(front);
        cube(world, piece(origin, front, 0, 0.25, 0), yaw, front, Material.STRIPPED_OAK_LOG, 1.1f, 0.45f, 1.1f, false);
        cube(world, piece(origin, front, 0, 0.55, 0), yaw, front, Material.OAK_PLANKS, 1.0f, 0.18f, 1.0f, false);
        cube(world, piece(origin, front, 0, 0.85, 0), yaw, front, Material.STONE, 0.95f, 0.35f, 0.95f, false);
        Location armAt = piece(origin, front, 0, 1.15, 0);
        world.spawn(armAt, BlockDisplay.class, spawned -> {
            spawned.setBlock(Material.SMOOTH_STONE.createBlockData());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0f);
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setTeleportDuration(8);
            spawned.setInterpolationDuration(8);
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.45f, -0.12f, -0.45f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.9f, 0.24f, 0.9f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            spawned.setRotation(yaw, 0f);
            tag(spawned, front);
            spawned.getPersistentDataContainer().set(ItemKeys.millstoneArm(), PersistentDataType.BYTE, (byte) 1);
        });
        cube(world, piece(origin, front, 0, 1.35, 0), yaw, front, Material.OAK_FENCE, 0.18f, 0.45f, 0.18f, false);
        cube(world, piece(origin, front, 0.42, 0.7, 0.05), yaw, front, Material.HAY_BLOCK, 0.22f, 0.22f, 0.22f, false);
        cube(world, piece(origin, front, -0.42, 0.7, -0.05), yaw, front, Material.HAY_BLOCK, 0.22f, 0.22f, 0.22f, false);

        Location hitAt = origin.clone().add(0.5, 0, 0.5);
        world.spawn(hitAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.35f);
            spawned.setInteractionHeight(1.7f);
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.customName(null);
            spawned.setCustomNameVisible(false);
            tag(spawned, front);
        });

        Location labelAt = piece(origin, front, 0, 1.85, 0.05);
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

    public static BlockDisplay findArm(UUID stationId, Location near) {
        if (near == null || near.getWorld() == null) {
            return null;
        }
        BlockDisplay best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : near.getWorld().getNearbyEntities(near, 3.5, 4.5, 3.5)) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            if (!display.getPersistentDataContainer().has(ItemKeys.millstoneArm(), PersistentDataType.BYTE)) {
                continue;
            }
            double dist = display.getLocation().distanceSquared(near);
            if (dist < bestDist) {
                bestDist = dist;
                best = display;
            }
        }
        return best;
    }

    public static Interaction findNearest(Location at, double radius) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        Interaction best = null;
        double bestDist = radius * radius;
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof Interaction hit) || !isMillstone(hit)) {
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

    private boolean despawnNearest(Player player) {
        Interaction hit = findNearest(player.getLocation(), 5.0);
        if (hit == null) {
            return false;
        }
        Location center = hit.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        for (Entity entity : world.getNearbyEntities(center, 2.5, 3.0, 2.5)) {
            if (isMillstone(entity)) {
                entity.remove();
            }
        }
        player.sendMessage("§eMillstone packed.");
        clearStationFile();
        return true;
    }

    private static void blinkLabels() {
        boolean on = (Bukkit.getCurrentTick() / 10) % 2 == 0;
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!isMillstone(display)) {
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

    private static void cube(
            World world,
            Location at,
            float yaw,
            BlockFace front,
            Material material,
            float width,
            float height,
            float depth,
            boolean arm
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
            tag(spawned, front);
            if (arm) {
                spawned.getPersistentDataContainer().set(ItemKeys.millstoneArm(), PersistentDataType.BYTE, (byte) 1);
            }
        });
    }

    private static void tag(Entity entity, BlockFace front) {
        entity.getPersistentDataContainer().set(ItemKeys.millstone(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.millstoneFacing(), PersistentDataType.STRING, front.name());
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
        return block.getType().isAir() && block.getRelative(BlockFace.UP).getType().isAir();
    }
}
