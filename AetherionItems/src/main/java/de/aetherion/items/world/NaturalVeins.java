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
}
