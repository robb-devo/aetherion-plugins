package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Schnittebene der Ultimate 天地一閃: eine riesige, hauchdünne Lichtfläche auf Kopfhöhe
 * über dem ganzen Dojo, mit leuchtendem Rand. Sie pulsiert, blitzt grell auf und zerspringt.
 */
public final class CutPlane extends BaseFx {

    private final FxContext c;
    private final Location origin;
    private final float sx, sz;
    private BlockDisplay sheet, sheetGlow;
    private final List<BlockDisplay> edges = new ArrayList<>();
    private int endAt = Integer.MAX_VALUE;

    /** origin = Mittelpunkt der Ebene (auf Schnitthöhe), sx/sz = Ausdehnung. */
    public CutPlane(FxContext c, Location origin, float sx, float sz) {
        super(c.fx);
        this.c = c;
        this.origin = origin;
        this.sx = sx;
        this.sz = sz;
        sheet = block(origin, Palette.WHITE_GLASS);
        sheetGlow = block(origin, Palette.PINK_GLASS);
        bright(sheet);
        bright(sheetGlow);
        if (sheet != null) {
            sheet.setViewRange(4f);
            DisplayFactory.snap(sheet, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(0.5f, 0.0f, 0.5f)));
            lerpAt(1, sheet, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx, 0.012f, sz)), 24);
        }
        if (sheetGlow != null) {
            sheetGlow.setViewRange(4f);
            DisplayFactory.snap(sheetGlow, Tf.box(new Vector3f(0, -0.03f, 0), new Quaternionf(), new Vector3f(0.5f, 0f, 0.5f)));
            lerpAt(1, sheetGlow, Tf.box(new Vector3f(0, -0.03f, 0), new Quaternionf(), new Vector3f(sx * 1.01f, 0.004f, sz * 1.01f)), 30);
        }
        // leuchtender Rand
        float[][] e = {{0, -sz / 2, sx, 0}, {0, sz / 2, sx, 0}, {-sx / 2, 0, sz, 1}, {sx / 2, 0, sz, 1}};
        for (float[] ed : e) {
            BlockDisplay d = block(origin, Palette.LIGHT);
            if (d == null) continue;
            bright(d);
            d.setViewRange(4f);
            Quaternionf rot = ed[3] == 1 ? new Quaternionf().rotationY((float) (Math.PI / 2)) : new Quaternionf();
            DisplayFactory.snap(d, Tf.box(new Vector3f(ed[0], 0, ed[1]), rot, new Vector3f(0.01f, 0.05f, 0.05f)));
            lerpAt(1, d, Tf.box(new Vector3f(ed[0], 0, ed[1]), rot, new Vector3f(ed[2], 0.07f, 0.07f)), 18);
            edges.add(d);
        }
    }

    private static void bright(Display d) {
        if (d != null) d.setBrightness(new Display.Brightness(15, 15));
    }

    /** Pulsieren: Fläche kurz dicker und wieder dünn. */
    public void pulse(float strength) {
        if (sheet == null) return;
        DisplayFactory.lerp(sheet, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx, 0.02f + 0.06f * strength, sz)), 3);
        at(age + 3, sheet, d -> DisplayFactory.lerp(d, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx, 0.012f, sz)), 6));
    }

    /** Der Schnitt: Fläche wird gleißend weiß (undurchsichtig) und zerspringt in Scherben. */
    public void flash() {
        if (sheet != null) {
            sheet.setBlock(Palette.LIGHT);
            DisplayFactory.snap(sheet, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx * 1.02f, 0.05f, sz * 1.02f)));
            lerpAt(age + 1, sheet, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx * 1.05f, 0.0f, sz * 1.05f)), 5);
        }
        if (sheetGlow != null) lerpAt(age, sheetGlow, Tf.box(new Vector3f(), new Quaternionf(), new Vector3f(sx * 1.2f, 0.0f, sz * 1.2f)), 6);
        for (BlockDisplay d : edges) {
            Transformation t = d.getTransformation();
            lerpAt(age, d, Tf.shrinkBox(t, 0f), 6);
        }
        // Scherben über die ganze Fläche
        int n = c.count(26);
        for (int i = 0; i < n; i++) {
            Location at = origin.clone().add(Rand.signed() * sx * 0.45f, 0, Rand.signed() * sz * 0.45f);
            c.fx.add(new Shards(c, at, 3, 2.2f, 0.35f, 22, Palette.WHITE_GLASS, Palette.PINK_GLASS, Palette.LIGHT));
        }
        endAt = age + 8;
    }

    @Override
    protected boolean update(int age) {
        return age < endAt;
    }
}
