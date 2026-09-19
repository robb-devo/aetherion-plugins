package de.aetherion.mining.veins;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinsWorld {

    public static final String DEFAULT_NAME = "aether_veins";
    public static final int LAYOUT = 4;

    private final AetherionMining plugin;
    private final ConcurrentHashMap<UUID, Location> exits = new ConcurrentHashMap<>();
    private final File dataFile;
    private VeinsNpcs npcs;
    private long nextReset;
    private int generation;
    private int layout;
    private World world;

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

    public int hubY() {
        return plugin.getConfig().getInt("veins.hub-y", 220);
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

    public Location hubSpawn() {
        if (world == null) {
            return null;
        }
        return new Location(world, 0.5, hubY() + 1, 0.5, 0f, 0f);
    }

    public long nextResetAt() {
        return nextReset;
    }

    public World ensureLoaded() {
        if (layout < LAYOUT) {
            rebuildNow();
            layout = LAYOUT;
            if (nextReset <= 0L) {
                nextReset = System.currentTimeMillis() + resetMillis();
            }
            saveData();
        }
        if (world != null && Bukkit.getWorld(world.getUID()) != null) {
            applyWorld(world);
            return world;
        }
        World existing = Bukkit.getWorld(worldName());
        if (existing != null) {
            world = existing;
            applyWorld(world);
            decorate();
            return world;
        }
        world = create();
        if (world != null) {
            decorate();
        }
        if (nextReset <= 0L) {
            nextReset = System.currentTimeMillis() + resetMillis();
            saveData();
        }
        return world;
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
        player.teleport(spawn);
        player.setFallDistance(0f);
        player.sendMessage("§7The Veins. §8Mine everything but the hub. Corners have favorites.");
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
        reset("The Veins closed. Stone is new again.");
    }

    public void reset(String reason) {
        World current = world != null ? world : Bukkit.getWorld(worldName());
        Location fallback = overworldSpawn();
        if (current != null) {
            for (Player occupant : new ArrayList<>(current.getPlayers())) {
                occupant.sendMessage("§8" + reason);
                occupant.teleport(fallback);
                occupant.setFallDistance(0f);
                exits.remove(occupant.getUniqueId());
            }
            current.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
            File folder = current.getWorldFolder();
            if (!current.getPlayers().isEmpty()) {
                plugin.getLogger().warning("The Veins still has players. Reset skipped.");
                return;
            }
            if (!Bukkit.unloadWorld(current, false)) {
                plugin.getLogger().warning("Could not unload The Veins.");
                return;
            }
            world = null;
            deleteLater(folder, 20L);
            deleteLater(folder, 80L);
        } else {
            deleteLater(new File(Bukkit.getWorldContainer(), worldName()), 20L);
        }
        generation++;
        nextReset = System.currentTimeMillis() + resetMillis();
        saveData();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world = create();
            if (world != null) {
                decorate();
            }
        }, 100L);
        plugin.getLogger().info("The Veins reset. Generation " + generation + ".");
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

    private void decorate() {
        if (world == null) {
            return;
        }
        VeinsHub.build(world, hubY());
        if (npcs != null) {
            npcs.spawnExit(world, hubY());
        }
    }

    private World create() {
        WorldCreator creator = new WorldCreator(worldName());
        creator.generator(new VeinsChunkGenerator(radius(), hubY()));
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        creator.seed(System.currentTimeMillis());
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().warning("Could not create The Veins.");
            return null;
        }
        applyWorld(created);
        return created;
    }

    private void rebuildNow() {
        World existing = world != null ? world : Bukkit.getWorld(worldName());
        Location fallback = overworldSpawn();
        File folder = existing != null
                ? existing.getWorldFolder()
                : new File(Bukkit.getWorldContainer(), worldName());
        if (existing != null) {
            for (Player occupant : new ArrayList<>(existing.getPlayers())) {
                occupant.sendMessage("§8The Veins is being rebuilt.");
                occupant.teleport(fallback);
                occupant.setFallDistance(0f);
            }
            existing.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
            if (!existing.getPlayers().isEmpty() || !Bukkit.unloadWorld(existing, false)) {
                plugin.getLogger().warning("Could not unload The Veins for rebuild.");
            }
        }
        world = null;
        deleteRecursively(folder);
        plugin.getLogger().info("The Veins layout " + LAYOUT + " — old world removed.");
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
        target.setSpawnLocation(radius() + 8, hubY() + 1, 0);
        VeinsGuard.open(target);
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
        border.setCenter(0.5, 0.5);
        border.setSize(radius() * 2.0);
        border.setDamageBuffer(2.0);
        border.setWarningDistance(8);
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
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        nextReset = yaml.getLong("next-reset", 0L);
        generation = yaml.getInt("generation", 0);
        layout = yaml.getInt("layout", 0);
        exits.clear();
        if (yaml.isConfigurationSection("exits")) {
            for (String key : yaml.getConfigurationSection("exits").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String worldName = yaml.getString("exits." + key + ".world");
                    World world = worldName == null ? null : Bukkit.getWorld(worldName);
                    if (world == null) {
                        continue;
                    }
                    exits.put(id, new Location(
                            world,
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

    private void deleteLater(File folder, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (folder != null && folder.exists() && !deleteRecursively(folder)) {
                plugin.getLogger().warning("Could not fully delete The Veins folder.");
            }
        }, delayTicks);
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File child : children) {
                ok &= deleteRecursively(child);
            }
        }
        return file.delete() && ok;
    }
}
