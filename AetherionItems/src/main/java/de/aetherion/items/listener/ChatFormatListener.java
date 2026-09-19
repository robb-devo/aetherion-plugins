package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatFormatListener implements Listener {

    private final AetherionItems plugin;

    public ChatFormatListener(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String prefix = plugin.ranks() == null ? "§f" : plugin.ranks().tabPrefix(player);
        event.setFormat(escape(prefix) + "%s§7: §f%s");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("%", "%%");
    }
}
