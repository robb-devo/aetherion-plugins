package de.aetherion.hub.service;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.data.PlayerHubData;
import de.aetherion.hub.data.PlayerHubStorage;
import de.aetherion.hub.model.HubSpawn;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HubService {

    /** Only these teleports exist on the Origin map. Everything else is purged. */
    public static final java.util.Set<String> ORIGIN_SPAWN_IDS = java.util.Set.of(
            "harbour",
            "ore_ridge",
            "mines",
            "capital",
            "forage_isle",
            "farm",
            "farm_isle",
            "borderlands",
            "colosseum",
            "eldervale",
            "amethyst"
    );

    private static final String[] RETIRED_SPAWN_IDS = {
            "veil", "ruins", "spawn", "lurker_camp",
            "royal_palace", "ticket_hall", "trash_chute",
            "guild_quarry", "worm_tunnels", "collections"
    };

    private final AetherionHub plugin;
    private final PlayerHubStorage storage;
    private final Map<String, HubSpawn> spawns = new LinkedHashMap<>();

    public HubService(AetherionHub plugin, PlayerHubStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        reload();
    }

    public static boolean isOriginSpawn(String id) {
        return id != null && ORIGIN_SPAWN_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    public void reload() {
        plugin.reloadConfig();
        spawns.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("spawns");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            String key = id.toLowerCase(Locale.ROOT);
            if (!isOriginSpawn(key)) {
                continue;
            }
            spawns.put(key, HubSpawn.fromConfig(key, section.getConfigurationSection(id)));
        }
    }

    public void ensureDefaultSpawns() {
        boolean changed = false;
        changed |= ensureSpawn("harbour", "Harbour", "Anker Harbour — Egon, market, and the docks.", "OAK_BOAT", 10);
        changed |= ensureSpawn("ore_ridge", "Ore Ridge", "Coal hill near the harbour. Quartermaster's first errand.", "IRON_ORE", 11);
        changed |= ensureSpawn("mines", "Mines", "Shabby Mine mouth. Shaft Foreman runs the first shift.", "IRON_PICKAXE", 12);
        changed |= ensureSpawn("capital", "Capital", "City hub — Skills, Ledger, and the road onward.", "BELL", 13);
        changed |= ensureSpawn("forage_isle", "Forage Isle", "Wooded chop loops. Find the lumberjack.", "OAK_LEAVES", 14);
        changed |= ensureSpawn("farm", "Farm", "Fields and Clucksworth. Walk in to unlock.", "HAY_BLOCK", 15);
        changed |= ensureSpawn("farm_isle", "Farm Isle", "Shared fields + Millstone pantry. Farming 10 portal.", "WHEAT", 16);
        changed |= ensureSpawn("borderlands", "Borderlands", "Beyond Vex's gate. Hostile wastes.", "COARSE_DIRT", 21);
        changed |= ensureSpawn("colosseum", "Colosseum", "Proctor's ring — Crypt vials, T2 bosses. Unlocks with the Proctor.", "SANDSTONE", 19);
        changed |= ensureSpawn("eldervale", "Eldervale", "Mining island past the slime jump pad. Walk in to unlock.", "DEEPSLATE_DIAMOND_ORE", 20);
        changed |= ensureSpawn("amethyst", "Amethyst Mines", "Crystal Guide on Eldervale. Mining 30. /amethyst.", "AMETHYST_CLUSTER", 22);

        if (plugin.getConfig().getConfigurationSection("spawns.harbour") != null
                && !plugin.getConfig().getBoolean("spawns.harbour.unlocked-by-default", false)) {
            plugin.getConfig().set("spawns.harbour.unlocked-by-default", true);
            changed = true;
        }
        // Organic walk-in unlocks (title popup via SpawnDiscoverListener).
        changed |= ensureDiscoverRadius("ore_ridge", 40);
        changed |= ensureDiscoverRadius("capital", 48);
        changed |= ensureDiscoverRadius("farm", 28);
        changed |= ensureDiscoverRadius("borderlands", 36);
        changed |= ensureDiscoverRadius("eldervale", 36);
        // Colosseum: no walk-in discover — soft gate until Proctor unlocks it.

        if (changed) {
            plugin.saveConfig();
            reload();
        }
        stampDiscoverRadii();
        repairSpawnLayout();
    }

    public void repairSpawnLayout() {
        boolean changed = retireSpawns();
        changed |= applyLayout("harbour", "Harbour", "Anker Harbour — Egon, market, and the docks.", "OAK_BOAT", 10, true);
        changed |= applyLayout("ore_ridge", "Ore Ridge", "Coal hill near the harbour. Quartermaster's first errand. Walk in to unlock.", "IRON_ORE", 11, false);
        changed |= applyLayout("mines", "Mines", "Shabby Mine mouth. Shaft Foreman runs the first shift.", "IRON_PICKAXE", 12, false);
        changed |= applyLayout("capital", "Capital", "City hub — Skills, Ledger, and the road onward. Walk in to unlock.", "BELL", 13, false);
        changed |= applyLayout("forage_isle", "Forage Isle", "Wooded chop loops. Find the lumberjack.", "OAK_LEAVES", 14, false);
        changed |= applyLayout("farm", "Farm", "Fields and Clucksworth. Walk in to unlock.", "HAY_BLOCK", 15, false);
        changed |= applyLayout("farm_isle", "Farm Isle", "Shared fields + Millstone pantry. Farming 10 portal.", "WHEAT", 16, false);
        changed |= applyLayout("borderlands", "Borderlands", "Beyond Vex's gate. Hostile wastes. Walk in to unlock.", "COARSE_DIRT", 21, false);
        changed |= applyLayout("colosseum", "Colosseum", "Proctor's ring — Crypt vials, T2 bosses. Unlocks with the Proctor.", "SANDSTONE", 19, false);
        changed |= applyLayout("eldervale", "Eldervale", "Mining island past the slime jump pad. Walk in to unlock.", "DEEPSLATE_DIAMOND_ORE", 20, false);
        changed |= applyLayout("amethyst", "Amethyst Mines", "Crystal Guide on Eldervale. Mining 30. /amethyst.", "AMETHYST_CLUSTER", 22, false);
        changed |= ensureDiscoverRadius("ore_ridge", 40);
        changed |= ensureDiscoverRadius("capital", 48);
        changed |= ensureDiscoverRadius("farm", 28);
        changed |= ensureDiscoverRadius("borderlands", 36);
        changed |= ensureDiscoverRadius("eldervale", 36);
        if (changed) {
            plugin.saveConfig();
            reload();
        }
        stampDiscoverRadii();
    }

    private boolean ensureDiscoverRadius(String id, double radius) {
        String path = "spawns." + id + ".discover-radius";
        if (plugin.getConfig().contains(path)) {
            return false;
        }
        plugin.getConfig().set(path, radius);
        return true;
    }

    /** Keep discover-radius on HubSpawn so /hubadmin set does not wipe it via saveSpawns. */
    private void stampDiscoverRadii() {
        stampDiscover("ore_ridge", 40);
        stampDiscover("capital", 48);
        stampDiscover("farm", 28);
        stampDiscover("borderlands", 36);
        stampDiscover("eldervale", 36);
    }

    private void stampDiscover(String id, double fallback) {
        HubSpawn spawn = spawn(id);
        if (spawn == null) {
            return;
        }
        String path = "spawns." + id + ".discover-radius";
        double radius = plugin.getConfig().contains(path)
                ? plugin.getConfig().getDouble(path, fallback)
                : fallback;
        spawn.setDiscoverRadius(radius);
    }

    private boolean retireSpawns() {
        boolean changed = false;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("spawns");
        if (section != null) {
            for (String id : List.copyOf(section.getKeys(false))) {
                String key = id.toLowerCase(Locale.ROOT);
                if (isOriginSpawn(key)) {
                    continue;
                }
                plugin.getConfig().set("spawns." + id, null);
                spawns.remove(key);
                changed = true;
            }
        }
        for (String id : RETIRED_SPAWN_IDS) {
            if (plugin.getConfig().isConfigurationSection("spawns." + id)
                    || plugin.getConfig().contains("spawns." + id)
                    || spawns.containsKey(id)) {
                plugin.getConfig().set("spawns." + id, null);
                spawns.remove(id);
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyLayout(
            String id,
            String displayName,
            String description,
            String icon,
            int slot,
            boolean unlockedByDefault
    ) {
        String path = "spawns." + id;
        if (plugin.getConfig().getConfigurationSection(path) == null) {
            plugin.getConfig().set(path + ".unlocked-by-default", unlockedByDefault);
        }
        boolean changed = false;
        if (!displayName.equals(plugin.getConfig().getString(path + ".display-name"))) {
            plugin.getConfig().set(path + ".display-name", displayName);
            changed = true;
        }
        if (!description.equals(plugin.getConfig().getString(path + ".description", ""))) {
            plugin.getConfig().set(path + ".description", description);
            changed = true;
        }
        if (!icon.equalsIgnoreCase(plugin.getConfig().getString(path + ".icon", ""))) {
            plugin.getConfig().set(path + ".icon", icon);
            changed = true;
        }
        if (plugin.getConfig().getInt(path + ".slot", -1) != slot) {
            plugin.getConfig().set(path + ".slot", slot);
            changed = true;
        }
        if (plugin.getConfig().getBoolean(path + ".unlocked-by-default") != unlockedByDefault) {
            // Harbour stays the only default unlock.
            if ("harbour".equals(id) || unlockedByDefault) {
                plugin.getConfig().set(path + ".unlocked-by-default", unlockedByDefault);
                changed = true;
            }
        }
        return changed;
    }

    public boolean ensureSpawn(String id, String displayName, String description, String icon, int slot) {
        if (plugin.getConfig().getConfigurationSection("spawns." + id) != null) {
            return false;
        }
        plugin.getConfig().set("spawns." + id + ".display-name", displayName);
        plugin.getConfig().set("spawns." + id + ".description", description);
        plugin.getConfig().set("spawns." + id + ".icon", icon);
        plugin.getConfig().set("spawns." + id + ".slot", slot);
        plugin.getConfig().set("spawns." + id + ".unlocked-by-default", false);
        return true;
    }

    public Collection<HubSpawn> spawns() {
        return spawns.values();
    }

    public HubSpawn spawn(String id) {
        if (id == null) {
            return null;
        }
        return spawns.get(id.toLowerCase(Locale.ROOT));
    }

    public HubSpawn ensureId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!isOriginSpawn(key)) {
            return null;
        }
        HubSpawn existing = spawn(key);
        if (existing != null) {
            return existing;
        }
        ensureSpawn(key, pretty(key), "Unlockable spawn.", "CAMPFIRE", nextSlot());
        HubSpawn created = HubSpawn.fromConfig(key, plugin.getConfig().getConfigurationSection("spawns." + key));
        if (created == null) {
            created = new HubSpawn(key);
            created.setDisplayName(pretty(key));
            created.setDescription("Unlockable spawn.");
            created.setIcon(Material.CAMPFIRE);
            created.setSlot(nextSlot());
        }
        spawns.put(key, created);
        plugin.saveConfig();
        return created;
    }

    public HubSpawn defaultSpawn() {
        HubSpawn harbour = spawn("harbour");
        if (harbour != null && harbour.unlockedByDefault()) {
            return harbour;
        }
        for (HubSpawn spawn : spawns.values()) {
            if (spawn.unlockedByDefault()) {
                return spawn;
            }
        }
        return spawns.values().stream().findFirst().orElse(null);
    }

    public PlayerHubData data(UUID uuid) {
        PlayerHubData data = storage.load(uuid);
        boolean changed = false;

        for (HubSpawn spawn : spawns.values()) {
            if (spawn.unlockedByDefault() && data.unlocked().add(spawn.id())) {
                changed = true;
            }
        }

        HubSpawn selected = spawn(data.selectedId());
        if (selected == null || !data.isUnlocked(selected.id())) {
            HubSpawn fallback = firstUnlocked(data);
            data.setSelectedId(fallback == null ? null : fallback.id());
            changed = true;
        }

        if (changed) {
            storage.save(data);
        }

        return data;
    }

    public void unload(UUID uuid) {
        storage.unload(uuid);
    }

    public HubSpawn selected(Player player) {
        return spawn(data(player.getUniqueId()).selectedId());
    }

    public boolean isUnlocked(Player player, String spawnId) {
        HubSpawn spawn = spawn(spawnId);
        return spawn != null && data(player.getUniqueId()).isUnlocked(spawn.id());
    }

    /**
     * Ensures the spawn is unlocked. Returns true if the spawn id exists
     * (whether newly added or already owned).
     */
    public boolean unlock(UUID uuid, String spawnId) {
        HubSpawn spawn = spawn(spawnId);
        if (spawn == null) {
            return false;
        }

        PlayerHubData data = data(uuid);
        if (data.unlocked().add(spawn.id())) {
            storage.save(data);
        }
        return true;
    }

    /** @return true only when this call newly unlocked the spawn */
    public boolean unlockNew(UUID uuid, String spawnId) {
        HubSpawn spawn = spawn(spawnId);
        if (spawn == null) {
            return false;
        }
        PlayerHubData data = data(uuid);
        if (!data.unlocked().add(spawn.id())) {
            return false;
        }
        storage.save(data);
        return true;
    }

    /**
     * Unlock and, on first unlock, play the new-area title + command hint.
     *
     * @return true only when this call newly unlocked the spawn
     */
    public boolean unlockAndAnnounce(Player player, String spawnId) {
        if (player == null) {
            return false;
        }
        if (!unlockNew(player.getUniqueId(), spawnId)) {
            return false;
        }
        HubSpawn spawn = spawn(spawnId);
        if (spawn != null) {
            announceUnlock(player, spawn);
        }
        return true;
    }

    public void announceUnlock(Player player, HubSpawn spawn) {
        if (player == null || spawn == null) {
            return;
        }
        String name = spawn.displayName();
        String command = de.aetherion.hub.command.SpawnGotoCommand.shortcutCommand(spawn.id());
        String hint = command == null ? "Manager → Teleports" : "/" + command;
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.15f);
        player.showTitle(Title.title(
                LegacyComponentSerializer.legacySection().deserialize(
                        "§b§lNEW AREA · " + name.toUpperCase(Locale.ROOT)
                ),
                LegacyComponentSerializer.legacySection().deserialize(
                        "§7teleport unlocked · " + hint
                ),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(500))
        ));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§b✦ §f" + name + " §7· " + hint
        ));
        player.sendMessage("§b✦ §eNew area: §f" + name + "§e.");
        player.sendMessage("§7Teleport unlocked — §e" + hint + "§7.");
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.unlock(player, "SPAWN_UNLOCKER", "Teleports", hint);
        }
    }

    public int unlockAll(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        PlayerHubData data = data(uuid);
        int added = 0;
        for (HubSpawn spawn : spawns.values()) {
            if (data.unlocked().add(spawn.id())) {
                added++;
            }
        }
        if (added > 0) {
            storage.save(data);
        }
        return added;
    }

    public boolean lock(UUID uuid, String spawnId) {
        HubSpawn spawn = spawn(spawnId);
        if (spawn == null) {
            return false;
        }

        PlayerHubData data = data(uuid);
        data.unlocked().remove(spawn.id());

        if (spawn.id().equals(data.selectedId())) {
            HubSpawn fallback = firstUnlocked(data);
            data.setSelectedId(fallback == null ? null : fallback.id());
        }

        storage.save(data);
        return true;
    }

    public boolean select(Player player, String spawnId) {
        HubSpawn spawn = spawn(spawnId);
        if (spawn == null || !isUnlocked(player, spawn.id())) {
            return false;
        }

        PlayerHubData data = data(player.getUniqueId());
        data.setSelectedId(spawn.id());
        storage.save(data);
        return true;
    }

    public boolean teleport(Player player, HubSpawn spawn) {
        if (spawn == null) {
            player.sendMessage(plugin.getConfig().getString("messages.no-spawn", "§cNo spawn is set."));
            return false;
        }

        if (!isUnlocked(player, spawn.id()) && !player.hasPermission("aetherionhub.admin")) {
            player.sendMessage(format("messages.locked", spawn));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        Location location = resolveLocation(spawn);
        if (location == null) {
            player.sendMessage(format("messages.missing-location", spawn));
            return false;
        }

        player.teleport(location);
        player.setFallDistance(0f);
        player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
        player.sendMessage(format("messages.teleported", spawn));
        return true;
    }

    public boolean teleportSelected(Player player) {
        return teleport(player, selected(player));
    }

    public HubSpawn setLocation(String id, Location location, boolean createIfMissing) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (!isOriginSpawn(key)) {
            return null;
        }
        HubSpawn spawn = spawns.get(key);

        if (spawn == null) {
            if (!createIfMissing) {
                return null;
            }
            spawn = new HubSpawn(key);
            spawn.setDisplayName(pretty(key));
            spawn.setDescription("Unlockable spawn.");
            spawn.setIcon(Material.BEACON);
            spawn.setSlot(nextSlot());
            spawn.setUnlockedByDefault(spawns.isEmpty());
            spawns.put(key, spawn);
        }

        spawn.setLocation(location);
        saveSpawns();
        return spawn;
    }

    public void saveSpawns() {
        plugin.getConfig().set("spawns", null);
        for (HubSpawn spawn : spawns.values()) {
            spawn.writeTo(plugin.getConfig().createSection("spawns." + spawn.id()));
        }
        plugin.saveConfig();
    }

    public void markHintShown(UUID uuid) {
        PlayerHubData data = data(uuid);
        if (data.hintShown()) {
            return;
        }
        data.setHintShown(true);
        storage.save(data);
    }

    public boolean hintShown(UUID uuid) {
        return data(uuid).hintShown();
    }

    public Location resolveLocation(HubSpawn spawn) {
        if (spawn == null) {
            return null;
        }
        if ("amethyst".equalsIgnoreCase(spawn.id())) {
            de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
            if (mining != null) {
                Location veins = mining.veinsHubLocation();
                if (veins != null) {
                    return veins;
                }
            }
        }
        Location location = spawn.toLocation();
        if (location != null) {
            return location;
        }
        if (spawn.unlockedByDefault() && !plugin.getServer().getWorlds().isEmpty()) {
            return plugin.getServer().getWorlds().get(0).getSpawnLocation();
        }
        return null;
    }

    public String format(String path, HubSpawn spawn) {
        String message = plugin.getConfig().getString(path, "");
        return message
                .replace("{name}", spawn.displayName())
                .replace("{id}", spawn.id());
    }

    public void saveAll() {
        storage.saveAll();
    }

    private HubSpawn firstUnlocked(PlayerHubData data) {
        for (HubSpawn spawn : spawns.values()) {
            if (data.isUnlocked(spawn.id())) {
                return spawn;
            }
        }
        return null;
    }

    private int nextSlot() {
        boolean[] used = new boolean[27];
        for (HubSpawn spawn : spawns.values()) {
            if (spawn.slot() >= 0 && spawn.slot() < used.length) {
                used[spawn.slot()] = true;
            }
        }
        for (int slot : new int[]{11, 13, 15, 10, 12, 14, 16, 19, 20, 21, 22, 23, 24, 25}) {
            if (!used[slot]) {
                return slot;
            }
        }
        return 13;
    }

    private static String pretty(String id) {
        String[] parts = id.split("[_\\-]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? id : builder.toString();
    }
}
