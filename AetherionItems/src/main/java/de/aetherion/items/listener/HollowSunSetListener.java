package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.combat.DamageNumbers;
import de.aetherion.items.combat.ScriptedHits;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Remnant of the Hollow Sun. Each piece carries one stage of the star's life:
 * Solar Sight (visor), Caged Star (cuirass), Swell (greaves), Starfall (sabatons).
 * The set climbs the same story: Main Sequence (2), Red Giant (3), Collapse (4).
 * Tells use the boss's language: gold / flare / violet marks where something lands, white means it fires now.
 */
public final class HollowSunSetListener implements Listener, Runnable, StatProvider {

    private static final String HELMET = "hollow_sun_helmet";
    private static final String CHEST = "hollow_sun_chestplate";
    private static final String LEGS = "hollow_sun_leggings";
    private static final String BOOTS = "hollow_sun_boots";

    private static final Color GOLD = Color.fromRGB(255, 196, 64);
    private static final Color SOLAR = Color.fromRGB(255, 236, 160);
    private static final Color HOT = Color.fromRGB(255, 255, 255);
    private static final Color FLARE = Color.fromRGB(255, 64, 24);
    private static final Color CRIMSON = Color.fromRGB(205, 18, 40);
    private static final Color VIOLET = Color.fromRGB(150, 70, 255);
    private static final Color VOID = Color.fromRGB(70, 22, 120);
    private static final Color PHOTON = Color.fromRGB(236, 226, 255);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final int SUNMARK_TICKS = 80;
    private static final double SUNMARK_BONUS = 1.06;

    private static final double HEAT_MAX = 100.0;
    private static final double HEAT_PER_HIT = 12.0;
    private static final double HEAT_PER_CRIT = 6.0;
    private static final int HEAT_IDLE_TICKS = 60;
    private static final double HEAT_DECAY_PER_TICK = 0.5;
    private static final double CORONA_RADIUS = 4.0;
    private static final double CORONA_SHARE = 0.60;

    private static final double SWELL_BELOW = 0.50;
    private static final double SWELL_DAMAGE = 1.10;
    private static final double SWELL_DEFENSE = 1.08;
    private static final double SWELL_SCALE = 0.06;
    private static final int FLARE_PULSES = 3;
    private static final int FLARE_PERIOD = 10;
    private static final double FLARE_SHARE = 0.03;

    private static final long STARFALL_CD_MS = 18_000L;
    private static final long DOUBLE_TAP_MS = 350L;
    private static final double STARFALL_MIN_HEIGHT = 3.0;
    private static final double STARFALL_SPEED = 2.4;
    private static final double STARFALL_RADIUS = 4.0;
    private static final double STARFALL_WEAPON = 1.5;
    private static final int STARFALL_TELL_TICKS = 6;

    private static final int MAIN_SEQUENCE_HITS = 5;
    private static final double STARCALL_SHARE = 0.35;

    private static final int RED_GIANT_TICKS = 160;
    private static final long RED_GIANT_CD_MS = 45_000L;
    private static final double RED_GIANT_BIG_HIT = 0.20;
    private static final double RED_GIANT_LOW = 0.35;
    private static final double RED_GIANT_DAMAGE = 1.15;
    private static final double RED_GIANT_BEAM = 3.5;
    private static final double RED_GIANT_BURN = 0.15;

    private static final long COLLAPSE_CD_MS = 12L * 60L * 1000L;
    private static final int COLLAPSE_TICKS = 30;
    private static final int REFORM_TICKS = 14;
    private static final double COLLAPSE_RADIUS = 6.0;
    private static final double COLLAPSE_RETURN = 0.35;
    private static final double NOVA_WEAPON = 3.0;
    private static final double NOVA_BOSS_WEAPON = 1.5;

    private static final int MAX_LIVE = 48;
    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static final NamespacedKey SWELL_KEY = ItemKeys.key("hollow_sun_swell");

    private static HollowSunSetListener active;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats stats;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> sunmarks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Flare>> flares = new ConcurrentHashMap<>();
    private int clock;

