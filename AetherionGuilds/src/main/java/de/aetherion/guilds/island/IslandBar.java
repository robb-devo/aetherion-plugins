package de.aetherion.guilds.island;

import de.aetherion.core.api.QuestBars;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The top of the screen belongs to the island while you're on one: quest and hint bars step aside (a Core
 * {@link QuestBars#suppressAll} lease, released the moment you leave), and a calm status bar shows instead:
 * <ul>
 *   <li>on your isle during Hollis' tour: the current step, progress = steps done;</li>
 *   <li>afterwards: tier, quarries, running belts, progress = how full your storage is;</li>
 *   <li>on a guild harbour: the running guild project, else the same production line;</li>
 *   <li>visiting: whose isle this is.</li>
 * </ul>
 * One update a second, only when the text changed. No sounds, no flashing.
 */
public final class IslandBar implements Listener {

    private static final String LEASE = "island";

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private GuildProjectService projects;
    private IslandGuide guide;
    private FactoryGoals goals;
    private long ticks;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();

    public IslandBar(JavaPlugin plugin, HostService hosts, StructureService structures, LogisticsService logistics) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public void attach(GuildProjectService projects, IslandGuide guide) {
        this.projects = projects;
        this.guide = guide;
    }

    public void attachGoals(FactoryGoals goals) {
        this.goals = goals;
    }

    private boolean statusBar() {
        return plugin.getConfig().getBoolean("island-bar.enabled", true);
    }

    /** Every second. */
    public void tick() {
        ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hosts.isIslandWorld(player.getWorld())) {
                leave(player);
                continue;
            }
            QuestBars.suppressAll(player, LEASE);
            if (!statusBar()) {
                hide(player);
                continue;
            }
            IslandHost host = hosts.at(player.getLocation());
            if (host == null || !hosts.exists(host)
                    || Math.abs(player.getLocation().getBlockX() - hosts.originX(host)) > 160) {
                hide(player);
                continue;
            }
            show(player, host);
            if (goals != null && ticks % 4L == 0L && !host.isGuild() && hosts.isMember(player, host)
                    && (guide == null || guide.objective(host.id()) == null)) {
                goals.check(player, host);
            }
        }
    }

    private void show(Player player, IslandHost host) {
        String text;
        float progress;
        BossBar.Color color;
        boolean member = hosts.isMember(player, host);
        String objective = !host.isGuild() && member && guide != null ? guide.objective(host.id()) : null;
        String[] warn;
        if (objective != null) {
            int done = guide.progress(host.id());
            text = "§e✦ " + guide.name() + " §8› §f" + objective + " §8(" + (done + 1) + "/3)";
            progress = Math.max(0.05f, done / 3f);
            color = BossBar.Color.YELLOW;
        } else if (!member) {
            text = "§7Visiting §f" + hosts.title(host) + " §8· §7Tier §f" + hosts.tier(host);
            progress = 1f;
            color = BossBar.Color.WHITE;
        } else if ((warn = warningOrGoal(host)) != null) {
            text = warn[0];
            progress = Float.parseFloat(warn[1]);
            color = warn[2].equals("red") ? BossBar.Color.RED : BossBar.Color.BLUE;
        } else {
            String project = host.isGuild() ? projectLine(host) : null;
            long used = 0L;
            long cap = 0L;
            int huts = 0;
            for (PlacedStructure structure : structures.of(host)) {
                if (structure.type().role() == StructureType.Role.SINK) {
                    used += PlacedStructure.total(structure.store());
                    cap += structure.capacity();
                    huts++;
                }
            }
            int running = 0;
            for (LogisticsService.Route route : logistics.routes(host)) {
                if (route.to() != null) {
                    running++;
                }
            }
            StringBuilder line = new StringBuilder(host.isGuild() ? "§6⚑ " : "§6✦ ").append("§f")
                    .append(hosts.title(host)).append(" §8· §7Tier §f").append(hosts.tier(host))
                    .append(" §8· §7Quarries §f").append(hosts.minions(host).size())
                    .append(" §8· §7Belts ").append(running > 0 ? "§a" + running + " running" : "§8none yet");
            if (project != null) {
                line.append(" §8· ").append(project);
            } else if (huts > 0) {
                line.append(" §8· §7Storage §f").append(cap <= 0 ? 0 : Math.min(100, used * 100 / cap)).append('%');
            }
            text = line.toString();
            progress = cap <= 0 ? 1f : Math.max(0f, Math.min(1f, (float) used / cap));
            color = project != null ? BossBar.Color.PURPLE : host.isGuild() ? BossBar.Color.BLUE : BossBar.Color.GREEN;
        }
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(legacy(text), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            shown.put(player.getUniqueId(), text);
            player.showBossBar(bar);
            return;
        }
        if (!text.equals(shown.get(player.getUniqueId()))) {
            shown.put(player.getUniqueId(), text);
            bar.name(legacy(text));
        }
        if (Math.abs(bar.progress() - progress) > 0.005f) {
            bar.progress(progress);
        }
        if (bar.color() != color) {
            bar.color(color);
        }
    }

    /**
     * After the tour the bar points at what matters: a problem on the line ("⚠ Storage Hut: Full"), else the
     * next factory goal. Both present: they take turns every few seconds. Null when neither applies.
     */
    private String[] warningOrGoal(IslandHost host) {
        String problem = logistics.worstProblem(host);
        FactoryGoals.Goal next = goals == null || host.isGuild() ? null : goals.next(host);
        boolean showProblem = problem != null && (next == null || (ticks / 5L) % 2L == 0L);
        if (showProblem) {
            return new String[]{"§c⚠ §f" + problem + " §8· §7/island → Production", "1", "red"};
        }
        if (next != null) {
            int done = goals.doneCount(host);
            int total = goals.all().size();
            return new String[]{"§b➤ Next: §f" + next.title() + " §8(" + (done + 1) + "/" + total + ")",
                    String.valueOf(Math.max(0.03f, done / (float) total)), "blue"};
        }
        return null;
    }

    private String projectLine(IslandHost host) {
        Guild guild = hosts.guild(host);
        GuildProjectService.Active active = projects == null || guild == null ? null : projects.active(guild);
        if (active == null || active.nextStage() == null) {
            return null;
        }
        return "§d" + active.type().display() + " §7stage §f" + (active.stage() + 1) + "§7/§f"
                + active.type().stages().size();
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        shown.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void leave(Player player) {
        hide(player);
        if (QuestBars.hintsHidden(player.getUniqueId())) {
            QuestBars.release(player, LEASE);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!hosts.isIslandWorld(player.getWorld())) {
            leave(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hide(player);
        QuestBars.release(player.getUniqueId(), LEASE);
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
        shown.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            QuestBars.release(player, LEASE);
        }
    }
}
