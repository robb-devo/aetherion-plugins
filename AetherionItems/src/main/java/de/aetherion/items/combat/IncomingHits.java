package de.aetherion.items.combat;

/**
 * Facade. The ThreadLocal lives in AetherionCore so every plugin sees the same flag.
 */
public final class IncomingHits {

    private IncomingHits() {
    }

    public static void beginRaw() {
        de.aetherion.core.combat.IncomingHits.beginRaw();
    }

    public static void endRaw() {
        de.aetherion.core.combat.IncomingHits.endRaw();
    }

    public static boolean isRaw() {
        return de.aetherion.core.combat.IncomingHits.isRaw();
    }
}
