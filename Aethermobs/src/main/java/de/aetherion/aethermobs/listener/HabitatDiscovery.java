package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.DungeonWorlds;
import de.aetherion.aethermobs.pet.HabitatPresentation;
import de.aetherion.aethermobs.pet.PetBorderlands;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetHabitat;
import de.aetherion.aethermobs.pet.PetSpawnZones;
import de.aetherion.aethermobs.pet.PlayerPetCollection;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pet biotopes become places: the first time a player settles into one they get a
 * short discover beat; later visits only whisper the name on the action bar.
 *
 * <p>Hysteresis: a biotope must be dominant on two consecutive samples (taken only
 * after the player has actually moved) before it counts as "entered", so walking a
 * border never flickers. Hub quiet bubbles, dungeons, Borderlands and no-pet worlds
 * are skipped. Discoveries live in {@code habitat-discoveries.yml}, apart from pet data.
 */
public final class HabitatDiscovery implements Listener, Runnable {

    /** Players are sampled round-robin; each one is looked at about every 2 s. */
    private static final int SAMPLE_EVERY_TICKS = 40;

    /** Skip the block scan until the player has moved this far since the last one. */
    private static final double MOVE_BEFORE_RESCAN_SQ = 5.0 * 5.0;

    /** No discovery beats this close to hub quest NPCs. */
    private static final double HUB_BUFFER = 40.0;

    /** A known biotope's name is whispered again at most this often. */
    private static final long WHISPER_COOLDOWN_MS = 10 * 60_000L;

    private final AetherMobs plugin;
    private final File file;
    private final Map<UUID, Set<PetHabitat>> discovered = new ConcurrentHashMap<>();
    private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int tick;

    public HabitatDiscovery(AetherMobs plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "habitat-discoveries.yml");
        load();
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 60L, 1L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int discoveredCount(UUID playerId) {
        Set<PetHabitat> set = discovered.get(playerId);
        return set == null ? 0 : set.size();
    }

    public boolean hasDiscovered(UUID playerId, PetHabitat habitat) {
        Set<PetHabitat> set = discovered.get(playerId);
        return set != null && set.contains(habitat);
    }

