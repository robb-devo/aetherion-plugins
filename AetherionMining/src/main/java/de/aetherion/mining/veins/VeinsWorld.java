package de.aetherion.mining.veins;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.HubAccess;
import de.aetherion.mining.AetherionMining;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Amethyst Area ({@code aether_veins}) lifecycle.
 *
 * <p>The finished BreadBuilds hub is never deleted, rebuilt, or pasted over.
 * Dig zones outside hub clearance are painted once (layout) and restored
 * identically every {@code veins.reset-hours} from the same seed — no per-block regen.
 */
public final class VeinsWorld {

    public static final String DEFAULT_NAME = "aether_veins";
    /** Dig-zone Crystal Hollows polish around live hub — not the old Deep Veins megamap. */
    /** Dig layout: solid flush cube (no clearance air gap). */
    public static final int LAYOUT = 6;

    private final AetherionMining plugin;
    private final ConcurrentHashMap<UUID, Location> exits = new ConcurrentHashMap<>();
    private final File dataFile;
    private VeinsNpcs npcs;
    private long nextReset;
    private int generation;
    private int layout;
    private boolean digZonesReady;
    private boolean painting;
    private World world;
    private boolean resetting;

    public VeinsWorld(AetherionMining plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "veins.yml");
        loadData();
    }

    public void bind(VeinsNpcs npcs) {
        this.npcs = npcs;
    }

    public String worldName() {
        return plugin.getConfig().getString("veins.world", DEFAULT_NAME);
    }

    public int radius() {
        return Math.max(16, plugin.getConfig().getInt("veins.radius", 250));
    }

    /** @deprecated Stale prototype key — use {@link #spawnY()}. Kept so old configs do not NPE. */
    @Deprecated
    public int hubY() {
        return spawnY();
    }

    public int spawnProtectRadius() {
        return Math.max(4, plugin.getConfig().getInt("veins.spawn-protect-radius", 20));
    }

    /** Half-extent of the solid dig cube in X/Z (schematic sits flush in the middle). */
    public int digHalfExtent() {
        int configured = plugin.getConfig().getInt("veins.dig-half-extent", 0);
        if (configured > 0) {
            return Math.max(48, configured);
        }
        // Legacy keys → treat outer radius as half-extent; ignore old clearance gap.
        int outer = plugin.getConfig().getInt("veins.dig-outer-radius", 0);
        if (outer > 0) {
            return Math.max(48, outer);
        }
        return Math.max(48, radius() - 20);
    }

    public int digDepth() {
        return Math.max(16, plugin.getConfig().getInt("veins.dig-depth", 48));
    }

    public int digHeight() {
        return Math.max(8, plugin.getConfig().getInt("veins.dig-height", 28));
    }

    /** @deprecated Clearance air-gap is retired — schematic footprint is skipped by voxel mask. */
    @Deprecated
    public int digHubClearance() {
        return 0;
    }

    public int digOuterRadius() {
        return digHalfExtent();
    }

    public long digSeed() {
        return plugin.getConfig().getLong("veins.dig-seed", 20260922L);
    }

    public long resetMillis() {
        return Math.max(1L, plugin.getConfig().getLong("veins.reset-hours", 24)) * 60L * 60L * 1000L;
    }

    public boolean keepInventory() {
        return plugin.getConfig().getBoolean("veins.keep-inventory", true);
    }

    public World world() {
        return world;
    }

    public boolean isVeins(World check) {
        return check != null && check.getName().equalsIgnoreCase(worldName());
    }

    public boolean isDigZonesReady() {
        return digZonesReady;
    }

    public boolean isResetting() {
        return resetting;
    }

    /** Resolved hub spawn: Hub {@code amethyst} if planted, else {@code veins.spawn-*}. */
    public Location hubSpawn() {
        World target = world != null ? world : Bukkit.getWorld(worldName());
        if (target == null) {
            return null;
        }
        HubAccess hub = AetherServices.hub();
        if (hub != null) {
            Location planted = hub.location("amethyst");
            if (planted != null && planted.getWorld() != null
                    && planted.getWorld().getName().equalsIgnoreCase(worldName())) {
                return planted.clone();
            }
        }
        return configSpawn(target);
    }

    public Location configSpawn(World target) {
        if (target == null) {
            return null;
        }
        double x = plugin.getConfig().getDouble("veins.spawn-x", 8.5);
        double y = plugin.getConfig().getDouble("veins.spawn-y", 18.0);
        double z = plugin.getConfig().getDouble("veins.spawn-z", 8.5);
        float yaw = (float) plugin.getConfig().getDouble("veins.spawn-yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("veins.spawn-pitch", 0);
        return new Location(target, x, y, z, yaw, pitch);
    }

    public int spawnX() {
        Location spawn = hubSpawn();
        return spawn == null ? 8 : spawn.getBlockX();
    }

    public int spawnY() {
        Location spawn = hubSpawn();
        if (spawn != null) {
            return spawn.getBlockY();
        }
        return (int) Math.floor(plugin.getConfig().getDouble("veins.spawn-y", 18.0));
    }

    public int spawnZ() {
        Location spawn = hubSpawn();
        return spawn == null ? 8 : spawn.getBlockZ();
    }

    public long nextResetAt() {
        return nextReset;
    }

    /**
     * Load the live Amethyst Mines world. Never deletes BreadBuilds / never rebuilds
     * the Deep Veins prototype megamap.
     */
    public World ensureLoaded() {
        if (world != null && Bukkit.getWorld(world.getUID()) != null) {
            applyWorld(world);
            ensureDigZones(null, false);
            return world;
        }
        World existing = Bukkit.getWorld(worldName());
        if (existing != null) {
            world = existing;
            applyWorld(world);
            ensureDigZones(null, false);
            if (nextReset <= 0L) {
                nextReset = System.currentTimeMillis() + resetMillis();
                saveData();
            }
            return world;
        }
        // Refuse to invent the old Y~220 stone-cube prototype.
        plugin.getLogger().severe(
                "World '" + worldName() + "' is not loaded. "
                        + "Paste/keep the finished Amethyst Mines (BreadBuilds) — "
                        + "Deep Veins prototype generation is disabled."
        );
        return null;
    }

    /**
     * Paint dig volume once when layout is behind, or force re-paint for admin.
     * Returns 1 if a paint job started, 0 if skipped / already running.
     */
    public int ensureDigZones(CommandSender sender, boolean force) {
        World target = world != null ? world : Bukkit.getWorld(worldName());
        if (target == null) {
            if (sender != null) {
                sender.sendMessage("§cAmethyst Area world is not loaded.");
            }
            return 0;
        }
        world = target;
        purgeNpcs(target);
        if (painting) {
            if (sender != null) {
                sender.sendMessage("§eDig paint already running…");
            }
            return 0;
        }
        if (!force && digZonesReady && layout >= LAYOUT) {
            return 0;
        }
        Location spawn = hubSpawn();
        if (spawn == null) {
            spawn = configSpawn(target);
        }
        final Location paintSpawn = spawn;
        final CommandSender out = sender != null ? sender : Bukkit.getConsoleSender();
        painting = true;
        digZonesReady = false;
        VeinsDigZones.paintAsync(
                plugin,
                target,
                paintSpawn.getBlockX(),
                paintSpawn.getBlockY(),
                paintSpawn.getBlockZ(),
                digHalfExtent(),
                digDepth(),
                digHeight(),
                digSeed(),
                out,
                plugin.getDataFolder(),
                () -> {
                    digZonesReady = true;
                    layout = LAYOUT;
                    painting = false;
                    saveData();
                    VeinsGuard.open(target);
                    VeinsGuard.protectSpawn(target, paintSpawn, spawnProtectRadius());
                    purgeNpcs(target);
                    scheduleSoftLight(out, paintSpawn);
                }
        );
        return 1;
    }

    private void purgeNpcs(World target) {
        if (npcs != null) {
            npcs.clearAllInWorld(target);
        }
    }

    public void rememberExit(Player player) {
        if (player == null || isVeins(player.getWorld())) {
            return;
        }
        exits.put(player.getUniqueId(), player.getLocation().clone());
        saveData();
    }

    public Location popExit(Player player) {
        if (player == null) {
            return overworldSpawn();
        }
        Location saved = exits.remove(player.getUniqueId());
        saveData();
        if (saved != null && saved.getWorld() != null && !isVeins(saved.getWorld())) {
            return saved;
        }
        return overworldSpawn();
    }

    public boolean enter(Player player) {
        World veins = ensureLoaded();
        if (player == null || veins == null) {
            return false;
        }
        rememberExit(player);
        if (player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        Location spawn = hubSpawn();
        if (spawn == null) {
            return false;
        }
        player.teleport(spawn);
        player.setFallDistance(0f);
        player.sendMessage("§7Amethyst Mines. §8Dig the four zones. Hub stays put — mined ore stays gone until daily reset.");
        player.playSound(spawn, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        return true;
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        Location target = popExit(player);
        player.teleport(target);
        player.setFallDistance(0f);
        player.sendMessage("§7Back to daylight.");
        player.playSound(target, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
        return true;
    }

    public void tickReset() {
        if (nextReset <= 0L) {
            nextReset = System.currentTimeMillis() + resetMillis();
            saveData();
            return;
        }
        if (System.currentTimeMillis() < nextReset) {
            return;
        }
        reset("Amethyst Area reset — dig zones restored.");
    }

    /**
     * Full dig-zone restore from the saved snapshot (same structure/ores).
     * Falls back to re-paint from {@code dig-seed} if the snapshot is missing.
     * Hub blocks are never rewritten. Does not delete the world folder.
     */
    public void reset(String reason) {
        if (resetting) {
            plugin.getLogger().warning("Amethyst dig reset already in progress.");
            return;
        }
        World current = world != null ? world : Bukkit.getWorld(worldName());
        if (current == null) {
            plugin.getLogger().warning("Cannot reset dig zones — world not loaded.");
            return;
        }
        resetting = true;
        Location fallback = overworldSpawn();
        Location spawn = hubSpawn();
        for (Player occupant : new ArrayList<>(current.getPlayers())) {
            occupant.sendMessage("§8" + reason);
            if (spawn != null) {
                occupant.teleport(spawn);
            } else {
                occupant.teleport(fallback);
            }
            occupant.setFallDistance(0f);
        }
        generation++;
        nextReset = System.currentTimeMillis() + resetMillis();
        saveData();
        CommandSender console = Bukkit.getConsoleSender();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                world = current;
                applyWorld(current);
                int restored = VeinsDigSnapshot.restore(current, plugin.getDataFolder(), console);
                if (restored < 0) {
                    digZonesReady = false;
                    ensureDigZones(console, true);
                } else {
                    digZonesReady = true;
                    layout = LAYOUT;
                    saveData();
                    if (spawn != null) {
                        VeinsGuard.protectSpawn(current, spawn, spawnProtectRadius());
                        scheduleSoftLight(console, spawn);
                    }
                }
                plugin.getLogger().info("Amethyst dig zones restored. Generation " + generation + ".");
            } finally {
                resetting = false;
            }
        });
    }

    public void rescueIfBuried(Player player) {
        if (player == null || !isVeins(player.getWorld())) {
            return;
        }
        Location feet = player.getLocation();
        if (feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable()) {
            return;
        }
        Location spawn = hubSpawn();
        if (spawn != null) {
            player.teleport(spawn);
            player.setFallDistance(0f);
        }
    }

    public boolean isProtected(Location location) {
        return VeinsHub.protectedSpot(location, hubSpawn(), spawnProtectRadius());
    }

    private void applyWorld(World target) {
        target.setAutoSave(true);
        target.setKeepSpawnInMemory(true);
        target.setSpawnFlags(false, false);
        target.setDifficulty(Difficulty.PEACEFUL);
        target.setPVP(false);
        target.setTime(18000L);
        target.setStorm(false);
        target.setThundering(false);
        Location spawn = hubSpawn();
        if (spawn != null) {
            target.setSpawnLocation(spawn);
            VeinsGuard.protectSpawn(target, spawn, spawnProtectRadius());
        }
        VeinsGuard.open(target);
        purgeNpcs(target);
        target.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        target.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        target.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        target.setGameRule(GameRule.MOB_GRIEFING, false);
        target.setGameRule(GameRule.DO_FIRE_TICK, false);
        target.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        target.setGameRule(GameRule.KEEP_INVENTORY, keepInventory());
        target.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        target.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        target.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        target.setGameRule(GameRule.DISABLE_RAIDS, true);
        var border = target.getWorldBorder();
        if (spawn != null) {
            border.setCenter(spawn.getX(), spawn.getZ());
        } else {
            border.setCenter(8.5, 8.5);
        }
        border.setSize(radius() * 2.0);
        border.setDamageBuffer(2.0);
        border.setWarningDistance(8);
    }

    private void scheduleSoftLight(CommandSender sender, Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("veins.softlight-on-paint", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runSoftLight(
                sender,
                spawn.getWorld(),
                spawn.getBlockX(),
                spawn.getBlockZ(),
                digOuterRadius() + 16
        ), 40L);
    }

    /**
     * Reuses Hub {@code SoftLightPass} via reflection (no hard Hub compile dep).
     */
    public void runSoftLight(CommandSender sender, World target, int centerX, int centerZ, int softRadius) {
        if (target == null) {
            if (sender != null) {
                sender.sendMessage("§cNo world for softlight.");
            }
            return;
        }
        CommandSender out = sender != null ? sender : Bukkit.getConsoleSender();
        try {
            Class<?> pass = Class.forName("de.aetherion.hub.util.SoftLightPass");
            pass.getMethod(
                    "run",
                    org.bukkit.plugin.java.JavaPlugin.class,
                    CommandSender.class,
                    World.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class
            ).invoke(
                    null,
                    softLightPlugin(),
                    out,
                    target,
                    centerX,
                    centerZ,
                    Math.max(64, softRadius),
                    plugin.getConfig().getInt("veins.softlight-min", 7),
                    plugin.getConfig().getInt("veins.softlight-step", 5),
                    plugin.getConfig().getInt("veins.softlight-level", 10)
            );
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            out.sendMessage("§cSoftlight needs AetherionHub loaded (SoftLightPass).");
            plugin.getLogger().warning("SoftLightPass unavailable: " + e.getMessage());
        }
    }

    private org.bukkit.plugin.java.JavaPlugin softLightPlugin() {
        org.bukkit.plugin.Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (hub instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            return javaPlugin;
        }
        return plugin;
    }

    private Location overworldSpawn() {
        if (Bukkit.getWorlds().isEmpty()) {
            return new Location(null, 0, 64, 0);
        }
        World main = Bukkit.getWorlds().get(0);
        if (isVeins(main) && Bukkit.getWorlds().size() > 1) {
            main = Bukkit.getWorlds().get(1);
        }
        return main.getSpawnLocation();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            nextReset = 0L;
            generation = 0;
            layout = 0;
            digZonesReady = false;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        nextReset = yaml.getLong("next-reset", 0L);
        generation = yaml.getInt("generation", 0);
        layout = yaml.getInt("layout", 0);
        digZonesReady = yaml.getBoolean("dig-zones-ready", layout >= LAYOUT);
        exits.clear();
        if (yaml.isConfigurationSection("exits")) {
            for (String key : yaml.getConfigurationSection("exits").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String worldName = yaml.getString("exits." + key + ".world");
                    World loaded = worldName == null ? null : Bukkit.getWorld(worldName);
                    if (loaded == null) {
                        continue;
                    }
                    exits.put(id, new Location(
                            loaded,
                            yaml.getDouble("exits." + key + ".x"),
                            yaml.getDouble("exits." + key + ".y"),
                            yaml.getDouble("exits." + key + ".z"),
                            (float) yaml.getDouble("exits." + key + ".yaw"),
                            (float) yaml.getDouble("exits." + key + ".pitch")
                    ));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveData() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-reset", nextReset);
        yaml.set("generation", generation);
        yaml.set("layout", layout);
        yaml.set("dig-zones-ready", digZonesReady);
        exits.forEach((id, location) -> {
            if (location == null || location.getWorld() == null) {
                return;
            }
            String path = "exits." + id;
            yaml.set(path + ".world", location.getWorld().getName());
            yaml.set(path + ".x", location.getX());
            yaml.set(path + ".y", location.getY());
            yaml.set(path + ".z", location.getZ());
            yaml.set(path + ".yaw", location.getYaw());
            yaml.set(path + ".pitch", location.getPitch());
        });
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save veins.yml: " + exception.getMessage());
        }
    }
}
