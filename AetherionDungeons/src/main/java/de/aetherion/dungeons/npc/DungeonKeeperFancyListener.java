package de.aetherion.dungeons.npc;

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
                    Object name = data == null ? null : data.getClass().getMethod("getName").invoke(data);
                    String fancyName = String.valueOf(name);
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (player == null) {
                        return;
                    }
                    if (DungeonKeeperService.isFancyKeeperName(fancyName)) {
                        event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                DungeonMenu.open(player);
                            }
                        });
                        return;
                    }
                    if (DungeonGuideService.isFancyGuideName(fancyName)) {
                        event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
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
