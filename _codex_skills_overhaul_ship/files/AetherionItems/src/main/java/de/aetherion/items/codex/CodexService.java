package de.aetherion.items.codex;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

/**
 * The Codex ledger: Collection ({@code blocks}), Bestiary ({@code kills} + {@code bosses}) and
 * everything built on top of it — Borderlands variants, claimed reward tiers, first-discovery dates.
 *
 * <p>{@code codex.yml} stays backward compatible: the three original maps are untouched, the new
 * ones sit beside them ({@code variants}, {@code claims}, {@code found}). Unknown ids are kept.
 */
public final class CodexService {

    public record Rank(int place, String name, long amount) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerCodex> players = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public CodexService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "codex.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    // ------------------------------------------------------------------ counting

    public void addKill(Player player, String id) {
        addKill(player, id, null);
    }

    /** One kill of {@code id}; {@code variant} ({@code sturdy}/{@code brute}/{@code crypt}) is tallied beside it. */
    public void addKill(Player player, String id, String variant) {
        if (player == null || id == null) {
            return;
        }
        // Unknown dungeon mobs are kept (the floor designers add new ones); anything else must be listed.
        if (CodexCatalog.mob(id) == null && !id.startsWith("dungeon:")) {
            return;
        }
        PlayerCodex data = data(player);
        long before = data.kills.getOrDefault(id, 0L);
        data.kills.merge(id, 1L, Long::sum);
        if (variant != null && !variant.isBlank()) {
            data.variants.merge(id + "@" + variant.toLowerCase(Locale.ROOT), 1L, Long::sum);
        }
        touched(player, data, "b:" + id, before, before + 1L);
    }

    public void addBossKill(Player player, String bossId) {
        if (player == null || bossId == null || bossId.isBlank()) {
            return;
        }
        PlayerCodex data = data(player);
        String key = bossId.toLowerCase(Locale.ROOT);
        long before = bossCount(data, key);
        data.bosses.merge(key, 1L, Long::sum);
        touched(player, data, "b:boss:" + key, before, before + 1L);
    }

    public void addBlock(Player player, String id) {
        addBlock(player, id, 1L);
    }

    public void addBlock(Player player, String id, long amount) {
        if (player == null || id == null || amount <= 0L || CodexCatalog.block(id) == null) {
            return;
        }
        PlayerCodex data = data(player);
        long before = data.blocks.getOrDefault(id, 0L);
        data.blocks.merge(id, amount, Long::sum);
        touched(player, data, "c:" + id, before, before + amount);
    }

    /** A block harvested without a break event (Vein Siphon vacuum, …). */
    public void noteHarvest(Player player, Material material) {
        String id = CodexCatalog.resolveBlock(material);
        if (id != null) {
            addBlock(player, id);
        }
    }

    private void touched(Player player, PlayerCodex data, String key, long before, long after) {
        data.version++;
        dirty = true;
        if (before <= 0L && after > 0L) {
            data.found.putIfAbsent(key, LocalDate.now().toEpochDay());
        }
        CodexNotices.progress(player, key, before, after);
    }

    // ------------------------------------------------------------------ reading

    public long kills(Player player, String id) {
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.kills.getOrDefault(id, 0L);
    }

    public long bossKills(Player player, String bossId) {
        if (player == null || bossId == null) {
            return 0L;
        }
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.bosses.getOrDefault(bossId.toLowerCase(Locale.ROOT), 0L);
    }

    public long blocks(Player player, String id) {
        PlayerCodex data = players.get(player.getUniqueId());
        return data == null ? 0L : data.blocks.getOrDefault(id, 0L);
    }

