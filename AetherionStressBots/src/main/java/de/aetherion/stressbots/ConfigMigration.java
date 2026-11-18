package de.aetherion.stressbots;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Patches an existing live config.yml in place (it is never replaced). Role anchors that still equal what an
 * older jar shipped move to the current defaults; anchors somebody tuned stay. Keys that are new in this
 * version are added. Runs once per {@code testbots.config-version}.
 *
 * <p>Version 3 (whole-network bots): every {@code testbots} key the jar ships that the live file lacks is added
 * (caps and spots of the new roles, {@code unlocks}); values already in the file are never touched.
 */
final class ConfigMigration {

    static final int VERSION = 3;

    /** Anchors shipped before version 2, per role (x, y, z). */
    private static final Map<String, double[][]> SHIPPED_V1 = Map.of(
            "mine", new double[][] {{22.5, 90, 580.5}, {80.5, 92, 530.5}, {53.5, 91, 482.5}},
            "forage", new double[][] {{560.5, 91, -200.5}, {559.5, 90, -208.5}, {677.5, 91, -116.5}, {560.5, 90, -160.5}},
            "catch", new double[][] {{560.5, 91, -200.5}, {559.5, 90, -208.5}, {560.5, 90, -160.5}, {22.5, 90, 580.5}},
            "fish", new double[][] {{560.5, 91, -200.5}, {559.5, 90, -208.5}, {560.5, 90, -160.5}, {22.5, 56, 305.5}}
    );

    private ConfigMigration() {
    }

    static void run(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("testbots.config-version", 1);
        Configuration defaults = config.getDefaults();
        if (version >= VERSION || defaults == null || !config.contains("testbots", true)) {
            return;
        }
        List<String> changes = new ArrayList<>();
        ConfigurationSection shippedRoles = defaults.getConfigurationSection("testbots.roles");
        if (version < 2 && shippedRoles != null && config.contains("testbots.roles", true)) {
            for (String role : shippedRoles.getKeys(false)) {
                String path = "testbots.roles." + role;
                ConfigurationSection shipped = shippedRoles.getConfigurationSection(role);
                if (shipped == null || !config.contains(path, true)) {
                    continue;
                }
                double[][] old = SHIPPED_V1.get(role);
                if (old != null && sameAnchors(config.getList(path + ".anchors"), old)) {
                    config.set(path + ".anchors", shipped.getList("anchors"));
                    changes.add(role + " anchors → current spots");
                }
                for (String key : shipped.getKeys(false)) {
                    if (!config.contains(path + "." + key, true)) {
                        config.set(path + "." + key, shipped.get(key));
                        changes.add(role + "." + key + " added");
                    }
                }
            }
        }
        if (version < 3) {
            int added = addMissing(config, defaults);
            if (added > 0) {
                changes.add(added + " new keys (wave 3 roles, unlocks)");
            }
        }
        config.set("testbots.config-version", VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("config.yml patched to version " + VERSION
                + (changes.isEmpty() ? " (nothing else to change)" : ": " + String.join(", ", changes)));
    }

    /** Adds every shipped leaf under {@code testbots} the live file does not have. Lists count as leaves. */
    private static int addMissing(FileConfiguration config, Configuration defaults) {
        ConfigurationSection shipped = defaults.getConfigurationSection("testbots");
        if (shipped == null) {
            return 0;
        }
        int added = 0;
        for (String key : shipped.getKeys(true)) {
            String path = "testbots." + key;
            if (shipped.isConfigurationSection(key) || config.contains(path, true)) {
                continue;
            }
            config.set(path, shipped.get(key));
            added++;
        }
        return added;
    }

    private static boolean sameAnchors(List<?> live, double[][] old) {
        if (live == null || live.size() != old.length) {
            return false;
        }
        for (int i = 0; i < old.length; i++) {
            if (!(live.get(i) instanceof Map<?, ?> point)) {
                return false;
            }
            if (!near(point.get("x"), old[i][0]) || !near(point.get("y"), old[i][1]) || !near(point.get("z"), old[i][2])) {
                return false;
            }
        }
        return true;
    }

    private static boolean near(Object value, double expected) {
        return value instanceof Number number && Math.abs(number.doubleValue() - expected) < 0.01;
    }
}
