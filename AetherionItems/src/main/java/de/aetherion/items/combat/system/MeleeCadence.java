package de.aetherion.items.combat.system;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.combat.ScriptedHits;
import de.aetherion.items.listener.DamageListener;
import de.aetherion.items.mining.OreTrollListener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Light vs heavy melee on top of the Aetherion DAMAGE stamp.
 * <p>
 * DamageListener (HIGH, registered first) replaces vanilla damage with the DAMAGE stat and ignores the
 * attack charge, which is why spam-clicking used to deal full damage. This hook runs right after it at
 * HIGH and only scales that number:
 * <ul>
 *   <li>Charge is read in {@code PrePlayerAttackEntityEvent}, before vanilla resets the ticker. A hit with
 *       no swing snapshot this tick (reflects, procs, other plugins' nested hits) is left alone.</li>
 *   <li>Early swings on stamped weapons: {@code floor + (1 - floor) * charge^2}, like vanilla. Attack-spread
 *       hits from the same swing take the same penalty, nothing else.</li>
 *   <li>Timed swings ({@code charge >= timed-charge}) build a combo on one target; the Nth is a finisher.
 *       Aerial (jump-crit), riposte, dash strike and vulnerable (staggered) stack, capped at max-mult.</li>
 *   <li>Against bosses only part of the bonus applies; the early-swing penalty applies in full.</li>
 * </ul>
 * The number is set at HIGH; combo, riposte and dash-strike are spent, and the strike cue plays, only at
 * MONITOR once the hit stood (a boss that blocks or gates the hit does not eat the riposte). Samurai's
 * hitbox cancels every hit and takes the damage itself, so it counts as landed.
 * <p>
 * Untouched: scripted ability hits, sweeps, bows / crossbows / wands, ore trolls, PvP, and the LOCKED
 * weapons in {@code melee.exempt-items}.
 */
final class MeleeCadence {

    /** One scaled hit waiting for MONITOR. */
    record Commit(EntityDamageByEntityEvent event, LivingEntity victim, HitFeedback.Strike kind, boolean boss,
                  boolean timed, UUID target, boolean finisher, boolean riposte, boolean dashStrike) {
    }

    private final CombatSystem system;

    MeleeCadence(CombatSystem system) {
        this.system = system;
    }

    /** PrePlayerAttackEntityEvent, MONITOR: the swing will happen; keep its real charge. */
    @SuppressWarnings("deprecation") // client ground flag: vanilla's own jump-crit check reads the same value
    void snapshot(Player player, Entity attacked) {
        CombatConfig cfg = system.config();
        // Dragon parts are not living; the damage event lands on the dragon, so key the swing on the root.
        Entity target = attacked == null ? null : AetherEntities.root(attacked);
        if (!cfg.enabled || !cfg.melee || !(target instanceof LivingEntity)) {
            return;
        }
        CombatState state = system.state(player);
        float charge = player.getAttackCooldown();
        state.swingCharge = charge;
        state.swingTick = Bukkit.getCurrentTick();
        state.swingTarget = target.getUniqueId();
        state.swingAerial = charge > 0.9f
                && player.getFallDistance() > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isInWater()
                && !player.isInsideVehicle()
                && !player.isSprinting()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    /** EntityDamageByEntityEvent, HIGH, ignoreCancelled, registered after DamageListener. */
    void apply(EntityDamageByEntityEvent event) {
        CombatConfig cfg = system.config();
        if (!cfg.enabled || !cfg.melee) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
            return;
        }
        if (ScriptedHits.isActive()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if ((mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) || cfg.worldDisabled(player.getWorld().getName())) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (system.isRangedOrWand(hand) || OreTrollListener.isOreTroll((Entity) victim) || cfg.exempt(system.heldItemId(player))) {
            return;
        }
        CombatState state = system.peek(player);
        long tick = Bukkit.getCurrentTick();
        if (state == null || state.swingTick != tick) {
            return;
        }
        float charge = state.swingCharge;
        boolean stamped = system.stamped(player);
        boolean timed = charge >= cfg.timedCharge;
        double penalty = stamped && !timed ? cfg.lightFloor + (1.0 - cfg.lightFloor) * charge * charge : 1.0;

        if (DamageListener.isSpreadHit(player.getUniqueId())) {
            // Spread copies of this swing: same penalty, no bonus, no bookkeeping.
            if (penalty < 1.0) {
                event.setDamage(event.getDamage() * penalty);
            }
            return;
        }
        UUID target = AetherEntities.root(victim).getUniqueId();
        if (!target.equals(state.swingTarget)) {
            return;
        }

        boolean aerial = state.swingAerial;
        boolean boss = system.bossTier(victim);
        boolean finisher = timed && target.equals(state.comboTarget) && tick <= state.comboExpireTick
                && state.comboCount + 1 >= cfg.finisherHits;
        boolean riposte = timed && tick < state.riposteUntil && target.equals(state.riposteTarget);
        boolean dashStrike = timed && tick < state.dashStrikeUntil;
        boolean vulnerable = system.guard().vulnerable(victim, tick);
        boolean airHit = timed && aerial;

        double bonus = 1.0;
        if (airHit && stamped) {
            // Unstamped weapons already got vanilla's x1.5 jump crit.
            bonus *= cfg.aerialMult;
        }
        if (finisher) {
            bonus *= cfg.finisherMult;
        }
        if (riposte) {
            bonus *= cfg.riposteMult;
        }
        if (dashStrike) {
            bonus *= cfg.strikeMult;
        }
        if (vulnerable) {
            bonus *= cfg.vulnerableMult;
        }
        bonus = Math.min(cfg.maxMult, bonus);
        if (boss) {
            bonus = 1.0 + (bonus - 1.0) * cfg.bossBonusScale;
        }
        double mult = penalty * bonus;
        if (Math.abs(mult - 1.0) > 1.0E-3) {
            event.setDamage(event.getDamage() * mult);
        }

        HitFeedback.Strike kind;
        if (riposte) {
            kind = HitFeedback.Strike.RIPOSTE;
        } else if (finisher) {
            kind = HitFeedback.Strike.FINISHER;
        } else if (airHit) {
            kind = HitFeedback.Strike.AERIAL;
        } else if (dashStrike) {
            kind = HitFeedback.Strike.DASH;
        } else if (timed) {
            kind = HitFeedback.Strike.HEAVY;
        } else {
            kind = HitFeedback.Strike.LIGHT;
        }
        // The damage number reads its style at MONITOR, before commit runs.
        HitFeedback.markStrike(player, victim, kind);
        state.strike = new Commit(event, victim, kind, boss, timed, target, finisher, riposte, dashStrike);
    }

    /** EntityDamageByEntityEvent, MONITOR, cancelled or not: spend the windows and play the cue if the hit stood. */
    void commit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        CombatState state = system.peek(player);
        if (state == null || state.strike == null || state.strike.event() != event) {
            return;
        }
        Commit hit = state.strike;
        state.strike = null;
        if (event.isCancelled() && !system.samuraiHitbox(hit.victim())) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        CombatConfig cfg = system.config();
        if (hit.timed()) {
            if (hit.finisher()) {
                state.comboCount = 0;
                state.comboTarget = null;
            } else if (hit.target().equals(state.comboTarget) && tick <= state.comboExpireTick) {
                state.comboCount++;
            } else {
                state.comboTarget = hit.target();
                state.comboCount = 1;
            }
            state.comboExpireTick = tick + cfg.comboWindow;
        } else {
            state.comboCount = 0;
            state.comboTarget = null;
        }
        if (hit.riposte()) {
            state.riposteUntil = 0L;
            state.riposteTarget = null;
        }
        if (hit.dashStrike()) {
            state.dashStrikeUntil = 0L;
        }
        system.feedback().strike(player, hit.victim(), hit.kind(), hit.boss());
        system.markCombat(player, tick);
    }
}
