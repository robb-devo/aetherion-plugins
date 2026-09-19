package de.aetherion.foraging.weather;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.habitat.ForageHabitat;
import de.aetherion.foraging.habitat.ForageHabitatSense;
import de.aetherion.foraging.habitat.ForageHabitatService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient habitat + day-phase weather on the forage isle (player-local).
 * Ritual overrides sit on top until they expire.
 */
public final class IsleWeatherService implements Listener, Runnable {

    public record WeatherState(WeatherKind kind, WeatherSource source, String habitat, DayNightPhase phase) {
        public static WeatherState clear() {
            return new WeatherState(WeatherKind.CLEAR, WeatherSource.AMBIENT, "none", DayNightPhase.DAY);
        }
    }

    private record RitualOverride(WeatherKind kind, long untilMs) {
    }

    private record AmbientCache(WeatherKind kind, String habitat, DayNightPhase phase, long untilMs) {
    }

    private final AetherionForaging plugin;
    private final Map<UUID, RitualOverride> overrides = new ConcurrentHashMap<>();
    private final Map<UUID, AmbientCache> ambient = new ConcurrentHashMap<>();
    private final Map<UUID, WeatherState> lastApplied = new ConcurrentHashMap<>();
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Map<DayNightPhase, Map<WeatherKind, Integer>>> tables = new HashMap<>();
    private boolean enabled = true;
    private int rerollSeconds = 90;

    private record Snapshot(WeatherState state, long untilMs) {
    }

