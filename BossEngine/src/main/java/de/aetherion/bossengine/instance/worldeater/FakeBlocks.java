package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client-side block paint: what the players see on the floor, never what the server has.
 * Used for readable, harmless surface language (cracks racing across the plaza, the missing-texture
 * rot of an unmade world, a biome spat onto the island). Only ever paints over solid blocks, so the
 * client and the server always agree about where you can stand. Every cell reverts on expiry,
 * on {@link #clear()}, and for anyone who walks out of view.
 */
public final class FakeBlocks {

    private static final class Entry {
        final Location loc;
        final BlockData data;
        long until;

        Entry(Location loc, BlockData data, long until) {
            this.loc = loc;
            this.data = data;
            this.until = until;
        }
    }

    private final World world;
    private final Supplier<List<Player>> viewers;
    private final Map<Long, Entry> fakes = new HashMap<>();
    private final Set<UUID> painted = new HashSet<>();
    private long clock;

    public FakeBlocks(World world, Supplier<List<Player>> viewers) {
        this.world = world;
        this.viewers = viewers;
    }

    /** Paints (x, y, z) as {@code data} for {@code ticks}. Air cells are left alone. */
    public boolean show(int x, int y, int z, BlockData data, int ticks) {
        if (world == null || data == null) {
            return false;
        }
        Location loc = new Location(world, x, y, z);
        if (!loc.getBlock().getType().isSolid()) {
            return false;
        }
        long key = SiteLayout.key(x, y, z);
        Entry e = fakes.get(key);
        if (e == null) {
            e = new Entry(loc, data, clock + ticks);
            fakes.put(key, e);
        } else {
            fakes.put(key, new Entry(loc, data, Math.max(e.until, clock + ticks)));
        }
        for (Player p : current()) {
            p.sendBlockChange(loc, data);
            painted.add(p.getUniqueId());
        }
        return true;
    }

    public boolean painted(int x, int y, int z) {
        return fakes.containsKey(SiteLayout.key(x, y, z));
    }

    public int size() {
        return fakes.size();
    }

    public void tick() {
        clock++;
        if (fakes.isEmpty()) {
            return;
        }
        List<Player> view = null;
        for (Iterator<Map.Entry<Long, Entry>> it = fakes.entrySet().iterator(); it.hasNext(); ) {
            Entry e = it.next().getValue();
            if (e.until > clock) {
                continue;
            }
            if (view == null) {
                view = current();
            }
            BlockData real = e.loc.getBlock().getBlockData();
            for (Player p : view) {
                p.sendBlockChange(e.loc, real);
            }
            it.remove();
        }
        if (clock % 20 == 0 && !fakes.isEmpty()) {
            // Re-assert for late joiners and for anyone whose client refreshed a chunk section.
            if (view == null) {
                view = current();
            }
            for (Entry e : fakes.values()) {
                if (!e.loc.getBlock().getType().isSolid()) {
                    continue;
                }
                for (Player p : view) {
                    p.sendBlockChange(e.loc, e.data);
                }
            }
            for (Player p : view) {
                painted.add(p.getUniqueId());
            }
        }
    }

    /** Reverts every painted cell for everyone who might have seen it. */
    public void clear() {
        if (world == null) {
            fakes.clear();
            return;
        }
        List<Player> view = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (painted.contains(p.getUniqueId()) || !fakes.isEmpty()) {
                view.add(p);
            }
        }
        for (Entry e : fakes.values()) {
            BlockData real = e.loc.getBlock().getBlockData();
            for (Player p : view) {
                if (p.getLocation().distanceSquared(e.loc) < 160 * 160) {
                    p.sendBlockChange(e.loc, real);
                }
            }
        }
        fakes.clear();
        painted.clear();
    }

    private List<Player> current() {
        List<Player> v = viewers == null ? null : viewers.get();
        return v == null ? List.of() : v;
    }
}
