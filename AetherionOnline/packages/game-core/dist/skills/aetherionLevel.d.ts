/**
 * Port of AetherionItems `AetherionLevel.java`.
 * Flat account ladder: XP_PER_LEVEL Aetherion XP = 1 level.
 * Skill grind feeds the pool at SKILL_XP_DIVISOR.
 */
export declare const MAX_LEVEL = 5000;
export declare const XP_PER_LEVEL = 100n;
export declare const SKILL_XP_DIVISOR = 40n;
export declare const STAT_EVERY = 5;
export declare function xpToNext(level: number): bigint;
export declare function fromSkillXp(skillXp: bigint): bigint;
export declare function of(xp: bigint): number;
/** Flat HP + Damage every STAT_EVERY account levels (mirrors milestoneStatBonus). */
export declare function milestoneStatBonus(level: number): number;
export declare function titleTier(level: number): number;
export declare function title(level: number): string;
