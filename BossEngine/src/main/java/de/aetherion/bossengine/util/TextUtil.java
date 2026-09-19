package de.aetherion.bossengine.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.ChatColor;

public final class TextUtil {

    private static final LegacyComponentSerializer SECTION =
            LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    public static String color(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static Component component(String input) {
        return SECTION.deserialize(color(input));
    }

    public static String plain(String input) {
        return ChatColor.stripColor(color(input));
    }
}
