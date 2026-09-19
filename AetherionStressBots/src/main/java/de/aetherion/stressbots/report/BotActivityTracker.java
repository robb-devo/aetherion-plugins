package de.aetherion.stressbots.report;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import de.aetherion.stressbots.AetherionStressBots;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Rough activity + death/error history for online bots (idle/mining/pathing/void/recovering/stuck).
 */
public final class BotActivityTracker implements Listener, Runnable {

    public static final String IDLE = "idle";
    public static final String MINING = "mining";
    public static final String FORAGING = "foraging";
    public static final String CATCHING = "catching";
    public static final String PATHING = "pathing";
    public static final String ROAMING = "roaming";
    public static final String FISHING = "fishing";
    public static final String TRADING = "trading";
    public static final String QUESTING = "questing";
    public static final String FIGHTING = "fighting";
    public static final String HOPPING = "hopping";
    public static final String BROWSING = "browsing";
    public static final String ERROR = "error";
    public static final String VOID = "void";
    public static final String RECOVERING = "recovering";
    public static final String STUCK = "stuck";

    private final AetherionStressBots plugin;
    private final Map<UUID, Runtime> runtimes = new ConcurrentHashMap<>();

    public BotActivityTracker(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public void onJoin(Player player) {
        Runtime runtime = runtime(player);
        runtime.note("joined");
        runtime.activity = IDLE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.deaths++;
        runtime.activity = RECOVERING;
        runtime.lastError = "died";
        runtime.note("died");
        plugin.getLogger().info("Testbot " + player.getName() + " died");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        Block block = event.getBlock();
        String name = block.getType().name().toLowerCase(Locale.ROOT);
        Runtime runtime = runtime(player);
        if (name.contains("log") || name.contains("stem") || name.contains("wood") || name.contains("bamboo")) {
            runtime.activity = FORAGING;
            runtime.note("broke " + name);
        } else if (name.contains("ore") || name.contains("stone") || name.contains("deepslate")) {
            runtime.activity = MINING;
            runtime.note("broke " + name);
        } else {
            runtime.note("broke " + name);
        }
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Material type = item.getType();
        Runtime runtime = runtime(player);
        if (type == Material.SNOWBALL || type == Material.ENDER_PEARL
                || type == Material.HEART_OF_THE_SEA || type == Material.GOLD_NUGGET
                || type == Material.NETHER_STAR || type.name().contains("BALL")) {
            runtime.activity = CATCHING;
            runtime.note("used " + pretty(item));
            runtime.lastActionMs = System.currentTimeMillis();
        } else if (type == Material.FISHING_ROD || type.name().contains("FISHING_ROD")) {
            runtime.activity = FISHING;
            runtime.note("cast " + pretty(item));
            runtime.lastActionMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = FISHING;
        runtime.note("fish " + event.getState().name().toLowerCase(Locale.ROOT));
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        BotRole role = roleOf(player);
        Runtime runtime = runtime(player);
        String who = event.getRightClicked().getName();
        if (role == BotRole.QUEST) {
            runtime.activity = QUESTING;
            runtime.note("npc " + who);
        } else if (role == BotRole.TRADE) {
            runtime.activity = TRADING;
            runtime.note("trader " + who);
        } else {
            runtime.note("click " + who);
        }
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isBot(player)) {
            return;
        }
        BotRole role = roleOf(player);
        Runtime runtime = runtime(player);
        String title = event.getView().getTitle().replaceAll("§.", "");
        if (role == BotRole.TRADE) {
            runtime.activity = TRADING;
            runtime.note("gui " + title);
        } else {
            runtime.activity = BROWSING;
            runtime.note("gui " + title);
        }
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = FIGHTING;
        runtime.note("hit " + event.getEntity().getName());
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        runtime(player).note("quit");
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isBot(player)) {
                continue;
            }
            Runtime runtime = runtime(player);
            Location loc = player.getLocation();
            double floor = plugin.getConfig().getDouble("testbots.safety.void-floor-y", 40);
            if (loc.getY() < floor) {
                runtime.activity = VOID;
                runtime.lastSample = loc.clone();
                continue;
            }
            if (now - runtime.lastRecoverMs < 2500) {
                runtime.activity = RECOVERING;
                runtime.lastSample = loc.clone();
                continue;
            }
            if (runtime.lastSample != null && runtime.lastSample.getWorld() == loc.getWorld()) {
                double dist = runtime.lastSample.distanceSquared(loc);
                if (dist > 0.35) {
                    BotRole role = roleOf(player);
                    if (role == BotRole.ROAM) {
                        runtime.activity = ROAMING;
                    } else if (role == BotRole.PAD) {
                        runtime.activity = HOPPING;
                    } else if (role == BotRole.QUEST) {
                        runtime.activity = QUESTING;
                    } else if (role == BotRole.TRADE) {
                        runtime.activity = TRADING;
                    } else if (role == BotRole.COMBAT) {
                        runtime.activity = FIGHTING;
                    } else if (now - runtime.lastActionMs > 1500) {
                        runtime.activity = PATHING;
                    }
                    runtime.lastActionMs = now;
                } else if (now - runtime.lastActionMs > stuckAfter(roleOf(player))
                        && !ERROR.equals(runtime.activity)
                        && !VOID.equals(runtime.activity) && !RECOVERING.equals(runtime.activity)
                        && !busyActivity(runtime.activity)) {
                    runtime.activity = STUCK.equals(runtime.activity) ? STUCK : IDLE;
                    if (now - runtime.lastActionMs > stuckAfter(roleOf(player)) + 4000) {
                        runtime.activity = STUCK;
                    }
                }
            }
            runtime.lastSample = loc.clone();
        }
    }

    public Runtime snapshot(Player player) {
        return runtime(player);
    }

    public void markError(Player player, String error) {
        if (player == null) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = ERROR;
        runtime.lastError = error == null ? "error" : error;
        runtime.note("error: " + runtime.lastError);
    }

    public void markAction(Player player, String action) {
        if (player == null) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.note(action);
        runtime.lastActionMs = System.currentTimeMillis();
    }

    public void markRecovering(Player player, String action) {
        if (player == null) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = RECOVERING;
        runtime.lastRecoverMs = System.currentTimeMillis();
        runtime.lastActionMs = runtime.lastRecoverMs;
        runtime.lastError = "";
        runtime.note("recover " + (action == null ? "" : action));
    }

    public void markVoid(Player player, String action) {
        if (player == null) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = VOID;
        runtime.note(action == null ? "void" : action);
    }

    private static long stuckAfter(BotRole role) {
        if (role == BotRole.FORAGE || role == BotRole.MINE || role == BotRole.FISH) {
            return 24_000;
        }
        return 12_000;
    }

    private static boolean busyActivity(String activity) {
        return FORAGING.equals(activity)
                || MINING.equals(activity)
                || FISHING.equals(activity)
                || CATCHING.equals(activity)
                || FIGHTING.equals(activity)
                || TRADING.equals(activity)
                || QUESTING.equals(activity)
                || HOPPING.equals(activity)
                || BROWSING.equals(activity);
    }

    private Runtime runtime(Player player) {
        return runtimes.computeIfAbsent(player.getUniqueId(), id -> new Runtime());
    }

    private boolean isBot(Player player) {
        return plugin.getRegistry().byPlayer(player) != null;
    }

    private BotRole roleOf(Player player) {
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        return handler == null ? null : handler.role();
    }

    public static String pretty(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "-";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName().replaceAll("§.", "");
        }
        return item.getType().name().toLowerCase(Locale.ROOT);
    }

    public static final class Runtime {
        private volatile String activity = IDLE;
        private volatile String lastError = "";
        private volatile int deaths;
        private volatile long lastActionMs = System.currentTimeMillis();
        private volatile long lastRecoverMs;
        private volatile Location lastSample;
        private final ConcurrentLinkedDeque<String> recent = new ConcurrentLinkedDeque<>();

        public String activity() {
            return activity;
        }

        public String lastError() {
            return lastError;
        }

        public int deaths() {
            return deaths;
        }

        public String lastAction() {
            String first = recent.peekFirst();
            return first == null ? "" : first;
        }

        public List<String> recentActions() {
            return new ArrayList<>(recent);
        }

        private void note(String line) {
            String text = line == null ? "" : line;
            recent.addFirst(text);
            while (recent.size() > 12) {
                recent.removeLast();
            }
        }
    }
}
