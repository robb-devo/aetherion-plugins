package de.aetherion.foraging;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;

import de.aetherion.foraging.isle.FellContext;
import de.aetherion.foraging.isle.ForageBridge;
import de.aetherion.foraging.isle.ForageIsle;
import de.aetherion.foraging.isle.ForageText;
import de.aetherion.foraging.isle.Wood;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.util.InventoryDrops;
import de.aetherion.items.world.AreaType;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.event.Event.Result.ALLOW;

public class ForagingListener implements Listener {

    private static final long REGROW_TICKS = 20L * 20;
    private static final long CHOP_MISS_COOLDOWN_MS = 60_000L;
    /**
     * Graduated per-trunk lock: a first miss is a short breather, repeat misses on the same trunk climb
     * to the full minute. Keeps the bar honest without hard-locking a tree you just fumbled once.
     */
    private static final long[] CHOP_MISS_STEPS_MS = {12_000L, 30_000L, CHOP_MISS_COOLDOWN_MS};
    /** Miss strikes on a trunk are forgotten this long after the last miss. */
    private static final long CHOP_MISS_MEMORY_MS = 180_000L;
    private static final long CANOPY_CLEAVER_COOLDOWN_MS = 20_000L;
    /** Incomplete trees without progress get restored so jobs/regenerating cannot leak forever. */
    private static final long IDLE_ABANDON_TICKS = 20L * 90;
    private static final String CANOPY_CLEAVER_ID = "canopy_cleaver";
    private static final int MAX_LOGS = 96;
    private static final int MAX_LEAF_RADIUS = 3;
    private static final int MAX_HORIZONTAL = 8;
    private static final int MAX_VERTICAL = 28;
    private static final double COLLAPSE_AT = 0.80d;
    /** Bonus Foraging XP for a Perfect fell (a tree pays ~40–60 XP in logs on its own). */
    private static final int PERFECT_BONUS_XP = 12;
    /** Streak bonus: +2 XP per clean fell beyond the first, capped. */
    private static final int STREAK_BONUS_STEP = 2;
    private static final int STREAK_BONUS_CAP = 8;
    /** Soft look-cache so collectLogs is not re-run every tick while staring at one block. */
    private static final int LOOK_CACHE_TICKS = 8;
    private static final int NEWCOMER_HINT_CHOPS = 10;
    private static final int NEWCOMER_HINT_INTERVAL_TICKS = 60;

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AetherionForaging plugin;
    private final ForagingHud hud = new ForagingHud();
    private final Set<String> regenerating = ConcurrentHashMap.newKeySet();
    private final Map<String, TreeJob> jobs = new ConcurrentHashMap<>();
    /** Live fells: the cutter is choosing where the tree falls (A-51). */
    private final Map<UUID, FellAim> pulses = new ConcurrentHashMap<>();
    /** Per-player cooldown on a failed tree only (uuid|treeId → untilMillis). */
    private final Map<String, Long> chopMissUntil = new ConcurrentHashMap<>();
    /** Recent misses on a trunk (uuid|treeId → [count, lastMissMillis]) for the graduated lock. */
    private final Map<String, long[]> chopMissStrikes = new ConcurrentHashMap<>();
    /** Canopy Cleaver perfect-fell cooldown (uuid → untilMillis). */
    private final Map<UUID, Long> canopyCleaverUntil = new ConcurrentHashMap<>();
    /** Players whose tree is collapsing — suppress arm swing / dig spam. */
    private final Set<UUID> collapsingPlayers = ConcurrentHashMap.newKeySet();
    private final FellStreak streaks = new FellStreak();
    /** One "equip a Foraging skill" nudge per player per boot. */
    private final Set<UUID> tipped = ConcurrentHashMap.newKeySet();
    /** First miss per boot explains the cooldown in chat; later misses stay on the action bar. */
    private final Set<UUID> taughtMiss = ConcurrentHashMap.newKeySet();
    /** Players currently shown a look-preview bar (not a live fell). */
    private final Set<UUID> previewing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LookCache> lookCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastHintTick = new ConcurrentHashMap<>();

    public ForagingListener(AetherionForaging plugin) {
        this.plugin = plugin;
    }

    ForagingHud hud() {
        return hud;
    }

