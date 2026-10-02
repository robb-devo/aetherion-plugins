package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandGuide;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.util.AetherionItemsAccess;
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
 * {@code /island}: the one home for your island. Build, Production and Land are the three big doors; home,
 * tier, Hollis, friends, collect and the border sit below. The tile that is the obvious next thing glows.
 * {@code /island build|land|production} jump straight into the same pages.
 */
public final class IslandMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int BUILD = 20;
    private static final int PRODUCTION = 22;
    private static final int LAND = 24;
    private static final int HOME = 29;
    private static final int TIER = 31;
    private static final int GUIDE = 33;
    private static final int FRIENDS = 38;
    private static final int COLLECT = 40;
    private static final int BORDER = 42;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private enum Next {
        HUT,
        QUARRY,
        WORKSHOP_OR_BELT,
        NONE
    }

    private final PersonalIslandService islands;
    private final MinionService minions;
    private final FriendMenu friends;
    private final BiomeSelectMenu biomes;
    private StarterSelectMenu starters;
    private BuildMenu build;
    private LandMenu land;
    private ProductionMenu production;
    private HostService hosts;
    private LogisticsService logistics;
    private StructureService structures;
    private LandService landService;
    private IslandGuide guide;

    public IslandMenu(PersonalIslandService islands, MinionService minions, FriendMenu friends, BiomeSelectMenu biomes) {
        this.islands = islands;
        this.minions = minions;
        this.friends = friends;
        this.biomes = biomes;
    }

    /** Island highlight: starters, blueprints, land, production, guide. */
    public void attachHighlight(StarterSelectMenu starters, BuildMenu build, LandMenu land, HostService hosts,
                                LogisticsService logistics) {
        this.starters = starters;
        this.build = build;
        this.land = land;
        this.hosts = hosts;
        this.logistics = logistics;
    }

    public void attachHub(ProductionMenu production, StructureService structures, LandService landService,
                          IslandGuide guide) {
        this.production = production;
        this.structures = structures;
        this.landService = landService;
        this.guide = guide;
    }

    public void open(Player player) {
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            if (starters != null) {
                starters.open(player);
            } else {
                player.sendMessage(AetherionItemsAccess.islandHint());
            }
            return;
        }
        PersonalIsland island = islands.byOwner(player.getUniqueId());
        if (island == null) {
            if (starters != null) {
                starters.open(player);
            } else {
                biomes.open(player);
            }
            return;
        }
        IslandHost host = IslandHost.personal(island.ownerId());
        StarterLayout starter = StarterLayout.byId(island.starter());
        Inventory inventory = MenuKit.framed(this, host, 54, "§8✦ " + (hosts != null ? hosts.title(host) : "Your Island"));
        Next next = next(host, island);

        inventory.setItem(HEADER, header(island, host, starter));

        if (build != null) {
            List<String> lore = new ArrayList<>(List.of(
                    "§7Blueprints: Storage Hut, Workshop,",
                    "§7Depot, Mill, Forge, and the",
                    "§7Belt Layer for conveyors.",
                    ""));
            switch (next) {
                case HUT -> lore.add("§6◆ Next: §fStorage Hut §7(first one free)");
                case WORKSHOP_OR_BELT -> lore.add("§6◆ Next: §fa belt from the quarry chute");
                default -> {
                }
            }
            lore.add("§e▶ Open blueprints");
            inventory.setItem(BUILD, MenuKit.tile(Material.CRAFTING_TABLE, "§6§lBuild", lore,
                    next == Next.HUT || next == Next.WORKSHOP_OR_BELT));
        }
        if (production != null) {
            inventory.setItem(PRODUCTION, MenuKit.tile(Material.BLAST_FURNACE, "§a§lProduction",
                    productionLore(host, island, next), next == Next.QUARRY || upgradeReady(player, host)));
        }
        if (land != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Owned: §f" + hosts.parcels(host).size() + " §7parcels §8(16×16)");
            if (landService != null) {
                lore.add("§7Next parcel: §6" + GuildFormat.compact(landService.price(host)) + " §7coins");
            }
            lore.add("");
            lore.add("§7Buy land touching yours; new");
            lore.add("§7ground rises there in your");
            lore.add("§7island's look.");
            lore.add("");
            lore.add("§e▶ Open the land map");
            inventory.setItem(LAND, MenuKit.tile(Material.FILLED_MAP, "§b§lLand", lore, false));
        }

        inventory.setItem(HOME, MenuKit.named(Material.OAK_DOOR, "§aGo Home", "§7Teleport to your island.", "",
                "§e▶ Click"));
        inventory.setItem(TIER, tierTile(island));
        inventory.setItem(GUIDE, guideTile(island));
        inventory.setItem(FRIENDS, MenuKit.named(Material.PLAYER_HEAD, "§bFriends & Visits",
                "§7Friends can visit your island", "§7while the server is online.", "", "§e▶ Open friends"));
        inventory.setItem(COLLECT, collectTile(island));
        inventory.setItem(BORDER, MenuKit.named(Material.SPYGLASS, "§eShow build border",
                "§7Gold particles trace where", "§7you may build, for a few seconds."));

        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§7Aetherion Manager"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------------------------------------
    // tiles
    // ------------------------------------------------------------------------------------------------

    private ItemStack header(PersonalIsland island, IslandHost host, StarterLayout starter) {
        List<String> lore = new ArrayList<>();
        lore.add(starter != null ? "§7" + starter.display() : "§7" + island.biome().display() + " pad");
        lore.add("");
        lore.add("§7Island Tier  §f" + island.islandLevel() + "§8/" + IslandTiers.MAX_LEVEL);
        lore.add("§7Build radius §f" + islands.buildRadius(island) + " §8+ §f" + hosts.parcels(host).size() + " §7parcels");
        lore.add("§7Quarries     §f" + island.minions().size());
        if (structures != null) {
            int buildings = 0;
            long used = 0L;
            long cap = 0L;
            for (PlacedStructure structure : structures.of(host)) {
                if (structure.type() == StructureType.QUARRY_HOUSING || structure.type() == StructureType.PROJECT) {
                    continue;
                }
                buildings++;
                if (structure.type().role() == StructureType.Role.SINK) {
                    used += PlacedStructure.total(structure.store());
                    cap += structure.capacity();
                }
            }
            lore.add("§7Buildings    §f" + buildings);
            if (cap > 0) {
                lore.add("§7Storage      " + de.aetherion.guilds.project.GuildProjectService.bar(used, cap));
            }
        }
        if (logistics != null) {
            int running = 0;
            for (LogisticsService.Route route : logistics.routes(host)) {
                if (route.to() != null) {
                    running++;
                }
            }
            lore.add("§7Belts        §f" + logistics.count(host) + "§8/" + logistics.cap(host)
                    + (running > 0 ? " §8· §a" + running + " running" : ""));
        }
        return MenuKit.glow(MenuKit.named(starter != null ? starter.icon() : island.biome().icon(),
                "§6§l" + hosts.title(host), lore));
    }

    private List<String> productionLore(IslandHost host, PersonalIsland island, Next next) {
        List<String> lore = new ArrayList<>();
        int shown = 0;
        if (logistics != null) {
            for (LogisticsService.Route route : logistics.routes(host)) {
                if (shown >= 4) {
                    break;
                }
                lore.add("§7" + route.from().label() + " §8→ "
                        + (route.deadEnd() ? "§c✖ nowhere" : "§f" + route.to().label()));
                shown++;
            }
        }
        if (shown == 0) {
            lore.add("§8No chains yet: quarry → belt → hut.");
        }
        lore.add("");
        lore.add("§7Buildings, quarries, upgrades,");
        lore.add("§7storage and every chain.");
        if (next == Next.QUARRY) {
            lore.add("");
            lore.add("§6◆ Next: §fplace a quarry near the hut");
        }
        lore.add("");
        lore.add("§e▶ Open production");
        return lore;
    }

    private ItemStack tierTile(PersonalIsland island) {
        if (!island.canUpgradeIsland()) {
            return MenuKit.named(Material.NETHER_STAR, "§6Island Tier " + island.islandLevel() + " §8(max)",
                    "§7Fully grown. Land parcels still", "§7add room to build.");
        }
        IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(island.islandLevel());
        List<String> lore = new ArrayList<>();
        lore.add("§7Now §fTier " + island.islandLevel() + " §8→ §fTier " + (island.islandLevel() + 1));
        lore.add(IslandTiers.perkLine(island.islandLevel() + 1));
        lore.add("");
        if (cost.compactedCobble() > 0) {
            lore.add("§f" + cost.compactedCobble() + " Compacted Cobble");
        }
        if (cost.cores() > 0) {
            lore.add("§d" + cost.cores() + " Quarry Core");
        }
        if (cost.coins() > 0) {
            lore.add("§6" + GuildFormat.compact(cost.coins()) + " coins");
        }
        lore.add("");
        lore.add("§e▶ Raise the tier");
        return MenuKit.named(Material.BEACON, "§eIsland Tier §f" + island.islandLevel(), lore);
    }

    private ItemStack guideTile(PersonalIsland island) {
        if (guide == null || !guide.available()) {
            return MenuKit.named(Material.BOOK, "§eFirst steps",
                    "§71. Storage Hut on the marked pad", "§72. A quarry a few steps away",
                    "§73. A belt from its chute into the hut");
        }
        IslandGuide.Step step = guide.step(island.ownerId());
        String objective = guide.objective(island.ownerId());
        List<String> lore = new ArrayList<>();
        lore.add("§7The isle hand. He walks you");
        lore.add("§7through hut → quarry → belt.");
        lore.add("");
        if (objective != null) {
            lore.add("§6◆ " + "§f" + objective.replace("§7", "§8"));
            lore.add("§7Tour: §f" + guide.progress(island.ownerId()) + "§8/§f3 §7done");
            lore.add("");
            lore.add("§e▶ Carry on with " + guide.name());
        } else if (step == IslandGuide.Step.SKIPPED) {
            lore.add("§8Tour skipped.");
            lore.add("");
            lore.add("§e▶ Take the tour after all");
        } else {
            lore.add("§a✔ Tour done. He's in the yard");
            lore.add("§afor tips.");
            lore.add("");
            lore.add("§e▶ Talk to " + guide.name());
        }
        return MenuKit.tile(Material.WRITABLE_BOOK, "§e" + guide.name() + " §7· Isle Hand", lore, objective != null);
    }

    private ItemStack collectTile(PersonalIsland island) {
        List<String> lore = new ArrayList<>();
        if (island.minions().isEmpty()) {
            lore.add("§8No quarries placed yet.");
        } else {
            int shown = 0;
            for (QuarryMinion minion : island.minions()) {
                if (shown++ >= 5) {
                    lore.add("§8…and " + (island.minions().size() - 5) + " more");
                    break;
                }
                QuarryMinion.StorageView view = minions.preview(minion);
                lore.add("§7" + minion.quarryType().display() + " §8Lv." + minion.level()
                        + " §7· §e" + GuildFormat.compact(view.rawEquivalent()) + "§8/" + GuildFormat.compact(minion.cap()));
            }
        }
        lore.add("");
        lore.add("§8Belted quarries fill your huts");
        lore.add("§8on their own.");
        lore.add("");
        lore.add("§e▶ Empty every quarry into your pack");
        return MenuKit.named(Material.CHEST, "§eCollect all quarries", lore);
    }

    // ------------------------------------------------------------------------------------------------
    // what's next
    // ------------------------------------------------------------------------------------------------

    private Next next(IslandHost host, PersonalIsland island) {
        if (structures == null) {
            return Next.NONE;
        }
        if (structures.count(host, StructureType.STORAGE_HUT) == 0) {
            return Next.HUT;
        }
        if (island.minions().isEmpty()) {
            return Next.QUARRY;
        }
        if (logistics != null) {
            for (LogisticsService.Route route : logistics.routes(host)) {
                if (route.to() != null) {
                    return Next.NONE;
                }
            }
        }
        return Next.WORKSHOP_OR_BELT;
    }

    private boolean upgradeReady(Player player, IslandHost host) {
        if (structures == null) {
            return false;
        }
        for (PlacedStructure structure : structures.of(host)) {
            if (structures.nextTier(structure) != null && structures.upgradeBlocked(player, structure) == null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // clicks
    // ------------------------------------------------------------------------------------------------

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        PersonalIsland island = islands.byOwner(player.getUniqueId());
        if (island == null) {
            player.closeInventory();
            open(player);
            return;
        }
        IslandHost host = IslandHost.personal(island.ownerId());
        switch (slot) {
            case BUILD -> {
                if (build != null) {
                    build.open(player, host);
                }
            }
            case PRODUCTION -> {
                if (production != null) {
                    production.open(player, host);
                }
            }
            case LAND -> {
                if (land != null) {
                    land.open(player, host);
                }
            }
            case HOME -> {
                player.closeInventory();
                islands.goHome(player);
            }
            case TIER -> {
                islands.upgradeIsland(player);
                open(player);
            }
            case GUIDE -> {
                player.closeInventory();
                if (guide != null && guide.available()) {
                    guide.resume(player);
                } else {
                    player.sendMessage("§6First steps: §7Storage Hut on the marked pad → a quarry a few steps away"
                            + " → a belt from its chute into the hut.");
                }
            }
            case FRIENDS -> friends.open(player);
            case COLLECT -> {
                minions.collectAll(player, island);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
                open(player);
            }
            case BORDER -> {
                player.closeInventory();
                if (landService != null) {
                    landService.flashBorder(player, host);
                    landService.sendBorderMessage(player);
                }
            }
            case BACK -> {
                player.closeInventory();
                player.performCommand("aethermanager");
            }
            case CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }
}