    public long variant(Player player, String id, String variant) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null ? 0L : data.variants.getOrDefault(id + "@" + variant, 0L);
    }

    /**
     * Count behind a claim key: {@code c:<collection>}, {@code b:<mob>}, {@code b:boss:<template>}.
     * Boss counts fold in any stray {@code dungeon:<template>} kills (floor bosses without a BossEngine id).
     */
    public long count(Player player, String key) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null ? 0L : count(data, key);
    }

    private static long count(PlayerCodex data, String key) {
        if (key == null) {
            return 0L;
        }
        if (key.startsWith("c:")) {
            return data.blocks.getOrDefault(key.substring(2), 0L);
        }
        if (key.startsWith("b:boss:")) {
            return bossCount(data, key.substring(7));
        }
        if (key.startsWith("b:")) {
            return data.kills.getOrDefault(key.substring(2), 0L);
        }
        return 0L;
    }

    private static long bossCount(PlayerCodex data, String template) {
        return data.bosses.getOrDefault(template, 0L) + data.kills.getOrDefault("dungeon:" + template, 0L);
    }

    /** Highest tier already paid out for a claim key (0 = none). */
    public int claimed(Player player, String key) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null || key == null ? 0 : data.claims.getOrDefault(key, 0);
    }

    public void setClaimed(Player player, String key, int tier) {
        if (player == null || key == null) {
            return;
        }
        PlayerCodex data = data(player);
        if (tier <= 0) {
            data.claims.remove(key);
        } else {
            data.claims.put(key, tier);
        }
        data.version++;
        dirty = true;
    }

    /** Epoch day the entry was first counted, or -1 (older data from before the overhaul). */
    public long foundDay(Player player, String key) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null ? -1L : data.found.getOrDefault(key, -1L);
    }

    /** Every raw kill id a player has on file (includes dungeon mobs the catalog doesn't list). */
    public java.util.Set<String> killIds(Player player) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null ? java.util.Set.of() : java.util.Set.copyOf(data.kills.keySet());
    }

    /** Bumps on every change of a player's ledger — cheap cache key for level math. */
    public long version(Player player) {
        PlayerCodex data = player == null ? null : players.get(player.getUniqueId());
        return data == null ? 0L : data.version;
    }

    public boolean hasAnyKill(Player player) {
        return hasAny(player, true, false);
    }

    public boolean hasAnyBlock(Player player) {
        return hasAny(player, false, false);
    }

    public boolean hasAnyBossKill(Player player) {
        return hasAny(player, false, true);
    }

    private boolean hasAny(Player player, boolean kills, boolean bosses) {
        if (player == null) {
            return false;
        }
        PlayerCodex data = players.get(player.getUniqueId());
        if (data == null) {
            return false;
        }
        if (bosses) {
            return data.bosses.values().stream().anyMatch(amount -> amount > 0);
        }
        return (kills ? data.kills : data.blocks).values().stream().anyMatch(amount -> amount > 0);
    }

    public List<Rank> topKills(String id) {
        return top(id, true);
    }

    public List<Rank> topBlocks(String id) {
        return top(id, false);
    }

    /** Top {@code limit} for any claim key (see {@link #count(Player, String)}). */
    public List<Rank> top(String key, int limit) {
        return rank(data -> count(data, key), limit);
    }

    /** Where {@code player} stands for a claim key (1-based), or 0 with nothing counted. */
    public int place(Player player, String key) {
        PlayerCodex mine = player == null ? null : players.get(player.getUniqueId());
        long own = mine == null ? 0L : count(mine, key);
        if (own <= 0L) {
            return 0;
        }
        int place = 1;
        for (PlayerCodex data : players.values()) {
            if (data != mine && count(data, key) > own) {
                place++;
            }
        }
        return place;
    }

    public int trackedPlayers() {
        return players.size();
    }

    private List<Rank> rank(ToLongFunction<PlayerCodex> amountOf, int limit) {
        List<PlayerCodex> ranked = new ArrayList<>();
        for (PlayerCodex data : players.values()) {
            if (amountOf.applyAsLong(data) > 0L) {
                ranked.add(data);
            }
        }
        ranked.sort(Comparator
                .comparingLong(amountOf)
                .reversed()
                .thenComparing(data -> data.name, String.CASE_INSENSITIVE_ORDER));
        List<Rank> result = new ArrayList<>();
        int place = 1;
        for (PlayerCodex data : ranked) {
            if (place > limit) {
                break;
            }
            result.add(new Rank(place, data.name, amountOf.applyAsLong(data)));
            place++;
        }
        return result;
    }

    // ------------------------------------------------------------------ persistence

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        for (PlayerCodex data : players.values()) {
            String path = "players." + data.id;
            config.set(path, null);
            config.set(path + ".name", data.name);
            writeLongs(config, path + ".kills", data.kills);
            writeLongs(config, path + ".blocks", data.blocks);
            writeLongs(config, path + ".bosses", data.bosses);
            writeLongs(config, path + ".variants", data.variants);
            for (Map.Entry<String, Integer> entry : data.claims.entrySet()) {
                config.set(path + ".claims." + yamlKey(entry.getKey()), entry.getValue());
            }
            writeLongs(config, path + ".found", data.found);
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save codex.yml: " + exception.getMessage());
        }
    }

    private static void writeLongs(YamlConfiguration config, String path, Map<String, Long> map) {
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            config.set(path + "." + yamlKey(entry.getKey()), entry.getValue());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private List<Rank> top(String id, boolean kills) {
        return rank(data -> kills ? data.kills.getOrDefault(id, 0L) : data.blocks.getOrDefault(id, 0L), 3);
    }

    private PlayerCodex data(Player player) {
        PlayerCodex data = players.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerCodex(uuid, publicLabel(player)));
        data.name = publicLabel(player);
        return data;
    }

    /** Prefer in-game nickname (testbots, TAB) so Collection/Bestiary leaderboards read nicely. */
    private static String publicLabel(Player player) {
        try {
            net.kyori.adventure.text.Component component = player.displayName();
            if (component != null) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(component)
                        .trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        } catch (RuntimeException ignored) {
        }
        String legacy = player.getDisplayName();
        if (legacy != null && !legacy.isBlank()) {
            return legacy.replaceAll("§.", "").trim();
        }
        return player.getName();
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null || !file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        ConfigurationSection section = root.getConfigurationSection(playerId.toString());
        if (section == null) {
            players.remove(playerId);
            return;
        }
        players.put(playerId, read(playerId, section));
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            players.put(uuid, read(uuid, section));
        }
    }

    private PlayerCodex read(UUID uuid, ConfigurationSection section) {
        PlayerCodex data = new PlayerCodex(uuid, section.getString("name", "Unknown"));
        readMap(section.getConfigurationSection("kills"), data.kills, true);
        readMap(section.getConfigurationSection("blocks"), data.blocks, false);
        readBosses(section.getConfigurationSection("bosses"), data.bosses);
        readPlain(section.getConfigurationSection("variants"), data.variants);
        readPlain(section.getConfigurationSection("found"), data.found);
        ConfigurationSection claims = section.getConfigurationSection("claims");
        if (claims != null) {
            for (String key : claims.getKeys(false)) {
                data.claims.put(fromYamlKey(key), Math.max(0, claims.getInt(key)));
            }
        }
        return data;
    }

    private void readBosses(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(fromYamlKey(key).toLowerCase(Locale.ROOT), section.getLong(key));
        }
    }

    private static void readPlain(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(fromYamlKey(key), section.getLong(key));
        }
    }

    private void readMap(ConfigurationSection section, Map<String, Long> target, boolean kills) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String id = fromYamlKey(key);
            if (kills && CodexCatalog.mob(id) == null && CodexCatalog.mob(key) != null) {
                id = key;
            }
            if (!kills && CodexCatalog.block(id) == null && CodexCatalog.block(key) != null) {
                id = key;
            }
            target.put(id, section.getLong(key));
        }
    }

    private static String yamlKey(String id) {
        return id.replace(":", "__");
    }

    private static String fromYamlKey(String key) {
        return key.replace("__", ":");
    }

    private static final class PlayerCodex {
        private final UUID id;
        private volatile String name;
        private final Map<String, Long> kills = new ConcurrentHashMap<>();
        private final Map<String, Long> blocks = new ConcurrentHashMap<>();
        private final Map<String, Long> bosses = new ConcurrentHashMap<>();
        /** {@code <mobId>@<variant>} → kills of that Borderlands variant. */
        private final Map<String, Long> variants = new ConcurrentHashMap<>();
        /** Claim key → highest tier paid out. */
        private final Map<String, Integer> claims = new ConcurrentHashMap<>();
        /** Claim key → epoch day of the first count. */
        private final Map<String, Long> found = new ConcurrentHashMap<>();
        private volatile long version;

        private PlayerCodex(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }


    /** Drop bestiary / collection / journal for one player. */
    public void wipePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        players.remove(playerId);
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        config.set("players." + playerId, null);
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not wipe codex for " + playerId + ": " + exception.getMessage());
            dirty = true;
        }
    }

    /** DEV: wipe only the claimed reward tiers (counts stay) so the claim loop can be replayed. */
    public void resetClaims(UUID playerId) {
        PlayerCodex data = playerId == null ? null : players.get(playerId);
        if (data != null) {
            data.claims.clear();
            data.version++;
            dirty = true;
        }
    }

    /** DEV: set a raw count for a claim key (no notices, no rewards). */
    public void setCount(Player player, String key, long amount) {
        if (player == null || key == null) {
            return;
        }
        PlayerCodex data = data(player);
        long value = Math.max(0L, amount);
        if (key.startsWith("c:")) {
            data.blocks.put(key.substring(2), value);
        } else if (key.startsWith("b:boss:")) {
            data.bosses.put(key.substring(7), value);
        } else if (key.startsWith("b:")) {
            data.kills.put(key.substring(2), value);
        } else {
            return;
        }
        if (value > 0L) {
            data.found.putIfAbsent(key, LocalDate.now().toEpochDay());
        }
        data.version++;
        dirty = true;
    }
}
