package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vertical traversal on a 180-block-tall island.
 * <ul>
 *   <li><b>Updrafts</b> — measured shafts where the air is clear from a walkable floor to a ledge you can't reach
 *   on foot (the Skyreach terrace above the Mountain Gate, the Crag peak). Step on the vent: the wind lifts you up
 *   the column, then glides you onto the ledge. A puff of cloud marks each vent when someone is near.</li>
 *   <li><b>Edge rescue</b> — drop off the island (below {@code rescue.floor-y} inside the footprint) and the wind
 *   puts you back on your last safe footing. Only for someone who stood on the island in the last 30 s.</li>
 *   <li><b>Grace</b> — no fall damage right after an updraft, a glide or a rescue.</li>
 * </ul>
 */
public final class OriginTraversal implements Listener {

    private static final int RIDE_TIMEOUT_TICKS = 20 * 20;

    private static final class Ride {
        OriginConfig.Updraft draft;
        int age;
        boolean gliding;
    }

    private final OriginIsle isle;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Location> safe = new HashMap<>();
    private final Map<UUID, Long> lastOnIsle = new HashMap<>();
    private int ventTick;

    OriginTraversal(OriginIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ updrafts

    /** Every 4 ticks: who is standing on a vent? */
    void triggerTick(List<Player> onIsle) {
        if (isle.config().updrafts().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            UUID id = player.getUniqueId();
            if (rides.containsKey(id) || isle.flight().flying(player) || player.isSneaking() || player.isFlying()
                    || player.isGliding() || player.getGameMode() == GameMode.SPECTATOR || player.isInsideVehicle()) {
                continue;
            }
            Long cool = cooldownUntil.get(id);
            if (cool != null && now < cool) {
                continue;
            }
            Location feet = player.getLocation();
            for (OriginConfig.Updraft draft : isle.config().updrafts().values()) {
                double[] f = draft.floor();
                double dx = feet.getX() - f[0];
                double dz = feet.getZ() - f[2];
                double dy = feet.getY() - f[1];
                if (dx * dx + dz * dz <= 1.35d * 1.35d && dy > -0.8d && dy < 1.8d) {
                    begin(player, draft);
                    break;
                }
            }
        }
    }

    public void begin(Player player, OriginConfig.Updraft draft) {
        Ride ride = new Ride();
        ride.draft = draft;
        rides.put(player.getUniqueId(), ride);
        grace(player, 25_000L);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.35f, 1.4f);
        OriginText.bar(player, "§f≋ " + draft.name() + " §8· §7hold on… §8(sneak to let go)");
    }

