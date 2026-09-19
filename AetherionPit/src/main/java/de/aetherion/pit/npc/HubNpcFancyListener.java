package de.aetherion.pit.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.pit.AetherionPit;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public final class HubNpcFancyListener {

    private HubNpcFancyListener() {
    }

    public static void register(AetherionPit plugin, HubNpcService npcs) {
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.player() == null || click.name() == null) {
                        return;
                    }
                    if (HubNpcService.isHubNpc(click.name())) {
                        FancyNpcFacade.cancel(event);
                        npcs.handleClick(click.player(), click.name());
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
