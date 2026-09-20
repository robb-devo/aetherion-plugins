package de.aetherion.quests;


import de.aetherion.quests.chest.ExploreChestService;
import de.aetherion.quests.chest.MerchantChestService;
import de.aetherion.quests.command.QuestCommand;
import de.aetherion.quests.command.QuestNPCCommand;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.data.PlayerQuestStorage;
import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.feedback.QuestFeedback;
import de.aetherion.quests.listener.ExploreChestListener;
import de.aetherion.quests.listener.LivingNpcInteractListener;
import de.aetherion.quests.listener.MerchantChestListener;
import de.aetherion.quests.listener.NpcAnchorListener;
import de.aetherion.quests.listener.NpcGuardListener;
import de.aetherion.quests.listener.NpcListener;
import de.aetherion.quests.listener.PlayerJoinListener;
import de.aetherion.quests.listener.QuestObjectiveListener;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.placeholder.QuestPlaceholderExpansion;
import de.aetherion.quests.quest.QuestRegistry;
import de.aetherion.quests.service.QuestNPCSpawnService;
import de.aetherion.quests.ui.EgonBriefingGUI;
import de.aetherion.quests.ui.EgonHintParticles;
import de.aetherion.quests.ui.LumberjackHintParticles;
import de.aetherion.quests.ui.QuestCompass;
import de.aetherion.quests.ui.QuestMarkerManager;
import de.aetherion.quests.ui.QuestProgressDisplay;
import de.aetherion.quests.ui.TutorialQuestTrail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;


public final class AetherionQuests extends JavaPlugin {


    private static AetherionQuests instance;


    private QuestManager questManager;

    private NPCDataStorage npcDataStorage;

    private PlayerQuestStorage playerQuestStorage;

    private DialogManager dialogManager;

    private QuestFeedback questFeedback;

    private QuestMarkerManager markerManager;

    private QuestCompass questCompass;

    private MerchantChestService merchantChests;

    private ExploreChestService exploreChests;

    private EgonHintParticles egonHintParticles;

    private LumberjackHintParticles lumberjackHintParticles;

    private TutorialQuestTrail tutorialQuestTrail;

    private LivingNpcService livingNpcService;

    private de.aetherion.quests.editor.NpcEditor npcEditor;

    private de.aetherion.core.api.QuestProgressAccess questAccess;



    @Override
    public void onEnable() {


        instance = this;



        /*
         * =========================================================
         * DATA STORAGE
         * =========================================================
         */

        npcDataStorage =
                new NPCDataStorage(
                        getDataFolder()
                );


        playerQuestStorage =
                new PlayerQuestStorage(
                        this,
                        getDataFolder()
                );



        /*
         * =========================================================
         * QUEST MANAGER
         * =========================================================
         */

        questManager =
                new QuestManager(
                        playerQuestStorage
                );


        questFeedback =
                new QuestFeedback(
                        this
                );


        markerManager =
                new QuestMarkerManager(
                        this,
                        questManager
                );


        questCompass =
                new QuestCompass(
                        this,
                        questManager
                );

        questCompass.start();

        egonHintParticles =
                new EgonHintParticles(
                        this,
                        questManager
                );
        egonHintParticles.start();

        lumberjackHintParticles = new LumberjackHintParticles(this);
        lumberjackHintParticles.start();

        tutorialQuestTrail = new TutorialQuestTrail(this, questManager);
        tutorialQuestTrail.start();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new QuestPlaceholderExpansion(this, questCompass).register();
            getLogger().info("PlaceholderAPI compass placeholders registered.");
        } else {
            getLogger().warning(
                    "PlaceholderAPI not found. TAB/DeluxeHub cannot show the quest arrow. "
                            + "The bossbar arrow still works."
            );
        }



        /*
         * =========================================================
         * DIALOG MANAGER
         * =========================================================
         */

        dialogManager =
                new DialogManager(
                        this,
                        questManager
                );

        new EgonBriefingGUI(this);



        /*
         * =========================================================
         * QUEST COMMAND
         * =========================================================
         */

        if (getCommand("aquest") != null) {

            getCommand("aquest").setExecutor(
                    new QuestCommand(
                            questManager
                    )
            );

        }



        /*
         * =========================================================
         * QUEST NPC COMMAND
         * =========================================================
         */

