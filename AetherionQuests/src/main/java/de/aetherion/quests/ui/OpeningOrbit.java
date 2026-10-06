package de.aetherion.quests.ui;

import de.aetherion.core.network.ServerNames;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.PlayerQuestStorage;
import de.aetherion.quests.npc.LivingNpcLife;
import de.aetherion.quests.npc.LivingNpcService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ankerhold opening orbit — language pick → 15 s spectator flyover → shortened FIND EGON.
 * Path is authored in {@code opening_orbit.yml} (Opus choreography against ankerhold_hub_v2).
 */
public final class OpeningOrbit implements Listener {

    public static final String TAG = "aetherion_opening_orbit";

    private static final String EGON = "egon";
    private static final double GATE_RADIUS = 48.0;
    private static final double START_X = 166.5;
    private static final double START_Y = 65.0;
    private static final double START_Z = 8.5;
    private static final double END_X = 147.5;
    private static final double END_Y = 65.0;
    private static final double END_Z = 4.5;
    private static final float END_YAW = 91.43f;
    private static final float END_PITCH = 0.67f;

    private static OpeningOrbit instance;
    private static NamespacedKey rigKey;

    private final AetherionQuests plugin;
    private final List<Sample> baked = new ArrayList<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private OpeningOrbit(AetherionQuests plugin) {
        this.plugin = plugin;
        loadBaked();
    }

    public static void register(AetherionQuests plugin) {
        if (plugin == null) {
            return;
        }
        if (instance != null) {
            instance.abortAll(true);
        }
        instance = new OpeningOrbit(plugin);
        rigKey = new NamespacedKey(plugin, "opening_orbit_rig");
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.purgeStrayRigs();
    }

    public static void shutdown() {
        if (instance != null) {
            instance.abortAll(true);
            instance = null;
        }
    }

    public static boolean isPlaying(Player player) {
        return player != null && instance != null && instance.sessions.containsKey(player.getUniqueId());
    }

    /**
     * After the first-join language pick: orbit when gated, else full HarbourArrival.
     */
    public static void playAfterLanguagePick(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (instance == null || !instance.shouldOrbit(player, false)) {
            HarbourArrival.play(player);
            return;
        }
        instance.start(player, false, () -> HarbourArrival.playAfterOrbit(player));
    }

    /** Admin replay — ignores shown-once; still needs Ankerhold footprint unless forced. */
    public static void replay(Player player) {
        if (player == null || !player.isOnline() || instance == null) {
            return;
        }
        if (instance.sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cOpening orbit already playing.");
            return;
        }
        if (!instance.enabled()) {
            player.sendMessage("§copening-orbit.enabled is false.");
            return;
        }
        if (!instance.onAllowedServer()) {
            player.sendMessage("§cOpening orbit is gated to "
                    + String.join(", ", instance.allowedServers())
                    + " (this server=" + instance.thisServer() + ").");
            return;
        }
        if (instance.baked.isEmpty()) {
            player.sendMessage("§copening_orbit.yml missing / empty.");
            return;
        }
        instance.start(player, true, () -> HarbourArrival.playAfterOrbit(player));
        player.sendMessage("§aReplaying opening orbit…");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("opening-orbit.enabled", true);
    }

    /**
     * Empty list = orbit never auto-plays (safe default).
     * mmo-c is exile now — list {@code mmo-r} only when Ankerhold is the live hub.
     */
    private List<String> allowedServers() {
        List<String> configured = plugin.getConfig().getStringList("opening-orbit.servers");
        return configured == null ? List.of() : configured;
    }

    private String thisServer() {
        return ServerNames.fromPort(Bukkit.getPort());
    }

