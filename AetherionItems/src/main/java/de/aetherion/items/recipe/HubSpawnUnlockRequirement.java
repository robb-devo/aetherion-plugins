package de.aetherion.items.recipe;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Unlocks when the player has discovered a Hub spawn (e.g. Eldervale walk-in).
 */
public final class HubSpawnUnlockRequirement implements UnlockRequirement {

    private final String spawnId;
    private final String display;

    public HubSpawnUnlockRequirement(String spawnId) {
        this(spawnId, "Discover " + (spawnId == null ? "area" : spawnId));
    }

    public HubSpawnUnlockRequirement(String spawnId, String display) {
        this.spawnId = spawnId == null ? "" : spawnId.toLowerCase(Locale.ROOT).trim();
        this.display = display == null ? "" : display;
    }

    @Override
    public boolean isUnlocked(Player player) {
        if (player == null || spawnId.isBlank()) {
            return false;
        }
        return isHubSpawnUnlocked(player.getUniqueId(), spawnId);
    }

    @Override
    public String getDisplayText() {
        return display;
    }

    public static boolean isHubSpawnUnlocked(UUID uuid, String spawnId) {
        if (uuid == null || spawnId == null || spawnId.isBlank()) {
            return false;
        }
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        return hub != null && hub.isUnlocked(uuid, spawnId);
    }
}
