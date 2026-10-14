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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scripted flights: the two unused arch pads of the map (southeast and southwest skyways) and the glide rings
 * on the Skyreach summit and the Crag peak. Each flight follows a terrain-checked path (validated against the
 * map when it was measured), smoothed with corner cutting and walked at a constant speed — the server
 * re-asserts the velocity every tick, so WASD can't knock you off course and a 600-block ride lands on the plaza.
 *
 * <p>If the live world has grown something new in the way, the flight notices it's stuck and lets go with slow
 * falling instead of dragging you through the wall.
 */
public final class OriginFlight implements Listener {

    private static final double MAX_STEP = 3.4d;

    private static final class Active {
        OriginConfig.Flight flight;
        List<Vector> points;
        int index;
        int progress;
        int stuck;
        int age;
    }

    private final OriginIsle isle;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    OriginFlight(OriginIsle isle) {
        this.isle = isle;
    }

    /** Every 4 ticks: pads and rings. */
    void triggerTick(List<Player> onIsle) {
        if (isle.config().flights().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            UUID id = player.getUniqueId();
            if (active.containsKey(id) || isle.traversal().riding(player) || player.isSneaking() || player.isFlying()
                    || player.isGliding() || player.isInsideVehicle() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Long cool = cooldownUntil.get(id);
            if (cool != null && now < cool) {
                continue;
            }
            Location at = player.getLocation();
            Block below = at.clone().subtract(0, 0.2, 0).getBlock();
            if (below.getType() != Material.SLIME_BLOCK) {
                below = at.getBlock().getRelative(0, -1, 0);
            }
            for (OriginConfig.Flight flight : isle.config().flights().values()) {
                boolean hit;
                if (flight.trigger() == OriginConfig.Trigger.PAD) {
                    hit = below.getType() == Material.SLIME_BLOCK && flight.padContains(below.getX(), below.getY(), below.getZ());
                } else {
                    double[] c = flight.at();
                    if (c == null) {
                        continue;
                    }
                    double dx = at.getX() - c[0];
                    double dz = at.getZ() - c[2];
                    double dy = at.getY() - c[1];
                    hit = dx * dx + dz * dz <= flight.radius() * flight.radius() && dy > -0.8d && dy < 1.6d;
                }
                if (hit) {
                    begin(player, flight);
                    break;
                }
            }
        }
    }

    public boolean begin(Player player, OriginConfig.Flight flight) {
        World world = isle.config().world();
        if (world == null || !world.equals(player.getWorld())) {
            return false;
        }
        List<Vector> raw = new ArrayList<>();
        raw.add(player.getLocation().toVector().add(new Vector(0, 0.3, 0)));
        for (int i = 1; i < flight.path().size(); i++) {
            double[] p = flight.path().get(i);
            raw.add(new Vector(p[0], p[1], p[2]));
        }
        Active flying = new Active();
        flying.flight = flight;
        flying.points = resample(chaikin(raw, 3), flight.speed());
        if (flying.points.size() < 2) {
            return false;
        }
        active.put(player.getUniqueId(), flying);
        player.setSprinting(false);
        if (player.isFlying()) {
            player.setFlying(false);
        }
        isle.traversal().grace(player, 60_000L);
        player.setFallDistance(0f);
        player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP_SMALL, SoundCategory.PLAYERS, 1f, 0.55f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.5f, 1.1f);
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.35f, 1.3f);
        OriginText.card(player, "§b⇗ §f" + flight.name(), flight.to().isBlank() ? "§7hold on" : "§7to §f" + flight.to(), 30);
        return true;
    }

    /** Every tick. */
    void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Active> e : new HashMap<>(active).entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) {
                end(e.getKey(), null, false);
                continue;
            }
            step(player, e.getValue());
        }
    }

    private void step(Player player, Active a) {
        a.age++;
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null || !world.equals(isle.config().world()) || player.isDead()) {
            end(player.getUniqueId(), player, false);
            return;
        }
        int last = a.points.size() - 1;
        Vector here = at.toVector();
        // Latency-proof: find where on the path the player actually is, then aim two points ahead.
        int best = a.index;
        double bestD = Double.MAX_VALUE;
        for (int i = Math.max(0, a.index - 3); i <= Math.min(last, a.index + 8); i++) {
            double d = here.distanceSquared(a.points.get(i));
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best <= a.progress) {
            a.stuck++;
        } else {
            a.stuck = 0;
            a.progress = best;
        }
        if (bestD > 64.0d) {
            a.stuck += 2;
        }
        if (a.stuck > 30) {
            end(player.getUniqueId(), player, false);
            return;
        }
        a.index = Math.max(a.index, best);
        if (a.index >= last - 1 && bestD < 4.0d) {
            end(player.getUniqueId(), player, true);
            return;
        }
        if (a.age > a.points.size() * 3 + 100) {
            end(player.getUniqueId(), player, false);
            return;
        }
        // clone(): Bukkit's Vector#subtract mutates — never touch the stored path.
        Vector v = a.points.get(Math.min(best + 2, last)).clone().subtract(here);
        double len = v.length();
        if (len > MAX_STEP) {
            v.multiply(MAX_STEP / len);
        }
        player.setVelocity(v);
        player.setFallDistance(0f);
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 1, 0.15, 0.1, 0.15, 0.0);
        if (a.age % 3 == 0) {
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.9, 0), 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (a.age % 25 == 0) {
            player.playSound(at, Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.3f, 1.25f);
        }
    }

    private void end(UUID id, Player player, boolean arrived) {
        Active a = active.remove(id);
        cooldownUntil.put(id, System.currentTimeMillis() + 4_000L);
        if (player == null) {
            return;
        }
        player.setFallDistance(0f);
        // Replace (not extend) the long in-flight window with a short landing window.
        isle.traversal().setGrace(player, arrived ? 5_000L : 12_000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, arrived ? 50 : 160, 0, false, false, true));
        if (a == null) {
            return;
        }
        if (arrived) {
            player.setVelocity(new Vector(0, -0.1, 0));
            Location at = player.getLocation();
            if (at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.1, 0), 16, 0.8, 0.05, 0.8, 0.02);
            }
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            isle.pads().rodeFlight(player, a.flight);
        } else {
            OriginText.bar(player, "§f≋ The wind lets go — slow down…");
        }
    }

    public boolean flying(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public int flyers() {
        return active.size();
    }

    // ------------------------------------------------------------------ path helpers

    static List<Vector> chaikin(List<Vector> pts, int iterations) {
        List<Vector> cur = pts;
        for (int it = 0; it < iterations; it++) {
            List<Vector> out = new ArrayList<>();
            out.add(cur.get(0).clone());
            for (int i = 0; i + 1 < cur.size(); i++) {
                Vector a = cur.get(i);
                Vector b = cur.get(i + 1);
                out.add(a.clone().multiply(0.75d).add(b.clone().multiply(0.25d)));
                out.add(a.clone().multiply(0.25d).add(b.clone().multiply(0.75d)));
            }
            out.add(cur.get(cur.size() - 1).clone());
            cur = out;
        }
        return cur;
    }

    /** Evenly spaced points, {@code step} blocks apart (one per tick). */
    static List<Vector> resample(List<Vector> pts, double step) {
        List<Vector> out = new ArrayList<>();
        if (pts.isEmpty()) {
            return out;
        }
        out.add(pts.get(0).clone());
        double carry = 0.0d;
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vector a = pts.get(i);
            Vector b = pts.get(i + 1);
            double seg = a.distance(b);
            if (seg < 1.0e-6) {
                continue;
            }
            double pos = step - carry;
            while (pos <= seg) {
                out.add(a.clone().add(b.clone().subtract(a).multiply(pos / seg)));
                pos += step;
            }
            carry = seg - (pos - step);
        }
        Vector end = pts.get(pts.size() - 1);
        if (out.get(out.size() - 1).distanceSquared(end) > 0.01d) {
            out.add(end.clone());
        }
        return out;
    }

    // ------------------------------------------------------------------ guards

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && active.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (active.containsKey(event.getPlayer().getUniqueId())
                && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            end(event.getPlayer().getUniqueId(), event.getPlayer(), false);
        }
    }

    /** Logging out mid-glide must not mean waking up 170 blocks in the air: land them first. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Active a = active.remove(id);
        cooldownUntil.remove(id);
        if (a == null) {
            return;
        }
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 30, 0, false, false, true));
        Location land = isle.config().location(a.flight.end());
        if (land != null && land.getWorld() == player.getWorld()) {
            land.setYaw(player.getLocation().getYaw());
            player.teleport(land);
        }
    }

    void clear() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(id);
            end(id, player, false);
        }
        active.clear();
    }
}
