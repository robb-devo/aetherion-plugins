package de.aetherion.guilds.island;

import de.aetherion.guilds.structure.BuildCosts;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an island has unlocked at its Hub, and the first-time milestones of its factory (first hut, first belt,
 * first quarry, Hub founded, first machine, first unlock, first upgrade...). Both live in the island's own save
 * file (section {@code progress}).
 *
 * <p><b>The funnel.</b> Every first milestone is logged once, in plain words, with the time since the island's
 * first step: to the console ({@code [Funnel] Robb (p:…): first belt after 4m 12s}) and to
 * {@code plugins/AetherionGuilds/funnel.log}. {@code /island admin funnel} counts how many islands reached each
 * step, so you see where people stop without reading code.
 */
public final class FactoryProgress {

    /** The steps of the funnel, in the order a new player meets them. */
    public static final List<String> STEPS = List.of("start", "first_hut", "first_quarry", "first_belt", "first_chain",
            "hub_founded", "first_unlock", "first_machine", "first_piece", "first_upgrade");

    private final JavaPlugin plugin;
    private final IslandFiles files;
    private final Map<String, Set<String>> unlocked = new HashMap<>();
    private final Map<String, Map<String, Long>> milestones = new HashMap<>();
    private boolean dirty;

    public FactoryProgress(JavaPlugin plugin, IslandFiles files) {
        this.plugin = plugin;
        this.files = files;
        for (String key : files.keys()) {
            YamlConfiguration doc = files.doc(key);
            ConfigurationSection section = doc.getConfigurationSection("progress");
            if (section == null) {
                continue;
            }
            unlocked.put(key, new HashSet<>(section.getStringList("unlocked")));
            ConfigurationSection steps = section.getConfigurationSection("milestones");
            if (steps != null) {
                Map<String, Long> map = new LinkedHashMap<>();
                for (String step : steps.getKeys(false)) {
                    map.put(step, steps.getLong(step));
                }
                milestones.put(key, map);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // unlocks
    // ------------------------------------------------------------------------------------------------

    public boolean unlocked(IslandHost host, StructureType type) {
        if (BuildCosts.unlock(type) == null) {
            return true;
        }
        Set<String> set = unlocked.get(host.key());
        return set != null && set.contains(type.id());
    }

    public void unlock(IslandHost host, StructureType type) {
        if (unlocked.computeIfAbsent(host.key(), k -> new HashSet<>()).add(type.id())) {
            dirty = true;
        }
    }

    /** The next part this island can unlock (first locked one in Hub order), or null when all are unlocked. */
    public BuildCosts.Unlock next(IslandHost host) {
        for (BuildCosts.Unlock unlock : BuildCosts.UNLOCKS) {
            if (!unlocked(host, unlock.type())) {
                return unlock;
            }
        }
        return null;
    }

    /** Islands that already built a part before unlocks existed keep it unlocked (nobody loses anything). */
    public int grandfather(StructureService structures, Iterable<IslandHost> hosts) {
        int granted = 0;
        for (IslandHost host : hosts) {
            for (PlacedStructure structure : structures.of(host)) {
                if (BuildCosts.unlock(structure.type()) != null && !unlocked(host, structure.type())) {
                    unlock(host, structure.type());
                    granted++;
                }
            }
        }
        if (granted > 0) {
            plugin.getLogger().info("Island unlocks: " + granted + " parts already built on islands are unlocked for them.");
        }
        return granted;
    }

    // ------------------------------------------------------------------------------------------------
    // milestones (the funnel)
    // ------------------------------------------------------------------------------------------------

    /** Records a first-time step for an island (once) and logs it with the time since its first step. */
    public void milestone(IslandHost host, String step) {
        if (host == null) {
            return;
        }
        Map<String, Long> map = milestones.computeIfAbsent(host.key(), k -> new LinkedHashMap<>());
        long now = System.currentTimeMillis();
        if (map.isEmpty()) {
            map.put("start", now);
            dirty = true;
            if ("start".equals(step)) {
                log(host, now, "[Funnel] " + name(host) + " (" + host.key() + "): started an island");
                return;
            }
        }
        if (map.containsKey(step)) {
            return;
        }
        map.put(step, now);
        dirty = true;
        long since = now - map.getOrDefault("start", now);
        String line = "[Funnel] " + name(host) + " (" + host.key() + "): " + step.replace('_', ' ') + " after "
                + duration(since);
        log(host, now, line);
    }

    private void log(IslandHost host, long now, String line) {
        plugin.getLogger().info(line);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "funnel.log").toPath(),
                    Instant.ofEpochMilli(now) + " " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not append to funnel.log: " + exception.getMessage());
        }
    }

    public boolean reached(IslandHost host, String step) {
        Map<String, Long> map = milestones.get(host.key());
        return map != null && map.containsKey(step);
    }

    /** How many islands reached each step, with the median time to get there. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        int total = milestones.size();
        out.add("§6Island funnel §8(" + total + " islands)");
        for (String step : STEPS) {
            List<Long> times = new ArrayList<>();
            for (Map<String, Long> map : milestones.values()) {
                Long at = map.get(step);
                if (at != null) {
                    times.add(at - map.getOrDefault("start", at));
                }
            }
            times.sort(Long::compare);
            String median = times.isEmpty() ? "-" : duration(times.get(times.size() / 2));
            int pct = total == 0 ? 0 : times.size() * 100 / total;
            out.add("§7" + step.replace('_', ' ') + ": §f" + times.size() + " §8(" + pct + "%) §7median §f" + median);
        }
        return out;
    }

    private static String name(IslandHost host) {
        if (host.isGuild()) {
            return "guild";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(host.id());
        return player.getName() == null ? "?" : player.getName();
    }

    static String duration(long ms) {
        long s = ms / 1000L;
        if (s < 60) {
            return s + "s";
        }
        long m = s / 60;
        if (m < 60) {
            return m + "m " + (s % 60) + "s";
        }
        long h = m / 60;
        return h < 48 ? h + "h " + (m % 60) + "m" : (h / 24) + "d " + (h % 24) + "h";
    }

    // ------------------------------------------------------------------------------------------------
    // saving (into the island's own file)
    // ------------------------------------------------------------------------------------------------

    public void forget(IslandHost host) {
        unlocked.remove(host.key());
        milestones.remove(host.key());
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        Set<String> keys = new HashSet<>(unlocked.keySet());
        keys.addAll(milestones.keySet());
        for (String key : keys) {
            ConfigurationSection section = files.replace(key, "progress");
            section.set("unlocked", new ArrayList<>(unlocked.getOrDefault(key, Set.of())));
            ConfigurationSection steps = section.createSection("milestones");
            for (Map.Entry<String, Long> entry : milestones.getOrDefault(key, Map.of()).entrySet()) {
                steps.set(entry.getKey(), entry.getValue());
            }
        }
        files.flush();
        dirty = false;
    }
}
