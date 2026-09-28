package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides the quest boss bar while another plugin's bar is on screen.
 * No-op when AetherionQuests is offline.
 *
 * <p>Two flavours:
 * <ul>
 *   <li>{@link #suppress(Player, String)} / {@link #release(UUID, String)} — owner-keyed leases.
 *       The quest bar only comes back once <em>every</em> lease is released, so the fishing bar,
 *       the fell bar and a farm event can overlap without un-hiding each other's screen.</li>
 *   <li>{@link #suppress(Player)} / {@link #unsuppress(Player)} — legacy one-shot toggle
 *       (bosses, dungeons, pet catch). A legacy unsuppress is ignored while a lease is held.</li>
 * </ul>
 */
public final class QuestBars {

    private static final Map<UUID, Set<String>> LEASES = new ConcurrentHashMap<>();

    private QuestBars() {
    }

    public static void suppress(Player player) {
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.suppress(player);
        }
    }

    public static void unsuppress(Player player) {
        if (player == null || leased(player.getUniqueId())) {
            return;
        }
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.unsuppress(player);
        }
    }

    public static void unsuppress(UUID playerId) {
        if (playerId == null || leased(playerId)) {
            return;
        }
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.unsuppress(playerId);
        }
    }

    /**
     * Take (or refresh) a named lease on the quest bar. Idempotent per owner.
     * Owners are short stable ids, e.g. {@code "fishing"}, {@code "fell"}, {@code "bird-scare"}.
     */
    public static void suppress(Player player, String owner) {
        if (player == null || owner == null) {
            return;
        }
        LEASES.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(owner);
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.suppress(player);
        }
    }

    /**
     * Drop a named lease. The quest bar returns only when no lease is left.
     * Safe to call when the owner never suppressed (no-op).
     */
    public static void release(UUID playerId, String owner) {
        if (playerId == null || owner == null) {
            return;
        }
        Set<String> owners = LEASES.get(playerId);
        if (owners == null || !owners.remove(owner)) {
            return;
        }
        if (!owners.isEmpty()) {
            return;
        }
        LEASES.remove(playerId, owners);
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.unsuppress(playerId);
        }
    }

    public static void release(Player player, String owner) {
        if (player != null) {
            release(player.getUniqueId(), owner);
        }
    }

    /** True while any plugin holds a named lease for this player. */
    public static boolean leased(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Set<String> owners = LEASES.get(playerId);
        return owners != null && !owners.isEmpty();
    }
}
