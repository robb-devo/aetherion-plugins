/**
 * Port of AetherionItems `DamageListener` combat math (server-authoritative).
 */
export function rollOutgoingHit(hit, random01 = Math.random) {
    let damage = hit.damage;
    if (damage <= 0) {
        return { damage: 0, crit: false };
    }
    let crit = false;
    if (hit.critChance > 0 && random01() * 100 < hit.critChance) {
        damage *= 1 + Math.max(0, hit.critDamage) / 100;
        crit = true;
    }
    damage *= hit.damageMultiplier ?? 1;
    return { damage, crit };
}
/**
 * Reduction: 100 / (100 + defense * 0.85)
 * Undead resist capped at 70%.
 */
export function applyIncomingMitigation(hit) {
    let defense = hit.defense * (hit.defenseMultiplier ?? 1);
    const pen = Math.min(1, Math.max(0, hit.armorPenetration ?? 0));
    defense *= 1 - pen;
    let dmg = hit.rawDamage;
    if (defense > 0) {
        dmg *= 100 / (100 + defense * 0.85);
    }
    if (hit.isUndeadAttack) {
        const resist = Math.min(70, Math.max(0, hit.undeadResist ?? 0));
        if (resist > 0) {
            dmg *= 1 - resist / 100;
        }
    }
    return dmg;
}
/**
 * Attack spread target count — same as Emerald Spread in DamageListener.
 * 100 AS => 1 guaranteed extra; leftover % rolls one more.
 */
export function attackSpreadTargets(attackSpread, random01 = Math.random) {
    if (attackSpread <= 0)
        return 0;
    const guaranteed = Math.floor(attackSpread / 100);
    const remaining = attackSpread - guaranteed * 100;
    let additional = guaranteed;
    if (remaining > 0 && random01() * 100 < remaining) {
        additional++;
    }
    return additional;
}
export function applyUndeadOutgoingBonus(damage, undeadDamagePercent) {
    const bonus = Math.min(80, Math.max(0, undeadDamagePercent));
    return damage * (1 + bonus / 100);
}
