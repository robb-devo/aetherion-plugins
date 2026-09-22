package de.aetherion.items.api;

import de.aetherion.core.api.ProgressAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.progress.ProgressionUnlock;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Items-side persistence flush/reload + progression flags.
 * Method order matches the former NetworkPlayerDataSync reflection calls.
 */
public final class ProgressAccessImpl implements ProgressAccess {

    private final AetherionItems plugin;

    public ProgressAccessImpl(AetherionItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public void flushPlayer(Player player) {
        if (player == null || plugin == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (plugin.getMarket() != null) {
            plugin.getMarket().closeOpenGuis(player);
        }
        if (plugin.getLoadoutListener() != null) {
            plugin.getLoadoutListener().flushWornLoadout(player);
        }
        if (plugin.getStorageInventory() != null) {
            plugin.getStorageInventory().savePlayerStorage(id);
            plugin.getStorageInventory().saveAll();
        }
        if (plugin.getCoins() != null) {
            plugin.getCoins().save();
        }
        if (plugin.getSkills() != null) {
            plugin.getSkills().save();
        }
        if (plugin.progress() != null) {
            plugin.progress().save();
        }
        if (plugin.getShards() != null) {
            plugin.getShards().save();
        }
        if (plugin.xpBoost() != null) {
            plugin.xpBoost().save();
        }
        if (plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().save();
        }
        if (plugin.blueprintUnlocks() != null) {
            plugin.blueprintUnlocks().save();
        }
        if (plugin.getAreas() != null) {
            plugin.getAreas().save();
        }
        if (plugin.getCodex() != null) {
            plugin.getCodex().save();
        }
        // ranks.save() was invoked reflectively but RankBadgeService.save() is private —
        // getMethod("save") never succeeded. Do not start flushing ranks here.
        if (plugin.getMarket() != null) {
            plugin.getMarket().save();
        }
    }

    @Override
    public void applyImportedCoins(UUID playerId, Map<String, Object> coinsYaml) {
        if (playerId == null || coinsYaml == null || coinsYaml.isEmpty() || plugin.getCoins() == null) {
            return;
        }
        try {
            plugin.getCoins().applyImported(
                    playerId,
                    asLong(coinsYaml.get("players." + playerId)),
                    asLong(coinsYaml.get("lifetime." + playerId))
            );
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Coin memory apply failed: " + ex.getMessage());
        }
    }

    @Override
    public void reloadAfterImport(Player player) {
        if (plugin == null) {
            return;
        }
        UUID id = player == null ? null : player.getUniqueId();
        // Overlay this player only. A full reloadFromDisk() would clobber other
        // online players' unsaved in-memory balances on the arrival JVM.
        if (id != null && plugin.getCoins() != null) {
            plugin.getCoins().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.getSkills() != null) {
            plugin.getSkills().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.progress() != null) {
            plugin.progress().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.getShards() != null) {
            plugin.getShards().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.xpBoost() != null) {
            plugin.xpBoost().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.blueprintUnlocks() != null) {
            plugin.blueprintUnlocks().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.getCodex() != null) {
            plugin.getCodex().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.ranks() != null) {
            plugin.ranks().overlayPlayerFromDisk(id);
        }
        if (id != null && plugin.getStorageInventory() != null) {
            plugin.getStorageInventory().invalidateAndReload(id);
        }
        if (id != null && plugin.getLoadoutListener() != null) {
            plugin.getLoadoutListener().invalidateCachesAfterNetworkImport(id);
        }
        if (player != null && plugin.xpBarSync() != null) {
            plugin.xpBarSync().sync(player);
        }
    }

    @Override
    public void syncAccountLevel(Player player) {
        if (player == null || plugin == null || plugin.xpBarSync() == null) {
            return;
        }
        plugin.xpBarSync().sync(player);
    }

    @Override
    public void resetLoadoutRuntime(Player player) {
        if (plugin == null || plugin.getLoadoutListener() == null) {
            return;
        }
        plugin.getLoadoutListener().resetRuntimeAfterNetworkSync(player);
    }

    @Override
    public boolean island(Player player) {
        return plugin != null && plugin.progress() != null && plugin.progress().island(player);
    }

    @Override
    public String islandHint() {
        if (plugin == null || plugin.progress() == null) {
            return "§7Reach Aetherion Level §f20 §7to claim your island.";
        }
        return plugin.progress().islandHint();
    }

    @Override
    public boolean guild(Player player) {
        return plugin != null && plugin.progress() != null && plugin.progress().guild(player);
    }

    @Override
    public String guildHint() {
        if (plugin == null || plugin.progress() == null) {
            return "§7Reach Aetherion Level §f75 §7to unlock.";
        }
        return plugin.progress().guildHint();
    }

    @Override
    public boolean hasFlag(Player player, String flagName) {
        ProgressionService.Flag flag = flag(flagName);
        return flag != null && plugin != null && plugin.progress() != null && plugin.progress().has(player, flag);
    }

    @Override
    public boolean unlock(Player player, String flagName, String title, String subtitle) {
        return ProgressionUnlock.unlock(player, flagName, title, subtitle);
    }

    @Override
    public boolean unlockSilent(Player player, String flagName) {
        ProgressionService.Flag flag = flag(flagName);
        if (flag == null || plugin == null || plugin.progress() == null) {
            return false;
        }
        return plugin.progress().unlock(player, flag);
    }

    @Override
    public void refreshManager(Player player) {
        de.aetherion.items.menu.AetherionManagerListener.refreshManagerItem(player);
    }

    @Override
    public void openManager(Player player) {
        if (plugin == null || plugin.getManager() == null || player == null) {
            return;
        }
        plugin.getManager().open(player);
    }

    private static ProgressionService.Flag flag(String flagName) {
        if (flagName == null || flagName.isBlank()) {
            return null;
        }
        try {
            return ProgressionService.Flag.valueOf(flagName.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
