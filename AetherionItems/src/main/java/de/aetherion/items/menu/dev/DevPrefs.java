package de.aetherion.items.menu.dev;

import de.aetherion.core.AetherKeys;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player DEV favorites (★, pinned with F) and recents (⟲, last gives / teleports).
 * Stored as action strings in the player's PDC — survives restarts, no extra files,
 * and a stale action simply renders as an "unavailable" stub until unpinned.
 */
final class DevPrefs {

    static final int SLOTS = 7;

    private static final NamespacedKey FAVORITES = AetherKeys.namespaced("aetherion", "dev_favorites");
    private static final NamespacedKey RECENTS = AetherKeys.namespaced("aetherion", "dev_recents");

    enum Pin {
        PINNED,
        UNPINNED,
        FULL
    }

    private DevPrefs() {
    }

    static List<String> favorites(Player player) {
        return read(player, FAVORITES);
    }

    static List<String> recents(Player player) {
        return read(player, RECENTS);
    }

    static Pin toggleFavorite(Player player, String action) {
        List<String> favorites = favorites(player);
        if (favorites.remove(action)) {
            write(player, FAVORITES, favorites);
            return Pin.UNPINNED;
        }
        if (favorites.size() >= SLOTS) {
            return Pin.FULL;
        }
        favorites.add(action);
        write(player, FAVORITES, favorites);
        return Pin.PINNED;
    }

    static boolean isFavorite(Player player, String action) {
        return favorites(player).contains(action);
    }

    static void recordRecent(Player player, String action) {
        List<String> recents = recents(player);
        recents.remove(action);
        recents.add(0, action);
        while (recents.size() > SLOTS) {
            recents.remove(recents.size() - 1);
        }
        write(player, RECENTS, recents);
    }

    static void clearRecents(Player player) {
        write(player, RECENTS, List.of());
    }

    private static List<String> read(Player player, NamespacedKey key) {
        List<String> values = new ArrayList<>();
        if (player == null) {
            return values;
        }
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String line : raw.split("\n")) {
            if (!line.isBlank() && !values.contains(line)) {
                values.add(line);
            }
        }
        return values;
    }

    private static void write(Player player, NamespacedKey key, List<String> values) {
        if (player == null) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (values.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, String.join("\n", values));
        }
    }
}
