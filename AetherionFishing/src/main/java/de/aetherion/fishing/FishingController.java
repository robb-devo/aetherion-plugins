package de.aetherion.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingController implements Listener {

    private static final Title.Times BITE_TIMES = Title.Times.times(
            Duration.ofMillis(40),
            Duration.ofMillis(650),
            Duration.ofMillis(120)
    );

    private static final int HARD_WAIT_TICKS = 20 * 14;
    /** Bobber must hit water within this window or the cast is discarded. */
    private static final int WATER_SETTLE_TICKS = 36;
    private final AetherionFishing plugin;
    private final FishingStats stats;
    private final FishingHud hud = new FishingHud();
    private final NamespacedKey lureKey;
    private final Map<UUID, CastSession> sessions = new ConcurrentHashMap<>();
    private final int baseStrikeTicks;
    private final int approachTicks;
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
                hud.waiting(player, progress, session.waitTotal <= 0 ? -1 : remaining);
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
                hud.approaching(player, session.approachTicks);
                if (arrived || session.approachTicks >= session.approachLimit || session.school.isEmpty()) {
                    startStrike(player, session, hook);
                }
                continue;
            }
            session.school.tick(hook.getLocation(), LureSchool.Swim.NIBBLE);
            if (session.strikeTicks % 2 == 0) {
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
            hud.striking(player, session.marker, session.zoneStart, session.zoneSize, hot);
            spark(hook.getLocation(), hot);
            if (session.strikeTicks >= session.strikeLimit) {
                miss(player, session, hook, true);
            }
        }
    }

    void shutdown() {
        hud.hideAll();
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
        session.beginStrike(catchStat, limit);
        player.showTitle(Title.title(
                Component.text("Bite!", NamedTextColor.AQUA),
                Component.text("Reel on green", NamedTextColor.GRAY),
                BITE_TIMES
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.7f, 1.25f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.6f);
        if (hook != null) {
            splash(hook.getLocation());
        }
    }

    private void land(Player player, CastSession session, FishHook hook) {
        session.resolved = true;
        sessions.remove(player.getUniqueId());
        hud.hide(player);
        Location at = hook != null ? hook.getLocation() : player.getLocation();
        plugin.getServer().getScheduler().runTaskLater(plugin, session.school::clear, 50L);
        player.sendActionBar(Component.text("On the line.", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 1.35f);
        if (hook != null) {
            splash(at);
            hook.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.2, 0), 6, 0.2, 0.15, 0.2, 0.01);
        }
    }

    private void miss(Player player, CastSession session, FishHook hook, boolean timeout) {
        session.resolved = true;
        sessions.remove(player.getUniqueId());
        hud.hide(player);
        Location at = hook != null ? hook.getLocation() : player.getLocation();
        session.school.scatter(at);
        plugin.getServer().getScheduler().runTaskLater(plugin, session.school::clear, 16L);
        String line = timeout ? "It let go." : tooSoonOrLate(session);
        player.sendActionBar(Component.text(line, NamedTextColor.GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, timeout ? 0.6f : 0.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 0.8f, 1.1f);
        if (hook != null && hook.isValid()) {
            bubbles(hook.getLocation(), 14);
            hook.remove();
        }
    }

    private void abort(Player player, boolean message) {
        CastSession session = sessions.remove(player.getUniqueId());
        hud.hide(player);
        if (session == null) {
            return;
        }
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
        int count = 1;
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
