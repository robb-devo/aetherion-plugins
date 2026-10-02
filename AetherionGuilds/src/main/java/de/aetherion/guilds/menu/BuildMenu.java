package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.FactoryGoals;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blueprints for one island, one row per job:
 * <pre>
 *   Buildings   Storage Hut · Workshop · Depot · Mill · Forge · (Quarries)
 *   Belts       Belt Layer · Splitter · Overflow Gate · Sorter
 *   More        Production · Next goal · Coin Shortcuts · Border
 * </pre>
 * Later belt pieces stay locked (with the reason on the tile) until the one before has been used, so a new
 * player sees the whole road but only one obvious next step. Reached from the island hub, the Workshop
 * lectern, the guild menu or {@code /island build}.
 */
public final class BuildMenu implements MenuKit.Menu {

    private static final Map<Integer, StructureType> BLUEPRINTS = new LinkedHashMap<>();

    static {
        BLUEPRINTS.put(10, StructureType.STORAGE_HUT);
        BLUEPRINTS.put(11, StructureType.WORKSHOP);
        BLUEPRINTS.put(12, StructureType.DEPOT);
        BLUEPRINTS.put(14, StructureType.MILL);
        BLUEPRINTS.put(15, StructureType.FORGE);
        BLUEPRINTS.put(21, StructureType.SPLITTER);
        BLUEPRINTS.put(22, StructureType.OVERFLOW);
        BLUEPRINTS.put(23, StructureType.SORTER);
    }

    private static final int HEADER = 4;
    private static final int QUARRIES = 16;
    private static final int BELTS = 19;
    private static final int PRODUCTION = 37;
    private static final int GOAL = 39;
    private static final int MARKET = 41;
    private static final int BORDER = 43;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final LandService land;
    private final Away away = new Away();
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

    public void open(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            player.sendMessage("§cNo island to build on.");
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        de.aetherion.guilds.structure.PlacedStructure hub = structures.hub(host);
        if (hub != null) {
            // once the Hub stands, Build lives at its lectern only: everywhere else just points the way
            away.open(player, host, hub);
            return;
        }
        desk(player, host, null);
    }

    /** The full Build desk: opened from the Hub lectern (or, before any Hub, from /island). */
    public void openDesk(Player player, IslandHost host) {
        if (host == null || !hosts.exists(host)) {
            return;
        }
        de.aetherion.guilds.structure.PlacedStructure hub = structures.hub(host);
        if (hub != null && !structures.atDesk(player, host)) {
            away.open(player, host, hub);
            return;
        }
        desk(player, host, hub);
    }

