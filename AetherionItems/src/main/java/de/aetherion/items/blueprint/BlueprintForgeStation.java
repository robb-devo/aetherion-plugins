package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Eldervale forge frame — bottom anvil is the world block; hammer + chains are displays.
 */
public final class BlueprintForgeStation {

    private static final String FILE = "blueprint-forge.yml";
    private static final double SEARCH_RADIUS = 10.0;
    private static final double DEFAULT_NPC_X = 53.5;
    private static final double DEFAULT_NPC_Y = 90.0;
    private static final double DEFAULT_NPC_Z = 500.5;

    private final AetherionItems plugin;
    private String worldName = "world";
    private int anvilX;
    private int anvilY;
    private int anvilZ;
    private double hammerTopY;
    private boolean resolved;

    public BlueprintForgeStation(AetherionItems plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureResolved, 40L);
    }

    public boolean isResolved() {
        return resolved;
    }

    public Location anvilCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !resolved) {
            return null;
        }
        return new Location(world, anvilX + 0.5, anvilY + 1.05, anvilZ + 0.5);
    }

    public Location hammerRest() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !resolved) {
            return null;
        }
        return new Location(world, anvilX + 0.5, hammerTopY, anvilZ + 0.5);
    }

    public Location hammerSlam() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !resolved) {
            return null;
        }
        // Just above the real anvil top.
        return new Location(world, anvilX + 0.5, anvilY + 1.2, anvilZ + 0.5);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public void ensureResolved() {
        Location hint = forgehandHint();
        Block anvil = resolved && stillValid()
                ? world().getBlockAt(anvilX, anvilY, anvilZ)
                : findAnvilNear(hint);
        if (anvil == null || !isAnvil(anvil.getType())) {
            anvil = findAnvilNear(hint);
        }
        if (anvil == null) {
            plugin.getLogger().warning("Blueprint forge: no anvil near Forgehand — place one in the frame.");
            resolved = false;
            return;
        }
        worldName = anvil.getWorld().getName();
        anvilX = anvil.getX();
        anvilY = anvil.getY();
        anvilZ = anvil.getZ();
        // Always refresh height from the free column above the anvil.
        hammerTopY = detectHammerTopY(anvil);
        resolved = true;
        save();
        ensureProps();
        plugin.getLogger().info("Blueprint forge station @ "
                + anvilX + "," + anvilY + "," + anvilZ + " hammerY=" + String.format(Locale.ROOT, "%.2f", hammerTopY));
    }

    public void ensureProps() {
        if (!resolved) {
            return;
        }
        World world = world();
        if (world == null) {
            return;
        }
        clearProps(world);
        spawnHammer(world);
    }

    public void clearProps(World world) {
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
            if (entity.getPersistentDataContainer().has(ItemKeys.blueprintForgeProp(), PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    private boolean stillValid() {
        World world = world();
        if (world == null) {
            return false;
        }
        return isAnvil(world.getBlockAt(anvilX, anvilY, anvilZ).getType());
    }

    private Location forgehandHint() {
        Location fromNpc = readNpcLocation();
        if (fromNpc != null) {
            return fromNpc;
        }
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, DEFAULT_NPC_X, DEFAULT_NPC_Y, DEFAULT_NPC_Z);
    }

    private Location readNpcLocation() {
        try {
            File file = new File(Bukkit.getPluginsFolder(), "AetherionQuests/npcs.yml");
            if (!file.exists()) {
                return null;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String base = "npcs.eldervale_upgrade.";
            if (!config.contains(base + "x")) {
                return null;
            }
            String world = config.getString(base + "world", "world");
            World w = Bukkit.getWorld(world);
            if (w == null) {
                return null;
            }
            return new Location(
                    w,
                    config.getDouble(base + "x", DEFAULT_NPC_X),
                    config.getDouble(base + "y", DEFAULT_NPC_Y),
                    config.getDouble(base + "z", DEFAULT_NPC_Z)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Block findAnvilNear(Location hint) {
        if (hint == null || hint.getWorld() == null) {
            return null;
        }
        World world = hint.getWorld();
        int cx = hint.getBlockX();
        int cy = hint.getBlockY();
        int cz = hint.getBlockZ();
        int r = (int) Math.ceil(SEARCH_RADIUS);
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - 3; y <= cy + 6; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isAnvil(block.getType())) {
                        continue;
                    }
                    double dx = (x + 0.5) - hint.getX();
                    double dy = (y + 0.5) - hint.getY();
                    double dz = (z + 0.5) - hint.getZ();
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = block;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Hammer sits at the top of the free air column above the anvil
     * (typically 3 empty blocks inside the frame).
     */
    private double detectHammerTopY(Block anvil) {
        World world = anvil.getWorld();
        int air = 0;
        for (int y = anvil.getY() + 1; y <= anvil.getY() + 8; y++) {
            Material type = world.getBlockAt(anvil.getX(), y, anvil.getZ()).getType();
            if (!type.isAir() && type != Material.LIGHT && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                break;
            }
            air++;
        }
        if (air < 1) {
            return anvil.getY() + 3.0;
        }
        // Entity Y = top of the free column (e.g. 3 air → anvilY+3).
        return anvil.getY() + air;
    }

    private void spawnHammer(World world) {
        Location at = hammerRest();
        if (at == null) {
            return;
        }
        world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(Material.ANVIL.createBlockData());
            display.setGravity(false);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            // Upright anvil, centered on the column.
            display.setTransformation(new Transformation(
                    new Vector3f(-0.5f, 0f, -0.5f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            display.setTeleportDuration(10);
            display.setInterpolationDuration(10);
            display.getPersistentDataContainer().set(ItemKeys.blueprintForgeProp(), PersistentDataType.BYTE, (byte) 1);
        });
        spawnChain(world, at.clone().add(0, 0.85, 0));
        spawnChain(world, at.clone().add(0, 1.75, 0));
    }

    private void spawnChain(World world, Location at) {
        world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(Material.CHAIN.createBlockData());
            display.setGravity(false);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(12, 12));
            // Two slim vertical chain segments stacked above the hammer.
            display.setTransformation(new Transformation(
                    new Vector3f(-0.125f, 0f, -0.125f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.25f, 0.9f, 0.25f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            display.setTeleportDuration(10);
            display.setInterpolationDuration(10);
            display.getPersistentDataContainer().set(ItemKeys.blueprintForgeProp(), PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Active hammer display for rituals. */
    public BlockDisplay hammerEntity() {
        return findProp(Material.ANVIL, hammerRest(), 2.5, 6.0);
    }

    /** Lower chain that rides with the hammer. */
    public BlockDisplay chainEntity() {
        Location rest = hammerRest();
        if (rest == null) {
            return null;
        }
        return findProp(Material.CHAIN, rest.clone().add(0, 0.85, 0), 2.5, 6.0);
    }

    /** Upper chain stacked on the lower one. */
    public BlockDisplay chainTopEntity() {
        Location rest = hammerRest();
        if (rest == null) {
            return null;
        }
        return findProp(Material.CHAIN, rest.clone().add(0, 1.75, 0), 2.5, 6.0);
    }

    private BlockDisplay findProp(Material material, Location near, double hx, double hy) {
        World world = world();
        if (world == null || near == null) {
            return null;
        }
        BlockDisplay best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(near, hx, hy, hx)) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            if (!display.getPersistentDataContainer().has(ItemKeys.blueprintForgeProp(), PersistentDataType.BYTE)) {
                continue;
            }
            if (display.getBlock().getMaterial() != material) {
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

    public void moveHammer(BlockDisplay hammer, BlockDisplay chain, BlockDisplay chainTop, Location hammerAt) {
        if (hammer == null || !hammer.isValid() || hammerAt == null) {
            return;
        }
        hammer.setTeleportDuration(10);
        hammer.teleport(hammerAt);
        if (chain != null && chain.isValid()) {
            chain.setTeleportDuration(10);
            chain.teleport(hammerAt.clone().add(0, 0.85, 0));
        }
        if (chainTop != null && chainTop.isValid()) {
            chainTop.setTeleportDuration(10);
            chainTop.teleport(hammerAt.clone().add(0, 1.75, 0));
        }
    }

    public void moveHammer(BlockDisplay hammer, BlockDisplay chain, Location hammerAt) {
        moveHammer(hammer, chain, null, hammerAt);
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("station.x")) {
            return;
        }
        worldName = config.getString("station.world", "world");
        anvilX = config.getInt("station.x");
        anvilY = config.getInt("station.y");
        anvilZ = config.getInt("station.z");
        hammerTopY = config.getDouble("station.hammer-top-y", anvilY + 4.0);
        resolved = true;
    }

    private void save() {
        File file = new File(plugin.getDataFolder(), FILE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("station.world", worldName);
        config.set("station.x", anvilX);
        config.set("station.y", anvilY);
        config.set("station.z", anvilZ);
        config.set("station.hammer-top-y", hammerTopY);
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save blueprint-forge.yml: " + ex.getMessage());
        }
    }

    private static boolean isAnvil(Material type) {
        return type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL;
    }
}
