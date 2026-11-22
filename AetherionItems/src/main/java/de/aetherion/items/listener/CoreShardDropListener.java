package de.aetherion.items.listener;

import de.aetherion.items.economy.QuarryItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CoreShardDropListener {

    private static final double CHANCE = 0.20;
    /** Used when the boss's loot table cannot be read. */
    private static final double DEFAULT_THRESHOLD_PERCENT = 5.0;

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

    /**
     * 20 % Quarry Core Shard per overworld boss kill, rolled for every fighter over the boss's loot participation
     * threshold (A-21, T-15; before, only the last hit rolled). No share map: the killer rolls once, as before.
     */
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
            Object shares = event.getClass().getMethod("getShareMap").invoke(event);
            if (shares instanceof Map<?, ?> map && !map.isEmpty()) {
                double threshold = thresholdPercent(template);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof UUID playerId) || !(entry.getValue() instanceof Number share)) {
                        continue;
                    }
                    double percent = share.doubleValue() * 100.0;
                    if (percent <= 0.0 || percent < threshold) {
                        continue;
                    }
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline() && ThreadLocalRandom.current().nextDouble() < CHANCE) {
                        give(player);
                    }
                }
                return;
            }
            if (ThreadLocalRandom.current().nextDouble() >= CHANCE) {
                return;
            }
            Object killerObj = event.getClass().getMethod("getKiller").invoke(event);
            if (killerObj instanceof Player killer && killer.isOnline()) {
                give(killer);
                return;
            }
            if (spawn instanceof Location location && location.getWorld() != null) {
                location.getWorld().dropItemNaturally(location, QuarryItems.shard());
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
    }

    private static void give(Player player) {
        var leftover = player.getInventory().addItem(QuarryItems.shard());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage("§dQuarry Core Shard §7dropped from the world boss.");
    }

    /** The boss's loot participation threshold in percent (BossEngine default 0), read without a compile link. */
    private static double thresholdPercent(Object template) {
        try {
            Object table = template.getClass().getMethod("getLootTable").invoke(template);
            if (table == null) {
                return DEFAULT_THRESHOLD_PERCENT;
            }
            Object value = table.getClass().getMethod("getParticipationThresholdPercent").invoke(table);
            return value instanceof Number number ? Math.max(0.0, number.doubleValue()) : DEFAULT_THRESHOLD_PERCENT;
        } catch (ReflectiveOperationException ex) {
            return DEFAULT_THRESHOLD_PERCENT;
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
