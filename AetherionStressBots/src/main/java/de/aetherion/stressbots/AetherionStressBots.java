package de.aetherion.stressbots;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionStressBots extends JavaPlugin {

    private BotProvisioner provisioner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        provisioner = new BotProvisioner(this);
        Bukkit.getPluginManager().registerEvents(new BotListener(this), this);

        PluginCommand command = getCommand("stressbots");
        if (command != null) {
            StressBotsCommand executor = new StressBotsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Stress bot assist enabled (combat="
                + getConfig().getString("prefixes.combat", "StressC")
                + ", mining="
                + getConfig().getString("prefixes.mining", "StressM")
                + ")");
    }

    public BotProvisioner getProvisioner() {
        return provisioner;
    }

    public void reloadAssist() {
        reloadConfig();
        provisioner = new BotProvisioner(this);
    }
}
