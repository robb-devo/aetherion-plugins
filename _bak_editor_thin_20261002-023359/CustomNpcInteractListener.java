package de.aetherion.quests.editor;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * Bridges FancyNpcs clicks into the editor NPC dialogue / wand.
 */
public final class CustomNpcInteractListener {

    private CustomNpcInteractListener() {
    }

    public static void register(NpcEditor editor) {
        AetherionQuests plugin = editor.plugin();
        if (!editor.service().available()) {
            plugin.getLogger().warning("FancyNpcs missing — editor NPC clicks disabled.");
            return;
        }
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            Listener marker = new Listener() {
            };
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    if (click.npc() == null || click.name() == null) {
                        return;
                    }
                    if (CustomNpcService.idFromFancyName(click.name()) == null) {
                        return;
                    }
                    Player player = click.player();
                    if (player == null) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            editor.clickEditorNpc(player, click.name());
                        }
                    });
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Editor NPC interact failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    marker,
                    EventPriority.NORMAL,
                    executor,
                    plugin,
                    true
            );
            plugin.getLogger().info("Editor NPC FancyNpcs interact bridge enabled.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs API missing — editor NPC clicks disabled.");
        }
    }
}
