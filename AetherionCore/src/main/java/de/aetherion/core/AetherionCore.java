package de.aetherion.core;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.command.PlaytimeCommand;
import de.aetherion.core.command.WipeCommand;
import de.aetherion.core.playtime.PlaytimeService;
import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Shared library plugin. Loads first. Does not tick the game.
 * Also keeps wipe-safe playtime (join/quit plus a periodic flush).
 */
public final class AetherionCore extends JavaPlugin {

    private static AetherionCore instance;
    private BetaWipe betaWipe;
    private NetworkWipeWatch networkWipeWatch;
    private PlaytimeService playtime;

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
        networkWipeWatch.start();
        startPlaytime();
        getLogger().info("Shared keys and hit flags ready. Game plugins keep the loop.");
    }

    private void startPlaytime() {
        PlaytimeCommand commands;
        try {
            playtime = new PlaytimeService(this);
            playtime.start();
            AetherServices.registerPlaytime(playtime);
            commands = new PlaytimeCommand(playtime);
        } catch (Exception exception) {
            playtime = null;
            getLogger().log(Level.SEVERE, "Playtime tracking did not start", exception);
            commands = new PlaytimeCommand(null);
        }
        bind(commands, "playtime");
        bind(commands, "fullplaytimereset");
    }

    private void bind(PlaytimeCommand executor, String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Missing command in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        if (playtime != null) {
            playtime.shutdown();
            AetherServices.clearPlaytime(playtime);
            playtime = null;
        }
        if (networkWipeWatch != null) {
            networkWipeWatch.stop();
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§8AetherionCore §7" + getPluginMeta().getVersion() + " §8· §7keys, wipe, playtime.");
        return true;
    }
}