    /** Nearest living stump (bottom log) for the Forager tutorial demo. */
    Block findDemoTrunk(Location near, int radius) {
        if (near == null || near.getWorld() == null) {
            return null;
        }
        World world = near.getWorld();
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        int r = Math.max(2, radius);
        int baseX = near.getBlockX();
        int baseY = near.getBlockY();
        int baseZ = near.getBlockZ();
        for (int x = baseX - r; x <= baseX + r; x++) {
            for (int y = baseY - 1; y <= baseY + 8; y++) {
                for (int z = baseZ - r; z <= baseZ + r; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isLog(block.getType())) {
                        continue;
                    }
                    // Prefer real stump row: not a log underneath.
                    if (isLog(block.getRelative(0, -1, 0).getType())) {
                        continue;
                    }
                    if (!hasConnectedCanopy(block)) {
                        continue;
                    }
                    double dist = block.getLocation().distanceSquared(near);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = block;
                    }
                }
            }
        }
        return best;
    }

    ForagingListener.TreeJob createJobPublic(Block start, Player player, Block skipMark) {
        return createJob(start, player, skipMark);
    }

    void collapsePublic(TreeJob job) {
        collapse(job);
    }

    /** Tutorial fall towards {@code dir} — the same animation a player's own fell gets. */
    void fallPublic(TreeJob job, Vector dir) {
        fall(job, dir, 90.0d, null);
    }

    /** A clear direction for the demo tree to fall (the gold mark the Forager shows). */
    Vector demoDirection(TreeJob job, Location away) {
        Location base = job.baseLocation();
        if (base == null) {
            return new Vector(1, 0, 0);
        }
        Vector avoid = away == null ? null : away.toVector().subtract(base.toVector()).setY(0);
        if (avoid != null && avoid.lengthSquared() > 0.01d) {
            avoid.normalize();
        } else {
            avoid = null;
        }
        return FellLane.markDirection(fellBase(job), job.reach(), job.blockKeys(), avoid);
    }

    /** The player is in a chop of their own (choosing where their tree falls). */
    boolean chopping(Player player) {
        return player != null && pulses.containsKey(player.getUniqueId());
    }

    /** Tutorial fall: no loot, no quest credit; suppress viewer dig/swing. */
    void markDemo(TreeJob job, Player viewer) {
        if (job == null) {
            return;
        }
        job.noLoot = true;
        job.breaker = null;
        if (viewer != null) {
            job.suppressSwing = viewer.getUniqueId();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player != null && collapsingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        TreeJob job = jobs.get(key(event.getBlock()));
        if (job != null && job.collapsing) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (collapsingPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardBreak(BreakBlockEvent event) {
        if (allowsLog(event.getCause().getFirstBlock())) {
            event.setResult(ALLOW);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (allowsLog(block)) {
                event.setResult(ALLOW);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block start = event.getBlock();
        if (!isLog(start.getType())) {
            return;
        }
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (worldName.startsWith("aedun_") || worldName.startsWith("ae_dun")) {
            return;
        }
        if (worldName.equals("aether_farm_island") || worldName.startsWith("aether_farm_")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§7Foraging is off on the Farm Isle.");
            return;
        }
        // Personal/guild islands: chop permanently — no tree seal-regen.
        if (worldName.equals("aether_islands")
                || worldName.equals("aether_guilds")
                || worldName.equals("aether_test")
                || worldName.startsWith("aether_test_")) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        if (!QuestProgressHook.canChopTrees(player)) {
            event.setCancelled(true);
            denyChop(player);
            return;
        }
        if (collapsingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (pulses.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        String startKey = key(start);
        if (regenerating.contains(startKey)) {
            event.setCancelled(true);
            return;
        }

        TreeJob job = jobs.get(startKey);
        if (job != null && (job.collapsing || job.felling)) {
            event.setCancelled(true);
            return;
        }
        if (job == null) {
            job = createJob(start, event.getPlayer(), start);
            if (job == null) {
                // No connected leaves → build/deco log. Survival cannot break these.
                event.setCancelled(true);
                hintLeafless(player);
                return;
            }
        }
        // Any stem of a living tree with axe starts CHOP (preview shows the same green window).
        // Hand / non-axe on the fell-mark stump can break it normally (no minigame).
        if (job.fellLog != null && !job.fellSpent) {
            if (isAxe(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                startChop(player, job);
                return;
            }
            if (job.fellLog.key().equals(startKey)) {
                job.fellSpent = true;
                job.fellLog = null;
                job.fellMarks.clear();
            }
        }
        job.breaker = player.getUniqueId();
        ensureWoodCap(job, player);
        if (job.woodPaid >= job.woodCap) {
            event.setCancelled(true);
            event.setDropItems(false);
            regenerating.add(key(start));
            start.setType(Material.AIR, false);
            TreeJob tracked = job;
            job.touchedTick = Bukkit.getCurrentTick();
            Bukkit.getScheduler().runTask(plugin, () -> afterBreak(tracked));
            return;
        }
        event.setCancelled(false);
        // Harbour: force oak. Forage Isle: typed wood matching the log.
        event.setDropItems(false);
        // Cancel so HarvestListener (HIGHEST) does not also pay out this log at the player.
        event.setCancelled(true);
        Material drop = forcedDropOr(start.getType(), start.getLocation());
        give(player, new ItemStack(drop, 1), start.getLocation());
        grantWood(player, drop);
        grantAxe(player);
        maybeIsleHeartwood(player, drop);
        QuestProgressHook.noteBroken(player, drop, 1);
        job.woodPaid++;
        job.lastDrop = drop;
        job.touchedTick = Bukkit.getCurrentTick();
        regenerating.add(startKey);
        start.setType(Material.AIR, false);
        TreeJob tracked = job;
        Bukkit.getScheduler().runTask(plugin, () -> afterBreak(tracked));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void allowProtectedLogs(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isLog(block.getType())) {
            return;
        }
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (worldName.startsWith("aedun_") || worldName.startsWith("ae_dun")) {
            return;
        }
        if (worldName.equals("aether_farm_island") || worldName.startsWith("aether_farm_")) {
            return;
        }
        // Only force-allow living trees. Placed build logs keep region protection.
        if (!hasConnectedCanopy(block)) {
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (collapsingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        World world = player.getWorld();
        if (world != null) {
            String worldName = world.getName().toLowerCase(Locale.ROOT);
            if (worldName.startsWith("aedun_") || worldName.startsWith("ae_dun")) {
                return;
            }
        }
        if (!isAxe(player.getInventory().getItemInMainHand())) {
            return;
        }
        FellAim pulse = pulses.get(player.getUniqueId());
        if (pulse != null) {
            if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
                return;
            }
            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            if (action != Action.LEFT_CLICK_BLOCK || clicked == null) {
                return;
            }
            if (!pulse.job.blockKeys().contains(FellLane.key(clicked.getX(), clicked.getY(), clicked.getZ()))) {
                // A swing at another tree starts that one (switching is free).
                if (isLog(clicked.getType())) {
                    TreeJob other = jobs.get(key(clicked));
                    if (other == null) {
                        other = createJob(clicked, player, null);
                    }
                    if (other != null && other != pulse.job && !other.collapsing && !other.fellSpent && other.fellLog != null) {
                        cancelChop(pulse);
                        startChop(player, other);
                    }
                }
                return;
            }
            if (pulse.chop(player)) {
                resolveChop(pulse);
            } else {
                hud.aiming(player, pulse, streaks.current(pulse.playerId));
            }
            return;
        }
        if (collapsingPlayers.contains(player.getUniqueId())
                && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            return;
        }
        if (action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isLog(block.getType())) {
            return;
        }
        TreeJob job = jobs.get(key(block));
        if (job == null) {
            job = createJob(block, player, null);
        }
        if (job == null || job.collapsing || job.fellSpent || job.fellLog == null) {
            return;
        }
        // Any log of this tree — not only the bottom fell-mark.
        if (!QuestProgressHook.canChopTrees(player)) {
            event.setCancelled(true);
            denyChop(player);
            return;
        }
        event.setCancelled(true);
        if (isCanopyCleaver(player.getInventory().getItemInMainHand())) {
            tryCanopyCleaver(player, job);
            return;
        }
        startChop(player, job);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        FellAim pulse = pulses.get(id);
        if (pulse != null) {
            missChop(pulse);
        }
        collapsingPlayers.remove(id);
        streaks.clear(id);
        canopyCleaverUntil.remove(id);
        clearLookPreview(id);
        hud.hide(event.getPlayer());
        ForagerChopDemo.releaseOnQuit(event.getPlayer());
    }

    /** Teleporting away mid-aim is not a miss — drop the aim, keep the tree and streak. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        FellAim pulse = pulses.get(id);
        if (pulse != null) {
            cancelChop(pulse);
        }
        hud.hide(id);
    }

    private static void denyChop(Player player) {
        // The demo never blocks any more (chopping opens when it starts); only "never talked to him" lands here.
        player.sendMessage(de.aetherion.core.exile.Say.t(player,
                "§7The §eForager§7 up the hill hands out the axe. Talk to him first.",
                "§7Der §eForager§7 oben am Hügel gibt dir die Axt. Sprich zuerst mit ihm."));
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.core.exile.Say.t(player, "→ Forager first · he has the axe", "→ Zuerst zum Forager · er hat die Axt"),
                net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
    }

    void tick() {
        int now = Bukkit.getCurrentTick();
        // Look-preview runs even with empty jobs — never createJob / entities from staring.
        // Every 8 ticks (~2.5/s) is enough for the soft bar and cuts raycast churn vs every 2.
        if (now % 8 == 0) {
            tickLookPreviews(now);
        }
        if (!jobs.isEmpty() && now % 100 == 0) {
            pruneIdleJobs(now);
        }
        if (pulses.isEmpty()) {
            return;
        }
        for (FellAim pulse : List.copyOf(pulses.values())) {
            Player player = Bukkit.getPlayer(pulse.playerId);
            if (player == null || !player.isOnline()) {
                missChop(pulse);
                continue;
            }
            if (player.getWorld() != pulse.base.getWorld()
                    || player.getLocation().distanceSquared(pulse.base) > 26.0d * 26.0d) {
                // Walked off: no fell, no penalty.
                cancelChop(pulse);
                continue;
            }
            boolean idle = pulse.tick(player);
            hud.aiming(player, pulse, streaks.current(pulse.playerId));
            if (idle) {
                // Stopped hitting: no fell, no penalty, the crack fades.
                cancelChop(pulse);
            }
        }
    }

    /**
     * Soft look-ahead: axe + reach raycast + living canopy → one BossBar.
     * Does not register TreeJobs (no map spam / idle restore churn from glancing).
     */
    private void tickLookPreviews(int now) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (pulses.containsKey(id) || collapsingPlayers.contains(id)) {
                // Active chop owns the bar; drop preview bookkeeping only.
                previewing.remove(id);
                lookCache.remove(id);
                continue;
            }
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                clearLookPreview(id);
                continue;
            }
            if (!isAxe(player.getInventory().getItemInMainHand())) {
                clearLookPreview(id);
                continue;
            }
            World world = player.getWorld();
            if (world == null || !isForagingMinigameWorld(world)) {
                clearLookPreview(id);
                continue;
            }
            // Canopy BFS is forage-isle only — harbour trees in `world` must not pay this tax.
            if (!inForageIsle(player.getLocation())) {
                clearLookPreview(id);
                continue;
            }
            if (!QuestProgressHook.canChopTrees(player)) {
                clearLookPreview(id);
                continue;
            }
            Block target = lookTarget(player);
            if (target == null || !isLog(target.getType())) {
                clearLookPreview(id);
                continue;
            }
            String startKey = key(target);
            if (regenerating.contains(startKey)) {
                clearLookPreview(id);
                continue;
            }

            LookCache cached = lookCache.get(id);
            String treeId;
            if (cached != null && cached.blockKey.equals(startKey) && cached.expiresAt > now) {
                treeId = cached.treeId;
            } else {
                TreeJob job = jobs.get(startKey);
                if (job != null) {
                    if (job.collapsing || job.fellSpent || job.fellLog == null || job.treeId == null) {
                        clearLookPreview(id);
                        continue;
                    }
                    treeId = job.treeId;
                } else {
                    treeId = peekTreeId(target);
                    if (treeId == null) {
                        clearLookPreview(id);
                        continue;
                    }
                }
                lookCache.put(id, new LookCache(startKey, treeId, now + LOOK_CACHE_TICKS));
            }

            long missLeft = missCooldownLeftMs(id, treeId);
            if (missLeft > 0L) {
                hud.previewCooling(player, (missLeft + 999L) / 1000L);
            } else {
                hud.preview(player, streaks.current(id));
                maybeNewcomerHint(player, now);
            }
            previewing.add(id);
        }
    }

    private void clearLookPreview(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lookCache.remove(playerId);
        lastHintTick.remove(playerId);
        if (previewing.remove(playerId) && !pulses.containsKey(playerId)) {
            hud.hide(playerId);
        }
    }

    private void maybeNewcomerHint(Player player, int now) {
        if (player == null || chopHintsDone(player) >= NEWCOMER_HINT_CHOPS) {
            return;
        }
        Integer last = lastHintTick.get(player.getUniqueId());
        if (last != null && now - last < NEWCOMER_HINT_INTERVAL_TICKS) {
            return;
        }
        lastHintTick.put(player.getUniqueId(), now);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.core.exile.Say.t(player, "Hit the trunk until it falls · the glowing spot counts twice",
                        "Auf den Stamm schlagen, bis er fällt · die leuchtende Stelle zählt doppelt"),
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }

    private static int chopHintsDone(Player player) {
        Integer n = player.getPersistentDataContainer().get(
                ForageKeys.chopHintCount(), PersistentDataType.INTEGER);
        return n == null ? 0 : Math.max(0, n);
    }

    private static void bumpChopHints(Player player) {
        if (player == null) {
            return;
        }
        int n = chopHintsDone(player);
        if (n >= NEWCOMER_HINT_CHOPS) {
            return;
        }
        player.getPersistentDataContainer().set(
                ForageKeys.chopHintCount(), PersistentDataType.INTEGER, n + 1);
    }

    /**
     * Resolve stable treeId without registering a TreeJob (look-only, no leaks).
     */
    private static String peekTreeId(Block start) {
        List<Block> logs = collectLogs(start);
        if (logs.isEmpty() || collectConnectedCanopy(logs).isEmpty()) {
            return null;
        }
        return logs.stream().map(ForagingListener::key).sorted().findFirst().orElse(null);
    }

    private static Block lookTarget(Player player) {
        double reach = 4.5d;
        try {
            var attr = player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
            if (attr != null) {
                reach = attr.getValue();
            }
        } catch (Throwable ignored) {
        }
        int dist = Math.max(3, (int) Math.ceil(reach));
        return player.getTargetBlockExact(dist, FluidCollisionMode.NEVER);
    }

    /** Worlds where the fell minigame (preview + chop) is active. */
    private static boolean isForagingMinigameWorld(World world) {
        if (world == null) {
            return false;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (worldName.startsWith("aedun_") || worldName.startsWith("ae_dun")) {
            return false;
        }
        if (worldName.equals("aether_farm_island") || worldName.startsWith("aether_farm_")) {
            return false;
        }
        // Personal/guild islands: permanent vanilla chop — no seal-regen / minigame.
        if (worldName.equals("aether_islands")
                || worldName.equals("aether_guilds")
                || worldName.equals("aether_test")
                || worldName.startsWith("aether_test_")) {
            return false;
        }
        return true;
    }

    private void pruneIdleJobs(int now) {
        Set<TreeJob> seen = new HashSet<>();
        for (TreeJob job : new HashSet<>(jobs.values())) {
            if (!seen.add(job) || job.collapsing || job.felling) {
                continue;
            }
            if (now - job.touchedTick < IDLE_ABANDON_TICKS) {
                continue;
            }
            // Partial chop abandoned — put the tree back and drop map entries.
            restore(job);
        }
        // Per-tree miss locks and cleaver cooldowns are short-lived — don't let them pile up.
        long nowMs = System.currentTimeMillis();
        chopMissUntil.values().removeIf(until -> until <= nowMs);
        chopMissStrikes.values().removeIf(strike -> nowMs - strike[1] > CHOP_MISS_MEMORY_MS);
        canopyCleaverUntil.values().removeIf(until -> until <= nowMs);
    }

    void shutdown() {
        for (FellAim pulse : List.copyOf(pulses.values())) {
            pulse.job.pulse = null;
            pulse.job.felling = false;
        }
        previewing.clear();
        lookCache.clear();
        lastHintTick.clear();
        hud.hideAll();
        pulses.clear();
        streaks.clearAll();
    }

    private void afterBreak(TreeJob job) {
        if (job.collapsing || job.felling) {
            return;
        }
        if (job.fellLog != null && !isLog(job.fellLog.block().getType())) {
            job.fellLog = null;
            job.fellMarks.clear();
            job.fellSpent = true;
        }
        int remaining = 0;
        for (Snapshot log : job.logs) {
            if (isLog(log.block().getType())) {
                remaining++;
            }
        }
        int mined = job.logs.size() - remaining;
        if (mined <= 0) {
            return;
        }
        boolean finished = remaining == 0;
        boolean enough = mined >= Math.ceil(job.logs.size() * COLLAPSE_AT);
        if (!finished && !enough) {
            return;
        }
        collapse(job);
    }

    private void startChop(Player player, TreeJob job) {
        if (job.felling || job.collapsing || job.fellSpent || job.fellLog == null) {
            return;
        }
        if (isChopMissCooling(player, job)) {
            long left = missCooldownLeftMs(player, job);
            long sec = Math.max(1L, (left + 999L) / 1000L);
            player.sendMessage("§cThat trunk needs a moment (§f" + sec + "s§c). Try another tree.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "This tree · " + sec + "s — other trees free",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            return;
        }
        FellAim existing = pulses.get(player.getUniqueId());
        if (existing != null) {
            if (existing.job == job) {
                return;
            }
            cancelChop(existing); // switching trees is free — no miss lock
        }
        Location base = fellBase(job);
        if (base == null) {
            return;
        }
        // Preview bookkeeping yields the bar to the live aim (same BossBar instance).
        previewing.remove(player.getUniqueId());
        lookCache.remove(player.getUniqueId());
        job.felling = true;
        int streak = streaks.current(player.getUniqueId());
        ForageIsle isle = ForageIsle.get();
        boolean titan = isle != null && !job.notched && isle.titan(job.baseLocation(), job.logs.size());
        if (titan) {
            // Titans need twice the hits in one go (no second aim any more); the isle still pays them as Titans.
            job.notched = true;
        }
        // The old Perfect-ring sources (skill level, streak, isle marks, Steady Hands) now keep the gold spot lit longer.
        int zone = ForagingStrike.zoneSize(zoneFor(player, streak, job.family, base, false));
        int glow = (int) Math.round((8 + 2 * zone) / speedFor(player, base, false));
        int hits = hitsFor(player.getInventory().getItemInMainHand(), job.logs.size(), titan);
        FellAim pulse = new FellAim(player, job, base, job.reach(), job.blockKeys(), plugin.fellAimTicks(), hits, glow);
        job.pulse = pulse;
        pulses.put(player.getUniqueId(), pulse);

        ForagingFx.start(player, job.fellLocation());
        hud.aiming(player, pulse, streak);
    }

    /**
     * Hits a tree takes (2026-11-27): the axe sets it, T1 6 … T5 2; big trees (24+ logs) take half again,
     * Titans twice. Vanilla axes count as T1 (wood, stone, gold), T2 (iron), T3 (diamond), T4 (netherite).
     */
    static int hitsFor(ItemStack axe, int logs, boolean titan) {
        int tier = axeTier(axe);
        int hits = Math.max(2, 7 - tier);
        if (logs >= 24) {
            hits = (int) Math.ceil(hits * 1.5d);
        }
        if (titan) {
            hits *= 2;
        }
        return hits;
    }

    static int axeTier(ItemStack axe) {
        if (axe == null || axe.getType().isAir()) {
            return 1;
        }
        try {
            AetherionItems items = AetherionItems.getInstance();
            String id = items == null || items.getItemManager() == null ? null : items.getItemManager().getItemId(axe);
            if (id != null) {
                String lower = id.toLowerCase(Locale.ROOT);
                if (lower.startsWith("foraging_axe")) {
                    int dash = lower.lastIndexOf('_');
                    String tail = lower.substring(dash + 1);
                    return tail.chars().allMatch(Character::isDigit) ? Math.max(1, Math.min(5, Integer.parseInt(tail))) : 1;
                }
                if (lower.equals("god_axe")) {
                    return 5;
                }
                if (lower.equals(CANOPY_CLEAVER_ID.toLowerCase(Locale.ROOT)) || lower.contains("timber") || lower.contains("bridged")) {
                    return 3;
                }
                if (lower.equals("simple_axe")) {
                    return 1;
                }
            }
        } catch (Throwable ignored) {
        }
        return switch (axe.getType()) {
            case NETHERITE_AXE -> 4;
            case DIAMOND_AXE -> 3;
            case IRON_AXE -> 2;
            default -> 1;
        };
    }

    /** Stump centre at the stump block's bottom — where every fall pivots. */
    private static Location fellBase(TreeJob job) {
        Location base = job.baseLocation();
        return base == null ? null : new Location(base.getWorld(), base.getX(), Math.floor(base.getY()), base.getZ());
    }

    /**
     * Perfect-cone size in steps (see {@link ForagingStrike#tolerance}): {@link #fellZoneFor} plus Foraging
     * Eldervale's marks, skills, mastery and Frostlit (isle only). A Titan's second cut is one step tighter.
     */
    private int zoneFor(Player player, int streak, Material wood, Location at, boolean titanCut) {
        int zone = fellZoneFor(player, streak);
        ForageIsle isle = ForageIsle.get();
        if (isle != null) {
            zone += isle.zoneBonus(player, wood, at);
        }
        return titanCut ? zone - 1 : zone;
    }

    /** Aim time divisor: Keen Edge gives more time on the isle, a Titan's second cut is quicker. */
    private static double speedFor(Player player, Location at, boolean titanCut) {
        ForageIsle isle = ForageIsle.get();
        double slow = isle == null ? 1.0d : isle.strikeFactor(player, at);
        return (titanCut ? 1.15d : 1.0d) / slow;
    }

    /**
     * Midgame growth: the cone is config size, +1 once the best Foraging skill reaches
     * {@link ForagingStrike#WIDE_WINDOW_LEVEL}, +1 while on a hot streak.
     */
    private int fellZoneFor(Player player, int streak) {
        int zone = plugin.fellZoneSize();
        if (ForagingSkills.bestLevel(player) >= ForagingStrike.WIDE_WINDOW_LEVEL) {
            zone++;
        }
        if (streak >= ForagingStrike.HOT_STREAK) {
            zone++;
        }
        return zone;
    }

    private void tryCanopyCleaver(Player player, TreeJob job) {
        if (job.felling || job.collapsing || job.fellSpent || job.fellLog == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        long cooldownMs = canopyCleaverCooldownMs(hand);
        long now = System.currentTimeMillis();
        Long until = canopyCleaverUntil.get(player.getUniqueId());
        if (until != null && now < until) {
            long left = Math.max(1L, (until - now + 999L) / 1000L);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Perfect Fell · " + left + "s",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
            return;
        }
        FellAim existing = pulses.get(player.getUniqueId());
        if (existing != null) {
            cancelChop(existing);
        }
        previewing.remove(player.getUniqueId());
        lookCache.remove(player.getUniqueId());
        canopyCleaverUntil.put(player.getUniqueId(), now + cooldownMs);
        Location at = job.anchor();
        job.pulse = null;
        job.felling = false;
        job.fellSpent = true;
        job.fellLog = null;
        job.fellMarks.clear();
        clearMissCooldown(player.getUniqueId(), job);
        collapsingPlayers.add(player.getUniqueId());
        ensureWoodCap(job, player);
        ForagingFx.success(player, at);
        player.clearActiveItem();
        tryForestDragonFromChop(player);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Perfect Fell",
                net.kyori.adventure.text.format.NamedTextColor.GREEN
        ));
        Location base = fellBase(job);
        Vector away = base == null ? new Vector(1, 0, 0)
                : base.toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector dir = away.lengthSquared() < 0.04d ? player.getLocation().getDirection().setY(0) : away;
        dir = dir.lengthSquared() < 0.0001d ? new Vector(1, 0, 0) : dir.normalize();
        Vector fallDir = dir;
        int cleaverStreak = streaks.current(player.getUniqueId());
        // The Cleaver skips the aim — no streak step, no Perfect bonus, but the fall still tallies.
        fall(job, fallDir, 90.0d, () -> fellHook(player, job, true, cleaverStreak, true, landedCrown(job, fallDir)));
        scheduleTally(job, player.getUniqueId(), "§6Perfect Fell §8(Cleaver)", cleaverStreak, 0);
    }

    private static long canopyCleaverCooldownMs(ItemStack item) {
        try {
            return de.aetherion.items.blueprint.BlueprintUpgrade.canopyCleaverCooldownMs(
                    de.aetherion.items.blueprint.BlueprintUpgrade.tier(item)
            );
        } catch (Throwable ignored) {
            return CANOPY_CLEAVER_COOLDOWN_MS;
        }
    }

    private static boolean isCanopyCleaver(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getItemManager() == null) {
            return false;
        }
        String id = items.getItemManager().getItemId(item);
        return CANOPY_CLEAVER_ID.equalsIgnoreCase(id);
    }

    private void resolveChop(FellAim pulse) {
        if (pulse == null || pulse.job.collapsing) {
            return;
        }
        if (pulse.hit()) {
            landChop(pulse, false);
        } else {
            // The core always works: a hung-up or twisted tree still comes down — a rough fell, no Perfect,
            // streak gone, but no locked trunk. A clean lane is the bonus, not the gate.
            streaks.reset(pulse.playerId);
            landChop(pulse, true);
        }
    }

    private void landChop(FellAim pulse, boolean rough) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
            return;
        }
        ForageIsle isle = ForageIsle.get();
        Player cutter = Bukkit.getPlayer(pulse.playerId);
        if (cutter != null && isle != null && !pulse.job.notched && isle.titan(pulse.job.baseLocation(), pulse.job.logs.size())) {
            // Only a fell started before the hit-count update can still land here.
            notchTitan(cutter, pulse);
            return;
        }
        Location at = pulse.job.anchor();
        pulse.job.pulse = null;
        pulse.job.felling = false;
        pulse.job.fellSpent = true;
        pulse.job.fellLog = null;
        pulse.job.fellMarks.clear();
        clearMissCooldown(pulse.playerId, pulse.job);
        Player player = Bukkit.getPlayer(pulse.playerId);
        hud.hide(pulse.playerId);
        // Stop dig/swing spam while the tree comes down.
        collapsingPlayers.add(pulse.playerId);
        boolean perfect = !rough && pulse.perfectHit();
        int streak = rough ? 0 : streaks.bump(pulse.playerId);
        if (player != null) {
            ensureWoodCap(pulse.job, player);
            if (rough) {
                ForagingFx.rough(player, at, pulse.missReason());
                if (isle != null) {
                    isle.onMiss(player, pulse.job.baseLocation());
                }
            } else {
                ForagingFx.success(player, at, perfect, streak);
                announceStreak(player, streak);
            }
            // Release held dig so the client stops flailing the axe.
            player.clearActiveItem();
            tryForestDragonFromChop(player);
            bumpChopHints(player);
        }
        Vector dir = pulse.direction();
        FellLane.Result lane = pulse.result();
        TreeJob job = pulse.job;
        boolean fellPerfect = perfect;
        int fellStreak = streak;
        fall(job, dir, lane.hitAngle(), () -> fellHook(player, job, fellPerfect, fellStreak, false, landedCrown(job, dir)));
        int bonus = (perfect ? PERFECT_BONUS_XP : 0)
                + Math.min(STREAK_BONUS_CAP, Math.max(0, streak - 1) * STREAK_BONUS_STEP);
        scheduleTally(job, pulse.playerId, rough ? "§eRough fell." : ForagingFx.word(perfect), streak, bonus);
    }

    /** Where the crown comes to rest after a fall towards {@code dir} (Crown Finds let go above it). */
    private static Location landedCrown(TreeJob job, Vector dir) {
        Location base = fellBase(job);
        if (base == null) {
            return job.crownLocation();
        }
        double along = Math.max(2.0d, job.reach() - 2.0d);
        return base.clone().add(dir.getX() * along, 2.5d, dir.getZ() * along);
    }

    /**
     * Foraging Eldervale Titans (giant trees) take two cuts. The first notches the trunk; the aim comes
     * straight back with a tighter gold ring and a little less time.
     */
    private void notchTitan(Player player, FellAim pulse) {
        TreeJob job = pulse.job;
        job.pulse = null;
        job.felling = false;
        job.notched = true;
        clearMissCooldown(pulse.playerId, job);
        hud.hide(pulse.playerId);
        Location at = job.baseLocation();
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 0.6f);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.5f);
        if (at != null && at.getWorld() != null) {
            de.aetherion.core.api.Vfx.puff(at.clone().add(0.0, 0.8, 0.0), org.bukkit.Particle.BLOCK, 30, Math.max(0.5, 0.5), job.family.createBlockData());
        }
        ForageText.bar(player, "§6✦ Notched! §7The Titan groans — §fone more cut.");
        player.clearActiveItem();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !job.collapsing && !job.fellSpent && job.fellLog != null) {
                startChop(player, job);
            }
        }, 8L);
    }

    /** Hands a finished fell to the Foraging Eldervale router (no-op off the isle / for demo trees). */
    private void fellHook(Player player, TreeJob job, boolean perfect, int streak, boolean cleaver, Location crown) {
        ForageIsle isle = ForageIsle.get();
        if (isle == null || player == null || !player.isOnline() || job == null || job.noLoot) {
            return;
        }
        try {
            isle.onFell(player, new FellContext(job.baseLocation(), crown == null ? job.crownLocation() : crown,
                    job.logs.size(), Wood.of(job.family), perfect, streak, cleaver, job.notched));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Foraging Eldervale fell hook failed: " + ex);
        }
    }

    /**
     * Reward beat after the canopy is down: result, wood paid against the tree's cap, and the
     * focus Foraging skill's level bar (bonus XP lands first so the bar shows it).
     */
    private void scheduleTally(TreeJob job, UUID playerId, String word, int streak, int bonusXp) {
        if (job == null || job.noLoot || playerId == null) {
            return;
        }
        long wait = TreeFall.LAND_TICKS + 10L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            ForagingSkills.bonus(player, bonusXp);
            StringBuilder line = new StringBuilder(word).append(ForagingStrike.streakTag(streak));
            if (job.woodCap > 0) {
                line.append(" §8· §f").append(job.woodPaid).append("§8/§7").append(job.woodCap)
                        .append(" §7").append(woodName(job.lastDrop));
            }
            String credit = ForagingSkills.credit(player);
            if (credit != null) {
                line.append("  §8│  ").append(credit);
            } else if (tipped.add(playerId)) {
                player.sendMessage("§8Tip: equip a Foraging skill in §7/skills foraging §8— it levels on every log,"
                        + " and Perfect fells pay extra.");
            }
            ForagingFx.tally(player, line.toString());
            ForageText.hold(player, 3000L);
            ForageIsle isle = ForageIsle.get();
            String mastery = isle == null ? null : isle.masteryLine(player, job.family, job.baseLocation());
            if (mastery != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        ForageText.bar(player, "§2Grove Mastery §8· " + mastery);
                    }
                }, 40L);
            }
        }, wait);
    }

    private static void announceStreak(Player player, int streak) {
        if (streak == ForagingStrike.HOT_STREAK) {
            player.sendMessage("§6✦ Five clean fells. §7The glowing spot now stays lit longer. Don't waste it.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.5f);
        } else if (streak > ForagingStrike.HOT_STREAK && streak % 10 == 0) {
            player.sendMessage("§6✦ " + streak + " clean fells. §7The trees have started a support group.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.7f);
        }
    }

    private static String woodName(Material drop) {
        if (drop == null) {
            return "wood";
        }
        String name = drop.name().toLowerCase(Locale.ROOT)
                .replace("_log", "")
                .replace("_stem", "")
                .replace("_block", "");
        return name.replace('_', ' ');
    }

    private void missChop(FellAim pulse) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
            return;
        }
        pulse.release(Bukkit.getPlayer(pulse.playerId));
        pulse.job.pulse = null;
        pulse.job.felling = false;
        // Keep fell mark — only this player is locked from THIS tree briefly.
        // Other trees stay instantly available (no global chop cooldown).
        long lockMs = markMissCooldown(pulse.playerId, pulse.job);
        Player player = Bukkit.getPlayer(pulse.playerId);
        ForageIsle isle = ForageIsle.get();
        boolean secondWind = player != null && isle != null && isle.secondWind(player, pulse.job.baseLocation());
        int lost = secondWind ? 0 : streaks.reset(pulse.playerId);
        hud.hide(pulse.playerId);
        if (player != null && player.isOnline()) {
            ForagingFx.miss(player, pulse.missReason(), lost, Math.max(1L, (lockMs + 999L) / 1000L));
            if (secondWind) {
                player.sendMessage("§b✦ Second Wind §8· §7Sure Grip kept your streak alive.");
            }
            if (taughtMiss.add(pulse.playerId)) {
                player.sendMessage("§7Left mid-fell. §fThat trunk§7 needs a breather — a few seconds at first,"
                        + " longer if it keeps happening. §fOther trees are free.");
            }
            if (isle != null) {
                isle.onMiss(player, pulse.job.baseLocation());
            }
        }
    }

    /** Abort a live aim without punishing the tree (e.g. start another trunk). */
    private void cancelChop(FellAim pulse) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
            return;
        }
        pulse.release(Bukkit.getPlayer(pulse.playerId));
        pulse.job.pulse = null;
        pulse.job.felling = false;
        hud.hide(pulse.playerId);
    }

    private boolean isChopMissCooling(Player player, TreeJob job) {
        if (player == null || job == null || job.treeId == null) {
            return false;
        }
        Long until = chopMissUntil.get(missKey(player.getUniqueId(), job.treeId));
        return until != null && until > System.currentTimeMillis();
    }

    private long missCooldownLeftMs(Player player, TreeJob job) {
        if (player == null || job == null || job.treeId == null) {
            return 0L;
        }
        return missCooldownLeftMs(player.getUniqueId(), job.treeId);
    }

    private long missCooldownLeftMs(UUID playerId, String treeId) {
        if (playerId == null || treeId == null) {
            return 0L;
        }
        Long until = chopMissUntil.get(missKey(playerId, treeId));
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /** Locks this trunk for this player and returns the lock length (ms). */
    private long markMissCooldown(UUID playerId, TreeJob job) {
        if (playerId == null || job == null || job.treeId == null) {
            return 0L;
        }
        String key = missKey(playerId, job.treeId);
        long now = System.currentTimeMillis();
        long[] strike = chopMissStrikes.compute(key, (k, prev) -> {
            if (prev == null || now - prev[1] > CHOP_MISS_MEMORY_MS) {
                return new long[]{1L, now};
            }
            return new long[]{prev[0] + 1L, now};
        });
        long ms = missCooldownMs(playerId, job, (int) strike[0]);
        chopMissUntil.put(key, now + ms);
        return ms;
    }

    /**
     * Graduated base (12s → 30s → 60s for repeat misses on the same trunk), then shortened on the isle by
     * the Sure Grip mark and the Steady Hands skill.
     */
    private static long missCooldownMs(UUID playerId, TreeJob job, int strikes) {
        int step = Math.max(0, Math.min(CHOP_MISS_STEPS_MS.length - 1, strikes - 1));
        long base = CHOP_MISS_STEPS_MS[step];
        ForageIsle isle = ForageIsle.get();
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (isle == null || player == null || job == null) {
            return base;
        }
        return isle.missCooldown(player, job.baseLocation(), base);
    }

    private void clearMissCooldown(UUID playerId, TreeJob job) {
        if (playerId == null || job == null || job.treeId == null) {
            return;
        }
        String key = missKey(playerId, job.treeId);
        chopMissUntil.remove(key);
        chopMissStrikes.remove(key);
    }

    private static String missKey(UUID playerId, String treeId) {
        return playerId + "|" + treeId;
    }

    private static void tryForestDragonFromChop(Player player) {
        try {
            Class.forName("de.aetherion.aethermobs.listener.ForagingDragonListener")
                    .getMethod("tryFromSuccessfulChop", Player.class)
                    .invoke(null, player);
        } catch (Throwable ignored) {
        }
    }

    private void collapse(TreeJob job) {
        if (job.collapsing) {
            return;
        }
        job.collapsing = true;
        if (job.breaker != null) {
            collapsingPlayers.add(job.breaker);
        }
        if (job.suppressSwing != null) {
            collapsingPlayers.add(job.suppressSwing);
        }

        List<Snapshot> leftover = new ArrayList<>();
        for (Snapshot log : job.logs) {
            if (isLog(log.block().getType())) {
                leftover.add(log);
            }
        }
        leftover.sort(Comparator.comparingInt((Snapshot snapshot) -> snapshot.y).reversed());

        int delay = 0;
        for (Snapshot log : leftover) {
            int wait = delay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block block = log.block();
                if (!isLog(block.getType())) {
                    return;
                }
                regenerating.add(log.key());
                collectLog(job, log);
                // Silent clear — no player dig / swing animation.
                block.setType(Material.AIR, false);
            }, wait);
            delay += 1;
        }

        delay += 1;
        int decorDelay = 0;
        for (Snapshot decor : job.decor) {
            int wait = delay + (decorDelay / 2);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block block = decor.block();
                if (!isTreeDecor(block.getType())) {
                    return;
                }
                regenerating.add(decor.key());
                block.setType(Material.AIR, false);
            }, wait);
            decorDelay++;
        }

        delay += Math.max(1, (job.decor.size() + 1) / 2);
        int leafDelay = 0;
        for (Snapshot leaf : job.leaves) {
            int wait = delay + (leafDelay / 3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block block = leaf.block();
                if (!isCanopy(block.getType())) {
                    return;
                }
                regenerating.add(leaf.key());
                block.setType(Material.AIR, false);
            }, wait);
            leafDelay++;
        }

        int clearAt = delay + Math.max(2, (job.leaves.size() + 2) / 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (job.breaker != null) {
                collapsingPlayers.remove(job.breaker);
            }
            if (job.suppressSwing != null) {
                collapsingPlayers.remove(job.suppressSwing);
            }
        }, clearAt + 2L);

        int restoreAt = clearAt + (int) REGROW_TICKS;
        for (Snapshot snapshot : job.all()) {
            regenerating.add(snapshot.key());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> restore(job), restoreAt);
    }

    /**
     * The animated fell (A-51): the tree tips over towards {@code dir} and lies down (or hangs up at
     * {@code stopAt} degrees). The world blocks go at once, the picture falls, and the wood is paid on the
     * thud; {@code onLand} runs right after. Regrow and swing suppression work like {@link #collapse}.
     */
    private void fall(TreeJob job, Vector dir, double stopAt, Runnable onLand) {
        if (job.collapsing) {
            return;
        }
        Location base = fellBase(job);
        if (base == null || base.getWorld() == null) {
            collapse(job);
            if (onLand != null) {
                onLand.run();
            }
            return;
        }
        job.collapsing = true;
        if (job.breaker != null) {
            collapsingPlayers.add(job.breaker);
        }
        if (job.suppressSwing != null) {
            collapsingPlayers.add(job.suppressSwing);
        }
        Location pivot = base.clone().add(dir.getX() * 0.5d, 0.0d, dir.getZ() * 0.5d);
        List<TreeFall.Piece> pieces = new ArrayList<>();
        List<Snapshot> standing = new ArrayList<>();
        for (Snapshot log : job.logs) {
            Block block = log.block();
            if (isLog(block.getType())) {
                standing.add(log);
                pieces.add(new TreeFall.Piece(log.x() - pivot.getX(), log.y() - pivot.getY(), log.z() - pivot.getZ(),
                        block.getBlockData(), true));
            }
        }
        for (Snapshot leaf : job.leaves) {
            Block block = leaf.block();
            if (isCanopy(block.getType())) {
                pieces.add(new TreeFall.Piece(leaf.x() - pivot.getX(), leaf.y() - pivot.getY(), leaf.z() - pivot.getZ(),
                        block.getBlockData(), false));
            }
        }
        for (Snapshot snapshot : job.all()) {
            regenerating.add(snapshot.key());
        }
        // Clear the real tree in one go — the displays take its place in the same tick.
        for (Snapshot log : standing) {
            log.block().setType(Material.AIR, false);
        }
        for (Snapshot decor : job.decor) {
            if (isTreeDecor(decor.block().getType())) {
                decor.block().setType(Material.AIR, false);
            }
        }
        for (Snapshot leaf : job.leaves) {
            if (isCanopy(leaf.block().getType())) {
                leaf.block().setType(Material.AIR, false);
            }
        }
        TreeFall.play(plugin, pivot, dir, stopAt, pieces, () -> {
            for (Snapshot log : standing) {
                collectLog(job, log);
            }
            if (onLand != null) {
                try {
                    onLand.run();
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Fell landing hook failed: " + ex);
                }
            }
        });
        int clearAt = TreeFall.LAND_TICKS + 6;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (job.breaker != null) {
                collapsingPlayers.remove(job.breaker);
            }
            if (job.suppressSwing != null) {
                collapsingPlayers.remove(job.suppressSwing);
            }
        }, clearAt);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restore(job), clearAt + REGROW_TICKS);
    }

    private void restore(TreeJob job) {
        if (job.breaker != null) {
            collapsingPlayers.remove(job.breaker);
        }
        if (job.suppressSwing != null) {
            collapsingPlayers.remove(job.suppressSwing);
        }
        for (Snapshot snapshot : job.all()) {
            regenerating.remove(snapshot.key());
            jobs.remove(snapshot.key());
            Block block = snapshot.block();
            Material current = block.getType();
            if (!current.isAir()
                    && !isLog(current)
                    && !isCanopy(current)
                    && !isTreeDecor(current)
                    && current != Material.VINE) {
                continue;
            }
            block.setBlockData(snapshot.data(), false);
        }
    }

    private TreeJob createJob(Block start, Player player, Block skipMark) {
        List<Block> logs = collectLogs(start);
        if (logs.isEmpty()) {
            return null;
        }
        List<Block> leaves = collectConnectedCanopy(logs);
        // No connected canopy = not a living tree (cabins / builds stay vanilla).
        if (leaves.isEmpty()) {
            return null;
        }
        TreeJob job = new TreeJob();
        job.touchedTick = Bukkit.getCurrentTick();
        if (player != null) {
            job.breaker = player.getUniqueId();
        }
        for (Block log : logs) {
            Snapshot snapshot = Snapshot.of(log);
            job.logs.add(snapshot);
            jobs.put(snapshot.key(), job);
        }
        job.treeId = job.logs.stream()
                .map(Snapshot::key)
                .sorted()
                .findFirst()
                .orElse(key(start));
        for (Block leaf : leaves) {
            job.leaves.add(Snapshot.of(leaf));
        }
        for (Block decor : collectTreeDecor(logs, leaves)) {
            job.decor.add(Snapshot.of(decor));
        }
        job.family = dominantWood(job.logs);
        markBase(job, skipMark);
        return job;
    }

    /** Most common typed drop in the trunk — custom trees mix woods, the majority names the tree. */
    private static Material dominantWood(List<Snapshot> logs) {
        Map<Material, Integer> counts = new java.util.EnumMap<>(Material.class);
        for (Snapshot log : logs) {
            counts.merge(dropLog(log.data().getMaterial()), 1, Integer::sum);
        }
        Material best = Material.OAK_LOG;
        int most = 0;
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private void markBase(TreeJob job, Block breaking) {
        if (!plugin.fellEnabled() || job.logs.isEmpty()) {
            return;
        }
        List<Snapshot> byY = new ArrayList<>(job.logs);
        byY.sort(Comparator.comparingInt(snapshot -> snapshot.y));
        int minY = byY.get(0).y;
        // Only the bottom row of the trunk — never mid/upper stems.
        List<Snapshot> bottomRow = new ArrayList<>();
        for (Snapshot snapshot : byY) {
            if (snapshot.y != minY) {
                break;
            }
            // True stump: something that isn't another log under it.
            Block below = snapshot.block().getRelative(0, -1, 0);
            if (!isLog(below.getType())) {
                bottomRow.add(snapshot);
            }
        }
        if (bottomRow.isEmpty()) {
            for (Snapshot snapshot : byY) {
                if (snapshot.y != minY) {
                    break;
                }
                bottomRow.add(snapshot);
            }
        }
        String breakingKey = breaking == null ? null : key(breaking);
        Snapshot chosen = null;
        for (Snapshot snapshot : bottomRow) {
            if (breakingKey == null || !snapshot.key().equals(breakingKey)) {
                chosen = snapshot;
                break;
            }
        }
        // If the only bottom block is being broken, clear the mark — do not promote upper logs.
        job.fellLog = chosen;
        job.fellMarks.clear();
        if (chosen != null) {
            job.fellMarks.addAll(bottomRow);
        }
    }

    private static List<Block> collectLogs(Block start) {
        // Custom map trees often mix wood types in one trunk — accept any log in the cluster.
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(key(start));
        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            if (!isLog(current.getType())) {
                continue;
            }
            if (!inRange(start, current)) {
                continue;
            }
            found.add(current);
            for (BlockFace face : FACES) {
                offer(queue, seen, current.getRelative(face));
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    offer(queue, seen, current.getRelative(dx, 0, dz));
                    offer(queue, seen, current.getRelative(dx, 1, dz));
                }
            }
        }
        return found;
    }

    /**
     * Canopy must touch the trunk (or touch other canopy already linked to it).
     * Radius-only leaf scans let houses with a decorative leaf nearby count as trees.
     * Harbour/custom builds: any leaf/wart canopy counts (family mismatch is common).
     */
    private static List<Block> collectConnectedCanopy(List<Block> logs) {
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        for (Block log : logs) {
            for (BlockFace face : FACES) {
                seedCanopy(queue, seen, found, log.getRelative(face));
            }
            // Corner-touching leaves (common on oak/birch crowns and custom trees).
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        seedCanopy(queue, seen, found, log.getRelative(dx, dy, dz));
                    }
                }
            }
        }
        while (!queue.isEmpty() && found.size() < 384) {
            Block current = queue.poll();
            for (BlockFace face : FACES) {
                Block next = current.getRelative(face);
                if (!isCanopy(next.getType())) {
                    continue;
                }
                if (!nearAnyLog(logs, next)) {
                    continue;
                }
                if (seen.add(key(next))) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static void seedCanopy(
            Queue<Block> queue,
            Set<String> seen,
            List<Block> found,
            Block block
    ) {
        if (!isCanopy(block.getType())) {
            return;
        }
        if (seen.add(key(block))) {
            found.add(block);
            queue.add(block);
        }
    }

    /**
     * Wood fixtures hanging on custom map trees (fences, slabs, stairs, trapdoors, signs…).
     * Flood from trunk only — house builds nearby stay unless they touch the trunk cluster.
     */
    private static List<Block> collectTreeDecor(List<Block> logs, List<Block> leaves) {
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        for (Block log : logs) {
            seen.add(key(log));
            for (BlockFace face : FACES) {
                seedDecor(queue, seen, found, log.getRelative(face));
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        seedDecor(queue, seen, found, log.getRelative(dx, dy, dz));
                    }
                }
            }
        }
        // Crown props often sit on leaves, not on trunk.
        for (Block leaf : leaves) {
            for (BlockFace face : FACES) {
                seedDecor(queue, seen, found, leaf.getRelative(face));
            }
        }
        while (!queue.isEmpty() && found.size() < 256) {
            Block current = queue.poll();
            for (BlockFace face : FACES) {
                Block next = current.getRelative(face);
                if (!isTreeDecor(next.getType())) {
                    continue;
                }
                if (!nearAnyLog(logs, next) && !nearAnyBlock(leaves, next, 2, 3)) {
                    continue;
                }
                if (seen.add(key(next))) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static void seedDecor(
            Queue<Block> queue,
            Set<String> seen,
            List<Block> found,
            Block block
    ) {
        if (!isTreeDecor(block.getType())) {
            return;
        }
        if (seen.add(key(block))) {
            found.add(block);
            queue.add(block);
        }
    }

    private static boolean nearAnyLog(List<Block> logs, Block canopy) {
        return nearAnyBlock(logs, canopy, MAX_LEAF_RADIUS + 2, MAX_LEAF_RADIUS + 4);
    }

    private static boolean nearAnyBlock(List<Block> anchors, Block at, int horiz, int vert) {
        for (Block anchor : anchors) {
            int dx = Math.abs(at.getX() - anchor.getX());
            int dy = Math.abs(at.getY() - anchor.getY());
            int dz = Math.abs(at.getZ() - anchor.getZ());
            if (Math.max(dx, dz) <= horiz && dy <= vert) {
                return true;
            }
        }
        return false;
    }

    private static void offer(Queue<Block> queue, Set<String> seen, Block next) {
        if (seen.add(key(next))) {
            queue.add(next);
        }
    }

    private static boolean inRange(Block origin, Block current) {
        int dx = Math.abs(current.getX() - origin.getX());
        int dy = Math.abs(current.getY() - origin.getY());
        int dz = Math.abs(current.getZ() - origin.getZ());
        return Math.max(dx, dz) <= MAX_HORIZONTAL && dy <= MAX_VERTICAL;
    }

    private static boolean allowsLog(Block block) {
        // Only bypass WG for real trees (connected canopy). Builds stay protected.
        if (block == null || !isLog(block.getType())) {
            return false;
        }
        return hasConnectedCanopy(block);
    }

    private void hintLeafless(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage("§cOnly living trees can be chopped. Logs without leaves stay put.");
    }

    private static boolean hasConnectedCanopy(Block start) {
        List<Block> logs = collectLogs(start);
        return !logs.isEmpty() && !collectConnectedCanopy(logs).isEmpty();
    }

    private static boolean isAxe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        try {
            if (Tag.ITEMS_AXES.isTagged(type)) {
                return true;
            }
        } catch (NoSuchFieldError | NoSuchMethodError ignored) {
        }
        return type.name().endsWith("_AXE");
    }

    private static boolean isLog(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LOGS.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_WOOD")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_STEM")
                || name.equals("MUSHROOM_STEM");
    }

    private static boolean isLeaf(Material material) {
        return material != null && material.name().endsWith("_LEAVES");
    }

    private static boolean isCanopy(Material material) {
        if (isLeaf(material)) {
            return true;
        }
        if (material == null) {
            return false;
        }
        // Fantasy / custom map crowns (not ground moss — that stays put).
        return material == Material.NETHER_WART_BLOCK
                || material == Material.WARPED_WART_BLOCK
                || material == Material.SHROOMLIGHT
                || material == Material.AZALEA
                || material == Material.FLOWERING_AZALEA
                || material == Material.MANGROVE_ROOTS
                || material == Material.MUDDY_MANGROVE_ROOTS
                || material.name().endsWith("_SAPLING")
                || material == Material.VINE
                || material == Material.GLOW_LICHEN;
    }

    /** Wooden extras attached to harbour / custom trees — cleared with the fall, no drops. */
    private static boolean isTreeDecor(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        try {
            if (Tag.WOODEN_FENCES.isTagged(material)
                    || Tag.FENCE_GATES.isTagged(material)
                    || Tag.WOODEN_SLABS.isTagged(material)
                    || Tag.WOODEN_STAIRS.isTagged(material)
                    || Tag.WOODEN_TRAPDOORS.isTagged(material)
                    || Tag.WOODEN_BUTTONS.isTagged(material)
                    || Tag.WOODEN_PRESSURE_PLATES.isTagged(material)
                    || Tag.ALL_SIGNS.isTagged(material)) {
                return true;
            }
        } catch (NoSuchFieldError | NoSuchMethodError ignored) {
        }
        String name = material.name();
        if (material == Material.LADDER) {
            return true;
        }
        if (name.contains("SIGN")) {
            return true;
        }
        if (!isWoodenName(name)) {
            return false;
        }
        return name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_SLAB")
                || name.endsWith("_STAIRS")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE");
    }

    private static boolean isWoodenName(String name) {
        return name.contains("OAK") || name.contains("SPRUCE") || name.contains("BIRCH")
                || name.contains("JUNGLE") || name.contains("ACACIA") || name.contains("DARK_OAK")
                || name.contains("MANGROVE") || name.contains("CHERRY") || name.contains("BAMBOO")
                || name.contains("CRIMSON") || name.contains("WARPED") || name.contains("PALE");
    }

    private static boolean canopyMatches(String logFamily, Material canopy) {
        if (logFamily == null || canopy == null) {
            return false;
        }
        if (isLeaf(canopy)) {
            String leafFamily = family(canopy);
            if (logFamily.equals(leafFamily)) {
                return true;
            }
            // Azalea grows on oak trunks.
            return logFamily.equals("OAK")
                    && (leafFamily.equals("AZALEA") || leafFamily.equals("FLOWERING_AZALEA"));
        }
        if (canopy == Material.SHROOMLIGHT) {
            return logFamily.equals("CRIMSON") || logFamily.equals("WARPED");
        }
        if (canopy == Material.NETHER_WART_BLOCK) {
            return logFamily.equals("CRIMSON");
        }
        if (canopy == Material.WARPED_WART_BLOCK) {
            return logFamily.equals("WARPED");
        }
        return false;
    }

    private static String family(Material material) {
        return material.name()
                .replace("STRIPPED_", "")
                .replace("FLOWERING_", "")
                .replace("_LOG", "")
                .replace("_WOOD", "")
                .replace("_LEAVES", "")
                .replace("_STEM", "")
                .replace("_HYPHAE", "")
                .replace("_WART_BLOCK", "");
    }

    /** Wood/hyphae (all-side bark) collapse to the matching log/stem so inv stays one stack. */
    private static Material dropLog(Material material) {
        if (material == null) {
            return Material.OAK_LOG;
        }
        String name = material.name().replace("STRIPPED_", "");
        if (name.endsWith("_WOOD")) {
            name = name.substring(0, name.length() - "_WOOD".length()) + "_LOG";
        } else if (name.endsWith("_HYPHAE")) {
            name = name.substring(0, name.length() - "_HYPHAE".length()) + "_STEM";
        }
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return material;
        }
    }

    private void collectLog(TreeJob job, Snapshot log) {
        if (job.noLoot) {
            return;
        }
        Material source = log.data().getMaterial();
        Material drop = forcedDropOr(source, log.block().getLocation());
        ItemStack stack = new ItemStack(drop, 1);
        Player player = job.breaker == null ? null : Bukkit.getPlayer(job.breaker);
        Location at = log.block().getLocation().add(0.5, 0.5, 0.5);
        if (player != null && player.isOnline()) {
            ensureWoodCap(job, player);
            if (job.woodPaid >= job.woodCap) {
                return;
            }
            // Cap is the balance lever — no per-log Fortune mult here (Fortune raises the cap).
            give(player, stack, log.block().getLocation());
            grantWood(player, drop);
            grantAxe(player);
            maybeIsleHeartwood(player, drop);
            QuestProgressHook.noteBroken(player, drop, 1);
            job.woodPaid++;
            job.lastDrop = drop;
            return;
        }
        if (at.getWorld() != null) {
            at.getWorld().dropItemNaturally(at, stack);
        }
    }

    /**
     * Soft start balance: whole tree falls, but wood payout is hard-capped.
     * Base 10, +1 per 10 Foraging skill levels, +floor(Fortune/25).
     */
    private void ensureWoodCap(TreeJob job, Player player) {
        if (job == null || job.woodCap > 0) {
            return;
        }
        int base = woodCapFor(player);
        ForageIsle isle = ForageIsle.get();
        job.woodCap = isle == null ? base : isle.woodCap(player, job.family, job.baseLocation(), base, job.notched);
    }

    private static int woodCapFor(Player player) {
        int cap = 10;
        int foraging = foragingLevel(player);
        cap += Math.max(0, foraging / 10);
        double fortune = fortune(player);
        cap += Math.max(0, (int) Math.floor(fortune / 25.0d));
        return Math.max(1, cap);
    }

    private static int foragingLevel(Player player) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getSkills() == null) {
                return 0;
            }
            var skills = items.getSkills();
            int best = 0;
            for (de.aetherion.items.skill.AetherSkill skill : de.aetherion.items.skill.AetherSkill.values()) {
                if (skill.category() != de.aetherion.items.skill.AetherSkill.Category.FORAGING) {
                    continue;
                }
                best = Math.max(best, skills.level(player, skill));
            }
            return best;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static double fortune(Player player) {
        try {
            de.aetherion.core.api.HarvestAccess harvest = de.aetherion.core.api.AetherServices.harvest();
            if (harvest != null) {
                return Math.max(0.0d, harvest.fortuneOf(player));
            }
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getHarvestListener() == null) {
                return 0.0d;
            }
            return Math.max(0.0d, items.getHarvestListener().fortuneOf(player));
        } catch (Throwable ignored) {
            return 0.0d;
        }
    }

    /**
     * Harbour: force oak from config. Forage Isle area: typed wood matching the log.
     * Regrow always restores original BlockData regardless of this.
     */
    private Material forcedDropOr(Material source, Location at) {
        if (inForageIsle(at)) {
            return dropLog(source);
        }
        String forced = plugin.getConfig().getString("foraging.force-drop", "OAK_LOG");
        if (forced != null && !forced.isBlank()) {
            try {
                return Material.valueOf(forced.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return dropLog(source);
    }

    private static boolean inForageIsle(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getAreas() != null
                    && items.getAreas().isType(at, AreaType.FORAGE_ISLE)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return de.aetherion.foraging.habitat.ForageHabitatService.inIsleFootprint(at);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void give(Player player, ItemStack stack, org.bukkit.Location at) {
        try {
            InventoryDrops.give(player, stack, at);
        } catch (NoClassDefFoundError ignored) {
            if (at != null && at.getWorld() != null) {
                at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.2, 0.5), stack);
            } else {
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
    }

    private static boolean payWood(Player player, Material material) {
        try {
            de.aetherion.core.api.HarvestAccess harvest = de.aetherion.core.api.AetherServices.harvest();
            if (harvest != null) {
                harvest.payWood(player, material);
                return true;
            }
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getHarvestListener() != null) {
                items.getHarvestListener().payWood(player, material);
                return true;
            }
        } catch (NoClassDefFoundError ignored) {
        }
        return false;
    }

    private static void grantAxe(Player player) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getItemManager() == null) {
                return;
            }
            ItemStack tool = player.getInventory().getItemInMainHand();
            de.aetherion.items.item.ForagingAxeProgress.grant(player, tool, items.getItemManager(), 4);
        } catch (Throwable ignored) {
        }
    }

    private void maybeIsleHeartwood(Player player, Material drop) {
        if (player == null || drop == null || !inForageIsle(player.getLocation())) {
            return;
        }
        double chance = plugin.getConfig().getDouble("isle-heartwood.chance", 0.03);
        ForageIsle isle = ForageIsle.get();
        if (isle != null) {
            chance = isle.heartwoodChance(player, drop, chance);
        }
        if (chance <= 0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        try {
            Class<?> heart = Class.forName("de.aetherion.items.economy.IsleHeartwood");
            Object kind = heart.getMethod("fromWoodDrop", Material.class).invoke(null, drop);
            if (kind == null) {
                return;
            }
            ItemStack stack = (ItemStack) kind.getClass().getMethod("create").invoke(kind);
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            give(player, stack, player.getLocation());
            String name = String.valueOf(kind.getClass().getMethod("itemId").invoke(kind));
            // P1 Signature: count the find in the Codex (heartwood already carries its Items id).
            de.aetherion.core.api.ItemFactoryAccess signatures = de.aetherion.core.api.AetherServices.items();
            if (signatures != null) {
                signatures.noteSignature(player, name);
            }
            player.sendMessage("§d✦ Rare heartwood §8· §f" + name.replace("isle_heartwood_", "").replace('_', ' '));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
        } catch (Throwable ignored) {
        }
    }

    private static void grantWood(Player player, Material material) {
        try {
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getSkills() != null) {
                items.getSkills().grantFromBlock(player, material);
            }
        } catch (NoClassDefFoundError ignored) {
        }
        // The break event is cancelled (we pay the wood ourselves), so the Codex never heard it — file it here.
        ForageBridge.codexWood(player, material);
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    /** Soft cache for look-preview — never creates entities or TreeJobs. */
    private record LookCache(String blockKey, String treeId, int expiresAt) {
    }

    static final class TreeJob {
        private final List<Snapshot> logs = new ArrayList<>();
        private final List<Snapshot> leaves = new ArrayList<>();
        private final List<Snapshot> decor = new ArrayList<>();
        /** Stable id for per-player miss cooldown (lowest log key). */
        String treeId;
        private boolean collapsing;
        private boolean felling;
        private boolean fellSpent;
        private boolean noLoot;
        /** Soft wood payout cap for this fell (0 = unset). */
        int woodCap;
        int woodPaid;
        /** Last wood type paid out — names the tally ("12/14 spruce"). */
        Material lastDrop;
        /** Majority typed wood of the trunk (mastery, finds, Titan dust). */
        Material family = Material.OAK_LOG;
        /** Foraging Eldervale Titan: the first clean cut landed. */
        boolean notched;
        UUID breaker;
        UUID suppressSwing;
        Snapshot fellLog;
        /** Last chop / create tick — used to abandon incomplete jobs. */
        int touchedTick;
        /** Bottom-row stump marks for sparks (same Y, ground-adjacent). */
        private final List<Snapshot> fellMarks = new ArrayList<>();
        private FellAim pulse;

        /** Every block of this tree, as {@link FellLane#key} — never in its own way. */
        Set<Long> blockKeys() {
            Set<Long> keys = new HashSet<>(logs.size() + leaves.size() + decor.size());
            for (Snapshot s : logs) {
                keys.add(FellLane.key(s.x(), s.y(), s.z()));
            }
            for (Snapshot s : leaves) {
                keys.add(FellLane.key(s.x(), s.y(), s.z()));
            }
            for (Snapshot s : decor) {
                keys.add(FellLane.key(s.x(), s.y(), s.z()));
            }
            return keys;
        }

        /** Trunk + crown height above the stump: how far the tree reaches when it lies flat. */
        int reach() {
            int low = Integer.MAX_VALUE;
            int top = Integer.MIN_VALUE;
            for (Snapshot s : logs) {
                low = Math.min(low, s.y());
                top = Math.max(top, s.y());
            }
            for (Snapshot s : leaves) {
                top = Math.max(top, s.y());
            }
            if (low == Integer.MAX_VALUE) {
                return 6;
            }
            return Math.max(4, Math.min(30, top - low + 1));
        }

        private List<Snapshot> all() {
            List<Snapshot> all = new ArrayList<>(logs.size() + leaves.size() + decor.size());
            all.addAll(logs);
            all.addAll(leaves);
            all.addAll(decor);
            return all;
        }

        private Location anchor() {
            Snapshot at = fellLog != null ? fellLog : (logs.isEmpty() ? null : logs.getFirst());
            return at == null ? null : at.block().getLocation();
        }

        Location fellLocation() {
            return fellLog == null ? null : fellLog.block().getLocation();
        }

        /** The stump: lowest log of the trunk. */
        Location baseLocation() {
            Snapshot low = null;
            for (Snapshot log : logs) {
                if (low == null || log.y < low.y) {
                    low = log;
                }
            }
            return low == null ? null : new Location(low.world(), low.x() + 0.5, low.y(), low.z() + 0.5);
        }

        /** Top of the crown, centred over the top layer of leaves (where Crown Finds let go). */
        Location crownLocation() {
            if (leaves.isEmpty()) {
                Location base = baseLocation();
                if (base == null) {
                    return null;
                }
                int top = base.getBlockY();
                for (Snapshot log : logs) {
                    top = Math.max(top, log.y);
                }
                return new Location(base.getWorld(), base.getX(), top + 2.0, base.getZ());
            }
            int topY = Integer.MIN_VALUE;
            for (Snapshot leaf : leaves) {
                topY = Math.max(topY, leaf.y);
            }
            double sx = 0;
            double sz = 0;
            int n = 0;
            World world = null;
            for (Snapshot leaf : leaves) {
                if (leaf.y >= topY - 1) {
                    sx += leaf.x();
                    sz += leaf.z();
                    n++;
                    world = leaf.world();
                }
            }
            return new Location(world, sx / n + 0.5, topY + 0.8, sz / n + 0.5);
        }
    }

    private record Snapshot(World world, int x, int y, int z, BlockData data) {
        static Snapshot of(Block block) {
            return new Snapshot(block.getWorld(), block.getX(), block.getY(), block.getZ(), block.getBlockData().clone());
        }

        Block block() {
            return world.getBlockAt(x, y, z);
        }

        String key() {
            return world.getUID() + ":" + x + ":" + y + ":" + z;
        }
    }
}
