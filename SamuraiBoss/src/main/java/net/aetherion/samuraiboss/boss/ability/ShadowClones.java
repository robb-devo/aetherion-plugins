package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.boss.ShadowClone;
import net.aetherion.samuraiboss.render.fx.CutSheet;
import net.aetherion.samuraiboss.render.fx.KanjiFx;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 影分身 Kage-bunshin – Schattenklone mit verzögerter Nachahmung.
 *
 * <p>Er tritt in seinen eigenen Schatten und erscheint auf einem Kreis um das Ziel; aus violetten
 * Pfützen steigen Schattenklone – gleichmäßig um denselben Kreis verteilt. Dann sprintet er mit
 * einem Stich mitten durch den Kreis. Jeder Klon wiederholt <b>exakt dieselbe Bewegung</b>
 * (aufgezeichnete Posen!), nur zeitversetzt und aus seiner Richtung. Am Boden zeigen violette
 * Linien, wo jeder Klon durchstoßen wird – der Mittelpunkt ist der gefährlichste Ort.</p>
 */
public final class ShadowClones extends Ability {

    private static final class C {
        ShadowClone clone;
        float delta;
        int delay;
        Location start, end, gStart, gEnd;
        Telegraph tele, puddle;
        boolean struck;
    }

    private final List<C> clones = new ArrayList<>();
    private final List<ShadowClone.Frame> history = new ArrayList<>();
    private Location pivot;
    private Location bossStart, bossEnd;
    private Telegraph bossTele;
    private boolean recording;

    private static final float R = 6.2f;
    private static final int TELE_AT = 16;
    private static final int DASH_AT = 34;
    private static final int DELAY = 9;

    public ShadowClones(Fight f) {
        super(f, "shadow-clones");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return phase == Phase.SEI ? 0 : 3;
    }

