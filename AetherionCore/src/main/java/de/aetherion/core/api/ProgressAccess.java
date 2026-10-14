package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Items progression + network flush/reload used by Dungeons transfer.
 * Unlock/flag methods match {@code ProgressionUnlock} / {@code ProgressionService}.
 */
public interface ProgressAccess {

    void flushPlayer(Player player);

    void applyImportedCoins(UUID playerId, Map<String, Object> coinsYaml);

    void reloadAfterImport(Player player);

    void resetLoadoutRuntime(Player player);

    boolean island(Player player);

    String islandHint();

    boolean guild(Player player);

    String guildHint();

    boolean hasFlag(Player player, String flagName);

    boolean unlock(Player player, String flagName, String title, String subtitle);

    boolean unlockSilent(Player player, String flagName);

    void refreshManager(Player player);

    void openManager(Player player);

    /**
     * After a snapshot import that included skills YAML, re-apply the Aetherion
     * account level bar from Skills. Default no-op if Skills is offline.
     */
    default void syncAccountLevel(Player player) {
    }
}
