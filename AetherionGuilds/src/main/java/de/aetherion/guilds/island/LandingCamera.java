package de.aetherion.guilds.island;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The first landing: five seconds of camera sweeping over the empty island before you drift down onto it. The
 * player spectates an invisible stand that circles the island; their game mode is written to their own data
 * first, so a quit or a crash mid-sweep gives it back on the next join. Sneaking ends it early.
 */
public final class LandingCamera implements Listener {

    private static final int TICKS = 100;
    private static final String TAG = "aeg_landing_cam";

    private final JavaPlugin plugin;
    private final NamespacedKey modeKey;
    private final Map<UUID, Sweep> sweeps = new HashMap<>();
    private boolean enabled = true;

    private static final class Sweep {
        ArmorStand camera;
        GameMode before;
        Location center;
        Runnable then;
        BukkitRunnable task;
    }

    public LandingCamera(JavaPlugin plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "landing_mode");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Sweeps over {@code center}, then runs {@code then} (straight away when the camera is off). */
    public void sweep(Player player, Location center, Runnable then) {
        World world = center == null ? null : center.getWorld();
        if (!enabled || world == null || sweeps.containsKey(player.getUniqueId())
                || player.getGameMode() == GameMode.SPECTATOR) {
            then.run();
            return;
        }
        Sweep sweep = new Sweep();
        sweep.before = player.getGameMode();
        sweep.then = then;
        sweep.center = center.clone();
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, sweep.before.name());
        double startAngle = Math.toRadians(200);
        Location first = point(center, startAngle, 0);
        sweep.camera = world.spawn(first, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.addScoreboardTag(TAG);
        });
        sweeps.put(player.getUniqueId(), sweep);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(first);
        player.setSpectatorTarget(sweep.camera);
        player.playSound(first, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.25f, 0.8f);
        player.playSound(first, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.AMBIENT, 0.5f, 1.8f);
        sweep.task = new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!player.isOnline() || !sweep.camera.isValid()) {
                    finish(player, false);
                    return;
                }
                double progress = t / (double) TICKS;
                double eased = progress * progress * (3 - 2 * progress);
                Location at = point(center, startAngle + eased * Math.PI * 0.75, eased);
                sweep.camera.teleport(at);
                if (t % 10 == 0 && player.getSpectatorTarget() != sweep.camera) {
                    // sneaking leaves the camera: take that as "skip"
                    finish(player, true);
                    return;
                }
                if (t >= TICKS) {
                    finish(player, true);
                }
            }
        };
        sweep.task.runTaskTimer(plugin, 1L, 1L);
    }

    /** A point on the sweep: high and wide at first, closer and lower at the end, always looking at the centre. */
    private static Location point(Location center, double angle, double progress) {
        double radius = 22 - 8 * progress;
        double height = 15 - 5 * progress;
        Location at = center.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
        double dx = center.getX() - at.getX();
        double dz = center.getZ() - at.getZ();
        double dy = center.getY() + 1 - at.getY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        at.setYaw(yaw);
        at.setPitch(pitch);
        return at;
    }

    private void finish(Player player, boolean continueAfter) {
        Sweep sweep = sweeps.remove(player.getUniqueId());
        if (sweep == null) {
            return;
        }
        if (sweep.task != null) {
            sweep.task.cancel();
        }
        if (player.isOnline()) {
            player.setSpectatorTarget(null);
            if (!continueAfter) {
                // cut short: put them down on the island, never in the air beside it
                player.teleport(sweep.center);
            }
            restore(player, sweep.before);
        }
        if (sweep.camera != null) {
            sweep.camera.remove();
        }
        if (continueAfter && player.isOnline()) {
            sweep.then.run();
        }
    }

    private void restore(Player player, GameMode before) {
        player.setGameMode(before == null || before == GameMode.SPECTATOR ? GameMode.SURVIVAL : before);
        player.getPersistentDataContainer().remove(modeKey);
    }

    public boolean sweeping(Player player) {
        return sweeps.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Sweep sweep = sweeps.get(player.getUniqueId());
        if (sweep == null) {
            return;
        }
        finish(player, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String mode = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (mode == null || sweeps.containsKey(player.getUniqueId())) {
            return;
        }
        // the server went down mid-sweep: give the game mode back
        GameMode before;
        try {
            before = GameMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            before = GameMode.SURVIVAL;
        }
        restore(player, before);
    }

    /** Spectators can't wander off by teleporting through other players' heads while the camera runs. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && sweeps.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Leftover stands from a crash (never saved, but just in case). */
    public void clear() {
        for (UUID id : sweeps.keySet().toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                finish(player, false);
            }
        }
        sweeps.clear();
    }
}
