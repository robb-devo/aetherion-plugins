package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

/**
 * {@code plugins/AetherionHub/origin.yml}: the measured Origin map (districts, landmarks, waystones, vistas,
 * bells, updrafts, flights, emitters, cast spots). Defaults ship in the jar; missing keys are merged into the
 * live file on load and existing values are never changed.
 */
public final class OriginConfig {

    public static final String FILE = "origin.yml";

    public record District(String id, String name, String color, Material icon, String tagline,
                           List<double[]> circles, double minY, double maxY, double[] anchor, String soundscape) {

        public boolean contains(double x, double y, double z) {
            if (y < minY || y > maxY) {
                return false;
            }
            for (double[] c : circles) {
                double dx = x - c[0];
                double dz = z - c[1];
                if (dx * dx + dz * dz <= c[2] * c[2]) {
                    return true;
                }
            }
            return false;
        }

        public String colored() {
            return color + name;
        }
    }

    public record Landmark(String id, String name, String district, double[] at, double radius, Material icon, String blurb) {
    }

    public record Waystone(String id, String name, String district, double[] stand, double[] cap, boolean sprout, String blurb) {
    }

    public record Vista(String id, String name, double[] at, double radius) {
    }

    public record Bell(String id, String name, int[] at) {
    }

    public record Updraft(String id, String name, double[] floor, double[] top) {
    }

    public enum Trigger { PAD, RIFT }

    public record Flight(String id, String name, String to, Trigger trigger, int[] min, int[] max, double[] at,
                         double radius, double speed, List<double[]> path) {

        public double[] start() {
            return path.get(0);
        }

        public double[] end() {
            return path.get(path.size() - 1);
        }

        public boolean padContains(int x, int y, int z) {
            return trigger == Trigger.PAD && min != null && max != null
                    && x >= Math.min(min[0], max[0]) && x <= Math.max(min[0], max[0])
                    && y >= Math.min(min[1], max[1]) && y <= Math.max(min[1], max[1])
                    && z >= Math.min(min[2], max[2]) && z <= Math.max(min[2], max[2]);
        }

        /** Where the take-off marker sits (pad centre or rift ring). */
        public double[] marker() {
            if (trigger == Trigger.RIFT && at != null) {
                return at;
            }
            if (min != null && max != null) {
                return new double[]{(min[0] + max[0]) / 2.0d + 0.5d, Math.max(min[1], max[1]) + 1.0d, (min[2] + max[2]) / 2.0d + 0.5d};
            }
            return start();
        }
    }

    public record Emitter(String kind, double[] at, double radius, String when) {
    }

    public record SpawnSeed(String id, double[] at, double discoverRadius) {
    }

    private final JavaPlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();

    private boolean enabled;
    private String worldName;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private String quietUntil;
    private boolean rescue;
    private int floorY;
    private final Map<String, Long> rewards = new LinkedHashMap<>();
    private final Map<String, District> districts = new LinkedHashMap<>();
    private final Map<String, Landmark> landmarks = new LinkedHashMap<>();
    private final Map<String, Waystone> waystones = new LinkedHashMap<>();
    private final Map<String, Vista> vistas = new LinkedHashMap<>();
    private final Map<String, Bell> bells = new LinkedHashMap<>();
    private final Map<String, Updraft> updrafts = new LinkedHashMap<>();
    private final Map<String, Flight> flights = new LinkedHashMap<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final Map<String, double[]> castPresets = new LinkedHashMap<>();
    private boolean castAutoPlace;
    private final Map<String, SpawnSeed> spawnSeeds = new LinkedHashMap<>();

    public OriginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads {@code origin.yml}, merging jar defaults into the live file. Returns a one-line summary. */
    public String load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource(FILE, false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(FILE)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean missing = missingKeys(yaml, defaults);
                yaml.setDefaults(defaults);
                yaml.options().copyDefaults(true);
                if (missing) {
                    yaml.save(file);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("origin.yml: could not merge defaults: " + exception.getMessage());
        }
        parse();
        return districts.size() + " districts · " + landmarks.size() + " landmarks · " + waystones.size() + " waystones · "
                + vistas.size() + " vistas · " + bells.size() + " bells · " + updrafts.size() + " updrafts · "
                + flights.size() + " flights · " + emitters.size() + " emitters";
    }

    private static boolean missingKeys(YamlConfiguration live, YamlConfiguration defaults) {
        // Checked before the defaults are attached: contains(key, true) ignores defaults.
        for (String key : defaults.getKeys(true)) {
            if (!live.contains(key, true)) {
                return true;
            }
        }
        return false;
    }

