package de.aetherion.core.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The world wire: news that every backend hears, and a tally of what has happened so far. Core runs it
 * ({@link AetherServices#world()}, never null once Core is enabled); any plugin may publish or listen.
 *
 * <p>Built for many players: one line per event (a boss kill is one line however many fought), listeners decide
 * what is worth showing, and the counts are there so the hundredth kill can be told apart from the first.
 * Everything here is called on the server thread; file work happens off it.
 */
public interface WorldAccess {

    /**
     * Tell the network. Listeners on this server hear it at once, the others within about a second.
     *
     * @param actor the player it is credited to, or null
     * @param data  small text extras (may be null)
     * @return the news as sent, with its id, time and server filled in
     */
    WorldNews publish(String kind, String subject, Player actor, Map<String, String> data);

    /** Hear every piece of news, from this server and the others. One listener set per plugin. */
    void listen(Plugin owner, Consumer<WorldNews> listener);

    /** Drop every listener the plugin registered (call on disable). */
    void unlisten(Plugin owner);

    /** How often this has happened on the whole network since the count began. */
    int count(String kind, String subject);

    /** The first time it happened, or null if it never has. */
    WorldNews first(String kind, String subject);

    /** The latest news of one kind, oldest first, at most {@code max}. Reaches back about two days. */
    List<WorldNews> recent(String kind, int max);
}
