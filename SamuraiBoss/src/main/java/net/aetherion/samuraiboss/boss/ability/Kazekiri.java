package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.CrescentWave;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 風斬り Kaze-giri – fliegende Windklinge. Eine Sichel aus Licht rast flach (Kniehöhe) über den
 * Boden: <b>drüberspringen</b> oder ausweichen. Ab Phase 2 drei Sicheln im Fächer.
 */
public final class Kazekiri extends Ability {

    private final List<CrescentWave> waves = new ArrayList<>();
    private final List<Telegraph> teles = new ArrayList<>();
    private Location origin;
    private float yaw;
    private static final float RANGE = 19f;
    private static final float WIDTH = 4.6f;

    public Kazekiri(Fight f) {
        super(f, "kazekiri");
    }

    @Override
    public int weight(Phase phase, double dist) {
        return dist > 5 ? 4 : 1;
    }

    @Override
    protected Sequence build() {
        waves.clear();
        teles.clear();
        origin = boss.location();
        yaw = MathUtil.yawTo(origin, targetLoc());
        boolean sheathed = !boss.drawn();
        int fan = f.phase() == Phase.SEI ? 1 : 3;
        int windup = 18;
        Location g = ground(origin);
        Sequence q = new Sequence().at(0, () -> {
            boss.stopWalking();
            boss.snapYaw(yaw);
            for (int i = 0; i < fan; i++) {
                float y = yaw + (i - (fan - 1) / 2f) * 26f;
                Vector d = MathUtil.dirOfYaw(y);
                Location a = g.clone().add(d.clone().multiply(1.0));
                Location b = g.clone().add(d.clone().multiply(RANGE));
                teles.add(fx.fx.add(Telegraph.line(fx, a, b, WIDTH, windup)));
            }
            if (sheathed) boss.play(boss.anims.iaiReady, ev -> {
            });
            else boss.play(boss.anims.yokogiri, 4, 6, 0.9f, ev -> {
                if (ev.equals("hit")) release(fan);
            });
        });
        if (sheathed) {
            q.at(windup, () -> boss.play(boss.anims.iaiStrike, 0, 4, 1f, ev -> {
                if (ev.equals("cut")) release(fan);
            }));
            q.at(windup + 16, () -> boss.play(boss.anims.iaiFinish, 4, 6, 1f, ev -> {
            }));
        }
        q.during(windup, windup + 40, t -> checkWaves());
        q.end(sheathed ? windup + 16 + 35 : windup + 40);
        return q;
    }

    private void release(int fan) {
        sfx.play(origin, Sfx.Id.WIND);
        sfx.play(origin, Sfx.Id.SLASH);
        for (Telegraph t : teles) t.strike();
        for (int i = 0; i < fan; i++) {
            float y = yaw + (i - (fan - 1) / 2f) * 26f;
            Location start = ground(origin).add(MathUtil.dirOfYaw(y).multiply(1.0));
            waves.add(fx.fx.add(new CrescentWave(fx, start, y, WIDTH, 0.62f, 0.95f, RANGE - 1f, Palette.LIGHT, Palette.PINK_GLASS)));
        }
    }

    private void checkWaves() {
        for (int w = 0; w < waves.size(); w++) {
            CrescentWave cw = waves.get(w);
            if (cw.isStopped() || cw.isVanishing()) continue;
            Vector3f front = cw.front();
            Vector3f dir = cw.direction();
            Location o = ground(origin).add(MathUtil.dirOfYaw(cw.yaw()).multiply(1.0));
            Location c = o.clone().add(front.x, 0, front.z);
            // Front = Strecke quer zur Flugrichtung
            Vector side = new Vector(-dir.z, 0, dir.x).multiply(WIDTH / 2);
            Location a = c.clone().add(side), b = c.clone().subtract(side);
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : targets()) {
                // wer hoch genug springt, entgeht der Welle
                if (e.getLocation().getY() > c.getY() + 0.95) continue;
                if (net.aetherion.samuraiboss.util.Hits.inLine(e, a, b, 0.9, -0.5, 1.4)) hit.add(e);
            }
            if (!hit.isEmpty()) damage("wave" + w, hit, "kazekiri", c, 0.7, 0.5);
        }
    }

    @Override
    protected void onCancel() {
        for (Telegraph t : teles) t.clear();
        for (CrescentWave w : waves) w.vanish();
    }
}
