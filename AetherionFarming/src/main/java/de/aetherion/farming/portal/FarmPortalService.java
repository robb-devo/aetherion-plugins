package de.aetherion.farming.portal;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.island.FarmIslandService;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmPortalService {

    public static final String TOOL_KEY = "farm_portal_tool";

    private final AetherionFarming plugin;
    private final FarmIslandService island;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    /** After a farm-portal teleport, ignore until the player leaves NETHER_PORTAL blocks. */
    private final java.util.Set<UUID> mustExitPortal = ConcurrentHashMap.newKeySet();

    public FarmPortalService(AetherionFarming plugin, FarmIslandService island) {
        this.plugin = plugin;
        this.island = island;
    }

    public FarmIslandService island() {
        return island;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("farm-island.enabled", true);
    }

    public int requiredFarmingLevel() {
        return Math.max(1, plugin.getConfig().getInt("farm-island.require-farming-level", 10));
    }

    public int cooldownTicks() {
        return Math.max(10, plugin.getConfig().getInt("farm-island.cooldown-ticks", 40));
    }

    public double hubRadius() {
        return plugin.getConfig().getDouble("farm-island.hub-portal-radius", 6.0);
    }

    public ItemStack createHubPortalTool() {
        ItemStack item = new ItemStack(Material.OBSIDIAN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lFarm Portal Tool");
            meta.setLore(List.of(
                    "§7DEV · place the hub portal",
                    "§7linked to the shared farm island.",
                    "",
                    "§eRight-click a block §7→ build portal",
                    "§7and save hub entry.",
                    "§eSneak + right-click §7→ clear hub portal."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    plugin.portalToolKey(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isHubPortalTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.portalToolKey(), PersistentDataType.BYTE);
    }

    public Location hubPortal() {
        return readLocation("farm-island.hub-portal");
    }

    public Location islandExit() {
        return readLocation("farm-island.island-exit");
    }

    public void setHubPortal(Location location) {
        writeLocation("farm-island.hub-portal", location);
        plugin.saveConfig();
    }

    public void clearHubPortal() {
        plugin.getConfig().set("farm-island.hub-portal", null);
        plugin.saveConfig();
    }

    public void setIslandExit(Location location) {
        writeLocation("farm-island.island-exit", location);
        plugin.saveConfig();
    }

    public int farmingLevel(Player player) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getSkills() == null) {
                return 0;
            }
            return items.getSkills().highestLevel(player, AetherSkill.Category.FARMING);
        } catch (NoClassDefFoundError | Exception ignored) {
            return 0;
        }
    }

    public boolean canUseHubPortal(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp() || player.hasPermission("aetherion.dev")) {
            return true;
        }
        return farmingLevel(player) >= requiredFarmingLevel();
    }

    public boolean tryEnterFromHub(Player player) {
        if (!enabled() || player == null) {
            return false;
        }
        Location hub = hubPortal();
        if (hub == null || hub.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(hub.getWorld())) {
            return false;
        }
        if (player.getLocation().distanceSquared(hub) > hubRadius() * hubRadius()) {
            return false;
        }
        if (!standingInPortal(player)) {
            clearExitGate(player);
            return false;
        }
        if (mustExitPortal.contains(player.getUniqueId())) {
            return true;
        }
        if (onCooldown(player)) {
            return true;
        }
        if (!canUseHubPortal(player)) {
            int need = requiredFarmingLevel();
            int have = farmingLevel(player);
            player.sendMessage("§cFarm island needs Farming Lv. §f" + need + "§c. You have §f" + have + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            markCooldown(player);
            return true;
        }
        Location dest = islandExit();
        if (dest == null || dest.getWorld() == null) {
            String status = island.ensureIsland(false);
            dest = islandExit();
            if (dest == null || dest.getWorld() == null) {
                player.sendMessage(status);
                player.sendMessage("§cFarm island exit is not set yet.");
                markCooldown(player);
                return true;
            }
        }
        // Land beside the island portal so you must walk out of the frame first.
        Location land = offsetBesidePortal(dest);
        markCooldown(player);
        teleportSafe(player, land);
        mustExitPortal.add(player.getUniqueId());
        unlockFarmIsleHub(player, land);
        player.sendMessage("§a✦ §fFarm Island");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.35f, 1.4f);
        return true;
    }

    public boolean tryReturnFromIsland(Player player) {
        if (!enabled() || player == null) {
            return false;
        }
        World islandWorld = Bukkit.getWorld(island.worldName());
        if (islandWorld == null || !player.getWorld().equals(islandWorld)) {
            return false;
        }
        if (!standingInPortal(player)) {
            clearExitGate(player);
            return false;
        }
        if (mustExitPortal.contains(player.getUniqueId())) {
            return true;
        }
        if (onCooldown(player)) {
            return true;
        }
        Location hub = hubPortal();
        if (hub == null || hub.getWorld() == null) {
            player.sendMessage("§cHub farm portal is not set. Use Dev → Portals.");
            markCooldown(player);
            return true;
        }
        Location exit = offsetBesidePortal(hub);
        markCooldown(player);
        teleportSafe(player, exit);
        mustExitPortal.add(player.getUniqueId());
        player.sendMessage("§a✦ §fBack at the Farm");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.35f, 1.2f);
        return true;
    }

    public boolean shouldCancelVanillaPortal(Player player) {
        if (!enabled() || player == null) {
            return false;
        }
        World islandWorld = Bukkit.getWorld(island.worldName());
        if (islandWorld != null && player.getWorld().equals(islandWorld)) {
            return true;
        }
        Location hub = hubPortal();
        if (hub == null || hub.getWorld() == null || !player.getWorld().equals(hub.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(hub) <= hubRadius() * hubRadius();
    }

    /**
     * Build a standard 4x5 nether portal facing the player, save hub endpoint.
     */
    public String placeHubPortal(Player player, Block clicked) {
        if (player == null || clicked == null || clicked.getWorld() == null) {
            return "§cInvalid placement.";
        }
        BlockFace facing = yawToCardinal(player.getLocation().getYaw());
        BlockFace right = rotateRight(facing);
        // Portal plane is perpendicular to facing: width along `right`, depth along facing.
        Block base = clicked.getRelative(BlockFace.UP);
        for (int y = 0; y <= 4; y++) {
            for (int w = -1; w <= 2; w++) {
                boolean frame = y == 0 || y == 4 || w == -1 || w == 2;
                Block at = base.getRelative(right, w).getRelative(BlockFace.UP, y);
                if (frame) {
                    at.setType(Material.OBSIDIAN, false);
                } else {
                    at.setType(Material.AIR, false);
                }
            }
        }
        // Fill portal blocks (inner 2x3) with correct axis.
        org.bukkit.Axis portalAxis = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH)
                ? org.bukkit.Axis.X
                : org.bukkit.Axis.Z;
        for (int y = 1; y <= 3; y++) {
            for (int w = 0; w <= 1; w++) {
                Block at = base.getRelative(right, w).getRelative(BlockFace.UP, y);
                org.bukkit.block.data.Orientable data =
                        (org.bukkit.block.data.Orientable) Material.NETHER_PORTAL.createBlockData();
                data.setAxis(portalAxis);
                at.setBlockData(data, false);
            }
        }
        Location center = base.getRelative(right, 0).getRelative(BlockFace.UP, 1).getLocation().add(0.5, 0, 0.5);
        center.setYaw(player.getLocation().getYaw());
        center.setPitch(0f);
        // Stand point just in front of the portal.
        Location stand = center.clone().add(facing.getModX() * -1.5, 0, facing.getModZ() * -1.5);
        stand.setYaw(player.getLocation().getYaw());
        setHubPortal(stand);
        island.ensureIsland(false);
        return "§aHub farm portal set. Farming Lv. §f" + requiredFarmingLevel() + " §arequired.";
    }

    public void teleportToIsland(Player player) {
        String status = island.ensureIsland(false);
        Location dest = islandExit();
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage(status);
            return;
        }
        teleportSafe(player, dest);
        player.sendMessage("§aTeleported to farm island.");
    }

    private boolean standingInPortal(Player player) {
        Block feet = player.getLocation().getBlock();
        Block head = player.getLocation().clone().add(0, 1, 0).getBlock();
        return feet.getType() == Material.NETHER_PORTAL || head.getType() == Material.NETHER_PORTAL;
    }

    private void clearExitGate(Player player) {
        if (player != null) {
            mustExitPortal.remove(player.getUniqueId());
        }
    }

    private boolean onCooldown(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void markCooldown(Player player) {
        long ms = cooldownTicks() * 50L;
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + ms);
    }

    private void teleportSafe(Player player, Location dest) {
        Location safe = dest.clone();
        if (safe.getWorld() == null) {
            return;
        }
        // Instant — no next-tick delay / vanilla portal wait.
        player.setPortalCooldown(Math.max(20, cooldownTicks()));
        player.teleport(safe);
        markCooldown(player);
    }

    private Location offsetBesidePortal(Location hub) {
        Location out = hub.clone();
        // Push farther so landing is clearly outside the portal frame.
        float yaw = out.getYaw();
        double rad = Math.toRadians(yaw);
        out.add(-Math.sin(rad) * 2.6, 0, Math.cos(rad) * 2.6);
        return out;
    }

    private Location readLocation(String path) {
        if (!plugin.getConfig().isConfigurationSection(path)) {
            return null;
        }
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null && worldName.equals(island.worldName())) {
            world = island.ensureWorld();
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"),
                (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch")
        );
    }

    private void writeLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
    }

    /** Unlock Hub teleport "Farm Isle" the first time you portal in. */
    private static void unlockFarmIsleHub(Player player, Location land) {
        if (player == null) {
            return;
        }
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            return;
        }
        // HubService.setLocation(String, Location, boolean) does not exist — keep the
        // historical no-op so island landing is not written as a spawn (gameplay-neutral).
        try {
            Object hubPlugin = Bukkit.getPluginManager().getPlugin("AetherionHub");
            if (hubPlugin != null && land != null && land.getWorld() != null) {
                Object service = hubPlugin.getClass().getMethod("getHub").invoke(hubPlugin);
                if (service != null) {
                    try {
                        service.getClass().getMethod("setLocation", String.class, Location.class, boolean.class)
                                .invoke(service, "farm_isle", land, true);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (hub.isUnlocked(player, "farm_isle")) {
            return;
        }
        hub.unlockNew(player.getUniqueId(), "farm_isle");
        player.sendMessage("§b✦ §eNew area: §fFarm Isle§e.");
        player.sendMessage("§7Teleport unlocked — Manager → Teleports.");
    }

    private static BlockFace yawToCardinal(float yaw) {
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

    private static BlockFace rotateRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
