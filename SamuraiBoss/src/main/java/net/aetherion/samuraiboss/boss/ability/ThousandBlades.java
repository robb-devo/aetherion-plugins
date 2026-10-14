package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.GhostBlade;
import net.aetherion.samuraiboss.render.fx.KanjiFx;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.SealCircle;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 千本桜 Senbon-zakura – "Tausend Klingen".
 *
 * <p>Er reckt das Schwert in den Himmel: Geisterklingen materialisieren sich spiralförmig aus
 * seiner Klinge und formen eine kreisende Krone über ihm. Er deutet auf die Herausforderer –
 * und die Klingen stürzen nacheinander herab. Jede Klinge dreht sich zuerst zu ihrem Ziel
 * (rote Kreismarkierung am Boden), schießt dann los, bleibt zitternd im Boden stecken und
 * zerfällt in Blüten.</p>
 */
public final class ThousandBlades extends Ability {

    private static final class Blade {
        GhostBlade gb;
        float angle;
        float height;
        Location impact;
        Telegraph tele;
        boolean launched, landed;
    }

    private final List<Blade> blades = new ArrayList<>();
    private Location origin;
    private SealCircle seal;
    private int targetCursor;

    private static final float HALO_R = 2.9f;
    private static final float HALO_Y = 4.6f;
    private static final int AIM = 4;     // Ticks zum Ausrichten
    private static final int FLIGHT = 6;  // Ticks Flugzeit
    private static final int WARN = 11;   // Ticks zwischen Markierung und Einschlag

    public ThousandBlades(Fight f) {
        super(f, "thousand-blades");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return switch (phase) {
            case SEI -> 0;
            case ARASHI -> dist > 3 ? 3 : 1;
            case SANGE -> 3;
        };
    }

