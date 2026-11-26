package de.aetherion.pit.brawl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sky Brawl stats and cosmetics per player, in plugins/AetherionPit/brawl.yml. */
public final class BrawlStore {

    public static final class Stats {
        public String name = "?";
        public int knockoffs;
        public int falls;
        public int best;
        public int shards;
        public String effect = BrawlEffect.PUFF.name();
        public final Set<String> owned = new LinkedHashSet<>(List.of(BrawlEffect.PUFF.name()));

        public BrawlEffect effect() {
            try {
                return BrawlEffect.valueOf(effect);
            } catch (IllegalArgumentException ex) {
                return BrawlEffect.PUFF;
            }
        }
    }

    public record Ranked(String name, int knockoffs) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private boolean dirty;

    public BrawlStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "brawl.yml");
        load();
    }

    public Stats of(Player player) {
        Stats s = stats.computeIfAbsent(player.getUniqueId(), id -> new Stats());
        s.name = player.getName();
        return s;
    }

    public void touch() {
        dirty = true;
    }

    public List<Ranked> top(int n) {
        List<Ranked> out = new ArrayList<>();
        for (Stats s : stats.values()) {
            if (s.knockoffs > 0) {
                out.add(new Ranked(s.name, s.knockoffs));
            }
        }
        out.sort((a, b) -> Integer.compare(b.knockoffs(), a.knockoffs()));
        return out.size() > n ? List.copyOf(out.subList(0, n)) : out;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = root.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                Stats s = new Stats();
                s.name = c.getString("name", "?");
                s.knockoffs = c.getInt("knockoffs");
                s.falls = c.getInt("falls");
                s.best = c.getInt("best");
                s.shards = c.getInt("shards");
                s.effect = c.getString("effect", BrawlEffect.PUFF.name());
                s.owned.addAll(c.getStringList("owned"));
                stats.put(id, s);
            } catch (IllegalArgumentException ignored) {
                // not a uuid
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : stats.entrySet()) {
            String k = "players." + e.getKey();
            Stats s = e.getValue();
            y.set(k + ".name", s.name);
            y.set(k + ".knockoffs", s.knockoffs);
            y.set(k + ".falls", s.falls);
            y.set(k + ".best", s.best);
            y.set(k + ".shards", s.shards);
            y.set(k + ".effect", s.effect);
            y.set(k + ".owned", new ArrayList<>(s.owned));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            de.aetherion.core.persist.AtomicYaml.save(y, file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save brawl.yml: " + ex.getMessage());
        }
    }
}
