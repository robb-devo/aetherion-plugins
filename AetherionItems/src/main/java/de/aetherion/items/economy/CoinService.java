package de.aetherion.items.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinService implements de.aetherion.core.api.CoinAccess {

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Long> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lifetime = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public CoinService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "coins.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public long get(Player player) {
        if (player == null) {
            return 0L;
        }
        return balances.getOrDefault(player.getUniqueId(), 0L);
    }

    public String formatted(Player player) {
        return String.format(Locale.US, "%,d", get(player));
    }

    public long get(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        return balances.getOrDefault(playerId, 0L);
    }

    public void add(Player player, long amount) {
        if (player == null) {
            return;
        }
        add(player.getUniqueId(), amount);
    }

    public void add(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return;
        }
        if (plugin instanceof de.aetherion.items.AetherionItems items
                && items.xpBoost() != null
                && items.xpBoost().active(playerId)) {
            amount = Math.max(amount, Math.round(amount * de.aetherion.items.shop.XpBoosterService.COIN_MULTIPLIER));
        }
        balances.merge(playerId, amount, Long::sum);
        lifetime.merge(playerId, amount, Long::sum);
        dirty = true;
    }

    public long lifetime(Player player) {
        return player == null ? 0L : lifetime(player.getUniqueId());
    }

    public long lifetime(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        return Math.max(lifetime.getOrDefault(playerId, 0L), balances.getOrDefault(playerId, 0L));
    }

    public boolean take(Player player, long amount) {
        if (player == null || amount <= 0L) {
            return false;
        }
        long current = get(player);
        if (current < amount) {
            return false;
        }
        balances.put(player.getUniqueId(), current - amount);
        dirty = true;
        return true;
    }

    /** Casino payouts: no XP-boost, no lifetime grind. */
    public void credit(Player player, long amount) {
        if (player == null || amount <= 0L) {
            return;
        }
        balances.merge(player.getUniqueId(), amount, Long::sum);
        dirty = true;
    }

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        balances.forEach((id, amount) -> config.set("players." + id, amount));
        lifetime.forEach((id, amount) -> config.set("lifetime." + id, amount));
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save coins.yml: " + exception.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    @Override
    public void reloadFromDisk() {
        load();
    }

    @Override
    public void applyImported(UUID playerId, Long balance, Long lifetimeEarned) {
        if (playerId == null) {
            return;
        }
        if (balance != null) {
            balances.put(playerId, balance);
        }
        if (lifetimeEarned != null) {
            lifetime.put(playerId, lifetimeEarned);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long amount = section.getLong(key);
                    balances.put(id, amount);
                    lifetime.put(id, amount);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection earned = config.getConfigurationSection("lifetime");
        if (earned == null) {
            return;
        }
        for (String key : earned.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                lifetime.merge(id, earned.getLong(key), Math::max);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
