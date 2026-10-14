package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Explosion aus echten Blütenblatt-Displays: fliegen nach außen, trudeln, sinken, verblassen.
 */
public final class PetalBurst extends BaseFx {

    private final int end;

    /**
     * @param speed   Reichweite des ersten Stoßes (Blöcke)
     * @param upBias  wie stark nach oben (0 = Kugel, 1 = eher Fontäne)
     * @param life    Gesamtdauer in Ticks
     */
    public PetalBurst(FxContext c, Location center, int count, float speed, float upBias, int life) {
        super(c.fx);
        int n = c.count(count);
        int burst = Math.max(3, life / 6);
        int drift = Math.max(4, (int) (life * 0.55f));
        int fade = Math.max(3, life - burst - drift);
        end = 1 + burst + drift + fade + 1;
        for (int i = 0; i < n; i++) {
            Display d = c.petal(this, center, i);
            if (d == null) break;
            float size = 0.13f + Rand.f() * 0.09f;
            Vector3f dir = Rand.unit();
            dir.y = dir.y * (1f - upBias) + upBias * (0.6f + Rand.f() * 0.6f);
            dir.normalize();
            Vector3f p1 = new Vector3f(dir).mul(speed * (0.55f + Rand.f() * 0.6f));
            Vector3f wind = new Vector3f(Rand.signed() * 0.8f, 0, Rand.signed() * 0.8f);
            Vector3f p2 = new Vector3f(p1).mul(1.45f).add(wind).add(0, -0.6f - Rand.f() * 1.2f, 0);
            Vector3f p3 = new Vector3f(p2).add(wind.mul(0.6f)).add(0, -0.7f - Rand.f() * 0.6f, 0);
            Quaternionf r0 = FxContext.randomRot(), r1 = FxContext.randomRot(), r2 = FxContext.randomRot(), r3 = FxContext.randomRot();
            snapAt(0, d, c.petalTf(new Vector3f(), r0, size * 0.3f));
            lerpAt(1, d, c.petalTf(p1, r1, size), burst);
            lerpAt(1 + burst, d, c.petalTf(p2, r2, size), drift);
            lerpAt(1 + burst + drift, d, c.petalTf(p3, r3, 0f), fade);
            freeAt(end, d);
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }
}
