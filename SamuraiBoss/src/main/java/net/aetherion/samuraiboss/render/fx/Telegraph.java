package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.MathUtil;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Boden-Telegrafie: Spieler sehen vorher genau, WO eine Attacke einschlägt und WANN.
 * <ul>
 *   <li><b>Linie</b>: rote Fläche + rosa Füllbalken, der sich bis zum Einschlag füllt</li>
 *   <li><b>Kreis</b>: Ring + Scheibe, die von der Mitte zum Ring wächst</li>
 *   <li><b>Kegel</b>: Fächer, der sich nach außen füllt</li>
 * </ul>
 * Beim Einschlag blitzt alles weiß auf und verschwindet.
 */
public final class Telegraph extends BaseFx {

    public enum Shape {LINE, CIRCLE, CONE}

    private final FxContext c;
    private final List<BlockDisplay> base = new ArrayList<>();
    private final List<BlockDisplay> fill = new ArrayList<>();
    private final List<org.bukkit.util.Transformation> fillFull = new ArrayList<>();
    private int strikeAt;
    private int endAt;
    private boolean struck;

    private static final float H = 0.025f;

    private Telegraph(FxContext c) {
        super(c.fx);
        this.c = c;
    }

    private static void bright(Display d) {
        if (d != null) d.setBrightness(new Display.Brightness(15, 15));
    }

    // ------------------------------------------------------------------ Linie

    /** Linie von a nach b (Bodenhöhe = a.y), Breite w, füllt sich in 'ticks' Ticks. */
    public static Telegraph line(FxContext c, Location a, Location b, float w, int ticks) {
        Telegraph t = new Telegraph(c);
        Location o = a.clone().add(0, 0.02, 0);
        Vector3f dir = MathUtil.delta(a, b);
        dir.y = 0;
        float len = dir.length();
        Quaternionf rot = MathUtil.lookAlongX(dir);
        BlockDisplay bd = t.block(o, Palette.RED_GLASS);
        BlockDisplay rim1 = t.block(o, Palette.RED);
        BlockDisplay rim2 = t.block(o, Palette.RED);
        BlockDisplay fl = t.block(o, Palette.PINK);
        bright(bd);
        bright(rim1);
        bright(rim2);
        bright(fl);
        Vector3f start = new Vector3f();
        t.snapAt(0, bd, Tf.beam(start, rot, len, w, H));
        Vector3f side = rot.transform(new Vector3f(0, 0, w / 2f - 0.04f));
        t.snapAt(0, rim1, Tf.beam(new Vector3f(side), rot, len, 0.07f, H * 1.6f));
        t.snapAt(0, rim2, Tf.beam(new Vector3f(side).negate(), rot, len, 0.07f, H * 1.6f));
        t.snapAt(0, fl, Tf.beam(start, rot, 0.001f, w * 0.55f, H * 1.4f));
        org.bukkit.util.Transformation full = Tf.beam(start, rot, len, w * 0.55f, H * 1.4f);
        t.lerpAt(1, fl, full, Math.max(1, ticks - 1));
        t.base.add(bd);
        t.base.add(rim1);
        t.base.add(rim2);
        t.fill.add(fl);
        t.fillFull.add(full);
        t.strikeAt = ticks;
        t.endAt = ticks + 400; // bis strike()/clear() gerufen wird
        return t;
    }

    // ------------------------------------------------------------------ Kreis

    public static Telegraph circle(FxContext c, Location center, float r, int ticks) {
        Telegraph t = new Telegraph(c);
        Location o = center.clone().add(0, 0.02, 0);
        int segs = Math.max(12, Math.min(28, Math.round(r * 4.5f)));
        float segLen = (float) (2 * Math.PI * r / segs) * 1.08f;
        for (int i = 0; i < segs; i++) {
            float a = (float) (i * 2 * Math.PI / segs);
            Vector3f pos = new Vector3f((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r);
            Quaternionf rot = new Quaternionf().rotationY(-a + (float) (Math.PI / 2));
            BlockDisplay seg = t.block(o, Palette.RED);
            bright(seg);
            t.snapAt(0, seg, Tf.box(new Vector3f(pos).add(0, H, 0), rot, new Vector3f(segLen, H * 2, 0.12f)));
            t.base.add(seg);
        }
        // gefüllte Scheibe: vier gegeneinander gedrehte Quadrate ergeben ein 16-Eck
        for (int k = 0; k < 4; k++) {
            Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(k * 22.5));
            float side = r * 2f * 0.92f;
            BlockDisplay g = t.block(o, Palette.RED_GLASS);
            bright(g);
            t.snapAt(0, g, Tf.box(new Vector3f(0, H / 2, 0), rot, new Vector3f(side, H, side)));
            t.base.add(g);
            BlockDisplay f = t.block(o, Palette.PINK);
            bright(f);
            t.snapAt(0, f, Tf.box(new Vector3f(0, H, 0), rot, new Vector3f(0.01f, H, 0.01f)));
            org.bukkit.util.Transformation full = Tf.box(new Vector3f(0, H, 0), rot, new Vector3f(side * 0.98f, H, side * 0.98f));
            t.lerpAt(1, f, full, Math.max(1, ticks - 1));
            t.fill.add(f);
            t.fillFull.add(full);
        }
        t.strikeAt = ticks;
        t.endAt = ticks + 400;
        return t;
    }

