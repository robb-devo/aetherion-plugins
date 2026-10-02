/**
 * Port of AetherionItems `SkillProgression.java`.
 */
export declare const MAX_SKILL_LEVEL = 100;
export declare const RARITY_EVERY = 20;
export declare const PER_LEVEL = 0.0102;
export declare const PER_LEVEL_AFTER_50 = 0.044;
export declare const PER_LEVEL_AFTER_75 = 0.14;
export declare const PER_RARITY = 0.08;
export declare const COMPACT_CHANCE_BASE = 0.006;
export declare const COMPACT_CHANCE_MAX = 0.028;
export declare function clampLevel(level: number): number;
export declare function rarityTier(level: number): number;
export declare function effectMultiplier(level: number): number;
export declare function compactChance(level: number): number;
export declare function xpToNext(level: number): number;
