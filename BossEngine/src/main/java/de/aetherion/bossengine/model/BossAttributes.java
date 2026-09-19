package de.aetherion.bossengine.model;

public class BossAttributes {

    private final double maxHealth;
    private final double movementSpeed;
    private final double attackDamage;
    private final double scale;
    private final double followRange;
    private final double knockbackResistance;

    public BossAttributes(
            double maxHealth,
            double movementSpeed,
            double attackDamage,
            double scale,
            double followRange,
            double knockbackResistance
    ) {
        this.maxHealth = maxHealth;
        this.movementSpeed = movementSpeed;
        this.attackDamage = attackDamage;
        this.scale = scale;
        this.followRange = followRange;
        this.knockbackResistance = knockbackResistance;
    }

    public static BossAttributes defaults() {
        return new BossAttributes(200, 0.25, 8, 1.0, 32, 0.0);
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public double getScale() {
        return scale;
    }

    public double getFollowRange() {
        return followRange;
    }

    public double getKnockbackResistance() {
        return knockbackResistance;
    }

    public BossAttributes overlay(BossAttributes overlay) {
        if (overlay == null) {
            return this;
        }
        return new BossAttributes(
                overlay.maxHealth > 0 ? overlay.maxHealth : maxHealth,
                overlay.movementSpeed > 0 ? overlay.movementSpeed : movementSpeed,
                overlay.attackDamage > 0 ? overlay.attackDamage : attackDamage,
                overlay.scale > 0 ? overlay.scale : scale,
                overlay.followRange > 0 ? overlay.followRange : followRange,
                overlay.knockbackResistance >= 0 ? overlay.knockbackResistance : knockbackResistance
        );
    }
}
