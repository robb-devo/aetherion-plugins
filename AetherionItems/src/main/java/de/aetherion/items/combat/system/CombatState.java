package de.aetherion.items.combat.system;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * One fighter's timers and resource. Main thread only; every field is a server tick or a plain value.
 */
final class CombatState {

    final UUID id;

    double stamina;
    long lastSpendTick = Long.MIN_VALUE / 4;
    long exhaustedUntil;
    long fullSinceTick;
    long lastCombatTick = Long.MIN_VALUE / 4;
    long denyUntil;

    long lastSprintTick = Long.MIN_VALUE / 4;
    long dashReadyTick;
    long dashStrikeUntil;
    long lastDashTick = Long.MIN_VALUE / 4;
    /** Last tick an entity hurt this player: knockback lifts fire PlayerJumpEvent too. */
    long lastHurtTick = Long.MIN_VALUE / 4;

    /** Last tick's feet position and the horizontal step since, for roll direction. */
    double lastX = Double.NaN;
    double lastZ = Double.NaN;
    double moveX;
    double moveZ;

    long rollStartTick = Long.MIN_VALUE / 4;
    long rollIframesUntil;
    long rollRecoverUntil;
    long rollEndTick = Long.MIN_VALUE / 4;
    boolean rollDodged;
    /** Roll impulse for the next tick; set inside PlayerJumpEvent, where vanilla's jump would overwrite it. */
    Vector pendingRoll;

    long parryStartTick = Long.MIN_VALUE / 4;
    long parryPerfectUntil;
    long parryGuardUntil;
    long parryLockUntil;
    boolean parryOpen;
    boolean parryCaught;
    long riposteUntil;
    UUID riposteTarget;
    /** Incoming hit caught in the guard window at LOW; the cut lands at HIGH, after absolute rewrites. */
    EntityDamageEvent guardCut;
    /** Boss ground-hazard tick in flight (and its tick): nested hits it spawns cannot be parried. */
    EntityDamageEvent hazard;
    long hazardTick;

    UUID comboTarget;
    int comboCount;
    long comboExpireTick;

    /** Charge snapshot taken before vanilla resets the attack ticker. */
    float swingCharge = 1f;
    long swingTick = Long.MIN_VALUE / 4;
    UUID swingTarget;
    boolean swingAerial;
    /** Outgoing hit scaled at HIGH, waiting for MONITOR to confirm it landed. */
    MeleeCadence.Commit strike;

    CombatState(UUID id, double stamina) {
        this.id = id;
        this.stamina = stamina;
    }

    boolean exhausted(long tick) {
        return tick < exhaustedUntil;
    }

    boolean inRoll(long tick) {
        return tick < rollRecoverUntil;
    }

    boolean iframes(long tick) {
        return tick < rollIframesUntil;
    }

    boolean guarding(long tick) {
        return tick < parryGuardUntil;
    }

    boolean perfectParry(long tick) {
        return tick < parryPerfectUntil;
    }

    boolean inCombat(long tick, int window) {
        return tick - lastCombatTick < window;
    }
}
