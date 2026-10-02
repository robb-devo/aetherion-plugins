package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.FactoryGoals;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.structure.StructureUpgrades;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hub desk (the Workshop lectern): the one place the factory is run from once the Hub stands. Build, the
 * Belt Layer, Production with upgrades, Coin Shortcuts, every line at a glance, the next goal and the Hub's own
 * tier (Foreman's Hall unlocks the Forge / Overflow Gate / Sorter, the Grand Hub doubles every machine).
 */
public final class HubMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int BUILD = 20;
    private static final int BELTS = 22;
    private static final int MARKET = 24;
    private static final int PRODUCTION = 29;
    private static final int LINES = 31;
    private static final int TIER = 33;
    private static final int GOAL = 40;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final BuildMenu build;
    private final ProductionMenu production;
    private final MarketMenu market;
    private final FactoryGoals goals;

    public HubMenu(HostService hosts, StructureService structures, LogisticsService logistics, PlacementService placement,
                   BuildMenu build, ProductionMenu production, MarketMenu market, FactoryGoals goals) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
        this.build = build;
        this.production = production;
        this.market = market;
        this.goals = goals;
    }

    public void open(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your Hub.");
            return;
        }
        PlacedStructure hub = structures.hub(host);
        if (hub == null) {
            build.open(player, host);
            return;
        }
        structures.markDesk(player);
        Inventory inventory = MenuKit.framed(this, host, 54, "§8✦ " + LogisticsService.hubName(hub.level()) + " · "
                + hosts.title(host));
        List<String> head = new ArrayList<>();
        head.add("§7Tier §f" + hub.level() + "§8/" + StructureUpgrades.maxLevel(StructureType.WORKSHOP)
                + " §8· §7Island Tier §f" + hosts.tier(host));
        int lines = 0;
        int running = 0;
        for (LogisticsService.Route route : logistics.routes(host)) {
            if (route.from().type().role() == StructureType.Role.SOURCE) {
                lines++;
                if (route.to() != null) {
                    running++;
                }
            }
        }
        head.add("§7Lines §f" + running + "§8/" + lines + " §7reach a machine");
        head.add("§7Belts §f" + logistics.count(host) + "§8/" + logistics.cap(host));
        String problem = logistics.worstProblem(host);
        if (problem != null) {
            head.add("§c⚠ " + problem);
        }
        head.add("");
        head.add("§8The factory desk: everything you");
        head.add("§8build, lay, upgrade or buy, here.");
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.LECTERN, "§6§l✦ " + LogisticsService.hubName(hub.level()), head)));
        inventory.setItem(BUILD, MenuKit.tile(Material.CRAFTING_TABLE, "§6§lBuild",
                List.of("§7Machines and belt pieces:", "§7Mill, Depot, Forge, Splitter,", "§7Overflow Gate, Sorter.", "",
                        "§e▶ Open Build"), goalWantsBuild(host)));
        inventory.setItem(BELTS, MenuKit.glow(MenuKit.named(Material.IRON_TRAPDOOR, "§e§lBelt Layer",
                "§7Straight into your hand.",
                "§7Right-click a start, then an end;",
                "§7orange arrow → green arrow.",
                "",
                "§7Laid §f" + logistics.count(host) + "§8/" + logistics.cap(host) + " §8· §6" + logistics.tileCost() + "§7 a tile",
                "",
                "§e▶ Take the Belt Layer")));
        inventory.setItem(MARKET, MenuKit.named(Material.GOLD_INGOT, "§6Coin Shortcuts",
                "§7Quarries, machine parts, cores", "§7and materials, for coins.", "", "§e▶ Open"));
        inventory.setItem(PRODUCTION, MenuKit.named(Material.BLAST_FURNACE, "§aProduction",
                "§7Every machine: its state,", "§7its stock, its upgrade.", "", "§e▶ Open Production"));
        inventory.setItem(LINES, linesTile(host));
        ItemStack tier = UpgradeTile.of(player, structures, hub);
        if (tier != null) {
            inventory.setItem(TIER, tier);
        }
        if (goals != null) {
            FactoryGoals.Goal next = goals.next(host);
            List<String> lore = new ArrayList<>();
            if (next == null) {
                lore.add("§7Every factory goal is done.");
            } else {
                lore.add("§f" + next.title());
                lore.add("§7" + next.how());
                lore.add("");
                lore.add("§7Reward §6" + GuildFormat.compact(next.reward()) + " " + hosts.fundsLabel(host));
            }
            inventory.setItem(GOAL, MenuKit.tile(Material.TARGET, next == null ? "§bAll goals done" : "§bNext goal", lore,
                    next != null));
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, host.isGuild() ? "§eGuild menu" : "§eIsland menu"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    private boolean goalWantsBuild(IslandHost host) {
        FactoryGoals.Goal next = goals == null ? null : goals.next(host);
        return next != null && List.of("mill_line", "split", "sort", "overflow", "forge_line").contains(next.id());
    }

    private ItemStack linesTile(IslandHost host) {
        List<String> lore = new ArrayList<>();
        for (String line : logistics.hubBoard(host).split("\n")) {
            if (line.contains("Right-click the lectern")) {
                continue;
            }
            lore.add(line);
        }
        return MenuKit.named(Material.FILLED_MAP, "§eEvery line", lore);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        IslandHost host = (IslandHost) holder.context();
        PlacedStructure hub = structures.hub(host);
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> {
                player.closeInventory();
                player.performCommand(host.isGuild() ? "guild" : "island");
            }
            case BUILD -> build.openDesk(player, host);
            case BELTS -> placement.startBelts(player, host);
            case MARKET -> market.open(player, host);
            case PRODUCTION -> production.open(player, host);
            case TIER -> {
                if (hub == null || structures.nextTier(hub) == null) {
                    return;
                }
                boolean buy = click == ClickType.SHIFT_RIGHT && structures.missingCoins(player, hub) > 0L;
                if (structures.upgradeBlocked(player, hub, buy) != null) {
                    structures.upgrade(player, hub, buy); // explains why not
                    open(player, host);
                    return;
                }
                player.closeInventory();
                structures.upgrade(player, hub, buy);
            }
            case GOAL -> {
                FactoryGoals.Goal next = goals == null ? null : goals.next(host);
                if (next != null) {
                    player.closeInventory();
                    player.sendMessage("§b➤ " + next.title() + "§7: " + next.how());
                }
            }
            default -> {
            }
        }
    }
}
