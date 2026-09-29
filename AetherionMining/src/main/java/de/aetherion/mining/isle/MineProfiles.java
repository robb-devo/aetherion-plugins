package de.aetherion.mining.isle;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.mining.AetherionMining;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Mining Eldervale progress per player ({@code mine-isle-players.yml}): discovered districts,
 * ore mastery, Crystal Finds and the specimen cabinet, claimed collection milestones, Foreman
 * contracts, the Hearth ration, this Veins cycle's tally — plus the isle-wide specimen records.
 * Saved on a dirty timer, on quit and on disable.
 */
public final class MineProfiles {

    public static final int CONTRACT_SLOTS = 3;

    public static final class Profile {
        final Set<String> districts = new LinkedHashSet<>();
        boolean surveyor;
        final Map<IsleOre, Long> mined = new EnumMap<>(IsleOre.class);
        final Map<IsleOre, Integer> tiers = new EnumMap<>(IsleOre.class);
        int crystals;
        double bestCarat;
        IsleOre bestOre;
        Grade bestGrade;
        /** "ore:grade" of every specimen ever grabbed. */
        final Set<String> cabinet = new LinkedHashSet<>();
        /** Highest claimed Collection milestone (1-based) per ore; 0 = none claimed. */
        final Map<IsleOre, Integer> claimed = new EnumMap<>(IsleOre.class);
        final Set<String> cabinetRewards = new LinkedHashSet<>();
        final ForemanContracts.Contract[] contracts = new ForemanContracts.Contract[CONTRACT_SLOTS];
        final long[] slotReadyAt = new long[CONTRACT_SLOTS];
        final int[] progress = new int[CONTRACT_SLOTS];
        long freeRerollAt;
        int contractsDone;
        String ration;
        long rationUntil;
        int veinsGeneration = -1;
        long veinsMined;
        long rubbleCleared;
        long trollsFelled;
        /** Lowest block Y ever mined on the isle (Integer.MAX_VALUE = never below the surface). */
        int deepest = Integer.MAX_VALUE;
        final Set<String> bands = new LinkedHashSet<>();
        long forgeRep;
        final Map<String, Integer> marks = new java.util.LinkedHashMap<>();
        int forged;
        long streakDay = -1L;
        int streak;
        int bestStreak;
        int dayBlocks;
        long blocksDay = -1L;
        final Map<String, Integer> felled = new java.util.LinkedHashMap<>();
        double resonanceBest;
        int geodeHearts;
        long shards;
        String tracking;
        boolean compassOff;
        final Set<String> met = new LinkedHashSet<>();
        long canaryUntil;
        long gloamUntil;

        public Set<String> districts() {
            return districts;
        }

        public boolean surveyor() {
            return surveyor;
        }

        public long mined(IsleOre ore) {
            return mined.getOrDefault(ore, 0L);
        }

        public int tier(IsleOre ore) {
            return tiers.getOrDefault(ore, 0);
        }

        public int crystals() {
            return crystals;
        }

        public double bestCarat() {
            return bestCarat;
        }

        public IsleOre bestOre() {
            return bestOre;
        }

        public Grade bestGrade() {
            return bestGrade;
        }

        public boolean hasSpecimen(IsleOre ore, Grade grade) {
            return cabinet.contains(ore.id() + ":" + grade.id());
        }

        public int claimed(IsleOre ore) {
            return claimed.getOrDefault(ore, 0);
        }

        public int contractsDone() {
            return contractsDone;
        }

        public long veinsMined() {
            return veinsMined;
        }

        public long rubbleCleared() {
            return rubbleCleared;
        }

        public long trollsFelled() {
            return trollsFelled;
        }

        public int deepest() {
            return deepest;
        }

        public Set<String> bands() {
            return bands;
        }

        public long forgeRep() {
            return forgeRep;
        }

        public int mark(String id) {
            return marks.getOrDefault(id, 0);
        }

        public int forged() {
            return forged;
        }

        public int streak() {
            return streak;
        }

        public int bestStreak() {
            return bestStreak;
        }

        public int felled(String kind) {
            return felled.getOrDefault(kind, 0);
        }

        public int felledTotal() {
            int total = 0;
            for (int value : felled.values()) {
                total += value;
            }
            return total;
        }

        public double resonanceBest() {
            return resonanceBest;
        }

        public int geodeHearts() {
            return geodeHearts;
        }

        public long shards() {
            return shards;
        }

        public String tracking() {
            return tracking;
        }

