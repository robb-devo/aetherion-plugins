package de.aetherion.items.command;

import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillMenu;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillService;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SkillCommand implements CommandExecutor, TabCompleter {

    private final SkillService skills;
    private final SkillMenu menu;

    public SkillCommand(SkillService skills, SkillMenu menu) {
        this.skills = skills;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (sender instanceof Player player) {
                menu.open(player);
            } else {
                sender.sendMessage("Usage: /skills");
            }
            return true;
        }
        if (!sender.hasPermission("aetherion.skills.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /skills <unlock|reset|setlevel> <player> ...");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }
        if (action.equals("unlock") || action.equals("grant")) {
            skills.grantAllSlots(target);
            sender.sendMessage("§aUnlocked all skill slots for §f" + target.getName() + "§a.");
            target.sendMessage("§aAll six skill slots just opened. Don't make it weird.");
            return true;
        }
        if (action.equals("reset")) {
            skills.reset(target);
            sender.sendMessage("§eReset loadout for §f" + target.getName() + "§e. Levels stayed.");
            target.sendMessage("§7Your equipped skills were cleared. The levels did not.");
            return true;
        }
        if (action.equals("wipe") || action.equals("wipelevels") || action.equals("resetlevels")) {
            skills.wipeProgress(target);
            sender.sendMessage("§eWiped all skill levels + loadout for §f" + target.getName() + "§e.");
            target.sendMessage("§eAll skill levels reset to 1. Loadout cleared — retest from scratch.");
            return true;
        }
        if (action.equals("setlevel") || action.equals("level")) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /skills setlevel <player> <skill|all> <1-60>");
                return true;
            }
            int level;
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cNot a number.");
                return true;
            }
            level = SkillProgression.clampLevel(level);
            if (args[2].equalsIgnoreCase("all")) {
                skills.setLevelAll(target, level);
                sender.sendMessage("§aSet every skill to Lv. " + level + " for §f" + target.getName() + "§a.");
                target.sendMessage("§aAll skills are now Lv. " + level + ". The jokes escalated.");
                return true;
            }
            AetherSkill skill = AetherSkill.byId(args[2]);
            if (skill == null) {
                sender.sendMessage("§cUnknown skill.");
                return true;
            }
            skills.setLevel(target, skill, level);
            sender.sendMessage("§aSet §f" + skill.displayName() + " §ato Lv. " + level + ".");
            target.sendMessage("§a" + skill.displayName() + " §7is now §fLv. " + level + "§7.");
            return true;
        }
        sender.sendMessage("§cUsage: /skills <unlock|reset|setlevel> <player> ...");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Stream<String> options = Stream.of("open");
            if (sender.hasPermission("aetherion.skills.admin")) {
                options = Stream.concat(options, Stream.of("unlock", "reset", "wipe", "setlevel"));
            }
            return options
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("aetherion.skills.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setlevel") && sender.hasPermission("aetherion.skills.admin")) {
            return Stream.concat(
                            Stream.of("all"),
                            Arrays.stream(AetherSkill.values()).map(AetherSkill::id)
                    )
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
