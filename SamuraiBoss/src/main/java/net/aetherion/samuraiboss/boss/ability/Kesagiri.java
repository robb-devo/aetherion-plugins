package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.SlashArc;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * 袈裟斬り Kesagiri – der diagonale Schnitt von der Schulter zur Hüfte (Nahkampf, Kegel).
 * In Phase 1 zieht er dafür aus der Scheide und steckt danach wieder weg; ab Phase 2 kämpft er
 * mit gezogener Klinge. Mit {@code tsubame=true}: 燕返し Tsubame-gaeshi, die "Schwalbenwende" –
 * sofort ein zweiter, aufsteigender Schnitt.
 */
public final class Kesagiri extends Ability {

    private final boolean tsubame;
    private Telegraph tele;
    private float yaw;
    private Location origin;

    public Kesagiri(Fight f, boolean tsubame) {
        super(f, tsubame ? "tsubame" : "kesagiri");
        this.tsubame = tsubame;
    }

    @Override
    public int weight(Phase phase, double dist) {
        if (dist > 6.5) return 0;
        if (tsubame) return phase == Phase.SEI ? 0 : 4;
        return phase == Phase.SEI ? 6 : 5;
    }

    @Override
    protected Sequence build() {
        Location start = boss.location();
        Location tl = targetLoc();
        // kurzer Ausfallschritt, falls das Ziel etwas weiter weg ist
        double dist = MathUtil.distXZ(start, tl);
        Location stand = start;
        if (dist > 3.2) stand = dashEnd(start, through(start, tl, -2.3, 4.0));
        yaw = MathUtil.yawTo(stand, tl);
        origin = stand;
        Location ground = ground(stand);
        boolean sheathed = !boss.drawn();
        int windup = sheathed ? 13 : (tsubame ? 8 : 10);
        final Location standF = stand;
        Sequence q = new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.snapYaw(yaw);
                    if (standF != start) {
                        boss.moveTo(standF, 3);
                        sfx.play(start, Sfx.Id.WHOOSH);
                    }
                    tele = fx.fx.add(Telegraph.cone(fx, ground, yaw, 62, tsubame ? 5.0f : 4.6f, windup));
                    if (sheathed) {
                        boss.play(boss.anims.kesaIai, 3, 6, 1f, this::onEvent);
                    } else {
                        boss.play(tsubame ? boss.anims.tsubame : boss.anims.kesagiri, 3, 6, 1f, this::onEvent);
                    }
                });
        if (sheathed) {
            q.at(30, () -> boss.play(boss.anims.sheatheFromKamae, 6, 6, 1f, ev -> {
            })).end(30 + 30);
        } else {
            q.end(tsubame ? 30 : 26);
        }
        return q;
    }

    private void onEvent(String ev) {
        switch (ev) {
            case "windup" -> sfx.play(boss.location(), Sfx.Id.WHOOSH, 0.8f, 1.2f);
            case "hit" -> strike(false);
            case "hit2" -> strike(true);
            default -> {
            }
        }
    }

    private void strike(boolean rising) {
        Location c = origin.clone().add(0, 1.3, 0);
        // diagonale Sichel; Tsubame: zweite Sichel spiegelverkehrt aufsteigend
        // Kesagiri: von oben rechts (lokal -X, angehoben) nach unten links; aufsteigend: gespiegelt (X-Form)
        float tilt = rising ? 48f : -52f;
        float start = 160f;
        float sweep = -140f;
        fx.fx.add(new SlashArc(fx, c, SlashArc.plane(yaw, tilt), 2.6f, start, sweep, 0.55f, 3, 8, Palette.LIGHT, Palette.PINK_GLASS));
        sfx.play(c, rising ? Sfx.Id.SLASH : Sfx.Id.HEAVY);
        if (tele != null && !rising) tele.strike();
        List<LivingEntity> hits = inCone(ground(origin), yaw, 64, tsubame ? 5.1 : 4.7);
        int n = damage(rising ? "rise" : "cut", hits, tsubame ? "tsubame" : "kesagiri", origin, 0.9, 0.3);
        for (LivingEntity e : hits) fx.fx.add(new PetalBurst(fx, e.getLocation().add(0, 1, 0), 6, 1.1f, 0.2f, 18));
        if (n > 0) f.atmo().shakeNear(f.participants(), origin, 0.5f, 3, 8);
    }

    @Override
    protected void onCancel() {
        if (tele != null) tele.clear();
    }
}
