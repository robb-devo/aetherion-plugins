package de.aetherion.core.combat;

/**
 * Nested {@code player.damage} from bosses must skip the defense formula.
 * One ThreadLocal for the whole network. Facades in Items keep old imports working.
 */
public final class IncomingHits {

    private static final ThreadLocal<Integer> RAW = ThreadLocal.withInitial(() -> 0);

    private IncomingHits() {
    }

    public static void beginRaw() {
        RAW.set(RAW.get() + 1);
    }

    public static void endRaw() {
        int depth = RAW.get() - 1;
        if (depth <= 0) {
            RAW.remove();
        } else {
            RAW.set(depth);
        }
    }

    public static boolean isRaw() {
        return RAW.get() > 0;
    }
}
