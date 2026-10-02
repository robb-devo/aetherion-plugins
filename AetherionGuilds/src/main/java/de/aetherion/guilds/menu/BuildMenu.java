package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.structure.StructureUpgrades;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints for one island: the curated structures in a row, the Belt Layer under them, the border and a
 * door to Production (where everything built is listed and upgraded). Reached from the island hub, the
 * Workshop lectern, the guild menu or {@code /island build}.
 */
public final class BuildMenu implements MenuKit.Menu {

    private static final Map<Integer, StructureType> BLUEPRINTS = Map.of(
            20, StructureType.STORAGE_HUT,
            21, StructureType.WORKSHOP,
            22, StructureType.DEPOT,
            23, StructureType.MILL,
            24, StructureType.FORGE
    );
    private static final int HEADER = 4;
    private static final int BELTS = 30;
    private static final int PRODUCTION = 32;
    private static final int BORDER = 40;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final LandService land;
    private StorageMenu storage;
    private MachineMenu machine;
    private ProductionMenu production;

    public BuildMenu(HostService hosts, StructureService structures, LogisticsService logistics,
                     PlacementService placement, LandService land) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
        this.land = land;
    }

    public void attach(StorageMenu storage, MachineMenu machine) {
        this.storage = storage;
        this.machine = machine;
    }

    public void attachProduction(ProductionMenu production) {
        this.production = production;
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
        int tier = hosts.tier(host);
        Inventory inventory = MenuKit.framed(this, host, 54, "§8Build · " + hosts.title(host));
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.CRAFTING_TABLE, "§6§lBlueprints",
                "§7Island Tier §f" + tier + "§8/" + IslandTiers.MAX_LEVEL,
                "§7Belts §f" + logistics.count(host) + "§8/" + logistics.cap(host),
                "",
                "§8Pick one: the blueprint goes in",
                "§8your hand and a hologram shows",
                "§8where it stands. Right-click builds.")));
        for (Map.Entry<Integer, StructureType> entry : BLUEPRINTS.entrySet()) {
            inventory.setItem(entry.getKey(), blueprint(player, host, entry.getValue(), tier));
        }
        inventory.setItem(BELTS, beltLayer(player, host));
        if (production != null) {
            inventory.setItem(PRODUCTION, MenuKit.named(Material.BLAST_FURNACE, "§aProduction",
                    "§7Everything built, its storage,", "§7speed and upgrades.", "", "§e▶ Open production"));
        }
        inventory.setItem(BORDER, MenuKit.named(Material.SPYGLASS, "§eShow build border",
                "§7Gold particles trace where you", "§7may build, for a few seconds."));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, host.isGuild() ? "§eBack to the guild" : "§eBack to your island"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private ItemStack beltLayer(Player player, IslandHost host) {
        boolean workshop = structures.hasWorkshop(host);
        LogisticsService.BeltPass pass = logistics.beltPass();
        boolean starterKit = !workshop && pass != null && pass.noWorkshopNeeded(player, host);
        int free = pass == null ? 0 : pass.freeTiles(host);
        List<String> lore = new ArrayList<>(List.of(
                "§7Conveyors that carry quarry output.",
                "§7Right-click a start, then an end;",
                "§7straight or one bend, keeps chaining.",
                "§7Start at an §6orange chute§7, end",
                "§7against a hut, depot, mill or forge.",
                "",
                "§7Cost §6" + logistics.tileCost() + " §7per tile" + (free > 0 ? " §8(§e" + free + " free left§8)" : ""),
                "§7Laid §f" + logistics.count(host) + "§8/" + logistics.cap(host)));
        lore.add("");
        if (workshop || starterKit) {
            lore.add(starterKit ? "§a▶ Starter kit: no Workshop needed" : "§a▶ Take the Belt Layer");
            return MenuKit.glow(MenuKit.named(Material.IRON_TRAPDOOR, "§e§lBelt Layer", lore));
        }
        lore.add("§c✖ Build a Workshop first");
        return MenuKit.named(Material.IRON_TRAPDOOR, "§7Belt Layer", lore);
    }

    private ItemStack blueprint(Player player, IslandHost host, StructureType type, int tier) {
        List<String> lore = new ArrayList<>(type.blurb());
        lore.add("");
        long price = structures.priceFor(host, type);
        lore.add("§7Cost " + (price <= 0 ? "§afree" : "§6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host))
                + (type.itemId() != null ? " §7+ §f1 " + StructureService.itemName(type.itemId()) : ""));
        lore.add("§7Built §f" + structures.count(host, type) + "§8/" + type.maxCount(tier));
        if (type.role() == StructureType.Role.SINK) {
            lore.add("§7Holds §f" + GuildFormat.compact(type.capacity()) + " §7raw-eq");
        }
        if (type.role() == StructureType.Role.PROCESSOR) {
            lore.add("§7Speed §f" + GuildFormat.compact((long) type.ratePerSecond()) + " §7raw-eq/s");
        }
        int max = StructureUpgrades.maxLevel(type);
        if (max > 1) {
            List<String> tiers = new ArrayList<>();
            for (int level = 2; level <= max; level++) {
                tiers.add(StructureUpgrades.tierName(type, level));
            }
            lore.add("§7Grows into §f" + String.join(" §8→ §f", tiers));
        }
        String blocked = structures.blockedReason(player, host, type);
        lore.add("");
        lore.add(blocked == null ? "§a▶ Take the blueprint" : "§c✖ " + blocked);
        boolean nextUp = type == StructureType.STORAGE_HUT && structures.count(host, type) == 0 && blocked == null;
        ItemStack item = blocked == null
                ? MenuKit.glow(MenuKit.named(type.icon(), "§e" + type.display() + (nextUp ? " §6◆" : ""), lore))
                : MenuKit.named(type.icon(), "§7" + type.display(), lore);
        return item;
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
        StructureType type = BLUEPRINTS.get(slot);
        if (type != null) {
            placement.startStructure(player, host, type);
            return;
        }
        if (slot == BELTS) {
            placement.startBelts(player, host);
            return;
        }
        if (slot == PRODUCTION && production != null) {
            production.open(player, host);
            return;
        }
        if (slot == BORDER) {
            player.closeInventory();
            land.flashBorder(player, host);
            land.sendBorderMessage(player);
        }
    }
}
