package de.aetherion.core.network;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Where a hop should end, beside the character: {@code shared/transfer/routes/<uuid>.yml}.
 *
 * <p>The character snapshot only carries the player. What they asked for ({@code /capital} typed on the
 * isles, {@code /island home} typed in the capital) travels here, so the arriving backend can finish it as
 * if it had been typed there. One route per player; a newer hop replaces it; stale routes expire.
 * The route never moves items — losing one only means landing at the default spot.
 */
public final class RouteBook {

    public enum Kind {
        /** Land wherever the backend lands people. */
        NONE,
        /** Replay this command (no slash) for the player once their character is in place. */
        COMMAND,
        /** Silent teleport to a hub spawn id when it is unlocked (no chat). */
        WARP
    }

    public record Route(String id, UUID player, String to, String from, Kind kind, String value, long createdAt) {
        public boolean addressedTo(String here) {
            return to != null && here != null && to.equalsIgnoreCase(here.trim());
        }

        public boolean fresh(long ttlMs) {
            return System.currentTimeMillis() - createdAt <= ttlMs;
        }

        /** First word of a COMMAND route, lower case, namespace stripped. */
        public String label() {
            if (kind != Kind.COMMAND || value == null || value.isBlank()) {
                return "";
            }
            String first = value.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            int colon = first.indexOf(':');
            return colon >= 0 ? first.substring(colon + 1) : first;
        }

        /** Words after the label. */
        public String[] args() {
            if (kind != Kind.COMMAND || value == null || value.isBlank()) {
                return new String[0];
            }
            String[] parts = value.trim().split("\\s+");
            String[] out = new String[Math.max(0, parts.length - 1)];
            System.arraycopy(parts, 1, out, 0, out.length);
            return out;
        }
    }

    private final Plugin plugin;
    private final File dir;
    private final long ttlMs;

    public RouteBook(Plugin plugin, File transferDir) {
        this.plugin = plugin;
        this.dir = new File(transferDir, "routes");
        this.ttlMs = Math.max(15L, plugin.getConfig().getLong("network.route-ttl-seconds", 120L)) * 1000L;
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Route] Could not create " + dir.getAbsolutePath());
        }
    }

    public long ttlMs() {
        return ttlMs;
    }

    public Route put(Player player, String to, String from, Kind kind, String value) {
        if (player == null || to == null || to.isBlank()) {
            return null;
        }
        Route route = new Route(
                UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                player.getUniqueId(),
                to.trim().toLowerCase(Locale.ROOT),
                from == null ? "" : from.trim().toLowerCase(Locale.ROOT),
                kind == null ? Kind.NONE : kind,
                value == null ? "" : value.trim(),
                System.currentTimeMillis());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", route.id());
        yaml.set("uuid", route.player().toString());
        yaml.set("name", player.getName());
        yaml.set("to", route.to());
        yaml.set("from", route.from());
        yaml.set("kind", route.kind().name());
        yaml.set("value", route.value());
        yaml.set("created", route.createdAt());
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, fileFor(route.player()), plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Route] Could not write route for " + player.getName(), ex);
            return null;
        }
        return route;
    }

    /** The pending route, or null (also null when unreadable). Does not check freshness or address. */
    public Route peek(UUID id) {
        if (id == null) {
            return null;
        }
        File file = fileFor(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String routeId = yaml.getString("id", "");
        if (routeId == null || routeId.isBlank()) {
            return null;
        }
        Kind kind;
        try {
            kind = Kind.valueOf(yaml.getString("kind", "NONE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            kind = Kind.NONE;
        }
        return new Route(routeId, id, yaml.getString("to", ""), yaml.getString("from", ""), kind,
                yaml.getString("value", ""), yaml.getLong("created", 0L));
    }

    /** Pending, fresh and meant for {@code here}; otherwise null. */
    public Route pendingFor(UUID id, String here) {
        Route route = peek(id);
        if (route == null || !route.addressedTo(here) || !route.fresh(ttlMs)) {
            return null;
        }
        return route;
    }

    /** Remove the route only if it is still the one we read (a newer hop may have replaced it). */
    public void drop(UUID id, String routeId) {
        if (id == null || routeId == null) {
            return;
        }
        Route current = peek(id);
        if (current == null || !routeId.equals(current.id())) {
            return;
        }
        File file = fileFor(id);
        if (file.isFile() && !file.delete()) {
            plugin.getLogger().warning("[Route] Could not delete " + file.getAbsolutePath());
        }
    }

    /** Remove routes past their TTL (any backend may sweep; async-safe). */
    public int purgeExpired() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        int removed = 0;
        long cutoff = System.currentTimeMillis() - ttlMs * 3L;
        for (File file : files) {
            if (file.lastModified() < cutoff && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    private File fileFor(UUID id) {
        return new File(dir, id + ".yml");
    }
}
