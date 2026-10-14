package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Siegelkreis am Boden: ein Ring aus Kanji, die langsam um die Mitte kreisen, umschlossen von
 * zwei leuchtenden Polygonringen. Mit Resource Pack: zwei texturierte Siegel-Scheiben.
 */
public final class SealCircle extends BaseFx {

    private record Glyph(Display d, float angle, float r, boolean text, float size) {
    }

    private final FxContext c;
    private final List<Glyph> glyphs = new ArrayList<>();
    private final float radius;
    private float spin;
    private final float spinSpeed;
    private int endAt = Integer.MAX_VALUE;
    private boolean fading;
    private float alphaScale = 0.05f;
    private final boolean packSeal;
    private Display sealA, sealB;

    public SealCircle(FxContext c, Location center, float radius, String ring, int rgb, float spinSpeed) {
        super(c.fx);
        this.c = c;
        this.radius = radius;
        this.spinSpeed = spinSpeed;
        this.packSeal = c.pack;
        Location o = center.clone().add(0, 0.04, 0);
        if (packSeal) {
            sealA = item(o, PackItems.item(c.settings, "seal"));
            sealB = item(o, PackItems.item(c.settings, "seal-ring"));
            for (Display d : new Display[]{sealA, sealB}) if (d != null) d.setBrightness(new Display.Brightness(15, 15));
        } else {
            int n = ring.length();
            for (int i = 0; i < n; i++) {
                TextDisplay td = text(o);
                if (td == null) break;
                td.text(c.kanji(String.valueOf(ring.charAt(i)), rgb));
                td.setBrightness(new Display.Brightness(15, 15));
                glyphs.add(new Glyph(td, (float) (i * Math.PI * 2 / n), radius * 0.78f, true, Math.max(1.2f, radius * 0.55f)));
            }
            ring(o, radius, Palette.PINK, 0.09f);
            ring(o, radius * 0.6f, Palette.LIGHT, 0.05f);
        }
        place(0);
    }

    private void ring(Location o, float r, BlockData mat, float w) {
        int segs = Math.max(12, Math.min(30, Math.round(r * 4.5f)));
        for (int i = 0; i < segs; i++) {
            Display d = block(o, mat);
            if (d == null) break;
            d.setBrightness(new Display.Brightness(15, 15));
            glyphs.add(new Glyph(d, (float) (i * Math.PI * 2 / segs), r, false, (float) (2 * Math.PI * r / segs) * 1.07f));
        }
        // w wird beim Platzieren verwendet (gleich für alle Ringe)
    }

    private void place(int interp) {
        if (packSeal) {
            float s = radius * 2.1f * alphaScale;
            Quaternionf flat = new Quaternionf().rotationY(spin).rotateX((float) (-Math.PI / 2));
            Quaternionf flat2 = new Quaternionf().rotationY(-spin * 1.6f).rotateX((float) (-Math.PI / 2));
            set(sealA, Tf.centered(new Vector3f(), flat, s), interp);
            set(sealB, Tf.centered(new Vector3f(0, 0.02f, 0), flat2, s * 1.15f), interp);
            return;
        }
        for (Glyph g : glyphs) {
            float a = g.angle + (g.text ? spin : -spin * 0.6f);
            float r = g.r * (0.6f + 0.4f * alphaScale);
            Vector3f p = new Vector3f((float) Math.cos(a) * r, 0.01f, (float) Math.sin(a) * r);
            Transformation t;
            if (g.text) {
                Quaternionf q = new Quaternionf().rotationY(-a - (float) (Math.PI / 2)).rotateX((float) (-Math.PI / 2));
                float s = g.size * alphaScale;
                Vector3f off = q.transform(new Vector3f(0, c.kanjiCenterOffset(s), 0));
                t = new Transformation(p.add(off), q, new Vector3f(s, s, s), new Quaternionf());
            } else {
                Quaternionf q = new Quaternionf().rotationY(-a + (float) (Math.PI / 2));
                t = Tf.box(p, q, new Vector3f(g.size * alphaScale, 0.03f, 0.08f));
            }
            set(g.d, t, interp);
        }
    }

    private static void set(Display d, Transformation t, int interp) {
        if (d == null) return;
        if (interp <= 0) DisplayFactory.snap(d, t);
        else DisplayFactory.lerp(d, t, interp);
    }

    public void fadeOut(int ticks) {
        fading = true;
        endAt = age + ticks + 2;
    }

    @Override
    protected boolean update(int age) {
        if (age >= endAt) return false;
        if (age == 0) return true;
        int step = 6;
        if (age % step == 0) {
            spin += spinSpeed * step;
            if (fading) alphaScale = Math.max(0f, alphaScale - 0.35f);
            else alphaScale = Math.min(1f, alphaScale + (age < 20 ? 0.34f : 0f));
            place(step);
        }
        return true;
    }
}
