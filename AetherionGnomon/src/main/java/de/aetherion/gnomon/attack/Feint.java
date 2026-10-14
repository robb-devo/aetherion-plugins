package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

/**
 * BAIT. "The boss feints the dodge you always take."
 *
 * <p>A leg rises over you with a normal-looking shadow. But the lens - the eye never lies - stares at the spot you
 * always dodge to. When the fake shadow should turn red it goes pale instead, a second leg whips up, and the real
 * shadow appears crimson where you were going. Watch the eye and you never go there; dodge the other way and the
 * Gnomon is out-thought (it staggers).
 */
public final class Feint extends Attack {

    private static final double RADIUS = 2.4;
    private static final double LEAD = 3.4;

    private Shapes.Circle fake;
    private Shapes.Circle real;
    private String key;
    private int fakeLeg;
    private int realLeg;
    private int windup;
    private int revealAt;
    private int impactAt;
    private int landedAt = -1;
    private double fx;
    private double fz;
    private double rx;
    private double rz;
    private boolean revealed;
    private boolean slammed;

    public Feint(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        windup = e.windup(e.cfg().telegraph("stomp", 24));
        int commit = e.commitTicks(e.cfg().telegraph("stomp-commit", 11));
        revealAt = Math.max(4, windup - commit);
        int warn = Math.max(e.cfg().minCommit(), e.cfg().telegraph("feint-real-warning", 14));
        impactAt = revealAt + warn;
        aim(f);
        fakeLeg = e.rig().legToward(fx, fz);
        realLeg = otherLeg(fakeLeg, rx, rz);
        e.rig().gait(false);
        e.rig().face(fx, fz);
        e.rig().holdLeg(fakeLeg, new Vector3d(fx, e.floorY() + 6.5, fz));
        // The tell: the eye is on the real spot from the very first tick.
        e.rig().snapLook(new Vector3d(rx, e.floorY(), rz));
        e.rig().gazeSize(2.4);
        fake = e.shapes().circle(fx, fz, RADIUS, Shapes.Style.FORMING);
        fake.grow(0.08, 0);
        key = e.newThreatKey("feint");
        e.threat(key, new Zone.Circle(fx, fz, RADIUS), e.clock() + revealAt, d);
        e.announceExploit(d, rx, rz);
        Vector3d foot = e.rig().leg(fakeLeg).foot();
        e.fx().sound(foot.x, foot.y + 2, foot.z, Sound.BLOCK_PISTON_EXTEND, 1.2f, 0.5f);
    }

    private void aim(Player f) {
        if (f == null) {
            return;
        }
        Vector3d c = e.rig().clampToReach(f.getLocation().getX(), f.getLocation().getZ());
        fx = c.x;
        fz = c.z;
        double[] p = e.predict(f.getUniqueId(), Read.SIDE, d.sign(), LEAD);
        Vector3d r = e.rig().clampToReach(p[0], p[1]);
        rx = r.x;
        rz = r.z;
    }

    private int otherLeg(int not, double x, double z) {
        int best = (not + 1) % 3;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (i == not) {
                continue;
            }
            Vector3d foot = e.rig().leg(i).foot();
            double dd = Math.hypot(foot.x - x, foot.z - z);
            if (dd < bestD) {
                bestD = dd;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean tick() {
        if (t < revealAt) {
            aim(e.player(d.target()));
            fake.move(fx, fz, 2);
            fake.grow(Math.max(0.08, t / (double) windup), 2);
            e.rig().holdLeg(fakeLeg, new Vector3d(fx, e.floorY() + 6.5, fz));
            e.rig().look(new Vector3d(rx, e.floorY(), rz));
            e.moveThreat(key, new Zone.Circle(fx, fz, RADIUS), e.clock() + (revealAt - t));
        } else if (!revealed) {
            revealed = true;
            fake.style(Shapes.Style.FEINT);
            real = e.shapes().circle(rx, rz, RADIUS, Shapes.Style.COMMITTED);
            real.grow(0.15, 0);
            real.grow(1.0, impactAt - t);
            e.rig().beam(true);
            e.rig().snapLook(new Vector3d(rx, e.floorY(), rz));
            e.rig().releaseLeg(fakeLeg, 10);
            e.rig().holdLeg(realLeg, new Vector3d(rx, e.floorY() + 5.5, rz));
            e.rig().moveTo(e.rig().hubX(), e.rig().hubZ());
            e.fx().sound(rx, e.floorY() + 1, rz, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.6f, 0.5f);
            e.fx().sound(rx, e.floorY() + 2, rz, Sound.ENTITY_BREEZE_WIND_BURST, 1.4f, 0.6f);
        }
        if (!slammed && t >= impactAt - 3) {
            slammed = true;
            e.rig().slamLeg(realLeg, new Vector3d(rx, e.floorY(), rz), 3, landed -> land());
        }
        if (landedAt >= 0 && t >= landedAt + 12) {
            return true;
        }
        return t > impactAt + 40;
    }

    private void land() {
        landedAt = t;
        Set<UUID> hit = e.strike(new Zone.Circle(rx, rz, RADIUS), e.cfg().damage("stomp", 62), "feint", 10, false, 0.9);
        e.impact(rx, rz, 0.95, !hit.isEmpty());
        e.resolve(key, hit);
        if (real != null) {
            real.remove();
        }
        if (fake != null) {
            fake.remove();
        }
        e.rig().beam(false);
    }

    @Override
    protected void cleanup() {
        if (fake != null) {
            fake.remove();
        }
        if (real != null) {
            real.remove();
        }
        if (landedAt < 0) {
            e.brain().cancelThreat(key);
            e.rig().releaseLeg(realLeg, 10);
            e.rig().releaseLeg(fakeLeg, 10);
        }
        e.rig().beam(false);
    }
}
