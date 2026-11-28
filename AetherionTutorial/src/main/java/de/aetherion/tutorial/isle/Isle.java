package de.aetherion.tutorial.isle;

import de.aetherion.tutorial.Marks;
import de.aetherion.tutorial.TutorialPlugin;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

/**
 * The isle role: everything that only exists on the backend that carries the world {@code morningbell}. Puts up the
 * cast, listens in on the gameplay plugins ({@link Notes}), runs the lessons ({@link Director}) and keeps the house
 * rules ({@link IsleRules}).
 */
public final class Isle {

    private final TutorialPlugin plugin;
    private final World world;
    private final Marks marks;
    private final Skins skins;
    private final Cast cast;
    private final Director director;
    private final IsleRules rules;
    private final Scenes scenes;
    private final Boss boss;
    private final BoosterGift boosters;
    private final Places places;
    private Notes notes;
    private BukkitTask slow;
    private BukkitTask fast;

    private Isle(TutorialPlugin plugin, World world, Marks marks) {
        this.plugin = plugin;
        this.world = world;
        this.marks = marks;
        this.skins = new Skins(plugin);
        this.cast = new Cast(plugin, this);
        this.director = new Director(plugin, this);
        this.rules = new IsleRules(plugin, this);
        this.scenes = new Scenes(plugin, this);
        this.boss = new Boss(plugin, this);
        this.boosters = new BoosterGift(plugin, this);
        this.places = new Places(this);
    }

    /** Null when the world has no {@code isle.json} (the island was not built by tools/isle). */
    public static Isle open(TutorialPlugin plugin, World world) {
        File file = new File(world.getWorldFolder(), "isle.json");
        if (!file.isFile()) {
            plugin.getLogger().severe("The world '" + world.getName() + "' has no isle.json: no lessons here. "
                    + "Build the island with AetherionTutorial/tools/isle and copy isle.json into the world folder.");
            return null;
        }
        Marks marks;
        try {
            marks = Marks.load(file);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Could not read " + file.getPath() + ": " + ex);
            return null;
        }
        Isle isle = new Isle(plugin, world, marks);
        isle.start();
        return isle;
    }

    private void start() {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(plugin.getConfig().getLong("isle.morning-time", 450L));
        org.bukkit.Location arrival = marks.at(world, "arrival");
        if (arrival != null) {
            world.setSpawnLocation(arrival);
        }
        places.register();
        skins.onSigned(cast::redress);
        Bukkit.getPluginManager().registerEvents(rules, plugin);
        Bukkit.getPluginManager().registerEvents(boss, plugin);
        Bukkit.getPluginManager().registerEvents(boosters, plugin);
        // one tick later: Quests registers its progress service in its own onEnable, FancyNpcs loads its bodies late
        Bukkit.getScheduler().runTask(plugin, () -> {
            notes = Notes.install(director);
            if (notes == null) {
                plugin.getLogger().warning("AetherionQuests is not here: the lessons cannot see what players do.");
            }
            cast.spawnAll();
            for (Player player : world.getPlayers()) {
                rules.admit(player, false);
            }
        });
        slow = Bukkit.getScheduler().runTaskTimer(plugin, director::heartbeat, 20L, 10L);
        fast = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            scenes.tick();
            boss.tick();
        }, 20L, 1L);
        plugin.getLogger().info("Morningbell is open: " + marks.keys().size() + " named spots, world '" + world.getName() + "'.");
    }

    public void close() {
        if (slow != null) {
            slow.cancel();
        }
        if (fast != null) {
            fast.cancel();
        }
        places.unregister();
        boss.shutdown();
        scenes.shutdown();
        director.shutdown();
        if (notes != null) {
            notes.uninstall();
            notes = null;
        }
        cast.removeAll();
    }

    TutorialPlugin plugin() {
        return plugin;
    }

    public World world() {
        return world;
    }

    public Marks marks() {
        return marks;
    }

    Skins skins() {
        return skins;
    }

    Cast cast() {
        return cast;
    }

    public Director director() {
        return director;
    }

    IsleRules rules() {
        return rules;
    }

    Scenes scenes() {
        return scenes;
    }

    Boss boss() {
        return boss;
    }

    BoosterGift boosters() {
        return boosters;
    }
}
