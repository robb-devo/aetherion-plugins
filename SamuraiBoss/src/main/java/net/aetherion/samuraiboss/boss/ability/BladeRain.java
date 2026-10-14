package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.GhostBlade;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 刃の雨 Ha-no-ame – Klingenregen (Phase 3).
 *
 * <p>Der Geist schwebt empor und reckt die Klinge zum roten Mond; ein Siegel aus Kanji dreht sich
 * hoch über dem Hof. In Wellen erscheinen Geisterklingen hoch in der Luft – Spitze nach unten –
 * und stürzen dann senkrecht herab. Jede Einschlagstelle ist vorher als Kreis markiert.</p>
 */
public final class BladeRain extends Ability {

    private static final class Drop {
        GhostBlade gb;
        Location impact;
        Telegraph tele;
    }

    private final List<Drop> drops = new ArrayList<>();
    private SealCircle skySeal;
    private static final float HEIGHT = 11.5f;
    private static final int WARN = 18;
    private static final Quaternionf DOWN = MathUtil.lookAlongZ(new Vector3f(0, -1, 0));

    public BladeRain(Fight f) {
        super(f, "blade-rain");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return phase == Phase.SANGE ? 3 : 0;
    }

    @Override
    protected Sequence build() {
        drops.clear();
        int waves = 4;
        int perWave = fx.count(7);
        Sequence q = new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.setHover(3.2f);
                    boss.setInvulnerable(true);
                    boss.play(boss.anims.raiseSky, 4, 6, 1f, ev -> {
                    });
                    sfx.play(boss.location(), Sfx.Id.RISE);
                })
                .at(12, () -> {
                    Location sky = f.arena().center().add(0, HEIGHT + 2.5, 0);
                    skySeal = fx.fx.add(new SealCircle(fx, sky, 9f, "刃雨天降散華桜", 0xFF9EC0, 0.04f));
                    sfx.play(sky, Sfx.Id.SUMMON, 1.2f, 0.7f);
                });
        for (int w = 0; w < waves; w++) {
            int t = 20 + w * 16;
            q.at(t, () -> wave(perWave));
        }
        int end = 20 + (waves - 1) * 16 + WARN + 24;
        q.at(end - 18, () -> {
            boss.setHover(0.6f);
            boss.setInvulnerable(false);
            boss.stopAction(8);
            if (skySeal != null) skySeal.fadeOut(12);
        });
        q.end(end);
        return q;
    }

    private void wave(int count) {
        List<LivingEntity> ts = targets();
        Location c = f.arena().center();
        for (int i = 0; i < count; i++) {
            Location at;
            if (i < ts.size()) {
                // eine Klinge pro Spieler genau auf ihn (mit Vorhalt)
                LivingEntity e = ts.get(i);
                at = e.getLocation().add(e.getVelocity().setY(0).multiply(6));
            } else if (!ts.isEmpty() && Rand.chance(0.6)) {
                LivingEntity e = Rand.pick(ts);
                at = e.getLocation().add(Rand.signed() * 4.5, 0, Rand.signed() * 4.5);
            } else {
                at = c.clone().add(Rand.signed() * 15, 0, Rand.signed() * 12);
            }
            at = f.clampDash(c, at);
            Location g = ground(at);
            Drop d = new Drop();
            d.impact = g;
            d.tele = fx.fx.add(Telegraph.circle(fx, g, 1.6f, WARN));
            d.gb = fx.fx.add(new GhostBlade(fx, g, 1.25f, false));
            d.gb.pose(new Vector3f(0, HEIGHT, 0), DOWN, 0.05f, 0);
            drops.add(d);
            int delay = Rand.range(0, 3);
            seq.later(1 + delay, () -> d.gb.pose(new Vector3f(0, HEIGHT - 0.5f, 0), DOWN, 1f, 5));
            seq.later(WARN - 3, () -> d.gb.pose(new Vector3f(0, 1.2f * 1.25f - 0.55f, 0), DOWN, 1f, 3));
            seq.later(WARN, () -> impact(d));
            seq.later(WARN + 16, () -> {
                if (!d.gb.isDead()) d.gb.dissolve(6);
                fx.fx.add(new PetalBurst(fx, d.impact.clone().add(0, 1, 0), 4, 0.8f, 0.5f, 14));
            });
        }
        sfx.play(c.clone().add(0, HEIGHT, 0), Sfx.Id.BLADE_FLY, 1.4f, 0.7f);
    }

    private void impact(Drop d) {
        if (d.tele != null) d.tele.strike();
        sfx.play(d.impact, Sfx.Id.BLADE_HIT, 1f, 0.9f + Rand.f() * 0.2f);
        fx.fx.add(new Shards(fx, d.impact.clone().add(0, 0.2, 0), 5, 0.45f, 0.12f, 14, Palette.LIGHT, Palette.PINK_GLASS));
        damage("drop" + System.identityHashCode(d), inCircle(d.impact, 1.6), "blade-rain", d.impact, 0.4, 0.3);
        f.atmo().shakeNear(f.participants(), d.impact, 0.25f, 2, 6);
    }

    @Override
    protected void onCancel() {
        for (Drop d : drops) {
            if (d.tele != null) d.tele.clear();
            if (!d.gb.isDead()) d.gb.dissolve(4);
        }
        if (skySeal != null) skySeal.fadeOut(6);
        boss.setHover(f.ghostForm() ? 0.6f : 0f);
        boss.setInvulnerable(false);
    }
}
