package de.aetherion.quests.command;

import de.aetherion.quests.lang.LangCode;
import de.aetherion.quests.lang.LangMenu;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.lang.PlayerLang;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /language · /sprache — pre-pre-beta quest/hint language pack.
 */
public final class LanguageCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            LangMenu.open(player, false);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") && player.hasPermission("aetherionquests.admin")) {
            LangPack.reload();
            player.sendMessage("§aLanguage pack reloaded. §7DE dialog overrides: §f" + LangPack.dialogOverrideCount());
            return true;
        }

        LangCode code = LangCode.parse(sub);
        if (args[0].equalsIgnoreCase("en")
                || args[0].equalsIgnoreCase("english")
                || args[0].equalsIgnoreCase("de")
                || args[0].equalsIgnoreCase("deutsch")
                || args[0].equalsIgnoreCase("german")) {
            PlayerLang.set(player, code);
            if (code.german()) {
                player.sendMessage("§6✦ §eSprache: §fDeutsch §8(§eexperimentell§8)");
            } else {
                player.sendMessage("§6✦ §eLanguage: §fEnglish §8(§afully supported§8)");
            }
            return true;
        }

        player.sendMessage("§7Usage: §f/" + label + " §8[§fen§8|§fde§8] §7or open the menu.");
        LangMenu.open(player, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("en", "de", "english", "deutsch", "reload")) {
                if (option.startsWith(p)) {
                    if (option.equals("reload") && !sender.hasPermission("aetherionquests.admin")) {
                        continue;
                    }
                    out.add(option);
                }
            }
        }
        return out;
    }
}
