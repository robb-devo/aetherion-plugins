package de.aetherion.fishing.isle;

import de.aetherion.fishing.FishingSkills;
import de.aetherion.fishing.LureHead;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The shoal: one boiling patch of water somewhere on the lake. Fish leap out of it, bites come in
 * half the time, rare fish crowd in. It is fished out after a couple of dozen catches (or four
 * minutes) and turns up in another water — so anglers chase it round the isle.
 *
 * <p>Spots are measured from the schematic: deep lake water 5–11 blocks from a pier or shore,
 * so it is always castable.
 */
public final class Shoals {

    public record Spot(String water, Location at) {
    }

    private final FishIsle isle;
    private final NamespacedKey leapKey;
    private final List<Spot> spots = new ArrayList<>();
    private final Map<UUID, Long> tideTips = new ConcurrentHashMap<>();
    private BukkitTask task;
    private Spot current;
    private int stock;
    private long endsAtMs;
    private long nextAtMs;
    private int ticks;

    Shoals(FishIsle isle) {
        this.isle = isle;
        this.leapKey = new NamespacedKey(isle.plugin(), "shoal_leap");
    }

    void start() {
        reload();
        purgeLeapers();
        nextAtMs = System.currentTimeMillis() + 20_000L;
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        current = null;
        purgeLeapers();
    }

    void reload() {
        spots.clear();
        World world = LakeWorld.world();
        ConfigurationSection root = isle.plugin().getConfig().getConfigurationSection("shoals.spots");
        if (world == null || root == null) {
            return;
        }
        for (String water : root.getKeys(false)) {
            for (String raw : root.getStringList(water)) {
                double[] v = LakeText.numbers(raw, 3);
                if (v != null) {
                    spots.add(new Spot(water, new Location(world, v[0], v[1], v[2])));
                }
            }
        }
    }

    private boolean enabled() {
        return isle.plugin().getConfig().getBoolean("shoals.enabled", true);
    }

    private int lifetimeSeconds() {
        return Math.max(60, isle.plugin().getConfig().getInt("shoals.lifetime-seconds", 240));
    }

    private int startingStock() {
        return Math.max(6, isle.plugin().getConfig().getInt("shoals.stock", 24));
    }

    double radius() {
        return Math.max(3.0d, isle.plugin().getConfig().getDouble("shoals.radius", 6.0d));
    }

    // ------------------------------------------------------------------ queries

    public Spot current() {
        return current;
    }

    public int stock() {
        return current == null ? 0 : stock;
    }

    public long secondsLeft() {
        return current == null ? 0L : Math.max(0L, (endsAtMs - System.currentTimeMillis()) / 1000L);
    }

    /** True when {@code hook} sits in the live shoal. */
    public boolean inShoal(Location hook) {
        Spot spot = current;
        if (spot == null || hook == null || hook.getWorld() == null || !hook.getWorld().equals(spot.at().getWorld())) {
            return false;
        }
        double dx = hook.getX() - spot.at().getX();
        double dz = hook.getZ() - spot.at().getZ();
        double r = radius();
        return dx * dx + dz * dz <= r * r && Math.abs(hook.getY() - spot.at().getY()) < 4.0d;
    }

    /** Wait factor inside the shoal — Tide Reader makes it quicker still. */
    public double waitFactor(Player player) {
        return 0.5d - 0.12d * FishingSkills.power(player, FishingSkills.TIDE_READER);
    }

    public String waterName() {
        Spot spot = current;
        if (spot == null) {
            return null;
        }
        Waters.Water water = isle.waters().byId(spot.water());
        return water == null ? spot.water() : water.name();
    }

    public String statusLine() {
        if (current == null) {
            return "§7No shoal right now §8(next in §f" + LakeText.clock(Math.max(0L, (nextAtMs - System.currentTimeMillis()) / 1000L)) + "§8)";
        }
        return "§bShoal §8· §f" + waterName() + " §8· §f" + stock + " §7fish left · §f" + LakeText.clock(secondsLeft());
    }

    // ------------------------------------------------------------------ lifecycle

    /** A catch was landed in the shoal. */
    void onCatch(Player player) {
        if (current == null) {
            return;
        }
        stock--;
        if (stock == 5) {
            for (Player visitor : LakeWorld.visitors()) {
                if (visitor.getLocation().distanceSquared(current.at()) < 40.0d * 40.0d) {
                    LakeText.bar(visitor, "§bThe shoal is thinning out §8· §f5 §7fish left");
                }
            }
        }
        if (stock <= 0) {
            scatter("§3The shoal in §f" + waterName() + " §3is fished out. §7It'll gather somewhere else.");
        }
    }

    /** DEV / timer: raise a shoal at {@code spot} (or a random configured spot). */
    public String raise(Spot spot) {
        if (spot == null) {
            spot = pick();
        }
        if (spot == null) {
            return "§cNo shoal spots configured (shoals.spots) or the isle world is missing.";
        }
        current = spot;
        stock = startingStock();
        endsAtMs = System.currentTimeMillis() + lifetimeSeconds() * 1000L;
        String line = "§b≋ A shoal is boiling in §f" + waterName() + "§b. §8Old Finn can point you.";
        for (Player visitor : LakeWorld.visitors()) {
            visitor.sendMessage(line);
            visitor.playSound(visitor.getLocation(), Sound.ENTITY_FISH_SWIM, SoundCategory.AMBIENT, 0.6f, 0.8f);
        }
        return "§aShoal raised in §f" + waterName() + " §8(" + spot.at().getBlockX() + " " + spot.at().getBlockZ() + ")";
    }

