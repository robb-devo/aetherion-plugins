package de.aetherion.items.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Screen cards (the toast at the top and the big card in the middle) live in AetherionQuests, which depends on Items,
 * so Items reaches them by reflection: {@code de.aetherion.quests.api.Cards}. Every call returns false when Quests,
 * the class or cards for this player are missing; the caller then says it the old way (chat or action bar).
 */
public final class CardsBridge {

    private static volatile ClassLoader loader;
    private static volatile Class<?> cards;

    private CardsBridge() {
    }

    private static Class<?> cards() {
        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests == null || !quests.isEnabled()) {
            return null;
        }
        ClassLoader now = quests.getClass().getClassLoader();
        if (cards != null && loader == now) {
            return cards;
        }
        try {
            cards = Class.forName("de.aetherion.quests.api.Cards", true, now);
            loader = now;
            return cards;
        } catch (Throwable ignored) {
            cards = null;
            loader = null;
            return null;
        }
    }

    private static boolean call(String name, Class<?>[] types, Object... args) {
        Class<?> type = cards();
        if (type == null) {
            return false;
        }
        try {
            Method method = type.getMethod(name, types);
            Object result = method.invoke(null, args);
            return result instanceof Boolean ok && ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Cards are on for this player (Quests loaded and hud-cards enabled). */
    public static boolean enabled(Player player) {
        return player != null && call("enabled", new Class<?>[]{Player.class}, player);
    }

    /** Small card at the top with its own mark ({@code glyph}), a few rows and an optional muted next line. */
    public static boolean note(Player player, String glyph, String title, List<String> lines, String next) {
        if (player == null || title == null) {
            return false;
        }
        return call("note", new Class<?>[]{Player.class, String.class, String.class, List.class, String.class},
                player, glyph, title, lines, next);
    }

    /** Big card above the middle of the screen. {@code accent} is a legacy colour code ("§6"); summary = one chat line. */
    public static boolean big(Player player, String header, String title, List<String> lines, List<ItemStack> icons,
                              String next, String accent, String summary) {
        if (player == null || title == null) {
            return false;
        }
        return call("big", new Class<?>[]{Player.class, String.class, String.class, List.class, List.class,
                        String.class, String.class, String.class},
                player, header, title, lines, icons, next, accent, summary);
    }
}
