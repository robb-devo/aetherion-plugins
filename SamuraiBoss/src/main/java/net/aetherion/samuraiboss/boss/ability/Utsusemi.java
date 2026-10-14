package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.SlashArc;
import net.aetherion.samuraiboss.render.fx.SpiralConverge;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * 空蝉 Utsusemi – "Zikadenhülle": Wer ihn zu sehr bedrängt, schlägt plötzlich nur noch in
 * Blütenblätter. Er löst sich auf, ein Kreis erscheint HINTER dem Angreifer – und von dort
 * kommt der Gegenschlag.
 */
public final class Utsusemi extends Ability {

    private Telegraph tele;
    private Location dest;

    public Utsusemi(Fight f) {
        super(f, "utsusemi");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return dist < 4 ? 2 : 0;
    }

    @Override
    protected Sequence build() {
        Location tl = targetLoc();
        Vector back = tl.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) back = MathUtil.dirOfYaw(boss.yaw());
        back.normalize().multiply(-2.4);
        dest = dashEnd(tl, tl.clone().add(back));
        Location from = boss.location();
        Location g = ground(dest);
        boolean wasDrawn = boss.drawn();
        return new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.setInvulnerable(true);
                    fx.fx.add(new PetalBurst(fx, from.clone().add(0, 1.2, 0), 26, 2.4f, 0.35f, 30));
                    sfx.play(from, Sfx.Id.PETALS);
                    sfx.play(from, Sfx.Id.WHOOSH);
                    boss.model().setAllHidden(true);
                    tele = fx.fx.add(Telegraph.circle(fx, g, 2.5f, 14));
                })
                .at(2, () -> boss.moveTo(dest, 0))
                .at(3, () -> fx.fx.add(new SpiralConverge(fx, g, 18, 2.6f, 2.2f, 10, false)))
                .at(14, () -> {
                    boss.model().setAllHidden(false);
                    boss.setInvulnerable(false);
                    boss.face(targetLoc());
                    boss.snapYaw(MathUtil.yawTo(dest, targetLoc()));
                    if (wasDrawn) boss.play(boss.anims.yokogiri, 0, 5, 1.3f, ev -> {
                        if (ev.equals("hit")) strike();
                    });
                    else boss.play(boss.anims.iaiStrike, 0, 4, 1f, ev -> {
                        if (ev.equals("cut")) strike();
                    });
                })
                .at(wasDrawn ? 30 : 26, () -> {
                    if (!wasDrawn) boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
                    });
                })
                .end(wasDrawn ? 32 : 26 + 36);
    }

    private void strike() {
        tele.strike();
        float yaw = boss.yaw();
        fx.fx.add(new SlashArc(fx, dest.clone().add(0, 1.1, 0), SlashArc.plane(yaw, 0), 2.5f, 0f, 360f, 0.5f, 4, 9,
                Palette.LIGHT, Palette.PINK_GLASS));
        sfx.play(dest, Sfx.Id.HEAVY);
        damage("counter", inCircle(ground(dest), 2.6), "utsusemi", dest, 0.9, 0.4);
    }

    @Override
    protected void onCancel() {
        boss.model().setAllHidden(false);
        boss.setInvulnerable(false);
        if (tele != null) tele.clear();
    }
}
