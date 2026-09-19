package de.aetherion.items.shop;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Playtime-only XP flask. Real-world AFK in bed does not count;
 * only ticks while the player is online.
 */
public final class XpBoosterService implements Listener, Runnable {

    public static final double MULTIPLIER = 1.05d;
    public static final double DAMAGE_MULTIPLIER = 1.04d;
    public static final double DEFENSE_MULTIPLIER = 1.03d;
    public static final double COIN_MULTIPLIER = 1.05d;
    public static final long DURATION_MS = 12L * 60L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Long> remaining = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> leftover = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public XpBoosterService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "xp-boosts.yml");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public boolean active(Player player) {
        return player != null && active(player.getUniqueId());
    }

    public boolean active(UUID playerId) {
        return playerId != null && remaining.getOrDefault(playerId, 0L) > 0L;
    }

    public long remainingMs(Player player) {
        return player == null ? 0L : remaining.getOrDefault(player.getUniqueId(), 0L);
    }

    public double multiplier(Player player) {
        return active(player) ? MULTIPLIER : 1.0d;
    }

    public double damageMultiplier(Player player) {
        return active(player) ? DAMAGE_MULTIPLIER : 1.0d;
    }

    public double defenseMultiplier(Player player) {
        return active(player) ? DEFENSE_MULTIPLIER : 1.0d;
    }

    public double coinMultiplier(Player player) {
        return active(player) ? COIN_MULTIPLIER : 1.0d;
    }

    public static String buffSummary() {
        return "§d+5% XP§7, §d+4% damage§7, §d+3% defense§7, §d+5% coins";
    }

    public int scale(Player player, int amount) {
        if (amount <= 0) {
            return amount;
        }
        double factor = multiplier(player);
        if (factor <= 1.0d) {
            return amount;
        }
        UUID id = player.getUniqueId();
        double total = amount * factor + leftover.getOrDefault(id, 0.0d);
        int grant = (int) Math.floor(total);
        leftover.put(id, total - grant);
        dirty = true;
        return Math.max(amount, grant);
    }

    public void addPlaytime(Player player, long millis) {
        if (player == null || millis <= 0L) {
            return;
        }
        remaining.merge(player.getUniqueId(), millis, Long::sum);
        dirty = true;
    }

    public String formatted(Player player) {
        return format(remainingMs(player));
    }

    public static String format(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours <= 0L) {
            return minutes + "m playtime";
        }
        return hours + "h " + minutes + "m playtime";
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            long left = remaining.getOrDefault(id, 0L);
            if (left <= 0L) {
                continue;
            }
            long next = left - 1000L;
            if (next <= 0L) {
                remaining.remove(id);
                leftover.remove(id);
                player.sendMessage("§5The phial runs dry. XP is ordinary again.");
            } else {
                remaining.put(id, next);
                de.aetherion.items.shop.AetherBloodVial.bless(player);
            }
            dirty = true;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active(event.getPlayer())) {
            de.aetherion.items.shop.AetherBloodVial.bless(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveIfDirty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onVanillaXp(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        if (amount <= 0 || !active(event.getPlayer())) {
            return;
        }
        event.setAmount(scale(event.getPlayer(), amount));
    }

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        remaining.forEach((id, amount) -> {
            if (amount > 0L) {
                config.set("players." + id + ".ms", amount);
                config.set("players." + id + ".leftover", leftover.getOrDefault(id, 0.0d));
            }
        });
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException ignored) {
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null || !file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        String key = playerId.toString();
        if (section == null || !section.contains(key)) {
            remaining.remove(playerId);
            leftover.remove(playerId);
            return;
        }
        long ms = section.getLong(key + ".ms");
        if (ms > 0L) {
            remaining.put(playerId, ms);
            leftover.put(playerId, section.getDouble(key + ".leftover"));
        } else {
            remaining.remove(playerId);
            leftover.remove(playerId);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                long ms = section.getLong(key + ".ms");
                if (ms > 0L) {
                    remaining.put(id, ms);
                    leftover.put(id, section.getDouble(key + ".leftover"));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
