package de.aetherion.core.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares the bottom ActionBar with the player HUD.
 *
 * <p>AetherionItems paints HP / defense / cooldowns into that one packet and keeps it alive. A plain
 * {@code sendActionBar} from another plugin wipes the HUD until its next refresh, and is wiped itself a
 * moment later. Post the line here instead: the painter draws it on the normal ActionBar line, above the
 * HUD, for {@code ticks}. Without a painter (AetherionItems offline) the line goes out as a plain ActionBar,
 * exactly as before.
 */
public final class ActionBarLane {

    /** Roughly a vanilla ActionBar's life (60 ticks, the last 20 fading). */
    public static final int DEFAULT_TICKS = 50;

    /** Repaints one player's ActionBar from {@link #line(Player)} plus its own HUD. */
    public interface Painter {
        void paint(Player player);
    }

    private record Line(Component text, long untilTick) {
    }

    private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
    private static volatile Painter painter;

    private ActionBarLane() {
    }

    public static void register(Painter owner) {
        painter = owner;
    }

    public static void unregister(Painter owner) {
        if (painter == owner) {
            painter = null;
            LINES.clear();
        }
    }

    public static void show(Player player, Component text) {
        show(player, text, DEFAULT_TICKS);
    }

    /** Post (or replace) this player's line for {@code ticks}. An empty component clears it. */
    public static void show(Player player, Component text, int ticks) {
        if (player == null) {
            return;
        }
        Painter owner = painter;
        if (owner == null) {
            player.sendActionBar(text == null ? Component.empty() : text);
            return;
        }
        if (blank(text)) {
            LINES.remove(player.getUniqueId());
        } else {
            LINES.put(player.getUniqueId(), new Line(text, Bukkit.getCurrentTick() + Math.max(1, ticks)));
        }
        try {
            owner.paint(player);
        } catch (RuntimeException ex) {
            // A broken HUD must not take the caller's loop down with it.
            player.sendActionBar(text == null ? Component.empty() : text);
        }
    }

    /** Drop this player's line (the HUD stays). */
    public static void clear(Player player) {
        show(player, Component.empty(), 0);
    }

    /** The live line for a painter, or {@code null} once it expired. */
    public static Component line(Player player) {
        if (player == null) {
            return null;
        }
        Line line = LINES.get(player.getUniqueId());
        if (line == null) {
            return null;
        }
        if (Bukkit.getCurrentTick() >= line.untilTick()) {
            LINES.remove(player.getUniqueId(), line);
            return null;
        }
        return line.text();
    }

    public static void forget(UUID playerId) {
        if (playerId != null) {
            LINES.remove(playerId);
        }
    }

    private static boolean blank(Component text) {
        return text == null
                || text instanceof TextComponent plain && plain.content().isEmpty() && plain.children().isEmpty();
    }
}