    // ------------------------------------------------------------------ Kegel

    /** Kegel ab origin in Richtung yaw (Grad), halber Winkel halfDeg, Radius r. */
    public static Telegraph cone(FxContext c, Location origin, float yaw, float halfDeg, float r, int ticks) {
        Telegraph t = new Telegraph(c);
        Location o = origin.clone().add(0, 0.02, 0);
        int wedges = Math.max(4, Math.round(halfDeg * 2 / 12f));
        float step = halfDeg * 2 / wedges;
        for (int i = 0; i < wedges; i++) {
            float aDeg = yaw - halfDeg + step * (i + 0.5f);
            Vector3f dir = MathUtil.vec(MathUtil.dirOfYaw(aDeg));
            Quaternionf rot = MathUtil.lookAlongX(dir);
            float rim = (float) (2 * r * Math.tan(Math.toRadians(step / 2))) * 1.05f;
            BlockDisplay g = t.block(o, Palette.RED_GLASS);
            bright(g);
            // Keil: schmaler Balken, der nach außen breit genug ist (überlappt innen)
            t.snapAt(0, g, Tf.beam(new Vector3f(), rot, r, rim, H));
            t.base.add(g);
            BlockDisplay f = t.block(o, Palette.PINK);
            bright(f);
            t.snapAt(0, f, Tf.beam(new Vector3f(), rot, 0.01f, rim * 0.9f, H * 1.4f));
            org.bukkit.util.Transformation full = Tf.beam(new Vector3f(), rot, r * 0.98f, rim * 0.9f, H * 1.4f);
            t.lerpAt(1, f, full, Math.max(1, ticks - 1));
            t.fill.add(f);
            t.fillFull.add(full);
        }
        // Rand-Bogen
        int arcSegs = Math.max(6, Math.round(halfDeg / 6f));
        for (int i = 0; i <= arcSegs; i++) {
            float aDeg = yaw - halfDeg + (halfDeg * 2) * i / arcSegs;
            Vector3f dir = MathUtil.vec(MathUtil.dirOfYaw(aDeg));
            Vector3f pos = new Vector3f(dir).mul(r);
            Quaternionf rot = MathUtil.lookAlongX(new Vector3f(-dir.z, 0, dir.x));
            BlockDisplay seg = t.block(o, Palette.RED);
            bright(seg);
            float len = (float) (2 * Math.PI * r * (halfDeg * 2 / 360f) / arcSegs) * 1.1f;
            t.snapAt(0, seg, Tf.box(pos.add(0, H, 0), rot, new Vector3f(len, H * 2, 0.12f)));
            t.base.add(seg);
        }
        t.strikeAt = ticks;
        t.endAt = ticks + 400;
        return t;
    }

    // ------------------------------------------------------------------ Einschlag

    /** Einschlag: weißer Blitz, dann verschwinden. */
    public void strike() {
        if (struck) return;
        struck = true;
        int now = age;
        for (BlockDisplay d : base) {
            blockAt(now, d, Palette.LIGHT);
            org.bukkit.util.Transformation tr = d.getTransformation();
            lerpAt(now + 1, d, Tf.shrinkBox(tr, 0.0f), 7);
        }
        for (int i = 0; i < fill.size(); i++) {
            BlockDisplay d = fill.get(i);
            blockAt(now, d, Palette.LIGHT);
            snapAt(now, d, fillFull.get(i));
            lerpAt(now + 1, d, Tf.shrinkBox(fillFull.get(i), 0.0f), 6);
        }
        endAt = now + 10;
    }

    /** Ohne Blitz entfernen (z. B. Fähigkeit abgebrochen). */
    public void clear() {
        endAt = age;
    }

    public boolean isStruck() {
        return struck;
    }

    @Override
    protected boolean update(int age) {
        return age < endAt;
    }

    /** Ändert die Farbe aller Füll-Teile (z. B. dunkelviolett für Schattenklone). */
    public Telegraph tint(BlockData fillColor, BlockData baseColor) {
        for (BlockDisplay d : fill) d.setBlock(fillColor);
        if (baseColor != null) for (BlockDisplay d : base) if (d.getBlock().equals(Palette.RED_GLASS)) d.setBlock(baseColor);
        return this;
    }

    @Override
    protected void onStop() {
        base.clear();
        fill.clear();
    }

    /** Für Unterklassen nicht benötigt – Hilfsmethode für sofortiges Entfernen. */
    static void hide(Display d) {
        DisplayFactory.snap(d, DisplayFactory.HIDDEN);
    }
}
