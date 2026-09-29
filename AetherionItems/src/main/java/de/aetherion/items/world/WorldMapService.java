package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldMapService {

    public static final int RADIUS = 1024;
    public static final int MAP_CENTER_X = 497;
    public static final int MAP_CENTER_Z = 256;
    private static final int MOSAIC = 32;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlacedMap> placed = new ConcurrentHashMap<>();
    private Component mosaicCache;

    public WorldMapService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "world-maps.yml");
        load();
        plugin.getServer().getScheduler().runTaskLater(plugin, this::stripLegacyDisplays, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ensureDisplays, 80L, 200L);
    }

    public ItemStack createTool() {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lWorld Atlas §8· §fSpawn");
            meta.setLore(List.of(
                    "§7DEV · biome mosaic of ~" + RADIUS + " blocks",
                    "§7around spawn §f" + MAP_CENTER_X + " " + MAP_CENTER_Z + "§7.",
                    "",
                    "§eClick a wall §7to hang the atlas.",
                    "§eSneak + click §7removes the nearest."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(ItemKeys.worldMapTool(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.worldMapTool(), PersistentDataType.BYTE);
    }

    public PlacedMap place(Block block, BlockFace face, Player player) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        BlockFace facing = wallFace(face, player);
        Location origin = block.getRelative(facing).getLocation().add(0.5, 0.5, 0.5);
        float yaw = player == null ? origin.getYaw() : player.getLocation().getYaw() + 180f;
        PlacedMap map = new PlacedMap(
                UUID.randomUUID(),
                origin.getWorld().getName(),
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                yaw,
                facing.name()
        );
        spawnDisplay(map);
        placed.put(map.id(), map);
        save();
        if (player != null) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, origin.clone().add(0, 0.4, 0), 12, 0.4, 0.3, 0.4, 0);
            player.playSound(origin, Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.15f);
            player.sendMessage("§aPlaced spawn atlas. §7Sneak-click with the tool to remove.");
        }
        return map;
    }

    public boolean removeNearest(Location location, Player player) {
        PlacedMap map = nearest(location, 8);
        if (map == null) {
            if (player != null) {
                player.sendMessage("§cNo world atlas nearby.");
            }
            return false;
        }
        removeDisplay(map);
        placed.remove(map.id());
        save();
        if (player != null) {
            player.playSound(location, Sound.ITEM_BOOK_PUT, 0.8f, 0.8f);
            player.sendMessage("§eRemoved spawn atlas.");
        }
        return true;
    }

    private void spawnDisplay(PlacedMap map) {
        World world = Bukkit.getWorld(map.world());
        if (world == null) {
            return;
        }
        Location origin = map.location(world);
        BlockFace facing = map.facing();
        float yaw = yawOf(facing);
        map.frames().clear();
        TextDisplay mosaic = world.spawn(origin, TextDisplay.class, spawned -> {
            spawned.text(mosaic(world));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setLineWidth(1024);
            spawned.setSeeThrough(false);
            spawned.setShadowed(false);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setBackgroundColor(Color.fromARGB(180, 8, 10, 14));
            spawned.setRotation(yaw, 0f);
            spawned.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(0.42f, 0.42f, 0.42f),
                    new AxisAngle4f()
            ));
            spawned.getPersistentDataContainer().set(
                    ItemKeys.worldMapDisplay(),
                    PersistentDataType.STRING,
                    map.id().toString()
            );
            spawned.addScoreboardTag("aetherion_world_map");
        });
        map.frames().add(mosaic.getUniqueId());
        TextDisplay label = world.spawn(origin.clone().add(0, 2.35, 0), TextDisplay.class, spawned -> {
            spawned.setText("§6Aetherion §8· §7Spawn atlas\n§8~" + RADIUS + " blocks");
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(false);
            spawned.setPersistent(false);
            spawned.setBackgroundColor(Color.fromARGB(120, 12, 10, 8));
            spawned.getPersistentDataContainer().set(
                    ItemKeys.worldMapDisplay(),
                    PersistentDataType.STRING,
                    map.id().toString()
            );
            spawned.addScoreboardTag("aetherion_world_map");
        });
        map.labelId(label.getUniqueId());
    }

    private Component mosaic(World world) {
        if (mosaicCache != null) {
            return mosaicCache;
        }
        Component out = Component.empty();
        int span = RADIUS * 2;
        double step = span / (double) MOSAIC;
        int originX = MAP_CENTER_X - RADIUS;
        int originZ = MAP_CENTER_Z - RADIUS;
        int spawnCol = (int) Math.round((MAP_CENTER_X - originX) / step);
        int spawnRow = (int) Math.round((MAP_CENTER_Z - originZ) / step);
        for (int row = 0; row < MOSAIC; row++) {
            for (int col = 0; col < MOSAIC; col++) {
                int worldX = originX + (int) Math.floor(col * step);
                int worldZ = originZ + (int) Math.floor(row * step);
                java.awt.Color color = biomeColor(world, worldX, worldZ);
                if (col == spawnCol && row == spawnRow) {
                    color = java.awt.Color.WHITE;
                } else if (Math.abs(col - spawnCol) <= 1 && Math.abs(row - spawnRow) <= 1) {
                    color = new java.awt.Color(200, 40, 40);
                }
                out = out.append(Component.text("█").color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            }
            if (row + 1 < MOSAIC) {
                out = out.append(Component.newline());
            }
        }
        mosaicCache = out;
        return out;
    }

    private java.awt.Color biomeColor(World world, int x, int z) {
        Biome biome;
        try {
            biome = world.getComputedBiome(x, 64, z);
        } catch (Throwable ignored) {
            biome = world.getBiome(x, 64, z);
        }
        String id = biomeKey(biome);
        if (id.contains("ocean") || id.contains("deep")) {
            return new java.awt.Color(38, 72, 140);
        }
        if (id.contains("river")) {
            return new java.awt.Color(64, 104, 180);
        }
        if (id.contains("beach") || id.contains("shore")) {
            return new java.awt.Color(220, 201, 134);
        }
        if (id.contains("desert")) {
            return new java.awt.Color(230, 201, 86);
        }
        if (id.contains("badlands") || id.contains("eroded")) {
            return new java.awt.Color(180, 90, 40);
        }
        if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.equals("grove")) {
            return new java.awt.Color(236, 242, 248);
        }
        if (id.contains("peak") || id.contains("slope") || id.contains("windswept_hills") || id.contains("stony")) {
            return new java.awt.Color(128, 128, 132);
        }
        if (id.contains("dark_forest") || id.equals("pale_garden")) {
            return new java.awt.Color(22, 58, 22);
        }
        if (id.contains("jungle") || id.contains("bamboo")) {
            return new java.awt.Color(24, 132, 48);
        }
        if (id.contains("swamp") || id.contains("mangrove")) {
            return new java.awt.Color(70, 92, 48);
        }
        if (id.contains("mushroom")) {
            return new java.awt.Color(148, 88, 148);
        }
        if (id.contains("cherry")) {
            return new java.awt.Color(228, 158, 178);
        }
        if (id.contains("savanna")) {
            return new java.awt.Color(164, 148, 72);
        }
        if (id.contains("taiga")) {
            return new java.awt.Color(52, 92, 62);
        }
        if (id.contains("plains") || id.contains("meadow") || id.contains("sunflower")) {
            return new java.awt.Color(96, 168, 68);
        }
        if (id.contains("forest") || id.contains("birch")) {
            return new java.awt.Color(46, 112, 46);
        }
        return new java.awt.Color(80, 130, 70);
    }

    private static String biomeKey(Biome biome) {
        if (biome == null) {
            return "";
        }
        try {
            return biome.getKey().getKey().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return biome.name().toLowerCase(Locale.ROOT);
        }
    }

    private static float yawOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private void removeDisplay(PlacedMap map) {
        for (UUID id : List.copyOf(map.frames())) {
            removeEntity(id);
        }
        map.frames().clear();
        removeEntity(map.labelId());
        map.labelId(null);
        World world = Bukkit.getWorld(map.world());
        if (world == null) {
            return;
        }
        Location at = map.location(world);
        if (!at.getChunk().isLoaded()) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(at, 6, 6, 6)) {
            if (entity.getPersistentDataContainer().has(ItemKeys.worldMapDisplay(), PersistentDataType.STRING)
                    || entity.getScoreboardTags().contains("aetherion_world_map")) {
                entity.remove();
            }
        }
    }

    private void removeEntity(UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private void stripLegacyDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (entity instanceof ItemDisplay || entity instanceof ItemFrame || entity instanceof TextDisplay) {
                    if (entity.getPersistentDataContainer().has(ItemKeys.worldMapDisplay(), PersistentDataType.STRING)
                            || entity.getScoreboardTags().contains("aetherion_world_map")) {
                        entity.remove();
                    }
                }
            }
        }
        for (PlacedMap map : placed.values()) {
            removeDisplay(map);
            spawnDisplay(map);
        }
        save();
    }

    private void ensureDisplays() {
        for (PlacedMap map : placed.values()) {
            World world = Bukkit.getWorld(map.world());
            if (world == null) {
                continue;
            }
            Location at = map.location(world);
            if (!at.getChunk().isLoaded()) {
                continue;
            }
            if (framesHealthy(map) || reclaimFrames(map, world, at)) {
                continue;
            }
            removeDisplay(map);
            spawnDisplay(map);
            save();
        }
    }

    private boolean reclaimFrames(PlacedMap map, World world, Location at) {
        TextDisplay mosaic = null;
        TextDisplay label = null;
        for (Entity entity : world.getNearbyEntities(at, 6, 6, 6)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            String id = display.getPersistentDataContainer().get(
                    ItemKeys.worldMapDisplay(), PersistentDataType.STRING);
            if (id == null || !id.equals(map.id().toString())) {
                continue;
            }
            if (display.getScoreboardTags().contains("aetherion_world_map")
                    && display.getBillboard() == Display.Billboard.FIXED
                    && mosaic == null) {
                mosaic = display;
            } else if (label == null) {
                label = display;
            } else {
                entity.remove();
            }
        }
        if (mosaic == null || label == null) {
            return false;
        }
        map.frames().clear();
        map.frames().add(mosaic.getUniqueId());
        map.labelId(label.getUniqueId());
        return true;
    }

    private boolean framesHealthy(PlacedMap map) {
        if (map.frames().size() != 1) {
            return false;
        }
        Entity mosaic = Bukkit.getEntity(map.frames().get(0));
        Entity label = map.labelId() == null ? null : Bukkit.getEntity(map.labelId());
        return mosaic instanceof TextDisplay && mosaic.isValid()
                && label instanceof TextDisplay && label.isValid();
    }

    private PlacedMap nearest(Location location, double max) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        PlacedMap best = null;
        double bestDist = max * max;
        for (PlacedMap map : placed.values()) {
            if (!map.world().equals(location.getWorld().getName())) {
                continue;
            }
            double dist = map.location(location.getWorld()).distanceSquared(location);
            if (dist <= bestDist) {
                bestDist = dist;
                best = map;
            }
        }
        return best;
    }

    private static BlockFace wallFace(BlockFace face, Player player) {
        if (face != null && face.isCartesian() && face != BlockFace.UP && face != BlockFace.DOWN) {
            return face;
        }
        float yaw = player == null ? 0f : player.getLocation().getYaw();
        yaw = (yaw % 360f + 360f) % 360f;
        if (yaw >= 45 && yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw >= 135 && yaw < 225) {
            return BlockFace.NORTH;
        }
        if (yaw >= 225 && yaw < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("maps")) {
            return;
        }
        for (String key : config.getConfigurationSection("maps").getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String path = "maps." + key + ".";
            placed.put(id, new PlacedMap(
                    id,
                    config.getString(path + "world", "world"),
                    config.getDouble(path + "x"),
                    config.getDouble(path + "y"),
                    config.getDouble(path + "z"),
                    (float) config.getDouble(path + "yaw"),
                    config.getString(path + "face", "NORTH")
            ));
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (PlacedMap map : placed.values()) {
            String path = "maps." + map.id() + ".";
            config.set(path + "world", map.world());
            config.set(path + "x", map.x());
            config.set(path + "y", map.y());
            config.set(path + "z", map.z());
            config.set(path + "yaw", map.yaw());
            config.set(path + "face", map.face());
            List<String> frames = new ArrayList<>();
            for (UUID id : map.frames()) {
                frames.add(id.toString());
            }
            config.set(path + "frames", frames);
            if (map.labelId() != null) {
                config.set(path + "label", map.labelId().toString());
            }
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
        }
    }

    static final class PlacedMap {
        private final UUID id;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final String face;
        private final List<UUID> frames = new ArrayList<>();
        private UUID labelId;

        private PlacedMap(UUID id, String world, double x, double y, double z, float yaw, String face) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.face = face == null || face.isBlank() ? "NORTH" : face;
        }

        UUID id() {
            return id;
        }

        String world() {
            return world;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }

        double z() {
            return z;
        }

        float yaw() {
            return yaw;
        }

        String face() {
            return face;
        }

        BlockFace facing() {
            try {
                BlockFace parsed = BlockFace.valueOf(face);
                if (parsed.isCartesian() && parsed != BlockFace.UP && parsed != BlockFace.DOWN) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return BlockFace.NORTH;
        }

        List<UUID> frames() {
            return frames;
        }

        UUID labelId() {
            return labelId;
        }

        void labelId(UUID labelId) {
            this.labelId = labelId;
        }

        Location location(World world) {
            return new Location(world, x, y, z, yaw, 0f);
        }
    }
}