    /** Every tick while someone rides. */
    void rideTick() {
        if (rides.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Ride> e : new HashMap<>(rides).entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) {
                rides.remove(e.getKey());
                continue;
            }
            ride(player, e.getValue());
        }
    }

    private void ride(Player player, Ride ride) {
        ride.age++;
        OriginConfig.Updraft d = ride.draft;
        Location at = player.getLocation();
        World world = isle.config().world();
        if (ride.age > RIDE_TIMEOUT_TICKS || player.isSneaking() || player.isDead() || world == null || !world.equals(player.getWorld())) {
            finish(player, false);
            return;
        }
        double[] f = d.floor();
        double[] t = d.top();
        if (!ride.gliding) {
            double toTop = t[1] + 1.5d - at.getY();
            double offX = at.getX() - f[0];
            double offZ = at.getZ() - f[2];
            if (offX * offX + offZ * offZ > 5.0d * 5.0d) {
                // Knocked or moved out of the column: let go instead of dragging them back.
                finish(player, false);
                return;
            }
            if (toTop <= 0) {
                ride.gliding = true;
            } else {
                Vector center = new Vector(f[0] - at.getX(), 0, f[2] - at.getZ()).multiply(0.25d);
                player.setVelocity(new Vector(center.getX(), Math.min(1.1d, 0.3d + toTop * 0.2d), center.getZ()));
                player.setFallDistance(0f);
                if (ride.age % 2 == 0) {
                    world.spawnParticle(Particle.CLOUD, at.clone().add(0, -0.3, 0), 2, 0.25, 0.1, 0.25, 0.01);
                }
                if (ride.age % 5 == 0) {
                    world.spawnParticle(Particle.END_ROD, at.clone().add(0, -1.2, 0), 1, 0.4, 0.4, 0.4, 0.01);
                }
                if (ride.age % 30 == 0) {
                    player.playSound(at, Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.25f, 1.6f);
                }
                return;
            }
        }
        Vector flat = new Vector(t[0] - at.getX(), 0, t[2] - at.getZ());
        double dist = flat.length();
        if (dist < 0.9d) {
            finish(player, true);
            return;
        }
        Vector v = flat.normalize().multiply(Math.min(0.5d, 0.12d + dist * 0.12d));
        v.setY(at.getY() < t[1] + 0.8d ? 0.25d : 0.02d);
        player.setVelocity(v);
        player.setFallDistance(0f);
    }

    private void finish(Player player, boolean arrived) {
        Ride ride = rides.remove(player.getUniqueId());
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3_000L);
        setGrace(player, 8_000L);
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
        if (arrived && ride != null) {
            player.setVelocity(new Vector(0, 0.1, 0));
            player.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, SoundCategory.PLAYERS, 0.9f, 0.9f);
            isle.pads().rodeUpdraft(player, ride.draft);
        }
    }

    public boolean riding(Player player) {
        return rides.containsKey(player.getUniqueId());
    }

    public int riders() {
        return rides.size();
    }

    // ------------------------------------------------------------------ safety

    /** Every 10 ticks: vents, safe footing, rescue. */
    void safetyTick(List<Player> onIsle) {
        ventTick++;
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            lastOnIsle.put(player.getUniqueId(), now);
            recordSafe(player);
        }
        if (ventTick % 2 == 0) {
            puffVents(onIsle);
        }
        if (isle.config().rescue()) {
            rescue();
        }
    }

    private void recordSafe(Player player) {
        Location at = player.getLocation();
        if (!player.isOnGround() || player.isInWater() || at.getY() < isle.config().floorY() + 40
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block below = at.clone().subtract(0, 0.2, 0).getBlock();
        Material type = below.getType();
        if (!type.isSolid() || type == Material.MAGMA_BLOCK || type == Material.SLIME_BLOCK || type.name().endsWith("_LEAVES")) {
            return;
        }
        safe.put(player.getUniqueId(), at.clone());
    }

    private void puffVents(List<Player> onIsle) {
        World world = isle.config().world();
        if (world == null || onIsle.isEmpty()) {
            return;
        }
        for (OriginConfig.Updraft d : isle.config().updrafts().values()) {
            Location vent = new Location(world, d.floor()[0], d.floor()[1], d.floor()[2]);
            for (Player player : onIsle) {
                if (player.getLocation().distanceSquared(vent) < 28 * 28) {
                    player.spawnParticle(Particle.CLOUD, vent.clone().add(0, 0.2, 0), 4, 0.35, 0.05, 0.35, 0.02);
                    player.spawnParticle(Particle.CLOUD, vent.clone().add(0, 1.8, 0), 2, 0.2, 0.7, 0.2, 0.05);
                    player.spawnParticle(Particle.END_ROD, vent.clone().add(0, 2.5, 0), 1, 0.2, 1.2, 0.2, 0.0);
                }
            }
        }
    }

    private void rescue() {
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        int floor = isle.config().floorY();
        long now = System.currentTimeMillis();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getY() >= floor || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            int[] fp = isle.config().footprint();
            if (at.getX() < fp[0] || at.getX() > fp[1] || at.getZ() < fp[2] || at.getZ() > fp[3]) {
                continue;
            }
            Long seen = lastOnIsle.get(player.getUniqueId());
            if (seen == null || now - seen > 30_000L) {
                continue;
            }
            Location back = safe.get(player.getUniqueId());
            if (back == null || back.getWorld() != world) {
                OriginConfig.District capital = isle.config().district("capital");
                back = capital == null ? null : isle.config().location(capital.anchor());
            }
            if (back == null) {
                continue;
            }
            back = back.clone();
            back.setYaw(at.getYaw());
            back.setPitch(at.getPitch());
            player.setFallDistance(0f);
            player.setVelocity(new Vector());
            player.teleport(back);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
            grace(player, 4_000L);
            player.playSound(back, Sound.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.7f, 1.4f);
            OriginText.bar(player, "§f≋ The wind puts you back on the island.");
        }
    }

    /** Extends fall grace to at least {@code ms} from now. */
    public void grace(Player player, long ms) {
        graceUntil.merge(player.getUniqueId(), System.currentTimeMillis() + ms, Math::max);
    }

    /** Sets fall grace to exactly {@code ms} from now (ends of rides shorten the long in-ride windows). */
    public void setGrace(Player player, long ms) {
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + ms);
    }

    public boolean inGrace(Player player) {
        Long until = graceUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public Location lastSafe(Player player) {
        Location at = safe.get(player.getUniqueId());
        return at == null ? null : at.clone();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        World world = isle.config().world();
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        if (inGrace(player) || rides.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFallDistance(0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (rides.containsKey(event.getPlayer().getUniqueId())) {
            finish(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Ride ride = rides.remove(id);
        if (ride != null) {
            // Mid-updraft logout: put them on the ledge they were heading for, softly.
            player.setFallDistance(0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, false, true));
            Location top = isle.config().location(ride.draft.top());
            if (top != null && top.getWorld() == player.getWorld()) {
                top.setYaw(player.getLocation().getYaw());
                player.teleport(top);
            }
        }
        graceUntil.remove(id);
        cooldownUntil.remove(id);
        safe.remove(id);
        lastOnIsle.remove(id);
    }

    /** Stop / live disable: riders get slow falling; landing grace set by other systems is kept. */
    void clear() {
        for (UUID id : rides.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setFallDistance(0f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 15, 0, false, false, true));
            }
        }
        rides.clear();
        cooldownUntil.clear();
    }
}
