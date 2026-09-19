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
 * DEV prop: ~1×2 roulette table (BlockDisplays + click hitbox), same idea as {@link CasinoCabinet}.
 */
public final class RouletteCabinet implements Listener {

    public RouletteCabinet() {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, RouletteCabinet::blinkLabels, 10L, 10L);
        }
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cRoulette Table");
            meta.setLore(List.of(
                    "§7DEV · place a 1×2 roulette prop.",
                    "§7Felt + ring. Click in-world to play.",
                    "",
                    "§eRight-click a block to place it.",
                    "§eSneak + right-click §7packs it up."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
        BlockFace front = look(player).getOppositeFace();
        if (!fits(origin.getBlock())) {
            player.sendMessage("§cNeed 1×2 air where it stands.");
            return;
        }
        build(origin, front);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.35f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 0.85f);
        player.sendMessage("§aRoulette's up. Click it to play.");
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
        openRoulette(player);
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
        openRoulette(player);
    }

    public static boolean despawn(Entity entity) {
        if (!isCabinet(entity)) {
            return false;
        }
        removeCluster(entity.getLocation(), 1.55, 2.9);
        return true;
    }

    private static void openRoulette(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getCasino() == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.35f);
        plugin.getCasino().open(player, "roulette");
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

    private static void build(Location origin, BlockFace front) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        float yaw = yawOf(front);
        // Pedestal
        cube(world, piece(origin, front, 0, 0.20, 0), yaw, front, Material.BLACK_CONCRETE, 0.78f, 0.36f, 0.78f);
        cube(world, piece(origin, front, 0, 0.42, 0), yaw, front, Material.GOLD_BLOCK, 0.90f, 0.10f, 0.90f);
        // Felt tabletop
        cube(world, piece(origin, front, 0, 0.72, 0), yaw, front, Material.GREEN_CONCRETE, 1.05f, 0.12f, 1.05f);
        // Outer rim
        cube(world, piece(origin, front, 0, 0.86, 0), yaw, front, Material.GOLD_BLOCK, 1.12f, 0.08f, 1.12f);
        // Wheel pockets — red / black / green zero vibe
        cube(world, piece(origin, front, 0, 0.96, 0.28), yaw, front, Material.RED_CONCRETE, 0.22f, 0.08f, 0.22f);
        cube(world, piece(origin, front, 0.28, 0.96, 0), yaw, front, Material.BLACK_CONCRETE, 0.22f, 0.08f, 0.22f);
        cube(world, piece(origin, front, 0, 0.96, -0.28), yaw, front, Material.RED_CONCRETE, 0.22f, 0.08f, 0.22f);
        cube(world, piece(origin, front, -0.28, 0.96, 0), yaw, front, Material.BLACK_CONCRETE, 0.22f, 0.08f, 0.22f);
        cube(world, piece(origin, front, 0.20, 0.96, 0.20), yaw, front, Material.RED_CONCRETE, 0.16f, 0.07f, 0.16f);
        cube(world, piece(origin, front, -0.20, 0.96, 0.20), yaw, front, Material.BLACK_CONCRETE, 0.16f, 0.07f, 0.16f);
        cube(world, piece(origin, front, 0.20, 0.96, -0.20), yaw, front, Material.BLACK_CONCRETE, 0.16f, 0.07f, 0.16f);
        cube(world, piece(origin, front, -0.20, 0.96, -0.20), yaw, front, Material.RED_CONCRETE, 0.16f, 0.07f, 0.16f);
        // Zero / ball
        cube(world, piece(origin, front, 0, 1.02, 0), yaw, front, Material.LIME_CONCRETE, 0.18f, 0.08f, 0.18f);
        cube(world, piece(origin, front, 0, 1.12, 0), yaw, front, Material.SMOOTH_QUARTZ, 0.10f, 0.10f, 0.10f);
        // Side lamp
        cube(world, piece(origin, front, 0.52, 0.85, 0.04), yaw, front, Material.GLOWSTONE, 0.12f, 0.12f, 0.12f);

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
            tag(spawned, front);
        });

        Location labelAt = piece(origin, front, 0, 1.55, 0.08);
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
        return Component.text("ROULETTE", bright ? NamedTextColor.RED : NamedTextColor.DARK_RED, TextDecoration.BOLD);
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
        entity.getPersistentDataContainer().set(ItemKeys.rouletteCabinet(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemKeys.rouletteCabinetFacing(), PersistentDataType.STRING, front.name());
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

    private static boolean fits(Block origin) {
        return replaceable(origin.getType()) && replaceable(origin.getRelative(BlockFace.UP).getType());
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
}
