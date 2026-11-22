package de.aetherion.quests.listener;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.talk.TalkUx;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * Bridges FancyNpcs clicks into the existing quest dialog pipeline.
 * Registered only when FancyNpcs is present (reflection — no hard compile link required at runtime).
 * <p>
 * A second, earlier listener serves paged talk: a click on the NPC whose bubble is waiting for the player goes on to
 * the next line and is cancelled, so no click handler (ours, or another plugin's guest NPC) restarts the talk.
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
        registerPagedTalk(plugin, livingNpcs);
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

    /**
     * LOW priority, before every NORMAL click handler (they all skip cancelled clicks). May run off the main thread:
     * it only reads {@link TalkUx#clickTarget} (a thread-safe snapshot) and hands the advance to the main thread.
     */
    private static void registerPagedTalk(AetherionQuests plugin, LivingNpcService livingNpcs) {
        try {
            Class<? extends Event> eventClass = FancyNpcFacade.interactEventClass();
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                TalkUx talk = TalkUx.get();
                if (talk == null) {
                    return;
                }
                try {
                    FancyNpcFacade.Interact click = FancyNpcFacade.readInteract(event);
                    Player player = click.player();
                    if (player == null || click.npc() == null) {
                        return;
                    }
                    TalkUx.Target target = talk.clickTarget(player.getUniqueId());
                    if (target == null || !sameNpc(livingNpcs, click, target)) {
                        return;
                    }
                    FancyNpcFacade.cancel(event);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TalkUx now = TalkUx.get();
                        if (now != null && player.isOnline() && !now.advance(player, target.npcId()) && now.hasChips(player)) {
                            now.pulseChips(player); // the replies came up meanwhile: point at them
                        }
                    });
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // not ours to handle: the normal click path runs
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.LOW, executor, plugin, true);
        } catch (ClassNotFoundException ex) {
            // FancyNpcs missing: nothing to bridge
        }
    }

    /** The clicked NPC is the one of the paged talk: same living NPC id, else standing right where it stands. */
    private static boolean sameNpc(LivingNpcService livingNpcs, FancyNpcFacade.Interact click, TalkUx.Target target)
            throws ReflectiveOperationException {
        String questId = click.name() == null ? null : livingNpcs.questIdFromFancyName(click.name());
        if (questId != null) {
            return questId.equalsIgnoreCase(target.npcId());
        }
        Location at = FancyNpcFacade.locationOf(click.npc());
        if (at == null || at.getWorld() == null || !at.getWorld().getName().equals(target.world())) {
            return false;
        }
        double dx = at.getX() - target.x();
        double dz = at.getZ() - target.z();
        return dx * dx + dz * dz < 2.25 && Math.abs(at.getY() - target.y()) < 2.5;
    }
}
