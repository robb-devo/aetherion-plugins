package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints for one island: the curated structures, the Rail Layer, a production overview and a list of
 * what's built (click one to manage it from anywhere on the island).
 */
public final class BuildMenu implements MenuKit.Menu {

    private static final Map<Integer, StructureType> BLUEPRINTS = Map.of(
            10, StructureType.STORAGE_HUT,
            11, StructureType.WORKSHOP,
            12, StructureType.DEPOT,
            14, StructureType.MILL,
            15, StructureType.FORGE
    );
    private static final int HEADER = 4;
    private static final int BELTS = 16;
    private static final int PRODUCTION = 22;
    private static final int BORDER = 24;
    private static final int LIST_START = 27;
    private static final int LIST_END = 44;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final LandService land;
    private StorageMenu storage;
    private MachineMenu machine;

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
        List<Object> listed = new ArrayList<>();
        Inventory inventory = MenuKit.create(this, new Context(host, listed), 54, "§8Build · " + hosts.title(host));
        inventory.setItem(HEADER, MenuKit.named(Material.CRAFTING_TABLE, "§6Blueprints",
                "§7Island Tier §f" + tier + " §8(" + IslandTiers.MAX_LEVEL + " max)",
                "§7Structures: §f" + structures.of(host).size(),
                "§7Belts: §f" + logistics.count(host) + "§8/§7" + logistics.cap(host),
                "",
                "§8Pick one, then look at flat ground:",
                "§8a ghost shows where it goes."));
        for (Map.Entry<Integer, StructureType> entry : BLUEPRINTS.entrySet()) {
            inventory.setItem(entry.getKey(), blueprint(player, host, entry.getValue(), tier));
        }
        boolean workshop = structures.hasWorkshop(host);
        List<String> beltLore = new ArrayList<>(List.of(
                "§7Rails that carry quarry output.",
                "§7Right-click a start, then an end;",
                "§7straight or L-shaped, keeps chaining.",
                "§7Start at a §6chute§7, end into a hut,",
                "§7depot, mill or forge.",
                "",
                "§7Cost: §6" + logistics.tileCost() + " §7per tile",
                "§7Laid: §f" + logistics.count(host) + "§8/§7" + logistics.cap(host)));
        beltLore.add(workshop ? "§a▶ Click for the Rail Layer" : "§c✖ Build a Workshop first");
        inventory.setItem(BELTS, workshop ? MenuKit.glow(MenuKit.named(Material.RAIL, "§eRail Layer §7(belts)", beltLore))
                : MenuKit.named(Material.RAIL, "§7Rail Layer §8(belts)", beltLore));
        inventory.setItem(PRODUCTION, MenuKit.named(Material.HOPPER, "§eProduction", productionLore(host)));
        inventory.setItem(BORDER, MenuKit.named(Material.SPYGLASS, "§eShow build border",
                "§7Gold particles trace where you",
                "§7may build for a few seconds."));
        int slot = LIST_START;
        for (PlacedStructure structure : structures.of(host)) {
            if (slot > LIST_END) {
                break;
            }
            if (structure.type() == StructureType.QUARRY_HOUSING || structure.type() == StructureType.PROJECT) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("§8at " + structure.x() + " " + structure.y() + " " + structure.z());
            if (structure.building()) {
                lore.add("§eBuilding…");
            }
            switch (structure.type().role()) {
                case SINK -> {
                    long used = PlacedStructure.total(structure.store());
                    lore.add("§7Stored: §f" + GuildFormat.compact(used) + "§8/§7" + GuildFormat.compact(structure.type().capacity()));
                    lore.add(logistics.summary(structure));
                    lore.add("§eClick §7to open");
                }
                case PROCESSOR -> {
                    lore.add(logistics.summary(structure));
                    lore.add("§7In: §f" + GuildFormat.compact(PlacedStructure.total(structure.input()))
                            + " §7Out: §f" + GuildFormat.compact(PlacedStructure.total(structure.output())));
                    lore.add("§eClick §7to open");
                }
                default -> lore.add("§7Its lectern opens these blueprints.");
            }
            inventory.setItem(slot, MenuKit.named(structure.type().icon(), "§f" + structure.label(), lore));
            listed.add(structure.id());
            slot++;
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBack"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack blueprint(Player player, IslandHost host, StructureType type, int tier) {
        List<String> lore = new ArrayList<>(type.blurb());
        lore.add("");
        long price = structures.priceFor(host, type);
        lore.add("§7Cost: " + (price <= 0 ? "§afree" : "§6" + GuildFormat.compact(price) + " " + hosts.fundsLabel(host))
                + (type.itemId() != null ? " §7+ §f1 " + StructureService.itemName(type.itemId()) : ""));
        lore.add("§7Built: §f" + structures.count(host, type) + "§8/§7" + type.maxCount(tier));
        if (type.role() == StructureType.Role.SINK) {
            lore.add("§7Holds: §f" + GuildFormat.compact(type.capacity()) + " §7raw-equivalent");
        }
        if (type.role() == StructureType.Role.PROCESSOR) {
            lore.add("§7Speed: §f" + GuildFormat.compact((long) type.ratePerSecond()) + " §7raw/s");
        }
        String blocked = structures.blockedReason(player, host, type);
        lore.add("");
        lore.add(blocked == null ? "§a▶ Click for the blueprint" : "§c✖ " + blocked);
        return blocked == null
                ? MenuKit.glow(MenuKit.named(type.icon(), "§e" + type.display(), lore))
                : MenuKit.named(type.icon(), "§7" + type.display(), lore);
    }

    private List<String> productionLore(IslandHost host) {
        List<String> lore = new ArrayList<>();
        List<LogisticsService.Route> routes = logistics.routes(host);
        if (routes.isEmpty()) {
            lore.add("§8No belts from any chute yet.");
            lore.add("§7Quarry → belt → Storage Hut is");
            lore.add("§7the first chain to build.");
            return lore;
        }
        int shown = 0;
        for (LogisticsService.Route route : routes) {
            if (shown++ >= 12) {
                lore.add("§8…and " + (routes.size() - 12) + " more");
                break;
            }
            String from = route.from().type() == StructureType.QUARRY_HOUSING
                    ? quarryName(host, route.from())
                    : route.from().label();
            String to = route.deadEnd() ? "§c✖ dead end" : "§f" + route.to().label();
            Res res = route.lastRes();
            lore.add("§7" + from + " §8→ " + to + (res != null ? " §8(" + res.color() + res.display() + "§8)" : ""));
        }
        return lore;
    }

    private String quarryName(IslandHost host, PlacedStructure housing) {
        var minion = hosts.minion(host, housing.minionId());
        return minion == null ? "Quarry" : minion.quarryType().display() + " §8Lv." + minion.level();
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Context context = (Context) holder.context();
        IslandHost host = context.host();
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
        if (slot == BORDER) {
            player.closeInventory();
            land.flashBorder(player, host);
            land.sendBorderMessage(player);
            return;
        }
        if (slot == PRODUCTION) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        if (slot >= LIST_START && slot <= LIST_END) {
            int index = slot - LIST_START;
            if (index >= context.listed().size()) {
                return;
            }
            PlacedStructure structure = structures.get((java.util.UUID) context.listed().get(index));
            if (structure == null) {
                return;
            }
            if (structure.type().role() == StructureType.Role.SINK && storage != null) {
                storage.open(player, structure);
            } else if (structure.type().role() == StructureType.Role.PROCESSOR && machine != null) {
                machine.open(player, structure);
            }
        }
    }

    private record Context(IslandHost host, List<Object> listed) {
    }
}
