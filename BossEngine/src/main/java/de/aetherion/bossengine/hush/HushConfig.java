package de.aetherion.bossengine.hush;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Immutable snapshot of {@code hush.yml}. Every value has an in-code default, so a partial or old file
 * never breaks a fight; a running encounter keeps the snapshot it started with.
 */
public final class HushConfig {

    private final YamlConfiguration yaml;

    private HushConfig(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static HushConfig load(File file) {
        YamlConfiguration yaml = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        return new HushConfig(yaml);
    }

    public static HushConfig defaults() {
        return new HushConfig(new YamlConfiguration());
    }

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

    public List<String> list(String path) {
        return yaml.getStringList(path);
    }

    /* ------------------------------------------------------------------ typed */

    public boolean enabled() {
        return b("enabled", true);
    }

    public String worldName() {
        return s("world.name", "aetherion_hush");
    }

    public int arenaY() {
        return i("world.arena-y", 100);
    }

    public int slotSpacing() {
        return Math.max(256, i("world.slot-spacing", 512));
    }

    public int maxInstances() {
        return Math.max(1, i("world.max-instances", 3));
    }

    public boolean keepInventory() {
        return b("world.keep-inventory", true);
    }

    public String entryPermission() {
        return s("entry.permission", "hush.enter");
    }

    public double entryRadius() {
        return d("entry.radius", 16.0);
    }

    public int minPlayers() {
        return Math.max(1, i("entry.min-players", 1));
    }

    public int maxPlayers() {
        return Math.max(1, i("entry.max-players", 5));
    }

    public int cooldownSeconds() {
        return Math.max(0, i("entry.cooldown-seconds", 120));
    }

    /** Bells in the normal worlds that open the encounter on right-click ({@code world,x,y,z}). */
    public List<String> entryBells() {
        return list("entry.bells");
    }

    public String fallbackReturn() {
        return s("entry.fallback-return", "");
    }

    public double healthPerExtra() {
        return d("scaling.health-per-extra-player", 0.55);
    }

    public double damagePerExtra() {
        return d("scaling.damage-per-extra-player", 0.08);
    }

    public int maxScaledPlayers() {
        return Math.max(1, i("scaling.max-players", 5));
    }

    public int respawnGraceTicks() {
        return Math.max(0, i("safety.respawn-grace-ticks", 60));
    }

    public int killPlane() {
        return Math.max(4, i("safety.kill-plane", 14));
    }

    public double rescuePower() {
        return d("safety.rescue-power", 30.0);
    }

    public int enrageTicks() {
        return Math.max(20 * 60, i("fight.enrage-seconds", 540) * 20);
    }
}
