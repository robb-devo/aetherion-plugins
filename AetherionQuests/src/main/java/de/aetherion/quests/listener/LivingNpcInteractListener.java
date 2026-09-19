package de.aetherion.quests.listener;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * Bridges FancyNpcs clicks into the existing quest dialog pipeline.
 * Registered only when FancyNpcs is present (reflection — no hard compile link required at runtime).
 */
public final class LivingNpcInteractListener {

    private LivingNpcInteractListener() {
    }

    public static void register(
            AetherionQuests plugin,
            NpcListener npcListener,
            LivingNpcService livingNpcs
    ) {
        if (plugin == null || npcListener == null || livingNpcs == null || !livingNpcs.available()) {
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
                    String questId = livingNpcs.questIdFromFancyName(click.name());
                    if (questId == null) {
                        return;
                    }
                    QuestNPC npc = QuestNPCRegistry.getNPC(questId);
                    Player player = click.player();
                    if (npc == null || player == null) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            npcListener.handleLivingClick(player, npc);
                        }
                    });
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Living NPC interact failed: " + ex.getMessage());
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
            plugin.getLogger().info("Living NPC FancyNpcs interact bridge enabled.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs API missing — living NPC clicks disabled.");
        }
    }
}