    private boolean onAllowedServer() {
        String here = thisServer();
        if (here == null || here.isBlank()) {
            return false;
        }
        for (String allowed : allowedServers()) {
            if (allowed != null && allowed.equalsIgnoreCase(here.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldOrbit(Player player, boolean force) {
        if (!enabled() || !onAllowedServer() || baked.isEmpty()) {
            return false;
        }
        if (!force) {
            PlayerQuestStorage storage = plugin.getPlayerQuestStorage();
            if (storage != null && storage.hasOpeningOrbitShown(player.getUniqueId())) {
                return false;
            }
        }
        return nearHarbour(player.getLocation());
    }

    private static boolean nearHarbour(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        double dx = at.getX() - START_X;
        double dy = at.getY() - START_Y;
        double dz = at.getZ() - START_Z;
        return dx * dx + dy * dy + dz * dz <= GATE_RADIUS * GATE_RADIUS;
    }

    private void start(Player player, boolean replay, Runnable then) {
        UUID id = player.getUniqueId();
        abort(id, false);

        World world = player.getWorld();
        Sample first = baked.get(0);
        Location eye = sampleLoc(world, first);

        ItemDisplay rig = world.spawn(eye, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setTeleportDuration(1);
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(1);
            spawned.addScoreboardTag(TAG);
            spawned.getPersistentDataContainer().set(rigKey, PersistentDataType.BYTE, (byte) 1);
        });

        GameMode prior = player.getGameMode();
        Session session = new Session(id, rig, prior, replay, then);
        sessions.put(id, session);

        player.closeInventory();
        player.setPlayerTime(1000L, false);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(rig);
        player.teleport(feetAt(world, START_X, START_Y, START_Z, 90f, 0f));

        fireCues(player, session, 0);
        playMotif(player);

        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, session), 1L, 1L);
    }

    private void tick(Player player, Session session) {
        if (player == null || !player.isOnline() || session.rig == null || !session.rig.isValid()) {
            abort(session.id, true);
            return;
        }
        session.tick++;
        int index = Math.min(session.tick, baked.size() - 1);
        Sample sample = baked.get(index);
        Location eye = sampleLoc(player.getWorld(), sample);
        session.rig.teleport(eye);
        player.setSpectatorTarget(session.rig);

        fireCues(player, session, session.tick);

        if (session.tick >= baked.size() - 1) {
            finish(player, session);
        }
    }

    private void fireCues(Player player, Session session, int tick) {
        if (session.cuesFired.contains(tick)) {
            return;
        }
        session.cuesFired.add(tick);
        Location at = player.getLocation();
        switch (tick) {
            case 4 -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.AMBIENT, 0.35f, 0.53f);
            case 68 -> player.playSound(at, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.15f, 0.6f);
            case 152 -> player.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 0.4f, 0.75f);
            case 258 -> {
                LivingNpcService living = plugin.getLivingNpcService();
                if (living != null) {
                    living.setTurnToPlayer(EGON, false);
                }
            }
            case 270 -> {
                LivingNpcService living = plugin.getLivingNpcService();
                if (living != null) {
                    living.setTurnToPlayer(EGON, true);
                }
            }
            case 276 -> {
                Location gate = new Location(player.getWorld(), 156.0, 65.5, 8.5);
                player.spawnParticle(Particle.SPLASH, gate, 18, 0.6, 0.35, 0.6, 0.02);
                player.spawnParticle(Particle.CLOUD, gate.clone().add(0, 0.4, 0), 6, 0.4, 0.2, 0.4, 0.01);
            }
            case 280 -> HarbourArrival.flareEgon(player);
            case 292 -> {
                LivingNpcLife life = LivingNpcLife.get();
                if (life != null) {
                    life.swing(EGON);
                }
            }
            default -> {
            }
        }
    }

    private void playMotif(Player player) {
        // Custom pack cue when present; quiet note-block stand-in always (ogg may not ship yet).
        player.playSound(player.getLocation(), "aetherion:open_orbit", SoundCategory.MUSIC, 0.55f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MUSIC, 0.32f, 0.8f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sessions.containsKey(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MUSIC, 0.26f, 1.05f);
            }
        }, 16L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && sessions.containsKey(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MUSIC, 0.2f, 0.9f);
            }
        }, 36L);
    }

    private void finish(Player player, Session session) {
        sessions.remove(session.id);
        if (session.task != null) {
            session.task.cancel();
        }
        clearRig(session);
        restorePlayer(player, session, true);
        markShown(session.id);
        if (session.then != null) {
            plugin.getServer().getScheduler().runTask(plugin, session.then);
        }
    }

    private void abort(UUID id, boolean restoreIfOnline) {
        Session session = sessions.remove(id);
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        clearRig(session);
        Player player = plugin.getServer().getPlayer(id);
        if (restoreIfOnline && player != null && player.isOnline()) {
            restorePlayer(player, session, session.tick >= 20);
            if (session.tick >= 20) {
                markShown(id);
            }
            if (session.then != null) {
                plugin.getServer().getScheduler().runTask(plugin, session.then);
            }
        } else {
            // Quit mid-flight: still mark if they saw enough so next join isn't a loop.
            if (session.tick >= 20) {
                markShown(id);
            }
        }
    }

    private void abortAll(boolean restore) {
        for (UUID id : List.copyOf(sessions.keySet())) {
            abort(id, restore);
        }
    }

    private void restorePlayer(Player player, Session session, boolean useEnd) {
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        player.resetPlayerTime();
        GameMode mode = session.priorMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : session.priorMode;
        try {
            player.setGameMode(mode);
        } catch (Throwable ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        World world = player.getWorld();
        if (useEnd) {
            player.teleport(feetAt(world, END_X, END_Y, END_Z, END_YAW, END_PITCH));
        } else {
            player.teleport(feetAt(world, START_X, START_Y, START_Z, 90f, 0f));
        }
        LivingNpcService living = plugin.getLivingNpcService();
        if (living != null) {
            // Leave Egon facing arrivals after the hand-off.
            living.setTurnToPlayer(EGON, true);
        }
    }

    private void clearRig(Session session) {
        if (session.rig != null) {
            try {
                session.rig.remove();
            } catch (Throwable ignored) {
            }
            session.rig = null;
        }
    }

    private void markShown(UUID id) {
        PlayerQuestStorage storage = plugin.getPlayerQuestStorage();
        if (storage != null) {
            storage.markOpeningOrbitShown(id);
        }
    }

    private void purgeStrayRigs() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getScoreboardTags().contains(TAG)
                        || (rigKey != null
                        && display.getPersistentDataContainer().has(rigKey, PersistentDataType.BYTE))) {
                    display.remove();
                }
            }
        }
    }

    private void loadBaked() {
        baked.clear();
        try (InputStream in = plugin.getResource("opening_orbit.yml")) {
            if (in == null) {
                plugin.getLogger().warning("opening_orbit.yml missing from jar — opening orbit disabled.");
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection root = yaml.getConfigurationSection("opening_orbit");
            if (root == null) {
                plugin.getLogger().warning("opening_orbit.yml has no opening_orbit root.");
                return;
            }
            List<?> rows = root.getList("baked");
            if (rows == null || rows.isEmpty()) {
                plugin.getLogger().warning("opening_orbit.yml baked path empty.");
                return;
            }
            for (Object row : rows) {
                Sample sample = parseSample(row);
                if (sample != null) {
                    baked.add(sample);
                }
            }
            plugin.getLogger().info("Opening orbit loaded: " + baked.size() + " ticks.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load opening_orbit.yml: " + ex.getMessage());
            baked.clear();
        }
    }

    private static Sample parseSample(Object row) {
        if (row instanceof List<?> list && list.size() >= 6) {
            return new Sample(
                    asInt(list.get(0)),
                    asDouble(list.get(1)),
                    asDouble(list.get(2)),
                    asDouble(list.get(3)),
                    (float) asDouble(list.get(4)),
                    (float) asDouble(list.get(5))
            );
        }
        return null;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static Location sampleLoc(World world, Sample sample) {
        Location loc = new Location(world, sample.x, sample.y, sample.z, sample.yaw, sample.pitch);
        return loc;
    }

    private static Location feetAt(World world, double x, double y, double z, float yaw, float pitch) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        abort(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        abort(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorld(PlayerChangedWorldEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            abort(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        // Allow our own restore teleports; block unrelated mid-flight transfers.
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        abort(event.getPlayer().getUniqueId(), true);
    }

    private static final class Sample {
        final int tick;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        Sample(int tick, double x, double y, double z, float yaw, float pitch) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Session {
        final UUID id;
        final GameMode priorMode;
        final boolean replay;
        final Runnable then;
        final java.util.Set<Integer> cuesFired = ConcurrentHashMap.newKeySet();
        ItemDisplay rig;
        BukkitTask task;
        int tick;

        Session(UUID id, ItemDisplay rig, GameMode priorMode, boolean replay, Runnable then) {
            this.id = id;
            this.rig = rig;
            this.priorMode = priorMode;
            this.replay = replay;
            this.then = then;
        }
    }
}
