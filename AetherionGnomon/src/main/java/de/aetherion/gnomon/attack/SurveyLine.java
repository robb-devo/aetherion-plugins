package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;
import de.aetherion.gnomon.math.Geo;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PUNISH at range. The lens focuses on you (you hear the spyglass in your own ears), a beam of light draws a survey
 * line across the dial through your position, and then the floor splits along it from the hub outward. Ground wave:
 * sidestep it or jump the fissure as it passes.
 *
 * <p>Answers kiters. The line tracks you slowly until it commits (outrun it while it's still white).
 */
public final class SurveyLine extends Attack {

    private static final double HALF_WIDTH = 1.1;
    private static final double START = 1.5;
    private static final double SPEED = 1.5;
    private static final double TRACK = Math.toRadians(1.2);

    private Shapes.Band line;
    private String key;
    private double ox;
    private double oz;
    private double angle;
    private double length;
    private int windup;
    private int commitAt;
    private double front;
    private boolean committed;
    private boolean resolved;
    private final Set<UUID> hitAll = new HashSet<>();
    private Player target;

    public SurveyLine(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        ox = e.rig().hubX();
        oz = e.rig().hubZ();
        e.rig().moveTo(ox, oz);
        e.rig().gait(false);
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        if (d.exploit() == null) {
            // A line is for someone at range: if the focus is under the hub, survey the farthest one instead.
            Player far = farthest();
            if (far != null && (f == null || dist(f) < 8)) {
                f = far;
            }
        }
        target = f;
        double[] aim = aim(f);
        angle = Geo.angle(aim[0] - ox, aim[1] - oz);
        length = lengthAlong(angle, aim);
        windup = e.windup(e.cfg().telegraph("survey-line", 28));
        commitAt = Math.max(2, windup - e.commitTicks(e.cfg().telegraph("survey-commit", 9)));
        line = e.shapes().band(ox, oz, angle, START, length, HALF_WIDTH, Shapes.Style.FORMING);
        line.grow(0.05, 0);
        line.grow(1.0, commitAt);
        e.rig().face(aim[0], aim[1]);
        e.rig().beam(true);
        e.rig().gazeSize(2.0);
        e.rig().snapLook(new Vector3d(aim[0], e.floorY(), aim[1]));
        if (f != null) {
            Vector3d lens = e.rig().lensPos();
            e.fx().soundTo(f, lens.x, lens.y, lens.z, Sound.ITEM_SPYGLASS_USE, 2f, 0.7f);
        }
        if (d.exploit() != null) {
            e.announceExploit(d, aim[0], aim[1]);
        }
        key = e.newThreatKey("line");
        e.threat(key, zone(START + length), e.clock() + windup + 6, d);
    }

    private double dist(Player p) {
        return Geo.dist(p.getLocation().getX(), p.getLocation().getZ(), ox, oz);
    }

    private Player farthest() {
        Player best = null;
        double bestD = 8;
        for (Player p : e.party().online()) {
            double dd = dist(p);
            if (dd > bestD) {
                bestD = dd;
                best = p;
            }
        }
        return best;
    }

    private double[] aim(Player f) {
        if (f == null) {
            return new double[]{ox + Math.cos(angle) * 10, oz + Math.sin(angle) * 10};
        }
        if (d.exploit() == Read.SIDE) {
            return e.predict(f.getUniqueId(), Read.SIDE, d.sign(), 3.0);
        }
        return new double[]{f.getLocation().getX(), f.getLocation().getZ()};
    }

    private double lengthAlong(double a, double[] aim) {
        double dist = Geo.dist(aim[0], aim[1], ox, oz);
        double want = dist + 7;
        // Stop at the rim of the dial.
        double maxR = e.arena().radius() + 0.5;
        double lo = 0;
        double hi = want;
        for (int i = 0; i < 20; i++) {
            double mid = (lo + hi) / 2;
            double x = ox + Math.cos(a) * mid;
            double z = oz + Math.sin(a) * mid;
            if (e.arena().distance(x, z) <= maxR) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Math.max(6, lo - START);
    }

    private Zone zone(double upTo) {
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        return new Zone.Capsule(ox + ux * START, oz + uz * START, ox + ux * upTo, oz + uz * upTo, HALF_WIDTH);
    }

    @Override
    public boolean tick() {
        if (t < commitAt) {
            Player f = target != null && target.isOnline() && e.party().contains(target) ? target : null;
            if (f != null) {
                double[] aim = aim(f);
                double want = Geo.angle(aim[0] - ox, aim[1] - oz);
                double diff = Geo.wrap(want - angle);
                angle = Geo.wrap(angle + Math.max(-TRACK, Math.min(TRACK, diff)));
                length = lengthAlong(angle, aim);
                line.set(ox, oz, angle, START, length, 1);
                double tx = ox + Math.cos(angle) * Geo.dist(aim[0], aim[1], ox, oz);
                double tz = oz + Math.sin(angle) * Geo.dist(aim[0], aim[1], ox, oz);
                e.rig().look(new Vector3d(tx, e.floorY(), tz));
                e.rig().face(tx, tz);
                e.moveThreat(key, zone(START + length), e.clock() + (windup - t) + 6);
            }
            return false;
        }
        if (!committed) {
            committed = true;
            line.style(Shapes.Style.COMMITTED);
            front = START;
            e.fx().sound(ox, e.floorY() + 2, oz, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.6f, 0.6f);
        }
        if (t < windup) {
            return false;
        }
        if (front < START + length) {
            double before = front;
            front = Math.min(START + length, front + SPEED);
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            Zone seg = new Zone.Capsule(ox + ux * before, oz + uz * before, ox + ux * front, oz + uz * front, HALF_WIDTH);
            hitAll.addAll(e.strike(seg, e.cfg().damage("survey-line", 50), "line", 16, true, 0.7));
            double px = ox + ux * front;
            double pz = oz + uz * front;
            e.fx().dust(px, e.floorY() + 0.1, pz, e.floorBlock(), 14, 0.5);
            e.fx().particle(Particle.CRIT, px, e.floorY() + 0.4, pz, 4, 0.3, 0.1);
            if (t % 2 == 0) {
                e.fx().sound(px, e.floorY(), pz, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
                e.fx().sound(px, e.floorY(), pz, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.7f);
            }
            line.set(ox + ux * (front - START), oz + uz * (front - START), angle, START, Math.max(0.1, length - (front - START)), 1);
            return false;
        }
        if (!resolved) {
            resolved = true;
            e.resolve(key, hitAll);
            e.rig().beam(false);
        }
        return true;
    }

    @Override
    protected void cleanup() {
        if (line != null) {
            line.remove();
        }
        if (!resolved) {
            e.brain().cancelThreat(key);
        }
        e.rig().beam(false);
    }
}
