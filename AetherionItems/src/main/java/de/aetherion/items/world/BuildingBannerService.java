package de.aetherion.items.world;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Rotation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEV building banners: 1 block high × 7 maps wide, purple-flame Aetherion art.
 */
public final class BuildingBannerService {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlacedBanner> placed = new ConcurrentHashMap<>();
    private boolean rebuiltArt;

    public BuildingBannerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "building-banners.yml");
        load();
        // AetherionDungeons may enable after Items — re-check role a tick later.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isDungeonServer()) {
                clearAllBanners();
                plugin.getLogger().info("Building banners disabled on dungeon server (casino/AH&BZ wiped).");
                return;
            }
            rebuildAllArt();
        }, 60L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (isDungeonServer()) {
                return;
            }
            ensureDisplays();
        }, 120L, 200L);
    }

    private static boolean isDungeonServer() {
        Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (dungeons == null || !dungeons.isEnabled()) {
            return false;
        }
        String role = dungeons.getConfig().getString("role", "");
        return "dungeon".equalsIgnoreCase(role);
    }

    /** Remove all saved banners + nearby tagged frames (dungeon hub cleanup). */
    public void clearAllBanners() {
        for (PlacedBanner banner : List.copyOf(placed.values())) {
            removeDisplay(banner);
        }
        placed.clear();
        try {
            YamlConfiguration empty = new YamlConfiguration();
            empty.save(file);
        } catch (IOException ignored) {
            if (file.exists()) {
                file.delete();
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : List.copyOf(world.getEntities())) {
                    if (!(entity instanceof ItemFrame frame)) {
                        continue;
                    }
                    if (frame.getPersistentDataContainer().has(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING)
                            || frame.getScoreboardTags().contains("aetherion_building_banner")) {
                        frame.remove();
                    }
                }
            }
        });
    }

    public ItemStack createTool(BuildingBannerKind kind) {
        if (kind == null) {
            kind = BuildingBannerKind.CASINO;
        }
        ItemStack item = new ItemStack(kind.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kind.displayName());
            meta.setLore(List.of(
                    "§7DEV · 1×7 map banner for buildings.",
                    "§dPurple flame · Aetherion style",
                    "",
                    "§eRight-click a wall §7to place (7 wide).",
                    "§eSneak + click §7removes nearby."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(ItemKeys.buildingBannerTool(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ItemKeys.buildingBannerKind(), PersistentDataType.STRING, kind.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ItemKeys.buildingBannerTool(), PersistentDataType.BYTE);
    }

    public BuildingBannerKind kindOf(ItemStack item) {
        if (!isTool(item)) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.buildingBannerKind(), PersistentDataType.STRING);
        BuildingBannerKind kind = BuildingBannerKind.fromId(id);
        return kind == null ? BuildingBannerKind.CASINO : kind;
    }

    public PlacedBanner place(Block block, BlockFace face, BuildingBannerKind kind, Player player) {
        if (isDungeonServer()) {
            if (player != null) {
                player.sendMessage("§cBuilding banners are disabled on the dungeon hub.");
            }
            return null;
        }
        if (block == null || block.getWorld() == null || kind == null) {
            return null;
        }
        BlockFace facing = wallFace(face, player);
        BlockFace right = rightOf(facing);
        Block air = block.getRelative(facing);
        int mid = BuildingBannerArt.PANELS / 2;
        UUID id = UUID.randomUUID();
        PlacedBanner banner = new PlacedBanner(
                id,
                air.getWorld().getName(),
                air.getX(),
                air.getY(),
                air.getZ(),
                facing.name(),
                kind.id()
        );
        for (int i = 0; i < BuildingBannerArt.PANELS; i++) {
            Block at = air.getRelative(right, i - mid);
            ItemFrame frame = spawnPanel(at, facing, kind, i, id);
            if (frame != null) {
                banner.frames().add(frame.getUniqueId());
            }
        }
        if (banner.frames().size() != BuildingBannerArt.PANELS) {
            removeDisplay(banner);
            if (player != null) {
                player.sendMessage("§cCould not place all 7 panels (blocked?).");
            }
            return null;
        }
        placed.put(id, banner);
        save();
        if (player != null) {
            Location fx = air.getLocation().add(0.5, 0.5, 0.5);
            player.spawnParticle(Particle.WITCH, fx, 28, 2.4, 0.35, 0.35, 0.01);
            player.spawnParticle(Particle.ENCHANT, fx, 40, 2.6, 0.4, 0.4, 0.4);
            player.playSound(fx, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.35f);
            player.sendMessage("§5Placed §d" + kind.title() + " §5banner §8(1×7)§5. §7Sneak-click to remove.");
        }
        return banner;
    }

    public boolean removeNearest(Location location, Player player) {
        PlacedBanner banner = nearest(location, 10);
        if (banner == null) {
            if (player != null) {
                player.sendMessage("§cNo building banner nearby.");
            }
            return false;
        }
        removeDisplay(banner);
        placed.remove(banner.id());
        save();
        if (player != null) {
            player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 0.85f);
            player.sendMessage("§eRemoved building banner.");
        }
        return true;
    }

    private ItemFrame spawnPanel(Block at, BlockFace facing, BuildingBannerKind kind, int panel, UUID bannerId) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        if (!at.isPassable() && at.getType() != Material.AIR && at.getType() != Material.CAVE_AIR && at.getType() != Material.VOID_AIR) {
            return null;
        }
        // Clear leftover frames in this cell
        for (Entity entity : world.getNearbyEntities(at.getLocation().add(0.5, 0.5, 0.5), 0.45, 0.45, 0.45)) {
            if (entity instanceof ItemFrame frame
                    && frame.getPersistentDataContainer().has(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING)) {
                frame.remove();
            }
        }
        MapView view = Bukkit.createMap(world);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new BuildingBannerRenderer(kind, panel));
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.setDisplayName("§5" + kind.title());
            meta.getPersistentDataContainer().set(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING, bannerId.toString());
            meta.getPersistentDataContainer().set(ItemKeys.buildingBannerKind(), PersistentDataType.STRING, kind.id());
            meta.getPersistentDataContainer().set(ItemKeys.buildingBannerPanel(), PersistentDataType.INTEGER, panel);
            mapItem.setItemMeta(meta);
        }

        try {
            return world.spawn(at.getLocation(), ItemFrame.class, frame -> {
                frame.setFacingDirection(facing, true);
                frame.setItem(mapItem, false);
                frame.setVisible(false);
                frame.setFixed(true);
                frame.setInvulnerable(true);
                frame.setGravity(false);
                frame.setRotation(Rotation.NONE);
                frame.getPersistentDataContainer().set(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING, bannerId.toString());
                frame.getPersistentDataContainer().set(ItemKeys.buildingBannerKind(), PersistentDataType.STRING, kind.id());
                frame.getPersistentDataContainer().set(ItemKeys.buildingBannerPanel(), PersistentDataType.INTEGER, panel);
                frame.addScoreboardTag("aetherion_building_banner");
            });
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void removeDisplay(PlacedBanner banner) {
        for (UUID frameId : List.copyOf(banner.frames())) {
            Entity entity = Bukkit.getEntity(frameId);
            if (entity != null) {
                entity.remove();
            }
        }
        banner.frames().clear();
        World world = Bukkit.getWorld(banner.world());
        if (world == null) {
            return;
        }
        Location at = banner.location(world);
        if (!at.getChunk().isLoaded()) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(at, 8, 4, 8)) {
            String id = entity.getPersistentDataContainer().get(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING);
            if (banner.id().toString().equals(id) || entity.getScoreboardTags().contains("aetherion_building_banner")) {
                String match = entity.getPersistentDataContainer().get(ItemKeys.buildingBannerDisplay(), PersistentDataType.STRING);
                if (banner.id().toString().equals(match)) {
                    entity.remove();
                }
            }
        }
    }

    private void rebuildAllArt() {
        for (PlacedBanner banner : placed.values()) {
            respawn(banner);
        }
        rebuiltArt = true;
        save();
        plugin.getLogger().info("Rebuilt " + placed.size() + " building banners (art v" + BuildingBannerArt.ART_VERSION + ").");
    }

    private void respawn(PlacedBanner banner) {
        BuildingBannerKind kind = BuildingBannerKind.fromId(banner.kind());
        if (kind == null) {
            return;
        }
        World world = Bukkit.getWorld(banner.world());
        if (world == null) {
            return;
        }
        BlockFace facing = banner.facing();
        BlockFace right = rightOf(facing);
        Block air = world.getBlockAt(banner.x(), banner.y(), banner.z());
        removeDisplay(banner);
        int mid = BuildingBannerArt.PANELS / 2;
        for (int i = 0; i < BuildingBannerArt.PANELS; i++) {
            Block cell = air.getRelative(right, i - mid);
            ItemFrame frame = spawnPanel(cell, facing, kind, i, banner.id());
            if (frame != null) {
                banner.frames().add(frame.getUniqueId());
            }
        }
    }

    private void ensureDisplays() {
        if (!rebuiltArt) {
            return;
        }
        for (PlacedBanner banner : placed.values()) {
            World world = Bukkit.getWorld(banner.world());
            if (world == null) {
                continue;
            }
            Location at = banner.location(world);
            if (!at.getChunk().isLoaded()) {
                continue;
            }
            if (framesHealthy(banner)) {
                reattachRenderers(banner);
                continue;
            }
            respawn(banner);
            save();
        }
    }

    private boolean framesHealthy(PlacedBanner banner) {
        if (banner.frames().size() != BuildingBannerArt.PANELS) {
            return false;
        }
        for (UUID id : banner.frames()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof ItemFrame frame) || !frame.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void reattachRenderers(PlacedBanner banner) {
        BuildingBannerKind kind = BuildingBannerKind.fromId(banner.kind());
        if (kind == null) {
            return;
        }
        for (UUID id : banner.frames()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            ItemStack item = frame.getItem();
            if (!(item.getItemMeta() instanceof MapMeta meta) || meta.getMapView() == null) {
                continue;
            }
            Integer panel = frame.getPersistentDataContainer().get(ItemKeys.buildingBannerPanel(), PersistentDataType.INTEGER);
            if (panel == null) {
                panel = meta.getPersistentDataContainer().get(ItemKeys.buildingBannerPanel(), PersistentDataType.INTEGER);
            }
            if (panel == null) {
                continue;
            }
            MapView view = meta.getMapView();
            boolean has = false;
            for (org.bukkit.map.MapRenderer renderer : view.getRenderers()) {
                if (renderer instanceof BuildingBannerRenderer) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                view.getRenderers().forEach(view::removeRenderer);
                view.addRenderer(new BuildingBannerRenderer(kind, panel));
            }
        }
    }

    private PlacedBanner nearest(Location location, double max) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        PlacedBanner best = null;
        double bestDist = max * max;
        for (PlacedBanner banner : placed.values()) {
            if (!banner.world().equals(location.getWorld().getName())) {
                continue;
            }
            double dist = banner.location(location.getWorld()).distanceSquared(location);
            if (dist <= bestDist) {
                bestDist = dist;
                best = banner;
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

    private static BlockFace rightOf(BlockFace facing) {
        // Viewer's right when looking at the frame (opposite of facing).
        return switch (facing) {
            case SOUTH -> BlockFace.EAST;
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (PlacedBanner banner : placed.values()) {
            String path = "banners." + banner.id() + ".";
            config.set(path + "world", banner.world());
            config.set(path + "x", banner.x());
            config.set(path + "y", banner.y());
            config.set(path + "z", banner.z());
            config.set(path + "face", banner.face());
            config.set(path + "kind", banner.kind());
            List<String> frames = new ArrayList<>();
            for (UUID id : banner.frames()) {
                frames.add(id.toString());
            }
            config.set(path + "frames", frames);
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save building-banners.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("banners")) {
            return;
        }
        for (String key : config.getConfigurationSection("banners").getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String path = "banners." + key + ".";
            PlacedBanner banner = new PlacedBanner(
                    id,
                    config.getString(path + "world", "world"),
                    config.getInt(path + "x"),
                    config.getInt(path + "y"),
                    config.getInt(path + "z"),
                    config.getString(path + "face", "SOUTH"),
                    config.getString(path + "kind", "casino")
            );
            for (String frame : config.getStringList(path + "frames")) {
                try {
                    banner.frames().add(UUID.fromString(frame));
                } catch (IllegalArgumentException ignored) {
                }
            }
            placed.put(id, banner);
        }
    }

    public static final class PlacedBanner {
        private final UUID id;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String face;
        private final String kind;
        private final List<UUID> frames = new ArrayList<>();

        public PlacedBanner(UUID id, String world, int x, int y, int z, String face, String kind) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face == null || face.isBlank() ? "SOUTH" : face;
            this.kind = kind == null ? "casino" : kind;
        }

        public UUID id() {
            return id;
        }

        public String world() {
            return world;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public String face() {
            return face;
        }

        public String kind() {
            return kind;
        }

        public BlockFace facing() {
            try {
                BlockFace parsed = BlockFace.valueOf(face);
                if (parsed.isCartesian() && parsed != BlockFace.UP && parsed != BlockFace.DOWN) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return BlockFace.SOUTH;
        }

        public List<UUID> frames() {
            return frames;
        }

        public Location location(World world) {
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }
    }
}
