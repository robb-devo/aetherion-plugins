package de.aetherion.guilds.world;

import de.aetherion.core.world.VoidChunkGenerator;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Island worlds live in real void. Two things used to break that:
 * <ul>
 *   <li>the generator is not stored in the world folder, so whoever loads the world first decides. A world
 *   manager (or an older boot) loading {@code aether_islands} without it turned every new chunk into overworld;
 *   our own loader then just took the already-loaded world as it was;</li>
 *   <li>nothing remembered the choice for the next start.</li>
 * </ul>
 * Here: the generator is written into {@code bukkit.yml} ({@code worlds.<name>.generator: AetherionGuilds},
 * served by {@link de.aetherion.guilds.AetherionGuilds#getDefaultWorldGenerator}), and a world that is already
 * loaded on the wrong generator is unloaded and loaded again on void. Terrain that was generated before is
 * cleaned by {@link VoidScrubber}.
 */
public final class VoidWorlds {

    private VoidWorlds() {
    }

    public static boolean isVoid(World world) {
        return world != null && world.getGenerator() instanceof VoidChunkGenerator;
    }

    /** Load (or re-load) an island world on the void generator; {@code rules} applies gamerules either way. */
    public static World load(JavaPlugin plugin, String name, Consumer<World> rules) {
        persist(plugin, name);
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            if (isVoid(existing)) {
                rules.accept(existing);
                return existing;
            }
            boolean reloaded = false;
            if (existing.getPlayers().isEmpty()) {
                try {
                    reloaded = Bukkit.unloadWorld(existing, true);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not unload '" + name + "': " + exception.getMessage());
                }
            }
            if (!reloaded) {
                plugin.getLogger().warning("Island world '" + name + "' is running on a normal generator and could not"
                        + " be reloaded right now. The void scrubber keeps new terrain out; a restart fixes it for good.");
                rules.accept(existing);
                return existing;
            }
            plugin.getLogger().warning("Island world '" + name + "' had been loaded without the void generator"
                    + " (world manager / older boot). Reloaded it on void.");
        }
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        World created = creator.createWorld();
        if (created != null) {
            rules.accept(created);
            if (!isVoid(created)) {
                plugin.getLogger().warning("Island world '" + name + "' still reports a non-void generator.");
            }
        }
        return created;
    }

    /**
     * {@code bukkit.yml → worlds.<name>.generator}: the server and world managers use it whenever they load the
     * world themselves. An entry someone set by hand is left alone.
     */
    static void persist(JavaPlugin plugin, String name) {
        if (!plugin.getConfig().getBoolean("void.persist-generator", true)) {
            return;
        }
        File file = new File("bukkit.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String path = "worlds." + name + ".generator";
            String current = yaml.getString(path, "");
            if (current != null && !current.isBlank()) {
                if (!current.split(":")[0].trim().equalsIgnoreCase(plugin.getName())) {
                    plugin.getLogger().info("bukkit.yml sets generator '" + current + "' for " + name + "; left as is.");
                }
                return;
            }
            yaml.set(path, plugin.getName());
            yaml.save(file);
            plugin.getLogger().info("bukkit.yml: " + name + " now always loads on the void generator.");
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not write the void generator into bukkit.yml: " + exception.getMessage());
        }
    }
}
