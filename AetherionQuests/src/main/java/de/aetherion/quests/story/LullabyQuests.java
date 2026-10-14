package de.aetherion.quests.story;

import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import java.util.List;
import java.util.Locale;

/**
 * Main story "Das Schlaflied", Act I–III (quests 1–10, 12–15). Additive: registered after the existing
 * quest table, uses only TALK / KILL / DELIVER, and never replaces an existing quest id.
 * <p>
 * Who offers, who receives and the soft account-level gates live in {@link #STEPS}; the click
 * routing in {@link StoryFlow} reads the same table.
 */
public final class LullabyQuests {

    /**
     * One story step. {@code talk} = TALK target (completes on that NPC), {@code turnIn} = the NPC
     * who takes the DELIVER items, {@code level} = soft Aetherion Level gate (0 = none).
     */
    public record Step(String id, String beat, String giver, String talk, String turnIn, int level) {
    }

    public static final String OLD_TALE = "lullaby_old_tale";
    public static final String GEAR = "lullaby_gear";
    public static final String INSIGNIA = "lullaby_insignia";
    public static final String SHORT_TOLL = "lullaby_short_toll";
    public static final String OLD_SEAL = "lullaby_old_seal";
    public static final String LETTERS = "lullaby_letters";
    public static final String THIRD_ANCHOR = "lullaby_third_anchor";
    public static final String FORGOTTEN = "lullaby_forgotten";
    public static final String SILENT_ROAD = "lullaby_silent_road";
    // Act III — the Exile (mmo-c, Runde 7). Doors live in Core (ExileDoors); these give each door a reason.
    public static final String FERRY = "lullaby_ferry";
    public static final String CAPTAIN = "lullaby_captain";
    public static final String HEART = "lullaby_heart";
    public static final String DOLL = "lullaby_doll";
    public static final String SEED = "lullaby_seed";

    /** The exile island world (TALK on the Ferryman only counts over there). */
    public static final String EXILE_WORLD = "aetherion_exile";

    /** Sister Aurel (AetherionHub Origin cast). Quests only receives her TALK through StoryAccess. */
    public static final String BELLKEEPER = "bellkeeper";

    public static final List<Step> STEPS = List.of(
            new Step(OLD_TALE, "old_tale", "town_crier", "berit", null, 0),
            new Step(GEAR, "gear", "berit", null, "ysolde", 0),
            new Step(INSIGNIA, "insignia", "ysolde", null, "ysolde", 0),
            new Step(SHORT_TOLL, "short_toll", "ysolde", BELLKEEPER, null, 0),
            new Step(OLD_SEAL, "old_seal", "isle_clerk", "ysolde", null, 20),
            new Step(LETTERS, "letters", "ysolde", null, "ysolde", 30),
            new Step(THIRD_ANCHOR, "third_anchor", "ysolde", "hesper", null, 40),
            new Step(FORGOTTEN, "forgotten", "nell", "berit", null, 50),
            new Step(SILENT_ROAD, "silent_road", "ysolde", null, "ysolde", 60),
            new Step(FERRY, "ferry", "ferryman", "ferryman", null, 75),
            new Step(CAPTAIN, "captain", "ferryman", null, "exile_sentry", 0),
            new Step(HEART, "heart", "orrin", null, "exile_usher", 0),
            new Step(DOLL, "doll", "exile_usher", null, "exile_usher", 0),
            new Step(SEED, "seed", "exile_gardener", null, "exile_gardener", 0)
    );

    private LullabyQuests() {
    }

    public static Step step(String questId) {
        if (questId == null) {
            return null;
        }
        String id = questId.toLowerCase(Locale.ROOT);
        for (Step step : STEPS) {
            if (step.id().equals(id)) {
                return step;
            }
        }
        return null;
    }

    public static boolean isLullaby(String questId) {
        return step(questId) != null;
    }

    /** The step before {@code step}, or null for the first one. */
    public static Step previous(Step step) {
        int index = STEPS.indexOf(step);
        return index <= 0 ? null : STEPS.get(index - 1);
    }

    public static Step next(Step step) {
        int index = STEPS.indexOf(step);
        return index < 0 || index + 1 >= STEPS.size() ? null : STEPS.get(index + 1);
    }

