package de.aetherion.stressbots;

import de.aetherion.core.api.AetherServices;
import de.aetherion.stressbots.control.RunnerControlClient;
import de.aetherion.stressbots.control.TestBotController;
import de.aetherion.stressbots.report.BotActivityTracker;
import de.aetherion.stressbots.report.BotReportBuilder;
import de.aetherion.stressbots.role.BotLocations;
import de.aetherion.stressbots.role.BotNicknames;
import de.aetherion.stressbots.role.BotRoleRegistry;
import de.aetherion.stressbots.safety.BotSafetyWatchdog;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
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
    private BukkitTask upkeepTask;

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
        upkeepTask = Bukkit.getScheduler().runTaskTimer(this, new BotUpkeep(this), 20L * 40, 20L * 90);

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
        warnLegacyDeathAnchors();
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
        if (upkeepTask != null) {
            upkeepTask.cancel();
            upkeepTask = null;
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
        warnLegacyDeathAnchors();
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

    private void warnLegacyDeathAnchors() {
        warnIfAnchor("testbots.roles.catch", 500.5, -200.5,
                "catch pad 500.5,-200.5 voids after TP — copy the new grove/return-platform anchors");
        warnIfAnchor("testbots.roles.forage", 479.5, -240.5,
                "forage pad 479.5,-240.5 is the jump-pad lip — copy grove/interior anchors");
        warnIfAnchor("testbots.roles.roam", 220.5, 160.5,
                "roam waypoint 220.5,160.5 is the husk/skeleton loop — remove borderlands from roam");
    }

    private void warnIfAnchor(String path, double x, double z, String message) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (Location anchor : BotLocations.readAnchors(section)) {
            if (Math.abs(anchor.getX() - x) < 0.6 && Math.abs(anchor.getZ() - z) < 0.6) {
                getLogger().warning("Live config still has " + message);
                return;
            }
        }
    }

    public static boolean canControl(CommandSender sender) {
        return sender != null && (sender.isOp()
                || sender.hasPermission("aetherion.stressbots.admin")
                || sender.hasPermission("aetherion.dev"));
    }
}
