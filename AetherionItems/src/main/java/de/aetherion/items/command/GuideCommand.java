package de.aetherion.items.command;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.guide.GuideAdvice;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.skill.AetherionLevel;
import de.aetherion.items.skill.SkillService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight guide — tutorial-aware via AetherionQuests, same tips as Discord.
 */
public final class GuideCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6✦ §eGuide §8» §7";

    private final AetherionItems plugin;

    public GuideCommand(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0 || equalsAny(args[0], "help", "how", "?")) {
            sendHow(player);
            sendNext(player);
            return true;
        }
        if (equalsAny(args[0], "next", "what", "todo")) {
            sendNext(player);
            return true;
        }
        if (equalsAny(args[0], "craft", "recipe", "item")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + "Usage: §f/" + label + " craft <name>");
                player.sendMessage(PREFIX + "Or open §f/atrecipes §7once crafting is unlocked.");
                return true;
            }
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            sendCraft(player, query);
            return true;
        }
        sendCraft(player, String.join(" ", args));
        return true;
    }

    private void sendHow(Player player) {
        player.sendMessage("§8§m----------§r §6✦ Aetherion Guide §8§m----------");
        player.sendMessage("§7Simple helper — not an AI. During orientation I follow");
        player.sendMessage("§7your §ftutorial quests§7 (Egon → …). Later I use unlocks.");
        player.sendMessage("");
        player.sendMessage("§e/guide§7 · §e/guide next §8— §7what to do now");
        player.sendMessage("§e/guide craft <name> §8— §7recipe ingredients");
        player.sendMessage("§e/guide how §8— §7this text");
        player.sendMessage("§7Discord: type §fguide §7in §f#guide §7(same tips).");
        player.sendMessage("§8§m--------------------------------");
    }

    private void sendNext(Player player) {
        GuideAdvice.Result result = GuideAdvice.next(player);
        SkillService skills = plugin.getSkills();
        int level = skills == null ? 1 : skills.accountLevel(player);
        player.sendMessage(PREFIX + "You are " + AetherionLevel.tag(level)
                + " §7" + AetherionLevel.coloredTitle(level));
        if (result.tutorial) {
            player.sendMessage(PREFIX + "§aOrientation §7— one step at a time:");
        } else {
            player.sendMessage(PREFIX + "Suggested next steps:");
        }
        int shown = 0;
        for (String tip : result.tips) {
            if (shown >= 4) {
                break;
            }
            player.sendMessage("  §6› §7" + tip);
            shown++;
        }
        player.sendMessage(PREFIX + "Craft: §e/guide craft <item> §8· §7Discord: §fguide §7in #guide");
    }

    private void sendCraft(Player player, String query) {
        ProgressionService progress = plugin.progress();
        if (progress != null && !progress.recipeBook(player)) {
            player.sendMessage(progress.hint(ProgressionService.Flag.WORKBENCH));
            player.sendMessage(PREFIX + "Preview anyway:");
        }
        for (String line : GuideAdvice.craftLines(query, 3)) {
            player.sendMessage(PREFIX + GuideAdvice.strip(line.replace("**", "").replace("`", "")));
        }
    }

    private static boolean equalsAny(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("next", "craft", "how").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
