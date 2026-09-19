package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

public final class BossJournalListener {

    private BossJournalListener() {
    }

    @SuppressWarnings("unchecked")
    public static void register(Plugin plugin, CodexService codex) {
        try {
            Class<? extends Event> eventClass = Class
                    .forName("de.aetherion.bossengine.event.BossDeathEvent")
                    .asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> handle(codex, event);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    new Listener() {
                    },
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void handle(CodexService codex, Event event) {
        try {
            Object damageMap = event.getClass().getMethod("getDamageMap").invoke(event);
            if (!(damageMap instanceof Map<?, ?> map)) {
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof UUID playerId)) {
                    continue;
                }
                if (!(entry.getValue() instanceof Number amount) || amount.doubleValue() <= 0.0) {
                    continue;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                Object instance = event.getClass().getMethod("getInstance").invoke(event);
                Object template = instance.getClass().getMethod("getTemplate").invoke(instance);
                String id = String.valueOf(template.getClass().getMethod("getId").invoke(template));
                boolean first = !codex.hasAnyBossKill(player);
                codex.addBossKill(player, id);
                if (first) {
                    de.aetherion.items.progress.UnlockToast.show(
                            player,
                            "Dungeon Journal",
                            "The bosses keep receipts"
                    );
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
