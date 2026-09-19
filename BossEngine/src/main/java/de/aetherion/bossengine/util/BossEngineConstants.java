package de.aetherion.bossengine.util;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class BossEngineConstants {

    public static final String NAMESPACE = "bossengine";
    public static final String PLUGIN_NAME = "BossEngine";

    private BossEngineConstants() {
    }

    public static String prefix(JavaPlugin plugin) {
        String raw = plugin.getConfig().getString("prefix", "&8[&5BossEngine&8]&r ");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
