package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;
import de.aetherion.gnomon.math.Geo;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

/**
 * PUNISH. A leg rises over a point and stabs it. Honest stomps aim at you; once it has read your dodge side (or your
 * orbit), it aims where you will be - and draws a crimson chevron there so you can see that it is guessing.
 *
 * <p>Readable: the shadow grows with the windup and turns red when it stops tracking. The lens looks at the real spot.
 */
public final class Stomp extends Attack {

    private static final double RADIUS = 2.4;
    private static final double TRACK_SPEED = 0.3;
    private static final double LEAD = 3.2;

    private Shapes.Circle shadow;
    private Shapes.Chevron mark;
    private String key;
    private int leg;
    private int windup;
    private int commitAt;
    private int slamAt;
    private int landedAt = -1;
    private double ax;
    private double az;
    private boolean lead;
    private boolean committed;
    private boolean slammed;

    public Stomp(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        lead = d.exploit() == Read.SIDE || d.exploit() == Read.ORBIT;
        windup = e.windup(e.cfg().telegraph("stomp", 24));
        int commit = e.commitTicks(e.cfg().telegraph("stomp-commit", 11));
        commitAt = Math.max(2, windup - commit);
        slamAt = windup - 3;
        double[] want = desired(f);
        Vector3d c = e.rig().clampToReach(want[0], want[1]);
        ax = c.x;
        az = c.z;
        leg = e.rig().legToward(ax, az);
        e.rig().gait(false);
        e.rig().face(ax, az);
        leanToward(0.9);
        e.rig().holdLeg(leg, hover());
        e.rig().look(new Vector3d(ax, e.floorY(), az));
        e.rig().gazeSize(1.8);
        shadow = e.shapes().circle(ax, az, RADIUS, Shapes.Style.FORMING);
        shadow.grow(0.08, 0);
        key = e.newThreatKey("stomp");
        e.threat(key, zone(), e.clock() + windup, d);
        if (lead && f != null) {
            e.announceExploit(d, ax, az);
            double from = Geo.angle(ax - f.getLocation().getX(), az - f.getLocation().getZ());
            mark = e.shapes().chevron(ax, az, from, 1.4, Shapes.Style.INK);
        }
        Vector3d foot = e.rig().leg(leg).foot();
        e.fx().sound(foot.x, foot.y + 2, foot.z, Sound.BLOCK_PISTON_EXTEND, 1.2f, 0.5f);
        e.fx().sound(foot.x, foot.y + 2, foot.z, Sound.ITEM_CROSSBOW_LOADING_START, 1.2f, 0.6f);
    }

    private double[] desired(Player f) {
        if (f == null) {
            return new double[]{ax, az};
        }
        if (lead) {
            return e.predict(f.getUniqueId(), d.exploit(), d.sign(), LEAD);
        }
        return new double[]{f.getLocation().getX(), f.getLocation().getZ()};
    }

    private Vector3d hover() {
        double hx = ax + (e.rig().hubX() - ax) * 0.18;
        double hz = az + (e.rig().hubZ() - az) * 0.18;
        return new Vector3d(hx, e.floorY() + 6.2, hz);
    }

    private void leanToward(double amount) {
        double dx = ax - e.rig().hubX();
        double dz = az - e.rig().hubZ();
        double len = Math.max(0.5, Math.hypot(dx, dz));
        e.rig().lean(dx / len * amount, dz / len * amount);
    }

    private Zone zone() {
        return new Zone.Circle(ax, az, RADIUS);
    }

    @Override
    public boolean tick() {
        if (t < commitAt) {
            Player f = e.player(d.target());
            double[] want = desired(f);
            Vector3d c = e.rig().clampToReach(want[0], want[1]);
            double dx = c.x - ax;
            double dz = c.z - az;
            double len = Math.hypot(dx, dz);
            if (len > TRACK_SPEED) {
                dx = dx / len * TRACK_SPEED;
                dz = dz / len * TRACK_SPEED;
            }
            ax += dx;
            az += dz;
            shadow.move(ax, az, 2);
            shadow.grow(Math.max(0.08, t / (double) windup), 2);
            if (mark != null && f != null) {
                double from = Geo.angle(ax - f.getLocation().getX(), az - f.getLocation().getZ());
                mark.move(ax, az, from, 2);
            }
            e.rig().holdLeg(leg, hover());
            e.rig().look(new Vector3d(ax, e.floorY(), az));
            leanToward(0.9);
            e.moveThreat(key, zone(), e.clock() + (windup - t));
        } else if (!committed) {
            committed = true;
            shadow.style(Shapes.Style.COMMITTED);
            shadow.grow(1.0, Math.max(1, windup - t));
            e.rig().snapLook(new Vector3d(ax, e.floorY(), az));
            e.rig().moveTo(e.rig().hubX(), e.rig().hubZ());
            e.fx().sound(ax, e.floorY() + 1, az, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.4f, 0.6f);
            e.fx().sound(ax, e.floorY() + 1, az, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 0.5f);
        }
        if (!slammed && t >= slamAt) {
            slammed = true;
            e.rig().lean(0, 0);
            e.rig().slamLeg(leg, new Vector3d(ax, e.floorY(), az), 3, landed -> land());
        }
        if (landedAt >= 0 && t >= landedAt + 12) {
            return true;
        }
        return t > windup + 40;
    }

    private void land() {
        landedAt = t;
        Set<UUID> hit = e.strike(zone(), e.cfg().damage("stomp", 62), "stomp", 10, false, 0.9);
        e.impact(ax, az, 0.9, !hit.isEmpty());
        e.resolve(key, hit);
        if (shadow != null) {
            shadow.remove();
        }
        if (mark != null) {
            mark.remove();
            mark = null;
        }
    }

    @Override
    protected void cleanup() {
        if (shadow != null) {
            shadow.remove();
        }
        if (mark != null) {
            mark.remove();
        }
        if (landedAt < 0) {
            e.brain().cancelThreat(key);
            e.rig().releaseLeg(leg, 10);
        }
        e.rig().lean(0, 0);
    }
}
