package de.aetherion.stressbots.control;

import de.aetherion.core.api.TestBotReport;
import de.aetherion.core.api.TestBotView;
import de.aetherion.core.api.TestBotsAccess;
import de.aetherion.stressbots.AetherionStressBots;
import de.aetherion.stressbots.report.BotReportBuilder;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Start/stop + caps + enabled gate. Spawning is done by the Mineflayer runner.
 */
public final class TestBotController implements TestBotsAccess {

    private final AetherionStressBots plugin;
    private final RunnerControlClient runner;
    private final BotReportBuilder reports;
    private final Map<BotRole, Integer> desired = new ConcurrentHashMap<>();

    public TestBotController(AetherionStressBots plugin, RunnerControlClient runner, BotReportBuilder reports) {
        this.plugin = plugin;
        this.runner = runner;
        this.reports = reports;
        for (BotRoleHandler handler : plugin.getRegistry().startable()) {
            desired.put(handler.role(), 0);
        }
    }

    @Override
    public boolean enabled() {
        return plugin.getConfig().getBoolean("testbots.enabled", false);
    }

    @Override
    public List<String> startableRoles() {
        List<String> ids = new ArrayList<>();
        for (BotRoleHandler handler : plugin.getRegistry().startable()) {
            ids.add(handler.role().id());
        }
        return ids;
    }

    @Override
    public List<String> wave1Roles() {
        return idsOf(plugin.getRegistry().wave1());
    }

    @Override
    public List<String> wave2Roles() {
        return idsOf(plugin.getRegistry().wave2());
    }

    private static List<String> idsOf(List<BotRoleHandler> handlers) {
        List<String> ids = new ArrayList<>();
        for (BotRoleHandler handler : handlers) {
            ids.add(handler.role().id());
        }
        return ids;
    }

    @Override
    public String start(String roleId, int count) {
        if (!enabled()) {
            return "§cTestbots disabled. Set §ftestbots.enabled: true §cin AetherionStressBots/config.yml.";
        }
        BotRole role = requireStartable(roleId);
        if (role == null) {
            return "§cUnknown role. Wave 1: mine, forage, catch, roam. Wave 2: combat, fish, farm, trade, quest, pad.";
        }
        BotRoleHandler handler = plugin.getRegistry().handler(role);
        int clamped = clampCount(role, count <= 0 ? Math.max(1, desired.getOrDefault(role, 1)) : count);
        if (clamped <= 0) {
            return "§cRole cap is 0.";
        }
        int others = onlineExcept(role);
        int max = maxTotal();
        if (others + clamped > max) {
            clamped = Math.max(0, max - others);
            if (clamped <= 0) {
                return "§cMax bots reached (" + max + "). Stop another role first.";
            }
        }
        desired.put(role, clamped);
        log().info("Testbots start " + role.id() + " count=" + clamped);
        RunnerControlClient.Response response = runner.setDesired(role.id(), clamped);
        if (response == null) {
            return "§cRunner not reachable at §f" + runner.baseUrl()
                    + "§c. Start: §fnpm start -- --listen §7in AetherionStressBots/runner";
        }
        if (!response.ok()) {
            return "§cRunner refused: §7" + response.message();
        }
        return "§aStarting §f" + clamped + " §a" + role.id() + " §7bots (" + handler.prefix() + "01..). "
                + "§8" + response.message();
    }

    @Override
    public String stop(String roleId) {
        BotRole role = BotRole.fromId(roleId);
        if (role == null) {
            return "§cUnknown role.";
        }
        desired.put(role, 0);
        log().info("Testbots stop " + role.id());
        RunnerControlClient.Response response = runner.stop(role.id());
        Bukkit.getScheduler().runTask(plugin, () -> kickRole(role));
        if (response == null) {
            return "§eStopped locally (kicked online " + role.id()
                    + " bots). Runner was unreachable — they may reconnect if the process is still up.";
        }
        return "§aStopped §f" + role.id() + "§a. §8" + response.message();
    }

    @Override
    public String stopAll() {
        for (BotRoleHandler handler : plugin.getRegistry().all()) {
            desired.put(handler.role(), 0);
        }
        log().info("Testbots stop-all");
        RunnerControlClient.Response response = runner.stopAll();
        Bukkit.getScheduler().runTask(plugin, () -> kickAllBots());
        if (response == null) {
            return "§eStop-all queued kick next tick. Runner unreachable — restart/stop the node process to prevent reconnect.";
        }
        return "§aStop-all sent. §8" + response.message();
    }

    @Override
    public int adjustDesired(String roleId, int delta) {
        BotRole role = requireStartable(roleId);
        if (role == null) {
            return 0;
        }
        int next = clampCount(role, desired.getOrDefault(role, 0) + delta);
        desired.put(role, next);
        return next;
    }

    @Override
    public int desired(String roleId) {
        BotRole role = BotRole.fromId(roleId);
        return desired(role);
    }

    public int desired(BotRole role) {
        if (role == null) {
            return 0;
        }
        return desired.getOrDefault(role, 0);
    }

    @Override
    public TestBotReport report() {
        return reports.build();
    }

    @Override
    public String reportText() {
        return reports.format(reports.build());
    }

    @Override
    public TestBotView bot(String name) {
        return reports.bot(name);
    }

    @Override
    public java.util.List<String> reportLore() {
        return reports.loreLines();
    }

    public boolean runnerReachable() {
        return runner.reachable();
    }

    public String runnerDetail() {
        return runner.detail();
    }

    public int maxTotal() {
        int configured = plugin.getConfig().getInt("testbots.max-total", 40);
        int server = Bukkit.getMaxPlayers();
        return Math.max(1, Math.min(configured, server));
    }

    public int clampCount(BotRole role, int requested) {
        BotRoleHandler handler = plugin.getRegistry().handler(role);
        int cap = handler == null ? 8 : handler.cap();
        int maxStart = plugin.getConfig().getInt("testbots.max-per-start", 40);
        int hard = Math.min(cap, Math.min(maxStart, maxTotal()));
        return Math.max(0, Math.min(requested, hard));
    }

    private BotRole requireStartable(String roleId) {
        BotRole role = BotRole.fromId(roleId);
        if (role == null || !role.startable()) {
            return null;
        }
        return role;
    }

    private int onlineExcept(BotRole keep) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler != null && handler.role() != keep) {
                count++;
            }
        }
        return count;
    }

    private void kickRole(BotRole role) {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler != null && handler.role() == role) {
                player.kick(net.kyori.adventure.text.Component.text("testbot stop " + role.id()));
            }
        }
    }

    private int kickAllBots() {
        int kicked = 0;
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (plugin.getRegistry().byPlayer(player) != null) {
                player.kick(net.kyori.adventure.text.Component.text("testbot stop-all"));
                kicked++;
            }
        }
        return kicked;
    }

    private Logger log() {
        return plugin.getLogger();
    }
}
