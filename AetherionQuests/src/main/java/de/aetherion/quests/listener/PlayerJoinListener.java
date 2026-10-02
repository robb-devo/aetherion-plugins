package de.aetherion.quests.listener;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.ui.QuestProgressDisplay;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class PlayerJoinListener implements Listener {


    private final QuestManager questManager;


    public PlayerJoinListener(QuestManager questManager) {
        this.questManager = questManager;
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        AetherionQuests plugin = AetherionQuests.getInstance();

        if (plugin == null) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {

            if (!player.isOnline()) {
                return;
            }

            questManager.enforceSingleQuest(player);

            questManager.backfillAetherXp(player);

            // First join: tiny language picker (quests/hints only) → HarbourArrival after the pick.
            if (!de.aetherion.quests.lang.PlayerLang.hasChosen(player)) {
                de.aetherion.quests.lang.LangMenu.openIfNeeded(player);
            } else {
                nudgeIfEgonWaiting(player, plugin);
            }

            Quest quest = questManager.getTrackedQuest(player);

            if (quest != null) {
                QuestProgressDisplay.showProgress(player, questManager);
            }

            if (plugin.getMarkerManager() != null) {
                plugin.getMarkerManager().refresh(player);
            }

        }, 20L);

    }


    /** Back on the server but never spoke to Egon: his bells + flare once the welcome title clears. */
    private void nudgeIfEgonWaiting(Player player, AetherionQuests plugin) {
        Quest welcome = questManager.getQuest("welcome_aboard");
        if (welcome == null
                || questManager.getQuestState(player, welcome) != de.aetherion.quests.model.QuestState.AVAILABLE
                || de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                de.aetherion.quests.ui.HarbourArrival.nudge(player);
            }
        }, 50L);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        QuestProgressDisplay.remove(event.getPlayer());
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getDialogManager() != null) {
            plugin.getDialogManager().forget(event.getPlayer().getUniqueId());
        }
        if (plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().unload(event.getPlayer().getUniqueId());
            questManager.forgetPlayer(event.getPlayer().getUniqueId());
        }
    }

}
