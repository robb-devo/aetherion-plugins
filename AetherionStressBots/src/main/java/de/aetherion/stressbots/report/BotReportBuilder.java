package de.aetherion.stressbots.report;

import de.aetherion.core.api.TestBotReport;
import de.aetherion.core.api.TestBotRoleView;
import de.aetherion.core.api.TestBotView;
import de.aetherion.stressbots.AetherionStressBots;
import de.aetherion.stressbots.control.TestBotController;
import de.aetherion.stressbots.role.BotNicknames;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the public {@link TestBotReport} snapshot from online players + tracker.
 */
public final class BotReportBuilder {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final AetherionStressBots plugin;

    public BotReportBuilder(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public TestBotReport build() {
        TestBotController controller = plugin.getController();
        List<TestBotView> bots = new ArrayList<>();
        Map<BotRole, List<TestBotView>> byRole = new EnumMap<>(BotRole.class);
        for (Player player : Bukkit.getOnlinePlayers()) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler == null) {
                continue;
            }
            TestBotView view = toView(player, handler);
            bots.add(view);
            byRole.computeIfAbsent(handler.role(), key -> new ArrayList<>()).add(view);
        }
        List<TestBotRoleView> roles = new ArrayList<>();
        for (BotRoleHandler handler : plugin.getRegistry().all()) {
            List<TestBotView> members = byRole.getOrDefault(handler.role(), List.of());
            roles.add(new TestBotRoleView(
                    handler.role().id(),
                    handler.role().name(),
                    members.size(),
                    controller.desired(handler.role()),
                    handler.cap(),
                    activityHint(members),
                    lastError(members)
            ));
        }
        double tps = 20.0;
        try {
            double[] ticks = Bukkit.getTPS();
            if (ticks != null && ticks.length > 0) {
                tps = ticks[0];
            }
        } catch (NoSuchMethodError ignored) {
            tps = -1;
        }
        boolean reachable = controller.runnerReachable();
        return new TestBotReport(
                controller.enabled(),
                reachable,
                reachable ? controller.runnerDetail() : "runner unreachable (" + controller.runnerDetail() + ")",
                tps,
                bots.size(),
                controller.maxTotal(),
                roles,
                bots,
                System.currentTimeMillis()
        );
    }

    public TestBotView bot(String name) {
        if (name == null) {
            return null;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                BotRoleHandler handler = plugin.getRegistry().byPlayer(online);
                if (handler == null) {
                    continue;
                }
                String nick = plugin.getNicknames().plain(online, handler.role());
                if (name.equalsIgnoreCase(nick) || name.equalsIgnoreCase(online.getName())) {
                    return toView(online, handler);
                }
            }
            return null;
        }
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler == null) {
            return null;
        }
        return toView(player, handler);
    }

    public String format(TestBotReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Aetherion testbots report\n");
        out.append("at ").append(ISO.format(Instant.ofEpochMilli(report.generatedAtMs()).atOffset(ZoneOffset.UTC))).append('\n');
        out.append("enabled=").append(report.enabled())
                .append(" runner=").append(report.runnerReachable() ? "up" : "down")
                .append(" ").append(report.runnerDetail()).append('\n');
        out.append("tps=");
        if (report.tps() < 0) {
            out.append("n/a");
        } else {
            out.append(String.format(Locale.ROOT, "%.2f", report.tps()));
        }
        out.append(" online=").append(report.onlineTotal())
                .append("/").append(report.maxTotal()).append('\n');
        out.append('\n');
        out.append("Roles:\n");
        for (TestBotRoleView role : report.roles()) {
            if (role.online() == 0 && role.desired() == 0 && !isWave1(role.id())) {
                continue;
            }
            out.append("  - ").append(role.id())
                    .append(" online=").append(role.online())
                    .append(" desired=").append(role.desired())
                    .append(" cap=").append(role.cap())
                    .append(" activity=").append(role.activityHint());
            if (!role.lastError().isBlank()) {
                out.append(" error=").append(role.lastError());
            }
            out.append('\n');
        }
        out.append('\n');
        out.append("Bots:\n");
        if (report.bots().isEmpty()) {
            out.append("  (none online)\n");
        }
        for (TestBotView bot : report.bots()) {
            out.append("  - ").append(bot.label())
                    .append(" role=").append(bot.role())
                    .append(" @ ").append(bot.world())
                    .append(String.format(Locale.ROOT, " %.1f %.1f %.1f", bot.x(), bot.y(), bot.z()))
                    .append(" activity=").append(bot.activity())
                    .append(" held=").append(bot.heldItem())
                    .append(" deaths=").append(bot.deaths());
            if (!bot.lastAction().isBlank()) {
                out.append(" last=").append(bot.lastAction());
            }
            if (!bot.lastError().isBlank()) {
                out.append(" err=").append(bot.lastError());
            }
            out.append('\n');
            if (!bot.recentActions().isEmpty()) {
                out.append("      recent: ").append(String.join(" | ", bot.recentActions())).append('\n');
            }
        }
        out.append('\n');
        out.append("Limits: skills level only while equipped (provisioner equips 3); boosters applied on kit not via GUI; pets vary (follow / collection / wild spawn / none); AH listings still not automated; quests right-click only; fishing skips strike minigame; pad hops use plugin TP for far islands.\n");
        return out.toString();
    }

    private TestBotView toView(Player player, BotRoleHandler handler) {
        Location loc = player.getLocation();
        ItemStack held = player.getInventory().getItemInMainHand();
        BotActivityTracker.Runtime runtime = plugin.getActivity().snapshot(player);
        BotNicknames nicks = plugin.getNicknames();
        String display = nicks == null ? player.getName() : nicks.plain(player, handler.role());
        return new TestBotView(
                player.getName(),
                display,
                handler.role().id(),
                loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                runtime.activity(),
                BotActivityTracker.pretty(held),
                runtime.deaths(),
                runtime.lastAction(),
                runtime.lastError(),
                runtime.recentActions()
        );
    }

    private static String activityHint(List<TestBotView> members) {
        if (members.isEmpty()) {
            return "offline";
        }
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (TestBotView bot : members) {
            counts.merge(bot.activity(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((key, value) -> parts.add(value + " " + key));
        return String.join(", ", parts);
    }

    private static String lastError(List<TestBotView> members) {
        for (TestBotView bot : members) {
            if (bot.lastError() != null && !bot.lastError().isBlank()) {
                return bot.label() + ": " + bot.lastError();
            }
        }
        return "";
    }

    private static boolean isWave1(String id) {
        BotRole role = BotRole.fromId(id);
        return role != null && role.startable();
    }
}
