package de.aetherion.core.exile;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import de.aetherion.core.AetherionCore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE KING'S LIFT (Kettenküste ↔ Hofstadt). Runde 7 built the cage, the brass lever and the shaft, but nothing
 * ever moved it — the quay had no way up. The cage rides the 31 blocks up the quay wall; at the top landing,
 * sneak to ride down. The rider sees the cage itself climb (a display copy of the real cage; the real one is
 * hidden for them while it is "away").
 *
 * <p>The cage cannot be walked into: the map generator cut the shaft's coast door through the outer skin only
 * (x 139), so a sandstone wall still stands between the quay and the cage's bars. The lift is therefore called
 * from that arch on the quay (right-click it or sneak in it); the ride opens in a short white inside the cage and
 * the way down ends in front of the arch. From inside the cage (staff) the lever and sneaking still work.
 */
public final class ExileLift implements Listener {

    public static final String TAG = "aetherion_exile_lift";

    private static final int[] CAGE = {131, 73, 23, 137, 77, 29};
    private static final int[] LEVER = {136, 75, 28};
    /** Cage floor surface centre (rig origin) at the bottom, and how high it climbs. */
    private static final double[] FLOOR = {134.5, 74.0, 26.5};
    private static final double RISE = 31.0;
    private static final double[] TOP_FEET = {127.5, 105.0, 26.5, 90};
    /** The coast door's arch: the blind back wall (x 138) and the recess (x 139) facing the quay. */
    private static final int[] COAST_ARCH = {138, 73, 23, 139, 81, 29};
    /** Feet in the arch or on the quay right in front of it. */
    private static final int[] COAST_ZONE = {139, 73, 23, 141, 75, 29};
    /** On the quay in front of the arch, facing out to the berths. */
    private static final double[] COAST_FEET = {140.5, 73.0, 26.5, 270};
    private static final double[] COAST_LABEL = {139.6, 76.6, 26.5};
    /** Where a rider called from the arch first sees the cage: at its bars, on the quay side. */
    private static final double DOOR_EYE_X = 2.0;
    private static final int RIDE_TICKS = 110;

    private final AetherionCore plugin;
    private final Map<UUID, Ride> rides = new ConcurrentHashMap<>();
    private final Set<UUID> driving = ConcurrentHashMap.newKeySet();
    private World world;

    private final class Ride {
        final UUID id;
        final boolean up;
        final GameMode prior;
        CineCam cam;
        BlockRig rig;
        BukkitTask task;
        Vector eyeRel;
        float yaw;
        int tick;
        /** The ride opened in a white that still has to clear. */
        boolean white;
        boolean ended;

        Ride(UUID id, boolean up, GameMode prior) {
            this.id = id;
            this.up = up;
            this.prior = prior;
        }
    }

