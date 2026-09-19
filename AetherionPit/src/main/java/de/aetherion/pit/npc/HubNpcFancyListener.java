package de.aetherion.pit.npc;

import de.aetherion.pit.AetherionPit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public final class HubNpcFancyListener {

    private HubNpcFancyListener() {
    }

    public static void register(AetherionPit plugin, HubNpcService npcs) {
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
                    if (HubNpcService.isHubNpc(name)) {
                        try {
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        } catch (ReflectiveOperationException ignored) {
                        }
                        npcs.handleClick(player, name);
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Hub NPC click failed: " + ex.getMessage());
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
            plugin.getLogger().info("Hub FancyNPC interact hooked.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs interact event missing — hub NPCs won't click.");
        }
    }
}
