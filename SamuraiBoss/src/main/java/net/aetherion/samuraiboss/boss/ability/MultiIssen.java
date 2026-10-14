package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.CutSheet;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.SealCircle;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.SpiralConverge;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Zwei Fähigkeiten mit demselben Prinzip "erst schneiden, dann fällt der Schaden":
 *
 * <p><b>三段一閃 Sandan-Issen</b> (Phase 2): drei Blitzschnitte hintereinander, jeder zielt neu
 * auf die aktuelle Position des Ziels (eigene Bodenlinie, kurze Vorwarnzeit). Wer in einer Linie
 * stand, ist "markiert" – beim Einrasten der Klinge fallen alle drei Schnitte gleichzeitig.</p>
 *
 * <p><b>五芒星 Gobōsei</b> (Phase 3): der Geist zeichnet mit fünf Sprints ein Pentagramm um das
 * Ziel. Jede Linie bleibt als leuchtende Narbe im Boden. Beim Klick der Scheide explodiert der
 * ganze Stern in Licht – wer dann noch auf einer Linie steht, wird getroffen.</p>
 */
public final class MultiIssen extends Ability {

    private final boolean star;
    private final List<Telegraph> teles = new ArrayList<>();
    private final List<Location[]> lines = new ArrayList<>();
    private final Set<LivingEntity> marked = new LinkedHashSet<>();
    private SealCircle seal;

    public MultiIssen(Fight f, boolean star) {
        super(f, star ? "star" : "triple-issen");
        this.star = star;
    }

    @Override
    public int weight(Phase phase, double dist) {
        if (star) return phase == Phase.SANGE ? 4 : 0;
        return phase == Phase.ARASHI ? (dist > 4 ? 4 : 2) : 0;
    }

    @Override
    protected Sequence build() {
        teles.clear();
        lines.clear();
        marked.clear();
        seal = null;
        return star ? buildStar() : buildTriple();
    }

    // =================================================================== 三段一閃

