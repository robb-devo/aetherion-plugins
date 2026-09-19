package de.aetherion.bossengine.command;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.item.SpawnItemDefinition;
import de.aetherion.bossengine.model.BossTemplate;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BossCommand implements CommandExecutor, TabCompleter {

    private final BossEngine plugin;

    public BossCommand(BossEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bossengine.admin")) {
            sender.sendMessage(TextUtil.component("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "kill" -> kill(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "info" -> info(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.component("&cPlayers only."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.component("&cUsage: /boss spawn <bossId>"));
            return;
        }
        Optional<BossInstance> spawned = plugin.getBossManager().spawn(
                args[1],
                player.getLocation(),
                SpawnCause.COMMAND,
                player
        );
        if (spawned.isEmpty()) {
            boolean known = plugin.getBossManager().getTemplates().get(args[1]).isPresent();
            sender.sendMessage(TextUtil.component(known
                    ? "&cSpawn failed. Max instances, WorldGuard, or the entity type refused."
                    : "&cUnknown boss id &f" + args[1] + "&c. Try &f/boss list&c."));
            return;
        }
        sender.sendMessage(TextUtil.component("&aSpawned &r" + spawned.get().getTemplate().getDisplayName()
                + " &7(" + (int) spawned.get().getCombatHealth()
                + "/" + (int) spawned.get().getCombatMaxHealth() + " HP)"));
    }

    private void kill(CommandSender sender, String[] args) {
        List<BossInstance> targets;
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            targets = new ArrayList<>(plugin.getBossManager().getActive());
        } else if (args.length >= 2) {
            targets = new ArrayList<>(plugin.getBossManager().getByTemplate(args[1]));
        } else if (sender instanceof Player player) {
            targets = plugin.getBossManager().getActive().stream()
                    .filter(instance -> instance.getEntity() != null
                            && instance.getEntity().getWorld().equals(player.getWorld())
                            && instance.getEntity().getLocation().distanceSquared(player.getLocation()) <= 16 * 16)
                    .toList();
        } else {
            sender.sendMessage(TextUtil.component("&cUsage: /boss kill <bossId|all>"));
            return;
        }

        if (targets.isEmpty()) {
            sender.sendMessage(TextUtil.component("&cNo matching living boss."));
            return;
        }

        targets.forEach(instance -> plugin.getBossManager().despawn(
                instance,
                BossDespawnEvent.Reason.COMMAND,
                null
        ));
        sender.sendMessage(TextUtil.component("&aRemoved " + targets.size() + " boss instance(s)."));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(TextUtil.component("&5Templates:"));
        for (BossTemplate template : plugin.getBossManager().getTemplates().getAll()) {
            long live = plugin.getBossManager().countActive(template.getId());
            sender.sendMessage(TextUtil.component(
                    "&8- &f" + template.getId() + " &7(" + live + " live) &8" + template.getEntityType().name()
            ));
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadEngine();
        sender.sendMessage(TextUtil.component("&aBossEngine reloaded."));
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        String itemId;

        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[1]);
            itemId = args[2];
        } else if (args.length == 2 && sender instanceof Player player) {
            target = player;
            itemId = args[1];
        } else if (sender instanceof Player player) {
            target = player;
            itemId = plugin.getSpawnItemService().getAll().stream()
                    .map(SpawnItemDefinition::getId)
                    .findFirst()
                    .orElse(null);
        } else {
            sender.sendMessage(TextUtil.component("&cUsage: /boss give [player] [itemId]"));
            return;
        }

        if (target == null) {
            sender.sendMessage(TextUtil.component("&cPlayer not found."));
            return;
        }

        Optional<SpawnItemDefinition> definition = plugin.getSpawnItemService().get(itemId);
        if (definition.isEmpty()) {
            sender.sendMessage(TextUtil.component("&cUnknown spawn item. Try: hollow_lurker_anchor"));
            return;
        }

        target.getInventory().addItem(plugin.getSpawnItemService().create(definition.get()));
        sender.sendMessage(TextUtil.component("&aGave spawn item &f" + definition.get().getId() + " &ato " + target.getName()));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.component("&cUsage: /boss info <bossId>"));
            return;
        }
        Optional<BossTemplate> template = plugin.getBossManager().getTemplates().get(args[1]);
        if (template.isEmpty()) {
            sender.sendMessage(TextUtil.component("&cUnknown boss."));
            return;
        }
        BossTemplate boss = template.get();
        sender.sendMessage(TextUtil.component("&5" + boss.getDisplayName()));
        sender.sendMessage(TextUtil.component("&7Id: &f" + boss.getId()));
        sender.sendMessage(TextUtil.component("&7Type: &f" + boss.getEntityType().name()));
        sender.sendMessage(TextUtil.component("&7Health: &f" + boss.getAttributes().getMaxHealth()));
        sender.sendMessage(TextUtil.component("&7Phases: &f" + boss.getPhases().size()));
        sender.sendMessage(TextUtil.component("&7Skills: &f" + boss.allSkills().size()));
        sender.sendMessage(TextUtil.component("&7Max instances: &f" + boss.getConditions().getMaxInstances()));
        sender.sendMessage(TextUtil.component("&7Leash: &f" + boss.getConditions().getLeashRadius()
                + " &7" + boss.getConditions().getLeashAction().name()));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.component("&5BossEngine"));
        sender.sendMessage(TextUtil.component("&8/boss spawn <id> &7- spawn at your location"));
        sender.sendMessage(TextUtil.component("&8/boss kill [id|all] &7- remove living bosses"));
        sender.sendMessage(TextUtil.component("&8/boss list"));
        sender.sendMessage(TextUtil.component("&8/boss info <id>"));
        sender.sendMessage(TextUtil.component("&8/boss give [player] [item] &7- spawn/anchor items"));
        sender.sendMessage(TextUtil.component("&8  hollow_lurker_anchor &7locks his cave home"));
        sender.sendMessage(TextUtil.component("&8/boss reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bossengine.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("spawn", "kill", "list", "reload", "give", "info"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("kill"))) {
            List<String> ids = new ArrayList<>();
            plugin.getBossManager().getTemplates().getAll().forEach(template -> ids.add(template.getId()));
            if (args[0].equalsIgnoreCase("kill")) {
                ids.add("all");
            }
            return filter(args[1], ids);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            plugin.getSpawnItemService().getAll().forEach(item -> names.add(item.getId()));
            return filter(args[1], names);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(args[2], plugin.getSpawnItemService().getAll().stream().map(SpawnItemDefinition::getId).toList());
        }
        return List.of();
    }

    private List<String> filter(String token, List<String> options) {
        String needle = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }
}