    public IsleWeatherService(AetherionForaging plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long period = Math.max(20L, plugin.getConfig().getLong("isle-weather.tick-ticks", 40L));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void reload() {
        tables.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("isle-weather");
        enabled = root == null || root.getBoolean("enabled", true);
        rerollSeconds = Math.max(30, root == null ? 90 : root.getInt("reroll-seconds", 90));
        if (root != null) {
            ConfigurationSection habitats = root.getConfigurationSection("habitats");
            if (habitats != null) {
                for (String habitatId : habitats.getKeys(false)) {
                    ConfigurationSection hab = habitats.getConfigurationSection(habitatId);
                    if (hab == null) {
                        continue;
                    }
                    Map<DayNightPhase, Map<WeatherKind, Integer>> byPhase = new EnumMap<>(DayNightPhase.class);
                    for (DayNightPhase phase : DayNightPhase.values()) {
                        ConfigurationSection phaseSec = hab.getConfigurationSection(phase.configKey());
                        if (phaseSec == null) {
                            continue;
                        }
                        Map<WeatherKind, Integer> weights = new EnumMap<>(WeatherKind.class);
                        for (String key : phaseSec.getKeys(false)) {
                            weights.put(WeatherKind.parse(key), Math.max(0, phaseSec.getInt(key)));
                        }
                        if (!weights.isEmpty()) {
                            byPhase.put(phase, weights);
                        }
                    }
                    if (!byPhase.isEmpty()) {
                        tables.put(habitatId.toLowerCase(Locale.ROOT), byPhase);
                    }
                }
            }
        }
        if (tables.isEmpty()) {
            loadDefaults();
        }
        plugin.getLogger().info("Isle weather: " + tables.size() + " habitats, enabled=" + enabled);
    }

    private void loadDefaults() {
        put("swamp", DayNightPhase.MORNING, w(WeatherKind.FOG, 50, WeatherKind.CLEAR, 30, WeatherKind.DRIZZLE, 20));
        put("swamp", DayNightPhase.DAY, w(WeatherKind.FOG, 25, WeatherKind.CLEAR, 40, WeatherKind.DRIZZLE, 25, WeatherKind.RAIN, 10));
        put("swamp", DayNightPhase.EVENING, w(WeatherKind.FOG, 40, WeatherKind.DRIZZLE, 30, WeatherKind.CLEAR, 30));
        put("swamp", DayNightPhase.NIGHT, w(WeatherKind.FOG, 45, WeatherKind.CLEAR, 35, WeatherKind.RAIN, 20));

        put("snow", DayNightPhase.MORNING, w(WeatherKind.SNOW, 60, WeatherKind.CLEAR, 25, WeatherKind.WINDY, 15));
        put("snow", DayNightPhase.DAY, w(WeatherKind.SNOW, 60, WeatherKind.CLEAR, 30, WeatherKind.WINDY, 10));
        put("snow", DayNightPhase.EVENING, w(WeatherKind.SNOW, 60, WeatherKind.CLEAR, 20, WeatherKind.WINDY, 20));
        put("snow", DayNightPhase.NIGHT, w(WeatherKind.SNOW, 65, WeatherKind.CLEAR, 20, WeatherKind.WINDY, 15));

        put("savanna", DayNightPhase.MORNING, w(WeatherKind.CLEAR, 70, WeatherKind.WINDY, 25, WeatherKind.DRIZZLE, 5));
        put("savanna", DayNightPhase.DAY, w(WeatherKind.CLEAR, 75, WeatherKind.WINDY, 25));
        put("savanna", DayNightPhase.EVENING, w(WeatherKind.CLEAR, 60, WeatherKind.WINDY, 35, WeatherKind.DRIZZLE, 5));
        put("savanna", DayNightPhase.NIGHT, w(WeatherKind.CLEAR, 70, WeatherKind.WINDY, 30));

        put("plains", DayNightPhase.MORNING, w(WeatherKind.CLEAR, 60, WeatherKind.FOG, 20, WeatherKind.DRIZZLE, 20));
        put("plains", DayNightPhase.DAY, w(WeatherKind.CLEAR, 70, WeatherKind.DRIZZLE, 20, WeatherKind.RAIN, 10));
        put("plains", DayNightPhase.EVENING, w(WeatherKind.CLEAR, 45, WeatherKind.FOG, 35, WeatherKind.DRIZZLE, 20));
        put("plains", DayNightPhase.NIGHT, w(WeatherKind.CLEAR, 55, WeatherKind.FOG, 30, WeatherKind.RAIN, 15));

        put("flower", DayNightPhase.MORNING, w(WeatherKind.CLEAR, 65, WeatherKind.FOG, 20, WeatherKind.DRIZZLE, 15));
        put("flower", DayNightPhase.DAY, w(WeatherKind.CLEAR, 75, WeatherKind.DRIZZLE, 20, WeatherKind.RAIN, 5));
        put("flower", DayNightPhase.EVENING, w(WeatherKind.CLEAR, 50, WeatherKind.FOG, 35, WeatherKind.DRIZZLE, 15));
        put("flower", DayNightPhase.NIGHT, w(WeatherKind.CLEAR, 60, WeatherKind.FOG, 30, WeatherKind.DRIZZLE, 10));

        put("jungle", DayNightPhase.MORNING, w(WeatherKind.DRIZZLE, 35, WeatherKind.FOG, 25, WeatherKind.CLEAR, 25, WeatherKind.RAIN, 15));
        put("jungle", DayNightPhase.DAY, w(WeatherKind.CLEAR, 40, WeatherKind.DRIZZLE, 30, WeatherKind.RAIN, 20, WeatherKind.FOG, 10));
        put("jungle", DayNightPhase.EVENING, w(WeatherKind.RAIN, 30, WeatherKind.DRIZZLE, 30, WeatherKind.FOG, 25, WeatherKind.CLEAR, 15));
        put("jungle", DayNightPhase.NIGHT, w(WeatherKind.RAIN, 25, WeatherKind.FOG, 35, WeatherKind.DRIZZLE, 25, WeatherKind.CLEAR, 15));

        put("dark_thicket", DayNightPhase.MORNING, w(WeatherKind.FOG, 40, WeatherKind.CLEAR, 40, WeatherKind.DRIZZLE, 20));
        put("dark_thicket", DayNightPhase.DAY, w(WeatherKind.CLEAR, 50, WeatherKind.FOG, 30, WeatherKind.DRIZZLE, 20));
        put("dark_thicket", DayNightPhase.EVENING, w(WeatherKind.FOG, 50, WeatherKind.CLEAR, 30, WeatherKind.RAIN, 20));
        put("dark_thicket", DayNightPhase.NIGHT, w(WeatherKind.FOG, 55, WeatherKind.CLEAR, 25, WeatherKind.RAIN, 20));

        put("lush", DayNightPhase.MORNING, w(WeatherKind.FOG, 30, WeatherKind.CLEAR, 50, WeatherKind.DRIZZLE, 20));
        put("lush", DayNightPhase.DAY, w(WeatherKind.CLEAR, 60, WeatherKind.DRIZZLE, 25, WeatherKind.RAIN, 15));
        put("lush", DayNightPhase.EVENING, w(WeatherKind.FOG, 35, WeatherKind.CLEAR, 40, WeatherKind.DRIZZLE, 25));
        put("lush", DayNightPhase.NIGHT, w(WeatherKind.CLEAR, 50, WeatherKind.FOG, 35, WeatherKind.DRIZZLE, 15));
    }

    private void put(String habitat, DayNightPhase phase, Map<WeatherKind, Integer> weights) {
        tables.computeIfAbsent(habitat, k -> new EnumMap<>(DayNightPhase.class)).put(phase, weights);
    }

    private static Map<WeatherKind, Integer> w(Object... pairs) {
        Map<WeatherKind, Integer> map = new EnumMap<>(WeatherKind.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((WeatherKind) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    /** Public API for fishing / rituals / HUD. */
    public WeatherState current(Player player) {
        if (player == null) {
            return WeatherState.clear();
        }
        long now = System.currentTimeMillis();
        Snapshot snap = snapshots.get(player.getUniqueId());
        if (snap != null && snap.untilMs() > now) {
            return snap.state();
        }
        WeatherState state = computeCurrent(player, now);
        snapshots.put(player.getUniqueId(), new Snapshot(state, now + 1000L));
        return state;
    }

    private WeatherState computeCurrent(Player player, long now) {
        Location at = player.getLocation();
        DayNightPhase phase = DayNightPhase.ofWorld(at.getWorld());
        boolean onIsle = ForageHabitatService.inIsleFootprint(at);
        String habitat = onIsle ? habitatId(at) : "off-isle";

        RitualOverride override = overrides.get(player.getUniqueId());
        if (override != null && override.untilMs() > now) {
            return new WeatherState(override.kind(), WeatherSource.RITUAL, habitat, phase);
        }
        if (override != null) {
            overrides.remove(player.getUniqueId());
        }
        if (!onIsle) {
            return WeatherState.clear();
        }
        WeatherKind kind = resolveAmbient(player, habitat, phase, now);
        return new WeatherState(kind, WeatherSource.AMBIENT, habitat, phase);
    }

    public void setRitualOverride(Player player, WeatherKind kind, int durationSeconds) {
        if (player == null || kind == null) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(10, durationSeconds) * 1000L;
        overrides.put(player.getUniqueId(), new RitualOverride(kind, until));
        ambient.remove(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        apply(player, current(player), true);
    }

    public void clearRitualOverride(Player player) {
        if (player == null) {
            return;
        }
        overrides.remove(player.getUniqueId());
        ambient.remove(player.getUniqueId());
        lastApplied.remove(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        if (ForageHabitatService.inIsleFootprint(player.getLocation())) {
            apply(player, current(player), true);
        } else {
            clearPlayerWeather(player);
        }
    }

    @Override
    public void run() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            RitualOverride override = overrides.get(player.getUniqueId());
            boolean forced = override != null && override.untilMs() > now;
            boolean onIsle = ForageHabitatService.inIsleFootprint(at);
            if (!onIsle && !forced) {
                // Only reset when we previously applied something — avoid packet spam.
                if (lastApplied.containsKey(player.getUniqueId())
                        || overrides.containsKey(player.getUniqueId())
                        || ambient.containsKey(player.getUniqueId())) {
                    clearPlayerWeather(player);
                    lastApplied.remove(player.getUniqueId());
                    overrides.remove(player.getUniqueId());
                }
                continue;
            }
            WeatherState state = current(player);
            apply(player, state, false);
        }
    }

    private WeatherKind resolveAmbient(Player player, String habitat, DayNightPhase phase, long now) {
        AmbientCache cache = ambient.get(player.getUniqueId());
        if (cache != null
                && cache.untilMs > now
                && cache.habitat().equals(habitat)
                && cache.phase() == phase) {
            return cache.kind();
        }
        WeatherKind rolled = roll(habitat, phase);
        ambient.put(player.getUniqueId(), new AmbientCache(rolled, habitat, phase, now + rerollSeconds * 1000L));
        return rolled;
    }

    private WeatherKind roll(String habitat, DayNightPhase phase) {
        Map<DayNightPhase, Map<WeatherKind, Integer>> byPhase = tables.get(habitat);
        if (byPhase == null) {
            byPhase = tables.get("plains");
        }
        if (byPhase == null) {
            return WeatherKind.CLEAR;
        }
        Map<WeatherKind, Integer> weights = byPhase.get(phase);
        if (weights == null || weights.isEmpty()) {
            weights = byPhase.get(DayNightPhase.DAY);
        }
        if (weights == null || weights.isEmpty()) {
            return WeatherKind.CLEAR;
        }
        int total = 0;
        for (int w : weights.values()) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return WeatherKind.CLEAR;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (Map.Entry<WeatherKind, Integer> e : weights.entrySet()) {
            cursor += Math.max(0, e.getValue());
            if (pick < cursor) {
                return e.getKey();
            }
        }
        return WeatherKind.CLEAR;
    }

    private String habitatId(Location at) {
        ForageHabitatSense sense = plugin.habitats() == null ? null : plugin.habitats().sense();
        if (sense != null) {
            ForageHabitatSense.Rule rule = sense.detect(at);
            if (rule != null) {
                return rule.id();
            }
        }
        ForageHabitat box = plugin.habitats() == null ? null : plugin.habitats().at(at);
        if (box != null && box.id() != null) {
            return box.id().toLowerCase(Locale.ROOT);
        }
        return "plains";
    }

    private void apply(Player player, WeatherState state, boolean force) {
        WeatherState prev = lastApplied.get(player.getUniqueId());
        if (!force && prev != null && prev.kind() == state.kind() && prev.source() == state.source()) {
            return;
        }
        lastApplied.put(player.getUniqueId(), state);
        // Visual weather (rain/snow packets) tanks FPS in dense foliage — keep CLEAR always.
        // Kind still drives TAB + loot/rituals; fog/rain are labels only until pack shaders.
        player.setPlayerWeather(WeatherType.CLEAR);
    }

    private void clearPlayerWeather(Player player) {
        try {
            player.resetPlayerWeather();
        } catch (Throwable ignored) {
        }
        try {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        } catch (Throwable ignored) {
        }
        ambient.remove(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Strip leftover Darkness / personal weather from earlier experiments.
        clearPlayerWeather(event.getPlayer());
        lastApplied.remove(event.getPlayer().getUniqueId());
        snapshots.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        overrides.remove(id);
        ambient.remove(id);
        lastApplied.remove(id);
        snapshots.remove(id);
    }
}