        if (getCommand("questnpc") != null) {

            getCommand("questnpc").setExecutor(
                    new QuestNPCCommand()
            );

        }

        npcEditor = new de.aetherion.quests.editor.NpcEditor(this);
        npcEditor.enable();

        de.aetherion.quests.lang.LangMenu.register(this);
        de.aetherion.quests.lang.LangPack.reload();
        if (getCommand("language") != null) {
            de.aetherion.quests.command.LanguageCommand languageCommand =
                    new de.aetherion.quests.command.LanguageCommand();
            getCommand("language").setExecutor(languageCommand);
            getCommand("language").setTabCompleter(languageCommand);
        }



        /*
         * =========================================================
         * QUESTS REGISTRIEREN
         * =========================================================
         */

        QuestRegistry.registerAll(
                questManager
        );
        if (npcEditor != null) {
            npcEditor.loadEditorQuests();
        }



        /*
         * =========================================================
         * QUEST NPCs REGISTRIEREN
         * =========================================================
         *
         * Die logischen NPC-Definitionen werden immer zuerst
         * registriert.
         *
         * Entity-UUIDs gehören NICHT zur persistenten Definition.
         */

        QuestNPCRegistry.registerAll();



        /*
         * =========================================================
         * GESPEICHERTE NPC-DATEN LADEN
         * =========================================================
         *
         * Es werden keine alten Entity-UUIDs übernommen.
         *
         * Die gespeicherten Positionsdaten bleiben in der
         * NPCDataStorage erhalten.
         */

        npcDataStorage.loadNPCs();



        /*
         * =========================================================
         * NPC LISTENER
         * =========================================================
         */

        livingNpcService = new LivingNpcService(this);

        NpcListener npcListener = new NpcListener(
                questManager,
                dialogManager
        );

