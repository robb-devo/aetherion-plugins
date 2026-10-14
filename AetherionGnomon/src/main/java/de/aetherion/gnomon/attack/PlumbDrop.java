package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;

import org.bukkit.Sound;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

/**
 * PUNISH, then the honest OPENING. The hub crouches, the chain hauls the heart up (anticipation), then lets go: the
 * plumb bob free-falls into the floor under the hub. Afterwards it sits there glowing gold with the lens dimmed and
 * drooping - the one moment the heart is low, slow and soft.
 *
 * <p>Answers huggers (and crowds under it). {@link FalseOpening} reuses this drop and then lies about the opening.
 */
public class PlumbDrop extends Attack {

    protected static final double RADIUS = 4.5;

    protected Shapes.Circle shadow;
    protected String key;
    protected double cx;
    protected double cz;
    protected int windup;
    protected int commitAt;
    protected int landedAt = -1;
    private boolean committed;
    private boolean dropped;
    private final boolean lie;

    public PlumbDrop(Encounter e, Decision d, boolean lie) {
        super(e, d);
        this.lie = lie;
    }

    @Override
    public boolean usesBob() {
        return true;
    }

    @Override
    public void start() {
        cx = e.rig().hubX();
        cz = e.rig().hubZ();
        e.rig().moveTo(cx, cz);
        e.rig().gait(false);
        windup = e.windup(e.cfg().telegraph("plumb-drop", 30));
        commitAt = Math.max(2, windup - e.commitTicks(8));
        e.rig().crouch(2.2);
        e.rig().pendulum().reel(Math.max(1.2, e.rig().chainRest() - 2.6), 0.14);
        e.rig().look(new Vector3d(cx, e.floorY(), cz));
        shadow = e.shapes().circle(cx, cz, RADIUS, Shapes.Style.FORMING);
        shadow.grow(0.05, 0);
        shadow.grow(1.0, windup);
        key = e.newThreatKey("plumb");
        e.threat(key, new Zone.Circle(cx, cz, RADIUS), e.clock() + windup + 8, d);
        Vector3d h = e.rig().hub();
        e.fx().sound(h.x, h.y, h.z, Sound.ITEM_CROSSBOW_LOADING_START, 1.6f, 0.4f);
        e.fx().sound(h.x, h.y, h.z, Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.5f);
    }

    @Override
    public boolean tick() {
        // Damp the swing while it hauls, so it falls where the shadow says.
        Vector3d v = e.rig().pendulum().velocity();
        if (!dropped) {
            e.rig().pendulum().kick(-v.x * 0.25, 0, -v.z * 0.25);
        }
        if (t == windup / 2) {
            Vector3d h = e.rig().hub();
            e.fx().sound(h.x, h.y, h.z, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.6f, 0.4f);
        }
        if (!committed && t >= commitAt) {
            committed = true;
            shadow.style(Shapes.Style.COMMITTED);
            e.fx().sound(cx, e.floorY() + 2, cz, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.6f, 0.5f);
        }
        if (!dropped && t >= windup) {
            dropped = true;
            Vector3d h = e.rig().hub();
            double toFloor = h.y - 0.8 - e.floorY() + 1.0;
            e.rig().pendulum().drop(toFloor, -0.9, this::landed);
            e.fx().sound(h.x, h.y, h.z, Sound.BLOCK_CHAIN_BREAK, 1.8f, 0.5f);
        }
        if (landedAt >= 0) {
            return afterLanding();
        }
        return t > windup + 60;
    }

    private void landed() {
        landedAt = t;
        Vector3d b = e.rig().bob();
        Set<UUID> hit = e.strike(new Zone.Circle(cx, cz, RADIUS), e.cfg().damage("plumb-drop", 80), "plumb", 20, false, 1.0);
        e.impact(b.x, b.z, 1.3, !hit.isEmpty());
        e.resolve(key, hit);
        if (shadow != null) {
            shadow.remove();
            shadow = null;
        }
        if (lie) {
            onLie();
        } else {
            e.openTrue(e.cfg().telegraph("opening", 52));
        }
    }

    /** Overridden by the false opening. */
    protected void onLie() {
    }

    /** @return true when the move is over. */
    protected boolean afterLanding() {
        return e.opening() == de.aetherion.gnomon.ai.Brain.Opening.NONE || t > landedAt + e.cfg().telegraph("opening", 52) + 10;
    }

    @Override
    protected void cleanup() {
        if (shadow != null) {
            shadow.remove();
        }
        if (landedAt < 0) {
            e.brain().cancelThreat(key);
            e.rig().pendulum().reel(e.rig().chainRest(), 0.2);
        }
        e.rig().crouch(0);
    }
}
