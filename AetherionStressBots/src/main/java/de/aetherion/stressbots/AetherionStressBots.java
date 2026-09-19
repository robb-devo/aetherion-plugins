package de.aetherion.stressbots;

import de.aetherion.core.api.AetherServices;
import de.aetherion.stressbots.control.RunnerControlClient;
import de.aetherion.stressbots.control.TestBotController;
import de.aetherion.stressbots.report.BotActivityTracker;
import de.aetherion.stressbots.report.BotReportBuilder;
import de.aetherion.stressbots.role.BotNicknames;
import de.aetherion.stressbots.role.BotRoleRegistry;
import de.aetherion.stressbots.safety.BotSafetyWatchdog;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AetherionStressBots extends JavaPlugin {

    private BotRoleRegistry registry;
    private BotNicknames nicknames;
    private BotProvisioner provisioner;
    private BotActivityTracker activity;
    private TestBotController controller;
    private BotSafetyWatchdog safety;
    private BukkitTask activityTask;
    private BukkitTask safetyTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        activity = new BotActivityTracker(this);
        wire();
        Bukkit.getPluginManager().registerEvents(new BotListener(this), this);
        Bukkit.getPluginManager().registerEvents(activity, this);
        safety = new BotSafetyWatchdog(this);
        Bukkit.getPluginManager().registerEvents(safety, this);
        activityTask = Bukkit.getScheduler().runTaskTimer(this, activity, 20L, 10L);
        safetyTask = Bukkit.getScheduler().runTaskTimer(this, safety, 20L, 5L);

        PluginCommand command = getCommand("stressbots");
        if (command != null) {
            StressBotsCommand executor = new StressBotsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        PluginCommand report = getCommand("botreport");
        if (report != null) {
            report.setExecutor(new BotReportCommand(this));
        }

        AetherServices.registerTestBots(controller);
        getLogger().info("Testbots ready (enabled=" + controller.enabled()
                + ", runner=" + getConfig().getString("testbots.runner.host", "127.0.0.1")
                + ":" + getConfig().getInt("testbots.runner.port", 18765)
                + ", max=" + controller.maxTotal() + ")");
    }

    @Override
    public void onDisable() {
        if (activityTask != null) {
            activityTask.cancel();
            activityTask = null;
        }
        if (safetyTask != null) {
            safetyTask.cancel();
            safetyTask = null;
        }
        if (controller != null) {
            AetherServices.clearTestBots(controller);
        }
    }

    public void reloadAssist() {
        reloadConfig();
        wire();
        AetherServices.registerTestBots(controller);
    }

    private void wire() {
        registry = new BotRoleRegistry(this);
        nicknames = new BotNicknames(this);
        provisioner = new BotProvisioner(this);
        BotReportBuilder reports = new BotReportBuilder(this);
        RunnerControlClient runner = new RunnerControlClient(this);
        controller = new TestBotController(this, runner, reports);
    }

    public BotRoleRegistry getRegistry() {
        return registry;
    }

    public BotNicknames getNicknames() {
        return nicknames;
    }

    public BotProvisioner getProvisioner() {
        return provisioner;
    }

    public BotActivityTracker getActivity() {
        return activity;
    }

    public BotSafetyWatchdog getSafety() {
        return safety;
    }

    public TestBotController getController() {
        return controller;
    }

    public static boolean canControl(CommandSender sender) {
        return sender != null && (sender.isOp()
                || sender.hasPermission("aetherion.stressbots.admin")
                || sender.hasPermission("aetherion.dev"));
    }
}
