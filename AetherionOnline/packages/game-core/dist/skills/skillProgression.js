/**
 * Port of AetherionItems `SkillProgression.java`.
 */
export const MAX_SKILL_LEVEL = 100;
export const RARITY_EVERY = 20;
export const PER_LEVEL = 0.0102;
export const PER_LEVEL_AFTER_50 = 0.044;
export const PER_LEVEL_AFTER_75 = 0.14;
export const PER_RARITY = 0.08;
export const COMPACT_CHANCE_BASE = 0.006;
export const COMPACT_CHANCE_MAX = 0.028;
export function clampLevel(level) {
    return Math.max(1, Math.min(MAX_SKILL_LEVEL, level));
}
export function rarityTier(level) {
    const clamped = clampLevel(level);
    if (clamped < RARITY_EVERY)
        return 0;
    return Math.min(5, Math.floor(clamped / RARITY_EVERY));
}
export function effectMultiplier(level) {
    const clamped = clampLevel(level);
    const early = Math.min(clamped, 50);
    const mid = Math.min(Math.max(0, clamped - 50), 25);
    const late = Math.max(0, clamped - 75);
    return (1.0 +
        (early - 1) * PER_LEVEL +
        mid * PER_LEVEL_AFTER_50 +
        late * PER_LEVEL_AFTER_75 +
        rarityTier(clamped) * PER_RARITY);
}
export function compactChance(level) {
    const clamped = clampLevel(level);
    if (MAX_SKILL_LEVEL <= 1)
        return COMPACT_CHANCE_BASE;
    const t = (clamped - 1) / (MAX_SKILL_LEVEL - 1);
    return COMPACT_CHANCE_BASE + (COMPACT_CHANCE_MAX - COMPACT_CHANCE_BASE) * t;
}
export function xpToNext(level) {
    if (level >= MAX_SKILL_LEVEL)
        return 0;
    const current = clampLevel(level);
    let xp = 32 + 11 * current + Math.floor((current * current) / 2);
    if (current >= 50)
        xp += (current - 49) * 70;
    if (current >= 75)
        xp += (current - 74) * 110;
    return xp;
}
