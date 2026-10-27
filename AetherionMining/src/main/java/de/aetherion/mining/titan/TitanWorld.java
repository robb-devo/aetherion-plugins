package de.aetherion.mining.titan;

import de.aetherion.mining.AetherionMining;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Swallowed Isles: Ossarath's body as a world of its own on the mine server. Every morning at
 * {@code titan.churn-hour} the belly "churns": everyone inside is coughed up into Jawcamp, the world is thrown away and
 * grown again from a fresh seed, pre-generated in the background, and the Gullet opens again.
 */
public final class TitanWorld {

    public static final String DEFAULT_NAME = "swallowed_isles";
    private static final int[] WARN_MINUTES = {10, 5, 1};

    private final AetherionMining plugin;
    private final File stateFile;
    private final TitanShape.Palette palette = new TitanShape.Palette();
    private final Map<UUID, Long> fallSafe = new ConcurrentHashMap<>();
    private Jawcamp camp;
    private World world;
    private TitanShape shape;
    private long seed;
    private int generation;
    private long nextChurn;
    private boolean ready;
    private boolean churning;
    private int warned;

    public TitanWorld(AetherionMining plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "titan.yml");
    }

    public void bind(Jawcamp camp) {
        this.camp = camp;
    }

    public static boolean isTitanWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(DEFAULT_NAME);
    }

    public String worldName() {
        return DEFAULT_NAME;
    }

    public World world() {
        return world;
    }

    public TitanShape shape() {
        return shape;
    }

    public boolean isTitan(World check) {
        return check != null && world != null && check.getUID().equals(world.getUID());
    }

    public boolean open() {
        return ready && !churning && world != null;
    }

    public int generation() {
        return generation;
    }

    public long nextChurnAt() {
        return nextChurn;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads the day's body (or grows the first one) and arms the churn clock. */
    public void start() {
        loadState();
        File folder = new File(Bukkit.getWorldContainer(), worldName());
        if (seed != 0L && folder.isDirectory()) {
            world = create(seed);
            if (world != null && !ready) {
                pregenerate();
            }
        } else {
            grow();
        }
        if (nextChurn <= 0L) {
            nextChurn = nextChurnTime();
            saveState();
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 20L, 20L * 20L);
        plugin.getLogger().info("[Titan] Swallowed Isles " + (world == null ? "unavailable" : "seed " + seed
                + ", generation " + generation + (ready ? ", open" : ", pre-generating")) + ".");
    }

    public void shutdown() {
        saveState();
    }

    private World create(long daySeed) {
        shape = new TitanShape(daySeed, palette);
        WorldCreator creator = new WorldCreator(worldName());
        creator.generator(new TitanGenerator(shape));
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.seed(daySeed);
        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().warning("[Titan] Could not create the Swallowed Isles world.");
            return null;
        }
        created.setAutoSave(true);
        created.setDifficulty(Difficulty.PEACEFUL);
        created.setPVP(false);
        created.setSpawnFlags(false, false);
        created.setTime(18000L);
        created.setStorm(false);
        created.setThundering(false);
        created.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        created.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        created.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        created.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        created.setGameRule(GameRule.DO_FIRE_TICK, false);
        created.setGameRule(GameRule.MOB_GRIEFING, false);
        created.setGameRule(GameRule.KEEP_INVENTORY, true);
        created.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        created.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        created.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        created.setGameRule(GameRule.DO_INSOMNIA, false);
        created.setGameRule(GameRule.DISABLE_RAIDS, true);
        created.setGameRule(GameRule.RANDOM_TICK_SPEED, 1);
        var border = created.getWorldBorder();
        border.setCenter((TitanShape.MIN_X + TitanShape.MAX_X) / 2.0, 0.5);
        border.setSize(TitanShape.MAX_X - TitanShape.MIN_X);
        border.setWarningDistance(4);
        double[] a = shape.arrival();
        created.setSpawnLocation(new Location(created, a[0], a[1], a[2]));
        de.aetherion.mining.veins.VeinsGuard.open(created);
        return created;
    }

    /** A brand-new body from a fresh seed (first start, or after a churn removed the old one). */
    private void grow() {
        seed = System.nanoTime() ^ (System.currentTimeMillis() << 21);
        if (seed == 0L) {
            seed = 1L;
        }
        generation++;
        ready = false;
        warned = 0;
        nextChurn = nextChurnTime();
        saveState();
        world = create(seed);
        if (world != null) {
            pregenerate();
        }
    }

    /** Generates every chunk of the body ahead of the miners, a few at a time, off the main thread. */
    private void pregenerate() {
        World target = world;
        if (target == null) {
            return;
        }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = TitanShape.MIN_X >> 4; cx <= (TitanShape.MAX_X - 1) >> 4; cx++) {
            for (int cz = TitanShape.MIN_Z >> 4; cz <= (TitanShape.MAX_Z - 1) >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        long started = System.currentTimeMillis();
        int[] next = {0};
        int[] inFlight = {0};
        int[] done = {0};
        int total = chunks.size();
        Runnable[] pump = new Runnable[1];
        pump[0] = () -> {
            while (inFlight[0] < 8 && next[0] < total) {
                int[] c = chunks.get(next[0]++);
                inFlight[0]++;
                target.getChunkAtAsync(c[0], c[1], true).whenComplete((chunk, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            inFlight[0]--;
                            done[0]++;
                            if (world != target) {
                                return;
                            }
                            if (done[0] >= total) {
                                ready = true;
                                saveState();
                                plugin.getLogger().info("[Titan] Swallowed Isles generation " + generation + " ready ("
                                        + total + " chunks, " + (System.currentTimeMillis() - started) / 1000 + " s).");
                                if (camp != null) {
                                    camp.announce("§2✦ §7The Gullet is open. §fOssarath §7has settled.");
                                    camp.refreshBoard();
                                }
                                return;
                            }
                            pump[0].run();
                        }));
            }
        };
        pump[0].run();
    }

    // ------------------------------------------------------------------ the churn

    private long nextChurnTime() {
        int hour = Math.max(0, Math.min(23, plugin.getConfig().getInt("titan.churn-hour", 5)));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toInstant().toEpochMilli();
    }

    private void tick() {
        if (churning || nextChurn <= 0L) {
            return;
        }
        long left = nextChurn - System.currentTimeMillis();
        for (int i = warned; i < WARN_MINUTES.length; i++) {
            if (left <= WARN_MINUTES[i] * 60_000L && left > 0) {
                warned = i + 1;
                warn(WARN_MINUTES[i]);
            }
        }
        if (left <= 0) {
            churn("§5✦ §fOssarath churns. §7The belly reshapes, you are coughed up into Jawcamp.");
        }
    }

    private void warn(int minutes) {
        String line = "§5✦ §7Ossarath stirs… §fThe Churn §7in §e" + minutes + (minutes == 1 ? " minute" : " minutes")
                + "§7. Everyone inside is coughed up to Jawcamp.";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
            if (isTitan(player.getWorld())) {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.2f, 0.5f);
            }
        }
    }

    /** Empties the body, throws the world away and grows the next one. */
    public void churn(String reason) {
        if (churning) {
            return;
        }
        churning = true;
        World old = world;
        if (old != null) {
            for (Player player : new ArrayList<>(old.getPlayers())) {
                player.sendMessage(reason);
                coughUp(player, false);
            }
        }
        world = null;
        ready = false;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            File folder = old != null ? old.getWorldFolder() : new File(Bukkit.getWorldContainer(), worldName());
            if (old != null) {
                for (Player straggler : new ArrayList<>(old.getPlayers())) {
                    straggler.teleport(campSpawn());
                }
                if (!Bukkit.unloadWorld(old, false)) {
                    plugin.getLogger().warning("[Titan] Could not unload the old body. Churn postponed one hour.");
                    world = old;
                    ready = true;
                    churning = false;
                    nextChurn = System.currentTimeMillis() + 3_600_000L;
                    saveState();
                    return;
                }
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                deleteRecursively(folder);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    churning = false;
                    grow();
                    if (camp != null) {
                        camp.announce("§5✦ §7Ossarath has churned. §fThe Gullet §7opens again in a few minutes.");
                    }
                });
            });
        }, 40L);
        plugin.getLogger().info("[Titan] Churn started (generation " + generation + ").");
    }

    // ------------------------------------------------------------------ in and out

    public Location campSpawn() {
        Location spawn = camp != null ? camp.spawn() : null;
        return spawn != null ? spawn : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    /** Swallowed whole: darkness, a gulp and a heartbeat, then the fall down the gullet into the pool. */
    public boolean swallow(Player player) {
        if (!open()) {
            player.sendMessage(churning || world == null
                    ? "§5The Gullet §7is closed while Ossarath churns. Try again in a few minutes."
                    : "§5The Gullet §7is still settling. Try again in a moment.");
            return false;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1f, 0.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1f, 0.6f);
        player.showTitle(Title.title(Component.text("§4Swallowed whole"), Component.text("§7…into Ossarath's gullet"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(500))));
        markFallSafe(player, 20_000L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !open()) {
                return;
            }
            double[] a = shape.arrival();
            Location to = new Location(world, a[0], a[1], a[2], -90f, 60f);
            player.teleport(to);
            player.setFallDistance(0f);
            player.playSound(to, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.2f, 0.5f);
        }, 20L);
        return true;
    }

    /** Back up into Jawcamp (the climbing stone, /jawcamp, death, the churn). */
    public void coughUp(Player player, boolean title) {
        Location spawn = campSpawn();
        player.teleport(spawn);
        player.setFallDistance(0f);
        player.playSound(spawn, Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1f, 0.7f);
        if (title) {
            player.showTitle(Title.title(Component.text("§2Coughed up"), Component.text("§7Back in Jawcamp"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(400))));
        }
    }

    public void markFallSafe(Player player, long millis) {
        fallSafe.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean fallSafe(Player player) {
        Long until = fallSafe.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            fallSafe.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void forget(UUID id) {
        fallSafe.remove(id);
    }

    // ------------------------------------------------------------------ state

    private void loadState() {
        if (!stateFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        seed = yaml.getLong("seed", 0L);
        generation = yaml.getInt("generation", 0);
        nextChurn = yaml.getLong("next-churn", 0L);
        ready = yaml.getBoolean("ready", false);
    }

    void saveState() {
        YamlConfiguration yaml = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        yaml.set("seed", seed);
        yaml.set("generation", generation);
        yaml.set("next-churn", nextChurn);
        yaml.set("ready", ready);
        try {
            if (!plugin.getDataFolder().isDirectory()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Titan] Could not save titan.yml: " + ex.getMessage());
        }
    }

    File stateFile() {
        return stateFile;
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File child : children) {
                ok &= deleteRecursively(child);
            }
        }
        return file.delete() && ok;
    }
}
