package de.aetherion.bossengine.helios.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet-only block destruction: the vanilla break overlay on arena blocks, per block, with its own
 * progress. The client keeps one crack per "breaker" id, so every block gets a stable fake id derived
 * from its position. Cracks are re-sent periodically (the client drops them after ~20 s without an
 * update) and cleared explicitly; nothing here touches the world.
 */
public final class BlockCracks {

    private static final class Crack {
        final Location loc;
        final int id;
        float progress;
        long until;

        Crack(Location loc, int id) {
            this.loc = loc;
            this.id = id;
        }
    }

    private final World world;
    private final Supplier<List<Player>> viewers;
    private final Map<Long, Crack> cracks = new HashMap<>();
    private long clock;

    public BlockCracks(World world, Supplier<List<Player>> viewers) {
        this.world = world;
        this.viewers = viewers;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int fakeId(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return -1_000_000 - (h & 0x3FFFFFFF);
    }

    /** Sets block (x, y, z) to {@code progress} (0..1) for {@code ticks}; 0 removes it. */
    public void set(int x, int y, int z, float progress, int ticks) {
        if (world == null) {
            return;
        }
        long k = key(x, y, z);
        if (progress <= 0f) {
            clear(k);
            return;
        }
        Crack c = cracks.computeIfAbsent(k, ignored -> new Crack(new Location(world, x, y, z), fakeId(x, y, z)));
        float p = Math.min(1f, progress);
        boolean changed = Math.abs(p - c.progress) > 0.05f;
        c.progress = p;
        c.until = clock + Math.max(1, ticks);
        if (changed) {
            for (Player v : viewers.get()) {
                v.sendBlockDamage(c.loc, c.progress, c.id);
            }
        }
    }

    private void clear(long k) {
        Crack c = cracks.remove(k);
        if (c == null) {
            return;
        }
        for (Player v : viewers.get()) {
            v.sendBlockDamage(c.loc, 0f, c.id);
        }
    }

    public void clear(int x, int y, int z) {
        clear(key(x, y, z));
    }

    public void tick() {
        clock++;
        if (cracks.isEmpty()) {
            return;
        }
        List<Player> view = viewers.get();
        for (Iterator<Crack> it = cracks.values().iterator(); it.hasNext(); ) {
            Crack c = it.next();
            if (c.until <= clock) {
                for (Player v : view) {
                    v.sendBlockDamage(c.loc, 0f, c.id);
                }
                it.remove();
            } else if (clock % 100 == 0) {
                for (Player v : view) {
                    v.sendBlockDamage(c.loc, c.progress, c.id);
                }
            }
        }
    }

    public int size() {
        return cracks.size();
    }

    public void clearAll() {
        List<Player> view = viewers.get();
        for (Crack c : new ArrayList<>(cracks.values())) {
            for (Player v : view) {
                v.sendBlockDamage(c.loc, 0f, c.id);
            }
        }
        cracks.clear();
    }
}
