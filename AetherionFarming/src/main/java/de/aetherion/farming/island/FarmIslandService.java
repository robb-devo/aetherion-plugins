package de.aetherion.farming.island;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Legacy void farm island — DELETED. Eldervale Farm Isle lives in {@code world}.
 * Kept as a thin stub so old portal/config call sites do not NPE. Never creates worlds.
 */
public final class FarmIslandService {

    public static final String DEFAULT_WORLD = "world";
    public static final String RESOURCE_PATH = "structures/tiny-farming-island.schem";
    private static final String LEGACY_VOID = "aether_farm_island";

    private final AetherionFarming plugin;

    public FarmIslandService(AetherionFarming plugin) {
        this.plugin = plugin;
    }

    public String worldName() {
        String name = plugin.getConfig().getString("farm-island.world", DEFAULT_WORLD);
        if (LEGACY_VOID.equalsIgnoreCase(name)) {
            return DEFAULT_WORLD;
        }
        return name;
    }

    public World ensureWorld() {
        String name = worldName();
        if (LEGACY_VOID.equalsIgnoreCase(plugin.getConfig().getString("farm-island.world", ""))) {
            plugin.getLogger().warning("Refusing deleted legacy world " + LEGACY_VOID);
            return null;
        }
        return Bukkit.getWorld(name);
    }

    public boolean worldEditPresent() {
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return we != null && we.isEnabled();
    }

    public void ensureSchematicOnDisk() {
        // no-op — legacy schematic path retired
    }

    public boolean isPasted() {
        return false;
    }

    /** Always refuses — void farm island is gone. */
    public String ensureIsland(boolean forceRebuild) {
        return "§cLegacy void farm island deleted. Use Eldervale Farm Isle in §fworld§c.";
    }
}
