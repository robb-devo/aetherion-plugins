package de.aetherion.guilds.menu;

import de.aetherion.core.api.ChromePage;
import de.aetherion.guilds.island.FactoryGoals;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.structure.BuildCosts;
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
 * The Hub (its lectern): where the factory grows. Top: the <b>unlock board</b> — every part you can unlock, the
 * next one glowing, paid from storage — and the Hub's own tier. Below: the Build Tool, Production, Coin
 * Shortcuts, every line, the next goal. Building itself happens anywhere on the island with the Build Tool.
 */
public final class HubMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int UNLOCKS = 10;
    private static final int TIER = 16;
    private static final int BUILD = 28;
    private static final int PRODUCTION = 30;
    private static final int MARKET = 32;
    private static final int LINES = 34;
    private static final int GOAL = 40;
    private static final int PRESS = 38;
    private static final int DOCK = 42;
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
    private de.aetherion.guilds.island.FactoryProgress progress;

    public void attachProgress(de.aetherion.guilds.island.FactoryProgress progress) {
        this.progress = progress;
    }

    private de.aetherion.guilds.island.DockService dock;
    private DockMenu dockMenu;

    /** Personal islands: the stage ladder, the hand press and the Dock. */
    public void attachDock(de.aetherion.guilds.island.DockService dock, DockMenu dockMenu) {
        this.dock = dock;
        this.dockMenu = dockMenu;
    }

    private boolean staged(IslandHost host) {
        return dock != null && host != null && !host.isGuild();
    }

    /** Island tour: the part the tour wants unlocked right now (free, the only glowing tile), or null. */
    private java.util.function.Function<IslandHost, StructureType> tourUnlock = host -> null;
    private String tourName = "the tour";

    private java.util.function.Predicate<IslandHost> touring = host -> false;

    public void attachTour(java.util.function.Function<IslandHost, StructureType> tourUnlock,
                           java.util.function.Predicate<IslandHost> touring, String tourName) {
        this.tourUnlock = tourUnlock == null ? host -> null : tourUnlock;
        this.touring = touring == null ? host -> false : touring;
        if (tourName != null) {
            this.tourName = tourName;
        }
    }

    private StructureType tourWants(IslandHost host) {
        return host == null || host.isGuild() ? null : tourUnlock.apply(host);
    }

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
        if (staged(host)) {
            head.add("§6" + de.aetherion.guilds.island.Stages.label(dock.stage(host)) + " §8· §7Island Tier §f" + hosts.tier(host));
        } else {
            head.add("§7Tier §f" + hub.level() + "§8/" + StructureUpgrades.maxLevel(StructureType.WORKSHOP)
                    + " §8· §7Island Tier §f" + hosts.tier(host));
        }
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
        StructureType tour = tourWants(host);
        if (tour != null) {
            PlacementService.Part tourPart = PlacementService.Part.of(tour);
            head.add("§6◆ " + tourName + ": §funlock the " + (tourPart == null ? tour.display() : tourPart.label())
                    + " §8(glowing)");
        }
        head.add(staged(host) ? "§8Top row: your stage's parts to unlock." : "§8Top row: unlock new parts.");
        head.add("§8Build them anywhere with the Build Tool.");
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.LECTERN, "§6§l✦ " + LogisticsService.hubName(hub.level()), head)));
        if (progress != null) {
            BuildCostsView.fill(this, inventory, player, host, hub.level());
        }
        inventory.setItem(BUILD, MenuKit.tile(Material.NETHERITE_PICKAXE, "§6§lBuild Tool",
                List.of("§7Belts, huts, machines: pick a", "§7part, see it, right-click to build.", "",
                        "§fSneak + scroll §7next part", "§fF §7all parts", "",
                        "§e▶ Into your hand"), goalWantsBuild(host)));
        inventory.setItem(MARKET, MenuKit.named(Material.GOLD_INGOT, "§6Coin Shortcuts",
                "§7Quarries, machine parts, cores", "§7and materials, for coins.", "", "§e▶ Open"));
        inventory.setItem(PRODUCTION, MenuKit.named(Material.BLAST_FURNACE, "§aProduction",
                "§7Every machine: its state,", "§7its stock, its upgrade.", "", "§e▶ Open Production"));
        inventory.setItem(LINES, linesTile(host));
        if (staged(host)) {
            inventory.setItem(TIER, stageTile(player, host));
            inventory.setItem(PRESS, pressTile(player, host));
            inventory.setItem(DOCK, dockTile(host));
        } else {
            ItemStack tier = UpgradeTile.of(player, structures, hub);
            if (tier != null) {
                inventory.setItem(TIER, tier);
            }
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
        ChromePage.open(player, inventory, ChromePage.Theme.ISLAND);
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
            case BUILD -> placement.openTool(player, host, null);
            case MARKET -> market.open(player, host);
            case PRODUCTION -> production.open(player, host);
            case PRESS -> {
                if (staged(host)) {
                    dock.press(player, host);
                    open(player, host);
                }
            }
            case DOCK -> {
                if (staged(host)) {
                    openDock(player, host);
                }
            }
            case TIER -> {
                if (staged(host)) {
                    openDock(player, host);
                    return;
                }
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
                List<BuildCosts.Unlock> board = BuildCosts.unlocks(host);
                if (slot >= UNLOCKS && slot < UNLOCKS + board.size() && hub != null) {
                    unlockClick(player, host, hub, board.get(slot - UNLOCKS));
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the unlock board
    // ------------------------------------------------------------------------------------------------

    /** Fills the unlock row (kept in a tiny helper so the layout reads top to bottom above). */
    private static final class BuildCostsView {
        static void fill(HubMenu menu, Inventory inventory, Player player, IslandHost host, int hubLevel) {
            StructureType tour = menu.tourWants(host);
            // mid-tour only the tour's part glows; nothing else competes for the eye
            BuildCosts.Unlock next = tour != null ? BuildCosts.unlock(host, tour)
                    : menu.touring.test(host) ? null : menu.progress.next(host);
            List<BuildCosts.Unlock> board = BuildCosts.unlocks(host);
            for (int i = 0; i < board.size(); i++) {
                inventory.setItem(UNLOCKS + i, menu.unlockTile(player, host, board.get(i), hubLevel, board.get(i) == next));
            }
        }
    }

    private ItemStack unlockTile(Player player, IslandHost host, BuildCosts.Unlock unlock, int hubLevel, boolean next) {
        StructureType type = unlock.type();
        PlacementService.Part part = PlacementService.Part.of(type);
        String name = part == null ? type.display() : part.label();
        Material icon = part == null ? type.icon() : part.icon();
        List<String> lore = new ArrayList<>();
        lore.add("§7" + (part == null ? "" : part.line()));
        if (progress.unlocked(host, type)) {
            lore.add("§7Each one costs: " + placement.costLine(player, host, structures.buildCost(host, type)));
            lore.add("");
            lore.add("§a✔ Unlocked §8· §e▶ Click: Build Tool with it");
            return MenuKit.named(icon, "§a" + name, lore);
        }
        lore.add("§7Unlock: " + placement.costLine(player, host, unlock.price()));
        lore.add("");
        if (staged(host) && dock.stage(host) < unlock.stage()) {
            lore.add("§c🔒 " + de.aetherion.guilds.island.Stages.label(unlock.stage()));
            lore.add("§8Ship the phase contract at your Dock.");
            return MenuKit.named(icon, "§8" + name, lore);
        }
        if (type == tourWants(host)) {
            String missing = structures.missing(player, host, unlock.price());
            lore.add(missing == null ? "§e▶ Click to unlock §8· §7then build it" : "§c✖ " + missing);
            return MenuKit.glow(MenuKit.named(icon, "§6§l" + name + " §6◆ " + tourName + ": unlock this", lore));
        }
        if (!staged(host) && hubLevel < unlock.hubLevel()) {
            lore.add("§c🔒 Needs Hub tier " + unlock.hubLevel() + " §8(upgrade it on the right)");
            return MenuKit.named(icon, "§8" + name, lore);
        }
        String missing = structures.missing(player, host, unlock.price());
        lore.add(missing == null ? "§e▶ Click to unlock" : "§c✖ " + missing);
        ItemStack item = MenuKit.named(icon, (next ? "§6§l" : "§e") + name + (next ? " §6◆ next" : ""), lore);
        return next ? MenuKit.glow(item) : item;
    }

    private void unlockClick(Player player, IslandHost host, PlacedStructure hub, BuildCosts.Unlock unlock) {
        StructureType type = unlock.type();
        if (progress == null) {
            return;
        }
        if (progress.unlocked(host, type)) {
            placement.openTool(player, host, PlacementService.Part.of(type));
            return;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "unlock parts"));
            return;
        }
        if (staged(host) && dock.stage(host) < unlock.stage()) {
            player.sendMessage("§cThat opens on " + de.aetherion.guilds.island.Stages.label(unlock.stage())
                    + ". Ship the phase contract at your Dock first.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        if (!staged(host) && hub.level() < unlock.hubLevel()) {
            player.sendMessage("§cUpgrade your Hub to tier " + unlock.hubLevel() + " first.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        // pay first (all or nothing), then unlock: nothing can be unlocked twice or for free
        if (!structures.pay(player, host, unlock.price())) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            open(player, host);
            return;
        }
        progress.unlock(host, type);
        structures.milestone(host, "first_unlock");
        celebrateUnlock(player, host, hub, type, BuildCosts.describe(unlock.price()));
        if (type == tourWants(host) || touring.test(host)) {
            // on the tour: straight into the Build Tool with it (no second menu)
            player.closeInventory();
            placement.openTool(player, host, PlacementService.Part.of(type));
            return;
        }
        open(player, host);
    }

    // ------------------------------------------------------------------------------------------------
    // personal islands: the stage, the hand press, the Dock
    // ------------------------------------------------------------------------------------------------

    private void openDock(Player player, IslandHost host) {
        if (dock.dock(host) == null) {
            player.sendMessage(structures.unlocked(host, StructureType.DOCK)
                    ? "§7Build your Dock first: Build Tool → Dock. Contracts and the airship come there."
                    : "§7Unlock the Dock on the top row first (" + BuildCosts.describe(BuildCosts.unlock(host,
                    StructureType.DOCK).price()) + "), then build it.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
            return;
        }
        if (dockMenu != null) {
            dockMenu.open(player, host);
        }
    }

    private ItemStack stageTile(Player player, IslandHost host) {
        int stage = dock.stage(host);
        List<String> lore = new ArrayList<>();
        lore.add("§7Your Hub is the §f" + LogisticsService.hubName(de.aetherion.guilds.island.Stages.hubLevel(stage)));
        lore.add("§7Island Tier §f" + hosts.tier(host) + " §8· §7quarries up to §fLv"
                + de.aetherion.guilds.island.Stages.quarryCap(stage));
        lore.add("");
        if (stage >= de.aetherion.guilds.island.Stages.MAX) {
            lore.add("§aThe top of the ladder.");
            lore.add("§7Contracts and the daily order");
            lore.add("§7keep coming at the Dock.");
            return MenuKit.named(Material.NETHER_STAR, "§6§l" + de.aetherion.guilds.island.Stages.label(stage), lore);
        }
        de.aetherion.guilds.island.DockService.Contract phase = dock.phase(host);
        lore.add("§7Next: §f" + de.aetherion.guilds.island.Stages.label(stage + 1));
        lore.addAll(de.aetherion.guilds.island.Stages.perks(stage + 1));
        lore.add("");
        if (phase != null) {
            lore.add("§7Phase contract §f" + phase.title() + "§7:");
            for (de.aetherion.guilds.island.DockService.Need need : phase.needs()) {
                long have = Math.min(need.amount(), phase.loaded(need.res()));
                lore.add((have >= need.amount() ? "§a✔ " : "§8• ") + "§f" + GuildFormat.compact(have) + "§8/§f"
                        + GuildFormat.compact(need.amount()) + " " + need.res().color() + need.res().display());
            }
        }
        lore.add("");
        lore.add(dock.dock(host) == null ? "§7Ship it at your §fDock §8(build one first)" : "§e▶ Open the Dock");
        return MenuKit.tile(Material.BEACON, "§6§l" + de.aetherion.guilds.island.Stages.label(stage), lore, false);
    }

    private ItemStack pressTile(Player player, IslandHost host) {
        long cobble = structures.stock(host, BuildCosts.COBBLE);
        boolean can = cobble >= de.aetherion.guilds.island.DockService.PRESS_RAW;
        List<String> lore = new ArrayList<>();
        lore.add("§7Press §f128 Cobblestone §7from storage");
        lore.add("§7into §a1 Compressed Cobblestone§7,");
        lore.add("§7by hand, right now. Your Mill");
        lore.add("§7does the same all day long.");
        lore.add("");
        lore.add("§7In storage: " + (can ? "§a" : "§c") + GuildFormat.compact(cobble) + " §7Cobblestone");
        lore.add("");
        lore.add(can ? "§e▶ Press one" : "§c✖ needs " + GuildFormat.compact(de.aetherion.guilds.island.DockService.PRESS_RAW - cobble)
                + " more Cobblestone");
        return MenuKit.tile(Material.PISTON, "§6Hand Press", lore, false); // a side door, never the tour's glow
    }

    private ItemStack dockTile(IslandHost host) {
        List<String> lore = new ArrayList<>();
        if (dock.dock(host) == null) {
            lore.add("§7Contracts, the daily order and");
            lore.add("§7the airship. Unlock and build");
            lore.add("§7a Dock first.");
            return MenuKit.named(Material.SPRUCE_BOAT, "§8Dock", lore);
        }
        int ready = 0;
        for (de.aetherion.guilds.island.DockService.Contract contract : dock.contracts(host)) {
            if (contract.full()) {
                ready++;
            }
        }
        lore.add("§7Open contracts §f" + dock.contracts(host).size() + (ready > 0 ? " §8· §a" + ready + " ready" : ""));
        lore.add("§7Dock Marks §b" + dock.marks(host));
        lore.add("");
        lore.add("§e▶ Open the Dock");
        return MenuKit.tile(Material.SPRUCE_BOAT, "§b§lDock", lore, ready > 0);
    }

    private void celebrateUnlock(Player player, IslandHost host, PlacedStructure hub, StructureType type, String paid) {
        PlacementService.Part part = PlacementService.Part.of(type);
        String name = part == null ? type.display() : part.label();
        player.sendTitle("§6✦ Unlocked §6✦", "§e" + name, 5, 40, 12);
        player.sendMessage("§a" + name + " unlocked §8(" + paid + ")§a. §7It's in your"
                + " Build Tool now: sneak + scroll to it.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        org.bukkit.World world = hosts.world(host);
        if (world != null) {
            org.bukkit.Location top = new org.bukkit.Location(world, hub.centerX(), hub.maxY() + 1.0, hub.centerZ());
            de.aetherion.core.api.Vfx.puff(top, org.bukkit.Particle.TOTEM_OF_UNDYING, 40, Math.max(0.8, 0.8), null);
        }
    }
}
