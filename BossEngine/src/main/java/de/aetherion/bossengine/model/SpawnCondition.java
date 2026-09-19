package de.aetherion.bossengine.model;

public class SpawnCondition {

    private final int maxInstances;
    private final double leashRadius;
    private final LeashAction leashAction;

    public SpawnCondition(int maxInstances, double leashRadius, LeashAction leashAction) {
        this.maxInstances = Math.max(1, maxInstances);
        this.leashRadius = Math.max(0, leashRadius);
        this.leashAction = leashAction == null ? LeashAction.TELEPORT : leashAction;
    }

    public static SpawnCondition defaults() {
        return new SpawnCondition(1, 48, LeashAction.TELEPORT);
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public double getLeashRadius() {
        return leashRadius;
    }

    public LeashAction getLeashAction() {
        return leashAction;
    }
}
