package de.aetherion.quests.quest;


import de.aetherion.items.skill.AetherSkill;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;


public class QuestRegistry {


    public static void registerAll(
            QuestManager questManager
    ) {


        /*
         * =========================================================
         * QUEST 1
         * WELCOME ABOARD
         * =========================================================
         *
         * Egon on Anker Harbour pier.
         * Sends the player to the Forager (lumberjack) — TALK objective.
         * Wood delivery + starter kit happen on gather_wood / Egon turn-in.
         * =========================================================
         */

        Quest welcomeAboard =
                new Quest(
                        "welcome_aboard",
                        "Welcome Aboard",
                        "Talk to the Forager up the hill from Anker Harbour."
                );


        welcomeAboard.setServiceId(
                "egon"
        );


        welcomeAboard.addObjective(
                new Objective(
                        ObjectiveType.TALK,
                        "lumberjack",
                        1
                )
        );


        // Waypoint only — XP/complete fanfare live on gather_wood / Egon turn-in.


        questManager.registerQuest(
                welcomeAboard
        );



        /*
         * =========================================================
         * QUEST 2
         * Forge Coal
         * =========================================================
         *
         * NPC:
         *
         * Quartermaster
         *
         * Aufgabe:
         *
         * 20 Coal zu ihm bringen.
         *
         * Reward:
         *
         * Emerald Booster + Mines teleport unlock (no Homestead Marker).
         *
         * Ore Ridge teleport unlocks by walking there (Hub discover),
         * not on accept.
         *
         * =========================================================
         */

        Quest forgeCoal =
                new Quest(
                        "forge_coal",
                        "Forge Coal",
                        "Bring the Quartermaster 20 Coal from Ore Ridge."
                );


        /*
         * =========================================================
         * QUEST SERVICE
         * =========================================================
         *
         * Diese Quest gehört zum Quartermaster.
         */

        forgeCoal.setServiceId(
                "quartermaster"
        );


        /*
         * =========================================================
         * OBJECTIVE
         * =========================================================
         */

        forgeCoal.addObjective(
                new Objective(
                        ObjectiveType.DELIVER,
                        "COAL",
                        20
                )
        );


        /*
         * =========================================================
         * REWARD
         * =========================================================
         */

        forgeCoal.addReward(
                new Reward(
                        "Coins",
                        60
                )
        );

        forgeCoal.addReward(
                new Reward(
                        "Emerald Booster",
                        1
                )
        );

        forgeCoal.addReward(
                new Reward(
                        "spawn:mines",
                        1
                )
        );

        forgeCoal.addReward(
                new Reward(
                        "XP",
                        70
                )
        );


        questManager.registerQuest(
                forgeCoal
        );


        /*
         * =========================================================
         * FIRST SHIFT — Shabby Mine intro → Capital hub
         * =========================================================
         */

        Quest firstShift =
                new Quest(
                        "first_shift",
                        "First Shift",
                        "Mine thirty-two ores in the Shabby Mine, then report back to the Shaft Foreman."
                );

        firstShift.setServiceId("foreman");
        firstShift.setTurnInNpcId("foreman");

        firstShift.addObjective(
                new Objective(
                        ObjectiveType.MINE,
                        "ANY_ORE",
                        32
                )
        );

        firstShift.addReward(new Reward("Coins", 80));
        firstShift.addReward(new Reward("XP", 90));

        questManager.registerQuest(firstShift);



        /*
         * =========================================================
         * HIDDEN BLUEPRINTS — retired (Surveyor is flavor / desk only)
         * =========================================================
         */

        /*
         * =========================================================
         * QUEST 3
         * FIRST HUNT
         * =========================================================
         *
         * Einfaches KILL-Objective.
         */

        Quest firstHunt =
                new Quest(
                        "first_hunt",
                        "First Hunt",
                        "Help clear the farm of a few animals."
                );


        firstHunt.setServiceId(
                "hunter"
        );


        firstHunt.addObjective(
                new Objective(
                        ObjectiveType.KILL,
                        "COW",
                        5
                )
        );
firstHunt.addReward(
                new Reward(
                        "Coins",
                        75
                )
        );


        firstHunt.addReward(
                new Reward(
                        "XP",
                        60
                )
        );


        questManager.registerQuest(
                firstHunt
        );



        /*
         * =========================================================
         * QUEST 4
         * GATHER WOOD  (harbour onboarding step 2)
         * =========================================================
         *
         * Forager teaches chop + gives Simple Axe on accept.
         * Deliver 10 oak logs back to Egon on the pier.
         * =========================================================
         */

        Quest gatherWood =
                new Quest(
                        "gather_wood",
                        "Egon's Timber Run",
                        "Chop oak with the Forager's axe, then deliver 10 logs to Egon."
                );


        gatherWood.setServiceId(
                "lumberjack"
        );
        gatherWood.setTurnInNpcId(
                "egon"
        );


        gatherWood.addObjective(
                new Objective(
                        ObjectiveType.DELIVER,
                        "OAK_LOG",
                        10
                )
        );

        gatherWood.addReward(
                new Reward(
                        "Starter Gear No Axe",
                        1
                )
        );

        gatherWood.addReward(
                new Reward(
                        "Coins",
                        60
                )
        );


        gatherWood.addReward(
                new Reward(
                        "XP",
                        80
                )
        );


        questManager.registerQuest(
                gatherWood
        );



        /*
         * =========================================================
         * QUEST 5
         * FARM HAND
         * =========================================================
         *
         * Einfaches HARVEST-Objective.
         */

        Quest farmHand =
                new Quest(
                        "farm_hand",
                        "Farm Hand",
                        "Harvest wheat at the fields. Lark keeps his collection nearby."
                );


        farmHand.setServiceId(
                "farmer"
        );


        farmHand.addObjective(
                new Objective(
                        ObjectiveType.HARVEST,
                        "WHEAT",
                        48
                )
        );
        farmHand.addReward(
                new Reward(
                        "Coins",
                        50
                )
        );


        farmHand.addReward(
                new Reward(
                        "XP",
                        50
                )
        );


        questManager.registerQuest(
                farmHand
        );



        /*
         * =========================================================
         * QUEST 6
         * A SIMPLE CRAFT (Second Recipe)
         * =========================================================
         *
         * Egon already hands out the Simple Pickaxe with starter gear.
         * Craftsman teaches the next recipe: Mining Pickaxe (wrap + coal).
         */

        Quest simpleCraft =
                new Quest(
                        "a_simple_craft",
                        "Second Recipe",
                        "Open the §agreen Recipe Book§7 in the Manager and craft a §fMining Pickaxe§7 — Simple Pickaxe in the middle, coal around it."
                );


        simpleCraft.setServiceId(
                "craftsman"
        );


        simpleCraft.addObjective(
                new Objective(
                        ObjectiveType.CRAFT,
                        "mining_pickaxe",
                        1
                )
        );
simpleCraft.addReward(
                new Reward(
                        "Coins",
                        80
                )
        );


        simpleCraft.addReward(
                new Reward(
                        "XP",
                        70
                )
        );


        questManager.registerQuest(
                simpleCraft
        );



        /*
         * =========================================================
         * QUEST 7
         * SHINY THINGS
         * =========================================================
         *
         * Einfaches MINE-Objective (ore break; inventory auto-pay has no pickup).
         */

        Quest shinyThings =
                new Quest(
                        "shiny_things",
                        "Shiny Things",
                        "Mine three diamonds."
                );


        shinyThings.setServiceId(
                "collector"
        );


        shinyThings.addObjective(
                new Objective(
                        ObjectiveType.MINE,
                        "DIAMOND",
                        3
                )
        );
shinyThings.addReward(
                new Reward(
                        "Coins",
                        100
                )
        );


        shinyThings.addReward(
                new Reward(
                        "XP",
                        90
                )
        );


        questManager.registerQuest(
                shinyThings
        );



        /*
         * =========================================================
         * QUEST 8
         * A GOOD CATCH
         * =========================================================
         *
         * Einfaches FISH-Objective.
         */

        Quest goodCatch =
                new Quest(
                        "a_good_catch",
                        "A Good Catch",
                        "Catch five fish."
                );


        goodCatch.setServiceId(
                "fisher"
        );
        goodCatch.setTurnInNpcId("fisher");


        goodCatch.addObjective(
                new Objective(
                        ObjectiveType.FISH,
                        "ANY",
                        5
                )
        );
goodCatch.addReward(
                new Reward(
                        "Coins",
                        70
                )
        );


        goodCatch.addReward(
                new Reward(
                        "XP",
                        65
                )
        );

        goodCatch.addReward(
                new Reward(
                        "RAW_COD",
                        10
                )
        );


        questManager.registerQuest(
                goodCatch
        );


        /*
         * =========================================================
         * DOCK PASS — Fishmonger gate → Tackle lesson
         * =========================================================
         */

        Quest dockPass =
                new Quest(
                        "dock_pass",
                        "Dock Pass",
                        "Get a shop pass from Tackle on the dock, then return to the Fishmonger."
                );

        dockPass.setServiceId("");
        dockPass.setTurnInNpcId("");

        dockPass.addObjective(
                new Objective(
                        ObjectiveType.DELIVER,
                        "dock_shop_pass",
                        1
                )
        );

        dockPass.addReward(new Reward("Coins", 40));
        dockPass.addReward(new Reward("XP", 45));

        questManager.registerQuest(dockPass);


        /*
         * =========================================================
         * QUEST 9
         * OPEN UP
         * =========================================================
         *
         * Einfaches USE-Objective.
         */

        Quest openUp =
                new Quest(
                        "open_up",
                        "Open Up",
                        "Open the merchant's sample chest."
                );


        openUp.setServiceId(
                "merchant"
        );


        openUp.addObjective(
                new Objective(
                        ObjectiveType.USE,
                        "MERCHANT_CHEST",
                        1
                )
        );
openUp.addReward(
                new Reward(
                        "Coins",
                        40
                )
        );


        openUp.addReward(
                new Reward(
                        "XP",
                        45
                )
        );


        questManager.registerQuest(
                openUp
        );


        /*
         * =========================================================
         * QUEST 9b
         * POCKET ZOO
         * =========================================================
         *
         * NPC: Lark
         * Catch any pet, then turn in for rare spheres.
         */

        Quest pocketZoo =
                new Quest(
                        "pocket_zoo",
                        "Pocket Zoo",
                        "Catch a wild pet with a Catch Sphere for Lark, talk to him, then equip it in the Manager."
                );

        pocketZoo.setServiceId(
                "lark"
        );

        pocketZoo.addObjective(
                new Objective(
                        ObjectiveType.CATCH,
                        "ANY",
                        1
                )
        );
        pocketZoo.addObjective(
                new Objective(
                        ObjectiveType.USE,
                        "AETHERION_PET_MENU",
                        1
                )
        );
        pocketZoo.addObjective(
                new Objective(
                        ObjectiveType.USE,
                        "AETHER_PET",
                        1
                )
        );

        pocketZoo.addReward(
                new Reward(
                        "Rare Catch Sphere",
                        16
                )
        );

        pocketZoo.addReward(
                new Reward(
                        "Coins",
                        80
                )
        );

        pocketZoo.addReward(
                new Reward(
                        "XP",
                        75
                )
        );

        questManager.registerQuest(
                pocketZoo
        );



        /*
         * =========================================================
         * QUEST 10
         * THOSE SOUNDS
         * =========================================================
         *
         * NPC: Nervous Miner
         * Kill the Hollow Lurker, then turn in for a Homestead Marker.
         */

        Quest thoseSounds =
                new Quest(
                        "those_sounds",
                        "Those Sounds",
                        "Find the source of those sounds and slay the Hollow Lurker."
                );

        thoseSounds.setServiceId(
                "miner"
        );

        thoseSounds.addObjective(
                new Objective(
                        ObjectiveType.KILL,
                        "hollow_lurker",
                        1
                )
        );

        thoseSounds.addReward(
                new Reward(
                        "Spawn: lurker_camp",
                        1
                )
        );

        thoseSounds.addReward(
                new Reward(
                        "Coins",
                        250
                )
        );

        thoseSounds.addReward(
                new Reward(
                        "XP",
                        500
                )
        );


        questManager.registerQuest(
                thoseSounds
        );


        registerBossHunt(
                questManager,
                "poultry_problem",
                "Poultry Problem",
                "Find McNugget and put the giant chicken back in the bucket.",
                "chicken_keeper",
                "mcnugget",
                900,
                600,
                "farm"
        );

        registerBossHunt(
                questManager,
                "troll_toll",
                "Troll Toll",
                "Cross the bridge. Survive the troll. Keep the change.",
                "tollkeeper",
                "bridge_troll",
                1400,
                1000,
                "mines"
        );

        registerBossHunt(
                questManager,
                "ink_contract",
                "Ink Contract",
                "Dive for Squidward and come back with fewer tentacles attached.",
                "dockhand",
                "squidward",
                1200,
                800,
                "harbour"
        );

        registerBossHunt(
                questManager,
                "ash_and_arrows",
                "Ash and Arrows",
                "Hunt Skuldugery before the flaming volley hunts you.",
                "ash_scout",
                "skuldugery",
                2200,
                2000,
                "capital"
        );

        registerBossHunt(
                questManager,
                "walking_mountain",
                "Walking Mountain",
                "Bring down the Aether Colossus. Try not to get stepped on.",
                "colossus_scholar",
                "aether_colossus",
                3000,
                3200,
                "royal_palace"
        );

        registerBossHunt(
                questManager,
                "the_veil",
                "The Veil",
                "Challenge Aetherion in the dungeon. Entertain it, or become a footnote.",
                "veil_priest",
                "aetherion",
                5500,
                8000,
                ""
        );

        registerT2BossHunt(
                questManager,
                "open_ticket",
                "Open Ticket",
                "Close Sir Balthazar. Permanently. Support will pretend this was the plan.",
                "patch_intern",
                "sir_balthazar",
                3200,
                3500,
                "ticket_hall"
        );

        registerT2BossHunt(
                questManager,
                "lost_and_found",
                "Lost and Found",
                "Unplug the Lobby Cleaner before it files the rest of the server as trash.",
                "void_janitor",
                "lobby_cleaner",
                3200,
                3500,
                "trash_chute"
        );

        registerT2BossHunt(
                questManager,
                "overtime",
                "Overtime",
                "Clock Sparky out. The guild quarry is over budget on lightning.",
                "fuse",
                "sparky",
                3500,
                4000,
                "guild_quarry"
        );

        registerT2BossHunt(
                questManager,
                "denied_claim",
                "Denied Claim",
                "Settle Baron von Wurm. Pickaxe insurance already said no.",
                "claims_adjuster",
                "baron_von_wurm",
                3800,
                4500,
                "worm_tunnels"
        );

        registerT2BossHunt(
                questManager,
                "bounced_check",
                "Bounced Check",
                "Foreclose the Insolvent Wither. Bring the ledger back in pieces if you have to.",
                "repo_agent",
                "insolvent_wither",
                4200,
                5000,
                "collections"
        );

        /*
         * Teaching NPCs — short lessons with dry humor.
         */
        Quest lessonSteel = new Quest(
                "lesson_steel",
                "Lesson: Steel",
                "Sergeant Vex wants ten Borderlands hostiles dealt with. Permanently."
        );
        lessonSteel.setServiceId("vex");
        lessonSteel.addObjective(new Objective(ObjectiveType.KILL, "BORDERLANDS", 10));
        lessonSteel.addReward(new Reward("Coins", 120));
        lessonSteel.addReward(new Reward("XP", 100));
        questManager.registerQuest(lessonSteel);

        Quest lessonBoost = new Quest(
                "lesson_boost",
                "Lesson: Boost",
                "Temper wants one booster fused onto your gear at the Manager anvil."
        );
        lessonBoost.setServiceId("booster_tutor");
        lessonBoost.addObjective(new Objective(ObjectiveType.USE, "APPLY_BOOSTER", 1));
        lessonBoost.addReward(new Reward("Coins", 100));
        lessonBoost.addReward(new Reward("XP", 80));
        questManager.registerQuest(lessonBoost);

        Quest borderRites = new Quest(
                "border_rites",
                "Border Rites",
                "The Rite Warden wants you to ignite one Borderlands spirit at the powder altar."
        );
        borderRites.setServiceId("rite_keeper");
        borderRites.addObjective(new Objective(ObjectiveType.USE, "BORDERLANDS_RITE", 1));
        borderRites.addReward(new Reward("Coins", 140));
        borderRites.addReward(new Reward("XP", 100));
        questManager.registerQuest(borderRites);

        Quest lessonManager = new Quest(
                "lesson_manager",
                "Lesson: Skills",
                "Miss Ledger wants you to equip one skill in the Aetherion Manager."
        );
        lessonManager.setServiceId("ledger");
        lessonManager.addObjective(new Objective(ObjectiveType.USE, "AETHER_SKILL", 1));
        lessonManager.addReward(new Reward("Coins", 90));
        lessonManager.addReward(new Reward("XP", 80));
        questManager.registerQuest(lessonManager);

        Quest lessonBones = new Quest(
                "lesson_bones",
                "Lesson: Dungeons",
                "Rook wants ten bones. Call it dungeon homework without the dungeon."
        );
        lessonBones.setServiceId("rook");
        lessonBones.addObjective(new Objective(ObjectiveType.DELIVER, "BONE", 10));
        lessonBones.addReward(new Reward("Coins", 110));
        lessonBones.addReward(new Reward("XP", 90));
        questManager.registerQuest(lessonBones);

        /*
         * World NPCs — flavor that fits the map.
         */
        registerBossHunt(
                questManager,
                "closed_road",
                "Closed Road",
                "The Pathwarden is the roadblock. Move him. Or become a new landmark.",
                "gate_warden",
                "pathwarden",
                1800,
                1400,
                ""
        );

        Quest sidewalkSurvey = new Quest(
                "sidewalk_survey",
                "Sidewalk Survey",
                "The Bench Cynic needs eight rotten flesh for 'research'. Don't ask."
        );
        sidewalkSurvey.setServiceId("bench_cynic");
        sidewalkSurvey.addObjective(new Objective(ObjectiveType.DELIVER, "ROTTEN_FLESH", 8));
        sidewalkSurvey.addReward(new Reward("Coins", 80));
        sidewalkSurvey.addReward(new Reward("XP", 70));
        questManager.registerQuest(sidewalkSurvey);

        Quest gravelAmbition = new Quest(
                "gravel_ambition",
                "Gravel Ambition",
                "Dust wants thirty cobblestone. The map can wait. The road cannot."
        );
        gravelAmbition.setServiceId("dust");
        gravelAmbition.addObjective(new Objective(ObjectiveType.DELIVER, "COBBLESTONE", 30));
        gravelAmbition.addReward(new Reward("Coins", 100));
        gravelAmbition.addReward(new Reward("XP", 80));
        questManager.registerQuest(gravelAmbition);

        /*
         * Plugin-tied quests — AetherionItems / Aethermobs.
         */
        Quest pantryRun = new Quest(
                "pantry_run",
                "Pantry Run",
                "Larder wants three Compressed Wheat. Compress farm wheat, then deliver."
        );
        pantryRun.setServiceId("larder");
        pantryRun.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_wheat", 3));
        pantryRun.addReward(new Reward("Random Booster", 1));
        pantryRun.addReward(new Reward("Coins", 120));
        pantryRun.addReward(new Reward("XP", 100));
        questManager.registerQuest(pantryRun);

        Quest pigInAPoke = new Quest(
                "pig_in_a_poke",
                "Pig in a Poke",
                "Pet Scout wants proof you can catch. Bag a wild pig with a Catch Sphere."
        );
        pigInAPoke.setServiceId("pet_scout");
        pigInAPoke.addObjective(new Objective(ObjectiveType.CATCH, "pig", 1));
        pigInAPoke.addReward(new Reward("Rare Catch Sphere", 8));
        pigInAPoke.addReward(new Reward("Coins", 90));
        pigInAPoke.addReward(new Reward("XP", 85));
        questManager.registerQuest(pigInAPoke);

        /*
         * Skill-gated world loops — compress / catch / account progression.
         */
        Quest coalAudit = new Quest(
                "coal_audit",
                "Coal Audit",
                "Ore Ledger wants five Compressed Coal. Bring receipts, not excuses."
        );
        coalAudit.setServiceId("ore_ledger");
        coalAudit.requireCategory(AetherSkill.Category.MINING, 8);
        coalAudit.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_coal", 5));
        coalAudit.addReward(new Reward("Random Booster", 1));
        coalAudit.addReward(new Reward("Coins", 160));
        coalAudit.addReward(new Reward("XP", 120));
        questManager.registerQuest(coalAudit);

        Quest stumpCensus = new Quest(
                "stump_census",
                "Stump Census",
                "Timber Clerk needs five Compressed Oak Logs. Trees that paid taxes."
        );
        stumpCensus.setServiceId("timber_clerk");
        stumpCensus.requireCategory(AetherSkill.Category.FORAGING, 8);
        stumpCensus.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_oak_log", 5));
        stumpCensus.addReward(new Reward("Random Booster", 1));
        stumpCensus.addReward(new Reward("Coins", 160));
        stumpCensus.addReward(new Reward("XP", 120));
        questManager.registerQuest(stumpCensus);

        Quest canopySample = new Quest(
                "canopy_sample",
                "Canopy Sample",
                "Canopy Clerk wants one Compressed Oak, Birch, and Spruce. Isle wood only counts as proof."
        );
        canopySample.setServiceId("canopy_clerk");
        canopySample.requireCategory(AetherSkill.Category.FORAGING, 5);
        canopySample.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_oak_log", 1));
        canopySample.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_birch_log", 1));
        canopySample.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_spruce_log", 1));
        canopySample.addReward(new Reward("Random Booster", 1));
        canopySample.addReward(new Reward("Coins", 400));
        canopySample.addReward(new Reward("XP", 250));
        questManager.registerQuest(canopySample);

        Quest scaleSample = new Quest(
                "scale_sample",
                "Scale Sample",
                "Dock Scaler wants three Compressed Cod. The clipboard is already wet."
        );
        scaleSample.setServiceId("dock_scaler");
        scaleSample.requireCategory(AetherSkill.Category.FISHING, 10);
        scaleSample.addObjective(new Objective(ObjectiveType.DELIVER, "compressed_cod", 3));
        scaleSample.addReward(new Reward("Random Booster", 1));
        scaleSample.addReward(new Reward("Coins", 180));
        scaleSample.addReward(new Reward("XP", 130));
        questManager.registerQuest(scaleSample);

        Quest bovineBrief = new Quest(
                "bovine_brief",
                "Bovine Brief",
                "Sphere Proctor wants a cow in a sphere. Not milk. Not steak. Custody."
        );
        bovineBrief.setServiceId("sphere_proctor");
        bovineBrief.requireAccountLevel(5);
        bovineBrief.addObjective(new Objective(ObjectiveType.CATCH, "cow", 1));
        bovineBrief.addReward(new Reward("Rare Catch Sphere", 10));
        bovineBrief.addReward(new Reward("Coins", 140));
        bovineBrief.addReward(new Reward("XP", 110));
        questManager.registerQuest(bovineBrief);

        Quest guildBrick = new Quest(
                "guild_brick",
                "Guild Brick",
                "Quarry Broker trades one Compacted Cobblestone for club-adjacent respect."
        );
        guildBrick.setServiceId("quarry_broker");
        guildBrick.requireAccountLevel(20);
        guildBrick.addObjective(new Objective(ObjectiveType.DELIVER, "compacted_cobblestone", 1));
        guildBrick.addReward(new Reward("Random Booster", 2));
        guildBrick.addReward(new Reward("Coins", 280));
        guildBrick.addReward(new Reward("XP", 200));
        questManager.registerQuest(guildBrick);

    }


