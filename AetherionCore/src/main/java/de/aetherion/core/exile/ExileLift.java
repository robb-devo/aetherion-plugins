package de.aetherion.core.exile;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import de.aetherion.core.AetherionCore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
 * ever moved it — the quay had no way up. Pull the lever (or sneak in the cage) and the cage rides the 31 blocks
 * up the quay wall; at the top landing, sneak to ride down. The rider sees the cage itself climb (a display copy
 * of the real cage; the real one is hidden for them while it is "away").
 */
public final class ExileLift implements Listener {

    private static final int[] CAGE = {131, 73, 23, 137, 77, 29};
    private static final int[] LEVER = {136, 75, 28};
    /** Cage floor surface centre (rig origin) at the bottom, and how high it climbs. */
    private static final double[] FLOOR = {134.5, 74.0, 26.5};
    private static final double RISE = 31.0;
    private static final double[] BOTTOM_FEET = {134.5, 74.0, 26.5, 270};
    private static final double[] TOP_FEET = {127.5, 105.0, 26.5, 90};
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
    }

    private boolean inCage(Location l) {
        return world != null && l.getWorld().equals(world)
                && l.getX() >= CAGE[0] && l.getX() < CAGE[3] + 1 && l.getY() >= CAGE[1] && l.getY() < CAGE[4]
                && l.getZ() >= CAGE[2] && l.getZ() < CAGE[5] + 1;
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
        if (inCage(p.getLocation())) {
            start(p, true);
        } else if (atTop(p.getLocation())) {
            start(p, false);
        }
    }

    /** Hint for whoever steps into the cage or onto the top landing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStep(org.bukkit.event.player.PlayerMoveEvent event) {
        if (world == null || event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        Player p = event.getPlayer();
        if (inCage(event.getTo()) && !inCage(event.getFrom())) {
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
            if (up) {
                r.eyeRel = eye.toVector().subtract(floor.toVector());
                r.eyeRel.setX(clamp(r.eyeRel.getX(), -2.4, 2.4));
                r.eyeRel.setZ(clamp(r.eyeRel.getZ(), -2.4, 2.4));
                r.eyeRel.setY(1.62);
            } else {
                r.eyeRel = new Vector(0.8, 1.62, 0.0);
            }
            r.yaw = up ? 270f : 90f; // face the open, barred sea side going up; the shaft wall going down
            driving.add(r.id);
            r.cam = CineCam.start(plugin, p, up ? eye : eye, 3);
            driving.remove(r.id);
            if (!up) {
                r.cam.addWhite(3, 0xA0);
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
        if (!r.up && k == 6) {
            r.cam.clearWhite(10);
        }
        if (k % 14 == 0 && k < RIDE_TICKS - 6) {
            p.playSound(eye, Sound.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.5f, 0.6f + (float) (eased * 0.2));
        }
        if (k % 40 == 0) {
            r.rig.hideStatic(p);
        }
        if (r.up && k == RIDE_TICKS - 8) {
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
        double[] f = arrived ? (r.up ? TOP_FEET : BOTTOM_FEET) : (r.up ? BOTTOM_FEET : TOP_FEET);
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
        if (rides.containsKey(event.getPlayer().getUniqueId())) {
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
