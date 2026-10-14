package de.aetherion.guilds;

import de.aetherion.guilds.command.FriendCommand;
import de.aetherion.guilds.command.GuildCommand;
import de.aetherion.guilds.command.IslandCommand;
import de.aetherion.guilds.island.Highlight;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandBar;
import de.aetherion.guilds.island.IslandGuide;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.island.UnlockService;
import de.aetherion.guilds.listener.GuildListener;
import de.aetherion.guilds.listener.HighlightListener;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.menu.BankMenu;
import de.aetherion.guilds.menu.BiomeSelectMenu;
import de.aetherion.guilds.menu.BuildMenu;
import de.aetherion.guilds.menu.FriendMenu;
import de.aetherion.guilds.menu.GuildMenu;
import de.aetherion.guilds.menu.GuildProjectMenu;
import de.aetherion.guilds.menu.IslandMenu;
import de.aetherion.guilds.menu.LandMenu;
import de.aetherion.guilds.menu.MachineMenu;
import de.aetherion.guilds.menu.ProductionMenu;
import de.aetherion.guilds.menu.QuarryMenu;
import de.aetherion.guilds.menu.StarterSelectMenu;
import de.aetherion.guilds.menu.StorageMenu;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.placeholder.GuildPlaceholderExpansion;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.service.FriendService;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.TemplateLibrary;
import de.aetherion.guilds.util.GuildFormat;
import de.aetherion.guilds.world.VoidScrubber;

