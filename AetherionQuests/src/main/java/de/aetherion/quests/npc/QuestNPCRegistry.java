package de.aetherion.quests.npc;


import de.aetherion.quests.model.Quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;


public class QuestNPCRegistry {


    private static final Map<String, QuestNPC> npcs =
            new LinkedHashMap<>();



    public static void registerNPC(
            QuestNPC npc
    ) {

        npcs.put(
                npc.getId(),
                npc
        );

    }



    public static QuestNPC getNPC(
            String id
    ) {

        if (id == null) {
            return null;
        }

        QuestNPC npc = npcs.get(id);
        if (npc != null) {
            return npc;
        }

        return npcs.get(id.toLowerCase());

    }



    public static boolean exists(
            String id
    ) {

        return npcs.containsKey(id);

    }



    public static Map<String, QuestNPC> getAll() {

        return npcs;

    }



    public static void removeNPC(
            String id
    ) {

        npcs.remove(id);

    }



    public static void removeNPCEntity(
            String id
    ) {


        QuestNPC npc =
                npcs.get(id);


        if (npc == null) {

            return;

        }


        if (npc.getEntityId() != null) {


            Entity entity =
                    Bukkit.getEntity(
                            npc.getEntityId()
                    );


            if (entity != null) {

                entity.remove();

            }

        }


        npcs.remove(id);

    }



    public static QuestNPC getNPCByEntityId(
            String entityId
    ) {


        for (QuestNPC npc :
                npcs.values()) {


            if (npc.getEntityId() != null
                    && npc.getEntityId()
                    .toString()
                    .equals(entityId)) {

                return npc;

            }

        }


        return null;

    }



    public static QuestNPC findForQuest(Quest quest) {

        if (quest == null) {
            return null;
        }

        QuestNPC byService = null;

        for (QuestNPC npc : npcs.values()) {

            if (npc == null) {
                continue;
            }

            if (npc.getQuestId() != null
                    && !npc.getQuestId().isBlank()
                    && npc.getQuestId().equalsIgnoreCase(quest.getId())) {
                return npc;
            }

            if (quest.hasService()
                    && npc.getServiceId() != null
                    && quest.getServiceId().equalsIgnoreCase(npc.getServiceId())) {
                byService = npc;
            }
        }

        return byService;
    }


