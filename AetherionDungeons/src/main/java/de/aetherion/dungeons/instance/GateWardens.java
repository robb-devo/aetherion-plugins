package de.aetherion.dungeons.instance;

import de.aetherion.core.npc.CastTroupe;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Gate Warden of each dungeon lobby as a real person: a body on a Core {@link CastTroupe}, one per instance
 * world. The tagged interaction box around him stays what players click and hit; this is only who they see.
 * A body leaves with its world, so finished instances never pile up.
 */
final class GateWardens implements Listener {

    private static GateWardens active;

    private final JavaPlugin plugin;
    private final CastTroupe troupe;
    private Consumer<Player> ready;

    private GateWardens(JavaPlugin plugin) {
        this.plugin = plugin;
        this.troupe = CastTroupe.open(plugin, "gate", (player, key) -> {
            if (ready != null) {
                ready.accept(player);
            }
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            relieve(world.getEntities());
        }
    }

    private static GateWardens of(Plugin plugin) {
        if (active == null && plugin instanceof JavaPlugin java && plugin.isEnabled()) {
            active = new GateWardens(java);
        }
        return active;
    }

    /** Stands the warden of {@code world} at {@code at} (a second call for the same world moves him). */
    static void seat(Plugin plugin, World world, Location at) {
        GateWardens wardens = of(plugin);
        if (wardens != null && world != null && at != null) {
            wardens.place(world, at);
        }
    }

    private void place(World world, Location at) {
        String key = key(world);
        troupe.remove(key);
        troupe.spawn(key, at, CastTroupe.Look
                .of("Gate Warden", "gate_warden", Material.IRON_SWORD, Color.fromRGB(0x2e2a36))
                .stand(Villager.Profession.NITWIT, Villager.Type.PLAINS)
                .labelled("§eGate Warden"));
        troupe.habit(key, CastTroupe.Habit.watch(8, 16));
    }

    /** What a click on the warden himself does (the box around him has its own listener). */
    static void onClick(Plugin plugin, Consumer<Player> ready) {
        GateWardens wardens = of(plugin);
        if (wardens != null) {
            wardens.ready = ready;
        }
    }

    static void shutdown() {
        if (active != null) {
            active.troupe.close();
            HandlerList.unregisterAll(active);
            active = null;
        }
    }

    private static String key(World world) {
        return world.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        troupe.remove(key(event.getWorld()));
    }

    /** Lobbies built before this build still hold a villager warden (the warm pool keeps worlds ready). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> loaded = List.copyOf(event.getEntities());
        if (loaded.stream().anyMatch(entity -> entity instanceof Villager)) {
            Bukkit.getScheduler().runTask(plugin, () -> relieve(loaded));
        }
    }

    private void relieve(Collection<Entity> entities) {
        if (!troupe.playerModels()) {
            return;
        }
        for (Entity entity : entities) {
            if (entity instanceof Villager && entity.isValid()
                    && entity.getScoreboardTags().contains(PrototypeDungeonBuilder.READY_NPC)) {
                Location at = entity.getLocation();
                World world = entity.getWorld();
                entity.remove();
                place(world, at);
            }
        }
    }
}
