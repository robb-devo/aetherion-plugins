package de.aetherion.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingController implements Listener {

    private static final Title.Times BITE_TIMES = Title.Times.times(
            Duration.ofMillis(40),
            Duration.ofMillis(650),
            Duration.ofMillis(120)
    );

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int HARD_WAIT_TICKS = 20 * 14;
    /** Bonus Fishing XP for reeling on the gold cell (base catch is 8, paid by Items). */
    private static final int PERFECT_BONUS_XP = 4;
    /** Streak bonus: +1 per two clean catches, capped — engagement, not a printer. */
    private static final int STREAK_BONUS_CAP = 3;
    /** Bobber must hit water within this window or the cast is discarded. */
    private static final int WATER_SETTLE_TICKS = 36;
    private final AetherionFishing plugin;
    private final FishingStats stats;
    private final FishingHud hud = new FishingHud();
    private final NamespacedKey lureKey;
    private final Map<UUID, CastSession> sessions = new ConcurrentHashMap<>();
    private final CatchStreak streaks = new CatchStreak();
    /** One "equip a Fishing skill" nudge per player per boot. */
    private final Set<UUID> tipped = ConcurrentHashMap.newKeySet();
    private final int baseStrikeTicks;
    private final int approachTicks;
    /** Place hooks (Fishing Eldervale). Null = every cast plays like the harbour. */
    private volatile CastHooks hooks;
    private static volatile Field nibbleField;
    private static volatile Field bitingField;
    private static volatile boolean nibbleLookupDone;

    FishingController(AetherionFishing plugin) {
        this.plugin = plugin;
        this.stats = new FishingStats();
        this.lureKey = new NamespacedKey(plugin, "lure_fish");
        this.baseStrikeTicks = Math.max(40, plugin.getConfig().getInt("strike-ticks", 70));
        this.approachTicks = Math.max(32, Math.min(56, plugin.getConfig().getInt("approach-ticks", 44)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> startCast(player, hook);
            case LURED, BITE -> startApproach(player, hook);
            case CAUGHT_FISH -> {
                // Resolved in HIGH. MONITOR is a safety net.
            }
            case FAILED_ATTEMPT -> {
                CastSession session = sessions.get(player.getUniqueId());
                if (session != null && !session.resolved) {
                    return;
                }
                abort(player, false);
            }
            case REEL_IN, IN_GROUND -> abort(player, false);
            default -> abort(player, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        CastSession session = sessions.get(player.getUniqueId());
        if (session == null || session.resolved) {
            return;
        }
        if (session.phase != CastSession.Phase.STRIKE || !session.inZone()) {
            event.setCancelled(true);
            miss(player, session, event.getHook(), false);
            return;
        }
        land(player, session, event.getHook());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHookEntity(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        Entity caught = event.getCaught();
        if (caught instanceof Player) {
            event.setCancelled(true);
            FishHook hook = event.getHook();
            if (hook != null && hook.isValid()) {
                try {
                    hook.setHookedEntity(null);
                } catch (NoSuchMethodError ignored) {
                }
                hook.remove();
            }
            abort(event.getPlayer(), false);
            return;
        }
        if (LureSchool.marked(caught, lureKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (LureSchool.marked(event.getEntity(), lureKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (LureSchool.marked(event.getEntity(), lureKey) || LureSchool.marked(event.getTarget(), lureKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (LureSchool.marked(event.getRightClicked(), lureKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abort(event.getPlayer(), false);
        streaks.clear(event.getPlayer().getUniqueId());
        CastHooks current = hooks;
        if (current != null) {
            current.forget(event.getPlayer().getUniqueId());
        }
    }

    void hooks(CastHooks hooks) {
        this.hooks = hooks;
    }

    /** True while the player has a live cast (bobber out). */
    public boolean casting(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    /** Clean catches in a row right now (0 when cooled off). */
    public int streak(UUID playerId) {
        return streaks.current(playerId);
    }

    /** DEV: set a streak outright (heat testing). */
    public void setStreak(UUID playerId, int count) {
        streaks.clear(playerId);
        for (int i = 0; i < Math.max(0, count); i++) {
            streaks.bump(playerId);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        abort(event.getPlayer(), false);
    }

    void tick() {
        Iterator<Map.Entry<UUID, CastSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastSession> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            CastSession session = entry.getValue();
            if (player == null || !player.isOnline() || session.resolved) {
                session.school.clear();
                hud.hide(entry.getKey());
                it.remove();
                continue;
            }
            FishHook hook = resolveHook(session.hookId);
            if (hook == null || !hook.isValid()) {
                session.school.clear();
                it.remove();
                hud.hide(player);
                continue;
            }
            session.ticks++;
            if (session.phase == CastSession.Phase.WAIT && session.waitingForWater) {
                if (inWater(hook)) {
                    session.waitingForWater = false;
                    session.ticks = 0;
                    settle(player, session, hook);
                    suppressVanillaWait(hook, session.waitLeft);
                } else if (session.ticks >= WATER_SETTLE_TICKS) {
                    session.school.clear();
                    it.remove();
                    hud.hide(player);
                    player.sendActionBar(Component.text("Cast into water.", NamedTextColor.GRAY));
                    if (hook.isValid()) {
                        hook.remove();
                    }
                } else {
                    // Freeze vanilla bite while the bobber is still in the air / on ground.
                    suppressVanillaWait(hook, Math.max(1, session.waitLeft));
                }
                continue;
            }
            if (!inWater(hook)) {
                session.school.clear();
                it.remove();
                hud.hide(player);
                if (hook.isValid()) {
                    hook.remove();
                }
                continue;
            }
            if (session.phase == CastSession.Phase.WAIT && session.ticks == 8) {
                spawnSchool(session, player, hook);
            }
            if (session.phase == CastSession.Phase.WAIT) {
                session.school.tick(hook.getLocation(), LureSchool.Swim.WANDER);
                if (session.waitLeft > 0) {
                    session.waitLeft--;
                }
                suppressVanillaWait(hook, session.waitLeft);
                int remaining = Math.max(0, Math.min(HARD_WAIT_TICKS, session.waitLeft));
                double progress = session.waitTotal <= 0 ? 0.0d : 1.0d - (remaining / (double) session.waitTotal);
                int streakNow = streaks.current(entry.getKey());
                CastHooks place = hooks;
                if (place != null && session.ticks % 10 == 1) {
                    session.hudTag = place.waitTag(player, session, hook.getLocation(), streakNow);
                }
                hud.waiting(player, progress, session.waitTotal <= 0 ? -1 : remaining, streakNow, session.hudTag);
                if (session.ticks % 12 == 0) {
                    bubbles(hook.getLocation(), 6);
                }
                if (session.waitLeft <= 0 && session.ticks > 12) {
                    startApproach(player, hook);
                }
                continue;
            }
            holdBite(hook);
            if (session.phase == CastSession.Phase.APPROACH) {
                // Spawn once per cast. Empty school must not respawn every tick.
                spawnSchool(session, player, hook);
                session.school.chooseBiter(hook.getLocation());
                session.approachTicks++;
                boolean arrived = session.school.tick(hook.getLocation(), LureSchool.Swim.APPROACH);
                // Telegraph: the bar fills as the biter closes in, then reads "ready…" with a tick.
                double distance = session.school.biterDistance(hook.getLocation());
                double closeness = distance < 0.0d
                        ? session.approachTicks / (double) Math.max(1, session.approachLimit)
                        : 1.0d - Math.min(1.0d, distance / 4.5d);
                boolean ready = (distance >= 0.0d && distance < 1.3d)
                        || session.approachTicks >= session.approachLimit - 8;
                if (ready && !session.readyCued) {
                    session.readyCued = true;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                }
                if (session.approachTicks % 4 == 0) {
                    wake(session.school.biterLocation(hook.getLocation()));
                }
                hud.approaching(player, session.approachTicks, closeness, ready, streaks.current(entry.getKey()));
                if (arrived || session.approachTicks >= session.approachLimit || session.school.isEmpty()) {
                    startStrike(player, session, hook);
                }
                continue;
            }
            session.school.tick(hook.getLocation(), LureSchool.Swim.NIBBLE);
            if (session.markerDue()) {
                session.pulseMarker();
            }
            session.strikeTicks++;
            boolean hot = session.inZone();
            if (hot && !session.enteredZone) {
                session.enteredZone = true;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.85f);
            }
            if (!hot) {
                session.enteredZone = false;
            }
            hud.striking(player, session.marker, session.zoneStart, session.zoneSize, session.perfectWidth(), hot,
                    streaks.current(entry.getKey()));
            spark(hook.getLocation(), hot);
            if (session.strikeTicks >= session.strikeLimit) {
                miss(player, session, hook, true);
            }
        }
    }

    void shutdown() {
        hud.hideAll();
        streaks.clearAll();
        for (CastSession session : sessions.values()) {
            session.school.clear();
        }
        sessions.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(lureKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void startCast(Player player, FishHook hook) {
        abort(player, false);
        if (hook == null) {
            return;
        }
        CastSession session = new CastSession(hook.getUniqueId(), new LureSchool(lureKey));
        session.waitTotal = stats.waitTicks(player);
        session.waitLeft = session.waitTotal;
        sessions.put(player.getUniqueId(), session);
        suppressVanillaWait(hook, session.waitLeft);
    }

    /** Bobber is in the water: let the place scale the owned wait (shoals, runs, bait). */
    private void settle(Player player, CastSession session, FishHook hook) {
        CastHooks place = hooks;
        if (place == null || hook == null) {
            return;
        }
        double factor = 1.0d;
        try {
            factor = place.settle(player, session, hook.getLocation());
            session.hudTag = place.waitTag(player, session, hook.getLocation(), streaks.current(player.getUniqueId()));
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Cast hook (settle) failed: " + error);
        }
        if (!(factor > 0.0d) || Math.abs(factor - 1.0d) < 0.001d) {
            return;
        }
        int scaled = (int) Math.round(session.waitTotal * Math.min(2.0d, factor));
        session.waitTotal = Math.max(16, Math.min(HARD_WAIT_TICKS, scaled));
        session.waitLeft = session.waitTotal;
    }

    private void startApproach(Player player, FishHook hook) {
        if (!inWater(hook)) {
            return;
        }
        CastSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            startCast(player, hook);
            session = sessions.get(player.getUniqueId());
        }
        if (session == null) {
            return;
        }
        if (session.waitingForWater) {
            if (!inWater(hook)) {
                return;
            }
            session.waitingForWater = false;
        }
        if (session.phase != CastSession.Phase.WAIT) {
            return;
        }
        spawnSchool(session, player, hook);
        session.school.chooseBiter(hook != null ? hook.getLocation() : null);
        session.beginApproach(approachTicks);
        suppressVanillaWait(hook, 0);
        holdBite(hook);
        player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 0.7f, 1.2f);
        if (hook != null) {
            bubbles(hook.getLocation(), 10);
        }
    }

    private void startStrike(Player player, CastSession session, FishHook hook) {
        if (session.phase == CastSession.Phase.STRIKE) {
            return;
        }
        double speed = stats.speed(player);
        double catchStat = stats.catchBonus(player);
        int limit = Math.min(110, baseStrikeTicks + (int) Math.round(speed * 0.28d));
        int streak = streaks.current(player.getUniqueId());
        session.beginStrike(catchStat, limit, streak);
        CastHooks place = hooks;
        CastHooks.BiteCue cue = null;
        if (place != null && hook != null) {
            try {
                cue = place.bite(player, session, hook.getLocation(), streak);
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Cast hook (bite) failed: " + error);
            }
        }
        if (cue == null) {
            player.showTitle(Title.title(
                    Component.text("Bite!", NamedTextColor.AQUA),
                    LEGACY.deserialize("§7Reel on green §8· §6gold is perfect"),
                    BITE_TIMES
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.6f);
        } else {
            player.showTitle(Title.title(LEGACY.deserialize(cue.title()), LEGACY.deserialize(cue.subtitle()), BITE_TIMES));
            if (cue.sound() != null) {
                player.playSound(player.getLocation(), cue.sound(), 0.6f, cue.pitch());
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.7f, 1.25f);
        if (hook != null) {
            splash(hook.getLocation());
        }
    }

    private void land(Player player, CastSession session, FishHook hook) {
        session.resolved = true;
        sessions.remove(player.getUniqueId());
        hud.hideBarOnly(player);
        boolean perfect = session.onPerfect();
        int streak = streaks.bump(player.getUniqueId());
        Location at = hook != null ? hook.getLocation() : player.getLocation();
        plugin.getServer().getScheduler().runTaskLater(plugin, session.school::clear, 50L);
        player.sendActionBar(LEGACY.deserialize(resultWord(perfect) + StrikeBar.streakTag(streak)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 1.35f);
        if (perfect) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.35f);
        }
        if (hook != null) {
            splash(at);
            hook.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.2, 0), 6, 0.2, 0.15, 0.2, 0.01);
            if (perfect) {
                hook.getWorld().spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.35, 0), 8, 0.25, 0.2, 0.25, 0.0);
            }
        }
        announceStreak(player, streak);
        UUID id = player.getUniqueId();
        Location hookAt = at.clone();
        // Items pays the catch + base XP at MONITOR this tick — land the reward line after it.
        plugin.getServer().getScheduler().runTask(plugin, () -> rewardBeat(id, session, hookAt, perfect, streak));
    }

    /** Reward beat: bonus XP, the place's catch line, then the focus Fishing skill's bar. */
    private void rewardBeat(UUID id, CastSession session, Location hookAt, boolean perfect, int streak) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        int bonus = (perfect ? PERFECT_BONUS_XP : 0) + Math.min(STREAK_BONUS_CAP, streak / 2);
        FishingSkills.bonus(player, bonus);
        String placeLine = null;
        CastHooks place = hooks;
        if (place != null) {
            try {
                placeLine = place.landed(player, session, hookAt, perfect, streak);
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Cast hook (landed) failed: " + error);
            }
        }
        String credit = FishingSkills.credit(player);
        StringBuilder line = new StringBuilder(resultWord(perfect)).append(StrikeBar.streakTag(streak));
        if (placeLine != null && !placeLine.isBlank()) {
            line.append("  §8│  ").append(placeLine);
        }
        if (credit != null) {
            line.append("  §8│  ").append(credit);
        }
        player.sendActionBar(LEGACY.deserialize(line.toString()));
        if (credit == null && tipped.add(id)) {
            player.sendMessage("§8Tip: equip a Fishing skill in §7/skills fishing §8— it levels on every catch,"
                    + " and perfect reels pay extra.");
        }
    }

    private static String resultWord(boolean perfect) {
        return perfect ? "§6Perfect reel." : "§bOn the line.";
    }

    private static void announceStreak(Player player, int streak) {
        if (streak == StrikeBar.HOT_STREAK) {
            player.sendMessage("§6✦ Hot water. §7Five clean in a row — the window's a cell wider and the fish noticed.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.5f);
        } else if (streak > StrikeBar.HOT_STREAK && streak % 10 == 0) {
            player.sendMessage("§6✦ " + streak + " clean catches. §7The dock is taking bets on you.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.7f);
        }
    }

    private void miss(Player player, CastSession session, FishHook hook, boolean timeout) {
        session.resolved = true;
        sessions.remove(player.getUniqueId());
        hud.hideBarOnly(player);
        Location at = hook != null ? hook.getLocation() : player.getLocation();
        boolean forgiven = false;
        CastHooks place = hooks;
        if (place != null) {
            try {
                forgiven = place.missed(player, session, at, timeout, streaks.current(player.getUniqueId()));
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Cast hook (missed) failed: " + error);
            }
        }
        int lost = forgiven ? 0 : streaks.reset(player.getUniqueId());
        session.school.scatter(at);
        plugin.getServer().getScheduler().runTaskLater(plugin, session.school::clear, 16L);
        String line = timeout ? "It let go." : tooSoonOrLate(session);
        int kept = forgiven ? streaks.current(player.getUniqueId()) : 0;
        String lostTag = kept >= 2 ? " §8· §7streak §e✦" + kept + " §akept"
                : lost >= 2 ? " §8· §7streak §e✦" + lost + " §7lost" : "";
        player.sendActionBar(LEGACY.deserialize("§7" + line + lostTag));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, timeout ? 0.6f : 0.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 0.8f, 1.1f);
        if (hook != null && hook.isValid()) {
            bubbles(hook.getLocation(), 14);
            hook.remove();
        }
    }

    private void abort(Player player, boolean message) {
        CastSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            // Nothing live — drop bar/lease only, keep the last catch's reward line readable.
            hud.hide(player.getUniqueId());
            return;
        }
        hud.hide(player);
        session.school.clear();
        if (message) {
            player.sendActionBar(Component.empty());
        }
    }

    private void spawnSchool(CastSession session, Player player, FishHook hook) {
        if (session == null || hook == null || session.schoolSpawned || !session.school.isEmpty()) {
            return;
        }
        session.schoolSpawned = true;
        int count = streaks.current(player.getUniqueId()) >= StrikeBar.HOT_STREAK ? 2 : 1;
        CastHooks place = hooks;
        if (place != null) {
            count += Math.max(0, place.extraLures(player, session, hook.getLocation()));
        }
        double catchStat = stats.catchBonus(player);
        if (catchStat >= 20.0d) {
            count++;
        }
        if (catchStat >= 70.0d) {
            count++;
        }
        session.school.spawn(hook.getLocation(), count);
    }

    private static void holdBite(FishHook hook) {
        if (hook == null || !hook.isValid()) {
            return;
        }
        Vector vel = hook.getVelocity();
        if (vel.lengthSquared() > 0.0004d) {
            hook.setVelocity(new Vector(0, 0, 0));
        }
        try {
            hook.setMinLureTime(40);
            hook.setMaxLureTime(80);
        } catch (NoSuchMethodError ignored) {
        }
        keepNibble(hook);
    }

    private static void keepNibble(FishHook hook) {
        try {
            Object handle = hook.getClass().getMethod("getHandle").invoke(hook);
            resolveNibbleFields(handle.getClass());
            if (nibbleField != null) {
                int nibble = nibbleField.getInt(handle);
                if (nibble < 20) {
                    nibbleField.setInt(handle, 40);
                }
            }
            if (bitingField != null && !bitingField.getBoolean(handle)) {
                bitingField.setBoolean(handle, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveNibbleFields(Class<?> type) {
        if (nibbleLookupDone) {
            return;
        }
        Class<?> cursor = type;
        while (cursor != null && (nibbleField == null || bitingField == null)) {
            for (Field field : cursor.getDeclaredFields()) {
                String name = field.getName();
                if (nibbleField == null && field.getType() == int.class
                        && ("nibble".equals(name) || "hookCountdown".equals(name))) {
                    field.setAccessible(true);
                    nibbleField = field;
                } else if (bitingField == null && field.getType() == boolean.class
                        && ("biting".equals(name) || "caughtFish".equals(name))) {
                    field.setAccessible(true);
                    bitingField = field;
                }
            }
            cursor = cursor.getSuperclass();
        }
        nibbleLookupDone = true;
    }

    private static String tooSoonOrLate(CastSession session) {
        if (session.phase != CastSession.Phase.STRIKE) {
            return ThreadLocalRandom.current().nextBoolean() ? "Too early." : "Not yet.";
        }
        if (session.marker < session.zoneStart) {
            return ThreadLocalRandom.current().nextBoolean() ? "Too early." : "It spat the hook.";
        }
        return ThreadLocalRandom.current().nextBoolean() ? "Too late." : "Gone.";
    }

    private static void suppressVanillaWait(FishHook hook, int ownedLeft) {
        if (hook == null || !hook.isValid()) {
            return;
        }
        hook.setSkyInfluenced(false);
        hook.setRainInfluenced(false);
        hook.setApplyLure(false);
        int cap = Math.max(1, HARD_WAIT_TICKS);
        hook.setMinWaitTime(1);
        hook.setMaxWaitTime(cap);
        try {
            hook.setWaitTime(1, cap);
        } catch (NoSuchMethodError ignored) {
        }
        int wait = Math.max(0, hook.getWaitTime());
        int target;
        if (ownedLeft > 0) {
            target = Math.max(1, Math.min(cap, ownedLeft));
        } else if (wait > 1) {
            // Do not park on 0: vanilla re-rolls 100-600 when wait is 0 before lure starts.
            target = 1;
        } else {
            target = wait;
        }
        if (wait != target && target > 0) {
            hook.setWaitTime(target);
        }
        try {
            int bite = hook.getTimeUntilBite();
            if (bite > 30) {
                hook.setTimeUntilBite(24);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static int remainingWait(FishHook hook) {
        if (hook == null) {
            return 0;
        }
        int wait = Math.max(0, hook.getWaitTime());
        if (wait > 0) {
            return wait;
        }
        try {
            return Math.max(0, hook.getTimeUntilBite());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static FishHook resolveHook(UUID id) {
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof FishHook hook ? hook : null;
    }

    private static boolean inWater(FishHook hook) {
        if (hook == null || !hook.isValid()) {
            return false;
        }
        try {
            if (hook.isInOpenWater() || hook.isInWater()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
        }
        Location loc = hook.getLocation();
        if (loc.getWorld() == null) {
            return false;
        }
        Material type = loc.getBlock().getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
            return true;
        }
        Material below = loc.clone().add(0, -0.2, 0).getBlock().getType();
        return below == Material.WATER || below == Material.BUBBLE_COLUMN;
    }

    private static void bubbles(Location at, int count) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(Particle.BUBBLE, at.clone().add(0, 0.1, 0), count, 0.18, 0.08, 0.18, 0.02);
        at.getWorld().spawnParticle(Particle.BUBBLE_POP, at, Math.max(2, count / 3), 0.12, 0.05, 0.12, 0.01);
    }

    /** Small wake behind the biter while it closes in. */
    private static void wake(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(Particle.BUBBLE, at, 2, 0.06, 0.04, 0.06, 0.01);
    }

    private static void splash(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0, 0.15, 0), 22, 0.22, 0.1, 0.22, 0.08);
        at.getWorld().spawnParticle(Particle.BUBBLE, at, 12, 0.16, 0.1, 0.16, 0.03);
    }

    private static void spark(Location at, boolean hot) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        if (hot) {
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.2, 0), 2, 0.08, 0.06, 0.08, 0.0);
        } else if (ThreadLocalRandom.current().nextInt(4) == 0) {
            at.getWorld().spawnParticle(Particle.SPLASH, at, 2, 0.1, 0.04, 0.1, 0.01);
        }
    }
}