    private Sequence buildTriple() {
        Sequence q = new Sequence();
        int base = 0;
        if (boss.drawn()) {
            // erst die Klinge wegstecken – ein Iai-Meister schneidet aus der Scheide
            q.at(0, () -> {
                boss.stopWalking();
                boss.play(boss.anims.sheatheFromKamae, 4, 4, 1.25f, ev -> {
                });
            });
            base = 24;
        }
        final int b = base;
        q.at(b, () -> {
            boss.stopWalking();
            boss.face(targetLoc());
            boss.play(boss.anims.iaiReady, ev -> {
            });
            planDash(0, 18);
            sfx.play(boss.location(), Sfx.Id.TELEGRAPH);
        });
        q.at(b + 18, () -> dash(0, true));
        q.at(b + 19, () -> planDash(1, 10));
        q.at(b + 29, () -> dash(1, false));
        q.at(b + 30, () -> planDash(2, 10));
        q.at(b + 40, () -> dash(2, false));
        q.at(b + 49, () -> boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
            if (ev.equals("click")) detonateTriple();
        }));
        q.end(b + 49 + 36);
        return q;
    }

    /** Plant Sprint i: vom aktuellen Standort durch die aktuelle Zielposition (4 Blöcke darüber hinaus). */
    private void planDash(int i, int windup) {
        Location from = boss.location();
        Location end = dashEnd(from, through(from, targetLoc(), 4.0, 15.0));
        Location ga = ground(from), gb = ground(end);
        gb.setY(ga.getY());
        lines.add(new Location[]{from, end, ga, gb});
        teles.add(fx.fx.add(Telegraph.line(fx, ga, gb, 1.7f, windup)));
        boss.snapYaw(MathUtil.yawTo(from, end));
    }

    private void dash(int i, boolean first) {
        if (i >= lines.size()) return;
        Location[] l = lines.get(i);
        Moves.trail(f, l[0], l[1], 3, 3, 9, false);
        if (first) boss.play(boss.anims.iaiStrike, 0, 4, 1f, ev -> {
        });
        else boss.play(boss.anims.quickDash, 1, 4, 1f, ev -> {
        });
        boss.moveTo(l[1], 2);
        boss.noDamageTicks(8);
        sfx.play(l[0], Sfx.Id.DASH);
        sfx.play(l[1], Sfx.Id.SLASH, 1f, 1f + i * 0.08f);
        teles.get(i).strike();
        fx.fx.add(new CutSheet(fx, l[2].clone().add(0, 0.1, 0), l[3].clone().add(0, 0.1, 0), 2.2f, 12,
                Palette.LIGHT, Palette.PINK_GLASS, false));
        marked.addAll(inLine(l[2], l[3], 0.95));
        f.atmo().shakeNear(f.participants(), l[1], 0.4f, 3, 12);
    }

    private void detonateTriple() {
        for (Location[] l : lines) {
            fx.fx.add(new CutSheet(fx, l[2].clone().add(0, 0.1, 0), l[3].clone().add(0, 0.1, 0), 3.0f, 14,
                    Palette.LIGHT, Palette.PINK_GLASS, true));
        }
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity e : marked) {
            if (!e.isValid() || e.isDead()) continue;
            victims.add(e);
            Moves.crossCut(f, e.getLocation());
            sfx.play(e.getLocation(), Sfx.Id.CUT);
        }
        int n = damage("cut", victims, "issen", boss.location(), 0.7, 0.35);
        if (n > 0) f.atmo().shake(f.participants(), 0.6f, 6);
        marked.clear();
    }

    // =================================================================== 五芒星

    private Sequence buildStar() {
        Location c = ground(targetLoc());
        float r = 6.5f;
        float a0 = MathUtil.yawTo(c, boss.location());
        // fünf Sternspitzen (Reihenfolge 0,2,4,1,3 = Pentagramm), Endpunkte im Kampfbereich halten
        Location[] pts = new Location[6];
        for (int k = 0; k <= 5; k++) {
            Location p = c.clone().add(MathUtil.dirOfYaw(a0 + k * 144f).multiply(r));
            pts[k] = f.clampDash(c, p);
            pts[k].setY(c.getY());
        }
        Sequence q = new Sequence();
        q.at(0, () -> {
            boss.stopWalking();
            Location from = boss.location();
            fx.fx.add(new PetalBurst(fx, from.clone().add(0, 1.2, 0), 20, 2f, 0.3f, 26));
            sfx.play(from, Sfx.Id.PETALS);
            boss.model().setAllHidden(true);
            boss.setInvulnerable(true);
            boss.moveTo(pts[0], 0);
            seal = fx.fx.add(new SealCircle(fx, c, r + 0.6f, "五芒星陣散華一閃桜", 0xFF8FB3, 0.03f));
            sfx.play(c, Sfx.Id.SUMMON);
        });
        q.at(6, () -> {
            fx.fx.add(new SpiralConverge(fx, pts[0], 14, 2.2f, 2.2f, 8, false));
            boss.snapYaw(MathUtil.yawTo(pts[0], pts[1]));
        });
        q.at(12, () -> {
            boss.model().setAllHidden(false);
            boss.setInvulnerable(false);
            boss.flash();
            sfx.play(pts[0], Sfx.Id.GLINT);
        });
        int t0 = 16, step = 7;
        for (int k = 0; k < 5; k++) {
            final int kk = k;
            q.at(t0 + k * step, () -> starDash(pts[kk], pts[kk + 1], kk));
        }
        int fin = t0 + 5 * step + 2;
        q.at(fin, () -> {
            boss.snapYaw(MathUtil.yawTo(boss.location(), c));
            boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
                if (ev.equals("click")) detonateStar(pts);
            });
        });
        q.end(fin + 36);
        return q;
    }

    private void starDash(Location a, Location b, int k) {
        boss.snapYaw(MathUtil.yawTo(a, b));
        Moves.trail(f, a, b, 2, 2, 8, false);
        boss.play(boss.anims.quickDash, 1, 3, 1.2f, ev -> {
        });
        boss.moveTo(b, 2);
        boss.noDamageTicks(6);
        sfx.play(a, Sfx.Id.DASH, 0.8f, 1.1f + k * 0.05f);
        sfx.play(b, Sfx.Id.SLASH, 0.8f, 1.2f + k * 0.06f);
        // leuchtende Narbe, die bis zur Detonation bleibt
        Location ga = ground(a), gb = ground(b);
        gb.setY(ga.getY());
        Telegraph scar = fx.fx.add(Telegraph.line(fx, ga, gb, 1.9f, 4));
        scar.tint(Palette.LIGHT, Palette.PINK_GLASS);
        teles.add(scar);
        lines.add(new Location[]{a, b, ga, gb});
        fx.fx.add(new CutSheet(fx, ga.clone().add(0, 0.1, 0), gb.clone().add(0, 0.1, 0), 1.6f, 8,
                Palette.LIGHT, Palette.PINK_GLASS, false));
    }

    private void detonateStar(Location[] pts) {
        sfx.play(boss.location(), Sfx.Id.FLASH);
        sfx.play(boss.location(), Sfx.Id.BOOM, 0.8f, 1.2f);
        for (Telegraph t : teles) t.strike();
        int i = 0;
        for (Location[] l : lines) {
            fx.fx.add(new CutSheet(fx, l[2].clone().add(0, 0.1, 0), l[3].clone().add(0, 0.1, 0), 3.4f, 16,
                    Palette.LIGHT, Palette.PINK_GLASS, true));
            damage("line" + i++, inLine(l[2], l[3], 1.0), "star", l[2], 0.6, 0.55);
        }
        for (int k = 0; k < 5; k++) {
            fx.fx.add(new net.aetherion.samuraiboss.render.fx.SpiralConverge.Pillar(fx, pts[k], 7f, 0.9f, 3, 4, 8));
            fx.fx.add(new Shards(fx, pts[k].clone().add(0, 0.5, 0), 6, 0.5f, 0.12f, 16, Palette.LIGHT, Palette.PINK_GLASS));
        }
        if (seal != null) seal.fadeOut(12);
        f.atmo().shake(f.participants(), 0.7f, 8);
    }

    @Override
    protected void onCancel() {
        for (Telegraph t : teles) t.clear();
        if (seal != null) seal.fadeOut(6);
        boss.model().setAllHidden(false);
        boss.setInvulnerable(false);
        marked.clear();
    }
}
