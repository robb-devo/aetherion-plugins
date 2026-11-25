package de.aetherion.guilds.island;

import io.papermc.paper.entity.TeleportFlag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The first landing, as one film shot. The player spectates an invisible lens (an item display) that moves every
 * tick with a short teleport interpolation, so the client glides it between frames instead of hopping. The shot
 * opens inside white cloud until the island's chunks have reached the client (a camera packet for a lens the
 * client doesn't know yet is dropped: the old sweep showed nothing for exactly that reason), clears the cloud
 * while it starts to drift, circles the island high and wide, comes down and ends in the player's own eyes where
 * they stand. There they get their body back with no cut. Sneaking skips to that moment. The game mode is written
 * to the player's data first, so a quit or a crash mid-shot gives it back on the next join.
 */
public final class LandingCamera implements Listener {

    private static final String TAG = "aeg_landing_cam";
    private static final double EYE = 1.62;
    /** Lens teleport interpolation (ticks): the client glides each frame, which also hides network jitter. */
    private static final int SMOOTH = 4;
    /** The flight itself, one frame per tick (10 s). */
    private static final int FLIGHT = 200;
    /** The last frame holds this long, so the gliding lens has fully arrived before the hand-over. */
    private static final int SETTLE = 12;
    /** The cloud holds at least this long (meshing happens in the white) and at most this long (never stuck). */
    private static final int PREROLL_MIN = 14;
    private static final int PREROLL_MAX = 90;
    private static final int CLEAR = 34;
    /** Chunks round the island's centre that must be on the client before the cloud clears. */
    private static final int SENT_RADIUS = 2;
    /** How far the circle sweeps round the island, and how high and wide it starts. */
    private static final double SWEEP = Math.toRadians(170);
    private static final double START_HEIGHT = 24;
    private static final double START_WIDTH = 20;
    /** The final look: a touch down onto the island ahead. */
    private static final double END_PITCH = 5;

    private final JavaPlugin plugin;
    private final NamespacedKey modeKey;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private boolean enabled = true;

    private static final class Shot {
        ItemDisplay lens;
        final List<TextDisplay> cloud = new ArrayList<>();
        GameMode before;
        Location focus;
        Location stand;
        Location[] path;
        Runnable arrived;
        BukkitRunnable task;
        boolean attached;
        int sent;
        int preroll;
        int frame = -1;
    }

