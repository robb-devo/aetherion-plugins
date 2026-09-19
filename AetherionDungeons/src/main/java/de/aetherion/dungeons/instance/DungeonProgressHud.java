package de.aetherion.dungeons.instance;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonProgressHud {

    private static final Map<UUID, BossBar> BARS = new ConcurrentHashMap<>();
    private static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();
    private static BukkitTask ticker;

    private DungeonProgressHud() {
    }

    public static void start(Plugin plugin, InstanceManager instances) {
        stop();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : List.copyOf(BARS.keySet())) {
                Player online = Bukkit.getPlayer(id);
                if (online == null || !online.isOnline()) {
                    BossBar bar = BARS.remove(id);
                    if (bar != null) {
                        bar.removeAll();
                        bar.setVisible(false);
                    }
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                DungeonSession session = instances.sessionOf(player);
                boolean inside = session != null && instances.isDungeonWorld(player.getWorld());
                if (!inside) {
                    if (BARS.containsKey(player.getUniqueId()) || HELD.contains(player.getUniqueId())) {
                        leaveDungeon(player);
                    }
                    continue;
                }
                HELD.add(player.getUniqueId());
                QuestHudHook.suppress(player);
                if (session.bossReleased() || EndlessEncounter.isEndless(session) || AshesEncounter.isAshes(session)) {
                    hide(player);
                } else {
                    show(player, session);
                }
            }
        }, 5L, 5L);
    }

    public static void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (UUID id : List.copyOf(BARS.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                hide(player);
            }
        }
        BARS.clear();
        for (UUID id : List.copyOf(HELD)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                QuestHudHook.unsuppress(player);
            }
        }
        HELD.clear();
    }

    public static void show(Player player, DungeonSession session) {
        if (player == null || session == null || session.layout() == null) {
            return;
        }
        if (EndlessEncounter.isEndless(session) || AshesEncounter.isAshes(session)) {
            hide(player);
            return;
        }
        int total = Math.max(1, session.layout().combatCount());
        int cleared = Math.max(0, Math.min(total, session.clearedCount()));
        BossBar bar = BARS.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = Bukkit.createBossBar("Dungeon", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
        bar.setProgress(cleared / (double) total);
        if (cleared <= 0) {
            bar.setTitle("§5Dungeon §8· §7Clear the chambers §f0§7/" + total);
        } else {
            bar.setTitle("§5Dungeon §8· §f" + cleared + "§7/" + total + " §8chambers");
        }
    }

    public static void hide(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = BARS.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    public static void leaveDungeon(Player player) {
        hide(player);
        if (player != null) {
            HELD.remove(player.getUniqueId());
        }
        QuestHudHook.unsuppress(player);
    }
}
