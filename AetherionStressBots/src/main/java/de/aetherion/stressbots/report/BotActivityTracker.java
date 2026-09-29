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
import org.bukkit.event.inventory.InventoryClickEvent;
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
    public static final String FARMING = "farming";
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
    public static final String AH = "ah";
    public static final String BAZAAR = "bazaar";
    public static final String QUEST_DIALOG = "quest_dialog";
    public static final String MINIGAME = "minigame";
    public static final String PAD_HOP = "pad_hop";
    public static final String COMBAT = "combat";

    private final AetherionStressBots plugin;
    private final Map<UUID, Runtime> runtimes = new ConcurrentHashMap<>();
    private final BotEconomyTracker economy = new BotEconomyTracker();

    public BotActivityTracker(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public BotEconomyTracker economy() {
        return economy;
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
        } else if (BotEconomyTracker.isCropName(name)) {
            runtime.activity = FARMING;
            runtime.note("harvest " + name);
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
        runtime.activity = event.getState() == PlayerFishEvent.State.CAUGHT_FISH ? FISHING : MINIGAME;
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
            runtime.activity = QUEST_DIALOG;
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
        Runtime runtime = runtime(player);
        String title = titleOf(event.getView().getTitle());
        if (de.aetherion.stressbots.role.BotPlaystyle.isLanguageTitle(title)) {
            runtime.note("language gui");
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player));
            runtime.lastActionMs = System.currentTimeMillis();
            return;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("auction")) {
            runtime.activity = AH;
            runtime.note("open ah");
        } else if (lower.contains("bazaar")) {
            runtime.activity = BAZAAR;
            runtime.note("open bazaar");
        } else if (lower.contains("quest")) {
            runtime.activity = QUEST_DIALOG;
            runtime.note("quest gui " + title);
        } else if (lower.contains("booster") || lower.contains("confirm purchase") || lower.contains("list ·")) {
            runtime.activity = lower.contains("booster") ? MINIGAME : TRADING;
            runtime.note("gui " + title);
        } else if (roleOf(player) == BotRole.TRADE) {
            runtime.activity = TRADING;
            runtime.note("gui " + title);
        } else {
            runtime.activity = BROWSING;
            runtime.note("gui " + title);
        }
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isBot(player)) {
            return;
        }
        String title = titleOf(event.getView().getTitle());
        Runtime runtime = runtime(player);
        String lower = title.toLowerCase(Locale.ROOT);
        if (de.aetherion.stressbots.role.BotPlaystyle.isLanguageTitle(title)) {
            runtime.note("language click slot " + event.getRawSlot());
            runtime.lastActionMs = System.currentTimeMillis();
            return;
        }
        if (lower.contains("auction")) {
            runtime.activity = AH;
            runtime.note("ah click " + event.getRawSlot());
            economy.noteMarketClick(AH, event.getRawSlot());
        } else if (lower.contains("bazaar")) {
            runtime.activity = BAZAAR;
            runtime.note("bazaar click " + event.getRawSlot());
            economy.noteMarketClick(BAZAAR, event.getRawSlot());
        } else if (lower.contains("quest")) {
            runtime.activity = QUEST_DIALOG;
            runtime.note("quest click " + event.getRawSlot());
        } else if (lower.contains("confirm purchase")) {
            runtime.activity = TRADING;
            runtime.note("confirm click " + event.getRawSlot());
        } else if (lower.contains("list")) {
            runtime.activity = TRADING;
            runtime.note("list price slot " + event.getRawSlot());
        } else if (lower.contains("booster")) {
            runtime.activity = MINIGAME;
            runtime.note("booster click " + event.getRawSlot());
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
        economy.forget(player.getUniqueId());
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
                        runtime.activity = PAD_HOP;
                    } else if (role == BotRole.QUEST) {
                        runtime.activity = QUESTING;
                    } else if (role == BotRole.TRADE) {
                        runtime.activity = TRADING;
                    } else if (role == BotRole.COMBAT) {
                        runtime.activity = FIGHTING;
                    } else if (role == BotRole.FARM) {
                        runtime.activity = FARMING;
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
            economy.sample(player, runtime.activity, false);
        }
    }

    public BotEconomyTracker.Snapshot economySnapshot() {
        int deaths = 0;
        int stuck = 0;
        int online = 0;
        Map<String, Integer> mix = new java.util.LinkedHashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isBot(player)) {
                continue;
            }
            online++;
            Runtime runtime = runtime(player);
            deaths += runtime.deaths();
            mix.merge(runtime.activity(), 1, Integer::sum);
            if (STUCK.equals(runtime.activity()) || VOID.equals(runtime.activity())) {
                stuck++;
            }
        }
        return economy.snapshot(deaths, stuck, mix, online);
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
        if (role == BotRole.FORAGE || role == BotRole.MINE || role == BotRole.FISH || role == BotRole.FARM) {
            return 24_000;
        }
        return 12_000;
    }

    private static boolean busyActivity(String activity) {
        return FORAGING.equals(activity)
                || MINING.equals(activity)
                || FARMING.equals(activity)
                || FISHING.equals(activity)
                || CATCHING.equals(activity)
                || FIGHTING.equals(activity)
                || TRADING.equals(activity)
                || QUESTING.equals(activity)
                || QUEST_DIALOG.equals(activity)
                || HOPPING.equals(activity)
                || PAD_HOP.equals(activity)
                || AH.equals(activity)
                || BAZAAR.equals(activity)
                || MINIGAME.equals(activity)
                || COMBAT.equals(activity)
                || BROWSING.equals(activity);
    }

    public void markActivity(Player player, String activity, String action) {
        if (player == null) {
            return;
        }
        Runtime runtime = runtime(player);
        if (activity != null && !activity.isBlank()) {
            runtime.activity = activity;
        }
        runtime.note(action);
        runtime.lastActionMs = System.currentTimeMillis();
    }

    private static String titleOf(String raw) {
        return raw == null ? "" : raw.replaceAll("§.", "");
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
