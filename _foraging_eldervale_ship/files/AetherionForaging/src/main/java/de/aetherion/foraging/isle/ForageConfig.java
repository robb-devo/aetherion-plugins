package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * {@code forage-isle.yml}: landmarks, updrafts, cast spots and tuning. On load the bundled defaults are
 * merged in <b>additively</b> — a missing landmark / key is added, a value you moved in-game is never
 * touched. DEV moves write straight back here.
 */
public final class ForageConfig {

    private final AetherionForaging plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<String, Landmark> landmarks = new LinkedHashMap<>();
    private final Map<String, Updraft> updrafts = new LinkedHashMap<>();

    public record Updraft(String id, String display, double fx, double fy, double fz, double tx, double ty, double tz) {
    }

    public ForageConfig(AetherionForaging plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "forage-isle.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("forage-isle.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        if (mergeDefaults()) {
            save();
        }
        landmarks.clear();
        ConfigurationSection lm = yaml.getConfigurationSection("landmarks");
        if (lm != null) {
            for (String id : lm.getKeys(false)) {
                Landmark landmark = Landmark.fromConfig(id, lm.getConfigurationSection(id));
                if (landmark != null) {
                    landmarks.put(landmark.id(), landmark);
                }
            }
        }
        updrafts.clear();
        ConfigurationSection up = yaml.getConfigurationSection("updrafts");
        if (up != null) {
            for (String id : up.getKeys(false)) {
                ConfigurationSection sec = up.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                double[] from = xyz(sec.getString("from"));
                double[] to = xyz(sec.getString("to"));
                if (from == null || to == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                updrafts.put(key, new Updraft(key, sec.getString("display", ForageText.pretty(id)),
                        from[0], from[1], from[2], to[0], to[1], to[2]));
            }
        }
    }

    /** Adds bundled keys that are missing; never replaces an existing value. */
    private boolean mergeDefaults() {
        try (InputStream in = plugin.getResource("forage-isle.yml")) {
            if (in == null) {
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (!yaml.contains(path, true) && !deletedByUser(path)) {
                    yaml.set(path, defaults.get(path));
                    changed = true;
                }
            }
            return changed;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "forage-isle.yml defaults could not be read", ex);
            return false;
        }
    }

    /** A landmark / updraft the admin removed stays removed (tombstoned under {@code removed}). */
    private boolean deletedByUser(String path) {
        List<String> removed = yaml.getStringList("removed");
        for (String gone : removed) {
            if (path.equals(gone) || path.startsWith(gone + ".")) {
                return true;
            }
        }
        return false;
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save forage-isle.yml", ex);
        }
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public boolean enabled() {
        return yaml.getBoolean("enabled", true);
    }

    public Map<String, Landmark> landmarks() {
        return Collections.unmodifiableMap(landmarks);
    }

    public Landmark landmark(String id) {
        return id == null ? null : landmarks.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Updraft> updrafts() {
        return Collections.unmodifiableMap(updrafts);
    }

    public List<Landmark> landmarksOf(Grove grove) {
        List<Landmark> out = new ArrayList<>();
        for (Landmark landmark : landmarks.values()) {
            if (landmark.grove() == grove) {
                out.add(landmark);
            }
        }
        return out;
    }

    public double tuning(String key, double fallback) {
        return yaml.getDouble("tuning." + key, fallback);
    }

    public int tuningInt(String key, int fallback) {
        return yaml.getInt("tuning." + key, fallback);
    }

    /** Moves a landmark's anchor (DEV). The box moves with it when it was radius-based. */
    public void moveLandmark(String id, double x, double y, double z) {
        String path = "landmarks." + id.toLowerCase(Locale.ROOT);
        ConfigurationSection sec = yaml.getConfigurationSection(path);
        if (sec == null) {
            return;
        }
        sec.set("anchor", fmt(x) + " " + fmt(y) + " " + fmt(z));
        save();
        reload();
    }

    public void moveUpdraft(String id, boolean from, double x, double y, double z) {
        String path = "updrafts." + id.toLowerCase(Locale.ROOT);
        ConfigurationSection sec = yaml.getConfigurationSection(path);
        if (sec == null) {
            sec = yaml.createSection(path);
            sec.set("display", ForageText.pretty(id));
        }
        sec.set(from ? "from" : "to", fmt(x) + " " + fmt(y) + " " + fmt(z));
        save();
        reload();
    }

    public void removeUpdraft(String id) {
        String path = "updrafts." + id.toLowerCase(Locale.ROOT);
        yaml.set(path, null);
        List<String> removed = new ArrayList<>(yaml.getStringList("removed"));
        if (!removed.contains(path)) {
            removed.add(path);
        }
        yaml.set("removed", removed);
        save();
        reload();
    }

    public double[] castAnchor(String role) {
        return xyz(yaml.getString("cast." + role + ".anchor"));
    }

    public float castYaw(String role) {
        return (float) yaml.getDouble("cast." + role + ".yaw", 0.0d);
    }

    public String castSkin(String role) {
        return yaml.getString("cast." + role + ".skin", "");
    }

    public void setCastAnchor(String role, double x, double y, double z, float yaw) {
        yaml.set("cast." + role + ".anchor", fmt(x) + " " + fmt(y) + " " + fmt(z));
        yaml.set("cast." + role + ".yaw", Math.round(yaw * 10.0f) / 10.0d);
        save();
    }

    public static String fmt(double v) {
        double r = Math.round(v * 2.0d) / 2.0d;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** "x y z" / "x,y,z" → three doubles, or null. */
    public static double[] xyz(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("[\\s,]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
