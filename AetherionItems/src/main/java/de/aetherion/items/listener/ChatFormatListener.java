package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;

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
        // Display name is already special rank + colored level + name.
        // Prepending tabPrefix again would print the rank twice.
        if (plugin.ranks() == null) {
            event.setFormat("§f%s§7: §f%s");
            return;
        }
        event.setFormat("%s§7: §f%s");
    }
}
