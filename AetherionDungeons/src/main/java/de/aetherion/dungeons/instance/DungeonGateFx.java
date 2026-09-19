package de.aetherion.dungeons.instance;

import de.aetherion.dungeons.AetherionDungeons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class DungeonGateFx {

    public static final String TAG = "aether_gate_glass";
    private static final NamespacedKey KEY_X = new NamespacedKey("aetheriondungeons", "gate_x");
    private static final NamespacedKey KEY_Z = new NamespacedKey("aetheriondungeons", "gate_z");
    private static final NamespacedKey KEY_ALONG = new NamespacedKey("aetheriondungeons", "gate_along");

    private DungeonGateFx() {
    }

    public static void seal(World world, int gx, int gz, boolean alongX) {
        clearDisplays(world, gx, gz);
        forEachCell(gx, gz, alongX, (x, y, z) -> {
            world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
            spawnPane(world, x, y, z, gx, gz, alongX, Material.RED_STAINED_GLASS);
        });
    }

    public static void release(World world, int gx, int gz, boolean alongX) {
        forEachCell(gx, gz, true, (x, y, z) -> clearSeal(world, x, y, z));
        forEachCell(gx, gz, false, (x, y, z) -> clearSeal(world, x, y, z));
        Location center = new Location(world, gx + 0.5, PrototypeDungeonBuilder.FLOOR_Y + 2.0, gz + 0.5);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.6f);
        java.util.List<BlockDisplay> panes = findDisplays(world, gx, gz);
        if (panes.isEmpty()) {
            return;
        }
        for (BlockDisplay pane : panes) {
            pane.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
            pane.setGlowColorOverride(org.bukkit.Color.fromRGB(80, 220, 90));
            pane.setGlowing(true);
        }
        Plugin plugin = AetherionDungeons.getInstance();
        if (plugin == null) {
            panes.forEach(Entity::remove);
            return;
        }
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (tick == 6) {
                    world.playSound(center, Sound.BLOCK_GLASS_HIT, 0.7f, 0.7f);
                }
                boolean any = false;
                for (BlockDisplay pane : panes) {
                    if (!pane.isValid()) {
                        continue;
                    }
                    any = true;
                    Location next = pane.getLocation().add(0, -0.22, 0);
                    pane.teleport(next);
                    if (tick > 8) {
                        float scale = Math.max(0.15f, 0.98f - (tick - 8) * 0.07f);
                        pane.setTransformation(new Transformation(
                                new Vector3f((1f - scale) * 0.5f, (1f - scale) * 0.5f, (1f - scale) * 0.5f),
                                new AxisAngle4f(),
                                new Vector3f(scale, scale, scale),
                                new AxisAngle4f()
                        ));
                    }
                }
                if (!any || tick >= 20) {
                    panes.forEach(Entity::remove);
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.35f, 1.3f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private static void spawnPane(World world, int x, int y, int z, int gx, int gz, boolean alongX, Material material) {
        Location at = new Location(world, x, y, z);
        world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(
                    new Vector3f(0.01f, 0.01f, 0.01f),
                    new AxisAngle4f(),
                    new Vector3f(0.98f, 0.98f, 0.98f),
                    new AxisAngle4f()
            ));
            display.addScoreboardTag(TAG);
            display.getPersistentDataContainer().set(KEY_X, PersistentDataType.INTEGER, gx);
            display.getPersistentDataContainer().set(KEY_Z, PersistentDataType.INTEGER, gz);
            display.getPersistentDataContainer().set(KEY_ALONG, PersistentDataType.BYTE, alongX ? (byte) 1 : (byte) 0);
        });
    }

    private static void clearDisplays(World world, int gx, int gz) {
        findDisplays(world, gx, gz).forEach(Entity::remove);
        forEachCell(gx, gz, true, (x, y, z) -> {
            if (world.getBlockAt(x, y, z).getType() == Material.BARRIER) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        });
        forEachCell(gx, gz, false, (x, y, z) -> {
            if (world.getBlockAt(x, y, z).getType() == Material.BARRIER) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        });
    }

    private static java.util.List<BlockDisplay> findDisplays(World world, int gx, int gz) {
        Location center = new Location(world, gx + 0.5, PrototypeDungeonBuilder.FLOOR_Y + 2.0, gz + 0.5);
        java.util.List<BlockDisplay> found = new java.util.ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, 5, 5, 5)) {
            if (!(entity instanceof BlockDisplay display) || !display.getScoreboardTags().contains(TAG)) {
                continue;
            }
            Integer x = display.getPersistentDataContainer().get(KEY_X, PersistentDataType.INTEGER);
            Integer z = display.getPersistentDataContainer().get(KEY_Z, PersistentDataType.INTEGER);
            if (x != null && z != null && x == gx && z == gz) {
                found.add(display);
            }
        }
        return found;
    }

    private static void clearSeal(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.BARRIER || block.getType() == Material.IRON_BARS || block.getType() == Material.IRON_DOOR) {
            block.setType(Material.AIR, false);
        }
    }

    private static void forEachCell(int gx, int gz, boolean alongX, CellConsumer consumer) {
        int y0 = PrototypeDungeonBuilder.FLOOR_Y;
        if (alongX) {
            for (int x = gx - 2; x <= gx + 2; x++) {
                for (int dy = 1; dy <= 4; dy++) {
                    consumer.accept(x, y0 + dy, gz);
                }
            }
            return;
        }
        for (int z = gz - 2; z <= gz + 2; z++) {
            for (int dy = 1; dy <= 4; dy++) {
                consumer.accept(gx, y0 + dy, z);
            }
        }
    }

    @FunctionalInterface
    private interface CellConsumer {
        void accept(int x, int y, int z);
    }
}
