package de.aetherion.items.combat.system;

import de.aetherion.core.AetherEntities;
import de.aetherion.core.AetherKeys;
import de.aetherion.items.combat.IncomingHits;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parry / guard on F, roll i-frames, and the stagger that a perfect parry puts on trash.
 * <p>
 * Runs on incoming player damage at LOW, before mitigation and before BossEngine turns boss melee into a
 * raw {@code BossHits} hit at HIGH. Cancels (roll, perfect parry) happen there; the guard-window cut only
 * marks the hit and lands at HIGH, after WildlifeLooks and OreTroll have set their absolute numbers.
 * Boss ground hazards (TRUE_DAMAGE ticks that are not a BossHits strike) can be rolled through but never
 * parried, and neither can the BossHits hit BossEngine makes out of them. Boss rules ({@code parry.bosses}):
 * <ul>
 *   <li>negate: a perfect parry cancels the hit. The outer melee event is cancelled, so BossEngine never
 *       re-applies it; skill and projectile hits arrive as nested raw events and are cancelled there.</li>
 *   <li>guard: only the guard cut, applied to the raw hit that carries the real damage.</li>
 *   <li>off: bosses ignore the guard.</li>
 * </ul>
 * Bosses are never staggered, frozen or reflected at; their AI stays theirs.
 */
final class Guard {

    private static final NamespacedKey APPLYING_HIT = new NamespacedKey("bossengine", "applying_hit");
    /** Marks a mob whose awareness we switched off, so a chunk unload mid-stagger can be repaired on load. */
    private final NamespacedKey frozenKey;

    private final CombatSystem system;
    private final Map<UUID, Freeze> frozen = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vulnerableUntil = new ConcurrentHashMap<>();

    Guard(CombatSystem system) {
        this.system = system;
        this.frozenKey = new NamespacedKey(system.plugin(), "combat_frozen");
    }

    /* =========================================================
     * INPUT
     * ========================================================= */

    /** F pressed with a weapon. Returns true when the swap should be swallowed. */
    boolean press(Player player, long tick) {
        CombatConfig cfg = system.config();
        if (!cfg.parry || !system.eligible(player)) {
            return false;
        }
        // Mid-draw bows keep F as a swap: the bow listeners drop their charge on any swap, cancelled or not.
        if (player.hasActiveItem() && ranged(player.getInventory().getItemInMainHand())) {
            return false;
        }
        CombatState state = system.state(player);
        if (state.parryOpen || tick < state.parryLockUntil || state.inRoll(tick)) {
            system.deny(player, state, tick);
            return true;
        }
        if (!system.spend(player, state, cfg.parryCost, tick)) {
            return true;
        }
        state.parryStartTick = tick;
        state.parryPerfectUntil = tick + cfg.perfectTicks;
        state.parryGuardUntil = tick + cfg.perfectTicks + cfg.guardTicks;
        state.parryOpen = true;
        state.parryCaught = false;
        system.feedback().guardUp(player);
        return true;
    }

    /** Per player per tick: close the window, and lock the guard after a whiff. */
    void tick(Player player, CombatState state, long tick) {
        if (state.parryOpen && tick >= state.parryGuardUntil) {
            state.parryOpen = false;
            if (!state.parryCaught) {
                state.parryLockUntil = tick + system.config().whiffLockout;
                system.feedback().whiff(player);
            }
        }
    }

    /* =========================================================
     * INCOMING (LOW, ignoreCancelled)
     * ========================================================= */

    void onIncoming(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CombatState state = system.peek(player);
        if (state == null) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        boolean iframes = state.iframes(tick);
        boolean guarding = state.guarding(tick);
        if (!iframes && !guarding) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || !system.config().enabled) {
            return;
        }

        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        if (damager == player) {
            return;
        }
        boolean strike = IncomingHits.isRaw() || flag(player, APPLYING_HIT);
        // TRUE_DAMAGE without a BossHits strike = a zone / aura tick (BoilingZone and friends).
        boolean hazard = !strike && flag(player, AetherKeys.TRUE_DAMAGE);
        if (state.hazard != null && state.hazardTick != tick) {
            state.hazard = null;
        }
        if (hazard && state.hazard == null) {
            state.hazard = event;
            state.hazardTick = tick;
        }
        boolean fromHazard = state.hazard != null && state.hazard != event;
        boolean raw = strike || hazard;
        Entity source = source(damager);
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean attack = damager != null && !hazard && (raw
                || cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE);
        boolean area = damager != null && (hazard
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.MAGIC
                || cause == EntityDamageEvent.DamageCause.SONIC_BOOM);
        if (!attack && !area) {
            return;
        }

