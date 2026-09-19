package de.aetherion.quests.bridge;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.ObjectiveMatcher;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Called from Items / Foraging when vanilla break/pickup events never fire
 * (tree collapse, inventory auto-pay, etc.).
 */
public final class QuestProgressBridge {

    private QuestProgressBridge() {
    }

    public static void noteBroken(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0) {
            return;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return;
        }
        for (Quest quest : manager.getQuests()) {
            if (manager.getQuestState(player, quest) != QuestState.ACTIVE) {
                continue;
            }
            for (Objective objective : quest.getObjectives()) {
                if (objective == null || objective.getType() == null) {
                    continue;
                }
                ObjectiveType type = objective.getType();
                if (type != ObjectiveType.BREAK
                        && type != ObjectiveType.MINE
                        && type != ObjectiveType.HARVEST) {
                    continue;
                }
                if (!ObjectiveMatcher.matchesBlock(material, objective.getTarget())) {
                    continue;
                }
                manager.addProgress(player, quest.getId(), objective.getTarget(), amount);
            }
        }
    }

    public static void noteCollected(Player player, Material material, int amount) {
        if (player == null) {
            return;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return;
        }
        if (material != null && amount > 0) {
            for (Quest quest : manager.getQuests()) {
                if (manager.getQuestState(player, quest) != QuestState.ACTIVE) {
                    continue;
                }
                for (Objective objective : quest.getObjectives()) {
                    if (objective == null || objective.getType() != ObjectiveType.COLLECT) {
                        continue;
                    }
                    if (!ObjectiveMatcher.matchesItem(material, objective.getTarget())
                            && !ObjectiveMatcher.matchesBlock(material, objective.getTarget())) {
                        continue;
                    }
                    manager.addProgress(player, quest.getId(), objective.getTarget(), amount);
                }
            }
        }
        // Instant inventory pay skips EntityPickup — keep DELIVER bars in sync.
        manager.syncDeliverProgress(player);
    }

    /**
     * After any inventory auto-pay / drop ingest — sync DELIVER objectives.
     */
    public static void noteInventoryGain(Player player) {
        if (player == null) {
            return;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return;
        }
        manager.syncDeliverProgress(player);
    }

    /**
     * Custom USE targets that never fire as a raw Bukkit interact
     * (e.g. Aetherion Manager opens with a cancelled click).
     */
    public static void noteUsed(Player player, String target) {
        if (player == null || target == null || target.isBlank()) {
            return;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return;
        }
        for (Quest quest : manager.getQuests()) {
            if (manager.getQuestState(player, quest) != QuestState.ACTIVE) {
                continue;
            }
            for (Objective objective : quest.getObjectives()) {
                if (objective == null || objective.getType() != ObjectiveType.USE) {
                    continue;
                }
                if (objective.getTarget() == null || !objective.getTarget().equalsIgnoreCase(target)) {
                    continue;
                }
                manager.addProgress(player, quest.getId(), objective.getTarget(), 1);
            }
        }
    }

    /**
     * Custom CRAFT targets by Aetherion item / recipe id (e.g. simple_pickaxe).
     */
    public static void noteCrafted(Player player, String target, int amount) {
        if (player == null || target == null || target.isBlank() || amount <= 0) {
            return;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return;
        }
        for (Quest quest : manager.getQuests()) {
            if (manager.getQuestState(player, quest) != QuestState.ACTIVE) {
                continue;
            }
            for (Objective objective : quest.getObjectives()) {
                if (objective == null || objective.getType() != ObjectiveType.CRAFT) {
                    continue;
                }
                if (objective.getTarget() == null || !objective.getTarget().equalsIgnoreCase(target)) {
                    continue;
                }
                manager.addProgress(player, quest.getId(), objective.getTarget(), amount);
            }
        }
    }

    /** Soft-query for other plugins (Manager Skills highlight, etc.). */
    public static boolean isQuestActive(Player player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return false;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return false;
        }
        Quest quest = find(manager, questId);
        if (quest == null) {
            return false;
        }
        QuestState state = manager.getQuestState(player, quest);
        return state == QuestState.ACTIVE || state == QuestState.READY;
    }

    /**
     * Pets tab blink only after Lark's equip lesson (post-catch return).
     */
    public static boolean shouldGuidePetsEquip(Player player) {
        if (player == null || !isQuestActive(player, "pocket_zoo")) {
            return false;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return false;
        }
        if (manager.getProgress(player, "pocket_zoo", "ANY") < 1) {
            return false;
        }
        if (manager.getProgress(player, "pocket_zoo", "AETHER_PET") >= 1) {
            return false;
        }
        try {
            AetherionQuests plugin = AetherionQuests.getInstance();
            return plugin != null
                    && plugin.getPlayerQuestStorage() != null
                    && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), "lark_equip_lesson");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isQuestCompleted(Player player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return false;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return false;
        }
        Quest quest = find(manager, questId);
        if (quest == null) {
            return false;
        }
        return manager.getQuestState(player, quest) == QuestState.COMPLETED;
    }

    /**
     * Harbour wood stays locked until the Forager finishes his chop lesson.
     * Quests missing → allow (singleplayer / offline tooling).
     */
    public static boolean canChopTrees(Player player) {
        if (player == null) {
            return false;
        }
        if (foragerDemoRunning(player)) {
            return false;
        }
        QuestManager manager = manager();
        if (manager == null) {
            return true;
        }
        try {
            if (de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, manager)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getPlayerQuestStorage() != null
                && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), "forager_chop_unlocked")) {
            return true;
        }
        Quest timber = find(manager, "gather_wood");
        if (timber == null) {
            return true;
        }
        QuestState state = manager.getQuestState(player, timber);
        if (state == QuestState.COMPLETED || state == QuestState.READY) {
            return true;
        }
        // Legacy softlock: already felled something on this quest before the gate existed.
        if (state == QuestState.ACTIVE && manager.getProgress(player, timber.getId(), "OAK_LOG") > 0) {
            unlockForagerChop(player);
            return true;
        }
        return false;
    }

    /** Called when the Forager chop demo finishes (or cannot play). */
    public static void unlockForagerChop(Player player) {
        if (player == null) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getPlayerQuestStorage() != null) {
            plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), "forager_chop_unlocked");
        }
    }

    private static boolean foragerDemoRunning(Player player) {
        de.aetherion.core.api.ForageAccess foraging = de.aetherion.core.api.AetherServices.foraging();
        return foraging != null && foraging.isChopDemoRunning(player);
    }

    private static Quest find(QuestManager manager, String questId) {
        for (Quest candidate : manager.getQuests()) {
            if (candidate != null && questId.equalsIgnoreCase(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private static QuestManager manager() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getQuestManager();
    }
}
