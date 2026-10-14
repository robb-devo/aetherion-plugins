package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.fx.PetalBurst;
import net.aetherion.samuraiboss.render.fx.Shockwave;
import net.aetherion.samuraiboss.render.fx.SlashArc;
import net.aetherion.samuraiboss.render.fx.Telegraph;
import net.aetherion.samuraiboss.render.fx.Tornado;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 桜嵐 Sakura-arashi – der Blütensturm.
 *
 * <p>Er wirbelt zweimal um die eigene Achse (drei Treffer im Kreis um ihn). Aus dem Wirbel löst
 * sich ein Tornado aus echten Blütenblättern und Windschlieren, der langsam auf die Spieler
 * zuwandert: Wer hineingerät, wird angesaugt, hochgerissen und kurz geblendet. In Phase 3 sind
 * es zwei Tornados.</p>
 */
public final class SakuraTornado extends Ability {

    private static final class Twister {
        Tornado fx;
        Location origin;
        Vector3f pos = new Vector3f();
        LivingEntity chase;
    }

    private final List<Twister> twisters = new ArrayList<>();
    private Telegraph tele;
    private Location center;
    private static final float SPIN_R = 4.4f;
    private static final int LIFE = 150;

    public SakuraTornado(Fight f) {
        super(f, "tornado");
    }

    @Override
    public int weight(Phase phase, double dist) {
        if (phase == Phase.SEI) return 0;
        return dist < 5 ? 4 : 2;
    }

    @Override
    protected Sequence build() {
        twisters.clear();
        center = boss.location();
        Location g = ground(center);
        int count = f.phase() == Phase.SANGE ? 2 : 1;
        int windup = 16;
        Sequence q = new Sequence()
                .at(0, () -> {
                    boss.stopWalking();
                    boss.face(targetLoc());
                    tele = fx.fx.add(Telegraph.circle(fx, g, SPIN_R, windup));
                    sfx.play(center, Sfx.Id.WIND, 0.8f, 0.7f);
                    boss.setWind(1.4f);
                })
                .at(windup - 4, () -> {
                    if (!boss.drawn()) boss.setDrawn(true);
                    boss.play(boss.anims.spinSlash, 3, 6, 1f, ev -> {
                        switch (ev) {
                            case "hit" -> spinHit(0);
                            case "hit2" -> spinHit(1);
                            case "hit3" -> spinHit(2);
                            default -> {
                            }
                        }
                    });
                })
                .at(windup + 24, () -> spawnTornados(count))
                .during(windup + 26, windup + 26 + LIFE, this::steer)
                .at(windup + 26 + LIFE, this::endTornados)
                .at(windup + 40, () -> boss.setWind(0.35f));
        q.end(windup + 26 + LIFE + 2);
        return q;
    }

    private void spinHit(int i) {
        if (i == 0 && tele != null) tele.strike();
        float yaw = boss.yaw() + i * 120f;
        Location c = center.clone().add(0, 1.0 + i * 0.25, 0);
        fx.fx.add(new SlashArc(fx, c, SlashArc.plane(yaw, (i - 1) * 9f), SPIN_R - 0.6f, 0f, 300f, 0.55f, 3, 7,
                Palette.LIGHT, Palette.PINK_GLASS));
        fx.fx.add(new PetalBurst(fx, c, 10, 1.6f, 0.2f, 18));
        sfx.play(center, i == 2 ? Sfx.Id.HEAVY : Sfx.Id.SLASH, 1f, 0.9f + i * 0.12f);
        damage("spin", inCircle(ground(center), SPIN_R), "kesagiri", center, 1.0, 0.45);
        if (i == 2) {
            fx.fx.add(new Shockwave(fx, ground(center), 1f, SPIN_R + 2f, 10, 0.4f, Palette.LIGHT, Palette.PINK_GLASS));
            f.atmo().shakeNear(f.participants(), center, 0.5f, 4, 12);
        }
    }