    /** DEV: raise a shoal on the water spot nearest to {@code at}. */
    public String raiseNear(Location at) {
        Spot best = null;
        double bestDist = Double.MAX_VALUE;
        for (Spot spot : spots) {
            if (at.getWorld() == null || !at.getWorld().equals(spot.at().getWorld())) {
                continue;
            }
            double distance = spot.at().distanceSquared(at);
            if (distance < bestDist) {
                bestDist = distance;
                best = spot;
            }
        }
        return raise(best);
    }

    public String clear() {
        if (current == null) {
            return "§7No shoal running.";
        }
        scatter("§3The shoal breaks up.");
        return "§eShoal cleared.";
    }

    private void scatter(String line) {
        Spot was = current;
        current = null;
        nextAtMs = System.currentTimeMillis() + 20_000L;
        if (was == null) {
            return;
        }
        World world = was.at().getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SPLASH, was.at().clone().add(0, 0.4, 0), 40, 2.0, 0.2, 2.0, 0.1);
        }
        for (Player visitor : LakeWorld.visitors()) {
            if (visitor.getLocation().distanceSquared(was.at()) < 64.0d * 64.0d) {
                visitor.sendMessage(line);
            }
        }
    }

    private Spot pick() {
        if (spots.isEmpty()) {
            return null;
        }
        List<Spot> pool = new ArrayList<>();
        for (Spot spot : spots) {
            // Never in the same water twice running — the point is to move.
            if (current == null || !spot.water().equals(current.water())) {
                pool.add(spot);
            }
        }
        if (pool.isEmpty()) {
            pool = spots;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void tick() {
        ticks += 10;
        long now = System.currentTimeMillis();
        if (current == null) {
            if (enabled() && now >= nextAtMs && !LakeWorld.anglers().isEmpty()) {
                raise(null);
            }
            return;
        }
        if (now >= endsAtMs) {
            scatter("§3The shoal in §f" + waterName() + " §3drifts apart.");
            return;
        }
        Location at = current.at();
        World world = at.getWorld();
        if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double r = radius() * 0.7d;
        for (int i = 0; i < 2; i++) {
            Location puff = at.clone().add(rng.nextDouble(-r, r), 0.15, rng.nextDouble(-r, r));
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, puff, 4, 0.15, 0.05, 0.15, 0.02);
            world.spawnParticle(Particle.SPLASH, puff.add(0, 0.2, 0), 3, 0.3, 0.02, 0.3, 0.02);
        }
        if (ticks % 40 == 0) {
            leap(at);
        }
        tideArrows();
    }

    /** A fish arcs out of the shoal and back in — the tell you can see from the shore. */
    private void leap(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double r = radius() * 0.6d;
        Location from = center.clone().add(rng.nextDouble(-r, r), 0.1, rng.nextDouble(-r, r));
        double heading = rng.nextDouble(Math.PI * 2.0d);
        double dx = Math.cos(heading) * 2.2d;
        double dz = Math.sin(heading) * 2.2d;
        ItemDisplay fish = world.spawn(from, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setItemStack(LureHead.random());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setTeleportDuration(2);
            spawned.setViewRange(1.2f);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.55f, 0.55f, 0.55f), new AxisAngle4f()));
            spawned.getPersistentDataContainer().set(leapKey, PersistentDataType.BYTE, (byte) 1);
        });
        world.spawnParticle(Particle.SPLASH, from, 12, 0.2, 0.05, 0.2, 0.08);
        int[] step = {0};
        final int steps = 14;
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), task -> {
            step[0]++;
            if (!fish.isValid() || step[0] > steps) {
                if (fish.isValid()) {
                    world.spawnParticle(Particle.SPLASH, fish.getLocation(), 14, 0.2, 0.05, 0.2, 0.08);
                    world.playSound(fish.getLocation(), Sound.ENTITY_FISH_SWIM, SoundCategory.AMBIENT, 0.5f, 1.3f);
                    fish.remove();
                }
                task.cancel();
                return;
            }
            double t = step[0] / (double) steps;
            double lift = Math.sin(Math.PI * t) * 1.6d;
            Location next = from.clone().add(dx * t, lift, dz * t);
            next.setYaw((float) Math.toDegrees(heading) - 90.0f);
            next.setPitch((float) (-60.0d * Math.cos(Math.PI * t)));
            fish.teleport(next);
        }, 1L, 1L);
    }

    /** Tide Reader: holding a rod on the isle, idle, a wake arrow points at the shoal. */
    private void tideArrows() {
        Spot spot = current;
        if (spot == null || ticks % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : LakeWorld.anglers()) {
            if (player.getInventory().getItemInMainHand().getType() != org.bukkit.Material.FISHING_ROD
                    || isle.casting(player) || !isle.line().quiet(player.getUniqueId())
                    || !FishingSkills.has(player, FishingSkills.TIDE_READER)) {
                continue;
            }
            if (!player.getWorld().equals(spot.at().getWorld())) {
                continue;
            }
            double distance = player.getLocation().distance(spot.at());
            if (distance < radius() + 10.0d) {
                continue;
            }
            Long last = tideTips.get(player.getUniqueId());
            if (last != null && now - last < 900L) {
                continue;
            }
            tideTips.put(player.getUniqueId(), now);
            LakeText.bar(player, "§3≋ Tide Reader §8· §b" + LakeText.arrow(player, spot.at()) + " §fshoal in "
                    + waterName() + " §8· §7" + (int) Math.round(distance) + "m");
        }
    }

    private void purgeLeapers() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (entity.getPersistentDataContainer().has(leapKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    public List<Spot> spots() {
        return spots;
    }
}
