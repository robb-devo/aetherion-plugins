package de.aetherion.quests.command;

import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

public class QuestCommand implements CommandExecutor {

    private final QuestManager questManager;

    public QuestCommand(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        /*
         * =========================================================
         * NO ARGUMENTS
         * =========================================================
         */

        if (args.length == 0) {

            sender.sendMessage("§bAetherionQuests");
            sender.sendMessage("§7/aquest accept <questId>");
            sender.sendMessage("§7/aquest deny <questId>");
            sender.sendMessage("§7/aquest start <questId>");
            sender.sendMessage("§7/aquest complete <questId>");
            sender.sendMessage("§7/aquest reset <questId|all>");
            sender.sendMessage("§7/aquest info <questId>");
            sender.sendMessage("§7/aquest cleanup");

            return true;
        }


        /*
         * =========================================================
         * ACCEPT QUEST
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("accept")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest accept <questId>"
                );

                return true;
            }


            Quest quest = questManager.getQuest(
                    args[1]
            );


            if (quest == null) {

                player.sendMessage(
                        "§cQuest not found."
                );

                return true;
            }


            if (questManager.hasQuest(
                    player,
                    quest
            )) {

                player.sendMessage(
                        "§eYou already have this quest."
                );

                return true;
            }


            questManager.startQuest(
                    player,
                    quest
            );


            player.sendMessage(
                    "§aQuest accepted: §f"
                            + quest.getTitle()
            );

            return true;
        }


        /*
         * =========================================================
         * DENY QUEST
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("deny")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest deny <questId>"
                );

                return true;
            }


            player.sendMessage(
                    "§cMaybe later."
            );

            return true;
        }


        /*
         * =========================================================
         * START QUEST
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("start")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest start <questId>"
                );

                return true;
            }


            Quest quest = questManager.getQuest(
                    args[1]
            );


            if (quest == null) {

                player.sendMessage(
                        "§cQuest not found: §f"
                                + args[1]
                );

                return true;
            }


            questManager.startQuest(
                    player,
                    quest
            );


            player.sendMessage(
                    "§aQuest started: §f"
                            + quest.getTitle()
            );

            return true;
        }


        /*
         * =========================================================
         * COMPLETE QUEST
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("complete")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest complete <questId>"
                );

                return true;
            }


            Quest quest = questManager.getQuest(
                    args[1]
            );


            if (quest == null) {

                player.sendMessage(
                        "§cQuest not found: §f"
                                + args[1]
                );

                return true;
            }


            QuestState state = questManager.getQuestState(
                    player,
                    quest
            );


            if (state == QuestState.COMPLETED) {

                player.sendMessage(
                        "§eQuest is already completed."
                );

                return true;
            }


            questManager.completeQuest(
                    player,
                    quest
            );


            player.sendMessage(
                    "§aQuest completed: §f"
                            + quest.getTitle()
            );

            return true;
        }


        /*
         * =========================================================
         * RESET QUEST
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("reset")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest reset <questId|all>"
                );

                return true;
            }

            if (args[1].equalsIgnoreCase("all")
                    || args[1].equalsIgnoreCase("*")) {
                questManager.resetAllQuests(player);
                player.sendMessage("§eAll quests reset. Harbour onboarding is fresh.");
                return true;
            }


            Quest quest = questManager.getQuest(
                    args[1]
            );


            if (quest == null) {

                player.sendMessage(
                        "§cQuest not found: §f"
                                + args[1]
                );

                return true;
            }


            questManager.resetQuest(
                    player,
                    quest
            );


            player.sendMessage(
                    "§eQuest reset: §f"
                            + quest.getTitle()
            );

            return true;
        }


        /*
         * =========================================================
         * QUEST INFO
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("info")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this."
                );

                return true;
            }


            if (args.length < 2) {

                player.sendMessage(
                        "§cUsage: /aquest info <questId>"
                );

                return true;
            }


            Quest quest = questManager.getQuest(
                    args[1]
            );


            if (quest == null) {

                player.sendMessage(
                        "§cQuest not found: §f"
                                + args[1]
                );

                return true;
            }


            QuestState state = questManager.getQuestState(
                    player,
                    quest
            );


            player.sendMessage(
                    "§b--- Quest Info ---"
            );

            player.sendMessage(
                    "§7ID: §f"
                            + quest.getId()
            );

            player.sendMessage(
                    "§7Title: §f"
                            + quest.getTitle()
            );

            player.sendMessage(
                    "§7State: §f"
                            + state
            );

            return true;
        }


        /*
         * =========================================================
         * NPC CLEANUP
         * =========================================================
         *
         * Entfernt die alten Quest-Villager im direkten Umfeld.
         *
         * WICHTIG:
         * Dieser Command verändert NICHT die aktuelle Registry
         * und NICHT die gespeicherten NPC-Daten.
         *
         * Er dient hier bewusst nur als Cleanup für alte/broken
         * Villager-Entities.
         *
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("cleanup")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cOnly players can use this command."
                );

                return true;
            }


            int removed = 0;


            for (Entity entity :
                    player.getNearbyEntities(5, 5, 5)) {

                if (!(entity instanceof Villager villager)) {
                    continue;
                }


                if (villager.getCustomName() == null) {
                    continue;
                }


                String name = villager
                        .getCustomName()
                        .replace("§b", "")
                        .replace("§f", "");


                if (name.equalsIgnoreCase("Fisherman")
                        || name.equalsIgnoreCase("Blacksmith")) {

                    villager.remove();
                    removed++;
                }
            }


            player.sendMessage(
                    "§aRemoved quest NPCs nearby: "
                            + removed
            );

            return true;
        }


        /*
         * =========================================================
         * UNKNOWN COMMAND
         * =========================================================
         */

        sender.sendMessage(
                "§cUnbekannter Command."
        );

        return true;
    }
}