    @Override
    protected Sequence build() {
        clones.clear();
        history.clear();
        recording = false;
        pivot = ground(targetLoc());
        int n = f.phase() == Phase.SANGE ? 4 : 3;
        float a0 = MathUtil.yawTo(pivot, boss.location());
        bossStart = onCircle(a0, R);
        bossEnd = onCircle(a0 + 180f, R * 0.85f);
        for (int i = 1; i <= n; i++) {
            C c = new C();
            c.delta = i * 360f / (n + 1);
            c.delay = i * DELAY;
            c.start = rotate(bossStart, c.delta);
            c.end = rotate(bossEnd, c.delta);
            clones.add(c);
        }
        int lastStrike = DASH_AT + n * DELAY;
        Sequence q = new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    Location from = boss.location();
                    fx.fx.add(new KanjiFx(fx, boss.eye().add(0, 2.0, 0), "影", 0xB070FF, 2.2f, null, true, 14, KanjiFx.Ending.SHATTER));
                    sfx.play(from, Sfx.Id.CLONE);
                    Moves.trail(f, from, from, 1, 4, 10, true);
                    boss.model().setAllHidden(true);
                    boss.setInvulnerable(true);
                    boss.moveTo(bossStart, 0);
                    boss.snapYaw(MathUtil.yawTo(bossStart, pivot));
                })
                .at(2, this::spawnClones)
                .at(8, () -> {
                    boss.model().setAllHidden(false);
                    boss.setInvulnerable(false);
                    fx.fx.add(new Shards(fx, bossStart.clone().add(0, 1, 0), 8, 0.4f, 0.12f, 14, Palette.BLACK_GLASS, Palette.PURPLE_GLASS));
                    boss.play(boss.anims.iaiReady, 3, 4, 1f, ev -> {
                    });
                })
                .at(TELE_AT, this::telegraphs)
                .during(2, lastStrike + 12, this::record)
                .at(DASH_AT - 4, () -> sfx.play(boss.location(), Sfx.Id.GLINT))
                .at(DASH_AT, this::bossDash)
                .at(lastStrike + 14, this::dissolveClones);
        for (C c : clones) q.at(DASH_AT + c.delay, () -> cloneStrike(c));
        q.end(lastStrike + 20);
        return q;
    }

    private Location onCircle(float yaw, float r) {
        Location p = pivot.clone().add(MathUtil.dirOfYaw(yaw).multiply(r));
        Location c = f.clampDash(pivot, p);
        c.setY(pivot.getY());
        return c;
    }

    /** Dreht einen Punkt um den Drehpunkt (Yaw-Konvention von Minecraft). */
    private Location rotate(Location p, float delta) {
        double dx = p.getX() - pivot.getX(), dz = p.getZ() - pivot.getZ();
        double r = Math.sqrt(dx * dx + dz * dz);
        float a = MathUtil.yawOf(dx, dz);
        Vector v = MathUtil.dirOfYaw(a + delta).multiply(r);
        Location out = pivot.clone().add(v.getX(), p.getY() - pivot.getY(), v.getZ());
        return out;
    }

    private void spawnClones() {
        for (C c : clones) {
            Location at = c.start.clone();
            float yaw = MathUtil.yawTo(c.start, pivot);
            c.clone = new ShadowClone(f, at, yaw);
            c.puddle = fx.fx.add(Telegraph.circle(fx, ground(at), 1.3f, 10));
            c.puddle.tint(Palette.PURPLE_GLASS, Palette.BLACK_GLASS);
            sfx.play(at, Sfx.Id.GHOST, 0.8f, 0.7f);
        }
        recording = true;
    }

    private void telegraphs() {
        int fill = DASH_AT - TELE_AT;
        Location ga = ground(bossStart), gb = ground(bossEnd);
        gb.setY(ga.getY());
        bossTele = fx.fx.add(Telegraph.line(fx, ga, gb, 1.8f, fill));
        for (C c : clones) {
            c.gStart = ground(c.start);
            c.gEnd = ground(c.end);
            c.gEnd.setY(c.gStart.getY());
            c.tele = fx.fx.add(Telegraph.line(fx, c.gStart, c.gEnd, 1.8f, fill + c.delay));
            c.tele.tint(Palette.PURPLE, Palette.PURPLE_GLASS);
            if (c.puddle != null) c.puddle.clear();
        }
    }

    /** Jeden Tick: Boss aufzeichnen, Klone den zeitversetzten Frame zeigen lassen. */
    private void record(int rel) {
        if (!recording) return;
        history.add(new ShadowClone.Frame(boss.location(), boss.yaw(), boss.currentPose().copy(), boss.drawn()));
        int now = history.size() - 1;
        boolean fast = boss.animator().recommendedInterval() == 1;
        for (C c : clones) {
            if (c.clone == null || c.clone.isRemoved()) continue;
            if (!fast && rel % 2 != 0 && c.clone.age() > 0) continue;
            ShadowClone.Frame fr = history.get(Math.max(0, now - c.delay));
            Location p = rotate(fr.loc(), c.delta);
            c.clone.show(p, fr.yaw() + c.delta, fr.pose(), fr.drawn(), fast ? 1 : 2);
        }
    }

    private void bossDash() {
        boss.snapYaw(MathUtil.yawTo(bossStart, bossEnd));
        if (!boss.drawn()) boss.setDrawn(true);
        boss.play(boss.anims.quickDash, 1, 4, 1f, ev -> {
        });
        Moves.trail(f, bossStart, bossEnd, 3, 3, 9, false);
        boss.moveTo(bossEnd, 2);
        boss.noDamageTicks(8);
        sfx.play(bossStart, Sfx.Id.DASH);
        sfx.play(pivot, Sfx.Id.SLASH);
        if (bossTele != null) bossTele.strike();
        Location ga = ground(bossStart), gb = ground(bossEnd);
        gb.setY(ga.getY());
        fx.fx.add(new CutSheet(fx, ga.clone().add(0, 0.1, 0), gb.clone().add(0, 0.1, 0), 2.3f, 12, Palette.LIGHT, Palette.PINK_GLASS, false));
        damage("boss", inLine(ga, gb, 1.0), "shadow-clone", pivot, 0.8, 0.35);
        f.atmo().shakeNear(f.participants(), pivot, 0.4f, 3, 12);
    }

    private void cloneStrike(C c) {
        if (c.struck || c.gStart == null) return;
        c.struck = true;
        if (c.tele != null) c.tele.strike();
        sfx.play(c.gStart, Sfx.Id.DASH, 0.9f, 0.75f);
        sfx.play(pivot, Sfx.Id.SLASH, 0.9f, 0.8f);
        fx.fx.add(new CutSheet(fx, c.gStart.clone().add(0, 0.1, 0), c.gEnd.clone().add(0, 0.1, 0), 2.3f, 12,
                Palette.PURPLE, Palette.PURPLE_GLASS, false));
        damage("clone" + c.delay, inLine(c.gStart, c.gEnd, 1.0), "shadow-clone", pivot, 0.8, 0.35);
    }

    private void dissolveClones() {
        for (C c : clones) {
            if (c.clone == null || c.clone.isRemoved()) continue;
            Location at = c.clone.location().add(0, 1.2, 0);
            fx.fx.add(new Shards(fx, at, 12, 0.7f, 0.16f, 18, Palette.BLACK_GLASS, Palette.PURPLE_GLASS, Palette.PURPLE));
            fx.fx.add(new PetalBurst(fx, at, 6, 1.2f, 0.3f, 16));
            sfx.play(at, Sfx.Id.SHATTER, 0.6f, 0.7f);
            c.clone.remove();
        }
    }

    @Override
    protected void onCancel() {
        boss.model().setAllHidden(false);
        boss.setInvulnerable(false);
        if (bossTele != null) bossTele.clear();
        for (C c : clones) {
            if (c.tele != null) c.tele.clear();
            if (c.puddle != null) c.puddle.clear();
            if (c.clone != null) c.clone.remove();
        }
        history.clear();
    }
}
