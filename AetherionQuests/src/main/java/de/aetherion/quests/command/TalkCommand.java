package de.aetherion.quests.command;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.LivingNpcSkins;
import de.aetherion.quests.npc.NpcMemory;
import de.aetherion.quests.talk.TalkUx;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /npctalk [world|classic]} — pick in-world bubbles + reply chips, or the classic
 * chat + chest flow. Admins: {@code /npctalk status}, {@code /npctalk reload}.
 */
public final class TalkCommand implements CommandExecutor, TabCompleter {

    private final AetherionQuests plugin;

    public TalkCommand(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        NpcMemory memory = NpcMemory.get();
        switch (sub) {
            case "world", "bubbles", "on" -> {
                if (!(sender instanceof Player player) || memory == null) {
                    return true;
                }
                memory.setClassic(player.getUniqueId(), false);
                player.sendMessage("§aNPC talk: in-world. §7Speech bubbles + reply chips at the NPC.");
                return true;
            }
            case "classic", "chest", "off" -> {
                if (!(sender instanceof Player player) || memory == null) {
                    return true;
                }
                memory.setClassic(player.getUniqueId(), true);
                TalkUx talk = TalkUx.get();
                if (talk != null) {
                    talk.end(player);
                }
                player.sendMessage("§eNPC talk: classic. §7Chat lines + the quest chest. §8/npctalk world to switch back.");
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("aetherionquests.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                LivingNpcSkins skins = LivingNpcSkins.get();
                sender.sendMessage("§6NPC talk §8· §f" + (TalkUx.get() != null ? "running" : "off")
                        + " §8· §7memory players: §f" + (memory == null ? 0 : memory.knownPlayers()));
                sender.sendMessage("§6Cast skins §8· §f" + (skins == null ? "off" : skins.status()));
                if (skins != null) {
                    List<String> pending = new ArrayList<>();
                    for (String id : skins.castIds()) {
                        if (!skins.isSigned(id)) {
                            pending.add(id);
                        }
                    }
                    if (!pending.isEmpty()) {
                        sender.sendMessage("§7Waiting on MineSkin: §f" + String.join(", ", pending));
                        sender.sendMessage("§8Tip: a free MineSkin API key in plugins/FancyNpcs/config.yml (mineskin_api_key) makes this fast.");
                    }
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("aetherionquests.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                plugin.reloadConfig();
                TalkUx talk = TalkUx.get();
                if (talk != null) {
                    talk.reload();
                }
                sender.sendMessage("§aTalk UX config reloaded.");
                return true;
            }
            default -> {
                boolean classic = sender instanceof Player p && memory != null && memory.prefersClassic(p.getUniqueId());
                sender.sendMessage("§6NPC talk §8· §7now: §f" + (classic ? "classic (chat + chest)" : "in-world (bubbles + reply chips)"));
                sender.sendMessage("§7/npctalk world §8· §7/npctalk classic");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> out = new ArrayList<>(List.of("world", "classic"));
        if (sender.hasPermission("aetherionquests.admin")) {
            out.add("status");
            out.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }
}
