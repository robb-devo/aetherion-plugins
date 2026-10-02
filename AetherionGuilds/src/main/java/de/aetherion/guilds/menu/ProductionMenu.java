package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.structure.StructureUpgrades;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything that makes things on an island, on one page: buildings first (storage fill, machine speed, the
 * upgrade that's waiting), then the quarries, then the chains between them. Click a building or quarry to
 * manage it; the ⬆ marker means an upgrade can be paid right now.
 */
public final class ProductionMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int[] LIST = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int CHAINS = 40;
    private static final int BACK = 45;
    private static final int BUILD = 48;
    private static final int CLOSE = 49;

    private record Entry(UUID structure, UUID minion) {
    }

    private record Context(IslandHost host, List<Entry> entries) {
    }

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final MinionService minions;
    private StorageMenu storage;
    private MachineMenu machine;
    private QuarryMenu quarry;
    private BuildMenu build;

    public ProductionMenu(HostService hosts, StructureService structures, LogisticsService logistics,
                          MinionService minions) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.minions = minions;
    }

    public void attach(StorageMenu storage, MachineMenu machine, QuarryMenu quarry, BuildMenu build) {
        this.storage = storage;
        this.machine = machine;
        this.quarry = quarry;
        this.build = build;
    }

    public void open(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            player.sendMessage("§cNo island here.");
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        List<Entry> entries = new ArrayList<>();
        Inventory inventory = MenuKit.framed(this, new Context(host, entries), 54, "§8Production · " + hosts.title(host));
        int index = 0;
        long used = 0L;
        long cap = 0L;
        for (PlacedStructure structure : structures.of(host)) {
            StructureType type = structure.type();
            if (type == StructureType.QUARRY_HOUSING || type == StructureType.PROJECT) {
                continue;
            }
            if (type.role() == StructureType.Role.SINK) {
                used += PlacedStructure.total(structure.store());
                cap += structure.capacity();
            }
            if (index >= LIST.length) {
                continue;
            }
            inventory.setItem(LIST[index++], buildingTile(player, structure));
            entries.add(new Entry(structure.id(), null));
        }
        for (QuarryMinion minion : hosts.minions(host)) {
            if (index >= LIST.length) {
                break;
            }
            inventory.setItem(LIST[index++], quarryTile(host, minion));
            entries.add(new Entry(null, minion.id()));
        }
        if (index == 0) {
            inventory.setItem(22, MenuKit.named(Material.BARREL, "§7Nothing running yet",
                    "§7Build a §fStorage Hut §7(free),", "§7set a quarry beside it and lay a",
                    "§7belt from its chute into the hut.", "", "§e▶ Blueprints below"));
        }
        List<String> head = new ArrayList<>();
        head.add("§7Buildings + quarries: §f" + entries.size());
        if (cap > 0) {
            head.add("§7Storage " + GuildProjectService.bar(used, cap));
            head.add("§8" + GuildFormat.compact(used) + " / " + GuildFormat.compact(cap) + " raw-eq");
        }
        head.add("");
        head.add("§8⬆ = an upgrade you can pay now");
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.BLAST_FURNACE, "§a§lProduction", head)));
        inventory.setItem(CHAINS, MenuKit.named(Material.IRON_TRAPDOOR, "§eChains", chainLore(host)));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, host.isGuild() ? "§eBack to the guild" : "§eBack to your island"));
        inventory.setItem(BUILD, MenuKit.named(Material.CRAFTING_TABLE, "§6Blueprints", "§7Build more, lay belts."));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack buildingTile(Player player, PlacedStructure structure) {
        List<String> lore = new ArrayList<>();
        StructureType type = structure.type();
        int max = StructureUpgrades.maxLevel(type);
        if (max > 1) {
            lore.add("§7Tier §f" + structure.level() + "§8/" + max);
        }
        if (structure.building()) {
            lore.add("§eBuilding…");
        }
        switch (type.role()) {
            case SINK -> {
                long used = PlacedStructure.total(structure.store());
                lore.add("§7Stored " + GuildProjectService.bar(used, structure.capacity()));
                lore.add("§8" + GuildFormat.compact(used) + " / " + GuildFormat.compact(structure.capacity()) + " raw-eq");
                lore.add(logistics.summary(structure));
            }
            case PROCESSOR -> {
                lore.add("§7Speed §f" + GuildFormat.compact((long) structure.ratePerSecond()) + " §7raw-eq/s");
                lore.add(logistics.summary(structure));
                boolean working = System.currentTimeMillis() - structure.lastWork() < 5000L;
                lore.add(working ? "§a● working" : "§8○ idle");
            }
            default -> lore.add("§7Its lectern opens the blueprints.");
        }
        StructureUpgrades.Tier next = structures.nextTier(structure);
        boolean ready = false;
        if (next != null) {
            ready = structures.upgradeBlocked(player, structure) == null;
            lore.add("");
            lore.add((ready ? "§a⬆ " : "§7⬆ ") + next.name() + (ready ? " §a(ready)" : ""));
        }
        lore.add("");
        lore.add(type.role() == StructureType.Role.UTILITY ? "§8Manage it at its lectern." : "§e▶ Open");
        String name = (ready ? "§a" : "§f") + structure.label() + (ready ? " §a⬆" : "");
        org.bukkit.inventory.ItemStack item = MenuKit.named(type.icon(), name, lore);
        return ready ? MenuKit.glow(item) : item;
    }

    private org.bukkit.inventory.ItemStack quarryTile(IslandHost host, QuarryMinion minion) {
        QuarryMinion.StorageView view = minions.preview(minion);
        PlacedStructure housing = structures.housingOfMinion(minion.id());
        List<String> lore = new ArrayList<>();
        lore.add("§7Level §f" + minion.level() + "§8/" + QuarryMinion.MAX_LEVEL);
        lore.add("§7Holding §e" + GuildFormat.compact(view.rawEquivalent()) + "§8/" + GuildFormat.compact(minion.cap()));
        lore.add(housing == null ? "§8No housing" : logistics.summary(housing));
        lore.add("");
        lore.add("§e▶ Open quarry");
        return MenuKit.named(minion.quarryType().icon(), "§f" + minion.quarryType().display(), lore);
    }

    private List<String> chainLore(IslandHost host) {
        List<String> lore = new ArrayList<>();
        List<LogisticsService.Route> routes = logistics.routes(host);
        if (routes.isEmpty()) {
            lore.add("§8No belts from any chute yet.");
            lore.add("§7Quarry → belt → Storage Hut");
            lore.add("§7is the first chain to build.");
            return lore;
        }
        int shown = 0;
        for (LogisticsService.Route route : routes) {
            if (shown++ >= 12) {
                lore.add("§8…and " + (routes.size() - 12) + " more");
                break;
            }
            String from = route.from().type() == StructureType.QUARRY_HOUSING
                    ? quarryName(host, route.from()) : route.from().label();
            String to = route.deadEnd() ? "§c✖ belt ends nowhere" : "§f" + route.to().label();
            Res res = route.lastRes();
            lore.add("§7" + from + " §8→ " + to + (res != null ? " §8(" + res.color() + res.display() + "§8)" : ""));
        }
        lore.add("");
        lore.add("§7Belts §f" + logistics.count(host) + "§8/" + logistics.cap(host));
        return lore;
    }

    private String quarryName(IslandHost host, PlacedStructure housing) {
        QuarryMinion minion = hosts.minion(host, housing.minionId());
        return minion == null ? "Quarry" : minion.quarryType().display() + " §8Lv." + minion.level();
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Context context = (Context) holder.context();
        IslandHost host = context.host();
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> {
                player.closeInventory();
                player.performCommand(host.isGuild() ? "guild" : "island");
            }
            case BUILD -> {
                if (build != null) {
                    build.open(player, host);
                }
            }
            default -> {
                int index = -1;
                for (int i = 0; i < LIST.length; i++) {
                    if (LIST[i] == slot) {
                        index = i;
                        break;
                    }
                }
                if (index < 0 || index >= context.entries().size()) {
                    return;
                }
                Entry entry = context.entries().get(index);
                if (entry.structure() != null) {
                    PlacedStructure structure = structures.get(entry.structure());
                    if (structure == null) {
                        open(player, host);
                        return;
                    }
                    if (structure.type().role() == StructureType.Role.SINK && storage != null) {
                        storage.open(player, structure);
                    } else if (structure.type().role() == StructureType.Role.PROCESSOR && machine != null) {
                        machine.open(player, structure);
                    }
                    return;
                }
                QuarryMinion minion = hosts.minion(host, entry.minion());
                if (minion == null || quarry == null) {
                    return;
                }
                if (host.isGuild()) {
                    Guild guild = hosts.guild(host);
                    if (guild != null) {
                        quarry.open(player, new MinionService.QuarryRef(guild, null, minion));
                    }
                } else {
                    PersonalIsland island = hosts.island(host);
                    if (island != null) {
                        quarry.open(player, new MinionService.QuarryRef(null, island, minion));
                    }
                }
            }
        }
    }
}