    @Override
    protected Sequence build() {
        blades.clear();
        targetCursor = 0;
        int n = fx.count(f.phase() == Phase.SANGE ? 18 : 12);
        origin = boss.location();
        int summon = 13;
        int launch0 = 48;
        int gap = f.phase() == Phase.SANGE ? 2 : 3;
        Sequence q = new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.face(targetLoc());
                    boss.play(boss.anims.raiseSky, 4, 6, 1f, ev -> {
                    });
                    seal = fx.fx.add(new SealCircle(fx, ground(origin), 3.4f, "千本桜剣舞", 0xFFB7C9, 0.05f));
                    sfx.play(origin, Sfx.Id.SUMMON);
                })
                .at(summon, () -> summon(n))
                .every(summon + 10, launch0 - 2, 4, rel -> orbit(4))
                .at(launch0 - 8, () -> {
                    if (targetValid()) boss.face(target.getLocation());
                    boss.play(boss.anims.pointBlade, 3, 6, 1f, ev -> {
                        if (ev.equals("launch")) sfx.play(boss.location(), Sfx.Id.BLADE_FLY, 1.2f, 0.8f);
                    });
                    fx.fx.add(new KanjiFx(fx, boss.eye().add(0, 2.2, 0), "斬", 0xFFFFFF, 1.8f, null, true, 8, KanjiFx.Ending.SHATTER));
                });
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int t = launch0 + i * gap;
            q.at(t, () -> mark(idx));
            q.at(t + WARN - FLIGHT - AIM, () -> aim(idx));
            q.at(t + WARN - FLIGHT, () -> fly(idx));
            q.at(t + WARN, () -> land(idx));
            q.at(t + WARN + 22, () -> vanish(idx));
        }
        int last = launch0 + (n - 1) * gap + WARN + 22;
        q.at(launch0 + 10, () -> boss.stopAction(8));
        q.at(last - 10, () -> {
            if (seal != null) seal.fadeOut(10);
        });
        q.end(last + 4);
        return q;
    }

    /** Lage der Displays relativ zum gemeinsamen Ursprung (über dem Boss). */
    private Location originLoc() {
        return origin.clone().add(0, 0.5, 0);
    }

    private void summon(int n) {
        Location o = originLoc();
        Location hand = boss.bladeTip();
        Vector3f handRel = MathUtil.delta(o, hand);
        sfx.play(hand, Sfx.Id.CHIME, 1f, 0.8f);
        fx.fx.add(new PetalBurst(fx, hand, 14, 1.2f, 0.6f, 20));
        for (int i = 0; i < n; i++) {
            Blade b = new Blade();
            b.gb = fx.fx.add(new GhostBlade(fx, o, 1.05f, false));
            b.angle = i * 360f / n;
            b.height = HALO_Y + (i % 2 == 0 ? 0.35f : -0.25f);
            b.gb.pose(handRel, MathUtil.lookAlongZ(new Vector3f(0, 1, 0)), 0.2f, 0);
            blades.add(b);
        }
        // gestaffelt aus der Klinge heraus in die Krone gleiten
        for (int i = 0; i < blades.size(); i++) {
            Blade b = blades.get(i);
            int delay = 1 + i / 2;
            Sequence s = seq;
            s.later(delay, () -> haloPose(b, 9));
        }
    }

    private Vector3f haloPos(Blade b) {
        double r = Math.toRadians(b.angle);
        return new Vector3f((float) Math.cos(r) * HALO_R, b.height, (float) Math.sin(r) * HALO_R);
    }

    /** In der Krone: Spitze zeigt schräg nach außen-unten. */
    private void haloPose(Blade b, int interp) {
        if (b.launched || b.gb.isDead()) return;
        Vector3f p = haloPos(b);
        Vector3f out = new Vector3f(p.x, 0, p.z).normalize().mul(0.55f).add(0, -0.85f, 0);
        b.gb.pose(p, MathUtil.lookAlongZ(out), 1f, interp);
    }

    private void orbit(int dt) {
        for (Blade b : blades) {
            if (b.launched) continue;
            b.angle += 9f;
            haloPose(b, dt);
        }
    }

    private LivingEntity nextTarget() {
        List<LivingEntity> ts = targets();
        if (ts.isEmpty()) return null;
        LivingEntity t = ts.get(targetCursor % ts.size());
        targetCursor++;
        return t;
    }

    /** Ziel wählen und Einschlagstelle markieren. */
    private void mark(int i) {
        if (i >= blades.size()) return;
        Blade b = blades.get(i);
        LivingEntity t = nextTarget();
        Location at;
        if (t != null) {
            at = t.getLocation();
            Vector v = t.getVelocity().setY(0);
            // leicht vorhalten, mit etwas Streuung
            at.add(v.multiply(5.0)).add(Rand.signed() * 0.8, 0, Rand.signed() * 0.8);
        } else {
            at = origin.clone().add(MathUtil.dirOfYaw(Rand.f() * 360f).multiply(3 + Rand.f() * 6));
        }
        at = f.clampDash(origin, at);
        b.impact = ground(at);
        b.tele = fx.fx.add(Telegraph.circle(fx, b.impact, 1.35f, WARN));
    }

    private void aim(int i) {
        if (i >= blades.size()) return;
        Blade b = blades.get(i);
        if (b.impact == null || b.gb.isDead()) return;
        b.launched = true;
        Vector3f p = b.gb.position();
        Vector3f tgt = MathUtil.delta(originLoc(), b.impact);
        Vector3f dir = new Vector3f(tgt).sub(p);
        // kurz zurückziehen (Ausholen) und auf das Ziel richten
        Vector3f back = new Vector3f(dir).normalize().mul(-0.6f).add(p);
        b.gb.pose(back, MathUtil.lookAlongZ(dir), 1.1f, AIM);
        sfx.play(originLoc().add(p.x, p.y, p.z), Sfx.Id.GLINT, 0.5f, 1.2f + Rand.f() * 0.4f);
    }

    private void fly(int i) {
        if (i >= blades.size()) return;
        Blade b = blades.get(i);
        if (b.impact == null || b.gb.isDead()) return;
        Vector3f tgt = MathUtil.delta(originLoc(), b.impact);
        Vector3f p = b.gb.position();
        Vector3f dir = new Vector3f(tgt).sub(p).normalize();
        // Spitze steckt 0.45 Blöcke im Boden
        Vector3f end = new Vector3f(tgt).sub(new Vector3f(dir).mul(1.2f * 1.05f - 0.45f));
        b.gb.pose(end, MathUtil.lookAlongZ(dir), 1.1f, FLIGHT);
        sfx.play(originLoc().add(p.x, p.y, p.z), Sfx.Id.BLADE_FLY, 0.6f, 1f + Rand.f() * 0.3f);
    }

    private void land(int i) {
        if (i >= blades.size()) return;
        Blade b = blades.get(i);
        if (b.impact == null) return;
        b.landed = true;
        if (b.tele != null) b.tele.strike();
        sfx.play(b.impact, Sfx.Id.BLADE_HIT);
        fx.fx.add(new Shards(fx, b.impact.clone().add(0, 0.2, 0), 4, 0.35f, 0.1f, 12, Palette.LIGHT, Palette.PINK_GLASS));
        fx.cherry(b.impact.clone().add(0, 0.3, 0), 3, 0.4);
        List<LivingEntity> hit = inCircle(b.impact, 1.4);
        damage("blade" + i, hit, "thousand-blades", b.impact, 0.35, 0.25);
    }

    private void vanish(int i) {
        if (i >= blades.size()) return;
        Blade b = blades.get(i);
        if (!b.gb.isDead()) {
            Vector3f p = b.gb.position();
            fx.fx.add(new PetalBurst(fx, originLoc().add(p.x, p.y + 0.5, p.z), 5, 0.8f, 0.4f, 16));
            b.gb.dissolve(6);
        }
    }

    @Override
    protected void onCancel() {
        for (Blade b : blades) {
            if (b.tele != null) b.tele.clear();
            if (!b.gb.isDead()) b.gb.dissolve(4);
        }
        if (seal != null) seal.fadeOut(6);
    }
}