    public HollowSunSetListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.stats = new ActiveEquipmentStats(itemManager);
        active = this;
        ActiveEquipmentStats.registerProvider(this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 2L, 2L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearSwellScale(player);
        }
    }

    /** Removes every set display and the Swell scale modifier (plugin disable). */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
        HollowSunSetListener listener = active;
        if (listener != null) {
            ActiveEquipmentStats.unregisterProvider(listener);
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearSwellScale(player);
            }
            listener.states.clear();
            listener.sunmarks.clear();
            listener.flares.clear();
        }
        active = null;
    }

    // ------------------------------------------------------------------ stats

    @Override
    public double getStat(Player player, ItemCapability capability) {
        return 0.0;
    }

    @Override
    public double getMultiplier(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 1.0;
        }
        State state = states.get(player.getUniqueId());
        if (state == null) {
            return 1.0;
        }
        if (capability == ItemCapability.DAMAGE) {
            double mul = state.swollen ? SWELL_DAMAGE : 1.0;
            if (state.redGiantLeft > 0) {
                mul *= RED_GIANT_DAMAGE;
            }
            return mul;
        }
        if (capability == ItemCapability.DEFENSE) {
            return state.swollen ? SWELL_DEFENSE : 1.0;
        }
        return 1.0;
    }

    // ------------------------------------------------------------------ outgoing hits

    /** Runs after DamageListener (same priority, registered later) so the Aetherion hit value is already set. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOutgoing(EntityDamageByEntityEvent event) {
        if (ScriptedHits.isActive()) {
            return;
        }
        Player player = attackerOf(event.getDamager());
        if (player == null || !(event.getEntity() instanceof LivingEntity victim) || !isTarget(victim)) {
            return;
        }
        Pieces pieces = pieces(player);
        if (pieces.count == 0) {
            return;
        }
        State state = state(player);
        UUID playerId = player.getUniqueId();
        if (isMarked(playerId, victim)) {
            event.setDamage(event.getDamage() * SUNMARK_BONUS);
        }
        if (DamageListener.isSpreadHit(playerId)) {
            return;
        }
        boolean melee = event.getDamager() instanceof Player
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        if (event.getDamager() instanceof Player && !melee) {
            return;
        }
        boolean crit = DamageNumbers.critThisTick(player) || DamageNumbers.isCrit(event.getDamager());
        double hit = Math.max(0.0, event.getDamage());
        int now = Bukkit.getCurrentTick();

        if (pieces.helmet && crit) {
            sunmark(player, victim);
        }
        if (pieces.legs && state.swollen && melee) {
            flare(player, victim, hit);
        }
        if (pieces.chest) {
            if (state.primed) {
                state.primed = false;
                state.heat = 0.0;
                corona(player, victim, hit);
            } else {
                state.heat = Math.min(HEAT_MAX, state.heat + HEAT_PER_HIT + (crit ? HEAT_PER_CRIT : 0.0));
                if (state.heat >= HEAT_MAX) {
                    prime(player, state);
                }
            }
            state.lastHitTick = now;
            heatBar(player, state);
        }
        if (pieces.count >= 2 && melee) {
            if (victim.getUniqueId().equals(state.sequenceTarget)) {
                state.sequenceCount++;
            } else {
                state.sequenceTarget = victim.getUniqueId();
                state.sequenceCount = 1;
            }
            if (state.sequenceCount >= MAIN_SEQUENCE_HITS) {
                state.sequenceCount = 0;
                starcall(player, victim, hit);
            }
        }
    }

    // ------------------------------------------------------------------ incoming hits

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGuarded(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        State state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.collapsing) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && Bukkit.getCurrentTick() <= state.fallGuardUntil) {
            event.setCancelled(true);
        }
    }

    /** Collapse: runs after Defense (DamageListener) so the final damage is the real one. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || isUnsavable(event.getCause())) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0.05) {
            return;
        }
        if (pieces(player).count < 4) {
            return;
        }
        Byte blocked = player.getPersistentDataContainer().get(AetherKeys.NO_SET_SAVE, PersistentDataType.BYTE);
        if (blocked != null && blocked == (byte) 1) {
            return;
        }
        State state = state(player);
        long nowMs = System.currentTimeMillis();
        if (state.collapsing || nowMs < state.collapseReadyAt) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
        state.collapseReadyAt = nowMs + COLLAPSE_CD_MS;
        new Collapse(player, state).runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || pieces(player).count < 3) {
            return;
        }
        State state = state(player);
        if (state.collapsing || state.redGiantLeft > 0 || System.currentTimeMillis() < state.redGiantReadyAt) {
            return;
        }
        double max = maxHealth(player);
        double taken = event.getFinalDamage();
        double after = player.getHealth() - taken;
        if (after <= 0.05) {
            return;
        }
        if (taken > max * RED_GIANT_BIG_HIT || after <= max * RED_GIANT_LOW) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !player.isDead() && pieces(player).count >= 3 && state.redGiantLeft <= 0) {
                    redGiant(player, state);
                }
            });
        }
    }

    // ------------------------------------------------------------------ starfall input

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!pieces(player).boots) {
            return;
        }
        State state = state(player);
        long nowMs = System.currentTimeMillis();
        long last = state.lastSneakMs;
        state.lastSneakMs = nowMs;
        if (nowMs - last > DOUBLE_TAP_MS || state.plunging) {
            return;
        }
        state.lastSneakMs = 0L;
        if (player.isOnGround() || player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double height = heightAboveGround(player.getLocation());
        if (height < STARFALL_MIN_HEIGHT) {
            return;
        }
        if (nowMs < state.starfallReadyAt) {
            long left = (state.starfallReadyAt - nowMs + 999L) / 1000L;
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5☀ Starfall §7— §f" + left + "s"));
            return;
        }
        state.starfallReadyAt = nowMs + STARFALL_CD_MS;
        new Plunge(player, state).runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearSwellScale(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        clearSwellScale(player);
        sunmarks.remove(id);
        flares.remove(id);
        State state = states.get(id);
        if (state != null && !state.collapsing) {
            states.remove(id);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void run() {
        clock += 2;
        int now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(player, now);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Hollow Sun tick failed for " + player.getName() + ": " + ex.getMessage());
            }
        }
        tickSunmarks(now);
        tickFlares(now);
    }

    private void tickPlayer(Player player, int now) {
        State state = states.get(player.getUniqueId());
        Pieces pieces = pieces(player);
        if (pieces.count == 0 || player.isDead()) {
            if (state != null && !state.collapsing && !state.plunging) {
                if (state.swollen) {
                    setSwollen(player, state, false);
                }
                states.remove(player.getUniqueId());
            }
            return;
        }
        if (state == null) {
            state = state(player);
        }
        if (!pieces.chest) {
            state.heat = 0.0;
            state.primed = false;
        } else if (!state.primed && state.heat > 0.0 && now - state.lastHitTick > HEAT_IDLE_TICKS) {
            double before = state.heat;
            state.heat = Math.max(0.0, state.heat - HEAT_DECAY_PER_TICK * 2.0);
            if (before > 50.0 && clock % 20 == 0) {
                heatBar(player, state);
            }
        }
        if (state.primed && clock % 6 == 0) {
            Location chest = player.getLocation().add(0, 1.25, 0);
            player.getWorld().spawnParticle(Particle.DUST, chest, 2, 0.16, 0.18, 0.16, 0, new Particle.DustOptions(HOT, 1.0f));
            player.getWorld().spawnParticle(Particle.END_ROD, chest, 1, 0.12, 0.15, 0.12, 0.0);
        }

        if (state.redGiantLeft > 0) {
            if (pieces.count < 3) {
                state.redGiantLeft = 0;
            } else {
                state.redGiantLeft -= 2;
                tickRedGiant(player, state);
                if (state.redGiantLeft <= 0) {
                    endRedGiant(player);
                }
            }
        }

        double max = maxHealth(player);
        boolean wantSwell = pieces.legs
                && (player.getHealth() < max * SWELL_BELOW || state.redGiantLeft > 0);
        if (wantSwell != state.swollen) {
            setSwollen(player, state, wantSwell);
        } else if (state.swollen && clock % 10 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.15, 0), 2, 0.3, 0.04, 0.3, 0,
                    new Particle.DustOptions(FLARE, 0.9f));
        }

        if (pieces.count >= 4 && !state.collapsing && clock % 50 == 0) {
            idleMotes(player);
        }
    }

    // ------------------------------------------------------------------ Solar Sight

    private void sunmark(Player player, LivingEntity victim) {
        Map<UUID, Integer> marks = sunmarks.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        Integer before = marks.put(victim.getUniqueId(), Bukkit.getCurrentTick() + SUNMARK_TICKS);
        if (before == null || before < Bukkit.getCurrentTick()) {
            Location feet = victim.getLocation();
            victim.getWorld().playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.85f);
            ring(feet.clone().add(0, 0.08, 0), ringRadius(victim) + 0.25, GOLD, 20, 1.1f);
            victim.getWorld().spawnParticle(Particle.END_ROD, feet.clone().add(0, victim.getHeight() + 0.25, 0), 4, 0.1, 0.05, 0.1, 0.01);
        }
    }

    private boolean isMarked(UUID playerId, LivingEntity victim) {
        Map<UUID, Integer> marks = sunmarks.get(playerId);
        if (marks == null) {
            return false;
        }
        Integer until = marks.get(victim.getUniqueId());
        return until != null && until >= Bukkit.getCurrentTick();
    }

    private void tickSunmarks(int now) {
        Iterator<Map.Entry<UUID, Map<UUID, Integer>>> owners = sunmarks.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, Map<UUID, Integer>> owner = owners.next();
            Iterator<Map.Entry<UUID, Integer>> marks = owner.getValue().entrySet().iterator();
            while (marks.hasNext()) {
                Map.Entry<UUID, Integer> mark = marks.next();
                Entity found = Bukkit.getEntity(mark.getKey());
                if (mark.getValue() < now || !(found instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                    marks.remove();
                    continue;
                }
                if (clock % 4 == 0) {
                    int left = mark.getValue() - now;
                    float size = left < 20 ? 0.75f : 0.95f;
                    ring(living.getLocation().add(0, 0.06, 0), ringRadius(living), GOLD, 14, size);
                }
            }
            if (owner.getValue().isEmpty()) {
                owners.remove();
            }
        }
    }

    private static double ringRadius(LivingEntity living) {
        return Math.max(0.55, living.getWidth() * 0.75);
    }

    // ------------------------------------------------------------------ Caged Star

    private void prime(Player player, State state) {
        state.primed = true;
        World world = player.getWorld();
        Location chest = player.getLocation().add(0, 1.25, 0);
        world.playSound(chest, Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.7f);
        world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, chest, 14, 0.22, 0.25, 0.22, 0, new Particle.DustOptions(SOLAR, 1.2f));
    }

    private void corona(Player player, LivingEntity victim, double hit) {
        double amount = Math.max(1.0, hit * CORONA_SHARE);
        Location center = victim.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.25f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1.0, 0), 26, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(HOT, 1.4f));
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double r = CORONA_RADIUS * Math.min(1.0, t / 4.0);
                ring(center.clone().add(0, 0.25, 0), r, t < 3 ? HOT : GOLD, 28, 1.4f);
                ring(center.clone().add(0, 0.75, 0), r * 0.85, SOLAR, 18, 1.0f);
                if (t == 1) {
                    List<LivingEntity> caught = targetsNear(center, CORONA_RADIUS);
                    for (LivingEntity target : caught) {
                        strike(target, player, amount);
                        world.spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.5, 0), 6, 0.2, 0.3, 0.2, 0.02);
                    }
                }
                if (t >= 5) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§6☀ §e§lCORONA"));
    }

    private void heatBar(Player player, State state) {
        if (state.primed) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§f☀ §e§lCORONA READY §7— §fnext hit bursts"));
            return;
        }
        if (state.heat > 50.0) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§6☀ Heat §f" + (int) Math.round(state.heat) + "%"));
        }
    }

    // ------------------------------------------------------------------ Swell

    private void setSwollen(Player player, State state, boolean on) {
        state.swollen = on;
        World world = player.getWorld();
        Location mid = player.getLocation().add(0, 1.0, 0);
        if (on) {
            world.playSound(mid, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 0.65f);
            world.playSound(mid, Sound.ENTITY_BLAZE_SHOOT, 0.45f, 0.5f);
            world.spawnParticle(Particle.DUST, mid, 30, 0.45, 0.6, 0.45, 0, new Particle.DustOptions(FLARE, 1.4f));
            world.spawnParticle(Particle.FLAME, mid, 12, 0.35, 0.5, 0.35, 0.03);
            applySwellScale(player);
        } else {
            world.spawnParticle(Particle.SMOKE, mid, 10, 0.3, 0.45, 0.3, 0.01);
            clearSwellScale(player);
        }
    }

    private void flare(Player player, LivingEntity victim, double hit) {
        Map<UUID, Flare> byTarget = flares.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        double perPulse = Math.max(1.0, hit * FLARE_SHARE);
        byTarget.put(victim.getUniqueId(), new Flare(perPulse, Bukkit.getCurrentTick() + FLARE_PERIOD));
        victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, victim.getHeight() * 0.5, 0), 8,
                0.25, 0.35, 0.25, 0, new Particle.DustOptions(FLARE, 1.1f));
    }

    private void tickFlares(int now) {
        Iterator<Map.Entry<UUID, Map<UUID, Flare>>> owners = flares.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, Map<UUID, Flare>> owner = owners.next();
            Player player = Bukkit.getPlayer(owner.getKey());
            Iterator<Map.Entry<UUID, Flare>> burns = owner.getValue().entrySet().iterator();
            while (burns.hasNext()) {
                Map.Entry<UUID, Flare> burn = burns.next();
                Entity found = Bukkit.getEntity(burn.getKey());
                if (player == null || !(found instanceof LivingEntity target) || !target.isValid() || target.isDead()) {
                    burns.remove();
                    continue;
                }
                Flare flare = burn.getValue();
                if (now < flare.nextTick) {
                    continue;
                }
                strike(target, player, flare.perPulse);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.6, 0), 5, 0.2, 0.3, 0.2, 0.01);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() * 0.6, 0), 4, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(FLARE, 1.0f));
                flare.pulses++;
                flare.nextTick = now + FLARE_PERIOD;
                if (flare.pulses >= FLARE_PULSES) {
                    burns.remove();
                }
            }
            if (owner.getValue().isEmpty()) {
                owners.remove();
            }
        }
    }

    private static void applySwellScale(Player player) {
        AttributeInstance scale = scaleOf(player);
        if (scale == null || hasSwellModifier(scale)) {
            return;
        }
        try {
            scale.addModifier(new AttributeModifier(SWELL_KEY, SWELL_SCALE, AttributeModifier.Operation.ADD_NUMBER));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void clearSwellScale(Player player) {
        AttributeInstance scale = scaleOf(player);
        if (scale == null) {
            return;
        }
        for (AttributeModifier modifier : new ArrayList<>(scale.getModifiers())) {
            if (SWELL_KEY.equals(modifier.getKey())) {
                scale.removeModifier(modifier);
            }
        }
    }

    private static boolean hasSwellModifier(AttributeInstance scale) {
        for (AttributeModifier modifier : scale.getModifiers()) {
            if (SWELL_KEY.equals(modifier.getKey())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Starfall

    private final class Plunge extends BukkitRunnable {

        private final Player player;
        private final State state;
        private int ticks;
        private double lastY;
        private int stalledTicks;

        Plunge(Player player, State state) {
            this.player = player;
            this.state = state;
            this.lastY = player.getLocation().getY();
            state.plunging = true;
            state.fallGuardUntil = Integer.MAX_VALUE;
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 0.6f);
            world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.55f);
            world.spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        }

        @Override
        public void run() {
            ticks++;
            if (!player.isOnline() || player.isDead()) {
                finish(false);
                return;
            }
            Location at = player.getLocation();
            World world = at.getWorld();
            if (world == null) {
                finish(false);
                return;
            }
            stalledTicks = ticks > 2 && at.getY() >= lastY - 0.02 ? stalledTicks + 1 : 0;
            lastY = at.getY();
            boolean landed = ticks > 1 && (player.isOnGround() || stalledTicks >= 3);
            if (landed || ticks > 80 || player.isFlying() || player.isInsideVehicle()) {
                finish(landed);
                return;
            }
            player.setVelocity(new Vector(0, -STARFALL_SPEED, 0));
            player.setFallDistance(0f);
            world.spawnParticle(Particle.DUST, at.clone().add(0, 1.2, 0), 4, 0.18, 0.5, 0.18, 0, new Particle.DustOptions(CRIMSON, 1.3f));
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.8, 0), 1, 0.1, 0.3, 0.1, 0.0);
            double drop = heightAboveGround(at);
            Location floor = at.clone().subtract(0, drop, 0);
            boolean close = drop / STARFALL_SPEED <= STARFALL_TELL_TICKS;
            if (close || ticks % 3 == 0) {
                disc(floor.add(0, 0.08, 0), STARFALL_RADIUS, close ? CRIMSON : VIOLET, close);
            }
        }

        private void finish(boolean landed) {
            cancel();
            state.plunging = false;
            state.fallGuardUntil = Bukkit.getCurrentTick() + 40;
            player.setFallDistance(0f);
            if (landed) {
                land(player, player.getLocation());
            }
        }
    }

    private void land(Player player, Location feet) {
        World world = feet.getWorld();
        if (world == null) {
            return;
        }
        double amount = Math.max(12.0, weaponDamage(player) * STARFALL_WEAPON);
        world.playSound(feet, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.75f);
        world.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION, feet.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLASH, feet.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
        Block below = feet.clone().subtract(0, 0.2, 0).getBlock();
        if (below.getType().isSolid()) {
            world.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.1, 0), 40, 1.4, 0.1, 1.4, 0.2, below.getBlockData());
        }
        for (LivingEntity target : targetsNear(feet, STARFALL_RADIUS)) {
            strike(target, player, amount);
            Vector away = target.getLocation().toVector().subtract(feet.toVector()).setY(0);
            away = away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize().multiply(1.1);
            target.setVelocity(target.getVelocity().add(away.setY(0.55)));
        }
        Location center = feet.clone().add(0, 0.1, 0);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double r = 0.6 + (STARFALL_RADIUS + 0.4) * (t / 6.0);
                ring(center, r, t <= 2 ? HOT : CRIMSON, 34, 1.5f);
                if (t % 2 == 0) {
                    ring(center.clone().add(0, 0.35, 0), r * 0.7, FLARE, 20, 1.1f);
                }
                if (t >= 6) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ Main Sequence

    private void starcall(Player player, LivingEntity victim, double hit) {
        double amount = Math.max(1.0, hit * STARCALL_SHARE);
        World world = victim.getWorld();
        Location feet = victim.getLocation();
        world.playSound(feet, Sound.ITEM_TRIDENT_THROW, 0.9f, 0.8f);
        world.playSound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.6f);
        ring(feet.clone().add(0, 0.08, 0), ringRadius(victim) + 0.35, GOLD, 18, 1.2f);
        BlockDisplay lance = spawnLance(feet);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                Location impact = victim.isValid() ? victim.getLocation() : feet;
                if (t == 1 && lance != null && lance.isValid()) {
                    ease(lance, lanceShape(0.22f, 0f, 9.5f), 3);
                }
                if (t <= 3) {
                    double y = 9.0 * (1.0 - t / 3.0);
                    world.spawnParticle(Particle.END_ROD, feet.clone().add(0, y + 0.5, 0), 3, 0.05, 0.4, 0.05, 0.0);
                    world.spawnParticle(Particle.DUST, feet.clone().add(0, y + 0.5, 0), 3, 0.06, 0.4, 0.06, 0, new Particle.DustOptions(GOLD, 1.2f));
                }
                if (t == 4) {
                    if (victim.isValid() && !victim.isDead()) {
                        strike(victim, player, amount);
                    }
                    world.spawnParticle(Particle.FLASH, impact.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.DUST, impact.clone().add(0, 0.6, 0), 20, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(SOLAR, 1.3f));
                    ring(impact.clone().add(0, 0.1, 0), 1.3, HOT, 18, 1.3f);
                    world.playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 1.4f);
                    if (lance != null && lance.isValid()) {
                        ease(lance, lanceShape(0.001f, 0f, 9.5f), 5);
                    }
                }
                if (t >= 10) {
                    discard(lance);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private BlockDisplay spawnLance(Location feet) {
        if (LIVE.size() >= MAX_LIVE || feet.getWorld() == null) {
            return null;
        }
        try {
            BlockDisplay display = feet.getWorld().spawn(feet, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.WHITE_CONCRETE.createBlockData());
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(GOLD);
                spawned.setTransformation(lanceShape(0.22f, 9.0f, 0.5f));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Transformation lanceShape(float width, float base, float height) {
        return new Transformation(new Vector3f(-width / 2f, base, -width / 2f), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf());
    }

    // ------------------------------------------------------------------ Red Giant

    private void redGiant(Player player, State state) {
        state.redGiantLeft = RED_GIANT_TICKS;
        state.redGiantReadyAt = System.currentTimeMillis() + RED_GIANT_CD_MS;
        state.beamAngle = Math.toRadians(player.getLocation().getYaw());
        World world = player.getWorld();
        Location mid = player.getLocation().add(0, 1.0, 0);
        world.playSound(mid, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.55f);
        world.playSound(mid, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.7f);
        world.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, mid, 40, 0.6, 0.7, 0.6, 0, new Particle.DustOptions(FLARE, 1.6f));
        world.spawnParticle(Particle.FLAME, mid, 20, 0.5, 0.6, 0.5, 0.05);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§c§lRED GIANT §7— §fI can burn brighter."));
    }

    private void tickRedGiant(Player player, State state) {
        World world = player.getWorld();
        Location waist = player.getLocation().add(0, 0.95, 0);
        state.beamAngle += Math.toRadians(4.5);
        for (int beam = 0; beam < 2; beam++) {
            double angle = state.beamAngle + beam * Math.PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            for (double d = 0.8; d <= RED_GIANT_BEAM; d += 0.45) {
                Location p = waist.clone().add(dx * d, 0, dz * d);
                world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(FLARE, 1.05f));
            }
            world.spawnParticle(Particle.FLAME, waist.clone().add(dx * RED_GIANT_BEAM, 0, dz * RED_GIANT_BEAM), 1, 0.03, 0.03, 0.03, 0.005);
        }
        int elapsed = RED_GIANT_TICKS - state.redGiantLeft;
        if (elapsed % 20 == 0) {
            world.playSound(waist, Sound.BLOCK_BEACON_AMBIENT, 0.55f, 0.7f + elapsed / (float) RED_GIANT_TICKS * 0.8f);
        }
        if (elapsed % 10 != 0) {
            return;
        }
        double burn = Math.max(2.0, weaponDamage(player) * RED_GIANT_BURN);
        for (LivingEntity target : targetsNear(waist, RED_GIANT_BEAM + 0.6)) {
            Vector to = target.getLocation().toVector().subtract(waist.toVector()).setY(0);
            if (to.lengthSquared() < 0.25) {
                continue;
            }
            double angle = Math.atan2(to.getZ(), to.getX());
            if (nearBeam(angle, state.beamAngle) || nearBeam(angle, state.beamAngle + Math.PI)) {
                strike(target, player, burn);
                world.spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.5, 0), 5, 0.2, 0.3, 0.2, 0.02);
            }
        }
    }

    private static boolean nearBeam(double angle, double beam) {
        double delta = Math.atan2(Math.sin(angle - beam), Math.cos(angle - beam));
        return Math.abs(delta) <= Math.toRadians(24.0);
    }

    private void endRedGiant(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.8f);
        world.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1.0, 0), 14, 0.4, 0.5, 0.4, 0.02);
    }

    // ------------------------------------------------------------------ Collapse

    private final class Collapse extends BukkitRunnable {

        private final Player player;
        private final State state;
        private final List<ItemDisplay> plates = new ArrayList<>();
        private final Vector[] outward = new Vector[4];
        private BlockDisplay core;
        private int t;

        Collapse(Player player, State state) {
            this.player = player;
            this.state = state;
            state.collapsing = true;
            player.setFallDistance(0f);
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, COLLAPSE_TICKS + 2, 3, false, false, true));
            World world = player.getWorld();
            Location chest = player.getLocation().add(0, 1.1, 0);
            world.playSound(chest, Sound.BLOCK_BEACON_DEACTIVATE, 1.1f, 0.6f);
            world.playSound(chest, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.5f);
            core = spawnCore(chest);
        }

        @Override
        public void run() {
            t++;
            if (!player.isOnline() || player.isDead()) {
                finish(player.isOnline() && !player.isDead());
                return;
            }
            World world = player.getWorld();
            Location feet = player.getLocation();
            Location chest = feet.clone().add(0, 1.1, 0);
            if (t <= COLLAPSE_TICKS) {
                blackStar(world, chest);
                if (core != null && core.isValid()) {
                    core.teleport(chest);
                    if (t == 1) {
                        ease(core, cube(0.7f), 6);
                    }
                }
                if (t == 10) {
                    world.playSound(chest, Sound.ENTITY_WARDEN_HEARTBEAT, 1.1f, 0.5f);
                }
                if (t % 2 == 0) {
                    pull(chest);
                }
                if (t == COLLAPSE_TICKS) {
                    nova(world, feet, chest);
                }
                return;
            }
            int reform = t - COLLAPSE_TICKS;
            tickPlates(feet, reform);
            if (reform < 8) {
                double r = 0.8 + COLLAPSE_RADIUS * (reform / 7.0);
                ring(feet.clone().add(0, 0.15, 0), r, reform < 2 ? HOT : VIOLET, 40, 1.6f);
                ring(feet.clone().add(0, 0.9, 0), r * 0.8, PHOTON, 24, 1.1f);
            }
            if (reform >= REFORM_TICKS) {
                finish(true);
            }
        }

        private void blackStar(World world, Location chest) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                Vector dir = new Vector(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
                if (dir.lengthSquared() < 0.01) {
                    continue;
                }
                dir.normalize();
                Location from = chest.clone().add(dir.clone().multiply(2.6));
                Vector in = dir.clone().multiply(-1);
                world.spawnParticle(Particle.SMOKE, from, 0, in.getX(), in.getY(), in.getZ(), 0.22);
                world.spawnParticle(Particle.DUST, from, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(VOID, 1.3f));
            }
            world.spawnParticle(Particle.DUST, chest, 4, 0.18, 0.18, 0.18, 0, new Particle.DustOptions(VIOLET, 1.1f));
            world.spawnParticle(Particle.REVERSE_PORTAL, chest, 3, 0.25, 0.25, 0.25, 0.02);
        }

        private void pull(Location chest) {
            for (LivingEntity target : targetsNear(chest, COLLAPSE_RADIUS)) {
                Vector to = chest.toVector().subtract(target.getLocation().toVector()).setY(0);
                if (to.lengthSquared() < 1.2) {
                    continue;
                }
                target.setVelocity(target.getVelocity().multiply(0.4).add(to.normalize().multiply(0.32)));
            }
        }

        private void nova(World world, Location feet, Location chest) {
            removeCore();
            world.spawnParticle(Particle.FLASH, chest, 3, 0.2, 0.2, 0.2, 0);
            world.spawnParticle(Particle.END_ROD, chest, 40, 0.3, 0.3, 0.3, 0.35);
            world.spawnParticle(Particle.DUST, chest, 40, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(PHOTON, 1.6f));
            world.playSound(chest, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.1f, 0.75f);
            world.playSound(chest, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.6f);
            double weapon = weaponDamage(player);
            for (LivingEntity target : targetsNear(feet, COLLAPSE_RADIUS)) {
                boolean boss = target.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING);
                strike(target, player, Math.max(10.0, weapon * (boss ? NOVA_BOSS_WEAPON : NOVA_WEAPON)));
                Vector away = target.getLocation().toVector().subtract(feet.toVector()).setY(0);
                away = away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize().multiply(1.6);
                target.setVelocity(away.setY(0.6));
            }
            state.collapsing = false;
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            restoreHealth(player);
            player.setNoDamageTicks(20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, false, true, true));
            player.sendMessage("§5✦ Last Light §7— §dthe star refused to go out. §8(12m)");
            spawnPlates(feet);
        }

        private void spawnPlates(Location feet) {
            Material[] pieces = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                    Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
            double yaw = Math.toRadians(feet.getYaw());
            for (int i = 0; i < pieces.length; i++) {
                double a = yaw + i * (Math.PI / 2.0) + Math.PI / 4.0;
                outward[i] = new Vector(Math.cos(a), 0.25 - i * 0.1, Math.sin(a)).multiply(1.7);
                if (LIVE.size() >= MAX_LIVE || feet.getWorld() == null) {
                    plates.add(null);
                    continue;
                }
                Material material = pieces[i];
                float height = plateHeight(i);
                try {
                    ItemDisplay plate = feet.getWorld().spawn(anchor(feet), ItemDisplay.class, spawned -> {
                        spawned.setItemStack(new ItemStack(material));
                        spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        spawned.setPersistent(false);
                        spawned.setBrightness(LIT);
                        spawned.setTeleportDuration(1);
                        spawned.setGlowing(true);
                        spawned.setGlowColorOverride(VIOLET);
                        spawned.setTransformation(plate(new Vector3f(0f, height, 0f)));
                    });
                    LIVE.add(plate);
                    plates.add(plate);
                } catch (Throwable ignored) {
                    plates.add(null);
                }
            }
        }

        private void tickPlates(Location feet, int reform) {
            World world = feet.getWorld();
            for (int i = 0; i < plates.size(); i++) {
                ItemDisplay plate = plates.get(i);
                if (plate == null || !plate.isValid()) {
                    continue;
                }
                plate.teleport(anchor(feet));
                Vector out = outward[i];
                float height = plateHeight(i);
                if (reform == 1) {
                    ease(plate, plate(new Vector3f((float) out.getX(), height + (float) out.getY(), (float) out.getZ())), 4);
                } else if (reform == 5) {
                    ease(plate, plate(new Vector3f(0f, height, 0f)), 8);
                }
                if (world != null && reform == 6 + i * 2) {
                    world.playSound(feet, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.9f, 0.8f + i * 0.2f);
                }
            }
        }

        private float plateHeight(int index) {
            return switch (index) {
                case 0 -> 1.75f;
                case 1 -> 1.25f;
                case 2 -> 0.8f;
                default -> 0.25f;
            };
        }

        private Location anchor(Location feet) {
            Location at = feet.clone();
            at.setYaw(0f);
            at.setPitch(0f);
            return at;
        }

        private void finish(boolean alive) {
            cancel();
            removeCore();
            for (ItemDisplay plate : plates) {
                discard(plate);
            }
            plates.clear();
            if (state.collapsing) {
                state.collapsing = false;
                if (alive) {
                    restoreHealth(player);
                }
            }
            if (!player.isOnline()) {
                states.remove(player.getUniqueId());
            }
        }

        private BlockDisplay spawnCore(Location chest) {
            if (LIVE.size() >= MAX_LIVE || chest.getWorld() == null) {
                return null;
            }
            try {
                BlockDisplay display = chest.getWorld().spawn(chest, BlockDisplay.class, spawned -> {
                    spawned.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    spawned.setPersistent(false);
                    spawned.setBrightness(LIT);
                    spawned.setTeleportDuration(1);
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(VIOLET);
                    spawned.setTransformation(cube(0.001f));
                });
                LIVE.add(display);
                return display;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void removeCore() {
            discard(core);
            core = null;
        }
    }

    private static Transformation cube(float size) {
        return new Transformation(new Vector3f(-size / 2f, -size / 2f, -size / 2f),
                new Quaternionf().rotateY((float) Math.toRadians(45.0)).rotateX((float) Math.toRadians(35.26)),
                new Vector3f(size, size, size), new Quaternionf());
    }

    private static Transformation plate(Vector3f at) {
        return new Transformation(at, new Quaternionf(), new Vector3f(0.75f, 0.75f, 0.75f), new Quaternionf());
    }

    private void restoreHealth(Player player) {
        double max = maxHealth(player);
        double restored = Math.max(4.0, max * COLLAPSE_RETURN);
        double missing = restored - player.getHealth();
        if (missing <= 0) {
            return;
        }
        HealthListener health = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getHealthListener();
        if (health != null) {
            health.heal(player, missing);
        } else {
            player.setHealth(Math.min(max, restored));
        }
    }

    // ------------------------------------------------------------------ idle

    private void idleMotes(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        world.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), 1, 0.12, 0.08, 0.12, 0, new Particle.DustOptions(GOLD, 0.8f));
        world.spawnParticle(Particle.END_ROD, base.clone().add(0, player.getHeight() + 0.1, 0), 1, 0.15, 0.03, 0.15, 0.0);
    }

    // ------------------------------------------------------------------ helpers

    /** Lands inside the real hit's invulnerability window, then restores it so the next real swing keeps its timing. */
    private static void strike(LivingEntity target, Player player, double amount) {
        if (target == null || !target.isValid() || target.isDead() || amount <= 0.0) {
            return;
        }
        int iframes = target.getNoDamageTicks();
        double last = target.getLastDamage();
        target.setNoDamageTicks(0);
        ScriptedHits.run(() -> target.damage(amount, player));
        if (target.isValid() && !target.isDead()) {
            target.setNoDamageTicks(iframes);
            target.setLastDamage(last);
        }
    }

    private List<LivingEntity> targetsNear(Location center, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return found;
        }
        double r2 = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, Math.max(2.5, radius * 0.75), radius)) {
            if (!(entity instanceof LivingEntity living) || !isTarget(living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(center) <= r2) {
                found.add(living);
            }
        }
        return found;
    }

    private static boolean isTarget(LivingEntity living) {
        return living.isValid() && !living.isDead() && TestPrototypeAbilities.isCombatTarget(living);
    }

    private double weaponDamage(Player player) {
        double damage = stats.getStat(player, ItemCapability.DAMAGE);
        return damage > 0.0 ? damage : 10.0;
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new State());
    }

    private Pieces pieces(Player player) {
        return new Pieces(
                isPiece(player.getInventory().getHelmet(), HELMET),
                isPiece(player.getInventory().getChestplate(), CHEST),
                isPiece(player.getInventory().getLeggings(), LEGS),
                isPiece(player.getInventory().getBoots(), BOOTS)
        );
    }

    private boolean isPiece(ItemStack item, String id) {
        return item != null && !item.getType().isAir() && id.equalsIgnoreCase(itemManager.getItemId(item));
    }

    private static double heightAboveGround(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return 0.0;
        }
        RayTraceResult hit = world.rayTraceBlocks(at, new Vector(0, -1, 0), 64.0, FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitPosition() == null) {
            return 64.0;
        }
        return Math.max(0.0, at.getY() - hit.getHitPosition().getY());
    }

    private static void ring(Location center, double radius, Color color, int points, float size) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            world.spawnParticle(Particle.DUST, center.getX() + Math.cos(a) * radius, center.getY(),
                    center.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private static void disc(Location center, double radius, Color color, boolean filled) {
        ring(center, radius, color, 32, filled ? 1.5f : 1.1f);
        if (filled) {
            ring(center, radius * 0.6, color, 20, 1.2f);
            World world = center.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.DUST, center, 12, radius * 0.35, 0.02, radius * 0.35, 0,
                        new Particle.DustOptions(color, 1.1f));
            }
        }
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(maxHealthAttribute());
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private static AttributeInstance scaleOf(Player player) {
        Attribute attribute = scaleAttribute();
        return attribute == null ? null : player.getAttribute(attribute);
    }

    private static boolean isUnsavable(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER;
    }

    private static Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException ignored) {
            return Attribute.valueOf("MAX_HEALTH");
        }
    }

    private static Attribute scaleAttribute() {
        try {
            return Attribute.valueOf("GENERIC_SCALE");
        } catch (IllegalArgumentException ignored) {
            try {
                return Attribute.valueOf("SCALE");
            } catch (IllegalArgumentException ignoredToo) {
                return null;
            }
        }
    }

    private record Pieces(boolean helmet, boolean chest, boolean legs, boolean boots, int count) {
        Pieces(boolean helmet, boolean chest, boolean legs, boolean boots) {
            this(helmet, chest, legs, boots, (helmet ? 1 : 0) + (chest ? 1 : 0) + (legs ? 1 : 0) + (boots ? 1 : 0));
        }
    }

    private static final class State {
        double heat;
        int lastHitTick;
        boolean primed;
        boolean swollen;
        long lastSneakMs;
        long starfallReadyAt;
        boolean plunging;
        int fallGuardUntil = Integer.MIN_VALUE;
        UUID sequenceTarget;
        int sequenceCount;
        int redGiantLeft;
        long redGiantReadyAt;
        double beamAngle;
        long collapseReadyAt;
        boolean collapsing;
    }

    private static final class Flare {
        final double perPulse;
        int nextTick;
        int pulses;

        Flare(double perPulse, int nextTick) {
            this.perPulse = perPulse;
            this.nextTick = nextTick;
        }
    }
}
