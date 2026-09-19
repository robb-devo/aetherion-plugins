package de.aetherion.bossengine.integration.worldguard;

/**
 * Thread-local gate used while BossEngine is spawning a tagged boss or minion.
 * WorldGuard's SpawnEntityEvent is allowed for the duration of the callback.
 */
public final class WorldGuardSpawnGuard {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private WorldGuardSpawnGuard() {
    }

    public static boolean isBypassing() {
        return DEPTH.get() > 0;
    }

    public static void runGuarded(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int next = DEPTH.get() - 1;
            if (next <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(next);
            }
        }
    }
}
