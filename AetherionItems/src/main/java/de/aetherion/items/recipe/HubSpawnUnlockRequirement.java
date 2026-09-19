package de.aetherion.items.recipe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
        Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (hub == null || !hub.isEnabled()) {
            return false;
        }
        try {
            Object unlocked = Class.forName("de.aetherion.hub.api.AetherionHubAPI")
                    .getMethod("isUnlocked", UUID.class, String.class)
                    .invoke(null, uuid, spawnId);
            return unlocked instanceof Boolean ok && ok;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
