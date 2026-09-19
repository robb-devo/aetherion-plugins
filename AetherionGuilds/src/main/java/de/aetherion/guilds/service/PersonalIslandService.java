package de.aetherion.guilds.service;

import de.aetherion.core.world.VoidChunkGenerator;
import de.aetherion.guilds.model.IslandBiome;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.world.IslandBuilder;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PersonalIslandService {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PersonalIsland> islands = new ConcurrentHashMap<>();
    private final Set<UUID> islandFlight = ConcurrentHashMap.newKeySet();
    private World world;
    private MinionService minions;

    public PersonalIslandService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "personal_islands.yml");
    }

    public void enable() {
        this.world = loadWorld();
        load();
    }

    public void attachMinions(MinionService minions) {
        this.minions = minions;
    }

    public World world() {
        return world;
    }

    public Collection<PersonalIsland> all() {
        return islands.values();
    }

    public PersonalIsland byOwner(UUID ownerId) {
        return ownerId == null ? null : islands.get(ownerId);
    }

    public PersonalIsland byPlot(int plot) {
        for (PersonalIsland island : islands.values()) {
            if (island.plot() == plot) {
                return island;
            }
        }
        return null;
    }

    public int spacing() {
        return Math.max(256, plugin.getConfig().getInt("personal-island-spacing",
                plugin.getConfig().getInt("island-spacing", 512)));
    }

    public int originX(PersonalIsland island) {
        return island.plot() * spacing();
    }

    public int originZ(PersonalIsland island) {
        return 0;
    }

    public int buildRadius() {
        return Math.max(8, plugin.getConfig().getInt("island-build-radius", 24));
    }

    public int buildRadius(PersonalIsland island) {
        if (island == null) {
            return buildRadius();
        }
        return IslandTiers.buildRadius(buildRadius(), island.islandLevel());
    }

    public boolean isPersonalWorld(World check) {
        return check != null && world != null && check.equals(world);
    }

    public int plotAt(Location location) {
        if (location == null || !isPersonalWorld(location.getWorld())) {
            return -1;
        }
        return Math.max(0, (int) Math.round(location.getX() / (double) spacing()));
    }

    public boolean onOwnIsland(Player player, PersonalIsland island) {
        if (player == null || island == null || !isPersonalWorld(player.getWorld())) {
            return false;
        }
        return plotAt(player.getLocation()) == island.plot();
    }

    public Location spawn(PersonalIsland island) {
        if (world == null || island == null) {
            return null;
        }
        ensureBuilt(island);
        return new Location(world, originX(island) + 0.5, 65, originZ(island) - 3.5, 0, 0);
    }

    public void ensureBuilt(PersonalIsland island) {
        if (island == null || world == null || island.islandBuilt()) {
            return;
        }
        IslandBuilder.build(world, originX(island), originZ(island), island.islandLevel(), island.biome());
        island.setIslandBuilt(true);
    }

    public PersonalIsland create(Player player, IslandBiome biome) {
        if (player == null) {
            return null;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return null;
        }
        if (byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§cYou already have a personal island.");
            return null;
        }
        IslandBiome chosen = biome == null ? IslandBiome.PLAINS : biome;
        PersonalIsland island = new PersonalIsland(player.getUniqueId(), nextPlot(), chosen);
        islands.put(island.ownerId(), island);
        ensureBuilt(island);
        save();
        player.sendMessage("§aPersonal island claimed §8(§f" + chosen.display() + "§8)§a.");
        player.sendMessage("§7Open §f/island §7anytime. Friends can visit while the server is online.");
        goHome(player);
        return island;
    }

    public void goHome(Player player) {
        if (player == null) {
            return;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return;
        }
        PersonalIsland island = byOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§7Pick a biome first to create your island.");
            return;
        }
        Location spawn = spawn(island);
        if (spawn == null) {
            player.sendMessage("§cIsland world is not ready.");
            return;
        }
        player.teleport(spawn);
        player.sendMessage("§aWelcome home.");
        Bukkit.getScheduler().runTask(plugin, () -> refreshFlight(player));
    }

    public void visit(Player visitor, UUID ownerId) {
        if (visitor == null || ownerId == null) {
            return;
        }
        PersonalIsland island = byOwner(ownerId);
        if (island == null) {
            visitor.sendMessage("§cThat player has no personal island yet.");
            return;
        }
        Location spawn = spawn(island);
        if (spawn == null) {
            visitor.sendMessage("§cIsland world is not ready.");
            return;
        }
        visitor.teleport(spawn);
        Bukkit.getScheduler().runTask(plugin, () -> refreshFlight(visitor));
    }

    public void refreshFlight(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        PersonalIsland island = byOwner(player.getUniqueId());
        boolean onOwn = island != null && onOwnIsland(player, island);
        if (onOwn) {
            player.setAllowFlight(true);
            islandFlight.add(player.getUniqueId());
            return;
        }
        if (islandFlight.remove(player.getUniqueId())) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public void clearFlight(Player player) {
        if (player == null) {
            return;
        }
        if (!islandFlight.remove(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public void upgradeIsland(Player player) {
        PersonalIsland island = byOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§cYou do not have a personal island.");
            return;
        }
        if (!island.canUpgradeIsland()) {
            player.sendMessage("§cYour island is already max level.");
            return;
        }
        int from = island.islandLevel();
        IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(from);
        if (cost.isEmpty()) {
            player.sendMessage("§cNo upgrade available.");
            return;
        }
        if (AetherionItemsAccess.count(player, "compacted_cobblestone") < cost.compactedCobble()) {
            player.sendMessage("§cNeed §f" + cost.compactedCobble() + " Compacted Cobblestone§c.");
            return;
        }
        if (AetherionItemsAccess.count(player, "quarry_core") < cost.cores()) {
            player.sendMessage("§cNeed §f" + cost.cores() + " Quarry Core§c.");
            return;
        }
        if (AetherionItemsAccess.coins(player) < cost.coins()) {
            player.sendMessage("§cNeed §6" + cost.coins() + " coins§c.");
            return;
        }
        AetherionItemsAccess.take(player, "compacted_cobblestone", cost.compactedCobble());
        AetherionItemsAccess.take(player, "quarry_core", cost.cores());
        if (!AetherionItemsAccess.takeCoins(player, cost.coins())) {
            player.sendMessage("§cCould not take coins.");
            return;
        }
        island.setIslandLevel(from + 1);
        IslandBuilder.upgrade(world, originX(island), originZ(island), from, island.islandLevel(), island.biome());
        save();
        player.sendMessage("§aIsland upgraded to §fLv." + island.islandLevel()
                + "§a. Build radius is now §f" + buildRadius(island) + "§a.");
    }

    public void wipeAll() {
        Location spawn = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPersonalWorld(player.getWorld())) {
                if (spawn != null) {
                    player.teleport(spawn);
                }
                player.sendMessage("§7Personal islands were wiped.");
            }
        }
        if (minions != null) {
            for (PersonalIsland island : List.copyOf(islands.values())) {
                minions.removePersonalVisuals(island);
            }
        }
        for (PersonalIsland island : List.copyOf(islands.values())) {
            IslandBuilder.clear(world, originX(island), originZ(island));
        }
        islands.clear();
        save();
        plugin.getLogger().info("Wiped all personal islands.");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (PersonalIsland island : islands.values()) {
            String path = "islands." + island.ownerId();
            config.set(path + ".plot", island.plot());
            config.set(path + ".biome", island.biome().name());
            config.set(path + ".island-built", island.islandBuilt());
            config.set(path + ".island-level", island.islandLevel());
            for (QuarryMinion minion : island.minions()) {
                String m = path + ".minions." + minion.id();
                config.set(m + ".type", minion.type());
                config.set(m + ".x", minion.x());
                config.set(m + ".y", minion.y());
                config.set(m + ".z", minion.z());
                config.set(m + ".lastTick", minion.lastTick());
                config.set(m + ".stored", minion.stored());
                config.set(m + ".storedCompressed", minion.storedCompressed());
                config.set(m + ".storedCompacted", minion.storedCompacted());
                config.set(m + ".level", minion.level());
                config.set(m + ".processor", minion.processor().name());
            }
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save personal_islands.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("islands");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            PersonalIsland island = new PersonalIsland(
                    owner,
                    section.getInt("plot"),
                    IslandBiome.fromId(section.getString("biome"))
            );
            island.setIslandBuilt(section.getBoolean("island-built", false));
            island.setIslandLevel(section.getInt("island-level", 1));
            ConfigurationSection minionsSection = section.getConfigurationSection("minions");
            if (minionsSection != null) {
                for (String minionKey : minionsSection.getKeys(false)) {
                    try {
                        ConfigurationSection minion = minionsSection.getConfigurationSection(minionKey);
                        if (minion == null) {
                            continue;
                        }
                        island.minions().add(new QuarryMinion(
                                UUID.fromString(minionKey),
                                minion.getString("type", QuarryMinion.COBBLE),
                                minion.getInt("x"),
                                minion.getInt("y"),
                                minion.getInt("z"),
                                minion.getLong("lastTick"),
                                minion.getInt("stored"),
                                minion.getInt("level", 1),
                                QuarryMinion.Processor.parse(minion.getString("processor")),
                                minion.getInt("storedCompressed"),
                                minion.getInt("storedCompacted")
                        ));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            islands.put(owner, island);
        }
    }

    private int nextPlot() {
        int max = -1;
        for (PersonalIsland island : islands.values()) {
            max = Math.max(max, island.plot());
        }
        return max + 1;
    }

    private World loadWorld() {
        String name = plugin.getConfig().getString("personal-island-world", "aether_islands");
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            applyRules(existing);
            return existing;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        World created = creator.createWorld();
        if (created != null) {
            applyRules(created);
        }
        return created;
    }

    private void applyRules(World world) {
        world.setKeepSpawnInMemory(false);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
    }
}
