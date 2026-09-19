package de.aetherion.bossengine.model;

public class BossOptions {

    private final boolean glowing;
    private final boolean customNameVisible;
    private final boolean silent;
    private final int invulnerableSpawnTicks;
    private final boolean persistent;
    private final boolean removeWhenFarAway;
    private final boolean preventItemPickup;

    public BossOptions(
            boolean glowing,
            boolean customNameVisible,
            boolean silent,
            int invulnerableSpawnTicks,
            boolean persistent,
            boolean removeWhenFarAway,
            boolean preventItemPickup
    ) {
        this.glowing = glowing;
        this.customNameVisible = customNameVisible;
        this.silent = silent;
        this.invulnerableSpawnTicks = invulnerableSpawnTicks;
        this.persistent = persistent;
        this.removeWhenFarAway = removeWhenFarAway;
        this.preventItemPickup = preventItemPickup;
    }

    public static BossOptions defaults() {
        return new BossOptions(false, true, false, 20, true, false, true);
    }

    public boolean isGlowing() {
        return glowing;
    }

    public boolean isCustomNameVisible() {
        return customNameVisible;
    }

    public boolean isSilent() {
        return silent;
    }

    public int getInvulnerableSpawnTicks() {
        return invulnerableSpawnTicks;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isRemoveWhenFarAway() {
        return removeWhenFarAway;
    }

    public boolean isPreventItemPickup() {
        return preventItemPickup;
    }
}
