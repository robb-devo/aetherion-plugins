package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.FactoryGoals;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.structure.BuildCosts;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Build Tool's picker: one row, every part, one click. Pick a part and it goes into your hand as the Build
 * Tool; in the world, sneak + scroll walks the same row and F brings this screen back.
 * <pre>
 *   [Belt] [Storage Hut] [Hub] [Depot] [Mill] [Splitter] [Forge] [Overflow] [Sorter]
 *                         [Quarries?]  [Take down]  [Border]
 * </pre>
 * Locked parts show where they unlock (the Hub). Nothing else lives here: upgrades, unlocks and Coin Shortcuts
 * are at the Hub.
 */
public final class BuildMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int ROW = 9;
    private static final int QUARRIES = 20;
    private static final int DISMANTLE = 22;
    private static final int BORDER = 24;
    private static final int BACK = 27;
    private static final int CLOSE = 35;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final LandService land;
    private StorageMenu storage;
    private MachineMenu machine;
    private ProductionMenu production;
    private FactoryGoals goals;
    private MarketMenu market;

    public BuildMenu(HostService hosts, StructureService structures, LogisticsService logistics,
                     PlacementService placement, LandService land) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
        this.land = land;
        placement.attachPicker(this::open);
    }

    public void attach(StorageMenu storage, MachineMenu machine) {
        this.storage = storage;
        this.machine = machine;
    }

    public void attachProduction(ProductionMenu production) {
        this.production = production;
    }

    public void attachElevation(FactoryGoals goals, MarketMenu market) {
        this.goals = goals;
        this.market = market;
    }

    /** The parts in picker order (the Hub only while there is none). */
    private List<PlacementService.Part> row(IslandHost host) {
        List<PlacementService.Part> parts = new ArrayList<>();
        for (PlacementService.Part part : PlacementService.Part.values()) {
            if (part == PlacementService.Part.DISMANTLE) {
                continue;
            }
            if (part == PlacementService.Part.HUB && structures.hasWorkshop(host)) {
                continue;
            }
            parts.add(part);
        }
        return parts;
    }

    public void open(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            player.sendMessage("§cNo island to build on.");
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        Inventory inventory = MenuKit.framed(this, host, 36, "§8Build Tool · pick a part");
        PlacementService.Part current = placement.part(player);
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.NETHERITE_PICKAXE, "§6§lBuild Tool",
                "§7In storage: §f" + GuildFormat.compact(structures.stock(host, BuildCosts.COBBLE)) + " Cobblestone"
                        + (structures.stock(host, BuildCosts.COMPRESSED) > 0
                        ? "§7, §f" + GuildFormat.compact(structures.stock(host, BuildCosts.COMPRESSED)) + " Compressed" : ""),
                "",
                "§fSneak + scroll §7next part",
                "§fRight-click §7build · §fLeft-click §7turn",
                "§fF §7this screen · §fQ §7put away")));
        List<PlacementService.Part> parts = row(host);
        for (int i = 0; i < parts.size() && i < 9; i++) {
            inventory.setItem(ROW + i, tile(player, host, parts.get(i), parts.get(i) == current));
        }
        inventory.setItem(QUARRIES, MenuKit.named(Material.IRON_PICKAXE, "§fQuarries",
                "§7A quarry is an item: hold it and",
                "§7place it like a block on your land.",
                "§7Its belt starts at the §6orange §7mark.",
                "",
                market != null && structures.hasWorkshop(host) ? "§7Buy more at your §6Hub§7." : "§7More come from your §6Hub§7."));
        inventory.setItem(DISMANTLE, tile(player, host, PlacementService.Part.DISMANTLE,
                current == PlacementService.Part.DISMANTLE));
        inventory.setItem(BORDER, MenuKit.named(Material.SPYGLASS, "§eShow my land",
                "§7Gold sparks trace where you", "§7may build, for a few seconds."));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, host.isGuild() ? "§eBack to the guild" : "§eBack to your island"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public PlacementService placement() {
        return placement;
    }

    /** Kept for the Hub and other screens that used to open the Build desk: it's the picker now. */
    public void openDesk(Player player, IslandHost host) {
        open(player, host);
    }

    private ItemStack tile(Player player, IslandHost host, PlacementService.Part part, boolean selected) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + part.line());
        StructureType type = part.type();
        if (type != null) {
            lore.add("§7Cost: " + placement.costLine(player, host, structures.buildCost(host, type)));
            int limit = structures.limit(host, type);
            if (limit < 99) {
                lore.add("§7Built: §f" + structures.count(host, type) + "§8/" + limit);
            }
        } else if (part == PlacementService.Part.BELT) {
            lore.add("§7Cost: §f" + BuildCosts.BELT_COBBLE + " Cobblestone §7a tile");
            lore.add("§7Laid: §f" + logistics.count(host) + "§8/" + logistics.cap(host));
        }
        String locked = placement.lockReason(host, part);
        lore.add("");
        if (locked != null) {
            BuildCosts.Unlock unlock = type == null ? null : BuildCosts.unlock(type);
            lore.add("§c🔒 " + locked);
            if (unlock != null) {
                lore.add("§7Unlock price: §f" + BuildCosts.describe(unlock.price()));
            }
            return MenuKit.named(part.icon(), "§7" + part.label(), lore);
        }
        lore.add(selected ? "§a◀ In your hand" : "§e▶ Click to pick");
        boolean next = goalWants(host, type);
        ItemStack item = MenuKit.named(part.icon(), (selected ? "§a§l" : "§e") + part.label() + (next ? " §6◆" : ""), lore);
        return selected || next ? MenuKit.glow(item) : item;
    }

    /** Does the next factory goal want this part? (It gets the ◆.) */
    private boolean goalWants(IslandHost host, StructureType type) {
        FactoryGoals.Goal next = goals == null ? null : goals.next(host);
        if (next == null || type == null) {
            return false;
        }
        return switch (next.id()) {
            case "hub" -> type == StructureType.WORKSHOP;
            case "mill_line" -> type == StructureType.MILL;
            case "split" -> type == StructureType.SPLITTER;
            case "sort" -> type == StructureType.SORTER;
            case "overflow" -> type == StructureType.OVERFLOW;
            case "forge_line" -> type == StructureType.FORGE;
            default -> false;
        };
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        IslandHost host = (IslandHost) holder.context();
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == BACK) {
            player.closeInventory();
            player.performCommand(host.isGuild() ? "guild" : "island");
            return;
        }
        if (slot == BORDER) {
            player.closeInventory();
            land.flashBorder(player, host);
            land.sendBorderMessage(player);
            return;
        }
        if (slot == QUARRIES && market != null && structures.hasWorkshop(host) && structures.atDesk(player, host)) {
            market.open(player, host);
            return;
        }
        PlacementService.Part part = null;
        if (slot == DISMANTLE) {
            part = PlacementService.Part.DISMANTLE;
        } else if (slot >= ROW && slot < ROW + 9) {
            List<PlacementService.Part> parts = row(host);
            if (slot - ROW < parts.size()) {
                part = parts.get(slot - ROW);
            }
        }
        if (part == null) {
            return;
        }
        String locked = placement.lockReason(host, part);
        if (locked != null) {
            player.sendMessage("§c" + part.label() + ": " + locked);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        placement.openTool(player, host, part);
    }

    /**
     * Legacy "build at your Hub" redirect menu from older jars. Build Tool replaces that flow —
     * kept so additive jar-surface checks stay green.
     */
    @Deprecated
    @SuppressWarnings("unused")
    private final class Away implements MenuKit.Menu {
        @Override
        public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
            player.closeInventory();
        }
    }
}
