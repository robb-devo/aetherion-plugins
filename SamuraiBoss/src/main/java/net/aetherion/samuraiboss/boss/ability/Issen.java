package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.CutSheet;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.Shards;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 一閃 Issen – der Iaijutsu-Blitzschnitt (Phase 1).
 *
 * <p>Er geht tief in die Iai-Haltung, eine Linie füllt sich auf dem Boden. Dann ist er
 * plötzlich am anderen Ende – eine Nachbild-Spur bleibt zurück, die Luft ist zerschnitten.
 * Er schüttelt das Blut ab und steckt die Klinge weg. <b>Erst beim Klick des Einrastens</b>
 * fällt der Schaden auf alle, die in der Linie standen – der klassische Anime-Moment.</p>
 */
public final class Issen extends Ability {

    private static final int WINDUP = 26;
    private Telegraph tele;
    private final List<LivingEntity> marked = new ArrayList<>();

    public Issen(Fight f) {
        super(f, "issen");
    }

    @Override
    public int weight(Phase phase, double dist) {
        if (phase != Phase.SEI) return 0;
        return dist > 3 ? 5 : 2;
    }

    @Override
    protected Sequence build() {
        marked.clear();
        Location start = boss.location();
        Location end = dashEnd(start, through(start, targetLoc(), 4.0, 17.0));
        Location gStart = ground(start), gEnd = ground(end);
        gEnd.setY(gStart.getY());
        float yaw = MathUtil.yawTo(start, end);
        return new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.snapYaw(yaw);
                    boss.play(boss.anims.iaiReady, ev -> {
                    });
                    tele = fx.fx.add(Telegraph.line(fx, gStart, gEnd, 1.7f, WINDUP));
                    sfx.play(start, Sfx.Id.TELEGRAPH);
                })
                .at(WINDUP - 8, () -> {
                    sfx.play(boss.location().add(0, 1.2, 0), Sfx.Id.GLINT);
                    fx.fx.add(new Shards(fx, boss.location().add(0, 1.0, 0), 4, 0.3f, 0.06f, 8, Palette.LIGHT));
                })
                .at(WINDUP, () -> {
                    // Blitzschritt
                    Moves.trail(f, start, end, 3, 4, 10, false);
                    boss.play(boss.anims.iaiStrike, 0, 4, 1f, ev -> {
                    });
                    boss.moveTo(end, 2);
                    boss.noDamageTicks(10);
                    sfx.play(start, Sfx.Id.DASH);
                    sfx.play(end, Sfx.Id.SLASH);
                    tele.strike();
                    fx.fx.add(new CutSheet(fx, start.clone().add(0, 0.1, 0), end.clone().add(0, 0.1, 0), 2.3f, 12,
                            Palette.LIGHT, Palette.PINK_GLASS, true));
                    // Wer jetzt in der Linie steht, ist "bereits geschnitten"
                    marked.addAll(inLine(gStart, gEnd, 0.95));
                    f.atmo().shakeNear(f.participants(), end, 0.6f, 4, 14);
                })
                .at(WINDUP + 9, () -> boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
                    if (ev.equals("click")) detonate();
                }))
                .end(WINDUP + 9 + 36);
    }

    /** Der verzögerte Schnitt beim Einrasten. */
    private void detonate() {
        for (LivingEntity e : marked) {
            if (!e.isValid()) continue;
            Moves.crossCut(f, e.getLocation());
            sfx.play(e.getLocation(), Sfx.Id.CUT);
        }
        damage("cut", marked, "issen", boss.location(), 0.6, 0.35);
        marked.clear();
    }

    @Override
    protected void onCancel() {
        if (tele != null) tele.clear();
        boss.stopAction(4);
        boss.setDrawn(false);
    }
}
