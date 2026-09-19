package de.aetherion.quests.listener;

import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft nudge during Vex's Borderlands lesson after a death.
 */
public final class VexDeathHintListener implements Listener {

    private static final String QUEST_ID = "lesson_steel";
    private static final long COOLDOWN_MS = 90_000L;

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingHint = new ConcurrentHashMap<>();

    public VexDeathHintListener(JavaPlugin plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isOnLessonSteel(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHint.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        // Mark once on death; whisper only after respawn (avoids double dialog).
        pendingHint.put(player.getUniqueId(), Boolean.TRUE);
        lastHint.put(player.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(pendingHint.remove(player.getUniqueId()))) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> whisper(player), 20L);
    }

    private boolean isOnLessonSteel(Player player) {
        Quest quest = questManager.getQuest(QUEST_ID);
        if (quest == null || player == null) {
            return false;
        }
        QuestState state = questManager.getQuestState(player, quest);
        return state == QuestState.ACTIVE || state == QuestState.READY;
    }

    private void whisper(Player player) {
        if (player == null || !player.isOnline() || !isOnLessonSteel(player)) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§cSergeant Vex §8» §fYou're vertical again.");
        if (QuestStoryGate.questCompleted(player, questManager, "lesson_boost")) {
            player.sendMessage("§cSergeant Vex §8» §7Trouble? Combat set + boosters. Recipe Book and anvil. Then finish the ten.");
        } else {
            player.sendMessage("§cSergeant Vex §8» §7Trouble? §eTemper§7 teaches boosters. Combat set from the Recipe Book. Soft gear.");
            de.aetherion.quests.ui.QuestHint.show(player, "booster_tutor", "Temper");
        }
        player.sendMessage("");
    }
}