    @Override
    public void run() {
        tick++;
        int slot = tick % SAMPLE_EVERY_TICKS;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Math.floorMod(player.getUniqueId().hashCode(), SAMPLE_EVERY_TICKS) != slot) {
                continue;
            }
            sample(player);
        }
    }

    private void sample(Player player) {
        if (!eligible(player)) {
            return;
        }
        Location at = player.getLocation();
        Track track = tracks.computeIfAbsent(player.getUniqueId(), id -> new Track());
        if (track.lastScan != null
                && track.lastScan.getWorld() == at.getWorld()
                && track.lastScan.distanceSquared(at) < MOVE_BEFORE_RESCAN_SQ) {
            return;
        }
        track.lastScan = at.clone();

        PetHabitat here = PetHabitat.dominantAt(at);
        if (here == PetHabitat.ANY) {
            here = null;
        }

        if (here != track.candidate) {
            track.candidate = here;
            return;
        }
        // Second consistent sample — the player is really here (or really nowhere).
        if (here == track.settled) {
            return;
        }
        track.settled = here;
        if (here != null) {
            arrive(player, here, track);
        }
    }

    private boolean eligible(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        if (DungeonWorlds.blocksWildPets(world) || DungeonWorlds.isDungeon(world)) {
            return false;
        }
        Location at = player.getLocation();
        // Wider than the pet-quiet bubble: the harbour tutorial owns titles there.
        return PetSpawnZones.nearestWithin(at, HUB_BUFFER) == null && !PetBorderlands.contains(at);
    }

    private void arrive(Player player, PetHabitat habitat, Track track) {
        UUID id = player.getUniqueId();
        Set<PetHabitat> known = discovered.computeIfAbsent(id, key -> EnumSet.noneOf(PetHabitat.class));
        if (known.add(habitat)) {
            save();
            announce(player, habitat, known.size());
            track.whisperedAt.put(habitat, System.currentTimeMillis());
            return;
        }
        long now = System.currentTimeMillis();
        Long last = track.whisperedAt.get(habitat);
        if (last != null && now - last < WHISPER_COOLDOWN_MS) {
            return;
        }
        track.whisperedAt.put(habitat, now);
        player.sendActionBar(legacy(HabitatPresentation.color(habitat) + "✦ §7"
                + HabitatPresentation.placeName(habitat)));
    }

    private void announce(Player player, PetHabitat habitat, int count) {
        String name = HabitatPresentation.coloredName(habitat);
        int total = HabitatPresentation.discoverableCount();

        // Sound first: one soft toast, the place underneath it, a late chime.
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.1f);
        player.playSound(player.getLocation(), HabitatPresentation.ambience(habitat), 0.8f, 0.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.25f);
                ring(player, habitat);
            }
        }, 8L);

        player.showTitle(Title.title(
                legacy(HabitatPresentation.color(habitat) + "§l" + HabitatPresentation.placeName(habitat)),
                legacy("§7Biotope discovered §8· §f" + count + "§8/§7" + total),
                Title.Times.times(Duration.ofMillis(600), Duration.ofMillis(2400), Duration.ofMillis(900))
        ));

        player.sendMessage("§8✦ " + name + " §8· §7" + HabitatPresentation.tagline(habitat));
        String species = speciesLine(player, habitat);
        if (species != null) {
            player.sendMessage(species);
        }
    }

    /** One quiet ring at the player's feet, only they see it. */
    private void ring(Player player, PetHabitat habitat) {
        Particle.DustOptions dust = new Particle.DustOptions(HabitatPresentation.dust(habitat), 0.9f);
        Location feet = player.getLocation().add(0.0, 0.15, 0.0);
        int points = 14;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            player.spawnParticle(
                    Particle.DUST,
                    feet.clone().add(Math.cos(angle) * 1.1, 0.0, Math.sin(angle) * 1.1),
                    1, 0.0, 0.0, 0.0, 0.0, dust
            );
        }
    }

    /** Which pets live here — names only for species the player has already met. */
    private String speciesLine(Player player, PetHabitat habitat) {
        if (plugin.getPetRegistry() == null) {
            return null;
        }
        PlayerPetCollection collection = plugin.getPetCollection(player);
        List<String> known = new ArrayList<>();
        int unknown = 0;
        for (PetDefinition definition : plugin.getPetRegistry().getAll()) {
            if (definition == null
                    || definition.getHabitat() != habitat
                    || definition.isShopExclusive()
                    || definition.getSpawnWeight() <= 0.0) {
                continue;
            }
            if (collection != null && collection.hasRevealed(definition.getId())) {
                known.add("§f" + definition.getDisplayName());
            } else {
                unknown++;
            }
        }
        if (known.isEmpty() && unknown == 0) {
            return null;
        }
        StringBuilder line = new StringBuilder("§8  Lives here: ");
        line.append(String.join("§7, ", known));
        if (unknown > 0) {
            if (!known.isEmpty()) {
                line.append("§7, ");
            }
            line.append("§8").append(unknown).append(" unknown");
        }
        return line.toString();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracks.remove(event.getPlayer().getUniqueId());
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Set<PetHabitat> set = EnumSet.noneOf(PetHabitat.class);
                for (String raw : players.getStringList(key)) {
                    try {
                        set.add(PetHabitat.valueOf(raw.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                discovered.put(id, set);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<PetHabitat>> entry : discovered.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue().stream()
                    .map(habitat -> habitat.name().toLowerCase(Locale.ROOT))
                    .sorted()
                    .toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save habitat-discoveries.yml: " + exception.getMessage());
        }
    }

    private static net.kyori.adventure.text.Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static final class Track {
        private Location lastScan;
        private PetHabitat candidate;
        private PetHabitat settled;
        private final Map<PetHabitat, Long> whisperedAt = new java.util.EnumMap<>(PetHabitat.class);
    }
}
