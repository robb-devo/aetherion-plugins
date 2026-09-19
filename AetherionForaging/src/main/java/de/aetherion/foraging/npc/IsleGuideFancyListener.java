package de.aetherion.foraging.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public final class IsleGuideFancyListener {

    private IsleGuideFancyListener() {
    }

    public static void register(AetherionForaging plugin, IsleGuideNpc guide) {
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.player() == null || click.name() == null) {
                        return;
                    }
                    if (IsleGuideNpc.isGuideFancy(click.name())) {
                        FancyNpcFacade.cancel(event);
                        guide.handleFancyClick(click.player(), click.name());
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
