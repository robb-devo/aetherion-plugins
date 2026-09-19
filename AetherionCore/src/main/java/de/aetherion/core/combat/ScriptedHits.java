package de.aetherion.core.combat;

/**
 * Pet spit, Mini Aetherion, and other scripted hits keep their own numbers.
 * One ThreadLocal for the whole network. Facades in Items keep old imports working.
 */
public final class ScriptedHits {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ScriptedHits() {
    }

    public static void run(Runnable action) {
        if (action == null) {
            return;
        }
        begin();
        try {
            action.run();
        } finally {
            end();
        }
    }

    public static void begin() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
