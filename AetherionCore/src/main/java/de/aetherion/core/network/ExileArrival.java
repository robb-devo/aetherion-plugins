package de.aetherion.core.network;

import de.aetherion.core.AetherionCore;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-join exile ferry cutscene — spectator ride along the authored arrival chain
 * ({@code exil_anchors.yml → arrival.ferry_path}), then land at the Königskai.
 *
 * <p>Lives in Core because MMO-C is a lean backend (no Quests jar).
 */
public final class ExileArrival implements Listener {

    public static final String TAG = "aetherion_exile_arrival";

    /** Authored ferry path (eye height ≈ feet + 1.6). From exil_anchors arrival.ferry_path + landing. */
    private static final double[][] FERRY = {
            {240.0, 170.0, 0.0},
            {199.0, 146.0, 1.0},
            {190.0, 128.0, 2.0}, // fog break — bell
            {182.0, 110.0, 2.0},
            {176.0, 96.0, 1.0},
            {171.0, 82.0, 2.0},
            {166.0, 74.0, 4.0},
            {162.5, 74.6, 6.5}  // landing eye
    };
    private static final int FOG_BREAK_INDEX = 2;
    private static final int DURATION_TICKS = 240; // 12 s
    private static final float LAND_YAW = 90f;
    private static final float LAND_PITCH = -8f;

    private final AetherionCore plugin;
    private final ExileHost host;
    private final NamespacedKey rigKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private final File storeFile;
    private final List<Sample> baked = new ArrayList<>();

    ExileArrival(AetherionCore plugin, ExileHost host) {
        this.plugin = plugin;
        this.host = host;
        this.rigKey = new NamespacedKey(plugin, "exile_arrival_rig");
        this.storeFile = new File(plugin.getDataFolder(), "exile-arrivals.yml");
        loadShown();
        bake();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("exile.cutscene.enabled", true);
    }

    boolean hasSeen(UUID id) {
        return id != null && shown.contains(id);
    }

    /** Play when first arriving; skip if already seen (unless force). */
    void playIfFirst(Player player) {
        if (player == null || !player.isOnline() || !enabled() || baked.isEmpty()) {
            host.finishArrival(player, false);
            return;
        }
        if (hasSeen(player.getUniqueId())) {
            host.finishArrival(player, false);
            return;
        }
        start(player, false);
    }

