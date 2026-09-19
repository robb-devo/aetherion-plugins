package de.aetherion.foraging.npc;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public final class IsleGuideFancyListener {

    private IsleGuideFancyListener() {
    }

    public static void register(AetherionForaging plugin, IsleGuideNpc guide) {
        try {
            Class<? extends Event> eventClass = Class
                    .forName("de.oliver.fancynpcs.api.events.NpcInteractEvent")
                    .asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    Method getPlayer = event.getClass().getMethod("getPlayer");
                    Method getNpc = event.getClass().getMethod("getNpc");
                    Player player = (Player) getPlayer.invoke(event);
                    Object npc = getNpc.invoke(event);
                    if (player == null || npc == null) {
                        return;
                    }
                    Object data = npc.getClass().getMethod("getData").invoke(npc);
                    String name = String.valueOf(data.getClass().getMethod("getName").invoke(data));
                    if (IsleGuideNpc.isGuideFancy(name)) {
                        try {
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        } catch (ReflectiveOperationException ignored) {
                        }
                        guide.handleFancyClick(player, name);
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Isle guide click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    new Listener() {
                    },
                    EventPriority.NORMAL,
                    executor,
                    plugin,
                    false
            );
            plugin.getLogger().info("Isle guide FancyNPC interact hooked.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs interact event missing — isle guide clicks may be limited.");
        }
    }
}