import de.aetherion.core.world.VoidChunkGenerator;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class AetherionGuilds extends JavaPlugin {

    private static AetherionGuilds instance;

    private GuildService guilds;
    private IslandService islands;
    private PersonalIslandService personal;
    private MinionService minions;
    private FriendService friends;
    private GuildMenu menu;
    private IslandMenu islandMenu;
    private FriendMenu friendMenu;

    // island highlight (starters, land, structures, belts, projects, unlock beats)
    private TemplateLibrary templates;
    private PasteService paste;
    private HostService hosts;
    private LandService land;
    private StructureService structures;
    private LogisticsService logistics;
    private PlacementService placement;
    private UnlockService unlock;
    private GuildProjectService projects;
    private Highlight highlight;
    // final knackpunkt pass: real void, the isle hand, the island bar
    private VoidScrubber voidScrubber;
    private IslandGuide guide;
    private IslandBar islandBar;
    private de.aetherion.guilds.island.FactoryGoals goals;
    private de.aetherion.guilds.island.IslandFiles islandFiles;
    private de.aetherion.guilds.island.FactoryProgress progress;
    private de.aetherion.core.api.IslandAccess islandAccess;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        islands = new IslandService(this);
        islands.enable();
        personal = new PersonalIslandService(this);
        personal.enable();
        guilds = new GuildService(this, islands);
        minions = new MinionService(this, guilds, islands);
        minions.attachPersonal(personal);
        guilds.attachMinions(minions);
        personal.attachMinions(minions);
        friends = new FriendService(this, personal);
        friendMenu = new FriendMenu(friends, guilds);
        BiomeSelectMenu biomeMenu = new BiomeSelectMenu(personal);
        islandMenu = new IslandMenu(personal, minions, friendMenu, biomeMenu);
        menu = new GuildMenu(guilds, minions, islands, friendMenu);
        BankMenu bankMenu = new BankMenu(guilds, menu);
        menu.setBank(bankMenu);
        QuarryMenu quarryMenu = new QuarryMenu(guilds, personal, minions);

        // ---- island highlight wiring -------------------------------------------------------------
        templates = new TemplateLibrary(this);
        templates.extractDefaults();
        de.aetherion.guilds.structure.Props.register(templates);
        paste = new PasteService(this);
        personal.attachHighlight(paste, templates);
        islands.attachHighlight(paste, templates);
        hosts = new HostService(personal, guilds, islands);
        voidScrubber = new VoidScrubber(this, hosts);
        getServer().getPluginManager().registerEvents(voidScrubber, this);
        land = new LandService(this, hosts);
        islandFiles = new de.aetherion.guilds.island.IslandFiles(this);
        islandFiles.load();
        structures = new StructureService(this, hosts, templates, paste);
        structures.attachFiles(islandFiles);
        structures.load();
        logistics = new LogisticsService(this, hosts, structures, minions);
        logistics.attachFiles(islandFiles);
        logistics.load();
        structures.attachBelts(logistics::isBelt);
        progress = new de.aetherion.guilds.island.FactoryProgress(this, islandFiles);
        structures.attachProgress(progress::unlocked, progress::milestone);
        progress.grandfather(structures, hosts.all());
        structures.onHousing(minions::refreshLook);
        placement = new PlacementService(this, hosts, structures, logistics);
        unlock = new UnlockService(this, personal, guilds, islands);
        projects = new GuildProjectService(this, guilds, hosts, structures, placement, unlock);
        projects.load();

        StarterSelectMenu starterMenu = new StarterSelectMenu(personal, unlock);
        BuildMenu buildMenu = new BuildMenu(hosts, structures, logistics, placement, land);
        LandMenu landMenu = new LandMenu(hosts, land);
        StorageMenu storageMenu = new StorageMenu(hosts, structures, logistics);
        MachineMenu machineMenu = new MachineMenu(hosts, structures, logistics);
        buildMenu.attach(storageMenu, machineMenu);
        storageMenu.attach(buildMenu);
        machineMenu.attach(buildMenu);
        ProductionMenu productionMenu = new ProductionMenu(hosts, structures, logistics, minions);
        productionMenu.attach(storageMenu, machineMenu, quarryMenu, buildMenu);
        buildMenu.attachProduction(productionMenu);
        storageMenu.attachProduction(productionMenu);
        machineMenu.attachProduction(productionMenu);
        GuildProjectMenu projectMenu = new GuildProjectMenu(guilds, projects);
        // elevation: belt pieces, factory goals, coin shortcuts
        goals = new de.aetherion.guilds.island.FactoryGoals(this, hosts, structures, logistics, islandFiles);
        islandFiles.finishSplit();
        de.aetherion.guilds.menu.MarketMenu marketMenu = new de.aetherion.guilds.menu.MarketMenu(this, hosts, minions);
        de.aetherion.guilds.menu.PieceMenu pieceMenu = new de.aetherion.guilds.menu.PieceMenu(hosts, structures, logistics);
        pieceMenu.attachProduction(productionMenu);
        marketMenu.attach(buildMenu);
        buildMenu.attachElevation(goals, marketMenu);
        productionMenu.attachElevation(pieceMenu, goals);
        marketMenu.attachHub(structures);
        de.aetherion.guilds.menu.HubMenu hubMenu = new de.aetherion.guilds.menu.HubMenu(hosts, structures, logistics,
                placement, buildMenu, productionMenu, marketMenu, goals);
        hubMenu.attachProgress(progress);
        de.aetherion.guilds.island.HubRitual hubRitual = new de.aetherion.guilds.island.HubRitual(this);
        structures.onHubFounded(hubRitual::play);
        logistics.setHubFooter(host -> {
            de.aetherion.guilds.structure.StructureType tour = guide == null ? null : guide.tourUnlock(host);
            de.aetherion.guilds.structure.BuildCosts.Unlock unlock = tour != null
                    ? de.aetherion.guilds.structure.BuildCosts.unlock(tour) : progress.next(host);
            String board = tour != null
                    ? "§6◆ Unlock: §f" + de.aetherion.guilds.structure.PlacementService.Part.of(tour).label()
                    + " §8(free on " + guide.name() + "'s tour)\n"
                    : unlock == null ? ""
                    : structures.hubLevel(host) >= unlock.hubLevel()
                    ? "§6◆ Next unlock: §f" + de.aetherion.guilds.structure.PlacementService.Part.of(unlock.type()).label()
                    + " §8(" + de.aetherion.guilds.structure.BuildCosts.describe(unlock.price()) + ")\n"
                    : "§6◆ Next: §fHub tier " + unlock.hubLevel() + " §8(unlocks " + de.aetherion.guilds.structure
                    .PlacementService.Part.of(unlock.type()).label() + ")\n";
            de.aetherion.guilds.island.FactoryGoals.Goal next = goals.next(host);
            return board + (next == null ? "§b✦ Every factory goal done" : "§b➤ Goal: §f" + next.title());
        });

        guide = new IslandGuide(this, hosts, structures, logistics, placement, minions);
        guide.attachMenus(islandMenu::open, buildMenu::open);
        guide.attachLectern(hubMenu::open);
        hubMenu.attachTour(guide::tourUnlock, guide::inFactoryTour, guide.name());
        structures.attachFreeFirst(guide::freeFirst);
        goals.attachTitleOwner(guide::ownsTitle);
        guide.register();
        unlock.attachGuide(guide);
        unlock.onClaim(host -> progress.milestone(host, "start"));
        unlock.attachStarterQuarry(() -> minions.createQuarryItem(de.aetherion.guilds.model.QuarryType.COBBLESTONE));
        islandBar = new IslandBar(this, hosts, structures, logistics);
        islandBar.attach(projects, guide);
        islandBar.attachGoals(goals);
        islandMenu.attachGoals(goals);
        guide.attachGoals(goals);
        getServer().getPluginManager().registerEvents(islandBar, this);
        highlight = new Highlight(templates, paste, hosts, land, unlock, structures, logistics, placement, projects,
                starterMenu, buildMenu, landMenu, projectMenu, productionMenu, guide, voidScrubber);
        islandMenu.attachHighlight(starterMenu, buildMenu, landMenu, hosts, logistics);
        islandMenu.attachHub(productionMenu, structures, land, guide);
        menu.attachHighlight(projectMenu, buildMenu, landMenu);
        quarryMenu.attachHighlight(hosts, structures, logistics);

        minions.setHooks(new MinionService.Hooks() {
            @Override
            public void placed(Player player, QuarryMinion minion, boolean personalHost, UUID hostId) {
                IslandHost host = personalHost ? IslandHost.personal(hostId) : IslandHost.guild(hostId);
                int rot = StateRotator.frontTowardViewer(player.getLocation().getYaw());
                structures.milestone(host, "first_quarry");
                player.sendMessage(switch (structures.raiseHousing(host, minion, rot)) {
                    case FULL -> "§7Its housing rises round it. Lay a belt from the §6orange mark §7(Build Tool → Belt).";
                    case RIG -> "§7Tight spot: a drill rig went up instead of the full housing."
                            + " Belts start on any cell next to it.";
                    case NONE -> "§7No room above it for a housing; belts can still start right next to it.";
                });
            }

            @Override
            public boolean housed(QuarryMinion minion) {
                PlacedStructure housing = structures.housingOfMinion(minion.id());
                return housing != null && !housing.frameless();
            }

            @Override
            public void removed(QuarryMinion minion, boolean personalHost, UUID hostId) {
                IslandHost host = personalHost ? IslandHost.personal(hostId) : IslandHost.guild(hostId);
                structures.removeHousing(host, minion.id(), true);
            }

            @Override
            public String tagSuffix(QuarryMinion minion) {
                return logistics.nametagSuffix(minion);
            }
        });
        personal.setRemovalHook(island -> {
            dropHost(IslandHost.personal(island.ownerId()));
            guide.forget(island.ownerId());
            goals.forget(IslandHost.personal(island.ownerId()));
            progress.forget(IslandHost.personal(island.ownerId()));
        });
        guilds.setRemovalHook(guild -> {
            dropHost(IslandHost.guild(guild.id()));
            projects.dropGuild(guild.id());
            goals.forget(IslandHost.guild(guild.id()));
            progress.forget(IslandHost.guild(guild.id()));
        });
        guilds.setCreatedHook((leader, guild) -> {
            projects.markVisited(guild, leader.getUniqueId());
            unlock.guildFounded(leader, guild);
        });

        GuildListener guildListener = new GuildListener(guilds, islands, personal, minions, menu, islandMenu, biomeMenu,
                quarryMenu, friendMenu, friends, bankMenu);
        guildListener.attachHosts(hosts);
        getServer().getPluginManager().registerEvents(guildListener, this);
        HighlightListener highlightListener = new HighlightListener(this, hosts, structures, logistics,
                placement, unlock, projects, minions, buildMenu, storageMenu, machineMenu, quarryMenu, projectMenu);
        highlightListener.attachPieces(pieceMenu);
        highlightListener.attachHub(hubMenu);
        getServer().getPluginManager().registerEvents(highlightListener, this);

        GuildCommand command = new GuildCommand(guilds, minions, menu);
        command.attachHighlight(highlight);
        var guild = getCommand("guild");
        if (guild != null) {
            guild.setExecutor(command);
            guild.setTabCompleter(command);
        }
        IslandCommand islandCommand = new IslandCommand(personal, islandMenu, biomeMenu);
        islandCommand.attachHighlight(highlight);
        islandCommand.attachFunnel(progress::report);
        var island = getCommand("island");
        if (island != null) {
            island.setExecutor(islandCommand);
            island.setTabCompleter(islandCommand);
        }
        FriendCommand friendCommand = new FriendCommand(friends, friendMenu);
        var friend = getCommand("friend");
        if (friend != null) {
            friend.setExecutor(friendCommand);
            friend.setTabCompleter(friendCommand);
        }

        getServer().getScheduler().runTaskLater(this, () -> {
            if (getConfig().getBoolean("reset-islands")) {
                guilds.wipeAll();
                personal.wipeAll();
                getConfig().set("reset-islands", false);
                saveConfig();
            }
            processPersonalWipeRequests();
            minions.purgeUnownedVisuals();
            minions.catchUpAll();
            minions.respawnVisuals();
            logistics.purgeLeftovers();
            structures.migrateLegacyMinions();
            structures.saveIfDirty();
            voidScrubber.sweep(false);
        }, 40L);
        // FancyNpcs loads its own NPCs late: clear guide leftovers once it's up
        getServer().getScheduler().runTaskLater(this, guide::purgeLeftovers, 100L);
        getServer().getScheduler().runTaskTimer(this, minions::tickVisuals, 20L, 4L);
        getServer().getScheduler().runTaskTimer(this, paste::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, logistics::tickCargo, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, placement::tick, 5L, 4L);
        getServer().getScheduler().runTaskTimer(this, logistics::tickActive, 60L, 20L);
        getServer().getScheduler().runTaskTimer(this, unlock::tick, 200L, 100L);
        getServer().getScheduler().runTaskTimer(this, voidScrubber::tick, 60L, 1L);
        getServer().getScheduler().runTaskTimer(this, guide::tick, 60L, 2L);
        getServer().getScheduler().runTaskTimer(this, islandBar::tick, 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            minions.catchUpAll();
            logistics.stepAll();
            guilds.save();
            personal.save();
            friends.save();
            structures.saveIfDirty();
            logistics.saveIfDirty();
            guide.saveIfDirty();
            goals.saveIfDirty();
            progress.saveIfDirty();
        }, 20L * 60, 20L * 60);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GuildPlaceholderExpansion(this).register();
        }

        // Core (island backend) lands arrivals on their island and respawns them there.
        islandAccess = new de.aetherion.guilds.island.IslandAccessImpl(personal, guilds, islands);
        de.aetherion.core.api.AetherServices.registerIslands(islandAccess);

        getLogger().info("AetherionGuilds enabled. Private + guild island worlds, quarries tick while the server runs."
                + " Island highlight: starters, land, structures, belts, projects.");
    }

    private void dropHost(IslandHost host) {
        if (islandFiles != null) {
            // keep the island's last state: written out, then moved to islands/removed/ (never deleted)
            structures.save();
            logistics.save();
            islandFiles.remove(host.key());
        }
        if (structures != null) {
            structures.dropHost(host);
            structures.save();
        }
        if (logistics != null) {
            logistics.dropHost(host);
            logistics.save();
        }
    }

    /**
     * Ops: drop {@code plugins/AetherionGuilds/wipe-personal-request.txt} with one UUID per line
     * (comments with #). On next boot those personal islands are wiped to zero — nobody else's.
     */
    private void processPersonalWipeRequests() {
        File req = new File(getDataFolder(), "wipe-personal-request.txt");
        if (!req.isFile()) {
            return;
        }
        List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(req.toPath());
        } catch (IOException exception) {
            getLogger().warning("Could not read wipe-personal-request.txt: " + exception.getMessage());
            return;
        }
        int wiped = 0;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                UUID id = UUID.fromString(line.split("\\s+")[0]);
                if (personal.wipe(id)) {
                    wiped++;
                    getLogger().info("Wipe-request: personal island wiped for " + id);
                } else {
                    getLogger().info("Wipe-request: no personal island for " + id);
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Wipe-request: bad UUID line: " + line);
            }
        }
        try {
            java.nio.file.Files.move(req.toPath(),
                    new File(getDataFolder(), "wipe-personal-request.done-" + System.currentTimeMillis() + ".txt").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            if (!req.delete()) {
                getLogger().warning("Could not clear wipe-personal-request.txt after processing");
            }
        }
        getLogger().info("Processed wipe-personal-request.txt (" + wiped + " wiped).");
    }

    /**
     * {@code bukkit.yml → worlds.<name>.generator: AetherionGuilds} (written on first start) resolves here, so the
     * island worlds come up on void whoever loads them.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }

    @Override
    public void onDisable() {
        if (islandAccess != null) {
            de.aetherion.core.api.AetherServices.clearIslands(islandAccess);
            islandAccess = null;
        }
        if (islandBar != null) {
            islandBar.shutdown();
        }
        if (guide != null) {
            guide.removeAll();
            guide.save();
        }
        if (placement != null) {
            placement.cancelAll();
        }
        if (minions != null) {
            minions.catchUpAll();
        }
        if (logistics != null) {
            logistics.stepAll();
            logistics.clearVisuals();
            logistics.save();
        }
        if (structures != null) {
            structures.save();
        }
        if (goals != null) {
            goals.saveIfDirty();
        }
        if (progress != null) {
            progress.saveIfDirty();
        }
        if (projects != null) {
            projects.save();
        }
        if (guilds != null) {
            guilds.save();
        }
        if (personal != null) {
            personal.save();
        }
        if (friends != null) {
            friends.save();
        }
        instance = null;
    }

    public static AetherionGuilds getInstance() {
        return instance;
    }

    public GuildService getGuilds() {
        return guilds;
    }

    public IslandService getIslands() {
        return islands;
    }

    public PersonalIslandService getPersonalIslands() {
        return personal;
    }

    public MinionService getMinions() {
        return minions;
    }

    public FriendService getFriends() {
        return friends;
    }

    public Highlight getHighlight() {
        return highlight;
    }

    public Guild guildAt(Location location) {
        if (guilds == null || islands == null || location == null) {
            return null;
        }
        if (!islands.isGuildWorld(location.getWorld())) {
            return null;
        }
        return guilds.byPlot(islands.plotAt(location));
    }

    public PersonalIsland personalAt(Location location) {
        if (personal == null || location == null || !personal.isPersonalWorld(location.getWorld())) {
            return null;
        }
        return personal.byPlot(personal.plotAt(location));
    }

    public String islandTitle(Location location) {
        PersonalIsland personalIsland = personalAt(location);
        if (personalIsland != null) {
            org.bukkit.OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(personalIsland.ownerId());
            String name = owner.getName() == null ? "Island" : owner.getName();
            return name + "'s Island";
        }
        Guild guild = guildAt(location);
        return guild == null ? "Guild Island" : GuildFormat.islandTitle(guild.name());
    }

    public void openMenu(org.bukkit.entity.Player player) {
        if (menu != null && player != null) {
            menu.open(player);
        }
    }

    public void openIslandMenu(org.bukkit.entity.Player player) {
        if (islandMenu != null && player != null) {
            islandMenu.open(player);
        }
    }
}