    public static void register(QuestManager questManager) {
        if (questManager == null) {
            return;
        }

        quest(questManager, OLD_TALE, "An Old Tale",
                "Hollis says Mother Berit tells the tale of the mad king best. "
                        + "Find her cottage at the edge of the Capital and listen.",
                List.of(new Objective(ObjectiveType.TALK, "berit", 1)),
                75, 60);

        quest(questManager, GEAR, "The Humming Deep",
                "The deep tunnels of the Shabby Mine hum Berit's lullaby. Kill the Hollow Lurker, "
                        + "then bring what it guards to Ysolde Thorne at the Archive.",
                List.of(new Objective(ObjectiveType.KILL, "hollow_lurker", 1),
                        new Objective(ObjectiveType.DELIVER, "lore_music_box_gear", 1)),
                400, 500);

        quest(questManager, INSIGNIA, "A Guard's Badge",
                "Ysolde wants proof the king had a guard. Kill Skuldugery, the burning archer, "
                        + "and bring his Sun Guard Insignia to the Archive.",
                List.of(new Objective(ObjectiveType.KILL, "skuldugery", 1),
                        new Objective(ObjectiveType.DELIVER, "lore_sun_guard_insignia", 1)),
                600, 800);

        quest(questManager, SHORT_TOLL, "Counting Bells",
                "Ask Sister Aurel, keeper of the Seven Bells, what she has been counting.",
                List.of(new Objective(ObjectiveType.TALK, BELLKEEPER, 1)),
                150, 150);

        quest(questManager, OLD_SEAL, "The Old Seal",
                "Your island papers carry an old seal: Kingdom of Eldervale. "
                        + "Take the question to Ysolde Thorne at the Archive.",
                List.of(new Objective(ObjectiveType.TALK, "ysolde", 1)),
                300, 300);

        quest(questManager, LETTERS, "Letters Signed M",
                "Find three sealed letters signed 'M' in world chests and bring them to Ysolde at the Archive.",
                List.of(new Objective(ObjectiveType.DELIVER, "lore_maelis_letter_1", 1),
                        new Objective(ObjectiveType.DELIVER, "lore_maelis_letter_2", 1),
                        new Objective(ObjectiveType.DELIVER, "lore_maelis_letter_3", 1)),
                800, 900);

        quest(questManager, THIRD_ANCHOR, "The Third Anchor",
                "Go down to the camp under the Fallen Anchor and ask Hesper Kael about the third anchor.",
                List.of(new Objective(ObjectiveType.TALK, "hesper", 1)),
                600, 700);

        quest(questManager, FORGOTTEN, "The Dark Window",
                "Nell says Mother Berit forgot to light her lamp. Check on Berit at her cottage.",
                List.of(new Objective(ObjectiveType.TALK, "berit", 1)),
                500, 600);

        quest(questManager, SILENT_ROAD, "The Silent Road",
                "Kill the Pathwarden on the sealed King's Road and bring the writ he carries "
                        + "to Ysolde at the Archive.",
                List.of(new Objective(ObjectiveType.KILL, "pathwarden", 1),
                        new Objective(ObjectiveType.DELIVER, "lore_sealed_writ_silent_year", 1)),
                1500, 2000);

        // ---------------------------------------------------------------- Act III · the Exile
        quest(questManager, FERRY, "Across the Veil",
                "Board the King's Ferry at the Veil Quay. Cross. Then speak to the Ferryman "
                        + "on the other side.",
                List.of(new Objective(ObjectiveType.TALK, "ferryman", 1)),
                1000, 1200);

        Quest captain = quest(questManager, CAPTAIN, "The Captain's Fall",
                "Something burns in the crater behind the Iron Ring. Wake it at Caedmon's sword "
                        + "and put it out. The Sentry at the Ring wants to know.",
                List.of(new Objective(ObjectiveType.KILL, "hollow_sun", 1)),
                2500, 3000);
        captain.setWaypoint(EXILE_WORLD, 73.5, 120.0, -69.5);

        Quest heart = quest(questManager, HEART, "A Heart in a Machine",
                "Orrin keeps a dying star beating in the Observatory. Step through his ring, "
                        + "end the Herald and the Requiem, then tell the Usher at the Little Theatre.",
                List.of(new Objective(ObjectiveType.KILL, "helios_requiem", 1)),
                4000, 5000);
        heart.setWaypoint(EXILE_WORLD, -126.5, 157.0, -165.5);

        Quest doll = quest(questManager, DOLL, "The Last Performance",
                "Wind the music box on Elin's stage. Sit through the performance. "
                        + "Then go back out to the Usher.",
                List.of(new Objective(ObjectiveType.KILL, "hanging_saint", 1)),
                4000, 5000);
        doll.setWaypoint(EXILE_WORLD, -35.5, 146.0, 69.5);

        Quest seed = quest(questManager, SEED, "What the Seed Ate",
                "Go down through the Maw to the Last Seed. Get past the Unbroken, end Nihil, "
                        + "and come back up to the Gardener's Boy.",
                List.of(new Objective(ObjectiveType.KILL, "world_eater", 1)),
                5000, 6000);
        seed.setWaypoint(EXILE_WORLD, -127.5, 135.0, 84.5);
    }

    private static Quest quest(QuestManager questManager, String id, String title, String description,
                               List<Objective> objectives, int coins, int xp) {
        Step step = step(id);
        Quest quest = new Quest(id, title, description);
        quest.setServiceId(step.giver());
        // TALK steps finish on the target; DELIVER steps hand in at the turn-in NPC.
        quest.setTurnInNpcId(step.turnIn() != null ? step.turnIn() : step.talk());
        if (step.level() > 0) {
            quest.requireAccountLevel(step.level());
        }
        for (Objective objective : objectives) {
            quest.addObjective(objective);
        }
        quest.addReward(new Reward("Coins", coins));
        quest.addReward(new Reward("XP", xp));
        questManager.registerQuest(quest);
        return quest;
    }
}
