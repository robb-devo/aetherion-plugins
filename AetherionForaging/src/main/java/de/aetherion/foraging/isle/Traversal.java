package de.aetherion.foraging.isle;

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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Getting around a 500-block island that is also 170 blocks tall.
 * <ul>
 *   <li><b>Updrafts</b> — measured shafts where the air is clear from a valley floor to the shelf above
 *   ({@code forage-isle.yml → updrafts}). Step onto one: the wind carries you up the column, then glides
 *   you onto the ledge. A puff of cloud marks each vent when someone is near.</li>
 *   <li><b>Fall-catch</b> — on the isle a fall never kills. A lethal drop leaves you on one heart and
 *   winded; Wind Step V and a recent updraft take the sting out entirely. Wind Step I halves fall damage.</li>
 *   <li><b>Edge rescue</b> — drop off the island (below {@code tuning.fall-catch-floor-y} inside the
 *   footprint) and the wind puts you back on your last safe footing.</li>
 * </ul>
 */
public final class Traversal implements Listener {

    private static final long LIFT_GRACE_MS = 8_000L;
    private static final int RIDE_TIMEOUT_TICKS = 20 * 16;

    private final ForageIsle isle;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Location> safe = new HashMap<>();
    /** Last time each player stood on the isle — only they get rescued (nobody is pulled up from below). */
    private final Map<UUID, Long> lastOnIsle = new HashMap<>();
    private int tick;

    private static final class Ride {
        ForageConfig.Updraft draft;
        int age;
        boolean gliding;
    }

    Traversal(ForageIsle isle) {
        this.isle = isle;
    }

