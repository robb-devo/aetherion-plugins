package de.aetherion.items.world;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Scattered ore veins. A random walk with bends, not a lattice and not a filled box.
 */
final class NaturalVeins {

    @FunctionalInterface
    interface Placer {
        /** @return true when an ore block was written */
        boolean tryPlace(int x, int y, int z);
    }

    private NaturalVeins() {
    }

    /**
     * Lumpy pocket around the origin. The walk is pulled back inside {@code radius}
     * so 10–40 ores stay one cluster instead of a long snake.
     */
    static int cluster(int ox, int oy, int oz, int extra, int radius, Placer placer) {
        if (extra <= 0 || placer == null || radius < 1) {
            return 0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int placed = 0;
        int x = ox;
        int y = oy;
        int z = oz;
        int limit = radius * radius;
        int budget = extra * 24;
        int spins = 0;
        while (placed < extra && spins < budget) {
            spins++;
            int nx = x + rng.nextInt(3) - 1;
            int ny = y + rng.nextInt(3) - 1;
            int nz = z + rng.nextInt(3) - 1;
            if (dist2(nx, ny, nz, ox, oy, oz) > limit) {
                nx = x + Integer.signum(ox - x);
                ny = y + Integer.signum(oy - y);
                nz = z + Integer.signum(oz - z);
            }
            x = nx;
            y = ny;
            z = nz;
            int jx = x + rng.nextInt(3) - 1;
            int jz = z + rng.nextInt(3) - 1;
            if (dist2(jx, y, jz, ox, oy, oz) > limit) {
                jx = x;
                jz = z;
            }
            if (placer.tryPlace(jx, y, jz)) {
                placed++;
            }
        }
        return placed;
    }

    static int grow(int ox, int oy, int oz, int extra, Placer placer) {
        if (extra <= 0 || placer == null) {
            return 0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int placed = 0;
        int x = ox;
        int y = oy;
        int z = oz;
        int dx = rng.nextInt(3) - 1;
        int dy = rng.nextInt(3) - 1;
        int dz = rng.nextInt(3) - 1;
        if (dx == 0 && dy == 0 && dz == 0) {
            dx = 1;
        }
        int spins = 0;
        int budget = extra * 18;
        while (placed < extra && spins < budget) {
            spins++;
            if (rng.nextInt(4) == 0) {
                dx = clamp(dx + rng.nextInt(3) - 1);
                dy = clamp(dy + rng.nextInt(3) - 1);
                dz = clamp(dz + rng.nextInt(3) - 1);
                if (dx == 0 && dy == 0 && dz == 0) {
                    dx = rng.nextBoolean() ? 1 : -1;
                }
            }
            // Mostly horizontal, with the occasional vertical kink.
            if (rng.nextInt(5) == 0) {
                dy = 0;
            }
            x += dx;
            y += dy;
            z += dz;
            int jx = x + rng.nextInt(3) - 1;
            int jy = y + rng.nextInt(3) - 1;
            int jz = z + rng.nextInt(3) - 1;
            if (placer.tryPlace(jx, jy, jz)) {
                placed++;
            }
        }
        return placed;
    }

    private static int clamp(int value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static int dist2(int x, int y, int z, int ox, int oy, int oz) {
        int dx = x - ox;
        int dy = y - oy;
        int dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
