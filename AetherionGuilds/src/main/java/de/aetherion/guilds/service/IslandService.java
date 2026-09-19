package de.aetherion.guilds.service;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.world.IslandBuilder;
import de.aetherion.guilds.world.VoidChunkGenerator;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandService {

    private final JavaPlugin plugin;
    private World world;

    public IslandService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.world = loadWorld();
    }

    public World world() {
        return world;
    }

    public int spacing() {
        return Math.max(256, plugin.getConfig().getInt("island-spacing", 512));
    }

    public int originX(Guild guild) {
        return guild.plot() * spacing();
    }

    public int originZ(Guild guild) {
        return 0;
    }

    public int buildRadius() {
        return Math.max(8, plugin.getConfig().getInt("island-build-radius", 24));
    }

    public int buildRadius(Guild guild) {
        if (guild == null) {
            return buildRadius();
        }
        return IslandTiers.buildRadius(buildRadius(), guild.islandLevel());
    }

    public Location spawn(Guild guild) {
        if (world == null) {
            return null;
        }
        ensureBuilt(guild);
        return new Location(world, originX(guild) + 0.5, 65, originZ(guild) - 3.5, 0, 0);
    }

    public void ensureBuilt(Guild guild) {
        if (guild == null || world == null || guild.islandBuilt()) {
            return;
        }
        Location spawn = IslandBuilder.build(world, originX(guild), originZ(guild), guild.islandLevel());
        guild.setIslandBuilt(true);
        world.setSpawnLocation(spawn);
    }

    public void expand(Guild guild, int fromLevel) {
        if (guild == null || world == null) {
            return;
        }
        ensureBuilt(guild);
        IslandBuilder.upgrade(world, originX(guild), originZ(guild), fromLevel, guild.islandLevel());
    }

    public void clearPlot(Guild guild) {
        if (guild == null || world == null) {
            return;
        }
        IslandBuilder.clear(world, originX(guild), originZ(guild));
        guild.setIslandBuilt(false);
    }

    public void clearPlots(int fromInclusive, int toExclusive) {
        if (world == null) {
            return;
        }
        int gap = spacing();
        for (int plot = Math.max(0, fromInclusive); plot < Math.max(fromInclusive + 1, toExclusive); plot++) {
            IslandBuilder.clear(world, plot * gap, 0);
        }
    }

    public boolean isGuildWorld(World check) {
        return check != null && world != null && check.equals(world);
    }

    public int plotAt(Location location) {
        if (location == null || !isGuildWorld(location.getWorld())) {
            return -1;
        }
        return Math.max(0, (int) Math.round(location.getX() / (double) spacing()));
    }

    public boolean onOwnIsland(Player player, Guild guild) {
        if (player == null || guild == null || !isGuildWorld(player.getWorld())) {
            return false;
        }
        return plotAt(player.getLocation()) == guild.plot();
    }

    public void teleportHome(Player player, Location fallback) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location target = fallback;
        if (target == null || target.getWorld() == null) {
            target = Bukkit.getWorlds().isEmpty() ? player.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(target);
    }

    private World loadWorld() {
        String name = plugin.getConfig().getString("island-world", "aether_guilds");
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
