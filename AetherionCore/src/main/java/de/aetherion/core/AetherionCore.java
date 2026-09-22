package de.aetherion.core;

import de.aetherion.core.command.NetworkRestartCommand;
import de.aetherion.core.command.WipeCommand;
import de.aetherion.core.network.MainWorldLink;
import de.aetherion.core.network.TransferSnapshotStore;
import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared library plugin. Loads first. Does not tick the game.
 */
public final class AetherionCore extends JavaPlugin {

    private static AetherionCore instance;
    private BetaWipe betaWipe;
    private NetworkWipeWatch networkWipeWatch;
    private MainWorldLink link;
    private TransferSnapshotStore snapshots;

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
        NetworkRestartCommand restartCommand = new NetworkRestartCommand(this);
        PluginCommand aenet = getCommand("aenet");
        if (aenet != null) {
            aenet.setExecutor(restartCommand);
            aenet.setTabCompleter(restartCommand);
        }
        networkWipeWatch.start();
        snapshots = new TransferSnapshotStore(this);
        link = new MainWorldLink(this, snapshots);
        getLogger().info("Shared keys and hit flags ready. Game plugins keep the loop.");
    }

    public MainWorldLink link() {
        return link;
    }

    public TransferSnapshotStore snapshots() {
        return snapshots;
    }

    @Override
    public void onDisable() {
        if (networkWipeWatch != null) {
            networkWipeWatch.stop();
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
}
