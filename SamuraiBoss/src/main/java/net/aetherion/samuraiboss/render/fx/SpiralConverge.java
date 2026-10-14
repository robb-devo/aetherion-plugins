package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Blütenblätter wirbeln spiralförmig aus der Umgebung zu einem Punkt zusammen (Erscheinen des
 * Bosses) – oder umgekehrt auseinander (reverse = true, sein Vergehen).
 */
public final class SpiralConverge extends BaseFx {

    private final int end;

    public SpiralConverge(FxContext c, Location center, int count, float radius, float height, int duration, boolean reverse) {
        super(c.fx);
        int n = c.count(count);
        int steps = 5;
        int stepT = Math.max(2, duration / steps);
        end = stepT * steps + 3;
        for (int i = 0; i < n; i++) {
            Display d = c.petal(this, center, i);
            if (d == null) break;
            float a0 = Rand.f() * 6.283f;
            float r0 = radius * (0.6f + Rand.f() * 0.6f);
            float y0 = Rand.f() * height * 0.4f;
            float size = 0.16f + Rand.f() * 0.1f;
            float turns = 1.2f + Rand.f() * 0.6f;
            Vector3f[] pts = new Vector3f[steps + 1];
            for (int k = 0; k <= steps; k++) {
                float u = k / (float) steps;
                float a = a0 + turns * 6.283f * u;
                float r = r0 * (1 - u) * (1 - u) + 0.15f * u;
                float y = y0 + (height - y0) * (float) Math.pow(u, 0.8);
                pts[k] = new Vector3f((float) Math.cos(a) * r, y, (float) Math.sin(a) * r);
            }
            if (reverse) {
                for (int k = 0; k < pts.length / 2; k++) {
                    Vector3f t = pts[k];
                    pts[k] = pts[pts.length - 1 - k];
                    pts[pts.length - 1 - k] = t;
                }
            }
            int delay = Rand.range(0, Math.max(1, duration / 5));
            snapAt(delay, d, c.petalTf(pts[0], FxContext.randomRot(), reverse ? size : 0f));
            for (int k = 1; k <= steps; k++) {
                float s = reverse ? size * (1 - (k / (float) steps)) : size * Math.min(1f, k / 2f) * (k == steps ? 0.2f : 1f);
                lerpAt(delay + 1 + (k - 1) * stepT, d, c.petalTf(pts[k], FxContext.randomRot(), s), stepT);
            }
            freeAt(Math.min(end + duration / 5, delay + 2 + steps * stepT), d);
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end + 12;
    }

    /** Hilfsmethode für Lichtsäulen. */
    public static final class Pillar extends BaseFx {
        private final int end;

        public Pillar(FxContext c, Location base, float height, float width, int grow, int hold, int fade) {
            super(c.fx);
            end = 1 + grow + hold + fade + 1;
            Display core = block(base, Palette.LIGHT);
            Display glow = block(base, Palette.PINK_GLASS);
            for (Display d : new Display[]{core, glow}) {
                if (d == null) continue;
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                float w = d == core ? width * 0.35f : width;
                snapAt(0, d, Tf.box(new Vector3f(0, 0.01f, 0), new Quaternionf(), new Vector3f(w * 0.2f, 0.02f, w * 0.2f)));
                lerpAt(1, d, Tf.box(new Vector3f(0, height / 2, 0), new Quaternionf(), new Vector3f(w, height, w)), grow);
                lerpAt(1 + grow + hold, d, Tf.box(new Vector3f(0, height / 2 + height * 0.3f, 0), new Quaternionf(),
                        new Vector3f(0f, height * 1.2f, 0f)), fade);
                freeAt(end, d);
            }
        }

        @Override
        protected boolean update(int age) {
            return age <= end;
        }
    }
}
