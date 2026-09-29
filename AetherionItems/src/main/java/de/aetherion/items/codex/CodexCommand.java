package de.aetherion.items.codex;

import de.aetherion.items.skill.AetherSkill;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code /codex}, {@code /collection}, {@code /bestiary}.
 *
 * <pre>
 * /codex                         hub
 * /codex collection|bestiary [category]
 * /codex journal | milestones
 * /codex claim <key> | claimall  (the chat [CLAIM] buttons run these)
 * /codex view <key>              one entry's tier ladder
 * /codex dev <player> set <key> <n> | resetclaims | info    (aetherion.dev)
 * /collection [category]  ·  /bestiary [category]
 * </pre>
 */
public final class CodexCommand implements CommandExecutor, TabCompleter {

    private static final String DEV = "aetherion.dev";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length > 0 && args[0].equalsIgnoreCase("dev")) {
            return dev(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only (try /codex dev).");
            return true;
        }
        if (name.equals("collection")) {
            return ledger(player, CodexChrome.Tab.COLLECTION, args.length > 0 ? args[0] : null);
        }
        if (name.equals("bestiary")) {
            return ledger(player, CodexChrome.Tab.BESTIARY, args.length > 0 ? args[0] : null);
        }
        if (args.length == 0) {
            CodexMenus.openHub(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "collection", "collections", "c" -> ledger(player, CodexChrome.Tab.COLLECTION, args.length > 1 ? args[1] : null);
            case "bestiary", "mobs", "b" -> ledger(player, CodexChrome.Tab.BESTIARY, args.length > 1 ? args[1] : null);
            case "journal", "bosses" -> CodexMenus.openGated(player, CodexChrome.Tab.JOURNAL);
            case "milestones", "perks" -> CodexMenus.openMilestones(player);
            case "claimall" -> CodexRewards.claimAll(player);
            case "claim" -> {
                if (args.length < 2) {
                    CodexRewards.claimAll(player);
                } else {
                    CodexRewards.claim(player, args[1]);
                    refreshOpen(player);
                }
            }
            case "view" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /codex view <entry>");
                } else if (args[1].startsWith("s:")) {
                    de.aetherion.items.skill.SkillOverviewGUI.open(player);
                } else {
                    CodexMenus.openDetail(player, args[1], null);
                }
            }
            default -> {
                player.sendMessage("§d/codex §7— the hub · §f/collection §7· §f/bestiary §7· §f/codex journal §7· §f/codex milestones §7· §f/codex claimall");
            }
        }
        return true;
    }

    private static boolean ledger(Player player, CodexChrome.Tab tab, String rawCategory) {
        String locked = CodexChrome.gate(player, tab);
        if (locked != null) {
            player.sendMessage(locked);
            CodexChrome.deny(player);
            return true;
        }
        String category = rawCategory == null ? null : category(tab, rawCategory);
        if (tab == CodexChrome.Tab.COLLECTION) {
            CodexMenus.openCollection(player, category);
        } else {
            CodexMenus.openBestiary(player, category);
        }
        return true;
    }

    /** Accepts ids ({@code ores}) and titles ({@code dirt & sand} → {@code dirt}). */
    private static String category(CodexChrome.Tab tab, String raw) {
        List<String> tabs = tab == CodexChrome.Tab.COLLECTION ? CodexCatalog.COLLECTION_TABS : CodexCatalog.BESTIARY_TABS;
        String wanted = raw.toLowerCase(Locale.ROOT);
        for (String id : tabs) {
            String title = CodexCatalog.categoryTitle(id).toLowerCase(Locale.ROOT);
            if (id.equals(wanted) || title.equals(wanted) || title.startsWith(wanted)) {
                return id;
            }
        }
        return null;
    }

    /** A chat [CLAIM] clicked while a Codex page is open should show up on it. */
    private static void refreshOpen(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof CodexView view) {
            view.render(player);
        }
    }

    // ------------------------------------------------------------------ dev

    private static boolean dev(CommandSender sender, String[] args) {
        if (!sender.hasPermission(DEV)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /codex dev <player> <set <key> <n> | resetclaims | info>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        CodexService service = CodexRewards.service();
        if (target == null || service == null) {
            sender.sendMessage("§cPlayer must be online (and the Codex loaded).");
            return true;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /codex dev <player> set <key> <n>  §7(keys: c:coal, b:ZOMBIE, b:boss:mcnugget)");
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[4]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage("§cNot a number.");
                    return true;
                }
                if (CodexBook.card(args[3]) == null) {
                    sender.sendMessage("§cUnknown key " + args[3] + ".");
                    return true;
                }
                service.setCount(target, args[3], amount);
                sender.sendMessage("§aSet §f" + args[3] + " §ato §f" + amount + " §afor §f" + target.getName()
                        + " §8(no notices; claims untouched)");
            }
            case "resetclaims" -> {
                service.resetClaims(target.getUniqueId());
                sender.sendMessage("§eCleared every claimed tier and seal for §f" + target.getName() + "§e. Counts stay.");
            }
            case "info" -> {
                CodexBook.Summary collection = CodexBook.summary(service, target, CodexBook.Ledger.COLLECTION);
                CodexBook.Summary bestiary = CodexBook.summary(service, target, CodexBook.Ledger.BESTIARY);
                sender.sendMessage("§d" + target.getName() + " §7Codex: §aCollection L" + collection.level()
                        + " §8(" + collection.found() + "/" + collection.total() + ", " + collection.claimableTiers() + " ready)"
                        + " §6Bestiary L" + bestiary.level()
                        + " §8(" + bestiary.found() + "/" + bestiary.total() + ", " + bestiary.claimableTiers() + " ready)"
                        + " §7score §d" + CodexHubGUI.score(target)
                        + " §7claimable total §e" + CodexRewards.claimableTotal(target));
            }
            default -> sender.sendMessage("§cUsage: /codex dev <player> <set <key> <n> | resetclaims | info>");
        }
        return true;
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("collection") || name.equals("bestiary")) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> tabs = name.equals("collection") ? CodexCatalog.COLLECTION_TABS : CodexCatalog.BESTIARY_TABS;
            return filter(tabs.stream(), args[0]);
        }
        if (args.length == 1) {
            Stream<String> options = Stream.of("collection", "bestiary", "journal", "milestones", "claimall", "claim", "view");
            if (sender.hasPermission(DEV)) {
                options = Stream.concat(options, Stream.of("dev"));
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("collection") || sub.equals("bestiary"))) {
            return filter((sub.equals("collection") ? CodexCatalog.COLLECTION_TABS : CodexCatalog.BESTIARY_TABS).stream(), args[1]);
        }
        if (args.length == 2 && (sub.equals("claim") || sub.equals("view"))) {
            return filter(keys().stream(), args[1]);
        }
        if (sub.equals("dev") && sender.hasPermission(DEV)) {
            if (args.length == 2) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
            }
            if (args.length == 3) {
                return filter(Stream.of("set", "resetclaims", "info"), args[2]);
            }
            if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                return filter(keys().stream().filter(key -> !key.startsWith("s:")), args[3]);
            }
        }
        return List.of();
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (CodexBook.Ledger ledger : CodexBook.Ledger.values()) {
            for (CodexBook.Card card : CodexBook.cards(ledger, CodexCatalog.ALL)) {
                keys.add(card.key());
            }
        }
        for (AetherSkill skill : AetherSkill.values()) {
            keys.add("s:" + skill.id());
        }
        return keys;
    }

    private static List<String> filter(Stream<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(80).toList();
    }
}
