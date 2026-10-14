package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blütentornado: Spirale aus Blütenblättern und weißen Windschlieren, die sich trichterförmig
 * nach oben weitet. Der Mittelpunkt kann bewegt werden (er wandert über das Schlachtfeld).
 */
public final class Tornado extends BaseFx {

    private static final class Orb {
        Display d;
        float angle, h, speed, size, rOff;
        boolean streak;
    }

    private final FxContext c;
    private final List<Orb> orbs = new ArrayList<>();
    /** Ringe aus Windschlieren, die dem Trichter eine klar lesbare Form geben. */
    private final List<Display[]> rings = new ArrayList<>();
    private float ringSpin;
    private static final int RINGS = 5, RING_SEGS = 7;
    private final Vector3f center = new Vector3f();
    private final float height, baseR, topR;
    private float grow = 0.15f;
    private boolean ending;
    private int endAt = Integer.MAX_VALUE;

    public Tornado(FxContext c, Location origin, int petals, float height, float baseR, float topR) {
        super(c.fx);
        this.c = c;
        this.height = height;
        this.baseR = baseR;
        this.topR = topR;
        int n = c.count(petals);
        for (int i = 0; i < n; i++) {
            Orb o = new Orb();
            o.d = c.petal(this, origin, i);
            if (o.d == null) break;
            o.angle = Rand.f() * 6.283f;
            o.h = Rand.f() * height;
            o.speed = 0.5f + Rand.f() * 0.35f;
            o.size = 0.2f + Rand.f() * 0.14f;
            o.rOff = Rand.range(-0.35f, 0.35f);
            DisplayFactory.snap(o.d, c.petalTf(new Vector3f(), FxContext.randomRot(), 0f));
            orbs.add(o);
        }
        int ringCount = Math.max(2, Math.round(RINGS * c.fx.density()));
        for (int k = 0; k < ringCount; k++) {
            Display[] seg = new Display[RING_SEGS];
            for (int i = 0; i < RING_SEGS; i++) {
                seg[i] = block(origin, k % 2 == 0 ? Palette.WHITE_GLASS : Palette.PINK_GLASS);
                if (seg[i] != null) {
                    seg[i].setBrightness(new Display.Brightness(15, 15));
                    DisplayFactory.snap(seg[i], DisplayFactory.HIDDEN);
                }
            }
            rings.add(seg);
        }
        int streaks = c.count(7);
        for (int i = 0; i < streaks; i++) {
            Orb o = new Orb();
            o.d = block(origin, Palette.WHITE_GLASS);
            if (o.d == null) break;
            o.d.setBrightness(new Display.Brightness(15, 15));
            o.streak = true;
            o.angle = Rand.f() * 6.283f;
            o.h = (i + 0.5f) / streaks * height;
            o.speed = 0.7f;
            DisplayFactory.snap(o.d, DisplayFactory.HIDDEN);
            orbs.add(o);
        }
    }

    public void moveTo(Vector3f rel) {
        center.set(rel);
    }

    private float ringHeight(int k) {
        return (k + 0.5f) / Math.max(1, rings.size()) * height * 0.92f;
    }

    public Vector3f center() {
        return new Vector3f(center);
    }

    /** Effektiver Radius in Bodennähe (für Treffer). */
    public float radiusAt(float y) {
        return (baseR + (topR - baseR) * Math.max(0, Math.min(1, y / height))) * grow;
    }

    public void finish() {
        if (ending) return;
        ending = true;
        for (int k = 0; k < rings.size(); k++) {
            for (Display d : rings.get(k)) {
                if (d == null) continue;
                Vector3f out = new Vector3f(Rand.signed(), 0.4f, Rand.signed()).mul(5f).add(center).add(0, ringHeight(k), 0);
                DisplayFactory.lerp(d, Tf.box(out, FxContext.randomRot(), new Vector3f()), 10);
            }
        }
        for (Orb o : orbs) {
            Vector3f out = new Vector3f((float) Math.cos(o.angle), 0.3f, (float) Math.sin(o.angle)).mul(6f + Rand.f() * 4f)
                    .add(center).add(0, o.h, 0);
            if (o.streak) DisplayFactory.lerp(o.d, Tf.box(out, new Quaternionf(), new Vector3f()), 10);
            else DisplayFactory.lerp(o.d, c.petalTf(out, FxContext.randomRot(), 0f), 12 + Rand.range(0, 6));
        }
        endAt = age + 20;
    }

    @Override
    protected boolean update(int age) {
        if (age >= endAt) return false;
        if (ending) return true;
        if (grow < 1f) grow = Math.min(1f, grow + 0.06f);
        if (age % 2 != 0) return true;
        // Trichter-Ringe: unten eng und schnell, oben weit und langsamer
        ringSpin += 0.55f;
        for (int k = 0; k < rings.size(); k++) {
            float h = ringHeight(k);
            float r = (baseR + (topR - baseR) * (h / height)) * grow + 0.15f;
            float spin = ringSpin * (1.6f - k * 0.18f) * (k % 2 == 0 ? 1 : -1);
            Display[] seg = rings.get(k);
            float segLen = (float) (2 * Math.PI * r / RING_SEGS) * 0.62f;
            for (int i = 0; i < seg.length; i++) {
                if (seg[i] == null) continue;
                float a = spin + i * (float) (2 * Math.PI / RING_SEGS);
                Vector3f p = new Vector3f((float) Math.cos(a) * r, h + (float) Math.sin(a * 2 + age * 0.1f) * 0.15f,
                        (float) Math.sin(a) * r).add(center);
                Quaternionf rot = new Quaternionf().rotationY(-a + (float) (Math.PI / 2)).rotateZ(0.18f);
                DisplayFactory.lerp(seg[i], Tf.box(p, rot, new Vector3f(segLen, 0.05f, 0.05f + 0.04f * k)), 2);
            }
        }
        for (Orb o : orbs) {
            float r = (baseR + (topR - baseR) * (o.h / height)) * grow + o.rOff;
            o.angle += o.speed * (1.4f - 0.6f * o.h / height) * 0.9f;
            o.h += o.streak ? 0f : 0.18f;
            if (o.h > height) o.h -= height;
            Vector3f p = new Vector3f((float) Math.cos(o.angle) * r, o.h, (float) Math.sin(o.angle) * r).add(center);
            if (o.streak) {
                // Windschliere: dünner, tangentialer Bogen
                Quaternionf rot = new Quaternionf().rotationY(-o.angle + (float) (Math.PI / 2)).rotateZ(0.12f);
                DisplayFactory.lerp(o.d, Tf.box(p, rot, new Vector3f(r * 1.3f, 0.04f, 0.04f)), 2);
            } else {
                DisplayFactory.lerp(o.d, c.petalTf(p, FxContext.randomRot(), o.size), 2);
            }
        }
        return true;
    }
}
