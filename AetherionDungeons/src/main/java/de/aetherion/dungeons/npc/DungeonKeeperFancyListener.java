package de.aetherion.dungeons.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.dungeons.AetherionDungeons;
import de.aetherion.dungeons.menu.DungeonMenu;
import de.aetherion.dungeons.npc.DungeonGuideService;
import de.aetherion.dungeons.npc.DungeonKeeperService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * FancyNpcs click → dungeon menu (reflection, no compile dep).
 */
public final class DungeonKeeperFancyListener {

    private DungeonKeeperFancyListener() {
    }

    public static void register(AetherionDungeons plugin) {
        if (plugin == null || Bukkit.getPluginManager().getPlugin("FancyNpcs") == null) {
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
                    if (click.player() == null || click.name() == null) {
                        return;
                    }
                    Player player = click.player();
                    String fancyName = click.name();
                    if (DungeonKeeperService.isFancyKeeperName(fancyName)) {
                        FancyNpcFacade.cancel(event);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                DungeonMenu.open(player);
                            }
                        });
                        return;
                    }
                    if (DungeonGuideService.isFancyGuideName(fancyName)) {
                        FancyNpcFacade.cancel(event);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && plugin.getGuide() != null) {
                                plugin.getGuide().openFor(player);
                            }
                        });
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Dungeon Keeper Fancy click failed: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(
                    eventClass, marker, EventPriority.NORMAL, executor, plugin, true);
            plugin.getLogger().info("Dungeon Keeper FancyNPC interact hooked.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs interact event missing — keeper falls back to villager clicks.");
        }
    }
}
