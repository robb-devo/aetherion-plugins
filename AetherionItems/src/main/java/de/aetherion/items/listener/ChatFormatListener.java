package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.rank.CelestialDye;
import de.aetherion.items.rank.RainbowDye;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Chat and compact list share {@code %aetherion_tab_prefix%}:
 * Monkey = {@link de.aetherion.items.rank.CelestialDye} {@code #B2FFFF},
 * Beta = {@link de.aetherion.items.rank.RainbowDye} (original rainbow).
 */
public final class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final AetherionItems plugin;

    public ChatFormatListener(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component prefix = prefixComponent(player);
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty()
                .append(prefix)
                .append(Component.text(source.getName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        // Fallback if another plugin still reads the deprecated format string.
        // Hex &#RRGGBB comes from tabPrefix: Monkey celestial / Beta RainbowDye.
        Player player = event.getPlayer();
        String prefix = plugin.ranks() == null ? "§f" : plugin.ranks().tabPrefix(player);
        // Use the plain name so displayName (already prefixed) cannot double, and so
        // LuckPerms rainbow prefixes never appear in the format string.
        event.setFormat(escape(prefix) + escape(player.getName()) + "§7: §f%s");
    }

    private Component prefixComponent(Player player) {
        if (plugin.ranks() == null) {
            return Component.empty();
        }
        String raw = plugin.ranks().tabPrefix(player);
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        return HEX.deserialize(raw.replace('§', '&'));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("%", "%%");
    }

    public static boolean usesCelestialForGroup(String group) {
        return CelestialDye.isCelestialGroup(group);
    }

    public static boolean usesRainbowForGroup(String group) {
        return RainbowDye.isRainbowGroup(group);
    }
}
