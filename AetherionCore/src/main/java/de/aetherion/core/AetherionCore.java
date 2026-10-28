package de.aetherion.core;

import de.aetherion.core.command.ExileCommand;
import de.aetherion.core.command.NetworkRestartCommand;
import de.aetherion.core.command.WipeCommand;
import de.aetherion.core.command.TransferCommand;
import de.aetherion.core.moderation.RaidBans;
import de.aetherion.core.moderation.RaidMenu;
import de.aetherion.core.moderation.RaidRoster;
import de.aetherion.core.network.CharacterHost;
import de.aetherion.core.network.ExileHost;
import de.aetherion.core.network.ExileLink;
import de.aetherion.core.network.IslandLink;
import de.aetherion.core.network.IslandsHost;
import de.aetherion.core.network.MainCommandRouter;
import de.aetherion.core.network.MainWorldLink;
import de.aetherion.core.network.NetworkRouter;
import de.aetherion.core.network.RouteBook;
import de.aetherion.core.network.ServerNames;
import de.aetherion.core.network.TransferSnapshotStore;
import de.aetherion.core.shutdown.ShutdownCountdown;
import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;
import de.aetherion.core.world.VoidChunkGenerator;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared library plugin. Loads first. Does not tick the game.
 *
 * <p>Keeps network transfer helpers ({@link MainWorldLink} / Core {@link TransferSnapshotStore})
 * for backends that do not run AetherionDungeons (Hub). MMO-R/MMO-D also keep Dungeons transfer.
 * Exile hops ({@link ExileLink} / {@link ExileHost}) live here so MMO-C can host the island.
 *
 * <p>Network wiring: {@link NetworkRouter} moves players between mmo-r / mmo-d / mmo-c / mmo-i (character v6 +
 * route). Dungeons provides the character transport where it runs; {@link CharacterHost} is Core's port for
 * mmo-c / mmo-i. {@link IslandLink} sends {@code /island} & co. to mmo-i from backends without Guilds;
 * {@link IslandsHost} lands arrivals on their island there.
 */
public final class AetherionCore extends JavaPlugin {

    private static AetherionCore instance;
    private BetaWipe betaWipe;
    private NetworkWipeWatch networkWipeWatch;
    private MainWorldLink link;
    private TransferSnapshotStore snapshots;
    private ExileLink exile;
    private ExileHost exileHost;
    private CharacterHost characterHost;
    private NetworkRouter router;
    private IslandLink islandLink;
    private IslandsHost islandsHost;
    private de.aetherion.core.exile.ExileQuay exileQuay;
    private RaidRoster raidRoster;
    private RaidBans raidBans;

