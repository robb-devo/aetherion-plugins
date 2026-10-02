/**
 * Port of AetherionItems `AetherionLevel.java`.
 * Flat account ladder: XP_PER_LEVEL Aetherion XP = 1 level.
 * Skill grind feeds the pool at SKILL_XP_DIVISOR.
 */
export const MAX_LEVEL = 5000;
export const XP_PER_LEVEL = 100n;
export const SKILL_XP_DIVISOR = 40n;
export const STAT_EVERY = 5;
const RANK_MARKS = [
    25, 50, 75, 100, 150, 200, 250, 350, 500, 750, 1250, 2500, 5000,
];
export function xpToNext(level) {
    if (level < 1 || level >= MAX_LEVEL)
        return 0n;
    return XP_PER_LEVEL;
}
export function fromSkillXp(skillXp) {
    if (skillXp <= 0n || SKILL_XP_DIVISOR <= 0n)
        return 0n;
    return skillXp / SKILL_XP_DIVISOR;
}
export function of(xp) {
    if (xp <= 0n)
        return 1;
    const uncapped = 1n + xp / XP_PER_LEVEL;
    if (uncapped >= BigInt(MAX_LEVEL))
        return MAX_LEVEL;
    return Number(uncapped);
}
/** Flat HP + Damage every STAT_EVERY account levels (mirrors milestoneStatBonus). */
export function milestoneStatBonus(level) {
    const clamped = Math.max(1, Math.min(MAX_LEVEL, level));
    return Math.floor((clamped - 1) / STAT_EVERY);
}
export function titleTier(level) {
    const clamped = Math.max(1, Math.min(MAX_LEVEL, level));
    let tier = 0;
    for (const mark of RANK_MARKS) {
        if (clamped >= mark)
            tier++;
        else
            break;
    }
    return tier;
}
export function title(level) {
    switch (titleTier(level)) {
        case 1:
            return "Veteran";
        case 2:
            return "Champion";
        case 3:
        case 4:
            return "Legend";
        case 5:
            return "Mythwright";
        case 6:
            return "Aetherborn";
        case 7:
            return "Celestine";
        case 8:
        case 9:
            return "Sovereign";
        case 10:
            return "Ascendant";
        case 11:
            return "Empyrean";
        case 12:
            return "Eternal";
        case 13:
            return "Aetherion";
        default:
            return "Adventurer";
    }
}