    public void replay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cExile arrival already playing.");
            return;
        }
        if (!enabled() || baked.isEmpty()) {
            player.sendMessage("§cExile cutscene disabled / empty.");
            return;
        }
        start(player, true);
        player.sendMessage("§8Replaying exile arrival…");
    }

    public boolean isPlaying(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public void stop(Player player) {
        if (player != null) {
            abort(player.getUniqueId(), true);
        }
    }

    private void start(Player player, boolean replay) {
        UUID id = player.getUniqueId();
        abort(id, false);

        World world = host.exileWorld();
        if (world == null) {
            host.finishArrival(player, false);
            return;
        }

        Sample first = baked.get(0);
        Location eye = first.at(world);
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
        Session session = new Session(id, rig, prior, replay);
        sessions.put(id, session);

        player.closeInventory();
        try {
            player.setPlayerTime(12400L, false);
        } catch (Throwable ignored) {
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(rig);
        player.teleport(host.spawnLocation());

        player.showTitle(Title.title(
                Component.text("DAS EXIL", NamedTextColor.DARK_GRAY, TextDecoration.BOLD),
                Component.text("through the Veil", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2200), Duration.ofMillis(600))
        ));
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.2f, 0.55f);
        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.AMBIENT, 0.15f, 0.5f);

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
        Location eye = sample.at(player.getWorld());
        session.rig.teleport(eye);
        player.setSpectatorTarget(session.rig);

        if (!session.bellStruck && sample.fogBreak) {
            session.bellStruck = true;
            player.playSound(eye, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 0.55f, 0.7f);
            player.playSound(eye, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MUSIC, 0.35f, 0.75f);
            player.spawnParticle(Particle.CLOUD, eye, 18, 0.8, 0.4, 0.8, 0.02);
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§8The fog breaks — §7one bell."));
        }

        if (session.tick >= baked.size() - 1) {
            finish(player, session);
        }
    }

    private void finish(Player player, Session session) {
        sessions.remove(session.id);
        if (session.task != null) {
            session.task.cancel();
        }
        clearRig(session);
        restore(player, session);
        markShown(session.id);
        host.finishArrival(player, true);
        player.sendMessage("§8Exil §8» §7Königskai. The chain ends here.");
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
            restore(player, session);
            if (session.tick >= 40) {
                markShown(id);
            }
            host.finishArrival(player, session.tick >= 40);
        } else if (session.tick >= 40) {
            markShown(id);
        }
    }

    private void restore(Player player, Session session) {
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        player.resetPlayerTime();
        GameMode mode = session.priorMode == GameMode.SPECTATOR ? GameMode.ADVENTURE : session.priorMode;
        if (mode == GameMode.SURVIVAL) {
            mode = GameMode.ADVENTURE;
        }
        try {
            player.setGameMode(mode);
        } catch (Throwable ignored) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.teleport(host.spawnLocation());
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

    private void bake() {
        baked.clear();
        // Dense samples along the polyline so spectator motion is smooth.
        int segments = FERRY.length - 1;
        for (int tick = 0; tick <= DURATION_TICKS; tick++) {
            double t = tick / (double) DURATION_TICKS;
            double path = t * segments;
            int i = Math.min(segments - 1, (int) Math.floor(path));
            double local = path - i;
            double x = lerp(FERRY[i][0], FERRY[i + 1][0], local);
            double y = lerp(FERRY[i][1], FERRY[i + 1][1], local);
            double z = lerp(FERRY[i][2], FERRY[i + 1][2], local);
            // Look toward next point (or west at the end).
            double nx = i + 1 < FERRY.length - 1
                    ? lerp(FERRY[i + 1][0], FERRY[Math.min(i + 2, FERRY.length - 1)][0], local)
                    : x - 4;
            double ny = i + 1 < FERRY.length - 1
                    ? lerp(FERRY[i + 1][1], FERRY[Math.min(i + 2, FERRY.length - 1)][1], local)
                    : y;
            double nz = i + 1 < FERRY.length - 1
                    ? lerp(FERRY[i + 1][2], FERRY[Math.min(i + 2, FERRY.length - 1)][2], local)
                    : z;
            Vector look = new Vector(nx - x, ny - y, nz - z);
            if (look.lengthSquared() < 1.0e-4) {
                look = new Vector(-1, -0.1, 0);
            }
            look.normalize();
            Location dir = new Location(null, x, y, z);
            dir.setDirection(look);
            boolean fog = i == FOG_BREAK_INDEX || (i == FOG_BREAK_INDEX - 1 && local > 0.85);
            baked.add(new Sample(x, y, z, dir.getYaw(), dir.getPitch(), fog && tick > 0));
        }
        // Ensure exactly one fog-break cue near the authored point.
        boolean marked = false;
        for (Sample sample : baked) {
            if (sample.fogBreak) {
                if (marked) {
                    sample.fogBreak = false;
                } else {
                    marked = true;
                }
            }
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void markShown(UUID id) {
        if (id == null || !shown.add(id)) {
            return;
        }
        saveShown();
    }

    private void loadShown() {
        shown.clear();
        if (!storeFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        for (String raw : yaml.getStringList("shown")) {
            try {
                shown.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveShown() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID id : shown) {
            list.add(id.toString());
        }
        yaml.set("shown", list);
        try {
            yaml.save(storeFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save exile-arrivals.yml: " + ex.getMessage());
        }
    }

    void purgeRigs(World world) {
        if (world == null) {
            return;
        }
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (display.getScoreboardTags().contains(TAG)
                    || display.getPersistentDataContainer().has(rigKey, PersistentDataType.BYTE)) {
                display.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abort(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        abort(event.getPlayer().getUniqueId(), false);
    }

    private static final class Sample {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private boolean fogBreak;

        private Sample(double x, double y, double z, float yaw, float pitch, boolean fogBreak) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.fogBreak = fogBreak;
        }

        private Location at(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private static final class Session {
        private final UUID id;
        private ItemDisplay rig;
        private final GameMode priorMode;
        private final boolean replay;
        private BukkitTask task;
        private int tick;
        private boolean bellStruck;

        private Session(UUID id, ItemDisplay rig, GameMode priorMode, boolean replay) {
            this.id = id;
            this.rig = rig;
            this.priorMode = priorMode;
            this.replay = replay;
        }
    }
}
