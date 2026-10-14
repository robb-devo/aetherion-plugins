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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ZONE. The needle's shadow becomes a clock hand and sweeps 270 degrees of the dial around the hub. Safe: inside the
 * chalk ring at its feet (where the heart can drop on you) or beyond the hand's tip (where you are far from the heart).
 *
 * <p>Adaptive: when it has read which way you circle, the hand sweeps against you - it comes to meet you.
 * Chalk chevrons show the direction before it moves.
 */
public final class HourHand extends Attack {

    private static final double INNER = 3.6;
    private static final double HALF_WIDTH = 1.7;

    private Shapes.Band hand;
    private final List<Shapes.Chevron> arrows = new ArrayList<>();
    private final Set<UUID> hitAll = new HashSet<>();
    private String key;
    private double cx;
    private double cz;
    private double outer;
    private double start;
    private double sweep;
    private int dir;
    private int windup;
    private int sweepTicks;
    private boolean committed;

    public HourHand(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        cx = e.rig().hubX();
        cz = e.rig().hubZ();
        e.rig().moveTo(cx, cz);
        e.rig().gait(false);
        outer = Math.min(21.5, e.arena().radius() - 1.5);
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        if (d.exploit() == Read.ORBIT) {
            dir = -d.sign();
        } else if (f != null && e.brain().confidence(f.getUniqueId(), Read.ORBIT) >= e.brain().config().notice()) {
            dir = -e.brain().sign(f.getUniqueId(), Read.ORBIT);
        } else {
            dir = Math.random() < 0.5 ? 1 : -1;
        }
        double fa = f == null ? e.rig().yaw() : Geo.angle(f.getLocation().getX() - cx, f.getLocation().getZ() - cz);
        start = fa - dir * Math.toRadians(110);
        sweep = Math.toRadians(270);
        windup = e.windup(e.cfg().telegraph("hour-hand", 32));
        sweepTicks = Math.max(30, e.cfg().telegraph("hour-hand-sweep", 54));

        hand = e.shapes().band(cx, cz, start, INNER, outer - INNER, HALF_WIDTH, Shapes.Style.FORMING);
        hand.grow(0.02, 0);
        hand.grow(1.0, windup);
        for (int i = 1; i <= 3; i++) {
            double a = start + dir * Math.toRadians(14 * i);
            double r = 9 + i;
            double tangent = a + dir * Math.PI / 2;
            arrows.add(e.shapes().chevron(cx + Math.cos(a) * r, cz + Math.sin(a) * r, tangent, 1.6, Shapes.Style.CHALK));
        }
        e.rig().needle(Math.toRadians(38), start + Math.PI);
        e.rig().faceAngle(start);
        e.rig().look(new Vector3d(cx + Math.cos(start) * outer * 0.7, e.floorY(), cz + Math.sin(start) * outer * 0.7));
        double from = dir > 0 ? start : start - sweep;
        key = e.newThreatKey("hand");
        e.threat(key, new Zone.Sector(cx, cz, from, from + sweep, INNER, outer), e.clock() + windup + sweepTicks / 2, d);
        if (d.exploit() != null) {
            e.announceExploit(d, cx, cz);
        }
        e.fx().sound(cx, e.floorY() + 8, cz, Sound.BLOCK_BELL_RESONATE, 1.8f, 0.7f);
    }

    @Override
    public boolean tick() {
        if (t < windup) {
            // A clock that speeds up as the hand gathers.
            int every = t < windup / 2 ? 6 : (t < windup - 8 ? 4 : 2);
            if (t % every == 0) {
                e.fx().sound(cx, e.floorY() + 6, cz, Sound.BLOCK_NOTE_BLOCK_HAT, 1.4f, 1.6f);
            }
            return false;
        }
        if (!committed) {
            committed = true;
            hand.style(Shapes.Style.COMMITTED);
            for (Shapes.Chevron c : arrows) {
                c.remove();
            }
            arrows.clear();
            e.fx().sound(cx, e.floorY() + 6, cz, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.6f, 1.3f);
            e.fx().sound(cx, e.floorY() + 6, cz, Sound.BLOCK_BELL_USE, 2f, 0.5f);
        }
        int s = t - windup;
        if (s <= sweepTicks) {
            double k = s / (double) sweepTicks;
            double a = start + dir * sweep * k;
            hand.angle(a, 1);
            e.rig().needle(Math.toRadians(38), a + Math.PI);
            e.rig().faceAngle(a);
            e.rig().look(new Vector3d(cx + Math.cos(a) * outer * 0.7, e.floorY(), cz + Math.sin(a) * outer * 0.7));
            hitAll.addAll(e.strike(new Zone.Hand(cx, cz, a, INNER, outer, HALF_WIDTH),
                    e.cfg().damage("hour-hand", 55), "hand", 40, false, 0.8));
            if (s % 5 == 0) {
                double mx = cx + Math.cos(a) * outer * 0.6;
                double mz = cz + Math.sin(a) * outer * 0.6;
                e.fx().sound(mx, e.floorY() + 1, mz, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.5f);
                e.fx().particle(Particle.SMOKE, mx, e.floorY() + 0.3, mz, 6, 0.6, 0.02);
            }
            return false;
        }
        if (s == sweepTicks + 1) {
            e.resolve(key, hitAll);
            hand.style(Shapes.Style.FEINT);
        }
        return s > sweepTicks + 8;
    }

    @Override
    protected void cleanup() {
        if (hand != null) {
            hand.remove();
        }
        for (Shapes.Chevron c : arrows) {
            c.remove();
        }
        arrows.clear();
        if (t <= windup + sweepTicks) {
            e.brain().cancelThreat(key);
        }
        e.rig().needleRest();
    }
}
