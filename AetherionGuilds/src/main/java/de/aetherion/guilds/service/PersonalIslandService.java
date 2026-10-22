package de.aetherion.guilds.service;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.guilds.island.IslandFiles;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.LandService;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.model.IslandBiome;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.Template;
import de.aetherion.guilds.template.TemplateLibrary;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;
import de.aetherion.guilds.world.IslandBuilder;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PersonalIslandService {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PersonalIsland> islands = new ConcurrentHashMap<>();
    private final Set<UUID> islandFlight = ConcurrentHashMap.newKeySet();
    private World world;
    private MinionService minions;
    // island highlight: authored starters, land parcels, unlock state
    private PasteService paste;
    private TemplateLibrary templates;
    private Consumer<YamlConfiguration> extraSave;
    private Consumer<PersonalIsland> removalHook;
    private final Map<UUID, List<Runnable>> waiting = new ConcurrentHashMap<>();
    /** Plot of every personal island that has factory data in islands/p_<uuid>.yml (structures, belts). */
    private final Map<UUID, Integer> filePlots = new ConcurrentHashMap<>();
    /** Per owner: plot -> structures standing there, from the factory save (admin recovery). */
    private final Map<UUID, Map<Integer, Integer>> fileVotes = new ConcurrentHashMap<>();
    /** personal_islands.yml existed but could not be read: never save over it (that would erase every island). */
    private boolean loadFailed;
    private boolean restoredUnsaved;
    /**
     * Starter id of a record restored from its factory save: the island is someone's own build (often an authored
     * starter), so it never gets the classic pad stamped on by a tier upgrade.
     */
    public static final String RESTORED = "restored";
    private String backedUp = "";

    public PersonalIslandService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "personal_islands.yml");
    }

    public void enable() {
        this.world = loadWorld();
        load();
    }

    public void attachMinions(MinionService minions) {
        this.minions = minions;
    }

    public void attachHighlight(PasteService paste, TemplateLibrary templates) {
        this.paste = paste;
        this.templates = templates;
    }

    /**
     * Reads which plot each island's factory save points at, then gives an island back to every owner whose
     * record is missing from personal_islands.yml while their island still stands on its plot. Without this, a
     * lost record made /island hand out a fresh claim (a starter pasted on a new or the same plot) and the void
     * scrubber treated the old island as nobody's land.
     */
    public void attachFiles(IslandFiles files) {
        filePlots.clear();
        fileVotes.clear();
        if (files == null) {
            return;
        }
        for (String key : files.keys()) {
            IslandHost host = IslandHost.parse(key);
            YamlConfiguration doc = files.existing(key);
            if (host == null || !key.startsWith("p:") || doc == null) {
                continue;
            }
            Map<Integer, Integer> votes = plotVotes(doc);
            if (!votes.isEmpty()) {
                fileVotes.put(host.id(), votes);
                filePlots.put(host.id(), votes.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey());
            }
        }
        recoverOrphans();
    }

    private Map<Integer, Integer> plotVotes(YamlConfiguration doc) {
        ConfigurationSection structures = doc.getConfigurationSection("structures");
        if (structures == null) {
            return Map.of();
        }
        Map<Integer, Integer> votes = new HashMap<>();
        for (String id : structures.getKeys(false)) {
            ConfigurationSection c = structures.getConfigurationSection(id);
            if (c == null || !c.contains("x")) {
                continue;
            }
            String w = c.getString("world", "");
            if (world != null && !w.isBlank() && !w.equals(world.getName())) {
                continue;
            }
            int plot = Math.max(0, (int) Math.round(c.getInt("x") / (double) spacing()));
            votes.merge(plot, 1, Integer::sum);
        }
        return votes;
    }

    /** Some island's factory save has structures on this plot (whether or not a record points at it). */
    public boolean hasSavedIsland(int plot) {
        return filePlots.containsValue(plot);
    }

    /** Admin recovery: plots this owner's factory save has structures on (plot -> count). */
    public Map<Integer, Integer> savedPlots(UUID owner) {
        return fileVotes.getOrDefault(owner, Map.of());
    }

    /**
     * Admin recovery: points the owner's record at {@code plot} (built, nothing pasted). Creates the record when it
     * is missing. Refuses a plot another island is recorded on.
     */
    public String movePlot(UUID owner, int plot) {
        PersonalIsland other = byPlot(plot);
        if (other != null && !other.ownerId().equals(owner)) {
            return "§cPlot " + plot + " belongs to " + other.ownerId() + ".";
        }
        PersonalIsland island = islands.get(owner);
        if (island == null) {
            island = new PersonalIsland(owner, plot, IslandBiome.PLAINS);
            island.parcels().addAll(LandService.initialParcels());
            islands.put(owner, island);
        }
        int from = island.plot();
        island.setPlot(plot);
        island.setIslandBuilt(true);
        save();
        plugin.getLogger().warning("Admin moved the island record of " + owner + " from plot " + from + " to plot " + plot + ".");
        return "§aIsland record of §f" + owner + " §anow points at plot §f" + plot + " §7(was " + from
                + (plotHasBlocks(plot) ? "" : "; §cthat plot looks empty") + "§7).";
    }

    /** Owners with a factory save and blocks on its plot but no record: their record comes back (built, no paste). */
    private void recoverOrphans() {
        if (loadFailed) {
            // Every record looks missing when the file could not be read: restore nothing, touch nothing.
            return;
        }
        int restored = 0;
        for (Map.Entry<UUID, Integer> entry : filePlots.entrySet()) {
            UUID owner = entry.getKey();
            int plot = entry.getValue();
            if (islands.containsKey(owner) || byPlot(plot) != null || !plotHasBlocks(plot)) {
                continue;
            }
            PersonalIsland island = new PersonalIsland(owner, plot, IslandBiome.PLAINS);
            island.setIslandBuilt(true);
            island.setStarter(RESTORED);
            island.parcels().addAll(LandService.initialParcels());
            islands.put(owner, island);
            restored++;
            plugin.getLogger().severe("Personal island of " + owner + " had no record in personal_islands.yml but still"
                    + " stands on plot " + plot + " (factory save islands/p_" + owner + ".yml): record restored, nothing"
                    + " pasted. Bought land beyond the first 3x3 parcels has to be re-granted by hand.");
        }
        if (restored > 0) {
            // Saved once the unlock state is attached (setExtraSave), or that save would drop the unlock.* sections.
            restoredUnsaved = true;
        }
    }

    /** True when the plot already has an island: blocks at or around its origin (chunks are loaded for the check). */
    public boolean plotHasBlocks(int plot) {
        if (world == null) {
            return false;
        }
        int ox = plot * spacing();
        int min = world.getMinHeight();
        for (int dx = -24; dx <= 24; dx += 12) {
            for (int dz = -24; dz <= 24; dz += 12) {
                int x = ox + dx;
                if (!world.isChunkGenerated(x >> 4, dz >> 4)) {
                    continue;
                }
                if (world.getHighestBlockYAt(x, dz) > min) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A plot nobody may get as a fresh claim: recorded, referenced by a factory save, or not empty. */
    private boolean plotTaken(int plot) {
        return byPlot(plot) != null || filePlots.containsValue(plot) || plotHasBlocks(plot);
    }

    /** Extra top-level sections written into personal_islands.yml (unlock state). */
    public void setExtraSave(Consumer<YamlConfiguration> extraSave) {
        this.extraSave = extraSave;
        if (restoredUnsaved) {
            restoredUnsaved = false;
            save();
        }
    }

    /** Called before an island's data is dropped (wipe): structures, belts. */
    public void setRemovalHook(Consumer<PersonalIsland> removalHook) {
        this.removalHook = removalHook;
    }

    public World world() {
        return world;
    }

    public Collection<PersonalIsland> all() {
        return islands.values();
    }

    public PersonalIsland byOwner(UUID ownerId) {
        return ownerId == null ? null : islands.get(ownerId);
    }

    public PersonalIsland byPlot(int plot) {
        for (PersonalIsland island : islands.values()) {
            if (island.plot() == plot) {
                return island;
            }
        }
        return null;
    }

    public int spacing() {
        return Math.max(256, plugin.getConfig().getInt("personal-island-spacing",
                plugin.getConfig().getInt("island-spacing", 512)));
    }

    public int originX(PersonalIsland island) {
        return island.plot() * spacing();
    }

    public int originZ(PersonalIsland island) {
        return 0;
    }

    public int buildRadius() {
        return Math.max(8, plugin.getConfig().getInt("island-build-radius", 24));
    }

    public int buildRadius(PersonalIsland island) {
        if (island == null) {
            return buildRadius();
        }
        return IslandTiers.buildRadius(buildRadius(), island.islandLevel());
    }

    public boolean isPersonalWorld(World check) {
        return check != null && world != null && check.equals(world);
    }

    public int plotAt(Location location) {
        if (location == null || !isPersonalWorld(location.getWorld())) {
            return -1;
        }
        return Math.max(0, (int) Math.round(location.getX() / (double) spacing()));
    }

    public boolean onOwnIsland(Player player, PersonalIsland island) {
        if (player == null || island == null || !isPersonalWorld(player.getWorld())) {
            return false;
        }
        return plotAt(player.getLocation()) == island.plot();
    }

    public Location spawn(PersonalIsland island) {
        if (world == null || island == null) {
            return null;
        }
        ensureBuilt(island);
        StarterLayout starter = StarterLayout.byId(island.starter());
        if (starter != null) {
            return new Location(world, originX(island) + starter.spawnX() + 0.5, 64 + starter.spawnY(),
                    originZ(island) + starter.spawnZ() + 0.5, starter.yaw(), 0);
        }
        int x = originX(island);
        int z = originZ(island) - 4;
        if ((island.starter() == null || RESTORED.equals(island.starter())) && world.getBlockAt(x, 64, z).getType().isAir()
                && world.getHighestBlockYAt(x, z) > world.getMinHeight()) {
            // restored record without its starter: stand on top of whatever is there instead of the classic pad spot
            return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5, 0, 0);
        }
        return new Location(world, originX(island) + 0.5, 65, originZ(island) - 3.5, 0, 0);
    }

    public void ensureBuilt(PersonalIsland island) {
        ensureBuilt(island, null);
    }

    /**
     * Builds the island if needed. Classic pads build instantly; authored starters paste over a few ticks, so
     * {@code then} runs once the island is really there (right away if it already is).
     */
    public void ensureBuilt(PersonalIsland island, Runnable then) {
        if (island == null || world == null) {
            return;
        }
        if (island.islandBuilt()) {
            if (then != null) {
                then.run();
            }
            return;
        }
        Integer lived = filePlots.get(island.ownerId());
        if (lived != null && lived == island.plot() && plotHasBlocks(island.plot())) {
            // The record lost its island-built flag, but the island has a factory save and stands there: never
            // paste a starter over it.
            plugin.getLogger().warning("Island of " + island.ownerId() + " on plot " + island.plot()
                    + " was marked unbuilt but is there: marked built, nothing pasted.");
            island.setIslandBuilt(true);
            save();
            if (then != null) {
                then.run();
            }
            return;
        }
        StarterLayout starter = StarterLayout.byId(island.starter());
        Template template = starter == null || templates == null ? null : templates.get(starter.templateId());
        if (template == null || paste == null) {
            IslandBuilder.build(world, originX(island), originZ(island), island.islandLevel(), island.biome());
            island.setIslandBuilt(true);
            if (then != null) {
                then.run();
            }
            return;
        }
        List<Runnable> queue = waiting.get(island.ownerId());
        if (queue != null) {
            if (then != null) {
                queue.add(then);
            }
            return;
        }
        queue = new ArrayList<>();
        if (then != null) {
            queue.add(then);
        }
        waiting.put(island.ownerId(), queue);
        paste.paste(template, world, originX(island), 64, originZ(island), 0, PasteService.Mode.SKIP_AIR, true, job -> {
            island.setIslandBuilt(true);
            save();
            List<Runnable> done = waiting.remove(island.ownerId());
            if (done != null) {
                for (Runnable runnable : done) {
                    runnable.run();
                }
            }
        });
    }

    public void whenBuilt(PersonalIsland island, Runnable then) {
        ensureBuilt(island, then);
    }

    /** Claim with an authored starter (the highlight flow). The caller runs the ceremony. */
    public PersonalIsland createStarter(Player player, StarterLayout starter) {
        if (player == null || starter == null) {
            return null;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return null;
        }
        if (byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§cYou already have a personal island.");
            return null;
        }
        PersonalIsland island = new PersonalIsland(player.getUniqueId(), nextPlot(), starter.biome());
        island.setStarter(starter.id());
        island.parcels().addAll(LandService.initialParcels());
        islands.put(island.ownerId(), island);
        save();
        player.sendMessage("§aIsland claimed: §f" + starter.display() + "§a. §7Open §f/island §7anytime.");
        return island;
    }

    public PersonalIsland create(Player player, IslandBiome biome) {
        if (player == null) {
            return null;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return null;
        }
        if (byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§cYou already have a personal island.");
            return null;
        }
        IslandBiome chosen = biome == null ? IslandBiome.PLAINS : biome;
        PersonalIsland island = new PersonalIsland(player.getUniqueId(), nextPlot(), chosen);
        islands.put(island.ownerId(), island);
        ensureBuilt(island);
        save();
        player.sendMessage("§aPersonal island claimed §8(§f" + chosen.display() + "§8)§a.");
        player.sendMessage("§7Open §f/island §7anytime. Friends can visit while the server is online.");
        goHome(player);
        return island;
    }

    public void goHome(Player player) {
        if (player == null) {
            return;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return;
        }
        PersonalIsland island = byOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§7Pick a biome first to create your island.");
            return;
        }
        if (!island.islandBuilt() && StarterLayout.byId(island.starter()) != null) {
            player.sendMessage("§7Your island is still rising…");
            whenBuilt(island, () -> {
                if (player.isOnline()) {
                    goHome(player);
                }
            });
            return;
        }
        Location spawn = spawn(island);
        if (spawn == null) {
            player.sendMessage("§cIsland world is not ready.");
            return;
        }
        player.teleport(spawn);
        player.sendMessage("§aWelcome home.");
        Bukkit.getScheduler().runTask(plugin, () -> refreshFlight(player));
    }

    public void visit(Player visitor, UUID ownerId) {
        if (visitor == null || ownerId == null) {
            return;
        }
        PersonalIsland island = byOwner(ownerId);
        if (island == null) {
            visitor.sendMessage("§cThat player has no personal island yet.");
            return;
        }
        if (!island.islandBuilt() && StarterLayout.byId(island.starter()) != null) {
            visitor.sendMessage("§7That island is still rising… try again in a moment.");
            ensureBuilt(island);
            return;
        }
        Location spawn = spawn(island);
        if (spawn == null) {
            visitor.sendMessage("§cIsland world is not ready.");
            return;
        }
        visitor.teleport(spawn);
        Bukkit.getScheduler().runTask(plugin, () -> refreshFlight(visitor));
    }

    public void refreshFlight(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        PersonalIsland island = byOwner(player.getUniqueId());
        boolean onOwn = island != null && onOwnIsland(player, island);
        if (onOwn) {
            player.setAllowFlight(true);
            islandFlight.add(player.getUniqueId());
            return;
        }
        if (islandFlight.remove(player.getUniqueId())) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public void clearFlight(Player player) {
        if (player == null) {
            return;
        }
        if (!islandFlight.remove(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public void upgradeIsland(Player player) {
        PersonalIsland island = byOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§cYou do not have a personal island.");
            return;
        }
        if (!island.canUpgradeIsland()) {
            player.sendMessage("§cYour island is already max level.");
            return;
        }
        int from = island.islandLevel();
        IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(from);
        if (cost.isEmpty()) {
            player.sendMessage("§cNo upgrade available.");
            return;
        }
        if (AetherionItemsAccess.count(player, "compacted_cobblestone") < cost.compactedCobble()) {
            player.sendMessage("§cNeed §f" + cost.compactedCobble() + " Compacted Cobblestone§c.");
            return;
        }
        if (AetherionItemsAccess.count(player, "quarry_core") < cost.cores()) {
            player.sendMessage("§cNeed §f" + cost.cores() + " Quarry Core§c.");
            return;
        }
        boolean economy = de.aetherion.core.api.AetherServices.coins() != null;
        if (economy && AetherionItemsAccess.coins(player) < cost.coins()) {
            player.sendMessage("§cNeed §6" + GuildFormat.compact(cost.coins()) + " coins§c.");
            return;
        }
        AetherionItemsAccess.take(player, "compacted_cobblestone", cost.compactedCobble());
        AetherionItemsAccess.take(player, "quarry_core", cost.cores());
        if (economy && !AetherionItemsAccess.takeCoins(player, cost.coins())) {
            player.sendMessage("§cCould not take coins.");
            return;
        }
        island.setIslandLevel(from + 1);
        if (StarterLayout.byId(island.starter()) == null && !RESTORED.equals(island.starter())) {
            // classic pads grow their platform; authored starters (and restored islands) grow through bought land
            IslandBuilder.upgrade(world, originX(island), originZ(island), from, island.islandLevel(), island.biome());
        }
        save();
        player.sendTitle("§6✦ Island Tier " + island.islandLevel() + " ✦", "§7Radius §f" + buildRadius(island)
                + " §8· §7more belts & machines", 5, 50, 15);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        de.aetherion.core.api.Vfx.puff(player.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, Math.max(0.5, 0.5), null);
        player.sendMessage("§aIsland Tier §f" + island.islandLevel()
                + "§a. Build radius is now §f" + buildRadius(island) + "§a; more belts and machines unlocked.");
    }

    /**
     * Wipe one personal island to zero: structures/belts/guide via removalHook,
     * world plot cleared, claim slot freed. Owner can claim fresh.
     */
    public boolean wipe(UUID ownerId) {
        if (ownerId == null) {
            return false;
        }
        PersonalIsland island = islands.get(ownerId);
        if (island == null) {
            return false;
        }
        Location hub = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPersonalWorld(player.getWorld())) {
                continue;
            }
            if (plotAt(player.getLocation()) != island.plot()) {
                continue;
            }
            if (hub != null) {
                player.teleport(hub);
            }
            if (player.getUniqueId().equals(ownerId)) {
                player.sendMessage("§7Your island progress was reset. Claim again with §f/island§7.");
            } else {
                player.sendMessage("§7That island was reset.");
            }
        }
        if (minions != null) {
            minions.removePersonalVisuals(island);
        }
        if (removalHook != null) {
            try {
                removalHook.accept(island);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Island wipe hook failed for " + ownerId + ": " + exception.getMessage());
            }
        }
        if (world != null) {
            IslandBuilder.clear(world, originX(island), originZ(island));
        }
        islands.remove(ownerId);
        islandFlight.remove(ownerId);
        waiting.remove(ownerId);
        save();
        plugin.getLogger().info("Wiped personal island for " + ownerId + " (plot " + island.plot() + ").");
        return true;
    }

    public void wipeAll() {
        Location spawn = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPersonalWorld(player.getWorld())) {
                if (spawn != null) {
                    player.teleport(spawn);
                }
                player.sendMessage("§7Personal islands were wiped.");
            }
        }
        if (minions != null) {
            for (PersonalIsland island : List.copyOf(islands.values())) {
                minions.removePersonalVisuals(island);
            }
        }
        if (removalHook != null) {
            for (PersonalIsland island : List.copyOf(islands.values())) {
                removalHook.accept(island);
            }
        }
        for (PersonalIsland island : List.copyOf(islands.values())) {
            IslandBuilder.clear(world, originX(island), originZ(island));
        }
        islands.clear();
        save();
        plugin.getLogger().info("Wiped all personal islands.");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (PersonalIsland island : islands.values()) {
            String path = "islands." + island.ownerId();
            config.set(path + ".plot", island.plot());
            config.set(path + ".biome", island.biome().name());
            config.set(path + ".island-built", island.islandBuilt());
            config.set(path + ".island-level", island.islandLevel());
            if (island.starter() != null) {
                config.set(path + ".starter", island.starter());
            }
            if (!island.parcels().isEmpty()) {
                List<String> parcels = new ArrayList<>();
                for (long key : island.parcels()) {
                    parcels.add(LandService.px(key) + "," + LandService.pz(key));
                }
                config.set(path + ".parcels", parcels);
            }
            for (QuarryMinion minion : island.minions()) {
                String m = path + ".minions." + minion.id();
                config.set(m + ".type", minion.type());
                config.set(m + ".x", minion.x());
                config.set(m + ".y", minion.y());
                config.set(m + ".z", minion.z());
                config.set(m + ".lastTick", minion.lastTick());
                config.set(m + ".stored", minion.stored());
                config.set(m + ".storedCompressed", minion.storedCompressed());
                config.set(m + ".storedCompacted", minion.storedCompacted());
                config.set(m + ".level", minion.level());
                config.set(m + ".processor", minion.processor().name());
            }
        }
        if (extraSave != null) {
            extraSave.accept(config);
        }
        if (loadFailed) {
            plugin.getLogger().severe("personal_islands.yml could not be read at start: not saving over it (that would"
                    + " erase every island record). Fix the file, then restart.");
            return;
        }
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            dailyBackup();
            AtomicYaml.save(config, file, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save personal_islands.yml: " + exception.getMessage());
        }
    }

    /** First save of each day keeps yesterday's file as personal_islands.yml.bak-<date> (the last 7 are kept). */
    private void dailyBackup() {
        String today = LocalDate.now().toString();
        if (today.equals(backedUp) || !file.isFile() || file.length() == 0L) {
            return;
        }
        backedUp = today;
        File folder = file.getParentFile();
        try {
            Files.copy(file.toPath(), new File(folder, file.getName() + ".bak-" + today).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not back up personal_islands.yml: " + exception.getMessage());
            return;
        }
        File[] old = folder.listFiles((dir, name) -> name.startsWith(file.getName() + ".bak-"));
        if (old != null && old.length > 7) {
            java.util.Arrays.sort(old, java.util.Comparator.comparing(File::getName));
            for (int i = 0; i < old.length - 7; i++) {
                if (!old[i].delete()) {
                    plugin.getLogger().fine("old island backup not removed: " + old[i].getName());
                }
            }
        }
    }

    private void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            loadFailed = true;
            plugin.getLogger().severe("personal_islands.yml is unreadable (" + exception.getMessage() + "). Island"
                    + " records are NOT saved until it is fixed; a copy is kept next to it.");
            try {
                Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".unreadable-"
                        + System.currentTimeMillis()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // the original stays where it is anyway
            }
            return;
        }
        ConfigurationSection root = config.getConfigurationSection("islands");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            PersonalIsland island = new PersonalIsland(
                    owner,
                    section.getInt("plot"),
                    IslandBiome.fromId(section.getString("biome"))
            );
            island.setIslandBuilt(section.getBoolean("island-built", false));
            island.setIslandLevel(section.getInt("island-level", 1));
            island.setStarter(section.getString("starter"));
            for (String raw : section.getStringList("parcels")) {
                String[] xz = raw.split(",");
                if (xz.length == 2) {
                    try {
                        island.parcels().add(LandService.key(Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            ConfigurationSection minionsSection = section.getConfigurationSection("minions");
            if (minionsSection != null) {
                for (String minionKey : minionsSection.getKeys(false)) {
                    try {
                        ConfigurationSection minion = minionsSection.getConfigurationSection(minionKey);
                        if (minion == null) {
                            continue;
                        }
                        island.minions().add(new QuarryMinion(
                                UUID.fromString(minionKey),
                                minion.getString("type", QuarryMinion.COBBLE),
                                minion.getInt("x"),
                                minion.getInt("y"),
                                minion.getInt("z"),
                                minion.getLong("lastTick"),
                                minion.getInt("stored"),
                                minion.getInt("level", 1),
                                QuarryMinion.Processor.parse(minion.getString("processor")),
                                minion.getInt("storedCompressed"),
                                minion.getInt("storedCompacted")
                        ));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            islands.put(owner, island);
        }
    }

    private int nextPlot() {
        int max = -1;
        for (PersonalIsland island : islands.values()) {
            max = Math.max(max, island.plot());
        }
        for (int plot : filePlots.values()) {
            max = Math.max(max, plot);
        }
        int plot = max + 1;
        while (plotTaken(plot)) {
            plot++;
        }
        return plot;
    }

    private World loadWorld() {
        String name = plugin.getConfig().getString("personal-island-world", "aether_islands");
        // always on the void generator, also when a world manager / older boot loaded it first (see VoidWorlds)
        return de.aetherion.guilds.world.VoidWorlds.load(plugin, name, this::applyRules);
    }

    private void applyRules(World world) {
        world.setKeepSpawnInMemory(false);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
    }
}
