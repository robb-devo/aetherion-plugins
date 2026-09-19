package de.aetherion.items.listener;

import de.aetherion.items.economy.QuarryItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class CoreShardDropListener {

    private static final double CHANCE = 0.20;

    private CoreShardDropListener() {
    }

    public static void register(Plugin plugin) {
        try {
            Class<? extends Event> eventClass = Class
                    .forName("de.aetherion.bossengine.event.BossDeathEvent")
                    .asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> handle(event);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    new Listener() {
                    },
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void handle(Event event) {
        try {
            Object instance = event.getClass().getMethod("getInstance").invoke(event);
            Object template = instance.getClass().getMethod("getTemplate").invoke(instance);
            String id = String.valueOf(template.getClass().getMethod("getId").invoke(template)).toLowerCase(Locale.ROOT);
            Object spawn = instance.getClass().getMethod("getSpawnLocation").invoke(instance);
            World world = spawn instanceof Location location ? location.getWorld() : null;
            if (id.startsWith("dungeon") || isDungeonWorld(world)) {
                return;
            }
            if (ThreadLocalRandom.current().nextDouble() >= CHANCE) {
                return;
            }
            ItemStack shard = QuarryItems.shard();
            Object killerObj = event.getClass().getMethod("getKiller").invoke(event);
            if (killerObj instanceof Player killer && killer.isOnline()) {
                var leftover = killer.getInventory().addItem(shard);
                leftover.values().forEach(left -> killer.getWorld().dropItemNaturally(killer.getLocation(), left));
                killer.sendMessage("§dQuarry Core Shard §7dropped from the world boss.");
                return;
            }
            if (spawn instanceof Location location && location.getWorld() != null) {
                location.getWorld().dropItemNaturally(location, shard);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean isDungeonWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("aedun_") || name.startsWith("ae_dun");
    }
}
