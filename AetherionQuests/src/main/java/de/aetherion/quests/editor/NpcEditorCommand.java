package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.HelpMenu;
import de.aetherion.quests.editor.gui.ListMenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class NpcEditorCommand implements CommandExecutor, TabCompleter {

    private final NpcEditor editor;

    public NpcEditorCommand(NpcEditor editor) {
        this.editor = editor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!NpcEditor.allowed(player)) {
            player.sendMessage("§cYou need §faetherion.npc.editor §cto use the NPC creator.");
            return true;
        }
        if (args.length == 0) {
            editor.openMain(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create", "new" -> {
                if (args.length >= 2) {
                    editor.createAt(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    editor.beginCreate(player);
                }
                yield true;
            }
            case "edit", "nearby" -> {
                CustomNpc npc = args.length >= 2
                        ? editor.resolve(args[1])
                        : editor.service().nearby(player, 8);
                if (npc == null) {
                    player.sendMessage("§eNo editor NPC " + (args.length >= 2 ? "named §f" + args[1] : "nearby") + "§e.");
                    player.sendMessage("§7Story NPCs (Egon, Twig, Miss Canopy) are not editable here.");
                } else {
                    editor.openEdit(player, npc);
                }
                yield true;
            }
            case "list" -> {
                ListMenu.open(player, 0);
                yield true;
            }
            case "delete", "remove" -> {
                CustomNpc npc = args.length >= 2
                        ? editor.resolve(args[1])
                        : editor.service().nearby(player, 6);
                if (npc == null) {
                    player.sendMessage("§eNothing to delete. Stand close or pass an id.");
                } else {
                    editor.delete(player, npc);
                }
                yield true;
            }
            case "move" -> {
                CustomNpc npc = args.length >= 2
                        ? editor.resolve(args[1])
                        : editor.service().nearby(player, 8);
                if (npc == null) {
                    player.sendMessage("§eNo editor NPC to move.");
                } else {
                    editor.moveHere(player, npc);
                }
                yield true;
            }
            case "duplicate", "copy" -> {
                CustomNpc npc = args.length >= 2
                        ? editor.resolve(args[1])
                        : editor.service().nearby(player, 8);
                if (npc == null) {
                    player.sendMessage("§eNo editor NPC to duplicate.");
                } else {
                    editor.duplicate(player, npc);
                }
                yield true;
            }
            case "wand" -> {
                editor.giveWand(player);
                yield true;
            }
            case "help" -> {
                HelpMenu.open(player);
                yield true;
            }
            default -> {
                editor.openMain(player);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !NpcEditor.allowed(player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of(
                    "create", "edit", "nearby", "list", "delete", "move", "duplicate", "wand", "help"
            ));
        }
        if (args.length == 2 && List.of("edit", "delete", "move", "duplicate", "remove", "copy")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[1], editor.storage().all().stream().flatMap(npc ->
                    Stream.of(npc.getId(), npc.getName())).toList());
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
