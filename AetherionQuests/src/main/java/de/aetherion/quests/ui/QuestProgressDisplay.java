package de.aetherion.quests.ui;


import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.BarSync;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class QuestProgressDisplay {


    private static final Map<UUID, BossBar> bars = new HashMap<>();
    private static final Map<UUID, BukkitTask> progressTasks = new HashMap<>();
    private static final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();


    public static void showQuestCompass(Player player, QuestManager questManager) {
        showProgress(player, questManager);
    }


    public static void suppress(Player player) {
        if (player == null) {
            return;
        }
        suppressed.add(player.getUniqueId());
        BossBar bar = bars.get(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }


    public static void unsuppress(Player player) {
        if (player == null) {
            return;
        }
        if (!suppressed.remove(player.getUniqueId())) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getQuestManager() != null) {
            showProgress(player, plugin.getQuestManager());
        }
    }

    public static void unsuppress(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (!suppressed.remove(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getQuestManager() != null) {
            showProgress(player, plugin.getQuestManager());
        }
    }


    public static void showProgress(Player player, QuestManager questManager) {

        if (player == null || questManager == null) {
            return;
        }

        if (suppressed.contains(player.getUniqueId())) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar != null) {
                bar.removePlayer(player);
                bar.setVisible(false);
            }
            return;
        }

        Quest quest = questManager.getTrackedQuest(player);

        if (quest == null) {
            quest = findActiveQuest(player, questManager);
        }

        if (quest == null) {
            remove(player);
            return;
        }

        BossBar bar = getOrCreateBar(player);
        BarSync.style(bar, BarStyle.SOLID);
        BarSync.visible(bar, true);

        Step step = step(player, questManager, quest);
        String title = de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle());
        String line = step.line().isEmpty() ? "§f" + title : "§f" + title + " §8• " + step.line();
        paint(player, bar, line, step.progress(), step.color());

    }

    /** What the quest bar shows after the title, how full the bar is and its colour. */
    public record Step(String line, double progress, BarColor color) {
    }

    /**
     * The step {@code quest} is on for {@code player}, exactly as the quest bar shows it: objectives with their
     * counts, "Return to …" once it is ready, the guided specials' own steps. Also read by the Aether Journal.
     */
    public static Step step(Player player, QuestManager questManager, Quest quest) {

        QuestState state = questManager.getQuestState(player, quest);

        if (state == QuestState.READY) {
            String npcName = turnInName(quest);
            String ret = de.aetherion.quests.lang.LangPack.progress(player, "return_to", "Return to {0}");
            try {
                ret = java.text.MessageFormat.format(ret, npcName);
            } catch (IllegalArgumentException ignored) {
                ret = "Return to " + npcName;
            }
            return new Step("§a" + ret, 1.0, BarColor.GREEN);
        }

        // Pocket Zoo: show one step at a time (catch → talk to Lark → equip).
        if ("pocket_zoo".equalsIgnoreCase(quest.getId())) {
            return pocketZooStep(player, questManager, quest);
        }

        // Border Rites: vial first → altar arrow + spawn prompt.
        if ("border_rites".equalsIgnoreCase(quest.getId())) {
            return borderRitesStep(player, questManager, quest);
        }

        int totalRequired = 0;
        int totalProgress = 0;
        StringBuilder objectiveText = new StringBuilder();

        for (Objective objective : quest.getObjectives()) {

            if (objective == null) {
                continue;
            }

            int required = Math.max(0, objective.getAmount());
            int current = Math.min(
                    Math.max(0, questManager.getProgress(player, quest.getId(), objective.getTarget())),
                    required
            );

            totalRequired += required;
            totalProgress += current;

            if (objectiveText.length() > 0) {
                objectiveText.append(" §8• ");
            }

            String label = objective.getDisplayName();
            if (label != null && label.indexOf('§') >= 0) {
                objectiveText.append(label);
            } else {
                objectiveText.append("§f").append(label);
            }
            objectiveText
                    .append(" ")
                    .append(current)
                    .append("§7/")
                    .append(required);

        }

        double progress = totalRequired <= 0
                ? 1.0
                : Math.min(1.0, Math.max(0.0, (double) totalProgress / totalRequired));

        BarColor color = progress < 0.34 ? BarColor.RED : progress < 0.67 ? BarColor.YELLOW : BarColor.GREEN;
        return new Step(totalRequired <= 0 ? "" : objectiveText.toString(), progress, color);

    }

    /**
     * Quest frame: bronze rails, scroll socket. The icon follows the state the colour already tells —
     * a tick once it's time to hand in, a star on the guided specials (purple), the scroll otherwise.
     * Change-only: this runs every progress tick and with the compass line.
     */
    private static void paint(Player player, BossBar bar, String title, double progress, BarColor color) {
        BarChrome.Icon icon = progress >= 1.0 && color == BarColor.GREEN ? BarChrome.Icon.QUEST_READY
                : color == BarColor.PURPLE ? BarChrome.Icon.QUEST_SPECIAL
                : BarChrome.Icon.QUEST;
        BarSync.title(bar, BarChrome.legacy(BarChrome.Family.QUEST, icon,
                withCompass(player, de.aetherion.core.api.JournalText.t(title))));
        BarSync.progress(bar, progress);
        BarSync.color(bar, color);
    }


    private static Step borderRitesStep(
            Player player,
            QuestManager questManager,
            Quest quest
    ) {
        int riteProgress = questManager.getProgress(player, quest.getId(), "BORDERLANDS_RITE");
        if (riteProgress >= 1) {
            String ret = de.aetherion.quests.lang.LangPack.progress(player, "return_to", "Return to {0}");
            try {
                ret = java.text.MessageFormat.format(ret, turnInName(quest));
            } catch (IllegalArgumentException ignored) {
                ret = "Return to " + turnInName(quest);
            }
            return new Step("§a" + ret, 1.0, BarColor.GREEN);
        }
        if (!hasBorderlandsSpiritVial(player)) {
            return new Step("§c" + de.aetherion.quests.lang.LangPack.progress(
                    player, "obtain_vial", "Obtain a spirit vial"), 0.25, BarColor.RED);
        }
        return new Step("§e" + de.aetherion.quests.lang.LangPack.progress(
                player, "spawn_boss", "Spawn a boss"), 0.6, BarColor.YELLOW);
    }

    static boolean hasBorderlandsSpiritVial(Player player) {
        if (player == null) {
            return false;
        }
        try {
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            if (de.aetherion.items.world.BorderlandsRiteService.isSpirit(inv.getItemInOffHand())) {
                return true;
            }
            for (org.bukkit.inventory.ItemStack stack : inv.getStorageContents()) {
                if (de.aetherion.items.world.BorderlandsRiteService.isSpirit(stack)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Step pocketZooStep(
            Player player,
            QuestManager questManager,
            Quest quest
    ) {
        int catchProgress = questManager.getProgress(player, quest.getId(), "ANY");
        int equipProgress = questManager.getProgress(player, quest.getId(), "AETHER_PET");
        boolean equipLesson = false;
        try {
            AetherionQuests plugin = AetherionQuests.getInstance();
            equipLesson = plugin != null
                    && plugin.getPlayerQuestStorage() != null
                    && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), "lark_equip_lesson");
        } catch (Throwable ignored) {
        }

        if (catchProgress < 1) {
            return new Step("§f" + de.aetherion.quests.lang.LangPack.progress(player, "catch_pet", "Catch a pet")
                    + " §e0§7/§f1", 0.25, BarColor.YELLOW);
        }
        if (!equipLesson || equipProgress < 1) {
            if (!equipLesson) {
                return new Step("§a" + de.aetherion.quests.lang.LangPack.progress(player, "talk_to_lark", "Talk to Lark"),
                        0.55, BarColor.GREEN);
            }
            return new Step("§d" + de.aetherion.quests.lang.LangPack.progress(player, "equip_pet", "Equip a Pet")
                    + " §e" + Math.min(1, equipProgress) + "§7/§f1", 0.75, BarColor.PURPLE);
        }
        return new Step("§a" + de.aetherion.quests.lang.LangPack.progress(player, "return_to_lark", "Return to Lark"),
                1.0, BarColor.GREEN);
    }


    private static String withCompass(Player player, String title) {

        AetherionQuests plugin = AetherionQuests.getInstance();

        if (plugin == null || plugin.getQuestCompass() == null) {
            return title;
        }

        String line = plugin.getQuestCompass().line(player);

        if (line == null || line.isBlank()) {
            return title;
        }

        return title + " §8• " + line;

    }


    private static Quest findActiveQuest(Player player, QuestManager questManager) {

        for (Quest quest : questManager.getQuests()) {
            if (quest == null) {
                continue;
            }

            QuestState state = questManager.getQuestState(player, quest);
            if (state == QuestState.ACTIVE || state == QuestState.READY) {
                return quest;
            }
        }

        return null;

    }


    /** Who takes {@code quest} back: its turn-in NPC, else the NPC that gives it. */
    public static String turnInName(Quest quest) {

        if (quest != null && quest.hasTurnInNpc()) {
            QuestNPC turnIn = QuestNPCRegistry.getNPC(quest.getTurnInNpcId());
            if (turnIn != null && turnIn.getName() != null && !turnIn.getName().isBlank()) {
                return turnIn.getName();
            }
        }

        QuestNPC npc = QuestNPCRegistry.findForQuest(quest);

        if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
            return npc.getName();
        }

        return "Quest NPC";

    }


    private static BossBar getOrCreateBar(Player player) {

        UUID uuid = player.getUniqueId();
        BossBar bar = bars.get(uuid);

        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            bars.put(uuid, bar);
        }

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        return bar;

    }


    private static void cancelProgressTask(Player player) {

        BukkitTask task = progressTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }

    }


    public static void remove(Player player) {

        if (player == null) {
            return;
        }

        cancelProgressTask(player);

        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        suppressed.remove(player.getUniqueId());

    }


    public static void removeAll() {

        for (BossBar bar : bars.values()) {
            if (bar != null) {
                bar.removeAll();
            }
        }

        bars.clear();
        suppressed.clear();

        for (BukkitTask task : progressTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        progressTasks.clear();

    }

}