    public static AetherionCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        betaWipe = new BetaWipe(this);
        if (betaWipe.isPending()) {
            betaWipe.run();
            betaWipe.clearPending();
        }
        trimExileOnce();
    }

    /** Before any world loads: cut the old overworld out of the exile world when an op armed it (see ExileVoidTrim). */
    private void trimExileOnce() {
        java.io.File armed = new java.io.File(getDataFolder(), "exile-void-trim.armed");
        if (!armed.isFile()) {
            return;
        }
        java.io.File world = new java.io.File(getServer().getWorldContainer(), getConfig().getString("exile.world", "aetherion_exile"));
        String result = world.isDirectory()
                ? de.aetherion.core.exile.ExileVoidTrim.run(world, (int) Math.floor(getConfig().getDouble("exile.spawn.x", 162.5)),
                        (int) Math.floor(getConfig().getDouble("exile.spawn.z", 6.5)), getLogger())
                : "skipped: no world folder " + world.getName();
        getLogger().info("[ExileVoid] " + world.getName() + ": " + result);
        java.io.File done = new java.io.File(getDataFolder(), "exile-void-trim." + (result.startsWith("aborted") ? "aborted" : "done"));
        if (!armed.renameTo(done)) {
            armed.delete();
        }
    }

    @Override
    public void onEnable() {
        if (betaWipe == null) {
            betaWipe = new BetaWipe(this);
        }
        getLogger().info("Wipe paths: " + betaWipe.layout().describe());
        networkWipeWatch = new NetworkWipeWatch(this, betaWipe);
        WipeCommand wipeCommand = new WipeCommand(this, betaWipe, networkWipeWatch);
        PluginCommand wipe = getCommand("wipe");
        if (wipe != null) {
            wipe.setExecutor(wipeCommand);
            wipe.setTabCompleter(wipeCommand);
        }

        PluginCommand report = getCommand("report");
        if (report != null) {
            report.setExecutor(new de.aetherion.core.command.ReportCommand(this));
        }

        NetworkRestartCommand restartCommand = new NetworkRestartCommand(this);
        PluginCommand aenet = getCommand("aenet");
        if (aenet != null) {
            aenet.setExecutor(restartCommand);
            aenet.setTabCompleter(restartCommand);
        }

        networkWipeWatch.start();

        snapshots = new TransferSnapshotStore(this);
        link = new MainWorldLink(this, snapshots);
        String here = link.serverName();
        characterHost = CharacterHost.createIfApplicable(this, here, snapshots.directory());
        router = new NetworkRouter(this, link, new RouteBook(this, snapshots.directory()), characterHost);
        new MainCommandRouter(this, router);
        islandLink = new IslandLink(this, router);
        if (ServerNames.isIslands(here)) {
            islandsHost = new IslandsHost(this, router);
        }
        exile = new ExileLink(this, link, snapshots);
        exileHost = new ExileHost(this, exile, snapshots);
        if (!exile.isExileServer() && !ServerNames.isMines(here)) {
            // The Veil Quay islet (ferry departure) lives on the main world; idle until /exile setquay placed it.
            // The mine server carries mmo-r's config copy, so it must never build the islet into its own world.
            exileQuay = new de.aetherion.core.exile.ExileQuay(this, exile);
        }

        ExileCommand exileCommand = new ExileCommand(this);
        PluginCommand exileCmd = getCommand("exile");
        if (exileCmd != null) {
            exileCmd.setExecutor(exileCommand);
            exileCmd.setTabCompleter(exileCommand);
        }

        TransferCommand transferCommand = new TransferCommand(this);
        PluginCommand transferCmd = getCommand("aetransfer");
        if (transferCmd != null) {
            transferCmd.setExecutor(transferCommand);
            transferCmd.setTabCompleter(transferCommand);
        }

        // Raid menu: rosters and IP bans shared by every backend through <shared>/moderation.
        java.io.File shared = betaWipe.layout().sharedRoot(true);
        java.io.File moderation = new java.io.File(shared != null ? shared : getDataFolder(), "moderation");
        raidRoster = new RaidRoster(this, moderation);
        raidBans = new RaidBans(this, moderation);
        raidRoster.start();
        raidBans.start();
        RaidMenu raidMenu = new RaidMenu(this, raidRoster, raidBans);
        PluginCommand raidCmd = getCommand("raidban");
        if (raidCmd != null) {
            raidCmd.setExecutor(raidMenu);
            raidCmd.setTabCompleter(raidMenu);
        }

        de.aetherion.core.api.BarChrome.enabled(getConfig().getBoolean("bars.pack-glyphs", true));
        // After every plugin is up: ProtocolLib may enable after Core. The damage-heart cap is always on; the diet is opt-in.
        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                de.aetherion.core.api.ParticleDiet.install(this, getConfig().getConfigurationSection("particles"),
                        getConfig().getBoolean("particles.diet", false));
            }
        });
        Bukkit.getPluginManager().registerEvents(new ShutdownCountdown(this), this);
        getLogger().info("Shared keys and hit flags ready. Game plugins keep the loop.");
        getLogger().info("ShutdownCountdown armed (10→8→6→4→2). Bypass: stop now|force.");
        getLogger().info("Exile link → " + exile.exileServer()
                + (exile.isExileServer() ? " (this backend hosts exile)" : ""));
        getLogger().info("Network here=" + here
                + " transport=" + (characterHost != null ? "core" : "plugin/none")
                + (islandsHost != null ? " (island host)" : ""));
    }

    public MainWorldLink link() {
        return link;
    }

    public TransferSnapshotStore snapshots() {
        return snapshots;
    }

    public ExileLink exile() {
        return exile;
    }

    public ExileHost exileHost() {
        return exileHost;
    }

    /** Cross-backend travel (character + route). Never null after enable. */
    public NetworkRouter router() {
        return router;
    }

    /** Core's character host on mmo-c / mmo-i, else null (Dungeons owns it on mmo-r / mmo-d). */
    public CharacterHost characterHost() {
        return characterHost;
    }

    public IslandsHost islandsHost() {
        return islandsHost;
    }

    /** The Veil Quay (ferry departure) on non-exile backends; null on mmo-c. */
    public de.aetherion.core.exile.ExileQuay exileQuay() {
        return exileQuay;
    }

    @Override
    public void onDisable() {
        if (exileQuay != null) {
            exileQuay.shutdown();
        }
        if (exileHost != null) {
            exileHost.shutdown();
        }
        if (characterHost != null) {
            // Usually already done when the first data plugin went down; idempotent per player.
            characterHost.flushAllOnDisable();
        }
        if (islandLink != null) {
            islandLink.unregister();
        }
        if (networkWipeWatch != null) {
            networkWipeWatch.stop();
        }
        if (raidRoster != null) {
            raidRoster.stop();
        }
        if (raidBans != null) {
            raidBans.stop();
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§8AetherionCore §7" + getPluginMeta().getVersion() + " §8· §7keys only, no tick.");
        return true;
    }

    /**
     * Island / dungeon void worlds (bukkit.yml / Multiverse {@code AetherionCore:void}). Available at STARTUP so
     * Multiverse can load {@code aether_islands} / {@code aether_guilds} as real void before Guilds enables.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }
}