        public boolean compassOff() {
            return compassOff;
        }

        public boolean met(MineRole role) {
            return met.contains(role.id());
        }
    }

    /** Heaviest specimen ever grabbed per ore family — the Assay Hall's bragging board. */
    public record SpecimenRecord(UUID holder, String name, double carats, Grade grade) {
    }

    private final AetherionMining plugin;
    private final File file;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<IsleOre, SpecimenRecord> records = new EnumMap<>(IsleOre.class);
    private YamlConfiguration disk;
    private boolean dirty;
    private BukkitTask saveTask;

    public MineProfiles(AetherionMining plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mine-isle-players.yml");
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

    /** Profiles currently in memory (online players and recent visitors). */
    public Map<UUID, Profile> loaded() {
        return profiles;
    }

    /** Every stored player id (for offline leaderboards). */
    public Set<UUID> known() {
        Set<UUID> out = new java.util.HashSet<>(profiles.keySet());
        ConfigurationSection players = disk.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    out.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    public Map<IsleOre, SpecimenRecord> records() {
        return records;
    }

    /** True when {@code carats} beats the isle record for {@code ore} (and stores it). */
    public boolean offerRecord(IsleOre ore, Player player, double carats, Grade grade) {
        SpecimenRecord current = records.get(ore);
        if (current != null && current.carats() >= carats) {
            return false;
        }
        records.put(ore, new SpecimenRecord(player.getUniqueId(), player.getName(), carats, grade));
        dirty = true;
        return true;
    }

    /** Wipe one player's Mining Eldervale progress (DEV). */
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
        records.forEach((ore, record) -> {
            String path = "records." + ore.id();
            disk.set(path + ".uuid", record.holder().toString());
            disk.set(path + ".name", record.name());
            disk.set(path + ".carats", record.carats());
            disk.set(path + ".grade", record.grade() == null ? null : record.grade().id());
        });
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            AtomicYaml.save(disk, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save mine-isle-players.yml", exception);
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
        profile.districts.addAll(section.getStringList("districts"));
        profile.surveyor = section.getBoolean("surveyor", false);
        readOreLongs(section.getConfigurationSection("mined"), profile.mined);
        readOreInts(section.getConfigurationSection("tiers"), profile.tiers);
        profile.crystals = section.getInt("crystals", 0);
        profile.bestCarat = section.getDouble("best-carat", 0.0d);
        profile.bestOre = IsleOre.byId(section.getString("best-ore"));
        profile.bestGrade = Grade.byId(section.getString("best-grade"));
        profile.cabinet.addAll(section.getStringList("cabinet"));
        readOreInts(section.getConfigurationSection("claimed"), profile.claimed);
        profile.cabinetRewards.addAll(section.getStringList("cabinet-rewards"));
        for (int slot = 0; slot < CONTRACT_SLOTS; slot++) {
            profile.contracts[slot] = ForemanContracts.Contract.read(section.getConfigurationSection("contracts." + slot));
            profile.slotReadyAt[slot] = section.getLong("slot-ready." + slot, 0L);
            profile.progress[slot] = section.getInt("progress." + slot, 0);
        }
        profile.freeRerollAt = section.getLong("free-reroll-at", 0L);
        profile.contractsDone = section.getInt("contracts-done", 0);
        profile.ration = section.getString("ration");
        profile.rationUntil = section.getLong("ration-until", 0L);
        profile.veinsGeneration = section.getInt("veins-generation", -1);
        profile.veinsMined = section.getLong("veins-mined", 0L);
        profile.rubbleCleared = section.getLong("rubble-cleared", 0L);
        profile.trollsFelled = section.getLong("trolls-felled", 0L);
        profile.deepest = section.getInt("deepest", Integer.MAX_VALUE);
        profile.bands.addAll(section.getStringList("bands"));
        profile.forgeRep = section.getLong("forge-rep", 0L);
        readStringInts(section.getConfigurationSection("marks"), profile.marks);
        profile.forged = section.getInt("forged", 0);
        profile.streakDay = section.getLong("streak-day", -1L);
        profile.streak = section.getInt("streak", 0);
        profile.bestStreak = section.getInt("best-streak", 0);
        profile.dayBlocks = section.getInt("day-blocks", 0);
        profile.blocksDay = section.getLong("blocks-day", -1L);
        readStringInts(section.getConfigurationSection("felled"), profile.felled);
        profile.resonanceBest = section.getDouble("resonance-best", 0.0d);
        profile.geodeHearts = section.getInt("geode-hearts", 0);
        profile.shards = section.getLong("shards", 0L);
        profile.tracking = section.getString("tracking");
        profile.compassOff = section.getBoolean("compass-off", false);
        profile.met.addAll(section.getStringList("met"));
        profile.canaryUntil = section.getLong("canary-until", 0L);
        profile.gloamUntil = section.getLong("gloam-until", 0L);
        return profile;
    }

    private void write(UUID id, Profile profile) {
        String path = "players." + id;
        disk.set(path, null);
        ConfigurationSection section = disk.createSection(path);
        section.set("districts", List.copyOf(profile.districts));
        section.set("surveyor", profile.surveyor);
        profile.mined.forEach((ore, count) -> section.set("mined." + ore.id(), count));
        profile.tiers.forEach((ore, tier) -> section.set("tiers." + ore.id(), tier));
        section.set("crystals", profile.crystals);
        section.set("best-carat", profile.bestCarat);
        section.set("best-ore", profile.bestOre == null ? null : profile.bestOre.id());
        section.set("best-grade", profile.bestGrade == null ? null : profile.bestGrade.id());
        section.set("cabinet", List.copyOf(profile.cabinet));
        profile.claimed.forEach((ore, index) -> section.set("claimed." + ore.id(), index));
        section.set("cabinet-rewards", List.copyOf(profile.cabinetRewards));
        for (int slot = 0; slot < CONTRACT_SLOTS; slot++) {
            ForemanContracts.Contract contract = profile.contracts[slot];
            if (contract != null) {
                contract.write(section.createSection("contracts." + slot));
            }
            if (profile.slotReadyAt[slot] > 0L) {
                section.set("slot-ready." + slot, profile.slotReadyAt[slot]);
            }
            if (profile.progress[slot] > 0) {
                section.set("progress." + slot, profile.progress[slot]);
            }
        }
        section.set("free-reroll-at", profile.freeRerollAt);
        section.set("contracts-done", profile.contractsDone);
        section.set("ration", profile.ration);
        section.set("ration-until", profile.rationUntil);
        section.set("veins-generation", profile.veinsGeneration);
        section.set("veins-mined", profile.veinsMined);
        section.set("rubble-cleared", profile.rubbleCleared);
        section.set("trolls-felled", profile.trollsFelled);
        if (profile.deepest != Integer.MAX_VALUE) {
            section.set("deepest", profile.deepest);
        }
        section.set("bands", List.copyOf(profile.bands));
        section.set("forge-rep", profile.forgeRep);
        profile.marks.forEach((mark, level) -> section.set("marks." + mark, level));
        section.set("forged", profile.forged);
        section.set("streak-day", profile.streakDay);
        section.set("streak", profile.streak);
        section.set("best-streak", profile.bestStreak);
        section.set("day-blocks", profile.dayBlocks);
        section.set("blocks-day", profile.blocksDay);
        profile.felled.forEach((kind, count) -> section.set("felled." + kind, count));
        section.set("resonance-best", profile.resonanceBest);
        section.set("geode-hearts", profile.geodeHearts);
        section.set("shards", profile.shards);
        section.set("tracking", profile.tracking);
        section.set("compass-off", profile.compassOff);
        section.set("met", List.copyOf(profile.met));
        section.set("canary-until", profile.canaryUntil);
        section.set("gloam-until", profile.gloamUntil);
    }

    private static void readStringInts(ConfigurationSection section, Map<String, Integer> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            into.put(key, section.getInt(key));
        }
    }

    private static void readOreLongs(ConfigurationSection section, Map<IsleOre, Long> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            IsleOre ore = IsleOre.byId(key);
            if (ore != null) {
                into.put(ore, section.getLong(key));
            }
        }
    }

    private static void readOreInts(ConfigurationSection section, Map<IsleOre, Integer> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            IsleOre ore = IsleOre.byId(key);
            if (ore != null) {
                into.put(ore, section.getInt(key));
            }
        }
    }

    private void loadRecords() {
        ConfigurationSection section = disk.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            IsleOre ore = IsleOre.byId(key);
            String uuid = section.getString(key + ".uuid");
            if (ore == null || uuid == null) {
                continue;
            }
            try {
                records.put(ore, new SpecimenRecord(UUID.fromString(uuid), section.getString(key + ".name", "?"),
                        section.getDouble(key + ".carats"), Grade.byId(section.getString(key + ".grade"))));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
