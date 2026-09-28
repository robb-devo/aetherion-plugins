package de.aetherion.farming.isle;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.farming.AetherionFarming;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Eldervale progress per player ({@code isle-players.yml}): discovered plots, crop mastery,
 * prize finds, harvest orders, bakehouse food — plus the isle-wide heaviest-prize records.
 * Saved on a dirty timer, on quit and on disable.
 */
public final class IsleProfiles {

    public static final int ORDER_SLOTS = 3;

    public static final class Profile {
        final Set<String> plots = new LinkedHashSet<>();
        boolean cartographer;
        final Map<IsleCrop, Long> harvested = new EnumMap<>(IsleCrop.class);
        final Map<IsleCrop, Integer> tiers = new EnumMap<>(IsleCrop.class);
        int prizes;
        double bestKg;
        IsleCrop bestCrop;
        final HarvestOrders.Order[] orders = new HarvestOrders.Order[ORDER_SLOTS];
        final long[] slotReadyAt = new long[ORDER_SLOTS];
        long freeRerollAt;
        int ordersDone;
        String food;
        long foodUntil;

        public Set<String> plots() {
            return plots;
        }

        public long harvested(IsleCrop crop) {
            return harvested.getOrDefault(crop, 0L);
        }

        public int tier(IsleCrop crop) {
            return tiers.getOrDefault(crop, 0);
        }

        public int prizes() {
            return prizes;
        }

        public double bestKg() {
            return bestKg;
        }

        public IsleCrop bestCrop() {
            return bestCrop;
        }

        public int ordersDone() {
            return ordersDone;
        }
    }

    /** Heaviest prize ever grabbed per crop — the isle's bragging board. */
    public record PrizeRecord(UUID holder, String name, double kg) {
    }

    private final AetherionFarming plugin;
    private final File file;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<IsleCrop, PrizeRecord> records = new EnumMap<>(IsleCrop.class);
    private YamlConfiguration disk;
    private boolean dirty;
    private BukkitTask saveTask;

    public IsleProfiles(AetherionFarming plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "isle-players.yml");
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        disk = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        loadRecords();
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 120L, 20L * 120L);
    }

    public Profile of(Player player) {
        return of(player.getUniqueId());
    }

    public Profile of(UUID id) {
        return profiles.computeIfAbsent(id, this::load);
    }

    public void markDirty() {
        dirty = true;
    }

    public Map<IsleCrop, PrizeRecord> records() {
        return records;
    }

    /** True when {@code kg} beats the isle record for {@code crop} (and stores it). */
    public boolean offerRecord(IsleCrop crop, Player player, double kg) {
        PrizeRecord current = records.get(crop);
        if (current != null && current.kg() >= kg) {
            return false;
        }
        records.put(crop, new PrizeRecord(player.getUniqueId(), player.getName(), kg));
        dirty = true;
        return true;
    }

    /** Wipe one player's Eldervale progress (DEV). */
    public void reset(UUID id) {
        profiles.put(id, new Profile());
        disk.set("players." + id, null);
        dirty = true;
    }

    public void unload(UUID id) {
        Profile profile = profiles.get(id);
        if (profile != null) {
            write(id, profile);
            profiles.remove(id);
            dirty = true;
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        profiles.forEach(this::write);
        disk.set("records", null);
        records.forEach((crop, record) -> {
            String path = "records." + crop.id();
            disk.set(path + ".uuid", record.holder().toString());
            disk.set(path + ".name", record.name());
            disk.set(path + ".kg", record.kg());
        });
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            AtomicYaml.save(disk, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save isle-players.yml", exception);
        }
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        save();
    }

    private Profile load(UUID id) {
        Profile profile = new Profile();
        ConfigurationSection section = disk.getConfigurationSection("players." + id);
        if (section == null) {
            return profile;
        }
        profile.plots.addAll(section.getStringList("plots"));
        profile.cartographer = section.getBoolean("cartographer", false);
        ConfigurationSection harvested = section.getConfigurationSection("harvested");
        if (harvested != null) {
            for (String key : harvested.getKeys(false)) {
                IsleCrop crop = IsleCrop.byId(key);
                if (crop != null) {
                    profile.harvested.put(crop, harvested.getLong(key));
                }
            }
        }
        ConfigurationSection tiers = section.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                IsleCrop crop = IsleCrop.byId(key);
                if (crop != null) {
                    profile.tiers.put(crop, tiers.getInt(key));
                }
            }
        }
        profile.prizes = section.getInt("prizes", 0);
        profile.bestKg = section.getDouble("best-kg", 0.0d);
        profile.bestCrop = IsleCrop.byId(section.getString("best-crop"));
        for (int slot = 0; slot < ORDER_SLOTS; slot++) {
            profile.orders[slot] = HarvestOrders.Order.read(section.getConfigurationSection("orders." + slot));
            profile.slotReadyAt[slot] = section.getLong("slot-ready." + slot, 0L);
        }
        profile.freeRerollAt = section.getLong("free-reroll-at", 0L);
        profile.ordersDone = section.getInt("orders-done", 0);
        profile.food = section.getString("food");
        profile.foodUntil = section.getLong("food-until", 0L);
        return profile;
    }

    private void write(UUID id, Profile profile) {
        String path = "players." + id;
        disk.set(path, null);
        ConfigurationSection section = disk.createSection(path);
        section.set("plots", java.util.List.copyOf(profile.plots));
        section.set("cartographer", profile.cartographer);
        profile.harvested.forEach((crop, count) -> section.set("harvested." + crop.id(), count));
        profile.tiers.forEach((crop, tier) -> section.set("tiers." + crop.id(), tier));
        section.set("prizes", profile.prizes);
        section.set("best-kg", profile.bestKg);
        section.set("best-crop", profile.bestCrop == null ? null : profile.bestCrop.id());
        for (int slot = 0; slot < ORDER_SLOTS; slot++) {
            HarvestOrders.Order order = profile.orders[slot];
            if (order != null) {
                order.write(section.createSection("orders." + slot));
            }
            if (profile.slotReadyAt[slot] > 0L) {
                section.set("slot-ready." + slot, profile.slotReadyAt[slot]);
            }
        }
        section.set("free-reroll-at", profile.freeRerollAt);
        section.set("orders-done", profile.ordersDone);
        section.set("food", profile.food);
        section.set("food-until", profile.foodUntil);
    }

    private void loadRecords() {
        ConfigurationSection section = disk.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            IsleCrop crop = IsleCrop.byId(key);
            String uuid = section.getString(key + ".uuid");
            if (crop == null || uuid == null) {
                continue;
            }
            try {
                records.put(crop, new PrizeRecord(UUID.fromString(uuid),
                        section.getString(key + ".name", "?"), section.getDouble(key + ".kg")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
