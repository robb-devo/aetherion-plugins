package de.aetherion.guilds.menu;

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
        head.add("§8Top row: unlock new parts.");
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
            case BUILD -> placement.openTool(player, host, null);
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
                if (slot >= UNLOCKS && slot < UNLOCKS + BuildCosts.UNLOCKS.size() && hub != null) {
                    unlockClick(player, host, hub, BuildCosts.UNLOCKS.get(slot - UNLOCKS));
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
            BuildCosts.Unlock next = menu.progress.next(host);
            for (int i = 0; i < BuildCosts.UNLOCKS.size(); i++) {
                inventory.setItem(UNLOCKS + i, menu.unlockTile(player, host, BuildCosts.UNLOCKS.get(i), hubLevel,
                        BuildCosts.UNLOCKS.get(i) == next));
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
        if (hubLevel < unlock.hubLevel()) {
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
        if (hub.level() < unlock.hubLevel()) {
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
        PlacementService.Part part = PlacementService.Part.of(type);
        String name = part == null ? type.display() : part.label();
        player.sendTitle("§6✦ Unlocked §6✦", "§e" + name, 5, 40, 12);
        player.sendMessage("§a" + name + " unlocked §8(" + BuildCosts.describe(unlock.price()) + ")§a. §7It's in your"
                + " Build Tool now: sneak + scroll to it.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        org.bukkit.World world = hosts.world(host);
        if (world != null) {
            org.bukkit.Location top = new org.bukkit.Location(world, hub.x() + 0.5, hub.maxY() + 1.0, hub.z() + 0.5);
            world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, top, 40, 0.8, 0.6, 0.8, 0.25);
        }
        open(player, host);
    }
}
