package de.aetherion.bossengine.helios;

import de.aetherion.bossengine.helios.core.DisplayBudget;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of {@code helios.yml}. Every value has an in-code default so a partial or old
 * file never breaks a fight; running encounters keep the snapshot they started with.
 */
public final class HeliosConfig {

    public enum VoidMode { RESCUE, DEATH }

    private final YamlConfiguration yaml;
    private final Map<String, String> soundOverrides = new HashMap<>();

    private HeliosConfig(YamlConfiguration yaml) {
        this.yaml = yaml;
        ConfigurationSection over = yaml.getConfigurationSection("sounds.overrides");
        if (over != null) {
            for (String k : over.getKeys(false)) {
                String v = over.getString(k);
                if (v != null && !v.isBlank()) {
                    soundOverrides.put(k, v);
                }
            }
        }
    }

    public static HeliosConfig load(File file) {
        YamlConfiguration yaml = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        return new HeliosConfig(yaml);
    }

    public static HeliosConfig defaults() {
        return new HeliosConfig(new YamlConfiguration());
    }

    /* ------------------------------------------------------------------ raw access */

    public double d(String path, double def) {
        return yaml.getDouble(path, def);
    }

    public int i(String path, int def) {
        return yaml.getInt(path, def);
    }

    public boolean b(String path, boolean def) {
        return yaml.getBoolean(path, def);
    }

    public String s(String path, String def) {
        String v = yaml.getString(path);
        return v == null ? def : v;
    }

    /** BossHits power for an attack, already multiplied by the global damage multiplier. */
    public double power(String path, double def) {
        return d(path + ".power", def) * d("damage.multiplier", 1.0);
    }

    /* ------------------------------------------------------------------ typed */

    public boolean enabled() {
        return b("enabled", true);
    }

    public String worldName() {
        return s("world.name", "helios_requiem");
    }

    public boolean keepInventory() {
        return b("world.keep-inventory", true);
    }

    public int arenaY() {
        return i("world.arena-y", 160);
    }

    public int slotSpacing() {
        return Math.max(256, i("world.slot-spacing", 1024));
    }

    public int maxInstances() {
        return Math.max(1, i("world.max-instances", 3));
    }

    public String entryPermission() {
        return s("entry.permission", "");
    }

    public double entryRadius() {
        return d("entry.radius", 16.0);
    }

    public int minPlayers() {
        return Math.max(1, i("entry.min-players", 1));
    }

    public int maxPlayers() {
        return Math.max(1, i("entry.max-players", 8));
    }

    public int cooldownSeconds() {
        return Math.max(0, i("entry.cooldown-seconds", 0));
    }

    public String fallbackReturn() {
        return s("entry.fallback-return", "");
    }

    public double healthPerExtra() {
        return d("scaling.health-per-extra-player", 0.45);
    }

    public double damagePerExtra() {
        return d("scaling.damage-per-extra-player", 0.06);
    }

    public int maxScaledPlayers() {
        return Math.max(1, i("scaling.max-scaled-players", 8));
    }

    public VoidMode voidMode() {
        try {
            return VoidMode.valueOf(s("void.mode", "RESCUE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VoidMode.RESCUE;
        }
    }

    public double rescuePower() {
        return d("void.rescue-power", 70.0) * d("damage.multiplier", 1.0);
    }

    public int killPlane() {
        return Math.max(4, i("void.kill-plane", 14));
    }

    public boolean echoes() {
        return b("echo.enabled", false);
    }

    public int respawnGraceTicks() {
        return Math.max(0, i("death.respawn-grace-seconds", 4) * 20);
    }

    public int rescueGraceTicks() {
        return Math.max(0, i("void.rescue-grace-seconds", 3) * 20);
    }

    public int heraldEnrageTicks() {
        return Math.max(20 * 30, i("enrage.herald-seconds", 420) * 20);
    }

    public int heliosEnrageTicks() {
        return Math.max(20 * 30, i("enrage.helios-seconds", 780) * 20);
    }

    public double enrageRamp() {
        return d("enrage.ramp-per-10s", 0.10);
    }

    public int displayCap() {
        return i("performance.display-cap", 420);
    }

    public int spawnsPerTick() {
        return i("performance.spawns-per-tick", 48);
    }

    public DisplayBudget.Quality quality() {
        try {
            return DisplayBudget.Quality.valueOf(s("performance.quality", "HIGH").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DisplayBudget.Quality.HIGH;
        }
    }

    public double degradeMspt() {
        return d("performance.degrade-mspt", 45.0);
    }

    public int buildPerTick() {
        return Math.max(200, i("performance.build-blocks-per-tick", 2500));
    }

    public int clearPerTick() {
        return Math.max(2000, i("performance.clear-blocks-per-tick", 60000));
    }

    public double bpm(String key, double def) {
        return Math.max(30.0, d("tempo." + key, def));
    }

    public float masterVolume() {
        return (float) d("sounds.master-volume", 1.0);
    }

    public Map<String, String> soundOverrides() {
        return soundOverrides;
    }

    public int claimSeconds() {
        return Math.max(20, i("reward.claim-seconds", 180));
    }
}
