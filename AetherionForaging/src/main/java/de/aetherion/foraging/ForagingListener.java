package de.aetherion.foraging;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.util.InventoryDrops;
import de.aetherion.items.world.AreaType;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
    private static final long CANOPY_CLEAVER_COOLDOWN_MS = 20_000L;
    /** Incomplete trees without progress get restored so jobs/regenerating cannot leak forever. */
    private static final long IDLE_ABANDON_TICKS = 20L * 90;
    private static final String CANOPY_CLEAVER_ID = "canopy_cleaver";
    private static final int MAX_LOGS = 96;
    private static final int MAX_LEAF_RADIUS = 3;
    private static final int MAX_HORIZONTAL = 8;
    private static final int MAX_VERTICAL = 28;
    private static final double COLLAPSE_AT = 0.80d;

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AetherionForaging plugin;
    private final ForagingHud hud = new ForagingHud();
    private final Set<String> regenerating = ConcurrentHashMap.newKeySet();
    private final Map<String, TreeJob> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, FellPulse> pulses = new ConcurrentHashMap<>();
    /** Per-player cooldown on a failed tree only (uuid|treeId → untilMillis). */
    private final Map<String, Long> chopMissUntil = new ConcurrentHashMap<>();
    /** Canopy Cleaver perfect-fell cooldown (uuid → untilMillis). */
    private final Map<UUID, Long> canopyCleaverUntil = new ConcurrentHashMap<>();
    /** Players whose tree is collapsing — suppress arm swing / dig spam. */
    private final Set<UUID> collapsingPlayers = ConcurrentHashMap.newKeySet();

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
        // Fell-mark log: axe starts CHOP. Hand / non-axe can break it normally (no minigame).
        if (job.fellLog != null && !job.fellSpent && job.fellLog.key().equals(startKey)) {
            if (isAxe(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                startChop(player, job);
                return;
            }
            job.fellSpent = true;
            job.fellLog = null;
            job.fellMarks.clear();
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
        // Cancel so MiningListener (HIGHEST) does not also pay out this log at the player.
        event.setCancelled(true);
        Material drop = forcedDropOr(start.getType(), start.getLocation());
        give(player, new ItemStack(drop, 1), start.getLocation());
        grantWood(player, drop);
        grantAxe(player);
        maybeIsleHeartwood(player, drop);
        QuestProgressHook.noteBroken(player, drop, 1);
        job.woodPaid++;
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
        FellPulse pulse = pulses.get(player.getUniqueId());
        if (pulse != null) {
            if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
                return;
            }
            event.setCancelled(true);
            if (pulse.chop()) {
                resolveChop(pulse);
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
        if (!job.fellLog.key().equals(key(block))) {
            return;
        }
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
        FellPulse pulse = pulses.get(id);
        if (pulse != null) {
            missChop(pulse);
        }
        collapsingPlayers.remove(id);
        hud.hide(event.getPlayer());
        ForagerChopDemo.releaseOnQuit(event.getPlayer());
    }

    private static void denyChop(Player player) {
        if (ForagerChopDemo.isRunning(player)) {
            player.sendMessage("§7Watch the §eForager§7 finish his chop first.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Wait — Forager is still chopping",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            return;
        }
        player.sendMessage("§7Talk to the §eForager§7 first — he shows you how to chop.");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "→ Forager · chop lesson first",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
    }

    void tick() {
        int now = Bukkit.getCurrentTick();
        if (jobs.isEmpty() && pulses.isEmpty()) {
            return;
        }
        if (!jobs.isEmpty() && now % 100 == 0) {
            pruneIdleJobs(now);
        }
        // No continuous spark particles — they tank FPS on the leafy isle.
        if (pulses.isEmpty()) {
            return;
        }
        for (FellPulse pulse : List.copyOf(pulses.values())) {
            Player player = Bukkit.getPlayer(pulse.playerId);
            if (player == null || !player.isOnline()) {
                missChop(pulse);
                continue;
            }
            boolean timedOut = pulse.tick();
            hud.striking(player, pulse.marker, pulse.zoneStart, pulse.zoneSize, pulse.hot());
            if (timedOut) {
                resolveChop(pulse);
            }
        }
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
    }

    void shutdown() {
        hud.hideAll();
        pulses.clear();
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
            player.sendMessage("§cThat trunk needs a moment (§f"
                    + Math.max(1, (left + 999L) / 1000L)
                    + "s§c). Try another tree.");
            return;
        }
        FellPulse existing = pulses.get(player.getUniqueId());
        if (existing != null) {
            if (existing.job == job) {
                return;
            }
            cancelChop(existing); // switching trees is free — no miss lock
        }
        job.felling = true;
        FellPulse pulse = new FellPulse(player, job, plugin.fellStrikeTicks(), plugin.fellZoneSize());
        job.pulse = pulse;
        pulses.put(player.getUniqueId(), pulse);
        ForagingFx.start(player);
        hud.striking(player, pulse.marker, pulse.zoneStart, pulse.zoneSize, pulse.hot());
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
        FellPulse existing = pulses.get(player.getUniqueId());
        if (existing != null) {
            cancelChop(existing);
        }
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
        collapse(job);
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

    private void resolveChop(FellPulse pulse) {
        if (pulse == null || pulse.job.collapsing) {
            return;
        }
        if (pulse.hit()) {
            landChop(pulse);
        } else {
            missChop(pulse);
        }
    }

    private void landChop(FellPulse pulse) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
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
        if (player != null) {
            ensureWoodCap(pulse.job, player);
            ForagingFx.success(player, at);
            // Release held dig so the client stops flailing the axe.
            player.clearActiveItem();
            tryForestDragonFromChop(player);
        }
        collapse(pulse.job);
    }

    private void missChop(FellPulse pulse) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
            return;
        }
        pulse.job.pulse = null;
        pulse.job.felling = false;
        // Keep fell mark — only this player is locked from THIS tree briefly.
        // Other trees stay instantly available (no global chop cooldown).
        markMissCooldown(pulse.playerId, pulse.job);
        Player player = Bukkit.getPlayer(pulse.playerId);
        hud.hide(pulse.playerId);
        if (player != null && player.isOnline()) {
            ForagingFx.miss(player);
            player.sendMessage("§7Missed the timing. §fThat trunk§7 cools ~1 min — other trees are free.");
        }
    }

    /** Abort an active bar without punishing the tree (e.g. start another trunk). */
    private void cancelChop(FellPulse pulse) {
        if (pulse == null || !pulses.remove(pulse.playerId, pulse)) {
            return;
        }
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
        Long until = chopMissUntil.get(missKey(player.getUniqueId(), job.treeId));
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private void markMissCooldown(UUID playerId, TreeJob job) {
        if (playerId == null || job == null || job.treeId == null) {
            return;
        }
        chopMissUntil.put(missKey(playerId, job.treeId), System.currentTimeMillis() + CHOP_MISS_COOLDOWN_MS);
    }

    private void clearMissCooldown(UUID playerId, TreeJob job) {
        if (playerId == null || job == null || job.treeId == null) {
            return;
        }
        chopMissUntil.remove(missKey(playerId, job.treeId));
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
        markBase(job, skipMark);
        return job;
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
    private static void ensureWoodCap(TreeJob job, Player player) {
        if (job == null || job.woodCap > 0) {
            return;
        }
        job.woodCap = woodCapFor(player);
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
            AetherionItems items = AetherionItems.getInstance();
            if (items == null || items.getMiningListener() == null) {
                return 0.0d;
            }
            return Math.max(0.0d, items.getMiningListener().fortuneOf(player));
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
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getMiningListener() != null) {
                items.getMiningListener().payWood(player, material);
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
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    static final class TreeJob {
        private final List<Snapshot> logs = new ArrayList<>();
        private final List<Snapshot> leaves = new ArrayList<>();
        private final List<Snapshot> decor = new ArrayList<>();
        /** Stable id for per-player miss cooldown (lowest log key). */
        private String treeId;
        private boolean collapsing;
        private boolean felling;
        private boolean fellSpent;
        private boolean noLoot;
        /** Soft wood payout cap for this fell (0 = unset). */
        int woodCap;
        int woodPaid;
        UUID breaker;
        UUID suppressSwing;
        Snapshot fellLog;
        /** Last chop / create tick — used to abandon incomplete jobs. */
        int touchedTick;
        /** Bottom-row stump marks for sparks (same Y, ground-adjacent). */
        private final List<Snapshot> fellMarks = new ArrayList<>();
        private FellPulse pulse;

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
