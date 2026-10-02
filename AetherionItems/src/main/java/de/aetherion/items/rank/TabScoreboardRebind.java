package de.aetherion.items.rank;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks TAB to pick the scoreboard again after extras are actually loaded.
 * <p>
 * TAB's {@code delay-on-join-milliseconds} (live config: 2500) assigns the
 * sidebar on a wall-clock timer. That pass can run while
 * {@code %aetherion_has_ultra%} is still cached as {@code no}, and a later
 * placeholder change is dropped when it lands inside the join delay. The
 * board then stays on {@code main} until {@code /tab reload}.
 * <p>
 * This calls {@code ScoreboardManagerImpl.sendHighestScoreboard}, which
 * walks the configured chain and keeps display conditions in charge.
 * It does not call {@code showScoreboard}, which would pin a board and
 * turn those conditions off.
 */
public final class TabScoreboardRebind {

    /**
     * Server ticks. The short delay covers Dev Menu edits on a player who is
     * already past join. 60 and 120 sit after a 2500ms join delay (50 ticks
     * at 20 TPS) and still run if that wall-clock timer won the race while
     * the main thread was lagging.
     */
    static final long[] RECHECK_DELAY_TICKS = {1L, 60L, 120L};

    /** Condition first, then the ultra-board identity lines. */
    static final String[] PLACEHOLDERS = {
            "%aetherion_has_ultra%",
            "%aetherion_ultra_rank%",
            "%aetherion_sidebar_1%",
            "%aetherion_sidebar_2%",
            "%aetherion_level_title%",
            "%aetherion_nametag%"
    };

    /**
     * Impl method that re-runs {@code detectHighestScoreboard}. Not part of
     * the public API interface, which only exposes the forcing {@code showScoreboard}.
     */
    static final String RESOLVE_METHOD = "sendHighestScoreboard";

    private static final AtomicBoolean HOOK_LOGGED = new AtomicBoolean();

    private TabScoreboardRebind() {
    }

    public static void schedule(JavaPlugin plugin, Player player) {
        if (plugin == null || player == null) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            return;
        }
        if (HOOK_LOGGED.compareAndSet(false, true)) {
            plugin.getLogger().info(
                    "TAB scoreboard rechecks after ranks load. Display conditions still choose the board.");
        }
        UUID id = player.getUniqueId();
        for (long delay : RECHECK_DELAY_TICKS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online != null && online.isOnline()) {
                    rebind(online);
                }
            }, delay);
        }
    }

    static void rebind(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
            if (tab == null || !tab.isEnabled()) {
                return;
            }
            ClassLoader loader = tab.getClass().getClassLoader();
            Class<?> api = Class.forName("me.neznamy.tab.api.TabAPI", true, loader);
            Object instance = api.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return;
            }
            Object manager = api.getMethod("getScoreboardManager").invoke(instance);
            if (manager == null) {
                return;
            }
            Object tabPlayer = api.getMethod("getPlayer", UUID.class).invoke(instance, player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }
            refreshPlaceholders(instance, tabPlayer);
            Method send = findResolveMethod(manager.getClass(), tabPlayer);
            if (send == null) {
                return;
            }
            Runnable task = () -> {
                try {
                    send.invoke(manager, tabPlayer);
                } catch (ReflectiveOperationException ignored) {
                    // A TAB build without this method keeps its own refresh.
                }
            };
            if (!runOnScoreboardThread(manager, task)) {
                task.run();
            }
        } catch (Throwable ignored) {
            // TAB absent or a different build. Scoreboard stays on TAB's own refresh.
        }
    }

    /**
     * TAB conditions read the last cached placeholder, not a live PAPI call.
     * Push a fresh value before the chain is walked again.
     */
    private static void refreshPlaceholders(Object api, Object tabPlayer) {
        try {
            Object placeholders = api.getClass().getMethod("getPlaceholderManager").invoke(api);
            if (placeholders == null) {
                return;
            }
            Method getPlaceholder = placeholders.getClass().getMethod("getPlaceholder", String.class);
            for (String identifier : PLACEHOLDERS) {
                Object placeholder;
                try {
                    placeholder = getPlaceholder.invoke(placeholders, identifier);
                } catch (ReflectiveOperationException ignored) {
                    continue;
                }
                if (placeholder == null) {
                    continue;
                }
                Method update = findUpdate(placeholder.getClass(), tabPlayer);
                if (update == null) {
                    continue;
                }
                try {
                    update.invoke(placeholder, tabPlayer);
                } catch (ReflectiveOperationException ignored) {
                    // One stale line is not a reason to skip the board switch.
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Older TAB builds still get the scoreboard walk below.
        }
    }

    static Method findResolveMethod(Class<?> type, Object tabPlayer) {
        if (type == null) {
            return null;
        }
        Method fallback = null;
        for (Method method : type.getMethods()) {
            if (!RESOLVE_METHOD.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (tabPlayer != null && method.getParameterTypes()[0].isInstance(tabPlayer)) {
                method.setAccessible(true);
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        if (fallback != null) {
            fallback.setAccessible(true);
        }
        return fallback;
    }

    static Method findUpdate(Class<?> type, Object tabPlayer) {
        if (type == null || tabPlayer == null) {
            return null;
        }
        for (Method method : type.getMethods()) {
            if (!"update".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isInstance(tabPlayer)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean runOnScoreboardThread(Object manager, Runnable task) {
        try {
            Object thread = manager.getClass().getMethod("getCustomThread").invoke(manager);
            if (thread == null) {
                return false;
            }
            Method execute = thread.getClass().getMethod("execute", Runnable.class);
            execute.invoke(thread, task);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
