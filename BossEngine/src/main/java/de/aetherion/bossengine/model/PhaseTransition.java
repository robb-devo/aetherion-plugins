package de.aetherion.bossengine.model;

import org.bukkit.Particle;

public class PhaseTransition {

    private final int durationTicks;
    private final boolean invulnerable;
    private final boolean freezeAi;
    private final double startRadius;
    private final double endRadius;
    private final Particle particle;
    private final Particle extraParticle;
    private final int points;
    private final double height;
    private final TransitionShape shape;
    private final boolean explode;
    private final double explodeDamage;
    private final double explodeRadius;
    private final double explodeKnockback;
    private final double hoverHeight;
    private final int lightningCount;
    private final double underDamage;
    private final double underRadius;

    public PhaseTransition(
            int durationTicks,
            boolean invulnerable,
            boolean freezeAi,
            double startRadius,
            double endRadius,
            Particle particle,
            Particle extraParticle,
            int points,
            double height,
            TransitionShape shape,
            boolean explode,
            double explodeDamage,
            double explodeRadius,
            double explodeKnockback,
            double hoverHeight,
            int lightningCount,
            double underDamage,
            double underRadius
    ) {
        this.durationTicks = Math.max(0, durationTicks);
        this.invulnerable = invulnerable;
        this.freezeAi = freezeAi;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.particle = particle == null ? Particle.SOUL_FIRE_FLAME : particle;
        this.extraParticle = extraParticle;
        this.points = Math.max(8, points);
        this.height = height;
        this.shape = shape == null ? TransitionShape.CIRCLE : shape;
        this.explode = explode;
        this.explodeDamage = Math.max(0, explodeDamage);
        this.explodeRadius = explodeRadius;
        this.explodeKnockback = explodeKnockback;
        this.hoverHeight = hoverHeight;
        this.lightningCount = Math.max(0, lightningCount);
        this.underDamage = Math.max(0, underDamage);
        this.underRadius = underRadius;
    }

    public static PhaseTransition none() {
        return new PhaseTransition(
                0, false, false, 0, 0,
                Particle.SOUL_FIRE_FLAME, Particle.SOUL,
                16, 0.2, TransitionShape.CIRCLE,
                false, 0, 0, 0,
                0, 0, 0, 0
        );
    }

    /**
     * Default mid-fight pause so a missing YAML transition cannot skip the phase
     * or leave the boss hittable while stats/skills swap.
     */
    public static PhaseTransition guard() {
        return new PhaseTransition(
                50, true, true, 4.6, 1.15,
                Particle.SOUL_FIRE_FLAME, Particle.SOUL,
                36, 0.25, TransitionShape.CIRCLE,
                false, 0, 0, 0,
                0, 0, 0, 0
        );
    }

    public boolean isEnabled() {
        return durationTicks > 0;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public boolean isFreezeAi() {
        return freezeAi;
    }

    public double getStartRadius() {
        return startRadius;
    }

    public double getEndRadius() {
        return endRadius;
    }

    public Particle getParticle() {
        return particle;
    }

    public Particle getExtraParticle() {
        return extraParticle;
    }

    public int getPoints() {
        return points;
    }

    public double getHeight() {
        return height;
    }

    public TransitionShape getShape() {
        return shape;
    }

    public boolean isExplode() {
        return explode;
    }

    public double getExplodeDamage() {
        return explodeDamage;
    }

    public double getExplodeRadius() {
        return explodeRadius > 0 ? explodeRadius : Math.max(startRadius, endRadius);
    }

    public double getExplodeKnockback() {
        return explodeKnockback;
    }

    public double getHoverHeight() {
        return hoverHeight;
    }

    public int getLightningCount() {
        return lightningCount;
    }

    public double getUnderDamage() {
        return underDamage;
    }

    public double getUnderRadius() {
        return underRadius > 0 ? underRadius : 2.8;
    }
}