        Bukkit.getPluginManager().registerEvents(
                npcListener,
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new de.aetherion.quests.listener.CoalHarbourHintListener(this, questManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new de.aetherion.quests.listener.HarbourOnboardingGate(this, questManager),
                this
        );

        LivingNpcInteractListener.register(
                this,
                npcListener,
                livingNpcService
        );

        Bukkit.getPluginManager().registerEvents(
                new NpcAnchorListener(),
                this
        );

        new NpcGuardListener(this);



        /*
         * =========================================================
         * QUEST OBJECTIVE LISTENER
         * =========================================================
         */

        Bukkit.getPluginManager().registerEvents(
                new QuestObjectiveListener(
                        questManager
                ),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new de.aetherion.quests.listener.VexDeathHintListener(this, questManager),
                this
        );
        de.aetherion.quests.listener.SurveyorMineHintListener surveyorHint =
                new de.aetherion.quests.listener.SurveyorMineHintListener(this);
        Bukkit.getPluginManager().registerEvents(surveyorHint, this);
        surveyorHint.start();

        de.aetherion.quests.listener.ForageHarbourHintListener forageHarbourHint =
                new de.aetherion.quests.listener.ForageHarbourHintListener(this);
        Bukkit.getPluginManager().registerEvents(forageHarbourHint, this);
        forageHarbourHint.start();

        merchantChests = new MerchantChestService(this);
        Bukkit.getPluginManager().registerEvents(
                new MerchantChestListener(merchantChests),
                this
        );
        exploreChests = new ExploreChestService(this);
        Bukkit.getPluginManager().registerEvents(
                new ExploreChestListener(exploreChests),
                this
        );



        /*
         * =========================================================
         * PLAYER JOIN LISTENER
         * =========================================================
         */

        Bukkit.getPluginManager().registerEvents(
                new PlayerJoinListener(
                        questManager
                ),
                this
        );



        /*
         * =========================================================
         * NPC RESTORE
         * =========================================================
         *
         * Der Restore wird um 40 Ticks (~2 Sekunden) verzögert.
         *
         * Grund:
         *
         * Mit nur 1 Tick Verzögerung sind Chunks in der Nähe
         * der gespeicherten NPC-Position teilweise noch nicht
         * vollständig geladen.
         *
         * 40 Ticks geben World/Chunk/Entity-Systemen Zeit,
         * vollständig zu laden, bevor vorhandene NPCs gesucht
         * werden.
         */

        // Restore after chunks settle. Decide dungeon-role late — Quests may enable before Dungeons.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (isDungeonBackend()) {
                getLogger().info("Dungeon backend detected — skipping quest NPC restore.");
                return;
            }
            restoreQuestNpcs();
            if (npcEditor != null) {
                npcEditor.restore();
            }
        }, 100L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (isDungeonBackend()) {
                return;
            }
            new QuestNPCSpawnService().ensureAllUnique();
            if (markerManager != null) {
                markerManager.refreshAll();
            }
        }, 200L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (merchantChests != null) {
                merchantChests.restoreAll();
            }
            if (exploreChests != null) {
                exploreChests.restoreAll();
            }
        }, 50L);



        /*
         * =========================================================
         * ENABLED
         * =========================================================
         */

        getLogger().info(
                "AetherionQuests enabled!"
        );
        questAccess = new de.aetherion.quests.api.QuestProgressAccessImpl(this);
        de.aetherion.core.api.AetherServices.registerQuests(questAccess);

    }



    private boolean isDungeonBackend() {
        org.bukkit.plugin.Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (dungeons == null || !dungeons.isEnabled()) {
            return false;
        }
        return "dungeon".equalsIgnoreCase(dungeons.getConfig().getString("role", "hub"));
    }
    private void restoreQuestNpcs() {
        new QuestNPCSpawnService().restoreAllNPCs();
        if (markerManager != null) {
            markerManager.refreshAll();
        }
        comfortLumberjackCamp();
    }

    /** Lights + keeps hostiles off the forager camp. */
    private void comfortLumberjackCamp() {
        if (npcDataStorage == null) {
            return;
        }
        Location at = npcDataStorage.getSavedLocation("lumberjack");
        if (at == null || at.getWorld() == null) {
            return;
        }
        int placed = de.aetherion.quests.npc.NpcCampComfort.lightCamp(at, 18, 3, 8, 12);
        if (placed > 0) {
            getLogger().info("Lumberjack camp: placed " + placed + " soft lights.");
        }
    }



    @Override
    public void onDisable() {

        if (markerManager != null) {
            markerManager.shutdown();
        }

        if (questCompass != null) {
            questCompass.shutdown();
        }

        if (egonHintParticles != null) {
            egonHintParticles.shutdown();
        }

        if (lumberjackHintParticles != null) {
            lumberjackHintParticles.shutdown();
        }

        if (tutorialQuestTrail != null) {
            tutorialQuestTrail.shutdown();
        }

        QuestProgressDisplay.removeAll();

        if (merchantChests != null) {
            merchantChests.shutdown();
            merchantChests.save();
        }
        if (exploreChests != null) {
            exploreChests.shutdown();
            exploreChests.save();
        }

        if (playerQuestStorage != null) {
            playerQuestStorage.flush();
        }

        if (npcEditor != null) {
            npcEditor.disable();
        }

        if (questAccess != null) {
            de.aetherion.core.api.AetherServices.clearQuests(questAccess);
            questAccess = null;
        }

        getLogger().info(
                "AetherionQuests disabled!"
        );

    }



    public static AetherionQuests getInstance() {

        return instance;

    }



    public QuestManager getQuestManager() {

        return questManager;

    }



    public NPCDataStorage getNpcDataStorage() {

        return npcDataStorage;

    }



    public PlayerQuestStorage getPlayerQuestStorage() {

        return playerQuestStorage;

    }



    public DialogManager getDialogManager() {

        return dialogManager;

    }


    public QuestFeedback getQuestFeedback() {

        return questFeedback;

    }


    public QuestMarkerManager getMarkerManager() {

        return markerManager;

    }


    public QuestCompass getQuestCompass() {

        return questCompass;

    }


    public MerchantChestService getMerchantChests() {
        return merchantChests;
    }

    public ExploreChestService getExploreChests() {
        return exploreChests;
    }


    public LivingNpcService getLivingNpcService() {
        return livingNpcService;
    }

    public de.aetherion.quests.editor.NpcEditor getNpcEditor() {
        return npcEditor;
    }


    public boolean despawnQuestNpc(String npcId) {
        return new QuestNPCSpawnService().unload(npcId);
    }


    public boolean despawnQuestNpcEntity(org.bukkit.entity.Entity entity) {
        return new QuestNPCSpawnService().despawnEntity(entity);
    }

}