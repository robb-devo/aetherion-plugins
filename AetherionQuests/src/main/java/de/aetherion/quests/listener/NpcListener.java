package de.aetherion.quests.listener;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.NPCType;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.npc.QuestNpcAppearance;
import de.aetherion.quests.service.QuestNPCSpawnService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class NpcListener implements Listener {


    private final QuestManager questManager;

    private final DialogManager dialogManager;

    private final Map<UUID, Integer> lastClickTick = new HashMap<>();


    public NpcListener(
            QuestManager questManager,
            DialogManager dialogManager
    ) {

        this.questManager = questManager;

        this.dialogManager = dialogManager;

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClickTick.remove(event.getPlayer().getUniqueId());
    }



    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(
            PlayerInteractEntityEvent event
    ) {

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }

        QuestNPC npc = resolveNpc(event.getRightClicked());
        if (npc == null) {
            return;
        }

        event.setCancelled(true);
        handleClick(event.getPlayer(), npc);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClickAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        QuestNPC npc = resolveNpc(event.getRightClicked());
        if (npc == null) {
            return;
        }

        event.setCancelled(true);
        handleClick(event.getPlayer(), npc);
    }


    /** Entry for FancyNpcs / living hosts — same dialog pipeline as villagers. */
    public void handleLivingClick(Player player, QuestNPC npc) {
        handleClick(player, npc);
    }

    private void handleClick(Player player, QuestNPC npc) {
        // Absorb double-fires (Entity + AtEntity, hologram + body) and spam clicks.
        int tick = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        Integer last = lastClickTick.get(id);
        if (last != null && tick - last < 10) {
            return;
        }
        lastClickTick.put(id, tick);

        // Same NPC mid-speech: ignore spam. Other NPCs may interrupt and start fresh.
        if (dialogManager.isSpeakingWith(player, npc)) {
            return;
        }

        // Pending Accept/Decline from another NPC must not stick across conversations.
        dialogManager.clearPendingChoice(player);


        if (player.isSneaking()
                && player.hasPermission("aetherionquests.admin")) {

            String heldId =
                    NpcAnchorListener.npcId(
                            player.getInventory().getItemInMainHand()
                    );

            if (heldId != null
                    && heldId.equalsIgnoreCase(npc.getId())) {

                // Clear npcs.yml so the guard sweep cannot bring them straight back.
                if (new QuestNPCSpawnService().forget(npc.getId())) {
                    player.sendMessage(
                            "§eDespawned §f"
                                    + npc.getName()
                                    + "§e. Place the anchor again to set a new spot."
                    );
                }
                return;

            }

        }



        /*
         * =========================================================
         * QUEST NPC
         * =========================================================
         *
         * Neue Quest:
         *
         * NPC
         *   ↓
         * Dialog
         *   ↓
         * Accept / Decline
         *   ↓
         * Quest ACTIVE
         *
         * Aktive Deliver-Quest:
         *
         * NPC
         *   ↓
         * Items abgeben
         *   ↓
         * Progress
         *   ↓
         * Quest COMPLETE
         *
         * =========================================================
         */

        // Surveyor before generic QUEST turn-in — never consume blueprints on talk.
        if ("surveyor".equalsIgnoreCase(npc.getId())) {
            handleSurveyorVisit(player, npc);
            return;
        }

        // Eldervale blueprint forge — upgrade stones on tools.
        if ("eldervale_upgrade".equalsIgnoreCase(npc.getId())) {
            handleEldervaleUpgradeVisit(player, npc);
            return;
        }

        // Farm Isle pantry / mill — Forgehand-style service (no quest).
        if ("root_cellar".equalsIgnoreCase(npc.getId())) {
            handleRootCellarVisit(player, npc);
            return;
        }

        // Lucky Vince — one punch line, then casino lobby (slots / roulette pick).
        if ("vince".equalsIgnoreCase(npc.getId())) {
            handleVinceVisit(player, npc);
            return;
        }

        // Crystal Liquidator — short intro, then open crystal desk.
        if ("liquidator".equalsIgnoreCase(npc.getId())) {
            handleLiquidatorVisit(player, npc);
            return;
        }

        if (npc.getType() == NPCType.QUEST) {

            handleQuestNPC(
                    player,
                    npc
            );

            return;

        }

        // Craftsman: recipe unlock + Mining Pickaxe gift (no quest).
        if ("craftsman".equalsIgnoreCase(npc.getId())) {
            handleCraftsmanVisit(player, npc);
            return;
        }

        // Gossip / lore NPCs — dialog only.
        if (npc.getType() == NPCType.FLAVOR) {
            // Dock shop — no quest marker; open shop directly.
            if ("fishmonger".equalsIgnoreCase(npc.getId())) {
                openFishShop(player);
                return;
            }
            dialogManager.clearPendingChoice(player);
            if (npc.getDialogId() != null && !npc.getDialogId().isEmpty()) {
                dialogManager.startDialog(player, npc);
            }
            if ("forage_pad_guide".equalsIgnoreCase(npc.getId())) {
                de.aetherion.quests.ui.QuestHint.show(player, "canopy_clerk", "Canopy Clerk");
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Hint · Canopy Clerk · Forage Isle",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN
                ));
            }
            return;
        }



        /*
         * =========================================================
         * SERVICE NPC
         * =========================================================
         */

        if (npc.getType() == NPCType.SERVICE) {

            handleServiceNPC(
                    player,
                    npc
            );

        }

    }


    private QuestNPC resolveNpc(Entity clicked) {
        if (clicked == null) {
            return null;
        }

        QuestNPC npc = QuestNPCRegistry.getNPCByEntityId(clicked.getUniqueId().toString());
        if (npc != null) {
            return npc;
        }

        String taggedId = QuestNpcAppearance.npcId(clicked);
        if (taggedId != null) {
            npc = QuestNPCRegistry.getNPC(taggedId);
            if (npc != null) {
                return npc;
            }
        }

        Entity vehicle = clicked.getVehicle();
        if (vehicle != null) {
            taggedId = QuestNpcAppearance.npcId(vehicle);
            if (taggedId != null) {
                npc = QuestNPCRegistry.getNPC(taggedId);
                if (npc != null) {
                    return npc;
                }
            }
        }

        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getMarkerManager() != null) {
            String markerNpcId = plugin.getMarkerManager().getNpcId(clicked);
            if (markerNpcId != null) {
                return QuestNPCRegistry.getNPC(markerNpcId);
            }
        }

        return null;
    }



    /*
     * =========================================================
     * QUEST NPC
     * =========================================================
     */

    private void handleQuestNPC(
            Player player,
            QuestNPC npc
    ) {

        // Active TALK objectives (e.g. welcome_aboard → lumberjack) win over the NPC's own quest.
        Quest talkQuest = findActiveTalkQuest(player, npc);
        if (talkQuest != null) {
            // Forager is a waypoint, not a real turn-in — no QUEST COMPLETE fanfare.
            boolean silentWaypoint = "welcome_aboard".equalsIgnoreCase(talkQuest.getId())
                    && "lumberjack".equalsIgnoreCase(npc.getId());
            questManager.completeQuest(player, talkQuest, !silentWaypoint);
            if (silentWaypoint) {
                handoffForagerOnboarding(player, npc);
            }
            return;
        }

        // Fishmonger: always a shop — no dialog gate.
        if ("fishmonger".equalsIgnoreCase(npc.getId())) {
            openFishShop(player);
            return;
        }

        // Dock Pass handoff removed — shop is open from the start.
        if ("fisher".equalsIgnoreCase(npc.getId())) {
            Quest dock = questManager.getQuest("dock_pass");
            if (dock != null) {
                QuestState dockState = questManager.getQuestState(player, dock);
                if (dockState == QuestState.ACTIVE || dockState == QuestState.READY) {
                    // Legacy: clear stuck dock_pass players quietly so Tackle can teach fishing.
                    questManager.completeQuest(player, dock, false);
                }
            }
        }

        // Egon receives gather_wood deliveries even though the Forager starts that quest.
        if ("egon".equalsIgnoreCase(npc.getId())) {
            Quest timber = questManager.getQuest("gather_wood");
            if (timber != null) {
                QuestState timberState = questManager.getQuestState(player, timber);
                if (timberState == QuestState.ACTIVE || timberState == QuestState.READY) {
                    tryTurnIn(player, npc, timber);
                    return;
                }
            }
        }

        // Re-play chop demo if they left mid-lesson (quest active, still locked).
        if ("lumberjack".equalsIgnoreCase(npc.getId())) {
            Quest timber = questManager.getQuest("gather_wood");
            AetherionQuests aq = AetherionQuests.getInstance();
            boolean locked = aq == null || aq.getPlayerQuestStorage() == null
                    || !aq.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), "forager_chop_unlocked");
            if (timber != null
                    && questManager.getQuestState(player, timber) == QuestState.ACTIVE
                    && locked) {
                org.bukkit.Location at = resolveNpcLocation(npc);
                npcSay(player, npc, "lumberjack.replay", "Still watching? One more time — wait for green CHOP.");
                if (at != null) {
                    playForagerDemo(player, at);
                } else {
                    de.aetherion.quests.bridge.QuestProgressBridge.unlockForagerChop(player);
                }
                return;
            }
        }


        String questId =
                npc.getQuestId();


        Quest quest =
                questManager.getQuestForNpc(
                        npc
                );


        if (quest == null) {

            // Flavor / gossip NPCs: dialog only, no quest error.
            if (questId == null || questId.isEmpty()) {
                if (npc.getDialogId() != null && !npc.getDialogId().isEmpty()) {
                    dialogManager.clearPendingChoice(player);
                    dialogManager.startDialog(player, npc);
                }
                return;
            }

            player.sendMessage(
                    "§cQuest not found: §f" + questId
            );

            return;

        }


        QuestState state =
                questManager.getQuestState(
                        player,
                        quest
                );



        /*
         * =====================================================
         * QUEST AVAILABLE
         * =====================================================
         */

        if (state == QuestState.AVAILABLE) {

            // Timber run is unlocked by Egon's pier handshake first.
            if ("gather_wood".equalsIgnoreCase(quest.getId())) {
                Quest welcome = questManager.getQuest("welcome_aboard");
                if (welcome != null
                        && questManager.getQuestState(player, welcome) != QuestState.COMPLETED) {
                    npcSay(player, npc, "lumberjack.egon_first",
                            "Talk to Egon on the pier first. Then we chop.");
                    return;
                }
            }

            dialogManager.startDialog(
                    player,
                    npc
            );

            return;

        }



        /*
         * =====================================================
         * QUEST ACTIVE
         * =====================================================
         *
         * Aktive DELIVER-Objectives werden direkt
         * beim Quest-NPC abgegeben.
         *
         * Beispiel Egon:
         *
         * Welcome Aboard
         * 10 Oak Logs
         *
         * =====================================================
         */

        if (state == QuestState.ACTIVE
                || state == QuestState.READY) {

            if ("lark".equalsIgnoreCase(npc.getId())
                    && "pocket_zoo".equalsIgnoreCase(quest.getId())
                    && state == QuestState.ACTIVE) {
                int catchProgress = questManager.getProgress(player, quest.getId(), "ANY");
                int equipProgress = questManager.getProgress(player, quest.getId(), "AETHER_PET");
                if (catchProgress >= 1 && equipProgress < 1) {
                    AetherionQuests plugin = AetherionQuests.getInstance();
                    String lessonKey = "lark_equip_lesson";
                    boolean taught = plugin != null
                            && plugin.getPlayerQuestStorage() != null
                            && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), lessonKey);
                    if (!taught && plugin != null && plugin.getPlayerQuestStorage() != null) {
                        plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), lessonKey);
                    }
                    // Part 2 — one line at a time; never dump the equip speech in one breath.
                    npcSay(player, npc, "lark.nice_catch",
                            "Nice catch. Pocket's squealing.");
                    de.aetherion.quests.ui.QuestProgressDisplay.showProgress(player, questManager);
                    if (plugin != null) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            npcSay(player, npc, "lark.equip_now",
                                    "Now equip it: §eManager → Pets§f (it'll blink). Open, pick your catch, equip. Then we're done.");
                            player.sendActionBar(net.kyori.adventure.text.Component.text(
                                    "→ Manager → Pets → Equip your catch",
                                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
                            ));
                            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                            if (progress != null) {
                                progress.refreshManager(player);
                            }
                            de.aetherion.quests.ui.QuestProgressDisplay.showProgress(player, questManager);
                        }, 45L);
                    }
                    return;
                }
            }

            if (quest.hasTurnInNpc()
                    && !quest.getTurnInNpcId().equalsIgnoreCase(npc.getId())) {
                questManager.syncDeliverProgress(player);
                if (questManager.areAllObjectivesComplete(player, quest)) {
                    String turnIn = quest.hasTurnInNpc() ? quest.getTurnInNpcId() : "Egon";
                    QuestNPC turnNpc = de.aetherion.quests.npc.QuestNPCRegistry.getNPC(turnIn);
                    String turnName = turnNpc != null ? turnNpc.getName() : turnIn;
                    npcSay(player, npc, LangPack.format(
                            player,
                            "msg.deliver_not_me",
                            "Nice haul. Deliver it to §e{0}§f — not me.",
                            turnName
                    ));
                } else {
                    showQuestProgress(player, quest);
                }
                return;
            }

            tryTurnIn(
                    player,
                    npc,
                    quest
            );

            return;

        }



        /*
         * =====================================================
         * QUEST COMPLETED
         * =====================================================
         */

        if (state == QuestState.COMPLETED) {

            // Miss Ledger stamps tutorial shut and keeps the help desk.
            if ("ledger".equalsIgnoreCase(npc.getId())
                    && de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)) {
                openLedgerHelpDesk(player, npc);
                return;
            }

            dialogManager.startCompletedDialog(player, npc);
            return;

        }

    }


    /**
     * After Egon's TALK step: Forager teaches chop, grants axe quest, points back to the pier.
     */
    private void handoffForagerOnboarding(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();

        npcSay(player, npc, "lumberjack.lesson", "Egon needs oak. Here's the axe lesson.");

        org.bukkit.Location at = resolveNpcLocation(npc);
        if (plugin == null) {
            Quest timber = questManager.getQuest("gather_wood");
            if (timber != null
                    && questManager.getQuestState(player, timber) == QuestState.AVAILABLE) {
                questManager.startQuest(player, timber);
            }
            playForagerDemo(player, at);
            return;
        }

        // Space handoff lines so chat / axe / demo never stack.
        // Chop stays locked until ForagerChopDemo finishes ("your turn").
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "lumberjack.take_axe", "Take this axe. Watch once — slowly — then you chop.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Watch the chop demo, then fell oak for Egon",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
        }, 50L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Quest timber = questManager.getQuest("gather_wood");
            if (timber != null
                    && questManager.getQuestState(player, timber) == QuestState.AVAILABLE) {
                questManager.startQuest(player, timber);
            }
            if (at != null) {
                playForagerDemo(player, at);
            } else {
                npcSay(player, npc, "lumberjack.click_trunk",
                        "Left-click a glowing trunk — when CHOP hits green, click again.");
                npcSay(player, npc, "lumberjack.ten_oak",
                        "Ten oak logs to §eEgon §fon the pier.");
                de.aetherion.quests.bridge.QuestProgressBridge.unlockForagerChop(player);
            }
        }, 110L);
    }


    private static org.bukkit.Location resolveNpcLocation(QuestNPC npc) {
        if (npc.getEntityId() != null) {
            org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(npc.getEntityId());
            if (entity != null) {
                return entity.getLocation();
            }
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getNpcDataStorage() != null) {
            return plugin.getNpcDataStorage().getSavedLocation(npc.getId());
        }
        return null;
    }


    private static void playForagerDemo(Player player, org.bukkit.Location near) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        if (foraging != null) {
            foraging.playChopDemo(player, near);
            return;
        }
        LivingNpcProfile.say(player, "lumberjack", "Forager",
                LangPack.msg(player, "say.lumberjack.demo_chop",
                        "Left-click a glowing trunk — green CHOP, then swing."));
        LivingNpcProfile.say(player, "lumberjack", "Forager",
                LangPack.msg(player, "say.lumberjack.demo_egon",
                        "Ten oak logs to Egon on the pier."));
    }



    /*
     * =========================================================
     * QUEST NPC QUEST ABSCHLIESSEN
     * =========================================================
     *
     * Der QuestManager übernimmt:
     *
     * - COMPLETED-State
     * - Rewards
     * - Tracking
     * - Bossbar entfernen
     *
     * =========================================================
     */

    private void tryTurnIn(
            Player player,
            QuestNPC npc,
            Quest quest
    ) {

        questManager.syncDeliverProgress(player);

        Quest talkQuest =
                findActiveTalkQuest(
                        player,
                        npc
                );

        if (talkQuest != null) {
            questManager.completeQuest(player, talkQuest);
            return;
        }

        if (!questManager.areAllObjectivesComplete(player, quest)) {
            showQuestProgress(player, quest);
            return;
        }

        if (!questManager.consumeDeliverItems(player, quest)) {
            npcSay(player, npc, "bring_items", "Bring me the items first.");
            showQuestProgress(player, quest);
            return;
        }

        completeQuestNPCQuest(player, npc, quest);

    }


    private void handoffTackleDockPass(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }

        if (de.aetherion.quests.item.DockShopPass.count(player, plugin) >= 1) {
            npcSay(player, npc,
                    "You already have the pass. Give it to the Fishmonger — one slip only.");
            return;
        }

        npcSay(player, npc, "Fishmonger sent you. Here's one dock pass.");
        player.getInventory().addItem(de.aetherion.quests.item.DockShopPass.create(plugin));
        questManager.syncDeliverProgress(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc,
                    "Hand him the paper. Buy a rod there if you need one, then come back for the reel lesson.");
        }, 45L);
    }


    private void handleVinceVisit(Player player, QuestNPC npc) {
        if (dialogManager.isSpeakingWith(player, npc)) {
            return;
        }
        String[] lines = LangPack.dialogs(player, "vince_intro", new String[] {
                "Casino's behind me. Pick a machine. Don't cry on the felt.",
                "Slots. Roulette. Coins in. Dignity stays in your pocket.",
                "I don't deal. I commentate. Machines do the dirty work.",
                "The glass is hungrier than you. Feed it anyway."
        });
        int pick = Math.floorMod(org.bukkit.Bukkit.getCurrentTick() / 40, lines.length);
        LivingNpcProfile.say(player, npc, lines[pick]);
    }

    private void handleLiquidatorVisit(Player player, QuestNPC npc) {
        if (dialogManager.isSpeakingWith(player, npc)) {
            return;
        }
        String[] lines = LangPack.dialogs(player, "liquidator_intro", new String[] {
                "Crystal desk. Buy, sell, or melt mats into Aether Crystals.",
                "Crystals come from the official shop — or as level rewards."
        });
        if (lines.length > 0) {
            LivingNpcProfile.say(player, npc, lines[0]);
        }
        if (lines.length > 1) {
            LivingNpcProfile.say(player, npc, lines[1]);
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            openLiquidatorGui(player);
            return;
        }
        // Two queued lines @ 30 ticks each, then open the desk.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                openLiquidatorGui(player);
            }
        }, 65L);
    }

    private static void openLiquidatorGui(Player player) {
        try {
            Object items = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance")
                    .invoke(null);
            if (items == null) {
                return;
            }
            Object liquidator = items.getClass().getMethod("getLiquidator").invoke(items);
            if (liquidator != null) {
                liquidator.getClass().getMethod("open", Player.class).invoke(liquidator, player);
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
        }
    }


    private void handleCraftsmanVisit(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String giftKey = "craftsman_mining_pick_gift";

        boolean hasPick = de.aetherion.quests.dialog.DialogManager.playerHasItemId(player, "mining_pickaxe");
        boolean gifted = plugin != null
                && plugin.getPlayerQuestStorage() != null
                && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), giftKey);

        // Unlock crafting on first real visit.
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.unlock(player, "WORKBENCH", "Crafting + Recipes", "Manager → green Recipe Book");
        }
        if (plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), "craftsman_spoken");
        }

        if (hasPick && !gifted) {
            if (plugin != null && plugin.getPlayerQuestStorage() != null) {
                plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), giftKey);
            }
            // One contiguous reward block: quest rewards + craftsman bonus, then blank / level-ups.
            questManager.runRewardBlock(player, () -> {
                Quest legacy = questManager.getQuest("a_simple_craft");
                if (legacy != null) {
                    QuestState st = questManager.getQuestState(player, legacy);
                    if (st == QuestState.ACTIVE || st == QuestState.READY || st == QuestState.AVAILABLE) {
                        questManager.completeQuest(player, legacy, false);
                    }
                }
                questManager.giftCoins(player, 1000);
            });
            LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.craftsman.pick_ok",
                    "There it is — Mining Pickaxe. Good work."));
            de.aetherion.quests.ui.QuestHint.clearPending(player);
            de.aetherion.quests.ui.QuestHint.show(player, "foreman", "Shaft Foreman");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Shaft Foreman · Mines",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA
            ));
            return;
        }

        if (gifted) {
            dialogManager.startCompletedDialog(player, npc);
            return;
        }

        if (dialogManager.isSpeakingWith(player, npc)) {
            return;
        }
        dialogManager.startDialog(player, npc);
    }


    private void handleSurveyorVisit(Player player, QuestNPC npc) {
        // Surveyor + hunt are post-tutorial — never enable trolls mid-orientation.
        if (de.aetherion.quests.util.QuestStoryGate.blockedByTutorial(player, questManager, npc.getId())) {
            if (dialogManager.isSpeakingWith(player, npc)) {
                return;
            }
            dialogManager.startDialog(player, npc);
            return;
        }

        // Hunt unlocks on first talk. Desk stays quiet until the first blueprint is stamped.
        enableBlueprintHunt(player);

        if (hasStampedAnyBlueprint(player)) {
            openSurveyorDesk(player);
            return;
        }

        if (playerHasSurveyorBlueprint(player)) {
            markSurveyorDeskReady(player);
            LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.surveyor.got_page",
                    "Got a page. Desk's yours — stamp it into a tool."));
            openSurveyorDesk(player);
            return;
        }

        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.surveyor.trolls",
                "Trolls crawl the veins now. Hunt them — they drop blueprint pages."));
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.surveyor.bring_page",
                "Bring a page. I'll stamp it into a tool here."));
    }

    private void handleEldervaleUpgradeVisit(Player player, QuestNPC npc) {
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.eldervale_upgrade.open",
                "Forge is hot. Blueprinted tool + Upgrade Stone. I'll open the desk."));
        // Dialog first, short beat, then forge GUI.
        Bukkit.getScheduler().runTaskLater(
                AetherionQuests.getInstance(),
                () -> {
                    if (player.isOnline()) {
                        openBlueprintForge(player);
                    }
                },
                40L
        );
    }

    private void handleRootCellarVisit(Player player, QuestNPC npc) {
        // First visit: one plain line. Later: open hub only.
        AetherionQuests plugin = AetherionQuests.getInstance();
        String spokenKey = "root_cellar_spoken";
        boolean first = plugin != null
                && plugin.getPlayerQuestStorage() != null
                && !plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), spokenKey);

        if (first && plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), spokenKey);
            LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.root_cellar.first",
                    "Millstone Pantry. Compacted crops go in. Refined pantry goods come out."));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    openRootCellarHub(player);
                }
            }, 40L);
            return;
        }

        openRootCellarHub(player);
    }

    private static void openRootCellarHub(Player player) {
        try {
            Class.forName("de.aetherion.items.farm.RootCellarHubGUI")
                    .getMethod("open", Player.class)
                    .invoke(null, player);
        } catch (Throwable ignored) {
            player.sendMessage("§cRoot Cellar shelves unavailable.");
        }
    }

    private static void openBlueprintForge(Player player) {
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance")
                    .invoke(null);
            if (plugin == null) {
                return;
            }
            Object itemManager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
            Class.forName("de.aetherion.items.blueprint.BlueprintForgeGUI")
                    .getMethod("open", org.bukkit.entity.Player.class,
                            Class.forName("de.aetherion.items.manager.ItemManager"))
                    .invoke(null, player, itemManager);
        } catch (Throwable ignored) {
            player.sendMessage("§cForge unavailable — talk to Forgehand with tool + Upgrade Stone.");
        }
    }


    /** @return true if hunt was newly enabled this click. */
    private static boolean enableBlueprintHunt(Player player) {
        try {
            Object result = Class.forName("de.aetherion.items.mining.BlueprintHunt")
                    .getMethod("enable", org.bukkit.entity.Player.class)
                    .invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }


    private static boolean isSurveyorDeskReady(Player player) {
        try {
            Object unlocks = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance").invoke(null);
            if (unlocks == null) {
                return false;
            }
            Object service = unlocks.getClass().getMethod("blueprintUnlocks").invoke(unlocks);
            if (service == null) {
                return false;
            }
            Object ready = service.getClass().getMethod("isDeskReady", Player.class).invoke(service, player);
            return ready instanceof Boolean && (Boolean) ready;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }


    private static boolean markSurveyorDeskReady(Player player) {
        try {
            Object unlocks = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance").invoke(null);
            if (unlocks == null) {
                return false;
            }
            Object service = unlocks.getClass().getMethod("blueprintUnlocks").invoke(unlocks);
            if (service == null) {
                return false;
            }
            Object first = service.getClass().getMethod("markDeskReady", Player.class).invoke(service, player);
            return first instanceof Boolean && (Boolean) first;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }


    private static boolean hasStampedAnyBlueprint(Player player) {
        try {
            Object unlocks = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance").invoke(null);
            if (unlocks == null) {
                return false;
            }
            Object service = unlocks.getClass().getMethod("blueprintUnlocks").invoke(unlocks);
            if (service == null) {
                return false;
            }
            Object stamped = service.getClass().getMethod("hasStampedAny", Player.class).invoke(service, player);
            return stamped instanceof Boolean && (Boolean) stamped;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }


    private static boolean playerHasSurveyorBlueprint(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> kindClass = Class.forName("de.aetherion.items.blueprint.BlueprintKind");
            Object[] kinds = (Object[]) kindClass.getMethod("values").invoke(null);
            for (Object kind : kinds) {
                String itemId = (String) kindClass.getMethod("itemId").invoke(kind);
                if (itemId != null && de.aetherion.quests.util.PlayerItems.count(player, itemId) > 0) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return de.aetherion.quests.util.PlayerItems.count(player, "blueprint_vein_siphon") > 0;
        }
        return false;
    }


    private static void openSurveyorDesk(Player player) {
        try {
            Class.forName("de.aetherion.items.blueprint.SurveyorGui")
                    .getMethod("openFor", org.bukkit.entity.Player.class)
                    .invoke(null, player);
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            player.sendMessage("§cSurveyor desk is offline.");
        }
    }


    private static void openFishShop(Player player) {
        try {
            Class.forName("de.aetherion.items.economy.FishShopService")
                    .getMethod("openFor", org.bukkit.entity.Player.class)
                    .invoke(null, player);
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            player.sendMessage("§cFish shop is offline.");
        }
    }


    private void briefQuartermasterSpawns(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();

        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.quartermaster.coal_ok", "Coal logged. Thanks."));
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.quartermaster.mines_tp",
                "Mines teleport's open — Manager → Teleports, or type §e/mines§f."));
        player.sendMessage("");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "→ Mines teleport · Manager or /mines",
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
        de.aetherion.quests.ui.QuestHint.show(player, "foreman", "Shaft Foreman", "also /mines");
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.quartermaster.next_foreman",
                "Next: §eShaft Foreman§f at the Mines. Yellow arrow up top tracks him."));

        if (plugin != null) {
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.refreshManager(player);
            }
        }
    }


    private static void blinkHomesteadMarker(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub != null) {
            hub.blinkUnlockItem(player, 10);
        }
    }


    private void escortToCapital(Player player, QuestNPC npc) {
        String name = npc.getName();
        npcSay(player, npc, "foreman.shift_done",
                "That's a shift. Head toward the hub — stop at §eTemper§f (Booster Tutor) on the way.");
        de.aetherion.quests.ui.QuestHint.clearPending(player);
        de.aetherion.quests.ui.QuestHint.show(player, "booster_tutor", "Temper");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "→ Temper · Booster Tutor",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }


    private void briefLedgerNext(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String name = npc.getName();

        npcSay(player, npc, "ledger.skill_ok", "Skill equipped. Good.");

        if (plugin == null) {
            npcSay(player, npc, "ledger.next_fields",
                    "Next: the fields. §eFarmer§f needs wheat. §eLark§f is in the same area for pets. Then come back to me.");
            de.aetherion.quests.ui.QuestHint.show(player, "farmer", "Farmer");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "ledger.farmer_hint",
                    "When you're ready: the fields. §eFarmer§f needs wheat — harvest and shoo the birds.");
        }, 40L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "ledger.lark_hint",
                    "§eLark§f is in the same area for pets — catch spheres, then equip. Talk to him at the fence.");
        }, 85L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "ledger.come_back",
                    "§aCome back after Farmer and Lark. §fI close orientation — and I keep a help menu if you get stuck.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Hint · Fields · Farmer + Lark → then Ledger",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            de.aetherion.quests.ui.QuestHint.show(player, "farmer", "Farmer");
        }, 130L);
    }

    private void briefVexNext(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String name = npc.getName();

        de.aetherion.quests.ui.QuestHint.clearPending(player);
        npcSay(player, npc, "vex.ten_down", "Ten down. Steel filed. You're less of a liability.");
        // Borderlands teleport unlocks by walking into the waste (Hub discover popup) — not here.

        if (plugin == null) {
            npcSay(player, npc, "vex.gear_tip",
                    "§7Soft tip — upgrade your combat set / boosters before the harder waste.");
            npcSay(player, npc, "vex.rite_hint",
                    "Optional next — §cRite Warden§f. Borderlands bosses, powder altar. No paperwork from me.");
            de.aetherion.quests.ui.QuestHint.show(player, "rite_keeper", "Rite Warden");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "vex.gear_tip",
                    "§7Soft tip — harden your gear a bit before you push deeper. Recipe Book + anvil help.");
        }, 40L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "vex.rite_hint",
                    "Optional next — §cRite Warden§f. Borderlands bosses, powder altar. No paperwork from me.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Soft hint · gear up · then Rite Warden",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            de.aetherion.quests.ui.QuestHint.show(player, "rite_keeper", "Rite Warden");
        }, 80L);
    }


    private void briefTemperNext(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String name = npc.getName();

        de.aetherion.quests.ui.QuestHint.clearPending(player);

        npcSay(player, npc, "booster.fused", "Booster fused. Anvil stays in the Manager.");

        if (plugin == null) {
            npcSay(player, npc, "booster.next_ledger", "Next — §dMiss Ledger§f at Capital.");
            de.aetherion.quests.ui.QuestHint.show(player, "ledger", "Miss Ledger");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "booster.next_ledger", "Next — §dMiss Ledger§f at the Capital. Skills.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Miss Ledger · Skills (Capital)",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            de.aetherion.quests.ui.QuestHint.show(player, "ledger", "Miss Ledger");
        }, 40L);
    }

    private void briefFarmerNext(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String name = npc.getName();

        npcSay(player, npc, "farmer.wheat_ok", "Wheat's done. Nice work.");

        // Both Fields stops done → Miss Ledger closes tutorial (not Farmer).
        if (de.aetherion.quests.util.QuestStoryGate.questCompleted(player, questManager, "pocket_zoo")) {
            briefBackToLedger(player, npc);
            return;
        }

        if (plugin == null) {
            npcSay(player, npc, "farmer.isle_peek",
                    "Other end of the barn — farm guide and portal. Peek when you're curious.");
            de.aetherion.quests.ui.QuestHint.show(player, "lark", "Lark");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "farmer.isle_peek",
                    "Other end of the barn — NPC and portal to the Farm Isle. Worth a look sometime.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Farm Isle · barn far end · optional peek",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
            // Soft compass only — Lark was already covered in his intro.
            de.aetherion.quests.ui.QuestHint.show(player, "lark", "Lark");
        }, 45L);
    }

    private void briefPocketZooNext(Player player, QuestNPC npc) {
        String name = npc.getName();
        npcSay(player, npc, "lark.catch_ok", "Catch done. You're set with pets.");

        // Both Fields stops done → Miss Ledger closes tutorial (not Lark).
        if (de.aetherion.quests.util.QuestStoryGate.questCompleted(player, questManager, "farm_hand")) {
            briefBackToLedger(player, npc);
            return;
        }

        npcSay(player, npc, "lark.farmer_left", "§eFarmer§f still wants wheat if you skipped him — same area.");
        de.aetherion.quests.ui.QuestHint.show(player, "farmer", "Farmer");
    }

    /** Soft graduation pointer — only Miss Ledger opens the briefing desk. */
    private void briefBackToLedger(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        String name = npc.getName();

        de.aetherion.quests.ui.QuestHint.clearPending(player);

        if (plugin == null) {
            npcSay(player, npc, "fields.back_ledger", "Back to §dMiss Ledger§f — she closes orientation. Or §e/capital§f.");
            de.aetherion.quests.ui.QuestHint.show(player, "ledger", "Miss Ledger", "or /capital");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            npcSay(player, npc, "fields.done_ledger",
                    "§aFields done. §f§dMiss Ledger§f stamps the tutorial shut. Questions go to her.");
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "→ Miss Ledger · or /capital",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
            ));
            de.aetherion.quests.ui.QuestHint.show(player, "ledger", "Miss Ledger", "or /capital");
        }, 30L);
    }

    private void openLedgerHelpDesk(Player player, QuestNPC npc) {
        AetherionQuests plugin = AetherionQuests.getInstance();

        boolean firstStamp = false;
        if (plugin != null && plugin.getPlayerQuestStorage() != null) {
            String key = "tutorial_graduation";
            if (!plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), key)) {
                plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), key);
                firstStamp = true;
            }
        }

        if (!firstStamp) {
            // Return visit — dialog first, short pause, then manager menu.
            LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.refresh",
                    "Need a refresher? Here's the desk."));
            if (plugin != null) {
                dialogManager.afterDialog(player, () ->
                        de.aetherion.quests.ui.EgonBriefingGUI.open(player, "Miss Ledger"));
            } else {
                de.aetherion.quests.ui.EgonBriefingGUI.open(player, "Miss Ledger");
            }
            return;
        }

        // Same QUEST COMPLETE block as every other turn-in, then rewards, blank, Ledger in her colour.
        if (plugin != null && plugin.getQuestFeedback() != null) {
            plugin.getQuestFeedback().playComplete(
                    player,
                    de.aetherion.quests.lang.LangPack.ui(player, "tutorial_name", "Tutorial")
            );
        }
        questManager.giftTutorialGraduation(player);
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.graduated",
                "Well done. Orientation's filed — map's yours."));
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.arrow_habit",
                "One habit: always watch the §eyellow arrow up top§f. It points to your current job."));

        if (plugin == null) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!de.aetherion.quests.util.QuestStoryGate.questCompleted(player, questManager, "lesson_steel")) {
                LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.hint_vex",
                        "§cSergeant Vex§f at the Borderlands gate could be interesting. Need help later? Talk to me."));
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Soft hint · Sergeant Vex",
                        net.kyori.adventure.text.format.NamedTextColor.RED
                ));
                de.aetherion.quests.ui.QuestHint.show(player, "vex", "Sergeant Vex");
            } else if (!de.aetherion.quests.util.QuestStoryGate.questCompleted(player, questManager, "border_rites")) {
                LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.hint_rite",
                        "§cRite Warden§f in the waste could be interesting. Need help later? Talk to me."));
                de.aetherion.quests.ui.QuestHint.show(player, "rite_keeper", "Rite Warden");
            } else {
                LivingNpcProfile.say(player, npc, LangPack.msg(player, "say.ledger.hint_help",
                        "Need help later? Talk to me."));
            }
        }, 50L);
    }


    private void completeQuestNPCQuest(
            Player player,
            QuestNPC npc,
            Quest quest
    ) {

        questManager.completeQuest(
                player,
                quest
        );

        // General rule: Complete + rewards first, then NPC dialog with a short beat.
        AetherionQuests plugin = AetherionQuests.getInstance();
        Runnable followUp = () -> {
            if (player.isOnline()) {
                runPostCompleteDialog(player, npc, quest);
            }
        };
        if (plugin == null) {
            followUp.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, followUp, 40L);
    }

    private void runPostCompleteDialog(Player player, QuestNPC npc, Quest quest) {
        if ("dock_pass".equalsIgnoreCase(quest.getId())) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            String name = npc.getName();
            npcSay(player, npc, "Pass checks out.");
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    npcSay(player, npc,
                            "Shop's open for T1 gear now — rod and armor. Talk to me when you want to browse.");
                }, 45L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    npcSay(player, npc,
                            "Want Tackle's fishing lesson? Buy a rod here, then go back to him.");
                }, 90L);
            }
            return;
        }

        if ("forge_coal".equalsIgnoreCase(quest.getId())) {
            briefQuartermasterSpawns(player, npc);
            return;
        }

        if ("first_shift".equalsIgnoreCase(quest.getId())) {
            escortToCapital(player, npc);
            return;
        }

        if ("lesson_manager".equalsIgnoreCase(quest.getId())) {
            briefLedgerNext(player, npc);
            return;
        }

        if ("lesson_steel".equalsIgnoreCase(quest.getId())) {
            briefVexNext(player, npc);
            return;
        }

        if ("lesson_boost".equalsIgnoreCase(quest.getId())) {
            briefTemperNext(player, npc);
            return;
        }

        if ("border_rites".equalsIgnoreCase(quest.getId())) {
            de.aetherion.quests.ui.QuestHint.clearPending(player);
            npcSay(player, npc, "rite.logged", "Rite logged. Altar stays. Keep the vials coming.");
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        npcSay(player, npc, "rite.free",
                                "You're free. Waste, desks, whatever. No leash from me.");
                    }
                }, 45L);
            } else {
                npcSay(player, npc, "rite.free",
                        "You're free. Waste, desks, whatever. No leash from me.");
            }
            return;
        }

        if ("farm_hand".equalsIgnoreCase(quest.getId())) {
            briefFarmerNext(player, npc);
            return;
        }

        if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
            briefPocketZooNext(player, npc);
            return;
        }

        if ("a_good_catch".equalsIgnoreCase(quest.getId())) {
            String name = npc.getName();
            npcSay(player, npc, "fisher.catch_ok", "Catch counted. Line clear.");
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    npcSay(player, npc, "fisher.rod_tip",
                            "Peek at your rod sometime — certain tools level up around here.");
                }, 40L);
            } else {
                npcSay(player, npc, "fisher.rod_tip",
                        "Peek at your rod sometime — certain tools level up around here.");
            }
            return;
        }

        if ("gather_wood".equalsIgnoreCase(quest.getId())
                || "egon".equalsIgnoreCase(npc.getId())) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            String name = npc.getName();
            npcSay(player, npc, "egon.logs_ok", "Logs received. Kit stays yours — don't lose it.");
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        npcSay(player, npc, "egon.next_qm",
                                "Quartermaster's past the little market — talk to him next.");
                    }
                }, 45L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        de.aetherion.quests.ui.QuestHint.show(player, "quartermaster", "Quartermaster");
                    }
                }, 70L);
            } else {
                npcSay(player, npc, "egon.next_qm",
                        "Quartermaster's past the little market — talk to him next.");
                de.aetherion.quests.ui.QuestHint.show(player, "quartermaster", "Quartermaster");
            }
            return;
        }

        npcSay(player, npc, "Excellent work!");
    }



    /*
     * =========================================================
     * SERVICE NPC
     * =========================================================
     */

    private void handleServiceNPC(
            Player player,
            QuestNPC npc
    ) {


        /*
         * =====================================================
         * AKTIVE TALK-QUESTS
         * =====================================================
         */

        Quest talkQuest =
                findActiveTalkQuest(
                        player,
                        npc
                );


        if (talkQuest != null) {

            questManager.completeQuest(
                    player,
                    talkQuest
            );


            npcSay(player, npc, "Your first equipment is ready.");


            return;

        }



        /*
         * =====================================================
         * SERVICE-ID PRÜFEN
         * =====================================================
         */

        String serviceId =
                npc.getServiceId();


        if (serviceId == null
                || serviceId.isEmpty()) {

            player.sendMessage(
                    "§cNo service configured for this NPC."
            );

            return;

        }



        /*
         * =====================================================
         * PASSENDE SERVICE-QUEST SUCHEN
         * =====================================================
         */

        Quest quest =
                findServiceQuest(
                        player,
                        serviceId
                );


        if (quest == null) {

            npcSay(player, npc, "There is nothing for you here right now.");

            return;

        }



        /*
         * =====================================================
         * QUEST STATE
         * =====================================================
         */

        QuestState state =
                questManager.getQuestState(
                        player,
                        quest
                );



        /*
         * =====================================================
         * QUEST AVAILABLE
         * =====================================================
         */

        if (state == QuestState.AVAILABLE) {

            dialogManager.startDialog(
                    player,
                    npc
            );

            return;

        }



        /*
         * =====================================================
         * QUEST ACTIVE
         * =====================================================
         */

        if (state == QuestState.ACTIVE) {

            boolean delivered =
                    handleDeliverObjectives(
                            player,
                            quest
                    );


            if (delivered) {

                state =
                        questManager.getQuestState(
                                player,
                                quest
                        );

            }


            if (
                    delivered
                            && areAllObjectivesComplete(
                            player,
                            quest
                    )
            ) {

                completeServiceQuest(
                        player,
                        npc,
                        quest
                );

                return;

            }


            if (state == QuestState.READY) {

                completeServiceQuest(
                        player,
                        npc,
                        quest
                );

                return;

            }


            showQuestProgress(
                    player,
                    quest
            );


            return;

        }



        /*
         * =====================================================
         * QUEST READY
         * =====================================================
         */

        if (state == QuestState.READY) {

            completeServiceQuest(
                    player,
                    npc,
                    quest
            );

            return;

        }



        /*
         * =====================================================
         * QUEST COMPLETED
         * =====================================================
         */

        if (state == QuestState.COMPLETED) {

            if ("ledger".equalsIgnoreCase(npc.getId())
                    && de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)) {
                openLedgerHelpDesk(player, npc);
                return;
            }

            dialogManager.startCompletedDialog(player, npc);
            return;

        }



        /*
         * =====================================================
         * FALLBACK
         * =====================================================
         */

        npcSay(player, npc, "There is nothing for you here right now.");

    }



    /*
     * =========================================================
     * ACTIVE TALK QUEST SUCHEN
     * =========================================================
     */

    private Quest findActiveTalkQuest(
            Player player,
            QuestNPC npc
    ) {


        for (Quest quest :
                questManager.getQuests()) {


            QuestState state =
                    questManager.getQuestState(
                            player,
                            quest
                    );


            if (state != QuestState.ACTIVE) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.TALK) {

                    continue;

                }


                String target =
                        objective.getTarget();


                if (target == null
                        || target.isEmpty()) {

                    continue;

                }


                if (target.equalsIgnoreCase(
                        npc.getId()
                )
                        || target.equalsIgnoreCase(
                        npc.getName()
                )) {

                    return quest;

                }

            }

        }


        return null;

    }



    /*
     * =========================================================
     * SERVICE QUEST SUCHEN
     * =========================================================
     */

    private Quest findServiceQuest(
            Player player,
            String serviceId
    ) {


        for (Quest quest :
                questManager.getQuests()) {


            if (quest == null) {

                continue;

            }


            if (quest.getServiceId() == null) {

                continue;

            }


            if (!quest.getServiceId()
                    .equalsIgnoreCase(
                            serviceId
                    )) {

                continue;

            }


            QuestState state =
                    questManager.getQuestState(
                            player,
                            quest
                    );


            if (state == QuestState.COMPLETED) {

                continue;

            }


            return quest;

        }


        return null;

    }



    /*
     * =========================================================
     * DELIVER OBJECTIVES
     * =========================================================
     *
     * Prüft alle DELIVER-Objectives der Quest.
     *
     * Das Target wird als Bukkit-Materialname erwartet.
     *
     * Beispiel:
     *
     * ObjectiveType.DELIVER
     * Target: OAK_LOG
     * Amount: 10
     *
     * Beim NPC werden die benötigten Items aus dem
     * Inventar entfernt und der Quest-Progress erhöht.
     *
     * =========================================================
     */

    private boolean handleDeliverObjectives(
            Player player,
            Quest quest
    ) {


        boolean deliveredAny =
                false;


        for (Objective objective :
                quest.getObjectives()) {


            if (objective == null) {

                continue;

            }


            if (objective.getType()
                    != ObjectiveType.DELIVER) {

                continue;

            }


            String target =
                    objective.getTarget();


            if (target == null
                    || target.isEmpty()) {

                continue;

            }


            int currentProgress =
                    questManager.getProgress(
                            player,
                            quest.getId(),
                            objective.getTarget()
                    );


            int requiredAmount =
                    objective.getAmount();


            int remaining =
                    requiredAmount
                            - currentProgress;


            if (remaining <= 0) {

                continue;

            }


            int available =
                    de.aetherion.quests.util.PlayerItems.count(
                            player,
                            target
                    );


            if (available <= 0) {

                continue;

            }


            int amountToDeliver =
                    Math.min(
                            available,
                            remaining
                    );


            de.aetherion.quests.util.PlayerItems.remove(
                    player,
                    target,
                    amountToDeliver
            );


            questManager.addProgress(
                    player,
                    quest.getId(),
                    objective.getTarget(),
                    amountToDeliver
            );


            deliveredAny = true;


            player.sendMessage(
                    "§aDelivered §f"
                            + amountToDeliver
                            + "x "
                            + formatObjectiveName(
                            objective.getTarget()
                    )
                            + "§a."
            );

        }


        return deliveredAny;

    }



    /*
     * =========================================================
     * ALLE OBJECTIVES ERFÜLLT?
     * =========================================================
     */

    private boolean areAllObjectivesComplete(
            Player player,
            Quest quest
    ) {


        if (
                quest.getObjectives() == null
                        || quest.getObjectives().isEmpty()
        ) {

            return true;

        }


        for (Objective objective :
                quest.getObjectives()) {


            if (objective == null) {

                continue;

            }


            int required =
                    objective.getAmount();


            if (required <= 0) {

                continue;

            }


            int progress =
                    questManager.getProgress(
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



    /*
     * =========================================================
     * ITEMS ZÄHLEN
     * =========================================================
     */

    private int countItems(
            Player player,
            Material material
    ) {


        int amount =
                0;


        for (ItemStack item :
                player.getInventory().getContents()) {


            if (item == null) {

                continue;

            }


            if (item.getType() != material) {

                continue;

            }


            amount +=
                    item.getAmount();

        }


        return amount;

    }



    /*
     * =========================================================
     * ITEMS ENTFERNEN
     * =========================================================
     */

    private void removeItems(
            Player player,
            Material material,
            int amount
    ) {


        int remaining =
                amount;


        ItemStack[] contents =
                player.getInventory().getContents();


        for (int slot = 0;
             slot < contents.length;
             slot++) {


            if (remaining <= 0) {

                break;

            }


            ItemStack item =
                    contents[slot];


            if (item == null) {

                continue;

            }


            if (item.getType() != material) {

                continue;

            }


            int remove =
                    Math.min(
                            item.getAmount(),
                            remaining
                    );


            int newAmount =
                    item.getAmount()
                            - remove;


            if (newAmount <= 0) {

                player.getInventory().setItem(
                        slot,
                        null
                );

            } else {

                item.setAmount(
                        newAmount
                );

            }


            remaining -=
                    remove;

        }

    }



    /*
     * =========================================================
     * SERVICE QUEST STARTEN
     * =========================================================
     */

    private void startServiceQuest(
            Player player,
            QuestNPC npc,
            Quest quest
    ) {


        npcSay(player, npc, "Let's get to work.");


        questManager.startQuest(
                player,
                quest
        );


        showQuestProgress(
                player,
                quest
        );

    }



    /*
     * =========================================================
     * QUEST PROGRESS ANZEIGEN
     * =========================================================
     */

    private void showQuestProgress(
            Player player,
            Quest quest
    ) {


        player.sendMessage(
                "§6"
                        + quest.getTitle()
                        + ":"
        );


        for (Objective objective :
                quest.getObjectives()) {


            int progress =
                    questManager.getProgress(
                            player,
                            quest.getId(),
                            objective.getTarget()
                    );


            player.sendMessage(
                    "§7"
                            + formatObjectiveName(
                            objective.getTarget()
                    )
                            + ": §f"
                            + progress
                            + "§7/§f"
                            + objective.getAmount()
            );

        }

    }



    /*
     * =========================================================
     * SERVICE QUEST ABSCHLIESSEN
     * =========================================================
     */

    private void completeServiceQuest(
            Player player,
            QuestNPC npc,
            Quest quest
    ) {


        questManager.completeQuest(
                player,
                quest
        );


        npcSay(player, npc, "Excellent work!");

    }



    /*
     * =========================================================
     * OBJECTIVE NAME FORMATIEREN
     * =========================================================
     */

    private String formatObjectiveName(
            String value
    ) {


        if (value == null
                || value.isEmpty()) {

            return "Unknown";

        }


        String[] parts =
                value.toLowerCase()
                        .split("_");


        StringBuilder result =
                new StringBuilder();


        for (String part :
                parts) {


            if (part.isEmpty()) {

                continue;

            }


            if (result.length() > 0) {

                result.append(" ");

            }


            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );


            if (part.length() > 1) {

                result.append(
                        part.substring(1)
                );

            }

        }


        return result.toString();

    }


    private static void npcSay(Player player, QuestNPC npc, String line) {
        LivingNpcProfile.say(player, npc, line);
    }

    private static void npcSay(Player player, QuestNPC npc, String key, String english) {
        LivingNpcProfile.say(player, npc, LangPack.msg(player, "say." + key, english));
    }

}