    private void parse() {
        enabled = yaml.getBoolean("enabled", true);
        worldName = yaml.getString("world", "world");
        minX = yaml.getInt("footprint.min-x", -540);
        maxX = yaml.getInt("footprint.max-x", 470);
        minZ = yaml.getInt("footprint.min-z", -630);
        maxZ = yaml.getInt("footprint.max-z", 380);
        quietUntil = yaml.getString("quiet-until-spawn", "capital");
        rescue = yaml.getBoolean("rescue.enabled", true);
        floorY = yaml.getInt("rescue.floor-y", -60);

        rewards.clear();
        ConfigurationSection rs = yaml.getConfigurationSection("rewards");
        if (rs != null) {
            for (String key : rs.getKeys(false)) {
                rewards.put(key, rs.getLong(key));
            }
        }

        districts.clear();
        ConfigurationSection ds = yaml.getConfigurationSection("districts");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection s = ds.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<double[]> circles = new ArrayList<>();
                for (String raw : s.getStringList("circles")) {
                    double[] c = nums(raw);
                    if (c != null && c.length >= 3) {
                        circles.add(c);
                    }
                }
                districts.put(id, new District(id, s.getString("name", OriginText.pretty(id)), s.getString("color", "§f"),
                        material(s.getString("icon"), Material.MAP), s.getString("tagline", ""), Collections.unmodifiableList(circles),
                        s.getDouble("min-y", -1000.0d), s.getDouble("max-y", 1000.0d), nums(s.getString("anchor")),
                        s.getString("soundscape", "none")));
            }
        }

        landmarks.clear();
        ConfigurationSection ls = yaml.getConfigurationSection("landmarks");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(id);
                double[] at = s == null ? null : nums(s.getString("at"));
                if (at == null) {
                    continue;
                }
                landmarks.put(id, new Landmark(id, s.getString("name", OriginText.pretty(id)), s.getString("district", ""), at,
                        s.getDouble("radius", 10.0d), material(s.getString("icon"), Material.PAPER), s.getString("blurb", "")));
            }
        }

        waystones.clear();
        ConfigurationSection ws = yaml.getConfigurationSection("waystones");
        if (ws != null) {
            for (String id : ws.getKeys(false)) {
                ConfigurationSection s = ws.getConfigurationSection(id);
                double[] stand = s == null ? null : nums(s.getString("stand"));
                if (stand == null) {
                    continue;
                }
                double[] cap = nums(s.getString("cap"));
                waystones.put(id, new Waystone(id, s.getString("name", OriginText.pretty(id)), s.getString("district", ""), stand,
                        cap == null ? new double[]{stand[0], stand[1] + 6.0d, stand[2]} : cap, s.getBoolean("sprout", false),
                        s.getString("blurb", "")));
            }
        }

        vistas.clear();
        ConfigurationSection vs = yaml.getConfigurationSection("vistas");
        if (vs != null) {
            for (String id : vs.getKeys(false)) {
                ConfigurationSection s = vs.getConfigurationSection(id);
                double[] at = s == null ? null : nums(s.getString("at"));
                if (at != null) {
                    vistas.put(id, new Vista(id, s.getString("name", OriginText.pretty(id)), at, s.getDouble("radius", 4.0d)));
                }
            }
        }

        bells.clear();
        ConfigurationSection bs = yaml.getConfigurationSection("bells");
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(id);
                double[] at = s == null ? null : nums(s.getString("at"));
                if (at != null) {
                    bells.put(id, new Bell(id, s.getString("name", OriginText.pretty(id)),
                            new int[]{(int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2])}));
                }
            }
        }

        updrafts.clear();
        ConfigurationSection us = yaml.getConfigurationSection("updrafts");
        if (us != null) {
            for (String id : us.getKeys(false)) {
                ConfigurationSection s = us.getConfigurationSection(id);
                double[] floor = s == null ? null : nums(s.getString("floor"));
                double[] top = s == null ? null : nums(s.getString("top"));
                if (floor != null && top != null) {
                    updrafts.put(id, new Updraft(id, s.getString("name", OriginText.pretty(id)), floor, top));
                }
            }
        }

        flights.clear();
        ConfigurationSection fs = yaml.getConfigurationSection("flights");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<double[]> path = new ArrayList<>();
                for (String raw : s.getStringList("path")) {
                    double[] p = nums(raw);
                    if (p != null && p.length >= 3) {
                        path.add(p);
                    }
                }
                if (path.size() < 2) {
                    continue;
                }
                Trigger trigger = "pad".equalsIgnoreCase(s.getString("trigger", "rift")) ? Trigger.PAD : Trigger.RIFT;
                flights.put(id, new Flight(id, s.getString("name", OriginText.pretty(id)), s.getString("to", ""), trigger,
                        ints(s.getString("min")), ints(s.getString("max")), nums(s.getString("at")), s.getDouble("radius", 1.4d),
                        Math.max(0.6d, Math.min(3.4d, s.getDouble("speed", 2.4d))), Collections.unmodifiableList(path)));
            }
        }

        emitters.clear();
        for (Map<?, ?> map : yaml.getMapList("emitters")) {
            Object kind = map.get("kind");
            Object at = map.get("at");
            double[] pos = at == null ? null : nums(String.valueOf(at));
            if (kind == null || pos == null) {
                continue;
            }
            Object radius = map.get("radius");
            Object when = map.get("when");
            emitters.add(new Emitter(String.valueOf(kind).toLowerCase(Locale.ROOT), pos,
                    radius instanceof Number n ? n.doubleValue() : 24.0d, when == null ? "any" : String.valueOf(when)));
        }

        castPresets.clear();
        castAutoPlace = yaml.getBoolean("cast.auto-place", true);
        ConfigurationSection cs = yaml.getConfigurationSection("cast.presets");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                double[] p = nums(cs.getString(id));
                if (p != null) {
                    castPresets.put(id, p);
                }
            }
        }

        spawnSeeds.clear();
        ConfigurationSection ss = yaml.getConfigurationSection("spawns");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection s = ss.getConfigurationSection(id);
                double[] at = s == null ? null : nums(s.getString("at"));
                if (at != null) {
                    spawnSeeds.put(id, new SpawnSeed(id, at, s.getDouble("discover-radius", 24.0d)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ accessors

    public YamlConfiguration raw() {
        return yaml;
    }

    public boolean enabled() {
        return enabled;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public boolean inFootprint(Location at) {
        return at != null && at.getWorld() != null && at.getWorld().getName().equalsIgnoreCase(worldName)
                && at.getX() >= minX && at.getX() <= maxX && at.getZ() >= minZ && at.getZ() <= maxZ;
    }

    public int[] footprint() {
        return new int[]{minX, maxX, minZ, maxZ};
    }

    public String quietUntil() {
        return quietUntil == null ? "" : quietUntil;
    }

    public boolean rescue() {
        return rescue;
    }

    public int floorY() {
        return floorY;
    }

    public long reward(String key, long fallback) {
        return rewards.getOrDefault(key, fallback);
    }

    public Map<String, District> districts() {
        return districts;
    }

    public District district(String id) {
        return id == null ? null : districts.get(id.toLowerCase(Locale.ROOT));
    }

    public District districtAt(Location at) {
        if (!inFootprint(at)) {
            return null;
        }
        for (District district : districts.values()) {
            if (district.contains(at.getX(), at.getY(), at.getZ())) {
                return district;
            }
        }
        return null;
    }

    public Map<String, Landmark> landmarks() {
        return landmarks;
    }

    public Landmark landmark(String id) {
        return id == null ? null : landmarks.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Waystone> waystones() {
        return waystones;
    }

    public Map<String, Vista> vistas() {
        return vistas;
    }

    public Map<String, Bell> bells() {
        return bells;
    }

    public Map<String, Updraft> updrafts() {
        return updrafts;
    }

    public Map<String, Flight> flights() {
        return flights;
    }

    public List<Emitter> emitters() {
        return emitters;
    }

    public Map<String, double[]> castPresets() {
        return castPresets;
    }

    public boolean castAutoPlace() {
        return castAutoPlace;
    }

    public Map<String, SpawnSeed> spawnSeeds() {
        return spawnSeeds;
    }

    public double[] point(String path) {
        return nums(yaml.getString(path));
    }

    public List<double[]> points(String path) {
        List<double[]> out = new ArrayList<>();
        for (String raw : yaml.getStringList(path)) {
            double[] p = nums(raw);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public Location location(double[] p) {
        World world = world();
        if (world == null || p == null || p.length < 3) {
            return null;
        }
        Location at = new Location(world, p[0], p[1], p[2]);
        if (p.length >= 4) {
            at.setYaw((float) p[3]);
        }
        if (p.length >= 5) {
            at.setPitch((float) p[4]);
        }
        return at;
    }

    // ------------------------------------------------------------------ parsing helpers

    static double[] nums(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("[\\s,]+");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i]);
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return out;
    }

    static int[] ints(String raw) {
        double[] d = nums(raw);
        if (d == null || d.length < 3) {
            return null;
        }
        return new int[]{(int) Math.floor(d[0]), (int) Math.floor(d[1]), (int) Math.floor(d[2])};
    }

    static Material material(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        return material == null || !material.isItem() ? fallback : material;
    }
}
