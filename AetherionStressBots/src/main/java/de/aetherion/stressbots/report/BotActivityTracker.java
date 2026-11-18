package de.aetherion.stressbots.report;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import de.aetherion.items.economy.MarketService;
import de.aetherion.stressbots.AetherionStressBots;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import java.lang.reflect.Field;
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
    public static final String AH = "ah";
    public static final String BAZAAR = "bazaar";
    public static final String QUEST_DIALOG = "quest_dialog";
    public static final String MINIGAME = "minigame";
    public static final String PAD_HOP = "pad_hop";
    public static final String COMBAT = "combat";
    static final String LANGUAGE = "language";

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
        runtime.count("blocks");
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
        Action action = event.getAction();
        Runtime runtime = runtime(player);
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (type == Material.SNOWBALL) {
            // Catch spheres: right click throws, a left click into the air locks the timing window.
            runtime.activity = left ? MINIGAME : CATCHING;
            runtime.count(left ? "locks" : "throws");
            runtime.note(left ? "timing click" : "threw " + pretty(item));
            runtime.lastActionMs = System.currentTimeMillis();
        } else if (type == Material.FISHING_ROD) {
            if (!left) {
                runtime.activity = FISHING;
                runtime.note("rod " + pretty(item));
                runtime.lastActionMs = System.currentTimeMillis();
            }
        } else if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null
                && type.name().endsWith("_AXE") && isWood(event.getClickedBlock().getType())) {
            // Felling is two clicks on the Fell bar, not a block break.
            runtime.activity = FORAGING;
            runtime.count("chops");
            runtime.note("chop " + event.getClickedBlock().getType().name().toLowerCase(Locale.ROOT));
            runtime.lastActionMs = System.currentTimeMillis();
        }
    }

    private static boolean isWood(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.equals("BAMBOO_BLOCK");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        PlayerFishEvent.State state = event.getState();
        if (state == PlayerFishEvent.State.FISHING) {
            runtime.count("casts");
        } else if (state == PlayerFishEvent.State.CAUGHT_FISH) {
            runtime.count("fish");
        }
        runtime.activity = state == PlayerFishEvent.State.CAUGHT_FISH ? FISHING : MINIGAME;
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
            runtime.count("talks");
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
        String kind = guiKind(event.getView().getTopInventory().getHolder(false));
        if (LANGUAGE.equals(kind)) {
            runtime.note("language gui");
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player));
            runtime.lastActionMs = System.currentTimeMillis();
            return;
        }
        if (kind.isEmpty()) {
            kind = roleOf(player) == BotRole.TRADE ? TRADING : BROWSING;
        }
        runtime.activity = kind;
        runtime.count("menus");
        runtime.note("open " + kind);
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        String kind = guiKind(event.getView().getTopInventory().getHolder(false));
        if (LANGUAGE.equals(kind)) {
            runtime.note("language click slot " + event.getRawSlot());
        } else if (!kind.isEmpty()) {
            runtime.activity = kind;
            runtime.note(kind + " click " + event.getRawSlot());
        }
        runtime.lastActionMs = System.currentTimeMillis();
    }

    /**
     * What a menu is, read from its holder: Aetherion menu titles are drawn from pack glyphs, so the
     * title never says "Auction House". Empty for vanilla and unknown menus.
     */
    static String guiKind(InventoryHolder holder) {
        if (holder == null) {
            return "";
        }
        if (holder instanceof MarketService.BrowseHolder
                || holder instanceof MarketService.PriceHolder
                || holder instanceof MarketService.ConfirmHolder) {
            return "BAZAAR".equals(marketChannel(holder)) ? BAZAAR : AH;
        }
        String name = holder.getClass().getName();
        if (de.aetherion.stressbots.role.BotPlaystyle.LANG_HOLDER.equals(name)) {
            return LANGUAGE;
        }
        if (name.startsWith("de.aetherion.quests.")) {
            return name.contains("Booster") ? MINIGAME : QUEST_DIALOG;
        }
        return BROWSING;
    }

    private static String marketChannel(InventoryHolder holder) {
        try {
            Field field = holder.getClass().getDeclaredField("channel");
            field.setAccessible(true);
            Object value = field.get(holder);
            return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isBot(player)) {
            return;
        }
        Runtime runtime = runtime(player);
        runtime.activity = FIGHTING;
        runtime.count("hits");
        runtime.note("hit " + event.getEntity().getName());
        runtime.lastActionMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !isBot(killer) || event.getEntity() instanceof Player) {
            return;
        }
        runtime(killer).count("kills");
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
            BotRole current = roleOf(player);
            double floor = current != null && current.free() && loc.getWorld() != null
                    ? loc.getWorld().getMinHeight() - 8
                    : plugin.getSafety() == null
                    ? plugin.getConfig().getDouble("testbots.safety.void-floor-y", 40)
                    : plugin.getSafety().floorOf(plugin.getRegistry().byPlayer(player));
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
        runtime.count("recovers");
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
        private final long sinceMs = System.currentTimeMillis();
        private final ConcurrentLinkedDeque<String> recent = new ConcurrentLinkedDeque<>();
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();

        /** Work done since the plugin first saw this bot: blocks, chops, casts, fish, throws, hits, … */
        public Map<String, Integer> counts() {
            return new java.util.TreeMap<>(counts);
        }

        public long sinceMs() {
            return sinceMs;
        }

        private void count(String key) {
            counts.merge(key, 1, Integer::sum);
        }

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
