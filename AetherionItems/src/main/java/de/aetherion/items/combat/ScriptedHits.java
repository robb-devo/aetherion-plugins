package de.aetherion.items.combat;

/**
 * Facade. The ThreadLocal lives in AetherionCore so every plugin sees the same flag.
 */
public final class ScriptedHits {

    private ScriptedHits() {
    }

    public static void run(Runnable action) {
        de.aetherion.core.combat.ScriptedHits.run(action);
    }

    public static void begin() {
        de.aetherion.core.combat.ScriptedHits.begin();
    }

    public static void end() {
        de.aetherion.core.combat.ScriptedHits.end();
    }

    public static boolean isActive() {
        return de.aetherion.core.combat.ScriptedHits.isActive();
    }
}
