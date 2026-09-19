package de.aetherion.dungeons.hub;

import de.aetherion.dungeons.bridge.RemoteServerBridge;
import de.aetherion.dungeons.npc.DungeonGuideService;
import de.aetherion.dungeons.npc.DungeonKeeperService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Dungeon-hub (mmo-d overworld): use the builder's existing portal frame,
 * clutter cleanup, hub rules. Never invents a new frame.
 */
public final class DungeonHubService implements Listener {

    private final JavaPlugin plugin;
    private final RemoteServerBridge remote;

    public DungeonHubService(JavaPlugin plugin, RemoteServerBridge remote) {
        this.plugin = plugin;
        this.remote = remote;
    }

    public void start() {
        if (remote == null || !remote.isDungeonRole()) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::bootstrap, 60L);
    }

    private void bootstrap() {
        ensureReturnPortal();
        cleanupClutter();
        plugin.getLogger().info("Dungeon hub ready — return portal + cleanup applied.");
    }

    /**
     * Strip auto-built portal blocks, then light the largest existing obsidian frame nearby.
     */
    public void ensureReturnPortal() {
        World world = Bukkit.getWorld(plugin.getConfig().getString("return-portal.world", "world"));
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return;
        }
        int hintX = (int) Math.floor(plugin.getConfig().getDouble("return-portal.x", -1.0));
        int hintY = (int) Math.floor(plugin.getConfig().getDouble("return-portal.y", 48.0));
        int hintZ = (int) Math.floor(plugin.getConfig().getDouble("return-portal.z", -27.0));

        clearNetherPortalsNear(world, hintX, hintY, hintZ, 28);
        // Tear down the tiny 4×5 frame this plugin previously forced in.
        stripForcedMiniFrame(world, hintX, hintY, hintZ);

        Frame frame = findLargestFrame(world, hintX, hintY, hintZ, 36);
        if (frame == null) {
            plugin.getLogger().warning("No existing obsidian portal frame found near "
                    + hintX + "," + hintY + "," + hintZ + " — place/adjust your frame, then /dungeon portal");
            return;
        }
        lightFrame(world, frame);
        persistPortalCenter(frame);
        plugin.getLogger().info("Return portal lit on existing frame at "
                + frame.centerX() + "," + frame.centerY() + "," + frame.centerZ()
                + " (" + frame.innerW() + "x" + frame.innerH() + ", axis=" + frame.axis() + ")");
    }

    private void clearNetherPortalsNear(World world, int cx, int cy, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), cy - 12); y <= cy + 16; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.NETHER_PORTAL) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /** Removes only the previously auto-built 4×5 rim if it is still that tiny footprint. */
    private void stripForcedMiniFrame(World world, int cx, int y, int cz) {
        // Old builder used x=cx-1..cx+2, y=y..y+4, z=cz
        int obsidian = 0;
        int airish = 0;
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Material type = world.getBlockAt(cx + dx, y + dy, cz).getType();
                boolean rim = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                if (rim && type == Material.OBSIDIAN) {
                    obsidian++;
                }
                if (!rim && (type == Material.AIR || type == Material.NETHER_PORTAL || type.isAir())) {
                    airish++;
                }
            }
        }
        // Only strip if it still looks like OUR mini frame (10 rim obsidians), not a bigger build.
        if (obsidian < 8 || airish < 4) {
            return;
        }
        // If neighboring blocks outside the mini footprint are also obsidian, it's the player's larger frame.
        boolean larger = false;
        for (int dx = -3; dx <= 4 && !larger; dx++) {
            for (int dy = -1; dy <= 5 && !larger; dy++) {
                if (dx >= -1 && dx <= 2 && dy >= 0 && dy <= 4) {
                    continue;
                }
                if (world.getBlockAt(cx + dx, y + dy, cz).getType() == Material.OBSIDIAN) {
                    larger = true;
                }
            }
        }
        if (larger) {
            return;
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                world.getBlockAt(cx + dx, y + dy, cz).setType(Material.AIR, false);
            }
        }
        plugin.getLogger().info("Removed auto-built mini portal frame at " + cx + "," + y + "," + cz);
    }

    private Frame findLargestFrame(World world, int cx, int cy, int cz, int radius) {
        Frame best = null;
        int bestArea = 0;
        int yMin = Math.max(world.getMinHeight() + 1, cy - 10);
        int yMax = cy + 20;
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    for (int innerW = 2; innerW <= 10; innerW++) {
                        for (int innerH = 3; innerH <= 12; innerH++) {
                            if (isFrameX(world, x, y, z, innerW, innerH)) {
                                int area = innerW * innerH;
                                if (area > bestArea) {
                                    bestArea = area;
                                    best = Frame.axisX(x, y, z, innerW, innerH);
                                }
                            }
                            if (isFrameZ(world, x, y, z, innerW, innerH)) {
                                int area = innerW * innerH;
                                if (area > bestArea) {
                                    bestArea = area;
                                    best = Frame.axisZ(x, y, z, innerW, innerH);
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Frame in Z-plane (portal axis X): rim at z, opening along X/Y. (x,y,z)=bottom-left inner corner. */
    private static boolean isFrameX(World world, int x, int y, int z, int innerW, int innerH) {
        // bottom / top
        for (int dx = -1; dx <= innerW; dx++) {
            if (!isObsidian(world, x + dx, y - 1, z) || !isObsidian(world, x + dx, y + innerH, z)) {
                return false;
            }
        }
        // sides
        for (int dy = 0; dy < innerH; dy++) {
            if (!isObsidian(world, x - 1, y + dy, z) || !isObsidian(world, x + innerW, y + dy, z)) {
                return false;
            }
        }
        // interior empty / portal
        for (int dx = 0; dx < innerW; dx++) {
            for (int dy = 0; dy < innerH; dy++) {
                Material type = world.getBlockAt(x + dx, y + dy, z).getType();
                if (type != Material.AIR && type != Material.NETHER_PORTAL && !type.isAir()
                        && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Frame in X-plane (portal axis Z): rim at x, opening along Z/Y. */
    private static boolean isFrameZ(World world, int x, int y, int z, int innerW, int innerH) {
        for (int dz = -1; dz <= innerW; dz++) {
            if (!isObsidian(world, x, y - 1, z + dz) || !isObsidian(world, x, y + innerH, z + dz)) {
                return false;
            }
        }
        for (int dy = 0; dy < innerH; dy++) {
            if (!isObsidian(world, x, y + dy, z - 1) || !isObsidian(world, x, y + dy, z + innerW)) {
                return false;
            }
        }
        for (int dz = 0; dz < innerW; dz++) {
            for (int dy = 0; dy < innerH; dy++) {
                Material type = world.getBlockAt(x, y + dy, z + dz).getType();
                if (type != Material.AIR && type != Material.NETHER_PORTAL && !type.isAir()
                        && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isObsidian(World world, int x, int y, int z) {
        Material type = world.getBlockAt(x, y, z).getType();
        return type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN;
    }

    private static void lightFrame(World world, Frame frame) {
        org.bukkit.Axis axis = frame.axisX() ? org.bukkit.Axis.X : org.bukkit.Axis.Z;
        for (int a = 0; a < frame.innerW(); a++) {
            for (int dy = 0; dy < frame.innerH(); dy++) {
                int x = frame.axisX() ? frame.x() + a : frame.x();
                int z = frame.axisX() ? frame.z() : frame.z() + a;
                Block block = world.getBlockAt(x, frame.y() + dy, z);
                block.setType(Material.NETHER_PORTAL, false);
                if (block.getBlockData() instanceof Orientable orientable) {
                    orientable.setAxis(axis);
                    block.setBlockData(orientable, false);
                }
            }
        }
    }

    private void persistPortalCenter(Frame frame) {
        FileConfiguration config = plugin.getConfig();
        config.set("return-portal.x", (double) frame.centerX());
        config.set("return-portal.y", (double) frame.centerY());
        config.set("return-portal.z", (double) frame.centerZ());
        config.set("return-portal.radius", Math.max(3.5, Math.max(frame.innerW(), frame.innerH()) * 0.75));
        plugin.saveConfig();
    }

    private record Frame(int x, int y, int z, int innerW, int innerH, boolean axisX) {
        static Frame axisX(int x, int y, int z, int w, int h) {
            return new Frame(x, y, z, w, h, true);
        }

        static Frame axisZ(int x, int y, int z, int w, int h) {
            return new Frame(x, y, z, w, h, false);
        }

        int centerX() {
            return axisX ? x + innerW / 2 : x;
        }

        int centerY() {
            return y + Math.max(1, innerH / 2);
        }

        int centerZ() {
            return axisX ? z : z + innerW / 2;
        }

        String axis() {
            return axisX ? "X" : "Z";
        }
    }

    public void cleanupClutter() {
        World world = Bukkit.getWorld(plugin.getConfig().getString("return-portal.world", "world"));
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (entity instanceof Player) {
                continue;
            }
            if (DungeonKeeperService.isKeeper(plugin, entity)) {
                continue;
            }
            if (DungeonGuideService.isGuide(plugin, entity)) {
                continue;
            }
            if (entity instanceof ItemFrame
                    || entity instanceof ItemDisplay
                    || entity instanceof TextDisplay) {
                entity.remove();
                removed++;
                continue;
            }
            String name = entity.getCustomName();
            if (name != null && (name.contains("Egon") || name.contains("Temper") || name.contains("Ledger")
                    || name.contains("Quartermaster") || name.contains("Craftsman") || name.contains("Vex")
                    || name.contains("Lumberjack") || name.contains("Fishmonger") || name.contains("Tackle")
                    || name.contains("Farmer") || name.contains("Lark") || name.contains("Foreman"))) {
                entity.remove();
                removed++;
            }
        }
        plugin.getLogger().info("Dungeon hub cleanup removed " + removed + " leftover entities.");
    }

    public boolean isHubWorld(World world) {
        if (world == null || remote == null || !remote.isDungeonRole()) {
            return false;
        }
        String lower = world.getName().toLowerCase(Locale.ROOT);
        if (lower.startsWith("aedun_") || lower.startsWith("ae_dun")) {
            return false;
        }
        String hub = plugin.getConfig().getString("return-portal.world", "world");
        return world.getName().equalsIgnoreCase(hub);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isHubWorld(event.getBlock().getWorld())) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                && event.getPlayer().hasPermission("aetherion.dungeon.admin")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isHubWorld(event.getBlock().getWorld())) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                && event.getPlayer().hasPermission("aetherion.dungeon.admin")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!isHubWorld(victim.getWorld())) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantTable(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        if (!isHubWorld(event.getClickedBlock().getWorld())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage("§5Dungeon Reliquary§7: Hold a §fdungeon relic §7or §fschematic §7and right-click it to identify / attune.");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.15f);
    }
}
