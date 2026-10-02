package de.aetherion.guilds.logistics;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Visible belt cargo: a few ItemDisplays per island that glide tile to tile using client-side teleport
 * interpolation (one teleport per tile, no per-tick motion packets). Purely cosmetic; the real accounting is
 * in {@link LogisticsService}. Hard caps per island and server-wide; nothing is saved (non-persistent).
 */
final class CargoVisuals {

    static final String TAG = "aeg_cargo";
    private static final int STEP_TICKS = 4;

    private static final class Cargo {
        final String hostKey;
        final ItemDisplay entity;
        final List<Location> points;
        int index;
        int clock;

        Cargo(String hostKey, ItemDisplay entity, List<Location> points) {
            this.hostKey = hostKey;
            this.entity = entity;
            this.points = points;
        }
    }

    private final List<Cargo> cargo = new ArrayList<>();
    private record Pending(String hostKey, List<Location> points, ItemStack icon, long at) {
    }
    private final List<Pending> pending = new ArrayList<>();
    private long clock;
    private final Map<String, Integer> perHost = new HashMap<>();
    private int perHostCap = 36;
    private int globalCap = 240;

    void caps(int perHostCap, int globalCap) {
        this.perHostCap = Math.max(0, perHostCap);
        this.globalCap = Math.max(0, globalCap);
    }

    boolean canSpawn(String hostKey) {
        return cargo.size() < globalCap && perHost.getOrDefault(hostKey, 0) < perHostCap;
    }

    void spawn(String hostKey, List<Location> points, ItemStack icon) {
        if (points.size() < 2 || !canSpawn(hostKey)) {
            return;
        }
        Location start = points.get(0);
        World world = start.getWorld();
        if (world == null || !world.isChunkLoaded(start.getBlockX() >> 4, start.getBlockZ() >> 4)) {
            return;
        }
        ItemDisplay display = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new AxisAngle4f(),
                    new Vector3f(0.95f, 0.95f, 0.95f), new AxisAngle4f()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(STEP_TICKS);
            d.setViewRange(0.6f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        cargo.add(new Cargo(hostKey, display, points));
        perHost.merge(hostKey, 1, Integer::sum);
    }

    /** Spawn after {@code delay} ticks (so a busy belt gets evenly spaced goods, not a clump). */
    void spawnLater(String hostKey, List<Location> points, ItemStack icon, int delay) {
        if (delay <= 0) {
            spawn(hostKey, points, icon);
            return;
        }
        if (pending.size() < 128) {
            pending.add(new Pending(hostKey, points, icon, clock + delay));
        }
    }

    void tick() {
        clock++;
        if (!pending.isEmpty()) {
            Iterator<Pending> due = pending.iterator();
            while (due.hasNext()) {
                Pending p = due.next();
                if (p.at() <= clock) {
                    due.remove();
                    spawn(p.hostKey(), p.points(), p.icon());
                }
            }
        }
        Iterator<Cargo> it = cargo.iterator();
        while (it.hasNext()) {
            Cargo c = it.next();
            if (!c.entity.isValid()) {
                it.remove();
                release(c.hostKey);
                continue;
            }
            if (++c.clock % STEP_TICKS != 0) {
                continue;
            }
            c.index++;
            if (c.index >= c.points.size()) {
                Location end = c.entity.getLocation();
                World world = end.getWorld();
                if (world != null) {
                    world.spawnParticle(Particle.POOF, end, 2, 0.1, 0.1, 0.1, 0.01);
                }
                c.entity.remove();
                it.remove();
                release(c.hostKey);
                continue;
            }
            c.entity.teleport(c.points.get(c.index));
        }
    }

    void clearHost(String hostKey) {
        pending.removeIf(p -> p.hostKey().equals(hostKey));
        Iterator<Cargo> it = cargo.iterator();
        while (it.hasNext()) {
            Cargo c = it.next();
            if (c.hostKey.equals(hostKey)) {
                c.entity.remove();
                it.remove();
            }
        }
        perHost.remove(hostKey);
    }

    void clearAll() {
        pending.clear();
        for (Cargo c : cargo) {
            c.entity.remove();
        }
        cargo.clear();
        perHost.clear();
    }

    int size() {
        return cargo.size();
    }

    private void release(String hostKey) {
        perHost.computeIfPresent(hostKey, (k, v) -> v <= 1 ? null : v - 1);
    }

    static void purgeLeftovers(World world) {
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntitiesByClass(org.bukkit.entity.Display.class)) {
            java.util.Set<String> tags = entity.getScoreboardTags();
            if (tags.contains(TAG) || tags.contains(LogisticsService.FX_TAG) || tags.contains(BeltVisuals.TAG)
                    || tags.contains(MachineFx.TAG)) {
                entity.remove();
            }
        }
    }
}
