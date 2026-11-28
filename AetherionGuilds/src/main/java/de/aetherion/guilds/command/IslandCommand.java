package de.aetherion.guilds.command;

import de.aetherion.guilds.island.Highlight;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.menu.BiomeSelectMenu;
import de.aetherion.guilds.menu.IslandMenu;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class IslandCommand implements CommandExecutor, TabCompleter {

    private final PersonalIslandService islands;
    private final IslandMenu menu;
    private final BiomeSelectMenu biomes;
    private Highlight highlight;

    public IslandCommand(PersonalIslandService islands, IslandMenu menu, BiomeSelectMenu biomes) {
        this.islands = islands;
        this.menu = menu;
        this.biomes = biomes;
    }

    public void attachHighlight(Highlight highlight) {
        this.highlight = highlight;
    }

    private java.util.function.Supplier<List<String>> funnel = List::of;

    private de.aetherion.guilds.island.DockService dock;

    /** {@code /island admin stage <0-3>}: put your own island on a stage (testing the ladder). */
    public void attachDock(de.aetherion.guilds.island.DockService dock) {
        this.dock = dock;
    }

    /** {@code /island admin funnel}: how far islands got (first hut, belt, quarry, Hub, machine, upgrade...). */
    public void attachFunnel(java.util.function.Supplier<List<String>> funnel) {
        this.funnel = funnel == null ? List::of : funnel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("puppet")) {
                puppet(sender, args);
                return true;
            }
            sender.sendMessage("Players only.");
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("admin") && args.length >= 2 && args[1].equalsIgnoreCase("puppet")) {
            puppet(sender, args);
            return true;
        }
        if (action.equals("admin")) {
            admin(player, args);
            return true;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            if (highlight != null && (action.isEmpty() || action.equals("create") || action.equals("claim")
                    || action.equals("menu"))) {
                highlight.starterMenu().open(player);
            } else {
                player.sendMessage(AetherionItemsAccess.islandHint());
            }
            return true;
        }
        PersonalIsland own = islands.byOwner(player.getUniqueId());
        if (args.length == 0) {
            // An island you already have is never claimed or rebuilt again: /island (/insel) takes you home.
            // The menu is /island menu.
            if (own != null) {
                islands.goHome(player);
            } else {
                menu.open(player);
            }
            return true;
        }
        IslandHost host = own == null ? null : IslandHost.personal(own.ownerId());
        switch (action) {
            case "create", "claim", "starter" -> {
                if (own != null) {
                    player.sendMessage("§cYou already have an island. Use §f/island home§c.");
                    return true;
                }
                if (highlight != null) {
                    highlight.starterMenu().open(player);
                } else {
                    biomes.open(player);
                }
            }
            case "home", "tp", "go" -> islands.goHome(player);
            case "menu" -> menu.open(player);
            case "upgrade", "tier" -> islands.upgradeIsland(player);
            case "build", "blueprints", "tool" -> {
                if (needIsland(player, host)) {
                    // on your island: the Build Tool straight into your hand (F shows every part); elsewhere the picker
                    if (host.equals(highlight.hosts().at(player.getLocation()))) {
                        highlight.placement().openTool(player, host, null);
                    } else {
                        highlight.buildMenu().open(player, host);
                    }
                }
            }
            case "land", "expand" -> {
                if (needIsland(player, host)) {
                    highlight.landMenu().open(player, host);
                }
            }
            case "belts", "rails", "conveyor" -> {
                if (needIsland(player, host)) {
                    highlight.placement().startBelts(player, host);
                }
            }
            case "border" -> {
                if (needIsland(player, host)) {
                    highlight.land().flashBorder(player, host);
                    highlight.land().sendBorderMessage(player);
                }
            }
            case "done", "cancel" -> {
                if (highlight != null) {
                    highlight.placement().putAway(player);
                }
            }
            case "production", "prod", "machines" -> {
                if (needIsland(player, host)) {
                    if (args.length > 1 && args[1].equalsIgnoreCase("chat")) {
                        production(player, host);
                    } else {
                        highlight.productionMenu().open(player, host);
                    }
                }
            }
            case "guide", "hollis", "tour" -> {
                if (highlight == null) {
                    return true;
                }
                if (args.length > 1) {
                    highlight.guide().chatPick(player, args[1]);
                } else {
                    highlight.guide().resume(player);
                }
            }
            default -> player.sendMessage("§7/island §8| §7home §8| §7build §8| §7production §8| §7land §8| §7belts §8| §7tier §8| §7guide");
        }
        return true;
    }

    private boolean needIsland(Player player, IslandHost host) {
        if (highlight == null) {
            player.sendMessage("§cIsland building is not loaded.");
            return false;
        }
        if (host == null) {
            player.sendMessage("§7Claim your island first: §f/island create");
            return false;
        }
        return true;
    }

    private void production(Player player, IslandHost host) {
        List<LogisticsService.Route> routes = highlight.logistics().routes(host);
        player.sendMessage("§6=== Production ===");
        if (routes.isEmpty()) {
            player.sendMessage("§7No belts from any chute yet. Quarry → belt → Storage Hut.");
            return;
        }
        for (LogisticsService.Route route : routes) {
            Res res = route.lastRes();
            player.sendMessage("§7" + route.from().label() + " §8→ "
                    + (route.deadEnd() ? "§cdead end" : "§f" + route.to().label())
                    + " §8(" + route.length() + " tiles" + (res != null ? ", " + res.display() : "") + ")");
        }
    }

    /**
     * Staff test rig: the console (or an admin) drives another player's island actions, for a camera client that
     * cannot click and for scripted run-throughs: {@code /island admin puppet <player> <action> [value]}. Every
     * action does exactly what the player's own click would do.
     */
    private java.util.function.Function<Player, String> opener;

    /** The test rig's "open what they look at" (the listener that opens buildings on a right-click). */
    public void attachOpener(java.util.function.Function<Player, String> opener) {
        this.opener = opener;
    }

    private void puppet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aetherion.guild.admin") || highlight == null) {
            sender.sendMessage("No permission.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("/island admin puppet <player> gate <0-2>|click|next|pick <n>|use|left|scroll <1|-1>"
                    + "|part <PART>|drop|close|slot <n>|state|wipe|open|menu");
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player must be online.");
            return;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        String value = args.length > 4 ? args[4] : "";
        de.aetherion.guilds.structure.PlacementService placement = highlight.placement();
        IslandHost here = highlight.hosts().at(target.getLocation());
        switch (action) {
            case "gate" -> {
                List<de.aetherion.guilds.island.StarterLayout> starters = de.aetherion.guilds.island.StarterLayout.personalChoices();
                highlight.starterMenu().claimGate(target, starters.get(Math.max(0, Math.min(starters.size() - 1, number(value, 0)))));
                sender.sendMessage("gate " + value);
            }
            case "click" -> sender.sendMessage(highlight.guide().puppetClick(target));
            case "next" -> sender.sendMessage(highlight.guide().puppetNext(target));
            case "pick" -> sender.sendMessage(highlight.guide().puppetPick(target, number(value, 0)));
            case "use" -> {
                placement.onInteract(target, true, target.getTargetBlockExact(24), false);
                sender.sendMessage("used at " + target.getTargetBlockExact(24));
            }
            case "left" -> placement.onInteract(target, false, target.getTargetBlockExact(24), false);
            case "scroll" -> placement.cycle(target, number(value, 1) >= 0 ? 1 : -1);
            case "part" -> {
                try {
                    if (here != null) {
                        placement.pick(target, here, de.aetherion.guilds.structure.PlacementService.Part.valueOf(
                                value.toUpperCase(Locale.ROOT)));
                    }
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("Unknown part.");
                }
            }
            case "drop" -> placement.onDropTool(target);
            case "wipe" -> sender.sendMessage("island wiped: " + islands.wipe(target.getUniqueId(), true));
            case "open" -> sender.sendMessage(opener == null ? "no opener" : opener.apply(target));
            case "menu" -> {
                org.bukkit.inventory.Inventory top = target.getOpenInventory().getTopInventory();
                StringBuilder out = new StringBuilder(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(target.getOpenInventory().title())).append(':');
                for (int i = 0; i < top.getSize(); i++) {
                    org.bukkit.inventory.ItemStack item = top.getItem(i);
                    if (item != null && !item.getType().isAir() && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(item.getItemMeta().displayName());
                        if (!name.isBlank()) {
                            out.append(" [").append(i).append("] ").append(name);
                        }
                    }
                }
                sender.sendMessage(out.toString());
            }
            case "close" -> sender.sendMessage("card closed: " + de.aetherion.guilds.island.IslandUi.dismissCard(target));
            case "slot" -> {
                if (target.getOpenInventory().getTopInventory().getHolder() instanceof de.aetherion.guilds.menu.MenuKit.Holder holder) {
                    holder.menu().click(target, holder, number(value, 0), org.bukkit.event.inventory.ClickType.LEFT);
                    sender.sendMessage("clicked slot " + value);
                } else if (target.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                    // another plugin's menu (the recipe book): the click goes out as the event a real click would be
                    org.bukkit.inventory.InventoryView view = target.getOpenInventory();
                    org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryClickEvent(view,
                            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, number(value, 0),
                            org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
                    sender.sendMessage("clicked slot " + value + " (event)");
                } else {
                    sender.sendMessage("No menu open.");
                }
            }
            case "state" -> sender.sendMessage(highlight.guide().puppetState(target)
                    + " tool=" + (placement.has(target) ? String.valueOf(placement.part(target)) : "none")
                    + " held=" + placement.holdingTool(target));
            default -> sender.sendMessage("Unknown action.");
        }
    }

    private static int number(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("aetherion.guild.admin")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (highlight == null) {
            player.sendMessage("§cIsland highlight not loaded.");
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "fx" -> {
                String which = args.length < 3 ? "unlock" : args[2].toLowerCase(Locale.ROOT);
                switch (which) {
                    case "foreshadow" -> highlight.unlock().foreshadowIsland(player);
                    case "guild" -> highlight.unlock().announceGuild(player);
                    case "guildhint" -> highlight.unlock().foreshadowGuild(player);
                    default -> highlight.unlock().announceIsland(player);
                }
            }
            case "rebuild" -> {
                IslandHost host = highlight.hosts().at(player.getLocation());
                if (host == null) {
                    player.sendMessage("§cStand on an island.");
                    return;
                }
                int tiles = highlight.logistics().rebuild(host);
                player.sendMessage("§aRe-placed " + tiles + " belt tiles from the island's save.");
            }
            case "reload-templates" -> {
                highlight.templates().reload();
                Res.clearCache();
                player.sendMessage("§aTemplates reloaded from plugins/AetherionGuilds/templates/.");
            }
            case "void" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("sweep")) {
                    boolean force = args.length > 3 && args[3].equalsIgnoreCase("force");
                    int queued = highlight.voidScrubber().sweep(force);
                    player.sendMessage("§aQueued §f" + queued + " §aloaded island chunks for the void check"
                            + (force ? " §7(forced)" : "") + "§a.");
                }
                player.sendMessage("§6Void §8| " + highlight.voidScrubber().status());
            }
            case "guide" -> {
                String what = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
                if (what.equals("reset")) {
                    highlight.guide().forget(player.getUniqueId());
                    player.sendMessage("§aYour island tour is reset. Go home; " + highlight.guide().name() + " says hello again.");
                } else {
                    player.sendMessage("§7/island admin guide reset §8(your own tour, for testing)");
                }
            }
            case "wipe" -> {
                // Own island only — progress to zero, claim slot freed. No other players.
                if (islands.wipe(player.getUniqueId())) {
                    player.sendMessage("§aYour island is gone. §7/island §ato claim a fresh one.");
                } else {
                    player.sendMessage("§7You had no personal island to wipe.");
                }
            }
            case "funnel" -> funnel.get().forEach(player::sendMessage);
            case "stage" -> {
                // your own island only: jump the stage ladder for testing (Hub and Island Tier follow)
                if (dock == null || islands.byOwner(player.getUniqueId()) == null) {
                    player.sendMessage("§7You have no personal island.");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§7/island admin stage <0-3> §8(now "
                            + dock.stage(IslandHost.personal(player.getUniqueId())) + ")");
                    return;
                }
                try {
                    dock.setStage(player, IslandHost.personal(player.getUniqueId()), Integer.parseInt(args[2]));
                } catch (NumberFormatException ignored) {
                    player.sendMessage("§7/island admin stage <0-3>");
                }
            }
            case "plots" -> {
                // /island admin plots <player>: where that player's record and factory save say the island is.
                if (args.length < 3) {
                    player.sendMessage("§7/island admin plots <player>");
                    return;
                }
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                PersonalIsland record = islands.byOwner(target.getUniqueId());
                player.sendMessage("§6Island of §f" + args[2] + " §8(" + target.getUniqueId() + ")");
                player.sendMessage("§7Record: " + (record == null ? "§cnone" : "§fplot " + record.plot()
                        + (record.islandBuilt() ? " §7built" : " §cunbuilt")
                        + (islands.plotHasBlocks(record.plot()) ? " §7· blocks there" : " §c· plot empty")));
                java.util.Map<Integer, Integer> saved = islands.savedPlots(target.getUniqueId());
                if (saved.isEmpty()) {
                    player.sendMessage("§7Factory save: §8no structures");
                }
                saved.forEach((plot, count) -> player.sendMessage("§7Factory save: §f" + count + " structures on plot " + plot
                        + (islands.plotHasBlocks(plot) ? " §7· blocks there" : " §c· plot empty")
                        + " §8→ §7/island admin moveplot " + args[2] + " " + plot));
            }
            case "moveplot" -> {
                if (args.length < 4) {
                    player.sendMessage("§7/island admin moveplot <player> <plot>");
                    return;
                }
                try {
                    org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                    player.sendMessage(islands.movePlot(target.getUniqueId(), Integer.parseInt(args[3])));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cPlot must be a number.");
                }
            }
            case "info" -> {
                IslandHost host = highlight.hosts().at(player.getLocation());
                player.sendMessage("§6Island highlight §8| §7paste jobs: §f" + highlight.paste().pending()
                        + " §8| §7cargo: §f" + highlight.logistics().cargoCount()
                        + " §8| §7conveyor displays: §f" + highlight.logistics().conveyorEntities()
                        + " §8| §7machine fx: §f" + highlight.logistics().machineFxEntities());
                if (host != null) {
                    player.sendMessage("§7Host §f" + host.key() + " §8| §7structures §f"
                            + highlight.structures().of(host).size() + " §8| §7belts §f" + highlight.logistics().count(host)
                            + " §8| §7parcels §f" + highlight.hosts().parcels(host).size()
                            + " §8| §7active §f" + highlight.logistics().isActive(host));
                }
            }
            default -> player.sendMessage("§7/island admin fx [unlock|foreshadow|guild|guildhint] §8| §7rebuild §8| §7reload-templates"
                    + " §8| §7void [sweep [force]] §8| §7guide reset §8| §7wipe §8| §7info §8| §7funnel §8| §7stage <0-3>"
                    + " §8| §7plots <player> §8| §7moveplot <player> <plot>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("create", "home", "menu", "build", "tool", "production", "land", "belts", "tier", "guide", "done")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("aetherion.guild.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("fx", "rebuild", "reload-templates", "void", "guide", "wipe", "info", "funnel", "stage", "plots", "moveplot")
                    .filter(o -> o.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