    public LandingCamera(JavaPlugin plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "landing_mode");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Flies over the island round {@code focus} and sets the player down at {@code stand} (feet on the ground; its
     * yaw is the way they look), then runs {@code arrived}. When the camera can't run, {@code fallback} runs.
     */
    public void fly(Player player, Location stand, Location focus, Runnable arrived, Runnable fallback) {
        World world = stand == null ? null : stand.getWorld();
        if (!enabled || world == null || focus == null || focus.getWorld() != world
                || shots.containsKey(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            fallback.run();
            return;
        }
        Shot shot = new Shot();
        shot.before = player.getGameMode();
        shot.arrived = arrived;
        shot.focus = focus.clone();
        shot.path = path(world, stand, focus);
        // one eye height under the last frame, looking the same way: the hand-over shows nothing
        Location last = shot.path[shot.path.length - 1];
        shot.stand = last.clone().subtract(0, EYE, 0);
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, shot.before.name());
        Location first = shot.path[0];
        shot.lens = world.spawn(first.clone(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setSilent(true);
            d.addScoreboardTag(TAG);
            d.setTeleportDuration(SMOOTH);
            d.setViewRange(2.0f);
            d.setRotation(first.getYaw(), first.getPitch());
        });
        shots.put(player.getUniqueId(), shot);
        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        if (!player.teleport(first)) {
            // held back (a mount, another plugin): no shot, the plain landing instead
            shots.remove(player.getUniqueId());
            restore(player, shot.before);
            drop(shot);
            fallback.run();
            return;
        }
        player.showEntity(plugin, shot.lens);
        cloud(player, shot, 4, 0xB4);
        player.playSound(first, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.14f, 0.6f);
        shot.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick(player, shot);
            }
        };
        shot.task.runTaskTimer(plugin, 1L, 1L);
    }

    private void tick(Player player, Shot shot) {
        if (!player.isOnline() || shot.lens == null || !shot.lens.isValid()) {
            finish(player, false);
            return;
        }
        if (player.getWorld() != shot.lens.getWorld()) {
            // a command or a portal took them elsewhere: let it stand, just give the mode back
            letGo(player);
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            // the world's own game mode (a world manager) or another plugin reset it right after the hop: keep the lens
            player.setGameMode(GameMode.SPECTATOR);
            if (shot.attached) {
                player.setSpectatorTarget(shot.lens);
            }
        }
        Entity target = player.getSpectatorTarget();
        boolean onLens = target != null && target.getUniqueId().equals(shot.lens.getUniqueId());
        if (!shot.attached) {
            // waiting for the lens's chunk: the player stays on the spot, inside the cloud
            if (player.getLocation().distanceSquared(shot.lens.getLocation()) > 0.25) {
                player.teleport(shot.lens.getLocation());
            }
            shot.sent = sent(player, shot.lens.getLocation(), 0) ? shot.sent + 1 : 0;
            if (shot.sent >= 2 || shot.preroll >= PREROLL_MAX - 10) { // the lens pairs one tick after its chunk
                shot.attached = true;
                player.setSpectatorTarget(shot.lens);
            }
        } else if (!onLens) {
            if (shot.frame >= 0) {
                finish(player, true); // sneaked off the lens: straight to the landing
                return;
            }
            player.setSpectatorTarget(shot.lens); // still in the cloud: back onto the lens
        }
        if (shot.frame < 0) {
            shot.preroll++;
            boolean ready = shot.attached && sent(player, shot.focus, SENT_RADIUS);
            if (shot.preroll >= PREROLL_MAX || (shot.preroll >= PREROLL_MIN && ready)) {
                rebind(player, shot);
                clearCloud(shot, CLEAR);
                shot.frame = 0;
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.55f, 0.8f);
                player.playSound(player.getLocation(), Sound.ENTITY_PARROT_AMBIENT, SoundCategory.AMBIENT, 0.35f, 1.8f);
                bar(player, "§8Sneak to skip");
            }
            return;
        }
        shot.frame++;
        if (shot.frame < shot.path.length) {
            shot.lens.teleport(shot.path[shot.frame], TeleportFlag.EntityState.RETAIN_PASSENGERS);
            if (shot.frame == 40) {
                bar(player, "§8Sneak to skip");
            } else if (shot.frame == 95) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.6f, 1.2f);
            } else if (shot.frame == 150) {
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.08f, 1.3f);
            }
            return;
        }
        if (shot.frame >= shot.path.length + SETTLE) {
            finish(player, true);
        }
    }

    private static void bar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    // ------------------------------------------------------------------------------------------------
    // the shot
    // ------------------------------------------------------------------------------------------------

    /**
     * One frame per tick: high and wide on the far side of the island, a slow descending circle round it, then
     * down into the player's eyes where they stand, looking the way they will look. Smootherstep in time, so it
     * starts and stops without a jolt, and the lens lifts over anything solid on the way (never through a roof).
     */
    private static Location[] path(World world, Location stand, Location focus) {
        Vector eye = stand.toVector().add(new Vector(0, EYE, 0));
        Vector centre = focus.toVector();
        double yaw = Math.toRadians(stand.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        double ex = eye.getX() - centre.getX();
        double ez = eye.getZ() - centre.getZ();
        double endRadius = Math.sqrt(ex * ex + ez * ez);
        double endAngle = endRadius < 0.01 ? Math.atan2(-forward.getZ(), -forward.getX()) : Math.atan2(ez, ex);
        double startRadius = Math.max(endRadius + START_WIDTH, 32);
        Vector lookFirst = centre.clone().add(new Vector(0, 1.0, 0));
        Vector lookLast = eye.clone().add(forward.clone().multiply(30)).add(new Vector(0, -30 * Math.tan(Math.toRadians(END_PITCH)), 0));

        Vector[] at = new Vector[FLIGHT + 1];
        for (int k = 0; k <= FLIGHT; k++) {
            double s = smootherstep(k / (double) FLIGHT);
            double rest = 1 - s;
            double angle = endAngle + SWEEP * rest;
            double radius = endRadius + (startRadius - endRadius) * Math.pow(rest, 1.2);
            double height = eye.getY() + START_HEIGHT * Math.pow(rest, 1.6);
            at[k] = new Vector(centre.getX() + Math.cos(angle) * radius, height, centre.getZ() + Math.sin(angle) * radius);
        }
        double[] lift = lift(world, at);
        Location[] frames = new Location[FLIGHT + 1];
        for (int k = 0; k <= FLIGHT; k++) {
            double s = smootherstep(k / (double) FLIGHT);
            Vector p = at[k].clone().add(new Vector(0, lift[k], 0));
            double w = smoothstep((s - 0.5) / 0.5);
            Vector look = lookFirst.clone().multiply(1 - w).add(lookLast.clone().multiply(w));
            Location frame = new Location(world, p.getX(), p.getY(), p.getZ());
            if (k == FLIGHT) {
                frame = new Location(world, eye.getX(), eye.getY(), eye.getZ());
                p = eye;
            }
            double dx = look.getX() - p.getX();
            double dy = look.getY() - p.getY();
            double dz = look.getZ() - p.getZ();
            frame.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            frame.setPitch((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            frames[k] = frame;
        }
        // the last frame looks exactly the way the player will (no turn at the hand-over)
        frames[FLIGHT].setYaw(stand.getYaw());
        frames[FLIGHT].setPitch((float) END_PITCH);
        return frames;
    }

    /**
     * How far each frame must rise to stay in open air (a little room round the lens), spread out before and
     * after every obstacle so the lens climbs over it in one calm curve. The very end stays where the eyes are.
     */
    private static double[] lift(World world, Vector[] at) {
        int n = at.length;
        double[] need = new double[n];
        for (int k = 0; k < n; k++) {
            double up = 0;
            while (up < 14 && !open(world, at[k].getX(), at[k].getY() + up, at[k].getZ())) {
                up += 0.5;
            }
            need[k] = up;
        }
        double[] held = new double[n];
        for (int k = 0; k < n; k++) {
            double max = 0;
            for (int j = Math.max(0, k - 16); j <= Math.min(n - 1, k + 16); j++) {
                max = Math.max(max, need[j]);
            }
            held[k] = max;
        }
        double[] lift = new double[n];
        for (int k = 0; k < n; k++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, k - 8); j <= Math.min(n - 1, k + 8); j++) {
                sum += held[j];
                count++;
            }
            double taper = Math.min(1.0, (n - 1 - k) / 6.0);
            lift[k] = sum / count * taper;
        }
        return lift;
    }

    /** Open air round a lens at x/y/z (unloaded chunks out here are void). */
    private static boolean open(World world, double x, double y, double z) {
        double[][] probes = {{0, 0, 0}, {0.35, 0, 0}, {-0.35, 0, 0}, {0, 0.35, 0}, {0, -0.35, 0}, {0, 0, 0.35}, {0, 0, -0.35}};
        for (double[] o : probes) {
            int bx = (int) Math.floor(x + o[0]);
            int by = (int) Math.floor(y + o[1]);
            int bz = (int) Math.floor(z + o[2]);
            if (by < world.getMinHeight() || by >= world.getMaxHeight() || !world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            if (!world.getBlockAt(bx, by, bz).isPassable()) {
                return false;
            }
        }
        return true;
    }

    private static double smootherstep(double t) {
        double x = Math.max(0, Math.min(1, t));
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private static double smoothstep(double t) {
        double x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------------------------------------------
    // lens and cloud
    // ------------------------------------------------------------------------------------------------

    /** Every chunk within {@code radius} of {@code at} has reached the client (true when that can't be told). */
    private static boolean sent(Player player, Location at, int radius) {
        if (at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
            return false;
        }
        try {
            int cx = at.getBlockX() >> 4;
            int cz = at.getBlockZ() >> 4;
            // only chunks the client will be sent from where it is now (a short view distance must not stall the shot)
            int view = Math.max(1, Math.min(player.getClientViewDistance(), player.getSendViewDistance()) - 1);
            int px = player.getLocation().getBlockX() >> 4;
            int pz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius
                            || Math.max(Math.abs(cx + dx - px), Math.abs(cz + dz - pz)) > view) {
                        continue;
                    }
                    long key = ((long) (cx + dx) & 0xffffffffL) | (((long) (cz + dz) & 0xffffffffL) << 32);
                    if (!player.isChunkSent(key)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable ex) {
            return true;
        }
    }

    /** The lens once more, still inside the cloud: a client that dropped the first camera packet is on it now. */
    private void rebind(Player player, Shot shot) {
        if (!player.isOnline() || !shot.lens.isValid() || player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        player.setSpectatorTarget(shot.lens);
    }

    /** Layers of pale cloud riding the lens, only for this player: each pair a little more of a white screen. */
    private void cloud(Player player, Shot shot, int layers, int alpha) {
        for (int i = 0; i < layers; i++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                final float z = dir * (1.1f + i * 0.05f);
                TextDisplay sheet = shot.lens.getWorld().spawn(shot.lens.getLocation(), TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setVisibleByDefault(false);
                    d.addScoreboardTag(TAG);
                    d.text(Component.text(" "));
                    d.setDefaultBackground(false);
                    d.setBackgroundColor(Color.fromARGB(alpha, 226, 234, 246));
                    d.setShadowed(false);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(0f, -3f, z), new Quaternionf(),
                            new Vector3f(96f, 24f, 1f), new Quaternionf()));
                });
                player.showEntity(plugin, sheet);
                shot.lens.addPassenger(sheet);
                shot.cloud.add(sheet);
            }
        }
    }

    /** The cloud thins away over {@code ticks} (every layer shrinks to nothing, a little apart), then goes. */
    private void clearCloud(Shot shot, int ticks) {
        List<TextDisplay> going = new ArrayList<>(shot.cloud);
        shot.cloud.clear();
        int k = 0;
        for (TextDisplay sheet : going) {
            if (!sheet.isValid()) {
                continue;
            }
            Transformation now = sheet.getTransformation();
            sheet.setInterpolationDelay(k++ % 3);
            sheet.setInterpolationDuration(ticks);
            sheet.setTransformation(new Transformation(now.getTranslation(), now.getLeftRotation(),
                    new Vector3f(0.01f, 0.01f, 1f), now.getRightRotation()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> going.forEach(Entity::remove), ticks + 4L);
    }

    private static void drop(Shot shot) {
        for (TextDisplay sheet : shot.cloud) {
            sheet.remove();
        }
        shot.cloud.clear();
        if (shot.lens != null) {
            for (Entity passenger : shot.lens.getPassengers()) {
                passenger.remove();
            }
            shot.lens.remove();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // the end of the shot
    // ------------------------------------------------------------------------------------------------

    /** Hands the body back on the ground under the last frame; {@code landed} runs the arrival after it. */
    private void finish(Player player, boolean landed) {
        Shot shot = shots.remove(player.getUniqueId());
        if (shot == null) {
            return;
        }
        if (shot.task != null) {
            shot.task.cancel();
        }
        if (player.isOnline()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                try {
                    player.setSpectatorTarget(null);
                } catch (Throwable ignored) {
                }
            }
            restore(player, shot.before);
            player.teleport(shot.stand);
            player.setFallDistance(0f);
        }
        drop(shot);
        if (landed && player.isOnline()) {
            shot.arrived.run();
        }
    }

    /** Taken elsewhere mid-shot: only the game mode comes back, they stay where they were sent. */
    private void letGo(Player player) {
        Shot shot = shots.remove(player.getUniqueId());
        if (shot == null) {
            return;
        }
        if (shot.task != null) {
            shot.task.cancel();
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try {
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
        }
        restore(player, shot.before);
        drop(shot);
    }

    private void restore(Player player, GameMode before) {
        player.setGameMode(before == null || before == GameMode.SPECTATOR ? GameMode.SURVIVAL : before);
        player.getPersistentDataContainer().remove(modeKey);
    }

    public boolean flying(Player player) {
        return shots.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (shots.containsKey(event.getPlayer().getUniqueId())) {
            finish(event.getPlayer(), false); // back on the ground before the save: they log in there
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String mode = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (mode == null || shots.containsKey(player.getUniqueId())) {
            return;
        }
        // the server went down mid-shot: give the game mode back
        GameMode before;
        try {
            before = GameMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            before = GameMode.SURVIVAL;
        }
        restore(player, before);
    }

    /**
     * Spectators can't wander off through the spectator menu while the shot runs. Going onto the lens itself is a
     * spectate teleport too, and that one passes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Shot shot = shots.get(event.getPlayer().getUniqueId());
        if (shot == null || shot.lens == null) {
            return;
        }
        Location to = event.getTo();
        boolean ontoLens = to != null && to.getWorld() == shot.lens.getWorld()
                && to.distanceSquared(shot.lens.getLocation()) < 4.0;
        if (!ontoLens) {
            event.setCancelled(true);
        }
    }

    /** Shots still running at shutdown end on the ground. */
    public void clear() {
        for (UUID id : shots.keySet().toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                finish(player, false);
            } else {
                Shot shot = shots.remove(id);
                if (shot != null) {
                    if (shot.task != null) {
                        shot.task.cancel();
                    }
                    drop(shot);
                }
            }
        }
        shots.clear();
    }
}
