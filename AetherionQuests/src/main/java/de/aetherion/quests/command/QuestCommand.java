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

    private static boolean isQuestAdmin(CommandSender sender) {
        return sender.isOp()
                || sender.hasPermission("aetherionquests.admin")
                || sender.hasPermission("aetherion.dev");
    }

    private static boolean denyUnlessAdmin(CommandSender sender) {
        if (isQuestAdmin(sender)) {
            return false;
        }
        sender.sendMessage("§cDEV only.");
        return true;
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
            sender.sendMessage("§7/aquest info <questId>");
            if (isQuestAdmin(sender)) {
                sender.sendMessage("§8/aquest start|complete|reset|cleanup|orbit|chests §7(DEV)");
            }

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

            if (denyUnlessAdmin(sender)) {
                return true;
            }

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

            if (denyUnlessAdmin(sender)) {
                return true;
            }

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

            if (denyUnlessAdmin(sender)) {
                return true;
            }

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
         * OPENING ORBIT (DEV replay)
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("orbit") || args[0].equalsIgnoreCase("openingorbit")) {

            if (denyUnlessAdmin(sender)) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this.");
                return true;
            }

            de.aetherion.quests.ui.OpeningOrbit.replay(player);
            return true;
        }

        /*
         * =========================================================
         * EXPLORATION CHESTS (DEV)
         * =========================================================
         */

        if (args[0].equalsIgnoreCase("chests")) {

            if (denyUnlessAdmin(sender)) {
                return true;
            }

            de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
            de.aetherion.quests.chest.ExploreChestService chests = plugin == null ? null : plugin.getExploreChests();
            de.aetherion.quests.chest.ExploreChestScatter scatter = plugin == null ? null : plugin.getExploreScatter();
            if (chests == null || scatter == null) {
                sender.sendMessage("§cExploration chests are not running.");
                return true;
            }
            String sub = args.length < 2 ? "" : args[1].toLowerCase(java.util.Locale.ROOT);
            switch (sub) {
                case "give" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cOnly players can use this.");
                        return true;
                    }
                    org.bukkit.inventory.ItemStack placer = args.length < 3
                            ? null
                            : de.aetherion.quests.listener.ExploreChestListener.create(args[2]);
                    if (placer == null) {
                        player.sendMessage("§cUsage: /aquest chests give <common|uncommon|rare|epic|legendary|mythic>");
                        return true;
                    }
                    player.getInventory().addItem(placer).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    player.sendMessage("§aGave " + placer.getItemMeta().getDisplayName() + " §aplacer.");
                }
                case "scatter" -> {
                    String result = scatter.run(true);
                    sender.sendMessage(result == null || result.isBlank() ? "§7Nothing to scatter here." : result);
                }
                case "unscatter" -> sender.sendMessage(scatter.undo());
                default -> {
                    StringBuilder line = new StringBuilder("§bExploration chests§7:");
                    java.util.Map<de.aetherion.quests.chest.ExploreChestKind, Integer> counts = chests.counts();
                    int total = 0;
                    for (de.aetherion.quests.chest.ExploreChestKind kind : de.aetherion.quests.chest.ExploreChestKind.values()) {
                        int n = counts.getOrDefault(kind, 0);
                        total += n;
                        line.append(' ').append(kind.chat()).append(kind.name().charAt(0))
                                .append(kind.name().substring(1).toLowerCase(java.util.Locale.ROOT)).append(" §f").append(n);
                    }
                    sender.sendMessage(line.append(" §7· total §f").append(total).toString());
                    sender.sendMessage("§8/aquest chests give <kind> | scatter | unscatter §7("
                            + de.aetherion.quests.chest.ExploreChestScatter.spotCount() + " authored spots)");
                }
            }
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

            if (denyUnlessAdmin(sender)) {
                return true;
            }

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