    private void spawnTornados(int count) {
        sfx.play(center, Sfx.Id.TORNADO);
        List<LivingEntity> ts = targets();
        for (int i = 0; i < count; i++) {
            Twister t = new Twister();
            t.origin = ground(center);
            float yaw = boss.yaw() + (count == 1 ? 0 : (i == 0 ? -60 : 60));
            Vector start = MathUtil.dirOfYaw(yaw).multiply(1.8);
            t.pos.set((float) start.getX(), 0, (float) start.getZ());
            t.fx = fx.fx.add(new Tornado(fx, t.origin, 60, 7.5f, 0.8f, 3.0f));
            t.fx.moveTo(t.pos);
            t.chase = ts.isEmpty() ? null : ts.get(i % ts.size());
            twisters.add(t);
        }
        f.petalStorm(center, 6f);
    }

    private void steer(int rel) {
        for (Twister t : twisters) {
            if (t.fx.isStopped()) continue;
            if (t.chase == null || !t.chase.isValid() || t.chase.isDead() || !targets().contains(t.chase)) {
                List<LivingEntity> ts = targets();
                t.chase = ts.isEmpty() ? null : ts.get((int) (Math.random() * ts.size()));
            }
            Location now = t.origin.clone().add(t.pos.x, 0, t.pos.z);
            if (t.chase != null) {
                Vector3f want = MathUtil.delta(t.origin, t.chase.getLocation());
                want.y = 0;
                Vector3f d = new Vector3f(want).sub(t.pos);
                float len = d.length();
                float speed = f.phase() == Phase.SANGE ? 0.15f : 0.12f;
                if (len > 0.05f) t.pos.add(d.mul(Math.min(speed, len) / len));
                // im Kampfbereich halten
                Location clamped = f.clampDash(t.origin, t.origin.clone().add(t.pos.x, 0, t.pos.z));
                t.pos.set((float) (clamped.getX() - t.origin.getX()), 0, (float) (clamped.getZ() - t.origin.getZ()));
                t.fx.moveTo(t.pos);
                now = t.origin.clone().add(t.pos.x, 0, t.pos.z);
            }
            if (rel % 2 == 0) f.petalStormCenter(now);
            if (rel % 20 == 0) sfx.play(now, Sfx.Id.TORNADO, 0.6f, 1f);
            // Sog & Treffer
            float r = Math.max(1.2f, t.fx.radiusAt(1f) + 0.6f);
            for (LivingEntity e : targets()) {
                Location el = e.getLocation();
                double dx = now.getX() - el.getX(), dz = now.getZ() - el.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r + 4.5 || el.getY() > now.getY() + 8) continue;
                if (dist > r) {
                    // leichter Sog zur Mitte
                    double pull = 0.045 * (1 - (dist - r) / 4.5);
                    e.setVelocity(e.getVelocity().add(new Vector(dx / dist * pull, 0, dz / dist * pull)));
                } else if (rel % 10 == 0) {
                    // erfasst: hochreißen, herumschleudern, blenden
                    Vector tangential = new Vector(-dz, 0, dx).normalize().multiply(0.45);
                    e.setVelocity(tangential.setY(0.95));
                    damage("t" + rel, List.of(e), "tornado", now, 0, 0);
                    if (e instanceof Player p) f.atmo().blind(p, s.tornadoBlindTicks);
                    fx.fx.add(new PetalBurst(fx, el.clone().add(0, 1, 0), 6, 1.2f, 0.5f, 16));
                }
            }
        }
    }

    private void endTornados() {
        for (Twister t : twisters) {
            if (!t.fx.isStopped()) {
                t.fx.finish();
                Location at = t.origin.clone().add(t.pos.x, 1, t.pos.z);
                fx.fx.add(new PetalBurst(fx, at, 18, 2.4f, 0.4f, 26));
                sfx.play(at, Sfx.Id.WIND, 1f, 1.3f);
            }
        }
        f.petalDrift();
    }

    @Override
    protected void onCancel() {
        if (tele != null) tele.clear();
        for (Twister t : twisters) if (!t.fx.isStopped()) t.fx.finish();
        boss.setWind(0.35f);
        f.petalDrift();
    }
}