    public static void registerAll() {


        npcs.clear();



        /*
         * =========================================================
         * QUARTERMASTER
         * =========================================================
         *
         * Quest NPC.
         *
         * Verantwortlich für den Einstieg in die
         * Beginner-Questline.
         *
         * Aktuelle Quest:
         *
         * forge_coal
         */

        QuestNPC quartermaster =
                new QuestNPC(
                        "quartermaster",
                        "Quartermaster",
                        NPCType.QUEST,
                        "forge_coal",
                        "quartermaster_intro",
                        null
                );



        /*
         * =========================================================
         * EGON THE EQUIPPER
         * =========================================================
         *
         * Quest NPC.
         *
         * Verantwortlich für den Gear-/Equipment-Teil
         * der Beginner-Questline.
         *
         * Aktuelle Quest:
         *
         * welcome_aboard
         */

        QuestNPC egon =
                new QuestNPC(
                        "egon",
                        "Egon the Equipper",
                        NPCType.QUEST,
                        "welcome_aboard",
                        "egon_intro",
                        null
                );



        /*
         * =========================================================
         * HUNTER
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.KILL
         *
         * Aktuelle Quest:
         *
         * first_hunt
         */

        QuestNPC hunter =
                new QuestNPC(
                        "hunter",
                        "Hunter",
                        NPCType.QUEST,
                        "first_hunt",
                        "hunter_intro",
                        "hunter"
                );



        /*
         * =========================================================
         * LUMBERJACK
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.BREAK
         *
         * Aktuelle Quest:
         *
         * gather_wood
         */

        QuestNPC lumberjack =
                new QuestNPC(
                        "lumberjack",
                        "Lumberjack",
                        NPCType.QUEST,
                        "gather_wood",
                        "lumberjack_intro",
                        "lumberjack"
                );



        /*
         * =========================================================
         * FARMER
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.HARVEST
         *
         * Aktuelle Quest:
         *
         * farm_hand
         */

        QuestNPC farmer =
                new QuestNPC(
                        "farmer",
                        "Farmer",
                        NPCType.QUEST,
                        "farm_hand",
                        "farmer_intro",
                        "farmer"
                );



        /*
         * =========================================================
         * CRAFTSMAN
         * =========================================================
         *
         * No quest — unlocks crafting/recipes, wants to see a Mining Pickaxe,
         * then gifts an Emerald Booster once.
         */

        QuestNPC craftsman =
                new QuestNPC(
                        "craftsman",
                        "Craftsman",
                        NPCType.FLAVOR,
                        "",
                        "craftsman_intro",
                        "craftsman"
                );



        /*
         * =========================================================
         * COLLECTOR
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.MINE
         *
         * Aktuelle Quest:
         *
         * shiny_things
         */

        QuestNPC collector =
                new QuestNPC(
                        "collector",
                        "Collector",
                        NPCType.QUEST,
                        "shiny_things",
                        "collector_intro",
                        "collector"
                );



        /*
         * =========================================================
         * FISHER
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.FISH
         *
         * Aktuelle Quest:
         *
         * a_good_catch
         */

        QuestNPC fisher =
                new QuestNPC(
                        "fisher",
                        "Tackle",
                        NPCType.QUEST,
                        "a_good_catch",
                        "fisher_intro",
                        "fisher"
                );

        QuestNPC fishmonger =
                new QuestNPC(
                        "fishmonger",
                        "Fishmonger",
                        NPCType.FLAVOR,
                        "",
                        "fishmonger_intro",
                        ""
                );

        QuestNPC foreman =
                new QuestNPC(
                        "foreman",
                        "Shaft Foreman",
                        NPCType.QUEST,
                        "first_shift",
                        "foreman_intro",
                        "foreman"
                );

        QuestNPC surveyor =
                new QuestNPC(
                        "surveyor",
                        "Surveyor",
                        NPCType.FLAVOR,
                        null,
                        "surveyor_intro",
                        "surveyor"
                );



        /*
         * =========================================================
         * MERCHANT (Coin Desk)
         * =========================================================
         *
         * Flavor only — talking activates world/sample chests.
         * No quest popup.
         */

        QuestNPC merchant =
                new QuestNPC(
                        "merchant",
                        "Merchant",
                        NPCType.FLAVOR,
                        "",
                        "merchant_intro",
                        "merchant"
                );



        /*
         * =========================================================
         * LARK
         * =========================================================
         *
         * Service NPC für:
         *
         * ObjectiveType.CATCH
         *
         * Aktuelle Quest:
         *
         * pocket_zoo
         */

        QuestNPC lark =
                new QuestNPC(
                        "lark",
                        "Lark",
                        NPCType.QUEST,
                        "pocket_zoo",
                        "lark_intro",
                        "lark"
                );



        /*
         * =========================================================
         * NPCs REGISTRIEREN
         * =========================================================
         */

        registerNPC(
                quartermaster
        );


        registerNPC(
                egon
        );


        registerNPC(
                hunter
        );


        registerNPC(
                lumberjack
        );


        registerNPC(
                farmer
        );


        registerNPC(
                craftsman
        );


        registerNPC(
                collector
        );


        registerNPC(
                fisher
        );

        registerNPC(
                fishmonger
        );

        registerNPC(
                foreman
        );

        registerNPC(
                surveyor
        );


        registerNPC(
                merchant
        );


        registerNPC(
                lark
        );


        /*
         * =========================================================
         * NERVOUS MINER
         * =========================================================
         *
         * First boss quest: Hollow Lurker.
         */

        QuestNPC miner =
                new QuestNPC(
                        "miner",
                        "Nervous Miner",
                        NPCType.QUEST,
                        "those_sounds",
                        "miner_intro",
                        "miner"
                );

        registerNPC(
                miner
        );


        registerNPC(new QuestNPC(
                "chicken_keeper",
                "Clucksworth",
                NPCType.QUEST,
                "poultry_problem",
                "chicken_keeper_intro",
                "chicken_keeper"
        ));

        registerNPC(new QuestNPC(
                "tollkeeper",
                "Tollkeeper",
                NPCType.QUEST,
                "troll_toll",
                "tollkeeper_intro",
                "tollkeeper"
        ));

        registerNPC(new QuestNPC(
                "dockhand",
                "Brine",
                NPCType.QUEST,
                "ink_contract",
                "dockhand_intro",
                "dockhand"
        ));

        registerNPC(new QuestNPC(
                "ash_scout",
                "Cinder",
                NPCType.QUEST,
                "ash_and_arrows",
                "ash_scout_intro",
                "ash_scout"
        ));

        registerNPC(new QuestNPC(
                "colossus_scholar",
                "Riven",
                NPCType.QUEST,
                "walking_mountain",
                "colossus_scholar_intro",
                "colossus_scholar"
        ));

        registerNPC(new QuestNPC(
                "veil_priest",
                "Nyx",
                NPCType.QUEST,
                "the_veil",
                "veil_priest_intro",
                "veil_priest"
        ));

        registerNPC(new QuestNPC(
                "patch_intern",
                "Helpline",
                NPCType.QUEST,
                "open_ticket",
                "patch_intern_intro",
                "patch_intern"
        ));

        registerNPC(new QuestNPC(
                "void_janitor",
                "Mopsworth",
                NPCType.QUEST,
                "lost_and_found",
                "void_janitor_intro",
                "void_janitor"
        ));

        registerNPC(new QuestNPC(
                "fuse",
                "Fuse",
                NPCType.QUEST,
                "overtime",
                "fuse_intro",
                "fuse"
        ));

        registerNPC(new QuestNPC(
                "claims_adjuster",
                "Wormsworth",
                NPCType.QUEST,
                "denied_claim",
                "claims_adjuster_intro",
                "claims_adjuster"
        ));

        registerNPC(new QuestNPC(
                "repo_agent",
                "Audit",
                NPCType.QUEST,
                "bounced_check",
                "repo_agent_intro",
                "repo_agent"
        ));

        // Teaching cast
        registerNPC(new QuestNPC(
                "vex",
                "Sergeant Vex",
                NPCType.QUEST,
                "lesson_steel",
                "vex_intro",
                "vex"
        ));
        registerNPC(new QuestNPC(
                "booster_tutor",
                "Temper",
                NPCType.QUEST,
                "lesson_boost",
                "booster_tutor_intro",
                "booster_tutor"
        ));
        registerNPC(new QuestNPC(
                "rite_keeper",
                "Rite Warden",
                NPCType.QUEST,
                "border_rites",
                "rite_keeper_intro",
                "rite_keeper"
        ));
        // T2 Colosseum — dialog/quest wiring filled in after arena loop lands.
        registerNPC(new QuestNPC(
                "arena_proctor",
                "Proctor",
                NPCType.FLAVOR,
                null,
                "arena_proctor_intro",
                "arena_proctor"
        ));
        registerNPC(new QuestNPC(
                "ledger",
                "Miss Ledger",
                NPCType.QUEST,
                "lesson_manager",
                "ledger_intro",
                "ledger"
        ));
        registerNPC(new QuestNPC(
                "rook",
                "Rook",
                NPCType.QUEST,
                "lesson_bones",
                "rook_intro",
                "rook"
        ));

        // World cast
        registerNPC(new QuestNPC(
                "gate_warden",
                "Gate Warden",
                NPCType.QUEST,
                "closed_road",
                "gate_warden_intro",
                "gate_warden"
        ));
        registerNPC(new QuestNPC(
                "bench_cynic",
                "Bench Cynic",
                NPCType.QUEST,
                "sidewalk_survey",
                "bench_cynic_intro",
                "bench_cynic"
        ));
        registerNPC(new QuestNPC(
                "dust",
                "Dust",
                NPCType.QUEST,
                "gravel_ambition",
                "dust_intro",
                "dust"
        ));

        // Flavor + plugin-tied quests (place via Dev Menu NPC anchors)
        registerNPC(new QuestNPC(
                "fry_gossip",
                "Fry Gossip",
                NPCType.FLAVOR,
                "",
                "fry_gossip_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "dock_whisper",
                "Dock Whisper",
                NPCType.FLAVOR,
                "",
                "dock_whisper_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "larder",
                "Larder",
                NPCType.QUEST,
                "pantry_run",
                "larder_intro",
                "larder"
        ));
        registerNPC(new QuestNPC(
                "pet_scout",
                "Pet Scout",
                NPCType.QUEST,
                "pig_in_a_poke",
                "pet_scout_intro",
                "pet_scout"
        ));

        // Skill-gated gather / catch / account loops (Dev Menu NPC anchors)
        registerNPC(new QuestNPC(
                "ore_ledger",
                "Ore Ledger",
                NPCType.QUEST,
                "coal_audit",
                "ore_ledger_intro",
                "ore_ledger"
        ));
        registerNPC(new QuestNPC(
                "timber_clerk",
                "Timber Clerk",
                NPCType.QUEST,
                "stump_census",
                "timber_clerk_intro",
                "timber_clerk"
        ));
        // Forage Isle — one compressed sample of each wood type.
        registerNPC(new QuestNPC(
                "canopy_clerk",
                "Canopy Clerk",
                NPCType.QUEST,
                "canopy_sample",
                "canopy_clerk_intro",
                "canopy_clerk"
        ));
        registerNPC(new QuestNPC(
                "dock_scaler",
                "Dock Scaler",
                NPCType.QUEST,
                "scale_sample",
                "dock_scaler_intro",
                "dock_scaler"
        ));
        // Farm Isle mill / pantry — service only, no quest (Forgehand pattern).
        registerNPC(new QuestNPC(
                "root_cellar",
                "Root Cellar",
                NPCType.FLAVOR,
                "",
                "",
                ""
        ));
        registerNPC(new QuestNPC(
                "sphere_proctor",
                "Sphere Proctor",
                NPCType.QUEST,
                "bovine_brief",
                "sphere_proctor_intro",
                "sphere_proctor"
        ));
        registerNPC(new QuestNPC(
                "quarry_broker",
                "Quarry Broker",
                NPCType.QUEST,
                "guild_brick",
                "quarry_broker_intro",
                "quarry_broker"
        ));
        registerNPC(new QuestNPC(
                "slag_poet",
                "Slag Poet",
                NPCType.FLAVOR,
                "",
                "slag_poet_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "bait_theory",
                "Bait Theory",
                NPCType.FLAVOR,
                "",
                "bait_theory_intro",
                ""
        ));

        // Anker market square — ambient stalls (place near the plaza)
        registerNPC(new QuestNPC(
                "stall_crumb",
                "Crumb",
                NPCType.FLAVOR,
                "",
                "stall_crumb_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "stall_tack",
                "Tack",
                NPCType.FLAVOR,
                "",
                "stall_tack_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "stall_brine",
                "Brine",
                NPCType.FLAVOR,
                "",
                "stall_brine_intro",
                ""
        ));

        // Hub farm portal guide — place yourself (Harrow).
        registerNPC(new QuestNPC(
                "farm_isle_guide",
                "Harrow",
                NPCType.FLAVOR,
                "",
                "farm_isle_guide_intro",
                ""
        ));

        // Harbour → Forage Isle jump pad guide — place in front of the pad.
        registerNPC(new QuestNPC(
                "forage_pad_guide",
                "Twig",
                NPCType.FLAVOR,
                "",
                "forage_pad_guide_intro",
                ""
        ));

        // Eldervale mining island welcome — place on the island landing.
        registerNPC(new QuestNPC(
                "eldervale_welcome",
                "Maren",
                NPCType.FLAVOR,
                "",
                "eldervale_welcome_intro",
                ""
        ));

        // Eldervale blueprint forge — upgrade stones.
        registerNPC(new QuestNPC(
                "eldervale_upgrade",
                "Forgehand",
                NPCType.FLAVOR,
                "",
                "eldervale_upgrade_intro",
                ""
        ));

        // Hub — personal island pointer (place yourself; Manager island tab).
        registerNPC(new QuestNPC(
                "isle_clerk",
                "Deed",
                NPCType.FLAVOR,
                "",
                "isle_clerk_intro",
                ""
        ));

        // Hub dungeon portal brief (place yourself at the Capital nether portal).
        registerNPC(new QuestNPC(
                "dungeon_gate",
                "Threshold",
                NPCType.FLAVOR,
                "",
                "dungeon_gate_intro",
                ""
        ));

        // Eldervale Crystal Guide — Amethyst Mines / The Veins (Mining 30).
        registerNPC(new QuestNPC(
                "amethyst_mines_guide",
                "Crystal Guide",
                NPCType.FLAVOR,
                "",
                "amethyst_mines_guide_intro",
                ""
        ));

        // FancyNpcs living hosts — visual prototypes (villagers untouched).
        registerNPC(new QuestNPC(
                "living_test",
                "Living Test",
                NPCType.FLAVOR,
                "",
                "living_test_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "rivet",
                "Rivet",
                NPCType.FLAVOR,
                "",
                "rivet_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "bar_whisper",
                "Bar Whisper",
                NPCType.FLAVOR,
                "",
                "bar_whisper_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "vince",
                "Lucky Vince",
                NPCType.FLAVOR,
                "",
                "vince_intro",
                ""
        ));
        registerNPC(new QuestNPC(
                "liquidator",
                "Crystal Liquidator",
                NPCType.FLAVOR,
                "",
                "liquidator_intro",
                ""
        ));

    }

    public static String linkedBoss(String npcId) {
        if (npcId == null) {
            return null;
        }
        return switch (npcId.toLowerCase()) {
            case "miner" -> "Hollow Lurker";
            case "chicken_keeper" -> "McNugget";
            case "tollkeeper" -> "Bridge Troll";
            case "dockhand" -> "Squidward";
            case "ash_scout" -> "Skuldugery";
            case "colossus_scholar" -> "Aether Colossus";
            case "veil_priest" -> "Aetherion";
            case "patch_intern" -> "Sir Balthazar";
            case "void_janitor" -> "The Lobby Cleaner";
            case "fuse" -> "Sparky";
            case "claims_adjuster" -> "Baron von Wurm";
            case "repo_agent" -> "The Insolvent Wither";
            case "gate_warden" -> "Pathwarden";
            default -> null;
        };
    }

}