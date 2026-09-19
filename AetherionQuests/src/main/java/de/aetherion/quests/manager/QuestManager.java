package de.aetherion.quests.manager;


import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.PlayerQuestData;
import de.aetherion.quests.data.PlayerQuestStorage;
import de.aetherion.quests.feedback.QuestFeedback;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.reward.QuestAetherXp;
import de.aetherion.quests.reward.Reward;
import de.aetherion.quests.reward.StarterGearReward;
import de.aetherion.quests.ui.QuestProgressDisplay;
import de.aetherion.quests.util.PlayerItems;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class QuestManager {


    private final Map<String, Quest> quests =
            new HashMap<>();


    private final Map<UUID, PlayerQuestData> playerData =
            new HashMap<>();


    /*
     * =========================================================
     * CURRENTLY TRACKED QUEST
     * =========================================================
     */

    private final Map<UUID, String> trackedQuests =
            new HashMap<>();


    private final PlayerQuestStorage storage;


    public QuestManager(
            PlayerQuestStorage storage
    ) {

        this.storage = storage;

    }


    /*
     * =========================================================
     * QUEST REGISTRIEREN
     * =========================================================
     */

    public void registerQuest(
            Quest quest
    ) {

        if (quest == null) {

            return;

        }


        quests.put(
                quest.getId(),
                quest
        );

    }


    /*
     * =========================================================
     * QUEST HOLEN
     * =========================================================
     */

    public Quest getQuest(
            String id
    ) {

        if (id == null) {

            return null;

        }


        return quests.get(id);

    }


    /*
     * =========================================================
     * ALLE QUESTS HOLEN
     * =========================================================
     */

    public Collection<Quest> getQuests() {

        return quests.values();

    }


    /*
     * =========================================================
     * PLAYER DATA LADEN
     * =========================================================
     */

    private PlayerQuestData getOrLoadPlayerData(
            Player player,
            String questId
    ) {

        UUID uuid =
                player.getUniqueId();


        PlayerQuestData data =
                playerData.get(uuid);


        if (data == null) {

            data =
                    new PlayerQuestData();

            playerData.put(
                    uuid,
                    data
            );

        }


        if (!data.hasQuest(questId)) {

            QuestState storedState =
                    storage.loadQuest(
                            uuid,
                            questId
                    );


            data.setQuestState(
                    questId,
                    storedState
            );


            storage.loadAllProgress(
                    uuid,
                    questId,
                    data
            );

        }


        return data;

    }


    /*
     * =========================================================
     * QUEST TRACKEN
     * =========================================================
     */

    public void trackQuest(
            Player player,
            Quest quest
    ) {

        if (
                player == null
                        || quest == null
        ) {

            return;

        }


        QuestState state =
                getQuestState(
                        player,
                        quest
                );


        if (
                state != QuestState.ACTIVE
                        && state != QuestState.READY
        ) {

            return;

        }


        trackedQuests.put(
                player.getUniqueId(),
                quest.getId()
        );

    }


    /*
     * =========================================================
     * TRACKING ENTFERNEN
     * =========================================================
     */

    public void untrackQuest(
            Player player
    ) {

        if (player == null) {

            return;

        }


        trackedQuests.remove(
                player.getUniqueId()
        );

    }


    /*
     * =========================================================
     * GET TRACKED QUEST ID
     * =========================================================
     */

    public String getTrackedQuestId(
            Player player
    ) {

        if (player == null) {

            return null;

        }


        return trackedQuests.get(
                player.getUniqueId()
        );

    }


    /*
     * =========================================================
     * GET TRACKED QUEST
     * =========================================================
     */

    public Quest getTrackedQuest(
            Player player
    ) {

        if (player == null) {

            return null;

        }


        UUID uuid =
                player.getUniqueId();


        String questId =
                trackedQuests.get(
                        uuid
                );


        if (
                questId == null
                        || questId.isBlank()
        ) {

            return null;

        }


        Quest quest =
                getQuest(
                        questId
                );


        if (quest == null) {

            trackedQuests.remove(
                    uuid
            );

            return null;

        }


        QuestState state =
                getQuestState(
                        player,
                        quest
                );


        if (
                state != QuestState.ACTIVE
                        && state != QuestState.READY
        ) {

            trackedQuests.remove(
                    uuid
            );

            return null;

        }


        return quest;

    }


    /** Any ACTIVE/READY quest for this player (for accept-conflict UI). */
    public Quest findActiveOrReadyQuest(Player player) {
        if (player == null) {
            return null;
        }
        Quest tracked = getTrackedQuest(player);
        if (tracked != null) {
            return tracked;
        }
        for (Quest quest : quests.values()) {
            if (quest == null) {
                continue;
            }
            QuestState state = getQuestState(player, quest);
            if (state == QuestState.ACTIVE || state == QuestState.READY) {
                return quest;
            }
        }
        return null;
    }


    /*
     * =========================================================
     * PRÜFEN, OB QUEST GETRACKT WIRD
     * =========================================================
     */

    public boolean isQuestTracked(
            Player player,
            Quest quest
    ) {

        if (
                player == null
                        || quest == null
        ) {

            return false;

        }


        String trackedQuestId =
                getTrackedQuestId(
                        player
                );


        if (trackedQuestId == null) {

            return false;

        }


        return trackedQuestId.equalsIgnoreCase(
                quest.getId()
        );

    }


    /*
     * =========================================================
     * NÄCHSTE SINNVOLLE QUEST FINDEN
     * =========================================================
     */

    public Quest findNextTrackedQuest(
            Player player
    ) {

        if (player == null) {

            return null;

        }


        for (
                Quest quest :
                quests.values()
        ) {

            if (quest == null) {

                continue;

            }


            QuestState state =
                    getQuestState(
                            player,
                            quest
                    );


            if (state == QuestState.READY) {

                return quest;

            }

        }


        for (
                Quest quest :
                quests.values()
        ) {

            if (quest == null) {

                continue;

            }


            QuestState state =
                    getQuestState(
                            player,
                            quest
                    );


            if (state == QuestState.ACTIVE) {

                return quest;

            }

        }


        return null;

    }


    /*
     * =========================================================
     * QUEST STARTEN
     * =========================================================
     */

    public void startQuest(
            Player player,
            Quest quest
    ) {

        if (
                player == null
                        || quest == null
        ) {

            return;

        }


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        quest.getId()
                );


        QuestState currentState =
                data.getQuestState(
                        quest.getId()
                );


        if (currentState == QuestState.COMPLETED) {

            return;

        }


        String gateFail = de.aetherion.quests.util.QuestSkillGate.failReason(player, quest);
        if (gateFail == null && "lesson_manager".equalsIgnoreCase(quest.getId())) {
            gateFail = de.aetherion.quests.util.QuestStoryGate.ledgerFailReason(player, this);
        }
        if (gateFail == null && "border_rites".equalsIgnoreCase(quest.getId())) {
            gateFail = de.aetherion.quests.util.QuestStoryGate.riteKeeperFailReason(player, this);
        }
        if (gateFail == null
                && !de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, this)
                && !de.aetherion.quests.util.QuestStoryGate.isTutorialQuest(quest.getId())) {
            gateFail = "§eComplete the tutorial first. §7Miss Ledger closes orientation after the Fields.";
        }
        if (gateFail != null) {
            player.sendMessage(gateFail);
            if ("lesson_manager".equalsIgnoreCase(quest.getId())
                    && !de.aetherion.quests.util.QuestStoryGate.ledgerUnlocked(player, this)) {
                de.aetherion.quests.util.QuestStoryGate.redirectToForeman(player, "Miss Ledger");
            }
            if ("border_rites".equalsIgnoreCase(quest.getId())
                    && !de.aetherion.quests.util.QuestStoryGate.riteKeeperUnlocked(player, this)) {
                de.aetherion.quests.util.QuestStoryGate.redirectToTutorial(player, "Rite Warden");
            }
            if (!de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, this)
                    && !de.aetherion.quests.util.QuestStoryGate.isTutorialQuest(quest.getId())) {
                de.aetherion.quests.util.QuestStoryGate.redirectToTutorial(player, "Quest Desk");
            }
            return;
        }


        if (
                currentState == QuestState.ACTIVE
                        || currentState == QuestState.READY
        ) {

            trackedQuests.put(
                    player.getUniqueId(),
                    quest.getId()
            );

            refreshPlayerUi(
                    player
            );

            return;

        }


        List<Quest> aborted =
                abandonOtherQuests(
                        player,
                        quest
                );


        if (!aborted.isEmpty()) {

            for (Quest oldQuest : aborted) {

                player.sendMessage(
                        "§cQuest aborted: §f"
                                + oldQuest.getTitle()
                );

            }


            QuestFeedback feedback =
                    feedback();


            if (feedback != null) {

                feedback.playAbandon(
                        player,
                        aborted.get(0)
                );

            }

        }


        data.setQuestState(
                quest.getId(),
                QuestState.ACTIVE
        );


        storage.saveQuest(
                player.getUniqueId(),
                quest.getId(),
                QuestState.ACTIVE
        );


        trackedQuests.put(
                player.getUniqueId(),
                quest.getId()
        );

        // Soft hints yield to active quests. Always drop the remembered trail on accept
        // so stale "Mine · teleport" etc. cannot resurface mid-tutorial.
        de.aetherion.quests.ui.QuestHint.clearPending(player);


        QuestFeedback feedback =
                feedback();


        if (feedback != null) {

            feedback.playAccept(
                    player,
                    quest
            );

        }


        refreshPlayerUi(
                player
        );

        syncDeliverProgress(
                player
        );

        // Catch spheres are tools required to complete the quest — not turn-in loot.
        if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
            if (claimStarterKit(player, quest.getId())) {
                giveCatchSpheres(player, "common", 16, false);
            }
            unlockProgressFeature(player, "PETS", "Pets", "Manager → Pets is open — catch, then equip");
        }
        if ("lesson_boost".equalsIgnoreCase(quest.getId())) {
            unlockProgressFeature(player, "ANVIL", "Anvil", "Manager → Anvil");
            giveEmeraldBooster(player, 1);
        }
        if ("lesson_manager".equalsIgnoreCase(quest.getId())) {
            unlockProgressFeature(player, "SKILLS", "Skills", "Manager → Skills will blink");
        }
        if ("a_simple_craft".equalsIgnoreCase(quest.getId())) {
            unlockProgressFeature(player, "WORKBENCH", "Crafting + Recipes", "Manager → green Recipe Book");
        }
        // Harbour onboarding: Forager hands out the Simple Axe once.
        if ("gather_wood".equalsIgnoreCase(quest.getId())) {
            if (claimStarterKit(player, quest.getId())) {
                StarterGearReward.giveSimpleAxe(player);
            }
        }
        // Fishing rod / coin rewards wait until turn-in (see completeQuest).

    }

    private static void unlockProgressFeature(Player player, String flag, String title, String subtitle) {
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.unlock(player, flag, title, subtitle);
        }
    }

    /**
     * Starter kits (rod, spheres, …) once per quest — not again after overwrite/re-accept.
     */
    private boolean claimStarterKit(Player player, String questId) {
        if (storage.hasStarterKit(player.getUniqueId(), questId)) {
            return false;
        }
        storage.markStarterKit(player.getUniqueId(), questId);
        return true;
    }


    /*
     * =========================================================
     * QUEST ABSCHLIESSEN
     * =========================================================
     */

    public void completeQuest(
            Player player,
            Quest quest
    ) {
        completeQuest(player, quest, true);
    }

    /**
     * @param announce when false: mark complete + rewards/tracking, no QUEST COMPLETE fanfare
     *                 (harbour waypoint stops like welcome_aboard → Forager).
     */
    public void completeQuest(
            Player player,
            Quest quest,
            boolean announce
    ) {

        if (
                player == null
                        || quest == null
        ) {

            return;

        }


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        quest.getId()
                );


        QuestState currentState =
                data.getQuestState(
                        quest.getId()
                );


        if (currentState == QuestState.COMPLETED) {

            return;

        }


        data.setQuestState(
                quest.getId(),
                QuestState.COMPLETED
        );


        storage.saveQuest(
                player.getUniqueId(),
                quest.getId(),
                QuestState.COMPLETED
        );


        /*
         * =====================================================
         * ANNOUNCE → REWARDS (bundled under complete)
         * =====================================================
         */

        if (announce) {
            QuestFeedback feedback = feedback();
            if (feedback != null) {
                feedback.playComplete(player, quest);
            }
        }

        giveRewards(
                player,
                quest
        );

        // Rod comes from Dock Pass handoff (Tackle) — not again on fishing turn-in.
        // Anvil unlocks with Temper (lesson_boost), not craftsman.

        int aetherXp = QuestAetherXp.of(quest);
        if (aetherXp > 0) {
            storage.setAetherXpGranted(
                    player.getUniqueId(),
                    quest.getId(),
                    aetherXp
            );
        }


        /*
         * =====================================================
         * TRACKING AKTUALISIEREN
         * =====================================================
         */

        trackedQuests.remove(
                player.getUniqueId()
        );


        // Timber turn-in leaves Quartermaster available — soft QuestHint only (no auto-start).


        Quest nextQuest =
                findNextTrackedQuest(
                        player
                );


        if (nextQuest != null) {

            trackedQuests.put(
                    player.getUniqueId(),
                    nextQuest.getId()
            );

            QuestProgressDisplay.showProgress(
                    player,
                    this
            );

        } else {

            QuestProgressDisplay.remove(
                    player
            );
        }


        refreshMarkers(
                player
        );

    }


    /*
     * =========================================================
     * QUEST REWARDS
     * =========================================================
     */

    private void giveRewards(
            Player player,
            Quest quest
    ) {

        boolean outer = beginRewardBatch(player);
        try {

        for (
                Reward reward :
                quest.getRewards()
        ) {


            if (reward == null) {

                continue;

            }


            String rewardName =
                    reward.getName();


            if (
                    rewardName == null
                            || rewardName.isBlank()
            ) {

                continue;

            }


            int amount =
                    reward.getAmount();


            if (amount <= 0) {

                continue;

            }


            /*
             * =================================================
             * STARTER GEAR
             * =================================================
             */

            if (
                    rewardName.equalsIgnoreCase(
                            "Starter Gear"
                    )
            ) {

                StarterGearReward.giveStarterGear(
                        player
                );

                continue;

            }

            if (rewardName.equalsIgnoreCase("Starter Gear No Axe")) {
                StarterGearReward.giveStarterGearWithoutAxe(player);
                continue;
            }

            if (rewardName.equalsIgnoreCase("Simple Axe")) {
                StarterGearReward.giveSimpleAxe(player);
                continue;
            }

            if (rewardName.equalsIgnoreCase("Simple Pickaxe")) {
                StarterGearReward.giveSimplePickaxe(player);
                continue;
            }


            /*
             * =================================================
             * XP
             * =================================================
             */

            if (QuestAetherXp.isXpReward(rewardName)) {

                giveAetherionXp(player, amount);
                continue;

            }


            /*
             * =================================================
             * COINS
             * =================================================
             */

            if (
                    rewardName.equalsIgnoreCase("Coins")
                            || rewardName.equalsIgnoreCase("Coin")
            ) {

                giveCoins(player, amount);
                continue;

            }


            if (
                    rewardName.equalsIgnoreCase("Random Booster")
                            || rewardName.equalsIgnoreCase("Random Boosters")
                            || rewardName.equalsIgnoreCase("random_booster")
            ) {

                giveRandomBoosters(player, amount);
                continue;

            }


            if (
                    rewardName.equalsIgnoreCase("Compacted Diamond Block")
                            || rewardName.equalsIgnoreCase("Compacted Diamond Blocks")
                            || rewardName.equalsIgnoreCase("Compacted Diamond")
                            || rewardName.equalsIgnoreCase("compacted_diamond")
                            || rewardName.equalsIgnoreCase("compacted_diamond_block")
            ) {

                giveCompactedDiamondBlocks(player, amount);
                continue;

            }


            if (
                    rewardName.equalsIgnoreCase("Emerald Booster")
                            || rewardName.equalsIgnoreCase("emerald_booster")
            ) {

                giveEmeraldBooster(
                        player,
                        amount
                );

                continue;

            }


            /*
             * =================================================
             * AETHERION RECIPE BOOK
             * =================================================
             */

            if (
                    rewardName.equalsIgnoreCase(
                            "Recipe Book"
                    )
            ) {

                giveRecipeBook(
                        player,
                        amount
                );

                continue;

            }


            if (
                    rewardName.equalsIgnoreCase("Homestead Marker")
                            || rewardName.equalsIgnoreCase("Camp Marker")
            ) {

                giveHomesteadMarker(player);
                continue;

            }

            if (
                    rewardName.toLowerCase(java.util.Locale.ROOT).startsWith("homestead marker:")
                            || rewardName.toLowerCase(java.util.Locale.ROOT).startsWith("camp marker:")
            ) {
                String spawnId = rewardName.substring(rewardName.indexOf(':') + 1).trim();
                giveHomesteadMarker(player, spawnId);
                continue;
            }


            if (
                    rewardName.toLowerCase().startsWith("spawn:")
                            || rewardName.toLowerCase().startsWith("spawn unlock:")
            ) {

                String spawnId = rewardName.substring(rewardName.indexOf(':') + 1).trim();
                giveSpawnUnlock(player, spawnId);
                continue;

            }


            if (
                    rewardName.equalsIgnoreCase("Rare Catch Sphere")
                            || rewardName.equalsIgnoreCase("Rare Catch Spheres")
                            || rewardName.equalsIgnoreCase("rare_catch_sphere")
            ) {

                giveCatchSpheres(player, "rare", amount, true);
                continue;

            }

            if (
                    rewardName.equalsIgnoreCase("RAW_COD")
                            || rewardName.equalsIgnoreCase("COD")
                            || rewardName.equalsIgnoreCase("Raw Cod")
                            || rewardName.equalsIgnoreCase("raw_cod")
            ) {
                giveItem(player, new ItemStack(Material.COD, amount), "Raw Cod");
                continue;
            }

            Material material =
                    Material.matchMaterial(
                            rewardName.replace(' ', '_').toUpperCase()
                    );
            if (material == null) {
                material = Material.matchMaterial(rewardName);
            }


            if (material == null) {

                Bukkit.getLogger().warning(
                        "[AetherionQuests] "
                                + "Unknown quest reward: "
                                + rewardName
                );

                continue;

            }


            if (!material.isItem()) {

                Bukkit.getLogger().warning(
                        "[AetherionQuests] "
                                + "Quest reward is not an item: "
                                + rewardName
                );

                continue;

            }


            giveItem(
                    player,
                    new ItemStack(
                            material,
                            amount
                    ),
                    rewardName
            );

        }

        } finally {
            if (outer) {
                endRewardBatch(player);
            }
        }

    }

    /**
     * Run several reward grants as one contiguous chat block
     * (all reward lines, blank line, deferred level-ups, blank line).
     */
    public void runRewardBlock(Player player, Runnable payout) {
        if (player == null || payout == null) {
            return;
        }
        boolean outer = beginRewardBatch(player);
        try {
            payout.run();
        } finally {
            if (outer) {
                endRewardBatch(player);
            }
        }
    }

    private boolean beginRewardBatch(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getSkills() == null) {
            return false;
        }
        return items.getSkills().beginRewardBatch(player);
    }

    private void endRewardBatch(Player player) {
        if (player != null && player.isOnline()) {
            player.sendMessage("");
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items != null && items.getSkills() != null) {
            items.getSkills().endRewardBatch(player);
        }
    }

    private void sendReward(Player player, String detail) {
        if (player == null || detail == null) {
            return;
        }
        player.sendMessage("§b✦ §3Reward: §f" + detail);
    }


    /*
     * =========================================================
     * RECIPE BOOK VERGEBEN
     * =========================================================
     */

    private void giveRecipeBook(
            Player player,
            int amount
    ) {


        AetherionItems aetherionItems =
                AetherionItems.getInstance();


        if (aetherionItems == null) {

            Bukkit.getLogger().warning(
                    "[AetherionQuests] "
                            + "AetherionItems is not available. "
                            + "Cannot give Recipe Book."
            );

            return;

        }


        CustomItem customItem =
                new CustomItem(
                        aetherionItems.getItemManager()
                );


        ItemStack recipeBook =
                customItem.createRecipeBook();


        recipeBook.setAmount(
                Math.max(
                        1,
                        amount
                )
        );


        giveItem(
                player,
                recipeBook,
                "Aetherion Recipe Book"
        );

    }


    private void giveHomesteadMarker(Player player) {
        giveHomesteadMarker(player, "lurker_camp");
    }


    private void giveHomesteadMarker(Player player, String spawnId) {
        String id = spawnId == null || spawnId.isBlank() ? "lurker_camp" : spawnId.toLowerCase();
        if (!Bukkit.getPluginManager().isPluginEnabled("AetherionHub")) {
            Bukkit.getLogger().warning(
                    "[AetherionQuests] AetherionHub is not loaded. Cannot give Homestead Marker."
            );
            player.sendMessage("§cCould not give Homestead Marker — AetherionHub is missing.");
            return;
        }

        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            Bukkit.getLogger().warning(
                    "[AetherionQuests] Could not give Homestead Marker: AetherionHub API missing"
            );
            player.sendMessage("§cCould not give Homestead Marker.");
            return;
        }
        try {
            boolean given = hub.giveUnlockItem(player, id);
            if (given) {
                sendReward(player, id.replace('_', ' ') + " §7marker");
            } else {
                player.sendMessage("§cCould not give Homestead Marker.");
            }
        } catch (Exception exception) {
            Bukkit.getLogger().warning(
                    "[AetherionQuests] Could not give Homestead Marker: " + exception.getMessage()
            );
            player.sendMessage("§cCould not give Homestead Marker.");
        }
    }


    private void giveSpawnUnlock(Player player, String spawnId) {
        giveSpawnUnlock(player, spawnId, true);
    }

    private void giveSpawnUnlockQuiet(Player player, String spawnId) {
        giveSpawnUnlock(player, spawnId, false);
    }

    private void giveSpawnUnlock(Player player, String spawnId, boolean announce) {
        if (spawnId == null || spawnId.isBlank()) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("AetherionHub")) {
            Bukkit.getLogger().warning("[AetherionQuests] AetherionHub is not loaded. Cannot unlock spawn " + spawnId + ".");
            if (announce) {
                player.sendMessage("§cCould not unlock teleport — AetherionHub is missing.");
            }
            return;
        }
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null) {
            Bukkit.getLogger().warning("[AetherionQuests] Could not unlock spawn: AetherionHub API missing");
            if (announce) {
                player.sendMessage("§cCould not unlock spawn.");
            }
            return;
        }
        try {
            boolean unlocked = hub.unlock(player, spawnId.toLowerCase());
            if (unlocked) {
                if (announce) {
                    String pretty = spawnId.replace('_', ' ');
                    sendReward(player, pretty + " §7teleport unlocked");
                    player.sendMessage("§7Open Aetherion Manager → Teleports to travel there.");
                    player.sendMessage("");
                }
                de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                if (progress != null) {
                    progress.unlockSilent(player, "SPAWN_UNLOCKER");
                }
            } else if (announce) {
                player.sendMessage("§cCould not unlock spawn §f" + spawnId + "§c.");
            }
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[AetherionQuests] Could not unlock spawn: " + exception.getMessage());
            if (announce) {
                player.sendMessage("§cCould not unlock spawn.");
            }
        }
    }


    private void giveAetherionXp(Player player, int amount) {
        giveAetherionXp(player, amount, true);
    }

    private void giveAetherionXp(Player player, int amount, boolean announce) {
        if (amount <= 0) {
            return;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getSkills() == null) {
            player.giveExp(amount);
            if (announce) {
                sendReward(player, amount + " XP");
            }
            return;
        }
        items.getSkills().addBonusXp(player, amount);
        if (announce) {
            sendReward(player, amount + " Aetherion XP");
        }
    }

    /**
     * Pays out missing / raised Aetherion XP for already completed quests
     * (Egon/Quartermaster had none; other quests may have been bumped).
     */
    public void backfillAetherXp(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int totalDelta = 0;
        int questsTouched = 0;

        for (Quest quest : quests.values()) {
            if (quest == null || quest.getId() == null) {
                continue;
            }
            if (getQuestState(player, quest) != QuestState.COMPLETED) {
                continue;
            }

            int expected = QuestAetherXp.of(quest);
            if (expected <= 0) {
                continue;
            }

            int granted = storage.getAetherXpGranted(uuid, quest.getId());
            if (granted < 0) {
                granted = QuestAetherXp.legacyGranted(quest.getId(), expected);
            }

            int delta = expected - granted;
            if (delta <= 0) {
                storage.setAetherXpGranted(uuid, quest.getId(), Math.max(granted, expected));
                continue;
            }

            giveAetherionXp(player, delta, false);
            storage.setAetherXpGranted(uuid, quest.getId(), expected);
            totalDelta += delta;
            questsTouched++;
        }

        if (totalDelta > 0) {
            player.sendMessage(
                    "§b+" + totalDelta + " Aetherion XP §8· §7quest catch-up"
                            + (questsTouched > 1 ? " §8(§7" + questsTouched + " quests§8)" : "")
            );
        }
    }


    private void giveCoins(Player player, int amount) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCoins() == null) {
            Bukkit.getLogger().warning("[AetherionQuests] AetherionItems is not available. Cannot give coins.");
            return;
        }
        items.getCoins().add(player, amount);
        sendReward(player, amount + " coins");
    }


    private void giveRandomBoosters(Player player, int amount) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            Bukkit.getLogger().warning("[AetherionQuests] AetherionItems is not available. Cannot give random boosters.");
            return;
        }
        CustomItem customItem = new CustomItem(items.getItemManager());
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) {
            giveItem(player, customItem.createRandomBooster(), "Random Booster");
        }
    }


    private void giveCompactedDiamondBlocks(Player player, int amount) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            Bukkit.getLogger().warning("[AetherionQuests] AetherionItems is not available. Cannot give compacted diamond blocks.");
            return;
        }
        int count = Math.max(1, amount);
        ItemStack stack = de.aetherion.items.economy.CompressedResource.DIAMOND.compacted();
        stack.setAmount(count);
        giveItem(player, stack, "Compacted Diamond Block");
        sendReward(player, count + " Compacted Diamond Block" + (count == 1 ? "" : "s"));
    }


    private void giveStarterFishingRod(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        ItemStack rod;
        String name;
        if (items != null && items.getItemManager() != null) {
            rod = new CustomItem(items.getItemManager()).fishing().rod(1);
            name = "Fishing Rod";
        } else {
            rod = new ItemStack(Material.FISHING_ROD);
            name = "Fishing Rod";
        }
        de.aetherion.items.storage.BoosterDelivery.giveQuestReward(player, rod);
        player.sendMessage("§bFisher §7handed you a §f" + name + "§7. Try not to drop it in the river.");
    }

    private void giveCatchSpheres(Player player, String sphereId, int amount, boolean reward) {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            Bukkit.getLogger().warning("[AetherionQuests] AetherMobs is not available. Cannot give catch spheres.");
            return;
        }
        try {
            ItemStack stack = (ItemStack) plugin.getClass()
                    .getMethod("createDevCatchSphere", String.class)
                    .invoke(plugin, sphereId);
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            stack.setAmount(Math.max(1, amount));
            String name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName()
                    : "Catch Sphere";
            giveItem(player, stack, name);
            if (reward) {
                sendReward(player, amount + " " + name);
            } else {
                player.sendMessage("§6Lark §8» §7" + amount + " common spheres. Don't eat them.");
            }
        } catch (ReflectiveOperationException exception) {
            Bukkit.getLogger().warning("[AetherionQuests] Could not give catch spheres.");
        }
    }


    private void giveEmeraldBooster(
            Player player,
            int amount
    ) {

        AetherionItems aetherionItems =
                AetherionItems.getInstance();

        if (aetherionItems == null) {

            Bukkit.getLogger().warning(
                    "[AetherionQuests] "
                            + "AetherionItems is not available. "
                            + "Cannot give Emerald Booster."
            );

            return;

        }

        CustomItem customItem =
                new CustomItem(
                        aetherionItems.getItemManager()
                );

        ItemStack booster =
                customItem.createEmeraldBooster();

        booster.setAmount(
                Math.max(1, amount)
        );

        giveItem(
                player,
                booster,
                "Emerald Booster"
        );

    }

    /** Craftsman Mining Pickaxe visit — no quest. */
    public void giftEmeraldBooster(Player player, int amount) {
        giveEmeraldBooster(player, amount);
    }

    public void giftCoins(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        giveCoins(player, amount);
    }

    /** Miss Ledger tutorial stamp — coins in quest-reward style, then a blank line. */
    public void giftTutorialGraduation(Player player) {
        if (player == null) {
            return;
        }
        boolean outer = beginRewardBatch(player);
        try {
            giveCoins(player, 250);
        } finally {
            if (outer) {
                endRewardBatch(player);
            } else if (player.isOnline()) {
                player.sendMessage("");
            }
        }
    }

    /**
     * Admin / Dev Menu: mark orientation complete without walking the harbour loop.
     * Completes every tutorial quest (quiet), stamps graduation kit, returns how many were newly completed.
     */
    public int markTutorialDone(Player player) {
        if (player == null) {
            return 0;
        }
        int newly = 0;
        for (String id : QuestStoryGate.tutorialQuestIds()) {
            Quest quest = getQuest(id);
            if (quest == null) {
                continue;
            }
            if (getQuestState(player, quest) == QuestState.COMPLETED) {
                continue;
            }
            completeQuest(player, quest, false);
            newly++;
        }
        for (String id : QuestStoryGate.tutorialDoneQuestIds()) {
            Quest quest = getQuest(id);
            if (quest == null) {
                continue;
            }
            if (getQuestState(player, quest) != QuestState.COMPLETED) {
                completeQuest(player, quest, false);
                newly++;
            }
        }
        if (!storage.hasStarterKit(player.getUniqueId(), "tutorial_graduation")) {
            storage.markStarterKit(player.getUniqueId(), "tutorial_graduation");
        }
        return newly;
    }


    /*
     * =========================================================
     * ITEM VERGEBEN
     * =========================================================
     */

    private void giveItem(
            Player player,
            ItemStack item,
            String displayName
    ) {


        if (
                item == null
                        || item.getType().isAir()
        ) {

            return;

        }


        de.aetherion.items.storage.BoosterDelivery.giveQuestReward(player, item);


        sendReward(
                player,
                displayName
                        + " §7x"
                        + item.getAmount()
        );

    }


    /*
     * =========================================================
     * QUEST ZURÜCKSETZEN
     * =========================================================
     */

    public void resetQuest(
            Player player,
            Quest quest
    ) {


        UUID uuid =
                player.getUniqueId();


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        quest.getId()
                );


        data.setQuestState(
                quest.getId(),
                QuestState.AVAILABLE
        );


        storage.saveQuest(
                uuid,
                quest.getId(),
                QuestState.AVAILABLE
        );


        for (
                var objective :
                quest.getObjectives()
        ) {


            data.setProgress(
                    quest.getId(),
                    objective.getTarget(),
                    0
            );


            storage.saveProgress(
                    uuid,
                    quest.getId(),
                    objective.getTarget(),
                    0
            );

        }


        if (
                isQuestTracked(
                        player,
                        quest
                )
        ) {

            trackedQuests.remove(
                    uuid
            );

        }


        refreshPlayerUi(
                player
        );

    }


    /**
     * Full harbour / beta wipe: every quest back to AVAILABLE, kits cleared, tracking off.
     */
    public void resetAllQuests(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        trackedQuests.remove(uuid);
        playerData.remove(uuid);
        storage.wipePlayer(uuid);
        QuestProgressDisplay.remove(player);
        refreshPlayerUi(player);
    }


    /*
     * =========================================================
     * QUEST BESITZEN?
     * =========================================================
     */

    public boolean hasQuest(
            Player player,
            Quest quest
    ) {


        QuestState state =
                getQuestState(
                        player,
                        quest
                );


        return state != QuestState.AVAILABLE;

    }


    /*
     * =========================================================
     * QUEST STATE
     * =========================================================
     */

    public QuestState getQuestState(
            Player player,
            Quest quest
    ) {


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        quest.getId()
                );


        return data.getQuestState(
                quest.getId()
        );

    }


    /*
     * =========================================================
     * ZENTRALER QUEST-PROGRESS
     * =========================================================
     */

    public void addProgress(
            Player player,
            String questId,
            String objective,
            int amount
    ) {


        Quest quest =
                getQuest(
                        questId
                );


        if (quest == null) {

            return;

        }


        QuestState state =
                getQuestState(
                        player,
                        quest
                );


        if (state != QuestState.ACTIVE) {

            return;

        }


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        questId
                );


        int current =
                data.getProgress(
                        questId,
                        objective
                );


        if (amount <= 0) {

            return;

        }


        int maximum =
                getObjectiveMaximum(
                        quest,
                        objective
                );


        if (
                maximum > 0
                        && current >= maximum
        ) {

            return;

        }


        int newAmount =
                amount;


        if (maximum > 0) {

            newAmount =
                    Math.min(
                            amount,
                            maximum - current
                    );

        }


        if (newAmount <= 0) {

            return;

        }


        data.addProgress(
                questId,
                objective,
                newAmount
        );


        int updated =
                data.getProgress(
                        questId,
                        objective
                );


        storage.saveProgress(
                player.getUniqueId(),
                questId,
                objective,
                updated
        );


        boolean objectiveComplete =
                maximum > 0
                        && updated >= maximum;


        boolean allComplete =
                areAllObjectivesComplete(
                        player,
                        quest
                );


        if (allComplete) {

            markReady(
                    player,
                    quest
            );

        }


        refreshPlayerUi(
                player
        );


        QuestFeedback feedback =
                feedback();


        if (feedback != null) {

            Objective questObjective =
                    findObjective(
                            quest,
                            objective
                    );


            if (allComplete) {

                AetherionQuests plugin =
                        AetherionQuests.getInstance();


                if (plugin != null) {

                    plugin.getServer().getScheduler().runTaskLater(
                            plugin,
                            () -> {

                                if (!player.isOnline()) {
                                    return;
                                }

                                if (getQuestState(player, quest) == QuestState.READY) {
                                    feedback.playReady(player, quest);
                                }

                            },
                            1L
                    );

                }

            } else {

                feedback.playProgress(
                        player,
                        quest,
                        questObjective,
                        updated,
                        maximum,
                        objectiveComplete
                );

            }

        }

    }


    /*
     * =========================================================
     * PROGRESS ABFRAGEN
     * =========================================================
     */

    public int getProgress(
            Player player,
            String questId,
            String objective
    ) {


        PlayerQuestData data =
                getOrLoadPlayerData(
                        player,
                        questId
                );


        return data.getProgress(
                questId,
                objective
        );

    }


    /*
     * =========================================================
     * MAXIMALES OBJECTIVE-LIMIT
     * =========================================================
     */

    private int getObjectiveMaximum(
            Quest quest,
            String objective
    ) {


        for (
                var questObjective :
                quest.getObjectives()
        ) {


            if (
                    questObjective
                            .getTarget()
                            .equalsIgnoreCase(
                                    objective
                            )
            ) {

                return questObjective.getAmount();

            }

        }


        return 0;

    }


    public boolean areAllObjectivesComplete(
            Player player,
            Quest quest
    ) {

        if (
                quest == null
                        || quest.getObjectives() == null
                        || quest.getObjectives().isEmpty()
        ) {

            return true;

        }


        for (Objective objective : quest.getObjectives()) {

            if (objective == null) {
                continue;
            }

            int required = objective.getAmount();

            if (required <= 0) {
                continue;
            }

            int progress = getProgress(
                    player,
                    quest.getId(),
                    objective.getTarget()
            );

            if (progress < required) {
                return false;
            }

        }

        return true;

    }


    public Quest getQuestForNpc(QuestNPC npc) {

        if (npc == null) {
            return null;
        }

        if (npc.getQuestId() != null && !npc.getQuestId().isBlank()) {
            Quest quest = getQuest(npc.getQuestId());
            if (quest != null) {
                return quest;
            }
        }

        if (npc.getServiceId() == null || npc.getServiceId().isBlank()) {
            return null;
        }

        for (Quest quest : quests.values()) {
            if (quest != null && quest.hasService(npc.getServiceId())) {
                return quest;
            }
        }

        return null;

    }


    public void syncDeliverProgress(Player player) {

        if (player == null) {
            return;
        }

        // Sync every active/ready DELIVER quest — not only the tracked one
        // (side-quests / auto-pickup must still update coal counts).
        for (Quest quest : getQuests()) {
            if (quest == null) {
                continue;
            }
            QuestState state = getQuestState(player, quest);
            if (state != QuestState.ACTIVE && state != QuestState.READY) {
                continue;
            }
            syncDeliverProgressForQuest(player, quest);
        }
    }

    private void syncDeliverProgressForQuest(Player player, Quest quest) {

        boolean hasDeliver = false;
        boolean changed = false;
        boolean increased = false;
        Objective lastObjective = null;
        int lastCurrent = 0;
        int lastMaximum = 0;

        for (Objective objective : quest.getObjectives()) {

            if (objective == null || objective.getType() != ObjectiveType.DELIVER) {
                continue;
            }

            hasDeliver = true;

            Material material = Material.matchMaterial(objective.getTarget());

            int have = material != null
                    ? PlayerItems.count(player, material)
                    : PlayerItems.count(player, objective.getTarget());
            // Compressed coal still counts toward a vanilla COAL delivery.
            if (material == Material.COAL) {
                have += PlayerItems.count(player, "compressed_coal") * 8;
                have += PlayerItems.count(player, "compacted_coal") * 64;
            }
            int next = Math.min(Math.max(0, have), Math.max(0, objective.getAmount()));
            int current = getProgress(player, quest.getId(), objective.getTarget());

            if (next == current) {
                continue;
            }

            changed = true;

            if (next > current) {
                increased = true;
                lastObjective = objective;
                lastCurrent = next;
                lastMaximum = objective.getAmount();
            }

            PlayerQuestData data = getOrLoadPlayerData(player, quest.getId());
            data.setProgress(quest.getId(), objective.getTarget(), next);
            storage.saveProgress(
                    player.getUniqueId(),
                    quest.getId(),
                    objective.getTarget(),
                    next
            );

        }

        if (!hasDeliver || !changed) {
            return;
        }

        boolean allComplete = areAllObjectivesComplete(player, quest);

        if (allComplete) {
            markReady(player, quest);
        } else if (getQuestState(player, quest) == QuestState.READY) {
            markActive(player, quest);
        }

        refreshPlayerUi(player);

        QuestFeedback questFeedback = feedback();

        if (questFeedback == null || !increased) {
            return;
        }

        if (allComplete) {

            AetherionQuests plugin = AetherionQuests.getInstance();

            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && getQuestState(player, quest) == QuestState.READY) {
                        questFeedback.playReady(player, quest);
                    }
                }, 1L);
            }

        } else {
            questFeedback.playProgress(
                    player,
                    quest,
                    lastObjective,
                    lastCurrent,
                    lastMaximum,
                    lastMaximum > 0 && lastCurrent >= lastMaximum
            );
        }

    }


    public boolean consumeDeliverItems(Player player, Quest quest) {

        if (player == null || quest == null) {
            return true;
        }

        for (Objective objective : quest.getObjectives()) {

            if (objective == null || objective.getType() != ObjectiveType.DELIVER) {
                continue;
            }

            if (deliverHave(player, objective) < objective.getAmount()) {
                return false;
            }

        }

        for (Objective objective : quest.getObjectives()) {

            if (objective == null || objective.getType() != ObjectiveType.DELIVER) {
                continue;
            }

            consumeDeliver(player, objective.getTarget(), objective.getAmount());

        }

        return true;

    }

    private static int deliverHave(Player player, Objective objective) {
        Material material = Material.matchMaterial(objective.getTarget());
        int have = material != null
                ? PlayerItems.count(player, material)
                : PlayerItems.count(player, objective.getTarget());
        if (material == Material.COAL) {
            have += PlayerItems.count(player, "compressed_coal") * 8;
            have += PlayerItems.count(player, "compacted_coal") * 64;
        }
        return have;
    }

    private static void consumeDeliver(Player player, String target, int amount) {
        Material material = Material.matchMaterial(target);
        if (material == Material.COAL) {
            int left = amount;
            int vanilla = PlayerItems.count(player, Material.COAL);
            int takeVanilla = Math.min(left, vanilla);
            if (takeVanilla > 0) {
                PlayerItems.remove(player, Material.COAL, takeVanilla);
                left -= takeVanilla;
            }
            while (left > 0) {
                int compressed = PlayerItems.count(player, "compressed_coal");
                if (compressed <= 0) {
                    break;
                }
                PlayerItems.remove(player, "compressed_coal", 1);
                left -= 8;
            }
            while (left > 0) {
                int compacted = PlayerItems.count(player, "compacted_coal");
                if (compacted <= 0) {
                    break;
                }
                PlayerItems.remove(player, "compacted_coal", 1);
                left -= 64;
            }
            return;
        }
        if (material != null) {
            PlayerItems.remove(player, material, amount);
        } else {
            PlayerItems.remove(player, target, amount);
        }
    }


    public void enforceSingleQuest(Player player) {

        if (player == null) {
            return;
        }

        Quest keep = findNextTrackedQuest(player);

        if (keep == null) {
            untrackQuest(player);
            return;
        }

        abandonOtherQuests(player, keep);
        trackQuest(player, keep);

    }


    public void abandonQuest(
            Player player,
            Quest quest
    ) {

        if (player == null || quest == null) {
            return;
        }

        PlayerQuestData data = getOrLoadPlayerData(
                player,
                quest.getId()
        );

        data.setQuestState(
                quest.getId(),
                QuestState.AVAILABLE
        );

        storage.saveQuest(
                player.getUniqueId(),
                quest.getId(),
                QuestState.AVAILABLE
        );

        for (Objective objective : quest.getObjectives()) {

            if (objective == null) {
                continue;
            }

            data.setProgress(
                    quest.getId(),
                    objective.getTarget(),
                    0
            );

            storage.saveProgress(
                    player.getUniqueId(),
                    quest.getId(),
                    objective.getTarget(),
                    0
            );

        }

        if (isQuestTracked(player, quest)) {
            trackedQuests.remove(player.getUniqueId());
        }

    }


    private List<Quest> abandonOtherQuests(
            Player player,
            Quest keep
    ) {

        List<Quest> aborted = new ArrayList<>();

        for (Quest quest : quests.values()) {

            if (quest == null) {
                continue;
            }

            if (keep != null && quest.getId().equalsIgnoreCase(keep.getId())) {
                continue;
            }

            QuestState state = getQuestState(player, quest);

            if (state != QuestState.ACTIVE && state != QuestState.READY) {
                continue;
            }

            abandonQuest(player, quest);
            aborted.add(quest);

        }

        return aborted;

    }


    private void markReady(
            Player player,
            Quest quest
    ) {

        if (getQuestState(player, quest) != QuestState.ACTIVE) {
            return;
        }

        PlayerQuestData data = getOrLoadPlayerData(
                player,
                quest.getId()
        );

        data.setQuestState(
                quest.getId(),
                QuestState.READY
        );

        storage.saveQuest(
                player.getUniqueId(),
                quest.getId(),
                QuestState.READY
        );

    }


    private void markActive(
            Player player,
            Quest quest
    ) {

        if (getQuestState(player, quest) != QuestState.READY) {
            return;
        }

        PlayerQuestData data = getOrLoadPlayerData(
                player,
                quest.getId()
        );

        data.setQuestState(
                quest.getId(),
                QuestState.ACTIVE
        );

        storage.saveQuest(
                player.getUniqueId(),
                quest.getId(),
                QuestState.ACTIVE
        );

    }


    private Objective findObjective(
            Quest quest,
            String target
    ) {

        if (quest == null || target == null) {
            return null;
        }

        for (Objective objective : quest.getObjectives()) {

            if (objective != null
                    && objective.getTarget() != null
                    && objective.getTarget().equalsIgnoreCase(target)) {
                return objective;
            }

        }

        return null;

    }


    private void refreshPlayerUi(Player player) {

        QuestProgressDisplay.showProgress(player, this);
        refreshMarkers(player);

    }


    private void refreshMarkers(Player player) {

        AetherionQuests plugin = AetherionQuests.getInstance();

        if (plugin != null && plugin.getMarkerManager() != null) {
            plugin.getMarkerManager().refresh(player);
        }

    }


    private QuestFeedback feedback() {

        AetherionQuests plugin = AetherionQuests.getInstance();

        if (plugin == null) {
            return null;
        }

        return plugin.getQuestFeedback();

    }

}