    private static void registerBossHunt(
            QuestManager questManager,
            String id,
            String title,
            String description,
            String serviceId,
            String bossId,
            int xp,
            int coins,
            String spawnId
    ) {
        Quest quest = new Quest(id, title, description);
        quest.setServiceId(serviceId);
        quest.addObjective(new Objective(ObjectiveType.KILL, bossId, 1));
        quest.addReward(new Reward("Random Booster", 2));
        quest.addReward(new Reward("Coins", coins));
        quest.addReward(new Reward("XP", xp));
        if (spawnId != null && !spawnId.isBlank()) {
            quest.addReward(new Reward("Spawn: " + spawnId, 1));
        }
        questManager.registerQuest(quest);
    }

    private static void registerT2BossHunt(
            QuestManager questManager,
            String id,
            String title,
            String description,
            String serviceId,
            String bossId,
            int xp,
            int coins,
            String spawnId
    ) {
        Quest quest = new Quest(id, title, description);
        quest.setServiceId(serviceId);
        quest.addObjective(new Objective(ObjectiveType.KILL, bossId, 1));
        quest.addReward(new Reward("Compacted Diamond Block", 2));
        quest.addReward(new Reward("Coins", coins));
        quest.addReward(new Reward("XP", xp));
        if (spawnId != null && !spawnId.isBlank()) {
            quest.addReward(new Reward("Spawn: " + spawnId, 1));
        }
        questManager.registerQuest(quest);
    }

}