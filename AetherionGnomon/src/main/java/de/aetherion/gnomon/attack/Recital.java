package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.Lang;
import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Profile;
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
 * EXECUTE (Phase II signature). "A previously safe habit becomes lethal - and you understand why."
 *
 * <p>The Gnomon reads your route from its ledger (the last ~10 seconds) and recites it: chalk marks light up along the
 * exact path you walked, in the order and rhythm you walked it, and each one erupts after a fair warning. Repeat your
 * habit and you walk into your own past. Ground eruptions - jump them or leave the line.
 */
public final class Recital extends Attack {

    private static final double R = 1.7;

    private static final class Beat {
        final double x;
        final double z;
        final int appear;
        final int burst;
        Shapes.Circle shape;
        boolean committed;
        boolean burst_;

        Beat(double x, double z, int appear, int burst) {
            this.x = x;
            this.z = z;
            this.appear = appear;
            this.burst = burst;
        }
    }

    private final List<Beat> beats = new ArrayList<>();
    private final Set<UUID> hitAll = new HashSet<>();
    private int last;

    public Recital(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        UUID target = d.target();
        Profile p = e.brain().profile(target);
        if (p == null) {
            return;
        }
        List<Profile.PathPoint> path = p.pathBetween(e.clock() - 260, e.clock() - 40);
        List<Profile.PathPoint> keep = new ArrayList<>();
        for (Profile.PathPoint pt : path) {
            if (!e.arena().inside(pt.x(), pt.z())) {
                continue;
            }
            if (keep.isEmpty() || Geo.dist(pt.x(), pt.z(), keep.get(keep.size() - 1).x(), keep.get(keep.size() - 1).z()) >= 2.2) {
                keep.add(pt);
            }
        }
        while (keep.size() > 16) {
            keep.remove(0);
        }
        int lead = Math.max(e.cfg().minTelegraph(), e.cfg().telegraph("recital-lead", 22));
        int at = 6;
        for (int i = 0; i < keep.size(); i++) {
            Profile.PathPoint pt = keep.get(i);
            if (i > 0) {
                long dt = pt.tick() - keep.get(i - 1).tick();
                at += (int) Math.max(5, Math.min(14, Math.round(dt * 0.75)));
            }
            beats.add(new Beat(pt.x(), pt.z(), at, at + lead));
        }
        last = beats.isEmpty() ? 0 : beats.get(beats.size() - 1).burst;
        e.rig().rear(1.5);
        e.rig().beam(true);
        e.rig().gazeSize(2.0);
        e.announceExploit(d, p.x(), p.z());
        Vector3d h = e.rig().hub();
        e.fx().sound(h.x, h.y, h.z, Sound.ITEM_BOOK_PAGE_TURN, 2f, 0.5f);
        e.fx().sound(h.x, h.y, h.z, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 2f, 0.5f);
        Player pl = e.player(target);
        if (pl != null && e.once("recital:" + pl.getUniqueId())) {
            e.fx().subtitle(pl, Lang.recital(pl), 50);
        }
    }

    @Override
    public boolean tick() {
        if (beats.isEmpty()) {
            return true;
        }
        for (Beat b : beats) {
            if (t == b.appear) {
                b.shape = e.shapes().dot(b.x, b.z, R, Shapes.Style.CHALK);
                b.shape.grow(0.1, 0);
                b.shape.grow(1.0, b.burst - b.appear);
                e.rig().look(new Vector3d(b.x, e.floorY(), b.z));
                e.rig().needle(Math.toRadians(30), Geo.angle(b.x - e.rig().hubX(), b.z - e.rig().hubZ()) + Math.PI);
                e.fx().sound(b.x, e.floorY() + 0.5, b.z, Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f, 1.2f);
            }
            if (!b.committed && b.shape != null && t >= b.burst - 8) {
                b.committed = true;
                b.shape.style(Shapes.Style.COMMITTED);
            }
            if (!b.burst_ && t >= b.burst) {
                b.burst_ = true;
                hitAll.addAll(e.strike(new Zone.Circle(b.x, b.z, R), e.cfg().damage("recital", 56), "recital", 8, true, 0.6));
                e.fx().particle(Particle.SQUID_INK, b.x, e.floorY() + 0.6, b.z, 18, 0.35, 0.12);
                e.fx().dust(b.x, e.floorY() + 0.2, b.z, e.floorBlock(), 16, 0.5);
                e.fx().sound(b.x, e.floorY(), b.z, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.4f, 0.6f);
                e.fx().sound(b.x, e.floorY(), b.z, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.5f);
                e.fx().shakeNear(b.x, b.z, 5, 5);
            }
            if (b.shape != null && t >= b.burst + 6) {
                b.shape.remove();
                b.shape = null;
            }
        }
        return t > last + 10;
    }

    @Override
    protected void cleanup() {
        for (Beat b : beats) {
            if (b.shape != null) {
                b.shape.remove();
            }
        }
        e.rig().rear(0);
        e.rig().beam(false);
        e.rig().needleRest();
        e.brain().beginThreat("recital-outcome", new Zone.Circle(0, 0, 0), e.clock(), d.exploit(), d.sign(),
                d.exploit() == null ? null : d.target());
        e.brain().resolveThreat("recital-outcome", hitAll);
    }
}
