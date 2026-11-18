package de.aetherion.stressbots;

import de.aetherion.stressbots.role.BotRole;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is a test bot, and what it is here to do.
 *
 * <p>Bots log in offline: through the proxy's test-bot door, or straight to a backend with the Velocity secret.
 * Either way their UUID is the offline one (version 3); real accounts on this online-mode network are version 4,
 * so the UUID alone tells a bot from a person whatever its name is. The role arrives with {@code /testbot hello}
 * on every backend the bot reaches. Old {@code QaMine01}-style names still map to their role by prefix.
 */
public final class BotIdentity {

    /** What the runner told this backend about one bot. {@code index} = slot number within its role, from 1. */
    public record Session(BotRole role, int index, String runnerId, long helloAt) {
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public static boolean offlineUuid(UUID id) {
        return id != null && id.version() == 3;
    }

    public void hello(Player player, BotRole role, int index, String runnerId) {
        if (player != null && role != null) {
            sessions.put(player.getUniqueId(), new Session(role, Math.max(1, index), runnerId == null ? "" : runnerId,
                    System.currentTimeMillis()));
        }
    }

    public Session session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public boolean saidHello(Player player) {
        return session(player) != null;
    }

    public BotRole role(Player player) {
        Session session = session(player);
        return session == null ? null : session.role();
    }

    /** Slot within the role (gear tier, pet, booster pick), or -1 when the runner never said. */
    public int index(Player player) {
        Session session = session(player);
        return session == null ? -1 : session.index();
    }

    public void forget(UUID id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    public int size() {
        return sessions.size();
    }
}
