package de.aetherion.pit.placeholder;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** %aetherionpit_level% · %aetherionpit_gold% · %aetherionpit_xp% · %aetherionpit_area% · %aetherionpit_server% */
public final class PitPlaceholders extends PlaceholderExpansion {

    private final AetherionPit plugin;

    public PitPlaceholders(AetherionPit plugin) {
        this.plugin = plugin;
    }

    public static void tryRegister(AetherionPit plugin) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            plugin.getLogger().info("PlaceholderAPI missing — TAB gold uses fallback.");
            return;
        }
        try {
            new PitPlaceholders(plugin).register();
            plugin.getLogger().info("PlaceholderAPI: %aetherionpit_level% / %aetherionpit_gold% / %aetherionpit_area%");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Pit placeholders failed: " + ex.getMessage());
        }
    }

    @Override
    public String getIdentifier() {
        return "aetherionpit";
    }

    @Override
    public String getAuthor() {
        return "Aetherion";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return "";
        }
        PitDataStore.Stats stats = plugin.data().of(player);
        return switch (params.toLowerCase()) {
            case "level", "lvl" -> String.valueOf(stats.level());
            case "xp" -> String.valueOf(stats.xp());
            case "gold", "g" -> String.valueOf(stats.gold());
            case "xp_needed", "needed" -> String.valueOf(plugin.levels().xpForNext(stats.level()));
            case "server" -> "Hub";
            case "area", "zone" -> plugin.safeZone().isSafe(player.getLocation())
                    ? "Spawn / Safe Zone"
                    : "Combat Zone";
            default -> null;
        };
    }
}