        if (iframes && (system.config().rollBosses || !(system.bossTier(source) || system.bossTier(damager)))) {
            event.setCancelled(true);
            if (!state.rollDodged) {
                state.rollDodged = true;
                system.refund(state, system.config().perfectDodgeRefund);
                system.feedback().perfectDodge(player);
            }
            system.markCombat(player, tick);
            return;
        }
        if (!guarding || !attack || fromHazard || system.dummies().unparryable() || !inFront(player, source, damager)) {
            return;
        }
        catchHit(event, player, state, damager, source, raw, tick);
    }

    /** HIGH, ignoreCancelled: the guard-window cut for the hit marked at LOW. */
    void applyCut(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CombatState state = system.peek(player);
        if (state == null || state.guardCut != event) {
            return;
        }
        state.guardCut = null;
        event.setDamage(event.getDamage() * system.config().guardTaken);
    }

    /** MONITOR, cancelled or not: drop this event's markers. */
    void settle(Player player, EntityDamageEvent event) {
        CombatState state = system.peek(player);
        if (state == null) {
            return;
        }
        if (state.guardCut == event) {
            state.guardCut = null;
        }
        if (state.hazard == event) {
            state.hazard = null;
        }
    }

    private void catchHit(EntityDamageEvent event, Player player, CombatState state, Entity damager, Entity source,
                          boolean raw, long tick) {
        CombatConfig cfg = system.config();
        boolean perfect = state.perfectParry(tick);
        boolean boss = system.bossTier(source) || system.bossTier(damager);
        boolean first = !state.parryCaught;
        system.markCombat(player, tick);

        if (boss) {
            if (cfg.bossParry == CombatConfig.BossParry.OFF) {
                return;
            }
            if (perfect && cfg.bossParry == CombatConfig.BossParry.NEGATE) {
                event.setCancelled(true);
                if (damager instanceof Projectile projectile) {
                    projectile.remove();
                }
                caught(player, state, source, true, true, first, tick);
                return;
            }
            // BossEngine cancels its own outer melee and re-applies the real damage as a nested raw hit.
            if (!raw && AetherEntities.isBoss(source) && damager == source
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                return;
            }
            state.guardCut = event;
            caught(player, state, source, false, true, first, tick);
            return;
        }

        if (!perfect) {
            state.guardCut = event;
            caught(player, state, source, false, false, first, tick);
            return;
        }

        event.setCancelled(true);
        if (damager instanceof Projectile projectile) {
            deflect(player, projectile, source);
        } else if (source instanceof LivingEntity living && !(living instanceof Player) && !system.protectedEntity(living)) {
            counter(player, living, tick);
        }
        caught(player, state, source, true, false, first, tick);
    }

    private void caught(Player player, CombatState state, Entity source, boolean perfect, boolean boss, boolean first,
                        long tick) {
        CombatConfig cfg = system.config();
        state.parryCaught = true;
        if (perfect) {
            if (first) {
                system.refund(state, cfg.parryRefund);
            }
            state.riposteUntil = tick + cfg.riposteWindow;
            state.riposteTarget = source == null ? null : AetherEntities.root(source).getUniqueId();
            system.feedback().perfectParry(player, source, boss);
        } else {
            system.feedback().guarded(player, source);
        }
    }

    /** Perfect parry on trash: shove and stagger. The riposte window is the payoff. */
    private void counter(Player player, LivingEntity attacker, long tick) {
        Vector away = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 1.0E-4) {
            attacker.setVelocity(attacker.getVelocity().add(away.normalize().multiply(0.45).setY(0.18)));
        }
        if (attacker instanceof Mob mob) {
            stagger(mob, tick);
        }
        if (system.dummies().isDummy(attacker)) {
            system.dummies().parried(attacker, tick);
        }
    }

    /** Perfect parry on a projectile: arrows go back at a non-boss shooter, everything else is just swatted. */
    private void deflect(Player player, Projectile projectile, Entity shooter) {
        boolean sendBack = system.config().reflectProjectiles && projectile instanceof AbstractArrow
                && shooter instanceof LivingEntity living && !(living instanceof Player)
                && !system.protectedEntity(living) && living.getWorld() == player.getWorld();
        double damage = projectile instanceof AbstractArrow arrow ? arrow.getDamage() : 2.0;
        projectile.remove();
        if (!sendBack) {
            return;
        }
        LivingEntity target = (LivingEntity) shooter;
        Vector aim = target.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
        if (aim.lengthSquared() < 1.0E-4) {
            return;
        }
        Arrow back = player.launchProjectile(Arrow.class, aim.normalize().multiply(2.2));
        back.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        back.setDamage(Math.max(2.0, damage));
        back.setCritical(true);
    }

    /* =========================================================
     * STAGGER / FREEZE / VULNERABLE
     * ========================================================= */

    void stagger(Mob mob, long tick) {
        CombatConfig cfg = system.config();
        if (system.protectedEntity(mob)) {
            return;
        }
        if (cfg.staggerTicks > 0) {
            freeze(mob, cfg.staggerTicks, tick).stagger = true;
        }
        if (cfg.vulnerableTicks > 0) {
            vulnerableUntil.put(mob.getUniqueId(), tick + cfg.vulnerableTicks);
        }
        system.feedback().stagger(mob);
    }

    boolean vulnerable(Entity entity, long tick) {
        if (entity == null || vulnerableUntil.isEmpty()) {
            return false;
        }
        Long until = vulnerableUntil.get(AetherEntities.root(entity).getUniqueId());
        return until != null && tick < until;
    }

    /** Pauses a mob's AI (awareness) for a few ticks. Never touches bosses or system entities. */
    Freeze freeze(Mob mob, int ticks, long now) {
        Freeze existing = frozen.get(mob.getUniqueId());
        if (existing != null) {
            existing.until = Math.max(existing.until, now + ticks);
            return existing;
        }
        boolean wasAware = mob.isAware();
        if (wasAware && !system.protectedEntity(mob)) {
            mob.setAware(false);
            mob.getPersistentDataContainer().set(frozenKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            wasAware = false;
        }
        Freeze freeze = new Freeze(mob, now + ticks, wasAware);
        frozen.put(mob.getUniqueId(), freeze);
        return freeze;
    }

    void tickStaggers(long tick) {
        if (!frozen.isEmpty()) {
            Iterator<Freeze> it = frozen.values().iterator();
            while (it.hasNext()) {
                Freeze freeze = it.next();
                Mob mob = freeze.mob;
                if (!mob.isValid()) {
                    it.remove();
                    continue;
                }
                if (tick >= freeze.until) {
                    thaw(freeze);
                    it.remove();
                } else if (freeze.stagger) {
                    system.feedback().staggerTick(mob, tick);
                }
            }
        }
        if (tick % 40 == 0 && !vulnerableUntil.isEmpty()) {
            vulnerableUntil.values().removeIf(until -> until <= tick);
        }
    }

    private void thaw(Freeze freeze) {
        if (freeze.restoreAware && freeze.mob.isValid()) {
            freeze.mob.setAware(true);
            freeze.mob.getPersistentDataContainer().remove(frozenKey);
        }
    }

    /** A mob saved while frozen (chunk unload, crash) gets its AI back the next time it loads. */
    void repair(Entity entity) {
        if (!(entity instanceof Mob mob) || frozen.containsKey(mob.getUniqueId())) {
            return;
        }
        PersistentDataContainer data = mob.getPersistentDataContainer();
        if (data.has(frozenKey, PersistentDataType.BYTE)) {
            data.remove(frozenKey);
            mob.setAware(true);
        }
    }

    void repairLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                repair(living);
            }
        }
    }

    void forget(UUID playerId) {
        // Per-player windows live in CombatState; nothing player-keyed here.
    }

    void releaseAll() {
        for (Freeze freeze : frozen.values()) {
            thaw(freeze);
        }
        frozen.clear();
        vulnerableUntil.clear();
    }

    static final class Freeze {
        final Mob mob;
        final boolean restoreAware;
        long until;
        boolean stagger;

        Freeze(Mob mob, long until, boolean restoreAware) {
            this.mob = mob;
            this.until = until;
            this.restoreAware = restoreAware;
        }
    }

    /* =========================================================
     * HELPERS
     * ========================================================= */

    private static boolean flag(Player player, NamespacedKey key) {
        Byte value = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private boolean ranged(ItemStack hand) {
        if (hand == null) {
            return false;
        }
        Material type = hand.getType();
        return type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT || system.isRangedOrWand(hand);
    }

    private static Entity source(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity entity ? entity : damager;
        }
        return damager;
    }

    /** Parry covers the front ~200 degrees; hits from behind always land. */
    private static boolean inFront(Player player, Entity source, Entity damager) {
        Vector from;
        if (damager instanceof Projectile projectile && projectile.getVelocity().lengthSquared() > 1.0E-4) {
            from = projectile.getVelocity().clone().multiply(-1).setY(0);
        } else {
            Entity origin = source != null ? source : damager;
            if (origin == null || origin.getWorld() != player.getWorld()) {
                return true;
            }
            from = origin.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        if (from.lengthSquared() < 0.04) {
            return true;
        }
        return CombatSystem.facing(player).dot(from.normalize()) >= -0.17;
    }
}