    public ExileLift(AetherionCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void arm(World exile) {
        this.world = exile;
        if (exile != null) {
            Chunk c = exile.getChunkAt((int) Math.floor(COAST_LABEL[0]) >> 4, (int) Math.floor(COAST_LABEL[2]) >> 4);
            refreshLabel(c);
        }
    }

    // ------------------------------------------------------------------ the coast door label

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (world == null || !event.getWorld().equals(world)) {
            return;
        }
        Chunk c = event.getChunk();
        if (c.getX() != (int) Math.floor(COAST_LABEL[0]) >> 4 || c.getZ() != (int) Math.floor(COAST_LABEL[2]) >> 4) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (c.isLoaded()) {
                refreshLabel(c);
            }
        });
    }

    /** The arch's label is not persistent: rebuilt with its chunk, so an unload never leaves doubles. */
    private void refreshLabel(Chunk c) {
        for (Entity e : c.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
        Location at = new Location(world, COAST_LABEL[0], COAST_LABEL[1], COAST_LABEL[2]);
        ExileDoors.label(plugin, at,
                "§6⛓ The King's Lift §8· §7up to the court\n§7right-click the arch or sneak in it",
                "§6⛓ Der Königslift §8· §7hinauf zur Hofstadt\n§7Rechtsklick auf den Bogen oder darin schleichen",
                e -> {
                    e.setPersistent(false);
                    e.addScoreboardTag(TAG);
                });
    }

    private boolean inCage(Location l) {
        return world != null && l.getWorld().equals(world)
                && l.getX() >= CAGE[0] && l.getX() < CAGE[3] + 1 && l.getY() >= CAGE[1] && l.getY() < CAGE[4]
                && l.getZ() >= CAGE[2] && l.getZ() < CAGE[5] + 1;
    }

    private boolean atCoastDoor(Location l) {
        return world != null && l.getWorld().equals(world)
                && l.getX() >= COAST_ZONE[0] && l.getX() < COAST_ZONE[3] + 1
                && l.getY() >= COAST_ZONE[1] && l.getY() < COAST_ZONE[4] + 1
                && l.getZ() >= COAST_ZONE[2] && l.getZ() < COAST_ZONE[5] + 1;
    }

    private static boolean inArch(Block b) {
        return b.getX() >= COAST_ARCH[0] && b.getX() <= COAST_ARCH[3] && b.getY() >= COAST_ARCH[1]
                && b.getY() <= COAST_ARCH[4] && b.getZ() >= COAST_ARCH[2] && b.getZ() <= COAST_ARCH[5];
    }

    private boolean atTop(Location l) {
        if (world == null || !l.getWorld().equals(world)) {
            return false;
        }
        double dx = l.getX() - TOP_FEET[0];
        double dz = l.getZ() - TOP_FEET[2];
        return Math.abs(l.getY() - TOP_FEET[1]) < 1.6 && dx * dx + dz * dz < 2.6 * 2.6;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLever(PlayerInteractEvent event) {
        if (world == null || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = event.getClickedBlock();
        if (b == null || !b.getWorld().equals(world)) {
            return;
        }
        if (b.getX() == LEVER[0] && b.getY() == LEVER[1] && b.getZ() == LEVER[2]) {
            event.setCancelled(true);
            if (inCage(event.getPlayer().getLocation())) {
                start(event.getPlayer(), true);
            }
        } else if (inArch(b) && event.getPlayer().getLocation().distanceSquared(b.getLocation().add(0.5, 0, 0.5)) < 6 * 6) {
            event.setCancelled(true);
            if (!rides.containsKey(event.getPlayer().getUniqueId())) {
                start(event.getPlayer(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || world == null) {
            return;
        }
        Player p = event.getPlayer();
        if (rides.containsKey(p.getUniqueId())) {
            return;
        }
        if (inCage(p.getLocation()) || atCoastDoor(p.getLocation())) {
            start(p, true);
        } else if (atTop(p.getLocation())) {
            start(p, false);
        }
    }

    /** Hint for whoever steps up to the coast door, into the cage or onto the top landing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStep(org.bukkit.event.player.PlayerMoveEvent event) {
        if (world == null || event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        Player p = event.getPlayer();
        if (atCoastDoor(event.getTo()) && !atCoastDoor(event.getFrom())) {
            p.sendActionBar(Component.text(Say.t(p, "The King's Lift · right-click the arch or sneak to go up",
                    "Der Königslift · Rechtsklick auf den Bogen oder schleichen, um hochzufahren"), NamedTextColor.GOLD));
        } else if (inCage(event.getTo()) && !inCage(event.getFrom())) {
            p.sendActionBar(Component.text(Say.t(p, "The King's Lift · pull the lever or sneak to go up",
                    "Der Königslift · Hebel ziehen oder schleichen, um hochzufahren"), NamedTextColor.GOLD));
        } else if (atTop(event.getTo()) && !atTop(event.getFrom())) {
            p.sendActionBar(Component.text(Say.t(p, "The King's Lift · sneak to ride down to the quay",
                    "Der Königslift · schleichen, um zum Kai hinabzufahren"), NamedTextColor.GOLD));
        }
    }

    private void start(Player p, boolean up) {
        Ride r = new Ride(p.getUniqueId(), up, p.getGameMode());
        rides.put(r.id, r);
        try {
            Location floor = new Location(world, FLOOR[0], FLOOR[1] + (up ? 0 : RISE), FLOOR[2]);
            Location origin = new Location(world, FLOOR[0], FLOOR[1], FLOOR[2]);
            r.rig = BlockRig.spawn(plugin, floor, 3, List.of(p));
            r.rig.copy(world, CAGE[0], CAGE[1], CAGE[2], CAGE[3], CAGE[4], CAGE[5], d -> true, true, origin);
            r.rig.hideStatic(p);
            Location eye = p.getEyeLocation();
            // called from the arch on the quay: the wall stands between, so the shot opens inside the cage in a white
            boolean fromDoor = up && !inCage(p.getLocation());
            if (fromDoor) {
                r.eyeRel = new Vector(DOOR_EYE_X, 1.62, clamp(eye.getZ() - floor.getZ(), -2.4, 2.4));
            } else if (up) {
                r.eyeRel = eye.toVector().subtract(floor.toVector());
                r.eyeRel.setX(clamp(r.eyeRel.getX(), -2.4, 2.4));
                r.eyeRel.setZ(clamp(r.eyeRel.getZ(), -2.4, 2.4));
                r.eyeRel.setY(1.62);
            } else {
                r.eyeRel = new Vector(0.8, 1.62, 0.0);
            }
            r.yaw = up ? 270f : 90f; // face the open, barred sea side going up; the shaft wall going down
            Location first = eye;
            if (fromDoor) {
                first = floor.clone().add(r.eyeRel);
                first.setYaw(r.yaw);
                first.setPitch(-18f);
            }
            driving.add(r.id);
            r.cam = CineCam.start(plugin, p, first, 3);
            driving.remove(r.id);
            if (!up || fromDoor) {
                r.cam.addWhite(3, 0xA0);
                r.white = true;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("King's Lift failed for " + p.getName() + ": " + ex.getMessage());
            end(p, r, false);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.9f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.9f, 0.5f);
        r.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(p, r), 1L, 1L);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void tick(Player p, Ride r) {
        if (r.ended) {
            return;
        }
        if (!p.isOnline()) {
            end(p, r, false);
            return;
        }
        driving.add(r.id);
        try {
            r.cam.hold();
        } finally {
            driving.remove(r.id);
        }
        int k = r.tick++;
        double u = Math.min(1.0, k / (double) RIDE_TICKS);
        double eased = u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
        double y = r.up ? eased * RISE : (1 - eased) * RISE;
        Location floor = new Location(world, FLOOR[0], FLOOR[1] + y, FLOOR[2]);
        r.rig.moveTo(floor);
        Location eye = floor.clone().add(r.eyeRel);
        eye.setYaw(r.yaw);
        // going up: look up the shaft, the four chains streaming past; going down: down into the cage
        eye.setPitch(r.up ? (float) (-18 - 34 * Math.sin(Math.PI * Math.min(1.0, u * 1.2))) : (float) (20 + 30 * Math.sin(Math.PI * u)));
        r.cam.frame(eye);
        if (r.white && k == 6) {
            r.white = false;
            r.cam.clearWhite(10);
        }
        if (k % 14 == 0 && k < RIDE_TICKS - 6) {
            p.playSound(eye, Sound.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.5f, 0.6f + (float) (eased * 0.2));
        }
        if (k % 40 == 0) {
            r.rig.hideStatic(p);
        }
        if (k == RIDE_TICKS - 8) {
            // both ends step out of the cage (the top landing, the quay in front of the arch): out of a white
            r.cam.addWhite(3, 0xA0);
        }
        if (k >= RIDE_TICKS) {
            p.playSound(eye, Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.9f, 0.5f);
            end(p, r, true);
        }
    }

    private void end(Player p, Ride r, boolean arrived) {
        if (r.ended) {
            return;
        }
        r.ended = true;
        rides.remove(r.id);
        if (r.task != null) {
            r.task.cancel();
        }
        double[] f = arrived ? (r.up ? TOP_FEET : COAST_FEET) : (r.up ? COAST_FEET : TOP_FEET);
        Location feet = new Location(world, f[0], f[1], f[2], (float) f[3], 0f);
        if (p != null && p.isOnline()) {
            if (r.rig != null) {
                r.rig.restoreStatic(p);
            }
            driving.add(r.id);
            try {
                if (r.cam != null) {
                    r.cam.end(feet, r.prior == GameMode.SPECTATOR ? GameMode.ADVENTURE : r.prior);
                }
            } finally {
                driving.remove(r.id);
            }
        } else if (r.cam != null) {
            r.cam.remove();
        }
        if (r.rig != null) {
            BlockRig rig = r.rig;
            Bukkit.getScheduler().runTaskLater(plugin, rig::remove, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (rides.containsKey(event.getPlayer().getUniqueId()) && !CineCam.rebinding(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Ride r = rides.get(event.getPlayer().getUniqueId());
        if (r == null || r.ended || driving.contains(r.id) || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Ride r = rides.get(event.getPlayer().getUniqueId());
        if (r != null) {
            end(event.getPlayer(), r, true);
        }
    }

    public void shutdown() {
        for (Ride r : List.copyOf(rides.values())) {
            end(Bukkit.getPlayer(r.id), r, true);
        }
    }

    static boolean isLiftMaterial(Material m) {
        return m != Material.AIR;
    }
}