    private void desk(Player player, IslandHost host, de.aetherion.guilds.structure.PlacedStructure hub) {
        int tier = hosts.tier(host);
        Inventory inventory = MenuKit.framed(this, host, 54, "§8Build · " + hosts.title(host));
        FactoryGoals.Goal next = goals == null ? null : goals.next(host);
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.CRAFTING_TABLE, "§6§lBuild",
                "§7Island Tier §f" + tier + "§8/" + IslandTiers.MAX_LEVEL,
                "§7Belts §f" + logistics.count(host) + "§8/" + logistics.cap(host),
                "",
                "§7Top row: §fbuildings§7.",
                "§7Middle row: §fbelts §7and belt pieces.",
                "",
                "§8Pick one: it goes in your hand and",
                "§8a hologram shows where it stands.")));
        for (Map.Entry<Integer, StructureType> entry : BLUEPRINTS.entrySet()) {
            inventory.setItem(entry.getKey(), blueprint(player, host, entry.getValue(), tier));
        }
        inventory.setItem(QUARRIES, MenuKit.named(Material.IRON_PICKAXE, "§fQuarries",
                "§7Quarries are items: hold one and",
                "§7place it on your land like a block.",
                "§7Its housing rises around it with an",
                "§6orange chute§7: your belt starts there.",
                "",
                "§7Get one: craft it, or buy it in",
                "§6Coin Shortcuts §7" + (hub != null ? "(below)." : "(at your Hub)."),
                market != null && hub != null ? "§e▶ Open Coin Shortcuts" : ""));
        inventory.setItem(BELTS, beltLayer(player, host));
        if (production != null) {
            inventory.setItem(PRODUCTION, MenuKit.named(Material.BLAST_FURNACE, "§aProduction",
                    "§7Everything you built: what it's", "§7doing, how full, its upgrades.", "", "§e▶ Open Production"));
        }
        if (goals != null) {
            List<String> lore = new ArrayList<>();
            if (next == null) {
                lore.add("§7Every factory goal is done.");
                lore.add("§7Keep growing: land, quarries, tiers.");
            } else {
                lore.add("§f" + next.title());
                lore.add("§7" + next.how());
                lore.add("");
                lore.add("§7Reward §6" + GuildFormat.compact(next.reward()) + " " + hosts.fundsLabel(host));
                lore.add("§8Goal " + (goals.doneCount(host) + 1) + " of " + goals.all().size());
            }
            inventory.setItem(GOAL, MenuKit.tile(Material.TARGET, next == null ? "§bAll goals done" : "§bNext goal", lore,
                    next != null));
        }
        if (market != null && hub != null) {
            inventory.setItem(MARKET, MenuKit.named(Material.GOLD_INGOT, "§6Coin Shortcuts",
                    "§7Skip the crafting: quarries, the",
                    "§7Quarry Mill and Forge parts, cores.",
                    "§7Same thing as crafting it, at a",
                    "§7price that hurts.",
                    "",
                    "§e▶ Open Coin Shortcuts"));
        } else if (market != null) {
            inventory.setItem(MARKET, MenuKit.named(Material.GOLD_NUGGET, "§7Coin Shortcuts",
                    "§7Open at your §6Hub§7: build the",
                    "§7Workshop first."));
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
                "§7Conveyors that carry what quarries",
                "§7and machines make.",
                "",
                "§fRight-click §7a start, then an end:",
                "§7straight or one bend, keeps chaining.",
                "§7Start at an §6orange arrow§7, end on",
                "§7a §agreen arrow §7(hut, depot, machine).",
                "",
                "§7Cost §6" + logistics.tileCost() + " §7per tile" + (free > 0 ? " §8(§e" + free + " free left§8)" : ""),
                "§7Laid §f" + logistics.count(host) + "§8/" + logistics.cap(host)));
        lore.add("");
        if (workshop || starterKit) {
            lore.add(starterKit ? "§a▶ Starter kit: no Workshop needed" : "§a▶ Take the Belt Layer");
            return MenuKit.glow(MenuKit.named(Material.IRON_TRAPDOOR, "§e§lBelt Layer", lore));
        }
        lore.add("§c✖ Found your Hub first (the Workshop)");
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
            lore.add("§7Holds §f" + GuildFormat.compact(type.capacity()) + " §7goods");
        }
        if (type.role() == StructureType.Role.PROCESSOR) {
            lore.add("§7Speed §f" + GuildFormat.compact((long) type.ratePerSecond()) + " §7goods / s");
        }
        if (type.router()) {
            lore.add("§8Sits on the belt line, 1 block.");
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
        if (blocked != null && type.itemId() != null && blocked.startsWith("Needs 1") && market != null) {
            lore.add("");
            lore.add("§c✖ " + blocked);
            lore.add("§7Craft it, or buy it in §6Coin Shortcuts§7.");
            lore.add("§eRight-click §7to open Coin Shortcuts");
            return MenuKit.named(type.icon(), "§7" + type.display(), lore);
        }
        lore.add("");
        lore.add(blocked == null ? "§a▶ Take the blueprint" : "§c✖ " + blocked);
        boolean nextUp = blocked == null && ((type == StructureType.STORAGE_HUT && structures.count(host, type) == 0)
                || goalWants(host, type));
        return blocked == null
                ? MenuKit.glow(MenuKit.named(type.icon(), "§e" + type.display() + (nextUp ? " §6◆" : ""), lore))
                : MenuKit.named(type.icon(), "§7" + type.display(), lore);
    }

    /**
     * Opened away from the Hub (from /island, a command, far across the island): building happens at the Hub,
     * so this only points the way there.
     */
    private final class Away implements MenuKit.Menu {
        private static final int WAY = 11;
        private static final int GO = 13;
        private static final int OVERVIEW = 15;

        void open(Player player, IslandHost host, de.aetherion.guilds.structure.PlacedStructure hub) {
            Inventory inventory = MenuKit.framed(this, host, 27, "§8Build · at your Hub");
            double dx = hub.x() + 0.5 - player.getLocation().getX();
            double dz = hub.z() + 0.5 - player.getLocation().getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            inventory.setItem(4, MenuKit.glow(MenuKit.named(Material.LECTERN, "§6§lYour Hub runs the factory",
                    "§7Build, the Belt Layer, belt pieces,",
                    "§7upgrades and Coin Shortcuts are at",
                    "§7the §fWorkshop lectern§7.",
                    "",
                    distance <= StructureService.HUB_REACH
                            ? "§aYou're right here: right-click the lectern."
                            : "§7It's §f" + distance + " §7blocks " + direction(dx, dz) + ".")));
            inventory.setItem(WAY, MenuKit.named(Material.COMPASS, "§eShow me the way",
                    "§7A trail of sparks toward the Hub.", "", "§e▶ Click"));
            inventory.setItem(GO, MenuKit.named(Material.ENDER_PEARL, "§aTake me to the Hub",
                    "§7Stand in front of the Workshop.", "", "§e▶ Click"));
            if (production != null) {
                inventory.setItem(OVERVIEW, MenuKit.named(Material.BLAST_FURNACE, "§aProduction",
                        "§7How every machine is doing.", "§8(Upgrades at the Hub.)", "", "§e▶ Open"));
            }
            inventory.setItem(18, MenuKit.named(Material.ARROW, host.isGuild() ? "§eBack to the guild" : "§eBack to your island"));
            inventory.setItem(22, MenuKit.named(Material.BARRIER, "§cClose"));
            player.openInventory(inventory);
        }

        @Override
        public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
            IslandHost host = (IslandHost) holder.context();
            de.aetherion.guilds.structure.PlacedStructure hub = structures.hub(host);
            switch (slot) {
                case WAY -> {
                    player.closeInventory();
                    if (hub != null) {
                        trail(player, hub);
                    }
                }
                case GO -> {
                    player.closeInventory();
                    org.bukkit.Location spot = structures.hubSpot(host);
                    if (spot != null) {
                        player.teleport(spot);
                        player.playSound(spot, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
                        player.sendMessage("§6✦ §7At your Hub. Right-click the §flectern§7.");
                    }
                }
                case OVERVIEW -> {
                    if (production != null) {
                        production.open(player, host);
                    }
                }
                case 18 -> {
                    player.closeInventory();
                    player.performCommand(host.isGuild() ? "guild" : "island");
                }
                case 22 -> player.closeInventory();
                default -> {
                }
            }
        }
    }

    private static String direction(double dx, double dz) {
        String ns = dz < -2 ? "north" : dz > 2 ? "south" : "";
        String ew = dx > 2 ? "east" : dx < -2 ? "west" : "";
        String both = ns.isEmpty() || ew.isEmpty() ? ns + ew : ns + "-" + ew;
        return both.isEmpty() ? "away" : both;
    }

    /** Sparks from the player toward the Hub (only they see them). */
    private static void trail(Player player, de.aetherion.guilds.structure.PlacedStructure hub) {
        org.bukkit.Location from = player.getLocation().add(0, 1.0, 0);
        org.bukkit.util.Vector to = new org.bukkit.util.Vector(hub.x() + 0.5, hub.y() + 2.0, hub.z() + 0.5);
        org.bukkit.util.Vector step = to.clone().subtract(from.toVector());
        double length = step.length();
        if (length < 1) {
            return;
        }
        step.normalize().multiply(0.6);
        org.bukkit.Location point = from.clone();
        org.bukkit.Particle.DustOptions gold = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 70), 1.2f);
        for (double walked = 0; walked < Math.min(length, 60); walked += 0.6) {
            point.add(step);
            player.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0, gold);
        }
        player.spawnParticle(org.bukkit.Particle.END_ROD, to.toLocation(player.getWorld()), 20, 0.4, 1.0, 0.4, 0.02);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    /** Does the next factory goal want this blueprint? (It gets the ◆.) */
    private boolean goalWants(IslandHost host, StructureType type) {
        FactoryGoals.Goal next = goals == null ? null : goals.next(host);
        if (next == null) {
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
        StructureType type = BLUEPRINTS.get(slot);
        if (type != null) {
            if (click.isRightClick() && market != null && type.itemId() != null) {
                String blocked = structures.blockedReason(player, host, type);
                if (blocked != null && blocked.startsWith("Needs 1")) {
                    market.open(player, host);
                    return;
                }
            }
            if (type.router()) {
                placement.startPiece(player, host, type);
            } else {
                placement.startStructure(player, host, type);
            }
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
        if ((slot == MARKET || slot == QUARRIES) && market != null && structures.hub(host) != null) {
            market.open(player, host);
            return;
        }
        if (slot == GOAL && goals != null) {
            FactoryGoals.Goal next = goals.next(host);
            if (next != null) {
                player.closeInventory();
                player.sendMessage("§b➤ " + next.title() + "§7: " + next.how());
            }
            return;
        }
        if (slot == BORDER) {
            player.closeInventory();
            land.flashBorder(player, host);
            land.sendBorderMessage(player);
        }
    }
}