    /** Every tick. Cheap: a handful of distance checks per forager on the isle. */
    void tick(List<Player> onIsle) {
        tick++;
        if (!rides.isEmpty()) {
            for (Map.Entry<UUID, Ride> e : new HashMap<>(rides).entrySet()) {
                Player player = org.bukkit.Bukkit.getPlayer(e.getKey());
                if (player == null || !player.isOnline()) {
                    rides.remove(e.getKey());
                    continue;
                }
                ride(player, e.getValue());
            }
        }
        if (tick % 4 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            if (rides.containsKey(player.getUniqueId()) || player.isSneaking() || player.isFlying()
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Long cool = cooldownUntil.get(player.getUniqueId());
            if (cool != null && now < cool) {
                continue;
            }
            Location feet = player.getLocation();
            for (ForageConfig.Updraft draft : isle.config().updrafts().values()) {
                double dx = feet.getX() - draft.fx();
                double dz = feet.getZ() - draft.fz();
                double dy = feet.getY() - draft.fy();
                if (dx * dx + dz * dz <= 1.3d * 1.3d && dy > -1.0d && dy < 2.0d) {
                    begin(player, draft);
                    break;
                }
            }
        }
        if (tick % 20 == 0) {
            puffVents(onIsle);
            recordSafe(onIsle);
        }
        if (tick % 10 == 0) {
            rescue();
        }
    }

    // ------------------------------------------------------------------ updrafts

    public void begin(Player player, ForageConfig.Updraft draft) {
        Ride ride = new Ride();
        ride.draft = draft;
        rides.put(player.getUniqueId(), ride);
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + LIFT_GRACE_MS + 12_000L);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.2f);
        ForageText.bar(player, "§f≋ " + draft.display() + " §8· §7hold on… §8(sneak to let go)");
    }

    private void ride(Player player, Ride ride) {
        ride.age++;
        ForageConfig.Updraft d = ride.draft;
        Location at = player.getLocation();
        if (ride.age > RIDE_TIMEOUT_TICKS || player.isSneaking() || player.getWorld() != isle.isleWorld()) {
            finish(player, false);
            return;
        }
        double speed = Math.max(0.5d, isle.config().tuning("updraft-speed", 1.05d));
        if (!ride.gliding) {
            double toTop = d.ty() + 1.5d - at.getY();
            if (toTop <= 0) {
                ride.gliding = true;
            } else {
                // Keep the rider centred on the shaft while it rises.
                Vector center = new Vector(d.fx() - at.getX(), 0, d.fz() - at.getZ()).multiply(0.25d);
                player.setVelocity(new Vector(center.getX(), Math.min(speed, 0.25d + toTop * 0.2d), center.getZ()));
                player.setFallDistance(0f);
                if (ride.age % 2 == 0) {
                    at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, -0.3, 0), 2, 0.25, 0.1, 0.25, 0.01);
                }
                return;
            }
        }
        Vector flat = new Vector(d.tx() - at.getX(), 0, d.tz() - at.getZ());
        double dist = flat.length();
        if (dist < 0.9d) {
            finish(player, true);
            return;
        }
        Vector v = flat.normalize().multiply(Math.min(0.5d, 0.12d + dist * 0.12d));
        v.setY(at.getY() < d.ty() + 0.8d ? 0.25d : 0.02d);
        player.setVelocity(v);
        player.setFallDistance(0f);
    }

    private void finish(Player player, boolean arrived) {
        Ride ride = rides.remove(player.getUniqueId());
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3_000L);
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + LIFT_GRACE_MS);
        player.setFallDistance(0f);
        ForageProfile profile = isle.profiles().get(player);
        int slowTicks = Woodwright.slowFallAfterLift(profile) ? 120 : 40;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowTicks, 0, false, false, true));
        if (arrived && ride != null) {
            player.setVelocity(new Vector(0, 0.1, 0));
            player.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, SoundCategory.PLAYERS, 0.9f, 0.9f);
        }
    }

    private void puffVents(List<Player> onIsle) {
        World world = isle.isleWorld();
        if (world == null || onIsle.isEmpty()) {
            return;
        }
        for (ForageConfig.Updraft d : isle.config().updrafts().values()) {
            Location vent = new Location(world, d.fx(), d.fy(), d.fz());
            boolean near = false;
            for (Player player : onIsle) {
                if (player.getLocation().distanceSquared(vent) < 24 * 24) {
                    near = true;
                    break;
                }
            }
            if (near) {
                world.spawnParticle(Particle.CLOUD, vent.clone().add(0, 0.2, 0), 4, 0.35, 0.05, 0.35, 0.02);
                world.spawnParticle(Particle.CLOUD, vent.clone().add(0, 1.6, 0), 2, 0.2, 0.6, 0.2, 0.04);
            }
        }
    }

    // ------------------------------------------------------------------ falling

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isle.onIsle(player.getLocation())) {
            return;
        }
        Long grace = graceUntil.get(player.getUniqueId());
        if (grace != null && grace > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }
        ForageProfile profile = isle.profiles().get(player);
        double damage = event.getDamage() * Woodwright.fallFactor(profile);
        if (damage >= player.getHealth()) {
            if (Woodwright.alwaysCaught(profile)) {
                event.setCancelled(true);
                ForageText.bar(player, "§a≋ The canopy caught you. §8(Wind Step V)");
                return;
            }
            damage = Math.max(0.0d, player.getHealth() - 2.0d);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 1, false, false, true));
            ForageText.bar(player, "§e≋ Caught by the branches §8· §7winded. §8(Updrafts and Wind Step help.)");
        }
        event.setDamage(damage);
    }

    private void recordSafe(List<Player> onIsle) {
        int floor = isle.config().tuningInt("fall-catch-floor-y", 70);
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            lastOnIsle.put(player.getUniqueId(), now);
            Location at = player.getLocation();
            if (at.getY() < floor + 2 || !player.isOnGround() || player.isInWater()) {
                continue;
            }
            Block below = at.clone().subtract(0, 0.2, 0).getBlock();
            Material type = below.getType();
            if (!type.isSolid() || type == Material.MAGMA_BLOCK || type.name().endsWith("_LEAVES")) {
                continue;
            }
            safe.put(player.getUniqueId(), at.clone());
        }
    }

    private void rescue() {
        World world = isle.isleWorld();
        if (world == null) {
            return;
        }
        int floor = isle.config().tuningInt("fall-catch-floor-y", 70);
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getY() >= floor || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!isle.inFootprintXZ(at)) {
                continue;
            }
            // Only someone who just fell off the isle — never a player who was down there anyway.
            Long seen = lastOnIsle.get(player.getUniqueId());
            if (seen == null || System.currentTimeMillis() - seen > 30_000L) {
                continue;
            }
            Location back = safe.get(player.getUniqueId());
            if (back == null || back.getWorld() != world) {
                Landmark landing = isle.config().landmark("landing");
                back = landing == null ? null : landing.anchor(world);
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
            graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + 4_000L);
            player.playSound(back, Sound.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.7f, 1.4f);
            ForageText.bar(player, "§f≋ The wind puts you back on the island.");
        }
    }

    /** Teleport target used by DEV / rescue when nothing is recorded. */
    public Location lastSafe(Player player) {
        Location at = safe.get(player.getUniqueId());
        return at == null ? null : at.clone();
    }

    public int riding() {
        return rides.size();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rides.remove(id);
        graceUntil.remove(id);
        cooldownUntil.remove(id);
        safe.remove(id);
        lastOnIsle.remove(id);
    }

    void clear() {
        rides.clear();
        graceUntil.clear();
        cooldownUntil.clear();
    }
}
