package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.boss.SamuraiSkins;
import net.aetherion.samuraiboss.render.fx.Afterimage;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.SlashArc;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 残像連斬 Zanzō-renzan – "Kette der Nachbilder" (Phase 3).
 *
 * <p>Fünf Sprünge in schneller Folge rund um die Herausforderer. An jeder Stelle bleibt ein
 * erstarrtes, leuchtendes Nachbild in Schlagpose stehen – umgeben von einem Kreis, der sich
 * langsam füllt. Erst wenn er die Klinge wegsteckt, schlagen ALLE Nachbilder gleichzeitig zu.</p>
 */
public final class AfterimageChain extends Ability {

    private final List<Location> spots = new ArrayList<>();
    private final List<Afterimage> images = new ArrayList<>();
    private final List<Telegraph> teles = new ArrayList<>();
    private static final int COUNT = 5;
    private static final int FIRST = 6;
    private static final int STEP = 7;
    private static final float RADIUS = 2.6f;

    public AfterimageChain(Fight f) {
        super(f, "afterimage");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return phase == Phase.SANGE ? (dist > 3 ? 3 : 2) : 0;
    }

    @Override
    protected Sequence build() {
        spots.clear();
        images.clear();
        teles.clear();
        List<LivingEntity> ts = targets();
        Location from = boss.location();
        for (int i = 0; i < COUNT; i++) {
            Location around = ts.isEmpty() ? targetLoc() : ts.get(i % ts.size()).getLocation();
            Location spot = around.clone().add(MathUtil.dirOfYaw(Rand.f() * 360f).multiply(2.0 + Rand.f() * 0.8));
            spot = f.clampDash(from, spot);
            spots.add(spot);
            from = spot;
        }
        int finish = FIRST + COUNT * STEP + 2;
        int click = finish + 25;
        Sequence q = new Sequence().at(0, () -> {
            boss.stopWalking();
            sfx.play(boss.location(), Sfx.Id.GHOST, 1f, 1.3f);
            boss.flash();
        });
        for (int i = 0; i < COUNT; i++) {
            final int k = i;
            int t = FIRST + i * STEP;
            q.at(t, () -> dash(k));
            q.at(t + 4, () -> freeze(k, click - (t + 4)));
        }
        q.at(finish, () -> {
            if (targetValid()) boss.face(target.getLocation());
            boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
                if (ev.equals("click")) detonate();
            });
        });
        q.end(finish + 36);
        return q;
    }

    private void dash(int k) {
        Location to = spots.get(k);
        Location from = boss.location();
        LivingEntity aim = nearestTo(to);
        float yaw = aim != null ? MathUtil.yawTo(to, aim.getLocation()) : MathUtil.yawTo(from, to);
        Moves.trail(f, from, to, 2, 1, 6, false);
        boss.moveTo(to, 2);
        boss.snapYaw(yaw);
        boss.noDamageTicks(5);
        if (!boss.drawn()) boss.setDrawn(true);
        boss.play(boss.anims.yokogiri, 1, 3, 1.7f, ev -> {
        });
        sfx.play(from, Sfx.Id.DASH, 0.7f, 1.2f + k * 0.05f);
        sfx.play(to, Sfx.Id.SLASH, 0.7f, 1.3f);
    }

    private LivingEntity nearestTo(Location l) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : targets()) {
            double d = e.getLocation().distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Nachbild einfrieren + Kreis, der sich bis zum Klick füllt. */
    private void freeze(int k, int fill) {
        images.add(Moves.ghost(f, 400, 6, SamuraiSkins.SAKURA));
        Location g = ground(spots.get(k));
        teles.add(fx.fx.add(Telegraph.circle(fx, g, RADIUS, Math.max(6, fill))));
        sfx.play(g, Sfx.Id.GLINT, 0.6f, 0.9f + k * 0.1f);
    }

    private void detonate() {
        sfx.play(boss.location(), Sfx.Id.FLASH);
        for (Telegraph t : teles) t.strike();
        for (int i = 0; i < spots.size(); i++) {
            Location c = spots.get(i);
            float yaw = Rand.f() * 360f;
            fx.fx.add(new SlashArc(fx, c.clone().add(0, 1.1, 0), SlashArc.plane(yaw, Rand.signed() * 30f), RADIUS, 0f, 340f,
                    0.5f, 3, 8, Palette.LIGHT, Palette.PINK_GLASS));
            fx.fx.add(new Shards(fx, c.clone().add(0, 1.2, 0), 10, 0.6f, 0.14f, 16, Palette.PINK_GLASS, Palette.WHITE_GLASS, Palette.LIGHT));
            fx.fx.add(new PetalBurst(fx, c.clone().add(0, 1.2, 0), 8, 1.5f, 0.3f, 18));
            sfx.play(c, Sfx.Id.CUT, 0.8f, 1f + i * 0.07f);
            damage("img" + i, inCircle(ground(c), RADIUS), "afterimage", c, 0.7, 0.4);
        }
        for (Afterimage a : images) fx.fx.kill(a);
        images.clear();
        f.atmo().shake(f.participants(), 0.5f, 6);
    }

    @Override
    protected void onCancel() {
        for (Telegraph t : teles) t.clear();
        for (Afterimage a : images) fx.fx.kill(a);
        images.clear();
    }
}
