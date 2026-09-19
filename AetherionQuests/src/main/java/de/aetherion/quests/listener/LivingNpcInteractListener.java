package de.aetherion.quests.listener;

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
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass =
                    (Class<? extends Event>) Class.forName("de.oliver.fancynpcs.api.events.NpcInteractEvent");

            Listener marker = new Listener() {
            };
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Object fancy = event.getClass().getMethod("getNpc").invoke(event);
                    if (fancy == null) {
                        return;
                    }
                    Object data = fancy.getClass().getMethod("getData").invoke(fancy);
                    if (data == null) {
                        return;
                    }
                    Object name = data.getClass().getMethod("getName").invoke(data);
                    String questId = livingNpcs.questIdFromFancyName(String.valueOf(name));
                    if (questId == null) {
                        return;
                    }
                    QuestNPC npc = QuestNPCRegistry.getNPC(questId);
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (npc == null || player == null) {
                        return;
                    }
                    event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
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
