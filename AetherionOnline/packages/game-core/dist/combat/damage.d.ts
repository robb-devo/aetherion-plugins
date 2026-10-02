/**
 * Port of AetherionItems `DamageListener` combat math (server-authoritative).
 */
export type HitRoll = {
    /** Base outgoing Aetherion DAMAGE capability. */
    damage: number;
    critChance: number;
    critDamage: number;
    /** Optional playtime booster, default 1. */
    damageMultiplier?: number;
};
export type IncomingHit = {
    rawDamage: number;
    defense: number;
    /** Optional playtime booster on defense, default 1. */
    defenseMultiplier?: number;
    undeadResist?: number;
    isUndeadAttack?: boolean;
    armorPenetration?: number;
};
export type CritResult = {
    damage: number;
    crit: boolean;
};
export declare function rollOutgoingHit(hit: HitRoll, random01?: () => number): CritResult;
/**
 * Reduction: 100 / (100 + defense * 0.85)
 * Undead resist capped at 70%.
 */
export declare function applyIncomingMitigation(hit: IncomingHit): number;
/**
 * Attack spread target count — same as Emerald Spread in DamageListener.
 * 100 AS => 1 guaranteed extra; leftover % rolls one more.
 */
export declare function attackSpreadTargets(attackSpread: number, random01?: () => number): number;
export declare function applyUndeadOutgoingBonus(damage: number, undeadDamagePercent: number): number;
