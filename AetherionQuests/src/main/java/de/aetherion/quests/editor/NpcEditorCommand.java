package de.aetherion.quests.editor;

import de.aetherion.quests.editor.gui.ConfirmMenu;
import de.aetherion.quests.editor.gui.HelpMenu;
import de.aetherion.quests.editor.gui.HomeMenu;
import de.aetherion.quests.editor.gui.OverviewMenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code /npc} (aliases {@code /aethernpc}, {@code /npceditor}). No arguments opens the studio — the Dev Menu
 * relies on that. Subcommands are shortcuts; everything is also reachable from the menus.
 */
public final class NpcEditorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> VISIBLE = List.of(
            "create", "edit", "list", "delete", "move", "duplicate", "tp", "preview", "wand", "undo", "help");
    private static final Set<String> TAKES_NPC = Set.of(
            "edit", "nearby", "open", "delete", "remove", "move", "duplicate", "copy", "tp", "goto", "preview", "talk");

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
            player.sendMessage("§cYou need §faetherion.npc.editor §cto use the NPC Studio.");
            return true;
        }
        if (args.length == 0) {
            HomeMenu.open(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String rest = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
        switch (action) {
            case "create", "new" -> {
                if (rest != null) {
                    editor.createAt(player, rest);
                } else {
                    editor.beginCreate(player);
                }
            }
            case "edit", "nearby", "open" -> {
                CustomNpc npc = target(player, rest, 8);
                if (npc != null) {
                    OverviewMenu.open(player, npc.getId());
                }
            }
            case "list" -> HomeMenu.open(player);
            case "delete", "remove" -> {
                CustomNpc npc = target(player, rest, 6);
                if (npc != null) {
                    ConfirmMenu.deleteNpc(player, npc.getId(), () -> HomeMenu.open(player));
                }
            }
            case "move" -> {
                CustomNpc npc = target(player, rest, 8);
                if (npc != null) {
                    editor.moveHere(player, npc);
                }
            }
            case "duplicate", "copy" -> {
                CustomNpc npc = target(player, rest, 8);
                if (npc != null) {
                    CustomNpc copy = editor.duplicate(player, npc);
                    if (copy != null) {
                        OverviewMenu.open(player, copy.getId());
                    }
                }
            }
            case "tp", "goto" -> {
                CustomNpc npc = target(player, rest, 8);
                if (npc != null) {
                    editor.teleportTo(player, npc);
                }
            }
            case "preview", "talk" -> {
                CustomNpc npc = target(player, rest, 8);
                if (npc != null) {
                    String id = npc.getId();
                    editor.preview(player, npc, null, null, () -> OverviewMenu.open(player, id));
                }
            }
            case "wand" -> editor.giveWand(player);
            case "help" -> HelpMenu.open(player, () -> HomeMenu.open(player));
            case "undo" -> editor.undo(player, null);
            case "cancel" -> {
                if (editor.prompting(player)) {
                    editor.cancelInput(player, true);
                } else {
                    player.sendMessage("§7Nothing to cancel.");
                }
            }
            case "done" -> {
                if (editor.prompting(player)) {
                    editor.finishInput(player);
                } else {
                    player.sendMessage("§7Nothing to finish.");
                }
            }
            case "back" -> editor.back(player);
            default -> {
                player.sendMessage("§7Unknown option §f" + args[0] + "§7 — opening the studio. §8(/npc help)");
                HomeMenu.open(player);
            }
        }
        return true;
    }

    /** NPC by id / name, or the nearest one within {@code radius} blocks. Explains itself when nothing matches. */
    private CustomNpc target(Player player, String query, double radius) {
        CustomNpc npc = query != null ? editor.resolve(query) : editor.service().nearby(player, radius);
        if (npc == null) {
            if (query != null) {
                editor.error(player, "No studio NPC called §f" + query + "§c. §7(/npc list shows them all)");
            } else {
                editor.error(player, "No studio NPC within " + (int) radius + " blocks. §7Name one, e.g. §f/npc edit Bob");
            }
            player.sendMessage("§8Story NPCs (Egon, Twig, Miss Canopy, …) are protected and can't be edited here.");
        }
        return npc;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !NpcEditor.allowed(player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], VISIBLE);
        }
        if (args.length == 2 && TAKES_NPC.contains(args[0].toLowerCase(Locale.ROOT))) {
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
