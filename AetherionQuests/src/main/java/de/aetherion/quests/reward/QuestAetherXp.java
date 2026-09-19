package de.aetherion.quests.reward;

import de.aetherion.quests.model.Quest;

import java.util.Map;

/**
 * Aetherion account XP on quest rewards (100 XP ≈ 1 account level).
 * {@link #legacyGranted(String)} is the amount players already received
 * before this pass — used only for catch-up deltas on completed quests.
 */
public final class QuestAetherXp {

    /**
     * Pre-update XP payouts. Quests missing here are treated as already paid
     * at the current registry amount (no double-grant).
     */
    private static final Map<String, Integer> LEGACY = Map.ofEntries(
            Map.entry("welcome_aboard", 0),
            Map.entry("forge_coal", 0),
            Map.entry("first_hunt", 35),
            Map.entry("gather_wood", 30),
            Map.entry("farm_hand", 25),
            Map.entry("a_simple_craft", 40),
            Map.entry("shiny_things", 50),
            Map.entry("a_good_catch", 35),
            Map.entry("dock_pass", 20),
            Map.entry("first_shift", 55),
            Map.entry("open_up", 20),
            Map.entry("pocket_zoo", 40),
            Map.entry("those_sounds", 400),
            Map.entry("poultry_problem", 800),
            Map.entry("troll_toll", 1200),
            Map.entry("ink_contract", 1000),
            Map.entry("ash_and_arrows", 1800),
            Map.entry("walking_mountain", 2500),
            Map.entry("the_veil", 4500),
            Map.entry("open_ticket", 2800),
            Map.entry("lost_and_found", 2800),
            Map.entry("overtime", 3000),
            Map.entry("denied_claim", 3200),
            Map.entry("bounced_check", 3500),
            Map.entry("closed_road", 1600),
            Map.entry("lesson_steel", 80),
            Map.entry("lesson_boost", 70),
            Map.entry("border_rites", 90),
            Map.entry("lesson_manager", 60),
            Map.entry("lesson_bones", 70),
            Map.entry("sidewalk_survey", 40),
            Map.entry("gravel_ambition", 50),
            Map.entry("pantry_run", 60),
            Map.entry("pig_in_a_poke", 50),
            Map.entry("coal_audit", 80),
            Map.entry("stump_census", 80),
            Map.entry("canopy_sample", 150),
            Map.entry("scale_sample", 90),
            Map.entry("bovine_brief", 70),
            Map.entry("guild_brick", 140)
    );

    private QuestAetherXp() {
    }

    public static boolean isXpReward(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.equalsIgnoreCase("XP")
                || name.equalsIgnoreCase("EXP")
                || name.equalsIgnoreCase("EXPERIENCE")
                || name.equalsIgnoreCase("Aetherion XP")
                || name.equalsIgnoreCase("Account XP");
    }

    public static int of(Quest quest) {
        if (quest == null) {
            return 0;
        }
        int total = 0;
        for (Reward reward : quest.getRewards()) {
            if (reward != null && isXpReward(reward.getName())) {
                total += Math.max(0, reward.getAmount());
            }
        }
        return total;
    }

    /**
     * XP already paid under the previous registry, or {@code current} when unknown
     * (safe default — never double-pays brand-new quests).
     */
    public static int legacyGranted(String questId, int current) {
        if (questId == null) {
            return current;
        }
        return LEGACY.getOrDefault(questId, current);
    }
}
